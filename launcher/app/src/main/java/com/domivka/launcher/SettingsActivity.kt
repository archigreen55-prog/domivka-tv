package com.domivka.launcher

import android.app.Activity
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.domivka.launcher.Ui.lp
import com.domivka.launcher.Ui.m

/**
 * City search (own on-screen keyboard, works with a D-pad and without any system IME), shimmer on/off, accent colour.
 */
class SettingsActivity : Activity() {
    private lateinit var aurora: AuroraView
    private lateinit var current: TextView
    private lateinit var query: TextView
    private lateinit var status: TextView
    private lateinit var toggle: PillButton
    private val results = ArrayList<PillButton>()
    private val handler = Handler(Looper.getMainLooper())
    private val searchRunnable = Runnable { search() }
    private var text = ""
    private var latin = false
    private var searchId = 0
    private var cities: List<City> = emptyList()
    private val keyRows = ArrayList<LinearLayout>()
    private val swatches = ArrayList<View>()
    private lateinit var langKey: PillButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setBackgroundDrawable(SkyDrawable())
        val root = FrameLayout(this)
        aurora = AuroraView(this, animatable = false)
        root.addView(aurora, FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH))
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(Ui.si(80f), Ui.si(44f), Ui.si(80f), Ui.si(40f))
            clipChildren = false
            clipToPadding = false
        }
        root.addView(col, FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH))
        col.addView(Ui.text(this, 28f, 800).apply { text = "Налаштування" }, lp(Ui.WRAP, Ui.si(60f)))
        val row = LinearLayout(this).apply { clipChildren = false }
        row.addView(cityCard(), lp(Ui.si(1040f), Ui.WRAP))
        row.addView(sideCard(), lp(0, Ui.WRAP, 1f).m(l = 26f))
        col.addView(row, lp(Ui.MATCH, Ui.WRAP).m(t = 18f))
        setContentView(root)
        refreshText()
        showKeys()
        // From the weather card: start on the keyboard (city). From the gear: start on the look (accent colours).
        if (intent.getStringExtra(EXTRA_FOCUS) == FOCUS_CITY) keyRows[1].getChildAt(3).requestFocus()
        else (swatches.firstOrNull { (it as Swatch).isCurrent } ?: swatches.firstOrNull() ?: toggle).requestFocus()
    }

    override fun onResume() {
        super.onResume()
        aurora.resumed = true
    }

    override fun onPause() {
        aurora.resumed = false
        super.onPause()
    }

    // ---- left card: city + keyboard -------------------------------------------------------------------------

    private fun card(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(Ui.si(32f), Ui.si(28f), Ui.si(32f), Ui.si(32f))
        background = GlassBackground.card(32f)
        clipChildren = false
    }

    private fun cityCard(): View {
        val c = card()
        current = Ui.text(this, 24f, 700, Prefs.accent)
        c.addView(Ui.text(this, 22f, 700, Ui.white(.8f)).apply { text = "Місто для погоди" })
        c.addView(current, lp(Ui.WRAP, Ui.WRAP).m(t = 4f))
        query = Ui.text(this, 30f, 600).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(Ui.si(24f), 0, Ui.si(24f), 0)
            background = GlassBackground.subtle(20f)
        }
        c.addView(query, lp(Ui.MATCH, Ui.si(64f)).m(t = 16f, b = 14f))
        for (i in 0 until 3) {
            val r = LinearLayout(this).apply { clipChildren = false }
            keyRows.add(r)
            c.addView(r, lp(Ui.WRAP, Ui.si(KEY_H)).m(t = if (i == 0) 0f else 8f))
        }
        val bottom = LinearLayout(this).apply { clipChildren = false }
        langKey = key("EN", 120f) { latin = !latin; showKeys() }
        bottom.addView(langKey, lp(Ui.si(120f), Ui.si(KEY_H)))
        bottom.addView(key("-", 74f) { type("-") }, lp(Ui.si(74f), Ui.si(KEY_H)).m(l = 8f))
        bottom.addView(key("Пробіл", 0f) { type(" ") }, lp(0, Ui.si(KEY_H), 1f).m(l = 8f))
        bottom.addView(key("⌫", 110f) { if (text.isNotEmpty()) { text = text.dropLast(1); changed() } }, lp(Ui.si(110f), Ui.si(KEY_H)).m(l = 8f))
        bottom.addView(key("Шукати", 170f) { handler.removeCallbacks(searchRunnable); search() }, lp(Ui.si(170f), Ui.si(KEY_H)).m(l = 8f))
        c.addView(bottom, lp(Ui.MATCH, Ui.si(KEY_H)).m(t = 8f))
        return c
    }

    private fun key(label: String, w: Float, onClick: () -> Unit) =
        PillButton(this, label, KEY_H, 26f, 0f).apply {
            minWidth = Ui.si(w)
            setOnClickListener { onClick() }
        }

    private fun showKeys() {
        val rows = if (latin) listOf("qwertyuiop", "asdfghjkl", "zxcvbnm'") else listOf("йцукенгшщзхї", "фівапролджє", "ячсмитьбюґ'")
        langKey.text = if (latin) "УКР" else "EN"
        for (i in 0 until 3) {
            keyRows[i].removeAllViews()
            for ((j, ch) in rows[i].withIndex()) {
                keyRows[i].addView(key(ch.toString(), 74f) { type(ch.toString()) }, lp(Ui.si(74f), Ui.si(KEY_H)).m(l = if (j == 0) 0f else 8f))
            }
        }
    }

    private fun type(s: String) {
        if (text.length < 30) {
            text += s
            changed()
        }
    }

    private fun changed() {
        refreshText()
        handler.removeCallbacks(searchRunnable)
        if (text.trim().length >= 2) handler.postDelayed(searchRunnable, 800)
    }

    private fun refreshText() {
        current.text = if (Prefs.hasCity) Prefs.cityName else "не вказано"
        current.setTextColor(Prefs.accent)
        val shown = text.replaceFirstChar { it.uppercase() }
        val s = SpannableString(if (text.isEmpty()) "Введіть назву міста" else "$shown|")
        if (text.isEmpty()) s.setSpan(ForegroundColorSpan(Ui.white(.45f)), 0, s.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        else s.setSpan(ForegroundColorSpan(Prefs.accent), s.length - 1, s.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        query.text = s
    }

    private fun search() {
        val q = text.trim()
        if (q.length < 2) return
        val id = ++searchId
        status.text = "Пошук…"
        WeatherRepository.searchCity(q) { list ->
            if (id != searchId || isDestroyed) return@searchCity
            cities = list ?: emptyList()
            status.text = when {
                list == null -> "Помилка мережі"
                list.isEmpty() -> "Нічого не знайдено"
                else -> ""
            }
            for ((i, b) in results.withIndex()) {
                val c = cities.getOrNull(i)
                b.visibility = if (c == null) View.GONE else View.VISIBLE
                if (c != null) b.text = if (c.detail.isEmpty()) c.name else "${c.name} · ${c.detail}"
            }
        }
    }

    // ---- right card: results, shimmer toggle, accent ---------------------------------------------------------

    private fun sideCard(): View {
        val c = card()
        c.addView(Ui.text(this, 22f, 700, Ui.white(.8f)).apply { text = "Результати пошуку" })
        for (i in 0 until 5) {
            val b = PillButton(this, "", 60f, 20f, 26f).apply {
                gravity = Gravity.CENTER_VERTICAL or Gravity.START
                ellipsize = android.text.TextUtils.TruncateAt.END
                visibility = View.GONE
                setOnClickListener { choose(i) }
            }
            results.add(b)
            c.addView(b, lp(Ui.MATCH, Ui.WRAP).m(t = 8f))
        }
        status = Ui.text(this, 20f, 500, Ui.white(.6f)).apply { text = "Введіть назву й натисніть «Шукати»" }
        c.addView(status, lp(Ui.WRAP, Ui.WRAP).m(t = 10f, b = 18f))

        toggle = PillButton(this, "", 64f, 22f, 26f).apply {
            setOnClickListener { Prefs.animate = !Prefs.animate; refreshToggle() }
        }
        c.addView(toggle, lp(Ui.MATCH, Ui.WRAP).m(t = 8f))
        refreshToggle()

        c.addView(Ui.text(this, 22f, 700, Ui.white(.8f)).apply { text = "Колір акценту" }, lp(Ui.WRAP, Ui.WRAP).m(t = 26f))
        val sw = LinearLayout(this).apply { clipChildren = false }
        for ((i, col) in ACCENTS.withIndex()) {
            sw.addView(Swatch(this, col).also { swatches.add(it) }, lp(Ui.si(60f), Ui.si(60f)).m(l = if (i == 0) 0f else 18f))
        }
        c.addView(sw, lp(Ui.WRAP, Ui.WRAP).m(t = 14f))
        return c
    }

    private fun refreshToggle() {
        toggle.text = "Анімація сяйва:  " + if (Prefs.animate) "увімкнено" else "вимкнено"
    }

    private fun choose(i: Int) {
        val c = cities.getOrNull(i) ?: return
        Prefs.setCity(c.name, c.lat, c.lon)
        refreshText()
        Toast.makeText(this, "Місто збережено: ${c.name}", Toast.LENGTH_SHORT).show()
    }

    private inner class Swatch(ctx: android.content.Context, private val color: Int) : View(ctx) {
        val isCurrent get() = Prefs.accent == color
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        init {
            isFocusable = true
            isClickable = true
            setOnClickListener {
                Prefs.accent = color
                refreshText()
                window.decorView.invalidate()
                (parent as? View)?.invalidate()
            }
            setOnFocusChangeListener { v, has ->
                val s = if (has) 1.1f else 1f
                v.animate().cancel()
                v.animate().scaleX(s).scaleY(s).setDuration(150).withLayer().start()
                invalidate()
            }
        }

        override fun onDraw(c: Canvas) {
            val cx = width / 2f
            val cy = height / 2f
            val r = width / 2f
            paint.style = Paint.Style.FILL
            paint.color = color
            c.drawCircle(cx, cy, r - Ui.s(5f), paint)
            if (isFocused) {
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = Ui.s(5f)
                paint.color = Ui.white(.95f)
                c.drawCircle(cx, cy, r - Ui.s(2.5f), paint)
            }
            if (Prefs.accent == color) {
                val d = context.getDrawable(R.drawable.ic_check)
                if (d != null) {
                    d.setTint(Ui.DARK)
                    val h = (Ui.s(30f)).toInt()
                    d.setBounds((cx - h / 2).toInt(), (cy - h / 2).toInt(), (cx + h / 2).toInt(), (cy + h / 2).toInt())
                    d.draw(c)
                }
            }
        }
    }

    companion object {
        const val EXTRA_FOCUS = "focus"
        const val FOCUS_CITY = "city"
        const val FOCUS_LOOK = "look"
        private const val KEY_H = 70f
        val ACCENTS = intArrayOf(0xFF3FE0C5.toInt(), 0xFF8FB4FF.toInt(), 0xFFC49BFF.toInt(), 0xFFFFD36E.toInt())
    }
}
