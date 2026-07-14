package com.cloudbasepredictor.model

data class WeatherCodePresentation(
    val label: String,
    val shortLabel: String,
)

enum class WeatherCondition {
    CLEAR_SKY,
    PARTLY_CLOUDY,
    FOG,
    DRIZZLE,
    RAIN,
    SNOW,
    RAIN_SHOWERS,
    SNOW_SHOWERS,
    THUNDERSTORM,
    UNKNOWN,
}

object WeatherCode {
    fun condition(code: Int): WeatherCondition = when (code) {
        0 -> WeatherCondition.CLEAR_SKY
        1, 2, 3 -> WeatherCondition.PARTLY_CLOUDY
        45, 48 -> WeatherCondition.FOG
        51, 53, 55, 56, 57 -> WeatherCondition.DRIZZLE
        61, 63, 65, 66, 67 -> WeatherCondition.RAIN
        71, 73, 75, 77 -> WeatherCondition.SNOW
        80, 81, 82 -> WeatherCondition.RAIN_SHOWERS
        85, 86 -> WeatherCondition.SNOW_SHOWERS
        95, 96, 99 -> WeatherCondition.THUNDERSTORM
        else -> WeatherCondition.UNKNOWN
    }

    /** Deterministic English fallback used by shared previews and non-localized callers. */
    fun present(code: Int): WeatherCodePresentation = when (condition(code)) {
        WeatherCondition.CLEAR_SKY -> WeatherCodePresentation(label = "Clear sky", shortLabel = "Clear")
        WeatherCondition.PARTLY_CLOUDY -> WeatherCodePresentation(label = "Partly cloudy", shortLabel = "Cloudy")
        WeatherCondition.FOG -> WeatherCodePresentation(label = "Fog", shortLabel = "Fog")
        WeatherCondition.DRIZZLE -> WeatherCodePresentation(label = "Drizzle", shortLabel = "Drizzle")
        WeatherCondition.RAIN -> WeatherCodePresentation(label = "Rain", shortLabel = "Rain")
        WeatherCondition.SNOW -> WeatherCodePresentation(label = "Snow", shortLabel = "Snow")
        WeatherCondition.RAIN_SHOWERS -> WeatherCodePresentation(label = "Rain showers", shortLabel = "Showers")
        WeatherCondition.SNOW_SHOWERS -> WeatherCodePresentation(label = "Snow showers", shortLabel = "Snow")
        WeatherCondition.THUNDERSTORM -> WeatherCodePresentation(label = "Thunderstorm", shortLabel = "Storm")
        WeatherCondition.UNKNOWN -> WeatherCodePresentation(label = "Unknown weather", shortLabel = "Unknown")
    }
}
