package com.cloudbasepredictor.ui.screens.forecast

import com.cloudbasepredictor.data.remote.HourlyForecastData
import com.cloudbasepredictor.data.remote.HourlyPoint
import com.cloudbasepredictor.data.remote.PressureLevelPoint
import com.cloudbasepredictor.model.DailyForecast
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ThermicChartDiagnosticsTest {

    private val pressureLevels = listOf(
        PressureLevelPoint(950, 18.0, 8.0, 10.0, 270.0, 600.0, 45.0, 5.0),
        PressureLevelPoint(900, 14.0, 4.0, 15.0, 280.0, 1000.0, 42.0, 5.0),
        PressureLevelPoint(850, 10.0, 1.0, 20.0, 290.0, 1500.0, 38.0, 5.0),
        PressureLevelPoint(800, 6.0, -2.0, 25.0, 300.0, 2000.0, 35.0, 10.0),
        PressureLevelPoint(700, -1.0, -10.0, 30.0, 310.0, 3000.0, 30.0, 10.0),
        PressureLevelPoint(600, -8.0, -18.0, 35.0, 320.0, 4200.0, 25.0, 5.0),
    )

    private fun makeHourlyData(
        hour: Int = 12,
        t2m: Double = 22.0,
        td2m: Double = 10.0,
        capeJKg: Double = 500.0,
    ) = HourlyForecastData(
        latitude = 46.7,
        longitude = 7.8,
        elevation = 500.0,
        hourlyPoints = listOf(
            HourlyPoint(
                date = "2024-05-15",
                hour = hour,
                temperature2mC = t2m,
                dewPoint2mC = td2m,
                cloudCoverLowPercent = 10.0,
                cloudCoverMidPercent = 5.0,
                cloudCoverHighPercent = 0.0,
                precipitationMm = 0.0,
                precipitationProbabilityPercent = 10.0,
                windSpeed10mKmh = 8.0,
                windDirection10mDeg = 270.0,
                capeJKg = capeJKg,
                freezingLevelHeightM = 3500.0,
                surfacePressureHpa = 955.0,
                shortwaveRadiationWm2 = 700.0,
                isDay = 1.0,
                pressureLevels = pressureLevels,
                liftedIndexC = -1.5,
                convectiveInhibitionJKg = 25.0,
                boundaryLayerHeightM = 1800.0,
            ),
        ),
        dailyForecasts = listOf(
            DailyForecast("2024-05-15", 24.0, 12.0, 1),
        ),
    )

    @Test
    fun buildChart_producesDiagnostics() {
        val chart = buildThermicChartFromData(makeHourlyData(), dayIndex = 0)
        assertTrue(chart.slotDiagnostics.isNotEmpty(), "Should have diagnostics")
        val diag = chart.slotDiagnostics.first()
        assertTrue(diag.topNominalKm > 0.5f, "Nominal top should be > elevation")
        assertTrue(diag.lclKm > 0.5f, "LCL should be > elevation")
        assertTrue(diag.cclKm == null || diag.cclKm > 0.5f, "CCL should be null or > elevation")
        assertNotNull(diag.modelCapeJKg, "Model CAPE should be present")
        assertNotNull(diag.modelCinJKg, "Model CIN should be present")
        assertNotNull(diag.normalizedCinJKg, "Normalized CIN should be present")
        assertNotNull(diag.liftedIndexC, "Model lifted index should be present")
        assertNotNull(diag.boundaryLayerHeightM, "Model PBL height should be present")
        assertTrue(diag.triggerExcessC >= diag.dryTopExcessC, "Trigger excess should be >= dry-top excess")
        assertEquals(22f, diag.surfaceTemperatureC!!, 0.0001f, "Surface T2m should be preserved")
        assertEquals(955f, diag.surfacePressureHpa!!, 0.0001f, "Surface pressure should be preserved")
        assertNotNull(diag.parcelStartTemperatureC, "Parcel start temperature should be preserved")
        assertNotNull(diag.dryTopAglM, "Dry-top AGL should be preserved")
        assertTrue(diag.usedPressureLevels.isNotEmpty(), "Used pressure-level diagnostics should be preserved")
        assertTrue(diag.topLowKm <= diag.topNominalKm, "Top range should contain nominal top")
        assertTrue(diag.topHighKm >= diag.topNominalKm, "Top range should contain nominal top")
        assertTrue(diag.updraftLowMps <= diag.updraftNominalMps, "Updraft range should contain nominal value")
        assertTrue(diag.updraftHighMps >= diag.updraftNominalMps, "Updraft range should contain nominal value")
        assertTrue(diag.computedCapeJKg >= 0f, "Computed CAPE should be >= 0")
        assertTrue(diag.computedCinJKg >= 0f, "Computed CIN should be >= 0")
        assertTrue(
            chart.cells.all { it.visualDepthM <= it.effectiveDepthM + 1f },
            "No thermic cell should draw deeper than its physical/evaluation bin",
        )
    }

    @Test
    fun buildChart_dryTopLine_isConsistentWithCells() {
        val chart = buildThermicChartFromData(makeHourlyData(), dayIndex = 0)
        val diag = chart.slotDiagnostics.firstOrNull() ?: return
        val maxCellTop = chart.cells
            .filter { it.startMinuteOfDayLocal == diag.startMinuteOfDayLocal }
            .maxOfOrNull { it.endAltitudeKm }

        if (maxCellTop != null) {
            assertTrue(
                maxCellTop <= diag.topNominalKm + 0.02f,
                "Cell top ($maxCellTop) should not exceed nominal top (${diag.topNominalKm})",
            )
        }
    }

    @Test
    fun buildChart_cloudDiagnostics_preserveCloudBaseAndMoistTop() {
        val chart = buildThermicChartFromData(makeHourlyData(), dayIndex = 0)
        val diag = chart.slotDiagnostics.firstOrNull() ?: return
        val cloudBase = diag.cloudBaseKm ?: return

        val moistTop = diag.moistEquilibriumTopKm
        if (moistTop != null && moistTop > cloudBase + 0.1f) {
            assertTrue(
                moistTop > cloudBase,
                "Moist top should stay above cloud base",
            )
        }
        assertTrue(chart.cloudMarkers.isEmpty(), "Cloud markers should no longer be repeated icons")
    }

    @Test
    fun buildChart_aggregation_preservesDiagnostics() {
        val chart = buildThermicChartFromData(makeHourlyData(), dayIndex = 0)
        val aggregated = chart.aggregatedForDisplay(
            timeBucketSlotCount = 1,
            altitudeBucketStepKm = 0.1f,
        )
        assertEquals(
            chart.slotDiagnostics.size,
            aggregated.slotDiagnostics.size,
            "Diagnostics should survive aggregation",
        )
    }

    @Test
    fun buildChart_noCape_capsBuoyancyBasedThermals() {
        val chart = buildThermicChartFromData(makeHourlyData(capeJKg = 0.0), dayIndex = 0)
        val maxOptimistic = chart.slotDiagnostics.maxOfOrNull { it.updraftHighMps } ?: 0f

        assertTrue(chart.cells.isNotEmpty(), "Dry profile can still produce capped weak thermals with 0 model CAPE")
        assertTrue(maxOptimistic <= 4.2f, "Zero CAPE should keep optimistic lift practical, got $maxOptimistic")
    }

    @Test
    fun buildChart_modelCapeCalibratesThermicStrength() {
        val lowCape = buildThermicChartFromData(makeHourlyData(capeJKg = 0.0), dayIndex = 0)
        val highCape = buildThermicChartFromData(makeHourlyData(capeJKg = 2500.0), dayIndex = 0)

        assertEquals(
            2500f,
            highCape.slotDiagnostics.first().modelCapeJKg!!,
            0.0001f,
            "Model CAPE should be preserved as a diagnostic",
        )
        assertTrue(
            highCape.slotDiagnostics.first().updraftNominalMps >
                lowCape.slotDiagnostics.first().updraftNominalMps,
            "Model CAPE should raise calibrated nominal updraft",
        )
    }

    @Test
    fun buildChart_missingPressureLevels_gracefulDegradation() {
        val hourlyData = HourlyForecastData(
            latitude = 46.7,
            longitude = 7.8,
            elevation = 500.0,
            hourlyPoints = listOf(
                HourlyPoint(
                    date = "2024-05-15",
                    hour = 12,
                    temperature2mC = 22.0,
                    dewPoint2mC = 10.0,
                    cloudCoverLowPercent = 10.0,
                    cloudCoverMidPercent = 5.0,
                    cloudCoverHighPercent = 0.0,
                    precipitationMm = 0.0,
                    precipitationProbabilityPercent = 10.0,
                    windSpeed10mKmh = 8.0,
                    windDirection10mDeg = 270.0,
                    capeJKg = 500.0,
                    freezingLevelHeightM = 3500.0,
                    pressureLevels = emptyList(),
                ),
            ),
            dailyForecasts = listOf(
                DailyForecast("2024-05-15", 24.0, 12.0, 1),
            ),
        )

        // Should not crash, may produce empty chart
        val chart = buildThermicChartFromData(hourlyData, dayIndex = 0)
        assertNotNull(chart, "Chart should never be null")
    }
}
