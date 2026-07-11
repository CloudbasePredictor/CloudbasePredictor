package com.cloudbasepredictor.data.forecast

import com.cloudbasepredictor.data.datasource.DataSourcePreference
import com.cloudbasepredictor.data.datasource.DataSourceRepository
import com.cloudbasepredictor.data.datasource.SimulatedForecastDataSource
import com.cloudbasepredictor.data.local.DatabaseErrorManager
import com.cloudbasepredictor.data.remote.HourlyForecastData
import com.cloudbasepredictor.data.remote.OpenMeteoRemoteDataSource
import com.cloudbasepredictor.di.IoDispatcher
import com.cloudbasepredictor.model.DailyForecast
import com.cloudbasepredictor.model.ForecastModel
import com.cloudbasepredictor.model.ForecastSnapshot
import com.cloudbasepredictor.model.SavedPlace
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import timber.log.Timber

interface ForecastRepository {
    fun observeForecast(placeId: String, model: ForecastModel): Flow<ForecastSnapshot?>

    fun isCached(
        placeId: String,
        model: ForecastModel,
        minimumForecastDays: Int = 1,
    ): Boolean

    /** Returns true if the cache already has the full visible forecast horizon. */
    fun isFullyCached(placeId: String, model: ForecastModel): Boolean

    suspend fun loadForecast(
        place: SavedPlace,
        forceRefresh: Boolean = false,
        model: ForecastModel = ForecastModel.BEST_MATCH,
        forecastDays: Int = MAX_FORECAST_DAYS,
    )

    /** Delete cached forecasts older than [cutoffMillis]. */
    suspend fun cleanupOldForecasts(cutoffMillis: Long)

    /** Clear all forecast caches (in-memory and DB). */
    suspend fun clearAllCaches()
}

