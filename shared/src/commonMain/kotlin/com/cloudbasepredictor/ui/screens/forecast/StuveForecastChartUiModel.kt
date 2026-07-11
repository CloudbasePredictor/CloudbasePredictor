@file:Suppress(
    "LongMethod",
    "LoopWithTooManyJumpStatements",
    "MagicNumber",
    "MaxLineLength",
    "ReturnCount",
)

package com.cloudbasepredictor.ui.screens.forecast

import com.cloudbasepredictor.domain.forecast.CclHourlyInput
import com.cloudbasepredictor.domain.forecast.CclHourlyResult
import com.cloudbasepredictor.domain.forecast.CclPressureLevel
import com.cloudbasepredictor.domain.forecast.ProfileLevel
import com.cloudbasepredictor.domain.forecast.SurfaceHeatingInput
import com.cloudbasepredictor.domain.forecast.analyzeCclHourly
import com.cloudbasepredictor.domain.forecast.dryAdiabatTempC
import com.cloudbasepredictor.domain.forecast.estimateSurfaceHeating
import com.cloudbasepredictor.domain.forecast.interpolateHeightKmAtPressure
import com.cloudbasepredictor.domain.forecast.moistAdiabatTempFromPointC
import com.cloudbasepredictor.domain.forecast.potentialTemperatureK
import com.cloudbasepredictor.domain.forecast.primaryCclResult
import com.cloudbasepredictor.domain.forecast.relativeHumidityFraction
import com.cloudbasepredictor.domain.forecast.satMixingRatioGKg
import com.cloudbasepredictor.util.toFixedDecimalString
import kotlin.math.abs
import kotlin.math.sin

/**
 * UI model for the Stüve / Skew-T forecast chart.
 *
 * The chart consumes prebuilt profiles, parcel data, and summary metrics so the
 * canvas layer only needs to handle transforms and drawing.
 */
data class StuveForecastChartUiModel(
    /** Pressure levels for the Y-axis, hPa (hectopascals), descending. */
    val pressureLevels: List<Float>,
    /** Environmental temperature sounding, °C at each pressure level. */
    val temperatureProfile: List<StuveProfilePoint>,
    /** Environmental dewpoint sounding, °C at each pressure level. */
    val dewpointProfile: List<StuveProfilePoint>,
    /** Parcel ascent path, °C, using the current heating model. */
    val parcelAscentPath: List<StuveProfilePoint>,
    /** Wind barbs drawn along the right side of the diagram. */
    val windBarbs: List<StuveWindBarb>,
    /** Convective Condensation Level pressure, hPa; null if not computed. */
    val cclPressureHpa: Float?,
    /** Convective temperature at the surface required to reach the CCL, °C. */
    val tconC: Float?,
    /** Hourly CCL results for surface, mixed-layer 50 hPa, and mixed-layer 100 hPa methods. */
    val cclResults: List<CclHourlyResult> = emptyList(),
    /** Moisture cue bands derived from temperature and dewpoint profiles. */
    val moistureBands: List<StuveMoistureBand> = emptyList(),
    /** Currently displayed hour of the day (local time, 6–22). */
    val selectedHour: Int,
    /** Station surface pressure, hPa. Used to set chart bottom. */
    val surfacePressureHpa: Float = 1050f,
)

data class StuveProfilePoint(
    /** Pressure level, hPa. */
    val pressureHpa: Float,
    /** Temperature at this pressure level, °C (degrees Celsius). */
    val temperatureC: Float,
    /** Height of this point in metres ASL, if available. */
    val heightMeters: Float? = null,
    /** True when this point comes from real backend model data (not interpolated). */
    val isRealData: Boolean = false,
)

data class StuveWindBarb(
    /** Pressure level, hPa. */
    val pressureHpa: Float,
    /** Wind speed, km/h. */
    val speedKmh: Float,
    /** Wind direction, degrees (meteorological: 0/360=N, 90=E — direction FROM). */
    val directionDeg: Float,
)

