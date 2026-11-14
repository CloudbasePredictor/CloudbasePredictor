@file:Suppress(
    "CyclomaticComplexMethod",
    "FunctionNaming",
    "LongMethod",
    "MagicNumber",
    "ReturnCount",
    "TooManyFunctions",
)

package com.cloudbasepredictor.ui.screens.forecast.views

import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cloudbasepredictor.data.units.AltitudeUnit
import com.cloudbasepredictor.data.units.DisplayUnits
import com.cloudbasepredictor.data.units.UnitPreset
import com.cloudbasepredictor.data.units.altitudeAxisUnitLabel
import com.cloudbasepredictor.data.units.formatAltitudeAxisValue
import com.cloudbasepredictor.data.units.formatWindSpeed
import com.cloudbasepredictor.data.units.resolveDisplayUnits
import com.cloudbasepredictor.model.ForecastMode
import com.cloudbasepredictor.ui.preview.ForecastPreviewData
import com.cloudbasepredictor.ui.preview.ForecastPreviewTheme
import com.cloudbasepredictor.ui.screens.forecast.ForecastReadyUiState
import com.cloudbasepredictor.ui.screens.forecast.ForecastTestTags.WIND_CHART_CANVAS
import com.cloudbasepredictor.ui.screens.forecast.ForecastTestTags.WIND_RENDERER_DESCRIPTION
import com.cloudbasepredictor.ui.screens.forecast.ForecastTestTags.WIND_TIME_AXIS
import com.cloudbasepredictor.ui.screens.forecast.ForecastTestTags.WIND_VIEW
import com.cloudbasepredictor.ui.screens.forecast.ReadableWindLevel
import com.cloudbasepredictor.ui.screens.forecast.WindCursorArea
import com.cloudbasepredictor.ui.screens.forecast.WindForecastChartUiModel
import com.cloudbasepredictor.ui.screens.forecast.WindGlyphColumn
import com.cloudbasepredictor.ui.screens.forecast.WindGlyphMetrics
import com.cloudbasepredictor.ui.screens.forecast.WindLevelMarker
import com.cloudbasepredictor.ui.screens.forecast.WindPillPlacement
import com.cloudbasepredictor.ui.screens.forecast.WindPillStack
import com.cloudbasepredictor.ui.screens.forecast.WindSample
import com.cloudbasepredictor.ui.screens.forecast.buildWindProfiles
import com.cloudbasepredictor.ui.screens.forecast.interpolateWind
import com.cloudbasepredictor.ui.screens.forecast.pickReadableWindLevels
import com.cloudbasepredictor.ui.screens.forecast.snapWindLevel
import com.cloudbasepredictor.ui.screens.forecast.windGlyphColumns
import com.cloudbasepredictor.ui.screens.forecast.windHourLabelColumns
import com.cloudbasepredictor.ui.screens.forecast.windHourIndexAtX
import com.cloudbasepredictor.ui.screens.forecast.windLegendLabelCenters
import com.cloudbasepredictor.ui.screens.forecast.windSpeedColor
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * The wind chart of the selected day: source-height glyphs over the interpolated wind, the 10 m row and a cursor.
 *
 * [labels] are the words the chart draws and its cursor reads out; the defaults are English.
 */
@Composable
fun WindForecastView(
    uiState: ForecastReadyUiState,
    modifier: Modifier = Modifier,
    onVisibleTopAltitudeChange: (Float) -> Unit = {},
    overlayBackHandler: ForecastOverlayBackHandler = ignoreForecastOverlayBack,
    labels: WindChartLabels = WindChartLabels(),
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .testTag(WIND_VIEW),
    ) {
        // The whole day fits the viewport, as in the other forecast charts.
        WindChartCanvas(
            chart = uiState.windChart,
            visibleTopAltitudeKm = uiState.chartViewport.visibleTopAltitudeKm,
            elevationKm = uiState.elevationKm,
            displayUnits = uiState.displayUnits,
            labels = labels,
            onVisibleTopAltitudeChange = onVisibleTopAltitudeChange,
            overlayBackHandler = overlayBackHandler,
            modifier = Modifier.fillMaxSize(),
        )

        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(WIND_TIME_AXIS_HEIGHT * max(1f, LocalDensity.current.fontScale))
                .testTag(WIND_TIME_AXIS),
        )
    }
}

