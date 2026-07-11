package com.cloudbasepredictor.data.remote

import com.cloudbasepredictor.model.DailyForecast
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class OpenMeteoForecastResponse(
    val daily: OpenMeteoDailyResponse,
)

@Serializable
data class OpenMeteoDailyResponse(
    val time: List<String>,
    @SerialName("temperature_2m_max")
    val temperatureMaxCelsius: List<Double?>,
    @SerialName("temperature_2m_min")
    val temperatureMinCelsius: List<Double?>,
    @SerialName("weather_code")
    val weatherCodes: List<Int?>,
)

fun OpenMeteoForecastResponse.toDomainModels(): List<DailyForecast> {
    val itemsCount = daily.time.size
    require(itemsCount == daily.temperatureMaxCelsius.size) {
        "Open-Meteo response contains mismatched max temperature data."
    }
    require(itemsCount == daily.temperatureMinCelsius.size) {
        "Open-Meteo response contains mismatched min temperature data."
    }
    require(itemsCount == daily.weatherCodes.size) {
        "Open-Meteo response contains mismatched weather code data."
    }

    return (0 until itemsCount).mapNotNull { index ->
        val maxTemp = daily.temperatureMaxCelsius[index] ?: return@mapNotNull null
        val minTemp = daily.temperatureMinCelsius[index] ?: return@mapNotNull null
        val weatherCode = daily.weatherCodes[index] ?: return@mapNotNull null
        DailyForecast(
            date = daily.time[index],
            maxTemperatureCelsius = maxTemp,
            minTemperatureCelsius = minTemp,
            weatherCode = weatherCode,
        )
    }
}