data class StuveMoistureBand(
    /** Lower-pressure edge of the band (higher altitude). */
    val topPressureHpa: Float,
    /** Higher-pressure edge of the band (lower altitude). */
    val bottomPressureHpa: Float,
    /** Average relative humidity as a fraction 0..1. */
    val relativeHumidityFraction: Float,
)

// --- Standard pressure levels used in the diagram ---
val STUVE_PRESSURE_LEVELS: List<Float> = listOf(
    1050f, 1000f, 950f, 900f, 850f, 800f, 750f,
    700f, 650f, 600f, 550f, 500f, 450f, 400f,
    350f, 300f, 250f,
)

// Approximate height in meters for standard pressure levels (ISA)
private val PRESSURE_TO_HEIGHT_MAP: Map<Float, Int> = mapOf(
    1050f to -300, 1000f to 111, 950f to 540, 900f to 988,
    850f to 1457, 800f to 1949, 750f to 2466, 700f to 3013,
    650f to 3591, 600f to 4206, 550f to 4865, 500f to 5574,
    450f to 6344, 400f to 7185, 350f to 8117, 300f to 9164,
    250f to 10363,
)

fun pressureToApproxHeightMeters(pressureHpa: Float): Int {
    val sorted = PRESSURE_TO_HEIGHT_MAP.entries.sortedByDescending { it.key }
    val lower = sorted.lastOrNull { it.key >= pressureHpa }
    val upper = sorted.firstOrNull { it.key < pressureHpa }
    if (lower == null) return sorted.last().value
    if (upper == null) return sorted.first().value
    val frac = (lower.key - pressureHpa) / (lower.key - upper.key)
    return (lower.value + frac * (upper.value - lower.value)).toInt()
}

// --- Reference line sets ---
val STUVE_DRY_ADIABAT_THETAS_K: List<Float> = listOf(
    253f, 263f, 273f, 283f, 293f, 303f, 313f, 323f, 333f, 343f, 353f, 363f, 373f,
)

val STUVE_MOIST_ADIABAT_THETAS_K: List<Float> = listOf(
    263f, 273f, 278f, 283f, 288f, 293f, 298f, 303f, 313f, 323f,
)

val STUVE_MIXING_RATIO_VALUES_GKG: List<Float> = listOf(
    0.4f, 1f, 2f, 3f, 5f, 8f, 12f, 16f, 20f, 28f,
)

