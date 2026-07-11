package com.cloudbasepredictor.ui.screens.forecast

import com.cloudbasepredictor.data.forecast.ForecastModeRepository
import com.cloudbasepredictor.data.forecast.ForecastModelRepository
import com.cloudbasepredictor.data.forecast.MAX_FORECAST_DAYS
import com.cloudbasepredictor.data.forecast.ForecastViewportRepository
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cloudbasepredictor.data.forecast.ForecastRepository
import com.cloudbasepredictor.data.forecast.exposedForecastDayCount
import com.cloudbasepredictor.data.forecast.nextForecastCacheRefreshMillis
import com.cloudbasepredictor.data.forecast.requestedForecastDaysForDayIndex
import com.cloudbasepredictor.data.map.MapLayerPreference
import com.cloudbasepredictor.data.map.MapLayerRepository
import com.cloudbasepredictor.data.place.PlaceRepository
import com.cloudbasepredictor.data.units.DisplayUnits
import com.cloudbasepredictor.data.units.UnitPreset
import com.cloudbasepredictor.data.units.UnitSettingsRepository
import com.cloudbasepredictor.model.DailyForecast
import com.cloudbasepredictor.model.ForecastMode
import com.cloudbasepredictor.model.ForecastModel
import com.cloudbasepredictor.model.ForecastSnapshot
import com.cloudbasepredictor.model.PlaceLocation
import com.cloudbasepredictor.model.SavedPlace
import com.cloudbasepredictor.model.WeatherCode
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber

