package com.cloudbasepredictor.ui.screens.forecast

import androidx.compose.ui.semantics.SemanticsPropertyKey

/**
 * Semantics key carrying the active parcel dry-adiabat potential temperature (K) when the chart
 * has an active cursor or heating-handle interaction. Used by instrumented tests to verify that
 * tap X position and bottom-handle drag produce distinct parcel guides.
 */
val StuveActiveThetaKKey = SemanticsPropertyKey<Float>("StuveActiveThetaK")

object ForecastTestTags {
    const val THERMIC_MODE_TAB = "forecast_mode_tab_thermic"
    const val STUVE_MODE_TAB = "forecast_mode_tab_stuve"
    const val WIND_MODE_TAB = "forecast_mode_tab_wind"
    const val CLOUD_MODE_TAB = "forecast_mode_tab_cloud"

    const val THERMIC_VIEW = "forecast_view_thermic"
    const val THERMIC_CURSOR_PANEL = "forecast_thermic_cursor_panel"
    const val STUVE_VIEW = "forecast_view_stuve"
    const val WIND_VIEW = "forecast_view_wind"
    const val CLOUD_VIEW = "forecast_view_cloud"
    const val THERMIC_TIME_AXIS = "forecast_thermic_time_axis"
    const val THERMIC_ALTITUDE_UNIT = "forecast_thermic_altitude_unit"
    const val STUVE_CHART_CANVAS = "forecast_stuve_chart_canvas"
    const val STUVE_TIME_SLIDER = "forecast_stuve_time_slider"
    const val STUVE_SELECTED_HOUR = "forecast_stuve_selected_hour"
    const val WIND_TIME_AXIS = "forecast_wind_time_axis"
    const val WIND_ALTITUDE_UNIT = "forecast_wind_altitude_unit"

    const val HELP_BUTTON = "forecast_help_button"

    const val MODEL_SELECTOR_BUTTON = "forecast_model_selector_button"
    const val MODEL_OPTION_PREFIX = "forecast_model_option_"

    const val FORECAST_CHART_AREA = "forecast_chart_area"
    const val MAP_PANEL = "forecast_map_panel"
    const val MAP_PANEL_SURFACE = "forecast_map_panel_surface"
    const val DAY_CHIP_PREFIX = "forecast_day_chip_"
    const val CLOUD_SCROLL = "forecast_cloud_scroll"
    const val CLOUD_SUNSHINE_ROW = "forecast_cloud_sunshine"
    const val CLOUD_RADIATION_ROW = "forecast_cloud_radiation"
    const val CLOUD_RAIN_ROW = "forecast_cloud_rain"
    const val CLOUD_LAYERS_ROW = "forecast_cloud_layers"
    const val CLOUD_TIME_AXIS_ROW = "forecast_cloud_time_axis"
}
