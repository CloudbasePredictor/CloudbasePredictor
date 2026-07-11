package com.cloudbasepredictor.data.forecast

import com.cloudbasepredictor.data.storage.StorageUnavailableException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ForecastCacheStoreTest {
    @Test
    fun inMemoryStoreUpsertsByPlaceAndRequestedModel() = runTest {
        val store = InMemoryForecastCacheStore()
        val first = record(placeId = "place-1", model = "icon", fetchedAtMillis = 100)
        val replacement = first.copy(hourlyDataJson = "replacement", fetchedAtMillis = 200)

        store.upsert(first)
        store.upsert(replacement)

        assertEquals(replacement, store.read("place-1", "icon"))
        assertNull(store.read("place-1", "gfs"))
    }

    @Test
    fun inMemoryCleanupUsesStrictlyOlderThanSemantics() = runTest {
        val store = InMemoryForecastCacheStore()
        store.upsert(record(placeId = "old", fetchedAtMillis = 99))
        store.upsert(record(placeId = "boundary", fetchedAtMillis = 100))

        assertEquals(1, store.deleteOlderThan(100))
        assertNull(store.read("old", "icon"))
        assertEquals("boundary", store.read("boundary", "icon")?.placeId)

        store.deleteAll()
        assertNull(store.read("boundary", "icon"))
    }

    @Test
    fun resilientStoreFallsBackToItsSessionMirror() = runTest {
        val durable = FailingForecastCacheStore()
        val store = ResilientForecastCacheStore(durable)
        val record = record(placeId = "fallback")

        store.upsert(record)

        assertEquals(record, store.read("fallback", "icon"))
        assertEquals(1, durable.operationCount)
    }

    @Test
    fun resilientStoreMirrorsDurableReadsBeforeTheBackendFails() = runTest {
        val durable = ToggleableForecastCacheStore()
        val record = record(placeId = "mirrored")
        durable.delegate.upsert(record)
        val store = ResilientForecastCacheStore(durable)

        assertEquals(record, store.read("mirrored", "icon"))
        durable.fails = true

        assertEquals(record, store.read("mirrored", "icon"))
    }
}

private fun record(
    placeId: String,
    model: String = "icon",
    fetchedAtMillis: Long = 1_000,
): ForecastCacheRecord {
    return ForecastCacheRecord(
        placeId = placeId,
        requestedModelApiName = model,
        resolvedModelApiName = model,
        forecastDays = 14,
        hourlyDataJson = "{}",
        fetchedAtMillis = fetchedAtMillis,
        nextExpectedUpdateMillis = fetchedAtMillis + 1_000,
    )
}

private class FailingForecastCacheStore : ForecastCacheStore {
    var operationCount = 0

    private fun unavailable(): Nothing {
        operationCount += 1
        throw StorageUnavailableException("Unavailable")
    }

    override suspend fun read(placeId: String, requestedModelApiName: String): ForecastCacheRecord? = unavailable()
    override suspend fun upsert(record: ForecastCacheRecord) = unavailable()
    override suspend fun deleteOlderThan(cutoffMillis: Long): Int = unavailable()
    override suspend fun deleteAll() = unavailable()
}

private class ToggleableForecastCacheStore : ForecastCacheStore {
    val delegate = InMemoryForecastCacheStore()
    var fails = false

    private fun checkAvailable() {
        if (fails) throw StorageUnavailableException("Unavailable")
    }

    override suspend fun read(placeId: String, requestedModelApiName: String): ForecastCacheRecord? {
        checkAvailable()
        return delegate.read(placeId, requestedModelApiName)
    }

    override suspend fun upsert(record: ForecastCacheRecord) {
        checkAvailable()
        delegate.upsert(record)
    }

    override suspend fun deleteOlderThan(cutoffMillis: Long): Int {
        checkAvailable()
        return delegate.deleteOlderThan(cutoffMillis)
    }

    override suspend fun deleteAll() {
        checkAvailable()
        delegate.deleteAll()
    }
}
