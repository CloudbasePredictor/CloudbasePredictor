package com.cloudbasepredictor.model

import com.cloudbasepredictor.util.toFixedDecimalString

data class PlaceLocation(
    val latitude: Double,
    val longitude: Double,
    val name: String? = null,
) {
    fun toRouteValue(): String {
        return "${latitude.toFixedDecimalString(ROUTE_DECIMAL_PLACES)}," +
            longitude.toFixedDecimalString(ROUTE_DECIMAL_PLACES)
    }

    fun toSavedPlace(): SavedPlace {
        val coordinatePlace = SavedPlace.fromCoordinates(
            latitude = latitude,
            longitude = longitude,
        )
        val routeName = name?.trim()?.takeIf { it.isNotEmpty() }
        return if (routeName != null) {
            coordinatePlace.copy(name = routeName)
        } else {
            coordinatePlace
        }
    }

    companion object {
        private const val ROUTE_DECIMAL_PLACES = 6

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