@Composable
private fun WindChartCanvas(
    chart: WindForecastChartUiModel,
    visibleTopAltitudeKm: Float,
    elevationKm: Float,
    displayUnits: DisplayUnits,
    labels: WindChartLabels,
    onVisibleTopAltitudeChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    overlayBackHandler: ForecastOverlayBackHandler,
) {
    val density = LocalDensity.current
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface
    val surfaceColor = MaterialTheme.colorScheme.surface
    val axisTextColor = MaterialTheme.colorScheme.onSurfaceVariant
    val gridBackgroundColor = lerp(
        start = MaterialTheme.colorScheme.surface,
        stop = MaterialTheme.colorScheme.onSurface,
        fraction = 0.035f,
    )
    val outlineColor = MaterialTheme.colorScheme.outlineVariant

    val textMeasurer = rememberTextMeasurer()
    val speedLabelStyle = remember(onSurfaceColor) {
        TextStyle(color = onSurfaceColor, fontSize = 10.sp, fontWeight = FontWeight.Bold)
    }
    val tooltipStyle = remember(onSurfaceColor) {
        TextStyle(
            color = onSurfaceColor,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
        )
    }
    val timeLabelStyle = remember(onSurfaceColor) {
        TextStyle(
            color = onSurfaceColor,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
        )
    }
    val axisStyle = remember(axisTextColor) {
        TextStyle(color = axisTextColor, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
    }
    // The altitude axis title and the legend.
    val smallAxisStyle = remember(axisStyle) { axisStyle.copy(fontSize = 9.sp) }
    // One cursor state for the life of the view: the gesture handler below is installed once and keeps writing to
    // it. A cursor put down on another chart (another day) is not shown, and is dropped once the chart changes.
    var crosshair by remember { mutableStateOf<WindCrosshair?>(null) }
    LaunchedEffect(chart) {
        if (crosshair?.chart != chart) crosshair = null
    }
    val crosshairPos = crosshair?.takeIf { it.chart == chart }?.position
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    val profileByHour = remember(chart.cells) { buildWindProfiles(chart.cells) }
    val effectiveTopAltitudeKm = max(
        elevationKm + visibleTopAltitudeKm, elevationKm + WIND_MIN_VISIBLE_ALTITUDE_RANGE_KM,
    )
    // Speed labels are laid out once here; frames, crosshair moves included, only draw them.
    val speedLabels = remember(profileByHour, displayUnits, speedLabelStyle, textMeasurer, density) {
        measureWindSpeedLabels(profileByHour, displayUnits, textMeasurer, speedLabelStyle, density)
    }
    // Glyph columns leave room for the widest speed pill that can show at the current altitude range;
    // levels far above it (jet-stream speeds) do not widen the spacing.
    val maxPillWidth = remember(speedLabels, elevationKm, effectiveTopAltitudeKm) {
        speedLabels.maxPillWidth(elevationKm, effectiveTopAltitudeKm)
    }
    val geometry = WindChartGeometry(
        width = canvasSize.width.toFloat(),
        height = canvasSize.height.toFloat(),
        hourCount = chart.hours.size,
        density = density.density,
        fontScale = density.fontScale,
        maxPillWidth = maxPillWidth,
        pillHeight = speedLabels.pillHeight,
    )
    val glyphs = remember(chart, canvasSize, elevationKm, effectiveTopAltitudeKm, speedLabels, maxPillWidth) {
        buildWindGlyphs(chart.hours, profileByHour, elevationKm, effectiveTopAltitudeKm, speedLabels, geometry)
    }
    // Interpolating every strip is the costliest part of a frame; crosshair moves reuse it. The plot height
    // follows the pill height through the 10 m row.
    val backgroundCache = remember(
        chart.hours, profileByHour, elevationKm, effectiveTopAltitudeKm, density, speedLabels.pillHeight,
    ) {
        WindBackgroundCache()
    }
    // Axis, legend and hour labels are laid out when their texts or styles change; frames only draw them. The text
    // measurer is remembered for the density and font scale, so a change of either measures them again.
    val altitudeTicks = remember(elevationKm, effectiveTopAltitudeKm) {
        buildAltitudeTicks(
            minAltitudeKm = elevationKm,
            maxAltitudeKm = effectiveTopAltitudeKm,
            stepKm = if (effectiveTopAltitudeKm <= 3.5f) 0.5f else 1f,
        )
    }
    val altitudeLabels = remember(altitudeTicks, displayUnits, axisStyle, textMeasurer) {
        ticksToLabels(altitudeTicks).map { altitudeKm ->
            WindAxisLabel(
                altitudeKm,
                textMeasurer.measureCanvasText(formatAltitudeAxisValue(altitudeKm, displayUnits), axisStyle),
            )
        }
    }
    // Keys of the level lines this chart draws, right of the legend's speed scale.
    val legendKeys = buildList {
        if (chart.cclKm.isNotEmpty()) add("CCL" to CCL_COLOR)
        if (chart.freezingLevelKm.isNotEmpty()) add("0°C" to FREEZING_COLOR)
    }
    val chartTexts = remember(displayUnits, labels, axisStyle, smallAxisStyle, textMeasurer, legendKeys) {
        textMeasurer.measureWindChartTexts(displayUnits, labels, axisStyle, smallAxisStyle, legendKeys)
    }
    val hourLabels = remember(chart.hours, timeLabelStyle, textMeasurer) {
        chart.hours.map { textMeasurer.measureCanvasText(formatPaddedInt(it, minimumDigits = 2), timeLabelStyle) }
    }
    val cursor = crosshairPos?.let { pos ->
        resolveWindCursor(pos, chart.hours, profileByHour, elevationKm, effectiveTopAltitudeKm, geometry, glyphs)
    }
    val tooltipLines = cursor?.let { windTooltipLines(it, displayUnits, labels) }.orEmpty()
    // Measured once per readout, not on every frame.
    val tooltipLayouts = remember(tooltipLines, tooltipStyle, textMeasurer) {
        tooltipLines.map { textMeasurer.measureCanvasText(it, tooltipStyle) }
    }
    // The pointer handlers below are installed once (keyed on Unit) and read every value that can change through
    // these states, so they always act on the current day, altitude range, layout and callback.
    val latestChart = rememberUpdatedState(chart)
    val latestVisibleTopAltitudeKm = rememberUpdatedState(visibleTopAltitudeKm)
    val latestOnVisibleTopAltitudeChange = rememberUpdatedState(onVisibleTopAltitudeChange)
    val latestGeometry = rememberUpdatedState(geometry)

    // Back dismisses the tap overlay (crosshair tooltip) first, before navigating away.
    overlayBackHandler(cursor != null) {
        crosshair = null
    }

    Canvas(
        modifier = modifier
            .testTag(WIND_CHART_CANVAS)
            .onSizeChanged { canvasSize = it }
            .semantics {
                contentDescription = (listOf(WIND_RENDERER_DESCRIPTION) + tooltipLines).joinToString("; ")
                stateDescription = if (cursor != null) "cursor" else "idle"
            }
            .pointerInput(Unit) {
                detectWindCursorGestures(
                    ownsGestureAt = { latestGeometry.value.cursorAreaContains(it) },
                    clampToOwnedArea = { latestGeometry.value.clampToCursorArea(it) },
                    onCursorMove = { position ->
                        crosshair = position?.let { WindCrosshair(it, latestChart.value) }
                    },
                )
            }
            .pointerInput(Unit) {
                detectForecastZoomGestures(
                    currentTopAltitudeKm = { latestVisibleTopAltitudeKm.value },
                    onVisibleTopAltitudeChange = { latestOnVisibleTopAltitudeChange.value(it) },
                )
            },
    ) {
        drawRect(color = surfaceColor, size = size)

        val g = WindChartGeometry(
            size.width, size.height, chart.hours.size, density.density, density.fontScale, maxPillWidth,
            speedLabels.pillHeight,
        )
        val plotLeft = g.plotLeft
        val plotTop = 0f
        val plotRight = size.width
        val plotBottom = g.plotBottom
        val plotWidth = plotRight - plotLeft
        val plotHeight = plotBottom - plotTop
        val minAltitudeKm = elevationKm

        if (plotWidth <= 0f || plotHeight <= 0f || chart.hours.isEmpty()) {
            return@Canvas
        }

        drawRect(
            color = gridBackgroundColor,
            topLeft = Offset(0f, plotTop),
            size = Size(size.width, plotHeight + g.surfaceRowHeight),
        )

        val columnWidth = g.columnWidth
        // ── Interpolated wind-speed background ────────────────────
        // The background and cursor use the same vector interpolation at each hour and altitude.
        backgroundCache.strips(size) {
            buildWindBackground(chart.hours, profileByHour, minAltitudeKm, effectiveTopAltitudeKm, g)
        }.forEach { strip ->
            drawRect(color = strip.color, topLeft = strip.topLeft, size = strip.size)
        }

        // Grid lines
        chart.hours.forEachIndexed { index, hour ->
            val x = plotLeft + index * columnWidth
            val alpha = if ((hour - chart.hours.first()) % 3 == 0) 0.5f else 0.22f
            drawLine(
                color = outlineColor.copy(alpha = alpha),
                start = Offset(x, plotTop),
                end = Offset(x, plotBottom + g.surfaceRowHeight),
                strokeWidth = 1.dp.toPx(),
            )
        }

        altitudeTicks.forEach { altKm ->
            val y = altitudeToY(altKm, minAltitudeKm, effectiveTopAltitudeKm, plotTop, plotBottom)
            drawLine(
                color = outlineColor.copy(alpha = 0.35f),
                start = Offset(plotLeft, y),
                end = Offset(plotRight, y),
                strokeWidth = 1.dp.toPx(),
            )
        }

        // Outline of the plot and of the 10 m row below it.
        drawRect(
            color = outlineColor.copy(alpha = 0.4f),
            topLeft = Offset(plotLeft, plotTop),
            size = Size(plotWidth, plotHeight),
            style = Stroke(width = 1.dp.toPx()),
        )
        drawRect(
            color = outlineColor.copy(alpha = 0.4f),
            topLeft = Offset(plotLeft, plotBottom),
            size = Size(plotWidth, g.surfaceRowHeight),
            style = Stroke(width = 1.dp.toPx()),
        )

        drawLevelLine(
            chart.cclKm, chart.hours, g, minAltitudeKm, effectiveTopAltitudeKm, CCL_COLOR, surfaceColor, 2.5f,
        )
        drawLevelLine(
            chart.freezingLevelKm, chart.hours, g, minAltitudeKm, effectiveTopAltitudeKm,
            FREEZING_COLOR, surfaceColor, 2f,
        )

        // Source glyphs sit at their true hourly height, above the CCL and 0 °C lines.
        glyphs.all.forEach { glyph ->
            drawWindGlyph(glyph, speedLabels.noData, onSurfaceColor, surfaceColor)
        }

        // ── Left axis ─────────────────────────────────────────────
        altitudeLabels.forEach { label ->
            val y = altitudeToY(label.altitudeKm, minAltitudeKm, effectiveTopAltitudeKm, plotTop, plotBottom)
            if (y >= 24.dp.toPx()) {
                drawMeasuredText(label.layout, 8.dp.toPx(), min(y + 4.dp.toPx(), plotBottom - 2.dp.toPx()))
            }
        }
        drawMeasuredText(chartTexts.axisTitle, 8.dp.toPx(), 15.dp.toPx())
        drawMeasuredText(chartTexts.surfaceRowTitle, 8.dp.toPx(), plotBottom + g.surfaceRowHeight / 2f + 4.dp.toPx())

        drawWindLegend(chartTexts, g)

        // ── Hours ─────────────────────────────────────────────────
        val timeBaseline = size.height - 4.dp.toPx()
        hourLabels.forEachIndexed { index, layout ->
            if (index in g.hourLabelColumns) {
                drawMeasuredText(layout, g.columnCenter(index), timeBaseline, CanvasTextAnchor.CENTER)
            }
        }

        // ── Crosshair overlay ──────────────────────────────────
        cursor?.let { selected ->
            // The reticle sits on the selected hour's column, on the snapped glyph when there is one, and on the
            // drawn 10 m glyph in the 10 m row, instead of the raw finger position.
            val cx = selected.x
            val cy = selected.y

            val dash = PathEffect.dashPathEffect(floatArrayOf(8f, 6f))
            drawLine(
                color = onSurfaceColor.copy(alpha = 0.5f),
                start = Offset(cx, plotTop),
                end = Offset(cx, plotBottom + g.surfaceRowHeight),
                strokeWidth = 1.dp.toPx(),
                pathEffect = dash,
            )
            drawLine(
                color = onSurfaceColor.copy(alpha = 0.5f),
                start = Offset(plotLeft, cy),
                end = Offset(plotRight, cy),
                strokeWidth = 1.dp.toPx(),
                pathEffect = dash,
            )

            val reticleR = 12.dp.toPx()
            drawCircle(
                color = onSurfaceColor.copy(alpha = 0.75f),
                radius = reticleR,
                center = Offset(cx, cy),
                style = Stroke(width = 1.5.dp.toPx()),
            )

            val lineH = 12.sp.toPx() * 1.3f
            val maxTextW = tooltipLayouts.maxOfOrNull { it.size.width.toFloat() } ?: 0f
            val padH = 8.dp.toPx()
            val padV = 6.dp.toPx()
            val gap = 6.dp.toPx()
            val ttW = maxTextW + padH * 2
            val ttH = lineH * tooltipLayouts.size + padV * 2
            // Prefer the right side of the finger; flip left near the edge.
            val ttX = if (cx + reticleR + gap + ttW <= plotRight - 4.dp.toPx()) {
                cx + reticleR + gap
            } else {
                max(plotLeft + 4.dp.toPx(), cx - reticleR - gap - ttW)
            }
            val ttY = (cy - ttH - reticleR).let { above ->
                if (above >= plotTop + 4.dp.toPx()) above else cy + reticleR
            }.coerceIn(plotTop, max(plotTop, plotBottom + g.surfaceRowHeight - ttH))

            drawRoundRect(
                color = surfaceColor.copy(alpha = 0.95f),
                topLeft = Offset(ttX, ttY),
                size = Size(ttW, ttH),
                cornerRadius = CornerRadius(6.dp.toPx()),
            )
            drawRoundRect(
                color = onSurfaceColor.copy(alpha = 0.3f),
                topLeft = Offset(ttX, ttY),
                size = Size(ttW, ttH),
                cornerRadius = CornerRadius(6.dp.toPx()),
                style = Stroke(width = 1.dp.toPx()),
            )
            tooltipLayouts.forEachIndexed { idx, layout ->
                drawMeasuredText(layout, ttX + padH, ttY + padV + (idx + 1) * lineH - lineH * 0.15f)
            }
        }
    }
}

/**
 * Pixel layout shared by drawing and hit testing.
 *
 * [maxPillWidth] is the widest speed pill that can be drawn and [pillHeight] the tallest. Glyph columns are spaced
 * and placed so that no pill overlaps another glyph or leaves the chart, and the vertical glyph spacing grows with
 * the pill height at large font scales.
 */
private class WindChartGeometry(
    val width: Float,
    val height: Float,
    hourCount: Int,
    density: Float,
    fontScale: Float,
    maxPillWidth: Float,
    pillHeight: Float,
) {
    val plotLeft = WIND_AXIS_WIDTH.value * density

    /** The 10 m row fits its pill; the legend and hour axis grow with the font scale, unchanged at 1x. */
    val surfaceRowHeight = max(
        WIND_SURFACE_ROW_HEIGHT.value * density,
        pillHeight + WIND_SURFACE_ROW_PADDING.value * density,
    )
    val legendHeight = windTextRowHeight(WIND_LEGEND_HEIGHT, density, fontScale)
    val timeAxisHeight = windTextRowHeight(WIND_TIME_AXIS_HEIGHT, density, fontScale)
    val plotBottom = height - surfaceRowHeight - legendHeight - timeAxisHeight
    val surfaceRowCenterY = plotBottom + surfaceRowHeight / 2f
    val columnWidth = if (hourCount > 0) max(0f, width - plotLeft) / hourCount else 0f
    val arrowLength = WIND_ARROW_LENGTH.value * density
    val pillGap = WIND_PILL_GAP.value * density

    /** Half of what a glyph needs vertically: its arrow, or a taller pill beside it at large font scales. */
    val glyphHalfHeight = max(
        WIND_GLYPH_HALF_HEIGHT.value * density,
        pillHeight / 2f + WIND_PILL_EDGE_INSET.value * density,
    )

    /** Between the centers of vertically adjacent glyphs; pills taller than the arrows push them further apart. */
    val levelSpacing = max(WIND_LEVEL_SPACING.value * density, pillHeight + WIND_PILL_VERTICAL_GAP.value * density)

    /** Pills of a stacked column: a gap above (or below) the arrow's end. */
    val stackedPill = WindPillStack(offset = arrowLength / 2f + pillGap + pillHeight / 2f, height = pillHeight)
    val snapDistance = WIND_CURSOR_SNAP_DISTANCE.value * density
    val backgroundStripHeight = max(1f, ceil(WIND_BACKGROUND_STRIP_HEIGHT.value * density))
    val drawsGlyphs = plotBottom > 2f * glyphHalfHeight && columnWidth > 0f
    val pillEdgeInset = WIND_PILL_EDGE_INSET.value * density
    val compactArrowPadding = WIND_COMPACT_ARROW_PADDING.value * density
    val minArrowLength = WIND_MIN_ARROW_LENGTH.value * density

    private val metrics = WindGlyphMetrics(
        arrowLength = arrowLength,
        gap = pillGap,
        pillWidth = maxPillWidth,
        clearance = WIND_GLYPH_CLEARANCE.value * density,
        edgeMargin = WIND_PILL_EDGE_MARGIN.value * density,
        minSlotWidth = WIND_GLYPH_SLOT_WIDTH.value * density,
    )

    /** Hour columns that draw glyphs, with where each one puts its speed pills. */
    val glyphColumns: List<WindGlyphColumn> = windGlyphColumns(hourCount, plotLeft, columnWidth, metrics)
    private val glyphColumnByIndex = glyphColumns.associateBy { it.index }

    /** Hour columns that carry hour labels; the same columns as [glyphColumns]. */
    val hourLabelColumns: Set<Int> = windHourLabelColumns(hourCount, columnWidth, metrics.slotWidth).toSet()

    fun columnCenter(index: Int): Float = plotLeft + index * columnWidth + columnWidth / 2f

    /** The glyph column at hour [index], or null for an hour between glyph columns. */
    fun glyphColumn(index: Int): WindGlyphColumn? = glyphColumnByIndex[index]

    /** Top-left of [label]'s pill for the arrow at [arrowX] in [column], with the pill centered on [pillCenterY]. */
    fun pillTopLeft(column: WindGlyphColumn, arrowX: Float, pillCenterY: Float, label: WindPillLabel?): Offset {
        val size = label?.size ?: return Offset.Zero
        val left = if (column.pills == WindPillPlacement.STACKED) {
            column.stackedPillLeft(arrowX, size.width)
        } else {
            arrowX + arrowLength / 2f + pillGap
        }
        return Offset(left, pillCenterY - size.height / 2f)
    }

    /** The plot and the 10 m row: where the cursor reads values and owns drags. Null without hour columns. */
    private val cursorArea: WindCursorArea? =
        WindCursorArea(plotLeft, 0f, width, plotBottom + surfaceRowHeight).takeIf { columnWidth > 0f }

    fun cursorAreaContains(pos: Offset): Boolean = cursorArea?.contains(pos.x, pos.y) == true

    /** [pos] moved onto the cursor area, so that a drag leaving it keeps the cursor on the nearest edge. */
    fun clampToCursorArea(pos: Offset): Offset =
        cursorArea?.let { Offset(it.clampX(pos.x), it.clampY(pos.y)) } ?: pos
}

/** Where the cursor was put down, and on which chart: a cursor from another chart (another day) is not shown. */
private class WindCrosshair(
    val position: Offset,
    val chart: WindForecastChartUiModel,
)

/** A measured speed label and the pill drawn around it. */
private class WindPillLabel(
    val layout: TextLayoutResult,
    val size: Size,
)

/**
 * Speed labels of every sample, laid out once for the profiles, units, text style and density (font scale
 * included), so that frames only draw them.
 */
private class WindSpeedLabels(
    private val bySample: Map<WindSample, WindPillLabel>,
    /** The "no data" mark of the 10 m row. */
    val noData: TextLayoutResult,
    minPillHeight: Float,
) {
    /** The tallest pill; vertical glyph spacing follows it at large font scales. */
    val pillHeight: Float = max(minPillHeight, bySample.values.maxOfOrNull { it.size.height } ?: 0f)

    operator fun get(sample: WindSample): WindPillLabel? = bySample[sample]

    /** The widest pill of the 10 m values and of the levels inside [minAltitudeKm]..[maxAltitudeKm]. */
    fun maxPillWidth(minAltitudeKm: Float, maxAltitudeKm: Float): Float = bySample
        .filterKeys { it.isSurface10m || it.altitudeKm in minAltitudeKm..maxAltitudeKm }
        .values
        .maxOfOrNull { it.size.width }
        ?: 0f
}

private fun measureWindSpeedLabels(
    profiles: Map<Int, List<WindSample>>,
    units: DisplayUnits,
    textMeasurer: TextMeasurer,
    style: TextStyle,
    density: Density,
): WindSpeedLabels {
    val byText = mutableMapOf<String, WindPillLabel>()
    val bySample = profiles.values.flatten().associateWith { sample ->
        val text = formatWindSpeed(sample.speedKmh, units, withUnit = false)
        byText.getOrPut(text) {
            val layout = textMeasurer.measureCanvasText(text, style)
            WindPillLabel(layout, density.windPillSize(layout))
        }
    }
    val noData = textMeasurer.measureCanvasText(
        "—", style.copy(color = style.color.copy(alpha = 0.6f), fontSize = 12.sp),
    )
    return WindSpeedLabels(bySample, noData, with(density) { WIND_PILL_MIN_SIZE.toPx() })
}

/** An altitude axis label, laid out for its tick. */
private class WindAxisLabel(
    val altitudeKm: Float,
    val layout: TextLayoutResult,
)

/** Axis titles and legend labels, laid out once for their texts, units and styles, so that frames only draw them. */
private class WindChartTexts(
    /** "km ASL" or "kft ASL" at the top of the altitude axis. */
    val axisTitle: TextLayoutResult,
    /** "10 m" beside the 10 m row. */
    val surfaceRowTitle: TextLayoutResult,
    /** The speed scale under the legend swatches, one label per [WIND_LEGEND_SPEEDS_KMH] step. */
    val legendScale: List<TextLayoutResult>,
    /** The speed unit left of the scale. */
    val legendUnit: TextLayoutResult,
    /** Keys of the level lines the chart draws, with their colors. */
    val legendKeys: List<Pair<TextLayoutResult, Color>>,
)

private fun TextMeasurer.measureWindChartTexts(
    units: DisplayUnits,
    labels: WindChartLabels,
    axisStyle: TextStyle,
    smallStyle: TextStyle,
    legendKeys: List<Pair<String, Color>>,
): WindChartTexts = WindChartTexts(
    axisTitle = measureCanvasText("${altitudeAxisUnitLabel(units)} ${labels.aboveSeaLevel}", smallStyle),
    surfaceRowTitle = measureCanvasText("10 m", axisStyle),
    legendScale = WIND_LEGEND_SPEEDS_KMH.map { speedKmh ->
        measureCanvasText(formatWindSpeed(speedKmh, units, withUnit = false), smallStyle)
    },
    legendUnit = measureCanvasText(units.windSpeed.label, smallStyle),
    legendKeys = legendKeys.map { (text, color) ->
        measureCanvasText(text, smallStyle.copy(color = color, fontWeight = FontWeight.Bold)) to color
    },
)

/** Draws a measured line with its baseline at [baselineY], placed like [drawCanvasText] places it. */
private fun DrawScope.drawMeasuredText(
    layout: TextLayoutResult,
    x: Float,
    baselineY: Float,
    anchor: CanvasTextAnchor = CanvasTextAnchor.START,
) {
    val left = when (anchor) {
        CanvasTextAnchor.START -> x
        CanvasTextAnchor.CENTER -> x - layout.size.width / 2f
        CanvasTextAnchor.END -> x - layout.size.width
    }
    drawText(textLayoutResult = layout, topLeft = Offset(left, baselineY - layout.firstBaseline))
}

/**
 * A glyph ready to draw: the arrow and speed pill of [sample], or the "no data" mark of the 10 m row when [sample]
 * is null. [arrowLength] is 0 where only the pill fits, and [label] is null where only the arrow does.
 */
private class WindGlyph(
    val sample: WindSample?,
    val arrowCenter: Offset,
    val arrowLength: Float,
    val label: WindPillLabel?,
    val pillTopLeft: Offset,
) {
    /** Where the cursor reticle marks this glyph: on its arrow, or on its pill where the arrow is left out. */
    val reticleCenter: Offset
        get() = if (arrowLength > 0f || label == null) {
            arrowCenter
        } else {
            pillTopLeft + Offset(label.size.width / 2f, label.size.height / 2f)
        }
}

/**
 * The glyphs to draw, in drawing order, and each glyph column's 10 m glyph by hour index, which the cursor marks
 * where it is drawn.
 */
private class WindGlyphs(
    val all: List<WindGlyph>,
    val surfaceByHourIndex: Map<Int, WindGlyph>,
)

/**
 * A cursor readout; [x] and [y] are where the reticle sits: on the hour's column, on the snapped glyph when there
 * is one, and on the drawn 10 m glyph in the 10 m row.
 */
internal data class WindCursor(
    val hour: Int,
    val altitudeKm: Float,
    val sample: WindSample?,
    val isSurface10m: Boolean,
    val x: Float,
    val y: Float,
)

/**
 * Upper-air source levels of one hour, their true screen Ys, and the levels whose glyphs the hour's
 * column draws (or would draw, for columns between glyph columns).
 */
private class WindHourLevels(
    val levels: List<WindSample>,
    val anchorYs: List<Float>,
    val drawn: List<ReadableWindLevel>,
)

/** Lays out one hour's levels; a [column] that stacks its pills spaces its glyphs for them. */
private fun layoutWindLevels(
    profile: List<WindSample>,
    elevationKm: Float,
    topAltitudeKm: Float,
    g: WindChartGeometry,
    column: WindGlyphColumn?,
): WindHourLevels {
    val levels = profile.filter { !it.isSurface10m }
    val anchorYs = levels.map { altitudeToY(it.altitudeKm, elevationKm, topAltitudeKm, 0f, g.plotBottom) }
    val drawn = if (g.drawsGlyphs) {
        pickReadableWindLevels(
            anchorYs,
            plotTop = 0f,
            plotBottom = g.plotBottom,
            glyphHalfHeight = g.glyphHalfHeight,
            minSpacing = g.levelSpacing,
            stackedPill = g.stackedPill.takeIf { column?.pills == WindPillPlacement.STACKED },
        )
    } else {
        emptyList()
    }
    return WindHourLevels(levels, anchorYs, drawn)
}

private fun buildWindGlyphs(
    hours: List<Int>,
    profiles: Map<Int, List<WindSample>>,
    elevationKm: Float,
    topAltitudeKm: Float,
    labels: WindSpeedLabels,
    g: WindChartGeometry,
): WindGlyphs {
    if (!g.drawsGlyphs) return WindGlyphs(emptyList(), emptyMap())
    val all = mutableListOf<WindGlyph>()
    val surfaceByHourIndex = mutableMapOf<Int, WindGlyph>()
    g.glyphColumns.forEach { column ->
        val x = g.columnCenter(column.index)
        val profile = profiles[hours[column.index]].orEmpty()
        val layout = layoutWindLevels(profile, elevationKm, topAltitudeKm, g, column)
        layout.drawn.forEach { level ->
            val sample = layout.levels[level.index]
            val label = labels[sample]?.takeIf { column.pills != WindPillPlacement.NONE }
            val pillTopLeft = g.pillTopLeft(column, x, level.pillCenterY, label)
            all += WindGlyph(sample, Offset(x, level.drawY), g.arrowLength, label, pillTopLeft)
        }
        val surface = g.surfaceGlyph(column, profile.firstOrNull { it.isSurface10m }, labels)
        all += surface
        surfaceByHourIndex[column.index] = surface
    }
    return WindGlyphs(all, surfaceByHourIndex)
}

/**
 * The 10 m glyph of [column], in the one-line row under the plot: the arrow centered on the column with its pill
 * beside it, as in the plot. A column that stacks its pills lays it out with [stackedSurfaceGlyph].
 */
private fun WindChartGeometry.surfaceGlyph(
    column: WindGlyphColumn,
    sample: WindSample?,
    labels: WindSpeedLabels,
): WindGlyph {
    val label = sample?.let { labels[it] }?.takeIf { column.pills != WindPillPlacement.NONE }
    if (sample == null || label == null || column.pills != WindPillPlacement.STACKED) {
        val x = columnCenter(column.index)
        return WindGlyph(
            sample, Offset(x, surfaceRowCenterY), arrowLength, label,
            pillTopLeft(column, x, surfaceRowCenterY, label),
        )
    }
    return stackedSurfaceGlyph(column, sample, label)
}

/**
 * The 10 m glyph of a column that stacks its pills, inside the column's pill span so it stays clear of the
 * previous column's 10 m pill and of the right edge. The row is one line high, too low for a full arrow above a
 * pill, so the glyph keeps whichever arrangement leaves the longer arrow: one line, the arrow (shortened if
 * needed) then the pill, with the arrow as close to the column center as the right edge allows; or a short arrow
 * under the pill. Where neither leaves a readable arrow, only the pill shows.
 */
private fun WindChartGeometry.stackedSurfaceGlyph(
    column: WindGlyphColumn,
    sample: WindSample,
    label: WindPillLabel,
): WindGlyph {
    val x = columnCenter(column.index)
    val (pillWidth, pillHeight) = label.size
    val lineArrow = min(arrowLength, column.pillSpanWidth - pillGap - pillWidth)
    val compactArrow = min(
        arrowLength, surfaceRowHeight - pillHeight - 2f * pillEdgeInset - 2f * compactArrowPadding,
    )
    val pillLeft = column.stackedPillLeft(x, pillWidth)
    val linePillTop = surfaceRowCenterY - pillHeight / 2f
    return when {
        max(lineArrow, compactArrow) < minArrowLength ->
            WindGlyph(sample, Offset(x, surfaceRowCenterY), 0f, label, Offset(pillLeft, linePillTop))
        lineArrow >= compactArrow -> {
            val arrowX = column.lineArrowX(x, lineArrow, pillGap, pillWidth)
            val pillTopLeft = Offset(arrowX + lineArrow / 2f + pillGap, linePillTop)
            WindGlyph(sample, Offset(arrowX, surfaceRowCenterY), lineArrow, label, pillTopLeft)
        }
        else -> {
            val pillTop = plotBottom + pillEdgeInset
            val arrowY = (pillTop + pillHeight + plotBottom + surfaceRowHeight - pillEdgeInset) / 2f
            WindGlyph(sample, Offset(pillLeft + pillWidth / 2f, arrowY), compactArrow, label, Offset(pillLeft, pillTop))
        }
    }
}

private fun resolveWindCursor(
    pos: Offset,
    hours: List<Int>,
    profiles: Map<Int, List<WindSample>>,
    elevationKm: Float,
    topAltitudeKm: Float,
    g: WindChartGeometry,
    glyphs: WindGlyphs,
): WindCursor? {
    if (!g.cursorAreaContains(pos)) return null
    val hourIndex = windHourIndexAtX(pos.x, g.plotLeft, g.columnWidth, hours.size) ?: return null
    val hour = hours[hourIndex]
    val profile = profiles[hour].orEmpty()
    val x = g.columnCenter(hourIndex)
    if (pos.y > g.plotBottom) {
        val surface = profile.firstOrNull { it.isSurface10m }
        // On the drawn 10 m glyph: a column that stacks its pills can move its 10 m arrow off the column center.
        val reticle = glyphs.surfaceByHourIndex[hourIndex]?.reticleCenter ?: Offset(x, g.surfaceRowCenterY)
        return WindCursor(hour, elevationKm, surface, isSurface10m = true, x = reticle.x, y = reticle.y)
    }
    // Snap to the glyph under the finger, at its arrow or its pill, so the readout reports that exact model value.
    // Columns between glyph columns snap to the glyphs they would draw beside their arrows; a column that stacks its
    // pills snaps to the glyphs it draws.
    val layout = layoutWindLevels(profile, elevationKm, topAltitudeKm, g, g.glyphColumn(hourIndex))
    val snapped = snapWindLevel(layout.anchorYs, layout.drawn, pos.y, 0f..g.plotBottom, g.snapDistance)
    if (snapped != null) {
        val level = layout.levels[snapped.index]
        return WindCursor(hour, level.altitudeKm, level, isSurface10m = false, x = x, y = snapped.drawY)
    }
    val altitude = yToAltitude(pos.y, elevationKm, topAltitudeKm, 0f, g.plotBottom)
    return WindCursor(hour, altitude, interpolateWind(profile, altitude), isSurface10m = false, x = x, y = pos.y)
}

/**
 * Moves the cursor with a single pointer; [onCursorMove] receives null where the cursor is dismissed.
 *
 * A gesture that goes down where the cursor reads values ([ownsGestureAt]) consumes its moves, so a
 * scrolling parent stays still while the crosshair follows, and keeps the crosshair on the nearest edge of that
 * area ([clampToOwnedArea]) where the finger leaves it. One that goes down elsewhere (altitude axis, legend, hour
 * labels) dismisses the cursor and leaves the drag to the parent, so short screens stay scrollable. The pinch zoom
 * handler sits inside this one and sees each event before it is consumed.
 */
private suspend fun PointerInputScope.detectWindCursorGestures(
    ownsGestureAt: (Offset) -> Boolean,
    clampToOwnedArea: (Offset) -> Offset,
    onCursorMove: (Offset?) -> Unit,
) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        if (!ownsGestureAt(down.position)) {
            // Nothing outside the area is kept, so a later layout change cannot reveal a cursor there.
            onCursorMove(null)
            return@awaitEachGesture
        }
        onCursorMove(down.position)
        do {
            val event = awaitPointerEvent()
            val single = event.changes.singleOrNull()
            if (single != null && single.pressed && !single.isConsumed) {
                onCursorMove(clampToOwnedArea(single.position))
            }
            event.changes.forEach { change ->
                if (change.positionChanged()) change.consume()
            }
        } while (event.changes.any { it.pressed })
    }
}

