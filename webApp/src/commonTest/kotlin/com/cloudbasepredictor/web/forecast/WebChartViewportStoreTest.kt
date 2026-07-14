package com.cloudbasepredictor.web.forecast

import com.cloudbasepredictor.data.storage.InMemoryKeyValueStorage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class WebChartViewportStoreTest {
    @Test
    fun topAltitudeRoundTripsThroughStorage() {
        val storage = InMemoryKeyValueStorage()
        WebChartViewportStore(storage).writeTopAltitudeKm(4.5f)

        assertEquals(4.5f, WebChartViewportStore(storage).readTopAltitudeKm())
    }

    @Test
    fun readReturnsNullWhenAbsent() {
        assertNull(WebChartViewportStore(InMemoryKeyValueStorage()).readTopAltitudeKm())
    }

    @Test
    fun legacyStringValueIsReadAsFallbackThenMigratedToFloat() {
        val storage = InMemoryKeyValueStorage()
        // Simulate a value persisted by the previous putString-based implementation.
        storage.putString("chart_top_altitude_km", "4.5")
        val store = WebChartViewportStore(storage)

        assertEquals(4.5f, store.readTopAltitudeKm())

        // Writing migrates the key to the typed float slot and clears the legacy string.
        store.writeTopAltitudeKm(6f)
        assertEquals(6f, storage.getFloat("chart_top_altitude_km"))
        assertNull(storage.getString("chart_top_altitude_km"))
        assertEquals(6f, store.readTopAltitudeKm())
    }

    @Test
    fun outOfRangeValuesAreRejected() {
        val storage = InMemoryKeyValueStorage()
        val store = WebChartViewportStore(storage)

        store.writeTopAltitudeKm(1000f)
        assertNull(store.readTopAltitudeKm())

        store.writeTopAltitudeKm(0f)
        assertNull(store.readTopAltitudeKm())
    }
}
