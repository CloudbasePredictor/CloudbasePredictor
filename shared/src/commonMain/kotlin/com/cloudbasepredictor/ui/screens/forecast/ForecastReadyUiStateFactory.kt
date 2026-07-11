@file:Suppress(
    "LongParameterList",
    "MagicNumber",
    "MatchingDeclarationName",
    "MaxLineLength",
    "ReturnCount",
)

package com.cloudbasepredictor.ui.screens.forecast

import com.cloudbasepredictor.data.map.MapLayerPreference
import com.cloudbasepredictor.data.remote.HourlyForecastData
import com.cloudbasepredictor.data.remote.HourlyPoint
import com.cloudbasepredictor.data.units.DisplayUnits
import com.cloudbasepredictor.data.units.UnitPreset
import com.cloudbasepredictor.data.units.resolveDisplayUnits
import com.cloudbasepredictor.model.DailyForecast
import com.cloudbasepredictor.model.ForecastMode
import com.cloudbasepredictor.model.ForecastModel
import com.cloudbasepredictor.model.SavedPlace
import com.cloudbasepredictor.model.WeatherCode
import com.cloudbasepredictor.util.toFixedDecimalString
import kotlin.math.abs

/** All platform-neutral inputs needed to prepare the shared forecast renderer. */
data class ForecastRenderInput(
    val hourlyData: HourlyForecastData,
    val place: SavedPlace,
    val requestedModel: ForecastModel,
    val resolvedModel: ForecastModel? = requestedModel,
    val selectedForecastMode: ForecastMode = ForecastMode.THERMIC,
    val selectedDayIndex: Int = 0,
    val stuveHour: Int = 12,
    val chartViewport: ForecastChartViewport = ForecastChartViewport(),
    val unitPreset: UnitPreset = UnitPreset.METRIC_KMH,
    val displayUnits: DisplayUnits = unitPreset.resolveDisplayUnits(),
    val fetchedAtMillis: Long,
    val modelGeneratedAtMillis: Long? = null,
    val favoritePlaces: List<SavedPlace> = emptyList(),
    val mapLayer: MapLayerPreference = MapLayerPreference.OPENFREEMAP,
    /** Optional shell-provided labels; defaults to labels derived from hourly daily data. */
    val dayChips: List<ForecastDayChipUiModel>? = null,
    /** Optional localized shell summary; defaults to a deterministic English summary. */
    val forecastText: String? = null,
)

/**
 * Builds every chart and the complete ready-state in common Kotlin.
 *
 * The function is pure: callers own loading, validation, persistence and localization. Android
 * may supply its existing localized day chips/summary, while the web app can use the common
 * defaults and still execute the exact same four chart builders.
 */
fun buildForecastReadyUiState(input: ForecastRenderInput): ForecastReadyUiState {
    val resolvedDayChips = input.dayChips ?: buildForecastDayChips(input.hourlyData.dailyForecasts)
    val availablePointDayCount = input.hourlyData.pointsByDate().size
    val lastAvailableDayIndex = maxOf(resolvedDayChips.size, availablePointDayCount) - 1
    val safeDayIndex = input.selectedDayIndex.coerceIn(0, lastAvailableDayIndex.coerceAtLeast(0))

    return ForecastReadyUiState(
        selectedPlace = input.place,
        selectedForecastMode = input.selectedForecastMode,
        selectedDayIndex = safeDayIndex,
        chartViewport = input.chartViewport,
        thermicChart = buildThermicChartFromData(input.hourlyData, dayIndex = safeDayIndex),
        stuveChart = buildStuveChartFromData(
            hourlyData = input.hourlyData,
            dayIndex = safeDayIndex,
            hour = input.stuveHour.coerceIn(FIRST_FORECAST_HOUR, LAST_FORECAST_HOUR),
        ),
        windChart = buildWindChartFromData(
            hourlyData = input.hourlyData,
            dayIndex = safeDayIndex,
            maxAltitudeKm = input.chartViewport.visibleTopAltitudeKm,
        ),
        cloudChart = buildCloudChartFromData(input.hourlyData, dayIndex = safeDayIndex),
        dayChips = resolvedDayChips,
        forecastText = input.forecastText ?: buildForecastSummary(
            mode = input.selectedForecastMode,
            place = input.place,
            days = input.hourlyData.dailyForecasts,
            selectedDayIndex = safeDayIndex,
        ),
        selectedModel = input.requestedModel,
        resolvedModel = input.resolvedModel,
        forecastUpdatedAtMillis = input.fetchedAtMillis,
        modelGeneratedAtMillis = input.modelGeneratedAtMillis,
        elevationKm = (input.hourlyData.elevation ?: 0.0).toFloat() / METERS_PER_KILOMETER,
        favoritePlaces = input.favoritePlaces,
        mapLayer = input.mapLayer,
        unitPreset = input.unitPreset,
        displayUnits = input.displayUnits,
    )
}

