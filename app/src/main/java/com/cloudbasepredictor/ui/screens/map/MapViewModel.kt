package com.cloudbasepredictor.ui.screens.map

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cloudbasepredictor.data.map.MapLayerPreference
import com.cloudbasepredictor.data.map.MapLayerRepository
import com.cloudbasepredictor.data.place.PlaceRepository
import com.cloudbasepredictor.model.PlaceLocation
import com.cloudbasepredictor.model.SavedPlace
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class MapCameraData(
    val latitude: Double,
    val longitude: Double,
    val zoom: Double,
)

data class MapUiState(
    val selectedPlace: SavedPlace? = null,
    val favoritePlaces: List<SavedPlace> = emptyList(),
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
    @param:ApplicationContext private val context: Context,
) : ViewModel() {
    private val selectedPlaceDraft = MutableStateFlow<SavedPlace?>(null)
    private val mutableEvents = MutableSharedFlow<MapEvent>()
    private val prefs = context.getSharedPreferences("map_camera", Context.MODE_PRIVATE)

    val events = mutableEvents.asSharedFlow()

    val uiState: StateFlow<MapUiState> = combine(
        selectedPlaceDraft,
        placeRepository.observeFavoritePlaces(),
        mapLayerRepository.selectedLayer,
    ) { selectedPlace, favorites, mapLayer ->
        MapUiState(
            selectedPlace = selectedPlace,
            favoritePlaces = favorites,
            initialCamera = loadCameraPosition(),
            mapLayer = mapLayer,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = MapUiState(
            initialCamera = loadCameraPosition(),
            mapLayer = mapLayerRepository.selectedLayer.value,
        ),
    )

    fun selectPoint(
        latitude: Double,
        longitude: Double,
    ) {
        val matchingFavorite = uiState.value.favoritePlaces.find { fav ->
            fav.isNearby(latitude, longitude)
        }
        selectedPlaceDraft.value = matchingFavorite ?: SavedPlace.fromCoordinates(
            latitude = latitude,
            longitude = longitude,
        )
    }

    fun selectFavoritePlace(place: SavedPlace) {
        selectedPlaceDraft.value = place
    }

    fun openSelectedForecast() {
        val place = selectedPlaceDraft.value ?: return

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
