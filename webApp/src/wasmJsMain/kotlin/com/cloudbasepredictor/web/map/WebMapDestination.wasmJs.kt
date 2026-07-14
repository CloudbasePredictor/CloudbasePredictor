@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package com.cloudbasepredictor.web.map

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.HtmlElementView
import com.cloudbasepredictor.data.launch.LaunchSiteBounds
import com.cloudbasepredictor.data.launch.LaunchSiteRepository
import com.cloudbasepredictor.data.map.MapCameraPosition
import com.cloudbasepredictor.data.map.MapLayerPreference
import com.cloudbasepredictor.model.LaunchSiteDisplay
import com.cloudbasepredictor.model.ManualFavoriteInputError
import com.cloudbasepredictor.model.ManualFavoriteInputResult
import com.cloudbasepredictor.model.ParaglidingLaunchSite
import com.cloudbasepredictor.model.PlaceLocation
import com.cloudbasepredictor.model.SavedPlace
import com.cloudbasepredictor.model.mergeColocatedFavoriteLaunchSites
import com.cloudbasepredictor.model.parseManualFavoriteInput
import com.cloudbasepredictor.web.WebRouteState
import com.cloudbasepredictor.web.i18n.LocalWebStrings
import com.cloudbasepredictor.web.i18n.WebStrings
import com.cloudbasepredictor.web.preferences.WebPreferencesState
import com.cloudbasepredictor.web.preview.WebPreviewData
import kotlinx.browser.document
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.await
import kotlinx.coroutines.launch
import androidx.compose.ui.tooling.preview.Preview
import org.w3c.dom.HTMLAnchorElement
import org.w3c.dom.HTMLButtonElement
import org.w3c.dom.HTMLDivElement
import org.w3c.dom.HTMLElement
import org.w3c.dom.HTMLInputElement
import org.w3c.dom.events.Event
import kotlin.math.abs

@Composable
internal actual fun WebMapDestination(
    routeState: WebRouteState,
    preferences: WebPreferencesState,
    favoritePlaces: List<SavedPlace>,
    savedCamera: MapCameraPosition?,
    launchSiteRepository: LaunchSiteRepository,
    onMapLayerSelected: (MapLayerPreference) -> Unit,
    onLocationConfirmed: (PlaceLocation) -> Unit,
    onCameraChanged: (MapCameraPosition) -> Unit,
    onAddFavorite: (SavedPlace) -> Unit,
    modifier: Modifier,
) {
    val strings = LocalWebStrings.current
    var selectedLocation by remember(routeState.location) { mutableStateOf(routeState.location) }
    var selectedLaunchSite by remember { mutableStateOf<ParaglidingLaunchSite?>(null) }
    var viewportBounds by remember { mutableStateOf<LaunchSiteBounds?>(null) }
    var launchSites by remember { mutableStateOf<List<ParaglidingLaunchSite>>(emptyList()) }
    val showLaunchSites = preferences.showLaunchSites
    val initialCamera = resolveInitialCamera(routeState, preferences, favoritePlaces, savedCamera)

    // Reloads whenever the toggle flips or the normalized viewport key changes; the previous request
    // is cancelled automatically. When disabled, nothing is requested (not even the manifest).
    LaunchedEffect(showLaunchSites, viewportBounds?.key) {
        if (!showLaunchSites) {
            launchSites = emptyList()
            selectedLaunchSite = null
        } else {
            val bounds = viewportBounds
            launchSites = if (bounds == null) {
                emptyList()
            } else {
                runCatching { launchSiteRepository.getLaunchSites(bounds) }.getOrDefault(emptyList())
            }
        }
    }

    val renderState = WebMapRenderState(
        initialCamera = initialCamera,
        layer = preferences.mapLayer,
        markers = buildMarkers(favoritePlaces, launchSites, selectedLocation),
        selectedLaunchSite = selectedLaunchSite,
        showLaunchSites = showLaunchSites,
    )

    WebMapSurface(
        state = renderState,
        strings = strings,
        onMapTap = { tapped ->
            // Tapping within ~200 m of a favorite selects the favorite (like Android).
            val nearbyFavorite = favoritePlaces.firstOrNull { favorite ->
                favorite.isNearby(tapped.latitude, tapped.longitude)
            }
            selectedLaunchSite = null
            selectedLocation = nearbyFavorite?.toLocation() ?: tapped
        },
        onLaunchSiteTap = { site ->
            selectedLaunchSite = site
            selectedLocation = site.toPlaceLocation()
        },
        onViewportChanged = { bounds -> viewportBounds = bounds },
        onLayerSelected = onMapLayerSelected,
        onLocationConfirmed = onLocationConfirmed,
        onCameraChanged = onCameraChanged,
        onAddFavorite = onAddFavorite,
        modifier = modifier,
    )
}

