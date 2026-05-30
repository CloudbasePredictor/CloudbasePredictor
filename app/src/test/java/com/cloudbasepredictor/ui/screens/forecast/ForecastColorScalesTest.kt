package com.cloudbasepredictor.ui.screens.forecast

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ForecastColorScalesTest {
    @Test
    fun windSpeedColor_usesRedAtMaximumScaleEdge() {
        val maximum = windSpeedColor(WIND_SPEED_COLOR_SCALE_MAX_KMH)

        assertEquals(Color(0xFFD32F2F).toArgb(), maximum.toArgb())
        assertEquals(maximum.toArgb(), windSpeedColor(WIND_SPEED_COLOR_SCALE_MAX_KMH + 20f).toArgb())
    }

    @Test
    fun windSpeedColor_passesThroughGreen() {
        val moderateWind = windSpeedColor(20f)

        assertTrue(moderateWind.greenChannel() > moderateWind.redChannel())
        assertTrue(moderateWind.greenChannel() > moderateWind.blueChannel())
    }

    @Test
    fun thermicStrengthColor_keepsWeakLiftVeryLightBlue() {
        val weakLift = thermicStrengthColor(0f)

        assertTrue(weakLift.redChannel() >= 240)
        assertTrue(weakLift.greenChannel() >= 240)
        assertTrue(weakLift.blueChannel() >= 240)
        assertTrue(weakLift.blueChannel() >= weakLift.greenChannel())
        assertTrue(weakLift.greenChannel() >= weakLift.redChannel())
    }

    @Test
    fun thermicStrengthColor_passesThroughGreenAndEndsAtRed() {
        val moderateLift = thermicStrengthColor(2f)
        val maximumLift = thermicStrengthColor(THERMIC_STRENGTH_COLOR_SCALE_MAX_MPS)

        assertTrue(moderateLift.greenChannel() > moderateLift.redChannel())
        assertTrue(moderateLift.greenChannel() > moderateLift.blueChannel())
        assertEquals(Color(0xFFD32F2F).toArgb(), maximumLift.toArgb())
        assertEquals(
            maximumLift.toArgb(),
            thermicStrengthColor(THERMIC_STRENGTH_COLOR_SCALE_MAX_MPS + 2f).toArgb(),
        )
    }
}

private fun Color.redChannel(): Int = (toArgb() shr 16) and 0xFF

private fun Color.greenChannel(): Int = (toArgb() shr 8) and 0xFF

private fun Color.blueChannel(): Int = toArgb() and 0xFF
