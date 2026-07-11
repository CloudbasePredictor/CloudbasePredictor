@file:Suppress("FunctionNaming")

package com.cloudbasepredictor.web.forecast

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.cloudbasepredictor.model.ForecastMode
import com.cloudbasepredictor.model.ForecastModel
import com.cloudbasepredictor.model.PlaceLocation
import com.cloudbasepredictor.model.SavedPlace
import com.cloudbasepredictor.ui.preview.ForecastPreviewData
import com.cloudbasepredictor.ui.preview.ForecastPreviewTheme
import com.cloudbasepredictor.ui.screens.forecast.ForecastReadyUiState
import com.cloudbasepredictor.ui.screens.forecast.views.CloudForecastView
import com.cloudbasepredictor.ui.screens.forecast.views.StuveForecastView
import com.cloudbasepredictor.ui.screens.forecast.views.ThermicForecastView
import com.cloudbasepredictor.ui.screens.forecast.views.WindForecastView
import com.cloudbasepredictor.web.WebDestination
import com.cloudbasepredictor.web.WebRouteState
import com.cloudbasepredictor.web.preferences.WebPreferencesState
import com.cloudbasepredictor.web.preview.WebPreviewData
import kotlin.coroutines.cancellation.CancellationException
import androidx.compose.ui.tooling.preview.Preview

@Composable
fun WebForecastDestination(
    routeState: WebRouteState,
    repository: WebForecastRepository,
    preferences: WebPreferencesState,
    favoritePlaces: List<SavedPlace>,
    onRouteChanged: (WebRouteState) -> Unit,
    onForecastModelSelected: (ForecastModel) -> Unit,
    onFavoriteToggle: (PlaceLocation, Boolean) -> Unit,
    onShareRequested: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val location = routeState.location
    if (location == null) {
        WebForecastNoLocation(
            onChooseLocation = {
                onRouteChanged(routeState.copy(destination = WebDestination.Map))
            },
            modifier = modifier,
        )
        return
    }

    var retryGeneration by remember(location, routeState.model) { mutableIntStateOf(0) }
    var loadState by remember(location, routeState.model) {
        mutableStateOf<WebForecastLoadState>(WebForecastLoadState.Loading)
    }
    val forecastDays = routeState.model.availableForecastDays.coerceIn(1, MAX_WEB_FORECAST_DAYS)
    LaunchedEffect(location, routeState.model, retryGeneration) {
        loadState = WebForecastLoadState.Loading
        loadState = try {
            WebForecastLoadState.Ready(
                repository.load(
                    location = location,
                    requestedModel = routeState.model,
                    forecastDays = forecastDays,
                    forceRefresh = retryGeneration > 0,
                ),
            )
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            WebForecastLoadState.Error(
                message = exception.message?.takeIf(String::isNotBlank)
                    ?: "Forecast data could not be loaded.",
            )
        }
    }

    when (val state = loadState) {
        WebForecastLoadState.Loading -> WebForecastLoading(location, modifier)
        is WebForecastLoadState.Error -> WebForecastError(
            message = state.message,
            onRetry = { retryGeneration++ },
            modifier = modifier,
        )
        is WebForecastLoadState.Ready -> {
            var visibleTopAltitudeKm by remember(location, routeState.model) {
                mutableFloatStateOf(DEFAULT_VISIBLE_TOP_ALTITUDE_KM)
            }
            val readyState = remember(
                state.result,
                routeState,
                visibleTopAltitudeKm,
                preferences.unitPreset,
                preferences.mapLayer,
                favoritePlaces,
            ) {
                buildWebForecastReadyState(
                    WebForecastPresentationInput(
                        location = location,
                        requestedModel = routeState.model,
                        result = state.result,
                        mode = routeState.mode,
                        dayIndex = routeState.dayIndex,
                        hour = routeState.hour,
                        visibleTopAltitudeKm = visibleTopAltitudeKm,
                        unitPreset = preferences.unitPreset,
                        mapLayer = preferences.mapLayer,
                        favoritePlaces = favoritePlaces,
                    ),
                )
            }
            val currentId = SavedPlace.fromCoordinates(location.latitude, location.longitude).id
            val isFavorite = favoritePlaces.any { it.id == currentId }
            WebForecastReadyContent(
                routeState = routeState,
                uiState = readyState,
                fromCache = state.result.fromCache,
                isFavorite = isFavorite,
                onRouteChanged = onRouteChanged,
                onForecastModelSelected = onForecastModelSelected,
                onFavoriteToggle = { onFavoriteToggle(location, isFavorite) },
                onShareRequested = onShareRequested,
                onVisibleTopAltitudeChanged = { visibleTopAltitudeKm = it },
                modifier = modifier,
            )
        }
    }
}

