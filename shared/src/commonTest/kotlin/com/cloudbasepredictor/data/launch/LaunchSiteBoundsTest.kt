package com.cloudbasepredictor.data.launch

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

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
    }

    @Test
    fun key_isPlatformIndependentGridIndexKey() {
        val bounds = LaunchSiteBounds(north = 45.50, south = 45.00, west = 5.75, east = 6.25)

        // south:west:north:east grid indices at the 0.25 degree grid size.
        assertEquals("180:23:182:25", bounds.key)
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

    @Test
    fun normalizedForMap_skipsQueriesWiderThanMaxSpan() {
        val bounds = LaunchSiteBounds.normalizedForMap(
            north = 50.0,
            south = 45.0,
            west = 5.0,
            east = 6.0,
            zoom = 10.0,
        )

        assertNull(bounds)
    }

    @Test
    fun normalizedForMap_rejectsInvalidCoordinates() {
        val bounds = LaunchSiteBounds.normalizedForMap(
            north = Double.NaN,
            south = 45.0,
            west = 5.0,
            east = 6.0,
            zoom = 10.0,
        )

        assertNull(bounds)
    }

    @Test
    fun contains_reflectsPointMembership() {
        val bounds = LaunchSiteBounds(north = 47.0, south = 46.0, west = 7.0, east = 8.0)

        assertTrue(bounds.contains(latitude = 46.5, longitude = 7.5))
        assertFalse(bounds.contains(latitude = 45.9, longitude = 7.5))
        assertFalse(bounds.contains(latitude = 46.5, longitude = 8.1))
    }

    @Test
    fun intersects_detectsOverlapAndDisjointBoxes() {
        val viewport = LaunchSiteBounds(north = 47.0, south = 46.0, west = 7.0, east = 8.0)
        val overlappingTile = LaunchSiteBounds(north = 48.0, south = 46.0, west = 8.0, east = 10.0)
        val disjointTile = LaunchSiteBounds(north = 54.0, south = 52.0, west = 12.0, east = 14.0)

        assertTrue(viewport.intersects(overlappingTile))
        assertTrue(overlappingTile.intersects(viewport))
        assertFalse(viewport.intersects(disjointTile))
    }
}
