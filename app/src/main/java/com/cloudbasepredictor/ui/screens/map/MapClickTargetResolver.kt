package com.cloudbasepredictor.ui.screens.map

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.cloudbasepredictor.model.ParaglidingLaunchSite
import com.cloudbasepredictor.model.SavedPlace
import kotlin.math.hypot
import kotlinx.serialization.json.JsonObject
import org.maplibre.compose.camera.CameraProjection
import org.maplibre.spatialk.geojson.Feature
import org.maplibre.spatialk.geojson.Position

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

internal fun CameraProjection.favoritePlaceScreenOffsets(
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

internal fun CameraProjection.launchSiteScreenOffsets(
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

internal val LAUNCH_SITE_ICON_SIZE = 22.dp
internal val LAUNCH_SITE_TOUCH_TARGET_SIZE = 56.dp
internal val FAVORITE_TOUCH_TARGET_SIZE = 48.dp

private val LAUNCH_SITE_HIT_SLOP = 40.dp
private val FAVORITE_HIT_SLOP = 24.dp