private fun buildMarkers(
    favoritePlaces: List<SavedPlace>,
    launchSites: List<ParaglidingLaunchSite>,
    selectedLocation: PlaceLocation?,
): List<WebMapMarker> {
    // A favorite saved on a launch site is drawn once (as a favorite-colored flag) instead of two
    // overlapping markers — matching the Android map. Merge first, then suppress the merged inputs.
    val colocated = mergeColocatedFavoriteLaunchSites(favoritePlaces, launchSites)
    val mergedFavoriteIds = colocated.mapTo(mutableSetOf()) { it.favorite.id }
    val mergedLaunchSiteIds = colocated.mapTo(mutableSetOf()) { it.launchSite.id }

    val launchMarkers = launchSites
        .filterNot { site -> site.id in mergedLaunchSiteIds }
        .map { site ->
            WebMapMarker(
                id = "launch-${site.id}",
                location = site.toPlaceLocation(),
                title = site.name,
                kind = WebMapMarkerKind.LAUNCH_SITE,
                launchSite = site,
            )
        }
    val favoriteLaunchMarkers = colocated.map { pair ->
        WebMapMarker(
            id = "favorite-launch-${pair.launchSite.id}",
            location = pair.favorite.toLocation(),
            title = pair.favorite.name,
            kind = WebMapMarkerKind.FAVORITE_LAUNCH_SITE,
            launchSite = pair.launchSite,
        )
    }
    val favoriteMarkers = favoritePlaces
        .filterNot { place -> place.id in mergedFavoriteIds }
        .map { place ->
            WebMapMarker(
                id = place.id,
                location = place.toLocation(),
                title = place.name,
                kind = WebMapMarkerKind.FAVORITE,
            )
        }
    val selectedMarker = selectedLocation?.let { location ->
        listOf(
            WebMapMarker(
                id = SELECTED_MARKER_ID,
                location = location,
                title = location.name ?: formatCoordinates(location),
                kind = WebMapMarkerKind.SELECTED,
            ),
        )
    }.orEmpty()
    // Draw order (bottom → top): plain launch flags, merged favorite flags, favorite pins, selection.
    return launchMarkers + favoriteLaunchMarkers + favoriteMarkers + selectedMarker
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
    strings: WebStrings,
    onMapTap: (PlaceLocation) -> Unit,
    onLaunchSiteTap: (ParaglidingLaunchSite) -> Unit,
    onViewportChanged: (LaunchSiteBounds?) -> Unit,
    onLayerSelected: (MapLayerPreference) -> Unit,
    onLocationConfirmed: (PlaceLocation) -> Unit,
    onCameraChanged: (MapCameraPosition) -> Unit,
    onAddFavorite: (SavedPlace) -> Unit,
    modifier: Modifier,
) {
    val scope = rememberCoroutineScope()
    val currentMapTap = rememberUpdatedState(onMapTap)
    val currentLaunchSiteTap = rememberUpdatedState(onLaunchSiteTap)
    val currentViewportChanged = rememberUpdatedState(onViewportChanged)
    val currentLayerSelected = rememberUpdatedState(onLayerSelected)
    val currentLocationConfirmed = rememberUpdatedState(onLocationConfirmed)
    val currentCameraChanged = rememberUpdatedState(onCameraChanged)
    val currentAddFavorite = rememberUpdatedState(onAddFavorite)
    val binding = remember {
        MapLibreBinding(
            scope = scope,
            strings = strings,
            onMapTap = { currentMapTap.value(it) },
            onLaunchSiteTap = { currentLaunchSiteTap.value(it) },
            onViewportChanged = { currentViewportChanged.value(it) },
            onLayerSelected = { currentLayerSelected.value(it) },
            onLocationConfirmed = { currentLocationConfirmed.value(it) },
            onCameraChanged = { currentCameraChanged.value(it) },
            onAddFavorite = { currentAddFavorite.value(it) },
        )
    }

    HtmlElementView(
        modifier = modifier,
        factory = {
            (document.createElement("div") as HTMLDivElement).apply {
                className = "cloudbase-map-root"
                setAttribute("data-testid", "web-map-host")
                setAttribute("aria-label", strings.mapAriaLabel)
                style.width = "100%"
                style.height = "100%"
            }.also { host -> binding.attach(host, state) }
        },
        update = { host -> binding.update(host, state) },
        onRelease = { host -> binding.release(host) },
        onReset = null,
    )
}

