package com.domivka.launcher

import android.app.Activity
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Paint
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.Toast
import com.domivka.launcher.Ui.lp
import com.domivka.launcher.Ui.m

/**
 * Settings opened by the gear: launcher look on the left (city, clock face, shimmer, accent),
 * TV system settings on the right. The city search itself lives in [SettingsActivity].
 */
class SettingsHubActivity : Activity() {
    private lateinit var aurora: AuroraView
    private lateinit var city: PillButton
    private lateinit var face: PillButton
    private lateinit var shimmer: PillButton
    private val swatches = ArrayList<View>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setBackgroundDrawable(SkyDrawable())
        val root = FrameLayout(this)
        aurora = AuroraView(this, animatable = false)
        root.addView(aurora, FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH))
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(Ui.si(80f), Ui.si(44f), Ui.si(80f), Ui.si(40f))
            clipChildren = false; clipToPadding = false
        }
        root.addView(col, FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH))
        col.addView(Ui.text(this, 28f, 800).apply { text = "Налаштування" }, lp(Ui.WRAP, Ui.si(60f)))

        val row = LinearLayout(this).apply { clipChildren = false }
        row.addView(lookCard(), lp(0, Ui.WRAP, 1f))
        row.addView(tvCard(), lp(0, Ui.WRAP, 1f).m(l = 26f))
        col.addView(row, lp(Ui.MATCH, Ui.WRAP).m(t = 18f))
        setContentView(root)
        city.requestFocus()
    }

    override fun onResume() {
        super.onResume()
        aurora.resumed = true
        refresh()
    }

    override fun onPause() {
        aurora.resumed = false
        super.onPause()
    }

    private fun card(title: String) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(Ui.si(32f), Ui.si(28f), Ui.si(32f), Ui.si(32f))
        background = GlassBackground.card(32f)
        clipChildren = false
        addView(Ui.text(this@SettingsHubActivity, 22f, 700, Ui.white(.8f)).apply { text = title })
    }

    private fun row(onClick: () -> Unit) = PillButton(this, "", 72f, 22f, 30f).apply {
        gravity = Gravity.CENTER_VERTICAL or Gravity.START
        setOnClickListener { onClick() }
    }

    private fun lookCard(): View {
        val c = card("Домівка")
        city = row {
            startActivity(Intent(this, SettingsActivity::class.java).putExtra(SettingsActivity.EXTRA_FOCUS, SettingsActivity.FOCUS_CITY))
        }
        c.addView(city, lp(Ui.MATCH, Ui.WRAP).m(t = 16f))
        face = row { Prefs.clockFace = (Prefs.clockFace + 1) % Prefs.CLOCK_FACES.size; refresh() }
        c.addView(face, lp(Ui.MATCH, Ui.WRAP).m(t = 12f))
        shimmer = row { Prefs.animate = !Prefs.animate; refresh() }
        c.addView(shimmer, lp(Ui.MATCH, Ui.WRAP).m(t = 12f))
        c.addView(Ui.text(this, 22f, 700, Ui.white(.8f)).apply { text = "Колір акценту" }, lp(Ui.WRAP, Ui.WRAP).m(t = 24f))
        val sw = LinearLayout(this).apply { clipChildren = false }
        for ((i, color) in SettingsActivity.ACCENTS.withIndex()) {
            val s = Swatch(color).also { swatches.add(it) }
            sw.addView(s, lp(Ui.si(60f), Ui.si(60f)).m(l = if (i == 0) 0f else 18f))
        }
        c.addView(sw, lp(Ui.WRAP, Ui.WRAP).m(t = 14f))
        return c
    }

    private fun tvCard(): View {
        val c = card("Телевізор")
        c.addView(row { open(Intent("android.settings.cvte.NETWORK_SETTINGS"), Intent(android.provider.Settings.ACTION_WIFI_SETTINGS)) }
            .apply { text = "Мережа і Wi-Fi" }, lp(Ui.MATCH, Ui.WRAP).m(t = 16f))
        c.addView(row { open(Intent(android.provider.Settings.ACTION_SETTINGS)) }
            .apply { text = "Усі налаштування телевізора" }, lp(Ui.MATCH, Ui.WRAP).m(t = 12f))
        c.addView(row { open(Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS), Intent(android.provider.Settings.ACTION_SETTINGS)) }
            .apply { text = "Bluetooth" }, lp(Ui.MATCH, Ui.WRAP).m(t = 12f))
        val ver = runCatching { packageManager.getPackageInfo(packageName, 0).versionName }.getOrNull() ?: ""
        c.addView(Ui.text(this, 18f, 500, Ui.white(.5f)).apply { text = "Домівка $ver" }, lp(Ui.WRAP, Ui.WRAP).m(t = 24f))
        return c
    }

    private fun open(vararg intents: Intent) {
        for (i in intents) {
            try { startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); return } catch (_: Exception) {}
        }
        Toast.makeText(this, "Не вдалося відкрити", Toast.LENGTH_SHORT).show()
    }

    private fun refresh() {
        city.text = "Місто для погоди:  " + (if (Prefs.hasCity) Prefs.cityName else "не вказано")
        face.text = "Годинник:  " + Prefs.CLOCK_FACES[Prefs.clockFace]
        shimmer.text = "Анімація сяйва:  " + if (Prefs.animate) "увімкнено" else "вимкнено"
        swatches.forEach { it.invalidate() }
    }

    private inner class Swatch(private val color: Int) : View(this@SettingsHubActivity) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        init {
            isFocusable = true; isClickable = true
            setOnClickListener { Prefs.accent = color; refresh() }
            setOnFocusChangeListener { v, has ->
                val s = if (has) 1.12f else 1f
                v.animate().cancel(); v.animate().scaleX(s).scaleY(s).setDuration(150).start()
                invalidate()
            }
        }

        override fun onDraw(c: Canvas) {
            val r = width / 2f
            paint.style = Paint.Style.FILL; paint.color = color
            c.drawCircle(r, r, r * 0.72f, paint)
            if (Prefs.accent == color || isFocused) {
                paint.style = Paint.Style.STROKE; paint.strokeWidth = Ui.s(3f)
                paint.color = if (isFocused) 0xFFFFFFFF.toInt() else Ui.white(.6f)
                c.drawCircle(r, r, r * 0.92f, paint)
            }
        }
    }
}
