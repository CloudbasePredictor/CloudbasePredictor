package com.cloudbasepredictor.ui.screens.forecast.views

import android.content.res.Configuration
import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cloudbasepredictor.R
import com.cloudbasepredictor.data.units.DisplayUnits
import com.cloudbasepredictor.data.units.altitudeAxisUnitLabel
import com.cloudbasepredictor.data.units.formatAltitudeAxisValue
import com.cloudbasepredictor.data.units.formatAltitudeKm
import com.cloudbasepredictor.data.units.formatAltitudeMeters
import com.cloudbasepredictor.data.units.formatVerticalSpeedRange
import com.cloudbasepredictor.domain.forecast.ThermalForecastConfidence
import com.cloudbasepredictor.domain.forecast.ThermalLimitingReason
import com.cloudbasepredictor.model.ForecastMode
import com.cloudbasepredictor.ui.preview.PreviewData
import com.cloudbasepredictor.ui.screens.forecast.ForecastReadyUiState
import com.cloudbasepredictor.ui.screens.forecast.ForecastTestTags.THERMIC_CURSOR_PANEL
import com.cloudbasepredictor.ui.screens.forecast.ForecastTestTags.THERMIC_VIEW
import com.cloudbasepredictor.ui.screens.forecast.ThermicForecastChartUiModel
import com.cloudbasepredictor.ui.screens.forecast.ThermicSlotDiagnostics
import com.cloudbasepredictor.ui.screens.forecast.aggregatedForDisplay
import com.cloudbasepredictor.ui.screens.forecast.thermicStrengthColor
import com.cloudbasepredictor.ui.screens.forecast.visibleSegment
import com.cloudbasepredictor.ui.theme.CloudbasePredictorTheme
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

@Composable
internal fun ThermicForecastView(
    uiState: ForecastReadyUiState,
    modifier: Modifier = Modifier,
    onVisibleTopAltitudeChange: (Float) -> Unit = {},
) {
    ForecastChartCard(
        modifier = modifier.testTag(THERMIC_VIEW),
    ) { chartModifier ->
        Box(
            modifier = chartModifier.background(MaterialTheme.colorScheme.surface),
        ) {
            ThermicForecastGrid(
                chart = uiState.thermicChart,
                visibleTopAltitudeKm = uiState.chartViewport.visibleTopAltitudeKm,
                elevationKm = uiState.elevationKm,
                displayUnits = uiState.displayUnits,
                onVisibleTopAltitudeChange = onVisibleTopAltitudeChange,
                modifier = Modifier.fillMaxSize(),
            )
            if (uiState.thermicChart.cells.isEmpty()) {
                ForecastInformationView(
                    message = stringResource(R.string.forecast_no_thermals),
                )
            }
        }
    }
}

