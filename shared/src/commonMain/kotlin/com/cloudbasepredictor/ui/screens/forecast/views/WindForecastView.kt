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
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cloudbasepredictor.data.units.AltitudeUnit
import com.cloudbasepredictor.data.units.DisplayUnits
import com.cloudbasepredictor.data.units.altitudeAxisUnitLabel
import com.cloudbasepredictor.data.units.formatAltitudeAxisValue
import com.cloudbasepredictor.data.units.formatWindSpeed
import com.cloudbasepredictor.model.ForecastMode
import com.cloudbasepredictor.ui.preview.ForecastPreviewData
import com.cloudbasepredictor.ui.preview.ForecastPreviewTheme
import com.cloudbasepredictor.ui.screens.forecast.ForecastReadyUiState
import com.cloudbasepredictor.ui.screens.forecast.ForecastTestTags.WIND_CHART_CANVAS
import com.cloudbasepredictor.ui.screens.forecast.ForecastTestTags.WIND_RENDERER_DESCRIPTION
import com.cloudbasepredictor.ui.screens.forecast.ForecastTestTags.WIND_TIME_AXIS
import com.cloudbasepredictor.ui.screens.forecast.ForecastTestTags.WIND_VIEW
import com.cloudbasepredictor.ui.screens.forecast.WindForecastChartUiModel
import com.cloudbasepredictor.ui.screens.forecast.WindLevelMarker
import com.cloudbasepredictor.ui.screens.forecast.WindSample
import com.cloudbasepredictor.ui.screens.forecast.buildWindProfiles
import com.cloudbasepredictor.ui.screens.forecast.interpolateWind
import com.cloudbasepredictor.ui.screens.forecast.pickReadableWindLevels
import com.cloudbasepredictor.ui.screens.forecast.windGlyphHourStride
import com.cloudbasepredictor.ui.screens.forecast.windHourIndexAtX
import com.cloudbasepredictor.ui.screens.forecast.windSpeedColor
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

