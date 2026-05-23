package com.cloudbasepredictor.ui.screens.map

import com.cloudbasepredictor.model.ParaglidingLaunchSite
import com.cloudbasepredictor.model.SavedPlace
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MapScreenMarkerLayerDataTest {
    private val favoritePlace = SavedPlace(
        id = "favorite-interlaken",
        name = "Interlaken",
        latitude = 46.6863,
        longitude = 7.8632,
        isFavorite = true,
    )
    private val otherFavoritePlace = SavedPlace(
        id = "favorite-zurich",
        name = "Zurich",
        latitude = 47.3769,
        longitude = 8.5417,
        isFavorite = true,
    )
    private val coordinatePlace = SavedPlace(
        id = "place:46.9480:7.4474",
        name = "46.9480, 7.4474",
        latitude = 46.9480,
        longitude = 7.4474,
        isFavorite = false,
    )
    private val launchSite = ParaglidingLaunchSite(
        id = "6176",
        name = "Saint Hilaire du Touvet",
        latitude = 45.3069,
        longitude = 5.88806,
    )
    private val otherLaunchSite = ParaglidingLaunchSite(
        id = "7342",
        name = "Annecy Planfait",
        latitude = 45.8401,
        longitude = 6.2182,
    )

    @Test
    fun buildMapMarkerLayerData_whenFavoriteIsSelected_movesItToSelectedFavoriteLayer() {
        val data = buildMapMarkerLayerData(
            MapUiState(
                selectedPlace = favoritePlace,
                favoritePlaces = listOf(favoritePlace, otherFavoritePlace),
            ),
        )

        assertNull(data.selectedCoordinatePlace)
        assertEquals(favoritePlace, data.selectedFavoritePlace)
        assertEquals(listOf(otherFavoritePlace), data.unselectedFavoritePlaces)
        assertEquals(listOf(favoritePlace, otherFavoritePlace), data.favoritePlacesForInteraction)
    }

    @Test
    fun buildMapMarkerLayerData_whenCoordinateIsSelected_keepsFavoritesInNormalLayer() {
        val data = buildMapMarkerLayerData(
            MapUiState(
                selectedPlace = coordinatePlace,
                favoritePlaces = listOf(favoritePlace, otherFavoritePlace),
            ),
        )

        assertEquals(coordinatePlace, data.selectedCoordinatePlace)
        assertNull(data.selectedFavoritePlace)
        assertEquals(listOf(favoritePlace, otherFavoritePlace), data.unselectedFavoritePlaces)
        assertEquals(listOf(favoritePlace, otherFavoritePlace), data.favoritePlacesForInteraction)
    }

    @Test
    fun buildMapMarkerLayerData_whenNewFavoriteIsSelected_keepsItAvailableForInteraction() {
        val pendingFavorite = favoritePlace.copy(id = "favorite-pending", name = "Pending favorite")

        val data = buildMapMarkerLayerData(
            MapUiState(
                selectedPlace = pendingFavorite,
                favoritePlaces = listOf(favoritePlace),
            ),
        )

        assertNull(data.selectedCoordinatePlace)
        assertEquals(pendingFavorite, data.selectedFavoritePlace)
        assertEquals(listOf(favoritePlace), data.unselectedFavoritePlaces)
        assertEquals(listOf(favoritePlace, pendingFavorite), data.favoritePlacesForInteraction)
    }

    @Test
    fun buildMapMarkerLayerData_whenLaunchSiteIsSelected_movesItToSelectedLaunchSiteLayer() {
        val data = buildMapMarkerLayerData(
            MapUiState(
                selectedLaunchSite = launchSite,
                launchSites = listOf(launchSite, otherLaunchSite),
            ),
        )

        assertEquals(launchSite, data.selectedLaunchSite)
        assertEquals(listOf(otherLaunchSite), data.unselectedLaunchSites)
        assertEquals(listOf(launchSite, otherLaunchSite), data.launchSitesForInteraction)
    }

    @Test
    fun buildMapMarkerLayerData_whenSelectedLaunchSiteIsOutsideVisibleSites_keepsItAvailableForInteraction() {
        val data = buildMapMarkerLayerData(
            MapUiState(
                selectedLaunchSite = launchSite,
                launchSites = listOf(otherLaunchSite),
            ),
        )

        assertEquals(launchSite, data.selectedLaunchSite)
        assertEquals(listOf(otherLaunchSite), data.unselectedLaunchSites)
        assertEquals(listOf(otherLaunchSite, launchSite), data.launchSitesForInteraction)
    }
}
