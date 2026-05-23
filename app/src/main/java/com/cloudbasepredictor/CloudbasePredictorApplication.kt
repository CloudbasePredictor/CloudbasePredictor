package com.cloudbasepredictor

import android.app.Application
import android.os.Build
import com.cloudbasepredictor.data.forecast.ForecastRepository
import com.cloudbasepredictor.di.cloudbasePredictorUserAgentInterceptor
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import org.maplibre.android.MapLibre
import org.maplibre.android.module.http.HttpRequestUtil
import timber.log.Timber

@HiltAndroidApp
class CloudbasePredictorApplication : Application() {

    @Inject
    lateinit var forecastRepository: ForecastRepository

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()

        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }

        configureMapLibreHttpClient()

        // Enable MapLibre ambient tile cache (200 MB).
        MapLibre.getInstance(this)
        org.maplibre.android.offline.OfflineManager.getInstance(this)
            .setMaximumAmbientCacheSize(
                200L * 1024 * 1024,
                object : org.maplibre.android.offline.OfflineManager.FileSourceCallback {
                    override fun onSuccess() {
                        Timber.d("Ambient tile cache set to 200 MB")
                    }
                    override fun onError(message: String) {
                        Timber.e("Failed to set ambient cache size: %s", message)
                    }
                },
            )

        // Schedule DB cleanup after 10 seconds.
        appScope.launch {
            delay(10_000L)
            val oneDayAgo = System.currentTimeMillis() - 24 * 60 * 60 * 1000L
            try {
                forecastRepository.cleanupOldForecasts(oneDayAgo)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "Forecast cleanup failed")
            }
        }
    }

    private fun configureMapLibreHttpClient() {
        HttpRequestUtil.setOkHttpClient(
            OkHttpClient.Builder()
                .dispatcher(mapLibreDispatcher())
                .addInterceptor(cloudbasePredictorUserAgentInterceptor())
                .build()
        )
    }

    private fun mapLibreDispatcher(): Dispatcher {
        return Dispatcher().apply {
            maxRequestsPerHost = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                20
            } else {
                10
            }
        }
    }
}
