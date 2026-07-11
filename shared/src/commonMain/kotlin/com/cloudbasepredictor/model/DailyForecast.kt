package com.cloudbasepredictor.model

import kotlinx.serialization.Serializable

@Serializable
data class DailyForecast(
    val date: String,
    val maxTemperatureCelsius: Double,
    val minTemperatureCelsius: Double,
    val weatherCode: Int,
)
