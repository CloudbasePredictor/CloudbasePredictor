@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
// Detekt cannot see parameters referenced from Kotlin/Wasm js(...) bodies.
@file:Suppress("UnusedParameter")

package com.cloudbasepredictor.web

import kotlinx.browser.window

actual fun copyWebShareUrl(routeState: WebRouteState) {
    val fragment = WebRouteStateCodec.encodeFragment(routeState)
    val url = window.location.href.substringBefore('#') + fragment
    copyBrowserText(url)
}

private fun copyBrowserText(value: String): Unit = js(
    "void globalThis.navigator?.clipboard?.writeText(value)",
)
