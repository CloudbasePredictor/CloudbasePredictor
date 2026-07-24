package com.cloudbasepredictor.ui.screens.forecast

import com.cloudbasepredictor.data.remote.HourlyForecastData
import com.cloudbasepredictor.data.remote.HourlyPoint
import com.cloudbasepredictor.data.remote.PressureLevelPoint
import com.cloudbasepredictor.domain.forecast.CclMethod
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ForecastChartBuildersWindTest {

    @Test
    fun buildWindChartFromData_usesStablePressureAltitudesForCompleteCellGrid() {
        val chart = buildWindChartFromData(
            hourlyData = openMeteoFozaIconSeamlessWindData(),
            dayIndex = 0,
            maxAltitudeKm = 4.5f,
        )

        val cellsByHour = chart.cells.groupBy { it.hour }
        assertEquals(listOf(6, 8, 12, 16, 22), chart.hours)
        assertTrue(chart.altitudeBandsKm.size >= 6, "Regression data should include several altitude bands")

        chart.hours.forEach { hour ->
            val hourAltitudes = cellsByHour.getValue(hour).map { it.altitudeKm }.toSet()
            assertEquals(
                chart.altitudeBandsKm.toSet(),
                hourAltitudes,
                "Every visible wind-speed band should have a painted cell at ${hour}h",
            )
        }
        assertTrue(
            chart.cclResults.any { it.method == CclMethod.ML50 },
            "Wind chart should keep shared CCL diagnostics for all displayed hours",
        )
    }

    @Test
    fun buildWindChartFromData_cclIgnoresPressureLevelsBelowTerrain() {
        val elevationM = 1500.0
        val chart = buildWindChartFromData(
            hourlyData = HourlyForecastData(
                latitude = 46.0,
                longitude = 11.0,
                elevation = elevationM,
                hourlyPoints = listOf(
                    HourlyPoint(
                        date = "2026-04-28",
                        hour = 12,
                        temperature2mC = 18.0,
                        dewPoint2mC = 8.0,
                        cloudCoverLowPercent = 0.0,
                        cloudCoverMidPercent = 0.0,
                        cloudCoverHighPercent = 0.0,
                        precipitationMm = 0.0,
                        precipitationProbabilityPercent = 0.0,
                        windSpeed10mKmh = 8.0,
                        windDirection10mDeg = 220.0,
                        capeJKg = 0.0,
                        freezingLevelHeightM = 3200.0,
                        surfacePressureHpa = 850.0,
                        shortwaveRadiationWm2 = 700.0,
                        sunshineDurationS = 3600.0,
                        isDay = 1.0,
                        pressureLevels = listOf(
                            level(1000, -30.0, 5.0, 10.0, 220.0, 100.0),
                            level(975, -25.0, 4.0, 12.0, 220.0, 300.0),
                            level(950, -20.0, 3.0, 14.0, 220.0, 600.0),
                            level(925, -16.0, 2.0, 16.0, 220.0, 900.0),
                            level(900, -12.0, 1.0, 18.0, 220.0, 1200.0),
                            level(800, 10.0, 0.0, 20.0, 220.0, 2000.0),
                            level(750, 3.0, -5.0, 24.0, 220.0, 2600.0),
                            level(700, -3.0, -10.0, 30.0, 220.0, 3200.0),
                        ),
                    ),
                ),
                dailyForecasts = emptyList(),
            ),
            dayIndex = 0,
            maxAltitudeKm = 4.5f,
        )

        val ml50 = chart.cclResults.first { it.method == CclMethod.ML50 }
        assertTrue(
            ml50.intersections.all { it.heightMslM > elevationM.toFloat() },
            "CCL intersections must not come from pressure levels below terrain",
        )
    }

    @Test
    fun buildWindChartFromData_marksOnlyDirectModelLevels() {
        val chart = buildWindChartFromData(
            hourlyData = HourlyForecastData(
                latitude = 46.0,
                longitude = 11.0,
                elevation = 1_000.0,
                hourlyPoints = listOf(
                    openMeteoFozaHour(
                        hour = 12,
                        surfaceWindSpeed = 8.0,
                        surfaceWindDirection = 220.0,
                        pressureLevels = listOf(
                            level(800, 5.0, 0.0, 20.0, 220.0, 2_000.0),
                            level(
                                pressureHpa = 700,
                                temperatureC = -3.0,
                                dewPointC = -10.0,
                                windSpeedKmh = 30.0,
                                windDirectionDeg = 230.0,
                                geopotentialHeightM = 3_000.0,
                                isSynthetic = true,
                            ),
                        ),
                    ),
                ),
                dailyForecasts = emptyList(),
            ),
            dayIndex = 0,
            maxAltitudeKm = 4.5f,
        )

        assertTrue(2f in chart.modelLevelAltitudesKm)
        assertTrue(3f !in chart.modelLevelAltitudesKm)
        assertTrue(chart.cells.none { it.altitudeKm == 3f })
    }

    private fun openMeteoFozaIconSeamlessWindData(): HourlyForecastData {
        return HourlyForecastData(
            latitude = 45.82,
            longitude = 11.26,
            elevation = 735.0,
            hourlyPoints = listOf(
                openMeteoFozaHour(
                    hour = 6,
                    surfaceWindSpeed = 2.6,
                    surfaceWindDirection = 304.0,
                    pressureLevels = listOf(
                        level(925, 12.3, 10.5, 2.0, 284.0, 800.43),
                        level(900, 10.9, 9.1, 2.0, 252.0, 1029.90),
                        level(850, 8.2, 7.8, 3.3, 214.0, 1505.00),
                        level(800, 4.9, 4.1, 6.2, 253.0, 2003.57),
                        level(700, -1.8, -2.9, 14.3, 270.0, 3083.00),
                        level(600, -9.6, -12.0, 18.2, 262.0, 4291.00),
                    ),
                ),
                openMeteoFozaHour(
                    hour = 8,
                    surfaceWindSpeed = 2.3,
                    surfaceWindDirection = 342.0,
                    pressureLevels = listOf(
                        level(925, 12.7, 10.2, 0.8, 313.0, 793.67),
                        level(900, 11.3, 8.9, 0.9, 204.0, 1023.38),
                        level(850, 8.7, 6.8, 3.6, 180.0, 1499.00),
                        level(800, 5.2, 3.9, 4.4, 214.0, 1997.87),
                        level(700, -1.7, -2.4, 8.3, 243.0, 3078.00),
                        level(600, -9.8, -10.6, 19.0, 277.0, 4287.00),
                    ),
                ),
                openMeteoFozaHour(
                    hour = 12,
                    surfaceWindSpeed = 5.2,
                    surfaceWindDirection = 155.0,
                    pressureLevels = listOf(
                        level(925, 14.6, 11.4, 5.8, 166.0, 800.60),
                        level(900, 12.6, 9.5, 6.7, 172.0, 1031.28),
                        level(850, 8.6, 7.0, 8.7, 180.0, 1509.00),
                        level(800, 5.2, -0.6, 10.6, 211.0, 2006.97),
                        level(700, -1.5, -12.7, 18.9, 238.0, 3085.00),
                        level(600, -9.8, -12.8, 16.3, 252.0, 4293.00),
                    ),
                ),
                openMeteoFozaHour(
                    hour = 16,
                    surfaceWindSpeed = 3.4,
                    surfaceWindDirection = 72.0,
                    pressureLevels = listOf(
                        level(925, 15.5, 9.3, 3.1, 127.0, 792.07),
                        level(900, 13.5, 7.3, 5.1, 163.0, 1023.24),
                        level(850, 9.4, 4.4, 10.9, 182.0, 1502.00),
                        level(800, 5.7, 0.5, 12.6, 214.0, 2000.57),
                        level(700, -1.7, -6.9, 22.5, 246.0, 3080.00),
                        level(600, -9.4, -10.7, 28.6, 268.0, 4289.00),
                    ),
                ),
                openMeteoFozaHour(
                    hour = 22,
                    surfaceWindSpeed = 5.0,
                    surfaceWindDirection = 291.0,
                    pressureLevels = listOf(
                        level(925, 13.4, 7.6, 1.8, 255.0, 802.36),
                        level(900, 11.9, 6.2, 3.1, 159.0, 1032.81),
                        level(850, 8.8, 4.0, 10.3, 139.0, 1510.00),
                        level(800, 5.5, -4.6, 8.6, 191.0, 2007.67),
                        level(700, -1.2, -19.0, 21.1, 242.0, 3085.00),
                        level(600, -9.8, -13.6, 27.8, 270.0, 4294.00),
                    ),
                ),
            ),
            dailyForecasts = emptyList(),
        )
    }

    private fun openMeteoFozaHour(
        hour: Int,
        surfaceWindSpeed: Double,
        surfaceWindDirection: Double,
        pressureLevels: List<PressureLevelPoint>,
    ): HourlyPoint {
        return HourlyPoint(
            date = "2026-04-28",
            hour = hour,
            temperature2mC = 12.0,
            dewPoint2mC = 8.0,
            cloudCoverLowPercent = 0.0,
            cloudCoverMidPercent = 0.0,
            cloudCoverHighPercent = 0.0,
            precipitationMm = 0.0,
            precipitationProbabilityPercent = 0.0,
            windSpeed10mKmh = surfaceWindSpeed,
            windDirection10mDeg = surfaceWindDirection,
            capeJKg = 0.0,
            freezingLevelHeightM = 2840.0,
            surfacePressureHpa = 932.0,
            shortwaveRadiationWm2 = 0.0,
            sunshineDurationS = 0.0,
            isDay = 1.0,
            pressureLevels = pressureLevels,
        )
    }

    private fun level(
        pressureHpa: Int,
        temperatureC: Double,
        dewPointC: Double,
        windSpeedKmh: Double,
        windDirectionDeg: Double,
        geopotentialHeightM: Double,
        isSynthetic: Boolean = false,
    ): PressureLevelPoint {
        return PressureLevelPoint(
            pressureHpa = pressureHpa,
            temperatureC = temperatureC,
            dewPointC = dewPointC,
            windSpeedKmh = windSpeedKmh,
            windDirectionDeg = windDirectionDeg,
            geopotentialHeightM = geopotentialHeightM,
            isSynthetic = isSynthetic,
        )
    }
}
