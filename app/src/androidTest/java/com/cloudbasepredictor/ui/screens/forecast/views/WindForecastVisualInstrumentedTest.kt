package com.cloudbasepredictor.ui.screens.forecast.views

import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.click
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.TouchInjectionScope
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.cloudbasepredictor.model.ForecastMode
import com.cloudbasepredictor.testutil.SimulatedTestData
import com.cloudbasepredictor.ui.preview.ForecastPreviewData
import com.cloudbasepredictor.ui.screens.forecast.ForecastReadyUiState
import com.cloudbasepredictor.ui.screens.forecast.ForecastScreen
import com.cloudbasepredictor.ui.screens.forecast.ForecastTestTags.WIND_CHART_CANVAS
import com.cloudbasepredictor.ui.screens.forecast.ForecastTestTags.WIND_RENDERER_DESCRIPTION
import com.cloudbasepredictor.ui.screens.forecast.ForecastTestTags.WIND_VIEW
import com.cloudbasepredictor.ui.theme.CloudbasePredictorTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs
import kotlin.math.roundToInt

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

        // Count the plot only: the legend below it and the altitude axis are excluded, because the legend
        // keys use the same CCL and 0 °C colors as the lines.
        val image = composeRule.onNodeWithTag(WIND_VIEW).captureToImage()
        val plotLeft = (AXIS_DP * density()).roundToInt()
        val plotWidth = image.width - plotLeft
        val plotHeight = image.height - (BOTTOM_CHROME_DP * density()).roundToInt()
        val pixels = IntArray(plotWidth * plotHeight)
        image.readPixels(pixels, startX = plotLeft, startY = 0, width = plotWidth, height = plotHeight)

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
        assertCursor("06h  10 m AGL", "8 km/h  350°")

        // Tapping close to a source glyph snaps to its exact hourly height.
        tapSparse(hourIndex = 0, y = { altitudeY(state, 2.357f) + 6f * density() })
        assertCursor("06h  2357 m ASL", "30 km/h  270°")

        tapSparse(hourIndex = 1, y = { surfaceRowY() })
        assertCursor("07h  10 m AGL", "No data")

        tapSparse(hourIndex = 2, y = { surfaceRowY() })
        assertCursor("08h  10 m AGL", "0 km/h  0°")
    }

    @Test
    fun sparseWind_cursorMarksInterpolatedOrHeldValuesAndMissingHours() {
        val state = showSparseWind()
        tapSparse(hourIndex = 0, y = { altitudeY(state, 1.5f) })
        assertCursorMatches(Regex("""06h  \d+ m ASL"""), Regex("""≈ \d+ km/h  \d+°"""))
        // Above the highest source level the top level is held, marked as not a source value.
        tapSparse(hourIndex = 0, y = { 20f * density() })
        assertCursorMatches(Regex("""06h  \d+ m ASL"""), Regex.fromLiteral("≈ 30 km/h  270°"))
        tapSparse(hourIndex = 3, y = { altitudeY(state, 1.5f) })
        assertCursorMatches(Regex("""09h  \d+ m ASL"""), Regex.fromLiteral("No data"))
    }

    @Test
    fun sparseWind_cursorWorksAfterSwitchingDay() {
        val firstDay = ForecastPreviewData.sparseWindState()
        var uiState by mutableStateOf(firstDay)
        composeRule.setContent {
            CloudbasePredictorTheme {
                WindForecastView(uiState)
            }
        }
        composeRule.waitForIdle()
        tapSparse(hourIndex = 0, y = { surfaceRowY() })
        assertCursor("06h  10 m AGL", "8 km/h  350°")

        // The next day keeps the sparse layout four hours later, 10 km/h stronger and veered by 90°.
        composeRule.runOnIdle {
            uiState = firstDay.copy(
                selectedDayIndex = firstDay.selectedDayIndex + 1,
                windChart = firstDay.windChart.copy(
                    hours = firstDay.windChart.hours.map { it + 4 },
                    cells = firstDay.windChart.cells.map { cell ->
                        cell.copy(
                            hour = cell.hour + 4,
                            speedKmh = cell.speedKmh + 10f,
                            directionDeg = (cell.directionDeg + 90f) % 360f,
                        )
                    },
                ),
            )
        }
        composeRule.waitForIdle()

        // The previous day's cursor is not carried over to the new chart.
        assertNoCursor()

        // A tap on another column must still move the cursor and read the new day's values.
        tapSparse(hourIndex = 2, y = { surfaceRowY() })
        assertCursor("12h  10 m AGL", "10 km/h  90°")
    }

    @Test
    fun sparseWind_dragFromSurfaceRowClampsToChartEdges() {
        showSparseWind()
        val canvas = composeRule.onNodeWithTag(WIND_CHART_CANVAS)
        canvas.performTouchInput { down(Offset(sparseColumnX(hourIndex = 1), surfaceRowY())) }
        assertCursor("07h  10 m AGL", "No data")

        // Down into the legend, the cursor stays on the 10 m row instead of disappearing.
        canvas.performTouchInput { dragTo(Offset(sparseColumnX(hourIndex = 1), legendY())) }
        assertCursor("07h  10 m AGL", "No data")

        // Then left over the altitude axis, it stops on the first hour.
        canvas.performTouchInput { dragTo(Offset(AXIS_DP * density() / 3f, legendY())) }
        assertCursor("06h  10 m AGL", "8 km/h  350°")

        canvas.performTouchInput { up() }
        composeRule.waitForIdle()
        assertCursor("06h  10 m AGL", "8 km/h  350°")
    }

    @Test
    fun sparseWind_tapOnLegendClearsCursor() {
        // At 2x font scale the legend covers the height of the 1x 10 m row. Switching to 1x moves the rows under the
        // tapped point without resizing the canvas (which drops any cursor), so a position kept from the legend tap
        // would show a cursor in the 10 m row there.
        var fontScale by mutableFloatStateOf(2f)
        val displayDensity = density()
        composeRule.setContent {
            CloudbasePredictorTheme {
                CompositionLocalProvider(LocalDensity provides Density(displayDensity, fontScale)) {
                    WindForecastView(ForecastPreviewData.sparseWindState())
                }
            }
        }
        composeRule.waitForIdle()
        // The 2x 10 m row, which is at least 26 dp high, ends above the 56 dp legend and the 32 dp hour axis.
        tapSparse(hourIndex = 0, y = { height - (56f + 32f + 13f) * displayDensity })
        assertCursor("06h  10 m AGL", "8 km/h  350°")

        tapSparse(hourIndex = 0, y = { surfaceRowY() })
        assertNoCursor()

        composeRule.runOnIdle { fontScale = 1f }
        composeRule.waitForIdle()
        assertNoCursor()
    }

    @Test
    fun sparseWind_resizeClearsCursorForGood() {
        // As when the Android map panel opens and closes again under the chart.
        var hostHeight by mutableStateOf(480.dp)
        var backEnabled = false
        composeRule.setContent {
            CloudbasePredictorTheme {
                Box(Modifier.height(hostHeight)) {
                    WindForecastView(
                        ForecastPreviewData.sparseWindState(),
                        overlayBackHandler = { enabled, _ -> backEnabled = enabled },
                    )
                }
            }
        }
        composeRule.waitForIdle()
        tapSparse(hourIndex = 0, y = { surfaceRowY() })
        assertCursor("06h  10 m AGL", "8 km/h  350°")
        composeRule.runOnIdle { assertTrue("Back should dismiss the shown cursor", backEnabled) }

        // On a shorter chart the old position lies below the 10 m row: the cursor is dropped, not just hidden.
        composeRule.runOnIdle { hostHeight = 320.dp }
        composeRule.waitForIdle()
        assertNoCursor()
        composeRule.runOnIdle { assertFalse("Back should navigate once the cursor is gone", backEnabled) }

        // Back at the first size, it does not come back.
        composeRule.runOnIdle { hostHeight = 480.dp }
        composeRule.waitForIdle()
        assertNoCursor()
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
        assertCursorMatches(Regex.fromLiteral("${lastHour}h  10 m AGL"), Regex("""\d+ km/h  \d+°"""))
    }

    @Test
    fun sparseWind_shortScreenWithExpandedMapKeepsSurfaceReadable() {
        showShortScreenSparseWind()
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
        assertCursor("06h  10 m AGL", "8 km/h  350°")
    }

    @Test
    fun sparseWind_shortScreenPlotDragMovesCursorWhileAxisAndLegendDragsScrollPage() {
        showShortScreenSparseWind()
        composeRule.onNodeWithTag(WIND_VIEW).performScrollTo()
        val density = density()

        // Inside the plot the cursor owns the drag: it follows the finger up while the page stays where it is.
        val plotDragTop = windViewTop()
        val canvas = composeRule.onNodeWithTag(WIND_CHART_CANVAS)
        canvas.performTouchInput { down(Offset(sparseColumnX(hourIndex = 2), plotBottomY() * 0.85f)) }
        val pressedAltitude = cursorAltitudeMeters(hour = "08")
        canvas.performTouchInput {
            dragTo(checkNotNull(currentPosition()) - Offset(0f, DRAG_DISTANCE_DP * density))
            up()
        }
        composeRule.waitForIdle()
        val draggedAltitude = cursorAltitudeMeters(hour = "08")
        assertEquals("A drag inside the plot must not scroll the page", plotDragTop, windViewTop(), 0.5f)
        assertTrue(
            "A drag up the plot should raise the cursor; it went from $pressedAltitude m to $draggedAltitude m",
            draggedAltitude > pressedAltitude,
        )

        // The altitude axis column leaves vertical drags to the page.
        dragUpOnCanvas { Offset(AXIS_DP * density / 3f, plotBottomY() * 0.85f) }
        assertPageScrolledFrom(plotDragTop, "altitude axis")

        // So do the legend and hour rows below the 10 m row.
        composeRule.onNodeWithTag(WIND_VIEW).performScrollTo()
        val legendDragTop = windViewTop()
        dragUpOnCanvas { Offset(width / 2f, legendY()) }
        assertPageScrolledFrom(legendDragTop, "legend")
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

    /** A short window with the map expanded: the forecast page scrolls and the chart keeps a fixed height. */
    private fun showShortScreenSparseWind() {
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
    }

    private fun density(): Float = composeRule.activity.resources.displayMetrics.density

    /** Taps the center of an hourly column of the sparse four-hour chart. */
    private fun tapSparse(hourIndex: Int, y: TouchInjectionScope.() -> Float) {
        composeRule.onNodeWithTag(WIND_CHART_CANVAS).performTouchInput {
            click(Offset(sparseColumnX(hourIndex), y()))
        }
    }

    /** The center of an hourly column of the sparse four-hour chart. */
    private fun TouchInjectionScope.sparseColumnX(hourIndex: Int): Float {
        val plotLeft = AXIS_DP * density()
        return plotLeft + (width - plotLeft) * (hourIndex + 0.5f) / 4f
    }

    // Mirrors the wind chart layout: 26 dp 10 m row, 28 dp legend and 16 dp hour axis below the plot.
    private fun TouchInjectionScope.surfaceRowY(): Float = height - (28f + 16f + 13f) * density()

    private fun TouchInjectionScope.legendY(): Float = height - (16f + 14f) * density()

    private fun TouchInjectionScope.plotBottomY(): Float = height - BOTTOM_CHROME_DP * density()

    private fun TouchInjectionScope.altitudeY(state: ForecastReadyUiState, altitudeKm: Float): Float {
        val range = state.chartViewport.visibleTopAltitudeKm
        return plotBottomY() * (1f - (altitudeKm - state.elevationKm) / range)
    }

    /** Drags one finger straight up on the wind canvas in small steps, far beyond the touch slop. */
    private fun dragUpOnCanvas(start: TouchInjectionScope.() -> Offset) {
        composeRule.onNodeWithTag(WIND_CHART_CANVAS).performTouchInput {
            val from = start()
            down(from)
            dragTo(from - Offset(0f, DRAG_DISTANCE_DP * density()))
            up()
        }
        composeRule.waitForIdle()
    }

    /** Moves the finger that is down to [target] in small steps; the gesture may span several touch inputs. */
    private fun TouchInjectionScope.dragTo(target: Offset) {
        val from = checkNotNull(currentPosition()) { "dragTo needs a finger down" }
        repeat(DRAG_STEPS) { step ->
            moveTo(from + (target - from) * ((step + 1f) / DRAG_STEPS))
        }
    }

    /** Top of the whole wind view in the root, unclipped, so it moves exactly with the page scroll. */
    private fun windViewTop(): Float = composeRule.onNodeWithTag(WIND_VIEW).fetchSemanticsNode().positionInRoot.y

    private fun assertPageScrolledFrom(topBeforeDrag: Float, area: String) {
        val scrolledDp = (topBeforeDrag - windViewTop()) / density()
        assertTrue("A drag on the $area should scroll the page; it moved $scrolledDp dp", scrolledDp > 16f)
    }

    /** Asserts the cursor readout exactly: its hour and height line, then its value line. */
    private fun assertCursor(hourLine: String, valueLine: String) {
        assertCursorMatches(Regex.fromLiteral(hourLine), Regex.fromLiteral(valueLine))
    }

    /** Asserts that a cursor is shown and that each pattern matches its whole readout line. */
    private fun assertCursorMatches(hourLine: Regex, valueLine: Regex) {
        composeRule.onNodeWithTag(WIND_CHART_CANVAS).assert(SemanticsMatcher("cursor reads [$hourLine; $valueLine]") {
            val lines = it.cursorLines()
            it.config.getOrNull(SemanticsProperties.StateDescription) == "cursor" &&
                lines.size == 2 && hourLine.matches(lines[0]) && valueLine.matches(lines[1])
        })
    }

    /** Asserts that no cursor is shown: the description is the renderer name alone. */
    private fun assertNoCursor() {
        composeRule.onNodeWithTag(WIND_CHART_CANVAS)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "idle"))
            .assertContentDescriptionEquals(WIND_RENDERER_DESCRIPTION)
    }

    /** The height of a shown cursor at [hour] (two digits) above sea level, e.g. 2357 for "06h  2357 m ASL". */
    private fun cursorAltitudeMeters(hour: String): Int {
        val hourLine = composeRule.onNodeWithTag(WIND_CHART_CANVAS).fetchSemanticsNode().cursorLines().firstOrNull()
        val match = Regex("""${hour}h  (\d+) m ASL""").matchEntire(hourLine.orEmpty())
        return checkNotNull(match) { "Expected a ${hour}h cursor above sea level, the readout was \"$hourLine\"" }
            .groupValues[1]
            .toInt()
    }

    /** The readout lines after the renderer name; the chart joins them with "; " in its description. */
    private fun SemanticsNode.cursorLines(): List<String> =
        config.getOrNull(SemanticsProperties.ContentDescription).orEmpty()
            .joinToString("; ")
            .split("; ")
            .drop(1)

    private companion object {
        const val AXIS_DP = 60f
        const val BOTTOM_CHROME_DP = 26f + 28f + 16f
        const val DRAG_DISTANCE_DP = 100f
        const val DRAG_STEPS = 10
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