@Suppress("LongParameterList")
private class MapLibreBinding(
    private val scope: CoroutineScope,
    private val strings: WebStrings,
    private val onMapTap: (PlaceLocation) -> Unit,
    private val onLaunchSiteTap: (ParaglidingLaunchSite) -> Unit,
    private val onViewportChanged: (LaunchSiteBounds?) -> Unit,
    private val onLayerSelected: (MapLayerPreference) -> Unit,
    private val onLocationConfirmed: (PlaceLocation) -> Unit,
    private val onCameraChanged: (MapCameraPosition) -> Unit,
    private val onAddFavorite: (SavedPlace) -> Unit,
) {
    private var disposed = false
    private var latestState: WebMapRenderState? = null
    private var loadJob: Job? = null
    private var mapModule: kotlin.js.JsAny? = null
    private var map: MapLibreMap? = null
    private var resizeObserver: WebResizeObserver? = null
    private val subscriptions = mutableListOf<MapLibreSubscription>()
    // Keyed by marker id so map updates can diff the delta instead of tearing down and rebuilding
    // every marker (and regenerating every launch-flag SVG) on each tap. Insertion order is
    // preserved so the draw order matches the order markers first appear.
    private val markers = LinkedHashMap<String, TrackedMarker>()
    private var appliedLayer: MapLayerPreference? = null
    private var root: HTMLDivElement? = null
    private var mapContainer: HTMLDivElement? = null
    private var status: HTMLDivElement? = null
    private var northResetButton: HTMLButtonElement? = null
    private var manualForm: HTMLDivElement? = null
    private var manualFormKeydownListener: ((Event) -> Unit)? = null
    private var manualNameInput: HTMLInputElement? = null
    private var manualCoordinatesInput: HTMLInputElement? = null
    private var manualError: HTMLDivElement? = null
    private var selectionCard: HTMLDivElement? = null
    private var selectionLabel: HTMLDivElement? = null
    private var selectionDetail: HTMLDivElement? = null
    private var selectionSource: HTMLDivElement? = null
    private var confirmButton: HTMLButtonElement? = null
    private var attributionButton: HTMLButtonElement? = null
    private var attributionDetail: HTMLDivElement? = null
    private var launchAttribution: HTMLDivElement? = null
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
                if (!disposed) showStatus(strings.mapLoadError, isError = true)
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
        releaseCurrentMap()
        root = null
        mapContainer = null
        status = null
        northResetButton = null
        manualFormKeydownListener?.let { listener ->
            manualForm?.removeEventListener("keydown", listener)
        }
        manualFormKeydownListener = null
        manualForm = null
        manualNameInput = null
        manualCoordinatesInput = null
        manualError = null
        selectionCard = null
        selectionLabel = null
        selectionDetail = null
        selectionSource = null
        confirmButton = null
        attributionButton = null
        attributionDetail = null
        launchAttribution = null
        layerButtons.clear()
    }

    private fun buildDom(host: HTMLDivElement) {
        val canvasHost = (document.createElement("div") as HTMLDivElement).apply {
            className = "cloudbase-map-canvas"
            setAttribute("data-testid", "map-canvas-host")
            // MapLibre adds its `maplibregl-map { position: relative; overflow: hidden }` class to
            // this element, and its lazy-loaded stylesheet arrives after ours, overriding the
            // `.cloudbase-map-canvas { position: absolute }` rule at equal specificity. That
            // collapsed the host to zero height and clipped the fully drawn map to nothing (a grey
            // rectangle on every platform). Inline styles outrank both stylesheets, so pin the
            // geometry here instead of in styles.css.
            style.position = "absolute"
            style.setProperty("inset", "0")
            style.width = "100%"
            style.height = "100%"
        }
        mapContainer = canvasHost
        host.appendChild(canvasHost)

        val toolbar = (document.createElement("div") as HTMLDivElement).apply {
            className = "cloudbase-map-layer-toolbar"
            setAttribute("role", "group")
            setAttribute("aria-label", strings.mapLayerTitle)
        }
        MapLayerPreference.entries.forEach { layer ->
            val button = domButton(layer.label, "cloudbase-map-layer-button") {
                onLayerSelected(layer)
            }
            layerButtons[layer] = button
            toolbar.appendChild(button)
        }
        host.appendChild(toolbar)

        val geolocate = domButton("◎", "cloudbase-map-geolocate") {
            requestDeviceLocation()
        }.apply {
            title = strings.useMyLocation
            setAttribute("aria-label", strings.useMyLocation)
        }
        host.appendChild(geolocate)

        northResetButton = domButton("N", "cloudbase-map-north-reset") {
            map?.resetNorth()
        }.apply {
            title = strings.resetNorthContentDescription
            setAttribute("aria-label", strings.resetNorthContentDescription)
            style.display = "none"
        }
        host.appendChild(requireNotNull(northResetButton))

        val addPoint = domButton("＋", "cloudbase-map-add-point") {
            showManualForm()
        }.apply {
            title = strings.addLocationManually
            setAttribute("aria-label", strings.addLocationManually)
        }
        host.appendChild(addPoint)
        buildManualAddForm(host)

        status = (document.createElement("div") as HTMLDivElement).apply {
            className = "cloudbase-map-status"
            setAttribute("role", "status")
            textContent = strings.loadingMap
        }.also(host::appendChild)

        buildSelectionCard(host)
        buildAttribution(host)
    }

    private fun buildSelectionCard(host: HTMLDivElement) {
        val label = (document.createElement("div") as HTMLDivElement).apply {
            className = "cloudbase-map-selection-label"
        }
        val detail = (document.createElement("div") as HTMLDivElement).apply {
            className = "cloudbase-map-selection-detail"
            style.display = "none"
        }
        val source = (document.createElement("div") as HTMLDivElement).apply {
            className = "cloudbase-map-selection-source"
            style.display = "none"
        }
        selectionLabel = label
        selectionDetail = detail
        selectionSource = source
        val text = (document.createElement("div") as HTMLDivElement).apply {
            className = "cloudbase-map-selection-text"
            appendChild(label)
            appendChild(detail)
            appendChild(source)
        }
        confirmButton = domButton(strings.showForecast, "cloudbase-map-confirm") {
            confirmSelection()
        }
        selectionCard = (document.createElement("div") as HTMLDivElement).apply {
            className = "cloudbase-map-selection-card"
            setAttribute("data-testid", "map-selection-card")
            appendChild(text)
            appendChild(requireNotNull(confirmButton))
        }.also(host::appendChild)
    }

    private fun buildAttribution(host: HTMLDivElement) {
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

        launchAttribution = (document.createElement("div") as HTMLDivElement).apply {
            className = "cloudbase-map-launch-attribution"
            setAttribute("data-testid", "launch-attribution")
            style.display = "none"
            appendChild(document.createTextNode(strings.launchSiteDataPrefix))
            appendChild(domLink("ParaglidingEarth", PARAGLIDING_EARTH_HOME))
            appendChild(document.createTextNode(strings.launchSiteLicenseSuffix))
        }.also(host::appendChild)
    }

    private fun buildManualAddForm(host: HTMLDivElement) {
        val nameInput = manualInput(strings.manualNamePlaceholder, strings.manualNameLabel, "manual-favorite-name")
        val coordinatesInput =
            manualInput("47.3769, 8.5417", strings.manualCoordinatesLabel, "manual-favorite-coordinates")
        manualNameInput = nameInput
        manualCoordinatesInput = coordinatesInput
        val help = (document.createElement("div") as HTMLDivElement).apply {
            className = "cloudbase-map-manual-help"
            textContent = strings.manualCoordinatesHelp
        }
        manualError = (document.createElement("div") as HTMLDivElement).apply {
            className = "cloudbase-map-manual-error"
            setAttribute("role", "alert")
            style.display = "none"
        }
        val actions = (document.createElement("div") as HTMLDivElement).apply {
            className = "cloudbase-map-manual-actions"
            appendChild(domButton(strings.actionCancel, "cloudbase-map-manual-cancel") { hideManualForm() })
            appendChild(domButton(strings.actionSave, "cloudbase-map-manual-save") { submitManualForm() })
        }
        val heading = (document.createElement("div") as HTMLDivElement).apply {
            className = "cloudbase-map-manual-heading"
            textContent = strings.addLocationHeading
        }
        manualForm = (document.createElement("div") as HTMLDivElement).apply {
            className = "cloudbase-map-manual-form"
            setAttribute("data-testid", "manual-favorite-form")
            setAttribute("role", "group")
            setAttribute("aria-label", strings.addLocationManually)
            style.display = "none"
            appendChild(heading)
            appendChild(nameInput)
            appendChild(coordinatesInput)
            appendChild(help)
            appendChild(requireNotNull(manualError))
            appendChild(actions)
        }.also(host::appendChild)

        // Escape cancels the manual-add form to match the Compose overlays. The form is DOM (not
        // Compose), so listen on the element directly; the listener is detached in [release].
        val keydownListener: (Event) -> Unit = { event ->
            if (isEscapeKey(event)) {
                event.preventDefault()
                hideManualForm()
            }
        }
        manualForm?.addEventListener("keydown", keydownListener)
        manualFormKeydownListener = keydownListener
    }

    private fun manualInput(placeholderText: String, label: String, testId: String): HTMLInputElement {
        return (document.createElement("input") as HTMLInputElement).apply {
            type = "text"
            className = "cloudbase-map-manual-input"
            placeholder = placeholderText
            autocomplete = "off"
            setAttribute("aria-label", label)
            setAttribute("data-testid", testId)
        }
    }

    private fun showManualForm() {
        manualNameInput?.value = ""
        manualCoordinatesInput?.value = ""
        manualError?.style?.display = "none"
        manualForm?.style?.display = "flex"
        manualNameInput?.focus()
    }

    private fun hideManualForm() {
        manualForm?.style?.display = "none"
    }

    private fun submitManualForm() {
        val name = manualNameInput?.value.orEmpty()
        val coordinates = manualCoordinatesInput?.value.orEmpty()
        when (val result = parseManualFavoriteInput(name, coordinates)) {
            is ManualFavoriteInputResult.Valid -> {
                onAddFavorite(result.input.toSavedPlace())
                hideManualForm()
                showStatus(strings.addedToFavorites.replace("%s", result.input.name), isError = false)
            }
            is ManualFavoriteInputResult.Invalid -> {
                manualError?.apply {
                    textContent = manualFavoriteErrorMessage(result.error)
                    style.display = "block"
                }
            }
        }
    }

    private fun manualFavoriteErrorMessage(error: ManualFavoriteInputError): String = when (error) {
        ManualFavoriteInputError.BLANK_NAME -> strings.manualErrorBlankName
        ManualFavoriteInputError.NAME_TOO_LONG -> strings.manualErrorNameTooLong
        ManualFavoriteInputError.BLANK_COORDINATES -> strings.manualErrorBlankCoordinates
        ManualFavoriteInputError.COORDINATES_FORMAT -> strings.manualErrorCoordinatesFormat
        ManualFavoriteInputError.LATITUDE_OUT_OF_RANGE -> strings.manualErrorLatitudeRange
        ManualFavoriteInputError.LONGITUDE_OUT_OF_RANGE -> strings.manualErrorLongitudeRange
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
            reportViewport(createdMap)
            updateNorthReset(createdMap)
        }
        subscriptions += createdMap.on("moveend") {
            saveCamera(createdMap)
            reportViewport(createdMap)
        }
        subscriptions += createdMap.on("rotate") {
            updateNorthReset(createdMap)
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
            showStatus(strings.mapProviderError, isError = true)
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
        val module = mapModule ?: return
        val desiredById = state.markers.associateBy { it.id }

        // Remove markers that disappeared or whose model changed; unchanged markers (e.g. the launch
        // flags, which dominate the count) are left in the DOM untouched, so no SVG is regenerated.
        markers.keys.toList().forEach { id ->
            val desired = desiredById[id]
            if (desired == null || desired != markers[id]?.model) {
                markers.remove(id)?.marker?.remove()
            }
        }

        // Add markers that are new or were just invalidated, preserving the intended draw order.
        state.markers.forEach { markerModel ->
            if (markers.containsKey(markerModel.id)) return@forEach
            val marker = createMapLibreMarker(module, markerOptionsFor(markerModel.kind))
                .setLngLat(lngLat(markerModel.location.longitude, markerModel.location.latitude))
                .addTo(currentMap)
            configureMarkerElement(marker.getElement(), markerModel, currentMap)
            markers[markerModel.id] = TrackedMarker(markerModel, marker)
        }
    }

    private fun configureMarkerElement(
        element: HTMLElement,
        markerModel: WebMapMarker,
        currentMap: MapLibreMap,
    ) {
        element.title = markerModel.title
        element.setAttribute("aria-label", markerModel.title)
        when (markerModel.kind) {
            WebMapMarkerKind.FAVORITE -> {
                element.style.cursor = "pointer"
                element.addEventListener("click", { event ->
                    event.stopPropagation()
                    onMapTap(markerModel.location)
                    flyToMarker(currentMap, markerModel.location)
                })
            }
            WebMapMarkerKind.LAUNCH_SITE, WebMapMarkerKind.FAVORITE_LAUNCH_SITE -> {
                val existingClass = element.className
                element.className =
                    if (existingClass.isBlank()) LAUNCH_MARKER_CLASS else "$existingClass $LAUNCH_MARKER_CLASS"
                element.setAttribute("data-launch-site-id", markerModel.launchSite?.id.orEmpty())
                element.style.cursor = "pointer"
                element.addEventListener("click", { event ->
                    event.stopPropagation()
                    markerModel.launchSite?.let(onLaunchSiteTap)
                })
            }
            WebMapMarkerKind.SELECTED -> Unit
        }
    }

    private fun flyToMarker(currentMap: MapLibreMap, location: PlaceLocation) {
        currentMap.flyTo(
            flyToOptions(
                longitude = location.longitude,
                latitude = location.latitude,
                zoom = maxOf(currentMap.getZoom(), DEVICE_LOCATION_ZOOM),
            ),
        )
    }

    private fun renderState(state: WebMapRenderState) {
        layerButtons.forEach { (layer, button) ->
            val selected = layer == state.layer
            button.className = "cloudbase-map-layer-button" + if (selected) " active" else ""
            button.setAttribute("aria-pressed", selected.toString())
        }
        val selectedMarker = state.markers.firstOrNull { it.kind == WebMapMarkerKind.SELECTED }
        val launchSite = state.selectedLaunchSite
        val hasSelection = selectedMarker != null || launchSite != null
        selectionCard?.style?.display = if (hasSelection) "flex" else "none"
        selectionLabel?.textContent = launchSite?.name ?: selectedMarker?.title.orEmpty()
        renderLaunchDetail(launchSite)
        confirmButton?.disabled = selectedMarker == null
        attributionButton?.textContent = state.layer.attributionCompact
        attributionDetail?.textContent = state.layer.attributionFull
        launchAttribution?.style?.display = if (state.showLaunchSites) "block" else "none"
    }

    private fun renderLaunchDetail(site: ParaglidingLaunchSite?) {
        if (site == null) clearLaunchDetail() else showLaunchDetail(site)
    }

    private fun clearLaunchDetail() {
        selectionDetail?.apply {
            textContent = ""
            style.display = "none"
        }
        selectionSource?.apply {
            textContent = ""
            style.display = "none"
        }
    }

    private fun showLaunchDetail(site: ParaglidingLaunchSite) {
        selectionDetail?.apply {
            textContent = launchDetailText(site)
            style.display = "block"
        }
        val source = selectionSource ?: return
        source.textContent = ""
        source.appendChild(document.createTextNode(strings.launchSiteDataPrefix))
        source.appendChild(domLink("ParaglidingEarth", site.link ?: PARAGLIDING_EARTH_HOME))
        source.appendChild(document.createTextNode(strings.launchSiteLicenseSuffix))
        source.style.display = "block"
    }

    private fun launchDetailText(site: ParaglidingLaunchSite): String {
        return buildList {
            site.altitudeMeters?.let { add(strings.launchDetailAltitude.replace("%s", it.toString())) }
            LaunchSiteDisplay.windDirectionsSummary(site)?.let { add(strings.launchDetailWind.replace("%s", it)) }
            LaunchSiteDisplay.activitiesSummary(site)?.let { add(strings.launchDetailActivities.replace("%s", it)) }
            site.landingName?.let { add(strings.launchDetailLanding.replace("%s", it)) }
            LaunchSiteDisplay.shortDescription(site)?.let { add(it) }
            add(formatCoordinates(site.toPlaceLocation()))
        }.joinToString(separator = "\n")
    }

    private fun confirmSelection() {
        latestState?.markers
            ?.firstOrNull { it.kind == WebMapMarkerKind.SELECTED }
            ?.location
            ?.let(onLocationConfirmed)
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

    private fun updateNorthReset(currentMap: MapLibreMap) {
        val rotated = abs(currentMap.getBearing()) > BEARING_EPSILON_DEGREES
        northResetButton?.style?.display = if (rotated) "flex" else "none"
    }

    private fun reportViewport(currentMap: MapLibreMap) {
        val bounds = currentMap.getBounds()
        onViewportChanged(
            LaunchSiteBounds.normalizedForMap(
                north = bounds.getNorth(),
                south = bounds.getSouth(),
                west = bounds.getWest(),
                east = bounds.getEast(),
                zoom = currentMap.getZoom(),
            ),
        )
    }

    private fun requestDeviceLocation() {
        showStatus(strings.findingLocation, isError = false)
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
                if (!disposed) showStatus(strings.locationError, isError = true)
            }
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
        markers.values.forEach { it.marker.remove() }
        markers.clear()
        map?.remove()
        map = null
        mapModule = null
        appliedLayer = null
    }

    // Pairs a live MapLibre marker with the model it was built from, so [syncMap] can tell whether a
    // marker of the same id still represents the same content and can be left in place.
    private class TrackedMarker(
        val model: WebMapMarker,
        val marker: MapLibreMarker,
    )
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