// --- Placeholder builder ---
fun buildPlaceholderStuveChart(
    hour: Int = 12,
    dayIndex: Int = 0,
): StuveForecastChartUiModel {
    val surfacePressure = 950f
    val availablePressures = listOf(surfacePressure) + STUVE_PRESSURE_LEVELS.filter { it < surfacePressure }
    val dayPhase = dayIndex * 0.4f
    val solarFactor = (1f - abs(hour - 13f) / 8f).coerceIn(0.1f, 1f)

    val temperatureProfile = availablePressures.map { pressure ->
        val heightMeters = pressureToApproxHeightMeters(pressure).toFloat()
        val heightKm = heightMeters / 1000f
        val baseTemp = 23f - 6.4f * heightKm + solarFactor * 3f
        val perturbation = sin(heightKm * 1.6f + dayPhase) * 1.8f
        val inversion = if (heightKm in 1.1f..1.7f) 1.8f * solarFactor else 0f
        StuveProfilePoint(
            pressureHpa = pressure,
            temperatureC = baseTemp + perturbation + inversion,
            heightMeters = heightMeters,
        )
    }

    val dewpointProfile = temperatureProfile.map { point ->
        val heightKm = (point.heightMeters ?: 0f) / 1000f
        val dewpointDepression = 6f + heightKm * 2.2f - solarFactor * 1.6f
        StuveProfilePoint(
            pressureHpa = point.pressureHpa,
            temperatureC = point.temperatureC - dewpointDepression.coerceAtLeast(2.5f),
            heightMeters = point.heightMeters,
        )
    }

    val surfaceHeightKm = (temperatureProfile.firstOrNull()?.heightMeters ?: 0f) / 1000f
    val profileLevels = temperatureProfile.map { temperaturePoint ->
        ProfileLevel(
            pressureHpa = temperaturePoint.pressureHpa,
            temperatureC = temperaturePoint.temperatureC,
            dewPointC = dewpointProfile.firstOrNull { it.pressureHpa == temperaturePoint.pressureHpa }?.temperatureC,
            heightKm = (
                temperaturePoint.heightMeters
                    ?: pressureToApproxHeightMeters(temperaturePoint.pressureHpa).toFloat()
                ) / 1000f,
        )
    }

    val heatingInput = SurfaceHeatingInput(
        hourOfDay = hour,
        shortwaveRadiationWm2 = 800f * solarFactor,
        cloudCoverLowPercent = 25f,
        cloudCoverMidPercent = 10f,
        cloudCoverHighPercent = 5f,
        precipitationMm = 0f,
        isDay = hour in 6..20,
    )
    val surfaceHeatingC = estimateSurfaceHeating(heatingInput)
    val cclResults = analyzeCclHourly(
        CclHourlyInput(
            time = "placeholderT${hour.toDouble().toFixedDecimalString(0).padStart(2, '0')}:00",
            surfaceTemperatureC = temperatureProfile.first().temperatureC,
            surfaceDewPointC = dewpointProfile.first().temperatureC,
            surfacePressureHpa = surfacePressure,
            surfaceElevationM = surfaceHeightKm * 1000f,
            pressureLevels = profileLevels.drop(1).map { level ->
                CclPressureLevel(
                    pressureHpa = level.pressureHpa,
                    temperatureC = level.temperatureC,
                    dewPointC = level.dewPointC,
                    heightMslM = level.heightKm * 1000f,
                    isSynthetic = level.isSynthetic,
                )
            },
        ),
    )
    val primaryCcl = cclResults.primaryCclResult()

    val parcelPressures = buildRenderableParcelPressures(
        surfacePressureHpa = surfacePressure,
        profilePressures = availablePressures,
    )
    val parcelPath = buildParcelAscentPath(
        pressures = parcelPressures,
        profile = profileLevels,
        surfaceTemperatureC = temperatureProfile.first().temperatureC,
        surfaceDewPointC = dewpointProfile.first().temperatureC,
        surfacePressureHpa = surfacePressure,
        surfaceHeatingC = surfaceHeatingC,
    )

    val windBarbs = availablePressures
        .filter { it in 300f..1000f }
        .map { pressure ->
            val heightKm = pressureToApproxHeightMeters(pressure) / 1000f
            StuveWindBarb(
                pressureHpa = pressure,
                speedKmh = 12f + heightKm * 9f + sin(heightKm + dayPhase) * 4f,
                directionDeg = 230f + sin(heightKm * 0.85f + dayPhase) * 55f,
            )
        }

    return StuveForecastChartUiModel(
        pressureLevels = STUVE_PRESSURE_LEVELS,
        temperatureProfile = temperatureProfile,
        dewpointProfile = dewpointProfile,
        parcelAscentPath = parcelPath,
        windBarbs = windBarbs,
        cclPressureHpa = primaryCcl?.cclPressureHpa,
        tconC = primaryCcl?.convectiveTemperatureC,
        cclResults = cclResults,
        moistureBands = buildMoistureBands(temperatureProfile, dewpointProfile),
        selectedHour = hour,
        surfacePressureHpa = surfacePressure,
    )
}

fun interpolateProfileTemperature(
    profile: List<StuveProfilePoint>,
    pressureHpa: Float,
): Float? = interpolateProfileValue(profile, pressureHpa) { it.temperatureC }

fun interpolateProfileHeightMeters(
    profile: List<StuveProfilePoint>,
    pressureHpa: Float,
): Float? = interpolateProfileValue(profile, pressureHpa) { point ->
    point.heightMeters ?: pressureToApproxHeightMeters(point.pressureHpa).toFloat()
}

