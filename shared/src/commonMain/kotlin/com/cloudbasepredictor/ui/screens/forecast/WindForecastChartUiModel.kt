@file:Suppress("MaxLineLength")

package com.cloudbasepredictor.ui.screens.forecast

import com.cloudbasepredictor.domain.forecast.CclHourlyResult
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/**
 * UI model for the wind forecast chart.
 *
 * All altitudes are in km ASL (above sea level).
 * Each cell holds wind speed (km/h) and direction (degrees, meteorological convention:
 * 0/360 = N, 90 = E, 180 = S, 270 = W — the direction the wind is coming FROM).
 */
data class WindForecastChartUiModel(
    /** Local hours of day (e.g. 6..22). */
    val hours: List<Int>,
    /** Sorted altitude center points, km ASL. */
    val altitudeBandsKm: List<Float>,
    /** Altitude bands with center/bottom/top boundaries for display. */
    val altitudeBands: List<WindAltitudeBand> = emptyList(),
    /** Grid cells with wind speed and direction per hour × altitude. */
    val cells: List<WindForecastCellUiModel>,
    /** Freezing level per hour (0 °C isotherm), km ASL. */
    val freezingLevelKm: List<WindLevelMarker> = emptyList(),
    /** Convective Condensation Level per hour, km ASL. */
    val cclKm: List<WindLevelMarker> = emptyList(),
    /** Hourly CCL results for all available methods and hours. */
    val cclResults: List<CclHourlyResult> = emptyList(),
)

/** Band boundaries around a data level for wind chart display. */
data class WindAltitudeBand(
    val centerKm: Float,
    val bottomKm: Float,
    val topKm: Float,
)

data class WindLevelMarker(
    val hour: Int,
    val altitudeKm: Float,
)

data class WindForecastCellUiModel(
    /** Local hour of day (0–23). */
    val hour: Int,
    /** Altitude, km ASL. */
    val altitudeKm: Float,
    /** Wind speed, km/h (kilometres per hour). */
    val speedKmh: Float,
    /** Wind direction, degrees (meteorological: 0/360=N, 90=E, 180=S, 270=W — FROM). */
    val directionDeg: Float,
)

fun buildPlaceholderWindForecastChart(
    dayIndex: Int = 0,
    minAltitudeKm: Float = 0.4f,
    maxAltitudeKm: Float = 4f,
    altitudeStepKm: Float = 0.25f,
): WindForecastChartUiModel {
    val hours = (6..22).toList()
    val altitudes = buildList {
        var alt = minAltitudeKm
        while (alt <= maxAltitudeKm + 0.001f) {
            add(alt)
            alt += altitudeStepKm
        }
    }
    val dayPhase = dayIndex * 0.5f

    val cells = buildList {
        hours.forEach { hour ->
            altitudes.forEach { alt ->
                val timeNorm = (hour - 6f) / 16f
                val altNorm = (alt - minAltitudeKm) / (maxAltitudeKm - minAltitudeKm)
                val solarPeak = (1f - abs(hour - 14f) / 8f).coerceIn(0f, 1f)

                // Wind speed increases with altitude and has some time variation
                val baseSpeed = 8f + altNorm * 35f
                val timeVariation = sin(timeNorm * PI.toFloat() * 1.3f + dayPhase) * 8f
                val speed = (baseSpeed + timeVariation + solarPeak * 5f).coerceAtLeast(2f)

                // Direction veers with altitude (Ekman spiral effect)
                val baseDirection = 240f + dayPhase * 20f
                val veer = altNorm * 45f
                val timeShift = sin(timeNorm * PI.toFloat() + dayPhase) * 15f
                val direction = (baseDirection + veer + timeShift) % 360f

                add(
                    WindForecastCellUiModel(
                        hour = hour,
                        altitudeKm = alt,
                        speedKmh = speed,
                        directionDeg = direction,
                    ),
                )
            }
        }
    }

    val bands = altitudes.mapIndexed { index, center ->
        val bottom = if (index > 0) (altitudes[index - 1] + center) / 2f else center - altitudeStepKm / 2f
        val top = if (index < altitudes.lastIndex) {
            (center + altitudes[index + 1]) / 2f
        } else {
            center + altitudeStepKm / 2f
        }
        WindAltitudeBand(centerKm = center, bottomKm = bottom, topKm = top)
    }

    return WindForecastChartUiModel(
        hours = hours,
        altitudeBandsKm = altitudes,
        altitudeBands = bands,
        cells = cells,
    )
}
