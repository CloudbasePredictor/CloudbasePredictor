package com.cloudbasepredictor.model

import com.cloudbasepredictor.util.toFixedDecimalString
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sqrt

data class SavedPlace(
    val id: String,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val isFavorite: Boolean = false,
) {
    /**
     * Returns true if the given coordinates are within ~200 m of this place.
     * Uses a fast equi-rectangular approximation suitable for short distances.
     */
    fun isNearby(lat: Double, lon: Double, thresholdMeters: Double = 200.0): Boolean {
        val dLat = (latitude - lat).toRadians()
        val dLon = (longitude - lon).toRadians() * cos(((latitude + lat) / 2.0).toRadians())
        val distMeters = sqrt(dLat * dLat + dLon * dLon) * EARTH_RADIUS_M
        return distMeters <= thresholdMeters
    }

    companion object {
        private const val EARTH_RADIUS_M = 6_371_000.0

        fun fromCoordinates(
            latitude: Double,
            longitude: Double,
        ): SavedPlace {
            val normalizedLatitude = latitude.toFixedDecimalString(COORDINATE_DECIMAL_PLACES)
            val normalizedLongitude = longitude.toFixedDecimalString(COORDINATE_DECIMAL_PLACES)
            val displayName = "$normalizedLatitude, $normalizedLongitude"

            return SavedPlace(
                id = "place:$normalizedLatitude:$normalizedLongitude",
                name = displayName,
                latitude = latitude,
                longitude = longitude,
            )
        }

        private const val COORDINATE_DECIMAL_PLACES = 4
    }
}

private fun Double.toRadians(): Double = this * PI / HALF_ROTATION_DEGREES

private const val HALF_ROTATION_DEGREES = 180.0
