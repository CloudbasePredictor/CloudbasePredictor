@file:Suppress(
    "CyclomaticComplexMethod",
    "FunctionNaming",
    "LongMethod",
    "MagicNumber",
    "TooManyFunctions",
    "UnusedParameter",
)

package com.cloudbasepredictor.ui.screens.forecast.views

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cloudbasepredictor.model.ForecastMode
import com.cloudbasepredictor.ui.preview.ForecastPreviewData
import com.cloudbasepredictor.ui.preview.ForecastPreviewTheme
import com.cloudbasepredictor.ui.screens.forecast.CloudForecastChartUiModel
import com.cloudbasepredictor.ui.screens.forecast.ForecastTestTags.CLOUD_LAYERS_ROW
import com.cloudbasepredictor.ui.screens.forecast.ForecastTestTags.CLOUD_RENDERER_DESCRIPTION
import com.cloudbasepredictor.ui.screens.forecast.ForecastTestTags.CLOUD_RADIATION_ROW
import com.cloudbasepredictor.ui.screens.forecast.ForecastTestTags.CLOUD_RAIN_ROW
import com.cloudbasepredictor.ui.screens.forecast.ForecastTestTags.CLOUD_SCROLL
import com.cloudbasepredictor.ui.screens.forecast.ForecastTestTags.CLOUD_SUNSHINE_ROW
import com.cloudbasepredictor.ui.screens.forecast.ForecastTestTags.CLOUD_TIME_AXIS_ROW
import com.cloudbasepredictor.ui.screens.forecast.ForecastTestTags.CLOUD_VIEW
import com.cloudbasepredictor.ui.screens.forecast.ForecastReadyUiState
import kotlin.math.max
import androidx.compose.ui.tooling.preview.Preview

@Composable
fun CloudForecastView(
    uiState: ForecastReadyUiState,
    modifier: Modifier = Modifier,
    onVisibleTopAltitudeChange: (Float) -> Unit = {},
) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .testTag(CLOUD_VIEW)
            .semantics { contentDescription = CLOUD_RENDERER_DESCRIPTION },
    ) {
        val minimumRowsHeight = CLOUD_TOP_OVERLAY_CLEARANCE +
            SUNSHINE_ROW_HEIGHT +
            RADIATION_ROW_HEIGHT +
            CLOUD_LAYERS_HEIGHT +
            RAIN_ROW_HEIGHT
        val minimumChartHeight = minimumRowsHeight + TIME_AXIS_HEIGHT
        val viewportHeight = if (maxHeight == Dp.Infinity) minimumChartHeight else maxHeight
        val scrollViewportHeight = (viewportHeight - TIME_AXIS_HEIGHT).coerceAtLeast(0.dp)
        val rowSpacing = if (scrollViewportHeight > minimumRowsHeight) {
            (scrollViewportHeight - minimumRowsHeight) / CLOUD_ROW_GAP_COUNT.toFloat()
        } else {
            0.dp
        }
        val scrollContentHeight = maxOf(scrollViewportHeight, minimumRowsHeight + rowSpacing * CLOUD_ROW_GAP_COUNT)

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(scrollViewportHeight)
                .verticalScroll(rememberScrollState())
                .testTag(CLOUD_SCROLL),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(scrollContentHeight),
            ) {
                Spacer(modifier = Modifier.height(CLOUD_TOP_OVERLAY_CLEARANCE))

                // Sunshine duration row (short)
                CloudSunshineCanvas(
                    chart = uiState.cloudChart,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(SUNSHINE_ROW_HEIGHT)
                        .testTag(CLOUD_SUNSHINE_ROW),
                )

                Spacer(modifier = Modifier.height(rowSpacing))

                // Shortwave radiation row (bar chart like rain)
                CloudRadiationCanvas(
                    chart = uiState.cloudChart,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(RADIATION_ROW_HEIGHT)
                        .testTag(CLOUD_RADIATION_ROW),
                )

                Spacer(modifier = Modifier.height(rowSpacing))

                // Cloud layers (High / Mid / Low)
                CloudLayersCanvas(
                    chart = uiState.cloudChart,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(CLOUD_LAYERS_HEIGHT)
                        .testTag(CLOUD_LAYERS_ROW),
                )

                Spacer(modifier = Modifier.height(rowSpacing))

                // Rain row (bar chart)
                CloudRainCanvas(
                    chart = uiState.cloudChart,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(RAIN_ROW_HEIGHT)
                        .testTag(CLOUD_RAIN_ROW),
                )

                Spacer(modifier = Modifier.height(rowSpacing))
            }
        }

        CloudTimeAxisCanvas(
            chart = uiState.cloudChart,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(TIME_AXIS_HEIGHT)
                .testTag(CLOUD_TIME_AXIS_ROW),
        )
    }
}

