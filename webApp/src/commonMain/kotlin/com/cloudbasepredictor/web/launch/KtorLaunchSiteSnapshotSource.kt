package com.cloudbasepredictor.web.launch

import com.cloudbasepredictor.data.remote.ParaglidingEarthFeatureCollection
import com.cloudbasepredictor.data.remote.RemoteApiException
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.http.isSuccess

/**
 * Fetches the launch-site snapshot over HTTP using the shared Cloudbase [HttpClient]. [baseUrl] must
 * be an absolute, same-origin URL ending in `data/launch-sites/` (computed from `document.baseURI`
 * so it works both locally and under the GitHub Pages `/CloudbasePredictor/` base path).
 */
class KtorLaunchSiteSnapshotSource(
    private val httpClient: HttpClient,
    baseUrl: String,
) : LaunchSiteSnapshotSource {
    private val normalizedBaseUrl: String = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"

    override suspend fun loadManifest(): LaunchSiteSnapshotManifest {
        val response = httpClient.get("${normalizedBaseUrl}manifest.json")
        ensureSuccess(response)
        return response.body()
    }

    override suspend fun loadTile(relativePath: String): ParaglidingEarthFeatureCollection {
        val response = httpClient.get("$normalizedBaseUrl${relativePath.trimStart('/')}")
        ensureSuccess(response)
        return response.body()
    }

    private fun ensureSuccess(response: HttpResponse) {
        if (!response.status.isSuccess()) {
            throw RemoteApiException(
                statusCode = response.status.value,
                message = "Launch-site snapshot request returned HTTP ${response.status.value}.",
            )
        }
    }
}
