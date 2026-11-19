package com.domivka.remote

import android.content.Context
import android.net.wifi.WifiManager
import dadb.Dadb
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.Executors

/**
 * Talks to the TV over ADB-over-Wi-Fi (the TV has ro.adb.secure=0, so no key is needed).
 * ON  = adb port open and the screen is awake; OFF = port closed or the TV is asleep.
 * Wake-up uses Wake-on-LAN; it only works if the TV listens to the network in standby.
 */
object Tv {
    enum class State { ON, OFF, UNKNOWN }

    private const val PORT = 5555
    // No IP/MAC is hardcoded: the TV is found by scanning the local network, and its MAC addresses
    // (Ethernet, Wi-Fi) are read from the TV while it is on, for Wake-on-LAN later.
    private const val DEFAULT_HOST = ""
    val io = Executors.newSingleThreadExecutor()
    private val pool = Executors.newCachedThreadPool()

    /**
     * One ADB session with a hard timeout. This TV's adbd serves ONE client at a time, so a second
     * client (e.g. a laptop with `adb connect`) makes the handshake hang forever.
     */
    private fun <T> adb(h: String, block: (Dadb) -> T): T? {
        val d = Dadb.create(h, PORT, null)
        val f = pool.submit<T> { block(d) }
        return try {
            f.get(7, java.util.concurrent.TimeUnit.SECONDS)
        } catch (e: java.util.concurrent.TimeoutException) {
            f.cancel(true)
            diag = "Телевізор зайнятий: до нього вже підключений інший пристрій (наприклад, комп'ютер з adb). Відключіть його."
            null
        } catch (e: Exception) {
            diag = "Помилка зв'язку: ${(e.cause ?: e).javaClass.simpleName} ${(e.cause ?: e).message ?: ""}"
            null
        } finally { runCatching { d.close() } }
    }

    /** Human-readable reason of the last failure, shown in the app. */
    @Volatile var diag = ""

    fun setHost(ctx: Context, h: String) { prefs(ctx).edit().putString("host", h.trim()).apply() }

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("tv", Context.MODE_PRIVATE)
    fun host(ctx: Context): String = prefs(ctx).getString("host", DEFAULT_HOST) ?: DEFAULT_HOST

    private fun open(h: String): Boolean = try {
        Socket().use { it.connect(InetSocketAddress(h, PORT), 1500); true }
    } catch (e: Exception) { false }

    /** Host that answers on the ADB port: the saved one, else a scan of the phone's /24 (DHCP may change the IP). */
    private fun findHost(ctx: Context): String? {
        val saved = host(ctx)
        if (saved.isNotEmpty() && open(saved)) { diag = ""; return saved }
        val ip = phoneIp(ctx)
        if (ip == null) { diag = "Телефон не в Wi-Fi (немає локальної адреси)"; return null }
        val base = ip.substringBeforeLast('.')
        val found = java.util.concurrent.ConcurrentLinkedQueue<String>()
        val pool = Executors.newFixedThreadPool(48)
        for (i in 1..254) pool.execute {
            val h = "$base.$i"
            try { Socket().use { it.connect(InetSocketAddress(h, PORT), 400); found.add(h) } } catch (_: Exception) {}
        }
        pool.shutdown(); pool.awaitTermination(6, java.util.concurrent.TimeUnit.SECONDS)
        // Only accept a device that really is our TV (has the launcher/vendor service), not another ADB device.
        for (h in found) if (isOurTv(h)) { prefs(ctx).edit().putString("host", h).apply(); diag = ""; return h }
        diag = if (saved.isNotEmpty()) "Телевізор $saved не відповідає; у мережі $base.x його не знайдено"
               else "У мережі $base.x телевізор не знайдено"
        return null
    }

    private fun isOurTv(h: String): Boolean = adb(h) {
        it.shell("getprop ro.product.model").output.contains("K32", true) ||
            it.shell("pm list packages com.cvte.tv.api.impl").output.contains("cvte")
    } == true

