@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package com.cloudbasepredictor.web.map

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.HtmlElementView
import com.cloudbasepredictor.data.map.MapCameraPosition
import com.cloudbasepredictor.data.map.MapLayerPreference
import com.cloudbasepredictor.model.PlaceLocation
import com.cloudbasepredictor.model.SavedPlace
import com.cloudbasepredictor.web.WebRouteState
import com.cloudbasepredictor.web.preferences.WebPreferencesState
import com.cloudbasepredictor.web.preview.WebPreviewData
import kotlinx.browser.document
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.await
import kotlinx.coroutines.launch
import androidx.compose.ui.tooling.preview.Preview
import org.w3c.dom.HTMLButtonElement
import org.w3c.dom.HTMLDivElement
import org.w3c.dom.HTMLElement
import org.w3c.dom.HTMLInputElement
import org.w3c.dom.events.Event
import org.w3c.dom.events.KeyboardEvent

@Composable
internal actual fun WebMapDestination(
    routeState: WebRouteState,
    preferences: WebPreferencesState,
    favoritePlaces: List<SavedPlace>,
    savedCamera: MapCameraPosition?,
    searchLocations: suspend (String) -> List<PlaceLocation>,
    onMapLayerSelected: (MapLayerPreference) -> Unit,
    onLocationConfirmed: (PlaceLocation) -> Unit,
    onCameraChanged: (MapCameraPosition) -> Unit,
    modifier: Modifier,
) {
    var selectedLocation by remember(routeState.location) { mutableStateOf(routeState.location) }
    val initialCamera = resolveInitialCamera(routeState, preferences, favoritePlaces, savedCamera)
    val favoriteMarkers = favoritePlaces.map { place ->
        WebMapMarker(
            id = place.id,
            location = place.toLocation(),
            title = place.name,
            kind = WebMapMarkerKind.FAVORITE,
        )
    }
    val renderState = WebMapRenderState(
        initialCamera = initialCamera,
        layer = preferences.mapLayer,
        markers = favoriteMarkers + selectedLocation?.let { location ->
            listOf(
                WebMapMarker(
                    id = SELECTED_MARKER_ID,
                    location = location,
                    title = location.name ?: formatCoordinates(location),
                    kind = WebMapMarkerKind.SELECTED,
                ),
            )
        }.orEmpty(),
    )

    WebMapSurface(
        state = renderState,
        searchLocations = searchLocations,
        onMapTap = { tapped ->
            // Tapping within ~200 m of a favorite selects the favorite (like Android).
            val nearbyFavorite = favoritePlaces.firstOrNull { favorite ->
                favorite.isNearby(tapped.latitude, tapped.longitude)
            }
            selectedLocation = nearbyFavorite?.toLocation() ?: tapped
        },
        onLayerSelected = onMapLayerSelected,
        onLocationConfirmed = onLocationConfirmed,
        onCameraChanged = onCameraChanged,
        modifier = modifier,
    )
}

