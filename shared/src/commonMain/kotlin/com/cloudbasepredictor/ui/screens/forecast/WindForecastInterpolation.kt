@file:Suppress("MatchingDeclarationName")

package com.cloudbasepredictor.ui.screens.forecast

import kotlin.math.abs
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
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
 * ([snapWindLevel]) where the reticle sits. [pillCenterY] is where the glyph's speed pill is centered: at [drawY]
 * beside the arrow, or above or below it in a column that stacks its pills ([WindPillStack]).
 */
internal data class ReadableWindLevel(
    val index: Int,
    val drawY: Float,
    val pillCenterY: Float = drawY,
) {
    /** Distance from [y] to the glyph: to its arrow or to its pill, whichever is closer. */
    fun distanceTo(y: Float): Float = min(abs(drawY - y), abs(pillCenterY - y))
}

/**
 * Speed pills stacked on their arrows: a pill [height] tall is centered [offset] above its arrow, or the same
 * distance below it where there is no room above inside the plot.
 */
internal data class WindPillStack(
    val offset: Float,
    val height: Float,
) {
    /** The pill center for an arrow at [arrowY], or null when the pill fits neither above nor below it. */
    fun pillCenterY(arrowY: Float, plotTop: Float, plotBottom: Float): Float? = when {
        arrowY - offset - height / 2f >= plotTop -> arrowY - offset
        arrowY + offset + height / 2f <= plotBottom -> arrowY + offset
        else -> null
    }
}

/**
 * Picks source levels whose glyphs fit inside the plot.
 *
 * [anchorYs] are the true screen Ys of the levels, ordered from the lowest altitude upward (screen Y
 * decreasing). Levels outside [plotTop]..[plotBottom] are not visible. A visible level too close to an
 * edge keeps its glyph fully inside the plot by drawing it up to [glyphHalfHeight] away from its true
 * height. Lower levels win when two drawn glyphs are closer than [minSpacing]; skipped levels can still
 * be read through the cursor (see [snapWindLevel]).
 *
 * With [stackedPill] each glyph carries its pill above its arrow, or below it near the plot top, and the spacing
 * grows by how far a pill reaches past [glyphHalfHeight] on the side it faces the neighbor; a level whose pill fits
 * on neither side is skipped.
 */
