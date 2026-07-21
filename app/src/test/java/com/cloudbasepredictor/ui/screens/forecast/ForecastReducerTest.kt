package com.cloudbasepredictor.ui.screens.forecast

import com.cloudbasepredictor.R
import com.cloudbasepredictor.data.map.MapLayerPreference
import com.cloudbasepredictor.data.remote.HourlyForecastData
import com.cloudbasepredictor.data.remote.HourlyPoint
import com.cloudbasepredictor.data.remote.PressureLevelPoint
import com.cloudbasepredictor.data.units.UnitPreset
import com.cloudbasepredictor.data.units.resolveDisplayUnits
import com.cloudbasepredictor.model.DailyForecast
import com.cloudbasepredictor.model.ForecastMode
import com.cloudbasepredictor.model.ForecastModel
import com.cloudbasepredictor.model.ForecastSnapshot
import com.cloudbasepredictor.model.SavedPlace
import com.cloudbasepredictor.ui.text.AppStringResources
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Fast JVM coverage for the extracted [reduceForecastUiState] reducer and the localized
 * [buildForecastText] summary. Neither touches Android/coroutine machinery, so the state
 * reduction can be exercised without constructing [ForecastViewModel].
 */
class ForecastReducerTest {

    private val incompleteError = "incomplete"
    private val summary = "SUMMARY"
    private val place = SavedPlace(
        id = "place:46.5582:7.8354",
        name = "Interlaken",
        latitude = 46.5582,
        longitude = 7.8354,
        isFavorite = false,
    )

    @Test
    fun readyStateHappyPath() {
        val state = reduce(
            inputs = inputs(
                place = place,
                snapshot = snapshot(hourly = forecastData()),
            ),
        )

        assertTrue(state is ForecastReadyUiState)
        state as ForecastReadyUiState
        assertEquals(place, state.selectedPlace)
        assertEquals(summary, state.forecastText)
        assertTrue(state.dayChips.isNotEmpty())
    }

    @Test
    fun failedRefreshPreservesCachedForecast() {
        // A background refresh failed (error present) but a usable cached forecast still exists,
        // so the chart must stay up rather than being wiped by a full-screen error.
        val state = reduce(
            inputs = inputs(
                place = place,
                snapshot = snapshot(hourly = forecastData()),
                errorMessage = "network down",
            ),
        )

        assertTrue("stale cache must survive a failed refresh", state is ForecastReadyUiState)
    }

    @Test
    fun errorWithNoCachedForecastShowsError() {
        val state = reduce(
            inputs = inputs(
                place = place,
                snapshot = null,
                errorMessage = "network down",
            ),
        )

        assertTrue(state is ForecastErrorUiState)
        assertEquals("network down", (state as ForecastErrorUiState).errorMessage)
    }

    @Test
    fun incompleteSnapshotWithoutHourlyDataShowsIncompleteError() {
        val state = reduce(
            inputs = inputs(
                place = place,
                snapshot = snapshot(hourly = null),
            ),
        )

        assertTrue(state is ForecastErrorUiState)
        assertEquals(incompleteError, (state as ForecastErrorUiState).errorMessage)
    }

    @Test
    fun loadingWhileSnapshotPresentKeepsForecastVisibleAndMarksItRefreshing() {
        val state = reduce(
            inputs = inputs(
                place = place,
                snapshot = snapshot(hourly = forecastData()),
                isLoading = true,
            ),
        )

        assertTrue(state is ForecastReadyUiState)
        assertTrue((state as ForecastReadyUiState).isRefreshing)
    }

    @Test
    fun noPlaceCoercesNegativeDayIndexToZero() {
        val state = reduce(
            inputs = inputs(
                place = null,
                snapshot = null,
                selectedDayIndex = -5,
            ),
        )

        assertTrue(state is ForecastNoPlaceUiState)
        assertEquals(0, state.selectedDayIndex)
    }

    @Test
    fun readyStateCoercesOutOfRangeDayIndex() {
        val state = reduce(
            inputs = inputs(
                place = place,
                snapshot = snapshot(hourly = forecastData()),
                selectedDayIndex = 99,
            ),
        )

        assertTrue(state is ForecastReadyUiState)
        state as ForecastReadyUiState
        assertTrue(state.selectedDayIndex >= 0)
        assertTrue(state.selectedDayIndex <= state.dayChips.lastIndex)
        assertNotEquals(99, state.selectedDayIndex)
    }

