package com.cloudbasepredictor.model

import kotlinx.serialization.Serializable

@Serializable
data class ParaglidingLaunchSite(
    val id: String,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val altitudeMeters: Int? = null,
    val countryCode: String? = null,
    val description: String? = null,
    val flightRules: String? = null,
    val access: String? = null,
    val comments: String? = null,
    val weather: String? = null,
    val lastEdit: String? = null,
    val link: String? = null,
    val orientations: List<LaunchSiteOrientation> = emptyList(),
    val activities: List<String> = emptyList(),
    val landingName: String? = null,
    val landingLatitude: Double? = null,
    val landingLongitude: Double? = null,
) {
    fun toPlaceLocation(): PlaceLocation {
        return PlaceLocation(
            latitude = latitude,
            longitude = longitude,
            name = name,
        )
    }
}

@Serializable
data class LaunchSiteOrientation(
    val direction: String,
    val rating: Int,
)