// ── Sunshine row ──────────────────────────────────────────────────

@Composable
private fun CloudSunshineCanvas(
    chart: CloudForecastChartUiModel,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val axisLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val gridBackgroundColor = cloudGridBackground()
    val outlineColor = MaterialTheme.colorScheme.outlineVariant

    val textMeasurer = rememberTextMeasurer()
    val labelStyle = remember(axisLabelColor) {
        TextStyle(
            color = axisLabelColor,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
        )
    }
    val valueStyle = remember {
        TextStyle(
            color = Color(0xFFFF8F00),
            fontSize = 9.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
        )
    }

    Canvas(modifier = modifier) {
        val leftAxisWidth = with(density) { LEFT_AXIS_WIDTH.toPx() }
        val plotLeft = leftAxisWidth
        val plotWidth = size.width - plotLeft
        if (plotWidth <= 0f || chart.hours.isEmpty()) return@Canvas

        drawRect(color = gridBackgroundColor, topLeft = Offset.Zero, size = size)

        val columnWidth = plotWidth / chart.hours.size

        drawVerticalGrid(chart, plotLeft, columnWidth, outlineColor, 0f, size.height)

        // Sun circles sized by fraction
        val sunColor = Color(0xFFFFB300)
        chart.sunshine.forEach { sun ->
            val hourIndex = chart.hours.indexOf(sun.hour)
            if (hourIndex < 0) return@forEach
            val cx = plotLeft + hourIndex * columnWidth + columnWidth / 2f
            val fraction = (sun.durationS / 3600f).coerceIn(0f, 1f)
            if (fraction > 0.01f) {
                val maxRadius = (minOf(columnWidth, size.height) * 0.35f)
                val radius = maxRadius * (0.4f + 0.6f * fraction)
                drawCircle(
                    color = sunColor.copy(alpha = 0.3f + 0.5f * fraction),
                    radius = radius,
                    center = Offset(cx, size.height / 2f),
                )
            }
        }

        drawCanvasText(
            textMeasurer = textMeasurer,
            text = "☀ h",
            x = 8.dp.toPx(),
            baselineY = size.height / 2f + with(density) { 10.sp.toPx() } * 0.35f,
            style = labelStyle,
        )

        val labelWidth = textMeasurer.measureCanvasText("0.9", valueStyle).size.width
        val cluster = max(1, kotlin.math.ceil(labelWidth * 1.3f / columnWidth).toInt())
        chart.sunshine.forEachIndexed { idx, sun ->
            if (idx % cluster != cluster / 2) return@forEachIndexed
            val hourIndex = chart.hours.indexOf(sun.hour)
            if (hourIndex < 0) return@forEachIndexed
            val cx = plotLeft + hourIndex * columnWidth + columnWidth / 2f
            val hours = sun.durationS / 3600f
            if (hours > 0.05f) {
                drawCanvasText(
                    textMeasurer = textMeasurer,
                    text = formatFixedDecimal(hours, fractionDigits = 1),
                    x = cx,
                    baselineY = size.height / 2f + with(density) { 9.sp.toPx() } * 0.35f,
                    style = valueStyle,
                    anchor = CanvasTextAnchor.CENTER,
                )
            }
        }

        drawLine(
            color = outlineColor.copy(alpha = 0.4f),
            start = Offset(0f, size.height),
            end = Offset(size.width, size.height),
            strokeWidth = 1.dp.toPx(),
        )
    }
}

// ── Shortwave radiation row ───────────────────────────────────────

