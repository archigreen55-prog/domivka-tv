package com.domivka.launcher

import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Calendar
import java.util.GregorianCalendar
import java.util.Locale
import java.util.concurrent.Executors
import kotlin.math.roundToInt

class DayForecast(val dow: Int, val code: Int, val max: Int, val min: Int)

class Weather(
    val temp: Int, val code: Int, val wind: Int, val max: Int, val min: Int,
    val days: List<DayForecast>, val fetchedAt: Long,
)

class City(val name: String, val detail: String, val lat: Double, val lon: Double)

/** Open-Meteo via HttpURLConnection + org.json. Last good JSON is cached in Prefs and shown offline. */
object WeatherRepository {
    private const val MAX_AGE = 30 * 60 * 1000L
    private val io = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    @Volatile private var loading = false

    private fun key() = "${Prefs.lat},${Prefs.lon}"

    fun cached(): Weather? {
        val json = Prefs.weatherJson ?: return null
        if (Prefs.weatherKey != key()) return null
        return try { parse(json, Prefs.weatherTime) } catch (e: Exception) { null }
    }

    fun isStale(): Boolean = cached()?.let { System.currentTimeMillis() - it.fetchedAt > MAX_AGE } ?: true

    /** Fetches fresh data in the background; [done] runs on the main thread with the new data, or null on failure. */
    fun refresh(done: (Weather?) -> Unit) {
        if (!Prefs.hasCity || loading) return
        loading = true
        val lat = Prefs.lat
        val lon = Prefs.lon
        val k = key()
        io.execute {
            var result: Weather? = null
            try {
                val url = "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f".format(Locale.US, lat, lon) +
                    "&current=temperature_2m,apparent_temperature,weather_code,wind_speed_10m" +
                    "&daily=weather_code,temperature_2m_max,temperature_2m_min" +
                    "&timezone=auto&wind_speed_unit=ms&forecast_days=6"
                val json = get(url)
                val now = System.currentTimeMillis()
                result = parse(json, now)
                if (k == key()) Prefs.saveWeather(json, now, k)
            } catch (e: Exception) {
                result = null
            }
            main.post {
                loading = false
                done(if (k == key()) result else null)
            }
        }
    }

    fun searchCity(query: String, done: (List<City>?) -> Unit) {
        io.execute {
            val list: List<City>? = try {
                val url = "https://geocoding-api.open-meteo.com/v1/search?name=" +
                    URLEncoder.encode(query, "UTF-8") + "&count=5&language=uk&format=json"
                val arr = JSONObject(get(url)).optJSONArray("results")
                val out = ArrayList<City>()
                if (arr != null) for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val detail = listOf(o.optString("admin1"), o.optString("country")).filter { it.isNotEmpty() }.joinToString(", ")
                    out.add(City(o.getString("name"), detail, o.getDouble("latitude"), o.getDouble("longitude")))
                }
                out
            } catch (e: Exception) {
                null
            }
            main.post { done(list) }
        }
    }

    private fun get(url: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.connectTimeout = 10_000
            c.readTimeout = 10_000
            c.setRequestProperty("Accept", "application/json")
            if (c.responseCode != 200) throw java.io.IOException("HTTP ${c.responseCode}")
            return c.inputStream.bufferedReader().use { it.readText() }
        } finally {
            c.disconnect()
        }
    }

    private fun parse(json: String, time: Long): Weather {
        val o = JSONObject(json)
        val cur = o.getJSONObject("current")
        val d = o.getJSONObject("daily")
        val dates = d.getJSONArray("time")
        val codes = d.getJSONArray("weather_code")
        val maxs = d.getJSONArray("temperature_2m_max")
        val mins = d.getJSONArray("temperature_2m_min")
        val days = ArrayList<DayForecast>()
        for (i in 1 until minOf(dates.length(), 6)) {   // index 0 is today; tiles show the next days
            val p = dates.getString(i).split("-")
            val dow = GregorianCalendar(p[0].toInt(), p[1].toInt() - 1, p[2].toInt()).get(Calendar.DAY_OF_WEEK)
            days.add(DayForecast(dow, codes.getInt(i), maxs.getDouble(i).roundToInt(), mins.getDouble(i).roundToInt()))
        }
        return Weather(
            cur.getDouble("temperature_2m").roundToInt(), cur.getInt("weather_code"),
            cur.getDouble("wind_speed_10m").roundToInt(),
            maxs.getDouble(0).roundToInt(), mins.getDouble(0).roundToInt(), days, time
        )
    }

    // ---- WMO code mapping -------------------------------------------------------------------------------------

    fun describe(code: Int) = when (code) {
        0 -> "Ясно"
        1 -> "Переважно ясно"
        2 -> "Мінлива хмарність"
        3 -> "Хмарно"
        45 -> "Туман"
        48 -> "Паморозний туман"
        51, 53, 55 -> "Мряка"
        56, 57 -> "Крижана мряка"
        61 -> "Невеликий дощ"
        63 -> "Дощ"
        65 -> "Сильний дощ"
        66, 67 -> "Крижаний дощ"
        71 -> "Невеликий сніг"
        73 -> "Сніг"
        75 -> "Сильний сніг"
        77 -> "Снігові зерна"
        80 -> "Короткочасний дощ"
        81 -> "Злива"
        82 -> "Сильна злива"
        85, 86 -> "Снігопад"
        95 -> "Гроза"
        96, 99 -> "Гроза з градом"
        else -> "Мінлива хмарність"
    }

    fun icon(code: Int) = when (code) {
        0, 1 -> R.drawable.w_sun
        2 -> R.drawable.w_partly
        3 -> R.drawable.w_cloud
        45, 48 -> R.drawable.w_fog
        51, 53, 55, 56, 57, 61, 63, 65, 66, 67, 80, 81, 82 -> R.drawable.w_rain
        71, 73, 75, 77, 85, 86 -> R.drawable.w_snow
        95, 96, 99 -> R.drawable.w_thunder
        else -> R.drawable.w_partly
    }
}
