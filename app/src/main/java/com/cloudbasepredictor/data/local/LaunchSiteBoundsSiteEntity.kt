package com.cloudbasepredictor.data.local

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "paragliding_launch_bounds_sites",
    primaryKeys = ["boundsKey", "siteId"],
    indices = [
        Index(value = ["siteId"]),
    ],
)
data class LaunchSiteBoundsSiteEntity(
    val boundsKey: String,
    val siteId: String,
)
