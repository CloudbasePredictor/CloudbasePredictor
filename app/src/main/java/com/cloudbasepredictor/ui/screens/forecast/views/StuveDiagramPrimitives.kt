package com.cloudbasepredictor.ui.screens.forecast.views

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas as ComposeGraphicsCanvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cloudbasepredictor.data.units.DisplayUnits
import com.cloudbasepredictor.data.units.formatAltitudeMeters
import com.cloudbasepredictor.ui.screens.forecast.StuveForecastChartUiModel
import com.cloudbasepredictor.ui.screens.forecast.StuveProfilePoint
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

internal fun DrawScope.drawMoistureCueStrip(
    chart: StuveForecastChartUiModel,
    plotTop: Float,
    plotBottom: Float,
    plotRight: Float,
    pressureToY: (Float) -> Float,
    density: androidx.compose.ui.unit.Density,
) {
    if (chart.moistureBands.isEmpty()) return

    val stripWidth = with(density) { 9.dp.toPx() }
    val stripLeft = plotRight + 2.dp.toPx()

    chart.moistureBands.forEach { band ->
        val bandTopY = pressureToY(band.topPressureHpa).coerceIn(plotTop, plotBottom)
        val bandBottomY = pressureToY(band.bottomPressureHpa).coerceIn(plotTop, plotBottom)
        if (bandBottomY <= bandTopY) return@forEach

        val intensity = ((band.relativeHumidityFraction - 0.55f) / 0.45f).coerceIn(0f, 1f)
        if (intensity <= 0f) return@forEach

        drawRect(
            color = lerp(
                start = Color(0xFFD7EAF4),
                stop = Color(0xFF4E7C9A),
                fraction = intensity,
            ).copy(alpha = 0.16f + intensity * 0.42f),
            topLeft = Offset(stripLeft, bandTopY),
            size = Size(stripWidth, bandBottomY - bandTopY),
        )
    }
}

internal fun drawMarkerLabel(
    canvas: ComposeGraphicsCanvas,
    text: String,
    y: Float,
    x: Float,
    color: Color,
    density: androidx.compose.ui.unit.Density,
    yOffsetPx: Float = 0f,
) {
    if (!y.isFinite()) return
    val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color.toArgb()
        textSize = with(density) { 9.sp.toPx() }
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
    }
    canvas.nativeCanvas.drawText(
        text,
        x,
        y - with(density) { 3.dp.toPx() } + yOffsetPx,
        labelPaint,
    )
}

internal fun DrawScope.drawAdiabat(
    pressures: List<Float>,
    computeTemp: (Float) -> Float,
    mapXY: (Float, Float) -> Offset,
    plotLeft: Float,
    plotRight: Float,
    plotTop: Float,
    plotBottom: Float,
    color: Color,
    strokeWidth: Float,
    pathEffect: PathEffect? = null,
) {
    val points = pressures.map { pressure ->
        mapXY(computeTemp(pressure), pressure)
    }.filter { point ->
        point.x in (plotLeft - 24f)..(plotRight + 24f) && point.y in plotTop..plotBottom
    }

    for (index in 0 until points.size - 1) {
        drawLine(
            color = color,
            start = points[index],
            end = points[index + 1],
            strokeWidth = strokeWidth,
            pathEffect = pathEffect,
        )
    }
}

internal fun DrawScope.drawSkewTProfile(
    points: List<StuveProfilePoint>,
    mapXY: (Float, Float) -> Offset,
    plotLeft: Float,
    plotRight: Float,
    plotTop: Float,
    plotBottom: Float,
    color: Color,
    strokeWidth: Float,
    pathEffect: PathEffect? = null,
    drawDataDots: Boolean = false,
    dataDotRadius: Float = 0f,
) {
    val offsets = points.map { point -> mapXY(point.temperatureC, point.pressureHpa) }

    for (index in 0 until offsets.size - 1) {
        val start = offsets[index]
        val end = offsets[index + 1]
        if (start.y in plotTop..plotBottom || end.y in plotTop..plotBottom) {
            drawLine(
                color = color,
                start = start,
                end = end,
                strokeWidth = strokeWidth,
                pathEffect = pathEffect,
            )
        }
    }

    if (drawDataDots && dataDotRadius > 0f) {
        points.forEachIndexed { index, point ->
            if (point.isRealData) {
                val offset = offsets[index]
                if (offset.x in plotLeft..plotRight && offset.y in plotTop..plotBottom) {
                    drawCircle(
                        color = color,
                        radius = dataDotRadius,
                        center = offset,
                    )
                }
            }
        }
    }
}

internal fun DrawScope.drawWindBarb(
    centerX: Float,
    centerY: Float,
    speedKmh: Float,
    directionDeg: Float,
    barbSize: Float,
    color: Color,
) {
    val angleRad = (directionDeg - 90f) * PI.toFloat() / 180f
    val halfSize = barbSize / 2f

    val tipX = centerX + cos(angleRad) * halfSize
    val tipY = centerY + sin(angleRad) * halfSize
    val tailX = centerX - cos(angleRad) * halfSize
    val tailY = centerY - sin(angleRad) * halfSize

    drawLine(
        color = color,
        start = Offset(tailX, tailY),
        end = Offset(tipX, tipY),
        strokeWidth = 1.5f,
    )

    val arrowLength = halfSize * 0.4f
    val arrowAngle = PI.toFloat() / 6f
    drawLine(
        color = color,
        start = Offset(tipX, tipY),
        end = Offset(
            tipX - cos(angleRad - arrowAngle) * arrowLength,
            tipY - sin(angleRad - arrowAngle) * arrowLength,
        ),
        strokeWidth = 1.5f,
    )
    drawLine(
        color = color,
        start = Offset(tipX, tipY),
        end = Offset(
            tipX - cos(angleRad + arrowAngle) * arrowLength,
            tipY - sin(angleRad + arrowAngle) * arrowLength,
        ),
        strokeWidth = 1.5f,
    )
}

internal fun formatAxisHeight(
    heightMeters: Float,
    displayUnits: DisplayUnits,
): String = formatAltitudeMeters(heightMeters, displayUnits, compact = true)

internal fun formatReadoutHeight(
    heightMeters: Int,
    displayUnits: DisplayUnits,
): String = formatAltitudeMeters(heightMeters.toFloat(), displayUnits)
