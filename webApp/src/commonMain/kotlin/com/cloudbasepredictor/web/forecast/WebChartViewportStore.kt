package com.cloudbasepredictor.web.forecast

import com.cloudbasepredictor.data.storage.KeyValueStorage

/**
 * Persists the forecast chart's visible top-altitude (km) in the durable user-state document so the
 * vertical zoom of the chart survives reloads and navigation, mirroring the Android app.
 */
class WebChartViewportStore(
    private val storage: KeyValueStorage,
) {
    fun readTopAltitudeKm(): Float? =
        storage.getString(TOP_ALTITUDE_KEY)
            ?.toFloatOrNull()
            ?.takeIf { it in MIN_TOP_ALTITUDE_KM..MAX_TOP_ALTITUDE_KM }

    fun writeTopAltitudeKm(value: Float) {
        if (value in MIN_TOP_ALTITUDE_KM..MAX_TOP_ALTITUDE_KM) {
            storage.putString(TOP_ALTITUDE_KEY, value.toString())
        }
    }

    private companion object {
        const val TOP_ALTITUDE_KEY = "chart_top_altitude_km"
        const val MIN_TOP_ALTITUDE_KM = 0.5f
        const val MAX_TOP_ALTITUDE_KM = 25f
    }
}