@Composable
fun WindForecastView(
    uiState: ForecastReadyUiState,
    modifier: Modifier = Modifier,
    onVisibleTopAltitudeChange: (Float) -> Unit = {},
    overlayBackHandler: ForecastOverlayBackHandler = ignoreForecastOverlayBack,
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
            onVisibleTopAltitudeChange = onVisibleTopAltitudeChange,
            overlayBackHandler = overlayBackHandler,
            modifier = Modifier.fillMaxSize(),
        )

        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(WIND_TIME_AXIS_HEIGHT)
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
    var crosshairPos by remember(chart) { mutableStateOf<Offset?>(null) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    val profileByHour = remember(chart.cells) { buildWindProfiles(chart.cells) }
    val geometry = WindChartGeometry(
        width = canvasSize.width.toFloat(),
        height = canvasSize.height.toFloat(),
        hourCount = chart.hours.size,
        density = density.density,
    )
    val effectiveTopAltitudeKm = max(
        elevationKm + visibleTopAltitudeKm, elevationKm + WIND_MIN_VISIBLE_ALTITUDE_RANGE_KM,
    )
    val glyphs = remember(chart, canvasSize, elevationKm, effectiveTopAltitudeKm, density) {
        buildWindGlyphs(chart.hours, profileByHour, elevationKm, effectiveTopAltitudeKm, geometry)
    }
    val cursor = crosshairPos?.let { pos ->
        resolveWindCursor(pos, chart.hours, profileByHour, elevationKm, effectiveTopAltitudeKm, geometry)
    }
    val tooltipLines = cursor?.let { windTooltipLines(it, displayUnits) }.orEmpty()
    val latestVisibleTopAltitudeKm = rememberUpdatedState(visibleTopAltitudeKm)

    // Back dismisses the tap overlay (crosshair tooltip) first, before navigating away.
    overlayBackHandler(cursor != null) {
        crosshairPos = null
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
                awaitPointerEventScope {
                    while (true) {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        crosshairPos = down.position
                        do {
                            val event = awaitPointerEvent()
                            val primary = event.changes.firstOrNull() ?: break
                            if (primary.pressed && event.changes.size == 1) {
                                crosshairPos = primary.position
                            }
                        } while (event.changes.any { it.pressed })
                    }
                }
            }
            .pointerInput(Unit) {
                detectForecastZoomGestures(
                    currentTopAltitudeKm = { latestVisibleTopAltitudeKm.value },
                    onVisibleTopAltitudeChange = onVisibleTopAltitudeChange,
                )
            },
    ) {
        drawRect(color = surfaceColor, size = size)

        val g = WindChartGeometry(size.width, size.height, chart.hours.size, density.density)
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
        val backgroundStripHeightPx = max(1f, ceil(2.dp.toPx()))
        chart.hours.forEachIndexed { hourIndex, hour ->
            val profile = profileByHour[hour] ?: return@forEachIndexed
            val x = plotLeft + hourIndex * columnWidth
            var stripTop = plotTop
            while (stripTop < plotBottom) {
                val stripBottom = min(stripTop + backgroundStripHeightPx, plotBottom)
                val sampleAltitudeKm = yToAltitude(
                    y = (stripTop + stripBottom) / 2f,
                    minAltitudeKm = minAltitudeKm,
                    maxAltitudeKm = effectiveTopAltitudeKm,
                    plotTop = plotTop,
                    plotBottom = plotBottom,
                )
                val sample = interpolateWind(profile, sampleAltitudeKm)
                if (sample != null) {
                    drawRect(
                        color = windSpeedBgColor(sample.speedKmh),
                        topLeft = Offset(x, stripTop),
                        size = Size(columnWidth, stripBottom - stripTop),
                    )
                }
                stripTop = stripBottom
            }
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

        val altitudeTicks = buildAltitudeTicks(
            minAltitudeKm = minAltitudeKm,
            maxAltitudeKm = effectiveTopAltitudeKm,
            stepKm = if (effectiveTopAltitudeKm <= 3.5f) 0.5f else 1f,
        )
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
        glyphs.forEach { glyph ->
            drawWindGlyph(glyph, displayUnits, textMeasurer, speedLabelStyle, onSurfaceColor, surfaceColor, size.width)
        }

        // ── Left axis ─────────────────────────────────────────────
        ticksToLabels(altitudeTicks).forEach { altitude ->
            val y = altitudeToY(altitude, minAltitudeKm, effectiveTopAltitudeKm, plotTop, plotBottom)
            if (y >= 24.dp.toPx()) {
                drawCanvasText(
                    textMeasurer, formatAltitudeAxisValue(altitude, displayUnits),
                    8.dp.toPx(), min(y + 4.dp.toPx(), plotBottom - 2.dp.toPx()), axisStyle,
                )
            }
        }
        drawCanvasText(
            textMeasurer, "${altitudeAxisUnitLabel(displayUnits)} ASL",
            8.dp.toPx(), 15.dp.toPx(), axisStyle.copy(fontSize = 9.sp),
        )
        drawCanvasText(
            textMeasurer, "10 m", 8.dp.toPx(), plotBottom + g.surfaceRowHeight / 2f + 4.dp.toPx(), axisStyle,
        )

        drawWindLegend(chart, displayUnits, g, textMeasurer, axisStyle.copy(fontSize = 9.sp))

        // ── Hours ─────────────────────────────────────────────────
        val timeBaseline = size.height - 4.dp.toPx()
        chart.hours.forEachIndexed { index, hour ->
            if (index % g.glyphStride != 0) return@forEachIndexed
            drawCanvasText(
                textMeasurer = textMeasurer,
                text = formatPaddedInt(hour, minimumDigits = 2),
                x = g.columnCenter(index),
                baselineY = timeBaseline,
                style = timeLabelStyle,
                anchor = CanvasTextAnchor.CENTER,
            )
        }

        // ── Crosshair overlay ──────────────────────────────────
        cursor?.let { selected ->
            val pos = crosshairPos ?: return@let
            // Center the reticle on the selected hour's glyphs instead of the raw finger position.
            val selectedIndex = chart.hours.indexOf(selected.hour)
            val cx = if (selectedIndex >= 0) g.columnCenter(selectedIndex) else pos.x.coerceIn(plotLeft, plotRight)
            val cy = if (selected.isSurface10m) {
                plotBottom + g.surfaceRowHeight / 2f
            } else {
                altitudeToY(selected.altitudeKm, minAltitudeKm, effectiveTopAltitudeKm, plotTop, plotBottom)
                    .coerceIn(plotTop, plotBottom)
            }

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

            val tooltipLayouts = tooltipLines.map { textMeasurer.measureCanvasText(it, tooltipStyle) }
            val lineH = 12.sp.toPx() * 1.3f
            val maxTextW = tooltipLayouts.maxOf { it.size.width.toFloat() }
            val padH = 8.dp.toPx()
            val padV = 6.dp.toPx()
            val gap = 6.dp.toPx()
            val ttW = maxTextW + padH * 2
            val ttH = lineH * tooltipLines.size + padV * 2
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
            tooltipLines.forEachIndexed { idx, line ->
                drawCanvasText(
                    textMeasurer = textMeasurer,
                    text = line,
                    x = ttX + padH,
                    baselineY = ttY + padV + (idx + 1) * lineH - lineH * 0.15f,
                    style = tooltipStyle,
                )
            }
        }
    }
}

