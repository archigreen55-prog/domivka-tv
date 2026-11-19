package com.domivka.remote

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

/** On-screen remote: round OK pad with arrows, power, home/back, volume. */
class RemoteActivity : Activity() {
    private lateinit var dot: View
    private lateinit var status: TextView
    private lateinit var detail: TextView

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.bg_screen)
            setPadding(dp(24), dp(40), dp(24), dp(24))
        }
        // header: status dot + text, power button on the right
        val header = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        dot = View(this).apply { background = android.graphics.drawable.GradientDrawable().apply { shape = android.graphics.drawable.GradientDrawable.OVAL; setColor(Color.GRAY) } }
        header.addView(dot, LinearLayout.LayoutParams(dp(12), dp(12)))
        status = TextView(this).apply { textSize = 18f; setTextColor(Color.WHITE); text = "Перевіряю…"; typeface = android.graphics.Typeface.DEFAULT_BOLD }
        header.addView(status, LinearLayout.LayoutParams(0, -2, 1f).apply { leftMargin = dp(12) })
        status.setOnClickListener { askHost() }
        header.addView(icon(R.drawable.ic_power, R.drawable.bg_key_power, 60, 16) {
            val s = Tv.state(this); if (s == Tv.State.ON) Tv.turnOff(this) else Tv.turnOn(this)
            refreshStatus()
        })
        root.addView(header, LinearLayout.LayoutParams(-1, -2))
        detail = TextView(this).apply { textSize = 13f; setTextColor(0xFFFFB4A8.toInt()) }
        root.addView(detail, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })

        // D-pad
        val pad = GridLayout(this).apply { columnCount = 3; rowCount = 3 }
        fun cell(v: View?, r: Int, c: Int) {
            val lp = GridLayout.LayoutParams(GridLayout.spec(r), GridLayout.spec(c)).apply { width = dp(96); height = dp(96); setMargins(dp(6), dp(6), dp(6), dp(6)) }
            pad.addView(v ?: View(this), lp)
        }
        cell(null, 0, 0); cell(key(R.drawable.ic_up, "KEYCODE_DPAD_UP"), 0, 1); cell(null, 0, 2)
        cell(key(R.drawable.ic_left, "KEYCODE_DPAD_LEFT"), 1, 0)
        cell(ok(), 1, 1)
        cell(key(R.drawable.ic_right, "KEYCODE_DPAD_RIGHT"), 1, 2)
        cell(null, 2, 0); cell(key(R.drawable.ic_down, "KEYCODE_DPAD_DOWN"), 2, 1); cell(null, 2, 2)
        root.addView(pad, LinearLayout.LayoutParams(-2, -2).apply { gravity = Gravity.CENTER_HORIZONTAL; topMargin = dp(36) })

        // home / back
        val nav = LinearLayout(this).apply { gravity = Gravity.CENTER }
        nav.addView(key(R.drawable.ic_back, "KEYCODE_BACK"), LinearLayout.LayoutParams(dp(120), dp(64)).apply { rightMargin = dp(12) })
        nav.addView(key(R.drawable.ic_home, "KEYCODE_HOME"), LinearLayout.LayoutParams(dp(120), dp(64)).apply { leftMargin = dp(12) })
        root.addView(nav, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(28) })

        // volume
        val vol = LinearLayout(this).apply { gravity = Gravity.CENTER }
        vol.addView(key(R.drawable.ic_vol_down, "KEYCODE_VOLUME_DOWN"), LinearLayout.LayoutParams(dp(96), dp(64)).apply { rightMargin = dp(10) })
        vol.addView(key(R.drawable.ic_mute, "KEYCODE_VOLUME_MUTE"), LinearLayout.LayoutParams(dp(96), dp(64)))
        vol.addView(key(R.drawable.ic_vol_up, "KEYCODE_VOLUME_UP"), LinearLayout.LayoutParams(dp(96), dp(64)).apply { leftMargin = dp(10) })
        root.addView(vol, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(16) })

        // Screen mirroring: TV opens its Miracast receiver, the phone opens its own cast dialog.
        val cast = LinearLayout(this).apply {
            gravity = Gravity.CENTER
            setBackgroundResource(R.drawable.bg_key_cast)
            addView(ImageView(this@RemoteActivity).apply { setImageResource(R.drawable.ic_cast) }, LinearLayout.LayoutParams(dp(26), dp(26)))
            addView(TextView(this@RemoteActivity).apply {
                text = "Транслювати екран телефона"; textSize = 17f; setTextColor(Color.WHITE)
                typeface = android.graphics.Typeface.DEFAULT_BOLD
            }, LinearLayout.LayoutParams(-2, -2).apply { leftMargin = dp(12) })
            setOnClickListener { startCast() }
        }
        root.addView(cast, LinearLayout.LayoutParams(-1, dp(64)).apply { topMargin = dp(28); leftMargin = dp(6); rightMargin = dp(6) })
        setContentView(root)
        // Newer Android versions ask permission before an app may talk to devices in the home network.
        if (android.os.Build.VERSION.SDK_INT >= 36) runCatching {
            if (checkSelfPermission("android.permission.ACCESS_LOCAL_NETWORK") != android.content.pm.PackageManager.PERMISSION_GRANTED)
                requestPermissions(arrayOf("android.permission.ACCESS_LOCAL_NETWORK"), 1)
        }
    }

    private fun startCast() {
        Toast.makeText(this, "Відкриваю трансляцію на телевізорі…", Toast.LENGTH_SHORT).show()
        Tv.io.execute {
            val ok = Tv.startMirroringReceiver(this)
            Tv.release()   // free the TV's single ADB slot while casting
            runOnUiThread {
                if (!ok) Toast.makeText(this, "Телевізор не відповідає", Toast.LENGTH_SHORT).show()
                openPhoneCastDialog()
            }
        }
    }

    /** Samsung Smart View first, then the standard Android cast screens. The user taps "32_Smart_TDS" there. */
    private fun openPhoneCastDialog() {
        val candidates = listOf(
            android.content.Intent().setClassName("com.samsung.android.smartmirroring", "com.samsung.android.smartmirroring.CastingDialog"),
            packageManager.getLaunchIntentForPackage("com.samsung.android.smartmirroring"),
            android.content.Intent("android.settings.CAST_SETTINGS"),
            android.content.Intent("android.settings.WIFI_DISPLAY_SETTINGS"),
        )
        for (i in candidates) {
            if (i == null) continue
            try { startActivity(i.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)); return } catch (_: Exception) {}
        }
        Toast.makeText(this, "Відкрийте «Smart View» у шторці телефона", Toast.LENGTH_LONG).show()
    }

    private fun icon(res: Int, bg: Int, size: Int, pad: Int, action: () -> Unit) = ImageView(this).apply {
        setImageResource(res); setBackgroundResource(bg); setPadding(dp(pad), dp(pad), dp(pad), dp(pad))
        layoutParams = LinearLayout.LayoutParams(dp(size), dp(size))
        setOnClickListener { Tv.io.execute(action) }
    }

    private fun key(res: Int, code: String) = ImageView(this).apply {
        setImageResource(res); setBackgroundResource(R.drawable.bg_key); setPadding(dp(18), dp(18), dp(18), dp(18))
        scaleType = ImageView.ScaleType.FIT_CENTER
        setOnClickListener {
            Tv.io.execute { if (!Tv.key(this@RemoteActivity, code)) runOnUiThread { Toast.makeText(this@RemoteActivity, "Немає зв'язку з телевізором", Toast.LENGTH_SHORT).show() } }
        }
    }

    private fun ok() = TextView(this).apply {
        text = "OK"; textSize = 26f; setTextColor(Color.WHITE); gravity = Gravity.CENTER; typeface = android.graphics.Typeface.DEFAULT_BOLD
        setBackgroundResource(R.drawable.bg_key_ok)
        setOnClickListener { Tv.io.execute { Tv.key(this@RemoteActivity, "KEYCODE_DPAD_CENTER") } }
    }

    private fun askHost() {
        val et = android.widget.EditText(this).apply {
            setText(Tv.host(this@RemoteActivity)); hint = "напр. 192.168.0.10"
            inputType = android.text.InputType.TYPE_CLASS_PHONE
        }
        android.app.AlertDialog.Builder(this).setTitle("Адреса телевізора").setView(et)
            .setPositiveButton("Зберегти") { _, _ -> Tv.setHost(this, et.text.toString()); Tv.io.execute { refreshStatus() } }
            .setNegativeButton("Скасувати", null).show()
    }


    override fun onResume() { super.onResume(); Tv.io.execute { refreshStatus() } }

    override fun onPause() { Tv.release(); super.onPause() }

    private fun refreshStatus() {
        val s = Tv.state(this)
        runOnUiThread {
            detail.text = if (s == Tv.State.ON) "" else Tv.diag.ifEmpty { if (s == Tv.State.OFF) "Екран у режимі сну або телевізор вимкнений" else "" }
            status.text = when (s) { Tv.State.ON -> "Телевізор увімкнений"; Tv.State.OFF -> "Телевізор вимкнений"; else -> "Немає зв'язку" }
            (dot.background as android.graphics.drawable.GradientDrawable).setColor(when (s) { Tv.State.ON -> 0xFF5CE08E.toInt(); Tv.State.OFF -> 0xFF8A93A8.toInt(); else -> 0xFFFF8A80.toInt() })
        }
    }
}
