@file:Suppress("MagicNumber")

package com.cloudbasepredictor.ui.screens.forecast.views

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cloudbasepredictor.data.units.DisplayUnits
import com.cloudbasepredictor.data.units.formatAltitudeMeters
import com.cloudbasepredictor.ui.screens.forecast.StuveForecastChartUiModel
import com.cloudbasepredictor.ui.screens.forecast.StuveProfilePoint
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
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

internal fun DrawScope.drawMarkerLabel(
    textMeasurer: TextMeasurer,
    text: String,
    y: Float,
    x: Float,
    color: Color,
    density: androidx.compose.ui.unit.Density,
    yOffsetPx: Float = 0f,
) {
    if (!y.isFinite()) return
    drawCanvasText(
        textMeasurer = textMeasurer,
        text = text,
        x = x,
        baselineY = y - with(density) { 3.dp.toPx() } + yOffsetPx,
        style = TextStyle(
            color = color,
            fontSize = 9.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
        ),
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
    val geometry = buildWindBarbGeometry(
        centerX = centerX,
        centerY = centerY,
        speedKmh = speedKmh,
        directionDeg = directionDeg,
        barbSize = barbSize,
    )
    val strokeWidth = 1.5f

    if (geometry.calmRadius != null) {
        drawCircle(
            color = color,
            radius = geometry.calmRadius,
            center = Offset(centerX, centerY),
            style = Stroke(width = strokeWidth),
        )
        return
    }

    drawLine(
        color = color,
        start = geometry.shaft.start,
        end = geometry.shaft.end,
        strokeWidth = strokeWidth,
        cap = StrokeCap.Round,
    )

    geometry.flags.forEach { flag ->
        val path = Path().apply {
            moveTo(flag.points[0].x, flag.points[0].y)
            lineTo(flag.points[1].x, flag.points[1].y)
            lineTo(flag.points[2].x, flag.points[2].y)
            close()
        }
        drawPath(path = path, color = color)
    }

    geometry.feathers.forEach { feather ->
        drawLine(
            color = color,
            start = feather.start,
            end = feather.end,
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round,
        )
    }
}

internal data class WindBarbSpeedParts(
    val roundedKnots: Int,
    val flags: Int,
    val fullFeathers: Int,
    val halfFeathers: Int,
)

internal data class WindBarbLine(
    val start: Offset,
    val end: Offset,
)

internal data class WindBarbFlag(
    val points: List<Offset>,
)

internal data class WindBarbGeometry(
    val shaft: WindBarbLine,
    val flags: List<WindBarbFlag>,
    val feathers: List<WindBarbLine>,
    val calmRadius: Float?,
)

internal fun windBarbSpeedParts(speedKmh: Float): WindBarbSpeedParts {
    val roundedKnots = (speedKmh.coerceAtLeast(0f) / KMH_PER_KNOT / 5f)
        .roundToInt() * 5
    var remainingKnots = roundedKnots
    val flags = remainingKnots / 50
    remainingKnots %= 50
    val fullFeathers = remainingKnots / 10
    remainingKnots %= 10
    val halfFeathers = if (remainingKnots >= 5) 1 else 0

    return WindBarbSpeedParts(
        roundedKnots = roundedKnots,
        flags = flags,
        fullFeathers = fullFeathers,
        halfFeathers = halfFeathers,
    )
}

internal fun buildWindBarbGeometry(
    centerX: Float,
    centerY: Float,
    speedKmh: Float,
    directionDeg: Float,
    barbSize: Float,
): WindBarbGeometry {
    val speedParts = windBarbSpeedParts(speedKmh)
    val center = Offset(centerX, centerY)
    if (speedParts.roundedKnots == 0) {
        return WindBarbGeometry(
            shaft = WindBarbLine(center, center),
            flags = emptyList(),
            feathers = emptyList(),
            calmRadius = barbSize * 0.18f,
        )
    }

    val angleRad = (directionDeg - 90f) * PI.toFloat() / 180f
    val shaftUnit = Offset(cos(angleRad), sin(angleRad))
    // Use the conventional northern-hemisphere barb side; this primitive has no latitude input.
    val featherSideUnit = Offset(-shaftUnit.y, shaftUnit.x)
    val halfSize = barbSize / 2f
    val fromEnd = center + shaftUnit * halfSize
    val toEnd = center - shaftUnit * halfSize

    val symbolCount = speedParts.flags + speedParts.fullFeathers + speedParts.halfFeathers
    val spacing = if (symbolCount > 0) {
        min(barbSize * 0.16f, barbSize * 0.78f / symbolCount)
    } else {
        barbSize * 0.16f
    }
    val featherBack = barbSize * 0.32f
    val featherOut = barbSize * 0.23f
    val flagBack = barbSize * 0.28f
    val flagOut = barbSize * 0.26f

    val flags = mutableListOf<WindBarbFlag>()
    val feathers = mutableListOf<WindBarbLine>()
    var offsetAlongShaft = 0f

    repeat(speedParts.flags) {
        val attach = fromEnd - shaftUnit * offsetAlongShaft
        val nextAttach = fromEnd - shaftUnit * (offsetAlongShaft + spacing)
        val outer = attach - shaftUnit * flagBack + featherSideUnit * flagOut
        flags += WindBarbFlag(listOf(attach, outer, nextAttach))
        offsetAlongShaft += spacing
    }

    repeat(speedParts.fullFeathers) {
        val attach = fromEnd - shaftUnit * offsetAlongShaft
        feathers += WindBarbLine(
            start = attach,
            end = attach - shaftUnit * featherBack + featherSideUnit * featherOut,
        )
        offsetAlongShaft += spacing
    }

    repeat(speedParts.halfFeathers) {
        val attach = fromEnd - shaftUnit * offsetAlongShaft
        feathers += WindBarbLine(
            start = attach,
            end = attach - shaftUnit * featherBack * 0.68f + featherSideUnit * featherOut * 0.58f,
        )
    }

    return WindBarbGeometry(
        shaft = WindBarbLine(start = toEnd, end = fromEnd),
        flags = flags,
        feathers = feathers,
        calmRadius = null,
    )
}

private const val KMH_PER_KNOT = 1.852f

internal fun formatAxisHeight(
    heightMeters: Float,
    displayUnits: DisplayUnits,
): String = formatAltitudeMeters(heightMeters, displayUnits, compact = true)

internal fun formatReadoutHeight(
    heightMeters: Int,
    displayUnits: DisplayUnits,
): String = formatAltitudeMeters(heightMeters.toFloat(), displayUnits)
