package com.cloudbasepredictor.data.remote

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter

interface OpenMeteoApi {
    suspend fun getForecast(
        latitude: Double,
        longitude: Double,
        daily: String = "temperature_2m_max,temperature_2m_min,weather_code",
        forecastDays: Int = 14,
        timezone: String = "auto",
    ): OpenMeteoForecastResponse

    /**
     * Fetch hourly forecast including surface and pressure-level variables.
     *
     * Use [models] to select a specific weather model (e.g. "icon_d2", "icon_eu").
     * Pass "best_match" or omit for automatic model selection.
     */
    suspend fun getHourlyForecast(
        latitude: Double,
        longitude: Double,
        hourly: String = HOURLY_VARIABLES,
        daily: String = "temperature_2m_max,temperature_2m_min,weather_code",
        forecastDays: Int = 7,
        timezone: String = "auto",
        models: String? = null,
    ): OpenMeteoHourlyForecastResponse
}

class KtorOpenMeteoApi(
    private val httpClient: HttpClient,
    baseUrl: String = OPEN_METEO_BASE_URL,
) : OpenMeteoApi {
    private val forecastEndpoint = "${baseUrl.trimEnd('/')}/v1/forecast"

    override suspend fun getForecast(
        latitude: Double,
        longitude: Double,
        daily: String,
        forecastDays: Int,
        timezone: String,
    ): OpenMeteoForecastResponse {
        return executeJsonRequest {
            httpClient.get(forecastEndpoint) {
                parameter("latitude", latitude)
                parameter("longitude", longitude)
                parameter("daily", daily)
                parameter("forecast_days", forecastDays)
                parameter("timezone", timezone)
            }
        }
    }

    override suspend fun getHourlyForecast(
        latitude: Double,
        longitude: Double,
        hourly: String,
        daily: String,
        forecastDays: Int,
        timezone: String,
        models: String?,
    ): OpenMeteoHourlyForecastResponse {
        return executeJsonRequest {
            httpClient.get(forecastEndpoint) {
                parameter("latitude", latitude)
                parameter("longitude", longitude)
                parameter("hourly", hourly)
                parameter("daily", daily)
                parameter("forecast_days", forecastDays)
                parameter("timezone", timezone)
                if (models != null) {
                    parameter("models", models)
                }
            }
        }
    }
}

const val OPEN_METEO_BASE_URL = "https://api.open-meteo.com"

/**
 * Surface + pressure-level variables requested for chart construction.
 *
 * Pressure levels: 1000, 975, 950, 925, 900, 875, 850, 800, 750, 700, 650, 600,
 * 550, 500, 450, 400, 350, 300, 250 hPa.
 * Extra levels improve Skew-T continuity and allow a Windy-like full sounding range.
 */
private const val HOURLY_VARIABLES =
    "temperature_2m,dew_point_2m," +
    "cloud_cover_low,cloud_cover_mid,cloud_cover_high," +
    "precipitation,precipitation_probability," +
    "wind_speed_10m,wind_direction_10m," +
    "cape,lifted_index,convective_inhibition,boundary_layer_height,freezing_level_height," +
    "surface_pressure,shortwave_radiation,sunshine_duration,is_day," +
    "temperature_1000hPa,temperature_975hPa,temperature_950hPa,temperature_925hPa,temperature_900hPa," +
    "temperature_875hPa,temperature_850hPa,temperature_800hPa,temperature_750hPa," +
    "temperature_700hPa,temperature_650hPa,temperature_600hPa,temperature_550hPa,temperature_500hPa," +
    "temperature_450hPa,temperature_400hPa,temperature_350hPa,temperature_300hPa,temperature_250hPa," +
    "dew_point_1000hPa,dew_point_975hPa,dew_point_950hPa,dew_point_925hPa,dew_point_900hPa," +
    "dew_point_875hPa,dew_point_850hPa,dew_point_800hPa,dew_point_750hPa," +
    "dew_point_700hPa,dew_point_650hPa,dew_point_600hPa,dew_point_550hPa,dew_point_500hPa," +
    "dew_point_450hPa,dew_point_400hPa,dew_point_350hPa,dew_point_300hPa,dew_point_250hPa," +
    "relative_humidity_1000hPa,relative_humidity_975hPa,relative_humidity_950hPa,relative_humidity_925hPa," +
    "relative_humidity_900hPa,relative_humidity_875hPa,relative_humidity_850hPa,relative_humidity_800hPa," +
    "relative_humidity_750hPa,relative_humidity_700hPa,relative_humidity_650hPa,relative_humidity_600hPa," +
    "relative_humidity_550hPa,relative_humidity_500hPa,relative_humidity_450hPa,relative_humidity_400hPa," +
    "relative_humidity_350hPa,relative_humidity_300hPa,relative_humidity_250hPa," +
    "cloud_cover_1000hPa,cloud_cover_975hPa,cloud_cover_950hPa,cloud_cover_925hPa," +
    "cloud_cover_900hPa,cloud_cover_875hPa,cloud_cover_850hPa,cloud_cover_800hPa," +
    "cloud_cover_750hPa,cloud_cover_700hPa,cloud_cover_650hPa,cloud_cover_600hPa," +
    "cloud_cover_550hPa,cloud_cover_500hPa,cloud_cover_450hPa,cloud_cover_400hPa," +
    "cloud_cover_350hPa,cloud_cover_300hPa,cloud_cover_250hPa," +
    "wind_speed_1000hPa,wind_speed_975hPa,wind_speed_950hPa,wind_speed_925hPa,wind_speed_900hPa," +
    "wind_speed_875hPa,wind_speed_850hPa,wind_speed_800hPa,wind_speed_750hPa," +
    "wind_speed_700hPa,wind_speed_650hPa,wind_speed_600hPa,wind_speed_550hPa,wind_speed_500hPa," +
    "wind_speed_450hPa,wind_speed_400hPa,wind_speed_350hPa,wind_speed_300hPa,wind_speed_250hPa," +
    "wind_direction_1000hPa,wind_direction_975hPa,wind_direction_950hPa,wind_direction_925hPa,wind_direction_900hPa," +
    "wind_direction_875hPa,wind_direction_850hPa,wind_direction_800hPa,wind_direction_750hPa," +
    "wind_direction_700hPa,wind_direction_650hPa,wind_direction_600hPa,wind_direction_550hPa,wind_direction_500hPa," +
    "wind_direction_450hPa,wind_direction_400hPa,wind_direction_350hPa,wind_direction_300hPa,wind_direction_250hPa," +
    "geopotential_height_1000hPa,geopotential_height_975hPa,geopotential_height_950hPa,geopotential_height_925hPa,geopotential_height_900hPa," +
    "geopotential_height_875hPa,geopotential_height_850hPa,geopotential_height_800hPa,geopotential_height_750hPa," +
    "geopotential_height_700hPa,geopotential_height_650hPa,geopotential_height_600hPa,geopotential_height_550hPa,geopotential_height_500hPa," +
    "geopotential_height_450hPa,geopotential_height_400hPa,geopotential_height_350hPa,geopotential_height_300hPa,geopotential_height_250hPa"