@SingleIn(AppScope::class)
class InMemoryForecastRepository @Inject constructor(
    private val openMeteoRemoteDataSource: OpenMeteoRemoteDataSource,
    private val dataSourceRepository: DataSourceRepository,
    private val simulatedForecastDataSource: SimulatedForecastDataSource,
    private val forecastCacheStore: ForecastCacheStore,
    private val databaseErrorManager: DatabaseErrorManager,
    private val json: Json,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : ForecastRepository {
    private val cachedForecasts = MutableStateFlow<Map<String, ForecastSnapshot>>(emptyMap())

    override fun observeForecast(placeId: String, model: ForecastModel): Flow<ForecastSnapshot?> {
        return cachedForecasts
            .map { forecasts -> forecasts[cacheKey(placeId, model)] }
            .distinctUntilChanged()
    }

    override fun isCached(
        placeId: String,
        model: ForecastModel,
        minimumForecastDays: Int,
    ): Boolean {
        val snapshot = cachedForecasts.value[cacheKey(placeId, model)] ?: return false
        return isForecastCacheUsable(
            snapshot = snapshot,
            requestedModel = model,
            minimumForecastDays = minimumForecastDays,
            nowMillis = System.currentTimeMillis(),
        )
    }

    override fun isFullyCached(placeId: String, model: ForecastModel): Boolean {
        return isCached(
            placeId = placeId,
            model = model,
            minimumForecastDays = MAX_FORECAST_DAYS,
        )
    }

    override suspend fun loadForecast(
        place: SavedPlace,
        forceRefresh: Boolean,
        model: ForecastModel,
        forecastDays: Int,
    ) = withContext(ioDispatcher) {
        val key = cacheKey(place.id, model)

        // 1. Check in-memory cache
        if (!forceRefresh && cachedForecasts.value.containsKey(key)) {
            val existing = cachedForecasts.value[key]
            if (existing != null && isForecastCacheUsable(
                    snapshot = existing,
                    requestedModel = model,
                    minimumForecastDays = forecastDays,
                    nowMillis = System.currentTimeMillis(),
                )
            ) {
                return@withContext
            }
        }

        // 2. Check DB cache (only for REAL data source)
        if (!forceRefresh && dataSourceRepository.preference.value == DataSourcePreference.REAL) {
            val dbSnapshot = loadFromDbCache(place.id, model, forecastDays)
            if (dbSnapshot != null) {
                cachedForecasts.value = cachedForecasts.value + (key to dbSnapshot)
                return@withContext
            }
        }

        // 3. Fetch from network, assets, or generate fake data
        val snapshot = when (dataSourceRepository.preference.value) {
            DataSourcePreference.FAKE -> {
                val fakeDays = generateFakeDays(forecastDays)
                ForecastSnapshot(
                    days = fakeDays,
                    updatedAtUtcMillis = System.currentTimeMillis(),
                    resolvedModel = model,
                    forecastDays = fakeDays.size,
                )
            }
            DataSourcePreference.SIMULATED -> {
                val hourlyData = simulatedForecastDataSource.loadForecastData()
                val now = System.currentTimeMillis()
                ForecastSnapshot(
                    days = hourlyData.dailyForecasts,
                    updatedAtUtcMillis = now,
                    hourlyData = hourlyData,
                    resolvedModel = ForecastModel.ICON_SEAMLESS,
                    forecastDays = hourlyData.dailyForecasts.size,
                    modelGeneratedAtMillis = now,
                )
            }
            DataSourcePreference.REAL -> {
                val (resolvedModel, hourlyData) = openMeteoRemoteDataSource.getHourlyForecastWithFallback(
                    latitude = place.latitude,
                    longitude = place.longitude,
                    requestedModel = model,
                    forecastDays = forecastDays,
                )
                val now = System.currentTimeMillis()
                val modelGenEstimate = estimateModelRunTime(now, resolvedModel)
                val loadedForecastDays = hourlyData.dailyForecasts.size.coerceAtMost(forecastDays)
                val snapshot = ForecastSnapshot(
                    days = hourlyData.dailyForecasts,
                    updatedAtUtcMillis = now,
                    hourlyData = hourlyData,
                    resolvedModel = resolvedModel,
                    forecastDays = loadedForecastDays,
                    modelGeneratedAtMillis = modelGenEstimate,
                )

                // Store in DB cache
                saveToDbCache(place.id, model, resolvedModel, loadedForecastDays, hourlyData, now)

                snapshot
            }
        }

        cachedForecasts.value = cachedForecasts.value + (key to snapshot)
    }

    override suspend fun cleanupOldForecasts(cutoffMillis: Long) = withContext(ioDispatcher) {
        try {
            val deleted = forecastCacheStore.deleteOlderThan(cutoffMillis)
            if (deleted > 0) {
                Timber.d("Cleaned up %d old cached forecasts", deleted)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e, "Failed to clean up old forecasts")
            handleDbError(e)
        }
    }

    override suspend fun clearAllCaches() = withContext(ioDispatcher) {
        cachedForecasts.value = emptyMap()
        try {
            forecastCacheStore.deleteAll()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e, "Failed to clear forecast cache")
            handleDbError(e)
        }
    }

    private suspend fun loadFromDbCache(
        placeId: String,
        model: ForecastModel,
        minForecastDays: Int,
    ): ForecastSnapshot? {
        return try {
            val entity = forecastCacheStore.read(placeId, model.apiName)
                ?: return null

            val now = System.currentTimeMillis()
            if (entity.forecastDays < minForecastDays) return null

            val hourlyData = json.decodeFromString<HourlyForecastData>(entity.hourlyDataJson)
            val resolvedModel = ForecastModel.fromApiName(entity.resolvedModelApiName) ?: model
            if (now >= entity.nextExpectedUpdateMillis) return null

            val snapshot = ForecastSnapshot(
                days = hourlyData.dailyForecasts,
                updatedAtUtcMillis = entity.fetchedAtMillis,
                hourlyData = hourlyData,
                resolvedModel = resolvedModel,
                forecastDays = entity.forecastDays,
                modelGeneratedAtMillis = estimateModelRunTime(entity.fetchedAtMillis, resolvedModel),
            )
            if (!isForecastCacheUsable(
                    snapshot = snapshot,
                    requestedModel = model,
                    minimumForecastDays = minForecastDays,
                    nowMillis = now,
                )
            ) {
                return null
            }

            snapshot
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e, "Failed to load from DB cache")
            handleDbError(e)
            null
        }
    }

    private suspend fun saveToDbCache(
        placeId: String,
        requestedModel: ForecastModel,
        resolvedModel: ForecastModel,
        forecastDays: Int,
        hourlyData: HourlyForecastData,
        fetchedAtMillis: Long,
    ) {
        try {
            val hourlyJson = json.encodeToString(HourlyForecastData.serializer(), hourlyData)
            val entity = ForecastCacheRecord(
                placeId = placeId,
                requestedModelApiName = requestedModel.apiName,
                resolvedModelApiName = resolvedModel.apiName,
                forecastDays = forecastDays,
                hourlyDataJson = hourlyJson,
                fetchedAtMillis = fetchedAtMillis,
                nextExpectedUpdateMillis = nextExpectedModelUpdateMillis(fetchedAtMillis, resolvedModel),
            )
            forecastCacheStore.upsert(entity)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e, "Failed to save to DB cache")
            handleDbError(e)
        }
    }

    private fun handleDbError(e: Exception) {
        if (isReportableDatabaseError(e)) {
            databaseErrorManager.reportError(e)
        }
    }

    private fun cacheKey(placeId: String, model: ForecastModel): String {
        return "$placeId:${model.apiName}"
    }

    private fun estimateModelRunTime(fetchedAtMillis: Long, model: ForecastModel): Long =
        estimateModelRunTimeInternal(fetchedAtMillis, model)
}

/**
 * Estimate the model run time by rounding down the fetch time to the nearest
 * multiple of the model's update interval.
 */
internal fun estimateModelRunTimeInternal(fetchedAtMillis: Long, model: ForecastModel): Long {
    val interval = model.updateIntervalMillis
    return (fetchedAtMillis / interval) * interval
}

internal fun isReportableDatabaseError(error: Throwable): Boolean {
    if (error is CancellationException) return false
    return error is android.database.sqlite.SQLiteException ||
        error is IllegalStateException
}

private fun generateFakeDays(count: Int): List<DailyForecast> {
    val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    val calendar = Calendar.getInstance(Locale.US)
    return List(count) { index ->
        calendar.timeInMillis = System.currentTimeMillis()
        calendar.add(Calendar.DAY_OF_YEAR, index)
        DailyForecast(
            date = dateFormat.format(calendar.time),
            maxTemperatureCelsius = 18.0 + index,
            minTemperatureCelsius = 9.0 + (index * 0.6),
            weatherCode = if (index % 2 == 0) 1 else 3,
        )
    }
}