/** Pixel layout shared by drawing and hit testing. */
private class WindChartGeometry(
    val width: Float,
    val height: Float,
    hourCount: Int,
    density: Float,
) {
    val plotLeft = WIND_AXIS_WIDTH.value * density
    val surfaceRowHeight = WIND_SURFACE_ROW_HEIGHT.value * density
    val legendHeight = WIND_LEGEND_HEIGHT.value * density
    val timeAxisHeight = WIND_TIME_AXIS_HEIGHT.value * density
    val plotBottom = height - surfaceRowHeight - legendHeight - timeAxisHeight
    val columnWidth = if (hourCount > 0) max(0f, width - plotLeft) / hourCount else 0f
    val glyphStride = windGlyphHourStride(columnWidth, WIND_GLYPH_SLOT_WIDTH.value * density)
    val glyphHalfHeight = WIND_GLYPH_HALF_HEIGHT.value * density
    val levelSpacing = WIND_LEVEL_SPACING.value * density
    val snapDistance = WIND_CURSOR_SNAP_DISTANCE.value * density

    fun columnCenter(index: Int): Float = plotLeft + index * columnWidth + columnWidth / 2f
}

private data class WindGlyph(
    val sample: WindSample?,
    val center: Offset,
    val isSurface10m: Boolean,
)

private data class WindCursor(
    val hour: Int,
    val altitudeKm: Float,
    val sample: WindSample?,
    val isSurface10m: Boolean,
)

