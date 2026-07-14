@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
@file:Suppress("FunctionNaming")

package com.cloudbasepredictor.web.forecast

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.HtmlElementView
import com.cloudbasepredictor.data.map.MapCameraPosition
import com.cloudbasepredictor.data.map.MapLayerPreference
import com.cloudbasepredictor.model.ForecastMapLocation
import com.cloudbasepredictor.model.ForecastMapLocationUpdateDecision
import com.cloudbasepredictor.model.PlaceLocation
import com.cloudbasepredictor.model.forecastMapDistanceMeters
import com.cloudbasepredictor.model.forecastMapLocationUpdateDecision
import com.cloudbasepredictor.web.map.MapLibreMap
import com.cloudbasepredictor.web.map.MapLibreSubscription
import com.cloudbasepredictor.web.i18n.LocalWebStrings
import com.cloudbasepredictor.web.map.WebForecastPanelInterop
import com.cloudbasepredictor.web.map.WebResizeObserver
import com.cloudbasepredictor.web.preview.WebPreviewData
import kotlinx.browser.document
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.await
import kotlinx.coroutines.launch
import org.w3c.dom.HTMLDivElement

@OptIn(ExperimentalComposeUiApi::class)
@Suppress("LongMethod")
@Composable
internal actual fun WebForecastMapPanel(
    currentLocation: PlaceLocation,
    mapLayer: MapLayerPreference,
    onLocationChanged: (PlaceLocation) -> Unit,
    modifier: Modifier,
) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val mapAriaLabel = LocalWebStrings.current.mapAriaLabel
    val handleHeightPx = with(density) { HandleHeight.toPx() }
    var parentHeightPx by remember { mutableFloatStateOf(0f) }
    val panelHeight = remember { Animatable(0f) }
    val maxPanelPx = (parentHeightPx * MAX_FRACTION).coerceAtLeast(0f)

    var lastUpdateMs by remember { mutableLongStateOf(0L) }
    var lastLocation by remember {
        mutableStateOf(ForecastMapLocation(currentLocation.latitude, currentLocation.longitude))
    }
    val currentOnLocationChanged = rememberUpdatedState(onLocationChanged)
    val binding = remember {
        ForecastMapPanelBinding(
            scope = scope,
            onCenterIdle = { latitude, longitude ->
                val candidate = ForecastMapLocation(latitude, longitude)
                val decision = forecastMapLocationUpdateDecision(
                    nowMs = panelNowMillis(),
                    lastUpdateTimeMs = lastUpdateMs,
                    lastLocation = lastLocation,
                    candidate = candidate,
                )
                if (decision == ForecastMapLocationUpdateDecision.UPDATE) {
                    lastUpdateMs = panelNowMillis()
                    lastLocation = candidate
                    currentOnLocationChanged.value(PlaceLocation(latitude = latitude, longitude = longitude))
                }
            },
        )
    }

    // Recenter the map when the forecast location changes from outside the panel (e.g. a favorite
    // jump), suppressing the resulting idle event so it does not echo back as an update.
    LaunchedEffect(currentLocation.latitude, currentLocation.longitude) {
        lastLocation = ForecastMapLocation(currentLocation.latitude, currentLocation.longitude)
        binding.recenter(currentLocation)
    }

    Box(
        modifier = modifier.onSizeChanged { parentHeightPx = it.height.toFloat() },
    ) {
        val totalPanelPx = (panelHeight.value + handleHeightPx)
            .coerceIn(handleHeightPx, maxPanelPx + handleHeightPx)
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .height(with(density) { totalPanelPx.toDp() }),
            color = MaterialTheme.colorScheme.surfaceContainer,
            shape = RoundedCornerShape(topStart = PanelCorner, topEnd = PanelCorner),
            tonalElevation = PanelElevation,
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(HandleHeight)
                        .pointerInput(maxPanelPx) {
                            detectDragGestures(
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    val next = (panelHeight.value - dragAmount.y).coerceIn(0f, maxPanelPx)
                                    scope.launch { panelHeight.snapTo(next) }
                                },
                                onDragEnd = {
                                    scope.launch {
                                        val target =
                                            if (panelHeight.value > maxPanelPx * SNAP_THRESHOLD_FRACTION) {
                                                maxPanelPx
                                            } else {
                                                0f
                                            }
                                        panelHeight.animateTo(target, tween(SNAP_ANIMATION_MILLIS))
                                    }
                                },
                            )
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .width(HandleBarWidth)
                            .height(HandleBarHeight)
                            .clip(RoundedCornerShape(HandleBarHeight))
                            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = HANDLE_ALPHA)),
                    )
                }

                if (panelHeight.value > 0f) {
                    ForecastPanelMapArea(
                        binding = binding,
                        initialLocation = currentLocation,
                        mapLayer = mapLayer,
                        mapAriaLabel = mapAriaLabel,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun ForecastPanelMapArea(
    binding: ForecastMapPanelBinding,
    initialLocation: PlaceLocation,
    mapLayer: MapLayerPreference,
    mapAriaLabel: String,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        HtmlElementView(
            modifier = Modifier.fillMaxSize(),
            factory = {
                (document.createElement("div") as HTMLDivElement).apply {
                    className = "cloudbase-forecast-map"
                    setAttribute("aria-label", mapAriaLabel)
                    style.width = "100%"
                    style.height = "100%"
                }.also { host ->
                    binding.attach(
                        host,
                        MapCameraPosition(initialLocation.latitude, initialLocation.longitude, PANEL_MAP_ZOOM),
                        mapLayer,
                    )
                }
            },
            update = { host -> binding.update(host, mapLayer) },
            onRelease = { host -> binding.release(host) },
            onReset = null,
        )
        // Center crosshair (the panned center is the selected forecast location).
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .width(CrosshairThickness)
                .height(CrosshairLength)
                .background(Color.Black.copy(alpha = CROSSHAIR_ALPHA)),
        )
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .width(CrosshairLength)
                .height(CrosshairThickness)
                .background(Color.Black.copy(alpha = CROSSHAIR_ALPHA)),
        )
    }
}

