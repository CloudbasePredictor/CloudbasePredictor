package com.cloudbasepredictor.ui.screens.forecast

import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cloudbasepredictor.R
import com.cloudbasepredictor.model.ForecastMode
import com.cloudbasepredictor.model.ForecastModel
import com.cloudbasepredictor.model.PlaceLocation
import com.cloudbasepredictor.model.SavedPlace
import com.cloudbasepredictor.ui.components.SaveFavoriteDialog
import com.cloudbasepredictor.ui.preview.PreviewData
import com.cloudbasepredictor.ui.screens.forecast.views.CloudForecastView
import com.cloudbasepredictor.ui.screens.forecast.views.StuveForecastView
import com.cloudbasepredictor.ui.screens.forecast.views.ThermicForecastView
import com.cloudbasepredictor.ui.screens.forecast.views.WindForecastView
import com.cloudbasepredictor.ui.theme.CloudbasePredictorTheme
import kotlinx.coroutines.flow.collectLatest

@Composable
fun ForecastRoute(
    placeLocation: PlaceLocation,
    onOpenMap: () -> Unit,
    onPlaceLocationChanged: (PlaceLocation) -> Unit,
    viewModel: ForecastViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(viewModel, placeLocation) {
        viewModel.setPlaceLocation(placeLocation)
    }

    LaunchedEffect(viewModel) {
        viewModel.networkErrorEvent.collectLatest { errorMsg ->
            Toast.makeText(context, errorMsg, Toast.LENGTH_SHORT).show()
        }
    }

    ForecastScreen(
        uiState = uiState,
        onDateSelected = viewModel::selectDay,
        onForecastModeSelected = viewModel::selectForecastMode,
        onForecastViewportTopChanged = viewModel::updateChartTopAltitude,
        onStuveHourChanged = viewModel::updateStuveHour,
        onSaveFavorite = viewModel::saveFavorite,
        onDeleteFavorite = viewModel::deleteFavorite,
        onFavoriteSelected = { place ->
            onPlaceLocationChanged(PlaceLocation.fromSavedPlace(place))
        },
        onRetryLoad = viewModel::retryLoad,
        onModelSelected = viewModel::selectModel,
        onOpenMap = onOpenMap,
        onMapLocationChanged = { latitude, longitude ->
            onPlaceLocationChanged(
                PlaceLocation(
                    latitude = latitude,
                    longitude = longitude,
                ),
            )
        },
    )
}

@Composable
fun ForecastScreen(
    uiState: ForecastUiState,
    onDateSelected: (Int) -> Unit,
    onForecastModeSelected: (ForecastMode) -> Unit = {},
    onForecastViewportTopChanged: (Float) -> Unit = {},
    onStuveHourChanged: (Int) -> Unit = {},
    onSaveFavorite: (String) -> Unit = {},
    onDeleteFavorite: () -> Unit = {},
    onFavoriteSelected: (SavedPlace) -> Unit = {},
    onRetryLoad: () -> Unit = {},
    onModelSelected: (ForecastModel) -> Unit = {},
    onOpenMap: () -> Unit,
    onMapLocationChanged: (Double, Double) -> Unit = { _, _ -> },
    initiallyExpandedMap: Boolean = false,
    modifier: Modifier = Modifier,
) {
    var showFavoriteDialog by rememberSaveable { mutableStateOf(false) }
    val density = LocalDensity.current
    var mapPanelHeightPx by remember { mutableFloatStateOf(0f) }
    val mapPanelHeightDp = with(density) { mapPanelHeightPx.toDp() }

    Surface(
        modifier = modifier
            .fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom)),
        ) {
            ForecastTopBar(
                placeName = uiState.selectedPlace?.name,
                isFavorite = uiState.selectedPlace?.isFavorite == true,
                selectedMode = uiState.selectedForecastMode,
                onModeSelected = onForecastModeSelected,
                onFavoriteClick = { showFavoriteDialog = true },
                onOpenMap = onOpenMap,
            )

            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .testTag(ForecastTestTags.FORECAST_CHART_AREA),
            ) {
                val forecastContentHeight = (maxHeight - mapPanelHeightDp).coerceAtLeast(0.dp)

                when (uiState) {
                    is ForecastLoadingUiState,
                    is ForecastNoPlaceUiState -> {
                        ForecastLoadingContent(
                            placeName = uiState.selectedPlace?.name,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(forecastContentHeight),
                        )
                    }
                    is ForecastErrorUiState -> {
                        ForecastErrorContent(
                            errorMessage = uiState.errorMessage,
                            onRetry = onRetryLoad,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(forecastContentHeight),
                        )
                    }
                    is ForecastReadyUiState -> {
                        ForecastReadyContent(
                            uiState = uiState,
                            onForecastViewportTopChanged = onForecastViewportTopChanged,
                            onStuveHourChanged = onStuveHourChanged,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(forecastContentHeight),
                        )
                    }
                }

                ForecastMapPanel(
                    currentPlace = uiState.selectedPlace,
                    favoritePlaces = uiState.favoritePlaces,
                    mapLayer = uiState.mapLayer,
                    onLocationChanged = onMapLocationChanged,
                    initiallyExpanded = initiallyExpandedMap,
                    onPanelHeightChanged = { mapPanelHeightPx = it },
                    modifier = Modifier.fillMaxSize().testTag(ForecastTestTags.MAP_PANEL),
                )

                ModelSelectorOverlay(
                    selectedModel = uiState.selectedModel,
                    resolvedModel = uiState.resolvedModel,
                    onModelSelected = onModelSelected,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 8.dp, end = 8.dp),
                )
            }

            if (uiState is ForecastReadyUiState) {
                ForecastDatePicker(
                    dayChips = uiState.dayChips,
                    selectedDayIndex = uiState.selectedDayIndex,
                    onDateSelected = onDateSelected,
                )
            }
        }

        if (showFavoriteDialog) {
            val place = uiState.selectedPlace
            SaveFavoriteDialog(
                currentName = if (place?.isFavorite == true) place.name else "",
                isFavorite = place?.isFavorite == true,
                favoritePlaces = uiState.favoritePlaces,
                selectedPlaceId = place?.id,
                onSave = onSaveFavorite,
                onDelete = onDeleteFavorite,
                onFavoriteSelected = onFavoriteSelected,
                onDismiss = { showFavoriteDialog = false },
            )
        }
    }
}

