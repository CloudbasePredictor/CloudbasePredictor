package com.cloudbasepredictor.di

import com.cloudbasepredictor.BuildConfig
import java.util.concurrent.TimeUnit
import okhttp3.Call
import okhttp3.Connection
import okhttp3.Interceptor
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import org.junit.Assert.assertEquals
import org.junit.Test

class UserAgentTest {
    @Test
    fun cloudbasePredictorUserAgent_identifiesAppBuildAndPublicProjectUrl() {
        val platform = if (BuildConfig.DEBUG) "Android-debug" else "Android"

        assertEquals(
            "CloudbasePredictor/${BuildConfig.VERSION_NAME} " +
                "($platform; +https://github.com/CloudbasePredictor/CloudbasePredictor)",
            cloudbasePredictorUserAgent(),
        )
    }

    @Test
    fun cloudbasePredictorUserAgentInterceptor_replacesExistingUserAgentHeader() {
        val originalRequest = Request.Builder()
            .url("https://example.com/tiles/0/0/0.png")
            .header("User-Agent", "MapLibre/12.0.1")
            .build()
        val chain = CapturingChain(originalRequest)

        cloudbasePredictorUserAgentInterceptor().intercept(chain)

        assertEquals(
            listOf(cloudbasePredictorUserAgent()),
            chain.proceededRequest.headers("User-Agent"),
        )
    }

    private class CapturingChain(
        private val originalRequest: Request,
    ) : Interceptor.Chain {
        lateinit var proceededRequest: Request

        override fun request(): Request = originalRequest

        override fun proceed(request: Request): Response {
            proceededRequest = request
            return Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(204)
                .message("No Content")
                .build()
        }

        override fun connection(): Connection? = null

        override fun call(): Call {
            error("Not needed for this test")
        }

        override fun connectTimeoutMillis(): Int = 0

        override fun withConnectTimeout(timeout: Int, unit: TimeUnit): Interceptor.Chain = this

        override fun readTimeoutMillis(): Int = 0

        override fun withReadTimeout(timeout: Int, unit: TimeUnit): Interceptor.Chain = this

        override fun writeTimeoutMillis(): Int = 0

        override fun withWriteTimeout(timeout: Int, unit: TimeUnit): Interceptor.Chain = this
    }
}
