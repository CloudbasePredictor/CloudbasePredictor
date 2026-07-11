package com.cloudbasepredictor.ui.screens.map

import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.cloudbasepredictor.data.map.MapLayerPreference
import com.cloudbasepredictor.model.ParaglidingLaunchSite
import com.cloudbasepredictor.model.SavedPlace
import com.cloudbasepredictor.ui.components.MapTestTags
import com.cloudbasepredictor.ui.preview.PreviewData
import com.cloudbasepredictor.ui.theme.CloudbasePredictorTheme
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.maplibre.android.maps.MapView
import org.maplibre.compose.camera.CameraPosition
import org.maplibre.compose.camera.rememberCameraState
import org.maplibre.spatialk.geojson.Position
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.abs

class MapScreenTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun mapScreen_showsFavoritesButtonWhenNoFavoritesExist() {
        composeRule.setContent {
            CloudbasePredictorTheme {
                MapScreen(
                    uiState = MapUiState(),
                    onMapTapped = { _, _ -> },
                    onFavoriteTapped = {},
                    onLaunchSiteTapped = {},
                    onOpenForecast = {},
                    onFavoriteClick = {},
                    onSaveCameraPosition = { _, _, _ -> },
                    autoOpenFavoritesOnStartup = true,
                )
            }
        }

