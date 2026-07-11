package com.cloudbasepredictor.model

data class WeatherCodePresentation(
    val label: String,
    val shortLabel: String,
)

object WeatherCode {
    fun present(code: Int): WeatherCodePresentation = when (code) {
        0 -> WeatherCodePresentation(label = "Clear sky", shortLabel = "Clear")
        1, 2, 3 -> WeatherCodePresentation(label = "Partly cloudy", shortLabel = "Cloudy")
        45, 48 -> WeatherCodePresentation(label = "Fog", shortLabel = "Fog")
        51, 53, 55, 56, 57 -> WeatherCodePresentation(label = "Drizzle", shortLabel = "Drizzle")
        61, 63, 65, 66, 67 -> WeatherCodePresentation(label = "Rain", shortLabel = "Rain")
        71, 73, 75, 77 -> WeatherCodePresentation(label = "Snow", shortLabel = "Snow")
        80, 81, 82 -> WeatherCodePresentation(label = "Rain showers", shortLabel = "Showers")
        85, 86 -> WeatherCodePresentation(label = "Snow showers", shortLabel = "Snow")
        95, 96, 99 -> WeatherCodePresentation(label = "Thunderstorm", shortLabel = "Storm")
        else -> WeatherCodePresentation(label = "Unknown weather", shortLabel = "Unknown")
    }
}
