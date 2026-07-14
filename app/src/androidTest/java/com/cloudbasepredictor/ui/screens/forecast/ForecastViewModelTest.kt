package com.cloudbasepredictor.ui.screens.forecast

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.test.platform.app.InstrumentationRegistry
import com.cloudbasepredictor.data.forecast.ForecastModeRepository
import com.cloudbasepredictor.data.forecast.ForecastModelRepository
import com.cloudbasepredictor.data.forecast.ForecastRepository
import com.cloudbasepredictor.data.forecast.ForecastViewportRepository
import com.cloudbasepredictor.data.map.MapLayerPreference
import com.cloudbasepredictor.data.map.MapLayerRepository
import com.cloudbasepredictor.data.place.PlaceRepository
import com.cloudbasepredictor.data.units.DisplayUnits
import com.cloudbasepredictor.data.units.UnitPreset
import com.cloudbasepredictor.data.units.UnitSettingsRepository
import com.cloudbasepredictor.data.units.resolveDisplayUnits
import com.cloudbasepredictor.model.ForecastMode
import com.cloudbasepredictor.model.ForecastModel
import com.cloudbasepredictor.model.ForecastSnapshot
import com.cloudbasepredictor.model.PlaceLocation
import com.cloudbasepredictor.model.SavedPlace
import com.cloudbasepredictor.ui.text.AppStringResources
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ForecastViewModelTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val viewModelStore = ViewModelStore()
    private var eventCollectionJob: Job? = null

    @After
    fun tearDown() {
        instrumentation.runOnMainSync {
            eventCollectionJob?.cancel()
            viewModelStore.clear()
        }
    }

    @Test
    fun selectingModelWhileLoadingCancelsPreviousRequestAndStartsNewOne() {
        val forecastRepository = BlockingForecastRepository()
        val networkErrorReceived = CountDownLatch(1)
        lateinit var viewModel: ForecastViewModel

        instrumentation.runOnMainSync {
            viewModel = createForecastViewModel(forecastRepository)
            viewModel.setPlaceLocation(PlaceLocation(47.7181, 12.5497))
            eventCollectionJob = CoroutineScope(Dispatchers.Main.immediate).launch {
                viewModel.networkErrorEvent.collect {
                    networkErrorReceived.countDown()
                }
            }
        }

        assertTrue(
            "Initial model request did not start",
            forecastRepository.initialRequestStarted.await(5, TimeUnit.SECONDS),
        )

        instrumentation.runOnMainSync {
            viewModel.selectModel(ForecastModel.ICON_D2)
        }

        assertTrue(
            "Initial model request was not cancelled",
            forecastRepository.initialRequestCancelled.await(5, TimeUnit.SECONDS),
        )
        assertTrue(
            "New model request did not start",
            forecastRepository.nextRequestStarted.await(5, TimeUnit.SECONDS),
        )
        assertEquals(
            listOf(ForecastModel.ICON_SEAMLESS, ForecastModel.ICON_D2),
            forecastRepository.requestedModels.toList(),
        )
        assertFalse(
            "Cancelling the stale model request should not emit a network error",
            networkErrorReceived.await(300, TimeUnit.MILLISECONDS),
        )
    }

    @Test
    fun savingFavoriteWhileLoadingDoesNotRestartSameLocationRequest() {
        val forecastRepository = SameLocationBlockingForecastRepository()
        val placeRepository = FakePlaceRepository()
        lateinit var viewModel: ForecastViewModel

        instrumentation.runOnMainSync {
            viewModel = createForecastViewModel(
                forecastRepository = forecastRepository,
                placeRepository = placeRepository,
            )
            viewModel.setPlaceLocation(PlaceLocation(47.7181, 12.5497))
        }

        assertTrue(
            "Initial forecast request did not start",
            forecastRepository.initialRequestStarted.await(5, TimeUnit.SECONDS),
        )

        instrumentation.runOnMainSync {
            viewModel.saveFavorite("Saved launch")
        }

        assertFalse(
            "Saving a favorite should not restart an in-flight request for the same forecast location",
            forecastRepository.secondRequestStarted.await(300, TimeUnit.MILLISECONDS),
        )
        assertEquals(1, forecastRepository.requestCount.get())
    }

    private fun createForecastViewModel(
        forecastRepository: ForecastRepository,
        placeRepository: PlaceRepository = FakePlaceRepository(SavedPlace.fromCoordinates(47.7181, 12.5497)),
    ): ForecastViewModel {
        val factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: kotlin.reflect.KClass<T>, extras: CreationExtras): T {
                return ForecastViewModel(
                    forecastRepository = forecastRepository,
                    placeRepository = placeRepository,
                    forecastModeRepository = FakeForecastModeRepository(),
                    forecastModelRepository = FakeForecastModelRepository(ForecastModel.ICON_SEAMLESS),
                    forecastViewportRepository = FakeForecastViewportRepository(),
                    mapLayerRepository = FakeMapLayerRepository(),
                    unitSettingsRepository = FakeUnitSettingsRepository(),
                    stringResources = FakeAppStringResources(),
                ) as T
            }
        }

        return ViewModelProvider.create(viewModelStore, factory)[ForecastViewModel::class]
    }
}

private class BlockingForecastRepository : ForecastRepository {
    val initialRequestStarted = CountDownLatch(1)
    val initialRequestCancelled = CountDownLatch(1)
    val nextRequestStarted = CountDownLatch(1)
    val requestedModels = CopyOnWriteArrayList<ForecastModel>()

    override fun observeForecast(placeId: String, model: ForecastModel): Flow<ForecastSnapshot?> {
        return flowOf(null)
    }

    override fun isCached(
        placeId: String,
        model: ForecastModel,
        minimumForecastDays: Int,
    ): Boolean = false

