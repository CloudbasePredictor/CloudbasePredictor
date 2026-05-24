package com.cloudbasepredictor.ui.screens.map

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.cloudbasepredictor.R
import com.cloudbasepredictor.model.ParaglidingLaunchSite
import com.cloudbasepredictor.model.SavedPlace
import com.cloudbasepredictor.ui.components.MapTestTags
import com.cloudbasepredictor.ui.preview.PreviewData
import com.cloudbasepredictor.ui.theme.CloudbasePredictorTheme
import org.maplibre.compose.camera.CameraState

@Composable
internal fun MapFavoriteTapTargetsOverlay(
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
internal fun MapFavoriteTapTargetsOverlayContent(
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
                val contentDescription = stringResource(R.string.cd_favorite_marker, place.name)

                key(place.id) {
                    val interactionSource = remember { MutableInteractionSource() }

                    Box(
                        modifier = Modifier
                            .size(FAVORITE_TOUCH_TARGET_SIZE)
                            .offset(x = topLeft.x, y = topLeft.y)
                            .align(Alignment.TopStart)
                            .clickable(
                                interactionSource = interactionSource,
                                indication = null,
                                onClick = { onFavoriteTapped(place) },
                            )
                            .semantics {
                                this.contentDescription = contentDescription
                            }
                            .testTag(MapTestTags.FAVORITE_TAP_TARGET_PREFIX + place.id),
                    )
                }
            }
    }
}

@Composable
internal fun MapLaunchSiteTapTargetsOverlay(
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
internal fun MapLaunchSiteTapTargetsOverlayContent(
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
                val contentDescription = stringResource(R.string.cd_launch_site_marker, site.name)

                key(site.id) {
                    val interactionSource = remember { MutableInteractionSource() }

                    Box(
                        modifier = Modifier
                            .size(LAUNCH_SITE_TOUCH_TARGET_SIZE)
                            .offset(x = topLeft.x, y = topLeft.y)
                            .align(Alignment.TopStart)
                            .clickable(
                                interactionSource = interactionSource,
                                indication = null,
                                onClick = { onLaunchSiteTapped(site) },
                            )
                            .semantics {
                                this.contentDescription = contentDescription
                            }
                            .testTag(MapTestTags.LAUNCH_SITE_TAP_TARGET_PREFIX + site.id),
                    )
                }
            }
    }
}

@Preview(showBackground = true)
@Composable
private fun MapTapTargetsOverlayPreview() {
    CloudbasePredictorTheme {
        Box(
            modifier = Modifier
                .size(220.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
        ) {
            MapLaunchSiteTapTargetsOverlayContent(
                launchSiteOffsets = listOf(
                    LaunchSiteScreenOffset(
                        launchSite = PreviewData.paraglidingLaunchSite,
                        screenOffset = DpOffset(x = 112.dp, y = 96.dp),
                    ),
                ),
                mapSize = IntSize(width = 10_000, height = 10_000),
                onLaunchSiteTapped = {},
                modifier = Modifier.fillMaxSize(),
            )
            MapFavoriteTapTargetsOverlayContent(
                favoritePlaceOffsets = listOf(
                    FavoritePlaceScreenOffset(
                        place = PreviewData.savedPlace,
                        screenOffset = DpOffset(x = 80.dp, y = 132.dp),
                    ),
                ),
                mapSize = IntSize(width = 10_000, height = 10_000),
                onFavoriteTapped = {},
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