    @Suppress("DEPRECATION")
    private fun phoneIp(ctx: Context): String? {
        val ip = (ctx.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager).connectionInfo?.ipAddress ?: return null
        if (ip == 0) return null
        return "${ip and 255}.${ip shr 8 and 255}.${ip shr 16 and 255}.${ip shr 24 and 255}"
    }

    fun state(ctx: Context): State {
        val h = findHost(ctx) ?: return State.OFF
        // Reuse the kept connection if there is one: a second connection would hang (one client only).
        val kept = session
        val check: (Dadb) -> State = { d ->
            learnMacs(ctx, d)
            val w = d.shell("dumpsys power | grep -m1 mWakefulness=").output
            if (w.contains("Asleep") || w.contains("Dozing")) State.OFF else State.ON
        }
        if (kept != null) return timed(kept, check) ?: State.UNKNOWN
        return adb(h) { d ->
            learnMacs(ctx, d)
            val w = d.shell("dumpsys power | grep -m1 mWakefulness=").output
            if (w.contains("Asleep") || w.contains("Dozing")) State.OFF else State.ON
        } ?: State.UNKNOWN
    }

    private fun learnMacs(ctx: Context, d: Dadb) {
        if (prefs(ctx).getString("macs", null) != null) return
        val re = Regex("([0-9a-fA-F]{2}(:[0-9a-fA-F]{2}){5})")
        val out = d.shell("dumpsys wifi | grep -m1 'MAC:'").output + "\n" + d.shell("dumpsys ethernet | grep -m1 'MAC address'").output
        val macs = re.findAll(out).map { it.value.lowercase() }.filter { it != "00:00:00:00:00:00" }.distinct().toList()
        if (macs.isNotEmpty()) prefs(ctx).edit().putString("macs", macs.joinToString(",")).apply()
    }

    private fun macs(ctx: Context): List<String> = prefs(ctx).getString("macs", "")!!.split(',').filter { it.isNotBlank() }

    // ---- fast keys ------------------------------------------------------------------------------------------
    // `input keyevent` starts a Java process on the TV and takes ~2 s on this CPU. Instead we keep one ADB
    // connection open while the remote is on screen and write the key straight into the TV's IR-remote
    // input device with the native `sendevent` (~0.1 s). Same path as the real remote, so volume is the
    // TV's own volume even when an HDMI box is shown, and POWER is a real standby.

    /** Android key name → Linux key code, from the TV's /system/usr/keylayout/mtkinp_events.kl. */
    private val LINUX = mapOf(
        "KEYCODE_DPAD_UP" to 103, "KEYCODE_DPAD_DOWN" to 108, "KEYCODE_DPAD_LEFT" to 105, "KEYCODE_DPAD_RIGHT" to 106,
        "KEYCODE_DPAD_CENTER" to 28, "KEYCODE_BACK" to 174, "KEYCODE_HOME" to 102, "KEYCODE_MENU" to 139,
        "KEYCODE_VOLUME_UP" to 115, "KEYCODE_VOLUME_DOWN" to 114, "KEYCODE_VOLUME_MUTE" to 113, "KEYCODE_POWER" to 116,
    )

    @Volatile private var session: Dadb? = null
    @Volatile private var keyDev: String? = null

    private fun session(ctx: Context): Dadb? {
        session?.let { return it }
        val h = findHost(ctx) ?: return null
        return Dadb.create(h, PORT, null).also { session = it }
    }

    /** Close the kept connection: this TV serves only one ADB client, so free it when the remote is hidden. */
    fun release() {
        val d = session; session = null
        if (d != null) pool.execute { runCatching { d.close() } }
    }

