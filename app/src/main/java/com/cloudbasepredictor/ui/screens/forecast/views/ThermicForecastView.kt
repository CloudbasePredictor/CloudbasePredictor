package com.cloudbasepredictor.ui.screens.forecast.views

import android.content.res.Configuration
import android.graphics.Paint
import android.graphics.Typeface
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
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
    val cellTextColor = MaterialTheme.colorScheme.onSurface
    val tooltipBackgroundColor = MaterialTheme.colorScheme.surface
    val gridBackgroundColor = lerp(
        start = MaterialTheme.colorScheme.surface,
        stop = MaterialTheme.colorScheme.onSurface,
        fraction = 0.035f,
    )
    val outlineColor = MaterialTheme.colorScheme.outlineVariant

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
    val tooltipHeaderPaint = remember(density, cellTextColor) {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = cellTextColor.toArgb()
            textSize = with(density) { 12.sp.toPx() }
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        }
    }
    val tooltipBodyPaint = remember(density, cellTextColor) {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = cellTextColor.toArgb()
            textSize = with(density) { 11.sp.toPx() }
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
        }
    }
    var crosshairPos by remember { mutableStateOf<Offset?>(null) }
    val latestVisibleTopAltitudeKm = rememberUpdatedState(visibleTopAltitudeKm)

    Canvas(
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
        if (chart.timeSlots.isEmpty()) {
            return@Canvas
        }

        val axisWidth = with(density) { 60.dp.toPx() }
        val outerHorizontalPadding = 0f
        val axisToPlotSpacing = 0f
        val bottomAxisHeight = with(density) { 38.dp.toPx() }
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
            val tooltipLines = mutableListOf<String>()
            tooltipLines += "${formatTimeLabel(timeSlot)}  Alt ${formatAltitudeKm(altKm, displayUnits)}"
            if (cell != null) {
                tooltipLines += "Air lift ${formatVerticalSpeedRange(cell.updraftLowMps, cell.updraftHighMps, displayUnits)}"
            }
            if (diag != null) {
                tooltipLines += "Top ${formatAltitudeKm(diag.topNominalKm, displayUnits)}  " +
                    "raw range ${formatAltitudeKm(diag.topLowKm, displayUnits)}-" +
                    formatAltitudeKm(diag.topHighKm, displayUnits)
                if (diag.topLowerPressureHpa != null && diag.topUpperPressureHpa != null) {
                    tooltipLines += "Raw levels ${diag.topLowerPressureHpa.toInt()}-${diag.topUpperPressureHpa.toInt()} hPa"
                }
                tooltipLines += "Conf ${formatConfidenceLabel(diag.confidence)}  limit " +
                    formatLimitingReasonLabel(diag.limitingReason)
                if (diag.triggerExcessC > 0f || diag.dryTopExcessC > 0f) {
                    tooltipLines += "Heat trigger ${formatSignedValue(diag.triggerExcessC)} dry ${formatSignedValue(diag.dryTopExcessC)}"
                }
                diag.cloudBaseKm?.let { cloudBaseKm ->
                    val moistTopKm = diag.moistEquilibriumTopKm
                    tooltipLines += if (moistTopKm != null && moistTopKm > cloudBaseKm + 0.1f) {
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
                    tooltipLines += "Diag ${convectionDiagnostics.joinToString("  ")}"
                }
                diag.boundaryLayerHeightM?.let {
                    tooltipLines += "PBL ${formatAltitudeMeters(it, displayUnits)}"
                }
            }

            val lineH = max(tooltipHeaderPaint.textSize, tooltipBodyPaint.textSize) * 1.35f
            val maxTextW = tooltipLines.withIndex().maxOf { (index, line) ->
                if (index == 0) tooltipHeaderPaint.measureText(line) else tooltipBodyPaint.measureText(line)
            }
            val padH = with(density) { 8.dp.toPx() }
            val padV = with(density) { 6.dp.toPx() }
            val ttW = maxTextW + padH * 2
            val ttH = lineH * tooltipLines.size + padV * 2
            val preferredTtX = if (cx + reticleR + ttW + 8.dp.toPx() < plotRight)
                cx + reticleR + 8.dp.toPx()
            else
                cx - reticleR - ttW - 8.dp.toPx()
            val ttX = preferredTtX.coerceIn(plotLeft, (plotRight - ttW).coerceAtLeast(plotLeft))
            val ttY = (cy - ttH / 2f).coerceIn(plotTop, plotBottom - ttH)

            drawRoundRect(
                color = tooltipBackgroundColor.copy(alpha = 0.96f),
                topLeft = Offset(ttX, ttY),
                size = Size(ttW, ttH),
                cornerRadius = CornerRadius(4.dp.toPx()),
            )
            drawRoundRect(
                color = outlineColor.copy(alpha = 0.4f),
                topLeft = Offset(ttX, ttY),
                size = Size(ttW, ttH),
                cornerRadius = CornerRadius(4.dp.toPx()),
                style = Stroke(width = 1.dp.toPx()),
            )
            drawIntoCanvas { canvas ->
                tooltipLines.forEachIndexed { idx, line ->
                    val paint = if (idx == 0) tooltipHeaderPaint else tooltipBodyPaint
                    canvas.nativeCanvas.drawText(
                        line,
                        ttX + padH,
                        ttY + padV + (idx + 1) * lineH - lineH * 0.15f,
                        paint,
                    )
                }
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
