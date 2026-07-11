package com.cloudbasepredictor.data.remote

import com.cloudbasepredictor.model.PlaceLocation
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import kotlinx.serialization.Serializable

class OpenMeteoGeocodingDataSource(
    private val httpClient: HttpClient,
    baseUrl: String = OPEN_METEO_GEOCODING_BASE_URL,
) {
    private val searchEndpoint = "${baseUrl.trimEnd('/')}/v1/search"

    suspend fun search(query: String, count: Int = DEFAULT_RESULT_COUNT): List<PlaceLocation> {
        val normalized = query.trim()
        if (normalized.length < MINIMUM_QUERY_LENGTH) return emptyList()
        val response: OpenMeteoGeocodingResponse = executeJsonRequest {
            httpClient.get(searchEndpoint) {
                parameter("name", normalized)
                parameter("count", count.coerceIn(1, MAXIMUM_RESULT_COUNT))
                parameter("language", "en")
                parameter("format", "json")
            }
        }
        return response.results
            .orEmpty()
            .mapNotNull(OpenMeteoGeocodingResult::toPlaceLocationOrNull)
            .distinctBy { location -> location.latitude to location.longitude }
    }
}

@Serializable
internal data class OpenMeteoGeocodingResponse(
    val results: List<OpenMeteoGeocodingResult>? = null,
)

@Serializable
internal data class OpenMeteoGeocodingResult(
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val country: String? = null,
    val admin1: String? = null,
)

private fun OpenMeteoGeocodingResult.toPlaceLocationOrNull(): PlaceLocation? {
    val hasValidCoordinates =
        latitude.isFinite() && latitude in MINIMUM_LATITUDE..MAXIMUM_LATITUDE &&
            longitude.isFinite() && longitude in MINIMUM_LONGITUDE..MAXIMUM_LONGITUDE
    val displayName = listOf(name, admin1, country)
        .mapNotNull { value -> value?.trim() }
        .filter(String::isNotEmpty)
        .distinct()
        .joinToString(", ")
        .takeIf(String::isNotEmpty)
    return displayName
        ?.takeIf { hasValidCoordinates }
        ?.let { validName ->
            PlaceLocation(latitude = latitude, longitude = longitude, name = validName)
        }
}

const val OPEN_METEO_GEOCODING_BASE_URL = "https://geocoding-api.open-meteo.com"
private const val DEFAULT_RESULT_COUNT = 8
private const val MAXIMUM_RESULT_COUNT = 20
private const val MINIMUM_QUERY_LENGTH = 2
private const val MINIMUM_LATITUDE = -90.0
private const val MAXIMUM_LATITUDE = 90.0
private const val MINIMUM_LONGITUDE = -180.0
private const val MAXIMUM_LONGITUDE = 180.0
