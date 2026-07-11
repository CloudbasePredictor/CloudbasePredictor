package com.cloudbasepredictor.web

import kotlinx.browser.document
import kotlinx.browser.window
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class BrowserSmokeTest {
    @Test
    fun applicationRootAndRouteAdapterRunInABrowser() {
        assertNotNull(document.body)
        assertTrue(window.navigator.userAgent.isNotBlank())

        val root = document.createElement("div").apply { id = WEB_APP_ROOT_ID }
        document.body?.appendChild(root)
        try {
            assertSame(root, findWebAppRoot())
            assertEquals(
                WebRouteState(WebDestination.Map),
                WebRouteStateCodec.decode("#/map"),
            )
        } finally {
            root.parentNode?.removeChild(root)
        }
    }
}
