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

    private fun surfaceCursor(sample: WindSample?, hour: Int = 6) =
        WindCursor(hour, altitudeKm = 0.58f, sample, isSurface10m = true, x = 0f, y = 0f)

    private fun upperAirCursor(sample: WindSample?) =
        WindCursor(hour = 6, altitudeKm = 2.357f, sample, isSurface10m = false, x = 0f, y = 0f)
}