@Composable
private fun CloudRadiationCanvas(
    chart: CloudForecastChartUiModel,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val axisLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val gridBackgroundColor = cloudGridBackground()
    val outlineColor = MaterialTheme.colorScheme.outlineVariant

    val textMeasurer = rememberTextMeasurer()
    val labelStyle = remember(axisLabelColor) {
        TextStyle(
            color = axisLabelColor,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
        )
    }
    val valueStyle = remember {
        TextStyle(
            color = Color(0xFFFF8F00),
            fontSize = 9.sp,
            fontFamily = FontFamily.Monospace,
        )
    }

    Canvas(modifier = modifier) {
        val leftAxisWidth = with(density) { LEFT_AXIS_WIDTH.toPx() }
        val plotLeft = leftAxisWidth
        val plotWidth = size.width - plotLeft
        if (plotWidth <= 0f || chart.hours.isEmpty()) return@Canvas

        drawRect(color = gridBackgroundColor, topLeft = Offset.Zero, size = size)

        val columnWidth = plotWidth / chart.hours.size
        val maxRadiation = 800f

        drawVerticalGrid(chart, plotLeft, columnWidth, outlineColor, 0f, size.height)

        chart.radiation.forEach { rad ->
            val hourIndex = chart.hours.indexOf(rad.hour)
            if (hourIndex < 0) return@forEach
            val x = plotLeft + hourIndex * columnWidth

            if (rad.radiationWm2 > 0f) {
                val barHeight = (rad.radiationWm2 / maxRadiation).coerceIn(0f, 1f) * (size.height * 0.7f)
                val barTop = size.height - barHeight - 2.dp.toPx()
                drawRoundRect(
                    color = radiationColor(rad.radiationWm2),
                    topLeft = Offset(x + columnWidth * 0.15f, barTop),
                    size = Size(columnWidth * 0.7f, barHeight),
                    cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx()),
                )
            }
        }

        drawCanvasText(
            textMeasurer = textMeasurer,
            text = "W/m²",
            x = 8.dp.toPx(),
            baselineY = size.height / 2f + with(density) { 10.sp.toPx() } * 0.35f,
            style = labelStyle,
        )

        val labelWidth = textMeasurer.measureCanvasText("999", valueStyle).size.width
        val cluster = max(1, kotlin.math.ceil(labelWidth * 1.3f / columnWidth).toInt())
        chart.radiation.forEachIndexed { idx, rad ->
            if (idx % cluster != cluster / 2) return@forEachIndexed
            val hourIndex = chart.hours.indexOf(rad.hour)
            if (hourIndex < 0) return@forEachIndexed
            val cx = plotLeft + hourIndex * columnWidth + columnWidth / 2f
            if (rad.radiationWm2 > 5f) {
                drawCanvasText(
                    textMeasurer = textMeasurer,
                    text = "${rad.radiationWm2.toInt()}",
                    x = cx,
                    baselineY = with(density) { 9.sp.toPx() } + 2.dp.toPx(),
                    style = valueStyle,
                    anchor = CanvasTextAnchor.CENTER,
                )
            }
        }

        drawLine(
            color = outlineColor.copy(alpha = 0.4f),
            start = Offset(0f, size.height),
            end = Offset(size.width, size.height),
            strokeWidth = 1.dp.toPx(),
        )
    }
}

// ── Cloud layers (High / Mid / Low) ───────────────────────────────

