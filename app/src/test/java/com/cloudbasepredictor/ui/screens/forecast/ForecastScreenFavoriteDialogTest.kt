package com.cloudbasepredictor.ui.screens.forecast

import com.cloudbasepredictor.model.SavedPlace
import org.junit.Assert.assertEquals
import org.junit.Test

class ForecastScreenFavoriteDialogTest {
    @Test
    fun favoriteDialogInitialName_usesFavoriteNameForExistingFavorite() {
        val place = SavedPlace(
            id = "place:47.3769:8.5417",
            name = "Zurich",
            latitude = 47.3769,
            longitude = 8.5417,
            isFavorite = true,
        )

        assertEquals("Zurich", favoriteDialogInitialName(place))
    }

    @Test
    fun favoriteDialogInitialName_usesLaunchSiteNameForNewNamedPlace() {
        val place = SavedPlace(
            id = "place:45.3069:5.8881",
            name = "Saint Hilaire du Touvet",
            latitude = 45.3069,
            longitude = 5.88806,
            isFavorite = false,
        )

        assertEquals("Saint Hilaire du Touvet", favoriteDialogInitialName(place))
    }

    @Test
    fun favoriteDialogInitialName_keepsCoordinatePlaceBlankForNewPoint() {
        val place = SavedPlace.fromCoordinates(
            latitude = 46.55823456,
            longitude = 7.83537654,
        )

        assertEquals("", favoriteDialogInitialName(place))
    }
}
