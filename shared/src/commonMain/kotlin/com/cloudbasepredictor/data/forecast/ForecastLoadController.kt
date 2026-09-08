package com.cloudbasepredictor.data.forecast

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex

/** One forecast target's load state. Platform adapters supply fetching, clocks, and expiry. */
class ForecastLoadController<T : Any>(
    private val fetch: suspend (forceRefresh: Boolean) -> T,
    private val refreshAtMillis: (T, Long) -> Long,
    private val nowMillis: () -> Long,
) {
    data class State<T>(val value: T? = null, val isLoading: Boolean = false, val failed: Boolean = false)

    private val mutableState = MutableStateFlow(State<T>())
    val state = mutableState.asStateFlow()
    private val requestMutex = Mutex()
    private val scheduledRefresh = MutableStateFlow<Schedule?>(null)
    private var scheduleGeneration = 0L
    private var failures = 0

    /** A busy controller ignores duplicate requests; cancellation always reaches its caller. */
    suspend fun load(forceRefresh: Boolean = false): Boolean {
        if (!requestMutex.tryLock()) return false
        var nextRefresh: Long? = null
        mutableState.update { it.copy(isLoading = true, failed = false) }
        return try {
            val value = fetch(forceRefresh)
            currentCoroutineContext().ensureActive()
            val now = nowMillis()
            // A stale server response must not create an immediate successful-refresh loop.
            nextRefresh = refreshAtMillis(value, now).let { deadline ->
                if (deadline > now) deadline else now + ForecastRefreshScheduler.retryDelayMillis(1)
            }
            failures = 0
            mutableState.value = State(value = value)
            true
        } catch (exception: CancellationException) {
            throw exception
        } catch (_: Exception) {
            failures++
            mutableState.update { it.copy(isLoading = false, failed = true) }
            if (mutableState.value.value != null) {
                nextRefresh = nowMillis() + ForecastRefreshScheduler.retryDelayMillis(failures)
            }
            false
        } finally {
            mutableState.update { it.copy(isLoading = false) }
            if (nextRefresh == null && mutableState.value.value != null) {
                nextRefresh = nowMillis() + ForecastRefreshScheduler.retryDelayMillis(1)
            }
            val schedule = nextRefresh?.let { Schedule(++scheduleGeneration, it) }
            requestMutex.unlock()
            // Publish only after releasing the request: collectLatest can cancel its old attempt.
            if (schedule != null) {
                scheduledRefresh.update { current ->
                    if (current != null && current.generation > schedule.generation) current else schedule
                }
            }
        }
    }

    /** Run in the platform lifecycle scope; leaving the target cancels delays and active requests. */
    suspend fun runAutoRefresh() {
        scheduledRefresh.filterNotNull().collectLatest { schedule ->
            delay((schedule.deadlineMillis - nowMillis()).coerceAtLeast(0))
            load(forceRefresh = true)
        }
    }

    private data class Schedule(val generation: Long, val deadlineMillis: Long)
}
