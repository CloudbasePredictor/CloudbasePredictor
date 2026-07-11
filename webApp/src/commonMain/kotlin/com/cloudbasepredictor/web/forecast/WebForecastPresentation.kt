package com.cloudbasepredictor.web.forecast

import com.cloudbasepredictor.data.map.MapLayerPreference
import com.cloudbasepredictor.data.units.UnitPreset
import com.cloudbasepredictor.model.ForecastMode
import com.cloudbasepredictor.model.ForecastModel
import com.cloudbasepredictor.model.PlaceLocation
import com.cloudbasepredictor.model.SavedPlace
import com.cloudbasepredictor.ui.screens.forecast.ForecastChartViewport
import com.cloudbasepredictor.ui.screens.forecast.ForecastReadyUiState
import com.cloudbasepredictor.ui.screens.forecast.ForecastRenderInput
import com.cloudbasepredictor.ui.screens.forecast.buildForecastReadyUiState

data class WebForecastPresentationInput(
    val location: PlaceLocation,
    val requestedModel: ForecastModel,
    val result: WebForecastResult,
    val mode: ForecastMode,
    val dayIndex: Int,
    val hour: Int,
    val visibleTopAltitudeKm: Float,
    val unitPreset: UnitPreset,
    val mapLayer: MapLayerPreference,
    val favoritePlaces: List<SavedPlace>,
)

fun buildWebForecastReadyState(input: WebForecastPresentationInput): ForecastReadyUiState {
    return buildForecastReadyUiState(
        ForecastRenderInput(
            hourlyData = input.result.hourlyData,
            place = input.location.toSavedPlace(input.favoritePlaces),
            requestedModel = input.requestedModel,
            resolvedModel = input.result.resolvedModel,
            selectedForecastMode = input.mode,
            selectedDayIndex = input.dayIndex,
            stuveHour = input.hour,
            chartViewport = ForecastChartViewport(
                visibleTopAltitudeKm = input.visibleTopAltitudeKm,
            ),
            unitPreset = input.unitPreset,
            fetchedAtMillis = input.result.fetchedAtMillis,
            modelGeneratedAtMillis = estimateModelRunMillis(
                input.result.fetchedAtMillis,
                input.result.resolvedModel,
            ),
            favoritePlaces = input.favoritePlaces,
            mapLayer = input.mapLayer,
        ),
    )
}

private fun PlaceLocation.toSavedPlace(favorites: List<SavedPlace>): SavedPlace {
    val coordinatePlace = SavedPlace.fromCoordinates(latitude, longitude)
    return favorites.firstOrNull { it.id == coordinatePlace.id }
        ?: coordinatePlace.copy(name = name ?: coordinatePlace.name)
}

private fun estimateModelRunMillis(fetchedAtMillis: Long, model: ForecastModel): Long {
    return (fetchedAtMillis / model.updateIntervalMillis) * model.updateIntervalMillis
}
