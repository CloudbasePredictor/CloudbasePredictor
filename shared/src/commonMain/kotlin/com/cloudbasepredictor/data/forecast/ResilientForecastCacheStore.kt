package com.cloudbasepredictor.data.forecast

import com.cloudbasepredictor.data.storage.StorageUnavailableException

/**
 * Keeps a session mirror of the durable cache and transparently falls back to it after a storage
 * failure. A failed durable backend stays disabled for the remainder of the session.
 */
class ResilientForecastCacheStore(
    private val durable: ForecastCacheStore,
    private val session: ForecastCacheStore = InMemoryForecastCacheStore(),
) : ForecastCacheStore {
    private var durableAvailable = true

    override suspend fun read(
        placeId: String,
        requestedModelApiName: String,
    ): ForecastCacheRecord? {
        if (!durableAvailable) return session.read(placeId, requestedModelApiName)
        return try {
            val record = durable.read(placeId, requestedModelApiName)
            if (record != null) session.upsert(record)
            record
        } catch (_: StorageUnavailableException) {
            durableAvailable = false
            session.read(placeId, requestedModelApiName)
        }
    }

    override suspend fun upsert(record: ForecastCacheRecord) {
        session.upsert(record)
        if (!durableAvailable) return
        try {
            durable.upsert(record)
        } catch (_: StorageUnavailableException) {
            durableAvailable = false
        }
    }

    override suspend fun deleteOlderThan(cutoffMillis: Long): Int {
        val sessionDeleted = session.deleteOlderThan(cutoffMillis)
        if (!durableAvailable) return sessionDeleted
        return try {
            durable.deleteOlderThan(cutoffMillis)
        } catch (_: StorageUnavailableException) {
            durableAvailable = false
            sessionDeleted
        }
    }

    override suspend fun deleteAll() {
        session.deleteAll()
        if (!durableAvailable) return
        try {
            durable.deleteAll()
        } catch (_: StorageUnavailableException) {
            durableAvailable = false
        }
    }
}
