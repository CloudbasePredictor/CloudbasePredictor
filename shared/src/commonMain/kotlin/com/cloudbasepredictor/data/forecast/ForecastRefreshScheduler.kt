package com.cloudbasepredictor.data.forecast

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive

/** Shared refresh timing. The caller owns the request and reports whether it succeeded. */
class ForecastRefreshScheduler(private val nowMillis: () -> Long) {
    suspend fun refreshWhenDue(refreshAtMillis: Long, refresh: suspend () -> Boolean) {
        delay((refreshAtMillis - nowMillis()).coerceAtLeast(0))
        var failures = 0
        while (true) {
            currentCoroutineContext().ensureActive()
            if (refresh()) return
            failures++
            delay(retryDelayMillis(failures))
        }
    }

    companion object {
        /** Retry transient failures after 30 seconds, doubling up to five minutes. */
        fun retryDelayMillis(failures: Int): Long =
            (INITIAL_RETRY_MILLIS * (1L shl (failures - 1).coerceIn(0, MAX_BACKOFF_SHIFT)))
                .coerceAtMost(MAX_RETRY_MILLIS)

        private const val INITIAL_RETRY_MILLIS = 30_000L
        private const val MAX_RETRY_MILLIS = 300_000L
        private const val MAX_BACKOFF_SHIFT = 4
    }
}
