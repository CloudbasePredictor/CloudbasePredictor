package com.cloudbasepredictor.ui.screens.map

import com.cloudbasepredictor.model.FavoriteLaunchSite
import com.cloudbasepredictor.model.ParaglidingLaunchSite
import com.cloudbasepredictor.model.SavedPlace
import com.cloudbasepredictor.model.mergeColocatedFavoriteLaunchSites
import com.cloudbasepredictor.model.nearestColocatedLaunchSite

internal data class MapMarkerLayerData(
    val selectedCoordinatePlace: SavedPlace?,
    val unselectedFavoritePlaces: List<SavedPlace>,
    val selectedFavoritePlace: SavedPlace?,
    val unselectedFavoriteLaunchSites: List<FavoriteLaunchSiteMarker>,
    val selectedFavoriteLaunchSite: FavoriteLaunchSiteMarker?,
    val favoritePlacesForInteraction: List<SavedPlace>,
    val favoriteLabelPlaces: List<SavedPlace>,
    val unselectedLaunchSites: List<ParaglidingLaunchSite>,
    val selectedLaunchSite: ParaglidingLaunchSite?,
    val launchSitesForInteraction: List<ParaglidingLaunchSite>,
)

internal data class FavoriteLaunchSiteMarker(
    val favoritePlace: SavedPlace,
    val launchSite: ParaglidingLaunchSite,
)

internal fun buildMapMarkerLayerData(uiState: MapUiState): MapMarkerLayerData {
    val favoriteLaunchSiteMarkers = buildFavoriteLaunchSiteMarkers(
        favoritePlaces = uiState.favoritePlaces,
        launchSites = uiState.launchSites,
    )
    val selectedPlaceFavoriteLaunchSite = uiState.selectedPlace
        ?.takeIf { selectedPlace -> selectedPlace.isSelectedFavorite(uiState.favoritePlaces) }
        ?.let { selectedPlace ->
            favoriteLaunchSiteMarkers.firstOrNull { marker ->
                marker.favoritePlace.id == selectedPlace.id
            } ?: favoriteLaunchSiteMarkerForFavorite(
                favoritePlace = selectedPlace.copy(isFavorite = true),
                launchSites = uiState.launchSites,
            )
        }
    val selectedLaunchSiteFavoriteMarker = uiState.selectedLaunchSite?.let { selectedLaunchSite ->
        favoriteLaunchSiteMarkers.firstOrNull { marker ->
            marker.launchSite.id == selectedLaunchSite.id
        }
    }
    val selectedFavoriteLaunchSite = selectedPlaceFavoriteLaunchSite ?: selectedLaunchSiteFavoriteMarker
    val selectedFavoritePlace = mapSelectedFavoritePlace(
        selectedPlace = uiState.selectedPlace,
        favoritePlaces = uiState.favoritePlaces,
    ).takeIf { selectedFavoriteLaunchSite == null }
    val selectedCoordinatePlace = uiState.selectedPlace?.takeIf { selectedPlace ->
        selectedFavoritePlace?.id != selectedPlace.id &&
            selectedFavoriteLaunchSite?.favoritePlace?.id != selectedPlace.id
    }
    val favoriteLaunchSiteFavoriteIds = favoriteLaunchSiteMarkers.map { marker ->
        marker.favoritePlace.id
    }.toSet()
    val favoritePlacesForInteraction = favoritePlacesWithSelected(
        favoritePlaces = uiState.favoritePlaces.filterNot { place ->
            place.id in favoriteLaunchSiteFavoriteIds
        },
        selectedFavoritePlace = selectedFavoritePlace,
    )
    val favoriteLabelPlaces = favoritePlacesWithSelected(
        favoritePlaces = uiState.favoritePlaces,
        selectedFavoritePlace = selectedFavoritePlace ?: selectedFavoriteLaunchSite?.favoritePlace,
    )
    val hiddenFavoriteIds = favoriteLaunchSiteFavoriteIds +
        listOfNotNull(
            selectedFavoritePlace?.id,
            selectedFavoriteLaunchSite?.favoritePlace?.id,
        )
    val unselectedFavoritePlaces = uiState.favoritePlaces.filterNot { place ->
        place.id in hiddenFavoriteIds
    }
    val selectedLaunchSite = uiState.selectedLaunchSite?.takeIf { site ->
        selectedFavoriteLaunchSite?.launchSite?.id != site.id
    }
    val launchSitesForInteraction = launchSitesWithSelected(
        launchSites = uiState.launchSites,
        selectedLaunchSite = uiState.selectedLaunchSite ?: selectedFavoriteLaunchSite?.launchSite,
    )
    val favoriteLaunchSiteIds = favoriteLaunchSiteMarkers.map { marker ->
        marker.launchSite.id
    }.toSet() + listOfNotNull(selectedFavoriteLaunchSite?.launchSite?.id)
    val unselectedFavoriteLaunchSites = favoriteLaunchSiteMarkers.filterNot { marker ->
        marker.launchSite.id == selectedFavoriteLaunchSite?.launchSite?.id
    }
    val unselectedLaunchSites = if (selectedLaunchSite == null) {
        uiState.launchSites.filterNot { site -> site.id in favoriteLaunchSiteIds }
    } else {
        uiState.launchSites.filterNot { site ->
            site.id == selectedLaunchSite.id || site.id in favoriteLaunchSiteIds
        }
    }

    return MapMarkerLayerData(
        selectedCoordinatePlace = selectedCoordinatePlace,
        unselectedFavoritePlaces = unselectedFavoritePlaces,
        selectedFavoritePlace = selectedFavoritePlace,
        unselectedFavoriteLaunchSites = unselectedFavoriteLaunchSites,
        selectedFavoriteLaunchSite = selectedFavoriteLaunchSite,
        favoritePlacesForInteraction = favoritePlacesForInteraction,
        favoriteLabelPlaces = favoriteLabelPlaces,
        unselectedLaunchSites = unselectedLaunchSites,
        selectedLaunchSite = selectedLaunchSite,
        launchSitesForInteraction = launchSitesForInteraction,
    )
}

