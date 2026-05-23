package com.cloudbasepredictor.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "paragliding_launch_bounds_cache")
data class LaunchSiteBoundsCacheEntity(
    @PrimaryKey
    val boundsKey: String,
    val south: Double,
    val west: Double,
    val north: Double,
    val east: Double,
    val fetchedAtMillis: Long,
)
