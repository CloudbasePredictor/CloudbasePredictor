@file:Suppress("MatchingDeclarationName")

package com.cloudbasepredictor.ui.screens.forecast

import kotlin.math.abs
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.ceil
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
    val isSurface10m: Boolean = false,
    val isInterpolated: Boolean = false,
)

internal fun buildWindProfiles(
    cells: List<WindForecastCellUiModel>,
): Map<Int, List<WindSample>> {
    return cells
        .groupBy { it.hour }
        .mapValues { (_, hourCells) ->
            hourCells
                .filter {
                    it.altitudeKm.isFinite() && it.speedKmh.isFinite() &&
                        it.speedKmh >= 0f && it.directionDeg.isFinite()
                }
                .sortedBy { it.altitudeKm }
                .distinctBy { it.altitudeKm.toBits() }
                .map { cell ->
                    WindSample(
                        altitudeKm = cell.altitudeKm,
                        speedKmh = cell.speedKmh,
                        directionDeg = cell.directionDeg,
                        isSurface10m = cell.isSurface10m,
                    )
                }
        }
        .filterValues { it.isNotEmpty() }
}

/** Interpolates vector components only between source levels; never extrapolates. */
internal fun interpolateWind(
    profile: List<WindSample>,
    targetAltitudeKm: Float,
): WindSample? {
    if (profile.isEmpty() || !targetAltitudeKm.isFinite()) return null
    // Tolerate floating-point error when converting an exact source Y back to altitude.
    val directSample = profile.firstOrNull {
        abs(it.altitudeKm - targetAltitudeKm) <= WIND_SOURCE_ALTITUDE_TOLERANCE_KM
    }
    val upperIndex = profile.indexOfFirst { it.altitudeKm > targetAltitudeKm }
    return when {
        directSample != null -> directSample
        targetAltitudeKm < profile.first().altitudeKm ||
            targetAltitudeKm > profile.last().altitudeKm || upperIndex <= 0 -> null
        else -> {
            val lower = profile[upperIndex - 1]
            val upper = profile[upperIndex]
            val fraction = (targetAltitudeKm - lower.altitudeKm) / (upper.altitudeKm - lower.altitudeKm)
            val (lowerU, lowerV) = windComponents(lower.speedKmh, lower.directionDeg)
            val (upperU, upperV) = windComponents(upper.speedKmh, upper.directionDeg)
            val u = lowerU + (upperU - lowerU) * fraction
            val v = lowerV + (upperV - lowerV) * fraction
            WindSample(
                altitudeKm = targetAltitudeKm,
                speedKmh = hypot(u, v),
                directionDeg = ((atan2(u, v) * 180f / PI.toFloat()) + 360f) % 360f,
                isInterpolated = true,
            )
        }
    }
}

/**
 * Picks source levels whose glyphs fit at their true height.
 *
 * [anchorYs] are ordered from the lowest altitude upward (screen Y decreasing). Lower levels win
 * when two are closer than [minSpacing]; skipped levels stay reachable through the cursor.
 */
internal fun pickReadableWindLevels(
    anchorYs: List<Float>,
    minY: Float,
    maxY: Float,
    minSpacing: Float,
): List<Int> {
    val kept = mutableListOf<Int>()
    var lastKeptY = Float.POSITIVE_INFINITY
    anchorYs.forEachIndexed { index, y ->
        if (y in minY..maxY && lastKeptY - y >= minSpacing) {
            kept += index
            lastKeptY = y
        }
    }
    return kept
}

/** Draws a glyph every N hours so neighboring glyphs keep at least [minSlotWidth] apart. */
internal fun windGlyphHourStride(columnWidth: Float, minSlotWidth: Float): Int {
    if (columnWidth <= 0f) return 1
    return maxOf(1, ceil(minSlotWidth / columnWidth - WIND_STRIDE_EPSILON).toInt())
}

/**
 * Maps an X coordinate to its exact hourly column. Each visible column belongs to one hour.
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

private fun windComponents(
    speedKmh: Float,
    directionDeg: Float,
): Pair<Float, Float> {
    val radians = directionDeg * PI.toFloat() / 180f
    return (speedKmh * sin(radians)) to (speedKmh * cos(radians))
}

private const val WIND_SOURCE_ALTITUDE_TOLERANCE_KM = 0.000001f
private const val WIND_STRIDE_EPSILON = 0.0001f