@Composable
private fun CloudLayersCanvas(
    chart: CloudForecastChartUiModel,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val axisLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val gridBackgroundColor = cloudGridBackground()
    val outlineColor = MaterialTheme.colorScheme.outlineVariant

    val textMeasurer = rememberTextMeasurer()
    val axisLabelStyle = remember(axisLabelColor) {
        TextStyle(
            color = axisLabelColor,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
        )
    }
    val percentStyle = remember(axisLabelColor) {
        TextStyle(
            color = axisLabelColor,
            fontSize = 9.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
        )
    }

    Canvas(modifier = modifier) {
        val leftAxisWidth = with(density) { LEFT_AXIS_WIDTH.toPx() }
        val plotLeft = leftAxisWidth
        val plotWidth = size.width - plotLeft
        if (plotWidth <= 0f || chart.hours.isEmpty()) return@Canvas

        drawRect(color = gridBackgroundColor, topLeft = Offset.Zero, size = size)

        val layerSpacing = with(density) { 4.dp.toPx() }
        val layerHeight = (size.height - layerSpacing * 2f) / 3f
        val columnWidth = plotWidth / chart.hours.size

        fun layerTopY(index: Int): Float = index * (layerHeight + layerSpacing)

        drawVerticalGrid(chart, plotLeft, columnWidth, outlineColor, 0f, size.height)

        for (i in 1..2) {
            val dividerY = layerTopY(i) - layerSpacing / 2f
            drawLine(
                color = outlineColor.copy(alpha = 0.3f),
                start = Offset(0f, dividerY),
                end = Offset(size.width, dividerY),
                strokeWidth = 1.dp.toPx(),
            )
        }

        chart.layers.forEach { layer ->
            val hourIndex = chart.hours.indexOf(layer.hour)
            if (hourIndex < 0) return@forEach
            val x = plotLeft + hourIndex * columnWidth

            drawCloudCell(x, layerTopY(0), columnWidth, layerHeight, layer.highCloudPercent, CLOUD_COLOR)
            drawCloudCell(x, layerTopY(1), columnWidth, layerHeight, layer.midCloudPercent, CLOUD_COLOR)
            drawCloudCell(x, layerTopY(2), columnWidth, layerHeight, layer.lowCloudPercent, CLOUD_COLOR)
        }

        for (i in 0..2) {
            drawRect(
                color = outlineColor.copy(alpha = 0.4f),
                topLeft = Offset(plotLeft, layerTopY(i)),
                size = Size(plotWidth, layerHeight),
                style = Stroke(width = 1.dp.toPx()),
            )
        }

        val layerNames = listOf("High", "Mid", "Low")
        layerNames.forEachIndexed { index, name ->
            val centerY = layerTopY(index) + layerHeight / 2f
            drawCanvasText(
                textMeasurer = textMeasurer,
                text = name,
                x = 8.dp.toPx(),
                baselineY = centerY + with(density) { 11.sp.toPx() } * 0.35f,
                style = axisLabelStyle,
            )
        }

        val labelWidth = textMeasurer.measureCanvasText("99%", percentStyle).size.width
        val cluster = max(1, kotlin.math.ceil(labelWidth * 1.3f / columnWidth).toInt())

        chart.layers.forEachIndexed { idx, layer ->
            if (idx % cluster != cluster / 2) return@forEachIndexed
            val hourIndex = chart.hours.indexOf(layer.hour)
            if (hourIndex < 0) return@forEachIndexed
            val cx = plotLeft + hourIndex * columnWidth + columnWidth / 2f

            if (layer.highCloudPercent > 5f) {
                drawCanvasText(
                    textMeasurer = textMeasurer,
                    text = "${layer.highCloudPercent.toInt()}%",
                    x = cx,
                    baselineY = layerTopY(0) + layerHeight / 2f + with(density) { 9.sp.toPx() } * 0.35f,
                    style = percentStyle,
                    anchor = CanvasTextAnchor.CENTER,
                )
            }
            if (layer.midCloudPercent > 5f) {
                drawCanvasText(
                    textMeasurer = textMeasurer,
                    text = "${layer.midCloudPercent.toInt()}%",
                    x = cx,
                    baselineY = layerTopY(1) + layerHeight / 2f + with(density) { 9.sp.toPx() } * 0.35f,
                    style = percentStyle,
                    anchor = CanvasTextAnchor.CENTER,
                )
            }
            if (layer.lowCloudPercent > 5f) {
                drawCanvasText(
                    textMeasurer = textMeasurer,
                    text = "${layer.lowCloudPercent.toInt()}%",
                    x = cx,
                    baselineY = layerTopY(2) + layerHeight / 2f + with(density) { 9.sp.toPx() } * 0.35f,
                    style = percentStyle,
                    anchor = CanvasTextAnchor.CENTER,
                )
            }
        }

        drawLine(
            color = outlineColor.copy(alpha = 0.4f),
            start = Offset(0f, size.height),
            end = Offset(size.width, size.height),
            strokeWidth = 1.dp.toPx(),
        )
    }
}

// ── Rain row ──────────────────────────────────────────────────────

