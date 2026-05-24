package com.cloudbasepredictor.ui.screens.map

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cloudbasepredictor.data.launch.LaunchSiteBounds
import com.cloudbasepredictor.data.launch.LaunchSiteDisplayRepository
import com.cloudbasepredictor.data.launch.LaunchSiteRepository
import com.cloudbasepredictor.data.map.MapLayerPreference
import com.cloudbasepredictor.data.map.MapLayerRepository
import com.cloudbasepredictor.data.place.PlaceRepository
import com.cloudbasepredictor.model.ParaglidingLaunchSite
import com.cloudbasepredictor.model.PlaceLocation
import com.cloudbasepredictor.model.SavedPlace
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber

data class MapCameraData(
    val latitude: Double,
    val longitude: Double,
    val zoom: Double,
)

data class MapUiState(
    val selectedPlace: SavedPlace? = null,
    val selectedLaunchSite: ParaglidingLaunchSite? = null,
    val favoritePlaces: List<SavedPlace> = emptyList(),
    val launchSites: List<ParaglidingLaunchSite> = emptyList(),
    val showLaunchSites: Boolean = true,
    val initialCamera: MapCameraData? = null,
    val mapLayer: MapLayerPreference = MapLayerPreference.OPENFREEMAP,
)

sealed interface MapEvent {
    data class OpenForecast(val placeLocation: PlaceLocation) : MapEvent
}

