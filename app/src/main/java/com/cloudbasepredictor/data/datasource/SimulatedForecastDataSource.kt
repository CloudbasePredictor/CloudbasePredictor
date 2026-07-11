package com.cloudbasepredictor.data.datasource

import android.content.Context
import com.cloudbasepredictor.data.remote.HourlyForecastData
import com.cloudbasepredictor.data.remote.OpenMeteoHourlyForecastResponse
import com.cloudbasepredictor.data.remote.toHourlyForecastData
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.serialization.json.Json

/**
 * Provides [HourlyForecastData] from a bundled JSON snapshot in `assets/simulated/`.
 *
 * The snapshot was downloaded from the Open-Meteo API for Brauneck Süd (47.66347, 11.52365)
 * using the ICON-Seamless model. See `assets/simulated/README.md` for details.
 *
 * This data source ignores location/model parameters and always returns the same snapshot,
 * making it suitable for deterministic tests and screenshots.
 */
@SingleIn(AppScope::class)
class SimulatedForecastDataSource @Inject constructor(
    private val context: Context,
    private val json: Json,
) {
    private var cached: HourlyForecastData? = null

    fun loadForecastData(): HourlyForecastData {
        cached?.let { return it }

        val jsonString = context.assets.open(ASSET_PATH).bufferedReader().use { it.readText() }
        val response = json.decodeFromString<OpenMeteoHourlyForecastResponse>(jsonString)
        val data = response.toHourlyForecastData()
        cached = data
        return data
    }

    companion object {
        const val ASSET_PATH = "simulated/brauneck_icon_seamless_20260418.json"

        /** Brauneck Süd launch site coordinates. */
        const val LATITUDE = 47.66347
        const val LONGITUDE = 11.52365
        const val PLACE_NAME = "Brauneck Süd"
        const val PLACE_ID = "simulated:brauneck"
    }
}
