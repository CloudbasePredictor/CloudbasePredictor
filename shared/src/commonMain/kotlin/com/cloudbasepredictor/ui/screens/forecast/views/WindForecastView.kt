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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
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
import com.cloudbasepredictor.data.units.DisplayUnits
import com.cloudbasepredictor.data.units.AltitudeUnit
import com.cloudbasepredictor.data.units.altitudeAxisUnitLabel
import com.cloudbasepredictor.data.units.formatAltitudeAxisValue
import com.cloudbasepredictor.data.units.formatWindSpeed
import com.cloudbasepredictor.model.ForecastMode
import com.cloudbasepredictor.ui.preview.ForecastPreviewData
import com.cloudbasepredictor.ui.preview.ForecastPreviewTheme
import com.cloudbasepredictor.ui.screens.forecast.ForecastTestTags.WIND_CHART_CANVAS
import com.cloudbasepredictor.ui.screens.forecast.ForecastTestTags.WIND_RENDERER_DESCRIPTION
import com.cloudbasepredictor.ui.screens.forecast.ForecastTestTags.WIND_TIME_AXIS
import com.cloudbasepredictor.ui.screens.forecast.ForecastTestTags.WIND_VIEW
import com.cloudbasepredictor.ui.screens.forecast.ForecastReadyUiState
import com.cloudbasepredictor.ui.screens.forecast.WindForecastChartUiModel
import com.cloudbasepredictor.ui.screens.forecast.WindSample
import com.cloudbasepredictor.ui.screens.forecast.layoutWindLabelCenters
import com.cloudbasepredictor.ui.screens.forecast.buildWindProfiles
import com.cloudbasepredictor.ui.screens.forecast.interpolateWind
import com.cloudbasepredictor.ui.screens.forecast.windHourIndexAtX
import com.cloudbasepredictor.ui.screens.forecast.windSpeedColor
import kotlin.math.PI
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
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .testTag(WIND_VIEW),
    ) {
        val scrollState = rememberScrollState()
        val viewportWidth = maxWidth
        // Keep every hour readable. Short viewports use extra label lanes within each hour.
        val labelCapacity = max(1, ((maxHeight - WIND_BOTTOM_AXIS_HEIGHT).value / 28f).toInt() - 1)
        val maximumLevels = uiState.windChart.cells
            .filter {
                !it.isSurface10m &&
                    it.altitudeKm <= uiState.elevationKm + uiState.chartViewport.visibleTopAltitudeKm
            }
            .groupBy { it.hour }.values.maxOfOrNull { it.size } ?: 0
        val lanes = max(1, ceil(maximumLevels.toFloat() / labelCapacity).toInt())
        val canvasWidth = maxOf(maxWidth, WIND_AXIS_WIDTH + WIND_COLUMN_WIDTH * lanes * uiState.windChart.hours.size)
        Box(Modifier.fillMaxSize().horizontalScroll(scrollState)) {
            WindChartCanvas(
                chart = uiState.windChart,
                visibleTopAltitudeKm = uiState.chartViewport.visibleTopAltitudeKm,
                elevationKm = uiState.elevationKm,
                displayUnits = uiState.displayUnits,
                labelCapacity = labelCapacity,
                viewportLeftPx = scrollState.value.toFloat(),
                viewportWidthPx = with(LocalDensity.current) { viewportWidth.toPx() },
                onVisibleTopAltitudeChange = onVisibleTopAltitudeChange,
                overlayBackHandler = overlayBackHandler,
                modifier = Modifier.width(canvasWidth).fillMaxHeight(),
            )
        }

        WindChartAxes(uiState, Modifier.fillMaxSize())

        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(WIND_TIME_AXIS_HEIGHT)
                .testTag(WIND_TIME_AXIS),
        )
    }
}

