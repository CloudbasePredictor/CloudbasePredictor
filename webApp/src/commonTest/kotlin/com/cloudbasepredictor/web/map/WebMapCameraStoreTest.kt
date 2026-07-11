package com.cloudbasepredictor.web.map

import com.cloudbasepredictor.data.map.MapCameraPosition
import com.cloudbasepredictor.data.storage.InMemoryKeyValueStorage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class WebMapCameraStoreTest {
    @Test
    fun cameraRoundTripsThroughDurableStorage() {
        val storage = InMemoryKeyValueStorage()
        val camera = MapCameraPosition(latitude = 47.6631, longitude = 11.5217, zoom = 12.5)

        WebMapCameraStore(storage).write(camera)

        assertEquals(camera, WebMapCameraStore(storage).read())
    }

    @Test
    fun absentCameraReadsNull() {
        assertNull(WebMapCameraStore(InMemoryKeyValueStorage()).read())
    }

    @Test
    fun outOfRangeCameraIsRejected() {
        val storage = InMemoryKeyValueStorage()
        storage.putString("map_camera_latitude", "999.0")
        storage.putString("map_camera_longitude", "11.0")
        storage.putString("map_camera_zoom", "10.0")

        assertNull(WebMapCameraStore(storage).read())
    }

    @Test
    fun clearRemovesThePersistedCamera() {
        val storage = InMemoryKeyValueStorage()
        val store = WebMapCameraStore(storage)
        store.write(MapCameraPosition(latitude = 52.52, longitude = 13.405, zoom = 5.5))

        store.clear()

        assertNull(store.read())
    }
}
