package com.cloudbasepredictor.data.remote

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ParaglidingEarthFeatureTest {
    @Test
    fun toDomainModel_mapsDetailedGeoJsonProperties() {
        val site = detailedFeature().toDomainModel()

        assertNotNull(site)
        requireNotNull(site)
        assertEquals("6176", site.id)
        assertEquals("Saint Hilaire du Touvet", site.name)
        assertEquals(45.3069, site.latitude, 0.0)
        assertEquals(5.88806, site.longitude, 0.0)
        assertEquals(906, site.altitudeMeters)
        assertEquals("fr", site.countryCode)
        assertEquals("North launch. South launch.", site.description)
        assertEquals("https://www.paraglidingearth.com/?site=6176", site.link)
        assertEquals(listOf("Paragliding", "Hang gliding", "Thermals", "XC"), site.activities)
        assertEquals("E", site.orientations.single { it.rating == 2 }.direction)
        assertEquals("Lumbin", site.landingName)
        assertEquals(45.302, site.landingLatitude ?: 0.0, 0.0)
        assertEquals(5.90612, site.landingLongitude ?: 0.0, 0.0)
    }

    @Test
    fun toDomainModel_rejectsMissingCoordinates() {
        val site = detailedFeature()
            .copy(geometry = ParaglidingEarthGeometry(coordinates = listOf(5.88806)))
            .toDomainModel()

        assertNull(site)
    }

    private fun detailedFeature(): ParaglidingEarthFeature {
        return ParaglidingEarthFeature(
            id = "6176",
            geometry = ParaglidingEarthGeometry(coordinates = listOf(5.88806, 45.3069)),
            properties = ParaglidingEarthProperties(
                name = "Saint Hilaire du Touvet",
                countryCode = "fr",
                takeoffAltitude = "906",
                takeoffDescription = "North launch.\r\nSouth launch.",
                pgeSiteId = "6176",
                pgeLink = "http://www.paraglidingearth.com/?site=6176",
                north = "1",
                east = "2",
                south = "0",
                paragliding = "1",
                hanggliding = "1",
                thermals = "1",
                xc = "1",
                landing = ParaglidingEarthLanding(
                    landingName = "Lumbin",
                    landingLat = "45.302",
                    landingLng = "5.90612",
                ),
            ),
        )
    }
}