@Composable
private fun CloudRainCanvas(
    chart: CloudForecastChartUiModel,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val axisLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val gridBackgroundColor = cloudGridBackground()
    val outlineColor = MaterialTheme.colorScheme.outlineVariant

    val textMeasurer = rememberTextMeasurer()
    val labelStyle = remember(axisLabelColor) {
        TextStyle(
            color = axisLabelColor,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
        )
    }
    val precipStyle = remember {
        TextStyle(
            color = Color(0xFF1565C0),
            fontSize = 9.sp,
            fontFamily = FontFamily.Monospace,
        )
    }

    Canvas(modifier = modifier) {
        val leftAxisWidth = with(density) { LEFT_AXIS_WIDTH.toPx() }
        val plotLeft = leftAxisWidth
        val plotWidth = size.width - plotLeft
        if (plotWidth <= 0f || chart.hours.isEmpty()) return@Canvas

        drawRect(color = gridBackgroundColor, topLeft = Offset.Zero, size = size)

        val columnWidth = plotWidth / chart.hours.size

        drawVerticalGrid(chart, plotLeft, columnWidth, outlineColor, 0f, size.height)

        chart.precipitation.forEach { precip ->
            val hourIndex = chart.hours.indexOf(precip.hour)
            if (hourIndex < 0) return@forEach
            val x = plotLeft + hourIndex * columnWidth

            if (precip.amountMm > 0f) {
                val barHeight = (precip.amountMm / 8f).coerceIn(0f, 1f) * (size.height * 0.6f)
                val barTop = size.height - barHeight - 2.dp.toPx()
                drawRoundRect(
                    color = precipColor(precip.amountMm),
                    topLeft = Offset(x + columnWidth * 0.15f, barTop),
                    size = Size(columnWidth * 0.7f, barHeight),
                    cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx()),
                )
            }
        }

        drawCanvasText(
            textMeasurer = textMeasurer,
            text = "Rain",
            x = 8.dp.toPx(),
            baselineY = size.height / 2f + with(density) { 10.sp.toPx() } * 0.35f,
            style = labelStyle,
        )

        val labelWidth = textMeasurer.measureCanvasText("99%", precipStyle).size.width
        val cluster = max(1, kotlin.math.ceil(labelWidth * 1.3f / columnWidth).toInt())

        chart.precipitation.forEachIndexed { idx, precip ->
            if (idx % cluster != cluster / 2) return@forEachIndexed
            val hourIndex = chart.hours.indexOf(precip.hour)
            if (hourIndex < 0) return@forEachIndexed
            val cx = plotLeft + hourIndex * columnWidth + columnWidth / 2f

            if (precip.amountMm > 0f) {
                drawCanvasText(
                    textMeasurer = textMeasurer,
                    text = "${precip.probabilityPercent.toInt()}%",
                    x = cx,
                    baselineY = with(density) { 9.sp.toPx() } + 2.dp.toPx(),
                    style = precipStyle,
                    anchor = CanvasTextAnchor.CENTER,
                )
                drawCanvasText(
                    textMeasurer = textMeasurer,
                    text = formatFixedDecimal(precip.amountMm, fractionDigits = 1),
                    x = cx,
                    baselineY = with(density) { 9.sp.toPx() } * 2f + 4.dp.toPx(),
                    style = precipStyle,
                    anchor = CanvasTextAnchor.CENTER,
                )
            } else if (precip.probabilityPercent > 10f) {
                drawCanvasText(
                    textMeasurer = textMeasurer,
                    text = "${precip.probabilityPercent.toInt()}%",
                    x = cx,
                    baselineY = with(density) { 9.sp.toPx() } + 2.dp.toPx(),
                    style = precipStyle,
                    anchor = CanvasTextAnchor.CENTER,
                )
            }
        }

        drawLine(
            color = outlineColor.copy(alpha = 0.4f),
            start = Offset(0f, size.height),
            end = Offset(size.width, size.height),
            strokeWidth = 1.dp.toPx(),
        )
    }
}

// ── Time axis ─────────────────────────────────────────────────────

@Composable
private fun CloudTimeAxisCanvas(
    chart: CloudForecastChartUiModel,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val axisLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val gridBackgroundColor = cloudGridBackground()

    val textMeasurer = rememberTextMeasurer()
    val hourLabelStyle = remember(axisLabelColor) {
        TextStyle(
            color = axisLabelColor,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
        )
    }

    Canvas(modifier = modifier) {
        val leftAxisWidth = with(density) { LEFT_AXIS_WIDTH.toPx() }
        val plotLeft = leftAxisWidth
        val plotWidth = size.width - plotLeft
        if (plotWidth <= 0f || chart.hours.isEmpty()) return@Canvas

        drawRect(color = gridBackgroundColor, topLeft = Offset.Zero, size = size)

        val columnWidth = plotWidth / chart.hours.size

        chart.hours.forEachIndexed { index, hour ->
            if ((hour - chart.hours.first()) % 3 != 0) return@forEachIndexed
            val labelCenterX = plotLeft + index * columnWidth + columnWidth / 2f
            drawCanvasText(
                textMeasurer = textMeasurer,
                text = formatPaddedInt(hour, minimumDigits = 2),
                x = labelCenterX,
                baselineY = with(density) { 12.sp.toPx() } + 4.dp.toPx(),
                style = hourLabelStyle,
                anchor = CanvasTextAnchor.CENTER,
            )
        }
    }
}

