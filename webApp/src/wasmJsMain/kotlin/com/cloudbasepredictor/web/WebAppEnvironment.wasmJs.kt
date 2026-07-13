@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package com.cloudbasepredictor.web

import com.cloudbasepredictor.data.remote.KtorOpenMeteoApi
import com.cloudbasepredictor.data.remote.OpenMeteoRemoteDataSource
import com.cloudbasepredictor.data.remote.createCloudbaseHttpClient
import com.cloudbasepredictor.web.forecast.WebChartViewportStore
import com.cloudbasepredictor.web.forecast.WebForecastRepository
import com.cloudbasepredictor.web.forecast.WebForecastSource
import com.cloudbasepredictor.web.i18n.browserLanguageTag
import com.cloudbasepredictor.web.launch.KtorLaunchSiteSnapshotSource
import com.cloudbasepredictor.web.launch.StaticLaunchSiteRepository
import com.cloudbasepredictor.web.launch.browserLaunchSiteSnapshotBaseUrl
import com.cloudbasepredictor.web.map.WebMapCameraStore
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
        mapCameraStore = WebMapCameraStore(keyValueStorage),
        chartViewportStore = WebChartViewportStore(keyValueStorage),
        launchSiteRepository = StaticLaunchSiteRepository(
            source = KtorLaunchSiteSnapshotSource(
                httpClient = httpClient,
                baseUrl = browserLaunchSiteSnapshotBaseUrl(),
            ),
        ),
        systemLanguageTag = browserLanguageTag(),
        closeAction = httpClient::close,
    )
}

private fun browserCurrentTimeMillis(): Long = browserCurrentTimeMillisAsDouble().toLong()

private fun browserCurrentTimeMillisAsDouble(): Double = js("Date.now()")
