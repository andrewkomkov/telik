package io.github.henry753951.airplayremote.weather

import android.content.Context
import android.util.Log
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
import java.util.TimeZone
import kotlin.math.roundToInt

/** The weather now (006 US9). */
data class WeatherNow(val temperatureC: Double, val code: Int, val isDay: Boolean) {
    /** A symbol for the WMO weather code of Open-Meteo. */
    val symbol: String
        get() = when (code) {
            0 -> if (isDay) "☀" else "☾"
            1, 2 -> if (isDay) "🌤" else "☁"
            3 -> "☁"
            45, 48 -> "🌫"
            in 51..67, in 80..82 -> "🌧"
            in 71..77, 85, 86 -> "❄"
            in 95..99 -> "⛈"
            else -> "☁"
        }

    fun text(fahrenheit: Boolean): String {
        val t = if (fahrenheit) temperatureC * 9 / 5 + 32 else temperatureC
        return "$symbol ${t.roundToInt()}°"
    }
}

data class City(val name: String, val latitude: Double, val longitude: Double)

/** One day of the forecast (006 T059). [date] is the local date, as `2026-11-17`. */
data class DayForecast(val date: String, val code: Int, val maxC: Double, val minC: Double) {
    val symbol: String get() = WeatherNow(maxC, code, isDay = true).symbol

    fun range(fahrenheit: Boolean): String {
        fun t(c: Double) = (if (fahrenheit) c * 9 / 5 + 32 else c).roundToInt()
        return "${t(maxC)}° / ${t(minC)}°"
    }
}

/** The weather now and the days ahead, from one request. */
data class WeatherReport(val now: WeatherNow, val days: List<DayForecast>)

/**
 * Open-Meteo, with no key and no account (FR-014). Telik sends only the city name for the geocoding
 * and the coordinates for the weather, at most once in 30 minutes.
 */
object Weather {
    private const val TAG = "Weather"
    private const val MAX_AGE_MS = 30 * 60 * 1000L
    private const val FILE = "weather"

    @Volatile private var cached: Pair<Long, WeatherReport>? = null

    /** `Europe/Belgrade` → `Belgrade`, `America/New_York` → `New York`. */
    fun zoneCity(zoneId: String): String? = zoneId.substringAfterLast('/', "").replace('_', ' ').ifBlank { null }

    fun parseCity(json: String): City? {
        val r = JSONObject(json).optJSONArray("results")?.optJSONObject(0) ?: return null
        if (!r.has("latitude") || !r.has("longitude")) return null
        return City(r.optString("name"), r.getDouble("latitude"), r.getDouble("longitude"))
    }

    /** The `daily` part: parallel arrays of dates, codes, highs and lows. Days with a gap stay out. */
    fun parseDays(json: String): List<DayForecast> {
        val d = JSONObject(json).optJSONObject("daily") ?: return emptyList()
        val dates = d.optJSONArray("time") ?: return emptyList()
        val codes = d.optJSONArray("weather_code")
        val max = d.optJSONArray("temperature_2m_max")
        val min = d.optJSONArray("temperature_2m_min")
        return (0 until dates.length()).mapNotNull { i ->
            if (max == null || min == null || max.isNull(i) || min.isNull(i)) return@mapNotNull null
            DayForecast(dates.optString(i), codes?.optInt(i, -1) ?: -1, max.getDouble(i), min.getDouble(i))
        }
    }

    fun parseNow(json: String): WeatherNow? {
        val c = JSONObject(json).optJSONObject("current") ?: return null
        if (!c.has("temperature_2m")) return null
        return WeatherNow(c.getDouble("temperature_2m"), c.optInt("weather_code", -1), c.optInt("is_day", 1) == 1)
    }

    /** Fahrenheit only for the regions that use it. */
    fun fahrenheit(locale: Locale = Locale.getDefault()): Boolean = locale.country in setOf("US", "LR", "MM", "BS", "BZ", "KY", "PW")

    /** The city of the settings (`Prefs.WEATHER_CITY`), or null for the time zone city. */
    fun cityName(context: Context): String? =
        context.getSharedPreferences(io.github.henry753951.airplayremote.Prefs.NAME, Context.MODE_PRIVATE)
            .getString(io.github.henry753951.airplayremote.Prefs.WEATHER_CITY.key, "")?.trim()?.ifBlank { null }

    /** Call after the city changes: the next [now] asks Open-Meteo again. */
    fun forget() { cached = null }

    /** The weather now, from the cache when it is younger than 30 minutes. Null without network. */
    fun now(context: Context): WeatherNow? = report(context)?.now

    /** The weather now and 5 days, from the cache when it is younger than 30 minutes. */
    fun report(context: Context): WeatherReport? {
        cached?.let { (at, w) -> if (System.currentTimeMillis() - at < MAX_AGE_MS) return w }
        return runCatching {
            val city = city(context)
            if (city == null) {
                Log.i(TAG, "no city")
                return null
            }
            val url = "https://api.open-meteo.com/v1/forecast?latitude=${city.latitude}&longitude=${city.longitude}" +
                "&current=temperature_2m,weather_code,is_day" +
                "&daily=weather_code,temperature_2m_max,temperature_2m_min&forecast_days=5&timezone=auto"
            val answer = get(url)
            val now = parseNow(answer)
            Log.i(TAG, if (now == null) "no weather in the answer" else "weather code ${now.code}")
            now?.let { WeatherReport(it, parseDays(answer)) }?.also { cached = System.currentTimeMillis() to it }
        }.onFailure { Log.w(TAG, "weather failed: ${it.javaClass.simpleName}") }.getOrNull()
    }

    private fun city(context: Context): City? {
        val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        val name = cityName(context) ?: zoneCity(TimeZone.getDefault().id) ?: return null
        if (prefs.getString("geo_for", null) == name && prefs.contains("lat")) {
            return City(name, prefs.getFloat("lat", 0f).toDouble(), prefs.getFloat("lon", 0f).toDouble())
        }
        val lang = Locale.getDefault().language
        val found = parseCity(get("https://geocoding-api.open-meteo.com/v1/search?name=${URLEncoder.encode(name, "UTF-8")}&count=1&language=$lang"))
            ?: return null
        prefs.edit().putString("geo_for", name).putFloat("lat", found.latitude.toFloat()).putFloat("lon", found.longitude.toFloat()).apply()
        return found
    }

    private fun get(url: String): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 6_000
        conn.readTimeout = 8_000
        try {
            check(conn.responseCode == 200) { "HTTP ${conn.responseCode}" }
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }
}