    @Test
    fun summaryUsesDistinctLocalizedStringsPerModeAndCarriesPlaceAndTemperature() {
        val resources = FakeStringResources()
        val snapshot = snapshot(hourly = forecastData())

        val thermic = buildForecastText(resources, ForecastMode.THERMIC, place, snapshot, 0)
        val stuve = buildForecastText(resources, ForecastMode.STUVE, place, snapshot, 0)

        // Each mode resolves a different string resource rather than a hardcoded English literal.
        assertNotEquals(thermic, stuve)
        assertTrue(thermic.contains(place.name))
        // Thermic summary formats the daily high/low from the snapshot.
        assertTrue(thermic.contains("22.0"))
        assertTrue(thermic.contains("11.0"))
        assertTrue(thermic.contains("res:${R.string.weather_partly_cloudy}"))
        assertFalse(thermic.contains("Partly cloudy"))
    }

    @Test
    fun summaryFallsBackToPendingStringWhenDayMissing() {
        val resources = FakeStringResources()
        val emptySnapshot = ForecastSnapshot(days = emptyList(), updatedAtUtcMillis = 0L)

        val text = buildForecastText(resources, ForecastMode.WIND, place, emptySnapshot, 0)

        assertTrue(text.contains(place.name))
    }

    private fun reduce(inputs: ForecastUiInputs): ForecastUiState =
        reduceForecastUiState(
            inputs = inputs,
            selectedModel = ForecastModel.ICON_SEAMLESS,
            favoritePlaces = emptyList(),
            preferences = MapAndUnitPreferences(
                mapLayer = MapLayerPreference.OPENFREEMAP,
                unitPreset = UnitPreset.METRIC_KMH,
                displayUnits = UnitPreset.METRIC_KMH.resolveDisplayUnits(),
            ),
            incompleteDataError = incompleteError,
            buildSummary = { _, _, _, _ -> summary },
        )

    private fun inputs(
        place: SavedPlace?,
        snapshot: ForecastSnapshot?,
        isLoading: Boolean = false,
        errorMessage: String? = null,
        selectedDayIndex: Int = 0,
    ): ForecastUiInputs = ForecastUiInputs(
        place = place,
        snapshot = snapshot,
        chartContext = ForecastChartContext(
            selectedForecastMode = ForecastMode.THERMIC,
            selectedDayIndex = selectedDayIndex,
            chartViewport = ForecastChartViewport(),
            stuveHour = 12,
        ),
        isLoading = isLoading,
        errorMessage = errorMessage,
    )

    private fun snapshot(hourly: HourlyForecastData?): ForecastSnapshot = ForecastSnapshot(
        days = dailyForecasts(),
        updatedAtUtcMillis = 1_752_220_800_000L,
        hourlyData = hourly,
        resolvedModel = ForecastModel.ICON_SEAMLESS,
    )

    private fun dailyForecasts(): List<DailyForecast> = FORECAST_DATES.mapIndexed { index, date ->
        DailyForecast(
            date = date,
            maxTemperatureCelsius = 22.0 + index,
            minTemperatureCelsius = 11.0 + index,
            weatherCode = if (index == 0) 1 else 2,
        )
    }

    private fun forecastData(): HourlyForecastData = HourlyForecastData(
        latitude = 46.5582,
        longitude = 7.8354,
        elevation = 580.0,
        hourlyPoints = FORECAST_DATES.flatMap { date ->
            (FIRST_HOUR..LAST_HOUR).map { hour -> forecastPoint(date, hour) }
        },
        dailyForecasts = dailyForecasts(),
    )

    private fun forecastPoint(date: String, hour: Int): HourlyPoint = HourlyPoint(
        date = date,
        hour = hour,
        temperature2mC = 12.0 + (hour - FIRST_HOUR) * 0.6,
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

    private companion object {
        val FORECAST_DATES = listOf(
            "2026-07-11",
            "2026-07-12",
            "2026-07-13",
            "2026-07-14",
            "2026-07-15",
            "2026-07-16",
            "2026-07-17",
        )
        const val FIRST_HOUR = 6
        const val LAST_HOUR = 22
    }
}

/** Deterministic string resolver: encodes the resource id and args so tests can assert on them. */
private class FakeStringResources : AppStringResources {
    override fun getString(resId: Int): String = "res:$resId"

    override fun getString(resId: Int, vararg formatArgs: Any): String =
        "res:$resId${formatArgs.joinToString(prefix = "(", postfix = ")")}"
}
