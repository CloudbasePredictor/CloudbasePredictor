package com.cloudbasepredictor.ui.screens.map

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cloudbasepredictor.BuildConfig
import com.cloudbasepredictor.R
import com.cloudbasepredictor.data.map.MapLayerPreference
import com.cloudbasepredictor.model.PlaceLocation
import com.cloudbasepredictor.model.SavedPlace
import com.cloudbasepredictor.ui.components.MapAttributionOverlay
import com.cloudbasepredictor.ui.components.MapFavoriteLabelsOverlay
import com.cloudbasepredictor.ui.components.MapTestTags
import com.cloudbasepredictor.ui.map.MapRasterBaseLayer
import com.cloudbasepredictor.ui.map.mapBaseStyle
import com.cloudbasepredictor.ui.map.mapLayerAttributionDetailRes
import com.cloudbasepredictor.ui.map.mapLayerAttributionRes
import com.cloudbasepredictor.ui.preview.PreviewData
import com.cloudbasepredictor.ui.theme.CloudbasePredictorTheme
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import org.maplibre.compose.camera.CameraPosition
import org.maplibre.compose.camera.rememberCameraState
import org.maplibre.compose.expressions.dsl.const
import org.maplibre.compose.layers.CircleLayer
import org.maplibre.compose.location.DesiredAccuracy
import org.maplibre.compose.location.LocationPuck
import org.maplibre.compose.location.rememberDefaultLocationProvider
import org.maplibre.compose.location.rememberNullLocationProvider
import org.maplibre.compose.location.rememberUserLocationState
import org.maplibre.compose.map.MapOptions
import org.maplibre.compose.map.MaplibreMap
import org.maplibre.compose.map.OrnamentOptions
import org.maplibre.compose.sources.GeoJsonData
import org.maplibre.compose.sources.rememberGeoJsonSource
import org.maplibre.compose.util.ClickResult
import org.maplibre.spatialk.geojson.Feature
import org.maplibre.spatialk.geojson.Position
import kotlin.math.min
import kotlin.time.Duration.Companion.seconds

private const val GEOJSON_PROPERTY_NAME = "name"
private const val GEOJSON_PROPERTY_PLACE_ID = "placeId"
private const val FAVORITE_POINTS_LAYER_ID = "favorite-points"
private const val USER_LOCATION_LAYER_ID_PREFIX = "user-location"
private const val DEVICE_LOCATION_MIN_ZOOM = 12.0
private const val NORTH_BUTTON_VISIBILITY_THRESHOLD_DEGREES = 1.0
private val LOCATION_PERMISSIONS = arrayOf(
    Manifest.permission.ACCESS_FINE_LOCATION,
    Manifest.permission.ACCESS_COARSE_LOCATION,
)

@Composable
fun MapRoute(
    onOpenForecast: (PlaceLocation) -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: MapViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel) {
        viewModel.events.collectLatest { event ->
            when (event) {
                is MapEvent.OpenForecast -> onOpenForecast(event.placeLocation)
            }
        }
    }

    MapScreen(
        uiState = uiState,
        onMapTapped = viewModel::selectPoint,
        onFavoriteTapped = viewModel::selectFavoritePlace,
        onOpenForecast = viewModel::openSelectedForecast,
        onFavoriteClick = viewModel::openForecastForPlace,
        onManualFavoriteSave = viewModel::addManualFavorite,
        onSaveCameraPosition = viewModel::saveCameraPosition,
        onOpenSettings = onOpenSettings,
        onMapLayerSelected = viewModel::selectMapLayer,
    )
}