@Composable
private fun ThermicForecastGrid(
    chart: ThermicForecastChartUiModel,
    visibleTopAltitudeKm: Float,
    elevationKm: Float,
    displayUnits: DisplayUnits,
    onVisibleTopAltitudeChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val axisLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val gridBackgroundColor = lerp(
        start = MaterialTheme.colorScheme.surface,
        stop = MaterialTheme.colorScheme.onSurface,
        fraction = 0.035f,
    )
    val outlineColor = MaterialTheme.colorScheme.outlineVariant
    val axisWidthPx = with(density) { THERMIC_AXIS_WIDTH.toPx() }
    val bottomAxisHeightPx = with(density) { THERMIC_BOTTOM_AXIS_HEIGHT.toPx() }

    val axisLabelPaint = remember(density, axisLabelColor) {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = axisLabelColor.toArgb()
            textSize = with(density) { 12.sp.toPx() }
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
        }
    }
    val hourLabelPaint = remember(density, axisLabelColor) {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = axisLabelColor.toArgb()
            textSize = with(density) { 12.sp.toPx() }
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        }
    }
    val unitLabelPaint = remember(density, axisLabelColor) {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = axisLabelColor.toArgb()
            textSize = with(density) { 11.sp.toPx() }
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
        }
    }
    var chartSize by remember { mutableStateOf(IntSize.Zero) }
    var crosshairPos by remember { mutableStateOf<Offset?>(null) }
    var cursorPanelSize by remember { mutableStateOf(IntSize.Zero) }
    val latestVisibleTopAltitudeKm = rememberUpdatedState(visibleTopAltitudeKm)
    val cursorInfo = remember(
        crosshairPos,
        chartSize,
        chart,
        visibleTopAltitudeKm,
        elevationKm,
        displayUnits,
        axisWidthPx,
        bottomAxisHeightPx,
    ) {
        buildThermicCursorInfo(
            position = crosshairPos,
            chartSize = chartSize,
            chart = chart,
            visibleTopAltitudeKm = visibleTopAltitudeKm,
            elevationKm = elevationKm,
            displayUnits = displayUnits,
            axisWidthPx = axisWidthPx,
            bottomAxisHeightPx = bottomAxisHeightPx,
        )
    }

    Box(
        modifier = modifier
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
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { chartSize = it },
        ) {
            if (chart.timeSlots.isEmpty()) {
                return@Canvas
            }

            val axisWidth = axisWidthPx
            val outerHorizontalPadding = 0f
            val axisToPlotSpacing = 0f
            val bottomAxisHeight = bottomAxisHeightPx
            val plotCornerRadius = 0f
            val tileInset = with(density) { 1.dp.toPx() }

            val plotLeft = outerHorizontalPadding + axisWidth + axisToPlotSpacing
            val plotTop = outerHorizontalPadding
            val plotRight = size.width - outerHorizontalPadding
            val plotBottom = size.height - bottomAxisHeight
            val plotWidth = plotRight - plotLeft
            val plotHeight = plotBottom - plotTop

            val effectiveTopAltitudeKm = max(
                elevationKm + visibleTopAltitudeKm,
                elevationKm + MIN_VISIBLE_ALTITUDE_RANGE_KM,
            )
            val majorAltitudeTicks = buildAltitudeTicks(
                minAltitudeKm = elevationKm,
                maxAltitudeKm = effectiveTopAltitudeKm,
                stepKm = thermicMajorAltitudeStepKm(effectiveTopAltitudeKm - elevationKm),
            )
            val minorAltitudeTicks = buildAltitudeTicks(
                minAltitudeKm = elevationKm,
                maxAltitudeKm = effectiveTopAltitudeKm,
                stepKm = 0.25f,
            )

            if (plotWidth <= 0f || plotHeight <= 0f) {
                return@Canvas
            }

            val displayChart = chart.aggregatedForDisplay(
                timeBucketSlotCount = resolveTimeBucketSlotCount(
                    plotWidth = plotWidth,
                    rawTimeSlotCount = chart.timeSlots.size,
                ),
                altitudeBucketStepKm = resolveAltitudeBucketStepKm(
                    plotHeight = plotHeight,
                    visibleTopAltitudeKm = effectiveTopAltitudeKm,
                    rawAltitudeStepKm = THERMIC_DATA_ALTITUDE_STEP_KM,
                ),
            )
            if (displayChart.timeSlots.isEmpty()) {
                return@Canvas
            }

            drawRoundRect(
                color = gridBackgroundColor,
                topLeft = Offset(outerHorizontalPadding, plotTop),
                size = Size(axisWidth, plotHeight),
                cornerRadius = CornerRadius(plotCornerRadius, plotCornerRadius),
            )
            drawRoundRect(
                color = gridBackgroundColor,
                topLeft = Offset(plotLeft, plotTop),
                size = Size(plotWidth, plotHeight),
                cornerRadius = CornerRadius(plotCornerRadius, plotCornerRadius),
            )

            val columnWidth = plotWidth / displayChart.timeSlots.size
            val timeIndexLookup = displayChart.timeSlots.withIndex().associate { (index, minute) ->
                minute to index
            }

            minorAltitudeTicks.forEach { altitudeKm ->
                val y = altitudeToY(
                    altitudeKm = altitudeKm,
                    minAltitudeKm = elevationKm,
                    maxAltitudeKm = effectiveTopAltitudeKm,
                    plotTop = plotTop,
                    plotBottom = plotBottom,
                )
                drawLine(
                    color = outlineColor.copy(alpha = 0.15f),
                    start = Offset(plotLeft, y),
                    end = Offset(plotRight, y),
                    strokeWidth = 1.dp.toPx(),
                )
            }

            displayChart.timeSlots.forEachIndexed { index, startMinute ->
                val x = plotLeft + (index * columnWidth)
                val boundaryAlpha = if (startMinute % THERMIC_MAJOR_TIME_STEP_MINUTES == 0) 0.42f else 0.18f

                drawLine(
                    color = outlineColor.copy(alpha = boundaryAlpha),
                    start = Offset(x, plotTop),
                    end = Offset(x, plotBottom),
                    strokeWidth = 1.dp.toPx(),
                )

                drawLine(
                    color = outlineColor.copy(alpha = 0.12f),
                    start = Offset(x + (columnWidth / 2f), plotTop),
                    end = Offset(x + (columnWidth / 2f), plotBottom),
                    strokeWidth = 1.dp.toPx(),
                )

                drawLine(
                    color = outlineColor.copy(alpha = 0.4f),
                    start = Offset(x + (columnWidth / 2f), plotBottom + 4.dp.toPx()),
                    end = Offset(x + (columnWidth / 2f), plotBottom + 10.dp.toPx()),
                    strokeWidth = 1.dp.toPx(),
                )
            }

            val diagnosticsBySlot = displayChart.slotDiagnostics
                .associateBy { it.startMinuteOfDayLocal }

            // Build cloud altitude lookup per time slot for clipping cells at cloud base.
            val cloudAltitudeBySlot = diagnosticsBySlot
                .mapNotNull { (minute, diag) -> diag.cloudBaseKm?.let { minute to it } }
                .toMap()
                .ifEmpty {
                    displayChart.cloudMarkers
                        .groupBy { it.startMinuteOfDayLocal }
                        .mapValues { (_, markers) -> markers.minOf { it.altitudeKm } }
                }

            val pressureLevelDash = PathEffect.dashPathEffect(floatArrayOf(2f, 6f))
            displayChart.pressureLevelAltitudesKm
                .distinctBy { (it * 1000f).toInt() }
                .filter { it in elevationKm..effectiveTopAltitudeKm }
                .forEach { altitudeKm ->
                    val y = altitudeToY(
                        altitudeKm = altitudeKm,
                        minAltitudeKm = elevationKm,
                        maxAltitudeKm = effectiveTopAltitudeKm,
                        plotTop = plotTop,
                        plotBottom = plotBottom,
                    )
                    drawLine(
                        color = outlineColor.copy(alpha = 0.28f),
                        start = Offset(plotLeft, y),
                        end = Offset(plotRight, y),
                        strokeWidth = 1.dp.toPx(),
                        pathEffect = pressureLevelDash,
                    )
                    drawLine(
                        color = outlineColor.copy(alpha = 0.5f),
                        start = Offset(plotLeft - 7.dp.toPx(), y),
                        end = Offset(plotLeft, y),
                        strokeWidth = 1.dp.toPx(),
                    )
                }

            val thermalTopColor = Color(0xFFE07020)
            val cloudBaseColor = Color(0xFF2088E0)
            val moistTopColor = Color(0xFFA040C0)

            displayChart.timeSlots.forEachIndexed { index, minute ->
                val diag = diagnosticsBySlot[minute] ?: return@forEachIndexed
                val columnLeft = plotLeft + (index * columnWidth)
                val bandLeft = columnLeft + tileInset
                val bandRight = columnLeft + columnWidth - tileInset

                val rangeLowKm = diag.topLowKm.coerceIn(elevationKm, effectiveTopAltitudeKm)
                val rangeHighKm = diag.topHighKm.coerceIn(elevationKm, effectiveTopAltitudeKm)
                if (rangeHighKm > rangeLowKm + ALTITUDE_EPSILON) {
                    val rangeTopY = altitudeToY(rangeHighKm, elevationKm, effectiveTopAltitudeKm, plotTop, plotBottom)
                    val rangeBottomY = altitudeToY(rangeLowKm, elevationKm, effectiveTopAltitudeKm, plotTop, plotBottom)
                    drawHatchedVerticalBand(
                        left = bandLeft,
                        top = rangeTopY,
                        right = bandRight,
                        bottom = rangeBottomY,
                        fillColor = thermalTopColor.copy(alpha = 0.12f),
                        hatchColor = thermalTopColor.copy(alpha = 0.30f),
                    )
                }

                val cloudBaseKm = diag.cloudBaseKm
                val cloudTopKm = diag.moistEquilibriumTopKm?.coerceAtLeast(cloudBaseKm ?: 0f)
                if (cloudBaseKm != null && cloudBaseKm <= effectiveTopAltitudeKm) {
                    val baseY = altitudeToY(
                        altitudeKm = cloudBaseKm.coerceIn(elevationKm, effectiveTopAltitudeKm),
                        minAltitudeKm = elevationKm,
                        maxAltitudeKm = effectiveTopAltitudeKm,
                        plotTop = plotTop,
                        plotBottom = plotBottom,
                    )
                    val topY = altitudeToY(
                        altitudeKm = (cloudTopKm ?: cloudBaseKm).coerceIn(elevationKm, effectiveTopAltitudeKm),
                        minAltitudeKm = elevationKm,
                        maxAltitudeKm = effectiveTopAltitudeKm,
                        plotTop = plotTop,
                        plotBottom = plotBottom,
                    )
                    if (baseY - topY > 2.dp.toPx()) {
                        drawRoundRect(
                            color = cloudBaseColor.copy(alpha = 0.10f),
                            topLeft = Offset(bandLeft, topY),
                            size = Size(bandRight - bandLeft, baseY - topY),
                            cornerRadius = CornerRadius(2.dp.toPx()),
                        )
                    }
                }
            }

            displayChart.cells.forEach { cell ->
                val timeIndex = timeIndexLookup[cell.startMinuteOfDayLocal] ?: return@forEach
                val cloudAlt = cloudAltitudeBySlot[cell.startMinuteOfDayLocal]
                val visibleSegment = cell.visibleSegment(
                    minAltitudeKm = elevationKm,
                    maxAltitudeKm = effectiveTopAltitudeKm,
                    cloudBaseKm = cloudAlt,
                ) ?: return@forEach

                val topY = altitudeToY(
                    altitudeKm = visibleSegment.endAltitudeKm,
                    minAltitudeKm = elevationKm,
                    maxAltitudeKm = effectiveTopAltitudeKm,
                    plotTop = plotTop,
                    plotBottom = plotBottom,
                )
                val bottomY = altitudeToY(
                    altitudeKm = visibleSegment.startAltitudeKm,
                    minAltitudeKm = elevationKm,
                    maxAltitudeKm = effectiveTopAltitudeKm,
                    plotTop = plotTop,
                    plotBottom = plotBottom,
                )
                val cellHeight = bottomY - topY

                if (cellHeight <= tileInset * 2f) {
                    return@forEach
                }

                drawRoundRect(
                    color = thermicStrengthColor(cell.strengthMps),
                    topLeft = Offset(
                        x = plotLeft + (timeIndex * columnWidth) + tileInset,
                        y = topY + tileInset,
                    ),
                    size = Size(
                        width = columnWidth - (tileInset * 2f),
                        height = cellHeight - (tileInset * 2f),
                    ),
                    cornerRadius = CornerRadius(tileInset * 2f, tileInset * 2f),
                )
            }

            majorAltitudeTicks.forEach { altitudeKm ->
                val y = altitudeToY(
                    altitudeKm = altitudeKm,
                    minAltitudeKm = elevationKm,
                    maxAltitudeKm = effectiveTopAltitudeKm,
                    plotTop = plotTop,
                    plotBottom = plotBottom,
                )

                drawLine(
                    color = outlineColor.copy(alpha = 0.34f),
                    start = Offset(outerHorizontalPadding, y),
                    end = Offset(plotRight, y),
                    strokeWidth = 1.dp.toPx(),
                )
            }

            drawLine(
                color = outlineColor.copy(alpha = 0.35f),
                start = Offset(plotRight, plotTop),
                end = Offset(plotRight, plotBottom),
                strokeWidth = 1.dp.toPx(),
            )

            drawRoundRect(
                color = outlineColor.copy(alpha = 0.4f),
                topLeft = Offset(plotLeft, plotTop),
                size = Size(plotWidth, plotHeight),
                cornerRadius = CornerRadius(plotCornerRadius, plotCornerRadius),
                style = Stroke(width = 1.dp.toPx()),
            )

            // ── Diagnostic lines: nominal top, cloud base, moist top ──────
            val cloudBaseDash = PathEffect.dashPathEffect(floatArrayOf(10f, 4f))
            val moistTopDash = PathEffect.dashPathEffect(floatArrayOf(4f, 6f))
            val diagLineWidth = 2.dp.toPx()

            fun drawDiagnosticLine(
                color: Color,
                pathEffect: PathEffect?,
                strokeWidth: Float = diagLineWidth,
                valueSelector: (ThermicSlotDiagnostics) -> Float?,
            ) {
                var prevX: Float? = null
                var prevY: Float? = null
                displayChart.timeSlots.forEachIndexed { index, minute ->
                    val diag = diagnosticsBySlot[minute] ?: return@forEachIndexed
                    val altKm = valueSelector(diag) ?: run {
                        prevX = null; prevY = null; return@forEachIndexed
                    }
                    if (altKm < elevationKm || altKm > effectiveTopAltitudeKm) {
                        prevX = null; prevY = null; return@forEachIndexed
                    }
                    val x = plotLeft + (index * columnWidth) + (columnWidth / 2f)
                    val y = altitudeToY(altKm, elevationKm, effectiveTopAltitudeKm, plotTop, plotBottom)
                    if (prevX != null && prevY != null) {
                        drawLine(
                            color = color,
                            start = Offset(prevX!!, prevY!!),
                            end = Offset(x, y),
                            strokeWidth = strokeWidth,
                            pathEffect = pathEffect,
                        )
                    }
                    prevX = x; prevY = y
                }
            }

            // Moist/cloud top (draw first, behind others)
            drawDiagnosticLine(moistTopColor, moistTopDash) { it.moistEquilibriumTopKm }
            // Cloud base line
            drawDiagnosticLine(cloudBaseColor, cloudBaseDash) { it.cloudBaseKm }
            // Nominal dry thermal top line
            drawDiagnosticLine(thermalTopColor, pathEffect = null, strokeWidth = diagLineWidth * 1.2f) { it.topNominalKm }

            drawIntoCanvas { canvas ->
                majorAltitudeTicks.forEach { altitudeKm ->
                    val y = altitudeToY(
                        altitudeKm = altitudeKm,
                        minAltitudeKm = elevationKm,
                        maxAltitudeKm = effectiveTopAltitudeKm,
                        plotTop = plotTop,
                        plotBottom = plotBottom,
                    )
                    canvas.nativeCanvas.drawText(
                        formatAltitudeAxisValue(altitudeKm, displayUnits),
                        outerHorizontalPadding + 8.dp.toPx(),
                        y + (axisLabelPaint.textSize * 0.35f),
                        axisLabelPaint,
                    )
                }

                canvas.nativeCanvas.drawText(
                    altitudeAxisUnitLabel(displayUnits),
                    outerHorizontalPadding + 8.dp.toPx(),
                    plotTop + unitLabelPaint.textSize + 4.dp.toPx(),
                    unitLabelPaint,
                )

                displayChart.timeSlots.forEachIndexed { index, startMinute ->
                    if (!shouldDrawTimeLabel(
                            startMinuteOfDayLocal = startMinute,
                            displayedSlotCount = displayChart.timeSlots.size,
                        )
                    ) {
                        return@forEachIndexed
                    }

                    canvas.nativeCanvas.drawText(
                        formatTimeLabel(startMinute),
                        plotLeft + (index * columnWidth) + (columnWidth / 2f),
                        plotBottom + hourLabelPaint.textSize + 14.dp.toPx(),
                        hourLabelPaint,
                    )
                }
            }

            // ── Crosshair overlay ──────────────────────────────────
            crosshairPos?.let { pos ->
                val cx = pos.x.coerceIn(plotLeft, plotRight)
                val cy = pos.y.coerceIn(plotTop, plotBottom)

                val dash = PathEffect.dashPathEffect(floatArrayOf(8f, 6f))
                drawLine(
                    color = outlineColor.copy(alpha = 0.6f),
                    start = Offset(cx, plotTop),
                    end = Offset(cx, plotBottom),
                    strokeWidth = 1.dp.toPx(),
                    pathEffect = dash,
                )
                drawLine(
                    color = outlineColor.copy(alpha = 0.6f),
                    start = Offset(plotLeft, cy),
                    end = Offset(plotRight, cy),
                    strokeWidth = 1.dp.toPx(),
                    pathEffect = dash,
                )

                val reticleR = with(density) { 18.dp.toPx() }
                drawCircle(
                    color = outlineColor.copy(alpha = 0.8f),
                    radius = reticleR,
                    center = Offset(cx, cy),
                    style = Stroke(width = 2.dp.toPx()),
                )
                val tick = with(density) { 4.dp.toPx() }
                for (angleDeg in listOf(0f, 90f, 180f, 270f)) {
                    val rad = angleDeg * kotlin.math.PI.toFloat() / 180f
                    drawLine(
                        color = outlineColor.copy(alpha = 0.8f),
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
            }
        }

        cursorInfo?.let { info ->
            ThermicCursorInfoPanel(
                cursorInfo = info,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset {
                        thermicCursorPanelOffset(
                            cursorPosition = info.position,
                            chartSize = chartSize,
                            panelSize = cursorPanelSize,
                            avoidRadiusPx = with(density) { THERMIC_CURSOR_PANEL_AVOID_RADIUS.toPx() },
                            marginPx = with(density) { THERMIC_CURSOR_PANEL_MARGIN.toPx() },
                        )
                    }
                    .onSizeChanged { cursorPanelSize = it },
            )
        }
    }
}

