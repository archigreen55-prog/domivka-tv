package com.domivka.launcher

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.Toast

/** Fallback: opens the manufacturer launcher, where the source can be picked by hand. */
class StockLauncherInputSwitcher(private val context: Context) : InputSwitcher {
    override fun switchTo(input: TvInput) {
        try {
            context.startActivity(
                Intent().setComponent(
                    ComponentName("com.cvte.tv.launcher.fall", "com.cvte.tv.launcher.NebulaFallActivity")
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (e: Exception) {
            Toast.makeText(context, "Не вдалося перемкнути вхід", Toast.LENGTH_SHORT).show()
        }
    }
}
