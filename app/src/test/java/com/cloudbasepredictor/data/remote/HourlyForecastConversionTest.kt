package com.cloudbasepredictor.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlinx.serialization.json.Json

class HourlyForecastConversionTest {

    private val expectedPressureLevels = listOf(
        1000, 975, 950, 925, 900, 875, 850, 800, 750, 700,
        650, 600, 550, 500, 450, 400, 350, 300, 250,
    )

    @Test
    fun toHourlyForecastData_synthesizesMissingUpperPressureLevels() {
        val response = OpenMeteoHourlyForecastResponse(
            latitude = 47.66,
            longitude = 11.5,
            elevation = 1523.0,
            hourly = OpenMeteoHourlyResponse(
                time = listOf("2026-04-18T12:00"),
                temperature2m = listOf(10.8),
                dewPoint2m = listOf(1.8),
                surfacePressure = listOf(852.9),
                temperature1000hPa = listOf(18.8),
                temperature975hPa = listOf(17.4),
                temperature950hPa = listOf(15.9),
                temperature925hPa = listOf(14.1),
                temperature900hPa = listOf(12.4),
                temperature875hPa = listOf(10.6),
                temperature850hPa = listOf(8.8),
                temperature800hPa = listOf(4.4),
                temperature750hPa = listOf(0.0),
                temperature700hPa = listOf(-4.4),
                temperature650hPa = listOf(-6.9),
                temperature600hPa = listOf(-9.4),
                temperature550hPa = listOf(-14.2),
                temperature500hPa = listOf(-19.1),
                dewPoint1000hPa = listOf(9.3),
                dewPoint975hPa = listOf(8.0),
                dewPoint950hPa = listOf(6.6),
                dewPoint925hPa = listOf(5.5),
                dewPoint900hPa = listOf(3.8),
                dewPoint875hPa = listOf(2.4),
                dewPoint850hPa = listOf(1.0),
                dewPoint800hPa = listOf(0.0),
                dewPoint750hPa = listOf(-3.0),
                dewPoint700hPa = listOf(-6.1),
                dewPoint650hPa = listOf(-11.6),
                dewPoint600hPa = listOf(-17.0),
                dewPoint550hPa = listOf(-21.9),
                dewPoint500hPa = listOf(-26.7),
                windSpeed1000hPa = listOf(4.8),
                windSpeed975hPa = listOf(4.7),
                windSpeed950hPa = listOf(4.9),
                windSpeed925hPa = listOf(4.9),
                windSpeed900hPa = listOf(4.9),
                windSpeed875hPa = listOf(4.8),
                windSpeed850hPa = listOf(4.8),
                windSpeed800hPa = listOf(3.1),
                windSpeed750hPa = listOf(3.9),
                windSpeed700hPa = listOf(4.7),
                windSpeed650hPa = listOf(13.0),
                windSpeed600hPa = listOf(21.3),
                windSpeed550hPa = listOf(21.7),
                windSpeed500hPa = listOf(22.1),
                windDirection1000hPa = listOf(42.0),
                windDirection975hPa = listOf(45.0),
                windDirection950hPa = listOf(45.0),
                windDirection925hPa = listOf(44.0),
                windDirection900hPa = listOf(43.0),
                windDirection875hPa = listOf(42.0),
                windDirection850hPa = listOf(41.0),
                windDirection800hPa = listOf(13.0),
                windDirection750hPa = listOf(333.0),
                windDirection700hPa = listOf(293.0),
                windDirection650hPa = listOf(297.5),
                windDirection600hPa = listOf(302.0),
                windDirection550hPa = listOf(310.5),
                windDirection500hPa = listOf(319.0),
                geopotentialHeight1000hPa = listOf(176.0),
                geopotentialHeight975hPa = listOf(391.0),
                geopotentialHeight950hPa = listOf(610.0),
                geopotentialHeight925hPa = listOf(834.89),
                geopotentialHeight900hPa = listOf(1064.85),
                geopotentialHeight875hPa = listOf(1302.9),
                geopotentialHeight850hPa = listOf(1541.0),
                geopotentialHeight800hPa = listOf(2035.96),
                geopotentialHeight750hPa = listOf(2571.5),
                geopotentialHeight700hPa = listOf(3107.0),
                geopotentialHeight650hPa = listOf(3710.5),
                geopotentialHeight600hPa = listOf(4314.0),
                geopotentialHeight550hPa = listOf(5006.0),
                geopotentialHeight500hPa = listOf(5698.0),
            ),
        )

        val point = response.toHourlyForecastData().hourlyPoints.single()
        val pressureLevels = point.pressureLevels.map { it.pressureHpa }

        assertEquals(expectedPressureLevels, pressureLevels)

        val level450 = requireNotNull(point.pressureLevels.firstOrNull { it.pressureHpa == 450 })
        assertNotNull(level450)
        assertTrue(level450.temperatureC < -19.1)
        assertTrue(level450.dewPointC!! < level450.temperatureC)
        assertTrue(requireNotNull(level450.geopotentialHeightM) > 5698.0)
        val level250 = requireNotNull(point.pressureLevels.firstOrNull { it.pressureHpa == 250 })
        assertTrue(level250.temperatureC < level450.temperatureC)
        assertTrue(level250.dewPointC!! < level250.temperatureC)
        assertTrue(requireNotNull(level250.geopotentialHeightM) > requireNotNull(level450.geopotentialHeightM))
    }

