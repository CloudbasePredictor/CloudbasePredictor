package com.cloudbasepredictor.ui.screens.forecast.views

import kotlin.test.Test
import kotlin.test.assertEquals

class ForecastCanvasDrawingTest {
    @Test
    fun fixedDecimalFormattingIsLocaleIndependentAndPreservesRequestedPrecision() {
        assertEquals("0.0", formatFixedDecimal(0f, fractionDigits = 1))
        assertEquals("12.3", formatFixedDecimal(12.34f, fractionDigits = 1))
        assertEquals("12.4", formatFixedDecimal(12.36f, fractionDigits = 1))
        assertEquals("-2.5", formatFixedDecimal(-2.5f, fractionDigits = 1))
        assertEquals("+2.5", formatFixedDecimal(2.5f, fractionDigits = 1, alwaysShowSign = true))
        assertEquals("-0.0", formatFixedDecimal(-0.0f, fractionDigits = 1, alwaysShowSign = true))
        assertEquals("007", formatFixedDecimal(7f, fractionDigits = 0, minimumIntegerDigits = 3))
    }

    @Test
    fun paddedIntegerFormattingSupportsHoursAndNegativeValues() {
        assertEquals("06", formatPaddedInt(6, minimumDigits = 2))
        assertEquals("18", formatPaddedInt(18, minimumDigits = 2))
        assertEquals("-06", formatPaddedInt(-6, minimumDigits = 2))
        assertEquals("-2147483648", formatPaddedInt(Int.MIN_VALUE, minimumDigits = 2))
    }
}
