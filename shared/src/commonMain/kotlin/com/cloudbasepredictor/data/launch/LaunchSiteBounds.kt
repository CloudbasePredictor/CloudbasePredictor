package com.cloudbasepredictor.data.launch

import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * Geographic bounding box used to query paragliding launch sites, normalized to a stable grid so
 * repeated map interactions reuse the same cache/tile selection. Shared between the Android live-API
 * cache (`DefaultLaunchSiteRepository`) and the web static-snapshot repository.
 */
data class LaunchSiteBounds(
    val north: Double,
    val south: Double,
    val west: Double,
    val east: Double,
) {
    /**
     * Stable, platform-independent key built from integer grid indices. Avoids locale-sensitive
     * string formatting so the same normalized box maps to the same key on every platform.
     */
    val key: String
        get() = "${gridIndex(south)}:${gridIndex(west)}:${gridIndex(north)}:${gridIndex(east)}"

    val latitudeSpan: Double
        get() = north - south

    val longitudeSpan: Double
        get() = east - west

    fun isValid(): Boolean {
        return north.isFinite() &&
            south.isFinite() &&
            west.isFinite() &&
            east.isFinite() &&
            north in MIN_LATITUDE..MAX_LATITUDE &&
            south in MIN_LATITUDE..MAX_LATITUDE &&
            west in MIN_LONGITUDE..MAX_LONGITUDE &&
            east in MIN_LONGITUDE..MAX_LONGITUDE &&
            north > south &&
            east > west
    }

    /** True when the point lies inside this box (inclusive). Used for exact viewport filtering. */
    fun contains(latitude: Double, longitude: Double): Boolean {
        return latitude in south..north && longitude in west..east
    }

    /** True when this box overlaps [other]. Used to pick snapshot tiles that touch the viewport. */
    fun intersects(other: LaunchSiteBounds): Boolean {
        val longitudesOverlap = west <= other.east && east >= other.west
        val latitudesOverlap = south <= other.north && north >= other.south
        return longitudesOverlap && latitudesOverlap
    }

    companion object {
        private const val GRID_SIZE_DEGREES = 0.25
        private const val MAX_QUERY_SPAN_DEGREES = 4.0
        private const val MIN_LATITUDE = -90.0
        private const val MAX_LATITUDE = 90.0
        private const val MIN_LONGITUDE = -180.0
        private const val MAX_LONGITUDE = 180.0
        const val MIN_MAP_ZOOM = 7.5

        private fun gridIndex(value: Double): Int = (value / GRID_SIZE_DEGREES).roundToInt()

        fun normalizedForMap(
            north: Double,
            south: Double,
            west: Double,
            east: Double,
            zoom: Double,
        ): LaunchSiteBounds? {
            val raw = LaunchSiteBounds(
                north = north.coerceIn(MIN_LATITUDE, MAX_LATITUDE),
                south = south.coerceIn(MIN_LATITUDE, MAX_LATITUDE),
                west = west.coerceIn(MIN_LONGITUDE, MAX_LONGITUDE),
                east = east.coerceIn(MIN_LONGITUDE, MAX_LONGITUDE),
            )
            val withinBudget = raw.isValid() &&
                raw.latitudeSpan <= MAX_QUERY_SPAN_DEGREES &&
                raw.longitudeSpan <= MAX_QUERY_SPAN_DEGREES
            if (zoom < MIN_MAP_ZOOM || !withinBudget) return null

            val normalized = LaunchSiteBounds(
                south = floor(raw.south / GRID_SIZE_DEGREES) * GRID_SIZE_DEGREES,
                north = ceil(raw.north / GRID_SIZE_DEGREES) * GRID_SIZE_DEGREES,
                west = floor(raw.west / GRID_SIZE_DEGREES) * GRID_SIZE_DEGREES,
                east = ceil(raw.east / GRID_SIZE_DEGREES) * GRID_SIZE_DEGREES,
            )
            return normalized.takeIf { it.isValid() }
        }
    }
}
