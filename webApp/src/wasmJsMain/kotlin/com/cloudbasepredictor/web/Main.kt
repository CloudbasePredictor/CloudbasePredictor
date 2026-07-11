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
                routeState = newState
                window.location.hash = WebRouteStateCodec.encodeFragment(newState)
            },
        )
    }
}

internal fun findWebAppRoot(): Element = requireNotNull(document.getElementById(WEB_APP_ROOT_ID)) {
    "Missing #$WEB_APP_ROOT_ID application root"
}