private fun mapSelectedFavoritePlace(
    selectedPlace: SavedPlace?,
    favoritePlaces: List<SavedPlace>,
): SavedPlace? {
    return selectedPlace?.takeIf { place ->
        place.isFavorite || favoritePlaces.any { favorite -> favorite.id == place.id }
    }
}

private fun SavedPlace.isSelectedFavorite(favoritePlaces: List<SavedPlace>): Boolean {
    return isFavorite || favoritePlaces.any { favorite -> favorite.id == id }
}

private fun favoritePlacesWithSelected(
    favoritePlaces: List<SavedPlace>,
    selectedFavoritePlace: SavedPlace?,
): List<SavedPlace> {
    if (selectedFavoritePlace == null) return favoritePlaces
    if (favoritePlaces.any { place -> place.id == selectedFavoritePlace.id }) return favoritePlaces
    return favoritePlaces + selectedFavoritePlace
}

private fun launchSitesWithSelected(
    launchSites: List<ParaglidingLaunchSite>,
    selectedLaunchSite: ParaglidingLaunchSite?,
): List<ParaglidingLaunchSite> {
    if (selectedLaunchSite == null) return launchSites
    if (launchSites.any { site -> site.id == selectedLaunchSite.id }) return launchSites
    return launchSites + selectedLaunchSite
}

// Colocation matching (a favorite saved on a launch site) lives in :shared so Android and web merge
// markers identically; this only adapts the shared result to the Android marker type.
private fun buildFavoriteLaunchSiteMarkers(
    favoritePlaces: List<SavedPlace>,
    launchSites: List<ParaglidingLaunchSite>,
): List<FavoriteLaunchSiteMarker> {
    return mergeColocatedFavoriteLaunchSites(favoritePlaces, launchSites).map { it.toMarker() }
}

private fun favoriteLaunchSiteMarkerForFavorite(
    favoritePlace: SavedPlace,
    launchSites: List<ParaglidingLaunchSite>,
): FavoriteLaunchSiteMarker? {
    return nearestColocatedLaunchSite(favoritePlace, launchSites)?.toMarker()
}

private fun FavoriteLaunchSite.toMarker(): FavoriteLaunchSiteMarker {
    return FavoriteLaunchSiteMarker(favoritePlace = favorite, launchSite = launchSite)
}
