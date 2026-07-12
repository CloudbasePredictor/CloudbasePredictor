@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
// Detekt cannot see parameters referenced from Kotlin/Wasm js(...) bodies.
@file:Suppress("UnusedParameter")

package com.cloudbasepredictor.web.map

import kotlin.js.JsAny
import kotlin.js.Promise
import org.w3c.dom.Element
import org.w3c.dom.HTMLElement

internal external interface MapLibreMap : JsAny {
    fun on(type: String, listener: (JsAny) -> Unit): MapLibreSubscription
    fun setStyle(style: JsAny): MapLibreMap
    fun flyTo(options: JsAny): MapLibreMap
    fun getCenter(): MapLibreLngLat
    fun getBounds(): MapLibreBounds
    fun getZoom(): Double
    fun getBearing(): Double
    fun resetNorth(): MapLibreMap
    fun resize(): MapLibreMap
    fun remove()
}

internal external interface MapLibreSubscription : JsAny {
    fun unsubscribe()
}

internal external interface MapLibreLngLat : JsAny {
    val lng: Double
    val lat: Double
}

internal external interface MapLibreBounds : JsAny {
    fun getNorth(): Double
    fun getSouth(): Double
    fun getWest(): Double
    fun getEast(): Double
}

internal external interface MapLibreMarker : JsAny {
    fun setLngLat(lngLat: JsAny): MapLibreMarker
    fun addTo(map: MapLibreMap): MapLibreMarker
    fun getElement(): HTMLElement
    fun remove(): MapLibreMarker
}

internal external interface WebResizeObserver : JsAny {
    fun observe(element: Element)
    fun disconnect()
}

internal fun loadMapLibreBundle(): Promise<JsAny> = js(
    """
    Promise.all([
      import(/* webpackChunkName: "maplibre" */ "maplibre-gl"),
      import(/* webpackChunkName: "maplibre" */ "maplibre-gl/dist/maplibre-gl.css")
    ]).then((modules) => modules[0])
    """,
)

internal fun createMapLibreMap(module: JsAny, options: JsAny): MapLibreMap =
    js("new module.Map(options)")

internal fun createMapLibreMarker(module: JsAny, options: JsAny): MapLibreMarker =
    js("new module.Marker(options)")

internal fun mapOptions(
    container: HTMLElement,
    style: JsAny,
    longitude: Double,
    latitude: Double,
    zoom: Double,
): JsAny = js(
    """({
        container: container,
        style: style,
        center: [longitude, latitude],
        zoom: zoom,
        attributionControl: false
    })""",
)

internal fun styleValue(value: String, isJson: Boolean): JsAny =
    js("isJson ? JSON.parse(value) : value")

internal fun markerOptions(color: String): JsAny = js("({ color: color })")

internal fun lngLat(longitude: Double, latitude: Double): JsAny =
    js("[longitude, latitude]")

internal fun flyToOptions(longitude: Double, latitude: Double, zoom: Double): JsAny =
    js("({ center: [longitude, latitude], zoom: zoom })")

internal fun eventLongitude(event: JsAny): Double = js("event.lngLat.lng")

internal fun eventLatitude(event: JsAny): Double = js("event.lngLat.lat")

internal fun createResizeObserver(callback: (JsAny) -> Unit): WebResizeObserver =
    js("new ResizeObserver(callback)")

internal fun yesterdayUtcDate(): String =
    js("new Date(Date.now() - 86400000).toISOString().slice(0, 10)")

internal fun requestBrowserLocation(): Promise<JsAny> = js(
    """
    new Promise((resolve, reject) => {
      if (!globalThis.navigator || !navigator.geolocation) {
        reject(new Error("Geolocation is unavailable in this browser."));
        return;
      }
      navigator.geolocation.getCurrentPosition(resolve, reject, {
        enableHighAccuracy: true,
        timeout: 10000,
        maximumAge: 60000
      });
    })
    """,
)

internal fun positionLatitude(position: JsAny): Double = js("position.coords.latitude")

internal fun positionLongitude(position: JsAny): Double = js("position.coords.longitude")
