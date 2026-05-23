package com.cloudbasepredictor.di

import com.cloudbasepredictor.BuildConfig
import com.cloudbasepredictor.data.remote.OpenMeteoApi
import com.cloudbasepredictor.data.remote.ParaglidingEarthApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import javax.inject.Qualifier
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ParaglidingEarthRetrofit

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    @Provides
    @Singleton
    fun provideJson(): Json {
        return Json {
            ignoreUnknownKeys = true
            explicitNulls = false
        }
    }

    @Provides
    fun provideLoggingInterceptor(): HttpLoggingInterceptor {
        return HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.BASIC
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
        }
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(
        loggingInterceptor: HttpLoggingInterceptor,
    ): OkHttpClient {
        val userAgentInterceptor = Interceptor { chain ->
            val suffix = if (BuildConfig.DEBUG) "Android-debug" else "Android"
            val request = chain.request().newBuilder()
                .header("User-Agent", "CloudbasePredictor/${BuildConfig.VERSION_NAME} ($suffix)")
                .build()
            chain.proceed(request)
        }
        return OkHttpClient.Builder()
            .addInterceptor(userAgentInterceptor)
            .addInterceptor(loggingInterceptor)
            .build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(
        okHttpClient: OkHttpClient,
        json: Json,
    ): Retrofit {
        return Retrofit.Builder()
            .baseUrl("https://api.open-meteo.com/")
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
    }

    @Provides
    @Singleton
    @ParaglidingEarthRetrofit
    fun provideParaglidingEarthRetrofit(
        okHttpClient: OkHttpClient,
        json: Json,
    ): Retrofit {
        return Retrofit.Builder()
            .baseUrl("https://www.paragliding.earth/api/")
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
    }

    @Provides
    @Singleton
    fun provideOpenMeteoApi(
        retrofit: Retrofit,
    ): OpenMeteoApi = retrofit.create(OpenMeteoApi::class.java)

    @Provides
    @Singleton
    fun provideParaglidingEarthApi(
        @ParaglidingEarthRetrofit retrofit: Retrofit,
    ): ParaglidingEarthApi = retrofit.create(ParaglidingEarthApi::class.java)
}
