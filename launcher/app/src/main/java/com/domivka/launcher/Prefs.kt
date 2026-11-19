package com.domivka.launcher

import android.content.Context
import android.content.SharedPreferences

object Prefs {
    private lateinit var sp: SharedPreferences

    fun init(ctx: Context) {
        sp = ctx.getSharedPreferences("domivka", Context.MODE_PRIVATE)
    }

    var accent: Int
        get() = sp.getInt("accent", Ui.ACCENT_DEFAULT)
        set(v) = sp.edit().putInt("accent", v).apply()

    /** Clock face: 0 = digital with seconds, 1 = digital, 2 = analog. */
    val CLOCK_FACES = arrayOf("цифровий із секундами", "цифровий", "стрілочний")

    var clockFace: Int
        get() = sp.getInt("clockFace", 0)
        set(v) = sp.edit().putInt("clockFace", v).apply()

    var animate: Boolean
        get() = sp.getBoolean("animate", true)
        set(v) = sp.edit().putBoolean("animate", v).apply()

    // Default city until the user picks another one in Settings (Open-Meteo geocoding: Київ).
    private const val DEFAULT_CITY = "Київ"
    private const val DEFAULT_LAT = "50.4501"
    private const val DEFAULT_LON = "30.5234"

    val cityName: String get() = sp.getString("city", DEFAULT_CITY) ?: DEFAULT_CITY
    val hasCity: Boolean get() = cityName.isNotEmpty()
    val lat: Double get() = sp.getString("lat", DEFAULT_LAT)?.toDoubleOrNull() ?: 0.0
    val lon: Double get() = sp.getString("lon", DEFAULT_LON)?.toDoubleOrNull() ?: 0.0

    fun setCity(name: String, lat: Double, lon: Double) {
        sp.edit().putString("city", name).putString("lat", lat.toString()).putString("lon", lon.toString())
            .remove("wjson").remove("wtime").remove("wkey").apply()
    }

    /** App slot: package name, AppDefs.ALL, or "" for an empty slot. */
    fun slot(i: Int): String = when (val v = sp.getString("slot$i", AppDefs.defaultSlots[i]) ?: "") {
        // EShare відкривав передачу файлів; трансляція екрана Android (Miracast) — це androidbox.
        "com.ecloud.eshare.server" -> "com.mediatek.androidbox"
        else -> v
    }
    fun setSlot(i: Int, v: String) = sp.edit().putString("slot$i", v).apply()

    // Weather cache
    val weatherJson: String? get() = sp.getString("wjson", null)
    val weatherTime: Long get() = sp.getLong("wtime", 0L)
    val weatherKey: String? get() = sp.getString("wkey", null)
    fun saveWeather(json: String, time: Long, key: String) {
        sp.edit().putString("wjson", json).putLong("wtime", time).putString("wkey", key).apply()
    }
}
