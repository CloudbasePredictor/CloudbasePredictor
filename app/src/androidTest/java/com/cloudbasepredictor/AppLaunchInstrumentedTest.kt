package com.cloudbasepredictor

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cloudbasepredictor.ui.components.MapTestTags
import org.junit.Assert.assertSame
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppLaunchInstrumentedTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun appLaunches_andHomeScreenIsVisible() {
        assertMapChromeVisible()
    }

    @Test
    fun applicationGraph_resolvesCoreBindings() {
        val application = composeRule.activity.application as CloudbasePredictorApplication

        assertSame(
            application.appGraph.metroViewModelFactory,
            application.appGraph.metroViewModelFactory,
        )
        assertSame(
            application.appGraph.openMeteoRemoteDataSource,
            application.appGraph.openMeteoRemoteDataSource,
        )
    }

    private fun assertMapChromeVisible() {
        composeRule.onNodeWithTag(MapTestTags.FAVORITES_BUTTON).assertIsDisplayed()
        composeRule.onNodeWithTag(MapTestTags.SETTINGS_BUTTON).assertIsDisplayed()
        composeRule.onNodeWithTag(MapTestTags.ATTRIBUTION_OVERLAY).assertIsDisplayed()
    }
}
