package com.cloudbasepredictor.ui.screens.forecast.views

import com.cloudbasepredictor.domain.forecast.dryAdiabatTempC
import com.cloudbasepredictor.domain.forecast.mixingRatioTemperatureC
import com.cloudbasepredictor.domain.forecast.potentialTemperatureK
import com.cloudbasepredictor.domain.forecast.satMixingRatioGKg
import com.cloudbasepredictor.ui.screens.forecast.StuveForecastChartUiModel
import com.cloudbasepredictor.ui.screens.forecast.StuveProfilePoint
import com.cloudbasepredictor.ui.screens.forecast.interpolateProfileHeightMeters
import com.cloudbasepredictor.ui.screens.forecast.interpolateProfileTemperature
import com.cloudbasepredictor.ui.screens.forecast.pressureToApproxHeightMeters
import kotlin.math.abs
import kotlin.math.roundToInt

internal data class SkewTCursorState(
    val y: Float,
    val x: Float,
    val isPinned: Boolean = false,
)

internal data class CursorReadout(
    val pressureHpa: Float,
    val altitudeMeters: Int,
    val temperatureC: Float?,
    val dewpointC: Float?,
    val parcelTemperatureC: Float?,
    val guideTemperatureC: Float?,
    val guideDryThetaK: Float?,
    val guideMixingRatioGKg: Float?,
    val parcelSurfaceTemperatureC: Float?,
    val criticalSurfaceDewpointC: Float?,
    val windSpeedKmh: Float?,
    val windDirectionDeg: Float?,
)

internal fun buildCursorReadout(
    chart: StuveForecastChartUiModel,
    pressureHpa: Float,
    anchorTemperatureC: Float? = null,
    parcelPath: List<StuveProfilePoint> = chart.parcelAscentPath,
): CursorReadout {
    val clampedPressure = pressureHpa.coerceIn(
        chart.temperatureProfile.lastOrNull()?.pressureHpa ?: pressureHpa,
        chart.surfacePressureHpa,
    )
    val altitudeMeters = (
        interpolateProfileHeightMeters(chart.temperatureProfile, clampedPressure)
            ?: pressureToApproxHeightMeters(clampedPressure).toFloat()
        ).roundToInt()
    val nearestWind = chart.windBarbs.minByOrNull { abs(it.pressureHpa - clampedPressure) }
        ?.takeIf { abs(it.pressureHpa - clampedPressure) <= 60f }

    val envTemperatureC = interpolateProfileTemperature(chart.temperatureProfile, clampedPressure)
    // The guide (adiabat lines, parcel surface temperature) is derived from the tapped temperature
    // when the user selects a specific chart X position; otherwise fall back to the environmental
    // temperature at this level (backward-compatible behaviour).
    val guideTemperatureC = anchorTemperatureC ?: envTemperatureC

    return CursorReadout(
        pressureHpa = clampedPressure,
        altitudeMeters = altitudeMeters,
        temperatureC = envTemperatureC,
        dewpointC = interpolateProfileTemperature(chart.dewpointProfile, clampedPressure),
        parcelTemperatureC = interpolateProfileTemperature(parcelPath, clampedPressure),
        guideTemperatureC = guideTemperatureC,
        guideDryThetaK = guideTemperatureC?.let { temperature ->
            potentialTemperatureK(temperature, clampedPressure)
        },
        guideMixingRatioGKg = guideTemperatureC?.let { temperature ->
            satMixingRatioGKg(temperature, clampedPressure)
        },
        parcelSurfaceTemperatureC = guideTemperatureC?.let { temperature ->
            dryAdiabatTempC(
                potentialTemperatureK(temperature, clampedPressure),
                chart.surfacePressureHpa,
            )
        },
        criticalSurfaceDewpointC = guideTemperatureC?.let { temperature ->
            mixingRatioTemperatureC(
                satMixingRatioGKg(temperature, clampedPressure),
                chart.surfacePressureHpa,
            )
        },
        windSpeedKmh = nearestWind?.speedKmh,
        windDirectionDeg = nearestWind?.directionDeg,
    )
}
