@file:Suppress("LongParameterList", "MagicNumber", "ReturnCount")

package com.cloudbasepredictor.web.forecast

import com.cloudbasepredictor.data.map.MapLayerPreference
import com.cloudbasepredictor.data.units.UnitPreset
import com.cloudbasepredictor.model.ForecastMode
import com.cloudbasepredictor.model.ForecastModel
import com.cloudbasepredictor.model.PlaceLocation
import com.cloudbasepredictor.model.SavedPlace
import com.cloudbasepredictor.model.WeatherCode
import com.cloudbasepredictor.ui.screens.forecast.ForecastChartViewport
import com.cloudbasepredictor.ui.screens.forecast.ForecastDayChipUiModel
import com.cloudbasepredictor.ui.screens.forecast.ForecastReadyUiState
import com.cloudbasepredictor.ui.screens.forecast.ForecastRenderInput
import com.cloudbasepredictor.ui.screens.forecast.buildForecastReadyUiState
import com.cloudbasepredictor.util.toFixedDecimalString
import com.cloudbasepredictor.web.i18n.FORECAST_DAY_MONTH
import com.cloudbasepredictor.web.i18n.FORECAST_PENDING_CLOUD
import com.cloudbasepredictor.web.i18n.FORECAST_PENDING_STUVE
import com.cloudbasepredictor.web.i18n.FORECAST_PENDING_THERMIC
import com.cloudbasepredictor.web.i18n.FORECAST_PENDING_WIND
import com.cloudbasepredictor.web.i18n.FORECAST_SUMMARY_CLOUD
import com.cloudbasepredictor.web.i18n.FORECAST_SUMMARY_STUVE
import com.cloudbasepredictor.web.i18n.FORECAST_SUMMARY_THERMIC
import com.cloudbasepredictor.web.i18n.FORECAST_SUMMARY_WIND
import com.cloudbasepredictor.web.i18n.FORECAST_TEMPERATURE_CELSIUS
import com.cloudbasepredictor.web.i18n.FORECAST_TODAY
import com.cloudbasepredictor.web.i18n.WebForecastStrings

data class WebForecastPresentationInput(
    val location: PlaceLocation,
    val requestedModel: ForecastModel,
    val result: WebForecastResult,
    val mode: ForecastMode,
    val dayIndex: Int,
    val hour: Int,
    val visibleTopAltitudeKm: Float,
    val unitPreset: UnitPreset,
    val mapLayer: MapLayerPreference,
    val favoritePlaces: List<SavedPlace>,
    val forecastStrings: WebForecastStrings,
)

fun buildWebForecastReadyState(input: WebForecastPresentationInput): ForecastReadyUiState {
    val place = input.location.toSavedPlace(input.favoritePlaces)
    val days = input.result.hourlyData.dailyForecasts
    val dayChips = buildLocalizedWebForecastDayChips(days, input.forecastStrings)
    val availablePointDayCount = input.result.hourlyData.pointsByDate().size
    val lastAvailableDayIndex = maxOf(dayChips.size, availablePointDayCount) - 1
    val safeDayIndex = input.dayIndex.coerceIn(0, lastAvailableDayIndex.coerceAtLeast(0))

    return buildForecastReadyUiState(
        ForecastRenderInput(
            hourlyData = input.result.hourlyData,
            place = place,
            requestedModel = input.requestedModel,
            resolvedModel = input.result.resolvedModel,
            selectedForecastMode = input.mode,
            selectedDayIndex = safeDayIndex,
            stuveHour = input.hour,
            chartViewport = ForecastChartViewport(
                visibleTopAltitudeKm = input.visibleTopAltitudeKm,
            ),
            unitPreset = input.unitPreset,
            fetchedAtMillis = input.result.fetchedAtMillis,
            modelGeneratedAtMillis = estimateModelRunMillis(
                input.result.fetchedAtMillis,
                input.result.resolvedModel,
            ),
            favoritePlaces = input.favoritePlaces,
            mapLayer = input.mapLayer,
            dayChips = dayChips,
            forecastText = buildLocalizedWebForecastSummary(
                strings = input.forecastStrings,
                mode = input.mode,
                place = place,
                days = days,
                selectedDayIndex = safeDayIndex,
            ),
        ),
    )
}

