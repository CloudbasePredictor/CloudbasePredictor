package com.cloudbasepredictor

import android.app.Application
import android.os.Build
import com.cloudbasepredictor.di.AppGraph
import com.cloudbasepredictor.di.cloudbasePredictorUserAgentInterceptor
import dev.zacsweers.metro.createGraphFactory
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import org.maplibre.android.MapLibre
import org.maplibre.android.module.http.HttpRequestUtil
import timber.log.Timber

class CloudbasePredictorApplication : Application() {

    lateinit var appGraph: AppGraph
        private set

    override fun onCreate() {
        super.onCreate()
        appGraph = createGraphFactory<AppGraph.Factory>().create(applicationContext)

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

        appGraph.forecastCacheMaintenance.scheduleStartupCleanup()
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
