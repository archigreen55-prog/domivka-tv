package com.domivka.launcher

import android.app.Application

class DomivkaApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Ui.init(this)
        Prefs.init(this)
        // The single injection point for TV input switching. Swap the implementation here.
        inputSwitcher = CvteInputSwitcher(this, StockLauncherInputSwitcher(this)).also { it.connect() }
    }

    companion object {
        lateinit var inputSwitcher: InputSwitcher
            private set
    }
}
