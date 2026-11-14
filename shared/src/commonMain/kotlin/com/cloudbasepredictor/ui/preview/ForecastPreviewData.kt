@file:Suppress("FunctionNaming", "MagicNumber")

package com.cloudbasepredictor.ui.preview

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import com.cloudbasepredictor.model.ForecastMode
import com.cloudbasepredictor.model.ForecastModel
import com.cloudbasepredictor.model.SavedPlace
import com.cloudbasepredictor.ui.screens.forecast.ForecastChartViewport
import com.cloudbasepredictor.ui.screens.forecast.ForecastDayChipUiModel
import com.cloudbasepredictor.ui.screens.forecast.ForecastReadyUiState
import com.cloudbasepredictor.ui.screens.forecast.buildPlaceholderCloudForecastChart
import com.cloudbasepredictor.ui.screens.forecast.buildPlaceholderStuveChart
import com.cloudbasepredictor.ui.screens.forecast.buildPlaceholderThermicForecastChart
import com.cloudbasepredictor.ui.screens.forecast.buildPlaceholderWindForecastChart
import com.cloudbasepredictor.ui.screens.forecast.WindForecastChartUiModel
import com.cloudbasepredictor.ui.screens.forecast.WindForecastCellUiModel
import androidx.compose.ui.tooling.preview.Preview

/** Realistic, deterministic forecast fixtures shared by all forecast UI previews. */
object ForecastPreviewData {
    const val noThermalsMessage = "Unfortunately, no thermals are expected."

    val savedPlace = SavedPlace(
        id = "place:46.5582:7.8354",
        name = "Interlaken",
        latitude = 46.5582,
        longitude = 7.8354,
        isFavorite = true,
    )

    val favoritePlaces = listOf(
        savedPlace,
        SavedPlace(
            id = "place:47.3769:8.5417",
            name = "Zurich",
            latitude = 47.3769,
            longitude = 8.5417,
            isFavorite = true,
        ),
    )

    val dayChips = listOf(
        ForecastDayChipUiModel(title = "Today", subtitle = "11 Jul"),
        ForecastDayChipUiModel(title = "Sun", subtitle = "12 Jul"),
        ForecastDayChipUiModel(title = "Mon", subtitle = "13 Jul"),
        ForecastDayChipUiModel(title = "Tue", subtitle = "14 Jul"),
    )

    // Sparse sources, close low levels, changing hourly heights, calm wind and missing hours.
    val sparseWindChart = WindForecastChartUiModel(
        hours = (6..9).toList(),
        altitudeBandsKm = listOf(0.59f, 0.614f, 0.620f, 0.625f, 1.5f, 2.357f, 2.398f),
        cells = listOf(
            WindForecastCellUiModel(6, 0.59f, 8f, 350f, isSurface10m = true),
            WindForecastCellUiModel(6, 0.614f, 12f, 10f),
            WindForecastCellUiModel(6, 0.620f, 16f, 90f),
            WindForecastCellUiModel(6, 2.357f, 30f, 270f),
            WindForecastCellUiModel(7, 0.625f, 0f, 0f),
            WindForecastCellUiModel(7, 2.398f, 24f, 90f),
            WindForecastCellUiModel(8, 0.59f, 0f, 0f, isSurface10m = true),
            WindForecastCellUiModel(8, 1.5f, 18f, 180f),
        ),
    )

    val readyState = ForecastReadyUiState(
        selectedPlace = savedPlace,
        selectedDayIndex = 2,
        thermicChart = buildPlaceholderThermicForecastChart(dayIndex = 2),
        stuveChart = buildPlaceholderStuveChart(hour = 12, dayIndex = 2),
        windChart = buildPlaceholderWindForecastChart(dayIndex = 2),
        cloudChart = buildPlaceholderCloudForecastChart(dayIndex = 2),
        dayChips = dayChips,
        forecastText = "Monday in Interlaken. Partly cloudy with usable afternoon lift.",
        selectedModel = ForecastModel.ICON_D2,
        resolvedModel = ForecastModel.ICON_D2,
        forecastUpdatedAtMillis = 1_752_220_800_000L,
        modelGeneratedAtMillis = 1_752_199_200_000L,
        elevationKm = 0.58f,
        favoritePlaces = favoritePlaces,
    )

    fun stateForMode(
        mode: ForecastMode,
        topAltitudeKm: Float = ForecastChartViewport().visibleTopAltitudeKm,
    ): ForecastReadyUiState = readyState.copy(
        selectedForecastMode = mode,
        chartViewport = ForecastChartViewport(visibleTopAltitudeKm = topAltitudeKm),
        forecastText = "$mode layered forecast preview for Interlaken.",
    )

    /** Wind state with sparse sources, close low levels, calm wind and missing hours. */
    fun sparseWindState(
        topAltitudeKm: Float = ForecastChartViewport().visibleTopAltitudeKm,
    ): ForecastReadyUiState = stateForMode(ForecastMode.WIND, topAltitudeKm).copy(windChart = sparseWindChart)
}

@Composable
fun ForecastPreviewTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) darkColorScheme() else lightColorScheme(),
        content = content,
    )
}

@Preview(showBackground = true)
@Composable
private fun ForecastPreviewThemePreview() {
    ForecastPreviewTheme {
        Text(text = ForecastPreviewData.readyState.forecastText)
    }
}
