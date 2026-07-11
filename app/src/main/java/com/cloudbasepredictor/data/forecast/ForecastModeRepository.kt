package com.cloudbasepredictor.data.forecast

import com.cloudbasepredictor.model.ForecastMode
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

interface ForecastModeRepository {
    val selectedMode: StateFlow<ForecastMode>

    fun selectMode(mode: ForecastMode)
}

@SingleIn(AppScope::class)
class InMemoryForecastModeRepository @Inject constructor() : ForecastModeRepository {
    private val mutableSelectedMode = MutableStateFlow(ForecastMode.THERMIC)

    override val selectedMode: StateFlow<ForecastMode> = mutableSelectedMode.asStateFlow()

    override fun selectMode(mode: ForecastMode) {
        mutableSelectedMode.value = mode
    }
}
