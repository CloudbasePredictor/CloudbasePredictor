package com.cloudbasepredictor.data.forecast

import com.cloudbasepredictor.di.ApplicationScope
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber

@SingleIn(AppScope::class)
class ForecastCacheMaintenance @Inject constructor(
    private val forecastRepository: ForecastRepository,
    @param:ApplicationScope private val appScope: CoroutineScope,
) {
    fun scheduleStartupCleanup() {
        appScope.launch {
            delay(FORECAST_CACHE_CLEANUP_DELAY_MILLIS)
            val oneDayAgo = System.currentTimeMillis() - FORECAST_CACHE_RETENTION_MILLIS
            try {
                forecastRepository.cleanupOldForecasts(oneDayAgo)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "Forecast cleanup failed")
            }
        }
    }

    private companion object {
        const val FORECAST_CACHE_CLEANUP_DELAY_MILLIS = 10_000L
        const val FORECAST_CACHE_RETENTION_MILLIS = 24L * 60L * 60L * 1000L
    }
}
