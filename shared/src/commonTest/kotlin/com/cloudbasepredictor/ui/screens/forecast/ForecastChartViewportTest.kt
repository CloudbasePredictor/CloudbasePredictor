package com.cloudbasepredictor.ui.screens.forecast

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ForecastChartViewportTest {
    @Test
    fun zoomedTopAltitudeKm_clampsZoomOutAtConfiguredMaxAltitude() {
        val zoomedOutTop = zoomedTopAltitudeKm(
            currentTopAltitudeKm = 12.2f,
            zoomChange = 0.6f,
        )

        assertEquals(MAX_TOP_ALTITUDE_KM, zoomedOutTop, 0.001f)
    }

    @Test
    fun zoomedTopAltitudeKm_keepsMinBoundWhenZoomingIn() {
        val zoomedInTop = zoomedTopAltitudeKm(
            currentTopAltitudeKm = 2.0f,
            zoomChange = 2.0f,
        )

        assertEquals(MIN_TOP_ALTITUDE_KM, zoomedInTop)
    }

    @Test
    fun viewport_withVisibleTopAltitudeKm_ignoresInvalidInput() {
        val viewport = ForecastChartViewport()

        assertEquals(
            MIN_TOP_ALTITUDE_KM,
            viewport.withVisibleTopAltitudeKm(Float.NaN).visibleTopAltitudeKm,
        )
    }

    @Test
    fun zoomedTopAltitudeKm_amplifiedZoomIn_producesMoreResponsiveChange() {
        // Zoom in gesture (zoomChange > 1 means pinch out = zoom in = lower altitude ceiling)
        val result = zoomedTopAltitudeKm(
            currentTopAltitudeKm = 4.5f,
            zoomChange = 1.2f,
        )
        // With amplification 2.5, effective zoom = 1 + (1.2 - 1) * 2.5 = 1.5
        // Result = 4.5 / 1.5 = 3.0
        assertEquals(3.0f, result, 0.01f)
    }

    @Test
    fun zoomedTopAltitudeKm_amplifiedZoomOut_producesMoreResponsiveChange() {
        // Zoom out gesture (zoomChange < 1 means pinch in = zoom out = raise altitude ceiling)
        val result = zoomedTopAltitudeKm(
            currentTopAltitudeKm = 5.0f,
            zoomChange = 0.9f,
        )
        // With amplification 2.5, effective zoom = 1 + (0.9 - 1) * 2.5 = 0.75
        // Result = 5.0 / 0.75 = 6.667
        assertEquals(6.667f, result, 0.01f)
    }

    @Test
    fun zoomedTopAltitudeKm_strongerPinchOutProducesDeeperZoom() {
        val gentleZoom = zoomedTopAltitudeKm(
            currentTopAltitudeKm = 4.5f,
            zoomChange = 1.2f,
        )
        val strongZoom = zoomedTopAltitudeKm(
            currentTopAltitudeKm = 4.5f,
            zoomChange = 1.6f,
        )

        assertTrue(strongZoom < gentleZoom)
        assertEquals(1.8f, strongZoom, 0.01f)
    }
}