private fun domLink(label: String, href: String): HTMLAnchorElement {
    return (document.createElement("a") as HTMLAnchorElement).apply {
        textContent = label
        setAttribute("href", href)
        setAttribute("target", "_blank")
        setAttribute("rel", "noopener noreferrer")
    }
}

// Launch sites (and favorites saved on them) render as flags; plain favorites and the selected
// point keep MapLibre's default teardrop pin, distinguished only by color.
private fun markerOptionsFor(kind: WebMapMarkerKind): kotlin.js.JsAny = when (kind) {
    WebMapMarkerKind.LAUNCH_SITE -> flagMarkerOptions(LAUNCH_SITE_MARKER_COLOR)
    WebMapMarkerKind.FAVORITE_LAUNCH_SITE -> flagMarkerOptions(FAVORITE_MARKER_COLOR)
    WebMapMarkerKind.FAVORITE -> markerOptions(FAVORITE_MARKER_COLOR)
    WebMapMarkerKind.SELECTED -> markerOptions(SELECTED_MARKER_COLOR)
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
private const val LAUNCH_SITE_MARKER_COLOR = "#00796b"
private const val LAUNCH_MARKER_CLASS = "cloudbase-launch-marker"
private const val PARAGLIDING_EARTH_HOME = "https://www.paragliding.earth"
private const val INITIAL_MAP_ZOOM = 10.0
private const val DEFAULT_MAP_ZOOM = 5.5
private const val DEVICE_LOCATION_ZOOM = 12.0
private const val BEARING_EPSILON_DEGREES = 0.5

@Preview
@Composable
private fun WebMapDestinationPreview() {
    WebMapDestination(
        routeState = WebPreviewData.mapRoute,
        preferences = WebPreviewData.preferences,
        favoritePlaces = WebPreviewData.favoritePlaces,
        savedCamera = null,
        launchSiteRepository = WebPreviewData.launchSiteRepository,
        onMapLayerSelected = {},
        onLocationConfirmed = {},
        onCameraChanged = {},
        onAddFavorite = {},
    )
}