@HiltViewModel
class MapViewModel @Inject constructor(
    private val placeRepository: PlaceRepository,
    private val mapLayerRepository: MapLayerRepository,
    private val launchSiteRepository: LaunchSiteRepository,
    private val launchSiteDisplayRepository: LaunchSiteDisplayRepository,
    @param:ApplicationContext private val context: Context,
) : ViewModel() {
    private val selectedPlaceDraft = MutableStateFlow<SavedPlace?>(null)
    private val selectedLaunchSiteDraft = MutableStateFlow<ParaglidingLaunchSite?>(null)
    private val visibleLaunchSites = MutableStateFlow<List<ParaglidingLaunchSite>>(emptyList())
    private val mutableEvents = MutableSharedFlow<MapEvent>()
    private val prefs = context.getSharedPreferences("map_camera", Context.MODE_PRIVATE)
    private var launchSiteLoadJob: Job? = null
    private var lastLaunchSiteBoundsKey: String? = null

    val events = mutableEvents.asSharedFlow()

    private val mapPreferences = combine(
        mapLayerRepository.selectedLayer,
        launchSiteDisplayRepository.showLaunchSites,
    ) { mapLayer, showLaunchSites ->
        MapPreferences(
            mapLayer = mapLayer,
            showLaunchSites = showLaunchSites,
        )
    }

    val uiState: StateFlow<MapUiState> = combine(
        selectedPlaceDraft,
        selectedLaunchSiteDraft,
        placeRepository.observeFavoritePlaces(),
        visibleLaunchSites,
        mapPreferences,
    ) { selectedPlace, selectedLaunchSite, favorites, launchSites, preferences ->
        MapUiState(
            selectedPlace = selectedPlace,
            selectedLaunchSite = selectedLaunchSite.takeIf { preferences.showLaunchSites },
            favoritePlaces = favorites,
            launchSites = launchSites.takeIf { preferences.showLaunchSites }.orEmpty(),
            showLaunchSites = preferences.showLaunchSites,
            initialCamera = loadCameraPosition(),
            mapLayer = preferences.mapLayer,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = MapUiState(
            initialCamera = loadCameraPosition(),
            mapLayer = mapLayerRepository.selectedLayer.value,
            showLaunchSites = launchSiteDisplayRepository.showLaunchSites.value,
        ),
    )

    init {
        viewModelScope.launch {
            launchSiteDisplayRepository.showLaunchSites.collect { showLaunchSites ->
                if (!showLaunchSites) {
                    clearLaunchSites(clearSelection = true)
                }
            }
        }
    }

    fun selectPoint(
        latitude: Double,
        longitude: Double,
    ) {
        val matchingFavorite = uiState.value.favoritePlaces.find { fav ->
            fav.isNearby(latitude, longitude)
        }
        selectedLaunchSiteDraft.value = null
        selectedPlaceDraft.value = matchingFavorite ?: SavedPlace.fromCoordinates(
            latitude = latitude,
            longitude = longitude,
        )
    }

    fun selectFavoritePlace(place: SavedPlace) {
        selectedLaunchSiteDraft.value = null
        selectedPlaceDraft.value = place
    }

    fun selectLaunchSite(site: ParaglidingLaunchSite) {
        selectedPlaceDraft.value = null
        selectedLaunchSiteDraft.value = site
    }

    fun clearSelection() {
        selectedPlaceDraft.value = null
        selectedLaunchSiteDraft.value = null
    }

    fun openSelectedForecast() {
        val selectedPlace = selectedPlaceDraft.value
        val launchSite = selectedLaunchSiteDraft.value
            ?: selectedPlace?.matchingLaunchSite(visibleLaunchSites.value)
        if (launchSite != null) {
            viewModelScope.launch {
                mutableEvents.emit(MapEvent.OpenForecast(launchSite.toPlaceLocation()))
            }
            return
        }

        val place = selectedPlace ?: return

        viewModelScope.launch {
            mutableEvents.emit(MapEvent.OpenForecast(PlaceLocation.fromSavedPlace(place)))
        }
    }

    fun openForecastForPlace(place: SavedPlace) {
        viewModelScope.launch {
            mutableEvents.emit(MapEvent.OpenForecast(PlaceLocation.fromSavedPlace(place)))
        }
    }

    fun addManualFavorite(place: SavedPlace) {
        val favoritePlace = place.copy(isFavorite = true)
        selectedLaunchSiteDraft.value = null
        selectedPlaceDraft.value = favoritePlace
        viewModelScope.launch {
            placeRepository.saveFavoritePlace(favoritePlace)
        }
    }

    fun saveCameraPosition(latitude: Double, longitude: Double, zoom: Double) {
        prefs.edit()
            .putLong(KEY_LAT, latitude.toBits())
            .putLong(KEY_LNG, longitude.toBits())
            .putLong(KEY_ZOOM, zoom.toBits())
            .putBoolean(KEY_HAS_POSITION, true)
            .apply()
    }

    fun selectMapLayer(layer: MapLayerPreference) {
        mapLayerRepository.selectLayer(layer)
    }

    fun loadLaunchSitesForVisibleBounds(
        north: Double,
        south: Double,
        west: Double,
        east: Double,
        zoom: Double,
    ) {
        if (!launchSiteDisplayRepository.showLaunchSites.value) {
            clearLaunchSites(clearSelection = true)
            return
        }

        val bounds = LaunchSiteBounds.normalizedForMap(
            north = north,
            south = south,
            west = west,
            east = east,
            zoom = zoom,
        )
        if (bounds == null) {
            clearLaunchSites(clearSelection = false)
            return
        }

        if (
            !shouldRequestLaunchSites(
                showLaunchSites = launchSiteDisplayRepository.showLaunchSites.value,
                boundsKey = bounds.key,
                lastBoundsKey = lastLaunchSiteBoundsKey,
            )
        ) {
            return
        }
        lastLaunchSiteBoundsKey = bounds.key

        launchSiteLoadJob?.cancel()
        launchSiteLoadJob = viewModelScope.launch {
            try {
                val launchSites = launchSiteRepository.getLaunchSites(bounds)
                if (lastLaunchSiteBoundsKey == bounds.key) {
                    visibleLaunchSites.value = launchSites
                }
            } catch (throwable: CancellationException) {
                throw throwable
            } catch (throwable: Throwable) {
                if (lastLaunchSiteBoundsKey == bounds.key) {
                    lastLaunchSiteBoundsKey = null
                }
                Timber.w(throwable, "Unable to load ParaglidingEarth launch sites")
            }
        }
    }

    private fun clearLaunchSites(clearSelection: Boolean) {
        lastLaunchSiteBoundsKey = null
        launchSiteLoadJob?.cancel()
        launchSiteLoadJob = null
        visibleLaunchSites.value = emptyList()
        if (clearSelection) {
            selectedLaunchSiteDraft.value = null
        }
    }

    private fun loadCameraPosition(): MapCameraData? {
        if (!prefs.getBoolean(KEY_HAS_POSITION, false)) return null
        return MapCameraData(
            latitude = Double.fromBits(prefs.getLong(KEY_LAT, 0L)),
            longitude = Double.fromBits(prefs.getLong(KEY_LNG, 0L)),
            zoom = Double.fromBits(prefs.getLong(KEY_ZOOM, 0L)),
        )
    }

    companion object {
        private const val KEY_HAS_POSITION = "has_position"
        private const val KEY_LAT = "camera_lat"
        private const val KEY_LNG = "camera_lng"
        private const val KEY_ZOOM = "camera_zoom"
    }
}

private data class MapPreferences(
    val mapLayer: MapLayerPreference,
    val showLaunchSites: Boolean,
)

internal fun shouldRequestLaunchSites(
    showLaunchSites: Boolean,
    boundsKey: String,
    lastBoundsKey: String?,
): Boolean {
    return showLaunchSites && boundsKey != lastBoundsKey
}

private const val COLOCATED_SELECTED_LAUNCH_SITE_THRESHOLD_METERS = 30.0

private fun SavedPlace.matchingLaunchSite(
    launchSites: List<ParaglidingLaunchSite>,
): ParaglidingLaunchSite? {
    if (!isFavorite) return null
    return launchSites.firstOrNull { launchSite ->
        isNearby(
            lat = launchSite.latitude,
            lon = launchSite.longitude,
            thresholdMeters = COLOCATED_SELECTED_LAUNCH_SITE_THRESHOLD_METERS,
        )
    }
}
