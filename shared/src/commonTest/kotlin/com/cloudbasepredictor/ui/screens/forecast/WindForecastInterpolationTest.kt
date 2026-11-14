package com.cloudbasepredictor.ui.screens.forecast

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
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
    fun pickReadableWindLevels_spacesStackedPillsAndFlipsThemBelowNearTheTop() {
        // Pills 14 tall stacked 18 above their arrows reach 17 past the 8 glyph half height: 37 between glyphs with
        // their pills on the same side, 54 where an upper glyph flips its pill below toward a lower one's pill above.
        val stack = WindPillStack(offset = 18f, height = 14f)
        val anchors = listOf(195f, 165f, 150f, 70f, 12f)
        assertEquals(
            listOf(
                ReadableWindLevel(0, drawY = 192f, pillCenterY = 174f),
                ReadableWindLevel(2, drawY = 150f, pillCenterY = 132f),
                ReadableWindLevel(3, drawY = 70f, pillCenterY = 52f),
                // No room above inside the plot: the pill goes below its arrow.
                ReadableWindLevel(4, drawY = 12f, pillCenterY = 30f),
            ),
            pickReadableWindLevels(anchors, 0f, 200f, glyphHalfHeight = 8f, minSpacing = 20f, stackedPill = stack),
        )
        // Beside their arrows the plain spacing keeps the 165 level 27 above the lowest one instead.
        assertEquals(
            listOf(0, 1, 3, 4),
            pickReadableWindLevels(anchors, 0f, 200f, glyphHalfHeight = 8f, minSpacing = 20f).map { it.index },
        )
        // 50 apart is enough beside the arrows or with both pills above, but not once the upper pill flips below.
        assertEquals(
            listOf(ReadableWindLevel(0, drawY = 70f, pillCenterY = 52f)),
            pickReadableWindLevels(listOf(70f, 20f), 0f, 200f, 8f, 20f, stack),
        )
        // A pill that fits neither above nor below its arrow drops the level.
        assertEquals(emptyList(), pickReadableWindLevels(listOf(20f), 0f, 40f, 8f, 20f, stack))
    }

    @Test
    fun snapWindLevel_hitsStackedPillsAndKeepsHiddenLevelsUnderThemUnsnapped() {
        val stack = WindPillStack(offset = 18f, height = 14f)
        // The 128 level is hidden by the glyph at 150, whose pill sits at 132.
        val anchors = listOf(150f, 128f)
        val drawn = pickReadableWindLevels(anchors, 0f, 200f, glyphHalfHeight = 8f, minSpacing = 20f, stack)
        assertEquals(listOf(ReadableWindLevel(0, drawY = 150f, pillCenterY = 132f)), drawn)
        val plot = 0f..200f

        // A tap on the pill selects its glyph, and the reticle sits on the arrow.
        assertEquals(drawn.single(), snapWindLevel(anchors, drawn, 126f, plot, snapDistance = 12f))
        // Beyond the pill's reach the hidden level under it still does not snap.
        assertNull(snapWindLevel(anchors, drawn, 116f, plot, snapDistance = 12f))
    }

    @Test
    fun windGlyphColumns_stackTrailingPillsWithoutRoomOnTheRight() {
        val kmh = WindGlyphMetrics(16f, 3f, pillWidth = 18f, clearance = 4f, edgeMargin = 2f, minSlotWidth = 44f)
        // 360 dp phone with 17 hours: the 21h column (index 15) has no room right of its arrows, so it stacks its
        // pills between the 18h pills and the right edge, keeping the first-hour phase of the hour labels.
        val phone = windGlyphColumns(17, plotLeft = 60f, columnWidth = 300f / 17, kmh)
        assertEquals(listOf(0, 3, 6, 9, 12, 15), phone.map { it.index })
        assertEquals(listOf(0, 3, 6, 9, 12, 15), windHourLabelColumns(17, 300f / 17, kmh.slotWidth))
        assertTrue(phone.dropLast(1).all { it.pills == WindPillPlacement.BESIDE })
        val trailing = phone.last()
        assertEquals(WindPillPlacement.STACKED, trailing.pills)
        // From the 18h center (280.6) past its 29 pill reach and the 4 clearance, to the 2 margin at the right edge.
        assertEquals(313.6f, trailing.pillSpan.start, absoluteTolerance = 0.05f)
        assertEquals(358f, trailing.pillSpan.endInclusive, absoluteTolerance = 0.001f)
        // The stacked pill stays centered on its column (333.5); the one-line 10 m glyph moves its arrow 4.5 left
        // so that its pill ends at the margin.
        val center = 60f + 15.5f * 300f / 17
        assertEquals(center - 9f, trailing.stackedPillLeft(center, 18f), absoluteTolerance = 0.001f)
        assertEquals(329f, trailing.lineArrowX(center, 16f, 3f, 18f), absoluteTolerance = 0.001f)

        // 420 dp: the last column has room on the right.
        assertTrue(windGlyphColumns(17, 0f, 360f / 17, kmh).all { it.pills == WindPillPlacement.BESIDE })
        // Every hour has a slot, but the last column is narrower than its pill needs.
        val wide = windGlyphColumns(17, 0f, 50f, kmh)
        assertEquals((0..16).toList(), wide.map { it.index })
        assertEquals(WindGlyphColumn(16, WindPillPlacement.STACKED, 808f..848f), wide.last())
        // A lone column without room on the right stacks its pills inside the plot.
        assertEquals(WindGlyphColumn(0, WindPillPlacement.STACKED, 2f..38f), windGlyphColumns(1, 0f, 40f, kmh).single())
        // Wide m/s pills at 24.5 per hour: not even one fits between the 20h pills and the right edge, so the 22h
        // column draws its arrows alone instead of being skipped.
        val mps = windGlyphColumns(17, 0f, 24.5f, kmh.copy(pillWidth = 26f))
        assertEquals((0..16 step 2).toList(), mps.map { it.index })
        assertEquals(WindPillPlacement.NONE, mps.last().pills)
        assertEquals(emptyList(), windGlyphColumns(0, 0f, 40f, kmh))
        assertEquals(emptyList(), windGlyphColumns(17, 0f, 0f, kmh))
        assertEquals(emptyList(), windHourLabelColumns(0, 40f, minSlotWidth = 44f))
    }

    @Test
    fun windGlyphColumn_keepsStackedPillsAndOneLineGlyphsInsideTheirSpan() {
        val column = WindGlyphColumn(16, WindPillPlacement.STACKED, 50f..100f)
        assertEquals(66f, column.stackedPillLeft(arrowX = 75f, width = 18f))
        assertEquals(82f, column.stackedPillLeft(arrowX = 95f, width = 18f))
        assertEquals(50f, column.stackedPillLeft(arrowX = 52f, width = 18f))
        // A line with room keeps its arrow on the column center, otherwise its pill ends at the span end.
        assertEquals(60f, column.lineArrowX(arrowX = 60f, arrowLength = 16f, gap = 3f, pillWidth = 18f))
        assertEquals(71f, column.lineArrowX(arrowX = 95f, arrowLength = 16f, gap = 3f, pillWidth = 18f))
        // A shortened arrow never starts before the span.
        assertEquals(54f, column.lineArrowX(arrowX = 52f, arrowLength = 8f, gap = 3f, pillWidth = 18f))
    }

    @Test
    fun windGlyphColumns_keepsGlyphFootprintsApartAndPillsInsideTheRightEdgeAtAnyWidth() {
        // Mirrors the wind glyph in dp: a 16 dp arrow centered on its column, a 3 dp gap, the pill, 4 dp clearance
        // to the next glyph, and a 2 dp margin to the right edge. Pill widths cover narrow and wide km/h, kt and m/s
        // labels and large font scales.
        for (pill in listOf(14f, 17f, 23f, 26f, 32f, 48f)) {
            val metrics = WindGlyphMetrics(16f, 3f, pill, clearance = 4f, edgeMargin = 2f, minSlotWidth = 44f)
            for (hourCount in 1..24) {
                for (plotWidth in 120..1600 step 5) {
                    assertGlyphColumnsFit(hourCount, plotWidth.toFloat(), metrics)
                }
            }
        }
    }

    private fun assertGlyphColumnsFit(hourCount: Int, plotWidth: Float, metrics: WindGlyphMetrics) {
        val columnWidth = plotWidth / hourCount
        val columns = windGlyphColumns(hourCount, plotLeft = 0f, columnWidth, metrics)
        val label = "pill ${metrics.pillWidth}, $hourCount hours, $plotWidth dp: $columns"
        // The trailing column is never skipped: glyphs and hour labels share their columns.
        assertEquals(windHourLabelColumns(hourCount, columnWidth, metrics.slotWidth), columns.map { it.index }, label)
        columns.map { glyphFootprint(it, columnWidth, metrics) }.zipWithNext().forEach { (left, right) ->
            assertTrue(right.start >= left.endInclusive + metrics.clearance - 0.01f, "overlap, $label")
        }
        columns.mapNotNull { pillFootprint(it, columnWidth, metrics) }.forEach { pill ->
            assertTrue(pill.start >= metrics.edgeMargin - 0.01f, "past the plot left, $label")
            assertTrue(pill.endInclusive <= plotWidth - metrics.edgeMargin + 0.01f, "past the right edge, $label")
        }
    }

    @Test
    fun windGlyphColumns_keepTrailingPillsForTwoDigitSpeedsOnPhonesAndWider() {
        // Two-digit km/h or kt pills over the 6–22h day: from 250 dp plots (310 dp screens) up, the trailing column
        // always has room for its pills, beside or stacked.
        for (pill in listOf(14f, 18f)) {
            val metrics = WindGlyphMetrics(16f, 3f, pill, clearance = 4f, edgeMargin = 2f, minSlotWidth = 44f)
            for (plotWidth in 250..1600 step 5) {
                val trailing = windGlyphColumns(17, 0f, plotWidth / 17f, metrics).last()
                assertTrue(trailing.pills != WindPillPlacement.NONE, "pill $pill, $plotWidth dp: $trailing")
            }
        }
    }

    @Test
    fun windCursorArea_containsItsEdgesAndKeepsDragsLeavingItOnTheNearestEdge() {
        // A 360 dp phone at 1x: the plot from the 60 dp altitude axis to the right edge, the 10 m row ending at 570.
        val area = WindCursorArea(left = 60f, top = 0f, right = 360f, bottom = 570f)
        assertTrue(area.contains(60f, 0f) && area.contains(360f, 570f) && area.contains(200f, 300f))
        assertFalse(area.contains(59f, 300f), "altitude axis")
        assertFalse(area.contains(200f, 571f), "legend")

        // A drag from the 10 m row into the legend stays on the row's bottom edge, one left of the first hour on the
        // plot's left edge, and one above the plot on its top edge; inside the area nothing moves.
        assertEquals(570f, area.clampY(600f))
        assertEquals(60f, area.clampX(20f))
        assertEquals(0f, area.clampY(-30f))
        assertEquals(360f, area.clampX(400f))
        assertEquals(200f, area.clampX(200f))
        assertEquals(300f, area.clampY(300f))

        // A canvas too small for the area contains nothing and clamps onto the area's start instead of failing.
        val collapsed = WindCursorArea(left = 60f, top = 0f, right = 40f, bottom = -10f)
        assertFalse(collapsed.contains(50f, 0f))
        assertEquals(60f, collapsed.clampX(100f))
        assertEquals(0f, collapsed.clampY(5f))
    }

    @Test
    fun windLegendLabelCenters_keepsLabelsThatFitAndThinsTheRestEvenly() {
        // The legend of a phone: nine items from the 60 dp axis to the CCL and 0 °C keys, 88 dp from the right edge,
        // and 9 sp monospace labels (5.4 dp per character at 1x) kept 3 dp (WIND_LEGEND_LABEL_GAP) clear of each
        // other, of the unit label at 8 dp and of the first key's label 6 dp into its slot.
        fun itemWidth(widthDp: Float) = (widthDp - 88f - 60f) / 9
        fun itemCenters(widthDp: Float) = List(9) { 60f + (it + 0.5f) * itemWidth(widthDp) }
        fun everyLabel(widthDp: Float) = itemCenters(widthDp).withIndex().associate { it.index to it.value }
        fun legend(characters: List<Int>, unitCharacters: Int, fontScale: Float, widthDp: Float = 360f) =
            windLegendLabelCenters(
                labelWidths = characters.map { it * 5.4f * fontScale },
                left = 60f,
                itemWidth = itemWidth(widthDp),
                bounds = (8f + unitCharacters * 5.4f * fontScale + 3f)..(widthDp - 88f + 6f - 3f),
                gap = 3f,
            )
        val kmh = listOf(1, 1, 2, 2, 2, 2, 2, 2, 2)
        // "0.0" to "8.3", then "11.1", "13.9" and "16.7".
        val mps = listOf(3, 3, 3, 3, 3, 3, 4, 4, 4)

        // Every km/h label keeps its place under its swatch, at least 12 dp from the next, at 1x on a 360 dp and a
        // 411 dp phone; so it does up to 1.5x, and so do m/s labels at 1x on the wider phone.
        assertLegendCenters(everyLabel(360f), legend(kmh, unitCharacters = 4, fontScale = 1f))
        assertLegendCenters(everyLabel(411f), legend(kmh, unitCharacters = 4, fontScale = 1f, widthDp = 411f))
        assertLegendCenters(everyLabel(360f), legend(kmh, unitCharacters = 4, fontScale = 1.5f))
        assertLegendCenters(everyLabel(411f), legend(mps, unitCharacters = 3, fontScale = 1f, widthDp = 411f))
        // On the 360 dp phone the four-character m/s labels would be 2 dp apart: every second label is drawn, still
        // under its swatch, from 1x on. So it is with km/h labels at 2x.
        val everySecond = (0..8 step 2).associateWith { itemCenters(360f)[it] }
        assertLegendCenters(everySecond, legend(mps, unitCharacters = 3, fontScale = 1f))
        assertLegendCenters(everySecond, legend(mps, unitCharacters = 3, fontScale = 1.1f))
        assertLegendCenters(everySecond, legend(kmh, unitCharacters = 4, fontScale = 2f))
        // At 2x the last m/s label, kept clear of the CCL key, would crowd the one before it, so every third is drawn.
        assertLegendCenters(
            (0..8 step 3).associateWith { itemCenters(360f)[it] },
            legend(mps, unitCharacters = 3, fontScale = 2f),
        )

        // A label crossing the unit label is moved right of it, which leaves no room for the next label.
        assertLegendCenters(
            mapOf(0 to 25f, 2 to 50f),
            windLegendLabelCenters(listOf(20f, 4f, 4f), left = 0f, itemWidth = 20f, bounds = 15f..60f, gap = 1f),
        )
        // A label wider than the room still shows, from the start of the room.
        assertLegendCenters(
            mapOf(0 to 50f),
            windLegendLabelCenters(listOf(100f), left = 0f, itemWidth = 20f, bounds = 0f..50f, gap = 1f),
        )
        assertEquals(emptyMap(), windLegendLabelCenters(emptyList(), 0f, 20f, 0f..50f, 1f))
    }

    private fun assertLegendCenters(expected: Map<Int, Float>, actual: Map<Int, Float>) {
        assertEquals(expected.keys.toList(), actual.keys.toList(), "drawn labels")
        expected.forEach { (index, center) ->
            assertEquals(center, actual.getValue(index), absoluteTolerance = 0.01f, "label $index")
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

    /** Horizontal extent of a column's glyphs: its arrow and its widest pill, wherever the column puts it. */
    private fun glyphFootprint(
        column: WindGlyphColumn,
        columnWidth: Float,
        metrics: WindGlyphMetrics,
    ): ClosedFloatingPointRange<Float> {
        val center = (column.index + 0.5f) * columnWidth
        val arrowLeft = center - metrics.arrowLength / 2f
        val arrowRight = center + metrics.arrowLength / 2f
        val pill = pillFootprint(column, columnWidth, metrics) ?: return arrowLeft..arrowRight
        return min(arrowLeft, pill.start)..max(arrowRight, pill.endInclusive)
    }

    private fun pillFootprint(
        column: WindGlyphColumn,
        columnWidth: Float,
        metrics: WindGlyphMetrics,
    ): ClosedFloatingPointRange<Float>? {
        val center = (column.index + 0.5f) * columnWidth
        val left = when (column.pills) {
            WindPillPlacement.BESIDE -> center + metrics.besideReach - metrics.pillWidth
            WindPillPlacement.STACKED -> column.stackedPillLeft(center, metrics.pillWidth)
            WindPillPlacement.NONE -> return null
        }
        return left..(left + metrics.pillWidth)
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
