@file:Suppress("FunctionNaming")

package com.cloudbasepredictor.web.forecast

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.cloudbasepredictor.data.map.MapLayerPreference
import com.cloudbasepredictor.model.PlaceLocation

/**
 * Draggable bottom map panel for the forecast screen (mirrors Android's `ForecastMapPanel`). Drag the
 * handle up to reveal a live map with a center crosshair; panning it updates the forecast location
 * in place (rate-limited via the shared [com.cloudbasepredictor.model.forecastMapLocationUpdateDecision]).
 * MapLibre lazy-loads only when the panel is first expanded so it stays out of the initial bundle.
 */
@Composable
internal expect fun WebForecastMapPanel(
    currentLocation: PlaceLocation,
    mapLayer: MapLayerPreference,
    onLocationChanged: (PlaceLocation) -> Unit,
    modifier: Modifier,
)
