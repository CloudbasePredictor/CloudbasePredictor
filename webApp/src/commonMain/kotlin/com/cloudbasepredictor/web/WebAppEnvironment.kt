package com.cloudbasepredictor.web

import com.cloudbasepredictor.data.launch.LaunchSiteRepository
import com.cloudbasepredictor.data.map.MapCameraStore
import com.cloudbasepredictor.data.place.FavoritePlaceStore
import com.cloudbasepredictor.model.PlaceLocation
import com.cloudbasepredictor.web.forecast.WebForecastRepository
import com.cloudbasepredictor.web.preferences.WebPreferences

@Suppress("LongParameterList")
class WebAppEnvironment(
    val forecastRepository: WebForecastRepository,
    val preferences: WebPreferences,
    val favoritePlaceStore: FavoritePlaceStore,
    val mapCameraStore: MapCameraStore,
    val launchSiteRepository: LaunchSiteRepository,
    val searchLocations: suspend (String) -> List<PlaceLocation>,
    private val closeAction: () -> Unit = {},
) {
    fun close() = closeAction()
}

expect fun createWebAppEnvironment(): WebAppEnvironment
