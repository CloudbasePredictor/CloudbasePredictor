@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package com.cloudbasepredictor.web.i18n

/** The browser's preferred language tag (e.g. "de-DE"), or null when unavailable. */
internal fun browserLanguageTag(): String? =
    browserLanguageTagOrEmpty().takeIf { it.isNotBlank() }

private fun browserLanguageTagOrEmpty(): String =
    js("(globalThis.navigator && navigator.language) ? String(navigator.language) : ''")
