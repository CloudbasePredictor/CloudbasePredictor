@file:Suppress(
    "CyclomaticComplexMethod",
    "FunctionNaming",
    "LongMethod",
    "MagicNumber",
    "MaxLineLength",
)

package com.cloudbasepredictor.ui.screens.forecast.views

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
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
import com.cloudbasepredictor.data.units.formatWindSpeed
import com.cloudbasepredictor.domain.forecast.dryAdiabatTempC
import com.cloudbasepredictor.domain.forecast.mixingRatioTemperatureC
import com.cloudbasepredictor.domain.forecast.moistAdiabatTempC
import com.cloudbasepredictor.domain.forecast.potentialTemperatureK
import com.cloudbasepredictor.model.ForecastMode
import com.cloudbasepredictor.ui.preview.ForecastPreviewData
import com.cloudbasepredictor.ui.preview.ForecastPreviewTheme
import com.cloudbasepredictor.ui.screens.forecast.ForecastTestTags.STUVE_CHART_CANVAS
import com.cloudbasepredictor.ui.screens.forecast.ForecastTestTags.STUVE_RENDERER_DESCRIPTION
import com.cloudbasepredictor.ui.screens.forecast.STUVE_DRY_ADIABAT_THETAS_K
import com.cloudbasepredictor.ui.screens.forecast.STUVE_MIXING_RATIO_VALUES_GKG
import com.cloudbasepredictor.ui.screens.forecast.STUVE_MOIST_ADIABAT_THETAS_K
import com.cloudbasepredictor.ui.screens.forecast.StuveActiveThetaKKey
import com.cloudbasepredictor.ui.screens.forecast.StuveForecastChartUiModel
import com.cloudbasepredictor.ui.screens.forecast.StuveProfilePoint
import com.cloudbasepredictor.ui.screens.forecast.buildRenderableParcelPressures
import com.cloudbasepredictor.ui.screens.forecast.interpolateProfileHeightMeters
import com.cloudbasepredictor.ui.screens.forecast.interpolateProfileTemperature
import com.cloudbasepredictor.ui.screens.forecast.pressureToApproxHeightMeters
import androidx.compose.ui.tooling.preview.Preview

