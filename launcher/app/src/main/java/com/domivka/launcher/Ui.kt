package com.domivka.launcher

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.util.TypedValue
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * All layout numbers in the code are mockup pixels (1920x1080 canvas). On the TV (1920x1080 px, density 240)
 * that is exactly 1 mockup px = 1 device px = 0.667 dp; on any other screen everything scales uniformly.
 */
object Ui {
    const val DARK = 0xFF04101C.toInt()
    const val WEEKEND = 0xFFFFB3CF.toInt()
    const val ACCENT_DEFAULT = 0xFF3FE0C5.toInt()
    const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
    const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

    var k = 1f
        private set

    fun init(ctx: Context) {
        val m = ctx.resources.displayMetrics
        val w = max(m.widthPixels, m.heightPixels).toFloat()
        val h = min(m.widthPixels, m.heightPixels).toFloat()
        k = min(w / 1920f, h / 1080f)
    }

    fun s(v: Float) = v * k
    fun si(v: Float) = (v * k).roundToInt()
    fun white(a: Float) = Color.argb((a * 255).roundToInt(), 255, 255, 255)

    private val fonts = HashMap<Int, Typeface>()

    /** Manrope static instances (weights 200/500/600/700/800), cut from the variable font; falls back to system fonts. */
    fun font(ctx: Context, weight: Int): Typeface = fonts.getOrPut(weight) {
        try {
            Typeface.createFromAsset(ctx.applicationContext.assets, "fonts/manrope_$weight.ttf")
        } catch (e: Exception) {
            when {
                weight <= 300 -> Typeface.create("sans-serif-light", Typeface.NORMAL)
                weight <= 600 -> Typeface.create("sans-serif-medium", Typeface.NORMAL)
                else -> Typeface.create("sans-serif", Typeface.BOLD)
            }
        }
    }

    fun text(ctx: Context, size: Float, weight: Int, color: Int = Color.WHITE): TextView = TextView(ctx).apply {
        setTextSize(TypedValue.COMPLEX_UNIT_PX, s(size))
        typeface = font(ctx, weight)
        setTextColor(color)
        includeFontPadding = false
        maxLines = 1
    }

    /** Letter spacing given in mockup px (CSS letter-spacing); must be called after the text size is set. */
    fun TextView.spacing(px: Float) {
        letterSpacing = px / size(this)
    }

    private fun size(tv: TextView) = tv.textSize / k

    fun lp(w: Int, h: Int, weight: Float = 0f) = LinearLayout.LayoutParams(w, h, weight)

    fun LinearLayout.LayoutParams.m(l: Float = 0f, t: Float = 0f, r: Float = 0f, b: Float = 0f) =
        apply { setMargins(si(l), si(t), si(r), si(b)) }
}
