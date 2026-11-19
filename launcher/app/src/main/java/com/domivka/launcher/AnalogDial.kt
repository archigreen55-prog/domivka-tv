package com.domivka.launcher

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.View
import java.util.Calendar
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** Minimal analog face: glass ring, 12 ticks, white hour/minute hands, accent second hand. */
class AnalogDial(ctx: Context) : View(ctx) {
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = Ui.white(.22f) }
    private val face = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Ui.white(.06f) }
    private val tick = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND }
    private val hand = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND; color = 0xFFFFFFFF.toInt() }
    private val sec = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND }
    private var h = 0f
    private var m = 0f
    private var s = 0f

    fun set(c: Calendar) {
        val ns = c.get(Calendar.SECOND).toFloat()
        val nm = c.get(Calendar.MINUTE) + ns / 60f
        val nh = (c.get(Calendar.HOUR) + nm / 60f)
        if (ns == s && nm == m && nh == h) return
        s = ns; m = nm; h = nh
        invalidate()
    }

    override fun onDraw(c: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val r = min(cx, cy) - Ui.s(2f)
        ring.strokeWidth = Ui.s(1.5f)
        c.drawCircle(cx, cy, r, face)
        c.drawCircle(cx, cy, r, ring)
        for (i in 0 until 12) {
            val a = Math.toRadians(i * 30.0)
            val major = i % 3 == 0
            tick.color = if (major) Ui.white(.85f) else Ui.white(.4f)
            tick.strokeWidth = Ui.s(if (major) 4f else 2.5f)
            val r1 = r - Ui.s(10f)
            val r2 = r - Ui.s(if (major) 24f else 18f)
            c.drawLine(cx + (sin(a) * r1).toFloat(), cy - (cos(a) * r1).toFloat(),
                cx + (sin(a) * r2).toFloat(), cy - (cos(a) * r2).toFloat(), tick)
        }
        hand(c, cx, cy, h * 30f, r * 0.5f, Ui.s(7f), hand)
        hand(c, cx, cy, m * 6f, r * 0.74f, Ui.s(5f), hand)
        sec.color = Prefs.accent
        sec.strokeWidth = Ui.s(2.5f)
        hand(c, cx, cy, s * 6f, r * 0.82f, Ui.s(2.5f), sec, tail = r * 0.16f)
        c.drawCircle(cx, cy, Ui.s(6f), sec)
    }

    private fun hand(c: Canvas, cx: Float, cy: Float, deg: Float, len: Float, w: Float, p: Paint, tail: Float = 0f) {
        val a = Math.toRadians(deg.toDouble())
        p.strokeWidth = w
        val dx = sin(a).toFloat()
        val dy = -cos(a).toFloat()
        c.drawLine(cx - dx * tail, cy - dy * tail, cx + dx * len, cy + dy * len, p)
    }

    override fun hasOverlappingRendering() = false
}
