package com.cloudbasepredictor.model

import kotlin.test.Test
import kotlin.test.assertEquals

class ManualFavoriteInputTest {
    @Test
    fun parseManualFavoriteInput_acceptsDecimalDegrees() {
        val input = validInput(name = "Zurich", coordinates = "47.3769, 8.5417")

        assertEquals("Zurich", input.name)
        assertEquals(47.3769, input.latitude, 0.0000001)
        assertEquals(8.5417, input.longitude, 0.0000001)
    }

    @Test
    fun parseManualFavoriteInput_acceptsDecimalCommaCoordinates() {
        val input = validInput(name = "Kronplatz", coordinates = "46,7399 11,9573")

        assertEquals(46.7399, input.latitude, 0.0000001)
        assertEquals(11.9573, input.longitude, 0.0000001)
    }

    @Test
    fun parseManualFavoriteInput_acceptsLabelledCoordinatesWithExtraNumbers() {
        val input = validInput(name = "Annecy", coordinates = "lat=45.8992 lon=6.1294 altitude=447")

        assertEquals(45.8992, input.latitude, 0.0000001)
        assertEquals(6.1294, input.longitude, 0.0000001)
    }

    @Test
    fun parseManualFavoriteInput_acceptsCardinalDirectionCoordinates() {
        val input = validInput(name = "Cape Town", coordinates = "33.9249 S, 18.4241 E")

        assertEquals(-33.9249, input.latitude, 0.0000001)
        assertEquals(18.4241, input.longitude, 0.0000001)
    }

    @Test
    fun parseManualFavoriteInput_acceptsDmsCoordinates() {
        val input = validInput(name = "Zurich", coordinates = "47 22 36.8 N 8 32 30.1 E")

        assertEquals(47.3768889, input.latitude, 0.0000001)
        assertEquals(8.5416944, input.longitude, 0.0000001)
    }

    @Test
    fun parseManualFavoriteInput_acceptsReversedCardinalOrder() {
        val input = validInput(name = "Zurich", coordinates = "E 8.5417 N 47.3769")

        assertEquals(47.3769, input.latitude, 0.0000001)
        assertEquals(8.5417, input.longitude, 0.0000001)
    }

    @Test
    fun parseManualFavoriteInput_normalizesNameWhitespace() {
        val input = validInput(name = "  Brauneck   South  ", coordinates = "47.6468, 11.5216")

        assertEquals("Brauneck South", input.name)
    }

    @Test
    fun parseManualFavoriteInput_rejectsBlankName() {
        assertEquals(
            ManualFavoriteInputError.BLANK_NAME,
            invalidInput(name = " ", coordinates = "47.3769, 8.5417"),
        )
    }

    @Test
    fun parseManualFavoriteInput_rejectsBlankCoordinates() {
        assertEquals(
            ManualFavoriteInputError.BLANK_COORDINATES,
            invalidInput(name = "Zurich", coordinates = " "),
        )
    }

    @Test
    fun parseManualFavoriteInput_rejectsAmbiguousCoordinates() {
        assertEquals(
            ManualFavoriteInputError.COORDINATES_FORMAT,
            invalidInput(name = "Zurich", coordinates = "47.3769, 8.5417, 1200"),
        )
    }

    @Test
    fun parseManualFavoriteInput_rejectsLatitudeOutOfRange() {
        assertEquals(
            ManualFavoriteInputError.LATITUDE_OUT_OF_RANGE,
            invalidInput(name = "Invalid", coordinates = "91.0, 8.5417"),
        )
    }

    @Test
    fun parseManualFavoriteInput_rejectsLongitudeOutOfRange() {
        assertEquals(
            ManualFavoriteInputError.LONGITUDE_OUT_OF_RANGE,
            invalidInput(name = "Invalid", coordinates = "47.3769, 181.0"),
        )
    }

    @Test
    fun toSavedPlace_usesCoordinateIdAndManualName() {
        val place = validInput(name = "Manual Zurich", coordinates = "47.37694, 8.54173").toSavedPlace()

        assertEquals("place:47.3769:8.5417", place.id)
        assertEquals("Manual Zurich", place.name)
        assertEquals(true, place.isFavorite)
    }

    private fun validInput(name: String, coordinates: String): ManualFavoriteInput {
        val result = parseManualFavoriteInput(name = name, coordinates = coordinates)
        return (result as ManualFavoriteInputResult.Valid).input
    }

    private fun invalidInput(name: String, coordinates: String): ManualFavoriteInputError {
        val result = parseManualFavoriteInput(name = name, coordinates = coordinates)
        return (result as ManualFavoriteInputResult.Invalid).error
    }
}
