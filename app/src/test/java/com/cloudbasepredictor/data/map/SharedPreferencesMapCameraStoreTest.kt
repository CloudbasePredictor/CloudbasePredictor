package com.cloudbasepredictor.data.map

import com.cloudbasepredictor.testing.FakeSharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SharedPreferencesMapCameraStoreTest {
    @Test
    fun compatibilityContract_keepsTheExistingFileAndKeys() {
        assertEquals("map_camera", MapCameraPersistenceContract.PREFERENCES_NAME)
        assertEquals("has_position", MapCameraPersistenceContract.KEY_HAS_POSITION)
        assertEquals("camera_lat", MapCameraPersistenceContract.KEY_LATITUDE)
        assertEquals("camera_lng", MapCameraPersistenceContract.KEY_LONGITUDE)
        assertEquals("camera_zoom", MapCameraPersistenceContract.KEY_ZOOM)
    }

    @Test
    fun read_restoresExistingPositionFromLegacyKeysWithExactBits() {
        val latitudeBits = (-0.0).toBits()
        val longitudeBits = Double.fromBits(1L).toBits()
        val zoomBits = 12.3456789012345.toBits()
        val preferences = FakeSharedPreferences(
            mapOf(
                MapCameraPersistenceContract.KEY_HAS_POSITION to true,
                MapCameraPersistenceContract.KEY_LATITUDE to latitudeBits,
                MapCameraPersistenceContract.KEY_LONGITUDE to longitudeBits,
                MapCameraPersistenceContract.KEY_ZOOM to zoomBits,
            ),
        )

        val position = SharedPreferencesMapCameraStore(preferences).read()

        requireNotNull(position)
        assertEquals(latitudeBits, position.latitude.toBits())
        assertEquals(longitudeBits, position.longitude.toBits())
        assertEquals(zoomBits, position.zoom.toBits())
    }

    @Test
    fun read_ignoresStaleCoordinatesWithoutPositionFlag() {
        val preferences = FakeSharedPreferences(
            mapOf(MapCameraPersistenceContract.KEY_LATITUDE to 47.0.toBits()),
        )

        assertNull(SharedPreferencesMapCameraStore(preferences).read())
    }

    @Test
    fun write_persistsExactCoordinateBitsAndPresenceFlag() {
        val preferences = FakeSharedPreferences()
        val position = MapCameraPosition(
            latitude = -0.0,
            longitude = Double.fromBits(1L),
            zoom = 14.125,
        )

        SharedPreferencesMapCameraStore(preferences).write(position)

        assertEquals(position.latitude.toBits(), preferences.getLong(MapCameraPersistenceContract.KEY_LATITUDE, 0L))
        assertEquals(position.longitude.toBits(), preferences.getLong(MapCameraPersistenceContract.KEY_LONGITUDE, 0L))
        assertEquals(position.zoom.toBits(), preferences.getLong(MapCameraPersistenceContract.KEY_ZOOM, 0L))
        assertTrue(preferences.getBoolean(MapCameraPersistenceContract.KEY_HAS_POSITION, false))
    }

    @Test
    fun clear_removesTheWholeCameraRecord() {
        val preferences = FakeSharedPreferences()
        val store = SharedPreferencesMapCameraStore(preferences)
        store.write(MapCameraPosition(latitude = 47.2692, longitude = 11.4041, zoom = 10.0))

        store.clear()

        assertNull(store.read())
        assertFalse(preferences.contains(MapCameraPersistenceContract.KEY_HAS_POSITION))
        assertFalse(preferences.contains(MapCameraPersistenceContract.KEY_LATITUDE))
        assertFalse(preferences.contains(MapCameraPersistenceContract.KEY_LONGITUDE))
        assertFalse(preferences.contains(MapCameraPersistenceContract.KEY_ZOOM))
    }
}
