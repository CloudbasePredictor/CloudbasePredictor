package com.cloudbasepredictor.web.preferences

import com.cloudbasepredictor.data.map.MapLayerPreference
import com.cloudbasepredictor.data.storage.KeyValueStorage
import com.cloudbasepredictor.data.theme.ThemePreference
import com.cloudbasepredictor.data.units.UnitPreset
import com.cloudbasepredictor.model.ForecastModel
import com.cloudbasepredictor.web.i18n.WebLanguage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class WebPreferencesState(
    val unitPreset: UnitPreset = UnitPreset.METRIC_KMH,
    val themePreference: ThemePreference = ThemePreference.AUTO,
    val mapLayer: MapLayerPreference = MapLayerPreference.OPENFREEMAP,
    val startWithFavorites: Boolean = true,
    val forecastModel: ForecastModel = ForecastModel.ICON_SEAMLESS,
    val showLaunchSites: Boolean = false,
    val language: WebLanguage = WebLanguage.SYSTEM,
)

class WebPreferences(
    private val storage: KeyValueStorage,
) {
    private val mutableState = MutableStateFlow(load())
    val state: StateFlow<WebPreferencesState> = mutableState.asStateFlow()

    fun selectUnitPreset(value: UnitPreset) {
        update(mutableState.value.copy(unitPreset = value))
        storage.putString(UNIT_PRESET_KEY, value.name)
    }

    fun selectTheme(value: ThemePreference) {
        update(mutableState.value.copy(themePreference = value))
        storage.putString(THEME_PREFERENCE_KEY, value.name)
    }

    fun selectMapLayer(value: MapLayerPreference) {
        update(mutableState.value.copy(mapLayer = value))
        storage.putString(MAP_LAYER_KEY, value.name)
    }

    fun setStartWithFavorites(value: Boolean) {
        update(mutableState.value.copy(startWithFavorites = value))
        storage.putBoolean(START_WITH_FAVORITES_KEY, value)
    }

    fun selectForecastModel(value: ForecastModel) {
        update(mutableState.value.copy(forecastModel = value))
        storage.putString(FORECAST_MODEL_KEY, value.apiName)
    }

    fun setShowLaunchSites(value: Boolean) {
        update(mutableState.value.copy(showLaunchSites = value))
        storage.putBoolean(SHOW_LAUNCH_SITES_KEY, value)
    }

    fun selectLanguage(value: WebLanguage) {
        update(mutableState.value.copy(language = value))
        storage.putString(LANGUAGE_KEY, value.name)
    }

    private fun update(value: WebPreferencesState) {
        mutableState.value = value
    }

    private fun load(): WebPreferencesState {
        return WebPreferencesState(
            unitPreset = storage.getString(UNIT_PRESET_KEY)
                ?.let { value -> UnitPreset.entries.firstOrNull { it.name == value } }
                ?: UnitPreset.METRIC_KMH,
            themePreference = storage.getString(THEME_PREFERENCE_KEY)
                ?.let { value -> ThemePreference.entries.firstOrNull { it.name == value } }
                ?: ThemePreference.AUTO,
            mapLayer = storage.getString(MAP_LAYER_KEY)
                ?.let { value -> MapLayerPreference.entries.firstOrNull { it.name == value } }
                ?: MapLayerPreference.OPENFREEMAP,
            startWithFavorites = storage.getBoolean(START_WITH_FAVORITES_KEY) ?: true,
            forecastModel = storage.getString(FORECAST_MODEL_KEY)
                ?.let(ForecastModel::fromApiName)
                ?: ForecastModel.ICON_SEAMLESS,
            showLaunchSites = storage.getBoolean(SHOW_LAUNCH_SITES_KEY) ?: false,
            language = storage.getString(LANGUAGE_KEY)
                ?.let { value -> WebLanguage.entries.firstOrNull { it.name == value } }
                ?: WebLanguage.SYSTEM,
        )
    }

    private companion object {
        const val UNIT_PRESET_KEY = "unit_preset"
        const val THEME_PREFERENCE_KEY = "theme_preference"
        const val MAP_LAYER_KEY = "map_layer"
        const val START_WITH_FAVORITES_KEY = "start_with_favorites"
        const val FORECAST_MODEL_KEY = "selected_forecast_model"
        const val SHOW_LAUNCH_SITES_KEY = "show_launch_sites"
        const val LANGUAGE_KEY = "app_language"
    }
}