@SuppressLint("MissingPermission")
@Composable
fun MapScreen(
    uiState: MapUiState,
    onMapTapped: (Double, Double) -> Unit,
    onFavoriteTapped: (SavedPlace) -> Unit,
    onOpenForecast: () -> Unit,
    onFavoriteClick: (SavedPlace) -> Unit,
    onSaveCameraPosition: (Double, Double, Double) -> Unit,
    onManualFavoriteSave: (SavedPlace) -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onMapLayerSelected: (MapLayerPreference) -> Unit = {},
    autoOpenFavoritesOnStartup: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val unavailableMessage = stringResource(R.string.map_unavailable_message)
    val waitingForLocationMessage = stringResource(R.string.map_waiting_for_location)
    val locationPermissionDeniedMessage = stringResource(R.string.map_location_permission_denied)
    val scope = rememberCoroutineScope()
    var mapRetryKey by rememberSaveable { mutableIntStateOf(0) }
    var mapLoadError by rememberSaveable { mutableStateOf<String?>(null) }
    var hasLocationPermission by remember { mutableStateOf(context.hasAnyLocationPermission()) }
    var centerOnNextLocation by rememberSaveable { mutableStateOf(false) }

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

    val initialCamera = uiState.initialCamera
    val cameraState = rememberCameraState(
        firstPosition = CameraPosition(
            target = if (initialCamera != null) {
                Position(longitude = initialCamera.longitude, latitude = initialCamera.latitude)
            } else {
                Position(longitude = 13.4050, latitude = 52.5200)
            },
            zoom = initialCamera?.zoom ?: 5.5,
        )
    )
    val locationProvider = if (hasLocationPermission) {
        rememberDefaultLocationProvider(
            updateInterval = 5.seconds,
            desiredAccuracy = DesiredAccuracy.Balanced,
            minDistanceMeters = 5.0,
        )
    } else {
        rememberNullLocationProvider()
    }
    val userLocationState = rememberUserLocationState(locationProvider)
    val normalizedCameraBearing = normalizedBearingDegrees(cameraState.position.bearing)

    fun centerMapOnDeviceLocation(position: Position) {
        centerOnNextLocation = false
        scope.launch {
            cameraState.animateTo(cameraPositionForDeviceLocation(cameraState.position, position))
        }
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { permissionResults ->
        val permissionGranted = permissionResults.hasAnyLocationPermissionGrant()
        hasLocationPermission = permissionGranted

        if (permissionGranted) {
            val location = userLocationState.location
            if (location != null) {
                centerMapOnDeviceLocation(location.position)
            } else {
                centerOnNextLocation = true
                Toast.makeText(
                    context.applicationContext,
                    waitingForLocationMessage,
                    Toast.LENGTH_SHORT,
                ).show()
            }
        } else {
            centerOnNextLocation = false
            Toast.makeText(
                context.applicationContext,
                locationPermissionDeniedMessage,
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

    LaunchedEffect(centerOnNextLocation, userLocationState.location) {
        if (!centerOnNextLocation) return@LaunchedEffect

        val location = userLocationState.location ?: return@LaunchedEffect
        centerOnNextLocation = false
        cameraState.animateTo(cameraPositionForDeviceLocation(cameraState.position, location.position))
    }

    DisposableEffect(Unit) {
        onDispose {
            val pos = cameraState.position
            onSaveCameraPosition(
                pos.target.latitude,
                pos.target.longitude,
                pos.zoom,
            )
        }
    }

    val markerData = uiState.selectedPlace?.let(::buildMarkerFeatureCollection) ?: emptyFeatureCollection()
    val favoritesData = buildFavoritesFeatureCollection(uiState.favoritePlaces)

    var showFavoritesDialog by rememberSaveable { mutableStateOf(false) }
    var showManualFavoriteDialog by rememberSaveable { mutableStateOf(false) }
    var didAutoOpenFavoritesDialog by rememberSaveable { mutableStateOf(false) }
    var showMapLayerMenu by rememberSaveable { mutableStateOf(false) }
    val mapAttributionText = stringResource(mapLayerAttributionRes(uiState.mapLayer))
    val mapAttributionDetailText = mapLayerAttributionDetailRes(uiState.mapLayer)?.let { detailRes ->
        stringResource(detailRes)
    }

    LaunchedEffect(uiState.mapLayer) {
        mapLoadError = null
    }

    LaunchedEffect(autoOpenFavoritesOnStartup, uiState.favoritePlaces.size) {
        if (
            autoOpenFavoritesOnStartup &&
            !didAutoOpenFavoritesDialog &&
            uiState.favoritePlaces.size >= MIN_FAVORITES_FOR_STARTUP_DIALOG
        ) {
            showFavoritesDialog = true
            didAutoOpenFavoritesDialog = true
        }
    }

    Box(
        modifier = modifier.fillMaxSize(),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            key(mapRetryKey, uiState.mapLayer) {
                MaplibreMap(
                    modifier = Modifier.fillMaxSize(),
                    baseStyle = mapBaseStyle(uiState.mapLayer),
                    cameraState = cameraState,
                    options = MapOptions(
                        ornamentOptions = OrnamentOptions.AllDisabled,
                    ),
                    onMapLoadFailed = { reason ->
                        mapLoadError = reason?.takeIf { it.isNotBlank() } ?: unavailableMessage
                    },
                    onMapLoadFinished = {
                        mapLoadError = null
                    },
                    onMapClick = { position, offset ->
                        val favoritePlace = findFavoritePlaceForFeatures(
                            features = cameraState.projection
                                ?.queryRenderedFeatures(
                                    offset = offset,
                                    layerIds = setOf(FAVORITE_POINTS_LAYER_ID),
                                )
                                .orEmpty(),
                            favoritePlaces = uiState.favoritePlaces,
                        )
                        if (favoritePlace != null) {
                            onFavoriteTapped(favoritePlace)
                        } else {
                            onMapTapped(position.latitude, position.longitude)
                        }
                        ClickResult.Consume
                    },
                ) {
                    MapRasterBaseLayer(uiState.mapLayer)

                    val favoritesSource = rememberGeoJsonSource(
                        data = GeoJsonData.JsonString(favoritesData),
                    )
                    CircleLayer(
                        id = FAVORITE_POINTS_LAYER_ID,
                        source = favoritesSource,
                        color = const(Color(0xFFFFD700)),
                        radius = const(8.dp),
                        strokeColor = const(Color.White),
                        strokeWidth = const(2.dp),
                    )

                    val markerSource = rememberGeoJsonSource(
                        data = GeoJsonData.JsonString(markerData),
                    )
                    CircleLayer(
                        id = "selected-point",
                        source = markerSource,
                        color = const(Color(0xFFE64A5B)),
                        radius = const(9.dp),
                        strokeColor = const(Color.White),
                        strokeWidth = const(3.dp),
                    )

                    if (hasLocationPermission && userLocationState.location != null) {
                        LocationPuck(
                            idPrefix = USER_LOCATION_LAYER_ID_PREFIX,
                            locationState = userLocationState,
                            cameraState = cameraState,
                        )
                    }
                }
            }

            MapFavoriteLabelsOverlay(
                favoritePlaces = uiState.favoritePlaces,
                cameraState = cameraState,
                markerRadius = 8.dp,
                fontSize = 10.sp,
            )
        }

        if (mapLoadError != null) {
            MapUnavailableCard(
                onRetry = {
                    mapLoadError = null
                    mapRetryKey++
                },
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(16.dp),
            )
        }

        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(start = 12.dp, top = 42.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            MapChromeIconButton(
                onClick = { showFavoritesDialog = true },
                imageVector = Icons.Filled.Star,
                contentDescription = stringResource(R.string.cd_favorites),
                modifier = Modifier.testTag(MapTestTags.FAVORITES_BUTTON),
                contentColor = Color(0xFFFFD700),
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(end = 12.dp, top = 42.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.End,
        ) {
            MapChromeIconButton(
                onClick = onOpenSettings,
                imageVector = Icons.Outlined.Settings,
                contentDescription = stringResource(R.string.cd_settings),
                modifier = Modifier.testTag(MapTestTags.SETTINGS_BUTTON),
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            MapChromeIconButton(
                onClick = {
                    val permissionGranted = context.hasAnyLocationPermission()
                    hasLocationPermission = permissionGranted

                    if (!permissionGranted) {
                        locationPermissionLauncher.launch(LOCATION_PERMISSIONS)
                        return@MapChromeIconButton
                    }

                    val location = userLocationState.location
                    if (location != null) {
                        centerMapOnDeviceLocation(location.position)
                    } else {
                        centerOnNextLocation = true
                        Toast.makeText(
                            context.applicationContext,
                            waitingForLocationMessage,
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                },
                imageVector = Icons.Outlined.MyLocation,
                contentDescription = stringResource(R.string.cd_current_location),
                modifier = Modifier.testTag(MapTestTags.CURRENT_LOCATION_BUTTON),
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (shouldShowNorthButton(cameraState.position.bearing)) {
                MapChromeIconButton(
                    onClick = {
                        scope.launch {
                            cameraState.animateTo(cameraState.position.copy(bearing = 0.0))
                        }
                    },
                    imageVector = Icons.Outlined.Explore,
                    contentDescription = stringResource(R.string.cd_reset_north),
                    modifier = Modifier.testTag(MapTestTags.NORTH_BUTTON),
                    contentColor = MaterialTheme.colorScheme.primary,
                    iconModifier = Modifier.rotate(-normalizedCameraBearing.toFloat()),
                )
            }

            Box {
                MapChromeIconButton(
                    onClick = { showMapLayerMenu = true },
                    imageVector = Icons.Outlined.Layers,
                    contentDescription = stringResource(R.string.cd_map_layer),
                    modifier = Modifier.testTag(MapTestTags.LAYER_BUTTON),
                    contentColor = if (uiState.mapLayer != MapLayerPreference.OPENFREEMAP) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )

                DropdownMenu(
                    expanded = showMapLayerMenu,
                    onDismissRequest = { showMapLayerMenu = false },
                ) {
                    MapLayerPreference.entries.forEach { layer ->
                        DropdownMenuItem(
                            text = { Text(text = stringResource(layer.labelRes())) },
                            leadingIcon = {
                                RadioButton(
                                    selected = layer == uiState.mapLayer,
                                    onClick = null,
                                )
                            },
                            onClick = {
                                onMapLayerSelected(layer)
                                showMapLayerMenu = false
                            },
                        )
                    }
                }
            }
        }

        val selectedPlace = uiState.selectedPlace
        if (selectedPlace != null) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 32.dp),
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SelectedPointCard(
                    selectedPlace = selectedPlace,
                    onOpenForecast = onOpenForecast,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        MapAttributionOverlay(
            text = mapAttributionText,
            detailText = mapAttributionDetailText,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(end = 8.dp, bottom = 4.dp),
        )
    }

    if (showFavoritesDialog) {
        FavoritesListDialog(
            favorites = uiState.favoritePlaces,
            onPlaceClick = onFavoriteClick,
            onAddManualClick = {
                showFavoritesDialog = false
                showManualFavoriteDialog = true
            },
            onDismiss = { showFavoritesDialog = false },
            modifier = Modifier.testTag(MapTestTags.FAVORITES_DIALOG),
        )
    }

    if (showManualFavoriteDialog) {
        ManualFavoriteDialog(
            onSave = onManualFavoriteSave,
            onDismiss = { showManualFavoriteDialog = false },
            modifier = Modifier.testTag(MapTestTags.MANUAL_FAVORITE_DIALOG),
        )
    }
}

private const val MIN_FAVORITES_FOR_STARTUP_DIALOG = 2

@Composable
private fun MapChromeIconButton(
    imageVector: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    iconModifier: Modifier = Modifier,
) {
    FloatingActionButton(
        onClick = onClick,
        modifier = modifier.size(40.dp),
        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
        contentColor = contentColor,
        elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 2.dp),
    ) {
        Icon(
            imageVector = imageVector,
            contentDescription = contentDescription,
            modifier = iconModifier,
        )
    }
}

private fun MapLayerPreference.labelRes(): Int {
    return when (this) {
        MapLayerPreference.OPENFREEMAP -> R.string.map_layer_openfreemap
        MapLayerPreference.OPENTOPOMAP -> R.string.map_layer_opentopomap
        MapLayerPreference.NASA_GIBS -> R.string.map_layer_nasa_gibs
        MapLayerPreference.ESRI_WORLD_IMAGERY -> R.string.map_layer_esri_world_imagery
    }
}

@Composable
private fun MapUnavailableCard(
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.map_unavailable_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(R.string.map_unavailable_message),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = onRetry) {
                Text(text = stringResource(R.string.action_retry))
            }
        }
    }
}

@Composable
private fun SelectedPointCard(
    selectedPlace: SavedPlace,
    onOpenForecast: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = selectedPlace.name,
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = String.format(
                    java.util.Locale.US,
                    stringResource(R.string.coordinates_lat_lon_format),
                    selectedPlace.latitude,
                    selectedPlace.longitude,
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = onOpenForecast) {
                Text(text = stringResource(R.string.action_open))
            }
        }
    }
}

internal fun buildMarkerFeatureCollection(place: SavedPlace): String {
    return """
        {
          "type": "FeatureCollection",
          "features": [
            {
              "type": "Feature",
              "geometry": {
                "type": "Point",
                "coordinates": [${place.longitude}, ${place.latitude}]
              },
              "properties": {
                "$GEOJSON_PROPERTY_PLACE_ID": "${place.id.escapeJsonString()}",
                "$GEOJSON_PROPERTY_NAME": "${place.name.escapeJsonString()}"
              }
            }
          ]
        }
    """.trimIndent()
}

internal fun buildFavoritesFeatureCollection(places: List<SavedPlace>): String {
    if (places.isEmpty()) return emptyFeatureCollection()
    val features = places.joinToString(",") { place ->
        """
            {
              "type": "Feature",
              "geometry": {
                "type": "Point",
                "coordinates": [${place.longitude}, ${place.latitude}]
              },
              "properties": {
                "$GEOJSON_PROPERTY_PLACE_ID": "${place.id.escapeJsonString()}",
                "$GEOJSON_PROPERTY_NAME": "${place.name.escapeJsonString()}"
              }
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

internal fun emptyFeatureCollection(): String {
    return """
        {
          "type": "FeatureCollection",
          "features": []
        }
    """.trimIndent()
}

internal fun findFavoritePlaceForFeatures(
    features: List<Feature<*, JsonObject?>>,
    favoritePlaces: List<SavedPlace>,
): SavedPlace? {
    return features.firstNotNullOfOrNull { feature ->
        val placeId = feature.properties
            ?.get(GEOJSON_PROPERTY_PLACE_ID)
            ?.jsonPrimitive
            ?.contentOrNull

        favoritePlaces.firstOrNull { favorite -> favorite.id == placeId }
    }
}

internal fun normalizedBearingDegrees(bearing: Double): Double {
    val normalized = bearing % 360.0
    return if (normalized < 0.0) normalized + 360.0 else normalized
}

internal fun shouldShowNorthButton(
    bearing: Double,
    thresholdDegrees: Double = NORTH_BUTTON_VISIBILITY_THRESHOLD_DEGREES,
): Boolean {
    val normalizedBearing = normalizedBearingDegrees(bearing)
    val distanceToNorth = min(normalizedBearing, 360.0 - normalizedBearing)
    return distanceToNorth >= thresholdDegrees
}

private fun cameraPositionForDeviceLocation(
    currentPosition: CameraPosition,
    devicePosition: Position,
): CameraPosition {
    return currentPosition.copy(
        target = devicePosition,
        zoom = currentPosition.zoom.coerceAtLeast(DEVICE_LOCATION_MIN_ZOOM),
    )
}

private fun Context.hasAnyLocationPermission(): Boolean {
    return LOCATION_PERMISSIONS.any { permission ->
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
    }
}

private fun Map<String, Boolean>.hasAnyLocationPermissionGrant(): Boolean {
    return LOCATION_PERMISSIONS.any { permission -> this[permission] == true }
}

private fun String.escapeJsonString(): String {
    return buildString(length) {
        this@escapeJsonString.forEach { char ->
            when (char) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\b' -> append("\\b")
                '\u000C' -> append("\\f")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> {
                    if (char < ' ') {
                        append("\\u")
                        append(char.code.toString(16).padStart(4, '0'))
                    } else {
                        append(char)
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun SelectedPointCardPreview() {
    CloudbasePredictorTheme {
        SelectedPointCard(
            selectedPlace = PreviewData.mapUiState.selectedPlace ?: PreviewData.savedPlace,
            onOpenForecast = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun MapScreenPreview() {
    CloudbasePredictorTheme {
        MapScreen(
            uiState = PreviewData.mapUiState,
            onMapTapped = { _, _ -> },
            onFavoriteTapped = {},
            onOpenForecast = {},
            onFavoriteClick = {},
            onSaveCameraPosition = { _, _, _ -> },
            autoOpenFavoritesOnStartup = false,
        )
    }
}
