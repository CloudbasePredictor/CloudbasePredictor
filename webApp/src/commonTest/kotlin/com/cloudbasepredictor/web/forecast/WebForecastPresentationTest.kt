package com.cloudbasepredictor.web.forecast

import com.cloudbasepredictor.data.forecast.IncompleteForecastDataException
import com.cloudbasepredictor.data.map.MapLayerPreference
import com.cloudbasepredictor.data.remote.HourlyForecastData
import com.cloudbasepredictor.data.remote.HourlyPoint
import com.cloudbasepredictor.data.remote.PressureLevelPoint
import com.cloudbasepredictor.data.units.UnitPreset
import com.cloudbasepredictor.model.DailyForecast
import com.cloudbasepredictor.model.ForecastMode
import com.cloudbasepredictor.model.ForecastModel
import com.cloudbasepredictor.model.PlaceLocation
import com.cloudbasepredictor.web.i18n.englishWebForecastStrings
import com.cloudbasepredictor.web.i18n.russianWebForecastStrings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
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
                forecastStrings = englishWebForecastStrings,
            ),
        )

        assertEquals(20, state.stuveChart.selectedHour)
        assertEquals(0, state.selectedDayIndex)
        assertEquals(ForecastMode.STUVE, state.selectedForecastMode)
        assertEquals(ForecastModel.ICON_EU, state.resolvedModel)
        assertEquals(UnitPreset.AVIATION, state.unitPreset)
        assertEquals(1, state.dayChips.size)
        assertTrue(state.thermicChart.timeSlots.isNotEmpty())
        assertTrue(state.windChart.hours.isNotEmpty())
        assertTrue(state.cloudChart.hours.isNotEmpty())
    }

    @Test
    fun localizesDayChipWeatherAndSummaryForSelectedWebLanguage() {
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
                mode = ForecastMode.THERMIC,
                dayIndex = 0,
                hour = 12,
                visibleTopAltitudeKm = 6f,
                unitPreset = UnitPreset.METRIC_KMH,
                mapLayer = MapLayerPreference.OPENFREEMAP,
                favoritePlaces = emptyList(),
                forecastStrings = russianWebForecastStrings,
            ),
        )

        assertEquals("Сегодня", state.dayChips.single().title)
        assertTrue(state.forecastText.contains("Переменная облачность"))
        assertTrue(state.forecastText.contains("Brauneck"))
        assertFalse(state.forecastText.contains("Partly cloudy"))
        assertFalse(state.forecastText.contains("Today"))
    }

    @Test
    fun incompleteForecastsCannotBecomeReadyOnTheWeb() {
        val data = sampleHourlyData()
        val invalidData = listOf(
            data.copy(hourlyPoints = emptyList()),
            data.copy(dailyForecasts = emptyList()),
            data.copy(hourlyPoints = data.hourlyPoints.map { it.copy(date = "2026-07-12") }),
            data.copy(hourlyPoints = data.hourlyPoints.map { it.copy(temperature2mC = null) }),
            data.copy(hourlyPoints = data.hourlyPoints.map { it.copy(dewPoint2mC = null) }),
            data.copy(hourlyPoints = data.hourlyPoints.map {
                it.copy(windSpeed10mKmh = null, windDirection10mDeg = null, pressureLevels = emptyList())
            }),
        )

        invalidData.forEach { incomplete ->
            assertFailsWith<IncompleteForecastDataException> {
                buildWebForecastReadyState(presentationInput(incomplete))
            }
        }
    }

    @Test
    fun dailyDateWithoutHourlyDataCannotBecomeReadyOnTheWeb() {
        val data = sampleHourlyData()
        val secondDailyDay = data.copy(
            dailyForecasts = data.dailyForecasts + DailyForecast("2026-07-12", 25.0, 13.0, 2),
        )

        assertFailsWith<IncompleteForecastDataException> {
            buildWebForecastReadyState(presentationInput(secondDailyDay).copy(dayIndex = 1))
        }
    }

    @Test
    fun extraHourlyDateDoesNotExtendTheWebDaySelection() {
        val data = sampleHourlyData()
        val extraHourlyDay = data.copy(
            hourlyPoints = data.hourlyPoints + data.hourlyPoints.map { it.copy(date = "2026-07-12") },
        )

        val state = buildWebForecastReadyState(presentationInput(extraHourlyDay).copy(dayIndex = 99))

        assertEquals(0, state.selectedDayIndex)
        assertTrue(state.forecastText.contains("Today"))
    }

    private fun presentationInput(data: HourlyForecastData) = WebForecastPresentationInput(
        location = PlaceLocation(47.67, 11.56, "Brauneck"),
        requestedModel = ForecastModel.ICON_D2,
        result = WebForecastResult(data, ForecastModel.ICON_EU, 1_752_225_600_000L, false),
        mode = ForecastMode.THERMIC,
        dayIndex = 0,
        hour = 12,
        visibleTopAltitudeKm = 6f,
        unitPreset = UnitPreset.METRIC_KMH,
        mapLayer = MapLayerPreference.OPENFREEMAP,
        favoritePlaces = emptyList(),
        forecastStrings = englishWebForecastStrings,
    )

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
