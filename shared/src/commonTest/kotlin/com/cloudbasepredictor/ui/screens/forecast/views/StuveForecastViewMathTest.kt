package com.cloudbasepredictor.ui.screens.forecast.views

import com.cloudbasepredictor.domain.forecast.dryAdiabatTempC
import com.cloudbasepredictor.domain.forecast.moistAdiabatTempFromPointC
import com.cloudbasepredictor.domain.forecast.potentialTemperatureK
import com.cloudbasepredictor.ui.screens.forecast.StuveForecastChartUiModel
import com.cloudbasepredictor.ui.screens.forecast.StuveProfilePoint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StuveForecastViewMathTest {

    @Test
    fun buildVisibleTemperatureAxisRange_ignores_upperLevel_cold_outliers_for_initial_view() {
        val chart = sampleChart(
            temperaturePoints = listOf(
                point(850f, 14f),
                point(800f, 12f),
                point(700f, 7f),
                point(600f, 2f),
                point(500f, -2f),
                point(400f, -8f),
                point(300f, -15f),
                point(250f, -20f),
            ),
            dewpointPoints = listOf(
                point(850f, 10f),
                point(800f, 7f),
                point(700f, 1f),
                point(600f, -6f),
                point(500f, -14f),
                point(400f, -25f),
                point(300f, -39f),
                point(250f, -48f),
            ),
        )

        val range = buildVisibleTemperatureAxisRange(
            chart = chart,
            topPressure = 250f,
            bottomPressure = 870f,
        )

        assertTrue(range.minC in -20f..0f)
        assertTrue(range.maxC in 20f..40f)
        assertTrue(range.maxC - range.minC <= 50f)
    }

    @Test
    fun buildVisibleTemperatureAxisRange_zoomed_upper_view_tracks_current_slice() {
        val chart = sampleChart(
            temperaturePoints = listOf(
                point(850f, 14f),
                point(700f, 7f),
                point(600f, 2f),
                point(500f, -2f),
                point(400f, -8f),
                point(300f, -15f),
                point(250f, -20f),
            ),
            dewpointPoints = listOf(
                point(850f, 10f),
                point(700f, 1f),
                point(600f, -6f),
                point(500f, -14f),
                point(400f, -25f),
                point(300f, -30f),
                point(250f, -33f),
            ),
        )

        val range = buildVisibleTemperatureAxisRange(
            chart = chart,
            topPressure = 500f,
            bottomPressure = 700f,
        )

        assertTrue(range.minC in -30f..0f)
        assertTrue(range.maxC in 10f..30f)
    }

    @Test
    fun recommendedStuveTopAltitudeKm_capsInitialAutoFitForVeryTallProfiles() {
        val chart = sampleChart(
            temperaturePoints = listOf(
                point(950f, 18f, 500f),
                point(850f, 13f, 1450f),
                point(700f, 3f, 3010f),
                point(500f, -8f, 5570f),
                point(300f, -26f, 9160f),
                point(250f, -36f, 10360f),
            ),
            dewpointPoints = listOf(
                point(950f, 10f, 500f),
                point(850f, 6f, 1450f),
                point(700f, -4f, 3010f),
                point(500f, -20f, 5570f),
                point(300f, -40f, 9160f),
                point(250f, -48f, 10360f),
            ),
        )

        val recommendedTop = recommendedStuveTopAltitudeKm(chart)

        assertEquals(6.5f, recommendedTop, 0.01f)
    }

    @Test
    fun buildSkewTProjection_keepsAdiabatAndCurveAnglesFixedAcrossZoom() {
        val chart = sampleChart(
            temperaturePoints = listOf(
                point(1000f, 20f, 120f),
                point(950f, 17f, 560f),
                point(900f, 14f, 1000f),
                point(850f, 11f, 1460f),
                point(700f, 1f, 3010f),
                point(500f, -13f, 5570f),
            ),
            dewpointPoints = listOf(
                point(1000f, 12f, 120f),
                point(950f, 9f, 560f),
                point(900f, 6f, 1000f),
                point(850f, 3f, 1460f),
                point(700f, -8f, 3010f),
                point(500f, -27f, 5570f),
            ),
            surfacePressureHpa = 980f,
        )
        val bottomPressure = 1000f

        // Three different vertical zoom levels: full column, mid zoom, tight zoom near the ground.
        // The °C span scales with the visible pressure span, so every line angle must stay constant.
        val projections = listOf(500f, 650f, 800f).map { topPressure ->
            buildSkewTProjection(
                chart = chart,
                topPressure = topPressure,
                bottomPressure = bottomPressure,
                plotLeft = 40f,
                plotRight = 340f,
                plotTop = 16f,
                plotBottom = 616f,
            )
        }
        val reference = projections.first()
        val dryThetaK = potentialTemperatureK(18f, 980f)

        val referenceAdiabatSlope = dryAdiabatScreenSlope(reference, dryThetaK)
        val referenceProfileSlope = profileSegmentScreenSlope(reference)
        projections.forEach { projection ->
            // A background dry-adiabat must keep the same on-screen angle at every zoom level.
            assertEquals(
                referenceAdiabatSlope,
                dryAdiabatScreenSlope(projection, dryThetaK),
                0.01f,
            )
            // A segment of the plotted temperature profile must keep its on-screen angle too.
            assertEquals(
                referenceProfileSlope,
                profileSegmentScreenSlope(projection),
                0.01f,
            )
        }

        // The °C window does crop with zoom (the accepted trade-off for fixed angles): zooming in
        // toward the ground shows a narrower span than the full-column view.
        assertTrue(projections.last().temperatureRange.spanC < reference.temperatureRange.spanC)
    }

    @Test
    fun skewTProjection_roundTripsTemperatureAtPressure() {
        val chart = sampleChart(
            temperaturePoints = listOf(
                point(950f, 18f, 500f),
                point(850f, 12f, 1450f),
                point(700f, 4f, 3010f),
                point(500f, -10f, 5570f),
            ),
            dewpointPoints = listOf(
                point(950f, 9f, 500f),
                point(850f, 5f, 1450f),
                point(700f, -2f, 3010f),
                point(500f, -18f, 5570f),
            ),
            surfacePressureHpa = 930f,
        )
        val projection = buildSkewTProjection(
            chart = chart,
            topPressure = 500f,
            bottomPressure = 950f,
            plotLeft = 40f,
            plotRight = 320f,
            plotTop = 16f,
            plotBottom = 616f,
        )
        val pressure = 700f
        val temperature = -4.5f

        val x = projection.temperatureToX(temperature, pressure)
        val roundTrippedTemperature = projection.xToTemperature(x, pressure)

        assertEquals(temperature, roundTrippedTemperature, 0.001f)
    }

    @Test
    fun shouldDrawDefaultParcelGuide_hidesGuideWhileTapOverlayIsShowing() {
        // No overlay → the default dashed parcel guide is drawn.
        assertTrue(shouldDrawDefaultParcelGuide(isCursorActive = false))
        // Tap overlay (cursor readout) showing → the default guide is hidden.
        assertFalse(shouldDrawDefaultParcelGuide(isCursorActive = true))
    }

    @Test
    fun buildInteractiveParcelFromPoint_usesDryBelowAnchorAndMoistAboveAnchor() {
        val chart = sampleChart(
            temperaturePoints = listOf(
                point(950f, 18f, 500f),
                point(850f, 12f, 1450f),
                point(700f, 4f, 3010f),
                point(500f, -10f, 5570f),
                point(400f, -20f, 7180f),
            ),
            dewpointPoints = listOf(
                point(950f, 9f, 500f),
                point(850f, 5f, 1450f),
                point(700f, -2f, 3010f),
                point(500f, -18f, 5570f),
                point(400f, -28f, 7180f),
            ),
        )
        val anchorTemperatureC = -1f
        val anchorPressureHpa = 600f

        val path = buildInteractiveParcelFromPoint(
            anchorTemperatureC = anchorTemperatureC,
            anchorPressureHpa = anchorPressureHpa,
            chart = chart,
            parcelPressures = listOf(950f, 850f, 700f, 500f, 400f),
        )

        val anchorPoint = path.firstOrNull { kotlin.math.abs(it.pressureHpa - anchorPressureHpa) < 0.01f }
        assertEquals(anchorTemperatureC, anchorPoint?.temperatureC ?: Float.NaN, 0.01f)

        val dryThetaK = potentialTemperatureK(anchorTemperatureC, anchorPressureHpa)
        val belowAnchor = path.first { kotlin.math.abs(it.pressureHpa - 700f) < 0.01f }
        val aboveAnchor = path.first { kotlin.math.abs(it.pressureHpa - 500f) < 0.01f }

        assertEquals(dryAdiabatTempC(dryThetaK, 700f), belowAnchor.temperatureC, 0.01f)
        assertEquals(
            moistAdiabatTempFromPointC(anchorTemperatureC, anchorPressureHpa, 500f),
            aboveAnchor.temperatureC,
            0.01f,
        )
    }

    private fun sampleChart(
        temperaturePoints: List<StuveProfilePoint>,
        dewpointPoints: List<StuveProfilePoint>,
        surfacePressureHpa: Float = 850f,
    ) = StuveForecastChartUiModel(
        pressureLevels = listOf(850f, 800f, 700f, 600f, 500f, 400f, 300f, 250f),
        temperatureProfile = temperaturePoints,
        dewpointProfile = dewpointPoints,
        parcelAscentPath = temperaturePoints.map { it.copy(temperatureC = it.temperatureC + 1.5f) },
        windBarbs = emptyList(),
        cclPressureHpa = 760f,
        tconC = 18f,
        selectedHour = 12,
        surfacePressureHpa = surfacePressureHpa,
    )

    private fun point(
        pressureHpa: Float,
        temperatureC: Float,
        heightMeters: Float? = null,
    ) =
        StuveProfilePoint(
            pressureHpa = pressureHpa,
            temperatureC = temperatureC,
            heightMeters = heightMeters,
            isRealData = true,
        )

    private fun dryAdiabatScreenSlope(
        projection: SkewTProjection,
        thetaK: Float,
    ): Float = screenSlopeBetweenPressures(
        projection = projection,
        lowerPressure = 950f,
        upperPressure = 900f,
        temperatureAt = { pressure -> dryAdiabatTempC(thetaK, pressure) },
    )

    private fun profileSegmentScreenSlope(
        projection: SkewTProjection,
    ): Float = screenSlopeBetweenPressures(
        projection = projection,
        lowerPressure = 950f,
        upperPressure = 900f,
        // A straight 950→900 hPa segment with a fixed lapse: 17 °C at 950, 14 °C at 900.
        temperatureAt = { pressure -> 17f + (14f - 17f) * (950f - pressure) / (950f - 900f) },
    )

    private fun screenSlopeBetweenPressures(
        projection: SkewTProjection,
        lowerPressure: Float,
        upperPressure: Float,
        temperatureAt: (Float) -> Float,
    ): Float {
        val lowerX = projection.temperatureToX(temperatureAt(lowerPressure), lowerPressure)
        val upperX = projection.temperatureToX(temperatureAt(upperPressure), upperPressure)
        val lowerY = projection.pressureToY(lowerPressure)
        val upperY = projection.pressureToY(upperPressure)
        return (upperX - lowerX) / (upperY - lowerY)
    }
}