@Composable
private fun ForecastLoadingContent(
    placeName: String?,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator(modifier = Modifier.size(48.dp))
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = if (placeName != null) {
                stringResource(R.string.loading_forecast_for_place, placeName)
            } else {
                stringResource(R.string.loading_forecast)
            },
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(modifier = Modifier.height(12.dp))
        LinearProgressIndicator(modifier = Modifier.fillMaxWidth(0.6f))
    }
}

@Composable
private fun ForecastErrorContent(
    errorMessage: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Outlined.ErrorOutline,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.error,
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = errorMessage,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onRetry) {
            Text(text = stringResource(R.string.action_retry))
        }
    }
}

@Composable
private fun ForecastReadyContent(
    uiState: ForecastReadyUiState,
    onForecastViewportTopChanged: (Float) -> Unit,
    onStuveHourChanged: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
        AnimatedContent(
            targetState = uiState.selectedForecastMode,
            transitionSpec = {
                val direction = if (targetState.ordinal > initialState.ordinal) 1 else -1
                (slideInHorizontally(
                    animationSpec = tween(300),
                    initialOffsetX = { fullWidth -> direction * fullWidth / 4 },
                ) + fadeIn(animationSpec = tween(300))) togetherWith
                    (slideOutHorizontally(
                        animationSpec = tween(300),
                        targetOffsetX = { fullWidth -> -direction * fullWidth / 4 },
                    ) + fadeOut(animationSpec = tween(300)))
            },
            label = "forecast_mode_transition",
        ) { mode ->
            when (mode) {
                ForecastMode.THERMIC -> {
                    ThermicForecastView(
                        uiState = uiState,
                        onVisibleTopAltitudeChange = onForecastViewportTopChanged,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                ForecastMode.STUVE -> {
                    StuveForecastView(
                        uiState = uiState,
                        onVisibleTopAltitudeChange = onForecastViewportTopChanged,
                        onStuveHourChanged = onStuveHourChanged,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                ForecastMode.WIND -> {
                    WindForecastView(
                        uiState = uiState,
                        onVisibleTopAltitudeChange = onForecastViewportTopChanged,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                ForecastMode.CLOUD -> {
                    CloudForecastView(
                        uiState = uiState,
                        onVisibleTopAltitudeChange = onForecastViewportTopChanged,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }

        HelpButtonOverlay(
            uiState = uiState,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 40.dp, top = 8.dp),
        )

    }
}

@Preview(showBackground = true)
@Composable
private fun ForecastScreenPreview() {
    CloudbasePredictorTheme {
        ForecastScreen(
            uiState = PreviewData.forecastReadyUiState,
            onDateSelected = {},
            onOpenMap = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ForecastScreenLoadingPreview() {
    CloudbasePredictorTheme {
        ForecastScreen(
            uiState = PreviewData.forecastLoadingUiState,
            onDateSelected = {},
            onOpenMap = {},
        )
    }
}

@Preview(
    name = "Forecast Loading Dark",
    showBackground = true,
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun ForecastScreenLoadingDarkPreview() {
    CloudbasePredictorTheme(darkTheme = true) {
        ForecastScreen(
            uiState = PreviewData.forecastLoadingUiState,
            onDateSelected = {},
            onOpenMap = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ForecastScreenErrorPreview() {
    CloudbasePredictorTheme {
        ForecastScreen(
            uiState = PreviewData.forecastErrorUiState,
            onDateSelected = {},
            onOpenMap = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ForecastScreenCloudPreview() {
    CloudbasePredictorTheme {
        ForecastScreen(
            uiState = PreviewData.forecastUiStateForMode(ForecastMode.CLOUD),
            onDateSelected = {},
            onOpenMap = {},
        )
    }
}
