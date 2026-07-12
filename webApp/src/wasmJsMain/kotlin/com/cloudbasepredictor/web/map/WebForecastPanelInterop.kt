@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package com.cloudbasepredictor.web.map

import com.cloudbasepredictor.data.map.MapCameraPosition
import com.cloudbasepredictor.data.map.MapLayerPreference
import kotlin.js.JsAny
import kotlin.js.Promise
import org.w3c.dom.HTMLDivElement

/**
 * Thin bridge that lets the forecast map panel reuse the map module's internal MapLibre interop
 * (lazy bundle load, map creation, styling) without leaking every low-level `js(...)` helper.
 */
internal object WebForecastPanelInterop {
    fun loadBundle(): Promise<JsAny> = loadMapLibreBundle()

    fun createMap(
        module: JsAny,
        host: HTMLDivElement,
        camera: MapCameraPosition,
        layer: MapLayerPreference,
    ): MapLibreMap = createMapLibreMap(
        module = module,
        options = mapOptions(
            container = host,
            style = styleFor(layer),
            longitude = camera.longitude,
            latitude = camera.latitude,
            zoom = camera.zoom,
        ),
    )

    fun observeResize(host: HTMLDivElement, onResize: () -> Unit): WebResizeObserver =
        createResizeObserver { onResize() }.also { observer -> observer.observe(host) }

    fun applyStyle(map: MapLibreMap, layer: MapLayerPreference) {
        map.setStyle(styleFor(layer))
    }

    fun flyTo(map: MapLibreMap, longitude: Double, latitude: Double) {
        map.flyTo(flyToOptions(longitude = longitude, latitude = latitude, zoom = map.getZoom()))
    }

    private fun styleFor(layer: MapLayerPreference): JsAny =
        when (val style = buildWebMapStyle(layer, yesterdayUtcDate())) {
            is WebMapStyle.Url -> styleValue(style.value, isJson = false)
            is WebMapStyle.Raster -> styleValue(style.toMapLibreJson(), isJson = true)
        }
}
