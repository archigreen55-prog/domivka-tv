package com.domivka.launcher

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.AbsoluteSizeSpan
import android.text.style.ForegroundColorSpan
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import com.domivka.launcher.Ui.lp
import com.domivka.launcher.Ui.m
import com.domivka.launcher.Ui.spacing
import java.util.Calendar
import java.util.GregorianCalendar

/**
 * Greeting by hour, big thin HH:mm with smaller seconds, weekday and date.
 * Ukrainian names are hardcoded (see [Uk]). Ticks every second between [start] and [stop].
 */
class ClockWidget(ctx: Context) : LinearLayout(ctx) {
    private val greeting = Ui.text(ctx, 24f, 600, Prefs.accent)
    private val time = Ui.text(ctx, TIME_SIZE, 200).apply {
        spacing(-4f)
        fontFeatureSettings = "tnum"
        gravity = Gravity.CENTER_VERTICAL
        maxLines = 1
    }
    private val dial = AnalogDial(ctx)
    private val weekday = Ui.text(ctx, 30f, 700)
    private val date = Ui.text(ctx, 24f, 500, Ui.white(.78f))
    private val secSize = Ui.si(TIME_SIZE * 0.42f)
    private val handler = Handler(Looper.getMainLooper())
    private var running = false
    private var lastText = ""

    private val ticker = object : Runnable {
        override fun run() {
            update()
            // fire just after the next whole second
            val now = System.currentTimeMillis()
            handler.postAtTime(this, SystemClock.uptimeMillis() + (1000 - now % 1000) + 5)
        }
    }

    init {
        orientation = VERTICAL
        setPadding(Ui.si(36f), Ui.si(32f), Ui.si(36f), Ui.si(32f))
        GlassBackground.bind(this, GlassBackground.card(32f))
        isClickable = true
        // OK on the clock cycles the face: digital+seconds → digital → analog.
        setOnClickListener {
            Prefs.clockFace = (Prefs.clockFace + 1) % Prefs.CLOCK_FACES.size
            applyFace()
            android.widget.Toast.makeText(ctx, "Годинник: " + Prefs.CLOCK_FACES[Prefs.clockFace], android.widget.Toast.LENGTH_SHORT).show()
        }
        clipChildren = false
        addView(greeting)
        addView(View(ctx), lp(Ui.MATCH, 0, 1f))
        // line-height .9: the text view is taller than that, so pull it in with negative margins
        val fm = time.paint.fontMetrics
        val pull = -((fm.descent - fm.ascent - Ui.s(TIME_SIZE * 0.9f)) / 2f).toInt()
        addView(time, lp(Ui.MATCH, Ui.WRAP).apply { setMargins(0, pull, 0, pull) })
        // Analog: big dial on the right, pulled up next to the greeting; text stays on the left.
        addView(dial, lp(Ui.si(232f), Ui.si(232f)).apply {
            gravity = Gravity.END
            topMargin = -Ui.si(40f)
            bottomMargin = -Ui.si(70f)
        })
        addView(View(ctx), lp(Ui.MATCH, 0, 1f))
        addView(weekday)
        addView(date, lp(Ui.WRAP, Ui.WRAP).m(t = 2f))
        applyFace()
    }

    private fun applyFace() {
        val analog = Prefs.clockFace == 2
        time.visibility = if (analog) GONE else VISIBLE
        dial.visibility = if (analog) VISIBLE else GONE
        lastText = ""
        update()
    }

    fun start() {
        if (running) return
        running = true
        ticker.run()
    }

    fun stop() {
        running = false
        handler.removeCallbacks(ticker)
    }

    fun update() {
        val c = GregorianCalendar()
        greeting.setTextColor(Prefs.accent)
        set(greeting, Uk.greeting(c.get(Calendar.HOUR_OF_DAY)))
        val hm = "%02d:%02d".format(java.util.Locale.US, c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE))
        val ss = "%02d".format(java.util.Locale.US, c.get(Calendar.SECOND))
        val face = Prefs.clockFace
        if (face == 2) dial.set(c)
        val key = hm + ss + Prefs.accent + face
        if (face != 2 && key != lastText) {
            lastText = key
            if (face == 1) time.text = hm
            else {
                val sb = SpannableStringBuilder(hm).append(' ').append(ss)
                val from = hm.length
                sb.setSpan(AbsoluteSizeSpan(secSize), from, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                sb.setSpan(ForegroundColorSpan(Ui.white(.6f)), from, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                time.text = sb
            }
        }
        set(weekday, Uk.daysFull[c.get(Calendar.DAY_OF_WEEK) - 1])
        set(date, "${c.get(Calendar.DAY_OF_MONTH)} ${Uk.monthsGen[c.get(Calendar.MONTH)]} ${c.get(Calendar.YEAR)}")
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        // Safety net: shrink the digits if "88:88 88" would not fit the card.
        val avail = w - paddingLeft - paddingRight
        if (avail <= 0) return
        val p = android.text.TextPaint(time.paint)
        var size = Ui.s(TIME_SIZE)
        fun width(s: Float): Float {
            p.textSize = s
            val a = p.measureText("88:88 ")
            p.textSize = s * 0.42f
            return a + p.measureText("88")
        }
        while (size > Ui.s(60f) && width(size) > avail) size -= Ui.s(2f)
        if (size != time.textSize) post { time.setTextSize(TypedValue.COMPLEX_UNIT_PX, size) }
    }

    private fun set(tv: android.widget.TextView, s: String) {
        if (tv.text.toString() != s) tv.text = s
    }

    private companion object {
        const val TIME_SIZE = 128f
    }
}
