package com.cloudbasepredictor.data.remote

import com.cloudbasepredictor.model.DailyForecast
import kotlinx.serialization.Serializable
import kotlin.math.pow

/**
 * Converts the hourly response into [HourlyForecastData] — an intermediate
 * domain model that the UI layer transforms into chart models.
 *
 * The conversion groups raw parallel arrays (one value per hourly time step)
 * into per-hour, per-pressure-level data points.
 */
fun OpenMeteoHourlyForecastResponse.toHourlyForecastData(): HourlyForecastData {
    val hourly = this.hourly
    val times = hourly.time  // ISO-8601 local, e.g. "2026-04-12T14:00"

    val tempByPressure = hourly.temperaturesByPressure().toMap()
    val dewByPressure = hourly.dewPointsByPressure().toMap()
    val relativeHumidityByPressure = hourly.relativeHumidityByPressure().toMap()
    val cloudCoverByPressure = hourly.cloudCoverByPressure().toMap()
    val windSpeedByPressure = hourly.windSpeedsByPressure().toMap()
    val windDirByPressure = hourly.windDirectionsByPressure().toMap()
    val geoHeightByPressure = hourly.geopotentialHeightsByPressure().toMap()

    val hourlyPoints = times.mapIndexed { i, isoTime ->
        val dateStr = isoTime.substringBefore("T")   // "2026-04-12"
        val hourStr = isoTime.substringAfter("T").substringBefore(":")
        val hour = hourStr.toIntOrNull() ?: 0

        val pressureLevelData = STANDARD_PRESSURE_LEVELS.mapNotNull { pHpa ->
            val temp = tempByPressure[pHpa]?.getOrNull(i)
            val dew = dewByPressure[pHpa]?.getOrNull(i)
            val relativeHumidity = relativeHumidityByPressure[pHpa]?.getOrNull(i)
            val cloudCover = cloudCoverByPressure[pHpa]?.getOrNull(i)
            val ws = windSpeedByPressure[pHpa]?.getOrNull(i)
            val wd = windDirByPressure[pHpa]?.getOrNull(i)
            val gh = geoHeightByPressure[pHpa]?.getOrNull(i)
            if (temp == null) return@mapNotNull null
            PressureLevelPoint(
                pressureHpa = pHpa,
                temperatureC = temp,
                dewPointC = dew,
                windSpeedKmh = ws,
                windDirectionDeg = wd,
                geopotentialHeightM = gh,
                relativeHumidityPercent = relativeHumidity,
                cloudCoverPercent = cloudCover,
            )
        }
        val completedPressureLevelData = completePressureLevels(pressureLevelData)

        HourlyPoint(
            date = dateStr,
            hour = hour,
            temperature2mC = hourly.temperature2m?.getOrNull(i),
            dewPoint2mC = hourly.dewPoint2m?.getOrNull(i),
            cloudCoverLowPercent = hourly.cloudCoverLow?.getOrNull(i),
            cloudCoverMidPercent = hourly.cloudCoverMid?.getOrNull(i),
            cloudCoverHighPercent = hourly.cloudCoverHigh?.getOrNull(i),
            precipitationMm = hourly.precipitation?.getOrNull(i),
            precipitationProbabilityPercent = hourly.precipitationProbability?.getOrNull(i),
            windSpeed10mKmh = hourly.windSpeed10m?.getOrNull(i),
            windDirection10mDeg = hourly.windDirection10m?.getOrNull(i),
            capeJKg = hourly.cape?.getOrNull(i),
            freezingLevelHeightM = hourly.freezingLevelHeight?.getOrNull(i),
            surfacePressureHpa = hourly.surfacePressure?.getOrNull(i),
            shortwaveRadiationWm2 = hourly.shortwaveRadiation?.getOrNull(i),
            sunshineDurationS = hourly.sunshineDuration?.getOrNull(i),
            isDay = hourly.isDay?.getOrNull(i),
            pressureLevels = completedPressureLevelData,
            liftedIndexC = hourly.liftedIndex?.getOrNull(i),
            convectiveInhibitionJKg = hourly.convectiveInhibition?.getOrNull(i),
            boundaryLayerHeightM = hourly.boundaryLayerHeight?.getOrNull(i),
        )
    }

    val dailyForecasts = daily?.let {
        OpenMeteoForecastResponse(daily = it).toDomainModels()
    } ?: emptyList()

    return HourlyForecastData(
        latitude = latitude,
        longitude = longitude,
        elevation = elevation,
        hourlyPoints = hourlyPoints,
        dailyForecasts = dailyForecasts,
        utcOffsetSeconds = utcOffsetSeconds,
        timezone = timezone,
    )
}

