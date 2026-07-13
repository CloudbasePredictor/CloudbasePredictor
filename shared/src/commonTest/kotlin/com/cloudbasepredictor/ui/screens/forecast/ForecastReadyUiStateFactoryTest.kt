package com.cloudbasepredictor.ui.screens.forecast

import com.cloudbasepredictor.data.remote.HourlyForecastData
import com.cloudbasepredictor.data.remote.HourlyPoint
import com.cloudbasepredictor.data.remote.PressureLevelPoint
import com.cloudbasepredictor.data.units.UnitPreset
import com.cloudbasepredictor.data.units.resolveDisplayUnits
import com.cloudbasepredictor.model.DailyForecast
import com.cloudbasepredictor.model.ForecastMode
import com.cloudbasepredictor.model.ForecastModel
import com.cloudbasepredictor.model.SavedPlace
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ForecastReadyUiStateFactoryTest {
    @Test
    fun factoryBuildsAllChartsAndPreservesPlatformNeutralInputs() {
        val data = forecastData()
        val place = SavedPlace.fromCoordinates(
            latitude = data.latitude,
            longitude = data.longitude,
        ).copy(name = "Interlaken")
        val units = UnitPreset.IMPERIAL.resolveDisplayUnits()

        val state = buildForecastReadyUiState(
            ForecastRenderInput(
                hourlyData = data,
                place = place,
                requestedModel = ForecastModel.ICON_SEAMLESS,
                resolvedModel = ForecastModel.ICON_D2,
                selectedForecastMode = ForecastMode.WIND,
                selectedDayIndex = 1,
                stuveHour = 14,
                chartViewport = ForecastChartViewport(visibleTopAltitudeKm = 5.5f),
                unitPreset = UnitPreset.IMPERIAL,
                displayUnits = units,
                fetchedAtMillis = 1_752_220_800_000L,
            ),
        )

        assertEquals(place, state.selectedPlace)
        assertEquals(ForecastMode.WIND, state.selectedForecastMode)
        assertEquals(1, state.selectedDayIndex)
        assertEquals(ForecastModel.ICON_SEAMLESS, state.selectedModel)
        assertEquals(ForecastModel.ICON_D2, state.resolvedModel)
        assertEquals(14, state.stuveChart.selectedHour)
        assertEquals(5.5f, state.chartViewport.visibleTopAltitudeKm)
        assertEquals(0.58f, state.elevationKm)
        assertEquals(units, state.displayUnits)
        assertTrue(state.thermicChart.timeSlots.isNotEmpty())
        assertTrue(state.windChart.hours.isNotEmpty())
        assertTrue(state.cloudChart.hours.isNotEmpty())
        assertTrue(state.stuveChart.temperatureProfile.isNotEmpty())
        assertEquals("Today", state.dayChips[0].title)
        assertEquals("11 Jul", state.dayChips[0].subtitle)
        assertEquals("Sun", state.dayChips[1].title)
        assertEquals("12 Jul", state.dayChips[1].subtitle)
        assertTrue(state.forecastText.contains("Interlaken"))
    }

    @Test
    fun minimumInputValidationRejectsMissingWind() {
        val valid = forecastData()
        val missingWind = valid.copy(
            hourlyPoints = valid.hourlyPoints.map { point ->
                point.copy(
                    windSpeed10mKmh = null,
                    windDirection10mDeg = null,
                    pressureLevels = point.pressureLevels.map { level ->
                        level.copy(windSpeedKmh = null, windDirectionDeg = null)
                    },
                )
            },
        )

        assertTrue(valid.hasRequiredForecastInputs(dayIndex = 0, stuveHour = 12))
        assertFalse(missingWind.hasRequiredForecastInputs(dayIndex = 0, stuveHour = 12))
    }

    private fun forecastData(): HourlyForecastData {
        val dates = listOf("2026-07-11", "2026-07-12")
        return HourlyForecastData(
            latitude = 46.5582,
            longitude = 7.8354,
            elevation = 580.0,
            hourlyPoints = dates.flatMap { date ->
                (6..22).map { hour -> forecastPoint(date, hour) }
            },
            dailyForecasts = dates.mapIndexed { index, date ->
                DailyForecast(
                    date = date,
                    maxTemperatureCelsius = 22.0 + index,
                    minTemperatureCelsius = 11.0 + index,
                    weatherCode = if (index == 0) 1 else 2,
                )
            },
        )
    }

    private fun forecastPoint(
        date: String,
        hour: Int,
    ): HourlyPoint = HourlyPoint(
        date = date,
        hour = hour,
        temperature2mC = 12.0 + (hour - 6) * 0.6,
        dewPoint2mC = 8.0,
        cloudCoverLowPercent = 20.0,
        cloudCoverMidPercent = 30.0,
        cloudCoverHighPercent = 10.0,
        precipitationMm = 0.0,
        precipitationProbabilityPercent = 5.0,
        windSpeed10mKmh = 12.0,
        windDirection10mDeg = 240.0,
        capeJKg = 250.0,
        freezingLevelHeightM = 2_800.0,
        surfacePressureHpa = 950.0,
        shortwaveRadiationWm2 = 550.0,
        sunshineDurationS = 2_700.0,
        isDay = 1.0,
        pressureLevels = listOf(
            pressureLevel(950, 500.0, 15.0, 10.0),
            pressureLevel(850, 1_450.0, 9.0, 4.0),
            pressureLevel(700, 3_010.0, 1.0, -5.0),
            pressureLevel(500, 5_570.0, -12.0, -22.0),
        ),
    )

    private fun pressureLevel(
        pressureHpa: Int,
        heightMeters: Double,
        temperatureC: Double,
        dewPointC: Double,
    ): PressureLevelPoint = PressureLevelPoint(
        pressureHpa = pressureHpa,
        temperatureC = temperatureC,
        dewPointC = dewPointC,
        windSpeedKmh = 20.0,
        windDirectionDeg = 250.0,
        geopotentialHeightM = heightMeters,
        relativeHumidityPercent = 60.0,
        cloudCoverPercent = 20.0,
    )
}