// ── Shared helpers ────────────────────────────────────────────────

@Composable
private fun cloudGridBackground(): Color = lerp(
    start = MaterialTheme.colorScheme.surface,
    stop = MaterialTheme.colorScheme.onSurface,
    fraction = 0.035f,
)

private fun DrawScope.drawVerticalGrid(
    chart: CloudForecastChartUiModel,
    plotLeft: Float,
    columnWidth: Float,
    outlineColor: Color,
    top: Float,
    bottom: Float,
) {
    chart.hours.forEachIndexed { index, hour ->
        val x = plotLeft + index * columnWidth
        val alpha = if ((hour - chart.hours.first()) % 3 == 0) 0.4f else 0.18f
        drawLine(
            color = outlineColor.copy(alpha = alpha),
            start = Offset(x, top),
            end = Offset(x, bottom),
            strokeWidth = 1.dp.toPx(),
        )
    }
}

private fun DrawScope.drawCloudCell(
    x: Float,
    y: Float,
    width: Float,
    height: Float,
    percent: Float,
    color: Color,
) {
    val alpha = (percent / 100f).coerceIn(0f, 1f) * 0.7f
    if (alpha > 0.02f) {
        drawRect(
            color = color.copy(alpha = alpha),
            topLeft = Offset(x, y),
            size = Size(width, height),
        )
    }
}

private fun precipColor(amountMm: Float): Color {
    val normalized = (amountMm / 8f).coerceIn(0f, 1f)
    val light = Color(0xFF90CAF9)
    val medium = Color(0xFF42A5F5)
    val heavy = Color(0xFF1565C0)
    return if (normalized <= 0.5f) {
        lerp(light, medium, normalized / 0.5f)
    } else {
        lerp(medium, heavy, (normalized - 0.5f) / 0.5f)
    }
}

private fun radiationColor(wm2: Float): Color {
    val normalized = (wm2 / 800f).coerceIn(0f, 1f)
    val low = Color(0xFFFFF9C4) // pale yellow
    val mid = Color(0xFFFFB300) // amber
    val high = Color(0xFFFF6F00) // deep orange
    return if (normalized <= 0.5f) {
        lerp(low, mid, normalized / 0.5f)
    } else {
        lerp(mid, high, (normalized - 0.5f) / 0.5f)
    }
}

private val CLOUD_COLOR = Color(0xFF78909C)

private val LEFT_AXIS_WIDTH = 60.dp
private val CLOUD_TOP_OVERLAY_CLEARANCE = 64.dp
private const val CLOUD_ROW_GAP_COUNT = 4
private val SUNSHINE_ROW_HEIGHT = 32.dp
private val RADIATION_ROW_HEIGHT = 48.dp
private val CLOUD_LAYERS_HEIGHT = 144.dp
private val RAIN_ROW_HEIGHT = 48.dp
private val TIME_AXIS_HEIGHT = 28.dp

// ── Previews ──────────────────────────────────────────────────────

@Preview(name = "Cloud Default", showBackground = true, widthDp = 420, heightDp = 720)
@Composable
private fun CloudForecastViewPreview() {
    ForecastPreviewTheme {
        CloudForecastView(
            uiState = ForecastPreviewData.stateForMode(ForecastMode.CLOUD),
        )
    }
}

@Preview(
    name = "Cloud Dark",
    showBackground = true,
    widthDp = 420,
    heightDp = 720,
)
@Composable
private fun CloudForecastViewDarkPreview() {
    ForecastPreviewTheme(darkTheme = true) {
        CloudForecastView(
            uiState = ForecastPreviewData.stateForMode(ForecastMode.CLOUD),
        )
    }
}
