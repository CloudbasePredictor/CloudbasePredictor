package com.cloudbasepredictor.ui.screens.map

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.tappableElement
import androidx.compose.foundation.layout.windowInsetsPadding
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
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
import com.cloudbasepredictor.ui.components.MapTestTags
import com.cloudbasepredictor.ui.map.mapLayerAttributionDetailRes
import com.cloudbasepredictor.ui.map.mapLayerAttributionRes
import com.cloudbasepredictor.ui.preview.PreviewData
import com.cloudbasepredictor.ui.theme.CloudbasePredictorTheme
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import org.maplibre.compose.camera.CameraPosition
import org.maplibre.compose.camera.rememberCameraState
import org.maplibre.compose.location.DesiredAccuracy
import org.maplibre.compose.location.LocationPuck
import org.maplibre.compose.location.rememberDefaultLocationProvider
import org.maplibre.compose.location.rememberNullLocationProvider
import org.maplibre.compose.location.rememberUserLocationState
import org.maplibre.spatialk.geojson.Position
import kotlin.time.Duration.Companion.seconds

private const val USER_LOCATION_LAYER_ID_PREFIX = "user-location"
private const val DEVICE_LOCATION_MIN_ZOOM = 12.0
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
        onDismissSelection = viewModel::clearSelection,
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
    onDismissSelection: () -> Unit = {},
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

    val markerLayerData = buildMapMarkerLayerData(uiState)

    var showFavoritesDialog by rememberSaveable { mutableStateOf(false) }
    var showManualFavoriteDialog by rememberSaveable { mutableStateOf(false) }
    var didAutoOpenFavoritesDialog by rememberSaveable { mutableStateOf(false) }
    val baseMapAttributionText = stringResource(mapLayerAttributionRes(uiState.mapLayer))
    val mapAttributionText = if (uiState.showLaunchSites) {
        val launchSiteAttributionText = stringResource(R.string.map_attribution_paraglidingearth_compact)
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
    val mapAttributionDetailText = if (uiState.showLaunchSites) {
        val launchSiteAttributionDetailText = stringResource(R.string.map_attribution_paraglidingearth_full)
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
                MapContent(
                    mapLayer = uiState.mapLayer,
                    showLaunchSites = uiState.showLaunchSites,
                    cameraState = cameraState,
                    markerLayerData = markerLayerData,
                    onMapLoadFailed = { reason ->
                        mapLoadError = reason?.takeIf { it.isNotBlank() } ?: unavailableMessage
                    },
                    onMapLoadFinished = {
                        mapLoadError = null
                        requestLaunchSitesForVisibleBounds()
                    },
                    onMapTapped = onMapTapped,
                    onFavoriteTapped = onFavoriteTapped,
                    onLaunchSiteTapped = onLaunchSiteTapped,
                    locationLayer = {
                        if (hasLocationPermission && userLocationState.location != null) {
                            LocationPuck(
                                idPrefix = USER_LOCATION_LAYER_ID_PREFIX,
                                locationState = userLocationState,
                                cameraState = cameraState,
                            )
                        }
                    },
                )
            }
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

        MapChrome(
            mapLayer = uiState.mapLayer,
            bearing = cameraState.position.bearing,
            onFavoritesClick = { showFavoritesDialog = true },
            onSettingsClick = onOpenSettings,
            onCurrentLocationClick = {
                val permissionGranted = context.hasAnyLocationPermission()
                hasLocationPermission = permissionGranted

                if (!permissionGranted) {
                    locationPermissionLauncher.launch(LOCATION_PERMISSIONS)
                } else {
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
                }
            },
            onResetNorthClick = {
                scope.launch {
                    cameraState.animateTo(cameraState.position.copy(bearing = 0.0))
                }
            },
            onMapLayerSelected = onMapLayerSelected,
        )

        MapSelectionCards(
            selectedPlace = uiState.selectedPlace,
            selectedLaunchSite = uiState.selectedLaunchSite,
            selectedFavoriteLaunchSite = markerLayerData.selectedFavoriteLaunchSite,
            onOpenForecast = onOpenForecast,
            onDismissSelection = onDismissSelection,
            modifier = Modifier.align(Alignment.BottomCenter),
        )

        MapAttributionOverlay(
            text = mapAttributionText,
            detailText = mapAttributionDetailText,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .windowInsetsPadding(WindowInsets.tappableElement)
                .padding(start = 8.dp, end = 8.dp),
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
