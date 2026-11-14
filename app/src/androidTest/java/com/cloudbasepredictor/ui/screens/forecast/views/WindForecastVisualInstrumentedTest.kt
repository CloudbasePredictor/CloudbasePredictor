package com.cloudbasepredictor.ui.screens.forecast.views

import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.click
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.TouchInjectionScope
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.compose.ui.unit.dp
import com.cloudbasepredictor.model.ForecastMode
import com.cloudbasepredictor.testutil.SimulatedTestData
import com.cloudbasepredictor.ui.preview.ForecastPreviewData
import com.cloudbasepredictor.ui.screens.forecast.ForecastReadyUiState
import com.cloudbasepredictor.ui.screens.forecast.ForecastScreen
import com.cloudbasepredictor.ui.screens.forecast.ForecastTestTags.WIND_CHART_CANVAS
import com.cloudbasepredictor.ui.screens.forecast.ForecastTestTags.WIND_VIEW
import com.cloudbasepredictor.ui.theme.CloudbasePredictorTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs

/**
 * Uses simulated forecast data only, so the test never depends on the real forecast backend.
 */
@RunWith(AndroidJUnit4::class)
class WindForecastVisualInstrumentedTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun windView_rendersThermicLevelMarkersOverWindSpeedBackground() {
        composeRule.setContent {
            CloudbasePredictorTheme {
                WindForecastView(
                    uiState = SimulatedTestData.forecastUiState(
                        context = composeRule.activity,
                        mode = ForecastMode.WIND,
                    ),
                )
            }
        }
        composeRule.waitForIdle()

        val image = composeRule.onNodeWithTag(WIND_VIEW).captureToImage()
        val pixels = IntArray(image.width * image.height)
        image.readPixels(pixels)

