package com.cloudbasepredictor.web.map

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.cloudbasepredictor.data.launch.LaunchSiteRepository
import com.cloudbasepredictor.data.map.MapCameraPosition
import com.cloudbasepredictor.data.map.MapLayerPreference
import com.cloudbasepredictor.model.ParaglidingLaunchSite
import com.cloudbasepredictor.model.PlaceLocation
import com.cloudbasepredictor.model.SavedPlace
import com.cloudbasepredictor.web.WebRouteState
import com.cloudbasepredictor.web.preferences.WebPreferencesState
import com.cloudbasepredictor.web.preview.WebPreviewData
import androidx.compose.ui.tooling.preview.Preview

data class WebMapMarker(
    val id: String,
    val location: PlaceLocation,
    val title: String,
    val kind: WebMapMarkerKind,
    val launchSite: ParaglidingLaunchSite? = null,
)

enum class WebMapMarkerKind {
    FAVORITE,
    SELECTED,
    LAUNCH_SITE,
}

data class WebMapRenderState(
    val initialCamera: MapCameraPosition,
    val layer: MapLayerPreference,
    val markers: List<WebMapMarker>,
    val selectedLaunchSite: ParaglidingLaunchSite? = null,
    val showLaunchSites: Boolean = false,
)

@Composable
internal expect fun WebMapDestination(
    routeState: WebRouteState,
    preferences: WebPreferencesState,
    favoritePlaces: List<SavedPlace>,
    savedCamera: MapCameraPosition?,
    launchSiteRepository: LaunchSiteRepository,
    searchLocations: suspend (String) -> List<PlaceLocation>,
    onMapLayerSelected: (MapLayerPreference) -> Unit,
    onLocationConfirmed: (PlaceLocation) -> Unit,
    onCameraChanged: (MapCameraPosition) -> Unit,
    onAddFavorite: (SavedPlace) -> Unit,
    modifier: Modifier = Modifier,
)

@Preview
@Composable
private fun WebMapModelsPreview() {
    WebMapDestination(
        routeState = WebPreviewData.mapRoute,
        preferences = WebPreviewData.preferences,
        favoritePlaces = WebPreviewData.favoritePlaces,
        savedCamera = null,
        launchSiteRepository = WebPreviewData.launchSiteRepository,
        searchLocations = { emptyList() },
        onMapLayerSelected = {},
        onLocationConfirmed = {},
        onCameraChanged = {},
        onAddFavorite = {},
    )
}
