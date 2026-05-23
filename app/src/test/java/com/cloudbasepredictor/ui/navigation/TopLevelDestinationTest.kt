package com.cloudbasepredictor.ui.navigation

import com.cloudbasepredictor.model.PlaceLocation
import org.junit.Assert.assertEquals
import org.junit.Test

class TopLevelDestinationTest {
    @Test
    fun forecastRoute_usesPlaceLocationPathArgument() {
        assertEquals(
            "forecast/{placeLocation}?placeName={placeName}",
            TopLevelDestination.Forecast.route,
        )
    }

    @Test
    fun forecastRoute_buildsConcreteRouteForLocation() {
        val route = TopLevelDestination.forecastRoute(
            PlaceLocation(
                latitude = 46.55823456,
                longitude = 7.83537654,
            ),
        )

        assertEquals("forecast/46.558235,7.835377", route)
    }

    @Test
    fun forecastRoute_addsOptionalEncodedPlaceName() {
        val route = TopLevelDestination.forecastRoute(
            PlaceLocation(
                latitude = 45.3069,
                longitude = 5.88806,
                name = "Saint Hilaire du Touvet",
            ),
        )

        assertEquals(
            "forecast/45.306900,5.888060?placeName=Saint%20Hilaire%20du%20Touvet",
            route,
        )
    }
}
