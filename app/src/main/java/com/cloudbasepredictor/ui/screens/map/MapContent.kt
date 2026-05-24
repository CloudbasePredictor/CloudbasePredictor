package com.cloudbasepredictor.ui.screens.map

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Star
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cloudbasepredictor.data.map.MapLayerPreference
import com.cloudbasepredictor.model.ParaglidingLaunchSite
import com.cloudbasepredictor.model.SavedPlace
import com.cloudbasepredictor.ui.components.MapFavoriteLabelsOverlay
import com.cloudbasepredictor.ui.map.MapRasterBaseLayer
import com.cloudbasepredictor.ui.map.mapBaseStyle
import com.cloudbasepredictor.ui.preview.PreviewData
import com.cloudbasepredictor.ui.theme.CloudbasePredictorTheme
import kotlinx.serialization.json.JsonObject
import org.maplibre.compose.camera.CameraPosition
import org.maplibre.compose.camera.CameraState
import org.maplibre.compose.camera.rememberCameraState
import org.maplibre.compose.expressions.dsl.const
import org.maplibre.compose.expressions.dsl.image
import org.maplibre.compose.expressions.value.SymbolAnchor
import org.maplibre.compose.layers.CircleLayer
import org.maplibre.compose.layers.SymbolLayer
import org.maplibre.compose.map.MapOptions
import org.maplibre.compose.map.MaplibreMap
import org.maplibre.compose.map.OrnamentOptions
import org.maplibre.compose.sources.GeoJsonData
import org.maplibre.compose.sources.rememberGeoJsonSource
import org.maplibre.compose.util.ClickResult
import org.maplibre.compose.util.MaplibreComposable
import org.maplibre.spatialk.geojson.Feature
import org.maplibre.spatialk.geojson.Position

