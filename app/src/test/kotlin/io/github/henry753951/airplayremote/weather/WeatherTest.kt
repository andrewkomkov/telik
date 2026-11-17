package io.github.henry753951.airplayremote.weather

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class WeatherTest {
    @Test fun zoneCity() {
        assertEquals("Belgrade", Weather.zoneCity("Europe/Belgrade"))
        assertEquals("New York", Weather.zoneCity("America/New_York"))
        assertEquals("Buenos Aires", Weather.zoneCity("America/Argentina/Buenos_Aires"))
        assertNull(Weather.zoneCity("UTC"))
    }

    @Test fun parseCity() {
        val json = """{"results":[{"id":1,"name":"Нови-Сад","latitude":45.25,"longitude":19.84}]}"""
        assertEquals(City("Нови-Сад", 45.25, 19.84), Weather.parseCity(json))
        assertNull(Weather.parseCity("""{"generationtime_ms":0.1}"""))
    }

    @Test fun parseNow() {
        val json = """{"current":{"time":"2026-11-17T12:00","temperature_2m":16.3,"weather_code":0,"is_day":1}}"""
        val w = Weather.parseNow(json)!!
        assertEquals(16.3, w.temperatureC, 0.001)
        assertEquals("☀ 16°", w.text(fahrenheit = false))
        assertEquals("☀ 61°", w.text(fahrenheit = true))
        assertNull(Weather.parseNow("""{"current":{}}"""))
    }

    @Test fun symbols() {
        assertEquals("☾", WeatherNow(5.0, 0, isDay = false).symbol)
        assertEquals("🌧", WeatherNow(5.0, 61, isDay = true).symbol)
        assertEquals("❄", WeatherNow(-2.0, 73, isDay = true).symbol)
        assertEquals("⛈", WeatherNow(20.0, 95, isDay = true).symbol)
        assertEquals("-2°", WeatherNow(-2.4, 3, true).text(false).substringAfter(' '))
    }

    @Test fun fahrenheitOnlyWhereUsed() {
        assertTrue(Weather.fahrenheit(Locale.US))
        assertFalse(Weather.fahrenheit(Locale.Builder().setLanguage("ru").setRegion("RS").build()))
    }
}
