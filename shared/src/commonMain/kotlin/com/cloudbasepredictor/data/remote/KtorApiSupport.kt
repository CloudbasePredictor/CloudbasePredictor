@file:Suppress("MatchingDeclarationName")

package com.cloudbasepredictor.data.remote

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.call.body
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.ResponseException
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

class RemoteApiException(
    val statusCode: Int,
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

fun createCloudbaseHttpClient(
    jsonConfiguration: Json,
    userAgent: String? = null,
): HttpClient {
    return HttpClient {
        configureCloudbaseClient(jsonConfiguration, userAgent)
    }
}

fun createCloudbaseHttpClient(
    engine: HttpClientEngine,
    jsonConfiguration: Json,
    userAgent: String? = null,
): HttpClient {
    return HttpClient(engine) {
        configureCloudbaseClient(jsonConfiguration, userAgent)
    }
}

private fun HttpClientConfig<*>.configureCloudbaseClient(
    jsonConfiguration: Json,
    userAgent: String?,
) {
    expectSuccess = false
    install(ContentNegotiation) {
        json(jsonConfiguration)
    }
    defaultRequest {
        if (userAgent != null) {
            header(HttpHeaders.UserAgent, userAgent)
        }
    }
}

internal suspend inline fun <reified T> executeJsonRequest(
    request: suspend () -> HttpResponse,
): T {
    val response = try {
        request()
    } catch (exception: ResponseException) {
        throw RemoteApiException(
            statusCode = exception.response.status.value,
            message = "Remote API returned HTTP ${exception.response.status.value}.",
            cause = exception,
        )
    }
    if (!response.status.isSuccess()) {
        throw RemoteApiException(
            statusCode = response.status.value,
            message = "Remote API returned HTTP ${response.status.value}.",
        )
    }
    return response.body()
}
