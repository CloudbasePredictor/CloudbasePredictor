package com.cloudbasepredictor.data.forecast

import com.cloudbasepredictor.data.remote.HourlyForecastData
import com.cloudbasepredictor.data.remote.HourlyPoint
import com.cloudbasepredictor.model.DailyForecast
import com.cloudbasepredictor.model.ForecastModel
import com.cloudbasepredictor.model.ForecastSnapshot
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ForecastCachePolicyTest {

    @Test
    fun nextExpectedModelUpdateMillis_usesModelRunBoundaryInsteadOfFetchTimeTtl() {
        val fetchedAtMillis = utcMillis("2026-06-04 11:55")

        val nextUpdateMillis = nextExpectedModelUpdateMillis(
            fetchedAtMillis = fetchedAtMillis,
            model = ForecastModel.ICON_D2,
        )

        assertEquals(utcMillis("2026-06-04 12:00"), nextUpdateMillis)
    }

    @Test
    fun isForecastCacheUsable_returnsFalseAtExpectedModelUpdate() {
        val fetchedAtMillis = utcMillis("2026-06-04 11:55")
        val snapshot = forecastSnapshot(
            firstDate = "2026-06-04",
            fetchedAtMillis = fetchedAtMillis,
            model = ForecastModel.ICON_D2,
        )

        assertTrue(
            isForecastCacheUsable(
                snapshot = snapshot,
                requestedModel = ForecastModel.ICON_D2,
                minimumForecastDays = 1,
                nowMillis = utcMillis("2026-06-04 11:59"),
            ),
        )
        assertFalse(
            isForecastCacheUsable(
                snapshot = snapshot,
                requestedModel = ForecastModel.ICON_D2,
                minimumForecastDays = 1,
                nowMillis = utcMillis("2026-06-04 12:00"),
            ),
        )
    }

    @Test
    fun isForecastCacheUsable_returnsFalseWhenForecastStartsBeforeLocalToday() {
        val nowMillis = utcMillis("2026-06-05 06:30")
        val snapshot = forecastSnapshot(
            firstDate = "2026-06-04",
            fetchedAtMillis = nowMillis,
            model = ForecastModel.ICON_D2,
            timezone = "Europe/Berlin",
            utcOffsetSeconds = 2 * 3_600,
        )

        assertFalse(
            isForecastCacheUsable(
                snapshot = snapshot,
                requestedModel = ForecastModel.ICON_D2,
                minimumForecastDays = 1,
                nowMillis = nowMillis,
            ),
        )
    }

    @Test
    fun isForecastCacheUsable_returnsFalseWhenForecastDayCountIsInsufficient() {
        val nowMillis = utcMillis("2026-06-04 10:30")
        val snapshot = forecastSnapshot(
            firstDate = "2026-06-04",
            fetchedAtMillis = nowMillis,
            model = ForecastModel.ICON_D2,
            forecastDays = 1,
        )

        assertFalse(
            isForecastCacheUsable(
                snapshot = snapshot,
                requestedModel = ForecastModel.ICON_D2,
                minimumForecastDays = 2,
                nowMillis = nowMillis,
            ),
        )
    }

    @Test
    fun nextForecastCacheRefreshMillis_usesLocalMidnightWhenEarlierThanModelUpdate() {
        val fetchedAtMillis = utcMillis("2026-06-04 21:30")
        val snapshot = forecastSnapshot(
            firstDate = "2026-06-04",
            fetchedAtMillis = fetchedAtMillis,
            model = ForecastModel.ECMWF_IFS,
            timezone = "Europe/Berlin",
            utcOffsetSeconds = 2 * 3_600,
        )

        val refreshMillis = nextForecastCacheRefreshMillis(
            snapshot = snapshot,
            requestedModel = ForecastModel.ECMWF_IFS,
            nowMillis = fetchedAtMillis,
        )

        assertEquals(utcMillis("2026-06-04 22:00"), refreshMillis)
    }

    private fun forecastSnapshot(
        firstDate: String,
        fetchedAtMillis: Long,
        model: ForecastModel,
        forecastDays: Int = 1,
        timezone: String = "UTC",
        utcOffsetSeconds: Int = 0,
    ): ForecastSnapshot {
        val days = List(forecastDays) { index ->
            DailyForecast(
                date = offsetDate(firstDate, index),
                maxTemperatureCelsius = 20.0 + index,
                minTemperatureCelsius = 10.0 + index,
                weatherCode = 1,
            )
        }
        val hourlyData = HourlyForecastData(
            latitude = 47.0,
            longitude = 11.0,
            elevation = 500.0,
            hourlyPoints = days.map { day ->
                HourlyPoint(
                    date = day.date,
                    hour = 12,
                    temperature2mC = 20.0,
                    dewPoint2mC = 10.0,
                    cloudCoverLowPercent = null,
                    cloudCoverMidPercent = null,
                    cloudCoverHighPercent = null,
                    precipitationMm = null,
                    precipitationProbabilityPercent = null,
                    windSpeed10mKmh = 10.0,
                    windDirection10mDeg = 180.0,
                    capeJKg = null,
                    freezingLevelHeightM = null,
                    pressureLevels = emptyList(),
                )
            },
            dailyForecasts = days,
            utcOffsetSeconds = utcOffsetSeconds,
            timezone = timezone,
        )
        return ForecastSnapshot(
            days = days,
            updatedAtUtcMillis = fetchedAtMillis,
            hourlyData = hourlyData,
            resolvedModel = model,
            forecastDays = forecastDays,
            modelGeneratedAtMillis = estimateModelRunTimeInternal(fetchedAtMillis, model),
        )
    }

    private fun offsetDate(
        firstDate: String,
        offsetDays: Int,
    ): String {
        val format = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
            timeZone = UTC
        }
        val calendar = java.util.Calendar.getInstance(UTC, Locale.US).apply {
            time = requireNotNull(format.parse(firstDate))
            add(java.util.Calendar.DAY_OF_MONTH, offsetDays)
        }
        return format.format(calendar.time)
    }

    private fun utcMillis(value: String): Long {
        return requireNotNull(
            SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).apply {
                timeZone = UTC
                isLenient = false
            }.parse(value),
        ).time
    }

    private companion object {
        val UTC: TimeZone = TimeZone.getTimeZone("UTC")
    }
}
