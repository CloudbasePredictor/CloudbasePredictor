package com.cloudbasepredictor.ui.screens.forecast

import com.cloudbasepredictor.R
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
import com.cloudbasepredictor.model.WeatherCondition
import com.cloudbasepredictor.ui.text.AppStringResources
import com.cloudbasepredictor.util.toFixedDecimalString
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
    private val stringResources: AppStringResources,
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
        reduceForecastUiState(
            inputs = inputs,
            selectedModel = currentModel,
            favoritePlaces = favorites,
            preferences = preferences,
            incompleteDataError = INCOMPLETE_FORECAST_DATA_ERROR,
            buildSummary = { mode, place, snapshot, dayIndex ->
                buildForecastText(
                    resources = stringResources,
                    mode = mode,
                    place = place,
                    snapshot = snapshot,
                    selectedDayIndex = dayIndex,
                )
            },
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

internal data class ForecastChartContext(
    val selectedForecastMode: ForecastMode,
    val selectedDayIndex: Int,
    val chartViewport: ForecastChartViewport,
    val stuveHour: Int = 12,
)

internal data class ForecastUiInputs(
    val place: SavedPlace?,
    val snapshot: ForecastSnapshot?,
    val chartContext: ForecastChartContext,
    val isLoading: Boolean,
    val errorMessage: String?,
)

internal data class MapAndUnitPreferences(
    val mapLayer: MapLayerPreference,
    val unitPreset: UnitPreset,
    val displayUnits: DisplayUnits,
)

/**
 * Pure state reduction for the forecast screen. Extracted from the [ForecastViewModel.uiState]
 * `combine` so the branching (no-place, stale-cache preservation, loading, incomplete data,
 * day-index coercion and the ready happy path) is unit-testable without constructing the view
 * model or touching Android/coroutine machinery. Mirrors the map module's `shouldRequestLaunchSites`.
 *
 * [buildSummary] resolves the localized forecast summary; callers pass a resource-backed
 * implementation in production and a plain lambda in tests.
 */
@Suppress("ReturnCount", "LongMethod", "CyclomaticComplexMethod", "LongParameterList")
internal fun reduceForecastUiState(
    inputs: ForecastUiInputs,
    selectedModel: ForecastModel,
    favoritePlaces: List<SavedPlace>,
    preferences: MapAndUnitPreferences,
    incompleteDataError: String,
    buildSummary: (
        mode: ForecastMode,
        place: SavedPlace,
        snapshot: ForecastSnapshot,
        dayIndex: Int,
    ) -> String,
): ForecastUiState {
    val place = inputs.place
    val snapshot = inputs.snapshot
    val currentChartContext = inputs.chartContext
    val loading = inputs.isLoading
    val currentError = inputs.errorMessage

    if (place == null) {
        return ForecastNoPlaceUiState(
            selectedForecastMode = currentChartContext.selectedForecastMode,
            selectedDayIndex = currentChartContext.selectedDayIndex.coerceAtLeast(0),
            selectedModel = selectedModel,
            favoritePlaces = favoritePlaces,
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
        return ForecastErrorUiState(
            errorMessage = currentError,
            selectedPlace = place,
            selectedForecastMode = currentChartContext.selectedForecastMode,
            selectedDayIndex = currentChartContext.selectedDayIndex.coerceAtLeast(0),
            selectedModel = selectedModel,
            resolvedModel = snapshot?.resolvedModel,
            favoritePlaces = favoritePlaces,
            mapLayer = preferences.mapLayer,
            unitPreset = preferences.unitPreset,
            displayUnits = preferences.displayUnits,
        )
    }

    if (snapshot == null) {
        return ForecastLoadingUiState(
            selectedPlace = place,
            selectedForecastMode = currentChartContext.selectedForecastMode,
            selectedDayIndex = currentChartContext.selectedDayIndex.coerceAtLeast(0),
            selectedModel = selectedModel,
            resolvedModel = snapshot?.resolvedModel,
            favoritePlaces = favoritePlaces,
            mapLayer = preferences.mapLayer,
            unitPreset = preferences.unitPreset,
            displayUnits = preferences.displayUnits,
        )
    }

    val hourlyData = snapshot.hourlyData
    if (hourlyData == null) {
        return ForecastErrorUiState(
            errorMessage = incompleteDataError,
            selectedPlace = place,
            selectedForecastMode = currentChartContext.selectedForecastMode,
            selectedDayIndex = currentChartContext.selectedDayIndex.coerceAtLeast(0),
            selectedModel = selectedModel,
            resolvedModel = snapshot.resolvedModel,
            favoritePlaces = favoritePlaces,
            mapLayer = preferences.mapLayer,
            unitPreset = preferences.unitPreset,
            displayUnits = preferences.displayUnits,
        )
    }

    val loadedForecastDays = snapshot.days.size
    val availableForecastDays = (snapshot.resolvedModel ?: selectedModel).visibleForecastDays()
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
        return ForecastErrorUiState(
            errorMessage = incompleteDataError,
            selectedPlace = place,
            selectedForecastMode = currentChartContext.selectedForecastMode,
            selectedDayIndex = safeDayIndex,
            selectedModel = selectedModel,
            resolvedModel = snapshot.resolvedModel,
            favoritePlaces = favoritePlaces,
            mapLayer = preferences.mapLayer,
            unitPreset = preferences.unitPreset,
            displayUnits = preferences.displayUnits,
        )
    }

    return buildForecastReadyUiState(
        ForecastRenderInput(
            hourlyData = hourlyData,
            place = place,
            requestedModel = selectedModel,
            resolvedModel = snapshot.resolvedModel,
            selectedForecastMode = currentChartContext.selectedForecastMode,
            selectedDayIndex = safeDayIndex,
            stuveHour = currentChartContext.stuveHour,
            chartViewport = currentChartContext.chartViewport,
            unitPreset = preferences.unitPreset,
            displayUnits = preferences.displayUnits,
            fetchedAtMillis = snapshot.updatedAtUtcMillis,
            modelGeneratedAtMillis = snapshot.modelGeneratedAtMillis,
            favoritePlaces = favoritePlaces,
            mapLayer = preferences.mapLayer,
            dayChips = dayChips,
            forecastText = buildSummary(
                currentChartContext.selectedForecastMode,
                place,
                snapshot,
                safeDayIndex,
            ),
        ),
    ).copy(isRefreshing = loading)
}

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

/**
 * Builds the localized forecast summary from Android string resources. Pure: it depends only on
 * the injected [AppStringResources], so it is unit-testable with a fake resolver.
 *
 * Temperatures use the app-wide Celsius convention (matching the shared `buildForecastSummary`
 * and the Stuve chart axes); the value is formatted via [toFixedDecimalString] rather than a
 * `Locale.US` pattern so it no longer bypasses i18n.
 */
internal fun buildForecastText(
    resources: AppStringResources,
    mode: ForecastMode,
    place: SavedPlace,
    snapshot: ForecastSnapshot,
    selectedDayIndex: Int,
): String {
    val selectedDay = snapshot.days.getOrNull(selectedDayIndex)
        ?: return when (mode) {
            ForecastMode.THERMIC ->
                resources.getString(R.string.forecast_summary_pending_thermic, place.name)
            ForecastMode.STUVE ->
                resources.getString(R.string.forecast_summary_pending_stuve, place.name)
            ForecastMode.WIND ->
                resources.getString(R.string.forecast_summary_pending_wind, place.name)
            ForecastMode.CLOUD ->
                resources.getString(R.string.forecast_summary_pending_cloud, place.name)
        }

    val weatherLabel = localizedWeatherLabel(resources, selectedDay.weatherCode)
    val dayTitle = if (selectedDayIndex == 0) {
        resources.getString(R.string.forecast_summary_today)
    } else {
        selectedDay.date
    }

    return when (mode) {
        ForecastMode.THERMIC -> resources.getString(
            R.string.forecast_summary_thermic,
            dayTitle,
            place.name,
            weatherLabel,
            formatTemperature(resources, selectedDay.maxTemperatureCelsius),
            formatTemperature(resources, selectedDay.minTemperatureCelsius),
        )
        ForecastMode.STUVE -> resources.getString(
            R.string.forecast_summary_stuve,
            dayTitle,
            place.name,
            weatherLabel,
        )
        ForecastMode.WIND -> resources.getString(
            R.string.forecast_summary_wind,
            dayTitle,
            place.name,
            weatherLabel,
        )
        ForecastMode.CLOUD -> resources.getString(
            R.string.forecast_summary_cloud,
            dayTitle,
            place.name,
            weatherLabel,
        )
    }
}

private fun localizedWeatherLabel(resources: AppStringResources, weatherCode: Int): String {
    val resourceId = when (WeatherCode.condition(weatherCode)) {
        WeatherCondition.CLEAR_SKY -> R.string.weather_clear_sky
        WeatherCondition.PARTLY_CLOUDY -> R.string.weather_partly_cloudy
        WeatherCondition.FOG -> R.string.weather_fog
        WeatherCondition.DRIZZLE -> R.string.weather_drizzle
        WeatherCondition.RAIN -> R.string.weather_rain
        WeatherCondition.SNOW -> R.string.weather_snow
        WeatherCondition.RAIN_SHOWERS -> R.string.weather_rain_showers
        WeatherCondition.SNOW_SHOWERS -> R.string.weather_snow_showers
        WeatherCondition.THUNDERSTORM -> R.string.weather_thunderstorm
        WeatherCondition.UNKNOWN -> R.string.weather_unknown
    }
    return resources.getString(resourceId)
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

private fun formatTemperature(resources: AppStringResources, valueCelsius: Double): String {
    return resources.getString(
        R.string.forecast_summary_temperature_celsius,
        valueCelsius.toFixedDecimalString(1),
    )
}

private fun ForecastModel.visibleForecastDays(): Int {
    return availableForecastDays.coerceAtMost(MAX_FORECAST_DAYS)
}