@OptIn(ExperimentalCoroutinesApi::class)
@ContributesIntoMap(AppScope::class)
@ViewModelKey
class ForecastViewModel @Inject constructor(
    private val forecastRepository: ForecastRepository,
    private val placeRepository: PlaceRepository,
    private val forecastModeRepository: ForecastModeRepository,
    private val forecastModelRepository: ForecastModelRepository,
    private val forecastViewportRepository: ForecastViewportRepository,
    private val mapLayerRepository: MapLayerRepository,
    private val unitSettingsRepository: UnitSettingsRepository,
) : ViewModel() {
    private val selectedDayIndex = MutableStateFlow(0)
    private val chartViewport = MutableStateFlow(
        ForecastChartViewport(
            visibleTopAltitudeKm = forecastViewportRepository.visibleTopAltitudeKm.value,
        ),
    )
    private val stuveHour = MutableStateFlow(12)
    private val isLoading = MutableStateFlow(false)
    private val errorMessage = MutableStateFlow<String?>(null)
    private var forecastLoadJob: Job? = null
    private var forecastLoadGeneration = 0
    private val placeLocation = MutableStateFlow<PlaceLocation?>(null)

    private val _networkErrorEvent = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val networkErrorEvent: SharedFlow<String> = _networkErrorEvent

    private val forecastPlace: StateFlow<SavedPlace?> = placeLocation
        .map { location -> location?.toSavedPlace() }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = null,
        )

    private val favoritePlaces = placeRepository.observeFavoritePlaces().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList(),
    )

    private val selectedPlace: StateFlow<SavedPlace?> = combine(
        placeLocation,
        favoritePlaces,
    ) { location, favorites ->
        location?.resolveForecastPlace(favorites)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = null,
    )

    private val forecastTarget = combine(
        forecastPlace,
        forecastModelRepository.selectedModel,
    ) { place, model ->
        ForecastLoadTarget(
            place = place,
            model = model,
        )
    }.distinctUntilChanged { previous, current ->
        previous.place?.id == current.place?.id && previous.model == current.model
    }

    private val selectedForecast = forecastTarget.flatMapLatest { target ->
        val place = target.place
        if (place == null) {
            flowOf(null)
        } else {
            forecastRepository.observeForecast(place.id, target.model)
        }
    }

    private val forecastRefreshTarget = forecastTarget.flatMapLatest { target ->
        val place = target.place
        if (place == null) {
            flowOf(ForecastRefreshTarget(target, snapshot = null))
        } else {
            forecastRepository.observeForecast(place.id, target.model)
                .map { snapshot -> ForecastRefreshTarget(target, snapshot) }
        }
    }

    private val selectedModeWithDayIndex = combine(
        forecastModeRepository.selectedMode,
        selectedDayIndex,
    ) { mode, dayIndex ->
        mode to dayIndex
    }

    private val chartContext = combine(
        selectedModeWithDayIndex,
        chartViewport,
        stuveHour,
    ) { selectedModeAndDayIndex, currentChartViewport, currentStuveHour ->
        ForecastChartContext(
            selectedForecastMode = selectedModeAndDayIndex.first,
            selectedDayIndex = selectedModeAndDayIndex.second,
            chartViewport = currentChartViewport,
            stuveHour = currentStuveHour,
        )
    }

    private val mapAndUnitPreferences = combine(
        mapLayerRepository.selectedLayer,
        unitSettingsRepository.unitPreset,
        unitSettingsRepository.displayUnits,
    ) { mapLayer, unitPreset, displayUnits ->
        MapAndUnitPreferences(
            mapLayer = mapLayer,
            unitPreset = unitPreset,
            displayUnits = displayUnits,
        )
    }

    private val uiInputs = combine(
        selectedPlace,
        selectedForecast,
        chartContext,
        isLoading,
        errorMessage,
    ) { place, snapshot, currentChartContext, loading, currentError ->
        ForecastUiInputs(
            place = place,
            snapshot = snapshot,
            chartContext = currentChartContext,
            isLoading = loading,
            errorMessage = currentError,
        )
    }

    val uiState: StateFlow<ForecastUiState> = combine(
        uiInputs,
        forecastModelRepository.selectedModel,
        favoritePlaces,
        mapAndUnitPreferences,
    ) { inputs, currentModel, favorites, preferences ->
        val place = inputs.place
        val snapshot = inputs.snapshot
        val currentChartContext = inputs.chartContext
        val loading = inputs.isLoading
        val currentError = inputs.errorMessage

        if (place == null) {
            return@combine ForecastNoPlaceUiState(
                selectedForecastMode = currentChartContext.selectedForecastMode,
                selectedDayIndex = currentChartContext.selectedDayIndex.coerceAtLeast(0),
                selectedModel = currentModel,
                favoritePlaces = favorites,
                mapLayer = preferences.mapLayer,
                unitPreset = preferences.unitPreset,
                displayUnits = preferences.displayUnits,
            )
        }

        // Only fall back to the full-screen error when there is nothing to display.
        // A failed background refresh (or extra-day load) while a usable forecast is
        // already cached must not wipe the chart; the user is notified via the
        // transient networkErrorEvent toast instead.
        if (currentError != null && snapshot?.hourlyData == null) {
            return@combine ForecastErrorUiState(
                errorMessage = currentError,
                selectedPlace = place,
                selectedForecastMode = currentChartContext.selectedForecastMode,
                selectedDayIndex = currentChartContext.selectedDayIndex.coerceAtLeast(0),
                selectedModel = currentModel,
                resolvedModel = snapshot?.resolvedModel,
                favoritePlaces = favorites,
                mapLayer = preferences.mapLayer,
                unitPreset = preferences.unitPreset,
                displayUnits = preferences.displayUnits,
            )
        }

        if (loading || snapshot == null) {
            return@combine ForecastLoadingUiState(
                selectedPlace = place,
                selectedForecastMode = currentChartContext.selectedForecastMode,
                selectedDayIndex = currentChartContext.selectedDayIndex.coerceAtLeast(0),
                selectedModel = currentModel,
                resolvedModel = snapshot?.resolvedModel,
                favoritePlaces = favorites,
                mapLayer = preferences.mapLayer,
                unitPreset = preferences.unitPreset,
                displayUnits = preferences.displayUnits,
            )
        }

        val hourlyData = snapshot.hourlyData
        if (hourlyData == null) {
            return@combine ForecastErrorUiState(
                errorMessage = INCOMPLETE_FORECAST_DATA_ERROR,
                selectedPlace = place,
                selectedForecastMode = currentChartContext.selectedForecastMode,
                selectedDayIndex = currentChartContext.selectedDayIndex.coerceAtLeast(0),
                selectedModel = currentModel,
                resolvedModel = snapshot.resolvedModel,
                favoritePlaces = favorites,
                mapLayer = preferences.mapLayer,
                unitPreset = preferences.unitPreset,
                displayUnits = preferences.displayUnits,
            )
        }

        val loadedForecastDays = snapshot.days.size
        val availableForecastDays = (snapshot.resolvedModel ?: currentModel).visibleForecastDays()
        val displayedForecastDays = exposedForecastDayCount(
            loadedForecastDays = loadedForecastDays,
            selectedDayIndex = currentChartContext.selectedDayIndex,
            maxForecastDays = availableForecastDays,
        )
        val dayChips = buildDisplayedDayChips(
            loadedDays = snapshot.days,
            displayedDayCount = displayedForecastDays,
        )
        val safeDayIndex = currentChartContext.selectedDayIndex.coerceIn(0, dayChips.lastIndex)

        if (!hourlyData.hasRequiredForecastInputs(
                dayIndex = safeDayIndex,
                stuveHour = currentChartContext.stuveHour,
            )
        ) {
            return@combine ForecastErrorUiState(
                errorMessage = INCOMPLETE_FORECAST_DATA_ERROR,
                selectedPlace = place,
                selectedForecastMode = currentChartContext.selectedForecastMode,
                selectedDayIndex = safeDayIndex,
                selectedModel = currentModel,
                resolvedModel = snapshot.resolvedModel,
                favoritePlaces = favorites,
                mapLayer = preferences.mapLayer,
                unitPreset = preferences.unitPreset,
                displayUnits = preferences.displayUnits,
            )
        }

        buildForecastReadyUiState(
            ForecastRenderInput(
                hourlyData = hourlyData,
                place = place,
                requestedModel = currentModel,
                resolvedModel = snapshot.resolvedModel,
                selectedForecastMode = currentChartContext.selectedForecastMode,
                selectedDayIndex = safeDayIndex,
                stuveHour = currentChartContext.stuveHour,
                chartViewport = currentChartContext.chartViewport,
                unitPreset = preferences.unitPreset,
                displayUnits = preferences.displayUnits,
                fetchedAtMillis = snapshot.updatedAtUtcMillis,
                modelGeneratedAtMillis = snapshot.modelGeneratedAtMillis,
                favoritePlaces = favorites,
                mapLayer = preferences.mapLayer,
                dayChips = dayChips,
                forecastText = buildForecastText(
                    mode = currentChartContext.selectedForecastMode,
                    place = place,
                    snapshot = snapshot,
                    selectedDayIndex = safeDayIndex,
                ),
            ),
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ForecastLoadingUiState(),
    )

    init {
        viewModelScope.launch {
            forecastTarget
                .collect { target ->
                    val place = target.place
                    val model = target.model
                    errorMessage.value = null

                    if (place == null) {
                        cancelForecastLoad()
                        return@collect
                    }

                    val requiredForecastDays = requestedForecastDaysForDayIndex(
                        dayIndex = selectedDayIndex.value,
                        maxForecastDays = model.visibleForecastDays(),
                    )
                    if (forecastRepository.isCached(
                            placeId = place.id,
                            model = model,
                            minimumForecastDays = requiredForecastDays,
                        )
                    ) {
                        cancelForecastLoad()
                        return@collect
                    }

                    startForecastLoad(
                        place = place,
                        model = model,
                        forecastDays = requiredForecastDays,
                    )
                }
        }

        viewModelScope.launch {
            forecastRefreshTarget.collectLatest { refreshTarget ->
                scheduleRefreshForSnapshot(refreshTarget)
            }
        }
    }

    fun setPlaceLocation(location: PlaceLocation) {
        placeLocation.value = location
    }

    fun selectDay(index: Int) {
        // Ignore re-taps on the already-selected day so we don't cancel and restart an
        // in-flight load (the day picker stays interactive during loading).
        if (index != selectedDayIndex.value) {
            selectedDayIndex.value = index
            forecastPlace.value?.let { place ->
                loadForecastForSelectedDay(place = place, dayIndex = index)
            }
        }
    }

    private fun loadForecastForSelectedDay(place: SavedPlace, dayIndex: Int) {
        val model = forecastModelRepository.selectedModel.value
        val requiredForecastDays = requestedForecastDaysForDayIndex(
            dayIndex = dayIndex,
            maxForecastDays = (uiState.value.resolvedModel ?: model).visibleForecastDays(),
        )
        if (forecastRepository.isCached(
                placeId = place.id,
                model = model,
                minimumForecastDays = requiredForecastDays,
            )
        ) {
            cancelForecastLoad()
        } else {
            errorMessage.value = null
            startForecastLoad(
                place = place,
                model = model,
                forecastDays = requiredForecastDays,
            )
        }
    }

    fun selectForecastMode(mode: ForecastMode) {
        forecastModeRepository.selectMode(mode)
    }

    fun updateChartTopAltitude(topAltitudeKm: Float) {
        chartViewport.update { currentViewport ->
            currentViewport.withVisibleTopAltitudeKm(topAltitudeKm)
        }
        forecastViewportRepository.setVisibleTopAltitudeKm(topAltitudeKm)
    }

    fun updateForecastLocation(latitude: Double, longitude: Double) {
        setPlaceLocation(PlaceLocation(latitude = latitude, longitude = longitude))
    }

    fun selectFavoritePlace(place: SavedPlace) {
        setPlaceLocation(PlaceLocation.fromSavedPlace(place))
    }

    fun saveFavorite(name: String) {
        val place = selectedPlace.value ?: return
        viewModelScope.launch {
            placeRepository.saveFavoritePlace(
                place.copy(
                    name = name,
                    isFavorite = true,
                ),
            )
        }
    }

    fun deleteFavorite() {
        val place = selectedPlace.value ?: return
        viewModelScope.launch {
            placeRepository.deleteFavorite(place.id)
        }
    }

    fun updateStuveHour(hour: Int) {
        stuveHour.value = hour.coerceIn(6, 22)
    }

    fun selectModel(model: ForecastModel) {
        forecastModelRepository.selectModel(model)
    }

    fun retryLoad() {
        val place = forecastPlace.value ?: return
        val model = forecastModelRepository.selectedModel.value
        val requiredForecastDays = requestedForecastDaysForDayIndex(
            dayIndex = selectedDayIndex.value,
            maxForecastDays = (uiState.value.resolvedModel ?: model).visibleForecastDays(),
        )
        errorMessage.value = null
        startForecastLoad(
            place = place,
            model = model,
            forecastDays = requiredForecastDays,
            forceRefresh = true,
        )
    }

    private fun startForecastLoad(
        place: SavedPlace,
        model: ForecastModel,
        forecastDays: Int,
        forceRefresh: Boolean = false,
    ) {
        val generation = ++forecastLoadGeneration
        forecastLoadJob?.cancel()
        isLoading.value = true
        forecastLoadJob = viewModelScope.launch {
            Timber.i(
                "Loading forecast: model=%s days=%d forceRefresh=%b",
                model.apiName,
                forecastDays,
                forceRefresh,
            )
            try {
                loadForecastWindow(
                    place = place,
                    model = model,
                    forecastDays = forecastDays,
                    forceRefresh = forceRefresh,
                )
                Timber.i(
                    "Forecast load finished: model=%s days=%d",
                    model.apiName,
                    forecastDays,
                )
            } catch (throwable: CancellationException) {
                Timber.i("Forecast load cancelled: model=%s", model.apiName)
                throw throwable
            } finally {
                if (generation == forecastLoadGeneration) {
                    isLoading.value = false
                }
            }
        }
    }

    private fun cancelForecastLoad() {
        forecastLoadGeneration++
        forecastLoadJob?.cancel()
        forecastLoadJob = null
        isLoading.value = false
    }

    private suspend fun loadForecastWindow(
        place: SavedPlace,
        model: ForecastModel,
        forecastDays: Int,
        forceRefresh: Boolean = false,
    ) {
        runCatching {
            forecastRepository.loadForecast(
                place = place,
                forceRefresh = forceRefresh,
                model = model,
                forecastDays = forecastDays,
            )
        }.onFailure { throwable ->
            if (throwable is CancellationException) {
                throw throwable
            }
            val msg = throwable.message ?: "Unable to load forecast right now."
            Timber.e(
                throwable,
                "Forecast load failed: model=%s days=%d forceRefresh=%b",
                model.apiName,
                forecastDays,
                forceRefresh,
            )
            errorMessage.value = msg
            _networkErrorEvent.tryEmit(msg)
        }
    }

    private suspend fun scheduleRefreshForSnapshot(refreshTarget: ForecastRefreshTarget) {
        val target = refreshTarget.target
        val place = target.place ?: return
        val snapshot = refreshTarget.snapshot ?: return
        val refreshAtMillis = nextForecastCacheRefreshMillis(
            snapshot = snapshot,
            requestedModel = target.model,
            nowMillis = System.currentTimeMillis(),
        )
        val refreshDelayMillis = (refreshAtMillis - System.currentTimeMillis()).coerceAtLeast(0L)
        if (refreshDelayMillis > 0L) {
            delay(refreshDelayMillis)
        }
        if (isLoading.value) return

        val requiredForecastDays = requestedForecastDaysForDayIndex(
            dayIndex = selectedDayIndex.value,
            maxForecastDays = (uiState.value.resolvedModel ?: target.model).visibleForecastDays(),
        )
        if (forecastRepository.isCached(
                placeId = place.id,
                model = target.model,
                minimumForecastDays = requiredForecastDays,
            )
        ) {
            return
        }

        errorMessage.value = null
        startForecastLoad(
            place = place,
            model = target.model,
            forecastDays = requiredForecastDays,
        )
    }
}