        composeRule.onNodeWithTag(MapTestTags.FAVORITES_BUTTON).assertIsDisplayed()
    }

    @Test
    fun mapScreen_showsCurrentLocationButton() {
        composeRule.setContent {
            CloudbasePredictorTheme {
                MapScreen(
                    uiState = MapUiState(),
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

        composeRule.onNodeWithTag(MapTestTags.CURRENT_LOCATION_BUTTON).assertIsDisplayed()
    }

    @Test
    fun mapScreen_autoOpensFavoritesDialogWhenAtLeastTwoFavoritesExist() {
        composeRule.setContent {
            CloudbasePredictorTheme {
                MapScreen(
                    uiState = MapUiState(favoritePlaces = favoritePlaces),
                    onMapTapped = { _, _ -> },
                    onFavoriteTapped = {},
                    onLaunchSiteTapped = {},
                    onOpenForecast = {},
                    onFavoriteClick = {},
                    onSaveCameraPosition = { _, _, _ -> },
                    autoOpenFavoritesOnStartup = true,
                )
            }
        }

        composeRule.onNodeWithTag(MapTestTags.FAVORITES_DIALOG).assertIsDisplayed()
    }

    @Test
    fun mapScreen_selectedPlaceCloseButtonDismissesSelection() {
        var dismissed = false

        composeRule.setContent {
            CloudbasePredictorTheme {
                MapScreen(
                    uiState = MapUiState(selectedPlace = favoritePlaces.first()),
                    onMapTapped = { _, _ -> },
                    onFavoriteTapped = {},
                    onLaunchSiteTapped = {},
                    onOpenForecast = {},
                    onDismissSelection = { dismissed = true },
                    onFavoriteClick = {},
                    onSaveCameraPosition = { _, _, _ -> },
                    autoOpenFavoritesOnStartup = false,
                )
            }
        }

        composeRule.onNodeWithTag(MapTestTags.SELECTION_CARD_CLOSE_BUTTON)
            .assertIsDisplayed()
            .performClick()

        composeRule.runOnIdle {
            assertTrue(dismissed)
        }
    }

    @Test
    fun mapScreen_selectedPlaceDismissIconDismissesSelection() {
        var dismissed = false

        composeRule.setContent {
            CloudbasePredictorTheme {
                MapScreen(
                    uiState = MapUiState(selectedPlace = favoritePlaces.first()),
                    onMapTapped = { _, _ -> },
                    onFavoriteTapped = {},
                    onLaunchSiteTapped = {},
                    onOpenForecast = {},
                    onDismissSelection = { dismissed = true },
                    onFavoriteClick = {},
                    onSaveCameraPosition = { _, _, _ -> },
                    autoOpenFavoritesOnStartup = false,
                )
            }
        }

        composeRule.onNodeWithTag(MapTestTags.SELECTION_CARD_DISMISS_ICON)
            .assertIsDisplayed()
            .performClick()

        composeRule.runOnIdle {
            assertTrue(dismissed)
        }
    }

    @Test
    fun mapContent_clickOnLaunchSiteMarkerShowsLaunchSiteCard() {
        val mapLoaded = AtomicBoolean(false)
        val selectedLaunchSite = AtomicReference<ParaglidingLaunchSite?>()
        val mapTapped = AtomicBoolean(false)
        val launchSites = mutableStateOf(emptyList<ParaglidingLaunchSite>())

        composeRule.setContent {
            LaunchSiteMapInteractionTestContent(
                launchSites = launchSites.value,
                mapLoaded = mapLoaded,
                selectedLaunchSite = selectedLaunchSite,
                mapTapped = mapTapped,
            )
        }

        composeRule.waitForInteractiveMap(mapLoaded)
        composeRule.runOnIdle {
            launchSites.value = listOf(launchSite)
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(MapTestTags.MAP_CONTENT)
            .performTouchInput {
                click(center)
            }

        composeRule.waitUntil(timeoutMillis = MAP_INTERACTION_TIMEOUT_MILLIS) {
            selectedLaunchSite.get()?.id == launchSite.id
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText(launchSite.name).assertIsDisplayed()
        composeRule.runOnIdle {
            assertFalse("Launch-site clicks must not select a coordinate marker", mapTapped.get())
        }
    }

    @Test
    fun mapContent_dragStartingOnLaunchSiteMarkerPansMap() {
        val mapLoaded = AtomicBoolean(false)
        val selectedLaunchSite = AtomicReference<ParaglidingLaunchSite?>()
        val mapTapped = AtomicBoolean(false)
        val cameraPosition = AtomicReference<CameraPosition>()
        val launchSites = mutableStateOf(emptyList<ParaglidingLaunchSite>())

        composeRule.setContent {
            LaunchSiteMapInteractionTestContent(
                launchSites = launchSites.value,
                mapLoaded = mapLoaded,
                selectedLaunchSite = selectedLaunchSite,
                mapTapped = mapTapped,
                cameraPosition = cameraPosition,
            )
        }

        composeRule.waitForInteractiveMap(mapLoaded, cameraPosition)
        composeRule.runOnIdle {
            launchSites.value = listOf(launchSite)
        }
        composeRule.waitForIdle()
        val beforeDrag = requireNotNull(cameraPosition.get())

        performShellSwipe(mapSwipeRightFromLaunchSiteIconCoordinates())

        composeRule.waitUntil(timeoutMillis = MAP_INTERACTION_TIMEOUT_MILLIS) {
            val afterDrag = cameraPosition.get() ?: return@waitUntil false
            cameraTargetChanged(beforeDrag, afterDrag)
        }

        composeRule.runOnIdle {
            val afterDrag = requireNotNull(cameraPosition.get())
            assertTrue(
                "Dragging from a launch-site icon should pan the map",
                cameraTargetChanged(beforeDrag, afterDrag),
            )
            assertNull(
                "Dragging from a launch-site icon should not open the launch-site card",
                selectedLaunchSite.get(),
            )
            assertFalse(
                "Dragging from a launch-site icon should not select a coordinate marker",
                mapTapped.get(),
            )
        }
    }

    @Test
    fun mapContent_clickOnFavoriteMarkerSelectsFavorite() {
        val favorite = favoritePlaces.first()
        val mapLoaded = AtomicBoolean(false)
        val selectedFavorite = AtomicReference<SavedPlace?>()
        val mapTapped = AtomicBoolean(false)

        composeRule.setContent {
            FavoriteMapInteractionTestContent(
                favorite = favorite,
                mapLoaded = mapLoaded,
                selectedFavorite = selectedFavorite,
                mapTapped = mapTapped,
            )
        }

        composeRule.waitForInteractiveMap(mapLoaded)

        composeRule.onNodeWithTag(MapTestTags.MAP_CONTENT)
            .performTouchInput {
                click(center)
            }

        composeRule.waitUntil(timeoutMillis = MAP_INTERACTION_TIMEOUT_MILLIS) {
            selectedFavorite.get()?.id == favorite.id
        }
        composeRule.waitForIdle()

        composeRule.runOnIdle {
            assertFalse(
                "Favorite marker clicks must not select a coordinate marker",
                mapTapped.get(),
            )
        }
    }

    @Test
    fun mapContent_dragStartingOnFavoriteMarkerPansMap() {
        val favorite = favoritePlaces.first()
        val mapLoaded = AtomicBoolean(false)
        val selectedFavorite = AtomicReference<SavedPlace?>()
        val mapTapped = AtomicBoolean(false)
        val cameraPosition = AtomicReference<CameraPosition>()

        composeRule.setContent {
            FavoriteMapInteractionTestContent(
                favorite = favorite,
                mapLoaded = mapLoaded,
                selectedFavorite = selectedFavorite,
                mapTapped = mapTapped,
                cameraPosition = cameraPosition,
            )
        }

        composeRule.waitForInteractiveMap(mapLoaded, cameraPosition)
        val beforeDrag = requireNotNull(cameraPosition.get())

        performShellSwipe(mapSwipeRightFromCenterCoordinates())

        composeRule.waitUntil(timeoutMillis = MAP_INTERACTION_TIMEOUT_MILLIS) {
            val afterDrag = cameraPosition.get() ?: return@waitUntil false
            cameraTargetChanged(beforeDrag, afterDrag)
        }

        composeRule.runOnIdle {
            val afterDrag = requireNotNull(cameraPosition.get())
            assertTrue(
                "Dragging from a favorite marker should pan the map",
                cameraTargetChanged(beforeDrag, afterDrag),
            )
            assertNull(
                "Dragging from a favorite marker should not select the favorite",
                selectedFavorite.get(),
            )
            assertFalse(
                "Dragging from a favorite marker should not select a coordinate marker",
                mapTapped.get(),
            )
        }
    }

    @Composable
    private fun LaunchSiteMapInteractionTestContent(
        launchSites: List<ParaglidingLaunchSite>,
        mapLoaded: AtomicBoolean,
        selectedLaunchSite: AtomicReference<ParaglidingLaunchSite?>,
        mapTapped: AtomicBoolean,
        cameraPosition: AtomicReference<CameraPosition> = AtomicReference(),
    ) {
        CloudbasePredictorTheme {
            val cameraState = rememberCameraState(firstPosition = launchSiteCameraPosition())
            val selectedLaunchSiteState = remember { mutableStateOf<ParaglidingLaunchSite?>(null) }

            LaunchedEffect(cameraState) {
                snapshotFlow { cameraState.position }.collect { position ->
                    cameraPosition.set(position)
                }
            }

            Box(
                modifier = Modifier.size(width = 320.dp, height = 420.dp),
            ) {
                MapContent(
                    mapLayer = MapLayerPreference.OPENTOPOMAP,
                    showLaunchSites = true,
                    cameraState = cameraState,
                    markerLayerData = buildMapMarkerLayerData(
                        MapUiState(launchSites = launchSites),
                    ),
                    onMapLoadFailed = {},
                    onMapLoadFinished = { mapLoaded.set(true) },
                    onMapTapped = { _, _ -> mapTapped.set(true) },
                    onFavoriteTapped = {},
                    onLaunchSiteTapped = { site ->
                        selectedLaunchSite.set(site)
                        selectedLaunchSiteState.value = site
                    },
                )

                MapSelectionCards(
                    selectedPlace = null,
                    selectedLaunchSite = selectedLaunchSiteState.value,
                    selectedFavoriteLaunchSite = null,
                    onOpenForecast = {},
                    onDismissSelection = {
                        selectedLaunchSite.set(null)
                        selectedLaunchSiteState.value = null
                    },
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }
        }
    }

    @Composable
    private fun FavoriteMapInteractionTestContent(
        favorite: SavedPlace,
        mapLoaded: AtomicBoolean,
        selectedFavorite: AtomicReference<SavedPlace?>,
        mapTapped: AtomicBoolean,
        cameraPosition: AtomicReference<CameraPosition> = AtomicReference(),
    ) {
        CloudbasePredictorTheme {
            val cameraState = rememberCameraState(firstPosition = favoriteCameraPosition(favorite))

            LaunchedEffect(cameraState) {
                snapshotFlow { cameraState.position }.collect { position ->
                    cameraPosition.set(position)
                }
            }

            Box(
                modifier = Modifier.size(width = 320.dp, height = 420.dp),
            ) {
                MapContent(
                    mapLayer = MapLayerPreference.OPENTOPOMAP,
                    showLaunchSites = true,
                    cameraState = cameraState,
                    markerLayerData = buildMapMarkerLayerData(
                        MapUiState(favoritePlaces = listOf(favorite)),
                    ),
                    onMapLoadFailed = {},
                    onMapLoadFinished = { mapLoaded.set(true) },
                    onMapTapped = { _, _ -> mapTapped.set(true) },
                    onFavoriteTapped = { selectedFavorite.set(it) },
                    onLaunchSiteTapped = {},
                )
            }
        }
    }

    private fun mapSwipeRightFromLaunchSiteIconCoordinates(): ShellSwipeCoordinates {
        return mapSwipeRightCoordinates { mapView ->
            val launchSiteIconCenterYOffsetPx =
                (LAUNCH_SITE_ICON_SIZE.value * mapView.resources.displayMetrics.density / 2f).toInt()
            mapView.height / 2 - launchSiteIconCenterYOffsetPx
        }
    }

    private fun mapSwipeRightFromCenterCoordinates(): ShellSwipeCoordinates {
        return mapSwipeRightCoordinates { mapView ->
            mapView.height / 2
        }
    }

    private fun mapSwipeRightCoordinates(
        startYInMapView: (MapView) -> Int,
    ): ShellSwipeCoordinates {
        val coordinates = AtomicReference<ShellSwipeCoordinates>()

        composeRule.runOnIdle {
            val contentView = composeRule.activity.findViewById<View>(android.R.id.content)
            val mapView = requireNotNull(contentView.findMapView()) {
                "MapView should be present before swiping"
            }
            val location = IntArray(2)
            mapView.getLocationOnScreen(location)
            val startY = location[1] + startYInMapView(mapView)
            coordinates.set(
                ShellSwipeCoordinates(
                    startX = location[0] + mapView.width / 2,
                    startY = startY,
                    endX = location[0] + mapView.width - 8,
                    endY = startY,
                ),
            )
        }

        return requireNotNull(coordinates.get())
    }

    private fun performShellSwipe(coordinates: ShellSwipeCoordinates) {
        InstrumentationRegistry.getInstrumentation()
            .uiAutomation
            .executeShellCommand(
                "input swipe ${coordinates.startX} ${coordinates.startY} " +
                    "${coordinates.endX} ${coordinates.endY} 500",
            )
            .close()
    }

    private data class ShellSwipeCoordinates(
        val startX: Int,
        val startY: Int,
        val endX: Int,
        val endY: Int,
    )

    private companion object {
        private const val MAP_INTERACTION_TIMEOUT_MILLIS = 15_000L
        private const val CAMERA_MOVE_EPSILON_DEGREES = 0.00001

        val favoritePlaces = listOf(
            SavedPlace(
                id = "favorite-interlaken",
                name = "Interlaken",
                latitude = 46.5582,
                longitude = 7.8354,
                isFavorite = true,
            ),
            SavedPlace(
                id = "favorite-zurich",
                name = "Zurich",
                latitude = 47.3769,
                longitude = 8.5417,
                isFavorite = true,
            ),
        )

        val launchSite = ParaglidingLaunchSite(
            id = "launch-saint-hilaire",
            name = "Saint Hilaire du Touvet",
            latitude = 45.3069,
            longitude = 5.88806,
        )

        fun launchSiteCameraPosition(): CameraPosition {
            return CameraPosition(
                target = Position(
                    longitude = launchSite.longitude,
                    latitude = launchSite.latitude,
                ),
                zoom = 13.0,
            )
        }

        fun favoriteCameraPosition(favorite: SavedPlace): CameraPosition {
            return CameraPosition(
                target = Position(
                    longitude = favorite.longitude,
                    latitude = favorite.latitude,
                ),
                zoom = 13.0,
            )
        }

        fun ComposeContentTestRule.waitForInteractiveMap(
            mapLoaded: AtomicBoolean,
            cameraPosition: AtomicReference<CameraPosition>? = null,
        ) {
            waitUntil(timeoutMillis = MAP_INTERACTION_TIMEOUT_MILLIS) {
                mapLoaded.get() && (cameraPosition == null || cameraPosition.get() != null)
            }
            waitForIdle()
            assertTrue("Map should load before interaction", mapLoaded.get())
            if (cameraPosition != null) {
                assertNotNull(
                    "Camera position should be observed before interaction",
                    cameraPosition.get(),
                )
            }
        }

        fun cameraTargetChanged(
            before: CameraPosition,
            after: CameraPosition,
        ): Boolean {
            return abs(before.target.latitude - after.target.latitude) > CAMERA_MOVE_EPSILON_DEGREES ||
                abs(before.target.longitude - after.target.longitude) > CAMERA_MOVE_EPSILON_DEGREES
        }
    }
}

private fun View.findMapView(): MapView? {
    if (this is MapView) return this
    if (this !is ViewGroup) return null

    for (index in 0 until childCount) {
        val childMapView = getChildAt(index).findMapView()
        if (childMapView != null) return childMapView
    }

    return null
}

@Preview(showBackground = true)
@Composable
private fun MapScreenTestPreview() {
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
