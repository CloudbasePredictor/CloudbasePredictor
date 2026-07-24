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
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cloudbasepredictor.data.units.DisplayUnits
import com.cloudbasepredictor.data.units.altitudeAxisUnitLabel
import com.cloudbasepredictor.data.units.formatAltitudeAxisValue
import com.cloudbasepredictor.data.units.formatAltitudeKm
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
import com.cloudbasepredictor.ui.screens.forecast.buildWindHourClusters
import com.cloudbasepredictor.ui.screens.forecast.buildWindProfiles
import com.cloudbasepredictor.ui.screens.forecast.interpolateWind
import com.cloudbasepredictor.ui.screens.forecast.windHourIndexAtX
import com.cloudbasepredictor.ui.screens.forecast.windSpeedColor
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import androidx.compose.ui.tooling.preview.Preview

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
    val axisLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface
    val surfaceColor = MaterialTheme.colorScheme.surface
    val gridBackgroundColor = lerp(
        start = MaterialTheme.colorScheme.surface,
        stop = MaterialTheme.colorScheme.onSurface,
        fraction = 0.035f,
    )
    val outlineColor = MaterialTheme.colorScheme.outlineVariant

    val textMeasurer = rememberTextMeasurer()
    val axisLabelStyle = remember(axisLabelColor) {
        TextStyle(
            color = axisLabelColor,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
        )
    }
    val unitLabelStyle = remember(axisLabelColor) {
        TextStyle(
            color = axisLabelColor,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
        )
    }
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
    val legendLabelStyle = remember(axisLabelColor) {
        TextStyle(
            color = axisLabelColor,
            fontSize = 9.sp,
            fontFamily = FontFamily.Monospace,
        )
    }
    val timeLabelStyle = remember(axisLabelColor) {
        TextStyle(
            color = axisLabelColor,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
        )
    }
    val cclLabelStyle = remember {
        TextStyle(
            color = Color(0xFFFF8C00),
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
        )
    }
    val freezingLabelStyle = remember {
        TextStyle(
            color = Color(0xFF00BCD4),
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
        )
    }
    var crosshairPos by remember { mutableStateOf<Offset?>(null) }
    val latestVisibleTopAltitudeKm = rememberUpdatedState(visibleTopAltitudeKm)

    // Back dismisses the tap overlay (crosshair tooltip) first, before navigating away.
    overlayBackHandler(crosshairPos != null) {
        crosshairPos = null
    }

    Canvas(
        modifier = modifier
            .testTag(WIND_CHART_CANVAS)
            .semantics {
                contentDescription = WIND_RENDERER_DESCRIPTION
                stateDescription = if (crosshairPos != null) "cursor" else "idle"
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
        val arrowSizePx = with(density) { 48.dp.toPx() }

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
        val profileByHour = buildWindProfiles(chart.cells)
        if (profileByHour.isEmpty()) return@Canvas
        val availableAltitudes = profileByHour.values.flatten().map { it.altitudeKm }
        val lowAvailableAltitudeKm = availableAltitudes.minOrNull() ?: return@Canvas
        val highAvailableAltitudeKm = availableAltitudes.maxOrNull() ?: return@Canvas
        val visibleModelLevels = chart.modelLevelAltitudesKm.filter {
            it in minAltitudeKm..effectiveTopAltitudeKm
        }

        // Horizontal clustering: skip arrow columns if cells are too narrow.
        val hourCluster = max(1, ceil(arrowSizePx * 1.1f / columnWidth).toInt())

        // ── Interpolated wind-speed background ────────────────────
        // Sample every few display pixels with the same vector interpolation used by arrows and
        // the cursor. This keeps the color under a cursor consistent with its exact hour/altitude.
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

        // Wind arrows: the native pressure levels are sparse and unevenly spaced
        // (≈0.5 km apart above 850 hPa), which leaves large empty gaps between rows.
        // Instead of drawing only on those raw levels, resample the profile onto an
        // evenly spaced grid of rows that fills the available vertical room, linearly
        // interpolating the wind (via u/v components, so direction wraps correctly)
        // between the surrounding levels. The lowest level is always the bottom row.
        val hourClusters = buildWindHourClusters(chart.hours.size, hourCluster)
        val minArrowSpacingPx = arrowSizePx * 1.1f
        val arrowDrawSize = min(arrowSizePx, columnWidth * hourCluster * 0.8f)

        // Evenly spaced target altitudes from the lowest visible level up to the
        // highest, one arrow row per ~minArrowSpacingPx of vertical space.
        val lowAltKm = max(minAltitudeKm, lowAvailableAltitudeKm)
        val highAltKm = min(effectiveTopAltitudeKm, highAvailableAltitudeKm)
        val kmPerPx = (effectiveTopAltitudeKm - minAltitudeKm) / plotHeight
        val altStepKm = (minArrowSpacingPx * kmPerPx).coerceAtLeast(0.01f)
        val arrowAltitudes = buildList {
            var a = lowAltKm
            while (a <= highAltKm + 0.0001f) {
                add(a)
                a += altStepKm
            }
            if (isEmpty()) add(lowAltKm)
        }

        hourClusters.forEach { cluster ->
            val hour = chart.hours[cluster.representativeIndex]
            val cellCenterX = plotLeft + cluster.centerColumn * columnWidth
            val profile = profileByHour[hour] ?: return@forEach

            arrowAltitudes.forEach { altKm ->
                val sample = interpolateWind(profile, altKm) ?: return@forEach
                val cellCenterY = altitudeToY(
                    altKm, minAltitudeKm, effectiveTopAltitudeKm, plotTop, plotBottom,
                )

                // Draw arrow — black in light theme (onSurface)
                drawWindArrow(
                    centerX = cellCenterX,
                    centerY = cellCenterY,
                    directionDeg = sample.directionDeg,
                    arrowSize = arrowDrawSize,
                    speedKmh = sample.speedKmh,
                    color = onSurfaceColor,
                )
            }
        }

        // Axis labels
        altitudeTicks.forEach { altKm ->
            val y = altitudeToY(
                altKm, minAltitudeKm, effectiveTopAltitudeKm, plotTop, plotBottom,
            )
            drawCanvasText(
                textMeasurer = textMeasurer,
                text = formatAltitudeAxisValue(altKm, displayUnits),
                x = 8.dp.toPx(),
                baselineY = y + with(density) { 12.sp.toPx() } * 0.35f,
                style = axisLabelStyle,
            )
        }

        drawCanvasText(
            textMeasurer = textMeasurer,
            text = altitudeAxisUnitLabel(displayUnits),
            x = 8.dp.toPx(),
            baselineY = plotTop + with(density) { 11.sp.toPx() } + 4.dp.toPx(),
            style = unitLabelStyle,
        )

        // Wind speed color legend and time axis at bottom.
        val legendSteps = listOf(0f, 5f, 10f, 15f, 20f, 30f, 40f, 50f, 60f)
        val legendY = plotBottom + 4.dp.toPx()
        val swatchH = 6.dp.toPx()
        val legendItemWidth = (plotWidth) / legendSteps.size
        val legendBaseline = legendY + swatchH + with(density) { 9.sp.toPx() } + 1.dp.toPx()
        legendSteps.forEachIndexed { index, speedKmh ->
            val lx = plotLeft + index * legendItemWidth
            val swatchW = legendItemWidth * 0.6f
            drawRoundRect(
                color = windSpeedColor(speedKmh).copy(alpha = 0.92f),
                topLeft = Offset(lx + (legendItemWidth - swatchW) / 2f, legendY),
                size = Size(swatchW, swatchH),
                cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx()),
            )
            drawCanvasText(
                textMeasurer = textMeasurer,
                text = formatWindSpeed(speedKmh, displayUnits, withUnit = false),
                x = lx + legendItemWidth / 2f,
                baselineY = legendBaseline,
                style = legendLabelStyle,
                anchor = CanvasTextAnchor.CENTER,
            )
        }
        drawCanvasText(
            textMeasurer = textMeasurer,
            text = displayUnits.windSpeed.label,
            x = plotLeft - 20.dp.toPx(),
            baselineY = legendBaseline,
            style = legendLabelStyle,
            anchor = CanvasTextAnchor.CENTER,
        )

        val timeBaseline = size.height - 4.dp.toPx()
        chart.hours.forEachIndexed { index, hour ->
            if ((hour - chart.hours.first()) % 3 != 0) return@forEachIndexed
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

        // Speed labels below each drawn arrow
        hourClusters.forEach { cluster ->
            val hour = chart.hours[cluster.representativeIndex]
            val cellCenterX = plotLeft + cluster.centerColumn * columnWidth
            val profile = profileByHour[hour] ?: return@forEach

            arrowAltitudes.forEach arrowLabel@{ altKm ->
                val sample = interpolateWind(profile, altKm) ?: return@arrowLabel
                val cellCenterY = altitudeToY(
                    altKm, minAltitudeKm, effectiveTopAltitudeKm, plotTop, plotBottom,
                )

                val labelBaseline = cellCenterY + arrowDrawSize / 2f +
                    with(density) { 9.sp.toPx() } + 1.dp.toPx()
                if (labelBaseline > plotBottom - 2.dp.toPx()) return@arrowLabel
                drawCanvasText(
                    textMeasurer = textMeasurer,
                    text = formatWindSpeed(sample.speedKmh, displayUnits, withUnit = false),
                    x = cellCenterX,
                    baselineY = labelBaseline,
                    style = speedLabelStyle,
                    anchor = CanvasTextAnchor.CENTER,
                )
            }
        }

        // CCL label (left side, 30dp from left edge)
        chart.cclKm.firstOrNull()?.let { firstCcl ->
            val y = altitudeToY(
                firstCcl.altitudeKm, minAltitudeKm, effectiveTopAltitudeKm, plotTop, plotBottom,
            )
            if (y in plotTop..plotBottom) {
                drawCanvasText(
                    textMeasurer = textMeasurer,
                    text = "CCL",
                    x = 30.dp.toPx(),
                    baselineY = y - 4.dp.toPx(),
                    style = cclLabelStyle,
                )
            }
        }

        // 0°C level label with snowflake (left side, 30dp from left edge)
        chart.freezingLevelKm.firstOrNull()?.let { firstFl ->
            val y = altitudeToY(
                firstFl.altitudeKm, minAltitudeKm, effectiveTopAltitudeKm, plotTop, plotBottom,
            )
            if (y in plotTop..plotBottom) {
                drawCanvasText(
                    textMeasurer = textMeasurer,
                    text = "❄ 0°C",
                    x = 30.dp.toPx(),
                    baselineY = y - 4.dp.toPx(),
                    style = freezingLabelStyle,
                )
            }
        }

        // Compact right-edge markers point to representative heights where the profile contains
        // direct model data. Every value between these markers uses interpolation.
        visibleModelLevels.forEach { altitudeKm ->
            val y = altitudeToY(
                altitudeKm,
                minAltitudeKm,
                effectiveTopAltitudeKm,
                plotTop,
                plotBottom,
            ).coerceIn(plotTop + 1.dp.toPx(), plotBottom - 1.dp.toPx())
            drawModelLevelMarker(
                rightEdgeX = plotRight - 1.dp.toPx(),
                centerY = y,
                fillColor = surfaceColor,
                outlineColor = onSurfaceColor,
            )
        }

        // ── Crosshair overlay ──────────────────────────────────
        crosshairPos?.let { pos ->
            val cx = pos.x.coerceIn(plotLeft, plotRight)
            val cy = pos.y.coerceIn(plotTop, plotBottom)

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

            val altKm = yToAltitude(cy, minAltitudeKm, effectiveTopAltitudeKm, plotTop, plotBottom)
            val hourIdx = windHourIndexAtX(
                x = cx,
                plotLeft = plotLeft,
                columnWidth = columnWidth,
                hourCount = chart.hours.size,
            ) ?: return@let
            val hour = chart.hours[hourIdx]
            val sample = profileByHour[hour]?.let { profile ->
                interpolateWind(profile, altKm)
            }

            val tooltipLines = mutableListOf<String>()
            tooltipLines += "${formatPaddedInt(hour, minimumDigits = 2)}h  ${formatAltitudeKm(altKm, displayUnits)}"
            if (sample != null) {
                tooltipLines += "${formatWindSpeed(sample.speedKmh, displayUnits)}  ${sample.directionDeg.toInt()}°"
            }

            val tooltipLayouts = tooltipLines.map { textMeasurer.measureCanvasText(it, tooltipStyle) }
            val lineH = with(density) { 12.sp.toPx() } * 1.3f
            val maxTextW = tooltipLayouts.maxOf { it.size.width.toFloat() }
            val padH = with(density) { 8.dp.toPx() }
            val padV = with(density) { 6.dp.toPx() }
            val ttW = maxTextW + padH * 2
            val ttH = lineH * tooltipLines.size + padV * 2
            val ttX = if (cx + reticleR + ttW + 8.dp.toPx() < plotRight)
                cx + reticleR + 8.dp.toPx()
            else
                cx - reticleR - ttW - 8.dp.toPx()
            val ttY = (cy - ttH / 2f).coerceIn(plotTop, plotBottom - ttH)

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

private fun DrawScope.drawModelLevelMarker(
    rightEdgeX: Float,
    centerY: Float,
    fillColor: Color,
    outlineColor: Color,
) {
    val width = 8.dp.toPx()
    val halfHeight = 4.dp.toPx()
    val marker = Path().apply {
        moveTo(rightEdgeX - width, centerY)
        lineTo(rightEdgeX, centerY - halfHeight)
        lineTo(rightEdgeX, centerY + halfHeight)
        close()
    }
    drawPath(path = marker, color = fillColor)
    drawPath(
        path = marker,
        color = outlineColor.copy(alpha = 0.9f),
        style = Stroke(width = 1.25.dp.toPx()),
    )
}

private fun DrawScope.drawWindArrow(
    centerX: Float,
    centerY: Float,
    directionDeg: Float,
    arrowSize: Float,
    speedKmh: Float,
    color: Color,
) {
    // Arrow points in the direction the wind is going TO (opposite of "from")
    val goingToDeg = (directionDeg + 180f) % 360f
    val angleRad = (goingToDeg - 90f) * PI.toFloat() / 180f
    val halfSize = arrowSize / 2f * 0.7f

    val tipX = centerX + cos(angleRad) * halfSize
    val tipY = centerY + sin(angleRad) * halfSize
    val tailX = centerX - cos(angleRad) * halfSize
    val tailY = centerY - sin(angleRad) * halfSize

    val strokeWidth = (2f + speedKmh / 50f).coerceAtMost(3.5f)

    drawLine(
        color = color,
        start = Offset(tailX, tailY),
        end = Offset(tipX, tipY),
        strokeWidth = strokeWidth,
        cap = StrokeCap.Round,
    )

    val arrowLen = halfSize * 0.35f
    val arrowAngle = PI.toFloat() / 6f
    drawLine(
        color = color,
        start = Offset(tipX, tipY),
        end = Offset(
            tipX - cos(angleRad - arrowAngle) * arrowLen,
            tipY - sin(angleRad - arrowAngle) * arrowLen,
        ),
        strokeWidth = strokeWidth,
        cap = StrokeCap.Round,
    )
    drawLine(
        color = color,
        start = Offset(tipX, tipY),
        end = Offset(
            tipX - cos(angleRad + arrowAngle) * arrowLen,
            tipY - sin(angleRad + arrowAngle) * arrowLen,
        ),
        strokeWidth = strokeWidth,
        cap = StrokeCap.Round,
    )
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
private val WIND_BOTTOM_AXIS_HEIGHT = 48.dp
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
