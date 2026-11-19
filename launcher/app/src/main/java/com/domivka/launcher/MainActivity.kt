package com.domivka.launcher

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.domivka.launcher.Ui.lp
import com.domivka.launcher.Ui.m
import com.domivka.launcher.Ui.spacing

class MainActivity : Activity() {
    private lateinit var aurora: AuroraView
    private lateinit var clock: ClockWidget
    private lateinit var calendar: CalendarWidget
    private lateinit var weather: WeatherWidget
    private lateinit var grid: AppsGrid
    private lateinit var homeIcon: ImageView
    private lateinit var netIcon: ImageView
    private lateinit var netText: TextView

    private val handler = Handler(Looper.getMainLooper())
    private var netCallback: ConnectivityManager.NetworkCallback? = null
    private var receiverRegistered = false

    private val timeReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, i: Intent?) = tick()
    }
    private val weatherTick = Runnable {
        refreshWeather()
        scheduleWeather(WEATHER_MAX_AGE)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setBackgroundDrawable(SkyDrawable())
        setContentView(buildUi())
        grid.tiles[0].requestFocus()       // initial focus: YouTube
    }

    // ---- UI --------------------------------------------------------------------------------------------------

    private fun buildUi(): View {
        val root = FrameLayout(this)
        aurora = AuroraView(this)
        root.addView(aurora, FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH))

        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(Ui.si(80f), Ui.si(44f), Ui.si(80f), Ui.si(48f))
            clipChildren = false
            clipToPadding = false
        }
        root.addView(col, FrameLayout.LayoutParams(Ui.MATCH, Ui.MATCH))

        col.addView(buildHeader(), lp(Ui.MATCH, Ui.si(60f)))

        // widgets row: 560 | flexible | 560, gap 26, height 370
        clock = ClockWidget(this)
        weather = WeatherWidget(this).apply {
            // OK on the weather card = change the city (Settings opens on the city search).
            setOnClickListener { openSettings(SettingsActivity.FOCUS_CITY) }
        }
        calendar = CalendarWidget(this)
        val widgets = LinearLayout(this).apply { clipChildren = false }
        widgets.addView(clock, lp(Ui.si(560f), Ui.MATCH))
        widgets.addView(weather, lp(0, Ui.MATCH, 1f).m(l = 26f, r = 26f))
        widgets.addView(calendar, lp(Ui.si(560f), Ui.MATCH))
        col.addView(widgets, lp(Ui.MATCH, Ui.si(370f)).m(t = 26f))

        // inputs row
        val inputs = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; clipChildren = false }
        inputs.addView(Ui.text(this, 22f, 700, Ui.white(.8f)).apply { text = "Входи" }, lp(Ui.si(110f), Ui.WRAP))
        val labels = listOf("HDMI 1" to TvInput.HDMI1, "HDMI 2" to TvInput.HDMI2, "HDMI 3" to TvInput.HDMI3,
            "Телебачення" to TvInput.TV, "AV" to TvInput.AV)
        for ((label, input) in labels) {
            val pill = PillButton(this, label)
            pill.setOnClickListener { DomivkaApp.inputSwitcher.switchTo(input) }
            inputs.addView(pill, lp(Ui.WRAP, Ui.si(72f)).m(l = 20f))
        }
        col.addView(inputs, lp(Ui.MATCH, Ui.si(72f)).m(t = 26f))

        // apps
        col.addView(Ui.text(this, 22f, 700, Ui.white(.8f)).apply { text = "Застосунки" }, lp(Ui.WRAP, Ui.WRAP).m(t = 26f))
        grid = AppsGrid(this, ::openSlot, ::tileMenu)
        col.addView(grid, lp(Ui.MATCH, Ui.WRAP).m(t = 12f))
        return root
    }

    private fun buildHeader(): View {
        val header = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; clipChildren = false }
        homeIcon = ImageView(this).apply {
            setImageDrawable(getDrawable(R.drawable.ic_home))
            val p = Ui.si(13f)
            setPadding(p, p, p, p)
            background = GlassBackground.badge()
        }
        header.addView(homeIcon, lp(Ui.si(52f), Ui.si(52f)))
        header.addView(Ui.text(this, 28f, 800).apply { text = "Домівка"; spacing(-0.5f) }, lp(Ui.WRAP, Ui.WRAP).m(l = 14f))
        header.addView(View(this), lp(0, 1, 1f))
        // Network pill: shows the Wi-Fi name, OK opens the system Wi-Fi settings.
        val net = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(Ui.si(20f), 0, Ui.si(24f), 0)
            GlassBackground.bind(this, GlassBackground.badge())
            isClickable = true
            setOnClickListener { openWifiSettings() }
        }
        netIcon = ImageView(this).apply { setImageDrawable(getDrawable(R.drawable.ic_wifi)) }
        net.addView(netIcon, lp(Ui.si(26f), Ui.si(26f)))
        netText = Ui.text(this, 20f, 600, Ui.white(.85f)).apply {
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            maxWidth = Ui.si(360f)
        }
        net.addView(netText, lp(Ui.WRAP, Ui.WRAP).m(l = 12f))
        header.addView(net, lp(Ui.WRAP, Ui.si(52f)))
        header.addView(IconButton(this, R.drawable.ic_settings).apply { setOnClickListener { openSettings() } },
            lp(Ui.si(52f), Ui.si(52f)).m(l = 18f))
        return header
    }

    private fun applyAccent() {
        homeIcon.setColorFilter(Prefs.accent)
        clock.update()
        calendar.invalidate()
    }

    // ---- lifecycle -------------------------------------------------------------------------------------------

    override fun onResume() {
        super.onResume()
        aurora.animationEnabled = Prefs.animate
        aurora.resumed = true
        // Like the stock launcher: mute the TV source picture while the home screen is shown,
        // otherwise the vendor "no signal → power off" popup jumps over us.
        (DomivkaApp.inputSwitcher as? CvteInputSwitcher)?.onHomeVisible()
        applyAccent()
        grid.refresh()
        tick()
        clock.start()
        updateNet()

        val w = WeatherRepository.cached()
        weather.bind(w)
        val age = if (w == null) WEATHER_MAX_AGE else System.currentTimeMillis() - w.fetchedAt
        if (!Prefs.hasCity) handler.removeCallbacks(weatherTick)
        else if (age >= WEATHER_MAX_AGE || age < 0) weatherTick.run()
        else scheduleWeather(WEATHER_MAX_AGE - age)

        registerReceiver(timeReceiver, IntentFilter().apply {
            addAction(Intent.ACTION_TIME_TICK)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
            addAction(Intent.ACTION_DATE_CHANGED)
        })
        receiverRegistered = true
        registerNetwork()
        if (currentFocus == null) grid.tiles[0].requestFocus()
    }

    override fun onPause() {
        aurora.resumed = false
        clock.stop()
        (DomivkaApp.inputSwitcher as? CvteInputSwitcher)?.onHomeHidden()
        handler.removeCallbacks(weatherTick)
        if (receiverRegistered) {
            unregisterReceiver(timeReceiver)
            receiverRegistered = false
        }
        unregisterNetwork()
        super.onPause()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        // Home pressed while we are already on top (or coming back from AllApps/Settings): reset to the first tile.
        grid.tiles[0].requestFocus()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // Home screen: Back does nothing.
    }

    // ---- clock / weather / network ---------------------------------------------------------------------------

    private fun tick() {
        clock.update()
        calendar.update()
    }

    private fun refreshWeather() {
        if (!Prefs.hasCity) {
            weather.bind(null)
            return
        }
        WeatherRepository.refresh { w -> weather.bind(w ?: WeatherRepository.cached()) }
    }

    private fun scheduleWeather(delay: Long) {
        handler.removeCallbacks(weatherTick)
        handler.postDelayed(weatherTick, delay)
    }

    private fun updateNet() {
        val cm = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
        val caps = try { cm.getNetworkCapabilities(cm.activeNetwork) } catch (e: Exception) { null }
        val (text, alpha) = when {
            caps == null -> "Без мережі" to 0.4f
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> wifiName() to 1f
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet" to 1f
            else -> "Мережа" to 1f
        }
        netText.text = text
        netIcon.alpha = alpha
        netIcon.setColorFilter(Ui.white(.8f))
    }

    private fun wifiName(): String = try {
        @Suppress("DEPRECATION")
        val ssid = (applicationContext.getSystemService(WIFI_SERVICE) as android.net.wifi.WifiManager).connectionInfo?.ssid
        val name = ssid?.removeSurrounding("\"")
        if (name.isNullOrBlank() || name == "<unknown ssid>" || name == "0x") "Wi-Fi" else name
    } catch (e: Exception) { "Wi-Fi" }

    private fun openWifiSettings() {
        val candidates = listOf(
            // CVTE: opens "Configuración de la red" (mode + Wi-Fi network). ACTION_WIFI_SETTINGS shows the wired page.
            Intent("android.settings.cvte.NETWORK_SETTINGS"),
            Intent(android.provider.Settings.ACTION_WIFI_SETTINGS),
            Intent(android.provider.Settings.ACTION_WIRELESS_SETTINGS),
            Intent(android.provider.Settings.ACTION_SETTINGS),
        )
        for (i in candidates) {
            try {
                startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return
            } catch (e: Exception) { /* try the next one */ }
        }
        Toast.makeText(this, "Не вдалося відкрити налаштування Wi-Fi", Toast.LENGTH_SHORT).show()
    }

    private fun registerNetwork() {
        try {
            val cm = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
            val cb = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    handler.post { updateNet(); if (Prefs.hasCity && WeatherRepository.isStale()) refreshWeather() }
                }
                override fun onLost(network: Network) {
                    handler.post { updateNet() }
                }
            }
            cm.registerDefaultNetworkCallback(cb)
            netCallback = cb
        } catch (e: Exception) {
            netCallback = null
        }
    }

    private fun unregisterNetwork() {
        val cb = netCallback ?: return
        try {
            (getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager).unregisterNetworkCallback(cb)
        } catch (e: Exception) { /* already gone */ }
        netCallback = null
    }

    // ---- apps ------------------------------------------------------------------------------------------------

    private fun openSlot(i: Int) {
        val v = Prefs.slot(i)
        when {
            v == AppDefs.ALL -> startActivity(Intent(this, AllAppsActivity::class.java))
            v.isEmpty() -> pick(i)
            !launchPackage(this, v) -> Toast.makeText(this, "Не встановлено", Toast.LENGTH_SHORT).show()
        }
    }

    private fun tileMenu(i: Int) {
        val v = Prefs.slot(i)
        when {
            v == AppDefs.ALL -> {}
            v.isEmpty() -> pick(i)
            else -> Dialogs.choose(this, label(v), listOf("Замінити", "Прибрати")) {
                if (it == 0) pick(i) else {
                    Prefs.setSlot(i, "")
                    grid.refresh()
                }
            }
        }
    }

    private fun label(pkg: String): String = AppDefs.def(pkg)?.label ?: try {
        packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
    } catch (e: Exception) { pkg }

    private fun pick(slot: Int) {
        startActivityForResult(
            Intent(this, AllAppsActivity::class.java).putExtra(AllAppsActivity.EXTRA_PICK, true).putExtra(AllAppsActivity.EXTRA_SLOT, slot),
            REQ_PICK
        )
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_PICK && resultCode == RESULT_OK && data != null) {
            val pkg = data.getStringExtra(AllAppsActivity.EXTRA_PKG) ?: return
            val slot = data.getIntExtra(AllAppsActivity.EXTRA_SLOT, -1)
            if (slot in 0..11) {
                Prefs.setSlot(slot, pkg)
                grid.refresh()
            }
        }
    }

    /** Gear → settings hub; weather card → straight to the city search. */
    private fun openSettings(focus: String? = null) {
        if (focus == SettingsActivity.FOCUS_CITY)
            startActivity(Intent(this, SettingsActivity::class.java).putExtra(SettingsActivity.EXTRA_FOCUS, focus))
        else startActivity(Intent(this, SettingsHubActivity::class.java))
    }

    // ---- keys ------------------------------------------------------------------------------------------------

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_MENU) return true
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_MENU) {
            val slot = grid.slotOf(currentFocus)
            if (slot >= 0) tileMenu(slot) else openSettings()
            return true
        }
        return super.onKeyUp(keyCode, event)
    }

    private companion object {
        const val REQ_PICK = 1
        const val WEATHER_MAX_AGE = 30 * 60 * 1000L
    }
}