/** Keep the height axis and color key visible while the hourly data scrolls. */
@Composable
private fun WindChartAxes(uiState: ForecastReadyUiState, modifier: Modifier) {
    val textMeasurer = rememberTextMeasurer()
    val surface = MaterialTheme.colorScheme.surface
    val foreground = MaterialTheme.colorScheme.onSurfaceVariant
    val background = lerp(surface, MaterialTheme.colorScheme.onSurface, 0.035f)
    val style = TextStyle(color = foreground, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
    Canvas(modifier) {
        val axisWidth = WIND_AXIS_WIDTH.toPx()
        val plotBottom = size.height - WIND_BOTTOM_AXIS_HEIGHT.toPx()
        if (plotBottom <= 0f) return@Canvas
        val topAltitude = max(
            uiState.elevationKm + uiState.chartViewport.visibleTopAltitudeKm,
            uiState.elevationKm + WIND_MIN_VISIBLE_ALTITUDE_RANGE_KM,
        )
        drawRect(background, size = Size(axisWidth, plotBottom + WIND_SURFACE_ROW_HEIGHT.toPx()))
        val ticks = buildAltitudeTicks(uiState.elevationKm, topAltitude, if (topAltitude <= 3.5f) 0.5f else 1f)
        ticks.forEach { altitude ->
            val y = altitudeToY(altitude, uiState.elevationKm, topAltitude, 0f, plotBottom)
            if (y >= 24.dp.toPx()) {
                drawCanvasText(
                    textMeasurer, formatAltitudeAxisValue(altitude, uiState.displayUnits),
                    8.dp.toPx(), min(y + 4.dp.toPx(), plotBottom), style,
                )
            }
        }
        drawCanvasText(
            textMeasurer, "${altitudeAxisUnitLabel(uiState.displayUnits)} ASL",
            8.dp.toPx(), 15.dp.toPx(), style.copy(fontSize = 9.sp),
        )
        drawCanvasText(textMeasurer, "10 m", 8.dp.toPx(), plotBottom + 15.dp.toPx(), style)
        drawCanvasText(textMeasurer, "AGL", 8.dp.toPx(), plotBottom + 29.dp.toPx(), style)

        val legendTop = plotBottom + WIND_SURFACE_ROW_HEIGHT.toPx()
        drawRect(
            surface, Offset(0f, legendTop),
            Size(size.width, size.height - legendTop - WIND_TIME_AXIS_HEIGHT.toPx()),
        )
        val steps = listOf(0f, 5f, 10f, 15f, 20f, 30f, 40f, 50f, 60f)
        val itemWidth = (size.width - axisWidth) / steps.size
        val legendStyle = style.copy(fontSize = 9.sp)
        steps.forEachIndexed { index, speed ->
            val x = axisWidth + index * itemWidth
            drawRoundRect(windSpeedColor(speed).copy(alpha = 0.92f),
                Offset(x + itemWidth * 0.2f, legendTop + 4.dp.toPx()),
                Size(itemWidth * 0.6f, 6.dp.toPx()), CornerRadius(2.dp.toPx()))
            drawCanvasText(textMeasurer, formatWindSpeed(speed, uiState.displayUnits, withUnit = false),
                x + itemWidth / 2f, legendTop + 23.dp.toPx(), legendStyle, CanvasTextAnchor.CENTER)
        }
        drawCanvasText(
            textMeasurer, uiState.displayUnits.windSpeed.label,
            8.dp.toPx(), legendTop + 23.dp.toPx(), legendStyle,
        )
        if (uiState.windChart.cclKm.isNotEmpty()) {
            drawCanvasText(textMeasurer, "CCL", axisWidth + 4.dp.toPx(),
                size.height - 38.dp.toPx(), legendStyle.copy(color = Color(0xFFFF8C00)))
        }
        if (uiState.windChart.freezingLevelKm.isNotEmpty()) {
            drawCanvasText(textMeasurer, "0°C", axisWidth + 44.dp.toPx(),
                size.height - 38.dp.toPx(), legendStyle.copy(color = Color(0xFF00BCD4)))
        }
        drawCanvasText(textMeasurer, "● Source   ≈ Interpolated   — No data", axisWidth + 4.dp.toPx(),
            size.height - 23.dp.toPx(), legendStyle)
    }
}

@Composable
private fun WindChartCanvas(
    chart: WindForecastChartUiModel,
    visibleTopAltitudeKm: Float,
    elevationKm: Float,
    displayUnits: DisplayUnits,
    labelCapacity: Int,
    viewportLeftPx: Float,
    viewportWidthPx: Float,
    onVisibleTopAltitudeChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    overlayBackHandler: ForecastOverlayBackHandler,
) {
    val density = LocalDensity.current
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface
    val surfaceColor = MaterialTheme.colorScheme.surface
    val gridBackgroundColor = lerp(
        start = MaterialTheme.colorScheme.surface,
        stop = MaterialTheme.colorScheme.onSurface,
        fraction = 0.035f,
    )
    val outlineColor = MaterialTheme.colorScheme.outlineVariant

    val textMeasurer = rememberTextMeasurer()
    val speedLabelStyle = remember(onSurfaceColor) {
        TextStyle(
            color = onSurfaceColor,
            fontSize = 9.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
        )
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
    var crosshairPos by remember(chart) { mutableStateOf<Offset?>(null) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    val profileByHour = remember(chart.cells) { buildWindProfiles(chart.cells) }
    val plotLeft = with(density) { WIND_AXIS_WIDTH.toPx() }
    val plotBottom = canvasSize.height - with(density) { WIND_BOTTOM_AXIS_HEIGHT.toPx() }
    val effectiveTopAltitudeKm = max(
        elevationKm + visibleTopAltitudeKm, elevationKm + WIND_MIN_VISIBLE_ALTITUDE_RANGE_KM,
    )
    val columnWidth = (canvasSize.width - plotLeft) / max(1, chart.hours.size)
    val placements = remember(chart, canvasSize, elevationKm, effectiveTopAltitudeKm, labelCapacity, density) {
        buildWindSourcePlacements(
            chart.hours, profileByHour, elevationKm, effectiveTopAltitudeKm,
            plotLeft, plotBottom, columnWidth, labelCapacity, density.density,
        )
    }
    val cursor = crosshairPos?.takeIf { pos ->
        pos.x in (viewportLeftPx + plotLeft)..(viewportLeftPx + viewportWidthPx) &&
            (pos.y in 0f..plotBottom || placements.any {
                it.isSurface10m && it.labelBounds.contains(pos)
            })
    }?.let { pos ->
        val hit = placements.firstOrNull { it.labelBounds.contains(pos) }
        val hourIndex = windHourIndexAtX(pos.x, plotLeft, columnWidth, chart.hours.size)
        val hour = hit?.hour ?: hourIndex?.let { chart.hours[it] }
        val altitude = hit?.altitudeKm ?: yToAltitude(
            pos.y.coerceIn(0f, max(0f, plotBottom)), elevationKm, effectiveTopAltitudeKm, 0f, plotBottom,
        )
        hour?.let {
            WindCursor(
                hour = it,
                altitudeKm = altitude,
                sample = if (hit != null) hit.sample else profileByHour[it]?.let { profile ->
                    interpolateWind(profile, altitude)
                },
                isSurface10m = hit?.isSurface10m == true,
            )
        }
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
        drawRect(
            color = surfaceColor,
            size = size,
        )

        val axisWidth = with(density) { WIND_AXIS_WIDTH.toPx() }
        val bottomAxisHeight = with(density) { WIND_BOTTOM_AXIS_HEIGHT.toPx() }

        val plotLeft = axisWidth
        val plotTop = 0f
        val plotRight = size.width
        val plotBottom = size.height - bottomAxisHeight
        val plotWidth = plotRight - plotLeft
        val plotHeight = plotBottom - plotTop

        val minAltitudeKm = elevationKm
        val effectiveTopAltitudeKm = max(
            elevationKm + visibleTopAltitudeKm,
            minAltitudeKm + WIND_MIN_VISIBLE_ALTITUDE_RANGE_KM,
        )

        if (plotWidth <= 0f || plotHeight <= 0f || chart.hours.isEmpty()) {
            return@Canvas
        }

        // Background
        drawRect(
            color = gridBackgroundColor,
            topLeft = Offset(0f, plotTop),
            size = Size(axisWidth, plotHeight),
        )
        drawRect(
            color = gridBackgroundColor,
            topLeft = Offset(plotLeft, plotTop),
            size = Size(plotWidth, plotHeight),
        )

        val columnWidth = plotWidth / chart.hours.size
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
                end = Offset(x, plotBottom),
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
                start = Offset(0f, y),
                end = Offset(plotRight, y),
                strokeWidth = 1.dp.toPx(),
            )
        }

        // Outline
        drawRect(
            color = outlineColor.copy(alpha = 0.4f),
            topLeft = Offset(plotLeft, plotTop),
            size = Size(plotWidth, plotHeight),
            style = Stroke(width = 1.dp.toPx()),
        )

        // ── CCL line (orange, 2.5dp, rounded) ──────────────────────
        if (chart.cclKm.isNotEmpty()) {
            val cclPath = Path()
            var started = false
            chart.cclKm.forEach { marker ->
                val hourIndex = chart.hours.indexOf(marker.hour)
                if (hourIndex < 0) return@forEach
                val x = plotLeft + hourIndex * columnWidth + columnWidth / 2f
                val y = altitudeToY(
                    marker.altitudeKm, minAltitudeKm, effectiveTopAltitudeKm, plotTop, plotBottom,
                )
                if (y !in plotTop..plotBottom) return@forEach
                if (!started) {
                    cclPath.moveTo(x, y)
                    started = true
                } else {
                    cclPath.lineTo(x, y)
                }
            }
            if (started) {
                drawPath(
                    path = cclPath,
                    color = Color(0xFFFF8C00),
                    style = Stroke(
                        width = 2.5f.dp.toPx(),
                        cap = androidx.compose.ui.graphics.StrokeCap.Round,
                        join = androidx.compose.ui.graphics.StrokeJoin.Round,
                    ),
                )
            }
        }

        // ── 0 °C level line (cyan, 2dp, rounded) ───────────────────
        if (chart.freezingLevelKm.isNotEmpty()) {
            val flPath = Path()
            var started = false
            chart.freezingLevelKm.forEach { marker ->
                val hourIndex = chart.hours.indexOf(marker.hour)
                if (hourIndex < 0) return@forEach
                val x = plotLeft + hourIndex * columnWidth + columnWidth / 2f
                val y = altitudeToY(
                    marker.altitudeKm, minAltitudeKm, effectiveTopAltitudeKm, plotTop, plotBottom,
                )
                if (y !in plotTop..plotBottom) return@forEach
                if (!started) {
                    flPath.moveTo(x, y)
                    started = true
                } else {
                    flPath.lineTo(x, y)
                }
            }
            if (started) {
                drawPath(
                    path = flPath,
                    color = Color(0xFF00BCD4),
                    style = Stroke(
                        width = 2f.dp.toPx(),
                        cap = androidx.compose.ui.graphics.StrokeCap.Round,
                        join = androidx.compose.ui.graphics.StrokeJoin.Round,
                    ),
                )
            }
        }

        // Source arrows stay at their hourly altitude. Only their labels are displaced.
        // The explicit surface readout has a dedicated row and a leader to its 10 m anchor.
        drawRect(
            color = gridBackgroundColor,
            topLeft = Offset(0f, plotBottom),
            size = Size(size.width, WIND_SURFACE_ROW_HEIGHT.toPx()),
        )
        placements.forEach { placement ->
            drawSourceWind(
                placement, displayUnits, textMeasurer, speedLabelStyle,
                onSurfaceColor, surfaceColor,
            )
        }
        val timeBaseline = size.height - 4.dp.toPx()
        chart.hours.forEachIndexed { index, hour ->
            val x = plotLeft + index * columnWidth + columnWidth / 2f
            drawCanvasText(
                textMeasurer = textMeasurer,
                text = formatPaddedInt(hour, minimumDigits = 2),
                x = x,
                baselineY = timeBaseline,
                style = timeLabelStyle,
                anchor = CanvasTextAnchor.CENTER,
            )
        }

        // ── Crosshair overlay ──────────────────────────────────
        cursor?.let { selected ->
            val pos = crosshairPos ?: return@let
            val cx = pos.x.coerceIn(plotLeft, plotRight)
            val cy = altitudeToY(
                selected.altitudeKm, minAltitudeKm, effectiveTopAltitudeKm, plotTop, plotBottom,
            ).coerceIn(plotTop, plotBottom)

            val dash = PathEffect.dashPathEffect(floatArrayOf(8f, 6f))
            drawLine(
                color = onSurfaceColor.copy(alpha = 0.5f),
                start = Offset(cx, plotTop),
                end = Offset(cx, plotBottom),
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

            val reticleR = with(density) { 18.dp.toPx() }
            drawCircle(
                color = onSurfaceColor.copy(alpha = 0.7f),
                radius = reticleR,
                center = Offset(cx, cy),
                style = Stroke(width = 2.dp.toPx()),
            )
            val tick = with(density) { 4.dp.toPx() }
            for (angleDeg in listOf(0f, 90f, 180f, 270f)) {
                val rad = angleDeg * PI.toFloat() / 180f
                drawLine(
                    color = onSurfaceColor.copy(alpha = 0.7f),
                    start = Offset(
                        cx + cos(rad) * (reticleR - tick),
                        cy + sin(rad) * (reticleR - tick),
                    ),
                    end = Offset(
                        cx + cos(rad) * (reticleR + tick),
                        cy + sin(rad) * (reticleR + tick),
                    ),
                    strokeWidth = 2.dp.toPx(),
                )
            }

            val tooltipLayouts = tooltipLines.map { textMeasurer.measureCanvasText(it, tooltipStyle) }
            val lineH = with(density) { 12.sp.toPx() } * 1.3f
            val maxTextW = tooltipLayouts.maxOf { it.size.width.toFloat() }
            val padH = with(density) { 8.dp.toPx() }
            val padV = with(density) { 6.dp.toPx() }
            val ttW = maxTextW + padH * 2
            val ttH = lineH * tooltipLines.size + padV * 2
            val visibleRight = min(plotRight, viewportLeftPx + viewportWidthPx)
            val ttX = (cx + reticleR + 8.dp.toPx()).coerceIn(
                viewportLeftPx + axisWidth + 4.dp.toPx(),
                max(viewportLeftPx + axisWidth + 4.dp.toPx(), visibleRight - ttW - 4.dp.toPx()),
            )
            val ttY = (cy - ttH - 24.dp.toPx()).coerceIn(plotTop, max(plotTop, plotBottom - ttH))

            drawRoundRect(
                color = gridBackgroundColor.copy(alpha = 0.92f),
                topLeft = Offset(ttX, ttY),
                size = Size(ttW, ttH),
                cornerRadius = CornerRadius(4.dp.toPx()),
            )
            drawRoundRect(
                color = onSurfaceColor.copy(alpha = 0.3f),
                topLeft = Offset(ttX, ttY),
                size = Size(ttW, ttH),
                cornerRadius = CornerRadius(4.dp.toPx()),
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

private data class WindSourcePlacement(
    val hour: Int,
    val altitudeKm: Float,
    val sample: WindSample?,
    val anchor: Offset,
    val labelBounds: Rect,
    val isSurface10m: Boolean = false,
)

private data class WindCursor(
    val hour: Int,
    val altitudeKm: Float,
    val sample: WindSample?,
    val isSurface10m: Boolean,
)

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

private fun buildWindSourcePlacements(
    hours: List<Int>,
    profiles: Map<Int, List<WindSample>>,
    elevationKm: Float,
    topAltitudeKm: Float,
    plotLeft: Float,
    plotBottom: Float,
    columnWidth: Float,
    labelCapacity: Int,
    density: Float,
): List<WindSourcePlacement> {
    if (plotBottom <= 28f * density || columnWidth <= 0f) return emptyList()
    return buildList {
        hours.forEachIndexed { index, hour ->
            val left = plotLeft + index * columnWidth
            val profile = profiles[hour].orEmpty()
            val levels = profile.filter { !it.isSurface10m && it.altitudeKm in elevationKm..topAltitudeKm }
                .sortedByDescending { it.altitudeKm }
            val lanes = levels.chunked(labelCapacity)
            val laneWidth = columnWidth / max(1, lanes.size)
            lanes.forEachIndexed { laneIndex, samples ->
                val anchorX = left + laneIndex * laneWidth + 12f * density
                val anchorYs = samples.map { altitudeToY(it.altitudeKm, elevationKm, topAltitudeKm, 0f, plotBottom) }
                val centers = layoutWindLabelCenters(anchorYs, 14f * density, plotBottom - 14f * density, 28f * density)
                samples.forEachIndexed { sampleIndex, sample ->
                    add(WindSourcePlacement(
                        hour, sample.altitudeKm, sample, Offset(anchorX, anchorYs[sampleIndex]),
                        Rect(anchorX + 12f * density, centers[sampleIndex] - 12f * density,
                            left + (laneIndex + 1) * laneWidth - 2f * density, centers[sampleIndex] + 12f * density),
                    ))
                }
            }
            val surface = profile.firstOrNull { it.isSurface10m }
            val surfaceAltitude = elevationKm + 0.01f
            add(WindSourcePlacement(
                hour, surfaceAltitude, surface,
                Offset(
                    left + columnWidth / 2f,
                    altitudeToY(surfaceAltitude, elevationKm, topAltitudeKm, 0f, plotBottom),
                ),
                Rect(
                    left + 2f * density, plotBottom + 10f * density,
                    left + columnWidth - 2f * density, plotBottom + 38f * density,
                ),
                isSurface10m = true,
            ))
        }
    }
}

private fun DrawScope.drawSourceWind(
    placement: WindSourcePlacement,
    units: DisplayUnits,
    textMeasurer: TextMeasurer,
    style: TextStyle,
    foreground: Color,
    surface: Color,
) {
    val bounds = placement.labelBounds
    val sample = placement.sample
    val labelCenterY = bounds.center.y
    if (sample != null) {
        drawLine(
            color = foreground.copy(alpha = 0.45f),
            start = placement.anchor,
            end = if (placement.isSurface10m) {
                Offset(bounds.center.x, bounds.top)
            } else {
                Offset(bounds.left, labelCenterY)
            },
            strokeWidth = 0.75.dp.toPx(),
        )
        drawCircle(surface, 3.dp.toPx(), placement.anchor)
        drawCircle(foreground, 1.75.dp.toPx(), placement.anchor)
        drawWindArrow(
            center = placement.anchor,
            directionDeg = sample.directionDeg,
            speedKmh = sample.speedKmh,
            shaftLength = 14.dp.toPx(),
            foreground = foreground,
            surface = surface,
        )
    }
    drawRoundRect(
        color = surface.copy(alpha = 0.90f),
        topLeft = bounds.topLeft, size = bounds.size, cornerRadius = CornerRadius(3.dp.toPx()),
    )
    val textX = if (placement.isSurface10m) bounds.center.x + 8.dp.toPx() else bounds.center.x
    val speed = sample?.let {
        val value = formatWindSpeed(it.speedKmh, units, withUnit = false)
        if (it.speedKmh < 0.05f) value else "$value ${it.directionDeg.roundToInt() % 360}°"
    } ?: "—"
    drawCanvasText(textMeasurer, speed, textX, labelCenterY - 1.dp.toPx(), style, CanvasTextAnchor.CENTER)
    drawCanvasText(
        textMeasurer, if (placement.isSurface10m) "10m AGL" else sourceAltitudeLabel(placement.altitudeKm, units),
        textX, labelCenterY + 10.dp.toPx(),
        style.copy(fontWeight = FontWeight.Normal, fontSize = 8.sp), CanvasTextAnchor.CENTER,
    )
    // Draw the surface direction on top of its readout background.
    if (placement.isSurface10m && sample != null) {
        drawWindArrow(
            Offset(bounds.left + 12.dp.toPx(), labelCenterY), sample.directionDeg,
            sample.speedKmh, 14.dp.toPx(), foreground, surface,
        )
    }
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
        drawCircle(foreground, 3.dp.toPx(), center, style = Stroke(1.dp.toPx()))
        return
    }
    val angle = (directionDeg + 90f) * PI.toFloat() / 180f
    val direction = Offset(cos(angle), sin(angle))
    val tip = center + direction * (shaftLength / 2f)
    val tail = center - direction * (shaftLength / 2f)
    val headLength = 4.dp.toPx()
    val path = Path().apply {
        moveTo(tail.x, tail.y)
        lineTo(tip.x, tip.y)
        moveTo(tip.x - cos(angle - PI.toFloat() / 6f) * headLength, tip.y - sin(angle - PI.toFloat() / 6f) * headLength)
        lineTo(tip.x, tip.y)
        lineTo(tip.x - cos(angle + PI.toFloat() / 6f) * headLength, tip.y - sin(angle + PI.toFloat() / 6f) * headLength)
    }
    drawPath(path, surface, style = Stroke(3.5.dp.toPx(), cap = StrokeCap.Round))
    drawPath(path, foreground, style = Stroke(1.25.dp.toPx(), cap = StrokeCap.Round))
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

private const val WIND_MIN_VISIBLE_ALTITUDE_RANGE_KM = 0.75f
private val WIND_AXIS_WIDTH = 60.dp
private val WIND_BOTTOM_AXIS_HEIGHT = 112.dp
private val WIND_SURFACE_ROW_HEIGHT = 40.dp
private val WIND_COLUMN_WIDTH = 76.dp
private val WIND_TIME_AXIS_HEIGHT = 16.dp

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
