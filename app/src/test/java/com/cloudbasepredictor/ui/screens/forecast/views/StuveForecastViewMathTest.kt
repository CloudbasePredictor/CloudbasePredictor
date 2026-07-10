package com.cloudbasepredictor.ui.screens.forecast.views

import com.cloudbasepredictor.domain.forecast.dryAdiabatTempC
import com.cloudbasepredictor.domain.forecast.moistAdiabatTempFromPointC
import com.cloudbasepredictor.domain.forecast.potentialTemperatureK
import com.cloudbasepredictor.ui.screens.forecast.StuveForecastChartUiModel
import com.cloudbasepredictor.ui.screens.forecast.StuveProfilePoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StuveForecastViewMathTest {

    @Test
    fun buildSkewTTemperatureAxisRange_sameVisibleEnvelope_isIndependentOfVerticalSpan() {
        val pressures = listOf(1000f, 900f, 800f, 700f, 600f, 500f)
        val chart = sampleChart(
            temperaturePoints = pressures.map { pressure -> point(pressure, 20f) },
            dewpointPoints = pressures.map { pressure -> point(pressure, 10f) },
            parcelPoints = pressures.map { pressure -> point(pressure, 26f) },
            surfacePressureHpa = 1000f,
        )

        val fullColumnRange = buildSkewTTemperatureAxisRange(
            chart = chart,
            topPressure = 500f,
            bottomPressure = 1000f,
        )
        val nearSurfaceRange = buildSkewTTemperatureAxisRange(
            chart = chart,
            topPressure = 800f,
            bottomPressure = 1000f,
        )

        assertEquals(fullColumnRange.minC, nearSurfaceRange.minC, 0.01f)
        assertEquals(fullColumnRange.maxC, nearSurfaceRange.maxC, 0.01f)
    }

    @Test
    fun buildSkewTTemperatureAxisRange_ignoresPointsAboveVisibleTop() {
        val sharedTemperatures = listOf(
            point(1000f, 20f),
            point(900f, 15f),
            point(800f, 10f),
        )
        val sharedDewpoints = listOf(
            point(1000f, 10f),
            point(900f, 5f),
            point(800f, 0f),
        )
        val referenceChart = sampleChart(
            temperaturePoints = sharedTemperatures + listOf(point(700f, 5f), point(500f, -10f)),
            dewpointPoints = sharedDewpoints + listOf(point(700f, -5f), point(500f, -20f)),
            surfacePressureHpa = 1000f,
        )
        val chartWithHiddenOutliers = sampleChart(
            temperaturePoints = sharedTemperatures + listOf(point(700f, 80f), point(500f, 120f)),
            dewpointPoints = sharedDewpoints + listOf(point(700f, -80f), point(500f, -120f)),
            surfacePressureHpa = 1000f,
        )

        val referenceRange = buildSkewTTemperatureAxisRange(
            chart = referenceChart,
            topPressure = 800f,
            bottomPressure = 1000f,
        )
        val outlierRange = buildSkewTTemperatureAxisRange(
            chart = chartWithHiddenOutliers,
            topPressure = 800f,
            bottomPressure = 1000f,
        )

        assertEquals(referenceRange.minC, outlierRange.minC, 0.01f)
        assertEquals(referenceRange.maxC, outlierRange.maxC, 0.01f)
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
    fun buildSkewTProjection_fitsVisibleProfilesAndUsesHorizontalSpaceAcrossZoom() {
        val chart = sampleChart(
            temperaturePoints = listOf(
                point(1000f, 25f, 120f),
                point(900f, 18f, 1000f),
                point(800f, 10f, 1950f),
                point(700f, 0f, 3010f),
                point(500f, -18f, 5570f),
            ),
            dewpointPoints = listOf(
                point(1000f, 5f, 120f),
                point(900f, -2f, 1000f),
                point(800f, -10f, 1950f),
                point(700f, -20f, 3010f),
                point(500f, -35f, 5570f),
            ),
            parcelPoints = listOf(
                point(1000f, 35f, 120f),
                point(900f, 28f, 1000f),
                point(800f, 20f, 1950f),
                point(700f, 10f, 3010f),
                point(500f, -5f, 5570f),
            ),
            surfacePressureHpa = 1000f,
        )
        val bottomPressure = 1000f
        val plotLeft = 40f
        val plotRight = 340f

        listOf(500f, 800f).forEach { topPressure ->
            val visiblePoints = listOf(
                chart.temperatureProfile,
                chart.dewpointProfile,
                chart.parcelAscentPath,
            ).flatten().filter { point -> point.pressureHpa in topPressure..bottomPressure }
            buildSkewTProjection(
                chart = chart,
                topPressure = topPressure,
                bottomPressure = bottomPressure,
                plotLeft = plotLeft,
                plotRight = plotRight,
                plotTop = 16f,
                plotBottom = 616f,
            )
                .also { projection ->
                    val visibleXs = visiblePoints.map { point ->
                        projection.temperatureToX(point.temperatureC, point.pressureHpa)
                    }
                    val plotWidth = plotRight - plotLeft

                    visibleXs.forEach { x ->
                        assertTrue("Visible profile point must stay inside the plot: x=$x", x in plotLeft..plotRight)
                    }
                    assertTrue(
                        "Visible profiles should reach the left fit padding",
                        visibleXs.min() <= plotLeft + plotWidth * 0.10f,
                    )
                    assertTrue(
                        "Visible profiles should reach the right fit padding",
                        visibleXs.max() >= plotRight - plotWidth * 0.10f,
                    )
                }
        }
    }

    @Test
    fun buildSkewTProjection_narrowVisibleProfilesStillUseHorizontalSpace() {
        val chart = sampleChart(
            temperaturePoints = listOf(point(1000f, 20f), point(800f, 8f)),
            dewpointPoints = listOf(point(1000f, 19f), point(800f, 7f)),
            parcelPoints = listOf(point(1000f, 22f), point(800f, 10f)),
            surfacePressureHpa = 1000f,
        )
        val projection = buildSkewTProjection(
            chart = chart,
            topPressure = 800f,
            bottomPressure = 1000f,
            plotLeft = 40f,
            plotRight = 340f,
            plotTop = 16f,
            plotBottom = 616f,
        )
        val xs = listOf(
            chart.temperatureProfile,
            chart.dewpointProfile,
            chart.parcelAscentPath,
        ).flatten().map { point ->
            projection.temperatureToX(point.temperatureC, point.pressureHpa)
        }

        assertTrue(xs.min() <= projection.plotLeft + projection.plotWidth * 0.10f)
        assertTrue(xs.max() >= projection.plotRight - projection.plotWidth * 0.10f)
    }

    @Test
    fun buildSkewTProjection_fitsInterpolatedViewportEdgesWhenNoSamplesAreInside() {
        val chart = sampleChart(
            temperaturePoints = listOf(point(1000f, 100f), point(800f, -100f)),
            dewpointPoints = listOf(point(1000f, 80f), point(800f, -120f)),
            parcelPoints = listOf(point(1000f, 120f), point(800f, -80f)),
            surfacePressureHpa = 1000f,
        )
        val topPressure = 850f
        val bottomPressure = 950f
        val projection = buildSkewTProjection(
            chart = chart,
            topPressure = topPressure,
            bottomPressure = bottomPressure,
            plotLeft = 40f,
            plotRight = 340f,
            plotTop = 16f,
            plotBottom = 616f,
        )

        listOf(chart.temperatureProfile, chart.dewpointProfile, chart.parcelAscentPath).forEach { profile ->
            assertTrue(profile.none { point -> point.pressureHpa in topPressure..bottomPressure })
            listOf(topPressure, bottomPressure).forEach { pressure ->
                val start = profile.first()
                val end = profile.last()
                val startY = projection.pressureToY(start.pressureHpa)
                val endY = projection.pressureToY(end.pressureHpa)
                val boundaryY = projection.pressureToY(pressure)
                val segmentFraction = (boundaryY - startY) / (endY - startY)
                val x = projection.temperatureToX(start.temperatureC, start.pressureHpa) +
                    segmentFraction *
                    (projection.temperatureToX(end.temperatureC, end.pressureHpa) -
                        projection.temperatureToX(start.temperatureC, start.pressureHpa))
                assertTrue("Interpolated viewport edge must stay inside the plot: x=$x", x in 40f..340f)
            }
        }
        assertTrue(
            segmentIntersectsVerticalRange(
                startY = projection.pressureToY(1000f),
                endY = projection.pressureToY(800f),
                plotTop = projection.plotTop,
                plotBottom = projection.plotBottom,
            ),
        )
    }

    @Test
    fun buildSkewTTemperatureAxisRange_emptyProfilesUsesFiniteFallback() {
        val chart = sampleChart(
            temperaturePoints = emptyList(),
            dewpointPoints = emptyList(),
            parcelPoints = emptyList(),
        )

        val range = buildSkewTTemperatureAxisRange(
            chart = chart,
            topPressure = 500f,
            bottomPressure = 1000f,
        )

        assertTrue(range.minC.isFinite())
        assertTrue(range.maxC.isFinite())
        assertTrue(range.spanC > 0f)
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
        parcelPoints: List<StuveProfilePoint> = temperaturePoints.map {
            it.copy(temperatureC = it.temperatureC + 1.5f)
        },
        surfacePressureHpa: Float = 850f,
    ) = StuveForecastChartUiModel(
        pressureLevels = listOf(850f, 800f, 700f, 600f, 500f, 400f, 300f, 250f),
        temperatureProfile = temperaturePoints,
        dewpointProfile = dewpointPoints,
        parcelAscentPath = parcelPoints,
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
