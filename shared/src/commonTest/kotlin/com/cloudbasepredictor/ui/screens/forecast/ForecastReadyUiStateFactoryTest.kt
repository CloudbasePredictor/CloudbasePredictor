package com.cloudbasepredictor.ui.screens.forecast

import com.cloudbasepredictor.data.forecast.IncompleteForecastDataException
import com.cloudbasepredictor.data.forecast.hasRequiredForecastInputs
import com.cloudbasepredictor.data.forecast.hasRequiredForecastWindow
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
import kotlin.test.assertFailsWith
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

    @Test
    fun factoryRejectsDailyDayWithoutMatchingHourlyDate() {
        val data = forecastData()
        val firstDayOnly = data.copy(hourlyPoints = data.hourlyPoints.filter { it.date == "2026-07-11" })

        assertFailsWith<IncompleteForecastDataException> {
            buildForecastReadyUiState(renderInput(firstDayOnly).copy(selectedDayIndex = 1))
        }
    }

    @Test
    fun factoryRejectsMismatchedDatesEvenWhenCountsMatch() {
        val data = forecastData()
        val mismatched = data.copy(hourlyPoints = data.hourlyPoints.map { it.copy(date = "2026-07-13") })

        assertFailsWith<IncompleteForecastDataException> {
            buildForecastReadyUiState(renderInput(mismatched))
        }
    }

    @Test
    fun extraHourlyDatesCannotSelectADayWithoutADailyLabel() {
        val data = forecastData()
        val oneDailyDay = data.copy(dailyForecasts = data.dailyForecasts.take(1))

        val state = buildForecastReadyUiState(renderInput(oneDailyDay).copy(selectedDayIndex = 99))

        assertEquals(0, state.selectedDayIndex)
        assertEquals(1, state.dayChips.size)
    }

    @Test
    fun chartsResolveSelectedDailyDateInsteadOfHourlyPosition() {
        val data = forecastData()
        val secondDayOnly = data.copy(
            hourlyPoints = data.hourlyPoints.filter { it.date == "2026-07-12" }
                .map { it.copy(temperature2mC = 31.0) },
        )

        val state = buildForecastReadyUiState(renderInput(secondDayOnly).copy(selectedDayIndex = 1))

        assertEquals(1, state.selectedDayIndex)
        assertEquals(31f, state.stuveChart.temperatureProfile.first().temperatureC)
        assertTrue(state.forecastText.contains("2026-07-12"))
    }

    @Test
    fun factoryRejectsEmptyDataAndMissingRequiredFields() {
        val data = forecastData()
        val invalidData = listOf(
            data.copy(hourlyPoints = emptyList()),
            data.copy(dailyForecasts = emptyList()),
            data.copy(hourlyPoints = data.hourlyPoints.map { it.copy(temperature2mC = null) }),
            data.copy(hourlyPoints = data.hourlyPoints.map { it.copy(dewPoint2mC = null) }),
            data.copy(hourlyPoints = data.hourlyPoints.map {
                it.copy(windSpeed10mKmh = null, windDirection10mDeg = null, pressureLevels = emptyList())
            }),
            data.copy(hourlyPoints = data.hourlyPoints.map { it.copy(hour = 2) }),
        )

        invalidData.forEach { incomplete ->
            assertFailsWith<IncompleteForecastDataException> {
                buildForecastReadyUiState(renderInput(incomplete))
            }
        }
    }

    @Test
    fun syntheticPressureWindCannotMakeAnOtherwiseEmptyWindChartReady() {
        val data = forecastData()
        val syntheticWind = data.copy(hourlyPoints = data.hourlyPoints.map { point ->
            point.copy(
                windSpeed10mKmh = null,
                windDirection10mDeg = null,
                pressureLevels = point.pressureLevels.map { it.copy(isSynthetic = true) },
            )
        })

        assertFailsWith<IncompleteForecastDataException> {
            buildForecastReadyUiState(renderInput(syntheticWind))
        }
    }

    @Test
    fun missingRequestedHourUsesAndLabelsTheClosestAvailableDaytimeHour() {
        val data = forecastData()
        val limitedHours = data.copy(hourlyPoints = data.hourlyPoints.filter { it.hour <= 20 })

        val state = buildForecastReadyUiState(renderInput(limitedHours).copy(stuveHour = 99))

        assertEquals(20, state.stuveChart.selectedHour)
    }

    @Test
    fun progressiveChipsRemainVisibleButAnUnloadedSelectionIsRejected() {
        val data = forecastData()
        val chips = buildForecastDayChips(data.dailyForecasts) + ForecastDayChipUiModel("Mon", "13 Jul")
        val input = renderInput(data).copy(dayChips = chips)

        assertEquals(chips, buildForecastReadyUiState(input).dayChips)
        assertFailsWith<IncompleteForecastDataException> {
            buildForecastReadyUiState(input.copy(selectedDayIndex = 2))
        }
    }

    @Test
    fun forecastWindowRejectsMissingSurfaceInputsAtAnyOfferedDaytimeHour() {
        val data = forecastData()
        for (hour in listOf(6, 18, 22)) {
            for (missingTemperature in listOf(true, false)) {
                val incomplete = data.copy(hourlyPoints = data.hourlyPoints.map { point ->
                    when {
                        point.hour != hour -> point
                        missingTemperature -> point.copy(temperature2mC = null)
                        else -> point.copy(dewPoint2mC = null)
                    }
                })

                assertTrue(incomplete.hasRequiredForecastInputs(dayIndex = 0, stuveHour = 12))
                assertFalse(incomplete.hasRequiredForecastWindow(forecastDays = 2), "Missing input at $hour:00")
            }
        }
    }

    @Test
    fun forecastWindowAcceptsSparseDaytimeHoursAndIgnoresIncompleteNighttimePoints() {
        val data = forecastData()
        val sparse = data.copy(hourlyPoints = data.hourlyPoints.filter { it.hour in listOf(9, 18, 21) })
        val withNighttimeGap = sparse.copy(
            hourlyPoints = sparse.hourlyPoints + forecastPoint("2026-07-11", 3).copy(
                temperature2mC = null,
                dewPoint2mC = null,
            ),
        )

        assertTrue(withNighttimeGap.hasRequiredForecastWindow(forecastDays = 5))
    }

    @Test
    fun forecastWindowRejectsAnIncompleteLaterRequestedDay() {
        val data = forecastData()
        val incompleteSecondDay = data.copy(hourlyPoints = data.hourlyPoints.map { point ->
            if (point.date == "2026-07-12") point.copy(temperature2mC = null) else point
        })

        assertTrue(incompleteSecondDay.hasRequiredForecastWindow(forecastDays = 1))
        assertFalse(incompleteSecondDay.hasRequiredForecastWindow(forecastDays = 2))
    }

    @Test
    fun forecastWindowAcceptsACompleteShorterFallbackHorizon() {
        val data = forecastData()

        assertTrue(data.hasRequiredForecastWindow(forecastDays = 5))
    }

    @Test
    fun forecastWindowRejectsEmptyAndMismatchedDates() {
        val data = forecastData()
        val missingSecondDate = data.copy(hourlyPoints = data.hourlyPoints.filter { it.date == "2026-07-11" })

        assertFalse(data.copy(dailyForecasts = emptyList()).hasRequiredForecastWindow(forecastDays = 2))
        assertFalse(data.copy(hourlyPoints = emptyList()).hasRequiredForecastWindow(forecastDays = 2))
        assertFalse(missingSecondDate.hasRequiredForecastWindow(forecastDays = 2))
        assertFalse(data.hasRequiredForecastWindow(forecastDays = 0))
    }

    private fun renderInput(data: HourlyForecastData) = ForecastRenderInput(
        hourlyData = data,
        place = SavedPlace.fromCoordinates(data.latitude, data.longitude).copy(name = "Interlaken"),
        requestedModel = ForecastModel.ICON_D2,
        fetchedAtMillis = 1_752_220_800_000L,
    )

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
