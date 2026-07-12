@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package com.cloudbasepredictor.web.launch

/**
 * Absolute, same-origin base URL for the launch-site snapshot, resolved against `document.baseURI`.
 * Using the document base (rather than a leading-slash path) keeps requests correct both locally and
 * under the GitHub Pages `/CloudbasePredictor/` base path.
 */
internal fun browserLaunchSiteSnapshotBaseUrl(): String =
    js("new URL('data/launch-sites/', document.baseURI).href")