/**
 * Intermediate domain model holding all forecast data needed for chart construction.
 */
@Serializable
data class HourlyForecastData(
    val latitude: Double,
    val longitude: Double,
    val elevation: Double?,
    val hourlyPoints: List<HourlyPoint>,
    val dailyForecasts: List<DailyForecast>,
    val utcOffsetSeconds: Int = 0,
    val timezone: String? = null,
) {
    /** Group hourly points by their date string (yyyy-MM-dd). */
    fun pointsByDate(): Map<String, List<HourlyPoint>> =
        hourlyPoints.groupBy { it.date }
}

/**
 * One hour of forecast data combining surface and pressure-level variables.
 */
@Serializable
data class HourlyPoint(
    /** Date in yyyy-MM-dd format. */
    val date: String,
    /** Local hour (0–23). */
    val hour: Int,
    /** Surface temperature at 2 m, °C. */
    val temperature2mC: Double?,
    /** Surface dewpoint at 2 m, °C. */
    val dewPoint2mC: Double?,
    /** Low cloud cover (0–3 km AGL), percent. */
    val cloudCoverLowPercent: Double?,
    /** Mid cloud cover (3–8 km AGL), percent. */
    val cloudCoverMidPercent: Double?,
    /** High cloud cover (>8 km AGL), percent. */
    val cloudCoverHighPercent: Double?,
    /** Precipitation in preceding hour, mm. */
    val precipitationMm: Double?,
    /** Precipitation probability, percent. */
    val precipitationProbabilityPercent: Double?,
    /** Surface wind speed at 10 m, km/h. */
    val windSpeed10mKmh: Double?,
    /** Surface wind direction at 10 m, degrees (meteorological). */
    val windDirection10mDeg: Double?,
    /** Convective Available Potential Energy, J/kg. */
    val capeJKg: Double?,
    /** Freezing level (0 °C isotherm) height, metres above sea level. */
    val freezingLevelHeightM: Double?,
    /** Surface pressure, hPa. Null if not available from model. */
    val surfacePressureHpa: Double? = null,
    /** Shortwave solar radiation (preceding hour mean), W/m². Null if not available. */
    val shortwaveRadiationWm2: Double? = null,
    /** Sunshine duration in the preceding hour, seconds (0–3600). Null if not available. */
    val sunshineDurationS: Double? = null,
    /** 1 if daytime, 0 if night. Null if not available. */
    val isDay: Double? = null,
    /** Data at each requested pressure level. */
    val pressureLevels: List<PressureLevelPoint>,
    /** Lifted index, °C. Negative values indicate increasing moist-convection potential. */
    val liftedIndexC: Double? = null,
    /** Model-supplied convective inhibition, J/kg. */
    val convectiveInhibitionJKg: Double? = null,
    /** Model-supplied boundary-layer height, metres above ground. */
    val boundaryLayerHeightM: Double? = null,
)

/**
 * Atmospheric data at a single pressure level for one hour.
 */
@Serializable
data class PressureLevelPoint(
    /** Pressure level, hPa. */
    val pressureHpa: Int,
    /** Temperature, °C. */
    val temperatureC: Double,
    /** Dewpoint temperature, °C. Null if not available. */
    val dewPointC: Double?,
    /** Wind speed, km/h. */
    val windSpeedKmh: Double?,
    /** Wind direction, degrees (meteorological). */
    val windDirectionDeg: Double?,
    /** Geopotential height, metres above sea level. */
    val geopotentialHeightM: Double?,
    /** Relative humidity at this pressure level, percent. Null if unavailable. */
    val relativeHumidityPercent: Double? = null,
    /** Cloud cover at this pressure level, percent. Null if unavailable. */
    val cloudCoverPercent: Double? = null,
    /** True when this level was synthesized by interpolation/extrapolation, not returned directly. */
    val isSynthetic: Boolean = false,
)

