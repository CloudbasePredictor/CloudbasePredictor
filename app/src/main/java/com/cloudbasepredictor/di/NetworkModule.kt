package com.cloudbasepredictor.di

import com.cloudbasepredictor.data.remote.KtorOpenMeteoApi
import com.cloudbasepredictor.data.remote.KtorParaglidingEarthApi
import com.cloudbasepredictor.data.remote.OpenMeteoApi
import com.cloudbasepredictor.data.remote.OpenMeteoRemoteDataSource
import com.cloudbasepredictor.data.remote.ParaglidingEarthApi
import com.cloudbasepredictor.data.remote.createCloudbaseHttpClient
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import io.ktor.client.HttpClient
import kotlinx.serialization.json.Json

@BindingContainer
object NetworkModule {
    @Provides
    @SingleIn(AppScope::class)
    fun provideJson(): Json {
        return Json {
            ignoreUnknownKeys = true
            explicitNulls = false
        }
    }

    @Provides
    @SingleIn(AppScope::class)
    fun provideHttpClient(json: Json): HttpClient {
        return createCloudbaseHttpClient(
            jsonConfiguration = json,
            userAgent = cloudbasePredictorUserAgent(),
        )
    }

    @Provides
    @SingleIn(AppScope::class)
    fun provideOpenMeteoApi(
        httpClient: HttpClient,
    ): OpenMeteoApi = KtorOpenMeteoApi(httpClient)

    @Provides
    @SingleIn(AppScope::class)
    fun provideParaglidingEarthApi(
        httpClient: HttpClient,
    ): ParaglidingEarthApi = KtorParaglidingEarthApi(httpClient)

    @Provides
    @SingleIn(AppScope::class)
    fun provideOpenMeteoRemoteDataSource(
        openMeteoApi: OpenMeteoApi,
    ): OpenMeteoRemoteDataSource = OpenMeteoRemoteDataSource(openMeteoApi)
}
