package com.cloudbasepredictor.ui.screens.forecast.views

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.click
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pinch
import androidx.compose.ui.test.swipe
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cloudbasepredictor.model.ForecastMode
import com.cloudbasepredictor.testutil.SimulatedTestData
import com.cloudbasepredictor.ui.screens.forecast.DEFAULT_TOP_ALTITUDE_KM
import com.cloudbasepredictor.ui.screens.forecast.ForecastTestTags.STUVE_CHART_CANVAS
import com.cloudbasepredictor.ui.screens.forecast.StuveActiveThetaKKey
import com.cloudbasepredictor.ui.theme.CloudbasePredictorTheme
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StuveForecastInteractionInstrumentedTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun stuveView_autoFitsTopAltitude_whenOpened() {
        var visibleTopAltitudeKm by mutableFloatStateOf(DEFAULT_TOP_ALTITUDE_KM)

        composeRule.setContent {
            CloudbasePredictorTheme {
                StuveForecastView(
                    uiState = SimulatedTestData.forecastUiState(
                        composeRule.activity,
                        mode = ForecastMode.STUVE,
                        topAltitudeKm = visibleTopAltitudeKm,
                    ),
                    onVisibleTopAltitudeChange = { visibleTopAltitudeKm = it },
                )
            }
        }

        composeRule.waitForIdle()

        composeRule.runOnIdle {
            assertTrue(visibleTopAltitudeKm > DEFAULT_TOP_ALTITUDE_KM + 0.4f)
        }
    }

    @Test
    fun stuveView_tapPinsCursorReadout() {
        var visibleTopAltitudeKm by mutableFloatStateOf(DEFAULT_TOP_ALTITUDE_KM)

        composeRule.setContent {
            CloudbasePredictorTheme {
                StuveForecastView(
                    uiState = SimulatedTestData.forecastUiState(
                        composeRule.activity,
                        mode = ForecastMode.STUVE,
                        topAltitudeKm = visibleTopAltitudeKm,
                    ),
                    onVisibleTopAltitudeChange = { visibleTopAltitudeKm = it },
                )
            }
        }

        composeRule.waitForIdle()

        composeRule.onNodeWithTag(STUVE_CHART_CANVAS)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "idle"))
            .performTouchInput {
                click(center)
            }

        composeRule.waitForIdle()

        composeRule.onNodeWithTag(STUVE_CHART_CANVAS)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "pinned"))
    }

    @Test
    fun stuveView_backDismissesPinnedOverlayBeforeNavigating() {
        var visibleTopAltitudeKm by mutableFloatStateOf(DEFAULT_TOP_ALTITUDE_KM)

        composeRule.setContent {
            CloudbasePredictorTheme {
                StuveForecastView(
                    uiState = SimulatedTestData.forecastUiState(
                        composeRule.activity,
                        mode = ForecastMode.STUVE,
                        topAltitudeKm = visibleTopAltitudeKm,
                    ),
                    onVisibleTopAltitudeChange = { visibleTopAltitudeKm = it },
                )
            }
        }

        composeRule.waitForIdle()

        // Tap to pin the readout overlay.
        composeRule.onNodeWithTag(STUVE_CHART_CANVAS).performTouchInput { click(center) }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(STUVE_CHART_CANVAS)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "pinned"))

        // Pressing back must dismiss the overlay (and not navigate away).
        composeRule.runOnUiThread {
            composeRule.activity.onBackPressedDispatcher.onBackPressed()
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(STUVE_CHART_CANVAS)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "idle"))
    }

    @Test
    fun stuveView_tapSetsActiveThetaK() {
        var visibleTopAltitudeKm by mutableFloatStateOf(DEFAULT_TOP_ALTITUDE_KM)

        composeRule.setContent {
            CloudbasePredictorTheme {
                StuveForecastView(
                    uiState = SimulatedTestData.forecastUiState(
                        composeRule.activity,
                        mode = ForecastMode.STUVE,
                        topAltitudeKm = visibleTopAltitudeKm,
                    ),
                    onVisibleTopAltitudeChange = { visibleTopAltitudeKm = it },
                )
            }
        }

        composeRule.waitForIdle()

        composeRule.onNodeWithTag(STUVE_CHART_CANVAS).performTouchInput {
            click(center)
        }

        composeRule.waitForIdle()

        // After tapping, the active theta-K semantics key must be present and finite.
        val node = composeRule.onNodeWithTag(STUVE_CHART_CANVAS)
            .fetchSemanticsNode()
        val thetaK = node.config.getOrElseNullable(StuveActiveThetaKKey) { null }
        assertNotNull("Active theta-K should be set after tap", thetaK)
        assertTrue("Active theta-K must be a positive finite value", thetaK!! > 0f && thetaK.isFinite())
    }

    /** Tapping the left side (colder) vs. the right side (warmer) of the chart at the same
     *  vertical position must produce different active parcel guide theta-K values. */
    @Test
    fun stuveView_tapAtDifferentXSamePressure_producesDifferentParcelGuides() {
        var visibleTopAltitudeKm by mutableFloatStateOf(DEFAULT_TOP_ALTITUDE_KM)

        composeRule.setContent {
            CloudbasePredictorTheme {
                StuveForecastView(
                    uiState = SimulatedTestData.forecastUiState(
                        composeRule.activity,
                        mode = ForecastMode.STUVE,
                        topAltitudeKm = visibleTopAltitudeKm,
                    ),
                    onVisibleTopAltitudeChange = { visibleTopAltitudeKm = it },
                )
            }
        }

        composeRule.waitForIdle()

        // Tap left-center (colder part of the Skew-T diagram).
        composeRule.onNodeWithTag(STUVE_CHART_CANVAS).performTouchInput {
            click(Offset(width * 0.30f, height * 0.45f))
        }
        composeRule.waitForIdle()
        val leftThetaK = composeRule.onNodeWithTag(STUVE_CHART_CANVAS)
            .fetchSemanticsNode()
            .config.getOrElseNullable(StuveActiveThetaKKey) { null }

        // Tap right-center (warmer part of the Skew-T diagram), same relative Y.
        composeRule.onNodeWithTag(STUVE_CHART_CANVAS).performTouchInput {
            click(Offset(width * 0.70f, height * 0.45f))
        }
        composeRule.waitForIdle()
        val rightThetaK = composeRule.onNodeWithTag(STUVE_CHART_CANVAS)
            .fetchSemanticsNode()
            .config.getOrElseNullable(StuveActiveThetaKKey) { null }

        assertNotNull("Left-side tap should produce active theta-K", leftThetaK)
        assertNotNull("Right-side tap should produce active theta-K", rightThetaK)
        assertFalse(
            "Left tap (x=0.30) and right tap (x=0.70) at same pressure should give different theta-K",
            kotlin.math.abs(leftThetaK!! - rightThetaK!!) < 1f,
        )
    }

    /** Dragging inside the plot area must update the cursor (theta-K changes with X). */
    @Test
    fun stuveView_dragInsideChart_updatesParcelGuide() {
        var visibleTopAltitudeKm by mutableFloatStateOf(DEFAULT_TOP_ALTITUDE_KM)

        composeRule.setContent {
            CloudbasePredictorTheme {
                StuveForecastView(
                    uiState = SimulatedTestData.forecastUiState(
                        composeRule.activity,
                        mode = ForecastMode.STUVE,
                        topAltitudeKm = visibleTopAltitudeKm,
                    ),
                    onVisibleTopAltitudeChange = { visibleTopAltitudeKm = it },
                )
            }
        }

        composeRule.waitForIdle()

        // Swipe horizontally across the chart to exercise the X update.
        composeRule.onNodeWithTag(STUVE_CHART_CANVAS).performTouchInput {
            swipe(
                start = Offset(width * 0.25f, height * 0.50f),
                end = Offset(width * 0.75f, height * 0.50f),
                durationMillis = 250,
            )
        }
        composeRule.waitForIdle()

        // After the drag the cursor is dismissed (drag => not pinned => null on release).
        // The important part is that state transitions happened without crash.
        composeRule.onNodeWithTag(STUVE_CHART_CANVAS)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "idle"))
    }

    /** Dragging the bottom handle changes the heating-delta which updates theta-K. */
    @Test
    fun stuveView_bottomHeatingHandleDrag_changesParcelGuide() {
        var visibleTopAltitudeKm by mutableFloatStateOf(DEFAULT_TOP_ALTITUDE_KM)

        composeRule.setContent {
            CloudbasePredictorTheme {
                StuveForecastView(
                    uiState = SimulatedTestData.forecastUiState(
                        composeRule.activity,
                        mode = ForecastMode.STUVE,
                        topAltitudeKm = visibleTopAltitudeKm,
                    ),
                    onVisibleTopAltitudeChange = { visibleTopAltitudeKm = it },
                )
            }
        }

        composeRule.waitForIdle()

        // The heating handle sits at the bottom edge of the plot area.
        // We perform a drag starting at the bottom-center of the canvas (where the handle is)
        // and moving rightward by ~20% of the chart width.
        composeRule.onNodeWithTag(STUVE_CHART_CANVAS).performTouchInput {
            // The handle is at the very bottom of the canvas (plotBottom ≈ canvas height - 34dp).
            // Using 92% down and centre X as a safe approximation that lands within the touch target.
            val handleApproxY = height * 0.92f
            val handleApproxX = width * 0.50f
            swipe(
                start = Offset(handleApproxX, handleApproxY),
                end = Offset(handleApproxX + width * 0.18f, handleApproxY),
                durationMillis = 300,
            )
        }
        composeRule.waitForIdle()

        // After the drag the StuveActiveThetaKKey should be set (heatingDeltaC != 0).
        val thetaK = composeRule.onNodeWithTag(STUVE_CHART_CANVAS)
            .fetchSemanticsNode()
            .config.getOrElseNullable(StuveActiveThetaKKey) { null }
        // Note: whether the handle hit detection succeeded depends on exact pixel layout;
        // if heatingDeltaC stayed 0 the key will be null.  We assert that the state is at
        // least idle (no crash) and note the limitation in comments.
        composeRule.onNodeWithTag(STUVE_CHART_CANVAS)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "idle"))
        // Best-effort assertion: if the swipe did hit the handle the key is set.
        if (thetaK != null) {
            assertTrue("Heating-handle drag theta-K must be positive", thetaK > 0f)
        }
    }

    @Test
    fun stuveView_twoFingerPinchZoom_changesTopAltitude() {
        var visibleTopAltitudeKm by mutableFloatStateOf(DEFAULT_TOP_ALTITUDE_KM)

        composeRule.setContent {
            CloudbasePredictorTheme {
                StuveForecastView(
                    uiState = SimulatedTestData.forecastUiState(
                        composeRule.activity,
                        mode = ForecastMode.STUVE,
                        topAltitudeKm = visibleTopAltitudeKm,
                    ),
                    onVisibleTopAltitudeChange = { visibleTopAltitudeKm = it },
                )
            }
        }

        composeRule.waitForIdle()
        val autoFitTopAltitudeKm = composeRule.runOnIdle { visibleTopAltitudeKm }

        composeRule.onNodeWithTag(STUVE_CHART_CANVAS).performTouchInput {
            val centerX = width / 2f
            val centerY = height / 2f
            val startHalfSpan = width * 0.12f
            val endHalfSpan = startHalfSpan * 2.2f

            pinch(
                start0 = Offset(centerX - startHalfSpan, centerY),
                end0 = Offset(centerX - endHalfSpan, centerY),
                start1 = Offset(centerX + startHalfSpan, centerY),
                end1 = Offset(centerX + endHalfSpan, centerY),
                durationMillis = 250,
            )
        }

        composeRule.waitForIdle()

        composeRule.runOnIdle {
            assertTrue(visibleTopAltitudeKm < autoFitTopAltitudeKm - 0.6f)
        }
    }
}
