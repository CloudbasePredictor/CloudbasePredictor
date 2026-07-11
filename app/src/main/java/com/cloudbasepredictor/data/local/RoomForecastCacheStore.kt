package com.cloudbasepredictor.data.local

import com.cloudbasepredictor.data.forecast.ForecastCacheRecord
import com.cloudbasepredictor.data.forecast.ForecastCacheStore

class RoomForecastCacheStore(
    private val dao: ForecastCacheDao,
) : ForecastCacheStore {
    override suspend fun read(
        placeId: String,
        requestedModelApiName: String,
    ): ForecastCacheRecord? {
        return dao.getCachedForecast(placeId, requestedModelApiName)?.toStoreRecord()
    }

    override suspend fun upsert(record: ForecastCacheRecord) {
        dao.upsertForecast(record.toRoomEntity())
    }

    override suspend fun deleteOlderThan(cutoffMillis: Long): Int {
        return dao.deleteOlderThan(cutoffMillis)
    }

    override suspend fun deleteAll() {
        dao.deleteAll()
    }
}

private fun CachedForecastEntity.toStoreRecord(): ForecastCacheRecord {
    return ForecastCacheRecord(
        placeId = placeId,
        requestedModelApiName = modelApiName,
        resolvedModelApiName = resolvedModelApiName,
        forecastDays = forecastDays,
        hourlyDataJson = hourlyDataJson,
        fetchedAtMillis = fetchedAtMillis,
        nextExpectedUpdateMillis = nextExpectedUpdateMillis,
    )
}

private fun ForecastCacheRecord.toRoomEntity(): CachedForecastEntity {
    return CachedForecastEntity(
        placeId = placeId,
        modelApiName = requestedModelApiName,
        resolvedModelApiName = resolvedModelApiName,
        forecastDays = forecastDays,
        hourlyDataJson = hourlyDataJson,
        fetchedAtMillis = fetchedAtMillis,
        nextExpectedUpdateMillis = nextExpectedUpdateMillis,
    )
}
