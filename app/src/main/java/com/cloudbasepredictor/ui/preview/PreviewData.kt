package com.cloudbasepredictor.ui.preview

import com.cloudbasepredictor.model.DailyForecast
import com.cloudbasepredictor.model.ForecastSnapshot
import com.cloudbasepredictor.model.ForecastMode
import com.cloudbasepredictor.model.ForecastModel
import com.cloudbasepredictor.model.SavedPlace
import com.cloudbasepredictor.data.datasource.DataSourcePreference
import com.cloudbasepredictor.data.theme.ThemePreference
import com.cloudbasepredictor.data.units.UnitPreset
import com.cloudbasepredictor.ui.screens.forecast.ForecastChartViewport
import com.cloudbasepredictor.ui.screens.forecast.ForecastDayChipUiModel
import com.cloudbasepredictor.ui.screens.forecast.ForecastErrorUiState
import com.cloudbasepredictor.ui.screens.forecast.ForecastLoadingUiState
import com.cloudbasepredictor.ui.screens.forecast.ForecastReadyUiState
import com.cloudbasepredictor.ui.screens.forecast.buildPlaceholderCloudForecastChart
import com.cloudbasepredictor.ui.screens.forecast.buildPlaceholderStuveChart
import com.cloudbasepredictor.ui.screens.forecast.buildPlaceholderThermicForecastChart
import com.cloudbasepredictor.ui.screens.forecast.buildPlaceholderWindForecastChart
import com.cloudbasepredictor.ui.screens.map.MapUiState
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

object PreviewData {
    val settingsFakeDataSource = DataSourcePreference.FAKE
    val settingsRealDataSource = DataSourcePreference.REAL
    val settingsAutoTheme = ThemePreference.AUTO
    val settingsDarkTheme = ThemePreference.DARK
    val settingsMetricMpsUnits = UnitPreset.METRIC_MPS
    val settingsImperialUnits = UnitPreset.IMPERIAL
    val settingsAviationUnits = UnitPreset.AVIATION

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
        SavedPlace(
            id = "place:46.9480:7.4474",
            name = "Bern",
            latitude = 46.9480,
            longitude = 7.4474,
            isFavorite = true,
        ),
    )

    val manualFavoriteName = "Brauneck South"
    val manualFavoriteCoordinates = "47.6468, 11.5216"

    val dailyForecasts: List<DailyForecast> = List(7) { index ->
        DailyForecast(
            date = dateOffsetByDays(index),
            maxTemperatureCelsius = 18.0 + index,
            minTemperatureCelsius = 9.0 + (index * 0.6),
            weatherCode = if (index % 2 == 0) 1 else 3,
        )
    }

    val forecastSnapshot = ForecastSnapshot(
        days = dailyForecasts,
        updatedAtUtcMillis = 1_715_777_600_000L,
        modelGeneratedAtMillis = 1_715_770_800_000L,
    )

    fun forecastDayChips(days: Int = 7): List<ForecastDayChipUiModel> {
        return List(days) { index ->
            ForecastDayChipUiModel(
                title = if (index == 0) "Today" else formatDate(dateOffsetByDays(index), "EEE"),
                subtitle = formatDate(dateOffsetByDays(index), "d MMM"),
            )
        }
    }

    val forecastReadyUiState = ForecastReadyUiState(
        selectedPlace = savedPlace,
        selectedDayIndex = 2,
        thermicChart = buildPlaceholderThermicForecastChart(dayIndex = 2),
        stuveChart = buildPlaceholderStuveChart(hour = 12, dayIndex = 2),
        windChart = buildPlaceholderWindForecastChart(dayIndex = 2),
        cloudChart = buildPlaceholderCloudForecastChart(dayIndex = 2),
        dayChips = forecastDayChips(7),
        forecastText = "Sat in Interlaken. Partly cloudy. High 20.0°C, low 10.2°C.",
        selectedModel = ForecastModel.ICON_D2,
        resolvedModel = ForecastModel.ICON_D2,
        forecastUpdatedAtMillis = 1_715_777_600_000L,
        elevationKm = 0.58f, // Interlaken ~580m ASL
        favoritePlaces = favoritePlaces,
    )

    val forecastLoadingUiState = ForecastLoadingUiState(
        selectedPlace = savedPlace,
        selectedDayIndex = 0,
    )

    val forecastZoomedOutUiState = forecastReadyUiState.copy(
        chartViewport = ForecastChartViewport(visibleTopAltitudeKm = 6.5f),
        forecastText = "Zoomed-out layered forecast preview with extended altitude range.",
    )

    val forecastErrorUiState = ForecastErrorUiState(
        selectedPlace = savedPlace,
        selectedDayIndex = 1,
        errorMessage = "Unable to refresh forecast layers right now.",
        selectedModel = ForecastModel.ICON_D2,
        resolvedModel = ForecastModel.ICON_D2,
    )

    fun forecastUiStateForMode(
        mode: ForecastMode,
        topAltitudeKm: Float = ForecastChartViewport().visibleTopAltitudeKm,
    ): ForecastReadyUiState {
        val modeLabel = when (mode) {
            ForecastMode.THERMIC -> "Thermic"
            ForecastMode.STUVE -> "Stuve"
            ForecastMode.WIND -> "Wind"
            ForecastMode.CLOUD -> "Cloud"
        }

        return forecastReadyUiState.copy(
            selectedForecastMode = mode,
            chartViewport = ForecastChartViewport(visibleTopAltitudeKm = topAltitudeKm),
            thermicChart = buildPlaceholderThermicForecastChart(dayIndex = forecastReadyUiState.selectedDayIndex),
            stuveChart = buildPlaceholderStuveChart(hour = 12, dayIndex = forecastReadyUiState.selectedDayIndex),
            windChart = buildPlaceholderWindForecastChart(dayIndex = forecastReadyUiState.selectedDayIndex),
            cloudChart = buildPlaceholderCloudForecastChart(dayIndex = forecastReadyUiState.selectedDayIndex),
            forecastText = "$modeLabel layered forecast preview for Interlaken.",
        )
    }

    val mapUiState = MapUiState(
        selectedPlace = savedPlace,
        favoritePlaces = favoritePlaces,
    )

    private fun dateOffsetByDays(days: Int): String {
        val calendar = Calendar.getInstance(Locale.US).apply {
            add(Calendar.DAY_OF_YEAR, days)
        }
        return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(calendar.time)
    }

    private fun formatDate(
        date: String,
        format: String,
    ): String {
        val parsedDate = SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(date)
            ?: return date
        return SimpleDateFormat(format, Locale.US).format(parsedDate)
    }
}
