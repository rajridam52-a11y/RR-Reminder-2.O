package com.example.core.network

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

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
    @Json(name = "weather_code") val weatherCode: Int = 0,
    @Json(name = "wind_speed_10m") val windSpeed10m: Double = 0.0,
)

@JsonClass(generateAdapter = true)
data class HourlyWeatherDto(
    val time: List<String> = emptyList(),
    @Json(name = "temperature_2m") val temperature2m: List<Double> = emptyList(),
    @Json(name = "precipitation_probability") val precipitationProbability: List<Int> = emptyList(),
    @Json(name = "weather_code") val weatherCode: List<Int> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class DailyWeatherDto(
    val time: List<String> = emptyList(),
    @Json(name = "weather_code") val weatherCode: List<Int> = emptyList(),
    @Json(name = "temperature_2m_max") val temperatureMax: List<Double> = emptyList(),
    @Json(name = "temperature_2m_min") val temperatureMin: List<Double> = emptyList(),
    val sunrise: List<String> = emptyList(),
    val sunset: List<String> = emptyList(),
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
)

@JsonClass(generateAdapter = true)
data class DailyForecastItem(
    val dateIso: String,
    val maxTempC: Double,
    val minTempC: Double,
    val rainProbPercent: Int,
    val weatherCode: Int,
)

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
