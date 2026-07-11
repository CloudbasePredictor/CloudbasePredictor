package com.cloudbasepredictor.di

import com.cloudbasepredictor.data.datasource.DataSourceRepository
import com.cloudbasepredictor.data.datasource.InMemoryDataSourceRepository
import com.cloudbasepredictor.data.forecast.ForecastRepository
import com.cloudbasepredictor.data.forecast.ForecastModeRepository
import com.cloudbasepredictor.data.forecast.ForecastModelRepository
import com.cloudbasepredictor.data.forecast.ForecastViewportRepository
import com.cloudbasepredictor.data.forecast.InMemoryForecastRepository
import com.cloudbasepredictor.data.forecast.InMemoryForecastModeRepository
import com.cloudbasepredictor.data.forecast.InMemoryForecastModelRepository
import com.cloudbasepredictor.data.forecast.SharedPrefsForecastViewportRepository
import com.cloudbasepredictor.data.launch.DefaultLaunchSiteRepository
import com.cloudbasepredictor.data.launch.LaunchSiteDisplayRepository
import com.cloudbasepredictor.data.launch.LaunchSiteRepository
import com.cloudbasepredictor.data.launch.SharedPrefsLaunchSiteDisplayRepository
import com.cloudbasepredictor.data.map.MapLayerRepository
import com.cloudbasepredictor.data.map.MapStartupRepository
import com.cloudbasepredictor.data.map.SharedPrefsMapLayerRepository
import com.cloudbasepredictor.data.map.SharedPrefsMapStartupRepository
import com.cloudbasepredictor.data.place.DefaultPlaceRepository
import com.cloudbasepredictor.data.place.PlaceRepository
import com.cloudbasepredictor.data.theme.SharedPrefsThemeRepository
import com.cloudbasepredictor.data.theme.ThemeRepository
import com.cloudbasepredictor.data.units.SharedPrefsUnitSettingsRepository
import com.cloudbasepredictor.data.units.UnitSettingsRepository
import dev.zacsweers.metro.Binds
import dev.zacsweers.metro.BindingContainer

@BindingContainer
abstract class RepositoryModule {
    @Binds
    abstract val DefaultPlaceRepository.bindPlaceRepository: PlaceRepository

    @Binds
    abstract val InMemoryForecastRepository.bindForecastRepository: ForecastRepository

    @Binds
    abstract val InMemoryForecastModeRepository.bindForecastModeRepository: ForecastModeRepository

    @Binds
    abstract val InMemoryForecastModelRepository.bindForecastModelRepository: ForecastModelRepository

    @Binds
    abstract val InMemoryDataSourceRepository.bindDataSourceRepository: DataSourceRepository

    @Binds
    abstract val SharedPrefsThemeRepository.bindThemeRepository: ThemeRepository

    @Binds
    abstract val SharedPrefsUnitSettingsRepository.bindUnitSettingsRepository: UnitSettingsRepository

    @Binds
    abstract val SharedPrefsForecastViewportRepository.bindForecastViewportRepository:
        ForecastViewportRepository

    @Binds
    abstract val SharedPrefsMapLayerRepository.bindMapLayerRepository: MapLayerRepository

    @Binds
    abstract val SharedPrefsMapStartupRepository.bindMapStartupRepository: MapStartupRepository

    @Binds
    abstract val DefaultLaunchSiteRepository.bindLaunchSiteRepository: LaunchSiteRepository

    @Binds
    abstract val SharedPrefsLaunchSiteDisplayRepository.bindLaunchSiteDisplayRepository:
        LaunchSiteDisplayRepository
}