@Composable
internal fun StuveDiagramCanvas(
    chart: StuveForecastChartUiModel,
    visibleTopAltitudeKm: Float,
    displayUnits: DisplayUnits,
    onVisibleTopAltitudeChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    overlayBackHandler: ForecastOverlayBackHandler = ignoreForecastOverlayBack,
) {
    val density = LocalDensity.current
    val axisLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface
    val surfaceColor = MaterialTheme.colorScheme.surface
    val gridBackgroundColor = lerp(
        start = MaterialTheme.colorScheme.surface,
        stop = MaterialTheme.colorScheme.onSurface,
        fraction = 0.02f,
    )
    val outlineColor = MaterialTheme.colorScheme.outlineVariant
    val latestVisibleTopAltitudeKm = rememberUpdatedState(visibleTopAltitudeKm)

    val textMeasurer = rememberTextMeasurer()
    val axisLabelStyle = remember(axisLabelColor) {
        TextStyle(
            color = axisLabelColor,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
        )
    }
    val altitudeLabelStyle = remember(axisLabelColor) {
        TextStyle(
            color = axisLabelColor,
            fontSize = 9.sp,
            fontFamily = FontFamily.Monospace,
        )
    }
    val tempLabelStyle = remember(axisLabelColor) {
        TextStyle(
            color = axisLabelColor,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
        )
    }
    val mixingRatioLabelStyle = remember {
        TextStyle(
            color = Color(0xFF5C88B4),
            fontSize = 8.sp,
            fontFamily = FontFamily.Monospace,
        )
    }
    val windLabelStyle = remember(axisLabelColor) {
        TextStyle(
            color = axisLabelColor,
            fontSize = 8.sp,
            fontFamily = FontFamily.Monospace,
        )
    }
    var cursorState by remember(
        chart.selectedHour,
        chart.surfacePressureHpa,
        chart.temperatureProfile,
        chart.dewpointProfile,
    ) {
        mutableStateOf<SkewTCursorState?>(null)
    }

    // Pressing the system back button first dismisses the tap overlay (cursor readout); only once
    // there is no overlay does back fall through to navigation. Disabled when no overlay is shown.
    overlayBackHandler(cursorState != null) {
        cursorState = null
    }

    // Heating-handle state: tracks how many °C the user has shifted the parcel start
    // temperature away from the forecast default via the bottom drag handle.
    // Reset whenever the selected hour or surface pressure changes (new sounding).
    var heatingDeltaC by remember(chart.selectedHour, chart.surfacePressureHpa) {
        mutableFloatStateOf(0f)
    }

    // Canvas pixel dimensions — updated by onSizeChanged, used to compute layout outside the
    // Canvas draw block for gesture detection and semantics.
    var canvasWidth by remember { mutableFloatStateOf(0f) }
    var canvasHeight by remember { mutableFloatStateOf(0f) }

    // Layout constants that mirror the computation inside the Canvas draw block.
    val leftAxisWidthPx = with(density) { 40.dp.toPx() }
    val rightAltitudeWidthPx = with(density) { 42.dp.toPx() }
    val rightWindWidthPx = with(density) { 58.dp.toPx() }
    val bottomAxisHeightPx = with(density) { 34.dp.toPx() }
    val topPaddingPx = with(density) { 16.dp.toPx() }

    // Derived layout (may be zero on first composition before onSizeChanged fires).
    val outerPlotLeft = leftAxisWidthPx
    val outerPlotRight = (canvasWidth - rightAltitudeWidthPx - rightWindWidthPx).coerceAtLeast(outerPlotLeft)
    val outerPlotWidth = (outerPlotRight - outerPlotLeft).coerceAtLeast(0f)
    val outerPlotBottom = (canvasHeight - bottomAxisHeightPx).coerceAtLeast(topPaddingPx)

    val outerChartBottomPressure = (chart.surfacePressureHpa + 20f).coerceAtMost(SKEWT_BOTTOM_PRESSURE)
    val outerTopPressure = altitudeKmToApproxPressureHpa(visibleTopAltitudeKm)
        .coerceIn(SKEWT_MIN_TOP_PRESSURE, outerChartBottomPressure - 50f)
    val outerProjection = remember(
        chart,
        outerTopPressure,
        outerChartBottomPressure,
        outerPlotLeft,
        outerPlotRight,
        topPaddingPx,
        outerPlotBottom,
    ) {
        buildSkewTProjection(
            chart = chart,
            topPressure = outerTopPressure,
            bottomPressure = outerChartBottomPressure,
            plotLeft = outerPlotLeft,
            plotRight = outerPlotRight,
            plotTop = topPaddingPx,
            plotBottom = outerPlotBottom,
        )
    }

    // Default parcel start temperature (first point of the pre-built ascent path).
    val defaultParcelStartTempC = remember(chart.parcelAscentPath, chart.temperatureProfile) {
        chart.parcelAscentPath.firstOrNull()?.temperatureC
            ?: chart.temperatureProfile.firstOrNull()?.temperatureC
            ?: 15f
    }

    // Memoised profile levels and parcel pressures used for live parcel recomputation.
    val profileLevels = remember(chart.temperatureProfile, chart.dewpointProfile) {
        buildMinimalProfileLevels(chart)
    }
    val parcelPressures = remember(chart.surfacePressureHpa, chart.pressureLevels) {
        buildRenderableParcelPressures(chart.surfacePressureHpa, chart.pressureLevels)
    }

    // Anchor temperature for the active cursor, computed from its X position.
    // Null when no cursor is active or the canvas has not been sized yet.
    val anchorTemperatureC: Float? = remember(cursorState, outerProjection) {
        val cursor = cursorState ?: return@remember null
        if (outerPlotWidth <= 0f) return@remember null
        val pressure = outerProjection.yToPressure(cursor.y)
            .coerceIn(outerTopPressure, outerChartBottomPressure)
        outerProjection.xToTemperature(cursor.x, pressure)
    }

    // Active parcel guide theta K exposed through semantics so that tests can verify
    // that different tap positions produce different parcel guides.
    val activeGuideThetaK: Float? = remember(anchorTemperatureC, cursorState, heatingDeltaC) {
        when {
            anchorTemperatureC != null && cursorState != null -> {
                val pressure = yToPressure(
                    cursorState!!.y, topPaddingPx, outerPlotBottom, outerTopPressure, outerChartBottomPressure,
                ).coerceIn(outerTopPressure, outerChartBottomPressure)
                potentialTemperatureK(anchorTemperatureC, pressure)
            }
            heatingDeltaC != 0f ->
                potentialTemperatureK(defaultParcelStartTempC + heatingDeltaC, chart.surfacePressureHpa)
            else -> null
        }
    }

    // Updated lambdas for gesture callbacks — always capture the latest state.
    val latestIsInHeatingZone = rememberUpdatedState { x: Float, y: Float ->
        if (outerPlotWidth <= 0f) return@rememberUpdatedState false
        val handleX = outerProjection
            .temperatureToX(defaultParcelStartTempC + heatingDeltaC, outerChartBottomPressure)
            .coerceIn(outerPlotLeft, outerPlotRight)
        val handleY = outerPlotBottom
        val touchRadiusPx = with(density) { 28.dp.toPx() }
        val dx = x - handleX
        val dy = y - handleY
        kotlin.math.sqrt(dx * dx + dy * dy) < touchRadiusPx
    }
    val latestOnHeatingDragDelta = rememberUpdatedState { deltaX: Float ->
        if (outerPlotWidth > 0f) {
            val tempSpan = outerProjection.temperatureRange.spanC
            heatingDeltaC = (heatingDeltaC + deltaX / outerPlotWidth * tempSpan)
                .coerceIn(-20f, 20f)
        }
    }

    Canvas(
        modifier = modifier
            .clipToBounds()
            .testTag(STUVE_CHART_CANVAS)
            .onSizeChanged { size ->
                canvasWidth = size.width.toFloat()
                canvasHeight = size.height.toFloat()
            }
            .semantics {
                contentDescription = STUVE_RENDERER_DESCRIPTION
                stateDescription = when {
                    cursorState?.isPinned == true -> "pinned"
                    cursorState != null -> "tracking"
                    else -> "idle"
                }
                activeGuideThetaK?.let { set(StuveActiveThetaKKey, it) }
            }
            .pointerInput(chart.selectedHour, chart.surfacePressureHpa) {
                detectSkewTGestures(
                    currentTopAltitudeKm = { latestVisibleTopAltitudeKm.value },
                    onVisibleTopAltitudeChange = onVisibleTopAltitudeChange,
                    onCursorStateChanged = { cursorState = it },
                    isInHeatingZone = { x, y -> latestIsInHeatingZone.value(x, y) },
                    onHeatingHandleDragDelta = { deltaX -> latestOnHeatingDragDelta.value(deltaX) },
                )
            },
    ) {
        drawRect(
            color = surfaceColor,
            size = size,
        )

        val chartBottomPressure = (chart.surfacePressureHpa + 20f)
            .coerceAtMost(SKEWT_BOTTOM_PRESSURE)
        val topPressure = altitudeKmToApproxPressureHpa(visibleTopAltitudeKm)
            .coerceIn(SKEWT_MIN_TOP_PRESSURE, chartBottomPressure - 50f)

        val leftAxisWidth = with(density) { 40.dp.toPx() }
        val rightAltitudeWidth = with(density) { 42.dp.toPx() }
        val rightWindWidth = with(density) { 58.dp.toPx() }
        val bottomAxisHeight = with(density) { 34.dp.toPx() }
        val topPadding = with(density) { 16.dp.toPx() }

        val plotLeft = leftAxisWidth
        val plotTop = topPadding
        val plotRight = size.width - rightAltitudeWidth - rightWindWidth
        val plotBottom = size.height - bottomAxisHeight
        val plotWidth = plotRight - plotLeft
        val plotHeight = plotBottom - plotTop

        if (plotWidth <= 0f || plotHeight <= 0f) return@Canvas

        val projection = buildSkewTProjection(
            chart = chart,
            topPressure = topPressure,
            bottomPressure = chartBottomPressure,
            plotLeft = plotLeft,
            plotRight = plotRight,
            plotTop = plotTop,
            plotBottom = plotBottom,
        )
        val tempAxisRange = projection.temperatureRange
        val temperatureAxisLabels = buildTemperatureAxisLabels(tempAxisRange)

        fun pressureToY(pressureHpa: Float) = projection.pressureToY(pressureHpa)
        fun temperatureToX(temperatureC: Float, pressureHpa: Float) =
            projection.temperatureToX(temperatureC, pressureHpa)
        fun yToPressure(y: Float) = projection.yToPressure(y)

        val pressureLabels = selectPressureLabels(
            topPressure = topPressure,
            plotHeight = plotHeight,
        )

        // Resolve anchor temperature from cursor X position once, at Canvas block scope,
        // so it can be used both inside clipRect (overlay drawing) and by the label drawing pass
        // (inline label drawing) without re-computing it twice.
        val drawAnchorTemperatureC: Float? = cursorState?.let { cursor ->
            val pressure = yToPressure(cursor.y).coerceIn(topPressure, chartBottomPressure)
            projection.xToTemperature(cursor.x, pressure)
        }

        drawRect(
            color = gridBackgroundColor,
            topLeft = Offset(plotLeft, plotTop),
            size = Size(plotWidth, plotHeight),
        )

        drawMoistureCueStrip(
            chart = chart,
            plotTop = plotTop,
            plotBottom = plotBottom,
            plotRight = plotRight,
            pressureToY = ::pressureToY,
            density = density,
        )

        clipRect(plotLeft, plotTop, plotRight, plotBottom) {
            temperatureAxisLabels.forEach { isothermTemp ->
                val alpha = if (isothermTemp.toInt() % 20 == 0) 0.35f else 0.15f
                drawLine(
                    color = outlineColor.copy(alpha = alpha),
                    start = Offset(temperatureToX(isothermTemp, chartBottomPressure), pressureToY(chartBottomPressure)),
                    end = Offset(temperatureToX(isothermTemp, topPressure), pressureToY(topPressure)),
                    strokeWidth = 1.dp.toPx(),
                )
            }

            pressureLabels.forEach { pressure ->
                val alpha = if (pressure.toInt() % 200 == 0) 0.4f else 0.2f
                drawLine(
                    color = outlineColor.copy(alpha = alpha),
                    start = Offset(plotLeft, pressureToY(pressure)),
                    end = Offset(plotRight, pressureToY(pressure)),
                    strokeWidth = 1.dp.toPx(),
                )
            }

            STUVE_DRY_ADIABAT_THETAS_K.forEach { theta ->
                drawAdiabat(
                    pressures = STUVE_DRY_REFERENCE_PRESSURES.filter { it in topPressure..chartBottomPressure },
                    computeTemp = { pressure -> dryAdiabatTempC(theta, pressure) },
                    mapXY = { temperature, pressure -> Offset(temperatureToX(temperature, pressure), pressureToY(pressure)) },
                    plotLeft = plotLeft,
                    plotRight = plotRight,
                    plotTop = plotTop,
                    plotBottom = plotBottom,
                    color = Color(0xFF4E9B64).copy(alpha = 0.32f),
                    strokeWidth = 1.dp.toPx(),
                )
            }

            val moistPressures = buildReferencePressures(chartBottomPressure, topPressure, stepHpa = 25f)
            STUVE_MOIST_ADIABAT_THETAS_K.forEach { theta ->
                drawAdiabat(
                    pressures = moistPressures,
                    computeTemp = { pressure -> moistAdiabatTempC(theta, pressure) },
                    mapXY = { temperature, pressure -> Offset(temperatureToX(temperature, pressure), pressureToY(pressure)) },
                    plotLeft = plotLeft,
                    plotRight = plotRight,
                    plotTop = plotTop,
                    plotBottom = plotBottom,
                    color = Color(0xFF2F8BAA).copy(alpha = 0.28f),
                    strokeWidth = 1.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx())),
                )
            }

            val mixingRatioPressures = buildReferencePressures(chartBottomPressure, topPressure, stepHpa = 50f)
            STUVE_MIXING_RATIO_VALUES_GKG.forEach { mixingRatio ->
                drawAdiabat(
                    pressures = mixingRatioPressures,
                    computeTemp = { pressure -> mixingRatioTemperatureC(mixingRatio, pressure) },
                    mapXY = { temperature, pressure -> Offset(temperatureToX(temperature, pressure), pressureToY(pressure)) },
                    plotLeft = plotLeft,
                    plotRight = plotRight,
                    plotTop = plotTop,
                    plotBottom = plotBottom,
                    color = Color(0xFF6E93C0).copy(alpha = 0.24f),
                    strokeWidth = 1.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 4.dp.toPx())),
                )
            }

            drawSkewTProfile(
                points = chart.temperatureProfile,
                mapXY = { temperature, pressure -> Offset(temperatureToX(temperature, pressure), pressureToY(pressure)) },
                plotLeft = plotLeft,
                plotRight = plotRight,
                plotTop = plotTop,
                plotBottom = plotBottom,
                color = Color(0xFFD83A3A),
                strokeWidth = 2.6f.dp.toPx(),
                drawDataDots = true,
                dataDotRadius = 2.6f.dp.toPx(),
            )

            drawSkewTProfile(
                points = chart.dewpointProfile,
                mapXY = { temperature, pressure -> Offset(temperatureToX(temperature, pressure), pressureToY(pressure)) },
                plotLeft = plotLeft,
                plotRight = plotRight,
                plotTop = plotTop,
                plotBottom = plotBottom,
                color = Color(0xFF2E6FB5),
                strokeWidth = 2.1f.dp.toPx(),
                drawDataDots = true,
                dataDotRadius = 2.2f.dp.toPx(),
            )

            // The default dashed parcel guide is hidden while the tap overlay is showing, so it does
            // not clutter the interactive parcel drawn through the tapped point.
            if (shouldDrawDefaultParcelGuide(isCursorActive = cursorState != null)) {
                drawSkewTProfile(
                    points = chart.parcelAscentPath,
                    mapXY = { temperature, pressure -> Offset(temperatureToX(temperature, pressure), pressureToY(pressure)) },
                    plotLeft = plotLeft,
                    plotRight = plotRight,
                    plotTop = plotTop,
                    plotBottom = plotBottom,
                    color = onSurfaceColor.copy(alpha = 0.58f),
                    strokeWidth = 2f.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 5.dp.toPx())),
                )
            }

            // ── Interactive parcel overlay ──────────────────────────────────────
            val interactiveParcelPath: List<StuveProfilePoint>? = when {
                drawAnchorTemperatureC != null && cursorState != null -> {
                    val anchorPressure = yToPressure(cursorState!!.y)
                        .coerceIn(topPressure, chartBottomPressure)
                    buildInteractiveParcelFromPoint(
                        anchorTemperatureC = drawAnchorTemperatureC,
                        anchorPressureHpa = anchorPressure,
                        chart = chart,
                        parcelPressures = parcelPressures,
                    )
                }
                heatingDeltaC != 0f ->
                    buildInteractiveParcelFromSurface(
                        parcelStartTempC = defaultParcelStartTempC + heatingDeltaC,
                        chart = chart,
                        profileLevels = profileLevels,
                        parcelPressures = parcelPressures,
                    )
                else -> null
            }
            val activeParcelPath = interactiveParcelPath ?: chart.parcelAscentPath

            interactiveParcelPath?.let { path ->
                if (drawAnchorTemperatureC != null && cursorState != null) {
                    val anchorPressure = yToPressure(cursorState!!.y)
                        .coerceIn(topPressure, chartBottomPressure)
                    val anchorPoint = StuveProfilePoint(
                        pressureHpa = anchorPressure,
                        temperatureC = drawAnchorTemperatureC,
                    )
                    val drySegment = listOf(anchorPoint) + path
                        .filter { it.pressureHpa > anchorPressure + 0.01f }
                        .sortedBy { it.pressureHpa }
                    val moistSegment = listOf(anchorPoint) + path
                        .filter { it.pressureHpa < anchorPressure - 0.01f }
                        .sortedByDescending { it.pressureHpa }

                    if (drySegment.size > 1) {
                        drawSkewTProfile(
                            points = drySegment,
                            mapXY = { temperature, pressure ->
                                Offset(temperatureToX(temperature, pressure), pressureToY(pressure))
                            },
                            plotLeft = plotLeft,
                            plotRight = plotRight,
                            plotTop = plotTop,
                            plotBottom = plotBottom,
                            color = Color(0xFF59A36A).copy(alpha = 0.88f),
                            strokeWidth = 0.8f.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(
                                floatArrayOf(6.dp.toPx(), 4.dp.toPx()),
                            ),
                        )
                    }
                    if (moistSegment.size > 1) {
                        drawSkewTProfile(
                            points = moistSegment,
                            mapXY = { temperature, pressure ->
                                Offset(temperatureToX(temperature, pressure), pressureToY(pressure))
                            },
                            plotLeft = plotLeft,
                            plotRight = plotRight,
                            plotTop = plotTop,
                            plotBottom = plotBottom,
                            color = Color(0xFF59A36A).copy(alpha = 0.88f),
                            strokeWidth = 2.4f.dp.toPx(),
                        )
                    }
                } else {
                    drawSkewTProfile(
                        points = path,
                        mapXY = { temperature, pressure ->
                            Offset(temperatureToX(temperature, pressure), pressureToY(pressure))
                        },
                        plotLeft = plotLeft,
                        plotRight = plotRight,
                        plotTop = plotTop,
                        plotBottom = plotBottom,
                        color = Color(0xFF59A36A).copy(alpha = 0.88f),
                        strokeWidth = 2.4f.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(
                            floatArrayOf(10.dp.toPx(), 5.dp.toPx()),
                        ),
                    )
                }
            }

            chart.cclPressureHpa?.let { pressure ->
                val y = pressureToY(pressure)
                drawLine(
                    color = Color(0xFFB36A27).copy(alpha = 0.5f),
                    start = Offset(plotLeft, y),
                    end = Offset(plotRight, y),
                    strokeWidth = 1.5f.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(7.dp.toPx(), 4.dp.toPx())),
                )
            }

            val activeCursor = cursorState
            if (activeCursor != null) {
                val readout = buildCursorReadout(
                    chart = chart,
                    pressureHpa = yToPressure(activeCursor.y),
                    anchorTemperatureC = drawAnchorTemperatureC,
                    parcelPath = activeParcelPath,
                )
                val cursorY = pressureToY(readout.pressureHpa)
                if (cursorY in plotTop..plotBottom) {
                    drawCursorOverlay(
                        readout = readout,
                        cursorY = cursorY,
                        topPressure = topPressure,
                        bottomPressure = chartBottomPressure,
                        plotLeft = plotLeft,
                        plotRight = plotRight,
                        onSurfaceColor = onSurfaceColor,
                        temperatureToX = ::temperatureToX,
                        pressureToY = ::pressureToY,
                        showThermoGuides = false,
                    )
                }
            }
        }

        drawRect(
            color = outlineColor.copy(alpha = 0.5f),
            topLeft = Offset(plotLeft, plotTop),
            size = Size(plotWidth, plotHeight),
            style = Stroke(width = 1.dp.toPx()),
        )

        // ── Heating handle: drawn at plotBottom, outside the clipped area ──────
        val activeParcelStartTempC = defaultParcelStartTempC + heatingDeltaC
        val handleX = temperatureToX(activeParcelStartTempC, chartBottomPressure)
            .coerceIn(plotLeft, plotRight)
        val handleColor = Color(0xFF59A36A)
        // Vertical stem from bottom of plot area down to the handle circle.
        drawLine(
            color = handleColor.copy(alpha = 0.75f),
            start = Offset(handleX, plotBottom),
            end = Offset(handleX, plotBottom + with(density) { 8.dp.toPx() }),
            strokeWidth = 2.dp.toPx(),
        )
        // Handle circle — tap / drag target.
        drawCircle(
            color = handleColor,
            radius = with(density) { 6.dp.toPx() },
            center = Offset(handleX, plotBottom + with(density) { 8.dp.toPx() }),
        )
        // Small tick on the plot bottom edge to show the handle position.
        drawLine(
            color = handleColor,
            start = Offset(handleX, plotBottom - with(density) { 4.dp.toPx() }),
            end = Offset(handleX, plotBottom),
            strokeWidth = 2.dp.toPx(),
        )

        chart.windBarbs.forEach { barb ->
            val y = pressureToY(barb.pressureHpa)
            if (y in plotTop..plotBottom) {
                drawWindBarb(
                    centerX = plotRight + rightAltitudeWidth + rightWindWidth / 2f,
                    centerY = y,
                    speedKmh = barb.speedKmh,
                    directionDeg = barb.directionDeg,
                    barbSize = with(density) { 20.dp.toPx() },
                    color = onSurfaceColor,
                )
            }
        }

        pressureLabels.forEach { pressure ->
            val y = pressureToY(pressure)
            if (y in plotTop..plotBottom) {
                drawCanvasText(
                    textMeasurer = textMeasurer,
                    text = "${pressure.toInt()}",
                    x = leftAxisWidth - 4.dp.toPx(),
                    baselineY = y + with(density) { 10.sp.toPx() } * 0.35f,
                    style = axisLabelStyle,
                    anchor = CanvasTextAnchor.END,
                )
            }
        }

        val temperatureLabelSizePx = with(density) { 10.sp.toPx() }
        val temperatureAxisBaseline = plotBottom + temperatureLabelSizePx + with(density) { 6.dp.toPx() }
        val temperatureReadoutBaseline = plotBottom + temperatureLabelSizePx + with(density) { 22.dp.toPx() }
        val temperatureReadoutLabelRight = (plotRight + rightAltitudeWidth + with(density) { 8.dp.toPx() })
            .coerceAtMost(size.width - with(density) { 4.dp.toPx() })
        temperatureAxisLabels.forEach { tempLabel ->
            val x = temperatureToX(tempLabel, chartBottomPressure)
            if (x in plotLeft..plotRight) {
                drawCanvasText(
                    textMeasurer = textMeasurer,
                    text = "${tempLabel.toInt()}°",
                    x = x,
                    baselineY = temperatureAxisBaseline,
                    style = tempLabelStyle,
                    anchor = CanvasTextAnchor.CENTER,
                )
            }
        }

        STUVE_MIXING_RATIO_VALUES_GKG.forEach { mixingRatio ->
            val x = temperatureToX(mixingRatioTemperatureC(mixingRatio, topPressure), topPressure)
            if (x in plotLeft..plotRight) {
                drawCanvasText(
                    textMeasurer = textMeasurer,
                    text = if (mixingRatio < 1f) formatFixedDecimal(mixingRatio, fractionDigits = 1)
                    else "${mixingRatio.toInt()}",
                    x = x,
                    baselineY = plotTop - 2.dp.toPx(),
                    style = mixingRatioLabelStyle,
                    anchor = CanvasTextAnchor.CENTER,
                )
            }
        }

        chart.cclPressureHpa?.let { pressure ->
            drawMarkerLabel(
                textMeasurer = textMeasurer,
                text = "CCL",
                y = pressureToY(pressure),
                x = plotLeft + 4.dp.toPx(),
                color = Color(0xFFB36A27),
                density = density,
                yOffsetPx = with(density) { 12.dp.toPx() },
            )
        }

        chart.windBarbs.forEach { barb ->
            val y = pressureToY(barb.pressureHpa)
            if (y in plotTop..plotBottom) {
                drawCanvasText(
                    textMeasurer = textMeasurer,
                    text = formatWindSpeed(barb.speedKmh, displayUnits, withUnit = false),
                    x = plotRight + rightAltitudeWidth + rightWindWidth / 2f,
                    baselineY = y + with(density) { 22.dp.toPx() },
                    style = windLabelStyle,
                    anchor = CanvasTextAnchor.CENTER,
                )
            }
        }

        pressureLabels.forEach { pressure ->
            val y = pressureToY(pressure)
            if (y in plotTop..plotBottom) {
                val heightMeters = interpolateProfileHeightMeters(chart.temperatureProfile, pressure)
                    ?: pressureToApproxHeightMeters(pressure).toFloat()
                drawCanvasText(
                    textMeasurer = textMeasurer,
                    text = formatAxisHeight(heightMeters, displayUnits),
                    x = plotRight + 4.dp.toPx(),
                    baselineY = y + with(density) { 9.sp.toPx() } * 0.35f,
                    style = altitudeLabelStyle,
                )
            }
        }

        cursorState?.let { activeCursor ->
            val activeParcelPath = when {
                drawAnchorTemperatureC != null -> {
                    val anchorPressure = yToPressure(activeCursor.y)
                        .coerceIn(topPressure, chartBottomPressure)
                    buildInteractiveParcelFromPoint(
                        anchorTemperatureC = drawAnchorTemperatureC,
                        anchorPressureHpa = anchorPressure,
                        chart = chart,
                        parcelPressures = parcelPressures,
                    )
                }
                heatingDeltaC != 0f ->
                    buildInteractiveParcelFromSurface(
                        parcelStartTempC = defaultParcelStartTempC + heatingDeltaC,
                        chart = chart,
                        profileLevels = profileLevels,
                        parcelPressures = parcelPressures,
                    )
                else -> chart.parcelAscentPath
            }
            val readout = buildCursorReadout(
                chart = chart,
                pressureHpa = yToPressure(activeCursor.y),
                anchorTemperatureC = drawAnchorTemperatureC,
                parcelPath = activeParcelPath,
            )
            val cursorY = pressureToY(readout.pressureHpa)
            if (cursorY in plotTop..plotBottom) {
                drawCursorInlineLabels(
                    textMeasurer = textMeasurer,
                    readout = readout,
                    cursorY = cursorY,
                    plotLeft = plotLeft,
                    plotRight = plotRight,
                    plotTop = plotTop,
                    plotBottom = plotBottom,
                    bottomPressure = chartBottomPressure,
                    rightWindCenterX = plotRight + rightAltitudeWidth + rightWindWidth / 2f,
                    labelMinX = 0f,
                    labelMaxX = size.width,
                    axisLabelStyle = axisLabelStyle,
                    altitudeLabelStyle = altitudeLabelStyle,
                    temperatureReadoutBaseline = temperatureReadoutBaseline,
                    temperatureReadoutLabelLeft = plotLeft,
                    temperatureReadoutLabelRight = temperatureReadoutLabelRight,
                    displayUnits = displayUnits,
                    density = density,
                    temperatureToX = ::temperatureToX,
                )
            }
        }
    }
}


@Preview(name = "Stuve Diagram Canvas", showBackground = true, widthDp = 420, heightDp = 640)
@Composable
private fun StuveDiagramCanvasPreview() {
    val uiState = ForecastPreviewData.stateForMode(ForecastMode.STUVE)
    ForecastPreviewTheme {
        StuveDiagramCanvas(
            chart = uiState.stuveChart,
            visibleTopAltitudeKm = uiState.chartViewport.visibleTopAltitudeKm,
            displayUnits = uiState.displayUnits,
            onVisibleTopAltitudeChange = {},
        )
    }
}
