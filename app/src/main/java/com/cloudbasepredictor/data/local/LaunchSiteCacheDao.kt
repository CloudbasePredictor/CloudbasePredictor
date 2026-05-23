package com.cloudbasepredictor.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface LaunchSiteCacheDao {
    @Query("SELECT * FROM paragliding_launch_bounds_cache WHERE boundsKey = :boundsKey LIMIT 1")
    suspend fun getBoundsCache(boundsKey: String): LaunchSiteBoundsCacheEntity?

    @Query(
        """
        SELECT * FROM paragliding_launch_sites
        WHERE latitude BETWEEN :south AND :north
          AND longitude BETWEEN :west AND :east
        ORDER BY name ASC
        """
    )
    suspend fun getLaunchSitesInBounds(
        south: Double,
        north: Double,
        west: Double,
        east: Double,
    ): List<CachedLaunchSiteEntity>

    @Query(
        """
        SELECT paragliding_launch_sites.* FROM paragliding_launch_sites
        INNER JOIN paragliding_launch_bounds_sites
          ON paragliding_launch_sites.id = paragliding_launch_bounds_sites.siteId
        WHERE paragliding_launch_bounds_sites.boundsKey = :boundsKey
        ORDER BY paragliding_launch_sites.name ASC
        """
    )
    suspend fun getLaunchSitesForBounds(boundsKey: String): List<CachedLaunchSiteEntity>

    @Upsert
    suspend fun upsertLaunchSites(sites: List<CachedLaunchSiteEntity>)

    @Upsert
    suspend fun upsertBoundsCache(bounds: LaunchSiteBoundsCacheEntity)

    @Upsert
    suspend fun upsertBoundsSiteMappings(mappings: List<LaunchSiteBoundsSiteEntity>)

    @Query("DELETE FROM paragliding_launch_bounds_sites WHERE boundsKey = :boundsKey")
    suspend fun deleteBoundsSiteMappings(boundsKey: String)

    @Query("DELETE FROM paragliding_launch_bounds_cache WHERE fetchedAtMillis < :cutoffMillis")
    suspend fun deleteBoundsOlderThan(cutoffMillis: Long): Int

    @Query("DELETE FROM paragliding_launch_sites WHERE fetchedAtMillis < :cutoffMillis")
    suspend fun deleteLaunchSitesOlderThan(cutoffMillis: Long): Int

    @Query(
        """
        DELETE FROM paragliding_launch_bounds_sites
        WHERE boundsKey NOT IN (SELECT boundsKey FROM paragliding_launch_bounds_cache)
        """
    )
    suspend fun deleteOrphanBoundsSiteMappings(): Int

    @Query(
        """
        DELETE FROM paragliding_launch_bounds_sites
        WHERE siteId NOT IN (SELECT id FROM paragliding_launch_sites)
        """
    )
    suspend fun deleteMissingSiteMappings(): Int
}
