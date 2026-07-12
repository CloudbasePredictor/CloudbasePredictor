package com.cloudbasepredictor.web.forecast

import com.cloudbasepredictor.data.forecast.ForecastCacheRecord
import com.cloudbasepredictor.data.forecast.ForecastCacheStore
import com.cloudbasepredictor.data.remote.HourlyForecastData
import com.cloudbasepredictor.model.ForecastModel
import com.cloudbasepredictor.model.PlaceLocation
import com.cloudbasepredictor.model.SavedPlace
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json

fun interface WebForecastSource {
    suspend fun fetch(
        location: PlaceLocation,
        requestedModel: ForecastModel,
        forecastDays: Int,
    ): Pair<ForecastModel, HourlyForecastData>
}

data class WebForecastResult(
    val hourlyData: HourlyForecastData,
    val resolvedModel: ForecastModel,
    val fetchedAtMillis: Long,
    val fromCache: Boolean,
    val nextExpectedUpdateMillis: Long = Long.MAX_VALUE,
)

/**
 * Browser forecast loader with durable-cache-first semantics.
 *
 * The cache implementation owns IndexedDB failure recovery; this class only decides whether a
 * decoded record is complete and still current for the requested model horizon.
 */
class WebForecastRepository(
    private val source: WebForecastSource,
    private val cacheStore: ForecastCacheStore,
    private val json: Json,
    private val nowMillis: () -> Long,
) {
    fun now(): Long = nowMillis()

    suspend fun load(
        location: PlaceLocation,
        requestedModel: ForecastModel,
        forecastDays: Int,
        forceRefresh: Boolean = false,
    ): WebForecastResult {
        val placeId = location.cachePlaceId()
        if (!forceRefresh) {
            readUsableCache(placeId, requestedModel, forecastDays)?.let { return it }
        }

        val (resolvedModel, hourlyData) = source.fetch(location, requestedModel, forecastDays)
        val fetchedAtMillis = nowMillis()
        cacheStore.upsert(
            ForecastCacheRecord(
                placeId = placeId,
                requestedModelApiName = requestedModel.apiName,
                resolvedModelApiName = resolvedModel.apiName,
                forecastDays = hourlyData.dailyForecasts.size.coerceAtMost(forecastDays),
                hourlyDataJson = json.encodeToString(HourlyForecastData.serializer(), hourlyData),
                fetchedAtMillis = fetchedAtMillis,
                nextExpectedUpdateMillis = nextExpectedModelUpdateMillis(
                    fetchedAtMillis = fetchedAtMillis,
                    model = resolvedModel,
                ),
            ),
        )
        runCatchingPreservingCancellation {
            cacheStore.deleteOlderThan(fetchedAtMillis - CACHE_RETENTION_MILLIS)
        }
        return WebForecastResult(
            hourlyData = hourlyData,
            resolvedModel = resolvedModel,
            fetchedAtMillis = fetchedAtMillis,
            fromCache = false,
            nextExpectedUpdateMillis = nextExpectedModelUpdateMillis(
                fetchedAtMillis = fetchedAtMillis,
                model = resolvedModel,
            ),
        )
    }

    private suspend fun readUsableCache(
        placeId: String,
        requestedModel: ForecastModel,
        minimumForecastDays: Int,
    ): WebForecastResult? {
        val record = cacheStore.read(placeId, requestedModel.apiName) ?: return null
        if (record.forecastDays < minimumForecastDays) return null
        if (nowMillis() >= record.nextExpectedUpdateMillis) return null
        val resolvedModel = ForecastModel.fromApiName(record.resolvedModelApiName) ?: return null
        val hourlyData = runCatching {
            json.decodeFromString(HourlyForecastData.serializer(), record.hourlyDataJson)
        }.getOrNull() ?: return null
        if (hourlyData.dailyForecasts.size < minimumForecastDays) return null
        return WebForecastResult(
            hourlyData = hourlyData,
            resolvedModel = resolvedModel,
            fetchedAtMillis = record.fetchedAtMillis,
            fromCache = true,
            nextExpectedUpdateMillis = record.nextExpectedUpdateMillis,
        )
    }
}

private fun PlaceLocation.cachePlaceId(): String {
    return SavedPlace.fromCoordinates(latitude, longitude).id
}

private fun nextExpectedModelUpdateMillis(
    fetchedAtMillis: Long,
    model: ForecastModel,
): Long {
    val modelRunMillis = (fetchedAtMillis / model.updateIntervalMillis) * model.updateIntervalMillis
    return modelRunMillis + model.updateIntervalMillis
}

private suspend inline fun runCatchingPreservingCancellation(block: suspend () -> Unit) {
    try {
        block()
    } catch (exception: CancellationException) {
        throw exception
    } catch (_: Exception) {
        // Cleanup is best effort and must not hide a successful forecast response.
    }
}

private const val CACHE_RETENTION_MILLIS = 24L * 60L * 60L * 1000L
