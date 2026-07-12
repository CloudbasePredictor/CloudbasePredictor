package com.cloudbasepredictor.ui.screens.forecast

import android.widget.Toast
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cloudbasepredictor.BuildConfig
import com.cloudbasepredictor.R
import com.cloudbasepredictor.data.map.MapLayerPreference
import com.cloudbasepredictor.model.FORECAST_MAP_LOCATION_UPDATE_MIN_DISTANCE_METERS
import com.cloudbasepredictor.model.FORECAST_MAP_LOCATION_UPDATE_RATE_LIMIT_MS
import com.cloudbasepredictor.model.ForecastMapLocation
import com.cloudbasepredictor.model.ForecastMapLocationUpdateDecision
import com.cloudbasepredictor.model.SavedPlace
import com.cloudbasepredictor.model.forecastMapDistanceMeters
import com.cloudbasepredictor.model.forecastMapLocationUpdateDecision
import com.cloudbasepredictor.ui.components.MapAttributionOverlay
import com.cloudbasepredictor.ui.screens.map.FavoriteLabelsLayer
import com.cloudbasepredictor.ui.map.MapRasterBaseLayer
import com.cloudbasepredictor.ui.map.mapBaseStyle
import com.cloudbasepredictor.ui.map.mapLayerAttributionDetailRes
import com.cloudbasepredictor.ui.map.mapLayerAttributionRes
import com.cloudbasepredictor.ui.screens.forecast.views.ForecastInformationView
import com.cloudbasepredictor.ui.theme.CloudbasePredictorTheme
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import org.maplibre.compose.camera.CameraPosition
import org.maplibre.compose.camera.rememberCameraState
import org.maplibre.compose.expressions.dsl.const
import org.maplibre.compose.layers.CircleLayer
import org.maplibre.compose.map.MapOptions
import org.maplibre.compose.map.MaplibreMap
import org.maplibre.compose.map.OrnamentOptions
import org.maplibre.compose.sources.GeoJsonData
import org.maplibre.compose.sources.rememberGeoJsonSource
import org.maplibre.compose.util.ClickResult
import org.maplibre.spatialk.geojson.Position
import kotlin.math.roundToInt

private const val DRAG_HANDLE_HEIGHT_DP = 24
private const val MAP_INITIAL_ZOOM = 12.0
private const val SNAP_THRESHOLD_FRACTION = 0.25f
private const val CAMERA_RECENTER_EPSILON_METERS = 5.0
private const val GEOJSON_PROPERTY_NAME = "name"

/**
 * A draggable map panel that sits at the bottom of the forecast screen.
 * The user can drag the handle upward to reveal a map (up to [maxFraction] of the parent height).
 * When the user moves the map to a new location, the forecast updates.
 */
