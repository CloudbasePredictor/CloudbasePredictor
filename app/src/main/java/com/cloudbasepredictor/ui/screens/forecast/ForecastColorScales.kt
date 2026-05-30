package com.cloudbasepredictor.ui.screens.forecast

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

internal const val THERMIC_STRENGTH_COLOR_SCALE_MAX_MPS = 5f
internal const val WIND_SPEED_COLOR_SCALE_MAX_KMH = 60f

private val thermicStrengthColorStops = listOf(
    0.0f to Color(0xFFF4FAFF),
    0.4f to Color(0xFFD7F0FF),
    0.8f to Color(0xFFA8D8FF),
    1.2f to Color(0xFF4BA3F2),
    1.6f to Color(0xFF00A896),
    2.0f to Color(0xFF43A047),
    3.0f to Color(0xFFD1C300),
    4.0f to Color(0xFFFB8C00),
    THERMIC_STRENGTH_COLOR_SCALE_MAX_MPS to Color(0xFFD32F2F),
)

private val windSpeedColorStops = listOf(
    0f to Color(0xFF1565C0),
    5f to Color(0xFF0288D1),
    10f to Color(0xFF00ACC1),
    15f to Color(0xFF00A86B),
    20f to Color(0xFF43A047),
    30f to Color(0xFF7CB342),
    40f to Color(0xFFFDD835),
    50f to Color(0xFFFB8C00),
    WIND_SPEED_COLOR_SCALE_MAX_KMH to Color(0xFFD32F2F),
)

internal fun thermicStrengthColor(strengthMps: Float): Color {
    return interpolateColorStops(
        value = strengthMps.coerceIn(0f, THERMIC_STRENGTH_COLOR_SCALE_MAX_MPS),
        colorStops = thermicStrengthColorStops,
    )
}

internal fun windSpeedColor(speedKmh: Float): Color {
    return interpolateColorStops(
        value = speedKmh.coerceIn(0f, WIND_SPEED_COLOR_SCALE_MAX_KMH),
        colorStops = windSpeedColorStops,
    )
}

private fun interpolateColorStops(
    value: Float,
    colorStops: List<Pair<Float, Color>>,
): Color {
    val lowerStop = colorStops.lastOrNull { it.first <= value } ?: colorStops.first()
    val upperStop = colorStops.firstOrNull { it.first >= value } ?: colorStops.last()

    if (lowerStop.first == upperStop.first) return lowerStop.second

    val fraction = (value - lowerStop.first) / (upperStop.first - lowerStop.first)
    return lerp(lowerStop.second, upperStop.second, fraction)
}
