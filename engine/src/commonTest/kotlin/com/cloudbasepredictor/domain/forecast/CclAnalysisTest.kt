package com.cloudbasepredictor.domain.forecast

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CclAnalysisTest {

    @Test
    fun analyzeCcl_filtersPressureLevelsBelowMountainSurface() {
        val result = analyzeCclHourly(
            cclInput(
                surfacePressureHpa = 850f,
                surfaceElevationM = 1500f,
                surfaceTemperatureC = 18f,
                surfaceDewPointC = 8f,
                levels = listOf(
                    level(1000f, -20f, 5f, 100f),
                    level(975f, -18f, 4f, 300f),
                    level(950f, -15f, 3f, 550f),
                    level(925f, -12f, 2f, 850f),
                    level(900f, -8f, 1f, 1200f),
                    level(800f, 10f, 0f, 2000f),
                    level(750f, 3f, -5f, 2600f),
                    level(700f, -3f, -10f, 3200f),
                ),
            ),
        ).first { it.method == CclMethod.SURFACE }

        assertNotNull(result.cclPressureHpa)
        assertTrue(result.cclPressureHpa < 850f, "CCL pressure must be above terrain pressure levels")
        assertTrue(result.cclHeightMslM!! > 1500f, "CCL height must remain above grid elevation")
        assertTrue(result.intersections.all { it.pressureHpa < 850f && it.heightMslM > 1500f })
    }

    @Test
    fun analyzeCcl_returnsSurfaceMl50AndMl100WithDistinctHumidityInputs() {
        val results = analyzeCclHourly(
            cclInput(
                surfaceTemperatureC = 26f,
                surfaceDewPointC = 16f,
                levels = listOf(
                    level(900f, 20f, 1f, 1000f),
                    level(850f, 12f, -5f, 1500f),
                    level(800f, 5f, -12f, 2050f),
                    level(750f, -1f, -18f, 2600f),
                    level(700f, -7f, -24f, 3200f),
                ),
            ),
        )

        val surface = results.first { it.method == CclMethod.SURFACE }
        val ml50 = results.first { it.method == CclMethod.ML50 }
        val ml100 = results.first { it.method == CclMethod.ML100 }

        assertNotNull(surface.cclPressureHpa)
        assertNotNull(ml50.cclPressureHpa)
        assertNotNull(ml100.cclPressureHpa)
        assertNotEquals(surface.cclPressureHpa, ml50.cclPressureHpa)
        assertNotEquals(ml50.cclPressureHpa, ml100.cclPressureHpa)
    }

    @Test
    fun mixedLayerMixingRatio_interpolatesLayerTopAndUsesTrapezoidalMean() {
        val input = cclInput(
            surfacePressureHpa = 950f,
            surfaceDewPointC = 10f,
            levels = listOf(
                level(925f, 17f, 0f, 800f),
                level(850f, 9f, -20f, 1500f),
            ),
        )

        val actual = mixedLayerMixingRatioKgKg(input, 50f)

        val surfaceW = mixingRatioFromDewPointKgKg(950f, 10f)
        val w925 = mixingRatioFromDewPointKgKg(925f, 0f)
        val w850 = mixingRatioFromDewPointKgKg(850f, -20f)
        val w900 = w925 + ((925f - 900f) / (925f - 850f)) * (w850 - w925)
        val expected = (
            ((surfaceW + w925) / 2f) * 25f +
                ((w925 + w900) / 2f) * 25f
            ) / 50f

        assertNotNull(actual)
        assertEquals(expected, actual, 0.000001f)
    }

    @Test
    fun mixedLayerMixingRatio_ignoresSyntheticLevels() {
        val input = cclInput(
            surfacePressureHpa = 950f,
            surfaceDewPointC = 10f,
            levels = listOf(
                level(900f, 18f, -35f, 1000f, isSynthetic = true),
                level(850f, 9f, 10f, 1500f),
            ),
        )

        val actual = mixedLayerMixingRatioKgKg(input, 100f)

        val surfaceW = mixingRatioFromDewPointKgKg(950f, 10f)
        val real850W = mixingRatioFromDewPointKgKg(850f, 10f)
        val expected = (surfaceW + real850W) / 2f
        assertNotNull(actual)
        assertEquals(expected, actual, 0.000001f)
    }

    @Test
    fun analyzeCcl_interpolatesCrossingPressureInLogPressure() {
        val surfacePressure = 950f
        val surfaceDewPoint = 10f
        val mixingRatio = mixingRatioFromDewPointKgKg(surfacePressure, surfaceDewPoint)
        val tMr900 = cclMixingRatioTemperatureC(mixingRatio, 900f)
        val tMr850 = cclMixingRatioTemperatureC(mixingRatio, 850f)
        val lowerDifference = 4f
        val upperDifference = -4f
        val expectedAlpha = 0.5f
        val expectedPressure = kotlin.math.exp(
            kotlin.math.ln(900f) + expectedAlpha * (kotlin.math.ln(850f) - kotlin.math.ln(900f)),
        )

        val result = analyzeCclHourly(
            cclInput(
                surfacePressureHpa = surfacePressure,
                surfaceTemperatureC = 24f,
                surfaceDewPointC = surfaceDewPoint,
                levels = listOf(
                    level(900f, tMr900 + lowerDifference, 0f, 1000f),
                    level(850f, tMr850 + upperDifference, -5f, 1500f),
                    level(800f, -5f, -10f, 2050f),
                ),
            ),
        ).first { it.method == CclMethod.SURFACE }

        assertNotNull(result.cclPressureHpa)
        assertEquals(expectedPressure, result.cclPressureHpa, 0.001f)
    }

    @Test
    fun analyzeCcl_multipleIntersectionsReturnsBottomAndWarning() {
        val surfacePressure = 950f
        val surfaceDewPoint = 10f
        val mixingRatio = mixingRatioFromDewPointKgKg(surfacePressure, surfaceDewPoint)

        fun shiftedTemperature(pressure: Float, difference: Float): Float {
            return cclMixingRatioTemperatureC(mixingRatio, pressure) + difference
        }

        val result = analyzeCclHourly(
            cclInput(
                surfacePressureHpa = surfacePressure,
                surfaceTemperatureC = 24f,
                surfaceDewPointC = surfaceDewPoint,
                levels = listOf(
                    level(900f, shiftedTemperature(900f, 4f), 0f, 1000f),
                    level(850f, shiftedTemperature(850f, -4f), -5f, 1500f),
                    level(800f, shiftedTemperature(800f, 4f), -10f, 2050f),
                    level(750f, shiftedTemperature(750f, -4f), -15f, 2600f),
                ),
            ),
        ).first { it.method == CclMethod.SURFACE }

        assertTrue(result.intersections.size >= 3)
        assertEquals(CclIntersectionType.BOTTOM, result.intersections.first().type)
        assertTrue(result.intersections.first().pressureHpa > result.intersections.last().pressureHpa)
        assertTrue(result.warnings.any { it.contains("Layered profile/inversion") })
    }

    @Test
    fun analyzeCcl_surfaceNearSaturationReturnsNearSurfaceCcl() {
        val result = analyzeCclHourly(
            cclInput(
                surfaceTemperatureC = 10f,
                surfaceDewPointC = 9.7f,
                levels = listOf(
                    level(900f, 8f, 3f, 1000f),
                    level(850f, 4f, 0f, 1500f),
                ),
            ),
        ).first { it.method == CclMethod.SURFACE }

        assertEquals(0f, result.cclHeightAglGridM!!, 0.001f)
        assertTrue(result.reachable)
        assertTrue(result.warnings.any { it.contains("Surface near saturation") })
    }

    @Test
    fun analyzeCcl_noIntersectionBelow500ReturnsNullAndWarning() {
        val result = analyzeCclHourly(
            cclInput(
                surfaceTemperatureC = 24f,
                surfaceDewPointC = 2f,
                levels = listOf(
                    level(900f, 25f, -10f, 1000f),
                    level(850f, 24f, -15f, 1500f),
                    level(800f, 22f, -20f, 2050f),
                    level(700f, 18f, -25f, 3200f),
                    level(500f, 10f, -40f, 5600f),
                    level(450f, -10f, -45f, 6400f),
                ),
            ),
        ).first { it.method == CclMethod.SURFACE }

        assertNull(result.cclPressureHpa)
        assertFalse(result.reachable)
        assertTrue(result.warnings.any { it.contains("No CCL found below 500 hPa") })
    }

    @Test
    fun analyzeCcl_computesConvectiveTemperatureHeatingMarginAndReachableFlag() {
        val result = analyzeCclHourly(
            cclInput(
                surfaceTemperatureC = 12f,
                surfaceDewPointC = 8f,
                levels = listOf(
                    level(900f, 18f, 2f, 1000f),
                    level(850f, 5f, -5f, 1500f),
                    level(800f, -2f, -10f, 2050f),
                ),
            ),
        ).first { it.method == CclMethod.SURFACE }

        assertNotNull(result.convectiveTemperatureC)
        assertNotNull(result.heatingMarginC)
        assertEquals(12f - result.convectiveTemperatureC, result.heatingMarginC, 0.001f)
        assertFalse(result.reachable)
        assertTrue(result.warnings.any { it.contains("theoretical only") })
    }

    private fun cclInput(
        surfacePressureHpa: Float = 950f,
        surfaceElevationM: Float = 600f,
        surfaceTemperatureC: Float = 24f,
        surfaceDewPointC: Float = 10f,
        levels: List<CclPressureLevel>,
    ): CclHourlyInput {
        return CclHourlyInput(
            time = "2026-04-23T12:00",
            surfaceTemperatureC = surfaceTemperatureC,
            surfaceDewPointC = surfaceDewPointC,
            surfacePressureHpa = surfacePressureHpa,
            surfaceElevationM = surfaceElevationM,
            pressureLevels = levels,
        )
    }

    private fun level(
        pressureHpa: Float,
        temperatureC: Float,
        dewPointC: Float?,
        heightMslM: Float?,
        isSynthetic: Boolean = false,
    ): CclPressureLevel {
        return CclPressureLevel(
            pressureHpa = pressureHpa,
            temperatureC = temperatureC,
            dewPointC = dewPointC,
            heightMslM = heightMslM,
            isSynthetic = isSynthetic,
        )
    }
}
