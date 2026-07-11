package com.cloudbasepredictor.model

import kotlin.test.Test
import kotlin.test.assertEquals

class WeatherCodeTest {
    @Test
    fun present_returnsClearSkyForCodeZero() {
        val presentation = WeatherCode.present(0)

        assertEquals("Clear sky", presentation.label)
        assertEquals("Clear", presentation.shortLabel)
    }

    @Test
    fun present_returnsCloudyForGroupedCloudCodes() {
        val presentation = WeatherCode.present(2)

        assertEquals("Partly cloudy", presentation.label)
        assertEquals("Cloudy", presentation.shortLabel)
    }

    @Test
    fun present_returnsUnknownForUnsupportedCode() {
        val presentation = WeatherCode.present(777)

        assertEquals("Unknown weather", presentation.label)
        assertEquals("Unknown", presentation.shortLabel)
    }
}