@Composable
fun ForecastMapPanel(
    currentPlace: SavedPlace?,
    favoritePlaces: List<SavedPlace>,
    mapLayer: MapLayerPreference = MapLayerPreference.OPENFREEMAP,
    onLocationChanged: (latitude: Double, longitude: Double) -> Unit,
    modifier: Modifier = Modifier,
    maxFraction: Float = 1f / 3f,
    initiallyExpanded: Boolean = false,
    onPanelHeightChanged: (Float) -> Unit = {},
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val mapUnavailableMessage = stringResource(R.string.map_unavailable_message)
    val scope = rememberCoroutineScope()
    var parentHeightPx by remember { mutableFloatStateOf(0f) }
    val panelHeightAnim = remember { Animatable(0f) }
    val panelHeightPx = panelHeightAnim.value
    val handleHeightPx = with(density) { DRAG_HANDLE_HEIGHT_DP.dp.toPx() }

    val maxPanelHeightPx = parentHeightPx * maxFraction
    val rateLimitedMessage = stringResource(
        R.string.forecast_map_update_too_frequent,
        FORECAST_MAP_LOCATION_UPDATE_RATE_LIMIT_MS / 1_000f,
    )
    val tooCloseMessage = stringResource(
        R.string.forecast_map_update_too_close,
        FORECAST_MAP_LOCATION_UPDATE_MIN_DISTANCE_METERS.roundToInt(),
    )

    // Snap to expanded when initiallyExpanded is true and parent height is known
    LaunchedEffect(initiallyExpanded, maxPanelHeightPx) {
        if (initiallyExpanded && maxPanelHeightPx > 0f) {
            panelHeightAnim.snapTo(maxPanelHeightPx)
        }
    }

    // Report panel height changes to parent so chart can resize
    LaunchedEffect(panelHeightPx, handleHeightPx) {
        val totalHeight = (panelHeightPx + handleHeightPx).coerceAtLeast(handleHeightPx)
        onPanelHeightChanged(totalHeight)
    }

    val cameraState = rememberCameraState(
        firstPosition = CameraPosition(
            target = if (currentPlace != null) {
                Position(longitude = currentPlace.longitude, latitude = currentPlace.latitude)
            } else {
                Position(longitude = 13.4050, latitude = 52.5200)
            },
            zoom = MAP_INITIAL_ZOOM,
        ),
    )

    var lastUpdateTimeMs by remember { mutableLongStateOf(0L) }
    var lastForecastLocation by remember {
        mutableStateOf(currentPlace?.toForecastMapLocation())
    }
    var ignoredProgrammaticIdleTarget by remember { mutableStateOf<ForecastMapLocation?>(null) }
    var blockedUpdateMessage by remember { mutableStateOf<String?>(null) }
    var mapLoadError by rememberSaveable { mutableStateOf<String?>(null) }
    val mapAttributionText = stringResource(mapLayerAttributionRes(mapLayer))
    val mapAttributionDetailText = mapLayerAttributionDetailRes(mapLayer)?.let { detailRes ->
        stringResource(detailRes)
    }

    // Re-center camera when currentPlace changes
    LaunchedEffect(currentPlace?.latitude, currentPlace?.longitude) {
        if (currentPlace != null) {
            val targetLocation = currentPlace.toForecastMapLocation()
            lastForecastLocation = targetLocation

            val cameraLocation = cameraState.position.target.toForecastMapLocation()
            if (forecastMapDistanceMeters(cameraLocation, targetLocation) > CAMERA_RECENTER_EPSILON_METERS) {
                ignoredProgrammaticIdleTarget = targetLocation
                cameraState.animateTo(
                    CameraPosition(
                        target = Position(longitude = currentPlace.longitude, latitude = currentPlace.latitude),
                        zoom = MAP_INITIAL_ZOOM,
                    ),
                )
            }
        }
    }

    LaunchedEffect(mapLayer) {
        mapLoadError = null
    }

    LaunchedEffect(mapLoadError) {
        val error = mapLoadError ?: return@LaunchedEffect
        if (BuildConfig.DEBUG) {
            Toast.makeText(
                context.applicationContext,
                error,
                Toast.LENGTH_LONG,
            ).show()
        }
    }

    // When camera stops moving, update forecast location (rate-limited)
    LaunchedEffect(cameraState) {
        snapshotFlow { cameraState.isCameraMoving }
            .drop(1) // skip initial value
            .collectLatest { isMoving ->
                if (isMoving) {
                    blockedUpdateMessage = null
                    return@collectLatest
                }

                if (!isMoving && panelHeightAnim.value > handleHeightPx * 2) {
                    val now = System.currentTimeMillis()
                    val target = cameraState.position.target
                    val targetLocation = target.toForecastMapLocation()
                    val programmaticIdleTarget = ignoredProgrammaticIdleTarget
                    ignoredProgrammaticIdleTarget = null
                    if (
                        programmaticIdleTarget != null &&
                        forecastMapDistanceMeters(targetLocation, programmaticIdleTarget) <= CAMERA_RECENTER_EPSILON_METERS
                    ) {
                        return@collectLatest
                    }

                    when (
                        forecastMapLocationUpdateDecision(
                            nowMs = now,
                            lastUpdateTimeMs = lastUpdateTimeMs,
                            lastLocation = lastForecastLocation,
                            candidate = targetLocation,
                        )
                    ) {
                        ForecastMapLocationUpdateDecision.UPDATE -> {
                            lastUpdateTimeMs = now
                            lastForecastLocation = targetLocation
                            blockedUpdateMessage = null
                            onLocationChanged(target.latitude, target.longitude)
                        }
                        ForecastMapLocationUpdateDecision.TOO_SOON -> {
                            blockedUpdateMessage = rateLimitedMessage
                            Toast.makeText(context.applicationContext, rateLimitedMessage, Toast.LENGTH_SHORT).show()
                        }
                        ForecastMapLocationUpdateDecision.TOO_CLOSE -> {
                            blockedUpdateMessage = tooCloseMessage
                            Toast.makeText(context.applicationContext, tooCloseMessage, Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .onSizeChanged { parentHeightPx = it.height.toFloat() },
    ) {
        val totalPanelHeight = (panelHeightPx + handleHeightPx).coerceIn(handleHeightPx, maxPanelHeightPx + handleHeightPx)
        val panelOffsetY = parentHeightPx - totalPanelHeight

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(with(density) { totalPanelHeight.toDp() })
                .offset { IntOffset(0, panelOffsetY.roundToInt()) }
                .testTag(ForecastTestTags.MAP_PANEL_SURFACE),
            color = MaterialTheme.colorScheme.surfaceContainer,
            shape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp),
            tonalElevation = 4.dp,
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // Drag handle with snap behavior
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(DRAG_HANDLE_HEIGHT_DP.dp)
                        .pointerInput(maxPanelHeightPx) {
                            detectDragGestures(
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    val newHeight = (panelHeightAnim.value - dragAmount.y)
                                        .coerceIn(0f, maxPanelHeightPx)
                                    scope.launch { panelHeightAnim.snapTo(newHeight) }
                                },
                                onDragEnd = {
                                    scope.launch {
                                        val target = if (panelHeightAnim.value > maxPanelHeightPx * SNAP_THRESHOLD_FRACTION) {
                                            maxPanelHeightPx
                                        } else {
                                            0f
                                        }
                                        panelHeightAnim.animateTo(target, animationSpec = tween(200))
                                    }
                                },
                            )
                        }
                        .align(Alignment.TopCenter),
                    contentAlignment = Alignment.Center,
                ) {
                    // Visual handle indicator
                    Box(
                        modifier = Modifier
                            .width(32.dp)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)),
                    )
                }

                // Map content (only visible when dragged open)
                if (panelHeightPx > 0f) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = DRAG_HANDLE_HEIGHT_DP.dp)
                            .fillMaxSize(),
                    ) {
                        key(mapLayer) {
                            MaplibreMap(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp)),
                                baseStyle = mapBaseStyle(mapLayer),
                                cameraState = cameraState,
                                options = MapOptions(
                                    ornamentOptions = OrnamentOptions.AllDisabled,
                                ),
                                onMapLoadFailed = { reason ->
                                    mapLoadError = reason?.takeIf { it.isNotBlank() } ?: mapUnavailableMessage
                                },
                                onMapLoadFinished = {
                                    mapLoadError = null
                                },
                                onMapClick = { _, _ -> ClickResult.Consume },
                            ) {
                                MapRasterBaseLayer(mapLayer)

                                // Favorites markers (below selected marker)
                                val favoritesData = buildFavoritesGeoJson(favoritePlaces)
                                val favoritesSource = rememberGeoJsonSource(
                                    data = GeoJsonData.JsonString(favoritesData),
                                )
                                CircleLayer(
                                    id = "forecast-favorite-points",
                                    source = favoritesSource,
                                    color = const(Color(0xFFFFD700)),
                                    radius = const(6.dp),
                                    strokeColor = const(Color.White),
                                    strokeWidth = const(2.dp),
                                )

                                // Selected place marker (on top)
                                val markerData = currentPlace?.let(::buildPlaceGeoJson) ?: emptyGeoJson()
                                val markerSource = rememberGeoJsonSource(
                                    data = GeoJsonData.JsonString(markerData),
                                )
                                CircleLayer(
                                    id = "forecast-selected-point",
                                    source = markerSource,
                                    color = const(Color(0xFFE64A5B)),
                                    radius = const(7.dp),
                                    strokeColor = const(Color.White),
                                    strokeWidth = const(2.dp),
                                )

                                // Native favorite name labels, drawn in-map so
                                // they move in sync with the base map (no lag).
                                FavoriteLabelsLayer(
                                    places = favoritePlaces,
                                    markerRadius = 6.dp,
                                    idPrefix = "forecast-favorite-label",
                                    textSize = 9.sp,
                                )
                            }
                        }

                        // Crosshair center indicator
                        Box(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .width(2.dp)
                                .height(20.dp)
                                .background(Color.Black.copy(alpha = 0.4f)),
                        )
                        Box(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .width(20.dp)
                                .height(2.dp)
                                .background(Color.Black.copy(alpha = 0.4f)),
                        )

                        MapAttributionOverlay(
                            text = mapAttributionText,
                            detailText = mapAttributionDetailText,
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(end = 8.dp, bottom = 8.dp),
                        )

                        when {
                            mapLoadError != null -> ForecastInformationView(
                                message = mapUnavailableMessage,
                            )
                            blockedUpdateMessage != null -> ForecastInformationView(
                                message = blockedUpdateMessage.orEmpty(),
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun SavedPlace.toForecastMapLocation(): ForecastMapLocation {
    return ForecastMapLocation(latitude = latitude, longitude = longitude)
}

private fun Position.toForecastMapLocation(): ForecastMapLocation {
    return ForecastMapLocation(latitude = latitude, longitude = longitude)
}

private fun buildPlaceGeoJson(place: SavedPlace): String {
    return """
        {
          "type": "FeatureCollection",
          "features": [{
            "type": "Feature",
            "geometry": {
              "type": "Point",
              "coordinates": [${place.longitude}, ${place.latitude}]
            },
            "properties": {"$GEOJSON_PROPERTY_NAME": "${place.name.replace("\"", "\\\"")}" }
          }]
        }
    """.trimIndent()
}

private fun buildFavoritesGeoJson(places: List<SavedPlace>): String {
    if (places.isEmpty()) return emptyGeoJson()
    val features = places.joinToString(",") { place ->
        """
            {
              "type": "Feature",
              "geometry": {
                "type": "Point",
                "coordinates": [${place.longitude}, ${place.latitude}]
              },
              "properties": {"$GEOJSON_PROPERTY_NAME": "${place.name.replace("\"", "\\\"")}" }
            }
        """
    }
    return """
        {
          "type": "FeatureCollection",
          "features": [$features]
        }
    """.trimIndent()
}

private fun emptyGeoJson(): String {
    return """
        {
          "type": "FeatureCollection",
          "features": []
        }
    """.trimIndent()
}

@Preview(showBackground = true, heightDp = 400)
@Composable
private fun ForecastMapPanelPreview() {
    CloudbasePredictorTheme {
        ForecastMapPanel(
            currentPlace = com.cloudbasepredictor.ui.preview.PreviewData.savedPlace,
            favoritePlaces = com.cloudbasepredictor.ui.preview.PreviewData.favoritePlaces,
            onLocationChanged = { _, _ -> },
        )
    }
}

@Preview(showBackground = true, heightDp = 400, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ForecastMapPanelDarkPreview() {
    CloudbasePredictorTheme {
        ForecastMapPanel(
            currentPlace = com.cloudbasepredictor.ui.preview.PreviewData.savedPlace,
            favoritePlaces = com.cloudbasepredictor.ui.preview.PreviewData.favoritePlaces,
            onLocationChanged = { _, _ -> },
        )
    }
}
