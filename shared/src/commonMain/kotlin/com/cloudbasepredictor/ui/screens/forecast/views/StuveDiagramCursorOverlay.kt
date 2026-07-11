@file:Suppress("LongMethod", "MagicNumber", "MaxLineLength")

package com.cloudbasepredictor.ui.screens.forecast.views

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.cloudbasepredictor.domain.forecast.dryAdiabatTempC
import com.cloudbasepredictor.domain.forecast.moistAdiabatTempFromPointC

internal fun DrawScope.drawCursorOverlay(
    readout: CursorReadout,
    cursorY: Float,
    topPressure: Float,
    bottomPressure: Float,
    plotLeft: Float,
    plotRight: Float,
    onSurfaceColor: Color,
    temperatureToX: (Float, Float) -> Float,
    pressureToY: (Float) -> Float,
    showThermoGuides: Boolean = true,
) {
    drawLine(
        color = onSurfaceColor.copy(alpha = 0.58f),
        start = Offset(plotLeft, cursorY),
        end = Offset(plotRight, cursorY),
        strokeWidth = 1.dp.toPx(),
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx())),
    )

    // Isotherm guide through the placed (tapped) point, not through the environment temperature on
    // the red curve — so the line passes through the cursor's green marker.
    readout.guideTemperatureC?.let { temperature ->
        drawLine(
            color = Color(0xFFD83A3A).copy(alpha = 0.45f),
            start = Offset(temperatureToX(temperature, bottomPressure), pressureToY(bottomPressure)),
            end = Offset(temperatureToX(temperature, topPressure), pressureToY(topPressure)),
            strokeWidth = 1.2f.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx())),
        )
    }

    if (showThermoGuides) {
        readout.guideDryThetaK?.let { thetaK ->
            drawAdiabat(
                pressures = buildReferencePressures(bottomPressure, readout.pressureHpa, stepHpa = 25f) +
                    listOf(readout.pressureHpa),
                computeTemp = { pressure -> dryAdiabatTempC(thetaK, pressure) },
                mapXY = { temperature, pressure ->
                    Offset(temperatureToX(temperature, pressure), pressureToY(pressure))
                },
                plotLeft = plotLeft,
                plotRight = plotRight,
                plotTop = pressureToY(topPressure),
                plotBottom = pressureToY(bottomPressure),
                color = Color(0xFF59A36A).copy(alpha = 0.72f),
                strokeWidth = 0.8f.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(7.dp.toPx(), 5.dp.toPx())),
            )
        }
    }

    if (showThermoGuides) {
        readout.guideTemperatureC?.let { guideTemperatureC ->
            drawAdiabat(
                pressures = (listOf(readout.pressureHpa) + buildReferencePressures(readout.pressureHpa, topPressure, stepHpa = 25f))
                    .distinct()
                    .sortedDescending(),
                computeTemp = { pressure ->
                    moistAdiabatTempFromPointC(
                        startTemperatureC = guideTemperatureC,
                        startPressureHpa = readout.pressureHpa,
                        targetPressureHpa = pressure,
                    )
                },
                mapXY = { temperature, pressure ->
                    Offset(temperatureToX(temperature, pressure), pressureToY(pressure))
                },
                plotLeft = plotLeft,
                plotRight = plotRight,
                plotTop = pressureToY(topPressure),
                plotBottom = pressureToY(bottomPressure),
                color = Color(0xFF59A36A).copy(alpha = 0.86f),
                strokeWidth = 1.8f.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(7.dp.toPx(), 5.dp.toPx())),
            )
        }
    }

    readout.temperatureC?.let { temperature ->
        drawCircle(
            color = Color(0xFFD83A3A),
            radius = 4.dp.toPx(),
            center = Offset(temperatureToX(temperature, readout.pressureHpa), cursorY),
        )
    }
    readout.dewpointC?.let { dewpoint ->
        drawCircle(
            color = Color(0xFF2E6FB5),
            radius = 3.6f.dp.toPx(),
            center = Offset(temperatureToX(dewpoint, readout.pressureHpa), cursorY),
        )
    }
    readout.parcelTemperatureC?.let { parcelTemperature ->
        val parcelX = temperatureToX(parcelTemperature, readout.pressureHpa)
        drawCircle(
            color = onSurfaceColor.copy(alpha = 0.65f),
            radius = 3.2f.dp.toPx(),
            center = Offset(parcelX, cursorY),
            style = Stroke(width = 1.6f.dp.toPx()),
        )

        readout.temperatureC?.let { ambientTemperature ->
            drawLine(
                color = Color(0xFFE2A85F).copy(alpha = 0.55f),
                start = Offset(temperatureToX(ambientTemperature, readout.pressureHpa), cursorY),
                end = Offset(parcelX, cursorY),
                strokeWidth = 1.8f.dp.toPx(),
            )
        }
    }

    readout.guideTemperatureC?.let { guideTemperature ->
        drawCircle(
            color = Color(0xFF59A36A),
            radius = 4.2f.dp.toPx(),
            center = Offset(temperatureToX(guideTemperature, readout.pressureHpa), cursorY),
            style = Stroke(width = 1.8f.dp.toPx()),
        )
    }
}
