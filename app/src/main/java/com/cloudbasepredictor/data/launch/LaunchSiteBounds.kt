package com.cloudbasepredictor.data.launch

import java.util.Locale
import kotlin.math.ceil
import kotlin.math.floor

data class LaunchSiteBounds(
    val north: Double,
    val south: Double,
    val west: Double,
    val east: Double,
) {
    val key: String
        get() = String.format(
            Locale.US,
            "%.2f:%.2f:%.2f:%.2f",
            south,
            west,
            north,
            east,
        )

    val latitudeSpan: Double
        get() = north - south

    val longitudeSpan: Double
        get() = east - west

    fun isValid(): Boolean {
        return north.isFinite() &&
            south.isFinite() &&
            west.isFinite() &&
            east.isFinite() &&
            north in -90.0..90.0 &&
            south in -90.0..90.0 &&
            west in -180.0..180.0 &&
            east in -180.0..180.0 &&
            north > south &&
            east > west
    }

    companion object {
        private const val GRID_SIZE_DEGREES = 0.25
        private const val MAX_QUERY_SPAN_DEGREES = 4.0
        const val MIN_MAP_ZOOM = 7.5

        fun normalizedForMap(
            north: Double,
            south: Double,
            west: Double,
            east: Double,
            zoom: Double,
        ): LaunchSiteBounds? {
            if (zoom < MIN_MAP_ZOOM) return null

            val raw = LaunchSiteBounds(
                north = north.coerceIn(-90.0, 90.0),
                south = south.coerceIn(-90.0, 90.0),
                west = west.coerceIn(-180.0, 180.0),
                east = east.coerceIn(-180.0, 180.0),
            )
            if (!raw.isValid()) return null
            if (
                raw.latitudeSpan > MAX_QUERY_SPAN_DEGREES ||
                raw.longitudeSpan > MAX_QUERY_SPAN_DEGREES
            ) {
                return null
            }

            val normalized = LaunchSiteBounds(
                south = floor(raw.south / GRID_SIZE_DEGREES) * GRID_SIZE_DEGREES,
                north = ceil(raw.north / GRID_SIZE_DEGREES) * GRID_SIZE_DEGREES,
                west = floor(raw.west / GRID_SIZE_DEGREES) * GRID_SIZE_DEGREES,
                east = ceil(raw.east / GRID_SIZE_DEGREES) * GRID_SIZE_DEGREES,
            )
            return if (normalized.isValid()) normalized else null
        }
    }
}
