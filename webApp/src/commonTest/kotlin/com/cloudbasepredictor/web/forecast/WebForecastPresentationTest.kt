package com.cloudbasepredictor.web.forecast

import com.cloudbasepredictor.data.map.MapLayerPreference
import com.cloudbasepredictor.data.remote.HourlyForecastData
import com.cloudbasepredictor.data.remote.HourlyPoint
import com.cloudbasepredictor.data.remote.PressureLevelPoint
import com.cloudbasepredictor.data.units.UnitPreset
import com.cloudbasepredictor.model.DailyForecast
import com.cloudbasepredictor.model.ForecastMode
import com.cloudbasepredictor.model.ForecastModel
import com.cloudbasepredictor.model.PlaceLocation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WebForecastPresentationTest {
    @Test
    fun createsAllFourSharedChartModelsWithSafeRouteBounds() {
        val state = buildWebForecastReadyState(
            WebForecastPresentationInput(
                location = PlaceLocation(47.67, 11.56, "Brauneck"),
                requestedModel = ForecastModel.ICON_D2,
                result = WebForecastResult(
                    hourlyData = sampleHourlyData(),
                    resolvedModel = ForecastModel.ICON_EU,
                    fetchedAtMillis = 1_752_225_600_000L,
                    fromCache = false,
                ),
                mode = ForecastMode.STUVE,
                dayIndex = 99,
                hour = 99,
                visibleTopAltitudeKm = 6f,
                unitPreset = UnitPreset.AVIATION,
                mapLayer = MapLayerPreference.OPENFREEMAP,
                favoritePlaces = emptyList(),
            ),
        )

        assertEquals(0, state.selectedDayIndex)
        assertEquals(ForecastMode.STUVE, state.selectedForecastMode)
        assertEquals(ForecastModel.ICON_EU, state.resolvedModel)
        assertEquals(UnitPreset.AVIATION, state.unitPreset)
        assertEquals(1, state.dayChips.size)
        assertTrue(state.thermicChart.timeSlots.isNotEmpty())
        assertTrue(state.windChart.hours.isNotEmpty())
        assertTrue(state.cloudChart.hours.isNotEmpty())
    }

    private fun sampleHourlyData(): HourlyForecastData {
        val pressureLevels = listOf(
            PressureLevelPoint(
                pressureHpa = 1000,
                temperatureC = 16.0,
                dewPointC = 10.0,
                windSpeedKmh = 12.0,
                windDirectionDeg = 220.0,
                geopotentialHeightM = 100.0,
            ),
            PressureLevelPoint(
                pressureHpa = 850,
                temperatureC = 7.0,
                dewPointC = 2.0,
                windSpeedKmh = 24.0,
                windDirectionDeg = 240.0,
                geopotentialHeightM = 1500.0,
            ),
        )
        return HourlyForecastData(
            latitude = 47.67,
            longitude = 11.56,
            elevation = 890.0,
            hourlyPoints = (6..20).map { hour ->
                HourlyPoint(
                    date = "2026-07-11",
                    hour = hour,
                    temperature2mC = 16.0,
                    dewPoint2mC = 10.0,
                    cloudCoverLowPercent = 20.0,
                    cloudCoverMidPercent = 10.0,
                    cloudCoverHighPercent = 5.0,
                    precipitationMm = 0.0,
                    precipitationProbabilityPercent = 5.0,
                    windSpeed10mKmh = 12.0,
                    windDirection10mDeg = 220.0,
                    capeJKg = 450.0,
                    freezingLevelHeightM = 3300.0,
                    pressureLevels = pressureLevels,
                )
            },
            dailyForecasts = listOf(
                DailyForecast("2026-07-11", 24.0, 12.0, 1),
            ),
        )
    }
}