@Suppress("LongMethod")
@Composable
private fun WebForecastReadyContent(
    routeState: WebRouteState,
    uiState: ForecastReadyUiState,
    fromCache: Boolean,
    isFavorite: Boolean,
    onRouteChanged: (WebRouteState) -> Unit,
    onForecastModelSelected: (ForecastModel) -> Unit,
    onFavoriteToggle: () -> Unit,
    onShareRequested: () -> Unit,
    onVisibleTopAltitudeChanged: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showModelSheet by remember(routeState.location) { mutableStateOf(false) }
    var visibleDayCount by remember(uiState.dayChips.size) {
        mutableIntStateOf(INITIAL_VISIBLE_DAY_CHIPS)
    }
    // Keep the selected day visible even when it is beyond the progressive window (e.g. deep links).
    val shownDayCount = maxOf(visibleDayCount, uiState.selectedDayIndex + 1)
        .coerceAtMost(uiState.dayChips.size)

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = uiState.selectedPlace?.name ?: "Forecast",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.semantics { heading() },
                    )
                    Text(
                        text = buildString {
                            append(uiState.resolvedModel?.displayName ?: uiState.selectedModel.displayName)
                            append(if (fromCache) " · saved forecast" else " · live forecast")
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onShareRequested) {
                        Text("Copy link")
                    }
                    OutlinedButton(onClick = onFavoriteToggle) {
                        Text(if (isFavorite) "Remove favorite" else "Save favorite")
                    }
                }
            }

            WebForecastModePicker(
                selectedMode = routeState.mode,
                onModeSelected = { mode -> onRouteChanged(routeState.copy(mode = mode)) },
            )
            WebForecastModelPill(
                selectedModel = routeState.model,
                resolvedModel = uiState.resolvedModel,
                onClick = { showModelSheet = true },
            )
            ScrollableChipRow {
                uiState.dayChips.take(shownDayCount).forEachIndexed { index, day ->
                    val isSelected = index == uiState.selectedDayIndex
                    val label = "${day.title} ${day.subtitle}"
                    FilterChip(
                        selected = isSelected,
                        onClick = { onRouteChanged(routeState.copy(dayIndex = index)) },
                        label = { Text(label) },
                    )
                }
                if (shownDayCount < uiState.dayChips.size) {
                    FilterChip(
                        selected = false,
                        onClick = {
                            visibleDayCount = (shownDayCount + DAY_CHIP_INCREMENT)
                                .coerceAtMost(uiState.dayChips.size)
                        },
                        label = { Text("More days") },
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) {
                when (routeState.mode) {
                    ForecastMode.THERMIC -> ThermicForecastView(
                        uiState = uiState,
                        onVisibleTopAltitudeChange = onVisibleTopAltitudeChanged,
                        noThermalsMessage = "No usable thermals are forecast for this period.",
                        modifier = Modifier.fillMaxSize(),
                    )
                    ForecastMode.STUVE -> StuveForecastView(
                        uiState = uiState,
                        onVisibleTopAltitudeChange = onVisibleTopAltitudeChanged,
                        onStuveHourChanged = { hour ->
                            onRouteChanged(routeState.copy(hour = hour))
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                    ForecastMode.WIND -> WindForecastView(
                        uiState = uiState,
                        onVisibleTopAltitudeChange = onVisibleTopAltitudeChanged,
                        modifier = Modifier.fillMaxSize(),
                    )
                    ForecastMode.CLOUD -> CloudForecastView(
                        uiState = uiState,
                        onVisibleTopAltitudeChange = onVisibleTopAltitudeChanged,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
            Text(
                text = "${uiState.forecastText} · Forecast data by Open-Meteo.com",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (showModelSheet) {
            WebForecastModelSheet(
                selectedModel = routeState.model,
                onModelSelected = { model ->
                    onForecastModelSelected(model)
                    onRouteChanged(routeState.copy(model = model, dayIndex = 0))
                    visibleDayCount = INITIAL_VISIBLE_DAY_CHIPS
                },
                onDismiss = { showModelSheet = false },
            )
        }
    }
}

private const val INITIAL_VISIBLE_DAY_CHIPS = 5
private const val DAY_CHIP_INCREMENT = 2

@Composable
private fun ScrollableChipRow(content: @Composable () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        content = { content() },
    )
}

@Composable
private fun WebForecastNoLocation(
    onChooseLocation: () -> Unit,
    modifier: Modifier = Modifier,
) {
    CenteredMessage(modifier) {
        Text("Choose a location to load a soaring forecast.")
        Spacer(Modifier.height(12.dp))
        Button(onClick = onChooseLocation) { Text("Open map") }
    }
}

@Composable
private fun WebForecastLoading(
    location: PlaceLocation,
    modifier: Modifier = Modifier,
) {
    CenteredMessage(modifier) {
        CircularProgressIndicator()
        Spacer(Modifier.height(12.dp))
        Text("Loading forecast for ${location.name ?: "selected location"}…")
    }
}

@Composable
private fun WebForecastError(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    CenteredMessage(modifier) {
        Text(message, color = MaterialTheme.colorScheme.error)
        Spacer(Modifier.height(12.dp))
        Button(onClick = onRetry) { Text("Retry") }
    }
}

@Composable
private fun CenteredMessage(
    modifier: Modifier,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        content = { content() },
    )
}

private sealed interface WebForecastLoadState {
    data object Loading : WebForecastLoadState
    data class Ready(val result: WebForecastResult) : WebForecastLoadState
    data class Error(val message: String) : WebForecastLoadState
}

internal val ForecastMode.webLabel: String
    get() = when (this) {
        ForecastMode.THERMIC -> "Thermic"
        ForecastMode.STUVE -> "Stüve"
        ForecastMode.WIND -> "Wind"
        ForecastMode.CLOUD -> "Cloud"
    }

private const val MAX_WEB_FORECAST_DAYS = 14
private const val DEFAULT_VISIBLE_TOP_ALTITUDE_KM = 4f

@Preview(name = "Web forecast", showBackground = true, widthDp = 1024, heightDp = 760)
@Composable
private fun WebForecastReadyContentPreview() {
    ForecastPreviewTheme {
        WebForecastReadyContent(
            routeState = WebPreviewData.forecastRoute,
            uiState = ForecastPreviewData.readyState,
            fromCache = false,
            isFavorite = true,
            onRouteChanged = {},
            onForecastModelSelected = {},
            onFavoriteToggle = {},
            onShareRequested = {},
            onVisibleTopAltitudeChanged = {},
        )
    }
}
