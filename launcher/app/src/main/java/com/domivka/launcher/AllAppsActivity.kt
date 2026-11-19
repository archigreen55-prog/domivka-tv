package com.domivka.launcher

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.BaseAdapter
import android.widget.FrameLayout
import android.widget.GridView
import android.widget.LinearLayout
import android.widget.Toast
import com.domivka.launcher.Ui.lp
import com.domivka.launcher.Ui.m

/** All launchable apps (LAUNCHER + LEANBACK_LAUNCHER). In pick mode it returns the chosen package instead of launching. */
class AllAppsActivity : Activity() {
    private class Entry(val pkg: String, val label: String, val image: Drawable?)

    private var entries: List<Entry> = emptyList()
    private lateinit var aurora: AuroraView
    private lateinit var grid: GridView
    private lateinit var empty: android.widget.TextView
    private var pickMode = false
    private var slot = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setBackgroundDrawable(SkyDrawable())
        pickMode = intent.getBooleanExtra(EXTRA_PICK, false)
        slot = intent.getIntExtra(EXTRA_SLOT, -1)

        val root = FrameLayout(this)
        aurora = AuroraView(this, animatable = false)
        root.addView(aurora, FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH))
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(Ui.si(80f), Ui.si(44f), Ui.si(80f), Ui.si(24f))
            clipChildren = false
            clipToPadding = false
        }
        root.addView(col, FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH))
        col.addView(Ui.text(this, 28f, 800).apply { text = if (pickMode) "Оберіть застосунок" else "Усі застосунки" },
            lp(Ui.WRAP, Ui.si(60f)).m(b = 8f))

        val pad = Ui.si(10f)
        grid = GridView(this).apply {
            numColumns = 6
            columnWidth = Ui.si(278f)
            horizontalSpacing = Ui.si(18f)
            verticalSpacing = Ui.si(18f)
            stretchMode = GridView.STRETCH_COLUMN_WIDTH
            selector = android.graphics.drawable.ColorDrawable(Color.TRANSPARENT)
            setPadding(pad, pad, pad, pad)
            clipToPadding = false
            clipChildren = false
            isVerticalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            isFocusable = true
            onItemClickListener = AdapterView.OnItemClickListener { _, _, pos, _ -> open(entries[pos]) }
        }
        col.addView(grid, lp(Ui.MATCH, 0, 1f))
        empty = Ui.text(this, 22f, 600, Ui.white(.7f)).apply { text = "Завантаження…"; gravity = Gravity.CENTER }
        col.addView(empty, 1, lp(Ui.MATCH, Ui.WRAP).m(t = 40f))
        setContentView(root)
        load()
    }

    override fun onResume() {
        super.onResume()
        aurora.resumed = true
    }

    override fun onPause() {
        aurora.resumed = false
        super.onPause()
    }

    private fun open(e: Entry) {
        if (pickMode) {
            setResult(RESULT_OK, Intent().putExtra(EXTRA_PKG, e.pkg).putExtra(EXTRA_SLOT, slot))
            finish()
        } else if (!launchPackage(this, e.pkg)) {
            Toast.makeText(this, "Не встановлено", Toast.LENGTH_SHORT).show()
        }
    }

    private fun load() {
        val pm = packageManager
        val self = packageName
        Thread {
            val seen = HashSet<String>()
            val list = ArrayList<Entry>()
            for (cat in arrayOf(Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER)) {
                val ris: List<ResolveInfo> = pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(cat), 0)
                for (ri in ris) {
                    val pkg = ri.activityInfo.packageName
                    if (pkg == self || !seen.add(pkg)) continue
                    val image: Drawable? = try {
                        (if (cat == Intent.CATEGORY_LEANBACK_LAUNCHER) ri.activityInfo.loadBanner(pm) else null) ?: ri.loadIcon(pm)
                    } catch (e: Exception) { null }
                    list.add(Entry(pkg, ri.loadLabel(pm).toString(), image))
                }
            }
            list.sortBy { it.label.lowercase() }
            Handler(Looper.getMainLooper()).post {
                if (isDestroyed) return@post
                entries = list
                grid.adapter = Adapter()
                empty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
                if (list.isEmpty()) empty.text = "Застосунків не знайдено"
                grid.requestFocus()
                grid.setSelection(0)
            }
        }.start()
    }

    private inner class Adapter : BaseAdapter() {
        override fun getCount() = entries.size
        override fun getItem(position: Int): Any = entries[position]
        override fun getItemId(position: Int) = position.toLong()
        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val cell = (convertView as? AppCell) ?: AppCell(this@AllAppsActivity)
            val e = entries[position]
            cell.bind(e.label, e.image)
            return cell
        }
    }

    companion object {
        const val EXTRA_PICK = "pick"
        const val EXTRA_SLOT = "slot"
        const val EXTRA_PKG = "pkg"
    }
}

/** Grid cell: app icon/banner on top, label below. Selection (GridView sets it on the D-pad focus) = scale + ring. */
class AppCell(ctx: Context) : View(ctx) {
    private val glass = GlassBackground.tile(26f)
    private var image: Drawable? = null
    private var layout: StaticLayout? = null
    private var label = ""
    private val textPaint = TextPaint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Ui.font(ctx, 700)
        textSize = Ui.s(20f)
        color = Color.WHITE
    }

    init {
        background = glass
    }

    fun bind(text: String, img: Drawable?) {
        label = text
        image = img
        layout = null
        glass.snapFocus(this, isSelected)
        invalidate()
    }

    override fun setSelected(selected: Boolean) {
        val changed = isSelected != selected
        super.setSelected(selected)
        if (changed) glass.animateFocus(this, selected)
    }

    override fun onMeasure(wSpec: Int, hSpec: Int) {
        setMeasuredDimension(MeasureSpec.getSize(wSpec), Ui.si(176f))
    }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat()
        image?.let {
            val bw = Ui.s(150f)
            val bh = Ui.s(84f)
            val iw = it.intrinsicWidth.coerceAtLeast(1)
            val ih = it.intrinsicHeight.coerceAtLeast(1)
            val sc = minOf(bw / iw, bh / ih)
            val dw = iw * sc
            val dh = ih * sc
            val l = (w - dw) / 2f
            val t = Ui.s(18f) + (bh - dh) / 2f
            it.setBounds(l.toInt(), t.toInt(), (l + dw).toInt(), (t + dh).toInt())
            it.draw(c)
        }
        val lay = layout ?: StaticLayout.Builder.obtain(label, 0, label.length, textPaint, (w - Ui.s(36f)).toInt())
            .setMaxLines(2).setEllipsize(TextUtils.TruncateAt.END).setIncludePad(false)
            .setAlignment(Layout.Alignment.ALIGN_CENTER).build().also { layout = it }
        c.save()
        c.translate(Ui.s(18f), Ui.s(112f))
        lay.draw(c)
        c.restore()
    }

    override fun hasOverlappingRendering() = false
}
