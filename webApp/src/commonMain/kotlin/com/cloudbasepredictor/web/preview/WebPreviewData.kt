package com.cloudbasepredictor.web.preview

import com.cloudbasepredictor.model.PlaceLocation
import com.cloudbasepredictor.model.SavedPlace
import com.cloudbasepredictor.web.WebDestination
import com.cloudbasepredictor.web.WebRouteState
import com.cloudbasepredictor.web.preferences.WebPreferencesState

object WebPreviewData {
    val brauneck = PlaceLocation(
        latitude = 47.6636,
        longitude = 11.5269,
        name = "Brauneck",
    )
    val forecastRoute = WebRouteState(
        destination = WebDestination.Forecast,
        location = brauneck,
    )
    val mapRoute = WebRouteState(
        destination = WebDestination.Map,
        location = brauneck,
    )
    val favoritePlaces = listOf(
        SavedPlace.fromCoordinates(brauneck.latitude, brauneck.longitude).copy(
            name = requireNotNull(brauneck.name),
            isFavorite = true,
        ),
    )
    val preferences = WebPreferencesState()
}