internal fun buildLocalizedWebForecastDayChips(
    days: List<com.cloudbasepredictor.model.DailyForecast>,
    strings: WebForecastStrings,
): List<ForecastDayChipUiModel> = days.mapIndexed { index, day ->
    val parsedDate = parseIsoDate(day.date)
    ForecastDayChipUiModel(
        title = if (index == 0) {
            strings[FORECAST_TODAY]
        } else {
            parsedDate?.let { (_, month, date) ->
                localizedWeekday(day.date, strings) ?: localizedDayMonth(date, month, strings)
            } ?: day.date
        },
        subtitle = parsedDate?.let { (_, month, date) ->
            localizedDayMonth(date, month, strings)
        } ?: day.date,
    )
}

internal fun buildLocalizedWebForecastSummary(
    strings: WebForecastStrings,
    mode: ForecastMode,
    place: SavedPlace,
    days: List<com.cloudbasepredictor.model.DailyForecast>,
    selectedDayIndex: Int,
): String {
    val selectedDay = days.getOrNull(selectedDayIndex)
        ?: return when (mode) {
            ForecastMode.THERMIC -> strings[FORECAST_PENDING_THERMIC].render("place" to place.name)
            ForecastMode.STUVE -> strings[FORECAST_PENDING_STUVE].render("place" to place.name)
            ForecastMode.WIND -> strings[FORECAST_PENDING_WIND].render("place" to place.name)
            ForecastMode.CLOUD -> strings[FORECAST_PENDING_CLOUD].render("place" to place.name)
        }
    val parsedDate = parseIsoDate(selectedDay.date)
    val dayTitle = if (selectedDayIndex == 0) {
        strings[FORECAST_TODAY]
    } else {
        parsedDate?.let { (_, month, day) -> localizedDayMonth(day, month, strings) }
            ?: selectedDay.date
    }
    val weather = localizedWeatherLabel(selectedDay.weatherCode, strings)
    val values = arrayOf(
        "day" to dayTitle,
        "place" to place.name,
        "weather" to weather,
    )

    return when (mode) {
        ForecastMode.THERMIC -> strings[FORECAST_SUMMARY_THERMIC].render(
            *values,
            "high" to strings[FORECAST_TEMPERATURE_CELSIUS].render(
                "value" to selectedDay.maxTemperatureCelsius.toFixedDecimalString(1),
            ),
            "low" to strings[FORECAST_TEMPERATURE_CELSIUS].render(
                "value" to selectedDay.minTemperatureCelsius.toFixedDecimalString(1),
            ),
        )
        ForecastMode.STUVE -> strings[FORECAST_SUMMARY_STUVE].render(*values)
        ForecastMode.WIND -> strings[FORECAST_SUMMARY_WIND].render(*values)
        ForecastMode.CLOUD -> strings[FORECAST_SUMMARY_CLOUD].render(*values)
    }
}

private fun localizedWeatherLabel(weatherCode: Int, strings: WebForecastStrings): String =
    strings.weatherLabel(WeatherCode.condition(weatherCode).ordinal)

private fun localizedWeekday(date: String, strings: WebForecastStrings): String? {
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
        ).mod(strings.weekdayShort.size)
    return strings.weekdayShort.getOrNull(dayOfWeek)
}

private fun localizedDayMonth(day: Int, month: Int, strings: WebForecastStrings): String =
    strings[FORECAST_DAY_MONTH].render(
        "day" to day.toString(),
        "month" to strings.monthShort[month - 1],
    )

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

private fun String.render(vararg replacements: Pair<String, String>): String =
    replacements.fold(this) { result, (key, value) -> result.replace("{$key}", value) }

private fun isLeapYear(year: Int): Boolean = year % 4 == 0 && (year % 100 != 0 || year % 400 == 0)

private fun PlaceLocation.toSavedPlace(favorites: List<SavedPlace>): SavedPlace {
    val coordinatePlace = SavedPlace.fromCoordinates(latitude, longitude)
    return favorites.firstOrNull { it.id == coordinatePlace.id }
        ?: coordinatePlace.copy(name = name ?: coordinatePlace.name)
}

private fun estimateModelRunMillis(fetchedAtMillis: Long, model: ForecastModel): Long {
    return (fetchedAtMillis / model.updateIntervalMillis) * model.updateIntervalMillis
}

private val DAYS_IN_MONTH = intArrayOf(31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)
