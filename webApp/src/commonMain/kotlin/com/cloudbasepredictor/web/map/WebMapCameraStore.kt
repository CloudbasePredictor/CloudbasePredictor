package com.cloudbasepredictor.web.map

import com.cloudbasepredictor.data.map.MapCameraPosition
import com.cloudbasepredictor.data.map.MapCameraStore
import com.cloudbasepredictor.data.storage.KeyValueStorage

/**
 * Persists the last map camera in the durable key-value store (the browser's `cbp.kmp.user-state`
 * document), mirroring Android's `SharedPreferencesMapCameraStore`. Values are stored as strings to
 * keep full `Double` precision for latitude/longitude. Restored on the next map open so the camera
 * survives reloads and navigation; falls back to the Android default (Berlin) when absent.
 */
class WebMapCameraStore(
    private val storage: KeyValueStorage,
) : MapCameraStore {
    override fun read(): MapCameraPosition? {
        val latitude = storage.getString(LATITUDE_KEY)?.toDoubleOrNull()
        val longitude = storage.getString(LONGITUDE_KEY)?.toDoubleOrNull()
        val zoom = storage.getString(ZOOM_KEY)?.toDoubleOrNull()
        if (latitude == null || longitude == null || zoom == null) return null
        return MapCameraPosition(latitude = latitude, longitude = longitude, zoom = zoom)
            .takeIf { latitude in MIN_LATITUDE..MAX_LATITUDE && longitude in MIN_LONGITUDE..MAX_LONGITUDE }
    }

    override fun write(position: MapCameraPosition) {
        storage.putString(LATITUDE_KEY, position.latitude.toString())
        storage.putString(LONGITUDE_KEY, position.longitude.toString())
        storage.putString(ZOOM_KEY, position.zoom.toString())
    }

    override fun clear() {
        storage.remove(LATITUDE_KEY)
        storage.remove(LONGITUDE_KEY)
        storage.remove(ZOOM_KEY)
    }

    private companion object {
        const val LATITUDE_KEY = "map_camera_latitude"
        const val LONGITUDE_KEY = "map_camera_longitude"
        const val ZOOM_KEY = "map_camera_zoom"
        const val MIN_LATITUDE = -90.0
        const val MAX_LATITUDE = 90.0
        const val MIN_LONGITUDE = -180.0
        const val MAX_LONGITUDE = 180.0
    }
}
