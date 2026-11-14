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
    fun interpolateWind_preservesEndpointsAndHoldsOutermostUpperAirLevel() {
        val surface = WindSample(1.01f, 0f, 0f, isSurface10m = true)
        val top = WindSample(3f, 25f, 350f)
        val profile = listOf(surface, top)
        assertEquals(surface, interpolateWind(profile, 1.01f))
        assertEquals(top, interpolateWind(profile, 3f))

        val above = assertNotNull(interpolateWind(profile, 4.5f))
        assertEquals(25f, above.speedKmh)
        assertEquals(350f, above.directionDeg)
        assertTrue(above.isInterpolated)
        assertEquals(4.5f, above.altitudeKm)

        // Without a 10 m value, the lowest level is held down to the terrain.
        val withoutSurface = listOf(WindSample(1.2f, 12f, 90f), top)
        val below = assertNotNull(interpolateWind(withoutSurface, 1f))
        assertEquals(12f, below.speedKmh)
        assertTrue(below.isInterpolated && !below.isSurface10m)

        // A profile with only a 10 m value is not stretched upward.
        assertNull(interpolateWind(profile.take(1), 1.02f))
        assertNull(interpolateWind(emptyList(), 1f))
        assertNull(interpolateWind(profile, Float.NaN))
    }

    @Test
    fun interpolateWind_holdsTenMetreValueDownToTerrainOnlyWithUpperAirLevels() {
        val surface = WindSample(1.01f, 8f, 350f, isSurface10m = true)

        // A profile with only a 10 m value is not stretched into the strip between terrain and 10 m.
        assertNull(interpolateWind(listOf(surface), 1.005f))
        assertNull(interpolateWind(listOf(surface), 1f))
        assertEquals(surface, interpolateWind(listOf(surface), 1.01f))

        // With upper-air levels the 10 m value is held down to the terrain, marked as not a source value.
        val below = assertNotNull(interpolateWind(listOf(surface, WindSample(2f, 20f, 270f)), 1.005f))
        assertEquals(8f, below.speedKmh)
        assertEquals(350f, below.directionDeg)
        assertEquals(1.005f, below.altitudeKm)
        assertTrue(below.isInterpolated && !below.isSurface10m)
    }

    @Test
    fun snapWindLevel_hitTestsDrawnGlyphsBeforeHiddenLevels() {
        // Like sparse 06h: 614 m (drawn) and 620 m (hidden) about 1 dp apart, 2357 m far above.
        val anchors = listOf(400f, 399f, 150f)
        val drawn = pickReadableWindLevels(anchors, 0f, 500f, glyphHalfHeight = 8f, minSpacing = 20f)
        assertEquals(listOf(ReadableWindLevel(0, 400f), ReadableWindLevel(2, 150f)), drawn)
        val plot = 0f..500f

        // Just above the drawn glyph's center, closer to the hidden level's true height.
        assertEquals(ReadableWindLevel(0, 400f), snapWindLevel(anchors, drawn, 398f, plot, snapDistance = 12f))
        // Just beyond the drawn glyph's reach the hidden level is still covered by that glyph.
        assertNull(snapWindLevel(anchors, drawn, 387.5f, plot, snapDistance = 12f))
        assertEquals(ReadableWindLevel(2, 150f), snapWindLevel(anchors, drawn, 160f, plot, snapDistance = 12f))
        assertNull(snapWindLevel(anchors, drawn, 300f, plot, snapDistance = 12f))
    }

    @Test
    fun snapWindLevel_followsShiftedEdgeGlyphsAndIgnoresLevelsOutsideThePlot() {
        // Lowest altitude first: 205 is below the plot, 199 hugs the bottom edge (drawn at 192), 178 is
        // hidden by the spacing but far enough from the drawn glyph to snap at its true height, 100 is
        // drawn, 3 hugs the top edge (drawn at 8), -5 is above the plot.
        val anchors = listOf(205f, 199f, 178f, 100f, 3f, -5f)
        val drawn = pickReadableWindLevels(anchors, 0f, 200f, glyphHalfHeight = 8f, minSpacing = 20f)
        assertEquals(listOf(ReadableWindLevel(1, 192f), ReadableWindLevel(3, 100f), ReadableWindLevel(4, 8f)), drawn)
        val plot = 0f..200f

        // The reticle sits on the shifted glyph, not on the level's true height or the level below the plot.
        assertEquals(ReadableWindLevel(1, 192f), snapWindLevel(anchors, drawn, 203f, plot, snapDistance = 12f))
        assertEquals(ReadableWindLevel(4, 8f), snapWindLevel(anchors, drawn, 0f, plot, snapDistance = 12f))
        // A hidden level away from drawn glyphs snaps at its true height.
        assertEquals(ReadableWindLevel(2, 178f), snapWindLevel(anchors, drawn, 172f, plot, snapDistance = 12f))

        // A level just above the visible top never snaps.
        val aboveTop = listOf(100f, -6f)
        val drawnBelow = pickReadableWindLevels(aboveTop, 0f, 200f, glyphHalfHeight = 8f, minSpacing = 20f)
        assertNull(snapWindLevel(aboveTop, drawnBelow, 4f, plot, snapDistance = 12f))
        // Without drawn glyphs (plot too short for them) in-plot levels still snap at their true height.
        assertEquals(ReadableWindLevel(1, 3f), snapWindLevel(listOf(-1f, 3f), emptyList(), 0f, 0f..10f, 12f))
    }

    @Test
    fun pickReadableWindLevels_keepsEdgeLevelsInsideThePlotAndLowerLevelOnCollision() {
        // Lowest altitude first: 205 is below the plot, 199/197 hug the bottom edge and collide,
        // 150 and 140 collide, 3 hugs the top edge, -2 is above the plot.
        val anchors = listOf(205f, 199f, 197f, 150f, 140f, 100f, 3f, -2f)
        assertEquals(
            listOf(
                ReadableWindLevel(1, 192f),
                ReadableWindLevel(3, 150f),
                ReadableWindLevel(5, 100f),
                ReadableWindLevel(6, 8f),
            ),
            pickReadableWindLevels(anchors, plotTop = 0f, plotBottom = 200f, glyphHalfHeight = 8f, minSpacing = 20f),
        )
        assertEquals(emptyList(), pickReadableWindLevels(emptyList(), 0f, 200f, 8f, 20f))
        assertEquals(emptyList(), pickReadableWindLevels(listOf(5f), 0f, 10f, 8f, 20f))
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
    fun windGlyphColumns_startAtFirstHourAndSkipTrailingColumnWithoutRoom() {
        // 360 dp phone with 17 hours: the 21h column (index 15) has no room for its pill, so it is skipped
        // while the columns keep the first-hour phase of the hour labels.
        assertEquals(listOf(0, 3, 6, 9, 12), windGlyphColumns(17, 300f / 17, minSlotWidth = 44f, rightExtent = 31f))
        assertEquals(listOf(0, 3, 6, 9, 12, 15), windHourLabelColumns(17, 300f / 17, minSlotWidth = 44f))
        // 420 dp: the last column has room.
        assertEquals(listOf(0, 3, 6, 9, 12, 15), windGlyphColumns(17, 360f / 17, minSlotWidth = 44f, rightExtent = 31f))
        // Every hour has a slot, but the last column is narrower than its pill needs.
        assertEquals((0..15).toList(), windGlyphColumns(17, 50f, minSlotWidth = 44f, rightExtent = 31f))
        // No column has room: the first one is drawn alone and flips its pill into the empty left side.
        assertEquals(listOf(0), windGlyphColumns(1, 40f, minSlotWidth = 44f, rightExtent = 31f))
        assertEquals(emptyList(), windGlyphColumns(0, 40f, minSlotWidth = 44f, rightExtent = 31f))
        assertEquals(emptyList(), windGlyphColumns(17, 0f, minSlotWidth = 44f, rightExtent = 31f))
        assertEquals(emptyList(), windHourLabelColumns(0, 40f, minSlotWidth = 44f))
    }

    @Test
    fun windGlyphColumns_keepsPillsOffNeighborsAndInsideTheRightEdgeAtAnyWidth() {
        // Mirrors the wind glyph in dp: a 16 dp arrow centered on its column, a 3 dp gap, the pill, 4 dp
        // clearance to the next arrow, and a 2 dp margin to the right edge. Pill widths cover narrow and
        // wide km/h, kt and m/s labels.
        val arrow = 16f
        val gap = 3f
        for (pill in listOf(14f, 17f, 23f, 26f, 32f)) {
            val slot = maxOf(44f, arrow + gap + pill + 4f)
            val rightExtent = arrow / 2f + gap + pill + 2f
            for (hourCount in 1..24) {
                for (plotWidth in 120..1600 step 5) {
                    val columnWidth = plotWidth.toFloat() / hourCount
                    val columns = windGlyphColumns(hourCount, columnWidth, slot, rightExtent)
                    val label = "pill $pill, $hourCount hours, $plotWidth dp: $columns"
                    assertTrue(columns.isNotEmpty(), label)
                    columns.zipWithNext().forEach { (left, right) ->
                        assertTrue((right - left) * columnWidth >= slot - 0.01f, "overlap, $label")
                    }
                    // Only a lone glyph may lack room on its right; it flips its pill to the empty left.
                    if (columns.size > 1) {
                        val roomOfLast = (hourCount - columns.last() - 0.5f) * columnWidth
                        assertTrue(roomOfLast >= rightExtent, "no room at the right edge, $label")
                    }
                }
            }
        }
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
