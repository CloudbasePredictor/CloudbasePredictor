package com.cloudbasepredictor.screenshot

import android.graphics.Bitmap
import android.os.Environment
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.click
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.test.platform.app.InstrumentationRegistry
import com.cloudbasepredictor.model.ForecastMode
import com.cloudbasepredictor.testutil.SimulatedTestData
import com.cloudbasepredictor.ui.preview.PreviewData
import com.cloudbasepredictor.ui.screens.forecast.DEFAULT_TOP_ALTITUDE_KM
import com.cloudbasepredictor.ui.screens.forecast.ForecastScreen
import com.cloudbasepredictor.ui.screens.forecast.ForecastTestTags.STUVE_CHART_CANVAS
import com.cloudbasepredictor.ui.screens.forecast.ForecastReadyUiState
import com.cloudbasepredictor.ui.screens.map.MapScreen
import com.cloudbasepredictor.ui.theme.CloudbasePredictorTheme
import org.junit.Rule
import org.junit.Test
import java.io.File

/**
 * Screenshot capture tests — render screens with simulated Brauneck data and save PNG screenshots.
 *
 * These tests are NOT part of the regular test suite. Run them explicitly:
 *   ./gradlew :app:connectedDebugAndroidTest \
 *       -Pandroid.testInstrumentationRunnerArguments.class=com.cloudbasepredictor.screenshot.ScreenshotCaptureTest
 *
 * Screenshots are saved to /sdcard/Pictures/CloudbaseScreenshots/ on the device.
 * Pull them with:
 *   adb pull /sdcard/Pictures/CloudbaseScreenshots/ app/screenshots/
 */
class ScreenshotCaptureTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun simulatedState(mode: ForecastMode): ForecastReadyUiState {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        return SimulatedTestData.forecastUiState(context, mode = mode)
    }

    @Test
    fun captureThermicForecast() {
        captureScreen("forecast_thermic") {
            CloudbasePredictorTheme {
                ForecastScreen(
                    uiState = simulatedState(ForecastMode.THERMIC),
                    onDateSelected = {},
                    onOpenMap = {},
                )
            }
        }
    }

    @Test
    fun captureStuveForecast() {
        val baseState = simulatedState(ForecastMode.STUVE)
        var visibleTopAltitudeKm by mutableFloatStateOf(DEFAULT_TOP_ALTITUDE_KM)

        composeRule.setContent {
            CloudbasePredictorTheme {
                ForecastScreen(
                    uiState = baseState.copy(
                        chartViewport = baseState.chartViewport.copy(
                            visibleTopAltitudeKm = visibleTopAltitudeKm,
                        ),
                    ),
                    onDateSelected = {},
                    onForecastViewportTopChanged = { visibleTopAltitudeKm = it },
                    onOpenMap = {},
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.waitForIdle()
        captureCurrentContent("forecast_stuve")
    }

    @Test
    fun captureStuveForecastSelectedLevel() {
        val baseState = simulatedState(ForecastMode.STUVE)
        var visibleTopAltitudeKm by mutableFloatStateOf(DEFAULT_TOP_ALTITUDE_KM)

        composeRule.setContent {
            CloudbasePredictorTheme {
                ForecastScreen(
                    uiState = baseState.copy(
                        chartViewport = baseState.chartViewport.copy(
                            visibleTopAltitudeKm = visibleTopAltitudeKm,
                        ),
                    ),
                    onDateSelected = {},
                    onForecastViewportTopChanged = { visibleTopAltitudeKm = it },
                    onOpenMap = {},
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(STUVE_CHART_CANVAS).performTouchInput {
            click(center)
        }
        composeRule.waitForIdle()

        captureCurrentContent("forecast_stuve_selected")
    }

    @Test
    fun captureStuveForecastSelectedMidLevel() {
        val baseState = simulatedState(ForecastMode.STUVE)
        var visibleTopAltitudeKm by mutableFloatStateOf(DEFAULT_TOP_ALTITUDE_KM)

        composeRule.setContent {
            CloudbasePredictorTheme {
                ForecastScreen(
                    uiState = baseState.copy(
                        chartViewport = baseState.chartViewport.copy(
                            visibleTopAltitudeKm = visibleTopAltitudeKm,
                        ),
                    ),
                    onDateSelected = {},
                    onForecastViewportTopChanged = { visibleTopAltitudeKm = it },
                    onOpenMap = {},
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(STUVE_CHART_CANVAS).performTouchInput {
            click(Offset(width * 0.48f, height * 0.47f))
        }
        composeRule.waitForIdle()

        captureCurrentContent("forecast_stuve_selected_midlevel")
    }

    @Test
    fun captureStuveForecastSelectedLowerMidLevel() {
        val baseState = simulatedState(ForecastMode.STUVE)
        var visibleTopAltitudeKm by mutableFloatStateOf(DEFAULT_TOP_ALTITUDE_KM)

        composeRule.setContent {
            CloudbasePredictorTheme {
                ForecastScreen(
                    uiState = baseState.copy(
                        chartViewport = baseState.chartViewport.copy(
                            visibleTopAltitudeKm = visibleTopAltitudeKm,
                        ),
                    ),
                    onDateSelected = {},
                    onForecastViewportTopChanged = { visibleTopAltitudeKm = it },
                    onOpenMap = {},
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(STUVE_CHART_CANVAS).performTouchInput {
            click(Offset(width * 0.58f, height * 0.66f))
        }
        composeRule.waitForIdle()

        captureCurrentContent("forecast_stuve_selected_lower_midlevel")
    }

    @Test
    fun captureWindForecast() {
        captureScreen("forecast_wind") {
            CloudbasePredictorTheme(darkTheme = false) {
                ForecastScreen(
                    uiState = simulatedState(ForecastMode.WIND),
                    onDateSelected = {},
                    onOpenMap = {},
                )
            }
        }
    }

    @Test
    fun captureCloudForecast() {
        captureScreen("forecast_cloud") {
            CloudbasePredictorTheme {
                ForecastScreen(
                    uiState = simulatedState(ForecastMode.CLOUD),
                    onDateSelected = {},
                    onOpenMap = {},
                )
            }
        }
    }

    @Test
    fun captureForecastLoading() {
        captureScreen("forecast_loading") {
            CloudbasePredictorTheme {
                ForecastScreen(
                    uiState = PreviewData.forecastLoadingUiState.copy(
                        selectedPlace = SimulatedTestData.brauneckPlace,
                    ),
                    onDateSelected = {},
                    onOpenMap = {},
                )
            }
        }
    }

    @Test
    fun captureForecastLoadingDark() {
        captureScreen("forecast_loading_dark") {
            CloudbasePredictorTheme(darkTheme = true) {
                ForecastScreen(
                    uiState = PreviewData.forecastLoadingUiState.copy(
                        selectedPlace = SimulatedTestData.brauneckPlace,
                    ),
                    onDateSelected = {},
                    onOpenMap = {},
                )
            }
        }
    }

    @Test
    fun captureForecastError() {
        captureScreen("forecast_error") {
            CloudbasePredictorTheme {
                ForecastScreen(
                    uiState = PreviewData.forecastErrorUiState.copy(
                        selectedPlace = SimulatedTestData.brauneckPlace,
                    ),
                    onDateSelected = {},
                    onOpenMap = {},
                )
            }
        }
    }

    @Test
    fun captureMapSelectedPoint() {
        captureScreen("map_selected_point") {
            CloudbasePredictorTheme {
                MapScreen(
                    uiState = PreviewData.mapUiState,
                    onMapTapped = { _, _ -> },
                    onFavoriteTapped = {},
                    onLaunchSiteTapped = {},
                    onOpenForecast = {},
                    onFavoriteClick = {},
                    onSaveCameraPosition = { _, _, _ -> },
                    autoOpenFavoritesOnStartup = false,
                )
            }
        }
    }

    // ── Raised map variants ───────────────────────────

    @Test
    fun captureThermicForecastWithMap() {
        captureScreen("forecast_thermic_map") {
            CloudbasePredictorTheme {
                ForecastScreen(
                    uiState = simulatedState(ForecastMode.THERMIC),
                    onDateSelected = {},
                    onOpenMap = {},
                    initiallyExpandedMap = true,
                )
            }
        }
    }

    @Test
    fun captureWindForecastWithMap() {
        captureScreen("forecast_wind_map") {
            CloudbasePredictorTheme {
                ForecastScreen(
                    uiState = simulatedState(ForecastMode.WIND),
                    onDateSelected = {},
                    onOpenMap = {},
                    initiallyExpandedMap = true,
                )
            }
        }
    }

    @Test
    fun captureCloudForecastWithMap() {
        captureScreen("forecast_cloud_map") {
            CloudbasePredictorTheme {
                ForecastScreen(
                    uiState = simulatedState(ForecastMode.CLOUD),
                    onDateSelected = {},
                    onOpenMap = {},
                    initiallyExpandedMap = true,
                )
            }
        }
    }

    @Test
    fun captureStuveForecastWithMap() {
        captureScreen("forecast_stuve_map") {
            CloudbasePredictorTheme {
                ForecastScreen(
                    uiState = simulatedState(ForecastMode.STUVE),
                    onDateSelected = {},
                    onOpenMap = {},
                    initiallyExpandedMap = true,
                )
            }
        }
    }

    // ── Dark theme variants ─────────────────────────────

    @Test
    fun captureThermicForecastDark() {
        captureScreen("forecast_thermic_dark") {
            CloudbasePredictorTheme(darkTheme = true) {
                ForecastScreen(
                    uiState = simulatedState(ForecastMode.THERMIC),
                    onDateSelected = {},
                    onOpenMap = {},
                )
            }
        }
    }

    @Test
    fun captureStuveForecastDark() {
        captureScreen("forecast_stuve_dark") {
            CloudbasePredictorTheme(darkTheme = true) {
                ForecastScreen(
                    uiState = simulatedState(ForecastMode.STUVE),
                    onDateSelected = {},
                    onOpenMap = {},
                )
            }
        }
    }

    @Test
    fun captureWindForecastDark() {
        captureScreen("forecast_wind_dark") {
            CloudbasePredictorTheme(darkTheme = true) {
                ForecastScreen(
                    uiState = simulatedState(ForecastMode.WIND),
                    onDateSelected = {},
                    onOpenMap = {},
                )
            }
        }
    }

    @Test
    fun captureCloudForecastDark() {
        captureScreen("forecast_cloud_dark") {
            CloudbasePredictorTheme(darkTheme = true) {
                ForecastScreen(
                    uiState = simulatedState(ForecastMode.CLOUD),
                    onDateSelected = {},
                    onOpenMap = {},
                )
            }
        }
    }

    private fun captureScreen(name: String, content: @Composable () -> Unit) {
        composeRule.setContent(content)
        composeRule.waitForIdle()
        captureCurrentContent(name)
    }

    private fun captureCurrentContent(name: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val bitmap = composeRule.onRoot().captureToImage()
            .let { imageBitmap ->
                val androidBitmap = Bitmap.createBitmap(
                    imageBitmap.width,
                    imageBitmap.height,
                    Bitmap.Config.ARGB_8888,
                )
                val buffer = IntArray(imageBitmap.width * imageBitmap.height)
                imageBitmap.readPixels(buffer)
                androidBitmap.setPixels(
                    buffer, 0, imageBitmap.width,
                    0, 0, imageBitmap.width, imageBitmap.height,
                )
                androidBitmap
            }

        val dir = File(
            requireNotNull(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)),
            "CloudbaseScreenshots",
        )
        dir.mkdirs()
        val file = File(dir, "$name.png")
        file.outputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }

        val sharedDir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
            "CloudbaseScreenshots",
        )
        val sharedFile = File(sharedDir, "$name.png")
        instrumentation.uiAutomation
            .executeShellCommand("mkdir -p ${sharedDir.absolutePath}")
            .close()
        instrumentation.uiAutomation
            .executeShellCommand("cp ${file.absolutePath} ${sharedFile.absolutePath}")
            .close()
    }
}
