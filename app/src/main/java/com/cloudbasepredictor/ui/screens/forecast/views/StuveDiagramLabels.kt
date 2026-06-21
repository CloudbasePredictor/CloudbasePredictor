package com.cloudbasepredictor.ui.screens.forecast.views

import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import androidx.compose.ui.graphics.Canvas as ComposeGraphicsCanvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cloudbasepredictor.data.units.DisplayUnits
import com.cloudbasepredictor.data.units.formatWindSpeed
import com.cloudbasepredictor.domain.forecast.dryAdiabatTempC
import java.util.Locale
import kotlin.math.roundToInt

internal fun drawCursorInlineLabels(
    canvas: ComposeGraphicsCanvas,
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
    axisLabelPaint: Paint,
    altitudeLabelPaint: Paint,
    temperatureReadoutBaseline: Float,
    temperatureReadoutLabelLeft: Float,
    temperatureReadoutLabelRight: Float,
    displayUnits: DisplayUnits,
    density: androidx.compose.ui.unit.Density,
    temperatureToX: (Float, Float) -> Float,
) {
    drawBadgeLabel(
        canvas = canvas,
        lines = listOf(
            formatReadoutHeight(readout.altitudeMeters, displayUnits),
            "${readout.pressureHpa.roundToInt()} hPa",
        ),
        centerX = plotLeft - with(density) { 10.dp.toPx() },
        centerY = cursorY,
        density = density,
        textPaint = Paint(axisLabelPaint).apply {
            color = Color(0xFF2B2B2B).toArgb()
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        },
        backgroundColor = Color(0xFFF3F3F3),
        minWidth = with(density) { 46.dp.toPx() },
        minX = labelMinX,
        maxX = labelMaxX,
    )

    drawBadgeLabel(
        canvas = canvas,
        lines = listOf(formatAxisHeight(readout.altitudeMeters.toFloat(), displayUnits)),
        centerX = plotRight + with(density) { 18.dp.toPx() },
        centerY = cursorY,
        density = density,
        textPaint = Paint(altitudeLabelPaint).apply {
            color = Color(0xFF2B2B2B).toArgb()
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        },
        backgroundColor = Color(0xFFF3F3F3).copy(alpha = 0.92f),
        minWidth = with(density) { 34.dp.toPx() },
        minX = labelMinX,
        maxX = labelMaxX,
    )

    val pointLabelPaint = fun(color: Color): Paint {
        return Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color.toArgb()
            textSize = with(density) { 9.sp.toPx() }
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        }
    }

    readout.temperatureC?.let { temperature ->
        val pointX = temperatureToX(temperature, readout.pressureHpa)
        drawPointValue(
            canvas = canvas,
            text = String.format(Locale.US, "T %.1f°", temperature),
            x = pointX + with(density) { 6.dp.toPx() },
            y = cursorY - with(density) { 6.dp.toPx() },
            paint = pointLabelPaint(Color(0xFFD83A3A)),
            maxX = plotRight - with(density) { 4.dp.toPx() },
            minX = plotLeft + with(density) { 4.dp.toPx() },
        )
    }

    readout.dewpointC?.let { dewpoint ->
        val pointX = temperatureToX(dewpoint, readout.pressureHpa)
        drawPointValue(
            canvas = canvas,
            text = String.format(Locale.US, "Td %.1f°", dewpoint),
            x = pointX - with(density) { 6.dp.toPx() },
            y = cursorY + with(density) { 16.dp.toPx() },
            paint = pointLabelPaint(Color(0xFF2E6FB5)).apply {
                textAlign = Paint.Align.RIGHT
            },
            maxX = plotRight - with(density) { 4.dp.toPx() },
            minX = plotLeft + with(density) { 4.dp.toPx() },
        )
    }

    readout.parcelTemperatureC?.let { parcelTemperature ->
        val pointX = temperatureToX(parcelTemperature, readout.pressureHpa)
        drawPointValue(
            canvas = canvas,
            text = String.format(Locale.US, "Parcel %.1f°", parcelTemperature),
            x = pointX + with(density) { 6.dp.toPx() },
            y = cursorY + with(density) { 28.dp.toPx() },
            paint = pointLabelPaint(Color(0xFF59A36A)),
            maxX = plotRight - with(density) { 4.dp.toPx() },
            minX = plotLeft + with(density) { 4.dp.toPx() },
        )
    }

    val readoutBottomLabels = buildList {
        readout.temperatureC?.let { temperature ->
            add(
                BottomAxisLabel(
                    text = String.format(Locale.US, "T %.0f°", temperature),
                    preferredX = temperatureToX(temperature, bottomPressure),
                    paint = pointLabelPaint(Color(0xFFD83A3A)).apply { textAlign = Paint.Align.CENTER },
                ),
            )
        }

        readout.dewpointC?.let { dewpoint ->
            add(
                BottomAxisLabel(
                    text = String.format(Locale.US, "Td %.0f°", dewpoint),
                    preferredX = temperatureToX(dewpoint, bottomPressure),
                    paint = pointLabelPaint(Color(0xFF2E6FB5)).apply { textAlign = Paint.Align.CENTER },
                ),
            )
        }

        readout.parcelSurfaceTemperatureC?.let { parcelSurfaceTemperature ->
            val bottomTemp = readout.guideDryThetaK?.let { dryAdiabatTempC(it, bottomPressure) } ?: return@let
            add(
                BottomAxisLabel(
                    text = String.format(Locale.US, "Parcel %.0f°", parcelSurfaceTemperature),
                    preferredX = temperatureToX(bottomTemp, bottomPressure),
                    paint = pointLabelPaint(Color(0xFF59A36A)).apply { textAlign = Paint.Align.CENTER },
                ),
            )
        }

    }

    drawBottomAxisLabels(
        canvas = canvas,
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
            canvas = canvas,
            lines = listOf(
                "${formatWindSpeed(readout.windSpeedKmh, displayUnits)} ${String.format(Locale.US, "%03.0f°", readout.windDirectionDeg)}",
            ),
            centerX = rightWindCenterX,
            centerY = windBadgeY,
            density = density,
            textPaint = pointLabelPaint(Color(0xFF2B2B2B)).apply {
                textAlign = Paint.Align.CENTER
            },
            backgroundColor = Color(0xFFF3F3F3),
            minWidth = with(density) { 74.dp.toPx() },
            minX = labelMinX,
            maxX = labelMaxX,
        )
    }
}

