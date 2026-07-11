package com.cloudbasepredictor.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SavedPlaceTest {
    @Test
    fun fromCoordinates_formatsIdAndNameWithFourDecimals() {
        val place = SavedPlace.fromCoordinates(
            latitude = 46.55823,
            longitude = 7.83537,
        )

        assertEquals("place:46.5582:7.8354", place.id)
        assertEquals("46.5582, 7.8354", place.name)
        assertEquals(46.55823, place.latitude, 0.0)
        assertEquals(7.83537, place.longitude, 0.0)
    }

    @Test
    fun fromCoordinates_roundsDecimalHalfUpWithoutPlatformLocale() {
        val place = SavedPlace.fromCoordinates(
            latitude = 1.00005,
            longitude = -0.00005,
        )

        assertEquals("place:1.0001:-0.0001", place.id)
        assertEquals("1.0001, -0.0001", place.name)
    }

    @Test
    fun isNearby_returnsTrueForSameCoordinates() {
        val innsbruck = SavedPlace(
            id = "innsbruck",
            name = "Innsbruck",
            latitude = 47.2692,
            longitude = 11.4041,
            isFavorite = true,
        )
        assertTrue(innsbruck.isNearby(47.2692, 11.4041))
    }

    @Test
    fun isNearby_returnsTrueWithin200m() {
        val innsbruck = SavedPlace(
            id = "innsbruck",
            name = "Innsbruck",
            latitude = 47.2692,
            longitude = 11.4041,
            isFavorite = true,
        )
        // ~100m offset in latitude (~0.0009°)
        assertTrue(innsbruck.isNearby(47.2701, 11.4041))
    }

    @Test
    fun isNearby_returnsFalseWhenFarAway() {
        val innsbruck = SavedPlace(
            id = "innsbruck",
            name = "Innsbruck",
            latitude = 47.2692,
            longitude = 11.4041,
            isFavorite = true,
        )
        // ~5km away
        assertFalse(innsbruck.isNearby(47.31, 11.4041))
    }
}
