package com.cloudbasepredictor.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "paragliding_launch_sites",
    indices = [
        Index(value = ["latitude", "longitude"]),
    ],
)
data class CachedLaunchSiteEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val altitudeMeters: Int?,
    val countryCode: String?,
    val description: String?,
    val flightRules: String?,
    val access: String?,
    val comments: String?,
    val weather: String?,
    val lastEdit: String?,
    val link: String?,
    val orientationsJson: String,
    val activitiesJson: String,
    val landingName: String?,
    val landingLatitude: Double?,
    val landingLongitude: Double?,
    val fetchedAtMillis: Long,
)