private fun drawBadgeLabel(
    canvas: ComposeGraphicsCanvas,
    lines: List<String>,
    centerX: Float,
    centerY: Float,
    density: androidx.compose.ui.unit.Density,
    textPaint: Paint,
    backgroundColor: Color,
    minWidth: Float = 0f,
    minX: Float = Float.NEGATIVE_INFINITY,
    maxX: Float = Float.POSITIVE_INFINITY,
) {
    if (lines.isEmpty()) return

    val paddingHorizontal = with(density) { 6.dp.toPx() }
    val paddingVertical = with(density) { 4.dp.toPx() }
    val lineSpacing = with(density) { 2.dp.toPx() }
    val lineHeight = textPaint.textSize
    val maxTextWidth = lines.maxOf { textPaint.measureText(it) }
    val boxWidth = maxOf(minWidth, maxTextWidth + paddingHorizontal * 2f)
    val boxHeight = (lineHeight * lines.size) + lineSpacing * (lines.size - 1) + paddingVertical * 2f
    val resolvedCenterX = if (minX.isFinite() && maxX.isFinite() && maxX > minX + boxWidth) {
        centerX.coerceIn(minX + boxWidth / 2f, maxX - boxWidth / 2f)
    } else {
        centerX
    }
    val rect = RectF(
        resolvedCenterX - boxWidth / 2f,
        centerY - boxHeight / 2f,
        resolvedCenterX + boxWidth / 2f,
        centerY + boxHeight / 2f,
    )

    val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = backgroundColor.toArgb()
    }
    canvas.nativeCanvas.drawRoundRect(
        rect,
        with(density) { 4.dp.toPx() },
        with(density) { 4.dp.toPx() },
        backgroundPaint,
    )

    val firstBaseline = rect.top + paddingVertical + lineHeight * 0.8f
    lines.forEachIndexed { index, line ->
        canvas.nativeCanvas.drawText(
            line,
            resolvedCenterX,
            firstBaseline + index * (lineHeight + lineSpacing),
            textPaint,
        )
    }
}

private fun drawPointValue(
    canvas: ComposeGraphicsCanvas,
    text: String,
    x: Float,
    y: Float,
    paint: Paint,
    maxX: Float,
    minX: Float,
) {
    val measuredWidth = paint.measureText(text)
    val drawX = when (paint.textAlign) {
        Paint.Align.RIGHT -> x.coerceAtMost(maxX).coerceAtLeast(minX + measuredWidth)
        Paint.Align.CENTER -> x.coerceIn(minX + measuredWidth / 2f, maxX - measuredWidth / 2f)
        else -> x.coerceAtLeast(minX).coerceAtMost(maxX - measuredWidth)
    }
    canvas.nativeCanvas.drawText(text, drawX, y, paint)
}

private data class BottomAxisLabel(
    val text: String,
    val preferredX: Float,
    val paint: Paint,
)

private fun drawBottomAxisLabels(
    canvas: ComposeGraphicsCanvas,
    labels: List<BottomAxisLabel>,
    y: Float,
    plotLeft: Float,
    plotRight: Float,
    minimumGapPx: Float,
) {
    if (labels.isEmpty()) return

    val layout = labels
        .map { label -> label to label.paint.measureText(label.text) }
        .sortedBy { it.first.preferredX }
    val centers = layoutBottomAxisLabelCenters(
        preferredCenters = layout.map { it.first.preferredX },
        widths = layout.map { it.second },
        left = plotLeft,
        right = plotRight,
        minimumGapPx = minimumGapPx,
    )

    layout.zip(centers).forEach { (positioned, centerX) ->
        canvas.nativeCanvas.drawText(
            positioned.first.text,
            centerX,
            y,
            positioned.first.paint,
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
