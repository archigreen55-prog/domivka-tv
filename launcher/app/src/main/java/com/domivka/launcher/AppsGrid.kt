package com.domivka.launcher

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import android.view.View
import android.widget.LinearLayout
import com.domivka.launcher.Ui.lp
import com.domivka.launcher.Ui.m

object AppDefs {
    const val ALL = "@all"

    class Def(val pkg: String, val label: String, val color: Int, val icon: Int)

    val defs = listOf(
        Def("org.smarttube.beta", "YouTube", 0xFFE53946.toInt(), R.drawable.ic_youtube),
        Def("com.mediatek.androidbox", "З Android", 0xFF24A866.toInt(), R.drawable.ic_android),
        Def("com.ecloud.eairplay", "З iPhone", 0xFF3A7BFF.toInt(), R.drawable.ic_iphone),
        Def("org.videolan.vlc", "Відео й музика", 0xFFE8822A.toInt(), R.drawable.ic_video),
        Def("com.retroarch.ra32", "Ретро-ігри", 0xFF6E56CF.toInt(), R.drawable.ic_games),
        Def("com.alexcantini.blockstv", "Тетріс", 0xFF12A594.toInt(), R.drawable.ic_tetris),
        Def("net.jeu.morpion", "Хрестики-нулики", 0xFF2B3550.toInt(), R.drawable.ic_tictactoe),
        Def("com.cvte.tv.media", "Мультимедіа", 0xFF2C7BE5.toInt(), R.drawable.ic_media),
        Def("com.android.providers.downloads.ui", "Завантаження", 0xFF1E9BD7.toInt(), R.drawable.ic_download),
        Def("com.cvte.tv.androidsetting", "Налаштування", 0xFF4B5568.toInt(), R.drawable.ic_settings),
    )
    val allApps = Def(ALL, "Усі застосунки", 0xFF39445E.toInt(), R.drawable.ic_apps)

    /** Slots 0..9 = defs, 10 = "all apps", 11 = empty ("Додати"). */
    val defaultSlots: List<String> = defs.map { it.pkg } + ALL + ""

    fun def(pkg: String): Def? = if (pkg == ALL) allApps else defs.firstOrNull { it.pkg == pkg }
}

/** Leanback launch intent first, then the plain launcher intent. */
fun launchIntent(pm: PackageManager, pkg: String): Intent? {
    // Системний «Завантаження» не має іконки запуску — відкриваємо список завантажень напряму.
    if (pkg == PKG_DOWNLOADS_UI) {
        val i = Intent(android.app.DownloadManager.ACTION_VIEW_DOWNLOADS)
        if (i.resolveActivity(pm) != null) return i
    }
    return pm.getLeanbackLaunchIntentForPackage(pkg) ?: pm.getLaunchIntentForPackage(pkg)
}

private const val PKG_DOWNLOADS_UI = "com.android.providers.downloads.ui"