private data class ThermicCursorInfo(
    val header: String,
    val details: List<String>,
    val position: Offset,
)

private data class ThermicCursorPanelCandidate(
    val x: Float,
    val y: Float,
)

private fun buildThermicCursorInfo(
    position: Offset?,
    chartSize: IntSize,
    chart: ThermicForecastChartUiModel,
    visibleTopAltitudeKm: Float,
    elevationKm: Float,
    displayUnits: DisplayUnits,
    axisWidthPx: Float,
    bottomAxisHeightPx: Float,
): ThermicCursorInfo? {
    if (position == null || chart.timeSlots.isEmpty() || chartSize.width <= 0 || chartSize.height <= 0) {
        return null
    }

    val plotLeft = axisWidthPx
    val plotTop = 0f
    val plotRight = chartSize.width.toFloat()
    val plotBottom = chartSize.height.toFloat() - bottomAxisHeightPx
    val plotWidth = plotRight - plotLeft
    val plotHeight = plotBottom - plotTop
    if (plotWidth <= 0f || plotHeight <= 0f) {
        return null
    }

    val effectiveTopAltitudeKm = max(
        elevationKm + visibleTopAltitudeKm,
        elevationKm + MIN_VISIBLE_ALTITUDE_RANGE_KM,
    )
    val displayChart = chart.aggregatedForDisplay(
        timeBucketSlotCount = resolveTimeBucketSlotCount(
            plotWidth = plotWidth,
            rawTimeSlotCount = chart.timeSlots.size,
        ),
        altitudeBucketStepKm = resolveAltitudeBucketStepKm(
            plotHeight = plotHeight,
            visibleTopAltitudeKm = effectiveTopAltitudeKm,
            rawAltitudeStepKm = THERMIC_DATA_ALTITUDE_STEP_KM,
        ),
    )
    if (displayChart.timeSlots.isEmpty()) {
        return null
    }

    val columnWidth = plotWidth / displayChart.timeSlots.size
    if (columnWidth <= 0f) {
        return null
    }

    val diagnosticsBySlot = displayChart.slotDiagnostics
        .associateBy { it.startMinuteOfDayLocal }
    val cloudAltitudeBySlot = diagnosticsBySlot
        .mapNotNull { (minute, diag) -> diag.cloudBaseKm?.let { minute to it } }
        .toMap()
        .ifEmpty {
            displayChart.cloudMarkers
                .groupBy { it.startMinuteOfDayLocal }
                .mapValues { (_, markers) -> markers.minOf { it.altitudeKm } }
        }

    val cx = position.x.coerceIn(plotLeft, plotRight)
    val cy = position.y.coerceIn(plotTop, plotBottom)
    val altKm = yToAltitude(cy, elevationKm, effectiveTopAltitudeKm, plotTop, plotBottom)
    val timeIdx = ((cx - plotLeft) / columnWidth).toInt()
        .coerceIn(0, displayChart.timeSlots.size - 1)
    val timeSlot = displayChart.timeSlots[timeIdx]
    val cell = displayChart.cells
        .filter { it.startMinuteOfDayLocal == timeSlot }
        .firstOrNull { candidate ->
            val visibleSegment = candidate.visibleSegment(
                minAltitudeKm = elevationKm,
                maxAltitudeKm = effectiveTopAltitudeKm,
                cloudBaseKm = cloudAltitudeBySlot[timeSlot],
            ) ?: return@firstOrNull false
            altKm in visibleSegment.startAltitudeKm..visibleSegment.endAltitudeKm
        }

    val diag = diagnosticsBySlot[timeSlot]
    val lines = mutableListOf<String>()
    lines += "${formatTimeLabel(timeSlot)}  Alt ${formatAltitudeKm(altKm, displayUnits)}"
    if (cell != null) {
        lines += "Air lift ${formatVerticalSpeedRange(cell.updraftLowMps, cell.updraftHighMps, displayUnits)}"
    }
    if (diag != null) {
        lines += "Top ${formatAltitudeKm(diag.topNominalKm, displayUnits)}  " +
            "raw range ${formatAltitudeKm(diag.topLowKm, displayUnits)}-" +
            formatAltitudeKm(diag.topHighKm, displayUnits)
        if (diag.topLowerPressureHpa != null && diag.topUpperPressureHpa != null) {
            lines += "Raw levels ${diag.topLowerPressureHpa.toInt()}-${diag.topUpperPressureHpa.toInt()} hPa"
        }
        lines += "Conf ${formatConfidenceLabel(diag.confidence)}  limit " +
            formatLimitingReasonLabel(diag.limitingReason)
        if (diag.triggerExcessC > 0f || diag.dryTopExcessC > 0f) {
            lines += "Heat trigger ${formatSignedValue(diag.triggerExcessC)} dry ${formatSignedValue(diag.dryTopExcessC)}"
        }
        diag.cloudBaseKm?.let { cloudBaseKm ->
            val moistTopKm = diag.moistEquilibriumTopKm
            lines += if (moistTopKm != null && moistTopKm > cloudBaseKm + 0.1f) {
                "Cloud layer ${formatAltitudeKm(cloudBaseKm, displayUnits)}-" +
                    formatAltitudeKm(moistTopKm, displayUnits)
            } else {
                "Cloud base ${formatAltitudeKm(cloudBaseKm, displayUnits)}"
            }
        }
        val convectionDiagnostics = buildList {
            diag.modelCapeJKg?.let { add("CAPE ${it.toInt()}") }
            diag.normalizedCinJKg?.let { add("CIN ${it.toInt()}") }
            diag.liftedIndexC?.let { add("LI ${formatSignedValue(it)}") }
        }
        if (convectionDiagnostics.isNotEmpty()) {
            lines += "Diag ${convectionDiagnostics.joinToString("  ")}"
        }
        diag.boundaryLayerHeightM?.let {
            lines += "PBL ${formatAltitudeMeters(it, displayUnits)}"
        }
    }

    return ThermicCursorInfo(
        header = lines.first(),
        details = lines.drop(1),
        position = Offset(cx, cy),
    )
}

