package com.cloudbasepredictor.web.launch

import com.cloudbasepredictor.data.launch.LaunchSiteBounds
import com.cloudbasepredictor.data.launch.LaunchSiteRepository
import com.cloudbasepredictor.data.remote.ParaglidingEarthFeature
import com.cloudbasepredictor.data.remote.toDomainModel
import com.cloudbasepredictor.model.ParaglidingLaunchSite
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Web [LaunchSiteRepository] backed by the build-time static snapshot. Loads the manifest once, then
 * the tiles intersecting the requested [LaunchSiteBounds] in parallel, converts the raw features with
 * the shared [toDomainModel] rule, filters to the exact viewport, and returns the closest results.
 *
 * All state is guarded so overlapping viewport requests do not fetch the same tile twice; a single
 * failing tile degrades to a partial result instead of failing the whole map.
 */
class StaticLaunchSiteRepository(
    private val source: LaunchSiteSnapshotSource,
    private val maxResults: Int = MAX_RESULTS,
    private val maxCachedTiles: Int = MAX_CACHED_TILES,
) : LaunchSiteRepository {
    private val manifestMutex = Mutex()
    private var cachedManifest: LaunchSiteSnapshotManifest? = null

    private val tileMutex = Mutex()
    private val cachedTiles = LinkedHashMap<String, List<ParaglidingLaunchSite>>()
    private val pendingTiles = HashMap<String, CompletableDeferred<List<ParaglidingLaunchSite>>>()

    override suspend fun getLaunchSites(bounds: LaunchSiteBounds): List<ParaglidingLaunchSite> {
        val manifest = loadManifestOnce()
        val tiles = manifest?.tiles.orEmpty().filter { bounds.intersects(it.bounds()) }
        if (tiles.isEmpty()) return emptyList()

        val loaded = coroutineScope {
            tiles.map { tile -> async { loadTileSites(tile) } }.awaitAll()
        }.flatten()
        return selectVisibleSites(bounds, loaded)
    }

    private fun selectVisibleSites(
        bounds: LaunchSiteBounds,
        sites: List<ParaglidingLaunchSite>,
    ): List<ParaglidingLaunchSite> {
        return sites
            .distinctBy(ParaglidingLaunchSite::id)
            .filter { bounds.contains(it.latitude, it.longitude) }
            .sortedBy { squaredDistanceToCenter(bounds, it) }
            .take(maxResults)
            .sortedBy(ParaglidingLaunchSite::name)
    }

    private suspend fun loadManifestOnce(): LaunchSiteSnapshotManifest? {
        cachedManifest?.let { return it }
        return manifestMutex.withLock {
            cachedManifest ?: runCatching { source.loadManifest() }
                .getOrNull()
                ?.also { cachedManifest = it }
        }
    }

    // Three exits express the cache-hit / awaiting-existing-load / owner-fetches states clearly.
    @Suppress("ReturnCount")
    private suspend fun loadTileSites(tile: LaunchSiteSnapshotTile): List<ParaglidingLaunchSite> {
        val (deferred, owned) = tileMutex.withLock {
            cachedTiles[tile.path]?.let { cached -> return touchCache(tile.path, cached) }
            pendingTiles[tile.path]?.let { existing -> existing to false }
                ?: CompletableDeferred<List<ParaglidingLaunchSite>>()
                    .also { fresh -> pendingTiles[tile.path] = fresh }
                    .let { fresh -> fresh to true }
        }
        if (!owned) return deferred.await()

        val sites = runCatching { fetchTileSites(tile) }.getOrDefault(emptyList())
        tileMutex.withLock {
            pendingTiles.remove(tile.path)
            if (sites.isNotEmpty()) storeInCache(tile.path, sites)
        }
        deferred.complete(sites)
        return sites
    }

    private suspend fun fetchTileSites(tile: LaunchSiteSnapshotTile): List<ParaglidingLaunchSite> =
        source.loadTile(tile.path).features.mapNotNull(ParaglidingEarthFeature::toDomainModel)

    private fun touchCache(
        path: String,
        cached: List<ParaglidingLaunchSite>,
    ): List<ParaglidingLaunchSite> {
        cachedTiles.remove(path)
        cachedTiles[path] = cached
        return cached
    }

    private fun storeInCache(path: String, sites: List<ParaglidingLaunchSite>) {
        cachedTiles.remove(path)
        cachedTiles[path] = sites
        while (cachedTiles.size > maxCachedTiles) {
            val oldest = cachedTiles.keys.firstOrNull() ?: break
            cachedTiles.remove(oldest)
        }
    }

    private fun squaredDistanceToCenter(
        bounds: LaunchSiteBounds,
        site: ParaglidingLaunchSite,
    ): Double {
        val centerLatitude = (bounds.north + bounds.south) / 2.0
        val centerLongitude = (bounds.west + bounds.east) / 2.0
        val deltaLatitude = site.latitude - centerLatitude
        val deltaLongitude = site.longitude - centerLongitude
        return deltaLatitude * deltaLatitude + deltaLongitude * deltaLongitude
    }

    private companion object {
        const val MAX_RESULTS = 150
        const val MAX_CACHED_TILES = 128
    }
}
