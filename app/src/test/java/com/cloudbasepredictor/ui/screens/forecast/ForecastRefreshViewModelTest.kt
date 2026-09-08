package com.cloudbasepredictor.ui.screens.forecast

import androidx.lifecycle.ViewModelStore
import com.cloudbasepredictor.data.forecast.ForecastModelRepository
import com.cloudbasepredictor.data.forecast.ForecastRepository
import com.cloudbasepredictor.data.forecast.ForecastViewportRepository
import com.cloudbasepredictor.data.forecast.InMemoryForecastModeRepository
import com.cloudbasepredictor.data.map.MapLayerPreference
import com.cloudbasepredictor.data.map.MapLayerRepository
import com.cloudbasepredictor.data.place.PlaceRepository
import com.cloudbasepredictor.data.remote.HourlyForecastData
import com.cloudbasepredictor.data.remote.HourlyPoint
import com.cloudbasepredictor.data.remote.PressureLevelPoint
import com.cloudbasepredictor.data.units.UnitPreset
import com.cloudbasepredictor.data.units.UnitSettingsRepository
import com.cloudbasepredictor.data.units.resolveDisplayUnits
import com.cloudbasepredictor.model.DailyForecast
import com.cloudbasepredictor.model.ForecastModel
import com.cloudbasepredictor.model.ForecastSnapshot
import com.cloudbasepredictor.model.PlaceLocation
import com.cloudbasepredictor.model.SavedPlace
import com.cloudbasepredictor.ui.text.AppStringResources
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ForecastRefreshViewModelTest {
    private val viewModelStore = ViewModelStore()

    @Before
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @After
    fun tearDown() {
        viewModelStore.clear()
        Dispatchers.resetMain()
    }

    @Test
    fun failedManualRefreshKeepsFreshForecastAndAutomaticallyRetriesWithCacheBypassed() = runTest {
        val repository = RefreshRepository()
        val viewModel = createViewModel(repository)
        backgroundScope.launch { viewModel.uiState.collect {} }
        val errors = mutableListOf<String>()
        backgroundScope.launch { viewModel.networkErrorEvent.collect { errors += it } }
        viewModel.setPlaceLocation(LOCATION)
        runCurrent()
        assertTrue(viewModel.uiState.value is ForecastReadyUiState)
        assertTrue(repository.forceRefreshRequests.isEmpty())
        val originalChart = (viewModel.uiState.value as ForecastReadyUiState).stuveChart

        viewModel.retryLoad()
        runCurrent()

        assertEquals(listOf(true), repository.forceRefreshRequests)
        assertEquals(listOf("Offline"), errors)
        assertTrue(viewModel.uiState.value is ForecastReadyUiState)
        val retainedState = viewModel.uiState.value as ForecastReadyUiState
        assertEquals(originalChart, retainedState.stuveChart)
        assertFalse(retainedState.isRefreshing)

        // The production deadline uses wall time; allow small differences between its two reads.
        advanceTimeBy(29_000)
        runCurrent()
        assertEquals(listOf(true), repository.forceRefreshRequests)
        advanceTimeBy(2_000)
        runCurrent()

        assertEquals(listOf(true, true), repository.forceRefreshRequests)
        assertEquals(1, repository.successfulRefreshes)
        assertTrue(viewModel.uiState.value is ForecastReadyUiState)
        assertFalse((viewModel.uiState.value as ForecastReadyUiState).isRefreshing)
        advanceTimeBy(120_000)
        runCurrent()
        assertEquals("Success should restore the future model deadline", 2, repository.forceRefreshRequests.size)
    }

    @Test
    fun clearingViewModelCancelsPendingRetryAfterManualFailure() = runTest {
        val repository = RefreshRepository()
        val viewModel = createViewModel(repository)
        backgroundScope.launch { viewModel.uiState.collect {} }
        viewModel.setPlaceLocation(LOCATION)
        runCurrent()
        viewModel.retryLoad()
        runCurrent()
        assertEquals(listOf(true), repository.forceRefreshRequests)

        viewModelStore.clear()
        runCurrent()
        advanceTimeBy(120_000)
        runCurrent()

        assertEquals(listOf(true), repository.forceRefreshRequests)
        assertEquals(0, repository.successfulRefreshes)
    }

    private fun createViewModel(repository: ForecastRepository): ForecastViewModel = ForecastViewModel(
        forecastRepository = repository,
        placeRepository = object : PlaceRepository {
            override val selectedPlace = MutableStateFlow<SavedPlace?>(null)
            override fun observeSavedPlaces() = flowOf(emptyList<SavedPlace>())
            override fun observeFavoritePlaces() = flowOf(emptyList<SavedPlace>())
            override suspend fun saveAndSelectPlace(place: SavedPlace) { selectedPlace.value = place }
            override suspend fun saveFavorite(placeId: String, name: String) = Unit
            override suspend fun saveFavoritePlace(place: SavedPlace) = Unit
            override suspend fun deleteFavorite(placeId: String) = Unit
            override suspend fun selectPlace(place: SavedPlace) { selectedPlace.value = place }
        },
        forecastModeRepository = InMemoryForecastModeRepository(),
        forecastModelRepository = object : ForecastModelRepository {
            override val selectedModel = MutableStateFlow(MODEL)
            override fun selectModel(model: ForecastModel) { selectedModel.value = model }
        },
        forecastViewportRepository = object : ForecastViewportRepository {
            override val visibleTopAltitudeKm = MutableStateFlow(4f)
            override fun setVisibleTopAltitudeKm(value: Float) { visibleTopAltitudeKm.value = value }
        },
        mapLayerRepository = object : MapLayerRepository {
            override val selectedLayer = MutableStateFlow(MapLayerPreference.OPENFREEMAP)
            override fun selectLayer(layer: MapLayerPreference) { selectedLayer.value = layer }
        },
        unitSettingsRepository = object : UnitSettingsRepository {
            override val unitPreset = MutableStateFlow(UnitPreset.METRIC_KMH)
            override val displayUnits = MutableStateFlow(UnitPreset.METRIC_KMH.resolveDisplayUnits())
            override fun setUnitPreset(unitPreset: UnitPreset) {
                this.unitPreset.value = unitPreset
                displayUnits.value = unitPreset.resolveDisplayUnits()
            }
        },
        stringResources = object : AppStringResources {
            override fun getString(resId: Int) = "res:$resId"
            override fun getString(resId: Int, vararg formatArgs: Any) = "res:$resId ${formatArgs.joinToString()}"
        },
    ).also { viewModelStore.put("forecast", it) }

    private class RefreshRepository : ForecastRepository {
        private val snapshots = MutableStateFlow<ForecastSnapshot?>(freshSnapshot())
        val forceRefreshRequests = mutableListOf<Boolean>()
        var successfulRefreshes = 0
            private set

        override fun observeForecast(placeId: String, model: ForecastModel) = snapshots
        // The cached window stays usable throughout the test, including after the failed refresh.
        override fun isCached(placeId: String, model: ForecastModel, minimumForecastDays: Int) = true
        override fun isFullyCached(placeId: String, model: ForecastModel) = true

        override suspend fun loadForecast(
            place: SavedPlace,
            forceRefresh: Boolean,
            model: ForecastModel,
            forecastDays: Int,
        ) {
            forceRefreshRequests += forceRefresh
            if (forceRefreshRequests.size == 1) error("Offline")
            successfulRefreshes++
            snapshots.value = requireNotNull(snapshots.value).let { snapshot ->
                snapshot.copy(updatedAtUtcMillis = snapshot.updatedAtUtcMillis + 1)
            }
        }

        override suspend fun cleanupOldForecasts(cutoffMillis: Long) = Unit
        override suspend fun clearAllCaches() { snapshots.value = null }
    }

    private companion object {
        val MODEL = ForecastModel.ICON_SEAMLESS
        val LOCATION = PlaceLocation(46.5582, 7.8354, "Interlaken")

        fun freshSnapshot(): ForecastSnapshot {
            val today = LocalDate.now(ZoneOffset.UTC)
            val days = (0L..5L).map { offset ->
                DailyForecast(today.plusDays(offset).toString(), 22.0, 11.0, 1)
            }
            val data = HourlyForecastData(
                latitude = LOCATION.latitude,
                longitude = LOCATION.longitude,
                elevation = 580.0,
                timezone = "UTC",
                dailyForecasts = days,
                hourlyPoints = days.map { day ->
                    HourlyPoint(
                        date = day.date,
                        hour = 12,
                        temperature2mC = 22.0,
                        dewPoint2mC = 11.0,
                        surfacePressureHpa = 950.0,
                        windSpeed10mKmh = 12.0,
                        windDirection10mDeg = 240.0,
                        cloudCoverLowPercent = 20.0,
                        cloudCoverMidPercent = 30.0,
                        cloudCoverHighPercent = 10.0,
                        precipitationMm = 0.0,
                        precipitationProbabilityPercent = 5.0,
                        capeJKg = 250.0,
                        freezingLevelHeightM = 2_800.0,
                        shortwaveRadiationWm2 = 550.0,
                        isDay = 1.0,
                        pressureLevels = listOf(
                            PressureLevelPoint(
                                pressureHpa = 850,
                                temperatureC = 9.0,
                                dewPointC = 4.0,
                                geopotentialHeightM = 1_450.0,
                                windSpeedKmh = 20.0,
                                windDirectionDeg = 250.0,
                            ),
                        ),
                    )
                },
            )
            val now = System.currentTimeMillis()
            return ForecastSnapshot(days, now, data, MODEL, forecastDays = days.size, modelGeneratedAtMillis = now)
        }
    }
}
