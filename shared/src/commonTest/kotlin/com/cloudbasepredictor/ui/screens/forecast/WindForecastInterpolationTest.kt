package com.cloudbasepredictor.ui.screens.forecast

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class WindForecastInterpolationTest {

    @Test
    fun interpolateWind_interpolatesSpeedAtRequestedAltitude() {
        val sample = interpolateWind(
            profile = listOf(
                WindSample(altitudeKm = 2f, speedKmh = 10f, directionDeg = 270f),
                WindSample(altitudeKm = 4f, speedKmh = 30f, directionDeg = 270f),
            ),
            targetAltitudeKm = 3f,
        )

        assertNotNull(sample)
        assertEquals(3f, sample.altitudeKm, absoluteTolerance = 0.001f)
        assertEquals(20f, sample.speedKmh, absoluteTolerance = 0.001f)
        assertEquals(270f, sample.directionDeg, absoluteTolerance = 0.001f)
    }

    @Test
    fun interpolateWind_preservesDirectModelValueAtItsAltitude() {
        val sample = interpolateWind(
            profile = listOf(
                WindSample(altitudeKm = 2f, speedKmh = 10f, directionDeg = 250f),
                WindSample(altitudeKm = 3f, speedKmh = 23f, directionDeg = 315f),
                WindSample(altitudeKm = 4f, speedKmh = 30f, directionDeg = 20f),
            ),
            targetAltitudeKm = 3f,
        )

        assertNotNull(sample)
        assertEquals(3f, sample.altitudeKm, absoluteTolerance = 0.001f)
        assertEquals(23f, sample.speedKmh, absoluteTolerance = 0.001f)
        assertEquals(315f, sample.directionDeg, absoluteTolerance = 0.001f)
    }

    @Test
    fun interpolateWind_wrapsDirectionThroughNorth() {
        val sample = interpolateWind(
            profile = listOf(
                WindSample(altitudeKm = 2f, speedKmh = 10f, directionDeg = 350f),
                WindSample(altitudeKm = 4f, speedKmh = 10f, directionDeg = 10f),
            ),
            targetAltitudeKm = 3f,
        )

        assertNotNull(sample)
        assertTrue(
            abs(sample.directionDeg) < 0.001f || abs(sample.directionDeg - 360f) < 0.001f,
            "Direction should interpolate through north, but was ${sample.directionDeg}",
        )
    }

    @Test
    fun buildWindProfiles_keepsHourlyValuesIndependent() {
        val profiles = buildWindProfiles(
            cells = listOf(
                cell(hour = 12, altitudeKm = 2f, speedKmh = 10f),
                cell(hour = 12, altitudeKm = 4f, speedKmh = 30f),
                cell(hour = 13, altitudeKm = 2f, speedKmh = 20f),
                cell(hour = 13, altitudeKm = 4f, speedKmh = 40f),
            ),
        )

        val hour12 = interpolateWind(profiles.getValue(12), targetAltitudeKm = 3f)
        val hour13 = interpolateWind(profiles.getValue(13), targetAltitudeKm = 3f)

        assertNotNull(hour12)
        assertNotNull(hour13)
        assertEquals(20f, hour12.speedKmh, absoluteTolerance = 0.001f)
        assertEquals(30f, hour13.speedKmh, absoluteTolerance = 0.001f)
    }

    @Test
    fun windHourIndexAtX_splitsVisualClusterIntoHourlyColumns() {
        val plotLeft = 60f
        val columnWidth = 30f

        assertEquals(0, windHourIndexAtX(75f, plotLeft, columnWidth, hourCount = 3))
        assertEquals(1, windHourIndexAtX(105f, plotLeft, columnWidth, hourCount = 3))
        assertEquals(2, windHourIndexAtX(135f, plotLeft, columnWidth, hourCount = 3))
    }

    @Test
    fun buildWindHourClusters_usesCenterHourWithoutAveraging() {
        val clusters = buildWindHourClusters(hourCount = 6, clusterSize = 3)

        assertEquals(
            listOf(
                WindHourCluster(startIndex = 0, endIndexExclusive = 3, representativeIndex = 1),
                WindHourCluster(startIndex = 3, endIndexExclusive = 6, representativeIndex = 4),
            ),
            clusters,
        )
        assertEquals(listOf(1.5f, 4.5f), clusters.map { it.centerColumn })
    }

    private fun cell(
        hour: Int,
        altitudeKm: Float,
        speedKmh: Float,
    ): WindForecastCellUiModel {
        return WindForecastCellUiModel(
            hour = hour,
            altitudeKm = altitudeKm,
            speedKmh = speedKmh,
            directionDeg = 270f,
        )
    }
}