private val STANDARD_PRESSURE_LEVELS = listOf(
    1000, 975, 950, 925, 900, 875, 850, 800, 750, 700,
    650, 600, 550, 500, 450, 400, 350, 300, 250,
)

private const val MIN_DEWPOINT_SPREAD_C = 1.5
private const val MAX_SYNTHETIC_WIND_SPEED_KMH = 180.0

private fun completePressureLevels(
    existingLevels: List<PressureLevelPoint>,
): List<PressureLevelPoint> {
    if (existingLevels.isEmpty()) return emptyList()

    val levelsByPressure = existingLevels.associateBy { it.pressureHpa }.toMutableMap()

    STANDARD_PRESSURE_LEVELS.forEach { pressure ->
        if (levelsByPressure[pressure] == null) {
            synthesizePressureLevel(pressure, levelsByPressure.values.toList())?.let { synthesized ->
                levelsByPressure[pressure] = synthesized
            }
        }
    }

    return levelsByPressure.values.sortedByDescending { it.pressureHpa }
}

private fun synthesizePressureLevel(
    targetPressureHpa: Int,
    sourceLevels: List<PressureLevelPoint>,
): PressureLevelPoint? {
    if (sourceLevels.isEmpty()) return null

    val targetHeightMeters = approxHeightForPressureHpa(targetPressureHpa)
    val temperatureC = extrapolateField(
        targetHeightMeters = targetHeightMeters,
        sourceLevels = sourceLevels,
    ) { level ->
        level.temperatureC
    } ?: return null
    val dewPointC = extrapolateField(
        targetHeightMeters = targetHeightMeters,
        sourceLevels = sourceLevels,
    ) { level ->
        level.dewPointC
    }?.coerceAtMost(temperatureC - MIN_DEWPOINT_SPREAD_C)
    val windSpeedKmh = extrapolateField(
        targetHeightMeters = targetHeightMeters,
        sourceLevels = sourceLevels,
    ) { level ->
        level.windSpeedKmh
    }?.coerceIn(0.0, MAX_SYNTHETIC_WIND_SPEED_KMH)
    val windDirectionDeg = extrapolateDirection(
        targetHeightMeters = targetHeightMeters,
        sourceLevels = sourceLevels,
    )
    val geopotentialHeightM = synthesizeGeopotentialHeight(targetPressureHpa, sourceLevels)

    return PressureLevelPoint(
        pressureHpa = targetPressureHpa,
        temperatureC = temperatureC,
        dewPointC = dewPointC,
        windSpeedKmh = windSpeedKmh,
        windDirectionDeg = windDirectionDeg,
        geopotentialHeightM = geopotentialHeightM,
        isSynthetic = true,
    )
}

private fun extrapolateField(
    targetHeightMeters: Double,
    sourceLevels: List<PressureLevelPoint>,
    valueSelector: (PressureLevelPoint) -> Double?,
): Double? {
    val samples = sourceLevels
        .mapNotNull { level ->
            valueSelector(level)?.let { value ->
                AtmosphericSample(
                    heightMeters = level.geopotentialHeightM
                        ?: approxHeightForPressureHpa(level.pressureHpa),
                    value = value,
                )
            }
        }
        .sortedBy { it.heightMeters }

    if (samples.size < 2) return samples.firstOrNull()?.value

    val lowerIndex = samples.indexOfLast { it.heightMeters <= targetHeightMeters }
    val upperIndex = samples.indexOfFirst { it.heightMeters >= targetHeightMeters }
    if (lowerIndex >= 0 && upperIndex >= 0 && lowerIndex != upperIndex) {
        val lower = samples[lowerIndex]
        val upper = samples[upperIndex]
        return interpolateLinear(
            x = targetHeightMeters,
            x0 = lower.heightMeters,
            y0 = lower.value,
            x1 = upper.heightMeters,
            y1 = upper.value,
        )
    }

    val regressionSamples = if (targetHeightMeters > samples.last().heightMeters) {
        samples.takeLast(minOf(samples.size, 4))
    } else {
        samples.take(minOf(samples.size, 4))
    }
    return regressLinear(targetHeightMeters, regressionSamples)
}

