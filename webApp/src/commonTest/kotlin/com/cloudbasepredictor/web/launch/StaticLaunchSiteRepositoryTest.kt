package com.cloudbasepredictor.web.launch

import com.cloudbasepredictor.data.launch.LaunchSiteBounds
import com.cloudbasepredictor.data.remote.ParaglidingEarthFeature
import com.cloudbasepredictor.data.remote.ParaglidingEarthFeatureCollection
import com.cloudbasepredictor.data.remote.ParaglidingEarthGeometry
import com.cloudbasepredictor.data.remote.ParaglidingEarthProperties
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StaticLaunchSiteRepositoryTest {
    @Test
    fun loadsOnlyTilesIntersectingBounds() = runTest {
        val source = FakeSnapshotSource()
        val repository = StaticLaunchSiteRepository(source)

        val sites = repository.getLaunchSites(alpsBounds)

        assertEquals(listOf("1001", "1002"), sites.map { it.id })
        assertEquals(1, source.loadCount(ALPS_TILE))
        assertEquals(0, source.loadCount(BERLIN_TILE))
    }

    @Test
    fun filtersSitesOutsideExactBounds() = runTest {
        val source = FakeSnapshotSource()
        val repository = StaticLaunchSiteRepository(source)

        // Bounds intersect the Alps tile but exclude Niederhorn (latitude 46.71).
        val sites = repository.getLaunchSites(
            LaunchSiteBounds(north = 46.70, south = 46.0, west = 7.0, east = 8.0),
        )

        assertEquals(listOf("1001"), sites.map { it.id })
    }

    @Test
    fun deduplicatesSitesById() = runTest {
        val source = FakeSnapshotSource(
            alpsFeatures = listOf(
                feature("1001", 7.7986, 46.696),
                feature("1002", 7.75, 46.71),
                feature("1001", 7.7986, 46.696, name = "Duplicate"),
            ),
        )
        val repository = StaticLaunchSiteRepository(source)

        val sites = repository.getLaunchSites(alpsBounds)

        assertEquals(listOf("1001", "1002"), sites.map { it.id })
    }

    @Test
    fun rejectsFeaturesWithInvalidCoordinates() = runTest {
        val source = FakeSnapshotSource(
            alpsFeatures = listOf(
                feature("1001", 7.7986, 46.696),
                ParaglidingEarthFeature(
                    id = "bad",
                    geometry = ParaglidingEarthGeometry(coordinates = listOf(400.0, 900.0)),
                    properties = ParaglidingEarthProperties(name = "Broken", pgeSiteId = "bad"),
                ),
            ),
        )
        val repository = StaticLaunchSiteRepository(source)

        val sites = repository.getLaunchSites(alpsBounds)

        assertEquals(listOf("1001"), sites.map { it.id })
    }

    @Test
    fun cachesTilesAcrossRequests() = runTest {
        val source = FakeSnapshotSource()
        val repository = StaticLaunchSiteRepository(source)

        repository.getLaunchSites(alpsBounds)
        repository.getLaunchSites(alpsBounds)

        assertEquals(1, source.loadCount(ALPS_TILE))
        assertEquals(1, source.manifestLoads)
    }

    @Test
    fun suppressesConcurrentDuplicateTileRequests() = runTest {
        val gate = CompletableDeferred<Unit>()
        val source = FakeSnapshotSource(tileGate = gate)
        val repository = StaticLaunchSiteRepository(source)

        val first = async { repository.getLaunchSites(alpsBounds) }
        val second = async { repository.getLaunchSites(alpsBounds) }
        testScheduler.advanceUntilIdle()
        gate.complete(Unit)
        val firstResult = first.await()
        val secondResult = second.await()

        assertEquals(1, source.loadCount(ALPS_TILE))
        assertEquals(firstResult.map { it.id }, secondResult.map { it.id })
    }

    @Test
    fun limitsResultsToNearestMaxMarkers() = runTest {
        val source = FakeSnapshotSource(
            alpsFeatures = listOf(
                feature("far", 6.10, 46.10),
                feature("near", 7.00, 47.00),
                feature("mid", 6.60, 46.60),
            ),
        )
        val repository = StaticLaunchSiteRepository(source, maxResults = 2)

        val sites = repository.getLaunchSites(
            LaunchSiteBounds(north = 48.0, south = 46.0, west = 6.0, east = 8.0),
        )

        assertEquals(2, sites.size)
        assertTrue(sites.any { it.id == "near" })
        assertTrue(sites.none { it.id == "far" })
    }

    private class FakeSnapshotSource(
        private val alpsFeatures: List<ParaglidingEarthFeature> = listOf(
            feature("1001", 7.7986, 46.696),
            feature("1002", 7.75, 46.71),
        ),
        private val tileGate: CompletableDeferred<Unit>? = null,
    ) : LaunchSiteSnapshotSource {
        var manifestLoads = 0
            private set
        private val tileLoads = mutableMapOf<String, Int>()

        fun loadCount(path: String): Int = tileLoads[path] ?: 0

        override suspend fun loadManifest(): LaunchSiteSnapshotManifest {
            manifestLoads++
            return LaunchSiteSnapshotManifest(
                schemaVersion = 1,
                datasetId = "test",
                siteCount = 3,
                tileSizeDegrees = 2,
                source = LaunchSiteSnapshotAttribution("ParaglidingEarth", "CC BY-SA 3.0"),
                tiles = listOf(
                    LaunchSiteSnapshotTile(
                        key = "68:93",
                        path = ALPS_TILE,
                        south = 46.0,
                        west = 6.0,
                        north = 48.0,
                        east = 8.0,
                    ),
                    LaunchSiteSnapshotTile(
                        key = "71:96",
                        path = BERLIN_TILE,
                        south = 52.0,
                        west = 12.0,
                        north = 54.0,
                        east = 14.0,
                    ),
                ),
            )
        }

        override suspend fun loadTile(relativePath: String): ParaglidingEarthFeatureCollection {
            tileLoads[relativePath] = (tileLoads[relativePath] ?: 0) + 1
            tileGate?.await()
            val features = when (relativePath) {
                ALPS_TILE -> alpsFeatures
                BERLIN_TILE -> listOf(feature("2001", 13.24, 52.49))
                else -> emptyList()
            }
            return ParaglidingEarthFeatureCollection(features = features)
        }
    }

    private companion object {
        const val ALPS_TILE = "tiles/68/93.json"
        const val BERLIN_TILE = "tiles/71/96.json"
        val alpsBounds = LaunchSiteBounds(north = 47.0, south = 46.0, west = 7.0, east = 8.0)

        fun feature(
            id: String,
            longitude: Double,
            latitude: Double,
            name: String = "Site $id",
        ): ParaglidingEarthFeature =
            ParaglidingEarthFeature(
                id = id,
                geometry = ParaglidingEarthGeometry(coordinates = listOf(longitude, latitude)),
                properties = ParaglidingEarthProperties(name = name, pgeSiteId = id),
            )
    }
}
