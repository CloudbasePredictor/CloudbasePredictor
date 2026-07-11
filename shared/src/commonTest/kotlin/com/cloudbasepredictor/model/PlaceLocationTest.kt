package com.cloudbasepredictor.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PlaceLocationTest {
    @Test
    fun toRouteValue_formatsCoordinatesWithStablePrecision() {
        val location = PlaceLocation(
            latitude = 46.55823456,
            longitude = 7.83537654,
        )

        assertEquals("46.558235,7.835377", location.toRouteValue())
    }

    @Test
    fun toRouteValue_roundsDecimalHalfUpWithoutPlatformLocale() {
        val location = PlaceLocation(
            latitude = 1.0000005,
            longitude = -0.0000005,
        )

        assertEquals("1.000001,-0.000001", location.toRouteValue())
    }

    @Test
    fun fromRouteValue_parsesValidLocation() {
        val location = PlaceLocation.fromRouteValue("46.558235,7.835377")

        assertEquals(46.558235, location?.latitude ?: 0.0, 0.0)
        assertEquals(7.835377, location?.longitude ?: 0.0, 0.0)
    }

    @Test
    fun fromRouteValue_rejectsInvalidLocation() {
        assertNull(PlaceLocation.fromRouteValue("not-a-location"))
        assertNull(PlaceLocation.fromRouteValue("91.0,7.0"))
        assertNull(PlaceLocation.fromRouteValue("46.0,181.0"))
    }

    @Test
    fun toSavedPlace_usesCoordinatePlaceFormat() {
        val place = PlaceLocation(
            latitude = 46.55823456,
            longitude = 7.83537654,
        ).toSavedPlace()

        assertEquals("place:46.5582:7.8354", place.id)
        assertEquals("46.5582, 7.8354", place.name)
    }

    @Test
    fun toSavedPlace_usesOptionalRouteNameWhenPresent() {
        val place = PlaceLocation(
            latitude = 45.3069,
            longitude = 5.88806,
            name = "Saint Hilaire du Touvet",
        ).toSavedPlace()

        assertEquals("place:45.3069:5.8881", place.id)
        assertEquals("Saint Hilaire du Touvet", place.name)
    }
}