private fun thermicCursorPanelOffset(
    cursorPosition: Offset,
    chartSize: IntSize,
    panelSize: IntSize,
    avoidRadiusPx: Float,
    marginPx: Float,
): IntOffset {
    if (chartSize.width <= 0 || chartSize.height <= 0 || panelSize.width <= 0 || panelSize.height <= 0) {
        return IntOffset.Zero
    }

    val panelWidth = panelSize.width.toFloat()
    val panelHeight = panelSize.height.toFloat()
    val horizontalInset = marginPx
    val verticalInset = marginPx
    val maxX = (chartSize.width.toFloat() - panelWidth - horizontalInset).coerceAtLeast(horizontalInset)
    val maxY = (chartSize.height.toFloat() - panelHeight - verticalInset).coerceAtLeast(verticalInset)

    fun placed(candidate: ThermicCursorPanelCandidate): ThermicCursorPanelCandidate {
        return ThermicCursorPanelCandidate(
            x = candidate.x.coerceIn(horizontalInset, maxX),
            y = candidate.y.coerceIn(verticalInset, maxY),
        )
    }

    fun ThermicCursorPanelCandidate.overlapsCursor(): Boolean {
        return x < cursorPosition.x + avoidRadiusPx &&
            x + panelWidth > cursorPosition.x - avoidRadiusPx &&
            y < cursorPosition.y + avoidRadiusPx &&
            y + panelHeight > cursorPosition.y - avoidRadiusPx
    }

    val above = ThermicCursorPanelCandidate(
        x = cursorPosition.x - panelWidth / 2f,
        y = cursorPosition.y - avoidRadiusPx - marginPx - panelHeight,
    )
    val below = ThermicCursorPanelCandidate(
        x = cursorPosition.x - panelWidth / 2f,
        y = cursorPosition.y + avoidRadiusPx + marginPx,
    )
    val right = ThermicCursorPanelCandidate(
        x = cursorPosition.x + avoidRadiusPx + marginPx,
        y = cursorPosition.y - panelHeight / 2f,
    )
    val left = ThermicCursorPanelCandidate(
        x = cursorPosition.x - avoidRadiusPx - marginPx - panelWidth,
        y = cursorPosition.y - panelHeight / 2f,
    )

    val verticalCandidates = if (cursorPosition.y > chartSize.height / 2f) {
        listOf(above, below, right, left)
    } else {
        listOf(below, above, right, left)
    }
    val resolved = verticalCandidates
        .map(::placed)
        .firstOrNull { !it.overlapsCursor() }
        ?: placed(if (cursorPosition.y > chartSize.height / 2f) above else below)

    return IntOffset(
        x = resolved.x.roundToInt(),
        y = resolved.y.roundToInt(),
    )
}

