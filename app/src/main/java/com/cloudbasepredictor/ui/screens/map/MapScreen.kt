package com.cloudbasepredictor.ui.screens.map

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Flag
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
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cloudbasepredictor.BuildConfig
import com.cloudbasepredictor.R
import com.cloudbasepredictor.data.map.MapLayerPreference
import com.cloudbasepredictor.model.ParaglidingLaunchSite
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
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import org.maplibre.compose.camera.CameraPosition
import org.maplibre.compose.camera.CameraProjection
import org.maplibre.compose.camera.CameraState
import org.maplibre.compose.camera.rememberCameraState
import org.maplibre.compose.expressions.dsl.const
import org.maplibre.compose.expressions.dsl.image
import org.maplibre.compose.expressions.value.SymbolAnchor
import org.maplibre.compose.layers.CircleLayer
import org.maplibre.compose.layers.SymbolLayer
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
import kotlin.math.hypot
import kotlin.math.min
import kotlin.time.Duration.Companion.seconds

private const val GEOJSON_PROPERTY_NAME = "name"
private const val GEOJSON_PROPERTY_PLACE_ID = "placeId"
private const val GEOJSON_PROPERTY_LAUNCH_SITE_ID = "launchSiteId"
private const val FAVORITE_POINTS_LAYER_ID = "favorite-points"
private const val LAUNCH_SITES_LAYER_ID = "paragliding-launch-sites"
private const val USER_LOCATION_LAYER_ID_PREFIX = "user-location"
private const val DEVICE_LOCATION_MIN_ZOOM = 12.0
private const val NORTH_BUTTON_VISIBILITY_THRESHOLD_DEGREES = 1.0
private val LAUNCH_SITE_ICON_SIZE = 22.dp
private val LAUNCH_SITE_TOUCH_TARGET_SIZE = 56.dp
private val LAUNCH_SITE_HIT_SLOP = 40.dp
private val FAVORITE_TOUCH_TARGET_SIZE = 48.dp
private val FAVORITE_HIT_SLOP = 24.dp
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
        onLaunchSiteTapped = viewModel::selectLaunchSite,
        onOpenForecast = viewModel::openSelectedForecast,
        onFavoriteClick = viewModel::openForecastForPlace,
        onManualFavoriteSave = viewModel::addManualFavorite,
        onSaveCameraPosition = viewModel::saveCameraPosition,
        onOpenSettings = onOpenSettings,
        onMapLayerSelected = viewModel::selectMapLayer,
        onVisibleBoundsChanged = viewModel::loadLaunchSitesForVisibleBounds,
    )
}

