package com.cloudbasepredictor.ui.screens.forecast

import com.cloudbasepredictor.data.map.MapLayerPreference
import com.cloudbasepredictor.data.units.DisplayUnits
import com.cloudbasepredictor.data.units.UnitPreset
import com.cloudbasepredictor.data.units.resolveDisplayUnits
import com.cloudbasepredictor.model.ForecastMode
import com.cloudbasepredictor.model.ForecastModel
import com.cloudbasepredictor.model.SavedPlace

data class ForecastDayChipUiModel(
    val title: String,
    val subtitle: String,
)

/**
 * Platform-neutral screen state consumed by the shared forecast renderer.
 *
 * Loading and error states intentionally do not carry chart data. Chart models are created only
 * for [ForecastReadyUiState], after the forecast snapshot contains hourly data for the selected
 * place and model.
 */
sealed interface ForecastUiState {
    /** Currently selected place, or null while the app has no forecast location. */
    val selectedPlace: SavedPlace?

    /** Active forecast visualisation mode (thermic / stuve / wind / cloud). */
    val selectedForecastMode: ForecastMode

    /** Zero-based index of the selected forecast day (0 = today). */
    val selectedDayIndex: Int

    /** Weather model requested by the user. */
    val selectedModel: ForecastModel

    /** Model actually used after fallback (may differ from [selectedModel]). */
    val resolvedModel: ForecastModel?

    /** Favorite places to show on the forecast map panel. */
    val favoritePlaces: List<SavedPlace>

    /** Selected map base layer shared with the main map screen. */
    val mapLayer: MapLayerPreference

    /** Unit preset selected in Settings. */
    val unitPreset: UnitPreset

    /** Resolved display units for the active preset. */
    val displayUnits: DisplayUnits
}

data class ForecastLoadingUiState(
    override val selectedPlace: SavedPlace? = null,
    override val selectedForecastMode: ForecastMode = ForecastMode.THERMIC,
    override val selectedDayIndex: Int = 0,
    override val selectedModel: ForecastModel = ForecastModel.ICON_SEAMLESS,
    override val resolvedModel: ForecastModel? = null,
    override val favoritePlaces: List<SavedPlace> = emptyList(),
    override val mapLayer: MapLayerPreference = MapLayerPreference.OPENFREEMAP,
    override val unitPreset: UnitPreset = UnitPreset.METRIC_KMH,
    override val displayUnits: DisplayUnits = UnitPreset.METRIC_KMH.resolveDisplayUnits(),
) : ForecastUiState

data class ForecastNoPlaceUiState(
    override val selectedPlace: SavedPlace? = null,
    override val selectedForecastMode: ForecastMode = ForecastMode.THERMIC,
    override val selectedDayIndex: Int = 0,
    override val selectedModel: ForecastModel = ForecastModel.ICON_SEAMLESS,
    override val resolvedModel: ForecastModel? = null,
    override val favoritePlaces: List<SavedPlace> = emptyList(),
    override val mapLayer: MapLayerPreference = MapLayerPreference.OPENFREEMAP,
    override val unitPreset: UnitPreset = UnitPreset.METRIC_KMH,
    override val displayUnits: DisplayUnits = UnitPreset.METRIC_KMH.resolveDisplayUnits(),
) : ForecastUiState

data class ForecastErrorUiState(
    val errorMessage: String,
    override val selectedPlace: SavedPlace? = null,
    override val selectedForecastMode: ForecastMode = ForecastMode.THERMIC,
    override val selectedDayIndex: Int = 0,
    override val selectedModel: ForecastModel = ForecastModel.ICON_SEAMLESS,
    override val resolvedModel: ForecastModel? = null,
    override val favoritePlaces: List<SavedPlace> = emptyList(),
    override val mapLayer: MapLayerPreference = MapLayerPreference.OPENFREEMAP,
    override val unitPreset: UnitPreset = UnitPreset.METRIC_KMH,
    override val displayUnits: DisplayUnits = UnitPreset.METRIC_KMH.resolveDisplayUnits(),
) : ForecastUiState

data class ForecastReadyUiState(
    override val selectedPlace: SavedPlace? = null,
    override val selectedForecastMode: ForecastMode = ForecastMode.THERMIC,
    override val selectedDayIndex: Int = 0,
    /** Visible altitude range controlled by pinch-to-zoom. */
    val chartViewport: ForecastChartViewport = ForecastChartViewport(),
    /** Thermic updraft strength chart data. */
    val thermicChart: ThermicForecastChartUiModel,
    /** Stueve thermodynamic diagram data for the selected hour. */
    val stuveChart: StuveForecastChartUiModel,
    /** Wind speed and direction chart data. */
    val windChart: WindForecastChartUiModel,
    /** Cloud coverage and precipitation chart data. */
    val cloudChart: CloudForecastChartUiModel,
    /** Day chips for the date picker (title and subtitle). */
    val dayChips: List<ForecastDayChipUiModel>,
    /** Summary text shown at the bottom of the chart. */
    val forecastText: String,
    override val selectedModel: ForecastModel = ForecastModel.ICON_SEAMLESS,
    override val resolvedModel: ForecastModel? = null,
    /** Timestamp (UTC millis) when the forecast data was last updated from the server. */
    val forecastUpdatedAtMillis: Long? = null,
    /** Estimated UTC millis of the model run that produced this forecast. */
    val modelGeneratedAtMillis: Long? = null,
    /** Terrain elevation in km ASL for the selected place. */
    val elevationKm: Float = 0f,
    override val favoritePlaces: List<SavedPlace> = emptyList(),
    override val mapLayer: MapLayerPreference = MapLayerPreference.OPENFREEMAP,
    override val unitPreset: UnitPreset = UnitPreset.METRIC_KMH,
    override val displayUnits: DisplayUnits = UnitPreset.METRIC_KMH.resolveDisplayUnits(),
) : ForecastUiState
