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
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.compose.ui.unit.dp
import com.cloudbasepredictor.model.ForecastMode
import com.cloudbasepredictor.testutil.SimulatedTestData
import com.cloudbasepredictor.ui.preview.ForecastPreviewData
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
        val state = SimulatedTestData.forecastUiState(composeRule.activity, mode = ForecastMode.WIND)
        val firstCclHour = state.windChart.cclKm.first().hour
        val visibleHours = state.windChart.hours.filter { it in firstCclHour..firstCclHour + 3 }
        composeRule.setContent {
            CloudbasePredictorTheme {
                WindForecastView(uiState = state.copy(windChart = state.windChart.copy(hours = visibleHours)))
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
    fun sparseWind_surfaceAndDisplacedLabelsSelectSourceValues() {
        showSparseWind()
        val density = composeRule.activity.resources.displayMetrics.density
        composeRule.onNodeWithTag(WIND_CHART_CANVAS).performTouchInput {
            click(Offset(60f * density + (width - 60f * density) / 8f, height - 92f * density))
        }
        assertCursorContains("06h", "10 m AGL", "8 km/h", "350°")
        assertCursorExcludes("≈")

        // The two low-level labels are separated; the upper one still selects its true 620 m source.
        composeRule.onNodeWithTag(WIND_CHART_CANVAS).performTouchInput {
            click(Offset(60f * density + (width - 60f * density) * 0.175f, height - 154f * density))
        }
        assertCursorContains("06h", "620 m ASL", "16 km/h", "90°")
        assertCursorExcludes("≈")

        composeRule.onNodeWithTag(WIND_CHART_CANVAS).performTouchInput {
            click(Offset(60f * density + (width - 60f * density) * 0.375f, height - 92f * density))
        }
        assertCursorContains("07h", "10 m AGL", "No data")

        composeRule.onNodeWithTag(WIND_CHART_CANVAS).performTouchInput {
            click(Offset(60f * density + (width - 60f * density) * 0.625f, height - 92f * density))
        }
        assertCursorContains("08h", "10 m AGL", "0 km/h")
        assertCursorExcludes("No data")
    }

    @Test
    fun sparseWind_cursorMarksInterpolationAndShowsUnsupportedOrMissingHours() {
        showSparseWind()
        val density = composeRule.activity.resources.displayMetrics.density
        composeRule.onNodeWithTag(WIND_CHART_CANVAS).performTouchInput {
            click(Offset(65f * density, (height - 112f * density) * 0.7f))
        }
        assertCursorContains("06h", "≈")
        composeRule.onNodeWithTag(WIND_CHART_CANVAS).performTouchInput {
            click(Offset(65f * density, 20f * density))
        }
        assertCursorContains("06h", "No data")
        composeRule.onNodeWithTag(WIND_CHART_CANVAS).performTouchInput {
            click(Offset(60f * density + (width - 60f * density) * 0.875f, (height - 112f * density) * 0.7f))
        }
        assertCursorContains("09h", "No data")
    }

    @Test
    fun windView_horizontalScrollKeepsLastHourSurfaceReadable() {
        composeRule.setContent {
            CloudbasePredictorTheme {
                WindForecastView(SimulatedTestData.forecastUiState(composeRule.activity, mode = ForecastMode.WIND))
            }
        }
        repeat(5) {
            composeRule.onNodeWithTag(WIND_VIEW).performTouchInput { swipeLeft() }
            composeRule.waitForIdle()
        }
        val density = composeRule.activity.resources.displayMetrics.density
        composeRule.onNodeWithTag(WIND_VIEW).performTouchInput {
            click(Offset(width - 38f * density, height - 92f * density))
        }
        assertCursorContains("22h", "10 m AGL", "km/h")
    }

    @Test
    fun sparseWind_shortScreenWithExpandedMapKeepsSurfaceReadable() {
        composeRule.setContent {
            CloudbasePredictorTheme {
                Box(Modifier.height(390.dp)) {
                    ForecastScreen(
                        uiState = ForecastPreviewData.stateForMode(ForecastMode.WIND),
                        onDateSelected = {},
                        onOpenMap = {},
                        initiallyExpandedMap = true,
                    )
                }
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(WIND_VIEW).performScrollTo()
        val density = composeRule.activity.resources.displayMetrics.density
        val bounds = composeRule.onNodeWithTag(WIND_VIEW).fetchSemanticsNode().boundsInRoot
        assertTrue(
            "A short window should retain a usable wind chart; height was ${bounds.height / density} dp",
            bounds.height >= 160f * density,
        )
        composeRule.onNodeWithTag(WIND_VIEW).performTouchInput {
            click(Offset(174f * density, height - 92f * density))
        }
        assertCursorContains("06h", "10 m AGL", "8 km/h", "350°")
    }

    private fun showSparseWind() {
        composeRule.setContent {
            CloudbasePredictorTheme {
                WindForecastView(ForecastPreviewData.stateForMode(ForecastMode.WIND))
            }
        }
        composeRule.waitForIdle()
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
