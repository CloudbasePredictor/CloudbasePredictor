package com.cloudbasepredictor.data.map

import android.content.SharedPreferences
import androidx.core.content.edit

class SharedPreferencesMapCameraStore(
    private val preferences: SharedPreferences,
) : MapCameraStore {
    override fun read(): MapCameraPosition? {
        if (!preferences.getBoolean(MapCameraPersistenceContract.KEY_HAS_POSITION, false)) return null
        return MapCameraPosition(
            latitude = Double.fromBits(preferences.getLong(MapCameraPersistenceContract.KEY_LATITUDE, 0L)),
            longitude = Double.fromBits(preferences.getLong(MapCameraPersistenceContract.KEY_LONGITUDE, 0L)),
            zoom = Double.fromBits(preferences.getLong(MapCameraPersistenceContract.KEY_ZOOM, 0L)),
        )
    }

    override fun write(position: MapCameraPosition) {
        preferences.edit {
            putLong(MapCameraPersistenceContract.KEY_LATITUDE, position.latitude.toBits())
            putLong(MapCameraPersistenceContract.KEY_LONGITUDE, position.longitude.toBits())
            putLong(MapCameraPersistenceContract.KEY_ZOOM, position.zoom.toBits())
            putBoolean(MapCameraPersistenceContract.KEY_HAS_POSITION, true)
        }
    }

    override fun clear() {
        preferences.edit {
            remove(MapCameraPersistenceContract.KEY_LATITUDE)
            remove(MapCameraPersistenceContract.KEY_LONGITUDE)
            remove(MapCameraPersistenceContract.KEY_ZOOM)
            remove(MapCameraPersistenceContract.KEY_HAS_POSITION)
        }
    }
}

internal object MapCameraPersistenceContract {
    const val PREFERENCES_NAME = "map_camera"
    const val KEY_HAS_POSITION = "has_position"
    const val KEY_LATITUDE = "camera_lat"
    const val KEY_LONGITUDE = "camera_lng"
    const val KEY_ZOOM = "camera_zoom"
}
