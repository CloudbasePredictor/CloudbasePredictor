package com.cloudbasepredictor.data.forecast

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Session-only forecast cache used by browser fallback paths and deterministic tests. */
class InMemoryForecastCacheStore : ForecastCacheStore {
    private val mutex = Mutex()
    private val records = mutableMapOf<ForecastCacheKey, ForecastCacheRecord>()

    override suspend fun read(
        placeId: String,
        requestedModelApiName: String,
    ): ForecastCacheRecord? = mutex.withLock {
        records[ForecastCacheKey(placeId, requestedModelApiName)]
    }

    override suspend fun upsert(record: ForecastCacheRecord) {
        mutex.withLock {
            records[ForecastCacheKey(record.placeId, record.requestedModelApiName)] = record
        }
    }

    override suspend fun deleteOlderThan(cutoffMillis: Long): Int = mutex.withLock {
        val keysToDelete = records
            .filterValues { record -> record.fetchedAtMillis < cutoffMillis }
            .keys
        keysToDelete.forEach(records::remove)
        keysToDelete.size
    }

    override suspend fun deleteAll() {
        mutex.withLock {
            records.clear()
        }
    }
}

private data class ForecastCacheKey(
    val placeId: String,
    val requestedModelApiName: String,
)
