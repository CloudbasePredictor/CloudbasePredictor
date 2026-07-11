@file:Suppress(
    "CyclomaticComplexMethod",
    "FunctionNaming",
    "LongMethod",
    "MagicNumber",
    "MaxLineLength",
    "TooManyFunctions",
)

package com.cloudbasepredictor.ui.screens.forecast.views

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cloudbasepredictor.model.ForecastMode
import com.cloudbasepredictor.ui.preview.ForecastPreviewData
import com.cloudbasepredictor.ui.preview.ForecastPreviewTheme
import com.cloudbasepredictor.ui.screens.forecast.ForecastReadyUiState
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.sin
import androidx.compose.ui.tooling.preview.Preview

@Composable
fun ForecastGridCard(
    uiState: ForecastReadyUiState,
    mode: ForecastMode,
    minAltitudeKm: Float,
    onVisibleTopAltitudeChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    ForecastChartCard(
        modifier = modifier,
    ) { chartModifier ->
        ForecastRiskGrid(
            mode = mode,
            minAltitudeKm = minAltitudeKm,
            visibleTopAltitudeKm = uiState.chartViewport.visibleTopAltitudeKm,
            onVisibleTopAltitudeChange = onVisibleTopAltitudeChange,
            modifier = chartModifier,
        )
    }
}

@Composable
internal fun ForecastChartCard(
    modifier: Modifier = Modifier,
    chartContent: @Composable (Modifier) -> Unit,
) {
    Box(
        modifier = modifier.fillMaxSize(),
    ) {
        chartContent(Modifier.fillMaxSize())
    }
}

