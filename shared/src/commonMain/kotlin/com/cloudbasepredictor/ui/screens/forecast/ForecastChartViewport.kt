package com.cloudbasepredictor.ui.screens.forecast

data class ForecastChartViewport(
    val visibleTopAltitudeKm: Float = DEFAULT_TOP_ALTITUDE_KM,
    val defaultTopAltitudeKm: Float = DEFAULT_TOP_ALTITUDE_KM,
    val maxTopAltitudeKm: Float = MAX_TOP_ALTITUDE_KM,
) {
    fun withVisibleTopAltitudeKm(topAltitudeKm: Float): ForecastChartViewport {
        return copy(
            visibleTopAltitudeKm = sanitizeTopAltitudeKm(
                topAltitudeKm = topAltitudeKm,
                minTopAltitudeKm = MIN_TOP_ALTITUDE_KM,
                maxTopAltitudeKm = maxTopAltitudeKm,
            ),
        )
    }
}

internal fun zoomedTopAltitudeKm(
    currentTopAltitudeKm: Float,
    zoomChange: Float,
    minTopAltitudeKm: Float = MIN_TOP_ALTITUDE_KM,
    maxTopAltitudeKm: Float = MAX_TOP_ALTITUDE_KM,
): Float {
    if (!zoomChange.isFinite() || zoomChange <= 0f) {
        return sanitizeTopAltitudeKm(
            topAltitudeKm = currentTopAltitudeKm,
            minTopAltitudeKm = minTopAltitudeKm,
            maxTopAltitudeKm = maxTopAltitudeKm,
        )
    }

    // Amplify the zoom gesture for a more responsive feel similar to maps
    val amplifiedZoom = 1f + (zoomChange - 1f) * ZOOM_AMPLIFICATION_FACTOR

    return sanitizeTopAltitudeKm(
        topAltitudeKm = currentTopAltitudeKm / amplifiedZoom,
        minTopAltitudeKm = minTopAltitudeKm,
        maxTopAltitudeKm = maxTopAltitudeKm,
    )
}

fun sanitizeTopAltitudeKm(
    topAltitudeKm: Float,
    minTopAltitudeKm: Float = DEFAULT_TOP_ALTITUDE_KM,
    maxTopAltitudeKm: Float = MAX_TOP_ALTITUDE_KM,
): Float {
    if (!topAltitudeKm.isFinite()) {
        return minTopAltitudeKm
    }

    return topAltitudeKm.coerceIn(minTopAltitudeKm, maxTopAltitudeKm)
}

const val DEFAULT_TOP_ALTITUDE_KM = 4.5f
internal const val MIN_TOP_ALTITUDE_KM = 1.5f
const val MAX_TOP_ALTITUDE_KM = 12.5f
private const val ZOOM_AMPLIFICATION_FACTOR = 2.5f