@Composable
private fun ThermicCursorInfoPanel(
    cursorInfo: ThermicCursorInfo,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .widthIn(max = THERMIC_CURSOR_PANEL_MAX_WIDTH)
            .semantics(mergeDescendants = true) {}
            .testTag(THERMIC_CURSOR_PANEL),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(6.dp),
        tonalElevation = 2.dp,
        shadowElevation = 2.dp,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = cursorInfo.header,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            cursorInfo.details.forEach { detail ->
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private fun resolveTimeBucketSlotCount(
    plotWidth: Float,
    rawTimeSlotCount: Int,
): Int {
    val rawColumnWidth = plotWidth / rawTimeSlotCount.coerceAtLeast(1)
    return max(1, ceil(MIN_TIME_BUCKET_WIDTH_PX / rawColumnWidth).toInt())
}

private fun resolveAltitudeBucketStepKm(
    plotHeight: Float,
    visibleTopAltitudeKm: Float,
    rawAltitudeStepKm: Float,
): Float {
    val rawRowHeight = plotHeight * (rawAltitudeStepKm / visibleTopAltitudeKm.coerceAtLeast(rawAltitudeStepKm))
    val bucketRowCount = max(1, ceil(MIN_ALTITUDE_BUCKET_HEIGHT_PX / rawRowHeight).toInt())
    return rawAltitudeStepKm * bucketRowCount
}

private fun shouldDrawTimeLabel(
    startMinuteOfDayLocal: Int,
    displayedSlotCount: Int,
): Boolean {
    return if (displayedSlotCount <= 8) {
        true
    } else {
        startMinuteOfDayLocal % THERMIC_MAJOR_TIME_STEP_MINUTES == 0
    }
}

private fun thermicMajorAltitudeStepKm(maxAltitudeKm: Float): Float {
    return if (maxAltitudeKm <= 3.5f) 0.5f else 1f
}

private fun buildAltitudeTicks(
    minAltitudeKm: Float,
    maxAltitudeKm: Float,
    stepKm: Float,
): List<Float> {
    val ticks = mutableListOf(minAltitudeKm)
    var nextTick = ceil(minAltitudeKm / stepKm) * stepKm

    while (nextTick < maxAltitudeKm) {
        if (nextTick > minAltitudeKm + ALTITUDE_EPSILON) {
            ticks += nextTick
        }
        nextTick += stepKm
    }

    if (maxAltitudeKm - ticks.last() > ALTITUDE_EPSILON) {
        ticks += maxAltitudeKm
    }

    return ticks.distinctBy { tick ->
        (tick * 100f).toInt()
    }
}

private fun altitudeToY(
    altitudeKm: Float,
    minAltitudeKm: Float,
    maxAltitudeKm: Float,
    plotTop: Float,
    plotBottom: Float,
): Float {
    val normalizedAltitude = (altitudeKm - minAltitudeKm) / (maxAltitudeKm - minAltitudeKm)
    return plotBottom - (normalizedAltitude * (plotBottom - plotTop))
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

private fun DrawScope.drawHatchedVerticalBand(
    left: Float,
    top: Float,
    right: Float,
    bottom: Float,
    fillColor: Color,
    hatchColor: Color,
) {
    val resolvedTop = minOf(top, bottom)
    val resolvedBottom = maxOf(top, bottom)
    val width = right - left
    val height = resolvedBottom - resolvedTop
    if (width <= 0f || height <= 0f) return

    drawRoundRect(
        color = fillColor,
        topLeft = Offset(left, resolvedTop),
        size = Size(width, height),
        cornerRadius = CornerRadius(2.dp.toPx()),
    )
    clipRect(left = left, top = resolvedTop, right = right, bottom = resolvedBottom) {
        var x = left - height
        val step = 8.dp.toPx()
        while (x < right + height) {
            drawLine(
                color = hatchColor,
                start = Offset(x, resolvedBottom),
                end = Offset(x + height, resolvedTop),
                strokeWidth = 1.dp.toPx(),
            )
            x += step
        }
    }
}

private fun formatConfidenceLabel(confidence: ThermalForecastConfidence): String {
    return when (confidence) {
        ThermalForecastConfidence.HIGH -> "HIGH"
        ThermalForecastConfidence.MEDIUM -> "MED"
        ThermalForecastConfidence.LOW -> "LOW"
    }
}

private fun formatLimitingReasonLabel(reason: ThermalLimitingReason): String {
    return when (reason) {
        ThermalLimitingReason.SURFACE_HEATING -> "heating"
        ThermalLimitingReason.INVERSION -> "inversion"
        ThermalLimitingReason.CLOUD_BASE -> "cloud base"
        ThermalLimitingReason.PROFILE_TOP -> "profile top"
        ThermalLimitingReason.PRECIPITATION -> "rain"
        ThermalLimitingReason.WEAK_RADIATION -> "weak sun"
        ThermalLimitingReason.WIND_SHEAR -> "wind shear"
        ThermalLimitingReason.MISSING_DATA -> "missing data"
    }
}

private fun formatSignedValue(value: Float): String {
    return String.format(Locale.US, "%+.1f", value)
}

private fun formatTimeLabel(startMinuteOfDayLocal: Int): String {
    val hour = startMinuteOfDayLocal / 60
    val minute = startMinuteOfDayLocal % 60
    return String.format(Locale.US, "%02d:%02d", hour, minute)
}

private const val MIN_VISIBLE_ALTITUDE_RANGE_KM = 0.75f
private const val ALTITUDE_EPSILON = 0.001f
private const val THERMIC_DATA_ALTITUDE_STEP_KM = 0.05f
private const val THERMIC_MAJOR_TIME_STEP_MINUTES = 180
private const val MIN_TIME_BUCKET_WIDTH_PX = 28f
private const val MIN_ALTITUDE_BUCKET_HEIGHT_PX = 20f
private val THERMIC_AXIS_WIDTH = 60.dp
private val THERMIC_BOTTOM_AXIS_HEIGHT = 38.dp
private val THERMIC_CURSOR_PANEL_AVOID_RADIUS = 28.dp
private val THERMIC_CURSOR_PANEL_MARGIN = 8.dp
private val THERMIC_CURSOR_PANEL_MAX_WIDTH = 360.dp

@Preview(name = "Thermic Default", showBackground = true, widthDp = 420, heightDp = 720)
@Composable
private fun ThermicForecastViewPreview() {
    CloudbasePredictorTheme {
        ThermicForecastView(
            uiState = PreviewData.forecastUiStateForMode(ForecastMode.THERMIC),
        )
    }
}

@Preview(name = "Thermic Zoomed Out", showBackground = true, widthDp = 420, heightDp = 720)
@Composable
private fun ThermicForecastViewZoomedOutPreview() {
    CloudbasePredictorTheme {
        ThermicForecastView(
            uiState = PreviewData.forecastUiStateForMode(
                mode = ForecastMode.THERMIC,
                topAltitudeKm = 6.5f,
            ),
        )
    }
}

@Preview(
    name = "Thermic Dark",
    showBackground = true,
    widthDp = 420,
    heightDp = 720,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun ThermicForecastViewDarkPreview() {
    CloudbasePredictorTheme(darkTheme = true) {
        ThermicForecastView(
            uiState = PreviewData.forecastUiStateForMode(ForecastMode.THERMIC),
        )
    }
}