private fun interpolateProfileValue(
    profile: List<StuveProfilePoint>,
    pressureHpa: Float,
    selector: (StuveProfilePoint) -> Float,
): Float? {
    val sorted = profile.sortedByDescending { it.pressureHpa }
    if (sorted.isEmpty()) return null
    sorted.firstOrNull { it.pressureHpa == pressureHpa }?.let { return selector(it) }
    for (i in 0 until sorted.size - 1) {
        val lower = sorted[i]
        val upper = sorted[i + 1]
        if (pressureHpa <= lower.pressureHpa && pressureHpa >= upper.pressureHpa) {
            val fraction = (lower.pressureHpa - pressureHpa) / (lower.pressureHpa - upper.pressureHpa)
            return selector(lower) + fraction * (selector(upper) - selector(lower))
        }
    }
    return null
}

fun buildRenderableParcelPressures(
    surfacePressureHpa: Float,
    profilePressures: List<Float>,
): List<Float> {
    return (listOf(surfacePressureHpa) + STUVE_PRESSURE_LEVELS + profilePressures)
        .filter { it <= surfacePressureHpa + 0.5f }
        .distinct()
        .sortedDescending()
}

internal fun buildParcelAscentPath(
    pressures: List<Float>,
    profile: List<ProfileLevel>,
    surfaceTemperatureC: Float,
    surfaceDewPointC: Float,
    surfacePressureHpa: Float,
    surfaceHeatingC: Float,
): List<StuveProfilePoint> {
    val parcelThetaK = potentialTemperatureK(surfaceTemperatureC + surfaceHeatingC, surfacePressureHpa)
    val surfaceMixingRatio = satMixingRatioGKg(surfaceDewPointC, surfacePressureHpa)

    var reachedLcl = false
    var lclTemperatureC: Float? = null
    var lclPressureHpa: Float? = null

    return pressures.map { pressure ->
        val heightMeters = interpolateHeightKmAtPressure(profile, pressure)?.times(1000f)
            ?: pressureToApproxHeightMeters(pressure).toFloat()
        val temperatureC = if (!reachedLcl) {
            val dryTemp = dryAdiabatTempC(parcelThetaK, pressure)
            val satMixingRatio = satMixingRatioGKg(dryTemp, pressure)
            if (satMixingRatio <= surfaceMixingRatio) {
                reachedLcl = true
                lclTemperatureC = dryTemp
                lclPressureHpa = pressure
            }
            dryTemp
        } else {
            moistAdiabatTempFromPointC(
                startTemperatureC = lclTemperatureC ?: dryAdiabatTempC(parcelThetaK, pressure),
                startPressureHpa = lclPressureHpa ?: pressure,
                targetPressureHpa = pressure,
            )
        }
        StuveProfilePoint(
            pressureHpa = pressure,
            temperatureC = temperatureC,
            heightMeters = heightMeters,
        )
    }
}

internal fun buildMoistureBands(
    temperatureProfile: List<StuveProfilePoint>,
    dewpointProfile: List<StuveProfilePoint>,
): List<StuveMoistureBand> {
    val sortedTemps = temperatureProfile.sortedByDescending { it.pressureHpa }
    if (sortedTemps.size < 2) return emptyList()

    return buildList {
        for (index in 0 until sortedTemps.size - 1) {
            val lower = sortedTemps[index]
            val upper = sortedTemps[index + 1]
            val dewLower = interpolateProfileTemperature(dewpointProfile, lower.pressureHpa) ?: continue
            val dewUpper = interpolateProfileTemperature(dewpointProfile, upper.pressureHpa) ?: continue
            val averageRelativeHumidity = (
                relativeHumidityFraction(lower.temperatureC, dewLower) +
                    relativeHumidityFraction(upper.temperatureC, dewUpper)
                ) / 2f
            add(
                StuveMoistureBand(
                    topPressureHpa = upper.pressureHpa,
                    bottomPressureHpa = lower.pressureHpa,
                    relativeHumidityFraction = averageRelativeHumidity,
                ),
            )
        }
    }
}
