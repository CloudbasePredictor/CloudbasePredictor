package com.cloudbasepredictor.di

import android.content.Context
import androidx.lifecycle.ViewModel
import com.cloudbasepredictor.data.forecast.ForecastCacheMaintenance
import com.cloudbasepredictor.data.local.DatabaseErrorManager
import com.cloudbasepredictor.data.remote.OpenMeteoRemoteDataSource
import com.cloudbasepredictor.data.theme.ThemeRepository
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactory
import dev.zacsweers.metrox.viewmodel.MetroViewModelFactory
import dev.zacsweers.metrox.viewmodel.ViewModelAssistedFactory
import dev.zacsweers.metrox.viewmodel.ViewModelGraph
import kotlin.reflect.KClass

@DependencyGraph(
    scope = AppScope::class,
    bindingContainers = [
        DatabaseModule::class,
        DispatcherModule::class,
        NetworkModule::class,
        RepositoryModule::class,
    ],
)
interface AppGraph : ViewModelGraph {
    val databaseErrorManager: DatabaseErrorManager
    val forecastCacheMaintenance: ForecastCacheMaintenance
    val openMeteoRemoteDataSource: OpenMeteoRemoteDataSource
    val themeRepository: ThemeRepository

    @DependencyGraph.Factory
    fun interface Factory {
        fun create(@Provides context: Context): AppGraph
    }
}

@Inject
@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
class CloudbaseViewModelFactory(
    override val viewModelProviders: Map<KClass<out ViewModel>, () -> ViewModel>,
    override val assistedFactoryProviders:
        Map<KClass<out ViewModel>, () -> ViewModelAssistedFactory>,
    override val manualAssistedFactoryProviders:
        Map<KClass<out ManualViewModelAssistedFactory>, () -> ManualViewModelAssistedFactory>,
) : MetroViewModelFactory()
