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

/**
 * Interpolates vector components between source levels.
 *
 * Beyond the profile the nearest sample is held and marked as interpolated: the highest upper-air level
 * above it, and the lowest sample below it down to the terrain. That lowest sample is the 10 m value when
 * there is one, so the thin strip between the terrain and 10 m above it shows the 10 m wind instead of a
 * gap; otherwise it is the lowest upper-air level. A profile without any upper-air level is not stretched
 * up or down: it only returns its exact 10 m sample.
 */
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
        profile.all { it.isSurface10m } -> null
        upperIndex < 0 -> profile.lastOrNull { !it.isSurface10m }?.heldAt(targetAltitudeKm)
        upperIndex == 0 -> profile.first().heldAt(targetAltitudeKm)
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

private fun WindSample.heldAt(altitudeKm: Float): WindSample =
    copy(altitudeKm = altitudeKm, isSurface10m = false, isInterpolated = true)

/**
 * A source level with the screen Y it is shown at: where its glyph is drawn, or for a cursor snap
 * ([snapWindLevel]) where the reticle sits.
 */
internal data class ReadableWindLevel(
    val index: Int,
    val drawY: Float,
)

/**
 * Picks source levels whose glyphs fit inside the plot.
 *
 * [anchorYs] are the true screen Ys of the levels, ordered from the lowest altitude upward (screen Y
 * decreasing). Levels outside [plotTop]..[plotBottom] are not visible. A visible level too close to an
 * edge keeps its glyph fully inside the plot by drawing it up to [glyphHalfHeight] away from its true
 * height. Lower levels win when two drawn glyphs are closer than [minSpacing]; skipped levels can still
 * be read through the cursor (see [snapWindLevel]).
 */
internal fun pickReadableWindLevels(
    anchorYs: List<Float>,
    plotTop: Float,
    plotBottom: Float,
    glyphHalfHeight: Float,
    minSpacing: Float,
): List<ReadableWindLevel> {
    if (plotBottom - plotTop < 2f * glyphHalfHeight) return emptyList()
    val kept = mutableListOf<ReadableWindLevel>()
    var lastKeptY = Float.POSITIVE_INFINITY
    anchorYs.forEachIndexed { index, y ->
        if (y !in plotTop..plotBottom) return@forEachIndexed
        val drawY = y.coerceIn(plotTop + glyphHalfHeight, plotBottom - glyphHalfHeight)
        if (lastKeptY - drawY >= minSpacing) {
            kept += ReadableWindLevel(index, drawY)
            lastKeptY = drawY
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
 * Picks the source level the cursor snaps to at [pointerY], with the Y its reticle sits on.
 *
 * [drawnLevels] are the glyphs the hour draws, as picked by [pickReadableWindLevels] from [anchorYs]. They
 * are hit-tested at their drawn Y first, so the readout always matches the glyph under the finger, even
 * when the glyph is shifted away from its true height at a plot edge. Only when no drawn glyph is within
 * [snapDistance] does a hidden level snap, at its true Y. Hidden levels outside [plotRange] never snap,
 * and neither do hidden levels within [snapDistance] of a drawn glyph: that glyph stands for them, and
 * snapping to them would put the reticle on a glyph that shows a different value.
 */
internal fun snapWindLevel(
    anchorYs: List<Float>,
    drawnLevels: List<ReadableWindLevel>,
    pointerY: Float,
    plotRange: ClosedFloatingPointRange<Float>,
    snapDistance: Float,
): ReadableWindLevel? {
    val drawnHit = drawnLevels
        .filter { abs(it.drawY - pointerY) <= snapDistance }
        .minByOrNull { abs(it.drawY - pointerY) }
    if (drawnHit != null) return drawnHit
    val drawnIndices = drawnLevels.mapTo(HashSet()) { it.index }
    return anchorYs.withIndex()
        .filter { (index, y) -> index !in drawnIndices && y in plotRange }
        .filter { (_, y) -> drawnLevels.none { abs(it.drawY - y) <= snapDistance } }
        .filter { (_, y) -> abs(y - pointerY) <= snapDistance }
        .minByOrNull { (_, y) -> abs(y - pointerY) }
        ?.let { (index, y) -> ReadableWindLevel(index, y) }
}

/**
 * Hour columns that draw glyphs: every [windGlyphHourStride]-th hour counted from the first hour, so they
 * line up with the hour labels and the 3-hour grid. A trailing column whose speed pill would not fit to
 * the right of its arrow ([rightExtent] past the column center) is skipped instead of flipping the pill
 * onto its neighbor; when no column has room, the first one is drawn alone.
 */
internal fun windGlyphColumns(
    hourCount: Int,
    columnWidth: Float,
    minSlotWidth: Float,
    rightExtent: Float,
): List<Int> {
    if (hourCount <= 0 || columnWidth <= 0f) return emptyList()
    val stride = windGlyphHourStride(columnWidth, minSlotWidth)
    // Column centers sit half a column in from their right edge; err on the safe side of the boundary.
    val lastWithRoom = floor(hourCount - 0.5f - rightExtent / columnWidth - WIND_STRIDE_EPSILON)
        .toInt()
        .coerceIn(0, hourCount - 1)
    return (0..lastWithRoom step stride).toList()
}

/** Hour columns that carry an hour label: every [windGlyphHourStride]-th hour from the first hour. */
internal fun windHourLabelColumns(
    hourCount: Int,
    columnWidth: Float,
    minSlotWidth: Float,
): List<Int> {
    if (hourCount <= 0 || columnWidth <= 0f) return emptyList()
    return (0 until hourCount step windGlyphHourStride(columnWidth, minSlotWidth)).toList()
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
