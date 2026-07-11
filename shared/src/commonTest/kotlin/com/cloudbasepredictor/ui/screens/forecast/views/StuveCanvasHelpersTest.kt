package com.cloudbasepredictor.ui.screens.forecast.views

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StuveCanvasHelpersTest {

    @Test
    fun layoutBottomAxisLabelCenters_keepsRightPackedLabelsInsideBoundsAndSeparated() {
        val centers = layoutBottomAxisLabelCenters(
            preferredCenters = listOf(170f, 170f, 170f),
            widths = listOf(40f, 40f, 70f),
            left = 0f,
            right = 180f,
            minimumGapPx = 10f,
        )

        assertEquals(3, centers.size)
        assertTrue(centers.first() - 20f >= 0f)
        assertTrue(centers.last() + 35f <= 180f)
        assertTrue(centers[1] - 20f >= centers[0] + 20f + 10f)
        assertTrue(centers[2] - 35f >= centers[1] + 20f + 10f)
    }

    @Test
    fun layoutBottomAxisLabelCenters_reducesGapWhenLabelsBarelyFit() {
        val centers = layoutBottomAxisLabelCenters(
            preferredCenters = listOf(90f, 90f, 90f),
            widths = listOf(50f, 50f, 50f),
            left = 0f,
            right = 160f,
            minimumGapPx = 12f,
        )

        assertEquals(3, centers.size)
        assertTrue(centers.first() - 25f >= 0f)
        assertTrue(centers.last() + 25f <= 160f)
        assertTrue(centers[1] - 25f >= centers[0] + 25f + 5f)
        assertTrue(centers[2] - 25f >= centers[1] + 25f + 5f)
    }

    @Test
    fun windBarbSpeedParts_roundsKmhToStandardFiveKnotSymbols() {
        assertEquals(
            WindBarbSpeedParts(
                roundedKnots = 5,
                flags = 0,
                fullFeathers = 0,
                halfFeathers = 1,
            ),
            windBarbSpeedParts(speedKmh = 10f),
        )
        assertEquals(
            WindBarbSpeedParts(
                roundedKnots = 15,
                flags = 0,
                fullFeathers = 1,
                halfFeathers = 1,
            ),
            windBarbSpeedParts(speedKmh = 28f),
        )
        assertEquals(
            WindBarbSpeedParts(
                roundedKnots = 50,
                flags = 1,
                fullFeathers = 0,
                halfFeathers = 0,
            ),
            windBarbSpeedParts(speedKmh = 93f),
        )
    }

    @Test
    fun buildWindBarbGeometry_pointsShaftEndTowardDirectionWindComesFrom() {
        val northWind = windBarbGeometry(directionDeg = 0f)
        assertTrue(northWind.shaft.end.y < northWind.shaft.start.y)

        val eastWind = windBarbGeometry(directionDeg = 90f)
        assertTrue(eastWind.shaft.end.x > eastWind.shaft.start.x)

        val southWind = windBarbGeometry(directionDeg = 180f)
        assertTrue(southWind.shaft.end.y > southWind.shaft.start.y)

        val westWind = windBarbGeometry(directionDeg = 270f)
        assertTrue(westWind.shaft.end.x < westWind.shaft.start.x)
    }

    @Test
    fun buildWindBarbGeometry_attachesFeathersAtWindSourceEnd() {
        val northWind = windBarbGeometry(directionDeg = 0f, speedKmh = 28f)

        val firstFeather = northWind.feathers.first()
        assertEquals(northWind.shaft.end.x, firstFeather.start.x, 0.01f)
        assertEquals(northWind.shaft.end.y, firstFeather.start.y, 0.01f)
        assertTrue(firstFeather.end.y > firstFeather.start.y)
        assertTrue(firstFeather.end.x > firstFeather.start.x)
    }

    @Test
    fun buildWindBarbGeometry_usesCalmCircleForSubFiveKnotWind() {
        val calmWind = windBarbGeometry(directionDeg = 0f, speedKmh = 3f)

        assertTrue(calmWind.calmRadius != null)
        assertTrue(calmWind.flags.isEmpty())
        assertTrue(calmWind.feathers.isEmpty())
    }

    private fun windBarbGeometry(
        directionDeg: Float,
        speedKmh: Float = 28f,
    ) = buildWindBarbGeometry(
        centerX = 50f,
        centerY = 50f,
        speedKmh = speedKmh,
        directionDeg = directionDeg,
        barbSize = 20f,
    )
}
