package com.cloudbasepredictor.e2e

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cloudbasepredictor.CloudbasePredictorApplication
import com.cloudbasepredictor.model.ForecastModel
import com.cloudbasepredictor.model.PlaceLocation
import com.cloudbasepredictor.model.SavedPlace
import com.cloudbasepredictor.ui.screens.forecast.ForecastRoute
import com.cloudbasepredictor.ui.screens.forecast.ForecastTestTags.MODEL_OPTION_PREFIX
import com.cloudbasepredictor.ui.screens.forecast.ForecastTestTags.MODEL_SELECTOR_BUTTON
import com.cloudbasepredictor.ui.screens.forecast.ForecastTestTags.THERMIC_VIEW
import com.cloudbasepredictor.ui.theme.CloudbasePredictorTheme
import dev.zacsweers.metrox.viewmodel.LocalMetroViewModelFactory
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * E2E tests that exercise the real Open-Meteo backend.
 *
 * Each test opens the forecast on the thermic tab for a known European location
 * and verifies that selecting each weather model loads successfully and renders a chart.
 *
 * Run with: ./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.cloudbasepredictor.e2e.ForecastModelE2eTest
 */
@RunWith(AndroidJUnit4::class)
class ForecastModelE2eTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ForecastTestActivity>()

    /** Innsbruck, Austria — Central Europe, covered by ICON D2 and AROME fallback chain. */
    private val testPlace = SavedPlace(
        id = "e2e_innsbruck",
        name = "Innsbruck",
        latitude = 47.2692,
        longitude = 11.4041,
        isFavorite = false,
    )

    private val loadTimeoutMs = 60_000L

    @Before
    fun setUp() {
        val appGraph = (composeRule.activity.application as CloudbasePredictorApplication).appGraph
        assertSame(
            appGraph.openMeteoRemoteDataSource,
            appGraph.openMeteoRemoteDataSource,
        )
        composeRule.setContent {
            CompositionLocalProvider(
                LocalMetroViewModelFactory provides appGraph.metroViewModelFactory,
            ) {
                CloudbasePredictorTheme {
                    ForecastRoute(
                        placeLocation = PlaceLocation.fromSavedPlace(testPlace),
                        onOpenMap = {},
                        onPlaceLocationChanged = {},
                    )
                }
            }
        }
    }

    @Test
    fun bestMatch_loadsAndShowsThermicChart() {
        waitForChartLoaded()
        composeRule.onNodeWithTag(THERMIC_VIEW).assertIsDisplayed()
    }

    @Test
    fun iconD2_loadsAndShowsThermicChart() {
        selectModelAndVerifyChart(ForecastModel.ICON_D2)
    }

    @Test
    fun iconEu_loadsAndShowsThermicChart() {
        selectModelAndVerifyChart(ForecastModel.ICON_EU)
    }

    @Test
    fun iconGlobal_loadsAndShowsThermicChart() {
        selectModelAndVerifyChart(ForecastModel.ICON_GLOBAL)
    }

    @Test
    fun iconSeamless_loadsAndShowsThermicChart() {
        selectModelAndVerifyChart(ForecastModel.ICON_SEAMLESS)
    }

    @Test
    fun meteofranceArome_loadsAndShowsThermicChart() {
        selectModelAndVerifyChart(ForecastModel.METEOFRANCE_AROME)
    }

    @Test
    fun meteofranceArpege_loadsAndShowsThermicChart() {
        selectModelAndVerifyChart(ForecastModel.METEOFRANCE_ARPEGE)
    }

    @Test
    fun ecmwfIfs_loadsAndShowsThermicChart() {
        selectModelAndVerifyChart(ForecastModel.ECMWF_IFS)
    }

    private fun selectModelAndVerifyChart(model: ForecastModel) {
        waitForChartLoaded()

        composeRule.onNodeWithTag(MODEL_SELECTOR_BUTTON).performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag(MODEL_OPTION_PREFIX + model.apiName)
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag(MODEL_OPTION_PREFIX + model.apiName).performClick()

        waitForForecastReady()
        composeRule.onNodeWithTag(THERMIC_VIEW).assertIsDisplayed()
    }

    private fun waitForChartLoaded() {
        composeRule.waitUntil(timeoutMillis = loadTimeoutMs) {
            composeRule.onAllNodesWithTag(THERMIC_VIEW)
                .fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun waitForForecastReady() {
        composeRule.waitUntil(timeoutMillis = loadTimeoutMs) {
            val chartVisible = composeRule.onAllNodesWithTag(THERMIC_VIEW)
                .fetchSemanticsNodes().isNotEmpty()
            val retryVisible = composeRule.onAllNodesWithText("Retry")
                .fetchSemanticsNodes().isNotEmpty()
            chartVisible || retryVisible
        }
    }
}
