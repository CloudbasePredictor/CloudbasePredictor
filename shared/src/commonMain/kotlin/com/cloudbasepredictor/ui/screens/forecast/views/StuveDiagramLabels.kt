@file:Suppress(
    "CyclomaticComplexMethod",
    "LongMethod",
    "MagicNumber",
    "MaxLineLength",
    "ReturnCount",
)

package com.cloudbasepredictor.ui.screens.forecast.views

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cloudbasepredictor.data.units.DisplayUnits
import com.cloudbasepredictor.data.units.formatWindSpeed
import com.cloudbasepredictor.domain.forecast.dryAdiabatTempC
import kotlin.math.roundToInt

internal fun DrawScope.drawCursorInlineLabels(
    textMeasurer: TextMeasurer,
    readout: CursorReadout,
    cursorY: Float,
    plotLeft: Float,
    plotRight: Float,
    plotTop: Float,
    plotBottom: Float,
    bottomPressure: Float,
    rightWindCenterX: Float,
    labelMinX: Float,
    labelMaxX: Float,
    axisLabelStyle: TextStyle,
    altitudeLabelStyle: TextStyle,
    temperatureReadoutBaseline: Float,
    temperatureReadoutLabelLeft: Float,
    temperatureReadoutLabelRight: Float,
    displayUnits: DisplayUnits,
    density: androidx.compose.ui.unit.Density,
    temperatureToX: (Float, Float) -> Float,
) {
    drawBadgeLabel(
        textMeasurer = textMeasurer,
        lines = listOf(
            formatReadoutHeight(readout.altitudeMeters, displayUnits),
            "${readout.pressureHpa.roundToInt()} hPa",
        ),
        centerX = plotLeft - with(density) { 10.dp.toPx() },
        centerY = cursorY,
        density = density,
        textStyle = axisLabelStyle.copy(
            color = Color(0xFF2B2B2B),
            fontWeight = FontWeight.Bold,
        ),
        backgroundColor = Color(0xFFF3F3F3),
        minWidth = with(density) { 46.dp.toPx() },
        minX = labelMinX,
        maxX = labelMaxX,
    )

    drawBadgeLabel(
        textMeasurer = textMeasurer,
        lines = listOf(formatAxisHeight(readout.altitudeMeters.toFloat(), displayUnits)),
        centerX = plotRight + with(density) { 18.dp.toPx() },
        centerY = cursorY,
        density = density,
        textStyle = altitudeLabelStyle.copy(
            color = Color(0xFF2B2B2B),
            fontWeight = FontWeight.Bold,
        ),
        backgroundColor = Color(0xFFF3F3F3).copy(alpha = 0.92f),
        minWidth = with(density) { 34.dp.toPx() },
        minX = labelMinX,
        maxX = labelMaxX,
    )

    val pointLabelStyle = fun(color: Color): TextStyle {
        return TextStyle(
            color = color,
            fontSize = 9.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
        )
    }

    readout.temperatureC?.let { temperature ->
        val pointX = temperatureToX(temperature, readout.pressureHpa)
        drawPointValue(
            textMeasurer = textMeasurer,
            text = "T ${formatFixedDecimal(temperature, fractionDigits = 1)}°",
            x = pointX + with(density) { 6.dp.toPx() },
            y = cursorY - with(density) { 6.dp.toPx() },
            style = pointLabelStyle(Color(0xFFD83A3A)),
            maxX = plotRight - with(density) { 4.dp.toPx() },
            minX = plotLeft + with(density) { 4.dp.toPx() },
        )
    }

    readout.dewpointC?.let { dewpoint ->
        val pointX = temperatureToX(dewpoint, readout.pressureHpa)
        drawPointValue(
            textMeasurer = textMeasurer,
            text = "Td ${formatFixedDecimal(dewpoint, fractionDigits = 1)}°",
            x = pointX - with(density) { 6.dp.toPx() },
            y = cursorY + with(density) { 16.dp.toPx() },
            style = pointLabelStyle(Color(0xFF2E6FB5)),
            anchor = CanvasTextAnchor.END,
            maxX = plotRight - with(density) { 4.dp.toPx() },
            minX = plotLeft + with(density) { 4.dp.toPx() },
        )
    }

    readout.parcelTemperatureC?.let { parcelTemperature ->
        val pointX = temperatureToX(parcelTemperature, readout.pressureHpa)
        drawPointValue(
            textMeasurer = textMeasurer,
            text = "Parcel ${formatFixedDecimal(parcelTemperature, fractionDigits = 1)}°",
            x = pointX + with(density) { 6.dp.toPx() },
            y = cursorY + with(density) { 28.dp.toPx() },
            style = pointLabelStyle(Color(0xFF59A36A)),
            maxX = plotRight - with(density) { 4.dp.toPx() },
            minX = plotLeft + with(density) { 4.dp.toPx() },
        )
    }

    val readoutBottomLabels = buildList {
        readout.temperatureC?.let { temperature ->
            add(
                BottomAxisLabel(
                    text = "T ${formatFixedDecimal(temperature, fractionDigits = 0)}°",
                    preferredX = temperatureToX(temperature, bottomPressure),
                    style = pointLabelStyle(Color(0xFFD83A3A)),
                ),
            )
        }

        readout.dewpointC?.let { dewpoint ->
            add(
                BottomAxisLabel(
                    text = "Td ${formatFixedDecimal(dewpoint, fractionDigits = 0)}°",
                    preferredX = temperatureToX(dewpoint, bottomPressure),
                    style = pointLabelStyle(Color(0xFF2E6FB5)),
                ),
            )
        }

        readout.parcelSurfaceTemperatureC?.let { parcelSurfaceTemperature ->
            val bottomTemp = readout.guideDryThetaK?.let { dryAdiabatTempC(it, bottomPressure) } ?: return@let
            add(
                BottomAxisLabel(
                    text = "Parcel ${formatFixedDecimal(parcelSurfaceTemperature, fractionDigits = 0)}°",
                    preferredX = temperatureToX(bottomTemp, bottomPressure),
                    style = pointLabelStyle(Color(0xFF59A36A)),
                ),
            )
        }

    }

    drawBottomAxisLabels(
        textMeasurer = textMeasurer,
        labels = readoutBottomLabels,
        y = temperatureReadoutBaseline,
        plotLeft = temperatureReadoutLabelLeft,
        plotRight = temperatureReadoutLabelRight,
        minimumGapPx = with(density) { 14.dp.toPx() },
    )

    if (readout.windSpeedKmh != null && readout.windDirectionDeg != null) {
        val windBadgeOffset = with(density) { 28.dp.toPx() }
        val windBadgeTopLimit = plotTop + with(density) { 10.dp.toPx() }
        val windBadgeBottomLimit = plotBottom - with(density) { 10.dp.toPx() }
        val preferredWindBadgeY = cursorY - windBadgeOffset
        val windBadgeY = if (preferredWindBadgeY >= windBadgeTopLimit) {
            preferredWindBadgeY
        } else {
            (cursorY + windBadgeOffset).coerceIn(windBadgeTopLimit, windBadgeBottomLimit)
        }
        drawBadgeLabel(
            textMeasurer = textMeasurer,
            lines = listOf(
                "${formatWindSpeed(readout.windSpeedKmh, displayUnits)} " +
                    "${formatFixedDecimal(readout.windDirectionDeg, fractionDigits = 0, minimumIntegerDigits = 3)}°",
            ),
            centerX = rightWindCenterX,
            centerY = windBadgeY,
            density = density,
            textStyle = pointLabelStyle(Color(0xFF2B2B2B)),
            backgroundColor = Color(0xFFF3F3F3),
            minWidth = with(density) { 74.dp.toPx() },
            minX = labelMinX,
            maxX = labelMaxX,
        )
    }
}

