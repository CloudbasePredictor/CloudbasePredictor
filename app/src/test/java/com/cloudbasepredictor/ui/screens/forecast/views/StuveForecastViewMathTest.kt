package com.cloudbasepredictor.ui.screens.forecast.views

import com.cloudbasepredictor.domain.forecast.dryAdiabatTempC
import com.cloudbasepredictor.domain.forecast.moistAdiabatTempFromPointC
import com.cloudbasepredictor.domain.forecast.potentialTemperatureK
import com.cloudbasepredictor.ui.screens.forecast.StuveForecastChartUiModel
import com.cloudbasepredictor.ui.screens.forecast.StuveProfilePoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

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

    @Test
    fun layoutBottomAxisLabelCenters_keepsRightPackedLabelsInsideBoundsAndSeparated() {
        val centers = layoutBottomAxisLabelCenters(
            preferredCenters = listOf(170f, 170f, 170f),
            widths = listOf(40f, 40f, 70f),
            left = 0f,
            right = 180f,
            minimumGapPx = 10f,
        )

        assertEquals(3, centers.size)
        assertTrue(centers.first() - 20f >= 0f)
        assertTrue(centers.last() + 35f <= 180f)
        assertTrue(centers[1] - 20f >= centers[0] + 20f + 10f)
        assertTrue(centers[2] - 35f >= centers[1] + 20f + 10f)
    }

    @Test
    fun layoutBottomAxisLabelCenters_reducesGapWhenLabelsBarelyFit() {
        val centers = layoutBottomAxisLabelCenters(
            preferredCenters = listOf(90f, 90f, 90f),
            widths = listOf(50f, 50f, 50f),
            left = 0f,
            right = 160f,
            minimumGapPx = 12f,
        )

        assertEquals(3, centers.size)
        assertTrue(centers.first() - 25f >= 0f)
        assertTrue(centers.last() + 25f <= 160f)
        assertTrue(centers[1] - 25f >= centers[0] + 25f + 5f)
        assertTrue(centers[2] - 25f >= centers[1] + 25f + 5f)
    }

    @Test
    fun windBarbSpeedParts_roundsKmhToStandardFiveKnotSymbols() {
        assertEquals(
            WindBarbSpeedParts(
                roundedKnots = 5,
                flags = 0,
                fullFeathers = 0,
                halfFeathers = 1,
            ),
            windBarbSpeedParts(speedKmh = 10f),
        )
        assertEquals(
            WindBarbSpeedParts(
                roundedKnots = 15,
                flags = 0,
                fullFeathers = 1,
                halfFeathers = 1,
            ),
            windBarbSpeedParts(speedKmh = 28f),
        )
        assertEquals(
            WindBarbSpeedParts(
                roundedKnots = 50,
                flags = 1,
                fullFeathers = 0,
                halfFeathers = 0,
            ),
            windBarbSpeedParts(speedKmh = 93f),
        )
    }

    @Test
    fun buildWindBarbGeometry_pointsShaftEndTowardDirectionWindComesFrom() {
        val northWind = windBarbGeometry(directionDeg = 0f)
        assertTrue(northWind.shaft.end.y < northWind.shaft.start.y)

        val eastWind = windBarbGeometry(directionDeg = 90f)
        assertTrue(eastWind.shaft.end.x > eastWind.shaft.start.x)

        val southWind = windBarbGeometry(directionDeg = 180f)
        assertTrue(southWind.shaft.end.y > southWind.shaft.start.y)

        val westWind = windBarbGeometry(directionDeg = 270f)
        assertTrue(westWind.shaft.end.x < westWind.shaft.start.x)
    }

    @Test
    fun buildWindBarbGeometry_attachesFeathersAtWindSourceEnd() {
        val northWind = windBarbGeometry(directionDeg = 0f, speedKmh = 28f)

        val firstFeather = northWind.feathers.first()
        assertEquals(northWind.shaft.end.x, firstFeather.start.x, 0.01f)
        assertEquals(northWind.shaft.end.y, firstFeather.start.y, 0.01f)
        assertTrue(firstFeather.end.y > firstFeather.start.y)
        assertTrue(firstFeather.end.x > firstFeather.start.x)
    }

    @Test
    fun buildWindBarbGeometry_usesCalmCircleForSubFiveKnotWind() {
        val calmWind = windBarbGeometry(directionDeg = 0f, speedKmh = 3f)

        assertTrue(calmWind.calmRadius != null)
        assertTrue(calmWind.flags.isEmpty())
        assertTrue(calmWind.feathers.isEmpty())
    }

    private fun sampleChart(
        temperaturePoints: List<StuveProfilePoint>,
        dewpointPoints: List<StuveProfilePoint>,
    ) = StuveForecastChartUiModel(
        pressureLevels = listOf(850f, 800f, 700f, 600f, 500f, 400f, 300f, 250f),
        temperatureProfile = temperaturePoints,
        dewpointProfile = dewpointPoints,
        parcelAscentPath = temperaturePoints.map { it.copy(temperatureC = it.temperatureC + 1.5f) },
        windBarbs = emptyList(),
        cclPressureHpa = 760f,
        tconC = 18f,
        selectedHour = 12,
        surfacePressureHpa = 850f,
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

    private fun windBarbGeometry(
        directionDeg: Float,
        speedKmh: Float = 28f,
    ) = buildWindBarbGeometry(
        centerX = 50f,
        centerY = 50f,
        speedKmh = speedKmh,
        directionDeg = directionDeg,
        barbSize = 20f,
    )
}
