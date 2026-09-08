package com.cloudbasepredictor.web.forecast

import com.cloudbasepredictor.data.forecast.ForecastCacheRecord
import com.cloudbasepredictor.data.forecast.IncompleteForecastDataException
import com.cloudbasepredictor.data.forecast.InMemoryForecastCacheStore
import com.cloudbasepredictor.data.remote.HourlyForecastData
import com.cloudbasepredictor.data.remote.HourlyPoint
import com.cloudbasepredictor.model.DailyForecast
import com.cloudbasepredictor.model.ForecastModel
import com.cloudbasepredictor.model.PlaceLocation
import com.cloudbasepredictor.model.SavedPlace
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
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

    @Test
    fun invalidForcedRefreshPreservesTheUsableDurableForecast() = kotlinx.coroutines.test.runTest {
        val valid = sampleHourlyData(dayCount = 2)
        var response = valid
        val cache = InMemoryForecastCacheStore()
        val repository = WebForecastRepository(
            source = WebForecastSource { _, model, _ -> model to response },
            cacheStore = cache,
            json = Json,
            nowMillis = { NOW_MILLIS },
        )
        repository.load(TEST_LOCATION, ForecastModel.ICON_SEAMLESS, forecastDays = 2)
        val placeId = SavedPlace.fromCoordinates(TEST_LOCATION.latitude, TEST_LOCATION.longitude).id
        val saved = cache.read(placeId, ForecastModel.ICON_SEAMLESS.apiName)
        val invalidResponses = listOf(
            valid.copy(hourlyPoints = emptyList()),
            valid.copy(dailyForecasts = emptyList()),
            valid.copy(hourlyPoints = valid.hourlyPoints.map { it.copy(temperature2mC = null) }),
            valid.copy(hourlyPoints = valid.hourlyPoints.map { it.copy(dewPoint2mC = null) }),
            valid.copy(hourlyPoints = valid.hourlyPoints.map {
                it.copy(windSpeed10mKmh = null, windDirection10mDeg = null)
            }),
            valid.copy(hourlyPoints = valid.hourlyPoints.filter { it.date == "2026-07-11" }),
        )

        invalidResponses.forEach { incomplete ->
            response = incomplete
            assertFailsWith<IncompleteForecastDataException> {
                repository.load(TEST_LOCATION, ForecastModel.ICON_SEAMLESS, forecastDays = 2, forceRefresh = true)
            }
            assertEquals(saved, cache.read(placeId, ForecastModel.ICON_SEAMLESS.apiName))
        }

        val restored = repository.load(TEST_LOCATION, ForecastModel.ICON_SEAMLESS, forecastDays = 2)
        assertTrue(restored.fromCache)
        assertEquals(valid, restored.hourlyData)
    }

    @Test
    fun partialHourRefreshPreservesTheUsableDurableForecast() = kotlinx.coroutines.test.runTest {
        val valid = sampleHourlyData(dayCount = 1)
        var response = valid
        val cache = InMemoryForecastCacheStore()
        val repository = WebForecastRepository(
            source = WebForecastSource { _, model, _ -> model to response },
            cacheStore = cache,
            json = Json,
            nowMillis = { NOW_MILLIS },
        )
        repository.load(TEST_LOCATION, ForecastModel.ICON_SEAMLESS, forecastDays = 1)
        val placeId = SavedPlace.fromCoordinates(TEST_LOCATION.latitude, TEST_LOCATION.longitude).id
        val saved = cache.read(placeId, ForecastModel.ICON_SEAMLESS.apiName)
        val incompleteHours = listOf(
            valid.hourlyPoints.map { if (it.hour == 18) it.copy(temperature2mC = null) else it },
            valid.hourlyPoints.map { if (it.hour == 18) it.copy(dewPoint2mC = null) else it },
        )

        incompleteHours.forEach { hourlyPoints ->
            response = valid.copy(hourlyPoints = hourlyPoints)
            assertFailsWith<IncompleteForecastDataException> {
                repository.load(TEST_LOCATION, ForecastModel.ICON_SEAMLESS, forecastDays = 1, forceRefresh = true)
            }
            assertEquals(saved, cache.read(placeId, ForecastModel.ICON_SEAMLESS.apiName))
            val restored = repository.load(TEST_LOCATION, ForecastModel.ICON_SEAMLESS, forecastDays = 1)
            assertTrue(restored.fromCache)
            assertEquals(valid, restored.hourlyData)
        }
    }

    @Test
    fun legacyPartialHourCacheIsRefetchedAndReplaced() = kotlinx.coroutines.test.runTest {
        val valid = sampleHourlyData(dayCount = 2)
        val cache = InMemoryForecastCacheStore()
        val placeId = SavedPlace.fromCoordinates(TEST_LOCATION.latitude, TEST_LOCATION.longitude).id
        cache.upsert(
            ForecastCacheRecord(
                placeId = placeId,
                requestedModelApiName = ForecastModel.ICON_SEAMLESS.apiName,
                resolvedModelApiName = ForecastModel.ICON_SEAMLESS.apiName,
                forecastDays = 2,
                hourlyDataJson = Json.encodeToString(
                    HourlyForecastData.serializer(),
                    valid.copy(hourlyPoints = valid.hourlyPoints.map {
                        if (it.hour == 18) it.copy(dewPoint2mC = null) else it
                    }),
                ),
                fetchedAtMillis = NOW_MILLIS,
                nextExpectedUpdateMillis = NOW_MILLIS + 60_000L,
            ),
        )
        var fetchCount = 0
        val repository = WebForecastRepository(
            source = WebForecastSource { _, model, _ ->
                fetchCount++
                model to valid
            },
            cacheStore = cache,
            json = Json,
            nowMillis = { NOW_MILLIS },
        )

        val replacement = repository.load(TEST_LOCATION, ForecastModel.ICON_SEAMLESS, forecastDays = 2)
        val cached = repository.load(TEST_LOCATION, ForecastModel.ICON_SEAMLESS, forecastDays = 2)

        assertEquals(1, fetchCount)
        assertFalse(replacement.fromCache)
        assertTrue(cached.fromCache)
        assertEquals(valid, cached.hourlyData)
    }

    private fun sampleHourlyData(dayCount: Int): HourlyForecastData {
        return HourlyForecastData(
            latitude = TEST_LOCATION.latitude,
            longitude = TEST_LOCATION.longitude,
            elevation = 890.0,
            hourlyPoints = List(dayCount) { index ->
                HourlyPoint(
                    date = "2026-07-${(11 + index).toString().padStart(2, '0')}",
                    hour = 12,
                    temperature2mC = 20.0,
                    dewPoint2mC = 9.0,
                    cloudCoverLowPercent = 20.0,
                    cloudCoverMidPercent = 10.0,
                    cloudCoverHighPercent = 5.0,
                    precipitationMm = 0.0,
                    precipitationProbabilityPercent = 5.0,
                    windSpeed10mKmh = 12.0,
                    windDirection10mDeg = 220.0,
                    capeJKg = 400.0,
                    freezingLevelHeightM = 3_000.0,
                    surfacePressureHpa = 910.0,
                    shortwaveRadiationWm2 = 600.0,
                    sunshineDurationS = 2_700.0,
                    isDay = 1.0,
                    pressureLevels = emptyList(),
                )
            }.flatMap { noon ->
                listOf(noon, noon.copy(hour = 18, temperature2mC = 17.0, shortwaveRadiationWm2 = 150.0))
            },
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
