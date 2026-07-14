package com.cloudbasepredictor.domain.forecast

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ParcelAnalysisTest {

    // ── Standard test profile: ~600 m ASL, typical Central European summer day ──

    private val standardProfile = listOf(
        ProfileLevel(pressureHpa = 950f, temperatureC = 18f, dewPointC = 8f, heightKm = 0.60f),
        ProfileLevel(pressureHpa = 925f, temperatureC = 15f, dewPointC = 6f, heightKm = 0.80f),
        ProfileLevel(pressureHpa = 900f, temperatureC = 12f, dewPointC = 4f, heightKm = 1.00f),
        ProfileLevel(pressureHpa = 850f, temperatureC = 8f, dewPointC = 0f, heightKm = 1.50f),
        ProfileLevel(pressureHpa = 800f, temperatureC = 4f, dewPointC = -4f, heightKm = 2.00f),
        ProfileLevel(pressureHpa = 700f, temperatureC = -3f, dewPointC = -15f, heightKm = 3.00f),
        ProfileLevel(pressureHpa = 600f, temperatureC = -10f, dewPointC = -25f, heightKm = 4.20f),
        ProfileLevel(pressureHpa = 500f, temperatureC = -20f, dewPointC = -35f, heightKm = 5.50f),
    )

    private val standardHeatingInput = SurfaceHeatingInput(
        hourOfDay = 13,
        shortwaveRadiationWm2 = 700f,
        cloudCoverLowPercent = 10f,
        cloudCoverMidPercent = 5f,
        cloudCoverHighPercent = 0f,
        precipitationMm = 0f,
        isDay = true,
    )

    // ── Thermodynamic helpers ──

    @Test
    fun potentialTemperature_atSurface() {
        // θ at 1000 hPa, 20°C should be close to 293.15 K
        val theta = potentialTemperatureK(20f, 1000f)
        assertEquals(293.15f, theta, 1f)
    }

    @Test
    fun dryAdiabat_roundTrip() {
        val theta = potentialTemperatureK(20f, 1000f)
        val t = dryAdiabatTempC(theta, 1000f)
        assertEquals(20f, t, 0.1f)
    }

    @Test
    fun dryAdiabat_decreasesWithAltitude() {
        val theta = potentialTemperatureK(20f, 1000f)
        val t1000 = dryAdiabatTempC(theta, 1000f)
        val t850 = dryAdiabatTempC(theta, 850f)
        val t700 = dryAdiabatTempC(theta, 700f)
        assertTrue(t1000 > t850, "Temperature should decrease with altitude")
        assertTrue(t850 > t700, "Temperature should decrease with altitude")
    }

    @Test
    fun satMixingRatio_increaseWithTemperature() {
        val mr10 = satMixingRatioGKg(10f, 1000f)
        val mr20 = satMixingRatioGKg(20f, 1000f)
        val mr30 = satMixingRatioGKg(30f, 1000f)
        assertTrue(mr20 > mr10, "Mixing ratio should increase with T")
        assertTrue(mr30 > mr20, "Mixing ratio should increase with T")
    }

    @Test
    fun satMixingRatio_realisticValues() {
        val mr = satMixingRatioGKg(15f, 1000f)
        // At 15°C, 1000 hPa: expect ~10.5 g/kg
        assertTrue(mr in 8f..14f, "Sat mixing ratio should be ~10 g/kg at 15°C")
    }

    @Test
    fun relativeHumidityFraction_isOneAtSaturation() {
        val humidity = relativeHumidityFraction(12f, 12f)
        assertEquals(1f, humidity, 0.001f)
    }

    @Test
    fun mixingRatioTemperature_roundTripsSaturationMixingRatio() {
        val originalTemperature = 7f
        val pressure = 850f
        val mixingRatio = satMixingRatioGKg(originalTemperature, pressure)
        val reconstructedTemperature = mixingRatioTemperatureC(mixingRatio, pressure)
        assertEquals(originalTemperature, reconstructedTemperature, 0.3f)
    }

    @Test
    fun moistAdiabat_warmerThanDryAboveLcl() {
        val theta = potentialTemperatureK(20f, 1000f)
        val dryTemp = dryAdiabatTempC(theta, 600f)
        val moistTemp = moistAdiabatTempC(theta, 600f)
        assertTrue(
            moistTemp > dryTemp,
            "Moist adiabat should be warmer than dry at same pressure",
        )
    }

    @Test
    fun estimateSurfacePressure_seaLevel() {
        val p = estimateSurfacePressure(0.0)
        assertEquals(1013.25f, p, 1f)
    }

    @Test
    fun estimateSurfacePressure_highElevation() {
        val p = estimateSurfacePressure(1500.0)
        assertTrue(p in 830f..860f, "Surface pressure at 1500m should be ~850 hPa")
    }

    // ── Surface heating ──

    @Test
    fun surfaceHeating_peakMidday_sunny() {
        val input = SurfaceHeatingInput(
            hourOfDay = 13,
            shortwaveRadiationWm2 = 800f,
            cloudCoverLowPercent = 0f,
            cloudCoverMidPercent = 0f,
            cloudCoverHighPercent = 0f,
            precipitationMm = 0f,
            isDay = true,
        )
        val heating = estimateSurfaceHeating(input)
        assertTrue(heating >= 4f, "Peak sunny midday heating should be >4°C")
        assertTrue(heating <= 8f, "Heating should not exceed 8°C")
    }

    @Test
    fun surfaceHeating_nightTime_zero() {
        val input = SurfaceHeatingInput(
            hourOfDay = 3,
            shortwaveRadiationWm2 = 0f,
            cloudCoverLowPercent = 0f,
            cloudCoverMidPercent = 0f,
            cloudCoverHighPercent = 0f,
            precipitationMm = 0f,
            isDay = false,
        )
        val heating = estimateSurfaceHeating(input)
        assertEquals(0f, heating, 0.01f)
    }

    @Test
    fun surfaceHeating_heavyLowCloud_reduced() {
        val clear = SurfaceHeatingInput(
            hourOfDay = 13,
            shortwaveRadiationWm2 = 700f,
            cloudCoverLowPercent = 0f,
            cloudCoverMidPercent = 0f,
            cloudCoverHighPercent = 0f,
            precipitationMm = 0f,
            isDay = true,
        )
        val overcast = clear.copy(cloudCoverLowPercent = 80f)
        val heatingClear = estimateSurfaceHeating(clear)
        val heatingOvercast = estimateSurfaceHeating(overcast)
        assertTrue(heatingOvercast < heatingClear, "Heavy low cloud should reduce heating")
    }

    @Test
    fun surfaceHeating_knownStrongRadiation_ignoresHighAndMidCloudCoverage() {
        val clear = SurfaceHeatingInput(
            hourOfDay = 13,
            shortwaveRadiationWm2 = 744f,
            cloudCoverLowPercent = 0f,
            cloudCoverMidPercent = 0f,
            cloudCoverHighPercent = 0f,
            precipitationMm = 0f,
            isDay = true,
        )
        val thinHighMidCloud = clear.copy(
            cloudCoverMidPercent = 100f,
            cloudCoverHighPercent = 100f,
        )

        val heatingClear = estimateSurfaceHeating(clear)
        val heatingCloudy = estimateSurfaceHeating(thinHighMidCloud)

        assertTrue(heatingCloudy >= 4f, "Strong radiation should still produce strong heating")
        assertEquals(heatingClear, heatingCloudy, 0.001f)
    }

    @Test
    fun surfaceHeating_precipitation_reduces() {
        val dry = SurfaceHeatingInput(
            hourOfDay = 13,
            shortwaveRadiationWm2 = 500f,
            cloudCoverLowPercent = 30f,
            cloudCoverMidPercent = 10f,
            cloudCoverHighPercent = 0f,
            precipitationMm = 0f,
            isDay = true,
        )
        val rainy = dry.copy(precipitationMm = 2f)
        val heatingDry = estimateSurfaceHeating(dry)
        val heatingRainy = estimateSurfaceHeating(rainy)
        assertTrue(heatingRainy < heatingDry, "Rain should reduce heating")
    }

    @Test
    fun surfaceHeating_noRadiationData_usesConservativeDefault() {
        val input = SurfaceHeatingInput(
            hourOfDay = 12,
            shortwaveRadiationWm2 = null,
            cloudCoverLowPercent = null,
            cloudCoverMidPercent = null,
            cloudCoverHighPercent = null,
            precipitationMm = null,
            isDay = true,
        )
        val heating = estimateSurfaceHeating(input)
        assertTrue(heating > 0f, "Should still provide some heating")
        assertTrue(heating < 5f, "Should be conservative")
    }

    @Test
    fun surfaceHeating_noRadiationData_appliesCloudPenaltyExactlyOnce() {
        // No radiation data exercises the conservative-default branch. The low-cloud
        // penalty must be applied once (shared with the final penalty step), not twice.
        val clear = SurfaceHeatingInput(
            hourOfDay = 12,
            shortwaveRadiationWm2 = null,
            cloudCoverLowPercent = 0f,
            cloudCoverMidPercent = 0f,
            cloudCoverHighPercent = 0f,
            precipitationMm = 0f,
            isDay = true,
        )
        val cloudy = clear.copy(cloudCoverLowPercent = 50f)

        val heatingClear = estimateSurfaceHeating(clear)
        val heatingCloudy = estimateSurfaceHeating(cloudy)

        // 50% low cloud → penalty = 0.50 * 0.7 = 0.35 in the no-radiation branch.
        val penalty = 0.35f
        assertEquals(
            heatingClear * (1f - penalty),
            heatingCloudy,
            0.01f,
            "Cloud penalty must be applied exactly once in the no-radiation branch",
        )
        // Guard against the previous double-application (penalty squared).
        assertTrue(
            heatingCloudy > heatingClear * (1f - penalty) * (1f - penalty) + 0.05f,
            "Penalty must not be applied twice (squared)",
        )
    }

    // ── Full parcel analysis ──

    @Test
    fun parcelAnalysis_standardProfile_findsUsableThermals() {
        val result = analyzeParcel(
            profile = standardProfile,
            surfaceTemperatureC = 22f,
            surfaceDewPointC = 10f,
            surfacePressureHpa = 955f,
            elevationKm = 0.58f,
            heatingInput = standardHeatingInput,
            modelCapeJKg = 300f,
        )

        assertNotNull(result, "Should produce analysis")

        assertTrue(result.thermalCells.isNotEmpty(), "Should have thermal cells")
        assertTrue(result.dryThermalTopKm > 0.58f, "Dry top should be above elevation")
        assertTrue(result.lclKm > 0.58f, "LCL should be above elevation")
        assertNotNull(result.cclKm, "CCL should be available")
        assertTrue(result.cclKm > 0.58f, "CCL should be above elevation")
        assertTrue(result.lclPressureHpa < 955f, "LCL pressure should be below the surface")
        assertNotNull(result.cclPressureHpa, "CCL pressure should be available")
        assertTrue(result.cclPressureHpa < 955f, "CCL pressure should be below the surface")
        assertNotNull(result.tconC, "TCON should be available for the standard profile")
    }

    @Test
    fun parcelAnalysis_dryTopBelowLcl_noCloudBase() {
        // Very stable profile — inversion at 800m, LCL will be above dry top
        val stableProfile = listOf(
            ProfileLevel(pressureHpa = 950f, temperatureC = 15f, dewPointC = 2f, heightKm = 0.60f),
            ProfileLevel(pressureHpa = 925f, temperatureC = 16f, dewPointC = 1f, heightKm = 0.80f), // Inversion!
            ProfileLevel(pressureHpa = 900f, temperatureC = 14f, dewPointC = -2f, heightKm = 1.00f),
            ProfileLevel(pressureHpa = 850f, temperatureC = 10f, dewPointC = -6f, heightKm = 1.50f),
            ProfileLevel(pressureHpa = 700f, temperatureC = -3f, dewPointC = -20f, heightKm = 3.00f),
        )
        val result = analyzeParcel(
            profile = stableProfile,
            surfaceTemperatureC = 16f,
            surfaceDewPointC = 2f,
            surfacePressureHpa = 955f,
            elevationKm = 0.58f,
            heatingInput = standardHeatingInput.copy(shortwaveRadiationWm2 = 200f),
            modelCapeJKg = 10f,
        )

        assertNotNull(result)
        // With a strong inversion, cloud base may be null (dry top below LCL)
        // This is acceptable - we're testing the logic handles it correctly
        assertTrue(
            result.dryThermalTopKm < 2f,
            "Dry top should be below 2 km in this stable profile",
        )
        assertTrue(result.dryThermalTopKm < result.lclKm, "Dry thermal buoyancy must end below the LCL")
        assertTrue(
            result.computedCinJKg > 0f,
            "A shallow dry-thermal layer below the LCL must not erase the cap above it",
        )
    }

    @Test
    fun parcelAnalysis_computedCapeIsPositive_withBuoyantProfile() {
        val result = analyzeParcel(
            profile = standardProfile,
            surfaceTemperatureC = 22f,
            surfaceDewPointC = 10f,
            surfacePressureHpa = 955f,
            elevationKm = 0.58f,
            heatingInput = standardHeatingInput,
            modelCapeJKg = 500f,
        )

        assertNotNull(result)
        assertTrue(result.computedCapeJKg > 0f, "Computed CAPE should be positive")
    }

    @Test
    fun parcelAnalysis_cinExcludesStableLayersAboveEquilibriumLevel() {
        // The standard profile produces positive CAPE. Appending a strong warm inversion after
        // that buoyant region adds stable layers above the equilibrium level. Those must not be
        // counted as convective inhibition, which ends at the LFC.
        val profileWithUpperInversion = standardProfile + listOf(
            ProfileLevel(pressureHpa = 450f, temperatureC = 8f, dewPointC = -30f, heightKm = 6.30f),
            ProfileLevel(pressureHpa = 400f, temperatureC = 6f, dewPointC = -35f, heightKm = 7.20f),
        )

        val base = analyzeParcel(
            profile = standardProfile,
            surfaceTemperatureC = 22f,
            surfaceDewPointC = 10f,
            surfacePressureHpa = 955f,
            elevationKm = 0.58f,
            heatingInput = standardHeatingInput,
            modelCapeJKg = 500f,
        )
        val withInversion = analyzeParcel(
            profile = profileWithUpperInversion,
            surfaceTemperatureC = 22f,
            surfaceDewPointC = 10f,
            surfacePressureHpa = 955f,
            elevationKm = 0.58f,
            heatingInput = standardHeatingInput,
            modelCapeJKg = 500f,
        )

        assertNotNull(base)
        assertNotNull(withInversion)
        assertTrue(base.computedCapeJKg > 0f, "Base profile must reach positive buoyancy")
        assertEquals(
            base.computedCinJKg,
            withInversion.computedCinJKg,
            0.5f,
            "A stable inversion above the EL must not increase CIN",
        )
    }

    @Test
    fun parcelAnalysis_thermalCellsOnlyBelowDryTop() {
        val result = analyzeParcel(
            profile = standardProfile,
            surfaceTemperatureC = 22f,
            surfaceDewPointC = 10f,
            surfacePressureHpa = 955f,
            elevationKm = 0.58f,
            heatingInput = standardHeatingInput,
        )

        assertNotNull(result)

        result.thermalCells.forEach { cell ->
            assertTrue(
                cell.endAltitudeKm <= result.dryThermalTopKm + 0.01f,
                "Cell top ${cell.endAltitudeKm} should be <= dry top ${result.dryThermalTopKm}",
            )
        }
    }

    @Test
    fun parcelAnalysis_cellsAreContiguous() {
        val result = analyzeParcel(
            profile = standardProfile,
            surfaceTemperatureC = 22f,
            surfaceDewPointC = 10f,
            surfacePressureHpa = 955f,
            elevationKm = 0.58f,
            heatingInput = standardHeatingInput,
        )

        assertNotNull(result)
        val cells = result.thermalCells
        if (cells.size >= 2) {
            val sorted = cells.sortedBy { it.startAltitudeKm }
            for (i in 0 until sorted.size - 1) {
                assertEquals(
                    sorted[i].endAltitudeKm,
                    sorted[i + 1].startAltitudeKm,
                    0.02f,
                    "Cell end should equal next cell start",
                )
            }
        }
    }

    @Test
    fun parcelAnalysis_insufficientProfile_returnsNull() {
        val thinProfile = listOf(
            ProfileLevel(pressureHpa = 950f, temperatureC = 18f, dewPointC = 8f, heightKm = 0.60f),
        )
        val result = analyzeParcel(
            profile = thinProfile,
            surfaceTemperatureC = 22f,
            surfaceDewPointC = 10f,
            surfacePressureHpa = 955f,
            elevationKm = 0.58f,
            heatingInput = standardHeatingInput,
        )
        assertNull(result, "Single-level profile should return null")
    }

    @Test
    fun parcelAnalysis_emptyProfile_returnsNull() {
        val result = analyzeParcel(
            profile = emptyList(),
            surfaceTemperatureC = 22f,
            surfaceDewPointC = 10f,
            surfacePressureHpa = 955f,
            elevationKm = 0.58f,
            heatingInput = standardHeatingInput,
        )
        assertNull(result, "Empty profile should return null")
    }

    @Test
    fun parcelAnalysis_allLevelsBelowElevation_returnsNull() {
        val lowProfile = listOf(
            ProfileLevel(pressureHpa = 1000f, temperatureC = 20f, dewPointC = 12f, heightKm = 0.10f),
            ProfileLevel(pressureHpa = 975f, temperatureC = 18f, dewPointC = 10f, heightKm = 0.30f),
        )
        val result = analyzeParcel(
            profile = lowProfile,
            surfaceTemperatureC = 22f,
            surfaceDewPointC = 10f,
            surfacePressureHpa = 850f,
            elevationKm = 1.5f,
            heatingInput = standardHeatingInput,
        )
        assertNull(result, "Profile below elevation should return null")
    }

    @Test
    fun parcelAnalysis_modelCapeIsDiagnosticOnly() {
        val resultNoModel = analyzeParcel(
            profile = standardProfile,
            surfaceTemperatureC = 22f,
            surfaceDewPointC = 10f,
            surfacePressureHpa = 955f,
            elevationKm = 0.58f,
            heatingInput = standardHeatingInput,
            modelCapeJKg = null,
        )
        val resultWithModel = analyzeParcel(
            profile = standardProfile,
            surfaceTemperatureC = 22f,
            surfaceDewPointC = 10f,
            surfacePressureHpa = 955f,
            elevationKm = 0.58f,
            heatingInput = standardHeatingInput,
            modelCapeJKg = 800f,
        )

        assertNotNull(resultNoModel)
        assertNotNull(resultWithModel)
        // Both should produce the same thermic cells; model CAPE is carried as a diagnostic only.
        assertTrue(resultNoModel.thermalCells.isNotEmpty())
        assertTrue(resultWithModel.thermalCells.isNotEmpty())
        resultNoModel.thermalCells.zip(resultWithModel.thermalCells).forEach { (withoutCape, withCape) ->
            assertEquals(withoutCape.strengthMps, withCape.strengthMps, 0.0001f)
        }
    }

    @Test
    fun parcelAnalysis_cloudBaseKm_usesReachableCcl() {
        val result = analyzeParcel(
            profile = standardProfile,
            surfaceTemperatureC = 22f,
            surfaceDewPointC = 10f,
            surfacePressureHpa = 955f,
            elevationKm = 0.58f,
            heatingInput = standardHeatingInput,
        )

        assertNotNull(result)

        val cloudBase = result.cloudBaseKm
        if (cloudBase != null) {
            assertNotNull(result.cclKm, "Cloud base should be tied to an available CCL")
            assertTrue(
                kotlin.math.abs(cloudBase - result.cclKm) <= 0.05f,
                "Cloud base should come from the reachable CCL",
            )
        }
    }

    @Test
    fun parcelAnalysis_strengthValues_areReasonable() {
        val result = analyzeParcel(
            profile = standardProfile,
            surfaceTemperatureC = 22f,
            surfaceDewPointC = 10f,
            surfacePressureHpa = 955f,
            elevationKm = 0.58f,
            heatingInput = standardHeatingInput,
        )

        assertNotNull(result)

        result.thermalCells.forEach { cell ->
            assertTrue(
                cell.strengthMps > 0f,
                "Strength should be positive: ${cell.strengthMps}",
            )
            assertTrue(
                cell.strengthMps <= 10f,
                "Strength should be <= 10 m/s: ${cell.strengthMps}",
            )
        }
    }

    // ── Solar elevation factor ──

    @Test
    fun solarElevationFactor_peaksAtMidday() {
        val f13 = solarElevationFactor(13)
        val f8 = solarElevationFactor(8)
        val f18 = solarElevationFactor(18)
        assertTrue(f13 >= f8, "Peak should be at 13")
        assertTrue(f13 >= f18, "Peak should be at 13")
        assertEquals(1f, f13, 0.01f)
    }

    @Test
    fun solarElevationFactor_zeroAtNight() {
        val f21 = solarElevationFactor(21)
        assertEquals(0f, f21, 0.01f)
    }

    // ── CAPE discrepancy analysis ──

    @Test
    fun capeDiscrepancy_documentedFactors() {
        // This test documents why computed CAPE differs from model CAPE.
        // Computed CAPE is retained for parcel diagnostics, not for thermic strength calibration.
        val result = analyzeParcel(
            profile = standardProfile,
            surfaceTemperatureC = 22f,
            surfaceDewPointC = 10f,
            surfacePressureHpa = 955f,
            elevationKm = 0.58f,
            heatingInput = standardHeatingInput,
            modelCapeJKg = 300f,
        )

        assertNotNull(result)

        // The discrepancy arises from:
        // 1. Surface heating (+2 to +8°C) boosts parcel θ above model's T2m-based CAPE
        // 2. Coarse profile (8 levels) vs model's fine vertical grid (~90 levels)
        // 3. No virtual temperature correction (moisture buoyancy ignored)
        // 4. Simplified moist adiabat (4 Newton iterations vs full integration)
        // 5. Model likely uses mixed-layer CAPE (avg lowest 100 hPa) vs our surface-based parcel
        //
        assertTrue(
            result.computedCapeJKg > 0f,
            "Computed CAPE (${result.computedCapeJKg}) should be positive",
        )

        // Surface heating shifts the parcel significantly warmer, typically resulting
        // in computed CAPE exceeding model CAPE when radiation is strong.
        assertTrue(
            result.surfaceHeatingC > 0f,
            "Surface heating should be > 0",
        )
    }
}
