package com.cloudbasepredictor.data.launch

import com.cloudbasepredictor.data.local.CachedLaunchSiteEntity
import com.cloudbasepredictor.data.local.LaunchSiteBoundsCacheEntity
import com.cloudbasepredictor.data.local.LaunchSiteBoundsSiteEntity
import com.cloudbasepredictor.data.local.LaunchSiteCacheDao
import com.cloudbasepredictor.data.remote.ParaglidingEarthApi
import com.cloudbasepredictor.data.remote.ParaglidingEarthFeature
import com.cloudbasepredictor.data.remote.ParaglidingEarthProperties
import com.cloudbasepredictor.di.IoDispatcher
import com.cloudbasepredictor.model.LaunchSiteOrientation
import com.cloudbasepredictor.model.ParaglidingLaunchSite
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import timber.log.Timber

interface LaunchSiteRepository {
    suspend fun getLaunchSites(bounds: LaunchSiteBounds): List<ParaglidingLaunchSite>
}

@Singleton
class DefaultLaunchSiteRepository @Inject constructor(
    private val api: ParaglidingEarthApi,
    private val launchSiteCacheDao: LaunchSiteCacheDao,
    private val launchSiteDisplayRepository: LaunchSiteDisplayRepository,
    private val json: Json,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : LaunchSiteRepository {
    override suspend fun getLaunchSites(bounds: LaunchSiteBounds): List<ParaglidingLaunchSite> = withContext(ioDispatcher) {
        if (launchSitesDisabled()) {
            return@withContext emptyList()
        }

        val key = bounds.key
        val now = System.currentTimeMillis()
        val cachedBounds = launchSiteCacheDao.getBoundsCache(key)
        if (launchSitesDisabled()) {
            return@withContext emptyList()
        }

        val cachedSites = if (cachedBounds != null) {
            loadCachedSitesForBounds(key)
        } else {
            loadCachedSitesInBounds(bounds)
        }

        if (launchSitesDisabled()) {
            return@withContext emptyList()
        }

        if (cachedBounds?.isFresh(now) == true) {
            return@withContext cachedSites
        }

        if (launchSitesDisabled()) {
            return@withContext emptyList()
        }

        try {
            val sites = api.getLaunchSitesInBounds(
                north = bounds.north,
                south = bounds.south,
                west = bounds.west,
                east = bounds.east,
            ).features.mapNotNull(ParaglidingEarthFeature::toDomainModel)

            launchSiteCacheDao.upsertLaunchSites(
                sites.map { site -> site.toEntity(json, fetchedAtMillis = now) },
            )
            launchSiteCacheDao.deleteBoundsSiteMappings(key)
            launchSiteCacheDao.upsertBoundsSiteMappings(
                sites.map { site ->
                    LaunchSiteBoundsSiteEntity(
                        boundsKey = key,
                        siteId = site.id,
                    )
                },
            )
            launchSiteCacheDao.upsertBoundsCache(bounds.toEntity(fetchedAtMillis = now))
            cleanupOldCache(now)
            sites.sortedBy(ParaglidingLaunchSite::name)
        } catch (throwable: CancellationException) {
            throw throwable
        } catch (throwable: Throwable) {
            if (cachedSites.isNotEmpty()) {
                Timber.w(throwable, "Using stale ParaglidingEarth launch site cache")
                cachedSites
            } else {
                throw throwable
            }
        }
    }

    private fun launchSitesDisabled(): Boolean {
        return !launchSiteDisplayRepository.showLaunchSites.value
    }

    private suspend fun loadCachedSitesForBounds(boundsKey: String): List<ParaglidingLaunchSite> {
        return launchSiteCacheDao.getLaunchSitesForBounds(boundsKey)
            .map { entity -> entity.toDomainModel(json) }
    }

    private suspend fun loadCachedSitesInBounds(bounds: LaunchSiteBounds): List<ParaglidingLaunchSite> {
        return launchSiteCacheDao.getLaunchSitesInBounds(
            south = bounds.south,
            north = bounds.north,
            west = bounds.west,
            east = bounds.east,
        ).map { entity -> entity.toDomainModel(json) }
    }

    private suspend fun cleanupOldCache(now: Long) {
        launchSiteCacheDao.deleteBoundsOlderThan(now - BOUNDS_CACHE_RETENTION_MILLIS)
        launchSiteCacheDao.deleteLaunchSitesOlderThan(now - LAUNCH_SITE_RETENTION_MILLIS)
        launchSiteCacheDao.deleteOrphanBoundsSiteMappings()
        launchSiteCacheDao.deleteMissingSiteMappings()
    }

    private fun LaunchSiteBoundsCacheEntity.isFresh(now: Long): Boolean {
        return now - fetchedAtMillis <= BOUNDS_CACHE_TTL_MILLIS
    }

    private fun LaunchSiteBounds.toEntity(fetchedAtMillis: Long): LaunchSiteBoundsCacheEntity {
        return LaunchSiteBoundsCacheEntity(
            boundsKey = key,
            south = south,
            west = west,
            north = north,
            east = east,
            fetchedAtMillis = fetchedAtMillis,
        )
    }

    companion object {
        private const val BOUNDS_CACHE_TTL_MILLIS = 30L * 24L * 60L * 60L * 1_000L
        private const val BOUNDS_CACHE_RETENTION_MILLIS = 180L * 24L * 60L * 60L * 1_000L
        private const val LAUNCH_SITE_RETENTION_MILLIS = 365L * 24L * 60L * 60L * 1_000L
    }
}

internal fun ParaglidingEarthFeature.toDomainModel(): ParaglidingLaunchSite? {
    val coordinates = geometry?.coordinates.orEmpty()
    val longitude = coordinates.getOrNull(0)?.takeIf { it.isFinite() } ?: return null
    val latitude = coordinates.getOrNull(1)?.takeIf { it.isFinite() } ?: return null
    if (latitude !in -90.0..90.0 || longitude !in -180.0..180.0) return null

    val props = properties ?: return null
    val siteId = props.pgeSiteId.orSanitizedNull() ?: id.orSanitizedNull() ?: return null
    val siteName = props.name.orSanitizedNull() ?: return null

    return ParaglidingLaunchSite(
        id = siteId,
        name = siteName,
        latitude = latitude,
        longitude = longitude,
        altitudeMeters = props.takeoffAltitude?.toIntOrNull(),
        countryCode = props.countryCode.orSanitizedNull(),
        description = props.takeoffDescription.compactMultilineText(),
        flightRules = props.flightRules.compactMultilineText(),
        access = props.goingThere.compactMultilineText(),
        comments = props.comments.compactMultilineText(),
        weather = props.weather.compactMultilineText(),
        lastEdit = props.lastEdit.orSanitizedNull(),
        link = props.pgeLink.orSanitizedNull()?.replace("http://", "https://"),
        orientations = props.launchSiteOrientations(),
        activities = props.launchSiteActivities(),
        landingName = props.landing?.landingName.orSanitizedNull(),
        landingLatitude = props.landing?.landingLat?.toDoubleOrNull(),
        landingLongitude = props.landing?.landingLng?.toDoubleOrNull(),
    )
}

private fun ParaglidingLaunchSite.toEntity(
    json: Json,
    fetchedAtMillis: Long,
): CachedLaunchSiteEntity {
    return CachedLaunchSiteEntity(
        id = id,
        name = name,
        latitude = latitude,
        longitude = longitude,
        altitudeMeters = altitudeMeters,
        countryCode = countryCode,
        description = description,
        flightRules = flightRules,
        access = access,
        comments = comments,
        weather = weather,
        lastEdit = lastEdit,
        link = link,
        orientationsJson = json.encodeToString(orientations),
        activitiesJson = json.encodeToString(activities),
        landingName = landingName,
        landingLatitude = landingLatitude,
        landingLongitude = landingLongitude,
        fetchedAtMillis = fetchedAtMillis,
    )
}

private fun CachedLaunchSiteEntity.toDomainModel(json: Json): ParaglidingLaunchSite {
    return ParaglidingLaunchSite(
        id = id,
        name = name,
        latitude = latitude,
        longitude = longitude,
        altitudeMeters = altitudeMeters,
        countryCode = countryCode,
        description = description,
        flightRules = flightRules,
        access = access,
        comments = comments,
        weather = weather,
        lastEdit = lastEdit,
        link = link,
        orientations = decodeCacheList(orientationsJson, json),
        activities = decodeCacheList(activitiesJson, json),
        landingName = landingName,
        landingLatitude = landingLatitude,
        landingLongitude = landingLongitude,
    )
}

private inline fun <reified T> decodeCacheList(
    value: String,
    json: Json,
): List<T> {
    return try {
        json.decodeFromString<List<T>>(value)
    } catch (error: SerializationException) {
        Timber.w(error, "Ignoring invalid cached ParaglidingEarth list")
        emptyList()
    } catch (error: IllegalArgumentException) {
        Timber.w(error, "Ignoring invalid cached ParaglidingEarth list")
        emptyList()
    }
}

private fun ParaglidingEarthProperties.launchSiteOrientations(): List<LaunchSiteOrientation> {
    return listOf(
        "N" to north,
        "NE" to northEast,
        "E" to east,
        "SE" to southEast,
        "S" to south,
        "SW" to southWest,
        "W" to west,
        "NW" to northWest,
    ).mapNotNull { (direction, value) ->
        val rating = value?.toIntOrNull() ?: return@mapNotNull null
        if (rating <= 0) return@mapNotNull null
        LaunchSiteOrientation(
            direction = direction,
            rating = rating,
        )
    }
}

private fun ParaglidingEarthProperties.launchSiteActivities(): List<String> {
    return buildList {
        addActivity(paragliding, "Paragliding")
        addActivity(hanggliding, "Hang gliding")
        addActivity(thermals, "Thermals")
        addActivity(soaring, "Soaring")
        addActivity(xc, "XC")
        addActivity(winch, "Winch")
        addActivity(hike, "Hike")
        addActivity(flatland, "Flatland")
    }
}

private fun MutableList<String>.addActivity(
    value: String?,
    label: String,
) {
    if (value == "1") add(label)
}

private fun String?.orSanitizedNull(): String? {
    return this
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
}

private fun String?.compactMultilineText(): String? {
    return orSanitizedNull()
        ?.replace(Regex("\\s+"), " ")
        ?.takeIf { it.isNotEmpty() }
}