/** One slice of an hourly column, filled with the interpolated wind colour. */
private class WindBackgroundStrip(
    val topLeft: Offset,
    val size: Size,
    val color: Color,
)

/**
 * Keeps the background strips for one canvas size, so redrawing for a crosshair move does not interpolate
 * every strip again. Create a new cache when the chart, profiles, altitude range, density or pill height (which
 * sets the plot height through the 10 m row) change.
 */
private class WindBackgroundCache {
    private var cachedSize = Size.Unspecified
    private var cachedStrips: List<WindBackgroundStrip> = emptyList()

    fun strips(size: Size, build: () -> List<WindBackgroundStrip>): List<WindBackgroundStrip> {
        if (size != cachedSize) {
            cachedStrips = build()
            cachedSize = size
        }
        return cachedStrips
    }
}

private fun buildWindBackground(
    hours: List<Int>,
    profiles: Map<Int, List<WindSample>>,
    elevationKm: Float,
    topAltitudeKm: Float,
    g: WindChartGeometry,
): List<WindBackgroundStrip> = buildList {
    hours.forEachIndexed { hourIndex, hour ->
        val profile = profiles[hour] ?: return@forEachIndexed
        val x = g.plotLeft + hourIndex * g.columnWidth
        var stripTop = 0f
        while (stripTop < g.plotBottom) {
            val stripBottom = min(stripTop + g.backgroundStripHeight, g.plotBottom)
            val sampleAltitudeKm = yToAltitude(
                y = (stripTop + stripBottom) / 2f,
                minAltitudeKm = elevationKm,
                maxAltitudeKm = topAltitudeKm,
                plotTop = 0f,
                plotBottom = g.plotBottom,
            )
            val sample = interpolateWind(profile, sampleAltitudeKm)
            if (sample != null) {
                add(
                    WindBackgroundStrip(
                        topLeft = Offset(x, stripTop),
                        size = Size(g.columnWidth, stripBottom - stripTop),
                        color = windSpeedBgColor(sample.speedKmh),
                    ),
                )
            }
            stripTop = stripBottom
        }
    }
}

