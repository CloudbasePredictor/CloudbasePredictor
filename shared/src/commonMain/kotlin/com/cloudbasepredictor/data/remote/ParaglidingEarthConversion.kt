package com.cloudbasepredictor.data.remote

import com.cloudbasepredictor.model.LaunchSiteOrientation
import com.cloudbasepredictor.model.ParaglidingLaunchSite

fun ParaglidingEarthFeature.toDomainModel(): ParaglidingLaunchSite? =
    validCoordinates()?.let { coordinates ->
        properties?.toDomainModel(
            featureId = id,
            coordinates = coordinates,
        )
    }

private fun ParaglidingEarthFeature.validCoordinates(): LaunchCoordinates? {
    val coordinates = geometry?.coordinates.orEmpty()
    val longitude = coordinates.getOrNull(LONGITUDE_INDEX)?.takeIf { value ->
        value.isFinite() && value in MINIMUM_LONGITUDE..MAXIMUM_LONGITUDE
    }
    val latitude = coordinates.getOrNull(LATITUDE_INDEX)?.takeIf { value ->
        value.isFinite() && value in MINIMUM_LATITUDE..MAXIMUM_LATITUDE
    }
    return if (latitude != null && longitude != null) {
        LaunchCoordinates(latitude = latitude, longitude = longitude)
    } else {
        null
    }
}

private fun ParaglidingEarthProperties.toDomainModel(
    featureId: String?,
    coordinates: LaunchCoordinates,
): ParaglidingLaunchSite? {
    val siteId = pgeSiteId.orSanitizedNull() ?: featureId.orSanitizedNull()
    val siteName = name.orSanitizedNull()

    return if (siteId != null && siteName != null) {
        ParaglidingLaunchSite(
            id = siteId,
            name = siteName,
            latitude = coordinates.latitude,
            longitude = coordinates.longitude,
            altitudeMeters = takeoffAltitude?.toIntOrNull(),
            countryCode = countryCode.orSanitizedNull(),
            description = takeoffDescription.compactMultilineText(),
            flightRules = flightRules.compactMultilineText(),
            access = goingThere.compactMultilineText(),
            comments = comments.compactMultilineText(),
            weather = weather.compactMultilineText(),
            lastEdit = lastEdit.orSanitizedNull(),
            link = pgeLink.orSanitizedNull()?.replace("http://", "https://"),
            orientations = launchSiteOrientations(),
            activities = launchSiteActivities(),
            landingName = landing?.landingName.orSanitizedNull(),
            landingLatitude = landing?.landingLat?.toDoubleOrNull(),
            landingLongitude = landing?.landingLng?.toDoubleOrNull(),
        )
    } else {
        null
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

private data class LaunchCoordinates(
    val latitude: Double,
    val longitude: Double,
)

private const val LONGITUDE_INDEX = 0
private const val LATITUDE_INDEX = 1
private const val MINIMUM_LATITUDE = -90.0
private const val MAXIMUM_LATITUDE = 90.0
private const val MINIMUM_LONGITUDE = -180.0
private const val MAXIMUM_LONGITUDE = 180.0
