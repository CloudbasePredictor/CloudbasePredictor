package com.cloudbasepredictor.ui.screens.forecast

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertFalse
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
        assertTrue(sample.isInterpolated)
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
        assertFalse(sample.isInterpolated)
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
    fun windHourIndexAtX_selectsEachHourlyColumn() {
        val plotLeft = 60f
        val columnWidth = 30f

        assertEquals(0, windHourIndexAtX(75f, plotLeft, columnWidth, hourCount = 3))
        assertEquals(1, windHourIndexAtX(105f, plotLeft, columnWidth, hourCount = 3))
        assertEquals(2, windHourIndexAtX(135f, plotLeft, columnWidth, hourCount = 3))
    }

    @Test
    fun interpolateWind_neverExtrapolatesAndPreservesEndpoints() {
        val profile = listOf(WindSample(1.01f, 0f, 0f, isSurface10m = true), WindSample(3f, 25f, 350f))
        assertEquals(profile.first(), interpolateWind(profile, 1.01f))
        assertEquals(profile.last(), interpolateWind(profile, 3f))
        assertNull(interpolateWind(profile, 1f))
        assertNull(interpolateWind(profile, 3.001f))
        assertNull(interpolateWind(profile.take(1), 1.02f))
        assertNull(interpolateWind(emptyList(), 1f))
        assertNull(interpolateWind(profile, Float.NaN))
    }

    @Test
    fun pickReadableWindLevels_keepsLowerLevelAndDropsCrowdedOrClippedOnes() {
        // Lowest altitude first: 199 is below the plot area, 180/178 collide, 3 is above it.
        val anchors = listOf(199f, 180f, 178f, 150f, 140f, 100f, 3f)
        assertEquals(listOf(1, 3, 5), pickReadableWindLevels(anchors, minY = 8f, maxY = 190f, minSpacing = 20f))
        assertEquals(emptyList(), pickReadableWindLevels(emptyList(), 8f, 190f, 20f))
    }

    @Test
    fun windGlyphHourStride_keepsGlyphSlotsReadable() {
        assertEquals(1, windGlyphHourStride(columnWidth = 50f, minSlotWidth = 44f))
        assertEquals(1, windGlyphHourStride(columnWidth = 44f, minSlotWidth = 44f))
        assertEquals(2, windGlyphHourStride(columnWidth = 23f, minSlotWidth = 44f))
        assertEquals(3, windGlyphHourStride(columnWidth = 17f, minSlotWidth = 44f))
        assertEquals(1, windGlyphHourStride(columnWidth = 0f, minSlotWidth = 44f))
    }

    @Test
    fun buildWindProfiles_doesNotTurnMissingOrInvalidWindIntoCalm() {
        val profiles = buildWindProfiles(listOf(
            cell(6, 1f, Float.NaN), cell(7, 1f, -1f), cell(8, 1f, 0f),
        ))
        assertTrue(6 !in profiles && 7 !in profiles)
        assertEquals(0f, profiles.getValue(8).single().speedKmh)
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