/** The cursor readout: the hour and height line, then the value line; the chart's description reads them out. */
internal fun windTooltipLines(cursor: WindCursor, units: DisplayUnits, labels: WindChartLabels): List<String> {
    val altitude = if (cursor.isSurface10m) {
        "10 m ${labels.aboveGround}"
    } else {
        "${sourceAltitudeLabel(cursor.altitudeKm, units)} ${labels.aboveSeaLevel}"
    }
    val sample = cursor.sample
    return listOf(
        "${formatPaddedInt(cursor.hour, minimumDigits = 2)}h  $altitude",
        if (sample == null) labels.noData else {
            val prefix = if (sample.isInterpolated) "≈ " else ""
            "$prefix${formatWindSpeed(sample.speedKmh, units)}  ${sample.directionDeg.roundToInt() % 360}°"
        },
    )
}

private fun sourceAltitudeLabel(altitudeKm: Float, units: DisplayUnits): String {
    val altitude = when (units.altitude) {
        AltitudeUnit.METERS -> altitudeKm * 1000f
        AltitudeUnit.FEET -> altitudeKm * 3280.84f
    }
    return "${altitude.roundToInt()} ${units.altitude.shortLabel}"
}

/**
 * Draws a glyph laid out by [buildWindGlyphs]: the speed pill, then the arrow over it. Placement and text layout
 * are done once there, so frames only draw.
 */
