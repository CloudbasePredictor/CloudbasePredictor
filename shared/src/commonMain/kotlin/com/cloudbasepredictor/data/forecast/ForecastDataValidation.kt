@file:Suppress("MatchingDeclarationName", "ReturnCount")

package com.cloudbasepredictor.data.forecast

import com.cloudbasepredictor.data.remote.HourlyForecastData
import com.cloudbasepredictor.data.remote.HourlyPoint
import kotlin.math.abs

/** Signals that a selected forecast day cannot supply the shared chart renderers. */
class IncompleteForecastDataException : IllegalArgumentException("Forecast data is incomplete.")

/**
 * Checks every offered daytime hour inside the requested window before a forecast enters a cache.
 * A fallback model may legitimately return fewer daily rows than the requested horizon.
 * Sparse schedules are valid, but a present hour must supply its required surface inputs.
 */
fun HourlyForecastData.hasRequiredForecastWindow(forecastDays: Int): Boolean {
    if (forecastDays <= 0 || dailyForecasts.isEmpty()) return false
    val pointsByDate = pointsByDate()
    return dailyForecasts.take(forecastDays).all { day ->
        val daytimePoints = pointsByDate[day.date].orEmpty()
            .filter { it.hour in FIRST_FORECAST_HOUR..LAST_FORECAST_HOUR }
        daytimePoints.isNotEmpty() &&
            daytimePoints.all { it.temperature2mC != null && it.dewPoint2mC != null } &&
            daytimePoints.any { it.hasRenderableWindInputs() }
    }
}

/** Checks the same selected-date and hourly requirements used by ready-state creation. */
fun HourlyForecastData.hasRequiredForecastInputs(
    dayIndex: Int,
    stuveHour: Int,
): Boolean = validatedSelection(dayIndex, stuveHour) != null

internal data class ValidatedForecastSelection(
    val hourlyDayIndex: Int,
    val stuveHour: Int,
)

internal fun HourlyForecastData.validatedSelection(
    dayIndex: Int,
    stuveHour: Int,
): ValidatedForecastSelection? {
    val selectedDate = dailyForecasts.getOrNull(dayIndex)?.date ?: return null
    val pointsByDate = pointsByDate()
    val hourlyDayIndex = pointsByDate.keys.sorted().indexOf(selectedDate)
    if (hourlyDayIndex < 0) return null
    val daytimePoints = pointsByDate[selectedDate].orEmpty()
        .filter { it.hour in FIRST_FORECAST_HOUR..LAST_FORECAST_HOUR }
    if (daytimePoints.isEmpty()) return null

    val boundedHour = stuveHour.coerceIn(FIRST_FORECAST_HOUR, LAST_FORECAST_HOUR)
    val stuvePoint = daytimePoints.firstOrNull { it.hour == boundedHour }
        ?: daytimePoints.minByOrNull { point -> abs(point.hour - boundedHour) }
        ?: return null
    // This surface pair also supplies the thermic renderer's required daytime inputs.
    if (stuvePoint.temperature2mC == null || stuvePoint.dewPoint2mC == null) return null
    if (daytimePoints.none { point -> point.hasRenderableWindInputs() }) return null

    return ValidatedForecastSelection(hourlyDayIndex = hourlyDayIndex, stuveHour = stuvePoint.hour)
}

private fun HourlyPoint.hasRenderableWindInputs(): Boolean {
    val hasSurfaceWind = windSpeed10mKmh != null && windDirection10mDeg != null
    val hasPressureLevelWind = pressureLevels.any { level ->
        !level.isSynthetic &&
            level.geopotentialHeightM != null &&
            level.windSpeedKmh != null &&
            level.windDirectionDeg != null
    }
    return hasSurfaceWind || hasPressureLevelWind
}

private const val FIRST_FORECAST_HOUR = 6
private const val LAST_FORECAST_HOUR = 22
