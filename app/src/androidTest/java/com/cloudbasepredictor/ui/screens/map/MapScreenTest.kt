package com.cloudbasepredictor.ui.screens.map

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.cloudbasepredictor.model.SavedPlace
import com.cloudbasepredictor.ui.components.MapTestTags
import com.cloudbasepredictor.ui.theme.CloudbasePredictorTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class MapScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun mapScreen_showsFavoritesButtonWhenNoFavoritesExist() {
        composeRule.setContent {
            CloudbasePredictorTheme {
                MapScreen(
                    uiState = MapUiState(),
                    onMapTapped = { _, _ -> },
                    onFavoriteTapped = {},
                    onLaunchSiteTapped = {},
                    onOpenForecast = {},
                    onFavoriteClick = {},
                    onSaveCameraPosition = { _, _, _ -> },
                    autoOpenFavoritesOnStartup = true,
                )
            }
        }

        composeRule.onNodeWithTag(MapTestTags.FAVORITES_BUTTON).assertIsDisplayed()
    }

    @Test
    fun mapScreen_showsCurrentLocationButton() {
        composeRule.setContent {
            CloudbasePredictorTheme {
                MapScreen(
                    uiState = MapUiState(),
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

        composeRule.onNodeWithTag(MapTestTags.CURRENT_LOCATION_BUTTON).assertIsDisplayed()
    }

    @Test
    fun mapScreen_autoOpensFavoritesDialogWhenAtLeastTwoFavoritesExist() {
        composeRule.setContent {
            CloudbasePredictorTheme {
                MapScreen(
                    uiState = MapUiState(favoritePlaces = favoritePlaces),
                    onMapTapped = { _, _ -> },
                    onFavoriteTapped = {},
                    onLaunchSiteTapped = {},
                    onOpenForecast = {},
                    onFavoriteClick = {},
                    onSaveCameraPosition = { _, _, _ -> },
                    autoOpenFavoritesOnStartup = true,
                )
            }
        }

        composeRule.onNodeWithTag(MapTestTags.FAVORITES_DIALOG).assertIsDisplayed()
    }

    @Test
    fun mapScreen_selectedPlaceCloseButtonDismissesSelection() {
        var dismissed = false

        composeRule.setContent {
            CloudbasePredictorTheme {
                MapScreen(
                    uiState = MapUiState(selectedPlace = favoritePlaces.first()),
                    onMapTapped = { _, _ -> },
                    onFavoriteTapped = {},
                    onLaunchSiteTapped = {},
                    onOpenForecast = {},
                    onDismissSelection = { dismissed = true },
                    onFavoriteClick = {},
                    onSaveCameraPosition = { _, _, _ -> },
                    autoOpenFavoritesOnStartup = false,
                )
            }
        }

        composeRule.onNodeWithTag(MapTestTags.SELECTION_CARD_CLOSE_BUTTON)
            .assertIsDisplayed()
            .performClick()

        composeRule.runOnIdle {
            assertTrue(dismissed)
        }
    }

    @Test
    fun mapScreen_selectedPlaceDismissIconDismissesSelection() {
        var dismissed = false

        composeRule.setContent {
            CloudbasePredictorTheme {
                MapScreen(
                    uiState = MapUiState(selectedPlace = favoritePlaces.first()),
                    onMapTapped = { _, _ -> },
                    onFavoriteTapped = {},
                    onLaunchSiteTapped = {},
                    onOpenForecast = {},
                    onDismissSelection = { dismissed = true },
                    onFavoriteClick = {},
                    onSaveCameraPosition = { _, _, _ -> },
                    autoOpenFavoritesOnStartup = false,
                )
            }
        }

        composeRule.onNodeWithTag(MapTestTags.SELECTION_CARD_DISMISS_ICON)
            .assertIsDisplayed()
            .performClick()

        composeRule.runOnIdle {
            assertTrue(dismissed)
        }
    }

    private companion object {
        val favoritePlaces = listOf(
            SavedPlace(
                id = "favorite-interlaken",
                name = "Interlaken",
                latitude = 46.5582,
                longitude = 7.8354,
                isFavorite = true,
            ),
            SavedPlace(
                id = "favorite-zurich",
                name = "Zurich",
                latitude = 47.3769,
                longitude = 8.5417,
                isFavorite = true,
            ),
        )
    }
}