@Composable
private fun ForecastRiskGrid(
    mode: ForecastMode,
    minAltitudeKm: Float,
    visibleTopAltitudeKm: Float,
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
    val axisBackgroundColor = gridBackgroundColor
    val plotBackgroundColor = gridBackgroundColor
    val outlineColor = MaterialTheme.colorScheme.outlineVariant

    val textMeasurer = rememberTextMeasurer()
    val axisLabelStyle = remember(axisLabelColor) {
        TextStyle(
            color = axisLabelColor,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
        )
    }
    val hourLabelStyle = remember(axisLabelColor) {
        TextStyle(
            color = axisLabelColor,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
        )
    }
    val unitLabelStyle = remember(axisLabelColor) {
        TextStyle(
            color = axisLabelColor,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
        )
    }
    val latestVisibleTopAltitudeKm = rememberUpdatedState(visibleTopAltitudeKm)

    Canvas(
        modifier = modifier.pointerInput(Unit) {
            detectForecastZoomGestures(
                currentTopAltitudeKm = { latestVisibleTopAltitudeKm.value },
                onVisibleTopAltitudeChange = onVisibleTopAltitudeChange,
            )
        },
    ) {
        val axisWidth = with(density) { 60.dp.toPx() }
        val outerHorizontalPadding = 0f
        val axisToPlotSpacing = 0f
        val bottomAxisHeight = with(density) { 38.dp.toPx() }
        val plotCornerRadius = 0f

        val plotLeft = outerHorizontalPadding + axisWidth + axisToPlotSpacing
        val plotTop = outerHorizontalPadding
        val plotRight = size.width - outerHorizontalPadding
        val plotBottom = size.height - bottomAxisHeight
        val plotWidth = plotRight - plotLeft
        val plotHeight = plotBottom - plotTop

        val effectiveTopAltitudeKm = max(
            visibleTopAltitudeKm,
            minAltitudeKm + MIN_VISIBLE_ALTITUDE_RANGE_KM,
        )
        val altitudeBands = buildAltitudeBands(
            minAltitudeKm = minAltitudeKm,
            maxAltitudeKm = effectiveTopAltitudeKm,
            stepKm = altitudeBandStepKm(effectiveTopAltitudeKm),
        )
        val altitudeTicks = buildAltitudeTicks(
            minAltitudeKm = minAltitudeKm,
            maxAltitudeKm = effectiveTopAltitudeKm,
            stepKm = altitudeTickStepKm(effectiveTopAltitudeKm),
        )

        if (plotWidth <= 0f || plotHeight <= 0f || altitudeBands.isEmpty()) {
            return@Canvas
        }

        drawRoundRect(
            color = axisBackgroundColor,
            topLeft = Offset(outerHorizontalPadding, plotTop),
            size = Size(axisWidth, plotHeight),
            cornerRadius = CornerRadius(plotCornerRadius, plotCornerRadius),
        )
        drawRoundRect(
            color = plotBackgroundColor,
            topLeft = Offset(plotLeft, plotTop),
            size = Size(plotWidth, plotHeight),
            cornerRadius = CornerRadius(plotCornerRadius, plotCornerRadius),
        )

        val columnWidth = plotWidth / LOCAL_FORECAST_HOURS.size

        altitudeBands.forEach { band ->
            val topY = altitudeToY(
                altitudeKm = band.endKm,
                minAltitudeKm = minAltitudeKm,
                maxAltitudeKm = effectiveTopAltitudeKm,
                plotTop = plotTop,
                plotBottom = plotBottom,
            )
            val bottomY = altitudeToY(
                altitudeKm = band.startKm,
                minAltitudeKm = minAltitudeKm,
                maxAltitudeKm = effectiveTopAltitudeKm,
                plotTop = plotTop,
                plotBottom = plotBottom,
            )
            val bandHeight = bottomY - topY
            val bandCenterKm = (band.startKm + band.endKm) / 2f

            LOCAL_FORECAST_HOURS.forEachIndexed { index, hour ->
                val risk = riskIntensity(
                    mode = mode,
                    hour = hour,
                    altitudeKm = bandCenterKm,
                    minAltitudeKm = minAltitudeKm,
                    maxAltitudeKm = effectiveTopAltitudeKm,
                )
                drawRect(
                    color = lerp(
                        start = gridBackgroundColor,
                        stop = riskColor(risk, mode = mode),
                        fraction = CELL_TINT_FRACTION,
                    ),
                    topLeft = Offset(plotLeft + (index * columnWidth), topY),
                    size = Size(columnWidth, bandHeight),
                )
            }
        }

        LOCAL_FORECAST_HOURS.forEachIndexed { index, hour ->
            val x = plotLeft + (index * columnWidth)
            val lineAlpha = if ((hour - LOCAL_FORECAST_HOURS.first()) % 3 == 0) 0.5f else 0.22f

            drawLine(
                color = outlineColor.copy(alpha = lineAlpha),
                start = Offset(x, plotTop),
                end = Offset(x, plotBottom),
                strokeWidth = 1.dp.toPx(),
            )

            drawLine(
                color = outlineColor.copy(alpha = 0.4f),
                start = Offset(x + (columnWidth / 2f), plotBottom + 4.dp.toPx()),
                end = Offset(x + (columnWidth / 2f), plotBottom + 10.dp.toPx()),
                strokeWidth = 1.dp.toPx(),
            )
        }

        drawLine(
            color = outlineColor.copy(alpha = 0.35f),
            start = Offset(plotRight, plotTop),
            end = Offset(plotRight, plotBottom),
            strokeWidth = 1.dp.toPx(),
        )

        altitudeTicks.forEach { altitudeKm ->
            val y = altitudeToY(
                altitudeKm = altitudeKm,
                minAltitudeKm = minAltitudeKm,
                maxAltitudeKm = effectiveTopAltitudeKm,
                plotTop = plotTop,
                plotBottom = plotBottom,
            )

            drawLine(
                color = outlineColor.copy(alpha = 0.45f),
                start = Offset(outerHorizontalPadding, y),
                end = Offset(plotRight, y),
                strokeWidth = 1.dp.toPx(),
            )
        }

        drawRoundRect(
            color = outlineColor.copy(alpha = 0.4f),
            topLeft = Offset(plotLeft, plotTop),
            size = Size(plotWidth, plotHeight),
            cornerRadius = CornerRadius(plotCornerRadius, plotCornerRadius),
            style = Stroke(width = 1.dp.toPx()),
        )

        altitudeTicks.forEach { altitudeKm ->
            val y = altitudeToY(
                altitudeKm = altitudeKm,
                minAltitudeKm = minAltitudeKm,
                maxAltitudeKm = effectiveTopAltitudeKm,
                plotTop = plotTop,
                plotBottom = plotBottom,
            )
            drawCanvasText(
                textMeasurer = textMeasurer,
                text = formatAltitudeLabel(altitudeKm),
                x = outerHorizontalPadding + 8.dp.toPx(),
                baselineY = y + with(density) { 12.sp.toPx() } * 0.35f,
                style = axisLabelStyle,
            )
        }

        drawCanvasText(
            textMeasurer = textMeasurer,
            text = "km",
            x = outerHorizontalPadding + 8.dp.toPx(),
            baselineY = plotBottom + with(density) { 11.sp.toPx() } + 12.dp.toPx(),
            style = unitLabelStyle,
        )

        LOCAL_FORECAST_HOURS.forEachIndexed { index, hour ->
            if ((hour - LOCAL_FORECAST_HOURS.first()) % 3 != 0) {
                return@forEachIndexed
            }

            val labelCenterX = plotLeft + (index * columnWidth) + (columnWidth / 2f)
            drawCanvasText(
                textMeasurer = textMeasurer,
                text = formatHourLabel(hour),
                x = labelCenterX,
                baselineY = plotBottom + with(density) { 12.sp.toPx() } + 14.dp.toPx(),
                style = hourLabelStyle,
                anchor = CanvasTextAnchor.CENTER,
            )
        }
    }
}

private fun altitudeBandStepKm(maxAltitudeKm: Float): Float {
    return if (maxAltitudeKm <= 3.5f) 0.25f else 0.5f
}

