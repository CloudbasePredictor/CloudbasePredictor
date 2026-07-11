package com.cloudbasepredictor.data.remote

import com.cloudbasepredictor.model.ForecastModel
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class KtorRemoteApiTest {
    @Test
    fun openMeteoApi_buildsDailyForecastRequestAndDecodesResponse() = runTest {
        val requests = mutableListOf<HttpRequestData>()
        val engine = MockEngine { request ->
            requests += request
            respondJson(DAILY_FORECAST_RESPONSE)
        }
        val client = createCloudbaseHttpClient(
            engine = engine,
            jsonConfiguration = testJson,
            userAgent = TEST_USER_AGENT,
        )

        try {
            val response = KtorOpenMeteoApi(
                httpClient = client,
                baseUrl = TEST_OPEN_METEO_BASE_URL,
            ).getForecast(
                latitude = 46.5,
                longitude = 7.8,
            )

            assertEquals(listOf("2026-07-11"), response.daily.time)
            val request = requests.single()
            assertEquals("/v1/forecast", request.url.encodedPath)
            assertEquals("46.5", request.url.parameters["latitude"])
            assertEquals("7.8", request.url.parameters["longitude"])
            assertEquals("14", request.url.parameters["forecast_days"])
            assertEquals("auto", request.url.parameters["timezone"])
            assertEquals(TEST_USER_AGENT, request.headers[HttpHeaders.UserAgent])
        } finally {
            client.close()
        }
    }

    @Test
    fun hourlyForecast_fallsBackOnBadRequestAndOmitsBestMatchModel() = runTest {
        val requestedModels = mutableListOf<String?>()
        val engine = MockEngine { request ->
            val model = request.url.parameters["models"]
            requestedModels += model
            if (model == ForecastModel.ICON_D2.apiName) {
                respondJson(
                    content = ERROR_RESPONSE,
                    status = HttpStatusCode.BadRequest,
                )
            } else {
                respondJson(HOURLY_FORECAST_RESPONSE)
            }
        }
        val client = createCloudbaseHttpClient(engine, testJson)
        val dataSource = OpenMeteoRemoteDataSource(
            KtorOpenMeteoApi(client, TEST_OPEN_METEO_BASE_URL),
        )

        try {
            val (resolvedModel, forecast) = dataSource.getHourlyForecastWithFallback(
                latitude = 46.5,
                longitude = 7.8,
                requestedModel = ForecastModel.ICON_D2,
            )

            assertEquals(ForecastModel.ICON_EU, resolvedModel)
            assertEquals(listOf<String?>("icon_d2", "icon_eu"), requestedModels)
            assertEquals(46.5, forecast.latitude)

            requestedModels.clear()
            dataSource.getHourlyForecastWithFallback(
                latitude = 46.5,
                longitude = 7.8,
                requestedModel = ForecastModel.BEST_MATCH,
            )
            assertEquals(1, requestedModels.size)
            assertNull(requestedModels.single())
        } finally {
            client.close()
        }
    }

    @Test
    fun hourlyForecast_doesNotFallbackOnNonBadRequest() = runTest {
        val engine = MockEngine {
            respondJson(
                content = ERROR_RESPONSE,
                status = HttpStatusCode.InternalServerError,
            )
        }
        val client = createCloudbaseHttpClient(engine, testJson)
        val dataSource = OpenMeteoRemoteDataSource(
            KtorOpenMeteoApi(client, TEST_OPEN_METEO_BASE_URL),
        )

        try {
            val exception = assertFailsWith<RemoteApiException> {
                dataSource.getHourlyForecastWithFallback(
                    latitude = 46.5,
                    longitude = 7.8,
                    requestedModel = ForecastModel.ICON_D2,
                )
            }
            assertEquals(500, exception.statusCode)
        } finally {
            client.close()
        }
    }

    @Test
    fun paraglidingEarthApi_buildsBoundingBoxRequest() = runTest {
        val requests = mutableListOf<HttpRequestData>()
        val engine = MockEngine { request ->
            requests += request
            respondJson(PARAGLIDING_EARTH_RESPONSE)
        }
        val client = createCloudbaseHttpClient(engine, testJson)

        try {
            val response = KtorParaglidingEarthApi(
                httpClient = client,
                baseUrl = TEST_PARAGLIDING_EARTH_BASE_URL,
            ).getLaunchSitesInBounds(
                north = 46.0,
                south = 45.0,
                west = 6.0,
                east = 7.0,
            )

            assertEquals(1, response.features.size)
            val request = requests.single()
            assertEquals("/api/geojson/getBoundingBoxSites.php", request.url.encodedPath)
            assertEquals("46.0", request.url.parameters["north"])
            assertEquals("45.0", request.url.parameters["south"])
            assertEquals("6.0", request.url.parameters["west"])
            assertEquals("7.0", request.url.parameters["east"])
            assertEquals("150", request.url.parameters["limit"])
            assertEquals("detailled", request.url.parameters["style"])
        } finally {
            client.close()
        }
    }

    private companion object {
        val testJson = Json {
            ignoreUnknownKeys = true
            explicitNulls = false
        }

        const val TEST_USER_AGENT = "CloudbasePredictor/test"
        const val TEST_OPEN_METEO_BASE_URL = "https://open-meteo.test"
        const val TEST_PARAGLIDING_EARTH_BASE_URL = "https://paragliding.test/api"
        const val DAILY_FORECAST_RESPONSE =
            """{"daily":{"time":["2026-07-11"],"temperature_2m_max":[20.0],"temperature_2m_min":[10.0],"weather_code":[1]}}"""
        const val HOURLY_FORECAST_RESPONSE =
            """{"latitude":46.5,"longitude":7.8,"hourly":{"time":[]}}"""
        const val PARAGLIDING_EARTH_RESPONSE =
            """{"features":[{"id":"launch-1"}]}"""
        const val ERROR_RESPONSE = """{"error":true}"""
    }
}

private fun io.ktor.client.engine.mock.MockRequestHandleScope.respondJson(
    content: String,
    status: HttpStatusCode = HttpStatusCode.OK,
) = respond(
    content = content,
    status = status,
    headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
)