private data class ForecastChartContext(
    val selectedForecastMode: ForecastMode,
    val selectedDayIndex: Int,
    val chartViewport: ForecastChartViewport,
    val stuveHour: Int = 12,
)

private data class ForecastUiInputs(
    val place: SavedPlace?,
    val snapshot: ForecastSnapshot?,
    val chartContext: ForecastChartContext,
    val isLoading: Boolean,
    val errorMessage: String?,
)

private data class MapAndUnitPreferences(
    val mapLayer: MapLayerPreference,
    val unitPreset: UnitPreset,
    val displayUnits: DisplayUnits,
)

private data class ForecastLoadTarget(
    val place: SavedPlace?,
    val model: ForecastModel,
)

private data class ForecastRefreshTarget(
    val target: ForecastLoadTarget,
    val snapshot: ForecastSnapshot?,
)

private const val INCOMPLETE_FORECAST_DATA_ERROR = "Forecast data is incomplete."

internal fun PlaceLocation.resolveForecastPlace(favoritePlaces: List<SavedPlace>): SavedPlace {
    val routePlace = toSavedPlace()
    return favoritePlaces.firstOrNull { favorite ->
        favorite.id == routePlace.id
    } ?: favoritePlaces.find { favorite ->
        favorite.isNearby(latitude, longitude)
    } ?: routePlace
}

