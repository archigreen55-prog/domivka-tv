package com.domivka.launcher

import android.content.Context
import android.graphics.Color
import android.util.TypedValue
import android.view.Gravity
import android.widget.TextView

/** Rounded pill button: glass when idle; light fill + accent ring + dark text when focused (the "HDMI 1" look). */
class PillButton(
    ctx: Context, label: String, height: Float = 72f, size: Float = 24f, padH: Float = 34f,
) : TextView(ctx) {
    init {
        text = label
        typeface = Ui.font(ctx, 700)
        setTextSize(TypedValue.COMPLEX_UNIT_PX, Ui.s(size))
        setTextColor(Color.WHITE)
        gravity = Gravity.CENTER
        includeFontPadding = false
        maxLines = 1
        setPadding(Ui.si(padH), 0, Ui.si(padH), 0)
        minHeight = Ui.si(height)
        GlassBackground.bind(this, GlassBackground.pill(height / 2f))
        isClickable = true
    }

    override fun onFocusChanged(gainFocus: Boolean, direction: Int, previouslyFocusedRect: android.graphics.Rect?) {
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect)
        setTextColor(if (gainFocus) Ui.DARK else Color.WHITE)
    }

    override fun hasOverlappingRendering() = false
}

/** Round glass icon button (settings gear in the header). */
class IconButton(ctx: Context, res: Int, size: Float = 52f, icon: Float = 26f) : android.widget.ImageView(ctx) {
    init {
        setImageDrawable(ctx.getDrawable(res))
        scaleType = ScaleType.FIT_CENTER
        val p = Ui.si((size - icon) / 2f)
        setPadding(p, p, p, p)
        GlassBackground.bind(this, GlassBackground.badge())
        isClickable = true
        setColorFilter(Ui.white(.9f))
    }

    override fun hasOverlappingRendering() = false
}
