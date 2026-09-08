package com.cloudbasepredictor.data.forecast

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class ForecastLoadControllerTest {
    @Test
    fun failedAutomaticRefreshKeepsForecastAndRetriesWithoutUserInput() = runTest {
        var calls = 0
        val controller = ForecastLoadController(
            fetch = { _: Boolean ->
                calls++
                if (calls == 2) error("Offline")
                calls
            },
            refreshAtMillis = { _: Int, now: Long -> now + 1_000 },
            nowMillis = { testScheduler.currentTime },
        )
        controller.load()
        backgroundScope.launch { controller.runAutoRefresh() }
        runCurrent()
        advanceTimeBy(1_000)
        runCurrent()
        assertEquals(1, controller.state.value.value)
        assertTrue(controller.state.value.failed)
        assertFalse(controller.state.value.isLoading)

        advanceTimeBy(30_000)
        runCurrent()
        assertEquals(3, controller.state.value.value)
        assertFalse(controller.state.value.failed)
    }

    @Test
    fun manualSuccessDuringBackoffReplacesOldSchedule() = runTest {
        var calls = 0
        val controller = ForecastLoadController(
            fetch = { _: Boolean ->
                calls++
                if (calls == 2) error("Offline")
                calls
            },
            refreshAtMillis = { value: Int, _: Long -> if (value == 1) 1_000 else 100_000 },
            nowMillis = { testScheduler.currentTime },
        )
        controller.load()
        backgroundScope.launch { controller.runAutoRefresh() }
        advanceTimeBy(1_000)
        runCurrent()
        controller.load(forceRefresh = true)
        runCurrent()
        advanceTimeBy(30_000)
        runCurrent()
        assertEquals(3, calls)
        assertEquals(3, controller.state.value.value)
    }

    @Test
    fun overlappingManualAndAutomaticLoadsDoNotDuplicateFetch() = runTest {
        val release = CompletableDeferred<Int>()
        var calls = 0
        val controller = ForecastLoadController(
            fetch = { _: Boolean -> calls++; release.await() },
            refreshAtMillis = { _: Int, _: Long -> Long.MAX_VALUE },
            nowMillis = { testScheduler.currentTime },
        )
        val first = launch { controller.load() }
        runCurrent()
        assertFalse(controller.load(forceRefresh = true))
        assertEquals(1, calls)
        assertTrue(controller.state.value.isLoading)
        release.complete(7)
        first.join()
        assertEquals(7, controller.state.value.value)
        assertFalse(controller.state.value.isLoading)
    }

    @Test
    fun cancelledLoadDoesNotBecomeAnError() = runTest {
        val controller = ForecastLoadController<Int>(
            fetch = { awaitCancellation() },
            refreshAtMillis = { _, _ -> Long.MAX_VALUE },
            nowMillis = { testScheduler.currentTime },
        )
        val load = launch { controller.load() }
        runCurrent()
        load.cancel()
        load.join()
        assertNull(controller.state.value.value)
        assertFalse(controller.state.value.failed)
        assertFalse(controller.state.value.isLoading)
    }

    @Test
    fun initialFailureCanBeRetriedWithCacheBypassed() = runTest {
        val forceFlags = mutableListOf<Boolean>()
        val controller = ForecastLoadController(
            fetch = { force: Boolean ->
                forceFlags += force
                if (!force) error("Invalid cache")
                7
            },
            refreshAtMillis = { _: Int, _: Long -> Long.MAX_VALUE },
            nowMillis = { testScheduler.currentTime },
        )
        assertFalse(controller.load())
        assertNull(controller.state.value.value)
        assertTrue(controller.state.value.failed)
        assertTrue(controller.load(forceRefresh = true))
        assertEquals(listOf(false, true), forceFlags)
        assertEquals(7, controller.state.value.value)
    }

    @Test
    fun failedManualRefreshRetriesBeforeTheNextModelRun() = runTest {
        var calls = 0
        val controller = ForecastLoadController(
            fetch = { _: Boolean ->
                calls++
                if (calls == 2) error("Offline")
                calls
            },
            refreshAtMillis = { _: Int, _: Long -> 1_000_000 },
            nowMillis = { testScheduler.currentTime },
        )
        controller.load()
        backgroundScope.launch { controller.runAutoRefresh() }
        controller.load(forceRefresh = true)
        runCurrent()
        advanceTimeBy(30_000)
        runCurrent()
        assertEquals(3, controller.state.value.value)
        assertFalse(controller.state.value.failed)
    }

    @Test
    fun schedulerRetriesWithBoundedBackoffAndStopsAfterSuccess() = runTest {
        val attempts = mutableListOf<Long>()
        ForecastRefreshScheduler { testScheduler.currentTime }.refreshWhenDue(1_000) {
            attempts += testScheduler.currentTime
            attempts.size == 7
        }
        assertEquals(listOf(1_000L, 31_000L, 91_000L, 211_000L, 451_000L, 751_000L, 1_051_000L), attempts)
    }

    @Test
    fun repeatedControllerFailuresReachTheRetryCapAndStillRecover() = runTest {
        val attempts = mutableListOf<Long>()
        val controller = ForecastLoadController(
            fetch = { _: Boolean ->
                attempts += testScheduler.currentTime
                if (attempts.size in 2..7) error("Offline")
                attempts.size
            },
            refreshAtMillis = { value: Int, _: Long -> if (value == 1) 1_000 else 9_000_000 },
            nowMillis = { testScheduler.currentTime },
        )
        controller.load()
        backgroundScope.launch { controller.runAutoRefresh() }
        advanceTimeBy(1_051_001)
        runCurrent()
        assertEquals(listOf(0L, 1_000L, 31_000L, 91_000L, 211_000L, 451_000L, 751_000L, 1_051_000L), attempts)
        assertEquals(8, controller.state.value.value)
        assertFalse(controller.state.value.failed)
    }

    @Test
    fun unchangedSuccessfulResultStillSchedulesTheNextRefresh() = runTest {
        var calls = 0
        val controller = ForecastLoadController(
            fetch = { _: Boolean -> calls++; "same forecast" },
            refreshAtMillis = { _: String, now: Long -> now + 1_000 },
            nowMillis = { testScheduler.currentTime },
        )
        controller.load()
        backgroundScope.launch { controller.runAutoRefresh() }
        advanceTimeBy(2_001)
        runCurrent()
        assertEquals(3, calls)
        assertEquals("same forecast", controller.state.value.value)
    }

    @Test
    fun overdueRefreshRunsImmediatelyAndCancellationStopsRetries() = runTest {
        var attempts = 0
        val refresh = launch {
            ForecastRefreshScheduler { testScheduler.currentTime }.refreshWhenDue(-1) {
                attempts++
                false
            }
        }
        runCurrent()
        assertEquals(1, attempts)
        refresh.cancel()
        refresh.join()
        advanceTimeBy(60_000)
        assertEquals(1, attempts)
    }
}
