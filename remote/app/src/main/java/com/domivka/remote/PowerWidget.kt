package com.domivka.remote

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews

/** Home-screen widget: shows ON/OFF and a round power button; tap the text to refresh. */
class PowerWidget : AppWidgetProvider() {
    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) = refresh(ctx)
    override fun onEnabled(ctx: Context) = refresh(ctx)

    override fun onReceive(ctx: Context, i: Intent) {
        when (i.action) {
            ACTION_TOGGLE -> run(ctx) { toggle(ctx) }
            ACTION_REFRESH -> refresh(ctx)
            else -> super.onReceive(ctx, i)
        }
    }

    private fun refresh(ctx: Context) {
        push(ctx, "Перевіряю…", null)
        run(ctx) { push(ctx, null, Tv.state(ctx)) }
    }

    private fun toggle(ctx: Context) {
        when (Tv.state(ctx)) {
            Tv.State.ON -> {
                push(ctx, "Вимикаю…", null)
                Tv.turnOff(ctx)
                Thread.sleep(2500)
                push(ctx, null, Tv.state(ctx))
            }
            else -> {
                push(ctx, "Вмикаю…", null)
                push(ctx, null, Tv.turnOn(ctx))
            }
        }
    }

    private fun run(ctx: Context, block: () -> Unit) {
        val pending = goAsync()
        Tv.io.execute { try { block() } finally { pending.finish() } }
    }

    companion object {
        const val ACTION_TOGGLE = "com.domivka.remote.TOGGLE"
        const val ACTION_REFRESH = "com.domivka.remote.REFRESH"

        fun push(ctx: Context, busy: String?, s: Tv.State?) {
            val v = RemoteViews(ctx.packageName, R.layout.widget_power)
            val (text, color, bg) = when {
                busy != null -> Triple(busy, 0xFFFFD36E.toInt(), R.drawable.bg_btn_off)
                s == Tv.State.ON -> Triple("● Увімкнений", 0xFF5CE08E.toInt(), R.drawable.bg_btn_on)
                s == Tv.State.OFF -> Triple("○ Вимкнений", 0xFFFFFFFF.toInt(), R.drawable.bg_btn_off)
                else -> Triple("Немає зв'язку", 0xFFFF8A80.toInt(), R.drawable.bg_btn_off)
            }
            v.setTextViewText(R.id.status, text)
            v.setTextColor(R.id.status, color)
            v.setInt(R.id.btn, "setBackgroundResource", bg)
            v.setOnClickPendingIntent(R.id.btn, pi(ctx, ACTION_TOGGLE, 1))
            v.setOnClickPendingIntent(R.id.info, pi(ctx, ACTION_REFRESH, 2))
            val mgr = AppWidgetManager.getInstance(ctx)
            mgr.updateAppWidget(ComponentName(ctx, PowerWidget::class.java), v)
        }

        private fun pi(ctx: Context, action: String, code: Int) = PendingIntent.getBroadcast(
            ctx, code, Intent(ctx, PowerWidget::class.java).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }
}
