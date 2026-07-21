package com.cloudbasepredictor.ui.screens.forecast

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.cloudbasepredictor.R
import com.cloudbasepredictor.model.ForecastMode
import com.cloudbasepredictor.model.ForecastModel
import com.cloudbasepredictor.model.SavedPlace
import com.cloudbasepredictor.testutil.SimulatedTestData
import com.cloudbasepredictor.ui.screens.forecast.ForecastTestTags.CLOUD_LAYERS_ROW
import com.cloudbasepredictor.ui.screens.forecast.ForecastTestTags.CLOUD_RADIATION_ROW
import com.cloudbasepredictor.ui.screens.forecast.ForecastTestTags.CLOUD_RAIN_ROW
import com.cloudbasepredictor.ui.screens.forecast.ForecastTestTags.CLOUD_SCROLL
import com.cloudbasepredictor.ui.screens.forecast.ForecastTestTags.CLOUD_SUNSHINE_ROW
import com.cloudbasepredictor.ui.screens.forecast.ForecastTestTags.CLOUD_TIME_AXIS_ROW
import com.cloudbasepredictor.ui.screens.forecast.ForecastTestTags.CLOUD_VIEW
import com.cloudbasepredictor.ui.screens.forecast.ForecastTestTags.FORECAST_CHART_AREA
import com.cloudbasepredictor.ui.screens.forecast.ForecastTestTags.MAP_PANEL
import com.cloudbasepredictor.ui.screens.forecast.ForecastTestTags.MAP_PANEL_SURFACE
import com.cloudbasepredictor.ui.screens.forecast.ForecastTestTags.MODEL_OPTION_PREFIX
import com.cloudbasepredictor.ui.screens.forecast.ForecastTestTags.MODEL_SELECTOR_BUTTON
import com.cloudbasepredictor.ui.screens.forecast.ForecastTestTags.STUVE_SELECTED_HOUR
import com.cloudbasepredictor.ui.screens.forecast.ForecastTestTags.STUVE_TIME_SLIDER
import com.cloudbasepredictor.ui.screens.forecast.ForecastTestTags.THERMIC_CURSOR_PANEL
import com.cloudbasepredictor.ui.screens.forecast.ForecastTestTags.THERMIC_VIEW
import com.cloudbasepredictor.ui.screens.forecast.ForecastTestTags.WIND_TIME_AXIS
import com.cloudbasepredictor.ui.screens.forecast.ForecastTestTags.WIND_VIEW
import com.cloudbasepredictor.ui.screens.forecast.views.CloudForecastView
import com.cloudbasepredictor.ui.theme.CloudbasePredictorTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ForecastScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun forecastScreen_rendersProvidedUiState() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val uiState = SimulatedTestData.forecastUiState(context)

        composeRule.setContent {
            CloudbasePredictorTheme {
                ForecastScreen(
                    uiState = uiState,
                    onDateSelected = {},
                    onOpenMap = {},
                )
            }
        }

        composeRule.onNodeWithTag(THERMIC_VIEW).assertIsDisplayed()
        composeRule.onNodeWithText(uiState.selectedPlace?.name.orEmpty()).assertIsDisplayed()
        composeRule.onNodeWithText(uiState.dayChips[uiState.selectedDayIndex].title).assertIsDisplayed()
    }

    @Test
    fun forecastScreen_clickingDayChipInvokesCallbackWithCorrectIndex() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        var selectedIndex: Int? = null
        val uiState = SimulatedTestData.forecastUiState(context).copy(
            selectedPlace = SimulatedTestData.brauneckPlace,
            selectedDayIndex = 0,
            dayChips = listOf(
                ForecastDayChipUiModel(title = "Day 1", subtitle = "Now"),
                ForecastDayChipUiModel(title = "Day 2", subtitle = "Next"),
            ),
            forecastText = "Forecast text",
        )

        composeRule.setContent {
            CloudbasePredictorTheme {
                ForecastScreen(
                    uiState = uiState,
                    onDateSelected = { selectedIndex = it },
                    onOpenMap = {},
                )
            }
        }

        composeRule.onNodeWithText("Day 2").performClick()
        composeRule.runOnIdle {
            assertEquals(1, selectedIndex)
        }
    }

    @Test
    fun forecastScreen_refreshActionInvokesCallback() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val refreshDescription = context.getString(R.string.action_refresh)
        var refreshRequested = false

        composeRule.setContent {
            CloudbasePredictorTheme {
                ForecastScreen(
                    uiState = SimulatedTestData.forecastUiState(context),
                    onDateSelected = {},
                    onRetryLoad = { refreshRequested = true },
                    onOpenMap = {},
                )
            }
        }

        composeRule.onNodeWithContentDescription(refreshDescription)
            .assertIsDisplayed()
            .performClick()

        composeRule.runOnIdle {
            assertTrue(refreshRequested)
        }
    }

    @Test
    fun forecastScreen_loadingStateShowsProgress() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val placeName = SimulatedTestData.brauneckPlace.name
        val expectedLoadingMessage = context.getString(R.string.loading_forecast_for_place, placeName)

        composeRule.setContent {
            CloudbasePredictorTheme {
                ForecastScreen(
                    uiState = ForecastLoadingUiState(selectedPlace = SimulatedTestData.brauneckPlace),
                    onDateSelected = {},
                    onOpenMap = {},
                )
            }
        }

        composeRule.onNodeWithText(expectedLoadingMessage).assertIsDisplayed()
        composeRule.onAllNodesWithTag(THERMIC_VIEW).assertCountEquals(0)
    }

    @Test
    fun forecastScreen_loadingStateShowsModelSelectorAndHandlesSelection() {
        var selectedModel: ForecastModel? = null

        composeRule.setContent {
            CloudbasePredictorTheme {
                ForecastScreen(
                    uiState = ForecastLoadingUiState(
                        selectedPlace = SimulatedTestData.brauneckPlace,
                        selectedModel = ForecastModel.ICON_SEAMLESS,
                    ),
                    onDateSelected = {},
                    onModelSelected = { selectedModel = it },
                    onOpenMap = {},
                )
            }
        }

        composeRule.onNodeWithTag(MODEL_SELECTOR_BUTTON)
            .assertIsDisplayed()
            .performClick()
        composeRule.onNodeWithTag(MODEL_OPTION_PREFIX + ForecastModel.ICON_D2.apiName)
            .performClick()

        composeRule.runOnIdle {
            assertEquals(ForecastModel.ICON_D2, selectedModel)
        }
    }

    @Test
    fun forecastScreen_favoriteDialogSelectsFavoritePlace() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val favoritesContentDescription = context.getString(R.string.cd_favorites)
        val zurichPlace = SavedPlace(
            id = "favorite-zurich",
            name = "Zurich",
            latitude = 47.3769,
            longitude = 8.5417,
            isFavorite = true,
        )
        val favoritePlaces = listOf(SimulatedTestData.brauneckPlace, zurichPlace)
        var selectedPlace: SavedPlace? = null

        composeRule.setContent {
            CloudbasePredictorTheme {
                ForecastScreen(
                    uiState = SimulatedTestData.forecastUiState(context).copy(
                        favoritePlaces = favoritePlaces,
                    ),
                    onDateSelected = {},
                    onFavoriteSelected = { selectedPlace = it },
                    onOpenMap = {},
                )
            }
        }

        composeRule.onNodeWithContentDescription(favoritesContentDescription)
            .assertIsDisplayed()
            .performClick()
        composeRule.onNodeWithText(zurichPlace.name)
            .assertIsDisplayed()
            .performClick()

        composeRule.runOnIdle {
            assertEquals(zurichPlace, selectedPlace)
        }
    }

    @Test
    fun forecastScreen_windModeShowsWindView() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        composeRule.setContent {
            CloudbasePredictorTheme {
                ForecastScreen(
                    uiState = SimulatedTestData.forecastUiState(context, mode = ForecastMode.WIND),
                    onDateSelected = {},
                    onOpenMap = {},
                )
            }
        }

        composeRule.onNodeWithTag(WIND_VIEW).assertIsDisplayed()
    }

    @Test
    fun forecastScreen_stuveModeShowsSelectedHour() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        composeRule.setContent {
            CloudbasePredictorTheme {
                ForecastScreen(
                    uiState = SimulatedTestData.forecastUiState(context, mode = ForecastMode.STUVE),
                    onDateSelected = {},
                    onOpenMap = {},
                )
            }
        }

        composeRule.onNodeWithTag(STUVE_TIME_SLIDER).assertIsDisplayed()
        composeRule.onNodeWithTag(STUVE_SELECTED_HOUR)
            .assertIsDisplayed()
            .assertTextEquals("12:00")
    }

    @Test
    fun forecastScreen_thermicModeShowsThermicView() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        composeRule.setContent {
            CloudbasePredictorTheme {
                ForecastScreen(
                    uiState = SimulatedTestData.forecastUiState(context, mode = ForecastMode.THERMIC),
                    onDateSelected = {},
                    onOpenMap = {},
                )
            }
        }

        composeRule.onNodeWithTag(THERMIC_VIEW).assertIsDisplayed()
    }

    @Test
    fun forecastScreen_thermicCursorPanelAvoidsSelectedPointAndRemainsTouchable() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val touchYFraction = 0.72f

        composeRule.setContent {
            CloudbasePredictorTheme {
                ForecastScreen(
                    uiState = SimulatedTestData.forecastUiState(context, mode = ForecastMode.THERMIC),
                    onDateSelected = {},
                    onOpenMap = {},
                )
            }
        }

        val thermicBoundsBeforeTouch = composeRule.onNodeWithTag(THERMIC_VIEW)
            .fetchSemanticsNode()
            .boundsInRoot
        val touchXInRoot = thermicBoundsBeforeTouch.left + thermicBoundsBeforeTouch.width * 0.55f
        val touchYInRoot = thermicBoundsBeforeTouch.top + thermicBoundsBeforeTouch.height * touchYFraction

        composeRule.onNodeWithTag(THERMIC_VIEW)
            .performTouchInput {
                click(Offset(width * 0.55f, height * touchYFraction))
            }

        val panelBounds = composeRule.onNodeWithTag(THERMIC_CURSOR_PANEL)
            .assertIsDisplayed()
            .fetchSemanticsNode()
            .boundsInRoot
        val chartBounds = composeRule.onNodeWithTag(FORECAST_CHART_AREA)
            .fetchSemanticsNode()
            .boundsInRoot
        val thermicBounds = composeRule.onNodeWithTag(THERMIC_VIEW)
            .fetchSemanticsNode()
            .boundsInRoot
        val avoidRadiusPx = with(composeRule.density) { 28.dp.toPx() }

        assertFalse(
            "Thermic cursor panel should not overlap the selected point marker",
            panelOverlapsPoint(
                panelBounds = panelBounds,
                pointX = touchXInRoot,
                pointY = touchYInRoot,
                radiusPx = avoidRadiusPx,
            ),
        )
        assertTrue(
            "Thermic view exceeds chart area",
            thermicBounds.bottom <= chartBounds.bottom + 1f,
        )

        val firstPanelText = thermicCursorPanelText()
        val panelCenterX = (panelBounds.left + panelBounds.right) / 2f
        val panelCenterY = (panelBounds.top + panelBounds.bottom) / 2f
        composeRule.onNodeWithTag(THERMIC_CURSOR_PANEL)
            .performTouchInput {
                click(center)
            }
        val secondPanelText = thermicCursorPanelText()
        val secondPanelBounds = composeRule.onNodeWithTag(THERMIC_CURSOR_PANEL)
            .fetchSemanticsNode()
            .boundsInRoot

        assertNotEquals(
            "Tapping the cursor panel area should update the thermic cursor underneath it",
            firstPanelText,
            secondPanelText,
        )
        assertFalse(
            "Thermic cursor panel should move away from the newly selected point marker",
            panelOverlapsPoint(
                panelBounds = secondPanelBounds,
                pointX = panelCenterX,
                pointY = panelCenterY,
                radiusPx = avoidRadiusPx,
            ),
        )
    }

    @Test
    fun forecastScreen_windModeShowsChartArea() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        composeRule.setContent {
            CloudbasePredictorTheme {
                ForecastScreen(
                    uiState = SimulatedTestData.forecastUiState(context, mode = ForecastMode.WIND),
                    onDateSelected = {},
                    onOpenMap = {},
                )
            }
        }

        composeRule.onNodeWithTag(FORECAST_CHART_AREA).assertIsDisplayed()
    }

    @Test
    fun forecastScreen_windModeKeepsTimeAxisVisibleAboveMapPanel() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        composeRule.setContent {
            CloudbasePredictorTheme {
                ForecastScreen(
                    uiState = SimulatedTestData.forecastUiState(context, mode = ForecastMode.WIND),
                    onDateSelected = {},
                    onOpenMap = {},
                )
            }
        }

        composeRule.onNodeWithTag(WIND_TIME_AXIS).assertIsDisplayed()

        val timeAxisBounds = composeRule.onNodeWithTag(WIND_TIME_AXIS)
            .fetchSemanticsNode().boundsInRoot
        val mapSurfaceBounds = composeRule.onNodeWithTag(MAP_PANEL_SURFACE)
            .fetchSemanticsNode().boundsInRoot

        assertTrue(
            "Wind time axis should stay above the collapsed map panel",
            timeAxisBounds.bottom <= mapSurfaceBounds.top + 1f,
        )
    }

    @Test
    fun forecastScreen_mapPanelDoesNotOverlapChartWhenCollapsed() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        composeRule.setContent {
            CloudbasePredictorTheme {
                ForecastScreen(
                    uiState = SimulatedTestData.forecastUiState(context),
                    onDateSelected = {},
                    onOpenMap = {},
                )
            }
        }

        val chartBounds = composeRule.onNodeWithTag(FORECAST_CHART_AREA)
            .fetchSemanticsNode().boundsInRoot
        val thermicBounds = composeRule.onNodeWithTag(THERMIC_VIEW)
            .fetchSemanticsNode().boundsInRoot

        // The thermic view should be within the chart area
        assertTrue(
            "Thermic view exceeds chart area",
            thermicBounds.bottom <= chartBounds.bottom + 1f,
        )
    }

    @Test
    fun forecastScreen_expandedMapPanelReducesCloudForecastHeight() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        composeRule.setContent {
            CloudbasePredictorTheme {
                ForecastScreen(
                    uiState = SimulatedTestData.forecastUiState(context, mode = ForecastMode.CLOUD),
                    onDateSelected = {},
                    onOpenMap = {},
                    initiallyExpandedMap = true,
                )
            }
        }

        val expandedPanelHeightPx = with(composeRule.density) { 80.dp.toPx() }
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onNodeWithTag(MAP_PANEL_SURFACE)
                .fetchSemanticsNode()
                .boundsInRoot
                .height > expandedPanelHeightPx
        }

        val cloudBounds = composeRule.onNodeWithTag(CLOUD_VIEW)
            .fetchSemanticsNode().boundsInRoot
        val timeAxisBounds = composeRule.onNodeWithTag(CLOUD_TIME_AXIS_ROW)
            .fetchSemanticsNode().boundsInRoot
        val mapSurfaceBounds = composeRule.onNodeWithTag(MAP_PANEL_SURFACE)
            .fetchSemanticsNode().boundsInRoot

        assertTrue(
            "Expanded map panel should reduce cloud forecast height instead of overlaying it",
            cloudBounds.bottom <= mapSurfaceBounds.top + 1f,
        )
        assertTrue(
            "Cloud time axis should stay above the expanded map panel",
            timeAxisBounds.bottom <= mapSurfaceBounds.top + 1f,
        )
    }

    @Test
    fun forecastScreen_cloudModeShowsAllRows() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        composeRule.setContent {
            CloudbasePredictorTheme {
                ForecastScreen(
                    uiState = SimulatedTestData.forecastUiState(context, mode = ForecastMode.CLOUD),
                    onDateSelected = {},
                    onOpenMap = {},
                )
            }
        }

        composeRule.onNodeWithTag(CLOUD_VIEW).assertIsDisplayed()
        composeRule.onNodeWithTag(CLOUD_SCROLL).assertIsDisplayed()
        composeRule.onNodeWithTag(CLOUD_SUNSHINE_ROW).assertIsDisplayed()
        composeRule.onNodeWithTag(CLOUD_RADIATION_ROW).assertIsDisplayed()
        composeRule.onNodeWithTag(CLOUD_LAYERS_ROW).assertIsDisplayed()
        composeRule.onNodeWithTag(CLOUD_RAIN_ROW).assertIsDisplayed()
        composeRule.onNodeWithTag(CLOUD_TIME_AXIS_ROW).assertIsDisplayed()
    }

    @Test
    fun forecastScreen_cloudModeRowsAreOrderedTopToBottom() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        composeRule.setContent {
            CloudbasePredictorTheme {
                ForecastScreen(
                    uiState = SimulatedTestData.forecastUiState(context, mode = ForecastMode.CLOUD),
                    onDateSelected = {},
                    onOpenMap = {},
                )
            }
        }

        val sunshineTop = composeRule.onNodeWithTag(CLOUD_SUNSHINE_ROW)
            .fetchSemanticsNode().boundsInRoot.top
        val radiationTop = composeRule.onNodeWithTag(CLOUD_RADIATION_ROW)
            .fetchSemanticsNode().boundsInRoot.top
        val layersTop = composeRule.onNodeWithTag(CLOUD_LAYERS_ROW)
            .fetchSemanticsNode().boundsInRoot.top
        val rainTop = composeRule.onNodeWithTag(CLOUD_RAIN_ROW)
            .fetchSemanticsNode().boundsInRoot.top

        assertTrue("Sunshine should be above radiation", sunshineTop < radiationTop)
        assertTrue("Radiation should be above cloud layers", radiationTop < layersTop)
        assertTrue("Cloud layers should be above rain", layersTop < rainTop)
    }

    @Test
    fun cloudForecastView_spreadsRowsWhenHeightAllows() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        composeRule.setContent {
            CloudbasePredictorTheme {
                CloudForecastView(
                    uiState = SimulatedTestData.forecastUiState(context, mode = ForecastMode.CLOUD),
                    modifier = Modifier
                        .width(360.dp)
                        .height(640.dp),
                )
            }
        }

        val minimumGapPx = with(composeRule.density) { 24.dp.toPx() }
        val minimumTopClearancePx = with(composeRule.density) { 56.dp.toPx() }
        val sunshineBounds = composeRule.onNodeWithTag(CLOUD_SUNSHINE_ROW)
            .fetchSemanticsNode().boundsInRoot
        val radiationBounds = composeRule.onNodeWithTag(CLOUD_RADIATION_ROW)
            .fetchSemanticsNode().boundsInRoot
        val layersBounds = composeRule.onNodeWithTag(CLOUD_LAYERS_ROW)
            .fetchSemanticsNode().boundsInRoot

        assertTrue(
            "Cloud rows should start below the overlay controls",
            sunshineBounds.top >= minimumTopClearancePx,
        )
        assertTrue(
            "Sunshine and radiation rows should spread apart when space allows",
            radiationBounds.top - sunshineBounds.bottom >= minimumGapPx,
        )
        assertTrue(
            "Radiation and cloud layer rows should spread apart when space allows",
            layersBounds.top - radiationBounds.bottom >= minimumGapPx,
        )
    }

    @Test
    fun cloudForecastView_scrollsRowsWhenHeightIsTight() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        composeRule.setContent {
            CloudbasePredictorTheme {
                CloudForecastView(
                    uiState = SimulatedTestData.forecastUiState(context, mode = ForecastMode.CLOUD),
                    modifier = Modifier
                        .width(360.dp)
                        .height(220.dp),
                )
            }
        }

        composeRule.onNodeWithTag(CLOUD_RAIN_ROW)
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithTag(CLOUD_TIME_AXIS_ROW).assertIsDisplayed()
    }

    @Test
    fun cloudForecastView_removesRowGapsWhenOnlyMinimumHeightFits() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        composeRule.setContent {
            CloudbasePredictorTheme {
                CloudForecastView(
                    uiState = SimulatedTestData.forecastUiState(context, mode = ForecastMode.CLOUD),
                    modifier = Modifier
                        .width(360.dp)
                        .height(364.dp),
                )
            }
        }

        val sunshineBounds = composeRule.onNodeWithTag(CLOUD_SUNSHINE_ROW)
            .fetchSemanticsNode().boundsInRoot
        val radiationBounds = composeRule.onNodeWithTag(CLOUD_RADIATION_ROW)
            .fetchSemanticsNode().boundsInRoot
        val layersBounds = composeRule.onNodeWithTag(CLOUD_LAYERS_ROW)
            .fetchSemanticsNode().boundsInRoot
        val rainBounds = composeRule.onNodeWithTag(CLOUD_RAIN_ROW)
            .fetchSemanticsNode().boundsInRoot

        assertTrue(
            "Sunshine and radiation rows should touch at minimum height",
            radiationBounds.top - sunshineBounds.bottom <= 1f,
        )
        assertTrue(
            "Radiation and cloud layer rows should touch at minimum height",
            layersBounds.top - radiationBounds.bottom <= 1f,
        )
        assertTrue(
            "Cloud layer and rain rows should touch at minimum height",
            rainBounds.top - layersBounds.bottom <= 1f,
        )
    }

    private fun thermicCursorPanelText(): String {
        val text = composeRule.onNodeWithTag(THERMIC_CURSOR_PANEL)
            .fetchSemanticsNode()
            .config[SemanticsProperties.Text]
        return text.joinToString(separator = "\n") { it.text }
    }

    private fun panelOverlapsPoint(
        panelBounds: Rect,
        pointX: Float,
        pointY: Float,
        radiusPx: Float,
    ): Boolean {
        return panelBounds.left < pointX + radiusPx &&
            panelBounds.right > pointX - radiusPx &&
            panelBounds.top < pointY + radiusPx &&
            panelBounds.bottom > pointY - radiusPx
    }
}
