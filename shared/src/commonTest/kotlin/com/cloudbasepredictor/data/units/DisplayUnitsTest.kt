package com.cloudbasepredictor.data.units

import kotlin.test.Test
import kotlin.test.assertEquals

class DisplayUnitsTest {
    @Test
    fun explicitPresetResolvesDisplayUnits() {
        val units = UnitPreset.METRIC_MPS.resolveDisplayUnits()

        assertEquals(WindSpeedUnit.MPS, units.windSpeed)
        assertEquals(AltitudeUnit.METERS, units.altitude)
        assertEquals(VerticalSpeedUnit.MPS, units.verticalSpeed)
    }

    @Test
    fun windSpeedFormattingConvertsFromKmh() {
        assertEquals(
            "36 km/h",
            formatWindSpeed(36f, UnitPreset.METRIC_KMH.resolveDisplayUnits()),
        )
        assertEquals(
            "10.0 m/s",
            formatWindSpeed(36f, UnitPreset.METRIC_MPS.resolveDisplayUnits()),
        )
        assertEquals(
            "22 mph",
            formatWindSpeed(36f, UnitPreset.IMPERIAL.resolveDisplayUnits()),
        )
        assertEquals(
            "19 kt",
            formatWindSpeed(36f, UnitPreset.AVIATION.resolveDisplayUnits()),
        )
    }

    @Test
    fun altitudeFormattingConvertsMetricAndImperial() {
        assertEquals(
            "900 m",
            formatAltitudeKm(0.9f, UnitPreset.METRIC_KMH.resolveDisplayUnits()),
        )
        assertEquals(
            "1.5 km",
            formatAltitudeKm(1.5f, UnitPreset.METRIC_KMH.resolveDisplayUnits()),
        )
        assertEquals(
            "4921 ft",
            formatAltitudeKm(1.5f, UnitPreset.IMPERIAL.resolveDisplayUnits()),
        )
        assertEquals(
            "11.5 kft",
            formatAltitudeKm(3.5f, UnitPreset.AVIATION.resolveDisplayUnits()),
        )
        assertEquals(
            "1.5km",
            formatAltitudeKm(
                1.5f,
                UnitPreset.METRIC_KMH.resolveDisplayUnits(),
                compact = true,
            ),
        )
        assertEquals(
            "11.5kft",
            formatAltitudeKm(
                3.5f,
                UnitPreset.AVIATION.resolveDisplayUnits(),
                compact = true,
            ),
        )
    }

    @Test
    fun verticalSpeedFormattingConvertsToFeetPerMinute() {
        assertEquals(
            "2.5 m/s",
            formatVerticalSpeed(2.5f, UnitPreset.METRIC_MPS.resolveDisplayUnits()),
        )
        assertEquals(
            "492 ft/min",
            formatVerticalSpeed(2.5f, UnitPreset.IMPERIAL.resolveDisplayUnits()),
        )
    }
}
