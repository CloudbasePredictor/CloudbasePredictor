package com.cloudbasepredictor.di

import com.cloudbasepredictor.BuildConfig
import okhttp3.Interceptor

private const val PROJECT_URL = "https://github.com/CloudbasePredictor/CloudbasePredictor"

internal fun cloudbasePredictorUserAgent(): String {
    val platform = if (BuildConfig.DEBUG) "Android-debug" else "Android"
    return "CloudbasePredictor/${BuildConfig.VERSION_NAME} ($platform; +$PROJECT_URL)"
}

internal fun cloudbasePredictorUserAgentInterceptor(): Interceptor {
    return Interceptor { chain ->
        val request = chain.request().newBuilder()
            .header("User-Agent", cloudbasePredictorUserAgent())
            .build()
        chain.proceed(request)
    }
}
