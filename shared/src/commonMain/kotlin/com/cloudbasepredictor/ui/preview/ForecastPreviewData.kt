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

    // 10 m wind at Interlaken: a light down-valley breeze in the morning, the up-valley breeze through the
    // afternoon, and a fresher down-valley breeze in the evening. The two-digit 21h value gives the trailing glyph
    // column of narrow phones a wide 10 m pill, so their previews show how that column fits its 10 m glyph.
    private val surfaceWindCells = listOf(
        // Hour, speed (km/h), direction the wind comes from (degrees).
        Triple(6, 4f, 70f),
        Triple(7, 5f, 75f),
        Triple(8, 4f, 80f),
        Triple(9, 3f, 120f),
        Triple(10, 5f, 220f),
        Triple(11, 8f, 235f),
        Triple(12, 11f, 240f),
        Triple(13, 13f, 245f),
        Triple(14, 15f, 250f),
        Triple(15, 16f, 250f),
        Triple(16, 14f, 245f),
        Triple(17, 12f, 240f),
        Triple(18, 9f, 235f),
        Triple(19, 6f, 200f),
        Triple(20, 8f, 80f),
        Triple(21, 11f, 75f),
        Triple(22, 9f, 70f),
    ).map { (hour, speedKmh, directionDeg) ->
        WindForecastCellUiModel(
            hour = hour,
            altitudeKm = PREVIEW_ELEVATION_KM + SURFACE_WIND_HEIGHT_KM,
            speedKmh = speedKmh,
            directionDeg = directionDeg,
            isSurface10m = true,
        )
    }

    /** Placeholder upper-air wind with 10 m values, so wind previews show the 10 m row. */
    val windChart: WindForecastChartUiModel = buildPlaceholderWindForecastChart(dayIndex = 2).let { chart ->
        chart.copy(cells = chart.cells + surfaceWindCells)
    }

    val readyState = ForecastReadyUiState(
        selectedPlace = savedPlace,
        selectedDayIndex = 2,
        thermicChart = buildPlaceholderThermicForecastChart(dayIndex = 2),
        stuveChart = buildPlaceholderStuveChart(hour = 12, dayIndex = 2),
        windChart = windChart,
        cloudChart = buildPlaceholderCloudForecastChart(dayIndex = 2),
        dayChips = dayChips,
        forecastText = "Monday in Interlaken. Partly cloudy with usable afternoon lift.",
        selectedModel = ForecastModel.ICON_D2,
        resolvedModel = ForecastModel.ICON_D2,
        forecastUpdatedAtMillis = 1_752_220_800_000L,
        modelGeneratedAtMillis = 1_752_199_200_000L,
        elevationKm = PREVIEW_ELEVATION_KM,
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

    // Interlaken terrain, and the 10 m wind above it, km ASL.
    private const val PREVIEW_ELEVATION_KM = 0.58f
    private const val SURFACE_WIND_HEIGHT_KM = 0.01f
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