private fun DrawScope.drawBadgeLabel(
    textMeasurer: TextMeasurer,
    lines: List<String>,
    centerX: Float,
    centerY: Float,
    density: androidx.compose.ui.unit.Density,
    textStyle: TextStyle,
    backgroundColor: Color,
    minWidth: Float = 0f,
    minX: Float = Float.NEGATIVE_INFINITY,
    maxX: Float = Float.POSITIVE_INFINITY,
) {
    if (lines.isEmpty()) return

    val paddingHorizontal = with(density) { 6.dp.toPx() }
    val paddingVertical = with(density) { 4.dp.toPx() }
    val lineSpacing = with(density) { 2.dp.toPx() }
    val textLayouts = lines.map { textMeasurer.measureCanvasText(it, textStyle) }
    val maxTextWidth = textLayouts.maxOf { it.size.width.toFloat() }
    val boxWidth = maxOf(minWidth, maxTextWidth + paddingHorizontal * 2f)
    val boxHeight = textLayouts.sumOf { it.size.height }.toFloat() +
        lineSpacing * (lines.size - 1) + paddingVertical * 2f
    val resolvedCenterX = if (minX.isFinite() && maxX.isFinite() && maxX > minX + boxWidth) {
        centerX.coerceIn(minX + boxWidth / 2f, maxX - boxWidth / 2f)
    } else {
        centerX
    }
    val topLeft = Offset(
        x = resolvedCenterX - boxWidth / 2f,
        y = centerY - boxHeight / 2f,
    )
    drawRoundRect(
        color = backgroundColor,
        topLeft = topLeft,
        size = Size(boxWidth, boxHeight),
        cornerRadius = CornerRadius(with(density) { 4.dp.toPx() }),
    )

    var textTop = topLeft.y + paddingVertical
    textLayouts.forEach { layout ->
        drawText(
            textLayoutResult = layout,
            topLeft = Offset(
                x = resolvedCenterX - layout.size.width / 2f,
                y = textTop,
            ),
        )
        textTop += layout.size.height + lineSpacing
    }
}

