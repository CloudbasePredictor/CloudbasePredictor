package com.cloudbasepredictor.ui.screens.forecast.views

import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cloudbasepredictor.model.ForecastMode
import com.cloudbasepredictor.testutil.SimulatedTestData
import com.cloudbasepredictor.ui.screens.forecast.ForecastTestTags.THERMIC_CURSOR_PANEL
import com.cloudbasepredictor.ui.screens.forecast.ForecastTestTags.THERMIC_VIEW
import com.cloudbasepredictor.ui.screens.forecast.ForecastTestTags.WIND_CHART_CANVAS
import com.cloudbasepredictor.ui.theme.CloudbasePredictorTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Verifies that the system back button dismisses an open tap overlay before it would navigate away,
 * for every forecast view that has one (Thermic, Wind; Stüve is covered separately).
 */
@RunWith(AndroidJUnit4::class)
class ForecastOverlayBackDismissInstrumentedTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun thermicView_backDismissesCursorPanelBeforeNavigating() {
        composeRule.setContent {
            CloudbasePredictorTheme {
                ThermicForecastView(
                    uiState = SimulatedTestData.forecastUiState(
                        composeRule.activity,
                        mode = ForecastMode.THERMIC,
                    ),
                )
            }
        }
        composeRule.waitForIdle()

        // Tap to show the crosshair info panel.
        composeRule.onNodeWithTag(THERMIC_VIEW).performTouchInput { click(center) }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(THERMIC_CURSOR_PANEL).assertExists()

        // Back must remove the overlay (and not navigate away).
        composeRule.runOnUiThread {
            composeRule.activity.onBackPressedDispatcher.onBackPressed()
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(THERMIC_CURSOR_PANEL).assertDoesNotExist()
    }

    @Test
    fun windView_backDismissesCrosshairBeforeNavigating() {
        composeRule.setContent {
            CloudbasePredictorTheme {
                WindForecastView(
                    uiState = SimulatedTestData.forecastUiState(
                        composeRule.activity,
                        mode = ForecastMode.WIND,
                    ),
                )
            }
        }
        composeRule.waitForIdle()

        // Tap to place the crosshair overlay.
        composeRule.onNodeWithTag(WIND_CHART_CANVAS).performTouchInput { click(center) }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(WIND_CHART_CANVAS)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "cursor"))

        // Back must clear the crosshair (and not navigate away).
        composeRule.runOnUiThread {
            composeRule.activity.onBackPressedDispatcher.onBackPressed()
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(WIND_CHART_CANVAS)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "idle"))
    }
}
