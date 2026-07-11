package com.cloudbasepredictor.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Response from the Open-Meteo `/v1/forecast` endpoint when requesting hourly
 * and daily data, including pressure-level variables.
 */
@Serializable
data class OpenMeteoHourlyForecastResponse(
    val latitude: Double,
    val longitude: Double,
    val elevation: Double? = null,
    @SerialName("utc_offset_seconds")
    val utcOffsetSeconds: Int = 0,
    val timezone: String? = null,
    val daily: OpenMeteoDailyResponse? = null,
    val hourly: OpenMeteoHourlyResponse,
)

/**
 * Hourly data block from Open-Meteo.
 *
 * Timestamps are in ISO-8601 local time (e.g. "2026-04-12T14:00").
 * Lists are parallel — index `i` corresponds to `time[i]`.
 *
 * Pressure-level fields use the suffix pattern `_<level>hPa`, e.g. `temperature_1000hPa`.
 * Null entries appear when the model does not cover that time step.
 */
@Serializable
data class OpenMeteoHourlyResponse(
    val time: List<String>,

    // --- Surface ---
    @SerialName("temperature_2m")
    val temperature2m: List<Double?>? = null,
    @SerialName("dew_point_2m")
    val dewPoint2m: List<Double?>? = null,

    // --- Cloud cover ---
    @SerialName("cloud_cover_low")
    val cloudCoverLow: List<Double?>? = null,
    @SerialName("cloud_cover_mid")
    val cloudCoverMid: List<Double?>? = null,
    @SerialName("cloud_cover_high")
    val cloudCoverHigh: List<Double?>? = null,

    // --- Precipitation ---
    val precipitation: List<Double?>? = null,
    @SerialName("precipitation_probability")
    val precipitationProbability: List<Double?>? = null,

    // --- Surface wind ---
    @SerialName("wind_speed_10m")
    val windSpeed10m: List<Double?>? = null,
    @SerialName("wind_direction_10m")
    val windDirection10m: List<Double?>? = null,

    // --- CAPE ---
    val cape: List<Double?>? = null,
    @SerialName("lifted_index")
    val liftedIndex: List<Double?>? = null,
    @SerialName("convective_inhibition")
    val convectiveInhibition: List<Double?>? = null,
    @SerialName("boundary_layer_height")
    val boundaryLayerHeight: List<Double?>? = null,

    // --- Freezing level ---
    @SerialName("freezing_level_height")
    val freezingLevelHeight: List<Double?>? = null,

    // --- Surface pressure ---
    @SerialName("surface_pressure")
    val surfacePressure: List<Double?>? = null,

    // --- Solar radiation ---
    @SerialName("shortwave_radiation")
    val shortwaveRadiation: List<Double?>? = null,

    // --- Sunshine duration (seconds per preceding hour) ---
    @SerialName("sunshine_duration")
    val sunshineDuration: List<Double?>? = null,

    // --- Day/night flag ---
    @SerialName("is_day")
    val isDay: List<Double?>? = null,

    // --- Temperature at pressure levels (°C) ---
    @SerialName("temperature_1000hPa")
    val temperature1000hPa: List<Double?>? = null,
    @SerialName("temperature_975hPa")
    val temperature975hPa: List<Double?>? = null,
    @SerialName("temperature_950hPa")
    val temperature950hPa: List<Double?>? = null,
    @SerialName("temperature_925hPa")
    val temperature925hPa: List<Double?>? = null,
    @SerialName("temperature_900hPa")
    val temperature900hPa: List<Double?>? = null,
    @SerialName("temperature_875hPa")
    val temperature875hPa: List<Double?>? = null,
    @SerialName("temperature_850hPa")
    val temperature850hPa: List<Double?>? = null,
    @SerialName("temperature_800hPa")
    val temperature800hPa: List<Double?>? = null,
    @SerialName("temperature_750hPa")
    val temperature750hPa: List<Double?>? = null,
    @SerialName("temperature_700hPa")
    val temperature700hPa: List<Double?>? = null,
    @SerialName("temperature_650hPa")
    val temperature650hPa: List<Double?>? = null,
    @SerialName("temperature_600hPa")
    val temperature600hPa: List<Double?>? = null,
    @SerialName("temperature_550hPa")
    val temperature550hPa: List<Double?>? = null,
    @SerialName("temperature_500hPa")
    val temperature500hPa: List<Double?>? = null,
    @SerialName("temperature_450hPa")
    val temperature450hPa: List<Double?>? = null,
    @SerialName("temperature_400hPa")
    val temperature400hPa: List<Double?>? = null,
    @SerialName("temperature_350hPa")
    val temperature350hPa: List<Double?>? = null,
    @SerialName("temperature_300hPa")
    val temperature300hPa: List<Double?>? = null,
    @SerialName("temperature_250hPa")
    val temperature250hPa: List<Double?>? = null,
    @SerialName("temperature_200hPa")
    val temperature200hPa: List<Double?>? = null,

    // --- Dewpoint at pressure levels (°C) ---
    @SerialName("dew_point_1000hPa")
    val dewPoint1000hPa: List<Double?>? = null,
    @SerialName("dew_point_975hPa")
    val dewPoint975hPa: List<Double?>? = null,
    @SerialName("dew_point_950hPa")
    val dewPoint950hPa: List<Double?>? = null,
    @SerialName("dew_point_925hPa")
    val dewPoint925hPa: List<Double?>? = null,
    @SerialName("dew_point_900hPa")
    val dewPoint900hPa: List<Double?>? = null,
    @SerialName("dew_point_875hPa")
    val dewPoint875hPa: List<Double?>? = null,
    @SerialName("dew_point_850hPa")
    val dewPoint850hPa: List<Double?>? = null,
    @SerialName("dew_point_800hPa")
    val dewPoint800hPa: List<Double?>? = null,
    @SerialName("dew_point_750hPa")
    val dewPoint750hPa: List<Double?>? = null,
    @SerialName("dew_point_700hPa")
    val dewPoint700hPa: List<Double?>? = null,
    @SerialName("dew_point_650hPa")
    val dewPoint650hPa: List<Double?>? = null,
    @SerialName("dew_point_600hPa")
    val dewPoint600hPa: List<Double?>? = null,
    @SerialName("dew_point_550hPa")
    val dewPoint550hPa: List<Double?>? = null,
    @SerialName("dew_point_500hPa")
    val dewPoint500hPa: List<Double?>? = null,
    @SerialName("dew_point_450hPa")
    val dewPoint450hPa: List<Double?>? = null,
    @SerialName("dew_point_400hPa")
    val dewPoint400hPa: List<Double?>? = null,
    @SerialName("dew_point_350hPa")
    val dewPoint350hPa: List<Double?>? = null,
    @SerialName("dew_point_300hPa")
    val dewPoint300hPa: List<Double?>? = null,
    @SerialName("dew_point_250hPa")
    val dewPoint250hPa: List<Double?>? = null,
    @SerialName("dew_point_200hPa")
    val dewPoint200hPa: List<Double?>? = null,

    // --- Relative humidity at pressure levels (%) ---
    @SerialName("relative_humidity_1000hPa")
    val relativeHumidity1000hPa: List<Double?>? = null,
    @SerialName("relative_humidity_975hPa")
    val relativeHumidity975hPa: List<Double?>? = null,
    @SerialName("relative_humidity_950hPa")
    val relativeHumidity950hPa: List<Double?>? = null,
    @SerialName("relative_humidity_925hPa")
    val relativeHumidity925hPa: List<Double?>? = null,
    @SerialName("relative_humidity_900hPa")
    val relativeHumidity900hPa: List<Double?>? = null,
    @SerialName("relative_humidity_875hPa")
    val relativeHumidity875hPa: List<Double?>? = null,
    @SerialName("relative_humidity_850hPa")
    val relativeHumidity850hPa: List<Double?>? = null,
    @SerialName("relative_humidity_800hPa")
    val relativeHumidity800hPa: List<Double?>? = null,
    @SerialName("relative_humidity_750hPa")
    val relativeHumidity750hPa: List<Double?>? = null,
    @SerialName("relative_humidity_700hPa")
    val relativeHumidity700hPa: List<Double?>? = null,
    @SerialName("relative_humidity_650hPa")
    val relativeHumidity650hPa: List<Double?>? = null,
    @SerialName("relative_humidity_600hPa")
    val relativeHumidity600hPa: List<Double?>? = null,
    @SerialName("relative_humidity_550hPa")
    val relativeHumidity550hPa: List<Double?>? = null,
    @SerialName("relative_humidity_500hPa")
    val relativeHumidity500hPa: List<Double?>? = null,
    @SerialName("relative_humidity_450hPa")
    val relativeHumidity450hPa: List<Double?>? = null,
    @SerialName("relative_humidity_400hPa")
    val relativeHumidity400hPa: List<Double?>? = null,
    @SerialName("relative_humidity_350hPa")
    val relativeHumidity350hPa: List<Double?>? = null,
    @SerialName("relative_humidity_300hPa")
    val relativeHumidity300hPa: List<Double?>? = null,
    @SerialName("relative_humidity_250hPa")
    val relativeHumidity250hPa: List<Double?>? = null,

    // --- Cloud cover at pressure levels (%) ---
    @SerialName("cloud_cover_1000hPa")
    val cloudCover1000hPa: List<Double?>? = null,
    @SerialName("cloud_cover_975hPa")
    val cloudCover975hPa: List<Double?>? = null,
    @SerialName("cloud_cover_950hPa")
    val cloudCover950hPa: List<Double?>? = null,
    @SerialName("cloud_cover_925hPa")
    val cloudCover925hPa: List<Double?>? = null,
    @SerialName("cloud_cover_900hPa")
    val cloudCover900hPa: List<Double?>? = null,
    @SerialName("cloud_cover_875hPa")
    val cloudCover875hPa: List<Double?>? = null,
    @SerialName("cloud_cover_850hPa")
    val cloudCover850hPa: List<Double?>? = null,
    @SerialName("cloud_cover_800hPa")
    val cloudCover800hPa: List<Double?>? = null,
    @SerialName("cloud_cover_750hPa")
    val cloudCover750hPa: List<Double?>? = null,
    @SerialName("cloud_cover_700hPa")
    val cloudCover700hPa: List<Double?>? = null,
    @SerialName("cloud_cover_650hPa")
    val cloudCover650hPa: List<Double?>? = null,
    @SerialName("cloud_cover_600hPa")
    val cloudCover600hPa: List<Double?>? = null,
    @SerialName("cloud_cover_550hPa")
    val cloudCover550hPa: List<Double?>? = null,
    @SerialName("cloud_cover_500hPa")
    val cloudCover500hPa: List<Double?>? = null,
    @SerialName("cloud_cover_450hPa")
    val cloudCover450hPa: List<Double?>? = null,
    @SerialName("cloud_cover_400hPa")
    val cloudCover400hPa: List<Double?>? = null,
    @SerialName("cloud_cover_350hPa")
    val cloudCover350hPa: List<Double?>? = null,
    @SerialName("cloud_cover_300hPa")
    val cloudCover300hPa: List<Double?>? = null,
    @SerialName("cloud_cover_250hPa")
    val cloudCover250hPa: List<Double?>? = null,

    // --- Wind speed at pressure levels (km/h) ---
    @SerialName("wind_speed_1000hPa")
    val windSpeed1000hPa: List<Double?>? = null,
    @SerialName("wind_speed_975hPa")
    val windSpeed975hPa: List<Double?>? = null,
    @SerialName("wind_speed_950hPa")
    val windSpeed950hPa: List<Double?>? = null,
    @SerialName("wind_speed_925hPa")
    val windSpeed925hPa: List<Double?>? = null,
    @SerialName("wind_speed_900hPa")
    val windSpeed900hPa: List<Double?>? = null,
    @SerialName("wind_speed_875hPa")
    val windSpeed875hPa: List<Double?>? = null,
    @SerialName("wind_speed_850hPa")
    val windSpeed850hPa: List<Double?>? = null,
    @SerialName("wind_speed_800hPa")
    val windSpeed800hPa: List<Double?>? = null,
    @SerialName("wind_speed_750hPa")
    val windSpeed750hPa: List<Double?>? = null,
    @SerialName("wind_speed_700hPa")
    val windSpeed700hPa: List<Double?>? = null,
    @SerialName("wind_speed_650hPa")
    val windSpeed650hPa: List<Double?>? = null,
    @SerialName("wind_speed_600hPa")
    val windSpeed600hPa: List<Double?>? = null,
    @SerialName("wind_speed_550hPa")
    val windSpeed550hPa: List<Double?>? = null,
    @SerialName("wind_speed_500hPa")
    val windSpeed500hPa: List<Double?>? = null,
    @SerialName("wind_speed_450hPa")
    val windSpeed450hPa: List<Double?>? = null,
    @SerialName("wind_speed_400hPa")
    val windSpeed400hPa: List<Double?>? = null,
    @SerialName("wind_speed_350hPa")
    val windSpeed350hPa: List<Double?>? = null,
    @SerialName("wind_speed_300hPa")
    val windSpeed300hPa: List<Double?>? = null,
    @SerialName("wind_speed_250hPa")
    val windSpeed250hPa: List<Double?>? = null,
    @SerialName("wind_speed_200hPa")
    val windSpeed200hPa: List<Double?>? = null,

    // --- Wind direction at pressure levels (°) ---
    @SerialName("wind_direction_1000hPa")
    val windDirection1000hPa: List<Double?>? = null,
    @SerialName("wind_direction_975hPa")
    val windDirection975hPa: List<Double?>? = null,
    @SerialName("wind_direction_950hPa")
    val windDirection950hPa: List<Double?>? = null,
    @SerialName("wind_direction_925hPa")
    val windDirection925hPa: List<Double?>? = null,
    @SerialName("wind_direction_900hPa")
    val windDirection900hPa: List<Double?>? = null,
    @SerialName("wind_direction_875hPa")
    val windDirection875hPa: List<Double?>? = null,
    @SerialName("wind_direction_850hPa")
    val windDirection850hPa: List<Double?>? = null,
    @SerialName("wind_direction_800hPa")
    val windDirection800hPa: List<Double?>? = null,
    @SerialName("wind_direction_750hPa")
    val windDirection750hPa: List<Double?>? = null,
    @SerialName("wind_direction_700hPa")
    val windDirection700hPa: List<Double?>? = null,
    @SerialName("wind_direction_650hPa")
    val windDirection650hPa: List<Double?>? = null,
    @SerialName("wind_direction_600hPa")
    val windDirection600hPa: List<Double?>? = null,
    @SerialName("wind_direction_550hPa")
    val windDirection550hPa: List<Double?>? = null,
    @SerialName("wind_direction_500hPa")
    val windDirection500hPa: List<Double?>? = null,
    @SerialName("wind_direction_450hPa")
    val windDirection450hPa: List<Double?>? = null,
    @SerialName("wind_direction_400hPa")
    val windDirection400hPa: List<Double?>? = null,
    @SerialName("wind_direction_350hPa")
    val windDirection350hPa: List<Double?>? = null,
    @SerialName("wind_direction_300hPa")
    val windDirection300hPa: List<Double?>? = null,
    @SerialName("wind_direction_250hPa")
    val windDirection250hPa: List<Double?>? = null,
    @SerialName("wind_direction_200hPa")
    val windDirection200hPa: List<Double?>? = null,

    // --- Geopotential height at pressure levels (m ASL) ---
    @SerialName("geopotential_height_1000hPa")
    val geopotentialHeight1000hPa: List<Double?>? = null,
    @SerialName("geopotential_height_975hPa")
    val geopotentialHeight975hPa: List<Double?>? = null,
    @SerialName("geopotential_height_950hPa")
    val geopotentialHeight950hPa: List<Double?>? = null,
    @SerialName("geopotential_height_925hPa")
    val geopotentialHeight925hPa: List<Double?>? = null,
    @SerialName("geopotential_height_900hPa")
    val geopotentialHeight900hPa: List<Double?>? = null,
    @SerialName("geopotential_height_875hPa")
    val geopotentialHeight875hPa: List<Double?>? = null,
    @SerialName("geopotential_height_850hPa")
    val geopotentialHeight850hPa: List<Double?>? = null,
    @SerialName("geopotential_height_800hPa")
    val geopotentialHeight800hPa: List<Double?>? = null,
    @SerialName("geopotential_height_750hPa")
    val geopotentialHeight750hPa: List<Double?>? = null,
    @SerialName("geopotential_height_700hPa")
    val geopotentialHeight700hPa: List<Double?>? = null,
    @SerialName("geopotential_height_650hPa")
    val geopotentialHeight650hPa: List<Double?>? = null,
    @SerialName("geopotential_height_600hPa")
    val geopotentialHeight600hPa: List<Double?>? = null,
    @SerialName("geopotential_height_550hPa")
    val geopotentialHeight550hPa: List<Double?>? = null,
    @SerialName("geopotential_height_500hPa")
    val geopotentialHeight500hPa: List<Double?>? = null,
    @SerialName("geopotential_height_450hPa")
    val geopotentialHeight450hPa: List<Double?>? = null,
    @SerialName("geopotential_height_400hPa")
    val geopotentialHeight400hPa: List<Double?>? = null,
    @SerialName("geopotential_height_350hPa")
    val geopotentialHeight350hPa: List<Double?>? = null,
    @SerialName("geopotential_height_300hPa")
    val geopotentialHeight300hPa: List<Double?>? = null,
    @SerialName("geopotential_height_250hPa")
    val geopotentialHeight250hPa: List<Double?>? = null,
    @SerialName("geopotential_height_200hPa")
    val geopotentialHeight200hPa: List<Double?>? = null,
) {
    /**
     * Helper to collect all pressure-level temperature lists alongside their pressure (hPa).
     * Returns pairs of (pressureHpa, list) for non-null lists only.
     */
    fun temperaturesByPressure(): List<Pair<Int, List<Double?>>> = listOfNotNull(
        temperature1000hPa?.let { 1000 to it },
        temperature975hPa?.let { 975 to it },
        temperature950hPa?.let { 950 to it },
        temperature925hPa?.let { 925 to it },
        temperature900hPa?.let { 900 to it },
        temperature875hPa?.let { 875 to it },
        temperature850hPa?.let { 850 to it },
        temperature800hPa?.let { 800 to it },
        temperature750hPa?.let { 750 to it },
        temperature700hPa?.let { 700 to it },
        temperature650hPa?.let { 650 to it },
        temperature600hPa?.let { 600 to it },
        temperature550hPa?.let { 550 to it },
        temperature500hPa?.let { 500 to it },
        temperature450hPa?.let { 450 to it },
        temperature400hPa?.let { 400 to it },
        temperature350hPa?.let { 350 to it },
        temperature300hPa?.let { 300 to it },
        temperature250hPa?.let { 250 to it },
    )

    fun dewPointsByPressure(): List<Pair<Int, List<Double?>>> = listOfNotNull(
        dewPoint1000hPa?.let { 1000 to it },
        dewPoint975hPa?.let { 975 to it },
        dewPoint950hPa?.let { 950 to it },
        dewPoint925hPa?.let { 925 to it },
        dewPoint900hPa?.let { 900 to it },
        dewPoint875hPa?.let { 875 to it },
        dewPoint850hPa?.let { 850 to it },
        dewPoint800hPa?.let { 800 to it },
        dewPoint750hPa?.let { 750 to it },
        dewPoint700hPa?.let { 700 to it },
        dewPoint650hPa?.let { 650 to it },
        dewPoint600hPa?.let { 600 to it },
        dewPoint550hPa?.let { 550 to it },
        dewPoint500hPa?.let { 500 to it },
        dewPoint450hPa?.let { 450 to it },
        dewPoint400hPa?.let { 400 to it },
        dewPoint350hPa?.let { 350 to it },
        dewPoint300hPa?.let { 300 to it },
        dewPoint250hPa?.let { 250 to it },
    )

    fun relativeHumidityByPressure(): List<Pair<Int, List<Double?>>> = listOfNotNull(
        relativeHumidity1000hPa?.let { 1000 to it },
        relativeHumidity975hPa?.let { 975 to it },
        relativeHumidity950hPa?.let { 950 to it },
        relativeHumidity925hPa?.let { 925 to it },
        relativeHumidity900hPa?.let { 900 to it },
        relativeHumidity875hPa?.let { 875 to it },
        relativeHumidity850hPa?.let { 850 to it },
        relativeHumidity800hPa?.let { 800 to it },
        relativeHumidity750hPa?.let { 750 to it },
        relativeHumidity700hPa?.let { 700 to it },
        relativeHumidity650hPa?.let { 650 to it },
        relativeHumidity600hPa?.let { 600 to it },
        relativeHumidity550hPa?.let { 550 to it },
        relativeHumidity500hPa?.let { 500 to it },
        relativeHumidity450hPa?.let { 450 to it },
        relativeHumidity400hPa?.let { 400 to it },
        relativeHumidity350hPa?.let { 350 to it },
        relativeHumidity300hPa?.let { 300 to it },
        relativeHumidity250hPa?.let { 250 to it },
    )

    fun cloudCoverByPressure(): List<Pair<Int, List<Double?>>> = listOfNotNull(
        cloudCover1000hPa?.let { 1000 to it },
        cloudCover975hPa?.let { 975 to it },
        cloudCover950hPa?.let { 950 to it },
        cloudCover925hPa?.let { 925 to it },
        cloudCover900hPa?.let { 900 to it },
        cloudCover875hPa?.let { 875 to it },
        cloudCover850hPa?.let { 850 to it },
        cloudCover800hPa?.let { 800 to it },
        cloudCover750hPa?.let { 750 to it },
        cloudCover700hPa?.let { 700 to it },
        cloudCover650hPa?.let { 650 to it },
        cloudCover600hPa?.let { 600 to it },
        cloudCover550hPa?.let { 550 to it },
        cloudCover500hPa?.let { 500 to it },
        cloudCover450hPa?.let { 450 to it },
        cloudCover400hPa?.let { 400 to it },
        cloudCover350hPa?.let { 350 to it },
        cloudCover300hPa?.let { 300 to it },
        cloudCover250hPa?.let { 250 to it },
    )

    fun windSpeedsByPressure(): List<Pair<Int, List<Double?>>> = listOfNotNull(
        windSpeed1000hPa?.let { 1000 to it },
        windSpeed975hPa?.let { 975 to it },
        windSpeed950hPa?.let { 950 to it },
        windSpeed925hPa?.let { 925 to it },
        windSpeed900hPa?.let { 900 to it },
        windSpeed875hPa?.let { 875 to it },
        windSpeed850hPa?.let { 850 to it },
        windSpeed800hPa?.let { 800 to it },
        windSpeed750hPa?.let { 750 to it },
        windSpeed700hPa?.let { 700 to it },
        windSpeed650hPa?.let { 650 to it },
        windSpeed600hPa?.let { 600 to it },
        windSpeed550hPa?.let { 550 to it },
        windSpeed500hPa?.let { 500 to it },
        windSpeed450hPa?.let { 450 to it },
        windSpeed400hPa?.let { 400 to it },
        windSpeed350hPa?.let { 350 to it },
        windSpeed300hPa?.let { 300 to it },
        windSpeed250hPa?.let { 250 to it },
    )

    fun windDirectionsByPressure(): List<Pair<Int, List<Double?>>> = listOfNotNull(
        windDirection1000hPa?.let { 1000 to it },
        windDirection975hPa?.let { 975 to it },
        windDirection950hPa?.let { 950 to it },
        windDirection925hPa?.let { 925 to it },
        windDirection900hPa?.let { 900 to it },
        windDirection875hPa?.let { 875 to it },
        windDirection850hPa?.let { 850 to it },
        windDirection800hPa?.let { 800 to it },
        windDirection750hPa?.let { 750 to it },
        windDirection700hPa?.let { 700 to it },
        windDirection650hPa?.let { 650 to it },
        windDirection600hPa?.let { 600 to it },
        windDirection550hPa?.let { 550 to it },
        windDirection500hPa?.let { 500 to it },
        windDirection450hPa?.let { 450 to it },
        windDirection400hPa?.let { 400 to it },
        windDirection350hPa?.let { 350 to it },
        windDirection300hPa?.let { 300 to it },
        windDirection250hPa?.let { 250 to it },
    )

    fun geopotentialHeightsByPressure(): List<Pair<Int, List<Double?>>> = listOfNotNull(
        geopotentialHeight1000hPa?.let { 1000 to it },
        geopotentialHeight975hPa?.let { 975 to it },
        geopotentialHeight950hPa?.let { 950 to it },
        geopotentialHeight925hPa?.let { 925 to it },
        geopotentialHeight900hPa?.let { 900 to it },
        geopotentialHeight875hPa?.let { 875 to it },
        geopotentialHeight850hPa?.let { 850 to it },
        geopotentialHeight800hPa?.let { 800 to it },
        geopotentialHeight750hPa?.let { 750 to it },
        geopotentialHeight700hPa?.let { 700 to it },
        geopotentialHeight650hPa?.let { 650 to it },
        geopotentialHeight600hPa?.let { 600 to it },
        geopotentialHeight550hPa?.let { 550 to it },
        geopotentialHeight500hPa?.let { 500 to it },
        geopotentialHeight450hPa?.let { 450 to it },
        geopotentialHeight400hPa?.let { 400 to it },
        geopotentialHeight350hPa?.let { 350 to it },
        geopotentialHeight300hPa?.let { 300 to it },
        geopotentialHeight250hPa?.let { 250 to it },
    )
}