private class ForecastMapPanelBinding(
    private val scope: CoroutineScope,
    private val onCenterIdle: (Double, Double) -> Unit,
) {
    private var disposed = false
    private var loadJob: Job? = null
    private var map: MapLibreMap? = null
    private var mapModule: kotlin.js.JsAny? = null
    private var resizeObserver: WebResizeObserver? = null
    private val subscriptions = mutableListOf<MapLibreSubscription>()
    private var root: HTMLDivElement? = null
    private var appliedLayer: MapLayerPreference? = null
    private var suppressNextIdle = false

    fun attach(host: HTMLDivElement, camera: MapCameraPosition, layer: MapLayerPreference) {
        root = host
        appliedLayer = layer
        disposed = false
        loadJob = scope.launch {
            val module = WebForecastPanelInterop.loadBundle().await()
            if (disposed || root !== host) return@launch
            mapModule = module
            val created = WebForecastPanelInterop.createMap(module, host, camera, layer)
            map = created
            subscriptions += created.on("moveend") { handleIdle(created) }
            resizeObserver = WebForecastPanelInterop.observeResize(host) { created.resize() }
        }
    }

    fun update(host: HTMLDivElement, layer: MapLayerPreference) {
        if (root !== host || disposed) return
        val currentMap = map ?: return
        if (appliedLayer != layer) {
            WebForecastPanelInterop.applyStyle(currentMap, layer)
            appliedLayer = layer
        }
    }

    fun recenter(location: PlaceLocation) {
        val currentMap = map ?: return
        val center = currentMap.getCenter()
        val distance = forecastMapDistanceMeters(
            ForecastMapLocation(center.lat, center.lng),
            ForecastMapLocation(location.latitude, location.longitude),
        )
        if (distance > RECENTER_EPSILON_METERS) {
            suppressNextIdle = true
            WebForecastPanelInterop.flyTo(currentMap, location.longitude, location.latitude)
        }
    }

    fun release(host: HTMLDivElement) {
        if (root !== host) return
        disposed = true
        loadJob?.cancel()
        loadJob = null
        resizeObserver?.disconnect()
        resizeObserver = null
        subscriptions.forEach(MapLibreSubscription::unsubscribe)
        subscriptions.clear()
        map?.remove()
        map = null
        mapModule = null
        root = null
    }

    private fun handleIdle(currentMap: MapLibreMap) {
        if (suppressNextIdle) {
            suppressNextIdle = false
            return
        }
        val center = currentMap.getCenter()
        onCenterIdle(center.lat, center.lng)
    }
}

private fun panelNowMillis(): Long = panelNowMillisAsDouble().toLong()

private fun panelNowMillisAsDouble(): Double = js("Date.now()")

private val HandleHeight = 24.dp
private val HandleBarWidth = 32.dp
private val HandleBarHeight = 4.dp
private val PanelCorner = 12.dp
private val PanelElevation = 4.dp
private val CrosshairThickness = 2.dp
private val CrosshairLength = 20.dp
private const val HANDLE_ALPHA = 0.4f
private const val CROSSHAIR_ALPHA = 0.4f
private const val MAX_FRACTION = 1f / 3f
private const val SNAP_THRESHOLD_FRACTION = 0.25f
private const val SNAP_ANIMATION_MILLIS = 200
private const val PANEL_MAP_ZOOM = 12.0
private const val RECENTER_EPSILON_METERS = 5.0

@Preview
@Composable
private fun WebForecastMapPanelPreview() {
    WebForecastMapPanel(
        currentLocation = WebPreviewData.brauneck,
        mapLayer = MapLayerPreference.OPENFREEMAP,
        onLocationChanged = {},
        modifier = Modifier.fillMaxSize(),
    )
}
