package com.cloudbasepredictor.data.launch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LaunchSiteBoundsTest {
    @Test
    fun normalizedForMap_roundsVisibleBoundsToStableGrid() {
        val bounds = LaunchSiteBounds.normalizedForMap(
            north = 45.307,
            south = 45.021,
            west = 5.881,
            east = 6.112,
            zoom = 10.0,
        )

        requireNotNull(bounds)
        assertEquals(45.50, bounds.north, 0.0)
        assertEquals(45.00, bounds.south, 0.0)
        assertEquals(5.75, bounds.west, 0.0)
        assertEquals(6.25, bounds.east, 0.0)
        assertEquals("45.00:5.75:45.50:6.25", bounds.key)
    }

    @Test
    fun normalizedForMap_skipsLowZoomQueries() {
        val bounds = LaunchSiteBounds.normalizedForMap(
            north = 45.307,
            south = 45.021,
            west = 5.881,
            east = 6.112,
            zoom = 6.0,
        )

        assertNull(bounds)
    }
}
