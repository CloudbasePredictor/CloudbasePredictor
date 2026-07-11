package com.cloudbasepredictor.ui.screens.forecast

import com.cloudbasepredictor.data.remote.HourlyForecastData
import com.cloudbasepredictor.data.remote.HourlyPoint
import com.cloudbasepredictor.data.remote.PressureLevelPoint
import com.cloudbasepredictor.domain.forecast.CclMethod
import com.cloudbasepredictor.domain.forecast.ProfileLevel
import com.cloudbasepredictor.domain.forecast.dryAdiabatTempC
import com.cloudbasepredictor.domain.forecast.moistAdiabatTempFromPointC
import com.cloudbasepredictor.domain.forecast.potentialTemperatureK
import com.cloudbasepredictor.domain.forecast.satMixingRatioGKg
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ForecastChartBuildersStuveTest {

    @Test
    fun buildStuveChartFromData_usesRequestedHourAndSurfaceDataAsFirstProfilePoint() {
        val chart = buildStuveChartFromData(
            hourlyData = sampleHourlyForecastData(
                points = listOf(
                    sampleHourlyPoint(hour = 12, temperature2mC = 17.5, dewPoint2mC = 8.5),
                ),
            ),
            dayIndex = 0,
            hour = 12,
        )

        assertEquals(12, chart.selectedHour)
        assertEquals(948f, chart.surfacePressureHpa, 0.01f)
        assertEquals(948f, chart.temperatureProfile.first().pressureHpa, 0.01f)
        assertEquals(17.5f, chart.temperatureProfile.first().temperatureC, 0.01f)
        assertEquals(8.5f, chart.dewpointProfile.first().temperatureC, 0.01f)
        assertTrue(chart.temperatureProfile.first().isRealData)
        assertTrue(chart.dewpointProfile.first().isRealData)
    }

    @Test
    fun buildStuveChartFromData_fallsBackToNearestHourWhenRequestedHourMissing() {
        val chart = buildStuveChartFromData(
            hourlyData = sampleHourlyForecastData(
                points = listOf(
                    sampleHourlyPoint(hour = 11, temperature2mC = 15.0, dewPoint2mC = 6.0),
                    sampleHourlyPoint(hour = 14, temperature2mC = 19.0, dewPoint2mC = 9.0),
                ),
            ),
            dayIndex = 0,
            hour = 12,
        )

        assertEquals(
            12,
            chart.selectedHour,
            "Nearest sounding hour should still be surfaced as the requested Stuve hour",
        )
        assertEquals(15.0f, chart.temperatureProfile.first().temperatureC, 0.01f)
        assertEquals(6.0f, chart.dewpointProfile.first().temperatureC, 0.01f)
    }

    @Test
    fun buildStuveChartFromData_buildsUsableParcelAndMoistureOutputs() {
        val chart = buildStuveChartFromData(
            hourlyData = sampleHourlyForecastData(
                points = listOf(
                    sampleHourlyPoint(hour = 13, temperature2mC = 20.0, dewPoint2mC = 10.0),
                ),
            ),
            dayIndex = 0,
            hour = 13,
        )

        assertFalse(chart.parcelAscentPath.isEmpty())
        assertEquals(chart.surfacePressureHpa, chart.parcelAscentPath.first().pressureHpa, 0.01f)
        assertTrue(
            chart.parcelAscentPath.first().temperatureC >= chart.temperatureProfile.first().temperatureC,
            "Parcel start should reflect zero or positive heating relative to surface temperature",
        )
        assertFalse(chart.moistureBands.isEmpty())
        assertNotNull(chart.cclPressureHpa)
        assertNotNull(chart.tconC)
        assertEquals(
            setOf(CclMethod.SURFACE, CclMethod.ML50, CclMethod.ML100),
            chart.cclResults.map { it.method }.toSet(),
        )
        assertEquals(
            chart.cclResults.first { it.method == CclMethod.ML50 }.cclPressureHpa,
            chart.cclPressureHpa,
            "Stuve should expose the shared ML50 CCL as the primary marker",
        )
    }

    @Test
    fun buildParcelAscentPath_usesMoistAdiabatThroughLclInsteadOfDryParcelTheta() {
        val surfacePressureHpa = 948f
        val surfaceTemperatureC = 24f
        val surfaceDewPointC = 18f
        val parcelThetaK = potentialTemperatureK(surfaceTemperatureC, surfacePressureHpa)
        val surfaceMixingRatio = satMixingRatioGKg(surfaceDewPointC, surfacePressureHpa)
        val pressures = listOf(948f, 900f, 850f, 800f, 750f, 700f, 650f, 600f, 550f, 500f, 450f, 400f)

        val path = buildParcelAscentPath(
            pressures = pressures,
            profile = sampleProfileLevels(),
            surfaceTemperatureC = surfaceTemperatureC,
            surfaceDewPointC = surfaceDewPointC,
            surfacePressureHpa = surfacePressureHpa,
            surfaceHeatingC = 0f,
        )

        val lclIndex = path.indexOfFirst { point ->
            satMixingRatioGKg(point.temperatureC, point.pressureHpa) <= surfaceMixingRatio
        }
        assertTrue(lclIndex in 1 until path.lastIndex, "Path should include an LCL transition point")

        val lclPoint = path[lclIndex]
        val upperPoint = path.first { abs(it.pressureHpa - 400f) < 0.01f }
        val expectedUpperTemp = moistAdiabatTempFromPointC(
            startTemperatureC = lclPoint.temperatureC,
            startPressureHpa = lclPoint.pressureHpa,
            targetPressureHpa = upperPoint.pressureHpa,
        )
        val dryUpperTemp = dryAdiabatTempC(parcelThetaK, upperPoint.pressureHpa)

        assertEquals(expectedUpperTemp, upperPoint.temperatureC, 0.01f)
        assertTrue(
            upperPoint.temperatureC > dryUpperTemp + 5f,
            "Upper parcel branch should be meaningfully warmer than the dry adiabat above the LCL",
        )
    }

    @Test
    fun buildStuveChartFromData_fallsBackToPlaceholderWhenSurfaceDataMissing() {
        val chart = buildStuveChartFromData(
            hourlyData = sampleHourlyForecastData(
                points = listOf(
                    sampleHourlyPoint(
                        hour = 12,
                        temperature2mC = null,
                        dewPoint2mC = 7.0,
                    ),
                ),
            ),
            dayIndex = 0,
            hour = 12,
        )

        assertEquals(12, chart.selectedHour)
        assertTrue(
            kotlin.math.abs(chart.surfacePressureHpa - 948f) > 0.01f,
            "Placeholder chart should not reuse the 948 hPa sample surface pressure",
        )
    }

    private fun sampleHourlyForecastData(points: List<HourlyPoint>): HourlyForecastData {
        return HourlyForecastData(
            latitude = 47.0,
            longitude = 11.0,
            elevation = 580.0,
            hourlyPoints = points,
            dailyForecasts = emptyList(),
        )
    }

    private fun sampleHourlyPoint(
        hour: Int,
        temperature2mC: Double?,
        dewPoint2mC: Double?,
    ): HourlyPoint {
        return HourlyPoint(
            date = "2026-04-23",
            hour = hour,
            temperature2mC = temperature2mC,
            dewPoint2mC = dewPoint2mC,
            cloudCoverLowPercent = 15.0,
            cloudCoverMidPercent = 5.0,
            cloudCoverHighPercent = 0.0,
            precipitationMm = 0.0,
            precipitationProbabilityPercent = 0.0,
            windSpeed10mKmh = 12.0,
            windDirection10mDeg = 210.0,
            capeJKg = 250.0,
            freezingLevelHeightM = 2800.0,
            surfacePressureHpa = 948.0,
            shortwaveRadiationWm2 = 700.0,
            sunshineDurationS = 3600.0,
            isDay = 1.0,
            pressureLevels = listOf(
                pressureLevel(900, 14.0, 8.0, 1000.0),
                pressureLevel(850, 11.0, 5.0, 1450.0),
                pressureLevel(800, 8.0, 2.0, 1950.0),
                pressureLevel(750, 5.0, -1.0, 2460.0),
                pressureLevel(700, 1.0, -5.0, 3010.0),
                pressureLevel(650, -2.0, -8.0, 3590.0),
                pressureLevel(600, -6.0, -13.0, 4200.0),
                pressureLevel(550, -10.0, -18.0, 4860.0),
                pressureLevel(500, -15.0, -24.0, 5570.0),
            ),
        )
    }

    private fun sampleProfileLevels(): List<ProfileLevel> {
        return listOf(
            ProfileLevel(pressureHpa = 900f, temperatureC = 14f, dewPointC = 8f, heightKm = 1.0f),
            ProfileLevel(pressureHpa = 850f, temperatureC = 11f, dewPointC = 5f, heightKm = 1.45f),
            ProfileLevel(pressureHpa = 800f, temperatureC = 8f, dewPointC = 2f, heightKm = 1.95f),
            ProfileLevel(pressureHpa = 750f, temperatureC = 5f, dewPointC = -1f, heightKm = 2.46f),
            ProfileLevel(pressureHpa = 700f, temperatureC = 1f, dewPointC = -5f, heightKm = 3.01f),
            ProfileLevel(pressureHpa = 650f, temperatureC = -2f, dewPointC = -8f, heightKm = 3.59f),
            ProfileLevel(pressureHpa = 600f, temperatureC = -6f, dewPointC = -13f, heightKm = 4.2f),
            ProfileLevel(pressureHpa = 550f, temperatureC = -10f, dewPointC = -18f, heightKm = 4.86f),
            ProfileLevel(pressureHpa = 500f, temperatureC = -15f, dewPointC = -24f, heightKm = 5.57f),
            ProfileLevel(pressureHpa = 450f, temperatureC = -20f, dewPointC = -29f, heightKm = 6.34f),
            ProfileLevel(pressureHpa = 400f, temperatureC = -26f, dewPointC = -35f, heightKm = 7.18f),
        )
    }

    private fun pressureLevel(
        pressureHpa: Int,
        temperatureC: Double,
        dewPointC: Double,
        geopotentialHeightM: Double,
    ): PressureLevelPoint {
        return PressureLevelPoint(
            pressureHpa = pressureHpa,
            temperatureC = temperatureC,
            dewPointC = dewPointC,
            windSpeedKmh = 20.0 + (1000 - pressureHpa) / 20.0,
            windDirectionDeg = 220.0,
            geopotentialHeightM = geopotentialHeightM,
        )
    }
}
