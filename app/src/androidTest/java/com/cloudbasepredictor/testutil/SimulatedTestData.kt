package com.cloudbasepredictor.testutil

import android.content.Context
import com.cloudbasepredictor.data.datasource.SimulatedForecastDataSource
import com.cloudbasepredictor.data.remote.HourlyForecastData
import com.cloudbasepredictor.data.remote.OpenMeteoHourlyForecastResponse
import com.cloudbasepredictor.data.remote.toHourlyForecastData
import com.cloudbasepredictor.model.ForecastMode
import com.cloudbasepredictor.model.ForecastModel
import com.cloudbasepredictor.model.SavedPlace
import com.cloudbasepredictor.ui.screens.forecast.ForecastChartViewport
import com.cloudbasepredictor.ui.screens.forecast.ForecastDayChipUiModel
import com.cloudbasepredictor.ui.screens.forecast.ForecastReadyUiState
import com.cloudbasepredictor.ui.screens.forecast.buildCloudChartFromData
import com.cloudbasepredictor.ui.screens.forecast.buildStuveChartFromData
import com.cloudbasepredictor.ui.screens.forecast.buildThermicChartFromData
import com.cloudbasepredictor.ui.screens.forecast.buildWindChartFromData
import kotlinx.serialization.json.Json

/**
 * Loads the simulated Brauneck ICON-Seamless forecast from app assets and builds
 * [ForecastReadyUiState] with real chart data. Use in instrumentation and screenshot tests.
 */
object SimulatedTestData {

    val brauneckPlace = SavedPlace(
        id = SimulatedForecastDataSource.PLACE_ID,
        name = SimulatedForecastDataSource.PLACE_NAME,
        latitude = SimulatedForecastDataSource.LATITUDE,
        longitude = SimulatedForecastDataSource.LONGITUDE,
        isFavorite = true,
    )

    private val json = Json { ignoreUnknownKeys = true }

    fun loadHourlyData(context: Context): HourlyForecastData {
        val jsonString = context.assets.open(SimulatedForecastDataSource.ASSET_PATH)
            .bufferedReader().use { it.readText() }
        val response = json.decodeFromString<OpenMeteoHourlyForecastResponse>(jsonString)
        return response.toHourlyForecastData()
    }

    fun forecastUiState(
        context: Context,
        mode: ForecastMode = ForecastMode.THERMIC,
        dayIndex: Int = 0,
        topAltitudeKm: Float = ForecastChartViewport().visibleTopAltitudeKm,
    ): ForecastReadyUiState {
        val hourlyData = loadHourlyData(context)
        val elevationKm = ((hourlyData.elevation ?: 0.0) / 1000.0).toFloat()

        val dayChips = hourlyData.dailyForecasts.mapIndexed { index, daily ->
            ForecastDayChipUiModel(
                title = if (index == 0) "Sat" else "Day $index",
                subtitle = daily.date.substringAfter("-").replace("-", "/"),
            )
        }

        return ForecastReadyUiState(
            selectedPlace = brauneckPlace,
            selectedForecastMode = mode,
            selectedDayIndex = dayIndex,
            chartViewport = ForecastChartViewport(visibleTopAltitudeKm = topAltitudeKm),
            thermicChart = buildThermicChartFromData(hourlyData, dayIndex = dayIndex),
            stuveChart = buildStuveChartFromData(hourlyData, dayIndex = dayIndex, hour = 12),
            windChart = buildWindChartFromData(hourlyData, dayIndex = dayIndex),
            cloudChart = buildCloudChartFromData(hourlyData, dayIndex = dayIndex),
            dayChips = dayChips,
            forecastText = "Sat in Brauneck Süd. ICON Seamless simulated forecast.",
            selectedModel = ForecastModel.ICON_SEAMLESS,
            resolvedModel = ForecastModel.ICON_SEAMLESS,
            forecastUpdatedAtMillis = System.currentTimeMillis(),
            elevationKm = elevationKm,
        )
    }
}
