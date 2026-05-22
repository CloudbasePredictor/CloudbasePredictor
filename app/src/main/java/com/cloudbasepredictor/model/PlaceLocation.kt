package com.cloudbasepredictor.model

import java.util.Locale

data class PlaceLocation(
    val latitude: Double,
    val longitude: Double,
) {
    fun toRouteValue(): String {
        return String.format(Locale.US, ROUTE_VALUE_FORMAT, latitude, longitude)
    }

    fun toSavedPlace(): SavedPlace {
        return SavedPlace.fromCoordinates(
            latitude = latitude,
            longitude = longitude,
        )
    }

    companion object {
        private const val ROUTE_VALUE_FORMAT = "%.6f,%.6f"

        fun fromSavedPlace(place: SavedPlace): PlaceLocation {
            return PlaceLocation(
                latitude = place.latitude,
                longitude = place.longitude,
            )
        }

        fun fromRouteValue(value: String): PlaceLocation? {
            val parts = value.split(',')
            if (parts.size != 2) return null

            val latitude = parts[0].toDoubleOrNull() ?: return null
            val longitude = parts[1].toDoubleOrNull() ?: return null
            if (!latitude.isFinite() || latitude !in -90.0..90.0) return null
            if (!longitude.isFinite() || longitude !in -180.0..180.0) return null

            return PlaceLocation(latitude = latitude, longitude = longitude)
        }
    }
}