private fun altitudeTickStepKm(maxAltitudeKm: Float): Float {
    return if (maxAltitudeKm <= 3.5f) 0.5f else 1f
}

private fun buildAltitudeBands(
    minAltitudeKm: Float,
    maxAltitudeKm: Float,
    stepKm: Float,
): List<AltitudeBand> {
    val bands = mutableListOf<AltitudeBand>()
    var currentStart = minAltitudeKm

    while (currentStart < maxAltitudeKm) {
        val currentEnd = (currentStart + stepKm).coerceAtMost(maxAltitudeKm)
        bands += AltitudeBand(
            startKm = currentStart,
            endKm = currentEnd,
        )
        currentStart = currentEnd
    }

    return bands
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

private fun riskIntensity(
    mode: ForecastMode,
    hour: Int,
    altitudeKm: Float,
    minAltitudeKm: Float,
    maxAltitudeKm: Float,
): Float {
    val timeNormalized = (hour - LOCAL_FORECAST_HOURS.first()).toFloat() /
        (LOCAL_FORECAST_HOURS.last() - LOCAL_FORECAST_HOURS.first()).toFloat()
    val altitudeNormalized = ((altitudeKm - minAltitudeKm) / (maxAltitudeKm - minAltitudeKm))
        .coerceIn(0f, 1f)
    val solarPeak = (1f - (abs(hour - 14f) / 8f)).coerceIn(0f, 1f)
    val wave = ((sin((timeNormalized * PI.toFloat() * (1.4f + (mode.ordinal * 0.18f))) + (mode.ordinal * 0.7f)) + 1f) / 2f)

    val altitudeBias = when (mode) {
        ForecastMode.THERMIC -> 1f - (altitudeNormalized * 0.9f)
        ForecastMode.STUVE -> 0.85f - (abs(altitudeNormalized - 0.5f) * 0.8f)
        ForecastMode.WIND -> 0.45f + (altitudeNormalized * 0.7f)
        ForecastMode.CLOUD -> 0.55f + ((1f - abs(altitudeNormalized - 0.65f)) * 0.45f)
    }.coerceIn(0f, 1f)

    val modeLift = when (mode) {
        ForecastMode.THERMIC -> (solarPeak * 0.7f) + (wave * 0.2f)
        ForecastMode.STUVE -> (wave * 0.35f) + (solarPeak * 0.45f)
        ForecastMode.WIND -> (wave * 0.55f) + (altitudeNormalized * 0.25f)
        ForecastMode.CLOUD -> ((1f - solarPeak) * 0.35f) + (wave * 0.35f)
    }

    return (0.12f + (altitudeBias * 0.35f) + (modeLift * 0.35f) + (wave * 0.18f)).coerceIn(0f, 1f)
}

private fun riskColor(
    intensity: Float,
    mode: ForecastMode,
): Color {
    val lowRisk = when (mode) {
        ForecastMode.THERMIC -> Color(0xFF6BBC65)
        ForecastMode.STUVE -> Color(0xFF7CBF6E)
        ForecastMode.WIND -> Color(0xFF80B974)
        ForecastMode.CLOUD -> Color(0xFF7DBA83)
    }
    val mediumRisk = Color(0xFFF2C94C)
    val highRisk = when (mode) {
        ForecastMode.THERMIC -> Color(0xFFE67E22)
        ForecastMode.STUVE -> Color(0xFFDFAF1B)
        ForecastMode.WIND -> Color(0xFFF2994A)
        ForecastMode.CLOUD -> Color(0xFFE0B63A)
    }

    return if (intensity <= 0.5f) {
        lerp(lowRisk, mediumRisk, intensity / 0.5f)
    } else {
        lerp(mediumRisk, highRisk, (intensity - 0.5f) / 0.5f)
    }
}

private fun formatAltitudeLabel(altitudeKm: Float): String {
    return formatFixedDecimal(altitudeKm, fractionDigits = 1)
}

private fun formatHourLabel(hour: Int): String {
    return formatPaddedInt(hour, minimumDigits = 2)
}

private data class AltitudeBand(
    val startKm: Float,
    val endKm: Float,
)

private const val MIN_VISIBLE_ALTITUDE_RANGE_KM = 0.75f
private const val ALTITUDE_EPSILON = 0.001f
private const val CELL_TINT_FRACTION = 0.18f
private val LOCAL_FORECAST_HOURS = (6..22).toList()

@Preview(name = "Forecast Grid Card", showBackground = true, widthDp = 420, heightDp = 720)
@Composable
private fun ForecastGridCardPreview() {
    ForecastPreviewTheme {
        ForecastGridCard(
            uiState = ForecastPreviewData.stateForMode(ForecastMode.CLOUD),
            mode = ForecastMode.CLOUD,
            minAltitudeKm = 0.5f,
            onVisibleTopAltitudeChange = {},
        )
    }
}
