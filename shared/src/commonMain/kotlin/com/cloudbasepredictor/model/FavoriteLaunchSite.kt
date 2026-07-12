package com.cloudbasepredictor.model

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sqrt

/** A favorite ([SavedPlace.isFavorite]) that sits on top of a launch site (within the colocation threshold). */
data class FavoriteLaunchSite(
    val favorite: SavedPlace,
    val launchSite: ParaglidingLaunchSite,
)

/** Favorites and launch sites closer than this are treated as the same point and merged into one marker. */
const val COLOCATED_MARKER_THRESHOLD_METERS: Double = 30.0

/**
 * Greedily pairs each favorite with the nearest launch site within [thresholdMeters], using each
 * favorite and each launch site at most once (closest candidate wins; ties broken by favorite id then
 * launch-site id). This mirrors the Android map so a favorite saved on a launch renders as a single
 * merged marker instead of two overlapping ones. Only [SavedPlace.isFavorite] places are eligible.
 */
fun mergeColocatedFavoriteLaunchSites(
    favorites: List<SavedPlace>,
    launchSites: List<ParaglidingLaunchSite>,
    thresholdMeters: Double = COLOCATED_MARKER_THRESHOLD_METERS,
): List<FavoriteLaunchSite> {
    val candidates = favorites
        .filter { place -> place.isFavorite }
        .flatMap { favorite ->
            launchSites.mapNotNull { launchSite ->
                colocationCandidate(favorite, launchSite, thresholdMeters)
            }
        }
        .sortedWith(colocationComparator())

    val usedFavoriteIds = mutableSetOf<String>()
    val usedLaunchSiteIds = mutableSetOf<String>()
    return candidates.mapNotNull { candidate ->
        val fresh = usedFavoriteIds.add(candidate.favorite.id) &&
            usedLaunchSiteIds.add(candidate.launchSite.id)
        if (fresh) candidate.toColocation() else null
    }
}

/**
 * The single nearest launch site colocated with [favorite] within [thresholdMeters], or null when the
 * place is not a favorite or nothing is close enough. Used to resolve a selected favorite to its
 * merged marker.
 */
fun nearestColocatedLaunchSite(
    favorite: SavedPlace,
    launchSites: List<ParaglidingLaunchSite>,
    thresholdMeters: Double = COLOCATED_MARKER_THRESHOLD_METERS,
): FavoriteLaunchSite? {
    if (!favorite.isFavorite) return null
    return launchSites
        .mapNotNull { launchSite -> colocationCandidate(favorite, launchSite, thresholdMeters) }
        .minWithOrNull(colocationComparator())
        ?.toColocation()
}

private data class ColocationCandidate(
    val favorite: SavedPlace,
    val launchSite: ParaglidingLaunchSite,
    val distanceMeters: Double,
) {
    fun toColocation(): FavoriteLaunchSite = FavoriteLaunchSite(favorite, launchSite)
}

private fun colocationCandidate(
    favorite: SavedPlace,
    launchSite: ParaglidingLaunchSite,
    thresholdMeters: Double,
): ColocationCandidate? {
    val distanceMeters = colocationDistanceMeters(
        latitude1 = favorite.latitude,
        longitude1 = favorite.longitude,
        latitude2 = launchSite.latitude,
        longitude2 = launchSite.longitude,
    )
    if (distanceMeters > thresholdMeters) return null
    return ColocationCandidate(favorite, launchSite, distanceMeters)
}

private fun colocationComparator(): Comparator<ColocationCandidate> {
    return compareBy<ColocationCandidate> { candidate -> candidate.distanceMeters }
        .thenBy { candidate -> candidate.favorite.id }
        .thenBy { candidate -> candidate.launchSite.id }
}

/** Fast equirectangular approximation, matching the Android colocation math exactly. */
private fun colocationDistanceMeters(
    latitude1: Double,
    longitude1: Double,
    latitude2: Double,
    longitude2: Double,
): Double {
    val deltaLatitude = (latitude1 - latitude2).toRadians()
    val midpointLatitude = ((latitude1 + latitude2) / 2.0).toRadians()
    val deltaLongitude = (longitude1 - longitude2).toRadians() * cos(midpointLatitude)
    return sqrt(deltaLatitude * deltaLatitude + deltaLongitude * deltaLongitude) * EARTH_RADIUS_METERS
}

private fun Double.toRadians(): Double = this * PI / HALF_CIRCLE_DEGREES

private const val HALF_CIRCLE_DEGREES = 180.0
private const val EARTH_RADIUS_METERS = 6_371_000.0
