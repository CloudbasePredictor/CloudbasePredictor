package com.cloudbasepredictor

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.cloudbasepredictor.data.local.DatabaseErrorManager
import com.cloudbasepredictor.data.theme.ThemeRepository
import com.cloudbasepredictor.ui.CloudbasePredictorApp
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    @Inject
    lateinit var databaseErrorManager: DatabaseErrorManager

    @Inject
    lateinit var themeRepository: ThemeRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            CloudbasePredictorApp(
                databaseErrorManager = databaseErrorManager,
                themeRepository = themeRepository,
            )
        }
    }
}