private fun buildWindGlyphs(
    hours: List<Int>,
    profiles: Map<Int, List<WindSample>>,
    elevationKm: Float,
    topAltitudeKm: Float,
    g: WindChartGeometry,
): List<WindGlyph> {
    if (g.plotBottom <= 2f * g.glyphHalfHeight || g.columnWidth <= 0f) return emptyList()
    return buildList {
        hours.forEachIndexed { index, hour ->
            if (index % g.glyphStride != 0) return@forEachIndexed
            val x = g.columnCenter(index)
            val profile = profiles[hour].orEmpty()
            val levels = profile.filter { !it.isSurface10m }
            val anchorYs = levels.map { altitudeToY(it.altitudeKm, elevationKm, topAltitudeKm, 0f, g.plotBottom) }
            pickReadableWindLevels(
                anchorYs,
                plotTop = 0f,
                plotBottom = g.plotBottom,
                glyphHalfHeight = g.glyphHalfHeight,
                minSpacing = g.levelSpacing,
            ).forEach { level ->
                add(WindGlyph(levels[level.index], Offset(x, level.drawY), isSurface10m = false))
            }
            add(
                WindGlyph(
                    sample = profile.firstOrNull { it.isSurface10m },
                    center = Offset(x, g.plotBottom + g.surfaceRowHeight / 2f),
                    isSurface10m = true,
                ),
            )
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
): WindCursor? {
    if (pos.x < g.plotLeft || pos.x > g.width || pos.y < 0f) return null
    val hourIndex = windHourIndexAtX(pos.x, g.plotLeft, g.columnWidth, hours.size) ?: return null
    val hour = hours[hourIndex]
    val profile = profiles[hour].orEmpty()
    if (pos.y > g.plotBottom) {
        if (pos.y > g.plotBottom + g.surfaceRowHeight) return null
        return WindCursor(hour, elevationKm, profile.firstOrNull { it.isSurface10m }, isSurface10m = true)
    }
    // Snap to a source level near the finger so the readout reports the exact model value.
    val snapped = profile
        .filter { !it.isSurface10m }
        .map { it to abs(altitudeToY(it.altitudeKm, elevationKm, topAltitudeKm, 0f, g.plotBottom) - pos.y) }
        .filter { (_, distance) -> distance <= g.snapDistance }
        .minByOrNull { (_, distance) -> distance }
        ?.first
    if (snapped != null) return WindCursor(hour, snapped.altitudeKm, snapped, isSurface10m = false)
    val altitude = yToAltitude(pos.y, elevationKm, topAltitudeKm, 0f, g.plotBottom)
    return WindCursor(hour, altitude, interpolateWind(profile, altitude), isSurface10m = false)
}

private fun windTooltipLines(cursor: WindCursor, units: DisplayUnits): List<String> {
    val altitude = if (cursor.isSurface10m) "10 m AGL" else sourceAltitudeLabel(cursor.altitudeKm, units) + " ASL"
    val sample = cursor.sample
    return listOf(
        "${formatPaddedInt(cursor.hour, minimumDigits = 2)}h  $altitude",
        if (sample == null) "No data" else {
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
 * The arrow is centered on its hour column at the source height; the speed pill sits to its right,
 * or to its left at the right edge of the chart.
 */
private fun DrawScope.drawWindGlyph(
    glyph: WindGlyph,
    units: DisplayUnits,
    textMeasurer: TextMeasurer,
    style: TextStyle,
    foreground: Color,
    surface: Color,
    chartRight: Float,
) {
    val sample = glyph.sample
    if (sample == null) {
        // Only the 10 m row has fixed slots; missing values there read as "no data", not calm.
        drawCanvasText(
            textMeasurer, "—", glyph.center.x, glyph.center.y + 4.dp.toPx(),
            style.copy(color = foreground.copy(alpha = 0.6f), fontSize = 12.sp), CanvasTextAnchor.CENTER,
        )
        return
    }
    val arrowLength = WIND_ARROW_LENGTH.toPx()
    val text = formatWindSpeed(sample.speedKmh, units, withUnit = false)
    val layout = textMeasurer.measureCanvasText(text, style)
    val pillHeight = max(14.dp.toPx(), layout.size.height.toFloat())
    val pillWidth = max(pillHeight, layout.size.width + 6.dp.toPx())
    val gap = 3.dp.toPx()
    val pillOnRight = glyph.center.x + arrowLength / 2f + gap + pillWidth <= chartRight - 2.dp.toPx()
    val pillLeft = if (pillOnRight) {
        glyph.center.x + arrowLength / 2f + gap
    } else {
        glyph.center.x - arrowLength / 2f - gap - pillWidth
    }
    val pillTopLeft = Offset(pillLeft, glyph.center.y - pillHeight / 2f)
    val radius = CornerRadius(pillHeight / 2f)
    drawRoundRect(surface.copy(alpha = 0.86f), pillTopLeft, Size(pillWidth, pillHeight), radius)
    drawRoundRect(
        foreground.copy(alpha = 0.4f), pillTopLeft, Size(pillWidth, pillHeight), radius,
        style = Stroke(0.75.dp.toPx()),
    )
    drawText(
        textLayoutResult = layout,
        topLeft = Offset(
            pillLeft + (pillWidth - layout.size.width) / 2f,
            glyph.center.y - layout.size.height / 2f,
        ),
    )
    drawWindArrow(
        center = glyph.center,
        directionDeg = sample.directionDeg,
        speedKmh = sample.speedKmh,
        shaftLength = arrowLength,
        foreground = foreground,
        surface = surface,
    )
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

/** One legend line: speed scale, then CCL and 0 °C line keys on the right. */
private fun DrawScope.drawWindLegend(
    chart: WindForecastChartUiModel,
    units: DisplayUnits,
    g: WindChartGeometry,
    textMeasurer: TextMeasurer,
    style: TextStyle,
) {
    val top = g.plotBottom + g.surfaceRowHeight
    val keys = buildList {
        if (chart.cclKm.isNotEmpty()) add("CCL" to CCL_COLOR)
        if (chart.freezingLevelKm.isNotEmpty()) add("0°C" to FREEZING_COLOR)
    }
    val keyWidth = 44.dp.toPx()
    val scaleRight = size.width - keys.size * keyWidth
    val steps = listOf(0f, 5f, 10f, 15f, 20f, 30f, 40f, 50f, 60f)
    val itemWidth = (scaleRight - g.plotLeft) / steps.size
    val swatchTop = top + 5.dp.toPx()
    val labelBaseline = top + 22.dp.toPx()
    steps.forEachIndexed { index, speed ->
        val x = g.plotLeft + index * itemWidth
        drawRoundRect(
            windSpeedColor(speed).copy(alpha = 0.92f),
            Offset(x + itemWidth * 0.15f, swatchTop),
            Size(itemWidth * 0.7f, 6.dp.toPx()), CornerRadius(2.dp.toPx()),
        )
        drawCanvasText(
            textMeasurer, formatWindSpeed(speed, units, withUnit = false),
            x + itemWidth / 2f, labelBaseline, style, CanvasTextAnchor.CENTER,
        )
    }
    drawCanvasText(textMeasurer, units.windSpeed.label, 8.dp.toPx(), labelBaseline, style)
    keys.forEachIndexed { index, (label, color) ->
        val x = scaleRight + index * keyWidth + 6.dp.toPx()
        drawLine(
            color, Offset(x, swatchTop + 3.dp.toPx()), Offset(x + 30.dp.toPx(), swatchTop + 3.dp.toPx()),
            strokeWidth = 2.5.dp.toPx(), cap = StrokeCap.Round,
        )
        drawCanvasText(textMeasurer, label, x, labelBaseline, style.copy(color = color, fontWeight = FontWeight.Bold))
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
private val WIND_ARROW_LENGTH = 16.dp
private val WIND_GLYPH_SLOT_WIDTH = 44.dp
private val WIND_GLYPH_HALF_HEIGHT = 8.dp
private val WIND_LEVEL_SPACING = 20.dp
private val WIND_CURSOR_SNAP_DISTANCE = 12.dp

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

@Preview(name = "Wind Sparse Sources", showBackground = true, widthDp = 420, heightDp = 720)
@Composable
private fun WindForecastViewSparsePreview() {
    ForecastPreviewTheme {
        WindForecastView(uiState = ForecastPreviewData.sparseWindState())
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
