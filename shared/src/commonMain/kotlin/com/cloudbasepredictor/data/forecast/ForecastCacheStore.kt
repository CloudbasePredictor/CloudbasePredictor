package com.cloudbasepredictor.data.forecast

/** Serialized durable forecast cache record shared by Android and web. */
data class ForecastCacheRecord(
    val placeId: String,
    val requestedModelApiName: String,
    val resolvedModelApiName: String,
    val forecastDays: Int,
    val hourlyDataJson: String,
    val fetchedAtMillis: Long,
    val nextExpectedUpdateMillis: Long,
)

interface ForecastCacheStore {
    suspend fun read(
        placeId: String,
        requestedModelApiName: String,
    ): ForecastCacheRecord?

    suspend fun upsert(record: ForecastCacheRecord)

    suspend fun deleteOlderThan(cutoffMillis: Long): Int

    suspend fun deleteAll()
}
