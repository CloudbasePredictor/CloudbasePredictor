package com.cloudbasepredictor.ui.navigation

import com.cloudbasepredictor.model.PlaceLocation
import org.junit.Assert.assertEquals
import org.junit.Test

class TopLevelDestinationTest {
    @Test
    fun forecastRoute_usesPlaceLocationPathArgument() {
        assertEquals("forecast/{placeLocation}", TopLevelDestination.Forecast.route)
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
}
