package com.cloudbasepredictor.screenshot

import android.graphics.Bitmap
import android.os.Environment
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.platform.app.InstrumentationRegistry
import com.cloudbasepredictor.data.datasource.DataSourcePreference
import com.cloudbasepredictor.data.language.AppLanguage
import com.cloudbasepredictor.data.map.MapLayerPreference
import com.cloudbasepredictor.data.theme.ThemePreference
import com.cloudbasepredictor.data.units.UnitPreset
import com.cloudbasepredictor.data.units.resolveDisplayUnits
import com.cloudbasepredictor.model.ForecastMode
import com.cloudbasepredictor.model.ForecastModel
import com.cloudbasepredictor.testutil.SimulatedTestData
import com.cloudbasepredictor.ui.components.MapAttributionOverlay
import com.cloudbasepredictor.ui.components.MapTestTags
import com.cloudbasepredictor.ui.components.SaveFavoriteDialog
import com.cloudbasepredictor.ui.preview.PreviewData
import com.cloudbasepredictor.ui.screens.about.AboutScreen
import com.cloudbasepredictor.ui.screens.forecast.DEFAULT_TOP_ALTITUDE_KM
import com.cloudbasepredictor.ui.screens.forecast.ForecastChartViewport
import com.cloudbasepredictor.ui.screens.forecast.ForecastNoPlaceUiState
import com.cloudbasepredictor.ui.screens.forecast.ForecastReadyUiState
import com.cloudbasepredictor.ui.screens.forecast.ForecastScreen
import com.cloudbasepredictor.ui.screens.forecast.ForecastTestTags.HELP_BUTTON
import com.cloudbasepredictor.ui.screens.forecast.ForecastTestTags.MODEL_SELECTOR_BUTTON
import com.cloudbasepredictor.ui.screens.forecast.ForecastTestTags.STUVE_CHART_CANVAS
import com.cloudbasepredictor.ui.screens.forecast.ForecastTestTags.THERMIC_VIEW
import com.cloudbasepredictor.ui.screens.forecast.ForecastTestTags.WIND_VIEW
import com.cloudbasepredictor.ui.screens.map.MapScreen
import com.cloudbasepredictor.ui.screens.map.MapUiState
import com.cloudbasepredictor.ui.screens.settings.SettingsScreen
import com.cloudbasepredictor.ui.theme.CloudbasePredictorTheme
import org.junit.Rule
import org.junit.Test
import java.io.File

/**
 * Broad screenshot capture set for design-system reviews.
 *
 * This class is separate from [ScreenshotCaptureTest] so the older screenshot capture task remains
 * stable. Run it explicitly:
 *   ./gradlew :app:connectedDebugAndroidTest \
 *       -Pandroid.testInstrumentationRunnerArguments.class=com.cloudbasepredictor.screenshot.DesignSystemScreenshotCaptureTest
 *
 * Screenshots are saved to /sdcard/Pictures/CloudbaseDesignSystemScreenshots/ on the device.
 */
class DesignSystemScreenshotCaptureTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun captureForecastThermicDay0Light() {
        captureForecastScreen("forecast_thermic_day0_light", ForecastMode.THERMIC, dayIndex = 0)
    }

    @Test
    fun captureForecastThermicDay1Light() {
        captureForecastScreen("forecast_thermic_day1_light", ForecastMode.THERMIC, dayIndex = 1)
    }

    @Test
    fun captureForecastThermicDay2Light() {
        captureForecastScreen("forecast_thermic_day2_light", ForecastMode.THERMIC, dayIndex = 2)
    }

    @Test
    fun captureForecastStuveDay0Light() {
        captureForecastScreen("forecast_stuve_day0_light", ForecastMode.STUVE, dayIndex = 0)
    }

    @Test
    fun captureForecastStuveDay1Light() {
        captureForecastScreen("forecast_stuve_day1_light", ForecastMode.STUVE, dayIndex = 1)
    }

    @Test
    fun captureForecastStuveDay2Light() {
        captureForecastScreen("forecast_stuve_day2_light", ForecastMode.STUVE, dayIndex = 2)
    }

    @Test
    fun captureForecastWindDay0Light() {
        captureForecastScreen("forecast_wind_day0_light", ForecastMode.WIND, dayIndex = 0)
    }

    @Test
    fun captureForecastWindDay1Light() {
        captureForecastScreen("forecast_wind_day1_light", ForecastMode.WIND, dayIndex = 1)
    }

    @Test
    fun captureForecastWindDay2Light() {
        captureForecastScreen("forecast_wind_day2_light", ForecastMode.WIND, dayIndex = 2)
    }

    @Test
    fun captureForecastCloudDay0Light() {
        captureForecastScreen("forecast_cloud_day0_light", ForecastMode.CLOUD, dayIndex = 0)
    }

    @Test
    fun captureForecastCloudDay1Light() {
        captureForecastScreen("forecast_cloud_day1_light", ForecastMode.CLOUD, dayIndex = 1)
    }

    @Test
    fun captureForecastCloudDay2Light() {
        captureForecastScreen("forecast_cloud_day2_light", ForecastMode.CLOUD, dayIndex = 2)
    }

    @Test
    fun captureForecastThermicDay0Dark() {
        captureForecastScreen("forecast_thermic_day0_dark", ForecastMode.THERMIC, dayIndex = 0, darkTheme = true)
    }

    @Test
    fun captureForecastStuveDay0Dark() {
        captureForecastScreen("forecast_stuve_day0_dark", ForecastMode.STUVE, dayIndex = 0, darkTheme = true)
    }

    @Test
    fun captureForecastWindDay0Dark() {
        captureForecastScreen("forecast_wind_day0_dark", ForecastMode.WIND, dayIndex = 0, darkTheme = true)
    }

    @Test
    fun captureForecastCloudDay0Dark() {
        captureForecastScreen("forecast_cloud_day0_dark", ForecastMode.CLOUD, dayIndex = 0, darkTheme = true)
    }

    @Test
    fun captureForecastThermicDay2ZoomedOut() {
        captureForecastScreen(
            name = "forecast_thermic_day2_zoomed_out",
            mode = ForecastMode.THERMIC,
            dayIndex = 2,
            topAltitudeKm = 6.5f,
        )
    }

    @Test
    fun captureForecastStuveDay2ZoomedOut() {
        captureForecastScreen(
            name = "forecast_stuve_day2_zoomed_out",
            mode = ForecastMode.STUVE,
            dayIndex = 2,
            topAltitudeKm = 6.5f,
        )
    }

    @Test
    fun captureForecastWindDay2ZoomedOut() {
        captureForecastScreen(
            name = "forecast_wind_day2_zoomed_out",
            mode = ForecastMode.WIND,
            dayIndex = 2,
            topAltitudeKm = 6.5f,
        )
    }

    @Test
    fun captureForecastThermicBestEffortFallback() {
        captureForecastScreen(
            name = "forecast_thermic_best_effort_fallback",
            mode = ForecastMode.THERMIC,
            dayIndex = 0,
            model = ForecastModel.BEST_MATCH,
            resolvedModel = ForecastModel.ICON_D2,
        )
    }

    @Test
    fun captureForecastThermicAromeModel() {
        captureForecastScreen(
            name = "forecast_thermic_arome_model",
            mode = ForecastMode.THERMIC,
            dayIndex = 0,
            model = ForecastModel.METEOFRANCE_AROME,
        )
    }

    @Test
    fun captureForecastWindEcmwfModel() {
        captureForecastScreen(
            name = "forecast_wind_ecmwf_model",
            mode = ForecastMode.WIND,
            dayIndex = 0,
            model = ForecastModel.ECMWF_IFS,
        )
    }

    @Test
    fun captureForecastThermicImperialUnits() {
        captureForecastScreen(
            name = "forecast_thermic_imperial_units",
            mode = ForecastMode.THERMIC,
            dayIndex = 0,
            unitPreset = UnitPreset.IMPERIAL,
        )
    }

    @Test
    fun captureForecastWindAviationUnits() {
        captureForecastScreen(
            name = "forecast_wind_aviation_units",
            mode = ForecastMode.WIND,
            dayIndex = 0,
            unitPreset = UnitPreset.AVIATION,
        )
    }

    @Test
    fun captureForecastLoadingLight() {
        captureScreen("forecast_loading_light") {
            CloudbasePredictorTheme {
                ForecastScreen(
                    uiState = PreviewData.forecastLoadingUiState.copy(
                        selectedPlace = SimulatedTestData.brauneckPlace,
                    ),
                    onDateSelected = {},
                    onOpenMap = {},
                )
            }
        }
    }

    @Test
    fun captureForecastLoadingDark() {
        captureScreen("forecast_loading_dark") {
            CloudbasePredictorTheme(darkTheme = true) {
                ForecastScreen(
                    uiState = PreviewData.forecastLoadingUiState.copy(
                        selectedPlace = SimulatedTestData.brauneckPlace,
                    ),
                    onDateSelected = {},
                    onOpenMap = {},
                )
            }
        }
    }

    @Test
    fun captureForecastErrorLight() {
        captureScreen("forecast_error_light") {
            CloudbasePredictorTheme {
                ForecastScreen(
                    uiState = PreviewData.forecastErrorUiState.copy(
                        selectedPlace = SimulatedTestData.brauneckPlace,
                    ),
                    onDateSelected = {},
                    onOpenMap = {},
                )
            }
        }
    }

    @Test
    fun captureForecastErrorDark() {
        captureScreen("forecast_error_dark") {
            CloudbasePredictorTheme(darkTheme = true) {
                ForecastScreen(
                    uiState = PreviewData.forecastErrorUiState.copy(
                        selectedPlace = SimulatedTestData.brauneckPlace,
                    ),
                    onDateSelected = {},
                    onOpenMap = {},
                )
            }
        }
    }

    @Test
    fun captureForecastNoPlace() {
        captureScreen("forecast_no_place") {
            CloudbasePredictorTheme {
                ForecastScreen(
                    uiState = ForecastNoPlaceUiState(),
                    onDateSelected = {},
                    onOpenMap = {},
                )
            }
        }
    }

    @Test
    fun captureForecastThermicSelectedCell() {
        composeRule.setContent {
            CloudbasePredictorTheme {
                ForecastScreen(
                    uiState = simulatedState(ForecastMode.THERMIC),
                    onDateSelected = {},
                    onOpenMap = {},
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(THERMIC_VIEW).performTouchInput {
            click(Offset(width * 0.62f, height * 0.58f))
        }
        composeRule.waitForIdle()
        captureCurrentContent("forecast_thermic_selected_cell")
    }

    @Test
    fun captureForecastWindSelectedCell() {
        composeRule.setContent {
            CloudbasePredictorTheme {
                ForecastScreen(
                    uiState = simulatedState(ForecastMode.WIND),
                    onDateSelected = {},
                    onOpenMap = {},
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(WIND_VIEW).performTouchInput {
            click(Offset(width * 0.55f, height * 0.52f))
        }
        composeRule.waitForIdle()
        captureCurrentContent("forecast_wind_selected_cell")
    }

    @Test
    fun captureForecastStuveSelectedMidlevel() {
        val stuveState = simulatedState(ForecastMode.STUVE)
        composeRule.setContent {
            CloudbasePredictorTheme {
                ForecastScreen(
                    uiState = stuveState.copy(
                        chartViewport = stuveState.chartViewport.copy(
                            visibleTopAltitudeKm = DEFAULT_TOP_ALTITUDE_KM,
                        ),
                    ),
                    onDateSelected = {},
                    onOpenMap = {},
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(STUVE_CHART_CANVAS).performTouchInput {
            click(Offset(width * 0.48f, height * 0.47f))
        }
        composeRule.waitForIdle()
        captureCurrentContent("forecast_stuve_selected_midlevel")
    }

    @Test
    fun captureThermicHelpDialogLight() {
        captureForecastHelpDialog("dialog_help_thermic_light", ForecastMode.THERMIC, darkTheme = false)
    }

    @Test
    fun captureStuveHelpDialogLight() {
        captureForecastHelpDialog("dialog_help_stuve_light", ForecastMode.STUVE, darkTheme = false)
    }

    @Test
    fun captureWindHelpDialogLight() {
        captureForecastHelpDialog("dialog_help_wind_light", ForecastMode.WIND, darkTheme = false)
    }

    @Test
    fun captureCloudHelpDialogLight() {
        captureForecastHelpDialog("dialog_help_cloud_light", ForecastMode.CLOUD, darkTheme = false)
    }

    @Test
    fun captureCloudHelpDialogDark() {
        captureForecastHelpDialog("dialog_help_cloud_dark", ForecastMode.CLOUD, darkTheme = true)
    }

    @Test
    fun captureModelSelectorSheetLight() {
        captureModelSelectorSheet("sheet_model_selector_light", darkTheme = false)
    }

    @Test
    fun captureModelSelectorSheetDark() {
        captureModelSelectorSheet("sheet_model_selector_dark", darkTheme = true)
    }

    @Test
    fun captureSaveFavoriteNewDialogLight() {
        captureDialog("dialog_save_favorite_new_light") {
            SaveFavoriteDialog(
                currentName = "",
                isFavorite = false,
                favoritePlaces = PreviewData.favoritePlaces,
                onSave = {},
                onDelete = {},
                onDismiss = {},
            )
        }
    }

    @Test
    fun captureSaveFavoriteEditDialogLight() {
        captureDialog("dialog_save_favorite_edit_light") {
            SaveFavoriteDialog(
                currentName = PreviewData.savedPlace.name,
                isFavorite = true,
                favoritePlaces = PreviewData.favoritePlaces,
                selectedPlaceId = PreviewData.savedPlace.id,
                onSave = {},
                onDelete = {},
                onFavoriteSelected = {},
                onDismiss = {},
            )
        }
    }

    @Test
    fun captureSaveFavoriteEditDialogDark() {
        captureDialog(
            name = "dialog_save_favorite_edit_dark",
            darkTheme = true,
        ) {
            SaveFavoriteDialog(
                currentName = PreviewData.savedPlace.name,
                isFavorite = true,
                favoritePlaces = PreviewData.favoritePlaces,
                selectedPlaceId = PreviewData.savedPlace.id,
                onSave = {},
                onDelete = {},
                onFavoriteSelected = {},
                onDismiss = {},
            )
        }
    }

    @Test
    fun captureMapSelectedPointLight() {
        captureMapScreen("map_selected_point_light", darkTheme = false)
    }

    @Test
    fun captureMapSelectedPointDark() {
        captureMapScreen("map_selected_point_dark", darkTheme = true)
    }

    @Test
    fun captureMapSelectedLaunchSiteLight() {
        captureMapScreen(
            name = "map_selected_launch_site_light",
            darkTheme = false,
            uiState = PreviewData.mapUiState.copy(
                selectedPlace = null,
                selectedLaunchSite = PreviewData.paraglidingLaunchSite,
            ),
        )
    }

    @Test
    fun captureMapTopoSelectedPointLight() {
        captureMapScreen(
            name = "map_topo_selected_point_light",
            darkTheme = false,
            uiState = PreviewData.mapUiState.copy(
                mapLayer = MapLayerPreference.OPENTOPOMAP,
            ),
        )
    }

    @Test
    fun captureMapLayerMenu() {
        composeRule.setContent {
            CloudbasePredictorTheme {
                MapScreen(
                    uiState = PreviewData.mapUiState.copy(
                        mapLayer = MapLayerPreference.ESRI_WORLD_IMAGERY,
                    ),
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
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(MapTestTags.LAYER_BUTTON).performClick()
        composeRule.waitForIdle()
        captureCurrentWindow("map_layer_menu")
    }

    @Test
    fun captureMapAttributionDialog() {
        composeRule.setContent {
            CloudbasePredictorTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.BottomEnd,
                    ) {
                        MapAttributionOverlay(
                            text = "OpenFreeMap + ParaglidingEarth",
                            detailText = "OpenFreeMap, OpenStreetMap, and ParaglidingEarth attribution details.",
                        )
                    }
                }
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(MapTestTags.ATTRIBUTION_OVERLAY).performClick()
        composeRule.waitForIdle()
        captureCurrentWindow("dialog_map_attribution")
    }

    @Test
    fun captureFavoritesDialogLight() {
        captureFavoritesDialog("dialog_favorites_list_light", darkTheme = false)
    }

    @Test
    fun captureFavoritesDialogDark() {
        captureFavoritesDialog("dialog_favorites_list_dark", darkTheme = true)
    }

    @Test
    fun captureFavoritesDialogEmptyLight() {
        composeRule.setContent {
            CloudbasePredictorTheme {
                MapScreen(
                    uiState = PreviewData.mapUiState.copy(
                        selectedPlace = null,
                        favoritePlaces = emptyList(),
                    ),
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
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(MapTestTags.FAVORITES_BUTTON).performClick()
        composeRule.waitForIdle()
        captureCurrentWindow("dialog_favorites_empty_light")
    }

    @Test
    fun captureManualFavoriteDialogLight() {
        captureManualFavoriteDialog("dialog_manual_favorite_light", darkTheme = false)
    }

    @Test
    fun captureManualFavoriteDialogDark() {
        captureManualFavoriteDialog("dialog_manual_favorite_dark", darkTheme = true)
    }

    @Test
    fun captureSettingsLightRealMetric() {
        captureSettingsScreen(
            name = "settings_light_real_metric",
            darkTheme = false,
            dataSource = DataSourcePreference.REAL,
            theme = ThemePreference.LIGHT,
            unitPreset = UnitPreset.METRIC_MPS,
        )
    }

    @Test
    fun captureSettingsDarkSimulatedImperial() {
        captureSettingsScreen(
            name = "settings_dark_simulated_imperial",
            darkTheme = true,
            dataSource = DataSourcePreference.SIMULATED,
            theme = ThemePreference.DARK,
            unitPreset = UnitPreset.IMPERIAL,
        )
    }

    @Test
    fun captureSettingsDataSourceMenu() {
        captureSettingsDropdown(
            name = "settings_data_source_menu",
            fieldText = "Real",
            dataSource = DataSourcePreference.REAL,
            theme = ThemePreference.LIGHT,
            unitPreset = UnitPreset.METRIC_MPS,
        )
    }

    @Test
    fun captureSettingsThemeMenu() {
        captureSettingsDropdown(
            name = "settings_theme_menu",
            fieldText = "Light",
            dataSource = DataSourcePreference.REAL,
            theme = ThemePreference.LIGHT,
            unitPreset = UnitPreset.METRIC_MPS,
        )
    }

    @Test
    fun captureSettingsUnitsMenu() {
        captureSettingsDropdown(
            name = "settings_units_menu",
            fieldText = "Metric (m/s)",
            dataSource = DataSourcePreference.REAL,
            theme = ThemePreference.LIGHT,
            unitPreset = UnitPreset.METRIC_MPS,
        )
    }

    @Test
    fun captureAboutLight() {
        captureScreen("about_light") {
            CloudbasePredictorTheme {
                AboutScreen(onBack = {})
            }
        }
    }

    @Test
    fun captureAboutDark() {
        captureScreen("about_dark") {
            CloudbasePredictorTheme(darkTheme = true) {
                AboutScreen(onBack = {})
            }
        }
    }

    private fun captureForecastScreen(
        name: String,
        mode: ForecastMode,
        dayIndex: Int,
        topAltitudeKm: Float = ForecastChartViewport().visibleTopAltitudeKm,
        model: ForecastModel = ForecastModel.ICON_SEAMLESS,
        resolvedModel: ForecastModel = model,
        unitPreset: UnitPreset = UnitPreset.METRIC_KMH,
        darkTheme: Boolean = false,
    ) {
        captureScreen(name) {
            CloudbasePredictorTheme(darkTheme = darkTheme) {
                ForecastScreen(
                    uiState = simulatedState(
                        mode = mode,
                        dayIndex = dayIndex,
                        topAltitudeKm = topAltitudeKm,
                        model = model,
                        resolvedModel = resolvedModel,
                        unitPreset = unitPreset,
                    ),
                    onDateSelected = {},
                    onOpenMap = {},
                )
            }
        }
    }

    private fun simulatedState(
        mode: ForecastMode,
        dayIndex: Int = 0,
        topAltitudeKm: Float = ForecastChartViewport().visibleTopAltitudeKm,
        model: ForecastModel = ForecastModel.ICON_SEAMLESS,
        resolvedModel: ForecastModel = model,
        unitPreset: UnitPreset = UnitPreset.METRIC_KMH,
    ): ForecastReadyUiState {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        return SimulatedTestData.forecastUiState(
            context = context,
            mode = mode,
            dayIndex = dayIndex,
            topAltitudeKm = topAltitudeKm,
        ).copy(
            selectedModel = model,
            resolvedModel = resolvedModel,
            forecastText = "Day $dayIndex ${mode.fileName} forecast for Brauneck Sud.",
            favoritePlaces = PreviewData.favoritePlaces,
            unitPreset = unitPreset,
            displayUnits = unitPreset.resolveDisplayUnits(),
        )
    }

    private fun captureForecastHelpDialog(
        name: String,
        mode: ForecastMode,
        darkTheme: Boolean,
    ) {
        composeRule.setContent {
            CloudbasePredictorTheme(darkTheme = darkTheme) {
                ForecastScreen(
                    uiState = simulatedState(mode),
                    onDateSelected = {},
                    onOpenMap = {},
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(HELP_BUTTON).performClick()
        composeRule.waitForIdle()
        captureCurrentWindow(name)
    }

    private fun captureModelSelectorSheet(
        name: String,
        darkTheme: Boolean,
    ) {
        composeRule.setContent {
            CloudbasePredictorTheme(darkTheme = darkTheme) {
                ForecastScreen(
                    uiState = simulatedState(
                        mode = ForecastMode.THERMIC,
                        model = ForecastModel.BEST_MATCH,
                        resolvedModel = ForecastModel.ICON_D2,
                    ),
                    onDateSelected = {},
                    onOpenMap = {},
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(MODEL_SELECTOR_BUTTON).performClick()
        composeRule.waitForIdle()
        captureCurrentWindow(name)
    }

    private fun captureMapScreen(
        name: String,
        darkTheme: Boolean,
        uiState: MapUiState = PreviewData.mapUiState,
    ) {
        captureScreen(name) {
            CloudbasePredictorTheme(darkTheme = darkTheme) {
                MapScreen(
                    uiState = uiState,
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
    }

    private fun captureFavoritesDialog(
        name: String,
        darkTheme: Boolean,
    ) {
        composeRule.setContent {
            CloudbasePredictorTheme(darkTheme = darkTheme) {
                MapScreen(
                    uiState = PreviewData.mapUiState,
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
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(MapTestTags.FAVORITES_BUTTON).performClick()
        composeRule.waitForIdle()
        captureCurrentWindow(name)
    }

    private fun captureManualFavoriteDialog(
        name: String,
        darkTheme: Boolean,
    ) {
        composeRule.setContent {
            CloudbasePredictorTheme(darkTheme = darkTheme) {
                MapScreen(
                    uiState = PreviewData.mapUiState,
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
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(MapTestTags.FAVORITES_BUTTON).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(MapTestTags.ADD_MANUAL_FAVORITE_BUTTON).performClick()
        composeRule.waitForIdle()
        captureCurrentWindow(name)
    }

    private fun captureSettingsScreen(
        name: String,
        darkTheme: Boolean,
        dataSource: DataSourcePreference,
        theme: ThemePreference,
        unitPreset: UnitPreset,
    ) {
        captureScreen(name) {
            CloudbasePredictorTheme(darkTheme = darkTheme) {
                SettingsScreen(
                    dataSource = dataSource,
                    onDataSourceChanged = {},
                    theme = theme,
                    onThemeChanged = {},
                    language = AppLanguage.SYSTEM,
                    onLanguageChanged = {},
                    unitPreset = unitPreset,
                    onUnitPresetChanged = {},
                    showLaunchSites = true,
                    onShowLaunchSitesChanged = {},
                    startWithFavorites = true,
                    onStartWithFavoritesChanged = {},
                    onBack = {},
                    onOpenAbout = {},
                )
            }
        }
    }

    private fun captureSettingsDropdown(
        name: String,
        fieldText: String,
        dataSource: DataSourcePreference,
        theme: ThemePreference,
        unitPreset: UnitPreset,
    ) {
        composeRule.setContent {
            CloudbasePredictorTheme {
                SettingsScreen(
                    dataSource = dataSource,
                    onDataSourceChanged = {},
                    theme = theme,
                    onThemeChanged = {},
                    language = AppLanguage.SYSTEM,
                    onLanguageChanged = {},
                    unitPreset = unitPreset,
                    onUnitPresetChanged = {},
                    showLaunchSites = true,
                    onShowLaunchSitesChanged = {},
                    startWithFavorites = true,
                    onStartWithFavoritesChanged = {},
                    onBack = {},
                    onOpenAbout = {},
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText(fieldText).performClick()
        composeRule.waitForIdle()
        captureCurrentWindow(name)
    }

    private fun captureDialog(
        name: String,
        darkTheme: Boolean = false,
        dialog: @Composable () -> Unit,
    ) {
        composeRule.setContent {
            CloudbasePredictorTheme(darkTheme = darkTheme) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    dialog()
                }
            }
        }
        composeRule.waitForIdle()
        captureCurrentWindow(name)
    }

    private fun captureScreen(name: String, content: @Composable () -> Unit) {
        composeRule.setContent(content)
        composeRule.waitForIdle()
        captureCurrentContent(name)
    }

    private fun captureCurrentContent(name: String) {
        val bitmap = composeRule.onRoot().captureToImage()
            .let { imageBitmap ->
                val androidBitmap = Bitmap.createBitmap(
                    imageBitmap.width,
                    imageBitmap.height,
                    Bitmap.Config.ARGB_8888,
                )
                val buffer = IntArray(imageBitmap.width * imageBitmap.height)
                imageBitmap.readPixels(buffer)
                androidBitmap.setPixels(
                    buffer, 0, imageBitmap.width,
                    0, 0, imageBitmap.width, imageBitmap.height,
                )
                androidBitmap
            }

        saveBitmap(name, bitmap)
    }

    private fun captureCurrentWindow(name: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        composeRule.waitForIdle()
        instrumentation.waitForIdleSync()
        val bitmap = requireNotNull(instrumentation.uiAutomation.takeScreenshot()) {
            "Unable to capture device screenshot"
        }
        saveBitmap(name, bitmap)
    }

    private fun saveBitmap(name: String, bitmap: Bitmap) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val dir = File(
            requireNotNull(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)),
            OUTPUT_DIR,
        )
        dir.mkdirs()
        val file = File(dir, "$name.png")
        file.outputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }

        val sharedDir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
            OUTPUT_DIR,
        )
        val sharedFile = File(sharedDir, "$name.png")
        instrumentation.uiAutomation
            .executeShellCommand("mkdir -p ${sharedDir.absolutePath}")
            .close()
        instrumentation.uiAutomation
            .executeShellCommand("cp ${file.absolutePath} ${sharedFile.absolutePath}")
            .close()
    }

    private val ForecastMode.fileName: String
        get() = name.lowercase()

    private companion object {
        const val OUTPUT_DIR = "CloudbaseDesignSystemScreenshots"
    }
}
