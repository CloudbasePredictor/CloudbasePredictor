package com.cloudbasepredictor.data.remote

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json

class OpenMeteoGeocodingApiTest {
    @Test
    fun searchBuildsAccessibleDisplayNamesAndDropsInvalidCoordinates() = runTest {
        val engine = MockEngine { request ->
            assertTrue(request.url.parameters["name"] == "Brauneck")
            respond(
                content = """
                    {"results":[
                      {"name":"Lenggries","admin1":"Bavaria","country":"Germany","latitude":47.68,"longitude":11.57},
                      {"name":"Invalid","latitude":120.0,"longitude":11.0}
                    ]}
                """.trimIndent(),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val client = createCloudbaseHttpClient(
            engine = engine,
            jsonConfiguration = Json { ignoreUnknownKeys = true },
        )

        val results = OpenMeteoGeocodingDataSource(client).search(" Brauneck ")

        assertEquals(1, results.size)
        assertEquals("Lenggries, Bavaria, Germany", results.single().name)
    }
}