fun launchPackage(ctx: Context, pkg: String): Boolean {
    val i = launchIntent(ctx.packageManager, pkg) ?: return false
    return try {
        ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (e: Exception) {
        false
    }
}

/** One glass tile of the 6x2 grid: icon square + label, or the dashed "Додати" placeholder. */
class AppTile(ctx: Context) : View(ctx) {
    enum class Kind { APP, ALL, ADD }

    var kind = Kind.ADD
        private set
    var pkg = ""
        private set
    private var label = ""
    private var badgeColor = 0
    private var icon: Drawable? = null
    private var ownIcon = false
    private var layout: StaticLayout? = null

    private val glass = GlassBackground.tile(26f)
    private val dashed = GlassBackground.dashed(26f)
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = Ui.s(2f)
        color = Ui.white(.35f)
    }
    private val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG)
    private val badge = RectF()
    private val padL = Ui.s(26f)
    private val badgeSize = Ui.s(54f)
    private val gap = Ui.s(16f)

    init {
        GlassBackground.bind(this, glass)
        isClickable = true
        isLongClickable = true
    }

    /** [value] is a slot value from Prefs. */
    fun bindSlot(value: String) {
        val pm = context.packageManager
        pkg = value
        val def = AppDefs.def(value)
        when {
            value.isEmpty() -> {
                kind = Kind.ADD
                label = "Додати"
                badgeColor = 0
                icon = context.getDrawable(R.drawable.ic_plus)?.mutate()?.also { it.alpha = 217 }
                ownIcon = false
                alpha = 1f
            }
            def != null -> {
                kind = if (value == AppDefs.ALL) Kind.ALL else Kind.APP
                label = def.label
                badgeColor = def.color
                icon = context.getDrawable(def.icon)
                ownIcon = false
                alpha = if (kind == Kind.ALL || launchIntent(pm, value) != null) 1f else 0.45f
            }
            else -> {
                kind = Kind.APP
                badgeColor = 0
                ownIcon = true
                try {
                    label = pm.getApplicationLabel(pm.getApplicationInfo(value, 0)).toString()
                    icon = pm.getApplicationIcon(value)
                } catch (e: PackageManager.NameNotFoundException) {
                    label = value
                    icon = null
                }
                alpha = if (launchIntent(pm, value) != null) 1f else 0.45f
            }
        }
        val bg = if (kind == Kind.ADD) dashed else glass
        if (background !== bg) GlassBackground.bind(this, bg) else bg.focus = if (isFocused) 1f else 0f
        buildLayout()
        invalidate()
    }

    val installed: Boolean get() = kind != Kind.APP || launchIntent(context.packageManager, pkg) != null

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        buildLayout()
    }

    private fun buildLayout() {
        if (width == 0) return
        val avail = (width - padL - badgeSize - gap - padL).toInt()
        val weight = if (kind == Kind.ADD) 600 else 700
        textPaint.typeface = Ui.font(context, weight)
        textPaint.color = if (kind == Kind.ADD) Ui.white(.85f) else Color.WHITE
        // shrink until the longest unbreakable chunk (word or hyphen part) fits
        var size = Ui.s(if (kind == Kind.ADD) 22f else 21f)
        val chunks = label.split(' ').flatMap { w -> w.split("-").mapIndexed { i, p -> if (i == 0 && w.contains('-')) "$p-" else p } }
        textPaint.textSize = size
        while (size > Ui.s(15f) && chunks.any { textPaint.measureText(it) > avail }) {
            size -= Ui.s(1f)
            textPaint.textSize = size
        }
        // break after the hyphen ourselves ("Хрестики-" / "нулики") instead of relying on the line breaker
        val text = if (!label.contains(' ') && label.contains('-') && textPaint.measureText(label) > avail) label.replaceFirst("-", "-\n") else label
        layout = StaticLayout.Builder.obtain(text, 0, text.length, textPaint, avail)
            .setMaxLines(2).setEllipsize(TextUtils.TruncateAt.END).setIncludePad(false)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL).build()
    }

    override fun onDraw(c: Canvas) {
        val cy = height / 2f
        badge.set(padL, cy - badgeSize / 2f, padL + badgeSize, cy + badgeSize / 2f)
        val r = Ui.s(16f)
        var iconSize = Ui.s(32f)
        when {
            kind == Kind.ADD -> {
                val h = stroke.strokeWidth / 2f
                badge.inset(h, h)
                c.drawRoundRect(badge, r, r, stroke)
                badge.inset(-h, -h)
                iconSize = Ui.s(30f)
            }
            ownIcon -> iconSize = badgeSize
            else -> {
                fill.color = badgeColor
                c.drawRoundRect(badge, r, r, fill)
            }
        }
        icon?.let {
            val l = (badge.centerX() - iconSize / 2f).toInt()
            val t = (badge.centerY() - iconSize / 2f).toInt()
            it.setBounds(l, t, l + iconSize.toInt(), t + iconSize.toInt())
            it.draw(c)
        }
        layout?.let {
            c.save()
            c.translate(badge.right + gap, cy - it.height / 2f)
            it.draw(c)
            c.restore()
        }
    }

    override fun hasOverlappingRendering() = false
}

/** 6 columns x 2 rows of [AppTile]; slot i is [tile] i. */
class AppsGrid(
    ctx: Context,
    private val onOpen: (slot: Int) -> Unit,
    private val onMenu: (slot: Int) -> Unit,
) : LinearLayout(ctx) {
    val tiles = Array(12) { AppTile(ctx) }

    init {
        orientation = VERTICAL
        clipChildren = false
        for (row in 0 until 2) {
            val r = LinearLayout(ctx).apply { clipChildren = false }
            for (col in 0 until 6) {
                val i = row * 6 + col
                tiles[i].setOnClickListener { onOpen(i) }
                tiles[i].setOnLongClickListener { onMenu(i); true }
                r.addView(tiles[i], lp(0, Ui.MATCH, 1f).m(l = if (col == 0) 0f else 18f))
            }
            addView(r, lp(Ui.MATCH, Ui.si(136f)).m(t = if (row == 0) 0f else 18f))
        }
        refresh()
    }

    fun refresh() {
        for (i in tiles.indices) tiles[i].bindSlot(Prefs.slot(i))
    }

    fun slotOf(v: View?): Int = tiles.indexOf(v)
}
