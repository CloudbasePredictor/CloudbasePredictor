package com.cloudbasepredictor.ui.screens.map

import com.cloudbasepredictor.model.ParaglidingLaunchSite
import com.cloudbasepredictor.model.SavedPlace
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import org.maplibre.spatialk.geojson.Feature

internal fun buildMarkerFeatureCollection(place: SavedPlace): String {
    return """
        {
          "type": "FeatureCollection",
          "features": [
            {
              "type": "Feature",
              "geometry": {
                "type": "Point",
                "coordinates": [${place.longitude}, ${place.latitude}]
              },
              "properties": {
                "$GEOJSON_PROPERTY_PLACE_ID": "${place.id.escapeJsonString()}",
                "$GEOJSON_PROPERTY_NAME": "${place.name.escapeJsonString()}"
              }
            }
          ]
        }
    """.trimIndent()
}

internal fun buildFavoritesFeatureCollection(places: List<SavedPlace>): String {
    if (places.isEmpty()) return emptyFeatureCollection()
    val features = places.joinToString(",") { place ->
        """
            {
              "type": "Feature",
              "geometry": {
                "type": "Point",
                "coordinates": [${place.longitude}, ${place.latitude}]
              },
              "properties": {
                "$GEOJSON_PROPERTY_PLACE_ID": "${place.id.escapeJsonString()}",
                "$GEOJSON_PROPERTY_NAME": "${place.name.escapeJsonString()}"
              }
            }
        """
    }
    return """
        {
          "type": "FeatureCollection",
          "features": [$features]
        }
    """.trimIndent()
}

internal fun buildLaunchSitesFeatureCollection(sites: List<ParaglidingLaunchSite>): String {
    if (sites.isEmpty()) return emptyFeatureCollection()
    val features = sites.joinToString(",") { site ->
        """
            {
              "type": "Feature",
              "geometry": {
                "type": "Point",
                "coordinates": [${site.longitude}, ${site.latitude}]
              },
              "properties": {
                "$GEOJSON_PROPERTY_LAUNCH_SITE_ID": "${site.id.escapeJsonString()}",
                "$GEOJSON_PROPERTY_NAME": "${site.name.escapeJsonString()}"
              }
            }
        """
    }
    return """
        {
          "type": "FeatureCollection",
          "features": [$features]
        }
    """.trimIndent()
}

internal fun emptyFeatureCollection(): String {
    return """
        {
          "type": "FeatureCollection",
          "features": []
        }
    """.trimIndent()
}

internal fun findFavoritePlaceForFeatures(
    features: List<Feature<*, JsonObject?>>,
    favoritePlaces: List<SavedPlace>,
): SavedPlace? {
    return features.firstNotNullOfOrNull { feature ->
        val placeId = feature.properties
            ?.get(GEOJSON_PROPERTY_PLACE_ID)
            ?.jsonPrimitive
            ?.contentOrNull

        favoritePlaces.firstOrNull { favorite -> favorite.id == placeId }
    }
}

internal fun findLaunchSiteForFeatures(
    features: List<Feature<*, JsonObject?>>,
    launchSites: List<ParaglidingLaunchSite>,
): ParaglidingLaunchSite? {
    return features.firstNotNullOfOrNull { feature ->
        val launchSiteId = feature.properties
            ?.get(GEOJSON_PROPERTY_LAUNCH_SITE_ID)
            ?.jsonPrimitive
            ?.contentOrNull

        launchSites.firstOrNull { site -> site.id == launchSiteId }
    }
}

private fun String.escapeJsonString(): String {
    return buildString(length) {
        this@escapeJsonString.forEach { char ->
            when (char) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\b' -> append("\\b")
                '\u000C' -> append("\\f")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> {
                    if (char < ' ') {
                        append("\\u")
                        append(char.code.toString(16).padStart(4, '0'))
                    } else {
                        append(char)
                    }
                }
            }
        }
    }
}

private const val GEOJSON_PROPERTY_NAME = "name"
private const val GEOJSON_PROPERTY_PLACE_ID = "placeId"
private const val GEOJSON_PROPERTY_LAUNCH_SITE_ID = "launchSiteId"