private fun buildForecastText(
    mode: ForecastMode,
    place: SavedPlace?,
    snapshot: ForecastSnapshot?,
    selectedDayIndex: Int,
): String {
    if (place == null) {
        return "Select a point on the map and open it to see the forecast here."
    }

    val days = snapshot?.days.orEmpty()
    val selectedDay = days.getOrNull(selectedDayIndex)

    if (selectedDay == null) {
        return when (mode) {
            ForecastMode.THERMIC -> {
                "Forecast content for ${place.name} will appear here."
            }
            ForecastMode.STUVE -> {
                "Stuve forecast content for ${place.name} will appear here."
            }
            ForecastMode.WIND -> {
                "Wind forecast content for ${place.name} will appear here."
            }
            ForecastMode.CLOUD -> {
                "Cloud forecast content for ${place.name} will appear here."
            }
        }
    }

    val weather = WeatherCode.present(selectedDay.weatherCode)
    val dayTitle = if (selectedDayIndex == 0) {
        "Today"
    } else {
        selectedDay.date
    }

    return when (mode) {
        ForecastMode.THERMIC -> {
            buildString {
                append(dayTitle)
                append(" in ")
                append(place.name)
                append(". ")
                append(weather.label)
                append(". High ")
                append(formatTemperature(selectedDay.maxTemperatureCelsius))
                append(", low ")
                append(formatTemperature(selectedDay.minTemperatureCelsius))
                append(". Thermic profile is ready for the selected altitude range.")
            }
        }
        ForecastMode.STUVE -> {
            buildString {
                append(dayTitle)
                append(" in ")
                append(place.name)
                append(". ")
                append(weather.label)
                append(". Stuve diagram is ready for the selected hour.")
            }
        }
        ForecastMode.WIND -> {
            buildString {
                append(dayTitle)
                append(" in ")
                append(place.name)
                append(". ")
                append(weather.label)
                append(". Wind profile is ready for the selected altitude range.")
            }
        }
        ForecastMode.CLOUD -> {
            buildString {
                append(dayTitle)
                append(" in ")
                append(place.name)
                append(". ")
                append(weather.label)
                append(". Cloud layers, radiation, sunshine, and precipitation are ready.")
            }
        }
    }
}

