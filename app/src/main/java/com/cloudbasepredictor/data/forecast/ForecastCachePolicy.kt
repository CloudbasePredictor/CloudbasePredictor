package com.cloudbasepredictor.data.forecast

import com.cloudbasepredictor.data.remote.HourlyForecastData
import com.cloudbasepredictor.model.ForecastModel
import com.cloudbasepredictor.model.ForecastSnapshot
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.abs
import kotlin.math.min

internal fun isForecastCacheUsable(
    snapshot: ForecastSnapshot,
    requestedModel: ForecastModel,
    minimumForecastDays: Int,
    nowMillis: Long,
): Boolean {
    if (snapshot.forecastDays < minimumForecastDays) return false
    return !isForecastCacheStale(
        snapshot = snapshot,
        requestedModel = requestedModel,
        nowMillis = nowMillis,
    )
}

internal fun isForecastCacheStale(
    snapshot: ForecastSnapshot,
    requestedModel: ForecastModel,
    nowMillis: Long,
): Boolean {
    if (nowMillis >= nextExpectedModelUpdateMillis(snapshot, requestedModel)) return true
    return isFirstForecastDateBeforeToday(snapshot, nowMillis)
}

internal fun nextForecastCacheRefreshMillis(
    snapshot: ForecastSnapshot,
    requestedModel: ForecastModel,
    nowMillis: Long,
): Long {
    val modelUpdateMillis = nextExpectedModelUpdateMillis(snapshot, requestedModel)
    val dateInvalidationMillis = nextForecastDateInvalidationMillis(snapshot, nowMillis)
        ?: Long.MAX_VALUE
    return min(modelUpdateMillis, dateInvalidationMillis)
}

internal fun nextExpectedModelUpdateMillis(
    snapshot: ForecastSnapshot,
    requestedModel: ForecastModel,
): Long {
    val model = snapshot.resolvedModel ?: requestedModel
    val modelRunMillis = snapshot.modelGeneratedAtMillis
        ?: estimateModelRunTimeInternal(snapshot.updatedAtUtcMillis, model)
    return modelRunMillis + model.updateIntervalMillis
}

internal fun nextExpectedModelUpdateMillis(
    fetchedAtMillis: Long,
    model: ForecastModel,
): Long {
    return estimateModelRunTimeInternal(fetchedAtMillis, model) + model.updateIntervalMillis
}

private fun isFirstForecastDateBeforeToday(
    snapshot: ForecastSnapshot,
    nowMillis: Long,
): Boolean {
    val firstDate = snapshot.firstForecastDate() ?: return false
    val hourlyData = snapshot.hourlyData ?: return false
    val today = formatForecastDate(
        timestampMillis = nowMillis,
        timeZone = resolveForecastTimeZone(hourlyData),
    )
    return firstDate < today
}

private fun nextForecastDateInvalidationMillis(
    snapshot: ForecastSnapshot,
    nowMillis: Long,
): Long? {
    val firstDate = snapshot.firstForecastDate() ?: return null
    val hourlyData = snapshot.hourlyData ?: return null
    val timeZone = resolveForecastTimeZone(hourlyData)
    val today = formatForecastDate(nowMillis, timeZone)
    if (firstDate < today) return nowMillis

    val staleDateStartMillis = startOfForecastDateMillis(firstDate, timeZone)
        ?: return nextLocalMidnightMillis(nowMillis, timeZone)
    val calendar = Calendar.getInstance(timeZone, Locale.US).apply {
        timeInMillis = staleDateStartMillis
        add(Calendar.DAY_OF_MONTH, 1)
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    return calendar.timeInMillis
}

private fun ForecastSnapshot.firstForecastDate(): String? {
    return days.firstOrNull()?.date
        ?: hourlyData?.hourlyPoints?.minByOrNull { point -> point.date }?.date
}

private fun formatForecastDate(
    timestampMillis: Long,
    timeZone: TimeZone,
): String {
    return SimpleDateFormat(FORECAST_DATE_PATTERN, Locale.US).apply {
        this.timeZone = timeZone
    }.format(Date(timestampMillis))
}

private fun startOfForecastDateMillis(
    date: String,
    timeZone: TimeZone,
): Long? {
    return runCatching {
        SimpleDateFormat(FORECAST_DATE_PATTERN, Locale.US).apply {
            this.timeZone = timeZone
            isLenient = false
        }.parse(date)?.time
    }.getOrNull()
}

private fun nextLocalMidnightMillis(
    nowMillis: Long,
    timeZone: TimeZone,
): Long {
    return Calendar.getInstance(timeZone, Locale.US).apply {
        timeInMillis = nowMillis
        add(Calendar.DAY_OF_MONTH, 1)
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
}

private fun resolveForecastTimeZone(hourlyData: HourlyForecastData): TimeZone {
    hourlyData.timezone
        ?.takeIf(String::isNotBlank)
        ?.let(::validTimeZoneOrNull)
        ?.let { return it }

    return TimeZone.getTimeZone(formatUtcOffsetTimeZoneId(hourlyData.utcOffsetSeconds))
}

private fun validTimeZoneOrNull(timeZoneId: String): TimeZone? {
    val timeZone = TimeZone.getTimeZone(timeZoneId)
    return when {
        timeZone.id == timeZoneId -> timeZone
        timeZoneId.equals("UTC", ignoreCase = true) -> timeZone
        timeZoneId.startsWith("GMT", ignoreCase = true) -> timeZone
        else -> null
    }
}

private fun formatUtcOffsetTimeZoneId(utcOffsetSeconds: Int): String {
    val sign = if (utcOffsetSeconds >= 0) "+" else "-"
    val totalSeconds = abs(utcOffsetSeconds)
    val hours = totalSeconds / 3_600
    val minutes = (totalSeconds % 3_600) / 60
    return String.format(Locale.US, "GMT%s%02d:%02d", sign, hours, minutes)
}

private const val FORECAST_DATE_PATTERN = "yyyy-MM-dd"
