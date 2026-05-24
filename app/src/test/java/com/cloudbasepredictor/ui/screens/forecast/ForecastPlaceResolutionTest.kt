package com.cloudbasepredictor.ui.screens.forecast

import com.cloudbasepredictor.model.PlaceLocation
import com.cloudbasepredictor.model.SavedPlace
import org.junit.Assert.assertEquals
import org.junit.Test

class ForecastPlaceResolutionTest {
    @Test
    fun resolveForecastPlace_namedLaunchLocationWithoutFavoriteKeepsLaunchName() {
        val place = PlaceLocation(
            latitude = 45.3069,
            longitude = 5.88806,
            name = "Saint Hilaire du Touvet",
        ).resolveForecastPlace(favoritePlaces = emptyList())

        assertEquals(
            SavedPlace(
                id = "place:45.3069:5.8881",
                name = "Saint Hilaire du Touvet",
                latitude = 45.3069,
                longitude = 5.88806,
                isFavorite = false,
            ),
            place,
        )
    }

    @Test
    fun resolveForecastPlace_namedLaunchLocationWithSavedFavoriteUsesFavorite() {
        val favorite = SavedPlace(
            id = "place:45.3069:5.8881",
            name = "Favorite Saint Hilaire",
            latitude = 45.3069,
            longitude = 5.88806,
            isFavorite = true,
        )

        val place = PlaceLocation(
            latitude = 45.3069,
            longitude = 5.88806,
            name = "Saint Hilaire du Touvet",
        ).resolveForecastPlace(favoritePlaces = listOf(favorite))

        assertEquals(favorite, place)
    }

    @Test
    fun resolveForecastPlace_namedLaunchLocationWithLegacyFavoriteIdUsesNearbyFavorite() {
        val favorite = SavedPlace(
            id = "favorite-saint-hilaire",
            name = "Favorite Saint Hilaire",
            latitude = 45.3069,
            longitude = 5.88806,
            isFavorite = true,
        )

        val place = PlaceLocation(
            latitude = 45.3069,
            longitude = 5.88806,
            name = "Saint Hilaire du Touvet",
        ).resolveForecastPlace(favoritePlaces = listOf(favorite))

        assertEquals(favorite, place)
    }
}
