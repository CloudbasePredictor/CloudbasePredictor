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
    fun outOfRangeValuesAreRejected() {
        val storage = InMemoryKeyValueStorage()
        val store = WebChartViewportStore(storage)

        store.writeTopAltitudeKm(1000f)
        assertNull(store.readTopAltitudeKm())

        store.writeTopAltitudeKm(0f)
        assertNull(store.readTopAltitudeKm())
    }
}
