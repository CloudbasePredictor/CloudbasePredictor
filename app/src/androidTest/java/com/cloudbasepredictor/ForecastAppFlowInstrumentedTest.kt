package com.cloudbasepredictor

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cloudbasepredictor.model.ForecastMode
import com.cloudbasepredictor.model.PlaceLocation
import com.cloudbasepredictor.model.SavedPlace
import com.cloudbasepredictor.testutil.SimulatedTestData
import com.cloudbasepredictor.ui.CloudbasePredictorApp
import com.cloudbasepredictor.ui.navigation.CloudbaseNavGraph
import com.cloudbasepredictor.ui.screens.forecast.ForecastScreen
import com.cloudbasepredictor.ui.screens.forecast.ForecastTestTags.CLOUD_MODE_TAB
import com.cloudbasepredictor.ui.screens.forecast.ForecastTestTags.CLOUD_VIEW
import com.cloudbasepredictor.ui.screens.forecast.ForecastTestTags.HELP_BUTTON
import com.cloudbasepredictor.ui.screens.forecast.ForecastTestTags.STUVE_MODE_TAB
import com.cloudbasepredictor.ui.screens.forecast.ForecastTestTags.STUVE_VIEW
import com.cloudbasepredictor.ui.screens.forecast.ForecastTestTags.THERMIC_MODE_TAB
import com.cloudbasepredictor.ui.screens.forecast.ForecastTestTags.THERMIC_VIEW
import com.cloudbasepredictor.ui.screens.forecast.ForecastTestTags.WIND_MODE_TAB
import com.cloudbasepredictor.ui.screens.forecast.ForecastTestTags.WIND_VIEW
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ForecastAppFlowInstrumentedTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Before
    fun setContent() {
        composeRule.setContent {
            var selectedPlace by remember { mutableStateOf<SavedPlace?>(null) }
            var selectedMode by remember { mutableStateOf(ForecastMode.THERMIC) }

            CloudbasePredictorApp(
                navGraph = { modifier, navController ->
                    CloudbaseNavGraph(
                        navController = navController,
                        modifier = modifier,
                        mapDestination = { onOpenForecast, _ ->
                            TestMapDestination(
                                selectedPlace = selectedPlace,
                                onSelectLocation = {
                                    selectedPlace = SimulatedTestData.brauneckPlace
                                },
                                onOpenForecast = onOpenForecast,
                            )
                        },
                        forecastDestination = { _, onOpenMap, _ ->
                            val context = composeRule.activity
                            ForecastScreen(
                                uiState = SimulatedTestData.forecastUiState(context, mode = selectedMode).copy(
                                    selectedPlace = selectedPlace ?: SimulatedTestData.brauneckPlace,
                                ),
                                onDateSelected = {},
                                onForecastModeSelected = { selectedMode = it },
                                onOpenMap = onOpenMap,
                            )
                        },
                    )
                },
            )
        }
    }

    @Test
    fun appShell_selectLocationAndOpenForecast_showsSelectedPlace() {
        composeRule.onNodeWithText("Select Brauneck").performClick()
        composeRule.onNodeWithText("Brauneck Süd").assertIsDisplayed()

        composeRule.onNodeWithText("Open forecast").performClick()

        composeRule.onNodeWithTag(THERMIC_VIEW).assertIsDisplayed()
        composeRule.onNodeWithText("Brauneck Süd").assertIsDisplayed()
    }

    @Test
    fun forecastTabs_switchDisplayedViews() {
        openForecast()

        composeRule.onNodeWithTag(STUVE_MODE_TAB).performClick()
        composeRule.onNodeWithTag(STUVE_VIEW).assertIsDisplayed()

        composeRule.onNodeWithTag(WIND_MODE_TAB).performClick()
        composeRule.onNodeWithTag(WIND_VIEW).assertIsDisplayed()

        composeRule.onNodeWithTag(CLOUD_MODE_TAB).performClick()
        composeRule.onNodeWithTag(CLOUD_VIEW).assertIsDisplayed()

        composeRule.onNodeWithTag(THERMIC_MODE_TAB).performClick()
        composeRule.onNodeWithTag(THERMIC_VIEW).assertIsDisplayed()
    }

    @Test
    fun forecastHelpDialog_reflectsSelectedMode() {
        openForecast()

        composeRule.onNodeWithTag(HELP_BUTTON).performClick()
        composeRule.onNodeWithText("Thermic forecast help").assertIsDisplayed()
        composeRule.onNodeWithText("Lines, bands and diagnostics:").assertIsDisplayed()
        composeRule.onNodeWithText("Colored squares are time-altitude bins", substring = true)
            .assertIsDisplayed()
        composeRule.onNodeWithText("Tap a cell for time, altitude, lift range", substring = true)
            .assertIsDisplayed()
        composeRule.onAllNodesWithText("Showing the thermic forecast", substring = true).assertCountEquals(0)
        composeRule.onNodeWithText("Close").performClick()

        composeRule.onNodeWithTag(STUVE_MODE_TAB).performClick()
        composeRule.onNodeWithTag(HELP_BUTTON).performClick()
        composeRule.onNodeWithText("Skew-T log-P diagram").assertIsDisplayed()
        composeRule.onNodeWithText("Lines:").assertIsDisplayed()
        composeRule.onNodeWithText("Controls:").assertIsDisplayed()
        composeRule.onNodeWithText("Green - parcel path from your tap or heating change").assertIsDisplayed()
        composeRule.onNodeWithText("Close").performClick()

        composeRule.onNodeWithTag(WIND_MODE_TAB).performClick()
        composeRule.onNodeWithTag(HELP_BUTTON).performClick()
        composeRule.onNodeWithText("Wind forecast help").assertIsDisplayed()
        composeRule.onNodeWithText("Moisture cues:").assertIsDisplayed()
        composeRule.onNodeWithText("Orange CCL line - estimated cumulus base when heating is sufficient.")
            .assertIsDisplayed()
        composeRule.onNodeWithText("Close").performClick()

        composeRule.onNodeWithTag(CLOUD_MODE_TAB).performClick()
        composeRule.onNodeWithTag(HELP_BUTTON).performClick()
        composeRule.onNodeWithText("Cloud forecast help").assertIsDisplayed()
        composeRule.onNodeWithText("Sun h - direct sunshine reaching the ground.")
            .assertIsDisplayed()
        composeRule.onAllNodesWithText("Showing the cloud forecast", substring = true).assertCountEquals(0)
    }

    @Test
    fun forecastTopBarMapButton_returnsToMapDestination() {
        openForecast()

        composeRule.onNodeWithContentDescription("Open map").performClick()
        composeRule.onNodeWithText("Test map destination").assertIsDisplayed()
    }

    private fun openForecast() {
        composeRule.onNodeWithText("Select Brauneck").performClick()
        composeRule.onNodeWithText("Open forecast").performClick()
        composeRule.onNodeWithTag(THERMIC_VIEW).assertIsDisplayed()
    }
}

/**
 * Deterministic test harness for forecast flow checks that need a selected place without relying
 * on the production map provider. This complements, but does not replace, real `MainActivity`
 * instrumentation coverage.
 */
@Composable
private fun TestMapDestination(
    selectedPlace: SavedPlace?,
    onSelectLocation: () -> Unit,
    onOpenForecast: (PlaceLocation) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(text = "Test map destination")
        selectedPlace?.let { place ->
            Text(text = place.name)
        }
        Button(onClick = onSelectLocation) {
            Text(text = "Select Brauneck")
        }
        Button(
            onClick = {
                selectedPlace?.let { place ->
                    onOpenForecast(PlaceLocation.fromSavedPlace(place))
                }
            },
            enabled = selectedPlace != null,
        ) {
            Text(text = "Open forecast")
        }
    }
}
