package com.domivka.launcher

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.ColorFilter
import android.graphics.drawable.Drawable
import android.view.View
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * "Glass" without any blur: translucent white rounded rect + thin light border, drawn over the (already soft) aurora.
 * [focus] (0..1) fades in the focus look: brighter fill + ring (white, or accent for pills). The ring is drawn
 * inside the bounds, so it is never clipped by a hardware layer or a parent.
 */
class GlassBackground private constructor(
    private val radius: Float,
    private val fill: Int,
    private val border: Int,
    private val borderW: Float,
    private val focusFill: Int,
    private val accentRing: Boolean,
    dashed: Boolean = false,
) : Drawable() {

    var focus = 0f
        set(v) {
            if (v != field) {
                field = v
                invalidateSelf()
            }
        }

    private val rect = RectF()
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        if (dashed) pathEffect = DashPathEffect(floatArrayOf(Ui.s(10f), Ui.s(8f)), 0f)
    }
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val ringW = Ui.s(5f)

    override fun draw(c: Canvas) {
        val f = focus
        val r = min(radius, min(bounds.width(), bounds.height()) / 2f)
        rect.set(bounds)
        val fc = lerp(fill, focusFill, f)
        if (Color.alpha(fc) != 0) {
            fillPaint.color = fc
            c.drawRoundRect(rect, r, r, fillPaint)
        }
        if (borderW > 0f && f < 1f) {
            borderPaint.strokeWidth = borderW
            borderPaint.color = scaleAlpha(border, 1f - f)
            val h = borderW / 2f
            rect.set(bounds.left + h, bounds.top + h, bounds.right - h, bounds.bottom - h)
            c.drawRoundRect(rect, r - h, r - h, borderPaint)
        }
        if (f > 0f) {
            ringPaint.strokeWidth = ringW
            ringPaint.color = scaleAlpha(if (accentRing) Prefs.accent else Ui.white(0.95f), f)
            val h = ringW / 2f
            rect.set(bounds.left + h, bounds.top + h, bounds.right - h, bounds.bottom - h)
            c.drawRoundRect(rect, r - h, r - h, ringPaint)
        }
    }

    /** Focus animation: scale 1.06 + ring, 150 ms, hardware layer while it runs. */
    fun animateFocus(v: View, has: Boolean) {
        val from = focus
        val to = if (has) 1f else 0f
        val sc = if (has) 1.06f else 1f
        v.animate().cancel()
        v.animate().scaleX(sc).scaleY(sc).setDuration(150)
            .setUpdateListener { a -> focus = from + (to - from) * a.animatedFraction }
            .withLayer().start()
    }

    /** Jump to the final state without animating (for recycled views). */
    fun snapFocus(v: View, has: Boolean) {
        v.animate().cancel()
        val sc = if (has) 1.06f else 1f
        v.scaleX = sc
        v.scaleY = sc
        focus = if (has) 1f else 0f
    }

    override fun setAlpha(alpha: Int) {}
    override fun setColorFilter(cf: ColorFilter?) {}
    @Deprecated("Deprecated in Java")
    override fun getOpacity() = PixelFormat.TRANSLUCENT

    companion object {
        private val listener = View.OnFocusChangeListener { v, has -> (v.background as? GlassBackground)?.animateFocus(v, has) }

        fun bind(v: View, bg: GlassBackground) {
            v.background = bg
            v.isFocusable = true
            v.onFocusChangeListener = listener
            bg.focus = if (v.isFocused) 1f else 0f
        }

        fun card(r: Float) = GlassBackground(Ui.s(r), Ui.white(.09f), Ui.white(.22f), Ui.s(1.5f), Ui.white(.16f), false)
        fun tile(r: Float) = GlassBackground(Ui.s(r), Ui.white(.10f), Ui.white(.22f), Ui.s(1.5f), Ui.white(.17f), false)
        fun pill(r: Float) = GlassBackground(Ui.s(r), Ui.white(.10f), Ui.white(.24f), Ui.s(1.5f), Ui.white(.92f), true)
        fun dashed(r: Float) = GlassBackground(Ui.s(r), 0, Ui.white(.35f), Ui.s(2f), Ui.white(.12f), false, true)
        fun badge() = GlassBackground(Ui.s(100f), Ui.white(.12f), Ui.white(.30f), Ui.s(1.5f), Ui.white(.22f), false)
        fun subtle(r: Float) = GlassBackground(Ui.s(r), Ui.white(.08f), 0, 0f, 0, false)

        private fun scaleAlpha(c: Int, f: Float) = (c and 0x00FFFFFF) or ((Color.alpha(c) * f).roundToInt() shl 24)

        private fun lerp(a: Int, b: Int, t: Float): Int {
            if (t <= 0f) return a
            if (t >= 1f) return b
            fun ch(x: Int, y: Int) = (x + (y - x) * t).roundToInt()
            return Color.argb(
                ch(Color.alpha(a), Color.alpha(b)), ch(Color.red(a), Color.red(b)),
                ch(Color.green(a), Color.green(b)), ch(Color.blue(a), Color.blue(b))
            )
        }
    }
}
