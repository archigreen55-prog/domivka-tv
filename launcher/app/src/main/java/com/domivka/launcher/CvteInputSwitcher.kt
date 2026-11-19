package com.domivka.launcher

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log

/**
 * Перемикання входів через закриту службу виробника CVTE (як це робить рідний лаунчер
 * com.cvte.tv.launcher.fall, клас SourceServer):
 *   1) ITVApiSystemInputSourceAidl.eventSystemInputSourceSetInputSource(id)
 *   2) ITVApiScreenWindowAidl.eventScreenWindowSetFull()
 *   3) старт com.cvte.tv.androidsetting/.TvSettingActivity (категорія TV_HOME, isLauncherGoToTv=true)
 *
 * Класи SDK (cn.cvte.tv.api.jar) лежать у BOOTCLASSPATH, тож доступні будь-якому застосунку.
 * Точні сигнатури SDK невідомі (немає документації), тому всі виклики — через рефлексію:
 * так невідповідність типу повернення не дасть NoSuchMethodError. На будь-якій помилці —
 * запасний варіант [fallback] (рідний лаунчер з рядком джерел).
 */
class CvteInputSwitcher(
    private val context: Context,
    private val fallback: InputSwitcher,
) : InputSwitcher {

    private val main = Handler(Looper.getMainLooper())
    private var manager: Any? = null
    private var bound = false
    private val pending = ArrayList<TvInput>()

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            manager = try {
                Class.forName("com.cvte.tv.api.aidl.ITvApiManager\$Stub")
                    .getMethod("asInterface", IBinder::class.java)
                    .invoke(null, binder)
            } catch (t: Throwable) {
                Log.e(TAG, "asInterface failed", t); null
            }
            Log.i(TAG, "connected; sources: " + describeSources())
            runCatching {
                val cur = manager?.call("getTVApiSystemInputSource")?.call("eventSystemInputSourceGetInputSource")
                if (cur != null) Log.i(TAG, "current: id=" + cur.javaClass.getField("id").getInt(cur) +
                        " cat=" + cur.javaClass.getField("category").get(cur))
            }.onFailure { Log.w(TAG, "current failed", it) }
            if (homeVisiblePending) { homeVisiblePending = false; setScreenMute(true) }
            val todo = ArrayList(pending); pending.clear()
            todo.forEach { switchTo(it) }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            manager = null
        }
    }

    /** Підключитися заздалегідь (викликати в onCreate головного екрана). */
    fun connect() {
        if (bound) return
        val intent = Intent(ACTION_TV_API).setPackage(PKG_TV_API)
        bound = try {
            context.bindService(intent, connection, Context.BIND_AUTO_CREATE)
        } catch (t: Throwable) {
            Log.e(TAG, "bindService failed", t); false
        }
    }

    fun disconnect() {
        if (bound) runCatching { context.unbindService(connection) }
        bound = false; manager = null
    }

    override fun switchTo(input: TvInput) {
        val mgr = manager
        if (mgr == null) {
            if (!bound) connect()
            if (bound) {
                pending.add(input)
                // Якщо служба не відповіла за 2 с — запасний варіант.
                main.postDelayed({ if (pending.remove(input)) fallback.switchTo(input) }, 2000)
            } else fallback.switchTo(input)
            return
        }
        try {
            val src = mgr.call("getTVApiSystemInputSource") ?: error("no input source api")
            val id = findSourceId(src, input) ?: error("input $input not found")
            Log.i(TAG, "set input $input -> id $id")
            setScreenMute(false)
            src.call("eventSystemInputSourceSetInputSource", id)
            runCatching { mgr.call("getTVApiScreenWindow")?.call("eventScreenWindowSetFull") }
                .onFailure { Log.w(TAG, "setFull failed", it) }
            context.startActivity(
                Intent(Intent.ACTION_MAIN)
                    .addCategory(CATEGORY_TV_HOME)
                    .setComponent(ComponentName(PKG_TV_SETTING, CLS_TV_SETTING))
                    .putExtra("isLauncherGoToTv", true)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (t: Throwable) {
            Log.e(TAG, "switch failed, fallback", t)
            fallback.switchTo(input)
        }
    }

    /** Головний екран на виду: глушимо картинку TV-джерела (як рідний лаунчер у onStop/PIP). */
    fun onHomeVisible() {
        if (manager == null) { homeVisiblePending = true; return }
        setScreenMute(true)
    }

    /** Leaving the home screen (an app or input opens): undo the mute, the vendor service also mutes audio with it. */
    fun onHomeHidden() {
        homeVisiblePending = false
        if (manager != null) setScreenMute(false)
    }

    private var homeVisiblePending = false

    private fun setScreenMute(mute: Boolean) {
        runCatching {
            val screen = manager?.call("getTVApiScreenWindow") ?: return
            val m = screen.javaClass.interfaces.firstNotNullOfOrNull { iface ->
                runCatching { iface.getMethod("eventScreenWindowMute", Boolean::class.javaPrimitiveType) }.getOrNull()
            } ?: screen.javaClass.getMethod("eventScreenWindowMute", Boolean::class.javaPrimitiveType)
                .also { it.isAccessible = true }
            m.invoke(screen, mute)
            Log.i(TAG, "screen mute=$mute")
        }.onFailure { Log.w(TAG, "screen mute failed", it) }
    }

    /** Діагностика: список входів так, як його бачить служба (для логів/налагодження). */
    fun describeSources(): String = try {
        val src = manager?.call("getTVApiSystemInputSource") ?: return "not connected"
        sources(src).joinToString { "${it.id}:${it.category}:${it.name}:${it.status}" }
    } catch (t: Throwable) { "error: $t" }

    private data class Src(val id: Int, val category: String, val name: String, val status: String)

    private fun sources(src: Any): List<Src> {
        val list = src.call("eventSystemInputSourceGetList") as? List<*> ?: return emptyList()
        return list.filterNotNull().map { e ->
            val c = e.javaClass
            Src(
                id = c.getField("id").getInt(e),
                category = c.getField("category").get(e)?.toString() ?: "",
                name = runCatching { c.getField("name").get(e)?.toString() ?: "" }.getOrDefault(""),
                status = runCatching { c.getField("status").get(e)?.toString() ?: "" }.getOrDefault(""),
            )
        }.filter { !it.status.endsWith("HIDE") }
    }

    private fun findSourceId(src: Any, input: TvInput): Int? {
        val all = sources(src)
        fun cat(vararg names: String) = all.filter { s -> names.any { s.category.endsWith(it) } }
        return when (input) {
            TvInput.HDMI1 -> cat("_HDMI").getOrNull(0)?.id
            TvInput.HDMI2 -> cat("_HDMI").getOrNull(1)?.id
            TvInput.HDMI3 -> cat("_HDMI").getOrNull(2)?.id
            TvInput.TV -> (cat("_TV") + cat("_DVBT", "_DVBC", "_DVBS", "_ATV")).firstOrNull()?.id
            TvInput.AV -> cat("_AV").firstOrNull()?.id
        }
    }

    private fun Any.call(method: String, vararg args: Int): Any? {
        val types = Array<Class<*>?>(args.size) { Int::class.javaPrimitiveType }
        // AIDL Stub.Proxy is a private nested class: take the method from its public
        // interface, otherwise invoke() can throw IllegalAccessException.
        val m = javaClass.interfaces.firstNotNullOfOrNull { iface ->
            runCatching { iface.getMethod(method, *types) }.getOrNull()
        } ?: javaClass.getMethod(method, *types).also { it.isAccessible = true }
        return m.invoke(this, *args.toTypedArray())
    }

    companion object {
        private const val TAG = "CvteInputSwitcher"
        private const val ACTION_TV_API = "com.cvte.tv.api.TV_API_SERVICE"
        private const val PKG_TV_API = "com.cvte.tv.api.impl"
        private const val PKG_TV_SETTING = "com.cvte.tv.androidsetting"
        private const val CLS_TV_SETTING = "com.cvte.tv.androidsetting.TvSettingActivity"
        private const val CATEGORY_TV_HOME = "android.intent.category.TV_HOME"
    }
}
