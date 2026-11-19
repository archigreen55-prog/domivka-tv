package com.domivka.launcher

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View
import java.util.Calendar
import java.util.GregorianCalendar

/**
 * Month grid, Monday first, 6x7 cells. Drawn directly on a canvas (one View instead of ~50).
 * Focusable: D-pad left/right flips months, OK jumps back to the current month; leaving the card resets it.
 */
class CalendarWidget(ctx: Context) : View(ctx) {
    private val padL = Ui.s(36f)
    private val padT = Ui.s(28f)
    private val bold = paint(800, 26f)
    private val year = paint(500, 22f)
    private val head = paint(700, 16f)
    private val num = paint(500, 20f).apply { fontFeatureSettings = "tnum" }
    private val today = paint(800, 20f).apply { fontFeatureSettings = "tnum"; color = Ui.DARK }
    private val dot = Paint(Paint.ANTI_ALIAS_FLAG)

    private var y = 0
    private var mo = 0
    private var td = 0
    private val days = IntArray(42)
    private var firstOut = 0   // cells [0, firstOut) belong to the previous month
    private var lastIn = 0     // cells [lastIn, 42) belong to the next month
    private var browsing = false
    private var offset = 0     // months away from today (0 = current month)
    private var todayKey = -1
    private val arrow = paint(700, 26f)

    init {
        GlassBackground.bind(this, GlassBackground.card(32f))
        isClickable = true
        // OK toggles "flip mode": only then left/right change months, otherwise they move focus as usual.
        setOnClickListener {
            browsing = !browsing
            if (!browsing && offset != 0) show(0) else invalidate()
        }
        update()
    }

    override fun onFocusChanged(gainFocus: Boolean, direction: Int, previouslyFocusedRect: android.graphics.Rect?) {
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect)
        if (!gainFocus) browsing = false
        if (!gainFocus && offset != 0) show(0) else invalidate()
    }

    override fun onKeyDown(keyCode: Int, event: android.view.KeyEvent): Boolean {
        if (browsing) when (keyCode) {
            android.view.KeyEvent.KEYCODE_BACK -> { browsing = false; show(0); return true }
            android.view.KeyEvent.KEYCODE_DPAD_LEFT -> { show(offset - 1); return true }
            android.view.KeyEvent.KEYCODE_DPAD_RIGHT -> { show(offset + 1); return true }
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun show(newOffset: Int) {
        offset = newOffset
        todayKey = -1
        update()
    }

    private fun paint(weight: Int, size: Float) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Ui.font(context, weight)
        textSize = Ui.s(size)
    }

    fun update() {
        val now = GregorianCalendar()
        val ny = now.get(Calendar.YEAR)
        val nm = now.get(Calendar.MONTH)
        val nd = now.get(Calendar.DAY_OF_MONTH)
        val key = (ny * 12 + nm) * 32 + nd
        if (key == todayKey) return
        todayKey = key
        val c = GregorianCalendar(ny, nm, 1)
        c.add(Calendar.MONTH, offset)
        y = c.get(Calendar.YEAR); mo = c.get(Calendar.MONTH)
        td = if (offset == 0) nd else -1
        val lead = (c.get(Calendar.DAY_OF_WEEK) + 5) % 7           // Monday = 0
        val inMonth = c.getActualMaximum(Calendar.DAY_OF_MONTH)
        c.add(Calendar.DAY_OF_MONTH, -lead)
        for (i in 0 until 42) {
            days[i] = c.get(Calendar.DAY_OF_MONTH)
            c.add(Calendar.DAY_OF_MONTH, 1)
        }
        firstOut = lead
        lastIn = lead + inMonth
        invalidate()
    }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat()
        val inner = w - 2 * padL
        // header: month left, year right, shared baseline (row height 40)
        val base = padT + Ui.s(26f) * 1.066f
        bold.color = Color.WHITE
        var x = padL
        if (isFocused && browsing) {
            // "‹ Листопад ›" hint that left/right flips the month
            arrow.color = Prefs.accent
            c.drawText("‹", x, base, arrow)
            x += arrow.measureText("‹  ")
        }
        c.drawText(Uk.monthsNom[mo], x, base, bold)
        if (isFocused && browsing) {
            c.drawText("›", x + bold.measureText(Uk.monthsNom[mo] + "  "), base, arrow)
        }
        year.color = if (offset != 0) Prefs.accent else Ui.white(.7f)
        year.textAlign = Paint.Align.RIGHT
        c.drawText(y.toString(), w - padL, base, year)
        year.textAlign = Paint.Align.LEFT
        // weekday names
        head.textAlign = Paint.Align.CENTER
        val hb = padT + Ui.s(40f + 8f) + Ui.s(16f) * 1.066f
        val colW = inner / 7f
        for (i in 0 until 7) {
            head.color = if (i >= 5) Ui.WEEKEND else Ui.white(.65f)
            c.drawText(Uk.daysShort[(i + 1) % 7], padL + colW * (i + 0.5f), hb, head)
        }
        // day grid: 36 px rows, 2 px gaps
        val gridTop = padT + Ui.s(40f + 8f + 16f * 1.366f + 8f)
        val gap = Ui.s(2f)
        val cellW = (inner - 6 * gap) / 7f
        val cellH = Ui.s(36f)
        num.textAlign = Paint.Align.CENTER
        today.textAlign = Paint.Align.CENTER
        val fm = num.fontMetrics
        val dy = -(fm.ascent + fm.descent) / 2f
        for (i in 0 until 42) {
            val cx = padL + (i % 7) * (cellW + gap) + cellW / 2f
            val cy = gridTop + (i / 7) * (cellH + gap) + cellH / 2f
            val out = i < firstOut || i >= lastIn
            when {
                !out && days[i] == td -> {
                    dot.color = Prefs.accent
                    c.drawCircle(cx, cy, cellH / 2f, dot)
                    c.drawText(days[i].toString(), cx, cy + dy, today)
                }
                else -> {
                    num.color = when {
                        out -> Ui.white(.28f)
                        i % 7 >= 5 -> Ui.WEEKEND
                        else -> Color.WHITE
                    }
                    c.drawText(days[i].toString(), cx, cy + dy, num)
                }
            }
        }
    }

    override fun hasOverlappingRendering() = false
}
