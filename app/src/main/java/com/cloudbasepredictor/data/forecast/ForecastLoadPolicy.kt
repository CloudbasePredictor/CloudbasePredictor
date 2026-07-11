package com.cloudbasepredictor.data.forecast

import com.cloudbasepredictor.model.DEFAULT_FORECAST_DAYS
import kotlin.math.max
import kotlin.math.min

internal const val INITIAL_FORECAST_DAYS = 5
internal const val FORECAST_DAY_BATCH_SIZE = 2
internal const val MAX_FORECAST_DAYS = DEFAULT_FORECAST_DAYS

internal fun requestedForecastDaysForDayIndex(
    dayIndex: Int,
    initialForecastDays: Int = INITIAL_FORECAST_DAYS,
    forecastDayBatchSize: Int = FORECAST_DAY_BATCH_SIZE,
    maxForecastDays: Int = MAX_FORECAST_DAYS,
): Int {
    val normalizedDayIndex = dayIndex.coerceAtLeast(0)
    val daysNeeded = max(initialForecastDays, normalizedDayIndex + 1)
    val remainder = daysNeeded % forecastDayBatchSize
    val roundedDays = if (remainder == 0) {
        daysNeeded
    } else {
        daysNeeded + (forecastDayBatchSize - remainder)
    }
    return roundedDays.coerceAtMost(maxForecastDays)
}

internal fun exposedForecastDayCount(
    loadedForecastDays: Int,
    selectedDayIndex: Int,
    initialForecastDays: Int = INITIAL_FORECAST_DAYS,
    forecastDayBatchSize: Int = FORECAST_DAY_BATCH_SIZE,
    maxForecastDays: Int = MAX_FORECAST_DAYS,
): Int {
    val normalizedLoadedDays = loadedForecastDays.coerceIn(0, maxForecastDays)
    val selectionWindow = requestedForecastDaysForDayIndex(
        dayIndex = selectedDayIndex,
        initialForecastDays = initialForecastDays,
        forecastDayBatchSize = forecastDayBatchSize,
        maxForecastDays = maxForecastDays,
    )
    val interactiveWindow = requestedForecastDaysForDayIndex(
        dayIndex = normalizedLoadedDays,
        initialForecastDays = initialForecastDays,
        forecastDayBatchSize = forecastDayBatchSize,
        maxForecastDays = maxForecastDays,
    )

    return max(normalizedLoadedDays, max(selectionWindow, interactiveWindow))
        .coerceIn(min(initialForecastDays, maxForecastDays), maxForecastDays)
}
