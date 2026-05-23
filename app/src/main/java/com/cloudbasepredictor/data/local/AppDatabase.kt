package com.cloudbasepredictor.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        SavedPlaceEntity::class,
        CachedForecastEntity::class,
        CachedLaunchSiteEntity::class,
        LaunchSiteBoundsCacheEntity::class,
        LaunchSiteBoundsSiteEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun savedPlaceDao(): SavedPlaceDao
    abstract fun forecastCacheDao(): ForecastCacheDao
    abstract fun launchSiteCacheDao(): LaunchSiteCacheDao
}
