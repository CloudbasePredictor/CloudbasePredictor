package com.cloudbasepredictor.ui.screens.forecast

import com.cloudbasepredictor.domain.forecast.ThermalForecastConfidence
import com.cloudbasepredictor.domain.forecast.ThermalLimitingReason
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ThermicForecastChartUiModelTest {

    @Test
    fun aggregatedForDisplay_averagesStrengthValuesInsideDisplayBucket() {
        val chart = ThermicForecastChartUiModel(
            timeSlots = listOf(360, 375),
            cells = listOf(
                ThermicForecastCellUiModel(
                    startMinuteOfDayLocal = 360,
                    startAltitudeKm = 0f,
                    endAltitudeKm = 0.05f,
                    strengthMps = 1f,
                ),
                ThermicForecastCellUiModel(
                    startMinuteOfDayLocal = 360,
                    startAltitudeKm = 0.05f,
                    endAltitudeKm = 0.1f,
                    strengthMps = 3f,
                ),
                ThermicForecastCellUiModel(
                    startMinuteOfDayLocal = 375,
                    startAltitudeKm = 0f,
                    endAltitudeKm = 0.05f,
                    strengthMps = 1f,
                ),
                ThermicForecastCellUiModel(
                    startMinuteOfDayLocal = 375,
                    startAltitudeKm = 0.05f,
                    endAltitudeKm = 0.1f,
                    strengthMps = 3f,
                ),
            ),
            cloudMarkers = emptyList(),
        )

        val aggregated = chart.aggregatedForDisplay(
            timeBucketSlotCount = 2,
            altitudeBucketStepKm = 0.1f,
        )

        assertEquals(listOf(360), aggregated.timeSlots)
        assertEquals(1, aggregated.cells.size)
        assertEquals(2f, aggregated.cells.single().strengthMps, 0.0001f)
    }

    @Test
    fun aggregatedForDisplay_pressureGridIgnoresTinyFinalPartialWhenChoosingStep() {
        val rawCells = listOf(
            ThermicForecastCellUiModel(
                startMinuteOfDayLocal = 720,
                startAltitudeKm = 0.50f,
                endAltitudeKm = 0.75f,
                strengthMps = 1.2f,
            ),
            ThermicForecastCellUiModel(
                startMinuteOfDayLocal = 720,
                startAltitudeKm = 0.75f,
                endAltitudeKm = 1.00f,
                strengthMps = 1.8f,
            ),
            ThermicForecastCellUiModel(
                startMinuteOfDayLocal = 720,
                startAltitudeKm = 1.00f,
                endAltitudeKm = 1.03f,
                strengthMps = 0.8f,
            ),
        )
        val chart = ThermicForecastChartUiModel(
            timeSlots = listOf(720),
            cells = rawCells,
            cloudMarkers = emptyList(),
            pressureLevelAltitudesKm = listOf(0.75f, 1.00f),
        )

        val aggregated = chart.aggregatedForDisplay(
            timeBucketSlotCount = 1,
            altitudeBucketStepKm = 0.1f,
        )

        assertEquals(rawCells, aggregated.cells)
    }

    @Test
    fun aggregatedForDisplay_preservesThermalTopAndCloudHeights() {
        val chart = ThermicForecastChartUiModel(
            timeSlots = listOf(360, 375),
            cells = listOf(
                ThermicForecastCellUiModel(
                    startMinuteOfDayLocal = 360,
                    startAltitudeKm = 0f,
                    endAltitudeKm = 0.05f,
                    strengthMps = 1f,
                ),
                ThermicForecastCellUiModel(
                    startMinuteOfDayLocal = 360,
                    startAltitudeKm = 0.05f,
                    endAltitudeKm = 0.15f,
                    strengthMps = 2f,
                ),
                ThermicForecastCellUiModel(
                    startMinuteOfDayLocal = 375,
                    startAltitudeKm = 0f,
                    endAltitudeKm = 0.05f,
                    strengthMps = 1.5f,
                ),
                ThermicForecastCellUiModel(
                    startMinuteOfDayLocal = 375,
                    startAltitudeKm = 0.05f,
                    endAltitudeKm = 0.15f,
                    strengthMps = 2.5f,
                ),
            ),
            cloudMarkers = listOf(
                ThermicForecastCloudMarkerUiModel(
                    startMinuteOfDayLocal = 360,
                    altitudeKm = 1.2f,
                ),
                ThermicForecastCloudMarkerUiModel(
                    startMinuteOfDayLocal = 375,
                    altitudeKm = 1.6f,
                ),
            ),
            slotDiagnostics = listOf(
                ThermicSlotDiagnostics(
                    startMinuteOfDayLocal = 360,
                    dryThermalTopKm = 1.4f,
                    topLowKm = 1.1f,
                    topNominalKm = 1.4f,
                    topHighKm = 1.7f,
                    updraftLowMps = 0.8f,
                    updraftNominalMps = 1.1f,
                    updraftHighMps = 1.5f,
                    confidence = ThermalForecastConfidence.HIGH,
                    limitingReason = ThermalLimitingReason.SURFACE_HEATING,
                    cloudBaseKm = 1.2f,
                    moistEquilibriumTopKm = 2.0f,
                    modelCapeJKg = 100f,
                    computedCapeJKg = 50f,
                    computedCinJKg = 0f,
                    lclKm = 1.1f,
                    cclKm = 1.2f,
                ),
                ThermicSlotDiagnostics(
                    startMinuteOfDayLocal = 375,
                    dryThermalTopKm = 1.8f,
                    topLowKm = 1.3f,
                    topNominalKm = 1.8f,
                    topHighKm = 2.4f,
                    updraftLowMps = 1.0f,
                    updraftNominalMps = 1.4f,
                    updraftHighMps = 1.9f,
                    confidence = ThermalForecastConfidence.LOW,
                    limitingReason = ThermalLimitingReason.WIND_SHEAR,
                    cloudBaseKm = 1.6f,
                    moistEquilibriumTopKm = 2.6f,
                    modelCapeJKg = 200f,
                    computedCapeJKg = 75f,
                    computedCinJKg = 0f,
                    lclKm = 1.5f,
                    cclKm = 1.6f,
                ),
            ),
        )

        val aggregated = chart.aggregatedForDisplay(
            timeBucketSlotCount = 2,
            altitudeBucketStepKm = 0.1f,
        )

        assertEquals(2, aggregated.cells.size)
        assertEquals(0.15f, aggregated.cells.last().endAltitudeKm, 0.0001f)
        assertEquals(listOf(1.2f, 1.6f), aggregated.cloudMarkers.map { it.altitudeKm })
        val diag = aggregated.slotDiagnostics.single()
        assertEquals(1.1f, diag.topLowKm, 0.0001f)
        assertEquals(1.6f, diag.topNominalKm, 0.0001f)
        assertEquals(2.4f, diag.topHighKm, 0.0001f)
        assertEquals(ThermalForecastConfidence.LOW, diag.confidence)
        assertEquals(ThermalLimitingReason.WIND_SHEAR, diag.limitingReason)
    }

    @Test
    fun visibleSegment_clipsCellAtCloudBaseClearance() {
        val cell = ThermicForecastCellUiModel(
            startMinuteOfDayLocal = 900,
            startAltitudeKm = 2.0f,
            endAltitudeKm = 3.1f,
            strengthMps = 4.0f,
        )

        val visibleSegment = cell.visibleSegment(
            minAltitudeKm = 1.1f,
            maxAltitudeKm = 5.5f,
            cloudBaseKm = 2.7f,
        )

        assertNotNull(visibleSegment)
        assertEquals(2.0f, visibleSegment.startAltitudeKm, 0.0001f)
        assertEquals(2.65f, visibleSegment.endAltitudeKm, 0.0001f)
    }

    @Test
    fun visibleSegment_hidesCellEntirelyAboveCloudBaseClearance() {
        val cell = ThermicForecastCellUiModel(
            startMinuteOfDayLocal = 900,
            startAltitudeKm = 2.7f,
            endAltitudeKm = 3.1f,
            strengthMps = 4.0f,
        )

        val visibleSegment = cell.visibleSegment(
            minAltitudeKm = 1.1f,
            maxAltitudeKm = 5.5f,
            cloudBaseKm = 2.7f,
        )

        assertNull(visibleSegment)
    }
}
