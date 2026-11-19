package com.domivka.launcher

import android.app.Activity
import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.widget.LinearLayout
import com.domivka.launcher.Ui.lp
import com.domivka.launcher.Ui.m

object Dialogs {
    /** Small D-pad friendly dialog with a title and one pill button per option. */
    fun choose(a: Activity, title: String, options: List<String>, onPick: (Int) -> Unit) {
        val d = Dialog(a, R.style.GlassDialog)
        val col = LinearLayout(a).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(Ui.si(44f), Ui.si(34f), Ui.si(44f), Ui.si(40f))
            background = GradientDrawable().apply {
                setColor(0xFF14253D.toInt())
                cornerRadius = Ui.s(32f)
                setStroke(Ui.si(1.5f), Ui.white(.22f))
            }
        }
        col.addView(Ui.text(a, 26f, 800, Color.WHITE).apply { text = title })
        var first: PillButton? = null
        options.forEachIndexed { i, o ->
            val b = PillButton(a, o)
            b.setOnClickListener { d.dismiss(); onPick(i) }
            col.addView(b, lp(Ui.si(420f), Ui.WRAP).m(t = if (i == 0) 24f else 14f))
            if (first == null) first = b
        }
        d.setContentView(col)
        d.show()
        first?.requestFocus()
    }
}