private fun DrawScope.drawWindGlyph(
    glyph: WindGlyph,
    noData: TextLayoutResult,
    foreground: Color,
    surface: Color,
) {
    val sample = glyph.sample
    if (sample == null) {
        // Only the 10 m row has fixed slots; missing values there read as "no data", not calm.
        drawText(
            textLayoutResult = noData,
            topLeft = Offset(
                glyph.arrowCenter.x - noData.size.width / 2f,
                glyph.arrowCenter.y + 4.dp.toPx() - noData.firstBaseline,
            ),
        )
        return
    }
    glyph.label?.let { drawWindPill(it, glyph.pillTopLeft, foreground, surface) }
    if (glyph.arrowLength > 0f) {
        drawWindArrow(
            center = glyph.arrowCenter,
            directionDeg = sample.directionDeg,
            speedKmh = sample.speedKmh,
            shaftLength = glyph.arrowLength,
            foreground = foreground,
            surface = surface,
        )
    }
}

private fun DrawScope.drawWindPill(
    label: WindPillLabel,
    topLeft: Offset,
    foreground: Color,
    surface: Color,
) {
    val radius = CornerRadius(label.size.height / 2f)
    drawRoundRect(surface.copy(alpha = 0.86f), topLeft, label.size, radius)
    drawRoundRect(foreground.copy(alpha = 0.4f), topLeft, label.size, radius, style = Stroke(0.75.dp.toPx()))
    drawText(
        textLayoutResult = label.layout,
        topLeft = topLeft + Offset(
            (label.size.width - label.layout.size.width) / 2f,
            (label.size.height - label.layout.size.height) / 2f,
        ),
    )
}