private fun extrapolateDirection(
    targetHeightMeters: Double,
    sourceLevels: List<PressureLevelPoint>,
): Double? {
    val directionSamples = sourceLevels
        .mapNotNull { level ->
            level.windDirectionDeg?.let { direction ->
                AtmosphericSample(
                    heightMeters = level.geopotentialHeightM
                        ?: approxHeightForPressureHpa(level.pressureHpa),
                    value = direction,
                )
            }
        }
        .sortedBy { it.heightMeters }

    if (directionSamples.size < 2) return directionSamples.firstOrNull()?.value

    val unwrappedValues = unwrapAngles(directionSamples.map { it.value })
    val regressionSamples = directionSamples.indices.map { index ->
        AtmosphericSample(
            heightMeters = directionSamples[index].heightMeters,
            value = unwrappedValues[index],
        )
    }

    val lowerIndex = regressionSamples.indexOfLast { it.heightMeters <= targetHeightMeters }
    val upperIndex = regressionSamples.indexOfFirst { it.heightMeters >= targetHeightMeters }
    val interpolated = if (lowerIndex >= 0 && upperIndex >= 0 && lowerIndex != upperIndex) {
        val lower = regressionSamples[lowerIndex]
        val upper = regressionSamples[upperIndex]
        interpolateLinear(
            x = targetHeightMeters,
            x0 = lower.heightMeters,
            y0 = lower.value,
            x1 = upper.heightMeters,
            y1 = upper.value,
        )
    } else {
        val fitSamples = if (targetHeightMeters > regressionSamples.last().heightMeters) {
            regressionSamples.takeLast(minOf(regressionSamples.size, 4))
        } else {
            regressionSamples.take(minOf(regressionSamples.size, 4))
        }
        regressLinear(targetHeightMeters, fitSamples)
    } ?: return null

    return normalizeAngle(interpolated)
}

private fun synthesizeGeopotentialHeight(
    targetPressureHpa: Int,
    sourceLevels: List<PressureLevelPoint>,
): Double {
    val heightOffsetMeters = sourceLevels
        .sortedBy { it.pressureHpa }
        .mapNotNull { level ->
            level.geopotentialHeightM?.minus(approxHeightForPressureHpa(level.pressureHpa))
        }
        .takeLast(4)
        .average()
        .takeIf { !it.isNaN() }
        ?: 0.0

    return approxHeightForPressureHpa(targetPressureHpa) + heightOffsetMeters
}

private fun approxHeightForPressureHpa(pressureHpa: Int): Double {
    return 44330.0 * (1.0 - (pressureHpa / 1013.25).pow(0.1903))
}

private fun interpolateLinear(
    x: Double,
    x0: Double,
    y0: Double,
    x1: Double,
    y1: Double,
): Double {
    if (kotlin.math.abs(x1 - x0) < 1e-6) return y0
    val fraction = (x - x0) / (x1 - x0)
    return y0 + fraction * (y1 - y0)
}

private fun regressLinear(
    targetX: Double,
    samples: List<AtmosphericSample>,
): Double? {
    if (samples.isEmpty()) return null
    if (samples.size == 1) return samples.first().value

    val meanX = samples.map { it.heightMeters }.average()
    val meanY = samples.map { it.value }.average()
    val denominator = samples.sumOf { (it.heightMeters - meanX) * (it.heightMeters - meanX) }
    if (kotlin.math.abs(denominator) < 1e-6) return samples.last().value

    val slope = samples.sumOf { (it.heightMeters - meanX) * (it.value - meanY) } / denominator
    return meanY + slope * (targetX - meanX)
}

private fun unwrapAngles(values: List<Double>): List<Double> {
    if (values.isEmpty()) return emptyList()

    val result = mutableListOf(values.first())
    values.drop(1).forEach { rawValue ->
        var adjusted = rawValue
        val previous = result.last()
        while (adjusted - previous > 180.0) adjusted -= 360.0
        while (previous - adjusted > 180.0) adjusted += 360.0
        result += adjusted
    }
    return result
}

private fun normalizeAngle(angleDeg: Double): Double {
    var normalized = angleDeg % 360.0
    if (normalized < 0.0) normalized += 360.0
    return normalized
}

private data class AtmosphericSample(
    val heightMeters: Double,
    val value: Double,
)