        assertTrue(
            "Wind forecast should draw the CCL overlay on top of the wind-speed background.",
            pixels.countNearColor(target = Color.rgb(0xFF, 0x8C, 0x00), tolerance = 2) > 100,
        )
        assertTrue(
            "Wind forecast should draw the freezing-level overlay on top of the wind-speed background.",
            pixels.countNearColor(target = Color.rgb(0x00, 0xBC, 0xD4), tolerance = 2) > 100,
        )
        assertTrue(
            "Wind forecast should keep the wind-speed cell colors visible behind overlays.",
            pixels.countWindSpeedBackgroundColors() > pixels.size / 20,
        )
    }

    @Test
    fun sparseWind_surfaceRowAndSourceLevelsSelectSourceValues() {
        val state = showSparseWind()
        tapSparse(hourIndex = 0, y = { surfaceRowY() })
        assertCursorContains("06h", "10 m AGL", "8 km/h", "350°")
        assertCursorExcludes("≈")

        // Tapping close to a source glyph snaps to its exact hourly height.
        tapSparse(hourIndex = 0, y = { altitudeY(state, 2.357f) + 6f * density() })
        assertCursorContains("06h", "2357 m ASL", "30 km/h", "270°")
        assertCursorExcludes("≈")

        tapSparse(hourIndex = 1, y = { surfaceRowY() })
        assertCursorContains("07h", "10 m AGL", "No data")

        tapSparse(hourIndex = 2, y = { surfaceRowY() })
        assertCursorContains("08h", "10 m AGL", "0 km/h")
        assertCursorExcludes("No data")
    }

    @Test
    fun sparseWind_cursorMarksInterpolationAndShowsUnsupportedOrMissingHours() {
        val state = showSparseWind()
        tapSparse(hourIndex = 0, y = { altitudeY(state, 1.5f) })
        assertCursorContains("06h", "≈")
        tapSparse(hourIndex = 0, y = { 20f * density() })
        assertCursorContains("06h", "No data")
        tapSparse(hourIndex = 3, y = { altitudeY(state, 1.5f) })
        assertCursorContains("09h", "No data")
    }

    @Test
    fun windView_fitsWholeDayAndLastHourSurfaceIsSelectable() {
        val state = SimulatedTestData.forecastUiState(composeRule.activity, mode = ForecastMode.WIND)
        composeRule.setContent {
            CloudbasePredictorTheme {
                WindForecastView(state)
            }
        }
        composeRule.waitForIdle()
        val lastHour = state.windChart.hours.last().toString().padStart(2, '0')
        composeRule.onNodeWithTag(WIND_CHART_CANVAS).performTouchInput {
            click(Offset(width - 2f * density(), surfaceRowY()))
        }
        assertCursorContains("${lastHour}h", "10 m AGL", "km/h")
    }

    @Test
    fun sparseWind_shortScreenWithExpandedMapKeepsSurfaceReadable() {
        composeRule.setContent {
            CloudbasePredictorTheme {
                Box(Modifier.height(390.dp)) {
                    ForecastScreen(
                        uiState = ForecastPreviewData.sparseWindState(),
                        onDateSelected = {},
                        onOpenMap = {},
                        initiallyExpandedMap = true,
                    )
                }
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(WIND_VIEW).performScrollTo()
        val density = density()
        val bounds = composeRule.onNodeWithTag(WIND_VIEW).fetchSemanticsNode().boundsInRoot
        assertTrue(
            "A short window should retain a usable wind chart; height was ${bounds.height / density} dp",
            bounds.height >= 160f * density,
        )
        composeRule.onNodeWithTag(WIND_VIEW).performTouchInput {
            click(Offset(AXIS_DP * density + (width - AXIS_DP * density) / 8f, surfaceRowY()))
        }
        assertCursorContains("06h", "10 m AGL", "8 km/h", "350°")
    }

    private fun showSparseWind(): ForecastReadyUiState {
        val state = ForecastPreviewData.sparseWindState()
        composeRule.setContent {
            CloudbasePredictorTheme {
                WindForecastView(state)
            }
        }
        composeRule.waitForIdle()
        return state
    }

    private fun density(): Float = composeRule.activity.resources.displayMetrics.density

    /** Taps the center of an hourly column of the sparse four-hour chart. */
    private fun tapSparse(hourIndex: Int, y: TouchInjectionScope.() -> Float) {
        composeRule.onNodeWithTag(WIND_CHART_CANVAS).performTouchInput {
            val plotLeft = AXIS_DP * density()
            click(Offset(plotLeft + (width - plotLeft) * (hourIndex + 0.5f) / 4f, y()))
        }
    }

    // Mirrors the wind chart layout: 26 dp 10 m row, 28 dp legend and 16 dp hour axis below the plot.
    private fun TouchInjectionScope.surfaceRowY(): Float = height - (28f + 16f + 13f) * density()

    private fun TouchInjectionScope.altitudeY(state: ForecastReadyUiState, altitudeKm: Float): Float {
        val plotBottom = height - BOTTOM_CHROME_DP * density()
        val range = state.chartViewport.visibleTopAltitudeKm
        return plotBottom * (1f - (altitudeKm - state.elevationKm) / range)
    }

    private fun assertCursorContains(vararg text: String) {
        composeRule.onNodeWithTag(WIND_CHART_CANVAS).assert(SemanticsMatcher("cursor contains ${text.toList()}") {
            val description = it.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty().joinToString("; ")
            text.all(description::contains)
        })
    }

    private fun assertCursorExcludes(text: String) {
        composeRule.onNodeWithTag(WIND_CHART_CANVAS).assert(SemanticsMatcher("cursor excludes $text") {
            !it.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty().joinToString("; ").contains(text)
        })
    }

    private companion object {
        const val AXIS_DP = 60f
        const val BOTTOM_CHROME_DP = 26f + 28f + 16f
    }

}

private fun IntArray.countNearColor(target: Int, tolerance: Int): Int {
    val targetRed = Color.red(target)
    val targetGreen = Color.green(target)
    val targetBlue = Color.blue(target)
    return count { color ->
        abs(Color.red(color) - targetRed) <= tolerance &&
            abs(Color.green(color) - targetGreen) <= tolerance &&
            abs(Color.blue(color) - targetBlue) <= tolerance
    }
}

private fun IntArray.countWindSpeedBackgroundColors(): Int {
    return count { color ->
        val red = Color.red(color)
        val green = Color.green(color)
        val blue = Color.blue(color)
        green > 70 && green > blue + 20 && red > 35
    }
}
