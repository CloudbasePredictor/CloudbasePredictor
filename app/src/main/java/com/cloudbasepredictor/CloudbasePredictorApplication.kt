package com.cloudbasepredictor

import android.app.Application
import android.os.Build
import com.cloudbasepredictor.data.forecast.ForecastCacheMaintenance
import com.cloudbasepredictor.di.cloudbasePredictorUserAgentInterceptor
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import org.maplibre.android.MapLibre
import org.maplibre.android.module.http.HttpRequestUtil
import timber.log.Timber

@HiltAndroidApp
class CloudbasePredictorApplication : Application() {

    @Inject
    lateinit var forecastCacheMaintenance: ForecastCacheMaintenance

    override fun onCreate() {
        super.onCreate()

        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }

        // Enable MapLibre ambient tile cache (200 MB).
        MapLibre.getInstance(this)
        configureMapLibreHttpClient()

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

        forecastCacheMaintenance.scheduleStartupCleanup()
    }

    private fun configureMapLibreHttpClient() {
        HttpRequestUtil.setOkHttpClient(
            OkHttpClient.Builder()
                .dispatcher(mapLibreDispatcher())
                .addInterceptor(cloudbasePredictorUserAgentInterceptor())
                .build(),
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