private fun buildDayChips(days: List<DailyForecast>): List<ForecastDayChipUiModel> {
    return days.take(MAX_FORECAST_DAYS).mapIndexed { index, day ->
        ForecastDayChipUiModel(
            title = if (index == 0) "Today" else formatDayTitle(day.date),
            subtitle = formatDaySubtitle(day.date, index),
        )
    }
}

private fun buildDisplayedDayChips(
    loadedDays: List<DailyForecast>,
    displayedDayCount: Int,
): List<ForecastDayChipUiModel> {
    val loadedDayChips = buildDayChips(loadedDays).take(displayedDayCount)
    if (loadedDayChips.size >= displayedDayCount) {
        return loadedDayChips
    }

    return buildList(displayedDayCount) {
        addAll(loadedDayChips)
        for (index in loadedDayChips.size until displayedDayCount) {
            add(placeholderDayChip(index))
        }
    }
}

private fun placeholderDayChip(index: Int): ForecastDayChipUiModel {
    val calendar = Calendar.getInstance().apply {
        add(Calendar.DAY_OF_YEAR, index)
    }
    return ForecastDayChipUiModel(
        title = if (index == 0) "Today" else SimpleDateFormat("EEE", Locale.US).format(calendar.time),
        subtitle = SimpleDateFormat("d MMM", Locale.US).format(calendar.time),
    )
}

private fun formatDayTitle(date: String): String {
    return parseForecastDate(date)?.let { parsedDate ->
        SimpleDateFormat("EEE", Locale.US).format(parsedDate)
    } ?: date
}

private fun formatDaySubtitle(
    date: String,
    selectedDayIndex: Int,
): String {
    if (selectedDayIndex == 0) {
        return "Today"
    }

    return parseForecastDate(date)?.let { parsedDate ->
        SimpleDateFormat("d MMM", Locale.US).format(parsedDate)
    } ?: date
}

private fun parseForecastDate(date: String): Date? {
    return runCatching {
        SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(date)
    }.getOrNull()
}

private fun formatTemperature(value: Double): String {
    return String.format(Locale.US, "%.1f°C", value)
}

private fun ForecastModel.visibleForecastDays(): Int {
    return availableForecastDays.coerceAtMost(MAX_FORECAST_DAYS)
}
