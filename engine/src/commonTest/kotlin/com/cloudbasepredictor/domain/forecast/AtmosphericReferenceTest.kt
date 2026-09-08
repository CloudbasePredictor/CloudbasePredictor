package com.cloudbasepredictor.domain.forecast

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * Independent published/analytic references; provenance and tolerances are in docs/forecast-calculation-references.md.
 */
class AtmosphericReferenceTest {
    @Test
    fun dryAdiabat_matchesPublishedMetPyExample() {
        val cases = listOf(1000f to 15f, 925f to 8.652495f, 850f to 1.925938f, 700f to -12.917867f)
        cases.forEach { (pressureHpa, expectedTemperatureC) ->
            assertEquals(expectedTemperatureC, dryAdiabatTempC(288.15f, pressureHpa), 0.03f, "$pressureHpa hPa")
        }
    }

    @Test
    fun potentialTemperature_matchesPublishedReferenceAndReferencePressureIdentity() {
        assertEquals(290.972015f, potentialTemperatureK(-0.15f, 800f), 0.02f)
        assertEquals(293.15f, potentialTemperatureK(20f, 1000f), 0.0001f)
        assertEquals(20f, dryAdiabatTempC(293.15f, 1000f), 0.0001f)
    }

    @Test
    fun vaporPressureAndMixingRatio_matchIndependentLiquidWaterCases() {
        AtmosphericReferenceData.humidity.forEach { case ->
            val label = "${case.dewPointC} C at ${case.pressureHpa} hPa"
            assertEquals(case.vaporPressureHpa, satVaporPressureHpa(case.dewPointC), 0.00002f, label)
            assertEquals(case.mixingRatioGKg, satMixingRatioGKg(case.dewPointC, case.pressureHpa), 0.00002f, label)
            assertEquals(
                case.mixingRatioGKg / 1000f,
                mixingRatioFromDewPointKgKg(case.pressureHpa, case.dewPointC),
                0.00000003f,
                label,
            )
        }
    }

    @Test
    fun dewPointInversion_acceptsIndependentMixingRatiosInBothUnitConventions() {
        AtmosphericReferenceData.humidity.forEach { case ->
            assertEquals(case.dewPointC, mixingRatioTemperatureC(case.mixingRatioGKg, case.pressureHpa), 0.0001f)
            assertEquals(
                case.dewPointC,
                cclMixingRatioTemperatureC(case.mixingRatioGKg / 1000f, case.pressureHpa),
                0.0001f,
            )
        }
    }

    @Test
    fun lcl_matchesIndependentSaturationRootsIncludingMountainLaunch() {
        val cases = listOf(
            LclReference(943f, 33f, 28f, 877.563323f),
            LclReference(1000f, 20f, 10f, 861.725118f),
            LclReference(850f, 15f, 0f, 677.456602f),
        )
        cases.forEach { case ->
            val elevationKm = 1.5f
            // Fine pressure spacing isolates the saturation root from coarse-profile interpolation.
            // Heights are prescribed geometry, not a pressure-to-height reference atmosphere.
            val profile = (0..100).map { index ->
                ProfileLevel(
                    pressureHpa = case.pressureHpa - 2f * index,
                    temperatureC = case.temperatureC - 0.12f * index,
                    dewPointC = case.dewPointC - 0.12f * index,
                    heightKm = elevationKm + 0.02f * index,
                )
            }
            val result = analyzeParcel(
                profile = profile,
                surfaceTemperatureC = case.temperatureC,
                surfaceDewPointC = case.dewPointC,
                surfacePressureHpa = case.pressureHpa,
                elevationKm = elevationKm,
                heatingInput = AtmosphericReferenceData.noHeating,
            )
            assertNotNull(result)
            assertEquals(0f, result.surfaceHeatingC)
            assertEquals(case.lclPressureHpa, result.lclPressureHpa, 0.3f, "Launch: $case")
            val expectedHeightKm = elevationKm + (case.pressureHpa - case.lclPressureHpa) * 0.01f
            assertEquals(expectedHeightKm, result.lclKm, 0.003f, "LCL must retain MSL elevation")
        }
    }

    @Test
    fun neutralDryColumn_hasNoCapeOrInhibition() {
        val result = analyzeDryColumn(environmentTemperatureScale = 1f)
        assertEquals(0f, result.computedCapeJKg, 0.0001f)
        assertEquals(0f, result.computedCinJKg, 0.01f)
    }

    @Test
    fun stableDryColumn_matchesConstantNegativeBuoyancyIntegral() {
        val result = analyzeDryColumn(environmentTemperatureScale = 1.01f)
        assertEquals(0f, result.computedCapeJKg, 0.0001f)
        // Integral over 2000 m: g * (1 - 1 / 1.01) * 2000; CIN is returned as a positive magnitude.
        assertEquals(194.257426f, result.computedCinJKg, 0.1f)
    }

    private fun analyzeDryColumn(environmentTemperatureScale: Float): ParcelAnalysisResult {
        val profile = AtmosphericReferenceData.dryColumn.map { level ->
            ProfileLevel(
                pressureHpa = level.pressureHpa,
                temperatureC = (level.temperatureC + 273.15f) * environmentTemperatureScale - 273.15f,
                dewPointC = -60f,
                heightKm = level.heightKm,
            )
        }
        // Parcel launch stays 20 C; the stable environment is independently warmer, including at its base.
        return assertNotNull(
            analyzeParcel(profile, 20f, -60f, 1000f, 0f, AtmosphericReferenceData.noHeating),
        )
    }

    private data class LclReference(
        val pressureHpa: Float,
        val temperatureC: Float,
        val dewPointC: Float,
        val lclPressureHpa: Float,
    )
}
