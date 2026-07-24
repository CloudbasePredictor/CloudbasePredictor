package com.cloudbasepredictor.ui.screens.forecast

import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.sin

/**
 * A wind vector sampled at a specific altitude.
 *
 * The chart, its background, and the cursor readout all use this same representation so they
 * cannot silently apply different altitude sampling rules.
 */
internal data class WindSample(
    val altitudeKm: Float,
    val speedKmh: Float,
    val directionDeg: Float,
)

internal data class WindHourCluster(
    val startIndex: Int,
    val endIndexExclusive: Int,
    val representativeIndex: Int,
) {
    val centerColumn: Float
        get() = (startIndex + endIndexExclusive) / 2f
}

internal fun buildWindProfiles(
    cells: List<WindForecastCellUiModel>,
): Map<Int, List<WindSample>> {
    return cells
        .groupBy { it.hour }
        .mapValues { (_, hourCells) ->
            hourCells
                .sortedBy { it.altitudeKm }
                .distinctBy { it.altitudeKm.toBits() }
                .map { cell ->
                    WindSample(
                        altitudeKm = cell.altitudeKm,
                        speedKmh = cell.speedKmh,
                        directionDeg = cell.directionDeg,
                    )
                }
        }
        .filterValues { it.isNotEmpty() }
}

/**
 * Interpolates wind at [targetAltitudeKm] using vector components, preserving direction wrapping
 * through north. Values outside the available profile are clamped to its nearest end.
 */
internal fun interpolateWind(
    profile: List<WindSample>,
    targetAltitudeKm: Float,
): WindSample? {
    if (profile.isEmpty()) return null
    val first = profile.first()
    val last = profile.last()
    return when {
        targetAltitudeKm <= first.altitudeKm -> first.copy(altitudeKm = targetAltitudeKm)
        targetAltitudeKm >= last.altitudeKm -> last.copy(altitudeKm = targetAltitudeKm)
        else -> {
            var lowerIndex = 0
            while (
                lowerIndex < profile.lastIndex &&
                profile[lowerIndex + 1].altitudeKm <= targetAltitudeKm
            ) {
                lowerIndex++
            }
            val lower = profile[lowerIndex]
            val upper = profile[lowerIndex + 1]
            val span = upper.altitudeKm - lower.altitudeKm
            if (span <= 0f) {
                lower.copy(altitudeKm = targetAltitudeKm)
            } else {
                val fraction =
                    ((targetAltitudeKm - lower.altitudeKm) / span).coerceIn(0f, 1f)
                val (lowerU, lowerV) = windComponents(lower.speedKmh, lower.directionDeg)
                val (upperU, upperV) = windComponents(upper.speedKmh, upper.directionDeg)
                val u = lowerU + (upperU - lowerU) * fraction
                val v = lowerV + (upperV - lowerV) * fraction

                WindSample(
                    altitudeKm = targetAltitudeKm,
                    speedKmh = hypot(u, v),
                    directionDeg = ((atan2(u, v) * 180f / PI.toFloat()) + 360f) % 360f,
                )
            }
        }
    }
}

/**
 * Maps an X coordinate to its exact hourly column. Visual arrow clusters do not affect this
 * mapping: a three-hour cluster is still split into three equal interactive columns.
 */
internal fun windHourIndexAtX(
    x: Float,
    plotLeft: Float,
    columnWidth: Float,
    hourCount: Int,
): Int? {
    if (columnWidth <= 0f || hourCount <= 0) return null
    return floor((x - plotLeft) / columnWidth)
        .toInt()
        .coerceIn(0, hourCount - 1)
}

/**
 * Groups narrow visual columns while selecting the actual center hour as their representative.
 * No time averaging is performed.
 */
internal fun buildWindHourClusters(
    hourCount: Int,
    clusterSize: Int,
): List<WindHourCluster> {
    if (hourCount <= 0 || clusterSize <= 0) return emptyList()
    return buildList {
        var startIndex = 0
        while (startIndex < hourCount) {
            val endIndexExclusive = (startIndex + clusterSize).coerceAtMost(hourCount)
            add(
                WindHourCluster(
                    startIndex = startIndex,
                    endIndexExclusive = endIndexExclusive,
                    representativeIndex = startIndex + (endIndexExclusive - startIndex) / 2,
                ),
            )
            startIndex = endIndexExclusive
        }
    }
}

private fun windComponents(
    speedKmh: Float,
    directionDeg: Float,
): Pair<Float, Float> {
    val radians = directionDeg * PI.toFloat() / 180f
    return (speedKmh * sin(radians)) to (speedKmh * cos(radians))
}