private fun resolveInitialCamera(
    routeState: WebRouteState,
    preferences: WebPreferencesState,
    favoritePlaces: List<SavedPlace>,
    savedCamera: MapCameraPosition?,
): MapCameraPosition {
    val location = routeState.location
    val favorite = favoritePlaces.firstOrNull()?.takeIf { preferences.startWithFavorites }
    return when {
        location != null ->
            MapCameraPosition(location.latitude, location.longitude, INITIAL_MAP_ZOOM)
        savedCamera != null -> savedCamera
        favorite != null ->
            MapCameraPosition(favorite.latitude, favorite.longitude, INITIAL_MAP_ZOOM)
        else -> DEFAULT_MAP_CAMERA
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun WebMapSurface(
    state: WebMapRenderState,
    searchLocations: suspend (String) -> List<PlaceLocation>,
    onMapTap: (PlaceLocation) -> Unit,
    onLayerSelected: (MapLayerPreference) -> Unit,
    onLocationConfirmed: (PlaceLocation) -> Unit,
    onCameraChanged: (MapCameraPosition) -> Unit,
    modifier: Modifier,
) {
    val scope = rememberCoroutineScope()
    val currentMapTap = rememberUpdatedState(onMapTap)
    val currentLayerSelected = rememberUpdatedState(onLayerSelected)
    val currentLocationConfirmed = rememberUpdatedState(onLocationConfirmed)
    val currentCameraChanged = rememberUpdatedState(onCameraChanged)
    val binding = remember {
        MapLibreBinding(
            scope = scope,
            searchLocations = searchLocations,
            onMapTap = { currentMapTap.value(it) },
            onLayerSelected = { currentLayerSelected.value(it) },
            onLocationConfirmed = { currentLocationConfirmed.value(it) },
            onCameraChanged = { currentCameraChanged.value(it) },
        )
    }

    HtmlElementView(
        modifier = modifier,
        factory = {
            (document.createElement("div") as HTMLDivElement).apply {
                className = "cloudbase-map-root"
                setAttribute("data-testid", "web-map-host")
                setAttribute("aria-label", "Forecast location map")
                style.width = "100%"
                style.height = "100%"
            }.also { host -> binding.attach(host, state) }
        },
        update = { host -> binding.update(host, state) },
        onRelease = { host -> binding.release(host) },
        onReset = null,
    )
}

private class MapLibreBinding(
    private val scope: CoroutineScope,
    private val searchLocations: suspend (String) -> List<PlaceLocation>,
    private val onMapTap: (PlaceLocation) -> Unit,
    private val onLayerSelected: (MapLayerPreference) -> Unit,
    private val onLocationConfirmed: (PlaceLocation) -> Unit,
    private val onCameraChanged: (MapCameraPosition) -> Unit,
) {
    private var disposed = false
    private var latestState: WebMapRenderState? = null
    private var loadJob: Job? = null
    private var searchJob: Job? = null
    private var mapModule: kotlin.js.JsAny? = null
    private var map: MapLibreMap? = null
    private var resizeObserver: WebResizeObserver? = null
    private val subscriptions = mutableListOf<MapLibreSubscription>()
    private val markers = mutableListOf<MapLibreMarker>()
    private var appliedLayer: MapLayerPreference? = null
    private var root: HTMLDivElement? = null
    private var mapContainer: HTMLDivElement? = null
    private var status: HTMLDivElement? = null
    private var searchInput: HTMLInputElement? = null
    private var searchResults: HTMLDivElement? = null
    private var selectionCard: HTMLDivElement? = null
    private var selectionLabel: HTMLDivElement? = null
    private var confirmButton: HTMLButtonElement? = null
    private var attributionButton: HTMLButtonElement? = null
    private var attributionDetail: HTMLDivElement? = null
    private val layerButtons = mutableMapOf<MapLayerPreference, HTMLButtonElement>()

    fun attach(host: HTMLDivElement, state: WebMapRenderState) {
        releaseCurrentMap()
        disposed = false
        root = host
        latestState = state
        buildDom(host)
        renderState(state)
        loadJob = scope.launch {
            try {
                val module = loadMapLibreBundle().await()
                if (disposed || root !== host) return@launch
                mapModule = module
                createMap(host, module, latestState ?: state)
            } catch (_: Throwable) {
                if (!disposed) showStatus("Map could not be loaded. Check the network and retry.", isError = true)
            }
        }
    }

    fun update(host: HTMLDivElement, state: WebMapRenderState) {
        if (root !== host || disposed) return
        latestState = state
        renderState(state)
        syncMap(state)
    }

    fun release(host: HTMLDivElement) {
        if (root !== host) return
        disposed = true
        loadJob?.cancel()
        loadJob = null
        searchJob?.cancel()
        searchJob = null
        releaseCurrentMap()
        root = null
        mapContainer = null
        status = null
        searchInput = null
        searchResults = null
        selectionCard = null
        selectionLabel = null
        confirmButton = null
        attributionButton = null
        attributionDetail = null
        layerButtons.clear()
    }

    private fun buildDom(host: HTMLDivElement) {
        val canvasHost = (document.createElement("div") as HTMLDivElement).apply {
            className = "cloudbase-map-canvas"
            setAttribute("data-testid", "map-canvas-host")
        }
        mapContainer = canvasHost
        host.appendChild(canvasHost)

        val toolbar = (document.createElement("div") as HTMLDivElement).apply {
            className = "cloudbase-map-layer-toolbar"
            setAttribute("role", "group")
            setAttribute("aria-label", "Map layer")
        }
        MapLayerPreference.entries.forEach { layer ->
            val button = domButton(layer.label, "cloudbase-map-layer-button") {
                onLayerSelected(layer)
            }
            layerButtons[layer] = button
            toolbar.appendChild(button)
        }
        host.appendChild(toolbar)

        val searchField = (document.createElement("input") as HTMLInputElement).apply {
            type = "search"
            className = "cloudbase-map-search-input"
            placeholder = "Search for a location"
            autocomplete = "off"
            setAttribute("aria-label", "Search location")
            setAttribute("data-testid", "location-search")
            addEventListener("keydown", { event ->
                if ((event as? KeyboardEvent)?.key == "Enter") {
                    event.preventDefault()
                    runLocationSearch()
                }
            })
        }
        searchInput = searchField
        val searchButton = domButton("Search", "cloudbase-map-search-button") {
            runLocationSearch()
        }
        searchResults = (document.createElement("div") as HTMLDivElement).apply {
            className = "cloudbase-map-search-results"
            setAttribute("role", "listbox")
            setAttribute("aria-label", "Location search results")
            style.display = "none"
        }
        (document.createElement("div") as HTMLDivElement).apply {
            className = "cloudbase-map-search"
            appendChild(searchField)
            appendChild(searchButton)
            appendChild(requireNotNull(searchResults))
        }.also(host::appendChild)

        val geolocate = domButton("◎", "cloudbase-map-geolocate") {
            requestDeviceLocation()
        }.apply {
            title = "Use my location"
            setAttribute("aria-label", "Use my location")
        }
        host.appendChild(geolocate)

        status = (document.createElement("div") as HTMLDivElement).apply {
            className = "cloudbase-map-status"
            setAttribute("role", "status")
            textContent = "Loading map…"
        }.also(host::appendChild)

        selectionLabel = (document.createElement("div") as HTMLDivElement).apply {
            className = "cloudbase-map-selection-label"
        }
        confirmButton = domButton("Show forecast", "cloudbase-map-confirm") {
            latestState?.markers
                ?.firstOrNull { it.kind == WebMapMarkerKind.SELECTED }
                ?.location
                ?.let(onLocationConfirmed)
        }
        selectionCard = (document.createElement("div") as HTMLDivElement).apply {
            className = "cloudbase-map-selection-card"
            setAttribute("data-testid", "map-selection-card")
            appendChild(requireNotNull(selectionLabel))
            appendChild(requireNotNull(confirmButton))
        }.also(host::appendChild)

        attributionButton = domButton("", "cloudbase-map-attribution-button") {
            val detail = attributionDetail ?: return@domButton
            val expanded = detail.style.display == "block"
            detail.style.display = if (expanded) "none" else "block"
            attributionButton?.setAttribute("aria-expanded", (!expanded).toString())
        }.apply {
            setAttribute("aria-expanded", "false")
        }
        attributionDetail = (document.createElement("div") as HTMLDivElement).apply {
            className = "cloudbase-map-attribution-detail"
            style.display = "none"
        }
        (document.createElement("div") as HTMLDivElement).apply {
            className = "cloudbase-map-attribution"
            appendChild(requireNotNull(attributionButton))
            appendChild(requireNotNull(attributionDetail))
        }.also(host::appendChild)
    }

    private fun createMap(host: HTMLDivElement, module: kotlin.js.JsAny, state: WebMapRenderState) {
        val container = mapContainer ?: return
        val createdMap = createMapLibreMap(
            module = module,
            options = mapOptions(
                container = container,
                style = styleFor(state.layer),
                longitude = state.initialCamera.longitude,
                latitude = state.initialCamera.latitude,
                zoom = state.initialCamera.zoom,
            ),
        )
        map = createdMap
        subscriptions += createdMap.on("load") {
            showStatus("", isError = false)
            createdMap.resize()
            saveCamera(createdMap)
        }
        subscriptions += createdMap.on("moveend") {
            saveCamera(createdMap)
        }
        subscriptions += createdMap.on("click") { event ->
            onMapTap(
                PlaceLocation(
                    latitude = eventLatitude(event),
                    longitude = eventLongitude(event),
                ),
            )
        }
        subscriptions += createdMap.on("error") {
            showStatus("The map provider reported an error.", isError = true)
        }
        resizeObserver = createResizeObserver { createdMap.resize() }.also { observer ->
            observer.observe(host)
        }
        syncMap(state, force = true)
    }

    private fun syncMap(state: WebMapRenderState, force: Boolean = false) {
        val currentMap = map ?: return
        if (force || appliedLayer != state.layer) {
            if (!force) currentMap.setStyle(styleFor(state.layer))
            appliedLayer = state.layer
        }
        markers.forEach(MapLibreMarker::remove)
        markers.clear()
        val module = mapModule ?: return
        state.markers.forEach { markerModel ->
            val marker = createMapLibreMarker(
                module,
                markerOptions(
                    if (markerModel.kind == WebMapMarkerKind.SELECTED) SELECTED_MARKER_COLOR
                    else FAVORITE_MARKER_COLOR,
                ),
            ).setLngLat(
                lngLat(markerModel.location.longitude, markerModel.location.latitude),
            ).addTo(currentMap)
            marker.getElement().apply {
                title = markerModel.title
                setAttribute("aria-label", markerModel.title)
                if (markerModel.kind == WebMapMarkerKind.FAVORITE) {
                    style.cursor = "pointer"
                    addEventListener("click", { event ->
                        event.stopPropagation()
                        onMapTap(markerModel.location)
                        currentMap.flyTo(
                            flyToOptions(
                                longitude = markerModel.location.longitude,
                                latitude = markerModel.location.latitude,
                                zoom = maxOf(currentMap.getZoom(), DEVICE_LOCATION_ZOOM),
                            ),
                        )
                    })
                }
            }
            markers += marker
        }
    }

    private fun renderState(state: WebMapRenderState) {
        layerButtons.forEach { (layer, button) ->
            val selected = layer == state.layer
            button.className = "cloudbase-map-layer-button" + if (selected) " active" else ""
            button.setAttribute("aria-pressed", selected.toString())
        }
        val selected = state.markers.firstOrNull { it.kind == WebMapMarkerKind.SELECTED }
        selectionCard?.style?.display = if (selected == null) "none" else "flex"
        selectionLabel?.textContent = selected?.title.orEmpty()
        confirmButton?.disabled = selected == null
        attributionButton?.textContent = state.layer.attributionCompact
        attributionDetail?.textContent = state.layer.attributionFull
    }

    private fun saveCamera(currentMap: MapLibreMap) {
        val center = currentMap.getCenter()
        onCameraChanged(
            MapCameraPosition(
                latitude = center.lat,
                longitude = center.lng,
                zoom = currentMap.getZoom(),
            ),
        )
    }

    private fun requestDeviceLocation() {
        showStatus("Finding your location…", isError = false)
        scope.launch {
            try {
                val position = requestBrowserLocation().await()
                if (disposed) return@launch
                val location = PlaceLocation(
                    latitude = positionLatitude(position),
                    longitude = positionLongitude(position),
                )
                onMapTap(location)
                map?.flyTo(
                    flyToOptions(
                        longitude = location.longitude,
                        latitude = location.latitude,
                        zoom = DEVICE_LOCATION_ZOOM,
                    ),
                )
                showStatus("", isError = false)
            } catch (_: Throwable) {
                if (!disposed) showStatus("Could not determine your location.", isError = true)
            }
        }
    }

    private fun runLocationSearch() {
        val query = searchInput?.value?.trim().orEmpty()
        if (query.length < MINIMUM_SEARCH_LENGTH) {
            showStatus("Enter at least two characters to search.", isError = true)
            return
        }
        searchJob?.cancel()
        showStatus("Searching for locations…", isError = false)
        searchJob = scope.launch {
            try {
                val matches = searchLocations(query)
                if (disposed) return@launch
                renderSearchResults(matches)
                showStatus(
                    message = if (matches.isEmpty()) "No matching locations found." else "",
                    isError = false,
                )
            } catch (_: Throwable) {
                if (!disposed) {
                    renderSearchResults(emptyList())
                    showStatus("Location search is temporarily unavailable.", isError = true)
                }
            }
        }
    }

    private fun renderSearchResults(matches: List<PlaceLocation>) {
        val container = searchResults ?: return
        container.textContent = ""
        container.style.display = if (matches.isEmpty()) "none" else "block"
        matches.forEach { location ->
            val label = location.name ?: formatCoordinates(location)
            val button = domButton(label, "cloudbase-map-search-result") {
                onMapTap(location)
                map?.flyTo(
                    flyToOptions(
                        longitude = location.longitude,
                        latitude = location.latitude,
                        zoom = DEVICE_LOCATION_ZOOM,
                    ),
                )
                searchInput?.value = label
                container.style.display = "none"
            }.apply {
                setAttribute("role", "option")
                setAttribute("aria-label", "Select $label")
            }
            container.appendChild(button)
        }
    }

    private fun showStatus(message: String, isError: Boolean) {
        status?.apply {
            textContent = message
            className = "cloudbase-map-status" + if (isError) " error" else ""
            style.display = if (message.isEmpty()) "none" else "block"
            setAttribute("role", if (isError) "alert" else "status")
        }
    }

    private fun releaseCurrentMap() {
        resizeObserver?.disconnect()
        resizeObserver = null
        subscriptions.forEach(MapLibreSubscription::unsubscribe)
        subscriptions.clear()
        markers.forEach(MapLibreMarker::remove)
        markers.clear()
        map?.remove()
        map = null
        mapModule = null
        appliedLayer = null
    }
}

private fun domButton(
    label: String,
    cssClass: String,
    onClick: (Event) -> Unit,
): HTMLButtonElement {
    return (document.createElement("button") as HTMLButtonElement).apply {
        type = "button"
        className = cssClass
        textContent = label
        addEventListener("click", onClick)
    }
}

private fun styleFor(layer: MapLayerPreference): kotlin.js.JsAny {
    return when (val style = buildWebMapStyle(layer, yesterdayUtcDate())) {
        is WebMapStyle.Url -> styleValue(style.value, isJson = false)
        is WebMapStyle.Raster -> styleValue(style.toMapLibreJson(), isJson = true)
    }
}

private fun formatCoordinates(location: PlaceLocation): String {
    return "${location.latitude}, ${location.longitude}"
}

private fun SavedPlace.toLocation(): PlaceLocation {
    return PlaceLocation(latitude = latitude, longitude = longitude, name = name)
}

// Default camera matches the Android app: Berlin at zoom 5.5 when no location, saved camera, or
// favorite is available.
private val DEFAULT_MAP_CAMERA = MapCameraPosition(
    latitude = 52.5200,
    longitude = 13.4050,
    zoom = DEFAULT_MAP_ZOOM,
)

private const val SELECTED_MARKER_ID = "selected-location"
private const val SELECTED_MARKER_COLOR = "#e64a5b"
private const val FAVORITE_MARKER_COLOR = "#ffc107"
private const val INITIAL_MAP_ZOOM = 10.0
private const val DEFAULT_MAP_ZOOM = 5.5
private const val DEVICE_LOCATION_ZOOM = 12.0
private const val MINIMUM_SEARCH_LENGTH = 2

@Preview
@Composable
private fun WebMapDestinationPreview() {
    WebMapDestination(
        routeState = WebPreviewData.mapRoute,
        preferences = WebPreviewData.preferences,
        favoritePlaces = WebPreviewData.favoritePlaces,
        savedCamera = null,
        searchLocations = { emptyList() },
        onMapLayerSelected = {},
        onLocationConfirmed = {},
        onCameraChanged = {},
    )
}
