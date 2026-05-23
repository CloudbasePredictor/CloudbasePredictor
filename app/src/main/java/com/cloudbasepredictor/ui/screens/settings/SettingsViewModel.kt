package com.cloudbasepredictor.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cloudbasepredictor.data.datasource.DataSourcePreference
import com.cloudbasepredictor.data.datasource.DataSourceRepository
import com.cloudbasepredictor.data.forecast.ForecastRepository
import com.cloudbasepredictor.data.launch.LaunchSiteDisplayRepository
import com.cloudbasepredictor.data.theme.ThemePreference
import com.cloudbasepredictor.data.theme.ThemeRepository
import com.cloudbasepredictor.data.units.UnitPreset
import com.cloudbasepredictor.data.units.UnitSettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val dataSourceRepository: DataSourceRepository,
    private val themeRepository: ThemeRepository,
    private val unitSettingsRepository: UnitSettingsRepository,
    private val launchSiteDisplayRepository: LaunchSiteDisplayRepository,
    private val forecastRepository: ForecastRepository,
) : ViewModel() {
    val dataSourcePreference: StateFlow<DataSourcePreference> = dataSourceRepository.preference
    val themePreference: StateFlow<ThemePreference> = themeRepository.preference
    val unitPreset: StateFlow<UnitPreset> = unitSettingsRepository.unitPreset
    val showLaunchSites: StateFlow<Boolean> = launchSiteDisplayRepository.showLaunchSites

    fun setDataSource(preference: DataSourcePreference) {
        val previousPreference = dataSourceRepository.preference.value
        dataSourceRepository.setPreference(preference)
        if (previousPreference != preference) {
            viewModelScope.launch {
                forecastRepository.clearAllCaches()
            }
        }
    }

    fun setTheme(preference: ThemePreference) {
        themeRepository.setPreference(preference)
    }

    fun setUnitPreset(unitPreset: UnitPreset) {
        unitSettingsRepository.setUnitPreset(unitPreset)
    }

    fun setShowLaunchSites(showLaunchSites: Boolean) {
        launchSiteDisplayRepository.setShowLaunchSites(showLaunchSites)
    }
}
