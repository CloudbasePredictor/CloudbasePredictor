@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package com.cloudbasepredictor.web

import com.cloudbasepredictor.data.remote.KtorOpenMeteoApi
import com.cloudbasepredictor.data.remote.OpenMeteoRemoteDataSource
import com.cloudbasepredictor.data.remote.OpenMeteoGeocodingDataSource
import com.cloudbasepredictor.data.remote.createCloudbaseHttpClient
import com.cloudbasepredictor.web.forecast.WebForecastRepository
import com.cloudbasepredictor.web.forecast.WebForecastSource
import com.cloudbasepredictor.web.preferences.WebPreferences
import com.cloudbasepredictor.web.storage.BrowserIndexedDbForecastCacheStore
import com.cloudbasepredictor.web.storage.BrowserLocalStorageFavoritePlaceStore
import com.cloudbasepredictor.web.storage.BrowserLocalStorageKeyValueStorage
import kotlinx.serialization.json.Json

actual fun createWebAppEnvironment(): WebAppEnvironment {
    val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }
    val httpClient = createCloudbaseHttpClient(jsonConfiguration = json)
    val remoteDataSource = OpenMeteoRemoteDataSource(KtorOpenMeteoApi(httpClient))
    val geocodingDataSource = OpenMeteoGeocodingDataSource(httpClient)
    val cacheStore = BrowserIndexedDbForecastCacheStore()
    val keyValueStorage = BrowserLocalStorageKeyValueStorage()
    return WebAppEnvironment(
        forecastRepository = WebForecastRepository(
            source = WebForecastSource { location, requestedModel, forecastDays ->
                remoteDataSource.getHourlyForecastWithFallback(
                    latitude = location.latitude,
                    longitude = location.longitude,
                    requestedModel = requestedModel,
                    forecastDays = forecastDays,
                )
            },
            cacheStore = cacheStore,
            json = json,
            nowMillis = ::browserCurrentTimeMillis,
        ),
        preferences = WebPreferences(keyValueStorage),
        favoritePlaceStore = BrowserLocalStorageFavoritePlaceStore(),
        searchLocations = geocodingDataSource::search,
        closeAction = httpClient::close,
    )
}

private fun browserCurrentTimeMillis(): Long = browserCurrentTimeMillisAsDouble().toLong()

private fun browserCurrentTimeMillisAsDouble(): Double = js("Date.now()")
