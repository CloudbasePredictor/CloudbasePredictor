package com.cloudbasepredictor.ui.screens.map

import org.junit.Assert.assertEquals
import org.junit.Test

class MapViewModelLaunchSitesTest {
    @Test
    fun shouldRequestLaunchSites_returnsFalseWhenLaunchSitesAreDisabled() {
        assertEquals(
            false,
            shouldRequestLaunchSites(
                showLaunchSites = false,
                boundsKey = "45.00:5.75:45.50:6.25",
                lastBoundsKey = null,
            ),
        )
    }

    @Test
    fun shouldRequestLaunchSites_returnsFalseForSameBoundsKey() {
        assertEquals(
            false,
            shouldRequestLaunchSites(
                showLaunchSites = true,
                boundsKey = "45.00:5.75:45.50:6.25",
                lastBoundsKey = "45.00:5.75:45.50:6.25",
            ),
        )
    }

    @Test
    fun shouldRequestLaunchSites_returnsTrueForEnabledNewBoundsKey() {
        assertEquals(
            true,
            shouldRequestLaunchSites(
                showLaunchSites = true,
                boundsKey = "45.00:5.75:45.50:6.25",
                lastBoundsKey = "46.00:6.75:46.50:7.25",
            ),
        )
    }
}