private fun DrawScope.drawPointValue(
    textMeasurer: TextMeasurer,
    text: String,
    x: Float,
    y: Float,
    style: TextStyle,
    anchor: CanvasTextAnchor = CanvasTextAnchor.START,
    maxX: Float,
    minX: Float,
) {
    val measuredWidth = textMeasurer.measureCanvasText(text, style).size.width.toFloat()
    val drawX = when (anchor) {
        CanvasTextAnchor.END -> x.coerceAtMost(maxX).coerceAtLeast(minX + measuredWidth)
        CanvasTextAnchor.CENTER -> x.coerceIn(minX + measuredWidth / 2f, maxX - measuredWidth / 2f)
        CanvasTextAnchor.START -> x.coerceAtLeast(minX).coerceAtMost(maxX - measuredWidth)
    }
    drawCanvasText(
        textMeasurer = textMeasurer,
        text = text,
        x = drawX,
        baselineY = y,
        style = style,
        anchor = anchor,
    )
}

private data class BottomAxisLabel(
    val text: String,
    val preferredX: Float,
    val style: TextStyle,
)

private fun DrawScope.drawBottomAxisLabels(
    textMeasurer: TextMeasurer,
    labels: List<BottomAxisLabel>,
    y: Float,
    plotLeft: Float,
    plotRight: Float,
    minimumGapPx: Float,
) {
    if (labels.isEmpty()) return

    val layout = labels
        .map { label -> label to textMeasurer.measureCanvasText(label.text, label.style).size.width.toFloat() }
        .sortedBy { it.first.preferredX }
    val centers = layoutBottomAxisLabelCenters(
        preferredCenters = layout.map { it.first.preferredX },
        widths = layout.map { it.second },
        left = plotLeft,
        right = plotRight,
        minimumGapPx = minimumGapPx,
    )

    layout.zip(centers).forEach { (positioned, centerX) ->
        drawCanvasText(
            textMeasurer = textMeasurer,
            text = positioned.first.text,
            x = centerX,
            baselineY = y,
            style = positioned.first.style,
            anchor = CanvasTextAnchor.CENTER,
        )
    }
}

internal fun layoutBottomAxisLabelCenters(
    preferredCenters: List<Float>,
    widths: List<Float>,
    left: Float,
    right: Float,
    minimumGapPx: Float,
): List<Float> {
    require(preferredCenters.size == widths.size)
    if (preferredCenters.isEmpty()) return emptyList()

    val availableWidth = right - left
    if (availableWidth <= 0f) return preferredCenters

    val totalLabelWidth = widths.fold(0f) { sum, width -> sum + width }
    val effectiveGap = if (widths.size > 1) {
        minOf(
            minimumGapPx,
            ((availableWidth - totalLabelWidth) / (widths.size - 1)).coerceAtLeast(0f),
        )
    } else {
        0f
    }

    fun minCenter(index: Int) = left + widths[index] / 2f
    fun maxCenter(index: Int) = right - widths[index] / 2f
    fun clampedCenter(index: Int, center: Float): Float {
        val minCenter = minCenter(index)
        val maxCenter = maxCenter(index)
        return if (minCenter <= maxCenter) {
            center.coerceIn(minCenter, maxCenter)
        } else {
            (left + right) / 2f
        }
    }

    fun requiredCenterAfter(previousIndex: Int, currentIndex: Int): Float =
        widths[previousIndex] / 2f + effectiveGap + widths[currentIndex] / 2f

    val centers = preferredCenters
        .mapIndexed { index, center -> clampedCenter(index, center) }
        .toMutableList()

    for (index in 1 until centers.size) {
        centers[index] = maxOf(
            centers[index],
            centers[index - 1] + requiredCenterAfter(index - 1, index),
        )
    }

    val rightOverflow = centers.last() + widths.last() / 2f - right
    if (rightOverflow > 0f) {
        for (index in centers.indices) {
            centers[index] -= rightOverflow
        }
    }

    val leftOverflow = left - (centers.first() - widths.first() / 2f)
    if (leftOverflow > 0f) {
        for (index in centers.indices) {
            centers[index] += leftOverflow
        }
    }

    for (index in 1 until centers.size) {
        centers[index] = maxOf(
            centers[index],
            centers[index - 1] + requiredCenterAfter(index - 1, index),
        )
    }

    val finalRightOverflow = centers.last() + widths.last() / 2f - right
    if (finalRightOverflow > 0f) {
        for (index in centers.lastIndex downTo 0) {
            centers[index] -= finalRightOverflow
        }
    }

    for (index in centers.lastIndex - 1 downTo 0) {
        centers[index] = minOf(
            centers[index],
            centers[index + 1] - requiredCenterAfter(index, index + 1),
        )
    }

    return centers.mapIndexed { index, center -> clampedCenter(index, center) }
}