@Composable
internal fun MapContent(
    mapLayer: MapLayerPreference,
    showLaunchSites: Boolean,
    cameraState: CameraState,
    markerLayerData: MapMarkerLayerData,
    onMapLoadFailed: (String?) -> Unit,
    onMapLoadFinished: () -> Unit,
    onMapTapped: (Double, Double) -> Unit,
    onFavoriteTapped: (SavedPlace) -> Unit,
    onLaunchSiteTapped: (ParaglidingLaunchSite) -> Unit,
    modifier: Modifier = Modifier,
    locationLayer: @Composable @MaplibreComposable () -> Unit = {},
) {
    val featureCollections = remember(showLaunchSites, markerLayerData) {
        buildMapFeatureCollections(
            showLaunchSites = showLaunchSites,
            markerLayerData = markerLayerData,
        )
    }
    val launchSitesForInteraction = if (showLaunchSites) {
        markerLayerData.launchSitesForInteraction
    } else {
        emptyList()
    }

    Box(modifier = modifier.fillMaxSize()) {
        MaplibreMap(
            modifier = Modifier.fillMaxSize(),
            baseStyle = mapBaseStyle(mapLayer),
            cameraState = cameraState,
            options = MapOptions(
                ornamentOptions = OrnamentOptions.AllDisabled,
            ),
            onMapLoadFailed = onMapLoadFailed,
            onMapLoadFinished = onMapLoadFinished,
            onMapClick = { position, offset ->
                val projection = cameraState.projection
                val launchSiteFeatures = projection
                    ?.queryRenderedFeatures(
                        offset = offset,
                        layerIds = LAUNCH_SITE_LAYER_IDS,
                    )
                    .orEmpty()
                val favoriteFeatures = projection
                    ?.queryRenderedFeatures(
                        offset = offset,
                        layerIds = FAVORITE_POINT_LAYER_IDS,
                    )
                    .orEmpty()
                val favoritePlaceScreenOffsets = projection
                    ?.favoritePlaceScreenOffsets(markerLayerData.favoritePlacesForInteraction)
                    .orEmpty()
                val launchSiteScreenOffsets = projection
                    ?.launchSiteScreenOffsets(launchSitesForInteraction)
                    .orEmpty()

                when (
                    val clickTarget = resolveMapClickTarget(
                        position = position,
                        clickOffset = offset,
                        launchSiteFeatures = launchSiteFeatures,
                        favoriteFeatures = favoriteFeatures,
                        favoritePlaces = markerLayerData.favoritePlacesForInteraction,
                        launchSites = launchSitesForInteraction,
                        favoritePlaceScreenOffsets = favoritePlaceScreenOffsets,
                        launchSiteScreenOffsets = launchSiteScreenOffsets,
                    )
                ) {
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
            MapRasterBaseLayer(mapLayer)
            MapMarkerLayers(
                featureCollections = featureCollections,
                launchSitesForInteraction = launchSitesForInteraction,
                onLaunchSiteTapped = onLaunchSiteTapped,
            )
            locationLayer()
        }

        MapLaunchSiteTapTargetsOverlay(
            launchSites = launchSitesForInteraction,
            cameraState = cameraState,
            onLaunchSiteTapped = onLaunchSiteTapped,
        )

        MapFavoriteTapTargetsOverlay(
            favoritePlaces = markerLayerData.favoritePlacesForInteraction,
            cameraState = cameraState,
            onFavoriteTapped = onFavoriteTapped,
        )

        MapFavoriteLabelsOverlay(
            favoritePlaces = markerLayerData.favoriteLabelPlaces,
            cameraState = cameraState,
            markerRadius = if (
                markerLayerData.selectedFavoritePlace == null &&
                markerLayerData.selectedFavoriteLaunchSite == null
            ) {
                FAVORITE_ICON_SIZE / 2
            } else {
                SELECTED_FAVORITE_ICON_SIZE / 2
            },
            fontSize = 10.sp,
        )
    }
}

@Composable
@MaplibreComposable
private fun MapMarkerLayers(
    featureCollections: MapFeatureCollections,
    launchSitesForInteraction: List<ParaglidingLaunchSite>,
    onLaunchSiteTapped: (ParaglidingLaunchSite) -> Unit,
) {
    val launchSitesSource = rememberGeoJsonSource(
        data = GeoJsonData.JsonString(featureCollections.launchSites),
    )
    val launchSiteLayerClick: (List<Feature<*, JsonObject?>>) -> ClickResult = { features ->
        val launchSite = findLaunchSiteForFeatures(
            features = features,
            launchSites = launchSitesForInteraction,
        )
        if (launchSite != null) {
            onLaunchSiteTapped(launchSite)
            ClickResult.Consume
        } else {
            ClickResult.Pass
        }
    }
    val launchSiteIconPainter = rememberVectorPainter(Icons.Filled.Flag)
    SymbolLayer(
        id = LAUNCH_SITES_LAYER_ID,
        source = launchSitesSource,
        iconImage = image(
            value = launchSiteIconPainter,
            size = DpSize(LAUNCH_SITE_ICON_SIZE, LAUNCH_SITE_ICON_SIZE),
            drawAsSdf = true,
        ),
        iconColor = const(LAUNCH_SITE_MARKER_COLOR),
        iconHaloColor = const(Color.White),
        iconHaloWidth = const(1.dp),
        iconAnchor = const(SymbolAnchor.Bottom),
        iconAllowOverlap = const(true),
        iconIgnorePlacement = const(true),
        onClick = launchSiteLayerClick,
    )

    val favoriteLaunchSitesSource = rememberGeoJsonSource(
        data = GeoJsonData.JsonString(featureCollections.favoriteLaunchSites),
    )
    SymbolLayer(
        id = FAVORITE_LAUNCH_SITES_LAYER_ID,
        source = favoriteLaunchSitesSource,
        iconImage = image(
            value = launchSiteIconPainter,
            size = DpSize(LAUNCH_SITE_ICON_SIZE, LAUNCH_SITE_ICON_SIZE),
            drawAsSdf = true,
        ),
        iconColor = const(FAVORITE_MARKER_COLOR),
        iconHaloColor = const(Color.White),
        iconHaloWidth = const(1.5.dp),
        iconAnchor = const(SymbolAnchor.Bottom),
        iconAllowOverlap = const(true),
        iconIgnorePlacement = const(true),
        onClick = launchSiteLayerClick,
    )

    val favoritesSource = rememberGeoJsonSource(
        data = GeoJsonData.JsonString(featureCollections.favorites),
    )
    val favoriteIconPainter = rememberVectorPainter(Icons.Filled.Star)
    SymbolLayer(
        id = FAVORITE_POINTS_LAYER_ID,
        source = favoritesSource,
        iconImage = image(
            value = favoriteIconPainter,
            size = DpSize(FAVORITE_ICON_SIZE, FAVORITE_ICON_SIZE),
            drawAsSdf = true,
        ),
        iconColor = const(FAVORITE_MARKER_COLOR),
        iconHaloColor = const(Color.White),
        iconHaloWidth = const(1.5.dp),
        iconAnchor = const(SymbolAnchor.Center),
        iconAllowOverlap = const(true),
        iconIgnorePlacement = const(true),
    )

    val selectedLaunchSiteSource = rememberGeoJsonSource(
        data = GeoJsonData.JsonString(featureCollections.selectedLaunchSite),
    )
    SymbolLayer(
        id = SELECTED_LAUNCH_SITE_LAYER_ID,
        source = selectedLaunchSiteSource,
        iconImage = image(
            value = launchSiteIconPainter,
            size = DpSize(SELECTED_LAUNCH_SITE_ICON_SIZE, SELECTED_LAUNCH_SITE_ICON_SIZE),
            drawAsSdf = true,
        ),
        iconColor = const(SELECTED_MARKER_COLOR),
        iconHaloColor = const(Color.White),
        iconHaloWidth = const(2.dp),
        iconAnchor = const(SymbolAnchor.Bottom),
        iconAllowOverlap = const(true),
        iconIgnorePlacement = const(true),
        onClick = launchSiteLayerClick,
    )

    val selectedFavoriteLaunchSiteSource = rememberGeoJsonSource(
        data = GeoJsonData.JsonString(featureCollections.selectedFavoriteLaunchSite),
    )
    SymbolLayer(
        id = SELECTED_FAVORITE_LAUNCH_SITE_LAYER_ID,
        source = selectedFavoriteLaunchSiteSource,
        iconImage = image(
            value = launchSiteIconPainter,
            size = DpSize(SELECTED_LAUNCH_SITE_ICON_SIZE, SELECTED_LAUNCH_SITE_ICON_SIZE),
            drawAsSdf = true,
        ),
        iconColor = const(FAVORITE_MARKER_COLOR),
        iconHaloColor = const(Color.White),
        iconHaloWidth = const(2.dp),
        iconAnchor = const(SymbolAnchor.Bottom),
        iconAllowOverlap = const(true),
        iconIgnorePlacement = const(true),
        onClick = launchSiteLayerClick,
    )

    val selectedFavoriteSource = rememberGeoJsonSource(
        data = GeoJsonData.JsonString(featureCollections.selectedFavorite),
    )
    SymbolLayer(
        id = SELECTED_FAVORITE_POINT_LAYER_ID,
        source = selectedFavoriteSource,
        iconImage = image(
            value = favoriteIconPainter,
            size = DpSize(SELECTED_FAVORITE_ICON_SIZE, SELECTED_FAVORITE_ICON_SIZE),
            drawAsSdf = true,
        ),
        iconColor = const(SELECTED_MARKER_COLOR),
        iconHaloColor = const(Color.White),
        iconHaloWidth = const(2.dp),
        iconAnchor = const(SymbolAnchor.Center),
        iconAllowOverlap = const(true),
        iconIgnorePlacement = const(true),
    )

    val markerSource = rememberGeoJsonSource(
        data = GeoJsonData.JsonString(featureCollections.selectedCoordinate),
    )
    CircleLayer(
        id = SELECTED_POINT_LAYER_ID,
        source = markerSource,
        color = const(SELECTED_MARKER_COLOR),
        radius = const(9.dp),
        strokeColor = const(Color.White),
        strokeWidth = const(3.dp),
    )
}

private data class MapFeatureCollections(
    val selectedCoordinate: String,
    val favorites: String,
    val selectedFavorite: String,
    val launchSites: String,
    val favoriteLaunchSites: String,
    val selectedLaunchSite: String,
    val selectedFavoriteLaunchSite: String,
)

private fun buildMapFeatureCollections(
    showLaunchSites: Boolean,
    markerLayerData: MapMarkerLayerData,
): MapFeatureCollections {
    return MapFeatureCollections(
        selectedCoordinate = markerLayerData.selectedCoordinatePlace
            ?.let(::buildMarkerFeatureCollection)
            ?: emptyFeatureCollection(),
        favorites = buildFavoritesFeatureCollection(markerLayerData.unselectedFavoritePlaces),
        selectedFavorite = markerLayerData.selectedFavoritePlace
            ?.let { buildFavoritesFeatureCollection(listOf(it)) }
            ?: emptyFeatureCollection(),
        launchSites = if (showLaunchSites) {
            buildLaunchSitesFeatureCollection(markerLayerData.unselectedLaunchSites)
        } else {
            emptyFeatureCollection()
        },
        favoriteLaunchSites = if (showLaunchSites) {
            buildLaunchSitesFeatureCollection(
                markerLayerData.unselectedFavoriteLaunchSites.map { it.launchSite },
            )
        } else {
            emptyFeatureCollection()
        },
        selectedLaunchSite = if (showLaunchSites && markerLayerData.selectedLaunchSite != null) {
            buildLaunchSitesFeatureCollection(listOf(markerLayerData.selectedLaunchSite))
        } else {
            emptyFeatureCollection()
        },
        selectedFavoriteLaunchSite = if (
            showLaunchSites &&
            markerLayerData.selectedFavoriteLaunchSite != null
        ) {
            buildLaunchSitesFeatureCollection(listOf(markerLayerData.selectedFavoriteLaunchSite.launchSite))
        } else {
            emptyFeatureCollection()
        },
    )
}

private const val FAVORITE_POINTS_LAYER_ID = "favorite-points"
private const val SELECTED_FAVORITE_POINT_LAYER_ID = "selected-favorite-point"
private const val LAUNCH_SITES_LAYER_ID = "paragliding-launch-sites"
private const val SELECTED_LAUNCH_SITE_LAYER_ID = "selected-paragliding-launch-site"
private const val FAVORITE_LAUNCH_SITES_LAYER_ID = "favorite-paragliding-launch-sites"
private const val SELECTED_FAVORITE_LAUNCH_SITE_LAYER_ID = "selected-favorite-paragliding-launch-site"
private const val SELECTED_POINT_LAYER_ID = "selected-point"
private val LAUNCH_SITE_LAYER_IDS = setOf(
    LAUNCH_SITES_LAYER_ID,
    SELECTED_LAUNCH_SITE_LAYER_ID,
    FAVORITE_LAUNCH_SITES_LAYER_ID,
    SELECTED_FAVORITE_LAUNCH_SITE_LAYER_ID,
)
private val FAVORITE_POINT_LAYER_IDS = setOf(FAVORITE_POINTS_LAYER_ID, SELECTED_FAVORITE_POINT_LAYER_ID)
private val FAVORITE_MARKER_COLOR = Color(0xFFFFC107)
private val SELECTED_MARKER_COLOR = Color(0xFFE64A5B)
private val LAUNCH_SITE_MARKER_COLOR = Color(0xFF00796B)
private val SELECTED_LAUNCH_SITE_ICON_SIZE = 30.dp
private val FAVORITE_ICON_SIZE = 20.dp
private val SELECTED_FAVORITE_ICON_SIZE = 30.dp

@Preview(showBackground = true)
@Composable
private fun MapContentPreview() {
    CloudbasePredictorTheme {
        val cameraState = rememberCameraState(
            firstPosition = CameraPosition(
                target = Position(longitude = 5.88806, latitude = 45.3069),
                zoom = 12.0,
            ),
        )
        MapContent(
            mapLayer = MapLayerPreference.OPENFREEMAP,
            showLaunchSites = true,
            cameraState = cameraState,
            markerLayerData = buildMapMarkerLayerData(
                PreviewData.mapUiState.copy(
                    selectedLaunchSite = PreviewData.paraglidingLaunchSite,
                    launchSites = listOf(PreviewData.paraglidingLaunchSite),
                ),
            ),
            onMapLoadFailed = {},
            onMapLoadFinished = {},
            onMapTapped = { _, _ -> },
            onFavoriteTapped = {},
            onLaunchSiteTapped = {},
            modifier = Modifier.size(width = 320.dp, height = 420.dp),
        )
    }
}
