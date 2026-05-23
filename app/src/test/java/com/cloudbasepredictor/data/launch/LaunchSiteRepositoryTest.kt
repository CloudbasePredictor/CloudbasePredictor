package com.cloudbasepredictor.data.launch

import com.cloudbasepredictor.data.local.CachedLaunchSiteEntity
import com.cloudbasepredictor.data.local.LaunchSiteBoundsCacheEntity
import com.cloudbasepredictor.data.local.LaunchSiteBoundsSiteEntity
import com.cloudbasepredictor.data.local.LaunchSiteCacheDao
import com.cloudbasepredictor.data.remote.ParaglidingEarthApi
import com.cloudbasepredictor.data.remote.ParaglidingEarthFeatureCollection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class LaunchSiteRepositoryTest {
    @Test
    fun getLaunchSites_launchSitesDisabled_doesNotReadCacheOrCallApi() = runBlocking {
        val api = FakeParaglidingEarthApi()
        val dao = FakeLaunchSiteCacheDao()
        val repository = launchSiteRepository(
            api = api,
            dao = dao,
            showLaunchSites = false,
        )

        val sites = repository.getLaunchSites(testBounds)

        assertEquals(0, sites.size)
        assertEquals(0, dao.getBoundsCacheCallCount)
        assertEquals(0, api.getLaunchSitesInBoundsCallCount)
    }

    @Test
    fun getLaunchSites_launchSitesEnabled_callsApiForMissingCache() = runBlocking {
        val api = FakeParaglidingEarthApi()
        val repository = launchSiteRepository(
            api = api,
            showLaunchSites = true,
        )

        repository.getLaunchSites(testBounds)

        assertEquals(1, api.getLaunchSitesInBoundsCallCount)
    }

    @Test
    fun getLaunchSites_launchSitesDisabledAfterCacheRead_doesNotCallApi() = runBlocking {
        val api = FakeParaglidingEarthApi()
        val launchSiteDisplayRepository = FakeLaunchSiteDisplayRepository(showLaunchSites = true)
        val dao = FakeLaunchSiteCacheDao(
            boundsCache = freshBoundsCache,
            onGetBoundsCache = {
                launchSiteDisplayRepository.setShowLaunchSites(false)
            },
        )
        val repository = launchSiteRepository(
            api = api,
            dao = dao,
            launchSiteDisplayRepository = launchSiteDisplayRepository,
        )

        val sites = repository.getLaunchSites(testBounds)

        assertEquals(0, sites.size)
        assertEquals(0, dao.getLaunchSitesForBoundsCallCount)
        assertEquals(0, api.getLaunchSitesInBoundsCallCount)
    }

    private fun launchSiteRepository(
        api: FakeParaglidingEarthApi = FakeParaglidingEarthApi(),
        dao: FakeLaunchSiteCacheDao = FakeLaunchSiteCacheDao(),
        showLaunchSites: Boolean = true,
        launchSiteDisplayRepository: FakeLaunchSiteDisplayRepository =
            FakeLaunchSiteDisplayRepository(showLaunchSites),
    ): DefaultLaunchSiteRepository {
        return DefaultLaunchSiteRepository(
            api = api,
            launchSiteCacheDao = dao,
            launchSiteDisplayRepository = launchSiteDisplayRepository,
            json = Json { ignoreUnknownKeys = true },
            ioDispatcher = Dispatchers.Unconfined,
        )
    }

    private class FakeLaunchSiteDisplayRepository(
        showLaunchSites: Boolean,
    ) : LaunchSiteDisplayRepository {
        private val mutableShowLaunchSites = MutableStateFlow(showLaunchSites)

        override val showLaunchSites: StateFlow<Boolean> = mutableShowLaunchSites.asStateFlow()

        override fun setShowLaunchSites(showLaunchSites: Boolean) {
            mutableShowLaunchSites.value = showLaunchSites
        }
    }

    private class FakeParaglidingEarthApi : ParaglidingEarthApi {
        var getLaunchSitesInBoundsCallCount = 0

        override suspend fun getLaunchSitesInBounds(
            north: Double,
            south: Double,
            west: Double,
            east: Double,
            limit: Int,
            style: String,
        ): ParaglidingEarthFeatureCollection {
            getLaunchSitesInBoundsCallCount++
            return ParaglidingEarthFeatureCollection()
        }
    }

    private class FakeLaunchSiteCacheDao(
        private val boundsCache: LaunchSiteBoundsCacheEntity? = null,
        private val onGetBoundsCache: () -> Unit = {},
    ) : LaunchSiteCacheDao {
        var getBoundsCacheCallCount = 0
        var getLaunchSitesForBoundsCallCount = 0

        override suspend fun getBoundsCache(boundsKey: String): LaunchSiteBoundsCacheEntity? {
            getBoundsCacheCallCount++
            onGetBoundsCache()
            return boundsCache
        }

        override suspend fun getLaunchSitesInBounds(
            south: Double,
            north: Double,
            west: Double,
            east: Double,
        ): List<CachedLaunchSiteEntity> = emptyList()

        override suspend fun getLaunchSitesForBounds(
            boundsKey: String,
        ): List<CachedLaunchSiteEntity> {
            getLaunchSitesForBoundsCallCount++
            return emptyList()
        }

        override suspend fun upsertLaunchSites(sites: List<CachedLaunchSiteEntity>) = Unit

        override suspend fun upsertBoundsCache(bounds: LaunchSiteBoundsCacheEntity) = Unit

        override suspend fun upsertBoundsSiteMappings(mappings: List<LaunchSiteBoundsSiteEntity>) = Unit

        override suspend fun deleteBoundsSiteMappings(boundsKey: String) = Unit

        override suspend fun deleteBoundsOlderThan(cutoffMillis: Long): Int = 0

        override suspend fun deleteLaunchSitesOlderThan(cutoffMillis: Long): Int = 0

        override suspend fun deleteOrphanBoundsSiteMappings(): Int = 0

        override suspend fun deleteMissingSiteMappings(): Int = 0
    }

    private companion object {
        val testBounds = LaunchSiteBounds(
            south = 45.0,
            west = 5.75,
            north = 45.5,
            east = 6.25,
        )
        val freshBoundsCache = LaunchSiteBoundsCacheEntity(
            boundsKey = testBounds.key,
            south = testBounds.south,
            west = testBounds.west,
            north = testBounds.north,
            east = testBounds.east,
            fetchedAtMillis = System.currentTimeMillis(),
        )
    }
}
