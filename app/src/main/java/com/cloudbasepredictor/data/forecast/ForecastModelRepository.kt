package com.cloudbasepredictor.data.forecast

import android.content.SharedPreferences
import com.cloudbasepredictor.model.ForecastModel
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

interface ForecastModelRepository {
    val selectedModel: StateFlow<ForecastModel>

    fun selectModel(model: ForecastModel)
}

@SingleIn(AppScope::class)
class InMemoryForecastModelRepository @Inject constructor(
    private val prefs: SharedPreferences,
) : ForecastModelRepository {
    private val mutableSelectedModel = MutableStateFlow(loadFromPrefs())

    override val selectedModel: StateFlow<ForecastModel> = mutableSelectedModel.asStateFlow()

    override fun selectModel(model: ForecastModel) {
        mutableSelectedModel.value = model
        prefs.edit().putString(KEY_SELECTED_MODEL, model.apiName).apply()
    }

    private fun loadFromPrefs(): ForecastModel {
        val apiName = prefs.getString(KEY_SELECTED_MODEL, null) ?: return DEFAULT_MODEL
        return ForecastModel.fromApiName(apiName) ?: DEFAULT_MODEL
    }

    private companion object {
        val DEFAULT_MODEL = ForecastModel.ICON_SEAMLESS
        const val KEY_SELECTED_MODEL = "selected_forecast_model"
    }
}