/** Checks the minimum shared inputs required by all four chart renderers. */
fun HourlyForecastData.hasRequiredForecastInputs(
    dayIndex: Int,
    stuveHour: Int,
): Boolean {
    val pointsByDate = pointsByDate()
    val dateKey = pointsByDate.keys.sorted().getOrNull(dayIndex) ?: return false
    val dayPoints = pointsByDate[dateKey].orEmpty()
    val daytimePoints = dayPoints.filter { it.hour in FIRST_FORECAST_HOUR..LAST_FORECAST_HOUR }
    if (daytimePoints.isEmpty()) return false

    val hasThermicSurfaceInputs = daytimePoints.any { point ->
        point.temperature2mC != null && point.dewPoint2mC != null
    }
    if (!hasThermicSurfaceInputs) return false

    val stuvePoint = dayPoints.firstOrNull { it.hour == stuveHour }
        ?: dayPoints.minByOrNull { point -> abs(point.hour - stuveHour) }
        ?: return false
    if (stuvePoint.temperature2mC == null || stuvePoint.dewPoint2mC == null) return false

    return daytimePoints.any { point -> point.hasRenderableWindInputs() }
}

/** Builds deterministic English day labels without a JVM date or locale dependency. */
fun buildForecastDayChips(days: List<DailyForecast>): List<ForecastDayChipUiModel> =
    days.mapIndexed { index, day ->
        ForecastDayChipUiModel(
            title = if (index == 0) "Today" else formatIsoWeekday(day.date) ?: day.date,
            subtitle = if (index == 0) "Today" else formatIsoDayMonth(day.date) ?: day.date,
        )
    }

fun buildForecastSummary(
    mode: ForecastMode,
    place: SavedPlace,
    days: List<DailyForecast>,
    selectedDayIndex: Int,
): String {
    val selectedDay = days.getOrNull(selectedDayIndex)
        ?: return when (mode) {
            ForecastMode.THERMIC -> "Forecast content for ${place.name} will appear here."
            ForecastMode.STUVE -> "Stuve forecast content for ${place.name} will appear here."
            ForecastMode.WIND -> "Wind forecast content for ${place.name} will appear here."
            ForecastMode.CLOUD -> "Cloud forecast content for ${place.name} will appear here."
        }
    val weather = WeatherCode.present(selectedDay.weatherCode)
    val dayTitle = if (selectedDayIndex == 0) "Today" else selectedDay.date

    return when (mode) {
        ForecastMode.THERMIC -> buildString {
            append(dayTitle)
            append(" in ")
            append(place.name)
            append(". ")
            append(weather.label)
            append(". High ")
            append(selectedDay.maxTemperatureCelsius.toFixedDecimalString(1))
            append("°C, low ")
            append(selectedDay.minTemperatureCelsius.toFixedDecimalString(1))
            append("°C. Thermic profile is ready for the selected altitude range.")
        }
        ForecastMode.STUVE ->
            "$dayTitle in ${place.name}. ${weather.label}. Stuve diagram is ready for the selected hour."
        ForecastMode.WIND ->
            "$dayTitle in ${place.name}. ${weather.label}. Wind profile is ready for the selected altitude range."
        ForecastMode.CLOUD ->
            "$dayTitle in ${place.name}. ${weather.label}. " +
                "Cloud layers, radiation, sunshine, and precipitation are ready."
    }
}

private fun HourlyPoint.hasRenderableWindInputs(): Boolean {
    val hasSurfaceWind = windSpeed10mKmh != null && windDirection10mDeg != null
    val hasPressureLevelWind = pressureLevels.any { level ->
        level.geopotentialHeightM != null &&
            level.windSpeedKmh != null &&
            level.windDirectionDeg != null
    }
    return hasSurfaceWind || hasPressureLevelWind
}

private fun formatIsoWeekday(date: String): String? {
    val (year, month, day) = parseIsoDate(date) ?: return null
    val offsets = intArrayOf(0, 3, 2, 5, 0, 3, 5, 1, 4, 6, 2, 4)
    val adjustedYear = if (month < 3) year - 1 else year
    val dayOfWeek = (
        adjustedYear +
            adjustedYear / 4 -
            adjustedYear / 100 +
            adjustedYear / 400 +
            offsets[month - 1] +
            day
        ).mod(WEEKDAY_LABELS.size)
    return WEEKDAY_LABELS[dayOfWeek]
}

private fun formatIsoDayMonth(date: String): String? {
    val (_, month, day) = parseIsoDate(date) ?: return null
    return "$day ${MONTH_LABELS[month - 1]}"
}

private fun parseIsoDate(date: String): Triple<Int, Int, Int>? {
    val parts = date.split('-')
    if (parts.size != 3) return null
    val year = parts[0].toIntOrNull() ?: return null
    val month = parts[1].toIntOrNull() ?: return null
    val day = parts[2].toIntOrNull() ?: return null
    if (month !in 1..12) return null
    val maxDay = DAYS_IN_MONTH[month - 1] + if (month == 2 && isLeapYear(year)) 1 else 0
    if (day !in 1..maxDay) return null
    return Triple(year, month, day)
}

private fun isLeapYear(year: Int): Boolean = year % 4 == 0 && (year % 100 != 0 || year % 400 == 0)

private val WEEKDAY_LABELS = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
private val MONTH_LABELS = listOf(
    "Jan", "Feb", "Mar", "Apr", "May", "Jun",
    "Jul", "Aug", "Sep", "Oct", "Nov", "Dec",
)
private val DAYS_IN_MONTH = intArrayOf(31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)
private const val FIRST_FORECAST_HOUR = 6
private const val LAST_FORECAST_HOUR = 22
private const val METERS_PER_KILOMETER = 1000f