    override fun isFullyCached(placeId: String, model: ForecastModel): Boolean = false

    override suspend fun loadForecast(
        place: SavedPlace,
        forceRefresh: Boolean,
        model: ForecastModel,
        forecastDays: Int,
    ) {
        requestedModels.add(model)
        if (model == ForecastModel.ICON_SEAMLESS) {
            initialRequestStarted.countDown()
            suspendCancellableCoroutine<Unit> { continuation ->
                continuation.invokeOnCancellation {
                    initialRequestCancelled.countDown()
                }
            }
        } else {
            nextRequestStarted.countDown()
        }
    }

    override suspend fun cleanupOldForecasts(cutoffMillis: Long) = Unit

    override suspend fun clearAllCaches() = Unit
}

private class SameLocationBlockingForecastRepository : ForecastRepository {
    val initialRequestStarted = CountDownLatch(1)
    val secondRequestStarted = CountDownLatch(1)
    val requestCount = AtomicInteger(0)

    override fun observeForecast(placeId: String, model: ForecastModel): Flow<ForecastSnapshot?> {
        return flowOf(null)
    }

    override fun isCached(
        placeId: String,
        model: ForecastModel,
        minimumForecastDays: Int,
    ): Boolean = false

    override fun isFullyCached(placeId: String, model: ForecastModel): Boolean = false

    override suspend fun loadForecast(
        place: SavedPlace,
        forceRefresh: Boolean,
        model: ForecastModel,
        forecastDays: Int,
    ) {
        val startedRequestCount = requestCount.incrementAndGet()
        if (startedRequestCount == 1) {
            initialRequestStarted.countDown()
            suspendCancellableCoroutine<Unit> { }
        } else {
            secondRequestStarted.countDown()
        }
    }

    override suspend fun cleanupOldForecasts(cutoffMillis: Long) = Unit

    override suspend fun clearAllCaches() = Unit
}

private class FakePlaceRepository(initialPlace: SavedPlace = SavedPlace.fromCoordinates(47.7181, 12.5497)) : PlaceRepository {
    private val mutableSelectedPlace = MutableStateFlow<SavedPlace?>(initialPlace)
    private val mutableFavoritePlaces = MutableStateFlow<List<SavedPlace>>(emptyList())

    override val selectedPlace: StateFlow<SavedPlace?> = mutableSelectedPlace.asStateFlow()

    override fun observeSavedPlaces(): Flow<List<SavedPlace>> = flowOf(emptyList())

    override fun observeFavoritePlaces(): Flow<List<SavedPlace>> = mutableFavoritePlaces.asStateFlow()

    override suspend fun saveAndSelectPlace(place: SavedPlace) {
        mutableSelectedPlace.value = place
    }

    override suspend fun saveFavorite(placeId: String, name: String) = Unit

    override suspend fun saveFavoritePlace(place: SavedPlace) {
        mutableFavoritePlaces.value = listOf(place)
        if (mutableSelectedPlace.value?.id == place.id) {
            mutableSelectedPlace.value = place
        }
    }

    override suspend fun deleteFavorite(placeId: String) = Unit

    override suspend fun selectPlace(place: SavedPlace) {
        mutableSelectedPlace.value = place
    }
}

private class FakeForecastModeRepository : ForecastModeRepository {
    private val mutableSelectedMode = MutableStateFlow(ForecastMode.THERMIC)

    override val selectedMode: StateFlow<ForecastMode> = mutableSelectedMode.asStateFlow()

    override fun selectMode(mode: ForecastMode) {
        mutableSelectedMode.value = mode
    }
}

private class FakeForecastModelRepository(initialModel: ForecastModel) : ForecastModelRepository {
    private val mutableSelectedModel = MutableStateFlow(initialModel)

    override val selectedModel: StateFlow<ForecastModel> = mutableSelectedModel.asStateFlow()

    override fun selectModel(model: ForecastModel) {
        mutableSelectedModel.value = model
    }
}

private class FakeForecastViewportRepository : ForecastViewportRepository {
    private val mutableVisibleTopAltitudeKm = MutableStateFlow(DEFAULT_TOP_ALTITUDE_KM)

    override val visibleTopAltitudeKm: StateFlow<Float> = mutableVisibleTopAltitudeKm.asStateFlow()

    override fun setVisibleTopAltitudeKm(value: Float) {
        mutableVisibleTopAltitudeKm.value = value
    }
}

private class FakeMapLayerRepository : MapLayerRepository {
    private val mutableSelectedLayer = MutableStateFlow(MapLayerPreference.OPENFREEMAP)

    override val selectedLayer: StateFlow<MapLayerPreference> = mutableSelectedLayer.asStateFlow()

    override fun selectLayer(layer: MapLayerPreference) {
        mutableSelectedLayer.value = layer
    }
}

private class FakeAppStringResources : AppStringResources {
    override fun getString(resId: Int): String = "res:$resId"

    override fun getString(resId: Int, vararg formatArgs: Any): String =
        "res:$resId${formatArgs.joinToString(prefix = "(", postfix = ")")}"
}

private class FakeUnitSettingsRepository : UnitSettingsRepository {
    private val mutableUnitPreset = MutableStateFlow(UnitPreset.METRIC_KMH)
    private val mutableDisplayUnits = MutableStateFlow(
        UnitPreset.METRIC_KMH.resolveDisplayUnits(),
    )

    override val unitPreset: StateFlow<UnitPreset> = mutableUnitPreset.asStateFlow()
    override val displayUnits: StateFlow<DisplayUnits> = mutableDisplayUnits.asStateFlow()

    override fun setUnitPreset(unitPreset: UnitPreset) {
        mutableUnitPreset.value = unitPreset
        mutableDisplayUnits.value = unitPreset.resolveDisplayUnits()
    }
}
