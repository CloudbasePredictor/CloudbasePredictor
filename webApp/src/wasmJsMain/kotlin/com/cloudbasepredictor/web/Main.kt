@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package com.cloudbasepredictor.web

import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import kotlinx.browser.document
import kotlinx.browser.window
import org.w3c.dom.Element
import org.w3c.dom.events.Event

internal const val WEB_APP_ROOT_ID: String = "cloudbase-app"

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    val environment = createWebAppEnvironment()
    ComposeViewport(findWebAppRoot()) {
        var routeState by remember {
            mutableStateOf(
                WebRouteStateCodec.decode(
                    fragment = window.location.hash,
                    defaultModel = environment.preferences.state.value.forecastModel,
                ),
            )
        }

        DisposableEffect(Unit) {
            val listener: (Event) -> Unit = {
                routeState = WebRouteStateCodec.decode(
                    fragment = window.location.hash,
                    defaultModel = environment.preferences.state.value.forecastModel,
                )
            }
            window.addEventListener("hashchange", listener)
            onDispose {
                window.removeEventListener("hashchange", listener)
                environment.close()
            }
        }

        CloudbaseWebApp(
            environment = environment,
            routeState = routeState,
            onNavigate = { newState ->
                val previous = routeState
                routeState = newState
                val fragment = WebRouteStateCodec.encodeFragment(newState)
                // Real navigations (destination/location changes) push a history entry; transient
                // view-state changes (model/mode/day/hour) replace it so Back leaves the screen in
                // one step instead of stepping through every chip tap. Both use the History API
                // rather than assigning `location.hash`, which would also fire `hashchange` and
                // double-apply the route we just set here.
                if (isRouteNavigation(previous, newState)) {
                    window.history.pushState(null, "", fragment)
                } else {
                    window.history.replaceState(null, "", fragment)
                }
            },
        )
    }
}

internal fun findWebAppRoot(): Element = requireNotNull(document.getElementById(WEB_APP_ROOT_ID)) {
    "Missing #$WEB_APP_ROOT_ID application root"
}