    private fun <T> timed(d: Dadb, block: (Dadb) -> T): T? {
        val f = pool.submit<T> { block(d) }
        return try { f.get(5, java.util.concurrent.TimeUnit.SECONDS) } catch (e: Exception) {
            f.cancel(true)
            diag = if (e is java.util.concurrent.TimeoutException)
                "Телевізор зайнятий: до нього вже підключений інший пристрій (наприклад, комп'ютер з adb). Відключіть його."
            else "Помилка зв'язку: ${(e.cause ?: e).javaClass.simpleName}"
            if (session === d) session = null
            runCatching { d.close() }
            null
        }
    }

    /** The IR-remote input device (/dev/input/eventN named "mtkinp_events"); event numbers can change on boot. */
    private fun findKeyDev(d: Dadb): String? {
        keyDev?.let { return it }
        val out = d.shell("getevent -p 2>/dev/null").output
        var dev: String? = null
        for (line in out.lines()) {
            val l = line.trim()
            if (l.startsWith("add device")) dev = l.substringAfter(": ").trim()
            else if (l.startsWith("name:") && (l.contains("mtkinp_events") || l.contains("cvt_keypad"))) {
                if (l.contains("mtkinp_events") || keyDev == null) keyDev = dev
                if (l.contains("mtkinp_events")) break
            }
        }
        return keyDev
    }

    /** Sends one key (KEYCODE_… name). Fast path via sendevent, fallback to `input keyevent`. */
    fun key(ctx: Context, code: String): Boolean {
        repeat(2) {   // second try with a fresh connection if the kept one went stale
            val d = session(ctx) ?: return false
            val ok = timed(d) { s ->
                val linux = LINUX[code]
                val dev = if (linux != null) findKeyDev(s) else null
                if (dev != null) {
                    s.shell("sendevent $dev 1 $linux 1; sendevent $dev 0 0 0; sendevent $dev 1 $linux 0; sendevent $dev 0 0 0").exitCode == 0
                } else s.shell("input keyevent $code").exitCode == 0
            }
            if (ok == true) return true
        }
        return false
    }

    fun run(ctx: Context, cmd: String): Boolean {
        val h = findHost(ctx) ?: return false
        return adb(h) { it.shell(cmd).exitCode == 0 } == true
    }

    /** Opens the TV's Miracast receiver ("Waiting for wireless display…", MediaTek androidbox). */
    fun startMirroringReceiver(ctx: Context): Boolean {
        val d = session(ctx) ?: return false
        return timed(d) { it.shell("am start -n com.mediatek.androidbox/.BoxActivity").exitCode == 0 } == true
    }

    /** Off: put the TV to sleep. Returns false if unreachable. */
    fun turnOff(ctx: Context) = key(ctx, "KEYCODE_POWER").also { release() }

    /** On: wake the screen if the TV is merely asleep, else send Wake-on-LAN. Waits up to ~20 s. */
    fun turnOn(ctx: Context): State {
        if (host(ctx).isNotEmpty() && open(host(ctx))) { key(ctx, "KEYCODE_WAKEUP"); release() } else wol(ctx)
        repeat(10) {
            Thread.sleep(2000)
            val s = state(ctx)
            if (s == State.ON) return s
        }
        return state(ctx)
    }

    private fun wol(ctx: Context) {
        val targets = ArrayList<String>().apply {
            add("255.255.255.255")
            phoneIp(ctx)?.let { add(it.substringBeforeLast('.') + ".255") }
        }
        try {
            DatagramSocket().use { s ->
                s.broadcast = true
                for (mac in macs(ctx)) {
                    val b = mac.split(':').map { it.toInt(16).toByte() }.toByteArray()
                    val magic = ByteArray(6) { 0xFF.toByte() } + ByteArray(16 * 6) { b[it % 6] }
                    for (t in targets) for (port in intArrayOf(9, 7))
                        s.send(DatagramPacket(magic, magic.size, InetAddress.getByName(t), port))
                }
            }
        } catch (_: Exception) {}
    }
}
