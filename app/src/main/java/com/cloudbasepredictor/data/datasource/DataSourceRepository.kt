package com.cloudbasepredictor.data.datasource

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class DataSourcePreference {
    REAL,
    SIMULATED,
    FAKE,
}

interface DataSourceRepository {
    val preference: StateFlow<DataSourcePreference>
    fun setPreference(preference: DataSourcePreference)
}

@SingleIn(AppScope::class)
class InMemoryDataSourceRepository @Inject constructor() : DataSourceRepository {
    override val preference = MutableStateFlow(DataSourcePreference.REAL)

    override fun setPreference(preference: DataSourcePreference) {
        this.preference.value = preference
    }
}
