package com.domivka.launcher

import android.content.Context
import android.text.TextPaint
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.domivka.launcher.Ui.lp
import com.domivka.launcher.Ui.m
import com.domivka.launcher.Ui.spacing

/** Weather card: current conditions + 5-day tiles. Focusable (OK = refresh, or open settings when no city is set). */
class WeatherWidget(ctx: Context) : LinearLayout(ctx) {
    private class DayTile(ctx: Context) : LinearLayout(ctx) {
        val dow = Ui.text(ctx, 19f, 700, Ui.white(.8f))
        val icon = ImageView(ctx)
        val max = Ui.text(ctx, 21f, 700)
        val min = Ui.text(ctx, 17f, 500, Ui.white(.6f))

        init {
            orientation = VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(Ui.si(8f), Ui.si(12f), Ui.si(8f), Ui.si(12f))
            background = GlassBackground.subtle(20f)
            addView(dow)
            addView(icon, lp(Ui.si(44f), Ui.si(33f)).m(t = 4f))
            addView(max, lp(Ui.WRAP, Ui.WRAP).m(t = 4f))
            addView(min, lp(Ui.WRAP, Ui.WRAP).m(t = 4f))
        }

        override fun hasOverlappingRendering() = false
    }

    private val icon = ImageView(ctx)
    private val temp = Ui.text(ctx, 84f, 200).apply { spacing(-3f) }
    private val desc = Ui.text(ctx, 22f, 700).apply { gravity = Gravity.END }
    private val hiLo = Ui.text(ctx, 19f, 500, Ui.white(.78f)).apply { gravity = Gravity.END }
    private val wind = Ui.text(ctx, 19f, 500, Ui.white(.78f)).apply { gravity = Gravity.END }
    private val city = Ui.text(ctx, 17f, 500, Ui.white(.6f)).apply { gravity = Gravity.END }
    private val tiles = Array(5) { DayTile(ctx) }
    private val tilesRow = LinearLayout(ctx)
    private var iconRes = 0

    init {
        orientation = VERTICAL
        setPadding(Ui.si(36f), Ui.si(30f), Ui.si(36f), Ui.si(30f))
        clipChildren = false
        GlassBackground.bind(this, GlassBackground.card(32f))
        isClickable = true

        val top = LinearLayout(ctx).apply { gravity = Gravity.CENTER_VERTICAL }
        top.addView(icon, lp(Ui.si(104f), Ui.si(78f)))
        top.addView(temp, lp(Ui.WRAP, Ui.WRAP).m(l = 18f))
        val col = LinearLayout(ctx).apply { orientation = VERTICAL; gravity = Gravity.END }
        col.addView(desc)
        col.addView(hiLo, lp(Ui.WRAP, Ui.WRAP).m(t = 3f))
        col.addView(wind, lp(Ui.WRAP, Ui.WRAP).m(t = 3f))
        col.addView(city, lp(Ui.WRAP, Ui.WRAP).m(t = 3f))
        top.addView(col, lp(0, Ui.WRAP, 1f).m(l = 16f))
        addView(top)
        addView(View(ctx), lp(Ui.MATCH, 0, 1f))
        tilesRow.clipChildren = false
        tiles.forEachIndexed { i, t -> tilesRow.addView(t, lp(0, Ui.WRAP, 1f).m(l = if (i == 0) 0f else 12f)) }
        addView(tilesRow)
        bind(null)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        fit()
    }

    /** Shrinks the description text if it would not fit next to the icon + temperature (e.g. "Мінлива хмарність"). */
    private fun fit() {
        if (width == 0) return
        val avail = width - paddingLeft - paddingRight - Ui.si(104f) - Ui.si(18f) - temp.paint.measureText(temp.text.toString()) - Ui.si(16f)
        val tp = TextPaint(desc.paint)
        var size = Ui.s(22f)
        tp.textSize = size
        while (size > Ui.s(14f) && tp.measureText(desc.text.toString()) > avail) {
            size -= Ui.s(0.5f)
            tp.textSize = size
        }
        desc.setTextSize(TypedValue.COMPLEX_UNIT_PX, size)
    }

    private fun setIcon(iv: ImageView, res: Int) = iv.setImageDrawable(resources.getDrawable(res, null))

    fun bind(w: Weather?) {
        if (w == null) {
            if (iconRes != R.drawable.w_cloud) { iconRes = R.drawable.w_cloud; setIcon(icon, iconRes) }
            temp.text = "—"
            if (!Prefs.hasCity) {
                desc.text = "Вкажіть місто"
                hiLo.text = "натисніть OK"
                city.text = ""
            } else {
                desc.text = "Немає даних"
                hiLo.text = ""
                city.text = Prefs.cityName
            }
            wind.text = ""
            tilesRow.visibility = View.INVISIBLE
            fit()
            return
        }
        val res = WeatherRepository.icon(w.code)
        if (res != iconRes) { iconRes = res; setIcon(icon, res) }
        temp.text = Uk.temp(w.temp)
        desc.text = WeatherRepository.describe(w.code)
        hiLo.text = "↑ ${Uk.temp(w.max)}  ↓ ${Uk.temp(w.min)}"
        wind.text = "вітер ${w.wind} м/с"
        val stale = System.currentTimeMillis() - w.fetchedAt > 45 * 60 * 1000L
        city.text = Prefs.cityName + if (stale) " · ${Uk.hm(w.fetchedAt)}" else ""
        tilesRow.visibility = View.VISIBLE
        for (i in tiles.indices) {
            val d = w.days.getOrNull(i)
            val t = tiles[i]
            t.visibility = if (d == null) View.INVISIBLE else View.VISIBLE
            if (d == null) continue
            t.dow.text = Uk.daysShort[d.dow - 1]
            t.dow.setTextColor(if (d.dow == 1 || d.dow == 7) Ui.WEEKEND else Ui.white(.8f))
            setIcon(t.icon, WeatherRepository.icon(d.code))
            t.max.text = Uk.temp(d.max)
            t.min.text = Uk.temp(d.min)
        }
        fit()
    }
}
