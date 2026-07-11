package com.cloudbasepredictor.web.forecast

import com.cloudbasepredictor.data.forecast.InMemoryForecastCacheStore
import com.cloudbasepredictor.data.remote.HourlyForecastData
import com.cloudbasepredictor.model.DailyForecast
import com.cloudbasepredictor.model.ForecastModel
import com.cloudbasepredictor.model.PlaceLocation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json

class WebForecastRepositoryTest {
    @Test
    fun freshForecastIsPersistedAndReused() = kotlinx.coroutines.test.runTest {
        var fetchCount = 0
        val cache = InMemoryForecastCacheStore()
        val repository = WebForecastRepository(
            source = WebForecastSource { _, requestedModel, forecastDays ->
                fetchCount++
                requestedModel to sampleHourlyData(forecastDays)
            },
            cacheStore = cache,
            json = Json,
            nowMillis = { NOW_MILLIS },
        )

        val first = repository.load(TEST_LOCATION, ForecastModel.ICON_SEAMLESS, forecastDays = 2)
        val second = repository.load(TEST_LOCATION, ForecastModel.ICON_SEAMLESS, forecastDays = 2)

        assertEquals(1, fetchCount)
        assertFalse(first.fromCache)
        assertTrue(second.fromCache)
        assertEquals(first.hourlyData, second.hourlyData)
    }

    @Test
    fun forceRefreshBypassesFreshCache() = kotlinx.coroutines.test.runTest {
        var fetchCount = 0
        val repository = WebForecastRepository(
            source = WebForecastSource { _, requestedModel, forecastDays ->
                fetchCount++
                requestedModel to sampleHourlyData(forecastDays)
            },
            cacheStore = InMemoryForecastCacheStore(),
            json = Json,
            nowMillis = { NOW_MILLIS },
        )

        repository.load(TEST_LOCATION, ForecastModel.ICON_SEAMLESS, forecastDays = 1)
        val refreshed = repository.load(
            TEST_LOCATION,
            ForecastModel.ICON_SEAMLESS,
            forecastDays = 1,
            forceRefresh = true,
        )

        assertEquals(2, fetchCount)
        assertFalse(refreshed.fromCache)
    }

    private fun sampleHourlyData(dayCount: Int): HourlyForecastData {
        return HourlyForecastData(
            latitude = TEST_LOCATION.latitude,
            longitude = TEST_LOCATION.longitude,
            elevation = 890.0,
            hourlyPoints = emptyList(),
            dailyForecasts = List(dayCount) { index ->
                DailyForecast(
                    date = "2026-07-${(11 + index).toString().padStart(2, '0')}",
                    maxTemperatureCelsius = 20.0,
                    minTemperatureCelsius = 9.0,
                    weatherCode = 1,
                )
            },
        )
    }

    private companion object {
        const val NOW_MILLIS = 1_752_225_600_000L
        val TEST_LOCATION = PlaceLocation(47.67, 11.56, "Brauneck")
    }
}
