package com.cloudbasepredictor

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.CompositionLocalProvider
import com.cloudbasepredictor.ui.CloudbasePredictorApp
import dev.zacsweers.metrox.viewmodel.LocalMetroViewModelFactory

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val appGraph = (application as CloudbasePredictorApplication).appGraph

        setContent {
            CompositionLocalProvider(
                LocalMetroViewModelFactory provides appGraph.metroViewModelFactory,
            ) {
                CloudbasePredictorApp(
                    databaseErrorManager = appGraph.databaseErrorManager,
                    themeRepository = appGraph.themeRepository,
                )
            }
        }
    }
}