/** Size of the speed pill around a measured label; also used to space the glyph columns. */
private fun Density.windPillSize(layout: TextLayoutResult): Size {
    val pillHeight = max(WIND_PILL_MIN_SIZE.toPx(), layout.size.height.toFloat())
    val pillWidth = max(pillHeight, layout.size.width + WIND_PILL_TEXT_PADDING.toPx())
    return Size(pillWidth, pillHeight)
}

private fun DrawScope.drawWindArrow(
    center: Offset,
    directionDeg: Float,
    speedKmh: Float,
    shaftLength: Float,
    foreground: Color,
    surface: Color,
) {
    if (speedKmh < 0.05f) {
        drawCircle(surface, 4.dp.toPx(), center)
        drawCircle(foreground, 3.dp.toPx(), center, style = Stroke(1.25.dp.toPx()))
        return
    }
    // Meteorological direction is where the wind comes FROM; the arrow points where it goes.
    val angle = (directionDeg + 90f) * PI.toFloat() / 180f
    val direction = Offset(cos(angle), sin(angle))
    val tip = center + direction * (shaftLength / 2f)
    val tail = center - direction * (shaftLength / 2f)
    val headLength = 5.dp.toPx()
    val path = Path().apply {
        moveTo(tail.x, tail.y)
        lineTo(tip.x, tip.y)
        moveTo(tip.x - cos(angle - PI.toFloat() / 6f) * headLength, tip.y - sin(angle - PI.toFloat() / 6f) * headLength)
        lineTo(tip.x, tip.y)
        lineTo(tip.x - cos(angle + PI.toFloat() / 6f) * headLength, tip.y - sin(angle + PI.toFloat() / 6f) * headLength)
    }
    val halo = Stroke(4.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
    drawPath(path, surface.copy(alpha = 0.9f), style = halo)
    drawPath(path, foreground, style = Stroke(1.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
}

private fun DrawScope.drawLevelLine(
    markers: List<WindLevelMarker>,
    hours: List<Int>,
    g: WindChartGeometry,
    minAltitudeKm: Float,
    maxAltitudeKm: Float,
    color: Color,
    halo: Color,
    widthDp: Float,
) {
    val path = Path()
    var started = false
    markers.forEach { marker ->
        val hourIndex = hours.indexOf(marker.hour)
        if (hourIndex < 0) return@forEach
        val y = altitudeToY(marker.altitudeKm, minAltitudeKm, maxAltitudeKm, 0f, g.plotBottom)
        if (y !in 0f..g.plotBottom) return@forEach
        val x = g.columnCenter(hourIndex)
        if (started) path.lineTo(x, y) else path.moveTo(x, y)
        started = true
    }
    if (!started) return
    drawPath(path, halo.copy(alpha = 0.6f), style = Stroke((widthDp + 1.5f).dp.toPx(), cap = StrokeCap.Round))
    drawPath(path, color, style = Stroke(widthDp.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
}

/**
 * One legend line: speed scale, then CCL and 0 °C line keys on the right. Every swatch is drawn; speed labels too
 * wide for their swatches at large font scales or with m/s values are thinned out and kept clear of the unit label
 * and the line keys ([windLegendLabelCenters]).
 */
private fun DrawScope.drawWindLegend(
    texts: WindChartTexts,
    g: WindChartGeometry,
) {
    val top = g.plotBottom + g.surfaceRowHeight
    val keyWidth = WIND_LEGEND_KEY_WIDTH.toPx()
    val keyInset = WIND_LEGEND_KEY_INSET.toPx()
    val scaleRight = size.width - texts.legendKeys.size * keyWidth
    val itemWidth = (scaleRight - g.plotLeft) / WIND_LEGEND_SPEEDS_KMH.size
    val swatchTop = top + 5.dp.toPx()
    val labelBaseline = top + g.legendHeight - WIND_LEGEND_BASELINE_INSET.toPx()
    val unitLeft = 8.dp.toPx()
    val labelGap = WIND_LEGEND_LABEL_GAP.toPx()
    val labelsRight = if (texts.legendKeys.isEmpty()) size.width else scaleRight + keyInset - labelGap
    val labelCenters = windLegendLabelCenters(
        labelWidths = texts.legendScale.map { it.size.width.toFloat() },
        left = g.plotLeft,
        itemWidth = itemWidth,
        bounds = (unitLeft + texts.legendUnit.size.width + labelGap)..labelsRight,
        gap = labelGap,
    )
    WIND_LEGEND_SPEEDS_KMH.forEachIndexed { index, speed ->
        val x = g.plotLeft + index * itemWidth
        drawRoundRect(
            windSpeedColor(speed).copy(alpha = 0.92f),
            Offset(x + itemWidth * 0.15f, swatchTop),
            Size(itemWidth * 0.7f, 6.dp.toPx()), CornerRadius(2.dp.toPx()),
        )
        labelCenters[index]?.let { centerX ->
            drawMeasuredText(texts.legendScale[index], centerX, labelBaseline, CanvasTextAnchor.CENTER)
        }
    }
    drawMeasuredText(texts.legendUnit, unitLeft, labelBaseline)
    texts.legendKeys.forEachIndexed { index, (layout, color) ->
        val x = scaleRight + index * keyWidth + keyInset
        drawLine(
            color, Offset(x, swatchTop + 3.dp.toPx()), Offset(x + 30.dp.toPx(), swatchTop + 3.dp.toPx()),
            strokeWidth = 2.5.dp.toPx(), cap = StrokeCap.Round,
        )
        drawMeasuredText(layout, x, labelBaseline)
    }
}

/** Background color for interpolated wind samples. */
private fun windSpeedBgColor(speedKmh: Float): Color {
    return windSpeedColor(speedKmh).copy(alpha = 0.68f)
}

private fun altitudeToY(
    altitudeKm: Float,
    minAltitudeKm: Float,
    maxAltitudeKm: Float,
    plotTop: Float,
    plotBottom: Float,
): Float {
    val normalizedAltitude = (altitudeKm - minAltitudeKm) / (maxAltitudeKm - minAltitudeKm)
    return plotBottom - normalizedAltitude * (plotBottom - plotTop)
}

private fun yToAltitude(
    y: Float,
    minAltitudeKm: Float,
    maxAltitudeKm: Float,
    plotTop: Float,
    plotBottom: Float,
): Float {
    val normalizedAltitude = (plotBottom - y) / (plotBottom - plotTop)
    return minAltitudeKm + normalizedAltitude * (maxAltitudeKm - minAltitudeKm)
}

private fun buildAltitudeTicks(
    minAltitudeKm: Float,
    maxAltitudeKm: Float,
    stepKm: Float,
): List<Float> {
    val ticks = mutableListOf(minAltitudeKm)
    var nextTick = ceil(minAltitudeKm / stepKm) * stepKm
    while (nextTick < maxAltitudeKm) {
        if (nextTick > minAltitudeKm + 0.001f) ticks += nextTick
        nextTick += stepKm
    }
    if (maxAltitudeKm - ticks.last() > 0.001f) ticks += maxAltitudeKm
    return ticks.distinctBy { (it * 100f).toInt() }
}

/** Drops the ground tick when the next round tick would crowd it on the axis. */
private fun ticksToLabels(ticks: List<Float>): List<Float> {
    if (ticks.size < 3) return ticks
    val step = ticks[2] - ticks[1]
    return if (ticks[1] - ticks[0] < step * 0.35f) ticks.drop(1) else ticks
}

private const val WIND_MIN_VISIBLE_ALTITUDE_RANGE_KM = 0.75f
private val CCL_COLOR = Color(0xFFFF8C00)
private val FREEZING_COLOR = Color(0xFF00BCD4)
private val WIND_AXIS_WIDTH = 60.dp
private val WIND_SURFACE_ROW_HEIGHT = 26.dp
private val WIND_LEGEND_HEIGHT = 28.dp
private val WIND_TIME_AXIS_HEIGHT = 16.dp
private val WIND_SURFACE_ROW_PADDING = 8.dp
private val WIND_LEGEND_BASELINE_INSET = 6.dp

// Legend speed scale steps (km/h), and the CCL and 0 °C keys right of it: each key's line and label start
// WIND_LEGEND_KEY_INSET into its slot.
private val WIND_LEGEND_SPEEDS_KMH = listOf(0f, 5f, 10f, 15f, 20f, 30f, 40f, 50f, 60f)
private val WIND_LEGEND_KEY_WIDTH = 44.dp
private val WIND_LEGEND_KEY_INSET = 6.dp

// Least room between two legend labels, and between a label and the unit label or a line key.
private val WIND_LEGEND_LABEL_GAP = 1.dp

/** Height of a row of axis text: [base] at the default font scale, growing proportionally above it. */
private fun windTextRowHeight(base: Dp, density: Float, fontScale: Float): Float =
    base.value * density * max(1f, fontScale)
private val WIND_ARROW_LENGTH = 16.dp
private val WIND_PILL_GAP = 3.dp
private val WIND_PILL_MIN_SIZE = 14.dp
private val WIND_PILL_TEXT_PADDING = 6.dp
private val WIND_PILL_EDGE_MARGIN = 2.dp

// Between a pill and the top or bottom edge of the plot or the 10 m row.
private val WIND_PILL_EDGE_INSET = 1.dp

// Between vertically adjacent pills beside their arrows, once a large font scale makes pills taller than arrows.
private val WIND_PILL_VERTICAL_GAP = 4.dp

// Above and below the short arrow under a pill in the 10 m row of a column that stacks its pills.
private val WIND_COMPACT_ARROW_PADDING = 1.dp

// Where room is this tight, the arrow is left out instead.
private val WIND_MIN_ARROW_LENGTH = 6.dp

// Room between a pill and the next glyph's arrow, which reaches 2 dp past its shaft with the halo.
private val WIND_GLYPH_CLEARANCE = 4.dp
private val WIND_GLYPH_SLOT_WIDTH = 44.dp
private val WIND_GLYPH_HALF_HEIGHT = 8.dp
private val WIND_LEVEL_SPACING = 20.dp
private val WIND_CURSOR_SNAP_DISTANCE = 12.dp
private val WIND_BACKGROUND_STRIP_HEIGHT = 2.dp

// A wide phone: every glyph column, the trailing 21h one included, keeps its pills beside its arrows, in the 10 m
// row too.
@Preview(name = "Wind Default", showBackground = true, widthDp = 420, heightDp = 720)
@Composable
private fun WindForecastViewPreview() {
    ForecastPreviewTheme {
        WindForecastView(
            uiState = ForecastPreviewData.stateForMode(ForecastMode.WIND),
        )
    }
}

@Preview(name = "Wind Zoomed Out", showBackground = true, widthDp = 420, heightDp = 720)
@Composable
private fun WindForecastViewZoomedOutPreview() {
    ForecastPreviewTheme {
        WindForecastView(
            uiState = ForecastPreviewData.stateForMode(
                mode = ForecastMode.WIND,
                topAltitudeKm = 6.5f,
            ),
        )
    }
}

// Sparse, close and changing source heights, calm wind, and hours without data: the 07h and 09h 10 m slots read
// "—", and 09h has no wind at all.
@Preview(name = "Wind Sparse Sources", showBackground = true, widthDp = 420, heightDp = 720)
@Composable
private fun WindForecastViewSparsePreview() {
    ForecastPreviewTheme {
        WindForecastView(uiState = ForecastPreviewData.sparseWindState())
    }
}

// A narrow phone: the last glyph column (21h) has no room right of its arrows, so it stacks its pills above them.
// Its 10 m glyph stays on one line, the arrow moved left of the column center so that the pill ends at the right
// margin.
@Preview(name = "Wind Phone", showBackground = true, widthDp = 360, heightDp = 640)
@Composable
private fun WindForecastViewPhonePreview() {
    ForecastPreviewTheme {
        WindForecastView(uiState = ForecastPreviewData.stateForMode(ForecastMode.WIND))
    }
}

// A narrow phone with m/s labels, the widest pills: the stacked 21h column keeps its pills inside the chart, and
// its 10 m glyph stays on one line with a shortened arrow.
@Preview(name = "Wind Phone m/s", showBackground = true, widthDp = 360, heightDp = 640)
@Composable
private fun WindForecastViewPhoneMpsPreview() {
    ForecastPreviewTheme {
        WindForecastView(
            uiState = ForecastPreviewData.stateForMode(ForecastMode.WIND)
                .copy(displayUnits = UnitPreset.METRIC_MPS.resolveDisplayUnits()),
        )
    }
}

// Doubled font scale: taller pills spread the levels and keep edge pills inside the plot. The stacked 21h column
// has no room for a readable 10 m arrow, so its 10 m row shows the pill alone.
@Preview(name = "Wind Phone Large Font", showBackground = true, widthDp = 360, heightDp = 640, fontScale = 2f)
@Composable
private fun WindForecastViewPhoneLargeFontPreview() {
    ForecastPreviewTheme {
        WindForecastView(uiState = ForecastPreviewData.stateForMode(ForecastMode.WIND))
    }
}

// m/s labels at 1.5x font scale: the legend draws every second speed label under all of its swatches, and the
// trailing 22h column, without room for any pill, draws its arrows alone, in the 10 m row too.
@Preview(name = "Wind Phone m/s Large Font", showBackground = true, widthDp = 360, heightDp = 640, fontScale = 1.5f)
@Composable
private fun WindForecastViewPhoneMpsLargeFontPreview() {
    ForecastPreviewTheme {
        WindForecastView(
            uiState = ForecastPreviewData.stateForMode(ForecastMode.WIND)
                .copy(displayUnits = UnitPreset.METRIC_MPS.resolveDisplayUnits()),
        )
    }
}

// Between phone and tablet widths every second hour draws glyphs. The trailing 22h column stacks its pills, and
// its 10 m row, too narrow for a one-line glyph, draws a short arrow under the pill.
@Preview(name = "Wind Large Phone", showBackground = true, widthDp = 450, heightDp = 720)
@Composable
private fun WindForecastViewLargePhonePreview() {
    ForecastPreviewTheme {
        WindForecastView(uiState = ForecastPreviewData.stateForMode(ForecastMode.WIND))
    }
}

@Preview(
    name = "Wind Dark",
    showBackground = true,
    widthDp = 420,
    heightDp = 720,
)
@Composable
private fun WindForecastViewDarkPreview() {
    ForecastPreviewTheme(darkTheme = true) {
        WindForecastView(
            uiState = ForecastPreviewData.stateForMode(ForecastMode.WIND),
        )
    }
}
