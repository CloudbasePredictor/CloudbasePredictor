package com.cloudbasepredictor.ui.screens.forecast.views

import com.cloudbasepredictor.data.units.UnitPreset
import com.cloudbasepredictor.data.units.resolveDisplayUnits
import com.cloudbasepredictor.ui.screens.forecast.WindSample
import kotlin.test.Test
import kotlin.test.assertEquals

class WindTooltipLinesTest {
    private val kmh = UnitPreset.METRIC_KMH.resolveDisplayUnits()

    @Test
    fun defaultLabelsKeepTheEnglishReadout() {
        val labels = WindChartLabels()
        assertEquals(
            listOf("06h  10 m AGL", "8 km/h  350°"),
            windTooltipLines(surfaceCursor(WindSample(0.59f, 8f, 350f, isSurface10m = true)), kmh, labels),
        )
        assertEquals(listOf("07h  10 m AGL", "No data"), windTooltipLines(surfaceCursor(null, hour = 7), kmh, labels))
        assertEquals(
            listOf("06h  2357 m ASL", "30 km/h  270°"),
            windTooltipLines(upperAirCursor(WindSample(2.357f, 30f, 270f)), kmh, labels),
        )
        assertEquals(
            listOf("06h  2357 m ASL", "≈ 30 km/h  270°"),
            windTooltipLines(upperAirCursor(WindSample(2.357f, 30f, 270f, isInterpolated = true)), kmh, labels),
        )
    }

    @Test
    fun platformLabelsReplaceTheHeightSuffixesAndNoData() {
        val labels = WindChartLabels(noData = "n/a", aboveSeaLevel = "MSL", aboveGround = "above ground")
        assertEquals(
            listOf("07h  10 m above ground", "n/a"),
            windTooltipLines(surfaceCursor(null, hour = 7), kmh, labels),
        )
        assertEquals(
            listOf("06h  2357 m MSL", "n/a"),
            windTooltipLines(upperAirCursor(null), kmh, labels),
        )
    }

    @Test
    fun wordsBeforeHeightPutTheHeightWordsFirst() {
        // The Chinese words, as the Android and web strings pass them.
        val labels = WindChartLabels(noData = "无数据", aboveSeaLevel = "海拔", aboveGround = "离地", wordsBeforeHeight = true)
        assertEquals(
            listOf("06h  离地 10 m", "8 km/h  350°"),
            windTooltipLines(surfaceCursor(WindSample(0.59f, 8f, 350f, isSurface10m = true)), kmh, labels),
        )
        assertEquals(
            listOf("06h  海拔 2357 m", "≈ 30 km/h  270°"),
            windTooltipLines(upperAirCursor(WindSample(2.357f, 30f, 270f, isInterpolated = true)), kmh, labels),
        )
        assertEquals(listOf("06h  海拔 2357 m", "无数据"), windTooltipLines(upperAirCursor(null), kmh, labels))
    }

    @Test
    fun altitudeAxisTitleFollowsTheWordOrderAndStacksOnTwoLines() {
        val english = WindChartLabels()
        val chinese = WindChartLabels(aboveSeaLevel = "海拔", wordsBeforeHeight = true)
        assertEquals("km ASL", windAltitudeAxisTitle(kmh, english))
        assertEquals("kft ASL", windAltitudeAxisTitle(UnitPreset.IMPERIAL.resolveDisplayUnits(), english))
        assertEquals("km\nASL", windAltitudeAxisTitle(kmh, english, separator = "\n"))
        assertEquals("km\ns.n.m.", windAltitudeAxisTitle(kmh, WindChartLabels(aboveSeaLevel = "s.n.m."), "\n"))
        assertEquals("海拔 km", windAltitudeAxisTitle(kmh, chinese))
        assertEquals("海拔\nkm", windAltitudeAxisTitle(kmh, chinese, separator = "\n"))
    }

    @Test
    fun tooltipStaysBesideTheReticleAndInsideTheCanvas() {
        // A 360 px canvas with a 60 px altitude axis and 4 px margins; the reticle with its gap spans 18 px to each
        // side of its center.
        val plot = 60f..360f
        fun left(reticleX: Float, width: Float) =
            windTooltipLeft(beside = (reticleX - 18f)..(reticleX + 18f), width = width, plot = plot, margin = 4f)

        // Right of the reticle where it fits the plot, otherwise left of it.
        assertEquals(118f, left(reticleX = 100f, width = 200f))
        assertEquals(182f, left(reticleX = 300f, width = 100f))
        // Left of the reticle it stops at the altitude axis while the plot has room.
        assertEquals(64f, left(reticleX = 100f, width = 250f))
        // Too wide for the plot from there, it moves over the axis column so that its right edge stays inside.
        assertEquals(36f, left(reticleX = 100f, width = 320f))
        // Wider than the canvas, which a wrapped readout never is, it starts at the margin.
        assertEquals(4f, left(reticleX = 100f, width = 400f))
    }

    private fun surfaceCursor(sample: WindSample?, hour: Int = 6) =
        WindCursor(hour, altitudeKm = 0.58f, sample, isSurface10m = true, x = 0f, y = 0f)

    private fun upperAirCursor(sample: WindSample?) =
        WindCursor(hour = 6, altitudeKm = 2.357f, sample, isSurface10m = false, x = 0f, y = 0f)
}
