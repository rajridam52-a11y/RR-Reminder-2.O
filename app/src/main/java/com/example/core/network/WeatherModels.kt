package com.example.core.network

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

@JsonClass(generateAdapter = true)
data class OpenMeteoForecastResponse(
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val timezone: String = "",
    val current: CurrentWeatherDto? = null,
    val hourly: HourlyWeatherDto? = null,
    val daily: DailyWeatherDto? = null,
)

@JsonClass(generateAdapter = true)
data class CurrentWeatherDto(
    val time: String = "",
    @Json(name = "temperature_2m") val temperature2m: Double = 0.0,
    @Json(name = "relative_humidity_2m") val relativeHumidity2m: Int = 0,
    @Json(name = "apparent_temperature") val apparentTemperature: Double = 0.0,
    val precipitation: Double = 0.0,
    @Json(name = "weather_code") val weatherCode: Int = 0,
    @Json(name = "surface_pressure") val surfacePressure: Double = 0.0,
    @Json(name = "wind_speed_10m") val windSpeed10m: Double = 0.0,
    @Json(name = "wind_direction_10m") val windDirection10m: Int = 0,
    @Json(name = "uv_index") val uvIndex: Double = 0.0,
    val visibility: Double = 0.0,
)

@JsonClass(generateAdapter = true)
data class HourlyWeatherDto(
    val time: List<String> = emptyList(),
    @Json(name = "temperature_2m") val temperature2m: List<Double> = emptyList(),
    @Json(name = "precipitation_probability") val precipitationProbability: List<Int> = emptyList(),
    @Json(name = "weather_code") val weatherCode: List<Int> = emptyList(),
    @Json(name = "uv_index") val uvIndex: List<Double> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class DailyWeatherDto(
    val time: List<String> = emptyList(),
    @Json(name = "weather_code") val weatherCode: List<Int> = emptyList(),
    @Json(name = "temperature_2m_max") val temperatureMax: List<Double> = emptyList(),
    @Json(name = "temperature_2m_min") val temperatureMin: List<Double> = emptyList(),
    val sunrise: List<String> = emptyList(),
    val sunset: List<String> = emptyList(),
    @Json(name = "uv_index_max") val uvIndexMax: List<Double> = emptyList(),
    @Json(name = "precipitation_sum") val precipitationSum: List<Double> = emptyList(),
    @Json(name = "precipitation_probability_max") val precipitationProbabilityMax: List<Int> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class GeocodingResponse(
    val results: List<GeocodingResultDto>? = null,
)

@JsonClass(generateAdapter = true)
data class GeocodingResultDto(
    val id: Long = 0,
    val name: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val country: String? = null,
    val admin1: String? = null,
    val timezone: String? = null,
)

@JsonClass(generateAdapter = true)
data class HourlyForecastItem(
    val timeIso: String,
    val tempC: Double,
    val rainProbPercent: Int,
    val weatherCode: Int,
    val uvIndex: Double = 0.0,
)

@JsonClass(generateAdapter = true)
data class DailyForecastItem(
    val dateIso: String,
    val maxTempC: Double,
    val minTempC: Double,
    val rainProbPercent: Int,
    val weatherCode: Int,
    val uvIndexMax: Double = 0.0,
    val precipitationSumMm: Double = 0.0,
)

object WeatherUnits {
    fun celsiusToFahrenheit(c: Double): Double = (c * 9.0 / 5.0) + 32.0

    fun kmhToMph(kmh: Double): Double = kmh * 0.621371

    fun formatTemp(tempC: Double, useFahrenheit: Boolean): String {
        val value = if (useFahrenheit) celsiusToFahrenheit(tempC) else tempC
        val unit = if (useFahrenheit) "°F" else "°C"
        return "${value.roundToInt()}$unit"
    }

    fun formatTempShort(tempC: Double, useFahrenheit: Boolean): String {
        val value = if (useFahrenheit) celsiusToFahrenheit(tempC) else tempC
        return "${value.roundToInt()}°"
    }

    fun formatWind(kmh: Double, useMph: Boolean, directionDeg: Int? = null): String {
        val value = if (useMph) kmhToMph(kmh).roundToInt() else kmh.roundToInt()
        val unit = if (useMph) "mph" else "km/h"
        val dir = directionDeg?.let { " ${degreesToCompass(it)}" }.orEmpty()
        return "$value $unit$dir"
    }

    fun formatVisibility(meters: Double, useMph: Boolean): String {
        if (meters <= 0.0) return "--"
        return if (useMph) {
            val miles = meters / 1609.34
            String.format(Locale.US, "%.1f mi", miles)
        } else {
            val km = meters / 1000.0
            String.format(Locale.US, "%.1f km", km)
        }
    }

    fun degreesToCompass(degrees: Int): String {
        val normalized = ((degrees % 360) + 360) % 360
        val dirs = listOf("N", "NNE", "NE", "ENE", "E", "ESE", "SE", "SSE", "S", "SSW", "SW", "WSW", "W", "WNW", "NW", "NNW")
        val index = (((normalized + 11.25) / 22.5).toInt()) % 16
        return dirs[index]
    }

    fun resolveZone(timezoneId: String?): ZoneId {
        if (timezoneId.isNullOrBlank()) return ZoneId.systemDefault()
        return runCatching { ZoneId.of(timezoneId) }.getOrDefault(ZoneId.systemDefault())
    }

    fun formatIsoTime(isoDateTime: String, use24Hour: Boolean): String {
        if (isoDateTime.isBlank()) return "--"
        val parsed = runCatching { LocalDateTime.parse(isoDateTime) }.getOrNull()
            ?: return isoDateTime.substringAfter("T", isoDateTime)
        val pattern = if (use24Hour) "HH:mm" else "h:mm a"
        return parsed.format(DateTimeFormatter.ofPattern(pattern, Locale.getDefault()))
    }

    fun sliceHourlyFromNow(
        items: List<HourlyForecastItem>,
        timezoneId: String?,
        nowMillis: Long = System.currentTimeMillis(),
        limit: Int = 24,
    ): List<HourlyForecastItem> {
        if (items.isEmpty()) return emptyList()
        val zone = resolveZone(timezoneId)
        val currentHourStart = ZonedDateTime.ofInstant(java.time.Instant.ofEpochMilli(nowMillis), zone)
            .toLocalDateTime()
            .withMinute(0)
            .withSecond(0)
            .withNano(0)
        val filtered = items.filter { item ->
            val ldt = runCatching { LocalDateTime.parse(item.timeIso) }.getOrNull()
            ldt != null && !ldt.isBefore(currentHourStart)
        }
        return (filtered.ifEmpty { items }).take(limit)
    }

    fun describeUv(uv: Double): String = when {
        uv < 3.0 -> "Low"
        uv < 6.0 -> "Moderate"
        uv < 8.0 -> "High"
        uv < 11.0 -> "Very High"
        else -> "Extreme"
    }
}

object WeatherCodeInterpreter {
    fun describe(code: Int): String = when (code) {
        0 -> "Clear Sky"
        1 -> "Mainly Clear"
        2 -> "Partly Cloudy"
        3 -> "Overcast"
        45, 48 -> "Foggy"
        51, 53, 55 -> "Drizzle"
        56, 57 -> "Freezing Drizzle"
        61, 63 -> "Rain"
        65 -> "Heavy Rain"
        66, 67 -> "Freezing Rain"
        71, 73 -> "Snow"
        75, 77 -> "Heavy Snow"
        80, 81 -> "Rain Showers"
        82 -> "Violent Showers"
        85, 86 -> "Snow Showers"
        95 -> "Thunderstorm"
        96, 99 -> "Severe Thunderstorm"
        else -> "Variable Conditions"
    }

    fun severeAlertFor(code: Int, windKmh: Double): String? = when {
        code in listOf(95, 96, 99) -> "Weather Advisory: Thunderstorm conditions detected in forecast."
        code in listOf(65, 66, 67, 82) -> "Rain Advisory: Heavy or freezing precipitation expected."
        code in listOf(75, 86) -> "Winter Advisory: Heavy snow conditions detected."
        windKmh >= 50.0 -> "Wind Advisory: Strong gusts (${windKmh.toInt()} km/h) reported."
        else -> null
    }
}