@SuppressLint("MissingPermission")
@Composable
fun MapScreen(
    uiState: MapUiState,
    onMapTapped: (Double, Double) -> Unit,
    onFavoriteTapped: (SavedPlace) -> Unit,
    onLaunchSiteTapped: (ParaglidingLaunchSite) -> Unit,
    onOpenForecast: () -> Unit,
    onFavoriteClick: (SavedPlace) -> Unit,
    onSaveCameraPosition: (Double, Double, Double) -> Unit,
    onManualFavoriteSave: (SavedPlace) -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onMapLayerSelected: (MapLayerPreference) -> Unit = {},
    onVisibleBoundsChanged: (north: Double, south: Double, west: Double, east: Double, zoom: Double) -> Unit = { _, _, _, _, _ -> },
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

    fun requestLaunchSitesForVisibleBounds() {
        if (!uiState.showLaunchSites) return

        val bounds = cameraState.projection?.queryVisibleBoundingBox() ?: return
        onVisibleBoundsChanged(
            bounds.north,
            bounds.south,
            bounds.west,
            bounds.east,
            cameraState.position.zoom,
        )
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

    LaunchedEffect(uiState.showLaunchSites) {
        if (uiState.showLaunchSites) {
            requestLaunchSitesForVisibleBounds()
        }
    }

    LaunchedEffect(cameraState) {
        snapshotFlow { cameraState.isCameraMoving }
            .drop(1)
            .collectLatest { isMoving ->
                if (!isMoving) {
                    requestLaunchSitesForVisibleBounds()
                }
            }
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
    val launchSitesData = if (uiState.showLaunchSites) {
        buildLaunchSitesFeatureCollection(uiState.launchSites)
    } else {
        emptyFeatureCollection()
    }

    var showFavoritesDialog by rememberSaveable { mutableStateOf(false) }
    var showManualFavoriteDialog by rememberSaveable { mutableStateOf(false) }
    var didAutoOpenFavoritesDialog by rememberSaveable { mutableStateOf(false) }
    var showMapLayerMenu by rememberSaveable { mutableStateOf(false) }
    val baseMapAttributionText = stringResource(mapLayerAttributionRes(uiState.mapLayer))
    val launchSiteAttributionText = stringResource(R.string.map_attribution_paraglidingearth_compact)
    val mapAttributionText = if (uiState.showLaunchSites) {
        stringResource(
            R.string.map_attribution_combined_format,
            baseMapAttributionText,
            launchSiteAttributionText,
        )
    } else {
        baseMapAttributionText
    }
    val baseMapAttributionDetailText = mapLayerAttributionDetailRes(uiState.mapLayer)?.let { detailRes ->
        stringResource(detailRes)
    }
    val launchSiteAttributionDetailText = stringResource(R.string.map_attribution_paraglidingearth_full)
    val mapAttributionDetailText = if (uiState.showLaunchSites) {
        listOfNotNull(
            baseMapAttributionDetailText,
            launchSiteAttributionDetailText,
        ).joinToString("\n")
    } else {
        baseMapAttributionDetailText
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
                        requestLaunchSitesForVisibleBounds()
                    },
                    onMapClick = { position, offset ->
                        val projection = cameraState.projection
                        val launchSiteFeatures = projection
                            ?.queryRenderedFeatures(
                                offset = offset,
                                layerIds = setOf(LAUNCH_SITES_LAYER_ID),
                            )
                            .orEmpty()
                        val favoriteFeatures = projection
                            ?.queryRenderedFeatures(
                                offset = offset,
                                layerIds = setOf(FAVORITE_POINTS_LAYER_ID),
                            )
                            .orEmpty()
                        val favoritePlaceScreenOffsets = projection
                            ?.favoritePlaceScreenOffsets(uiState.favoritePlaces)
                            .orEmpty()
                        val launchSiteScreenOffsets = projection
                            ?.launchSiteScreenOffsets(uiState.launchSites)
                            .orEmpty()

                        val clickTarget = resolveMapClickTarget(
                            position = position,
                            clickOffset = offset,
                            launchSiteFeatures = launchSiteFeatures,
                            favoriteFeatures = favoriteFeatures,
                            favoritePlaces = uiState.favoritePlaces,
                            launchSites = uiState.launchSites,
                            favoritePlaceScreenOffsets = favoritePlaceScreenOffsets,
                            launchSiteScreenOffsets = launchSiteScreenOffsets,
                        )
                        when (clickTarget) {
                            is MapClickTarget.LaunchSite -> onLaunchSiteTapped(clickTarget.launchSite)
                            is MapClickTarget.FavoritePlace -> onFavoriteTapped(clickTarget.place)
                            is MapClickTarget.Coordinates -> onMapTapped(
                                clickTarget.latitude,
                                clickTarget.longitude,
                            )
                        }
                        ClickResult.Consume
                    },
                ) {
                    MapRasterBaseLayer(uiState.mapLayer)

                    val launchSitesSource = rememberGeoJsonSource(
                        data = GeoJsonData.JsonString(launchSitesData),
                    )
                    val launchSiteIconPainter = rememberVectorPainter(Icons.Filled.Flag)
                    SymbolLayer(
                        id = LAUNCH_SITES_LAYER_ID,
                        source = launchSitesSource,
                        iconImage = image(
                            value = launchSiteIconPainter,
                            size = DpSize(LAUNCH_SITE_ICON_SIZE, LAUNCH_SITE_ICON_SIZE),
                            drawAsSdf = true,
                        ),
                        iconColor = const(Color(0xFF00796B)),
                        iconHaloColor = const(Color.White),
                        iconHaloWidth = const(1.dp),
                        iconAnchor = const(SymbolAnchor.Bottom),
                        iconAllowOverlap = const(true),
                        iconIgnorePlacement = const(true),
                        onClick = { features ->
                            val launchSite = findLaunchSiteForFeatures(
                                features = features,
                                launchSites = uiState.launchSites,
                            )
                            if (launchSite != null) {
                                onLaunchSiteTapped(launchSite)
                                ClickResult.Consume
                            } else {
                                ClickResult.Pass
                            }
                        },
                    )

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

            MapLaunchSiteTapTargetsOverlay(
                launchSites = uiState.launchSites,
                cameraState = cameraState,
                onLaunchSiteTapped = onLaunchSiteTapped,
            )

            MapFavoriteTapTargetsOverlay(
                favoritePlaces = uiState.favoritePlaces,
                cameraState = cameraState,
                onFavoriteTapped = onFavoriteTapped,
            )

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
        val selectedLaunchSite = uiState.selectedLaunchSite
        if (selectedLaunchSite != null || selectedPlace != null) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 32.dp),
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (selectedLaunchSite != null) {
                    LaunchSiteCard(
                        launchSite = selectedLaunchSite,
                        onOpenForecast = onOpenForecast,
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else if (selectedPlace != null) {
                    SelectedPointCard(
                        selectedPlace = selectedPlace,
                        onOpenForecast = onOpenForecast,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
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
private fun MapFavoriteTapTargetsOverlay(
    favoritePlaces: List<SavedPlace>,
    cameraState: CameraState,
    onFavoriteTapped: (SavedPlace) -> Unit,
    modifier: Modifier = Modifier,
) {
    val projection = cameraState.projection ?: return
    val cameraPosition = cameraState.position
    var mapSize by remember { mutableStateOf(IntSize.Zero) }

    val favoritePlaceOffsets = remember(favoritePlaces, projection, cameraPosition, mapSize) {
        projection.favoritePlaceScreenOffsets(favoritePlaces)
    }

    MapFavoriteTapTargetsOverlayContent(
        favoritePlaceOffsets = favoritePlaceOffsets,
        mapSize = mapSize,
        onFavoriteTapped = onFavoriteTapped,
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { mapSize = it },
    )
}

@Composable
private fun MapFavoriteTapTargetsOverlayContent(
    favoritePlaceOffsets: List<FavoritePlaceScreenOffset>,
    mapSize: IntSize,
    onFavoriteTapped: (SavedPlace) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val mapWidth = with(density) { mapSize.width.toDp() }
    val mapHeight = with(density) { mapSize.height.toDp() }

    Box(modifier = modifier) {
        favoritePlaceOffsets
            .filter { placeOffset ->
                val topLeft = centeredTapTargetOffset(
                    anchorOffset = placeOffset.screenOffset,
                    targetSize = FAVORITE_TOUCH_TARGET_SIZE,
                )
                topLeft.x <= mapWidth &&
                    topLeft.x + FAVORITE_TOUCH_TARGET_SIZE >= 0.dp &&
                    topLeft.y <= mapHeight &&
                    topLeft.y + FAVORITE_TOUCH_TARGET_SIZE >= 0.dp
            }
            .forEach { placeOffset ->
                val place = placeOffset.place
                val topLeft = centeredTapTargetOffset(
                    anchorOffset = placeOffset.screenOffset,
                    targetSize = FAVORITE_TOUCH_TARGET_SIZE,
                )
                val interactionSource = remember(place.id) { MutableInteractionSource() }
                val contentDescription = stringResource(R.string.cd_favorite_marker, place.name)

                Box(
                    modifier = Modifier
                        .size(FAVORITE_TOUCH_TARGET_SIZE)
                        .offset(x = topLeft.x, y = topLeft.y)
                        .align(Alignment.TopStart)
                        .semantics {
                            this.contentDescription = contentDescription
                        }
                        .testTag(MapTestTags.FAVORITE_TAP_TARGET_PREFIX + place.id)
                        .clickable(
                            interactionSource = interactionSource,
                            indication = null,
                            onClick = { onFavoriteTapped(place) },
                        ),
                )
            }
    }
}

@Composable
private fun MapLaunchSiteTapTargetsOverlay(
    launchSites: List<ParaglidingLaunchSite>,
    cameraState: CameraState,
    onLaunchSiteTapped: (ParaglidingLaunchSite) -> Unit,
    modifier: Modifier = Modifier,
) {
    val projection = cameraState.projection ?: return
    val cameraPosition = cameraState.position
    var mapSize by remember { mutableStateOf(IntSize.Zero) }

    val launchSiteOffsets = remember(launchSites, projection, cameraPosition, mapSize) {
        projection.launchSiteScreenOffsets(launchSites)
    }

    MapLaunchSiteTapTargetsOverlayContent(
        launchSiteOffsets = launchSiteOffsets,
        mapSize = mapSize,
        onLaunchSiteTapped = onLaunchSiteTapped,
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { mapSize = it },
    )
}

@Composable
private fun MapLaunchSiteTapTargetsOverlayContent(
    launchSiteOffsets: List<LaunchSiteScreenOffset>,
    mapSize: IntSize,
    onLaunchSiteTapped: (ParaglidingLaunchSite) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val mapWidth = with(density) { mapSize.width.toDp() }
    val mapHeight = with(density) { mapSize.height.toDp() }

    Box(modifier = modifier) {
        launchSiteOffsets
            .filter { siteOffset ->
                val topLeft = launchSiteTapTargetOffset(siteOffset.screenOffset)
                topLeft.x <= mapWidth &&
                    topLeft.x + LAUNCH_SITE_TOUCH_TARGET_SIZE >= 0.dp &&
                    topLeft.y <= mapHeight &&
                    topLeft.y + LAUNCH_SITE_TOUCH_TARGET_SIZE >= 0.dp
            }
            .forEach { siteOffset ->
                val site = siteOffset.launchSite
                val topLeft = launchSiteTapTargetOffset(siteOffset.screenOffset)
                val interactionSource = remember(site.id) { MutableInteractionSource() }
                val contentDescription = stringResource(R.string.cd_launch_site_marker, site.name)

                Box(
                    modifier = Modifier
                        .size(LAUNCH_SITE_TOUCH_TARGET_SIZE)
                        .offset(x = topLeft.x, y = topLeft.y)
                        .align(Alignment.TopStart)
                        .semantics {
                            this.contentDescription = contentDescription
                        }
                        .testTag(MapTestTags.LAUNCH_SITE_TAP_TARGET_PREFIX + site.id)
                        .clickable(
                            interactionSource = interactionSource,
                            indication = null,
                            onClick = { onLaunchSiteTapped(site) },
                        ),
                )
            }
    }
}

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
private fun LaunchSiteCard(
    launchSite: ParaglidingLaunchSite,
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
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = launchSite.name,
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(R.string.map_launch_site_source),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )

            launchSite.altitudeMeters?.let { altitudeMeters ->
                Text(
                    text = stringResource(R.string.map_launch_site_altitude_format, altitudeMeters),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            launchSite.windSummary()?.let { windSummary ->
                Text(
                    text = stringResource(R.string.map_launch_site_wind_format, windSummary),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            launchSite.activities.takeIf { it.isNotEmpty() }?.let { activities ->
                Text(
                    text = stringResource(
                        R.string.map_launch_site_activity_format,
                        activities.joinToString(", "),
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            launchSite.landingName?.let { landingName ->
                Text(
                    text = stringResource(R.string.map_launch_site_landing_format, landingName),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            launchSite.cardDescription()?.let { description ->
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                )
            }

            Text(
                text = String.format(
                    java.util.Locale.US,
                    stringResource(R.string.coordinates_lat_lon_format),
                    launchSite.latitude,
                    launchSite.longitude,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Text(
                text = stringResource(R.string.map_launch_site_data_attribution),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Button(onClick = onOpenForecast) {
                Text(text = stringResource(R.string.action_open))
            }
        }
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

internal fun buildLaunchSitesFeatureCollection(sites: List<ParaglidingLaunchSite>): String {
    if (sites.isEmpty()) return emptyFeatureCollection()
    val features = sites.joinToString(",") { site ->
        """
            {
              "type": "Feature",
              "geometry": {
                "type": "Point",
                "coordinates": [${site.longitude}, ${site.latitude}]
              },
              "properties": {
                "$GEOJSON_PROPERTY_LAUNCH_SITE_ID": "${site.id.escapeJsonString()}",
                "$GEOJSON_PROPERTY_NAME": "${site.name.escapeJsonString()}"
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

internal fun findLaunchSiteForFeatures(
    features: List<Feature<*, JsonObject?>>,
    launchSites: List<ParaglidingLaunchSite>,
): ParaglidingLaunchSite? {
    return features.firstNotNullOfOrNull { feature ->
        val launchSiteId = feature.properties
            ?.get(GEOJSON_PROPERTY_LAUNCH_SITE_ID)
            ?.jsonPrimitive
            ?.contentOrNull

        launchSites.firstOrNull { site -> site.id == launchSiteId }
    }
}

internal sealed interface MapClickTarget {
    data class LaunchSite(val launchSite: ParaglidingLaunchSite) : MapClickTarget
    data class FavoritePlace(val place: SavedPlace) : MapClickTarget
    data class Coordinates(
        val latitude: Double,
        val longitude: Double,
    ) : MapClickTarget
}

internal data class LaunchSiteScreenOffset(
    val launchSite: ParaglidingLaunchSite,
    val screenOffset: DpOffset,
)

internal data class FavoritePlaceScreenOffset(
    val place: SavedPlace,
    val screenOffset: DpOffset,
)

internal fun resolveMapClickTarget(
    position: Position,
    clickOffset: DpOffset,
    launchSiteFeatures: List<Feature<*, JsonObject?>>,
    favoriteFeatures: List<Feature<*, JsonObject?>>,
    favoritePlaces: List<SavedPlace>,
    launchSites: List<ParaglidingLaunchSite>,
    favoritePlaceScreenOffsets: List<FavoritePlaceScreenOffset>,
    launchSiteScreenOffsets: List<LaunchSiteScreenOffset>,
): MapClickTarget {
    val favoritePlace = findFavoritePlaceForFeatures(
        features = favoriteFeatures,
        favoritePlaces = favoritePlaces,
    ) ?: findFavoritePlaceNearScreenOffset(
        clickOffset = clickOffset,
        favoritePlaceScreenOffsets = favoritePlaceScreenOffsets,
    )
    if (favoritePlace != null) {
        return MapClickTarget.FavoritePlace(favoritePlace)
    }

    val launchSite = findLaunchSiteForFeatures(
        features = launchSiteFeatures,
        launchSites = launchSites,
    ) ?: findLaunchSiteNearScreenOffset(
        clickOffset = clickOffset,
        launchSiteScreenOffsets = launchSiteScreenOffsets,
    )
    if (launchSite != null) {
        return MapClickTarget.LaunchSite(launchSite)
    }

    return MapClickTarget.Coordinates(
        latitude = position.latitude,
        longitude = position.longitude,
    )
}

internal fun findFavoritePlaceNearScreenOffset(
    clickOffset: DpOffset,
    favoritePlaceScreenOffsets: List<FavoritePlaceScreenOffset>,
    maxDistance: Dp = FAVORITE_HIT_SLOP,
): SavedPlace? {
    return favoritePlaceScreenOffsets
        .map { placeOffset ->
            placeOffset to placeOffset.screenOffset.distanceTo(clickOffset)
        }
        .filter { (_, distance) -> distance <= maxDistance }
        .minByOrNull { (_, distance) -> distance }
        ?.first
        ?.place
}

internal fun findLaunchSiteNearScreenOffset(
    clickOffset: DpOffset,
    launchSiteScreenOffsets: List<LaunchSiteScreenOffset>,
    maxDistance: Dp = LAUNCH_SITE_HIT_SLOP,
): ParaglidingLaunchSite? {
    return launchSiteScreenOffsets
        .map { siteOffset ->
            siteOffset to siteOffset.screenOffset.distanceTo(clickOffset)
        }
        .filter { (_, distance) -> distance <= maxDistance }
        .minByOrNull { (_, distance) -> distance }
        ?.first
        ?.launchSite
}

internal fun centeredTapTargetOffset(
    anchorOffset: DpOffset,
    targetSize: Dp,
): DpOffset {
    return DpOffset(
        x = anchorOffset.x - targetSize / 2,
        y = anchorOffset.y - targetSize / 2,
    )
}

internal fun launchSiteTapTargetOffset(
    anchorOffset: DpOffset,
    targetSize: Dp = LAUNCH_SITE_TOUCH_TARGET_SIZE,
    iconSize: Dp = LAUNCH_SITE_ICON_SIZE,
): DpOffset {
    return DpOffset(
        x = anchorOffset.x - targetSize / 2,
        y = anchorOffset.y - (targetSize + iconSize) / 2,
    )
}

private fun CameraProjection.favoritePlaceScreenOffsets(
    favoritePlaces: List<SavedPlace>,
): List<FavoritePlaceScreenOffset> {
    return favoritePlaces.mapNotNull { place ->
        val screenOffset = runCatching {
            screenLocationFromPosition(
                Position(longitude = place.longitude, latitude = place.latitude),
            )
        }.getOrNull() ?: return@mapNotNull null

        FavoritePlaceScreenOffset(
            place = place,
            screenOffset = screenOffset,
        )
    }
}

private fun CameraProjection.launchSiteScreenOffsets(
    launchSites: List<ParaglidingLaunchSite>,
): List<LaunchSiteScreenOffset> {
    return launchSites.mapNotNull { site ->
        val screenOffset = runCatching {
            screenLocationFromPosition(
                Position(longitude = site.longitude, latitude = site.latitude),
            )
        }.getOrNull() ?: return@mapNotNull null

        LaunchSiteScreenOffset(
            launchSite = site,
            screenOffset = screenOffset,
        )
    }
}

private fun DpOffset.distanceTo(other: DpOffset): Dp {
    return hypot(
        (x - other.x).value,
        (y - other.y).value,
    ).dp
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

private fun ParaglidingLaunchSite.windSummary(): String? {
    if (orientations.isEmpty()) return null
    val best = orientations.filter { it.rating >= 2 }.map { it.direction }
    val possible = orientations.filter { it.rating == 1 }.map { it.direction }
    return when {
        best.isNotEmpty() && possible.isNotEmpty() -> {
            "best ${best.joinToString(", ")}; possible ${possible.joinToString(", ")}"
        }
        best.isNotEmpty() -> best.joinToString(", ")
        possible.isNotEmpty() -> possible.joinToString(", ")
        else -> null
    }
}

private fun ParaglidingLaunchSite.cardDescription(): String? {
    val text = listOfNotNull(description, weather, flightRules).firstOrNull { it.isNotBlank() } ?: return null
    return text.shortenForCard(maxLength = 220)
}

private fun String.shortenForCard(maxLength: Int): String {
    if (length <= maxLength) return this
    return take(maxLength)
        .trimEnd()
        .trimEnd('.', ',', ';', ':')
        .plus("...")
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
private fun LaunchSiteCardPreview() {
    CloudbasePredictorTheme {
        LaunchSiteCard(
            launchSite = PreviewData.paraglidingLaunchSite,
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
            onLaunchSiteTapped = {},
            onOpenForecast = {},
            onFavoriteClick = {},
            onSaveCameraPosition = { _, _, _ -> },
            autoOpenFavoritesOnStartup = false,
        )
    }
}