    @Test
    fun toHourlyForecastData_mapsNewThermalDiagnosticsAndPressureFields() {
        val response = OpenMeteoHourlyForecastResponse(
            latitude = 47.66,
            longitude = 11.5,
            elevation = 1523.0,
            hourly = OpenMeteoHourlyResponse(
                time = listOf("2026-04-18T12:00"),
                temperature2m = listOf(10.8),
                dewPoint2m = listOf(1.8),
                cape = listOf(350.0),
                liftedIndex = listOf(-2.5),
                convectiveInhibition = listOf(45.0),
                boundaryLayerHeight = listOf(1750.0),
                temperature850hPa = listOf(8.8),
                dewPoint850hPa = listOf(1.0),
                relativeHumidity850hPa = listOf(61.0),
                cloudCover850hPa = listOf(35.0),
                windSpeed850hPa = listOf(14.0),
                windDirection850hPa = listOf(260.0),
                geopotentialHeight850hPa = listOf(1541.0),
            ),
        )

        val point = response.toHourlyForecastData().hourlyPoints.single()
        val level850 = requireNotNull(point.pressureLevels.firstOrNull { it.pressureHpa == 850 })

        assertEquals(-2.5, point.liftedIndexC!!, 0.0001)
        assertEquals(45.0, point.convectiveInhibitionJKg!!, 0.0001)
        assertEquals(1750.0, point.boundaryLayerHeightM!!, 0.0001)
        assertEquals(61.0, level850.relativeHumidityPercent!!, 0.0001)
        assertEquals(35.0, level850.cloudCoverPercent!!, 0.0001)
        assertEquals(false, level850.isSynthetic)
    }

    @Test
    fun toHourlyForecastData_preservesForecastTimezoneMetadata() {
        val response = OpenMeteoHourlyForecastResponse(
            latitude = 47.66,
            longitude = 11.5,
            elevation = 1523.0,
            utcOffsetSeconds = 7_200,
            timezone = "Europe/Berlin",
            hourly = OpenMeteoHourlyResponse(
                time = listOf("2026-04-18T12:00"),
                temperature2m = listOf(10.8),
                dewPoint2m = listOf(1.8),
            ),
        )

        val data = response.toHourlyForecastData()

        assertEquals(7_200, data.utcOffsetSeconds)
        assertEquals("Europe/Berlin", data.timezone)
    }

    @Test
    fun oldCachedHourlyForecastDataWithoutTimezoneMetadataStillDecodes() {
        val json = Json {
            ignoreUnknownKeys = true
            explicitNulls = false
        }

        val data = json.decodeFromString<HourlyForecastData>(
            """
            {
              "latitude": 47.66,
              "longitude": 11.5,
              "elevation": 1523.0,
              "hourlyPoints": [],
              "dailyForecasts": []
            }
            """.trimIndent(),
        )

        assertEquals(0, data.utcOffsetSeconds)
        assertNull(data.timezone)
    }

    @Test
    fun oldCachedJsonWithoutNewFieldsStillDecodes() {
        val json = Json {
            ignoreUnknownKeys = true
            explicitNulls = false
        }
        val response = json.decodeFromString<OpenMeteoHourlyForecastResponse>(
            """
            {
              "latitude": 47.66,
              "longitude": 11.5,
              "elevation": 1523.0,
              "hourly": {
                "time": ["2026-04-18T12:00"],
                "temperature_2m": [10.8],
                "dew_point_2m": [1.8],
                "temperature_850hPa": [8.8],
                "dew_point_850hPa": [1.0],
                "geopotential_height_850hPa": [1541.0]
              }
            }
            """.trimIndent(),
        )

        val point = response.toHourlyForecastData().hourlyPoints.single()
        val level850 = requireNotNull(point.pressureLevels.firstOrNull { it.pressureHpa == 850 })
        assertNull(point.liftedIndexC)
        assertNull(point.convectiveInhibitionJKg)
        assertNull(point.boundaryLayerHeightM)
        assertNull(level850.relativeHumidityPercent)
        assertNull(level850.cloudCoverPercent)
    }
}