internal fun pickReadableWindLevels(
    anchorYs: List<Float>,
    plotTop: Float,
    plotBottom: Float,
    glyphHalfHeight: Float,
    minSpacing: Float,
    stackedPill: WindPillStack? = null,
): List<ReadableWindLevel> {
    if (plotBottom - plotTop < 2f * glyphHalfHeight) return emptyList()
    val pillReach = stackedPill?.let { it.offset + it.height / 2f - glyphHalfHeight } ?: 0f
    val kept = mutableListOf<ReadableWindLevel>()
    var lastKeptY = Float.POSITIVE_INFINITY
    var lastKeptReachUp = 0f
    anchorYs.forEachIndexed { index, y ->
        if (y !in plotTop..plotBottom) return@forEachIndexed
        val drawY = y.coerceIn(plotTop + glyphHalfHeight, plotBottom - glyphHalfHeight)
        val pillY = if (stackedPill == null) {
            drawY
        } else {
            stackedPill.pillCenterY(drawY, plotTop, plotBottom) ?: return@forEachIndexed
        }
        val reachUp = if (pillY < drawY) pillReach else 0f
        val reachDown = if (pillY > drawY) pillReach else 0f
        if (lastKeptY - drawY >= minSpacing + lastKeptReachUp + reachDown) {
            kept += ReadableWindLevel(index, drawY, pillY)
            lastKeptY = drawY
            lastKeptReachUp = reachUp
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
 * are hit-tested first, at their arrow and at their pill ([ReadableWindLevel.distanceTo]), so the readout always
 * matches the glyph under the finger, even when the glyph is shifted away from its true height at a plot edge or
 * its pill is stacked above or below the arrow. Only when no drawn glyph is within [snapDistance] does a hidden
 * level snap, at its true Y. Hidden levels outside [plotRange] never snap, and neither do hidden levels within
 * [snapDistance] of a drawn glyph: that glyph stands for them, and snapping to them would put the reticle on a
 * glyph that shows a different value.
 */
internal fun snapWindLevel(
    anchorYs: List<Float>,
    drawnLevels: List<ReadableWindLevel>,
    pointerY: Float,
    plotRange: ClosedFloatingPointRange<Float>,
    snapDistance: Float,
): ReadableWindLevel? {
    val drawnHit = drawnLevels
        .filter { it.distanceTo(pointerY) <= snapDistance }
        .minByOrNull { it.distanceTo(pointerY) }
    if (drawnHit != null) return drawnHit
    val drawnIndices = drawnLevels.mapTo(HashSet()) { it.index }
    return anchorYs.withIndex()
        .filter { (index, y) -> index !in drawnIndices && y in plotRange }
        .filter { (_, y) -> drawnLevels.none { it.distanceTo(y) <= snapDistance } }
        .filter { (_, y) -> abs(y - pointerY) <= snapDistance }
        .minByOrNull { (_, y) -> abs(y - pointerY) }
        ?.let { (index, y) -> ReadableWindLevel(index, y) }
}

/**
 * Horizontal sizes of the wind glyphs in pixels: an arrow [arrowLength] long centered on its hour column and,
 * [gap] to its right, a speed pill at most [pillWidth] wide.
 */
internal data class WindGlyphMetrics(
    val arrowLength: Float,
    val gap: Float,
    val pillWidth: Float,
    /** Between a pill and the next glyph's arrow, which reaches past its shaft with the halo. */
    val clearance: Float,
    /** Between a pill and the right edge of the chart. */
    val edgeMargin: Float,
    /** The narrowest glyph slot, however narrow the pills are. */
    val minSlotWidth: Float,
) {
    /** From one glyph column to the next, so a pill beside its arrow stays clear of the next glyph. */
    val slotWidth: Float = max(minSlotWidth, arrowLength + gap + pillWidth + clearance)

    /** How far a pill beside its arrow reaches right of the column center. */
    val besideReach: Float = arrowLength / 2f + gap + pillWidth
}

/** Where a glyph column draws its speed pills. */
internal enum class WindPillPlacement {
    /** To the right of each arrow, at the arrow's height. */
    BESIDE,

    /** Above each arrow, or below it near the plot top ([WindPillStack]), inside [WindGlyphColumn.pillSpan]. */
    STACKED,

    /** Nowhere: not even one pill fits between the previous column's pills and the right edge. */
    NONE,
}

/**
 * A glyph column at hour [index] and where it draws its speed pills. A [WindPillPlacement.STACKED] column keeps
 * them inside [pillSpan] (screen X), clear of the previous column's pills and of the right edge.
 */
internal data class WindGlyphColumn(
    val index: Int,
    val pills: WindPillPlacement = WindPillPlacement.BESIDE,
    val pillSpan: ClosedFloatingPointRange<Float> = 0f..0f,
) {
    val pillSpanWidth: Float get() = pillSpan.endInclusive - pillSpan.start

    /** Left edge of a stacked pill [width] wide: centered on the arrow at [arrowX], moved inside [pillSpan]. */
    fun stackedPillLeft(arrowX: Float, width: Float): Float =
        (arrowX - width / 2f).coerceIn(pillSpan.start, max(pillSpan.start, pillSpan.endInclusive - width))

    /**
     * Arrow center of a one-line glyph inside [pillSpan]: the arrow [arrowLength] long, [gap], then a pill
     * [pillWidth] wide, with the arrow as close to [arrowX] (its column center) as the right end allows. The line
     * has to fit: [arrowLength] + [gap] + [pillWidth] <= [pillSpanWidth].
     */
    fun lineArrowX(arrowX: Float, arrowLength: Float, gap: Float, pillWidth: Float): Float =
        min(arrowX, pillSpan.endInclusive - pillWidth - gap - arrowLength / 2f)
            .coerceAtLeast(pillSpan.start + arrowLength / 2f)
}

/**
 * Hour columns that draw glyphs: every [windGlyphHourStride]-th hour counted from the first hour, the same columns
 * as [windHourLabelColumns], so they line up with the hour labels and the 3-hour grid. [plotLeft] is the screen X
 * of the first column's left edge.
 *
 * Every column keeps its speed pills to the right of its arrows, except a trailing column without room for them
 * before the right edge: it stacks them above or below its arrows instead, inside the room between the previous
 * column's pills and the right edge. When not even one pill fits there, that column draws its arrows alone.
 */
internal fun windGlyphColumns(
    hourCount: Int,
    plotLeft: Float,
    columnWidth: Float,
    metrics: WindGlyphMetrics,
): List<WindGlyphColumn> {
    if (hourCount <= 0 || columnWidth <= 0f) return emptyList()
    val columns = windHourLabelColumns(hourCount, columnWidth, metrics.slotWidth).map { WindGlyphColumn(it) }
    val last = columns.last().index
    // Column centers sit half a column in from their right edge; err on the safe side of the boundary.
    val roomRight = (hourCount - last - 0.5f - WIND_STRIDE_EPSILON) * columnWidth
    val previousPillsEnd = columns.getOrNull(columns.size - 2)
        ?.let { plotLeft + (it.index + 0.5f) * columnWidth + metrics.besideReach + metrics.clearance }
    val span = (previousPillsEnd ?: (plotLeft + metrics.edgeMargin))..
        (plotLeft + hourCount * columnWidth - metrics.edgeMargin)
    val pills = if (span.endInclusive - span.start >= metrics.pillWidth) {
        WindPillPlacement.STACKED
    } else {
        WindPillPlacement.NONE
    }
    return if (roomRight >= metrics.besideReach + metrics.edgeMargin) {
        columns
    } else {
        columns.dropLast(1) + WindGlyphColumn(last, pills, span)
    }
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
