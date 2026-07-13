@file:Suppress("FunctionNaming")

package com.cloudbasepredictor.web

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.WbCloudy
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.cloudbasepredictor.data.theme.ThemePreference
import com.cloudbasepredictor.model.PlaceLocation
import com.cloudbasepredictor.model.SavedPlace
import com.cloudbasepredictor.web.about.WebAboutDestination
import com.cloudbasepredictor.web.favorites.WebFavoritesDialog
import com.cloudbasepredictor.web.forecast.WebForecastDestination
import com.cloudbasepredictor.web.forecast.WebForecastMapPanel
import com.cloudbasepredictor.web.i18n.LocalWebStrings
import com.cloudbasepredictor.web.i18n.WebStrings
import com.cloudbasepredictor.web.i18n.webStringsFor
import com.cloudbasepredictor.web.map.WebMapDestination
import com.cloudbasepredictor.web.preferences.WebPreferencesState
import com.cloudbasepredictor.web.preview.WebDestinationPreviewData
import com.cloudbasepredictor.web.settings.WebSettingsDestination
import com.cloudbasepredictor.web.presentation.usesNavigationRail
import kotlinx.coroutines.launch
import androidx.compose.ui.tooling.preview.Preview

@OptIn(ExperimentalMaterial3Api::class)
@Suppress("LongMethod")
@Composable
fun CloudbaseWebApp(
    environment: WebAppEnvironment,
    routeState: WebRouteState,
    onNavigate: (WebRouteState) -> Unit,
    modifier: Modifier = Modifier,
) {
    val preferences by environment.preferences.state.collectAsState()
    val favoritePlaces by environment.favoritePlaceStore
        .observeAll()
        .collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    val systemDark = isSystemInDarkTheme()
    val darkTheme = when (preferences.themePreference) {
        ThemePreference.AUTO -> systemDark
        ThemePreference.LIGHT -> false
        ThemePreference.DARK -> true
    }
    var showFavorites by remember { mutableStateOf(false) }
    var autoOpenHandled by remember { mutableStateOf(false) }

    // Auto-open the favorites dialog on the map when the user opted in and has at least two saved
    // locations, once per app load — matching the Android app's startup behavior.
    LaunchedEffect(favoritePlaces, routeState.destination, preferences.startWithFavorites) {
        val shouldAutoOpen = routeState.destination == WebDestination.Map &&
            preferences.startWithFavorites &&
            favoritePlaces.size >= AUTO_OPEN_FAVORITE_THRESHOLD
        if (!autoOpenHandled && shouldAutoOpen) {
            autoOpenHandled = true
            showFavorites = true
        }
    }

    val strings = webStringsFor(preferences.language.resolve(environment.systemLanguageTag))
    MaterialTheme(colorScheme = if (darkTheme) darkColorScheme() else lightColorScheme()) {
        CompositionLocalProvider(LocalWebStrings provides strings) {
        Surface(modifier = modifier.fillMaxSize()) {
            ResponsiveWebScaffold(
                selectedDestination = routeState.destination,
                onDestinationSelected = { destination ->
                    onNavigate(routeState.copy(destination = destination))
                },
                onFavoriteLocationsClick = { showFavorites = true },
            ) { contentPadding ->
                Box(modifier = Modifier.fillMaxSize()) {
                    DestinationContent(
                        environment = environment,
                        routeState = routeState,
                        preferences = preferences,
                        favoritePlaces = favoritePlaces,
                        onNavigate = onNavigate,
                        onFavoriteToggle = { location, isFavorite ->
                            scope.launch {
                                val place = location.toSavedPlace().copy(isFavorite = true)
                                if (isFavorite) {
                                    environment.favoritePlaceStore.delete(place.id)
                                } else {
                                    environment.favoritePlaceStore.upsert(place)
                                }
                            }
                        },
                        onShareRequested = { copyWebShareUrl(routeState) },
                        onAddFavorite = { place ->
                            scope.launch { environment.favoritePlaceStore.upsert(place) }
                        },
                        contentPadding = contentPadding,
                    )

                    if (showFavorites) {
                        WebFavoritesDialog(
                            savedPlaces = favoritePlaces,
                            onPlaceSelected = { place ->
                                showFavorites = false
                                onNavigate(
                                    routeState.copy(
                                        destination = WebDestination.Forecast,
                                        location = place.toLocation(),
                                    ),
                                )
                            },
                            onPlaceDeleted = { place ->
                                scope.launch { environment.favoritePlaceStore.delete(place.id) }
                            },
                            onDismiss = { showFavorites = false },
                        )
                    }
                }
            }
        }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ResponsiveWebScaffold(
    selectedDestination: WebDestination,
    onDestinationSelected: (WebDestination) -> Unit,
    onFavoriteLocationsClick: () -> Unit,
    content: @Composable (PaddingValues) -> Unit,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        if (usesNavigationRail(maxWidth.value)) {
            Row(modifier = Modifier.fillMaxSize()) {
                WebNavigationRail(
                    selectedDestination = selectedDestination,
                    onDestinationSelected = onDestinationSelected,
                    onFavoriteLocationsClick = onFavoriteLocationsClick,
                )
                Scaffold(
                    modifier = Modifier.weight(1f),
                    content = content,
                )
            }
        } else {
            Scaffold(
                topBar = {
                    WebTopAppBar(onFavoriteLocationsClick)
                },
                bottomBar = {
                    WebNavigationBar(
                        selectedDestination = selectedDestination,
                        onDestinationSelected = onDestinationSelected,
                    )
                },
                content = content,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WebTopAppBar(onFavoriteLocationsClick: () -> Unit) {
    val strings = LocalWebStrings.current
    TopAppBar(
        title = { Text("Cloudbase Predictor") },
        actions = {
            IconButton(onClick = onFavoriteLocationsClick) {
                Icon(
                    imageVector = Icons.Outlined.Star,
                    contentDescription = strings.favoriteLocations,
                )
            }
        },
    )
}

@Composable
private fun WebNavigationRail(
    selectedDestination: WebDestination,
    onDestinationSelected: (WebDestination) -> Unit,
    onFavoriteLocationsClick: () -> Unit,
) {
    val strings = LocalWebStrings.current
    NavigationRail(
        modifier = Modifier.fillMaxHeight(),
        header = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Cloudbase", style = MaterialTheme.typography.labelLarge)
                Text("Predictor", style = MaterialTheme.typography.labelMedium)
                IconButton(onClick = onFavoriteLocationsClick) {
                    Icon(
                        imageVector = Icons.Outlined.Star,
                        contentDescription = strings.favoriteLocations,
                    )
                }
            }
        },
    ) {
        WebDestination.entries.forEach { destination ->
            NavigationRailItem(
                selected = destination == selectedDestination,
                onClick = { onDestinationSelected(destination) },
                icon = { Icon(imageVector = destination.icon, contentDescription = null) },
                label = { Text(destination.navLabel(strings)) },
                alwaysShowLabel = true,
            )
        }
    }
}

@Composable
private fun WebNavigationBar(
    selectedDestination: WebDestination,
    onDestinationSelected: (WebDestination) -> Unit,
) {
    val strings = LocalWebStrings.current
    NavigationBar {
        WebDestination.entries.forEach { destination ->
            NavigationBarItem(
                selected = destination == selectedDestination,
                onClick = { onDestinationSelected(destination) },
                icon = { Icon(imageVector = destination.icon, contentDescription = null) },
                label = { Text(destination.navLabel(strings)) },
            )
        }
    }
}

private fun WebDestination.navLabel(strings: WebStrings): String = when (this) {
    WebDestination.Map -> strings.navMap
    WebDestination.Forecast -> strings.navForecast
    WebDestination.Settings -> strings.navSettings
    WebDestination.About -> strings.navAbout
}

private val WebDestination.icon: ImageVector
    get() = when (this) {
        WebDestination.Map -> Icons.Outlined.Map
        WebDestination.Forecast -> Icons.Outlined.WbCloudy
        WebDestination.Settings -> Icons.Outlined.Settings
        WebDestination.About -> Icons.Outlined.Info
    }

@Suppress("LongMethod", "LongParameterList")
@Composable
private fun DestinationContent(
    environment: WebAppEnvironment,
    routeState: WebRouteState,
    preferences: WebPreferencesState,
    favoritePlaces: List<SavedPlace>,
    onNavigate: (WebRouteState) -> Unit,
    onFavoriteToggle: (PlaceLocation, Boolean) -> Unit,
    onShareRequested: () -> Unit,
    onAddFavorite: (SavedPlace) -> Unit,
    contentPadding: PaddingValues,
) {
    val contentModifier = Modifier
        .fillMaxSize()
        .padding(contentPadding)
    when (routeState.destination) {
        WebDestination.Forecast -> Box(modifier = contentModifier) {
            WebForecastDestination(
                routeState = routeState,
                repository = environment.forecastRepository,
                preferences = preferences,
                favoritePlaces = favoritePlaces,
                onRouteChanged = onNavigate,
                onForecastModelSelected = environment.preferences::selectForecastModel,
                onFavoriteToggle = onFavoriteToggle,
                onShareRequested = onShareRequested,
                initialTopAltitudeKm = environment.chartViewportStore.readTopAltitudeKm(),
                onTopAltitudeChanged = environment.chartViewportStore::writeTopAltitudeKm,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = FORECAST_MAP_PANEL_PEEK),
            )
            // Sibling of the forecast content so the draggable map panel survives the chart's
            // background reloads instead of being torn down and re-created on every location update.
            routeState.location?.let { location ->
                WebForecastMapPanel(
                    currentLocation = location,
                    mapLayer = preferences.mapLayer,
                    onLocationChanged = { updated ->
                        onNavigate(routeState.copy(location = updated))
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        WebDestination.Map -> WebMapDestination(
            routeState = routeState,
            preferences = preferences,
            favoritePlaces = favoritePlaces,
            savedCamera = environment.mapCameraStore.read(),
            launchSiteRepository = environment.launchSiteRepository,
            onMapLayerSelected = environment.preferences::selectMapLayer,
            onCameraChanged = environment.mapCameraStore::write,
            onAddFavorite = onAddFavorite,
            onLocationConfirmed = { location ->
                onNavigate(
                    routeState.copy(
                        destination = WebDestination.Forecast,
                        location = location,
                    ),
                )
            },
            modifier = contentModifier,
        )
        WebDestination.Settings -> WebSettingsDestination(
            state = preferences,
            onUnitPresetSelected = environment.preferences::selectUnitPreset,
            onThemePreferenceSelected = environment.preferences::selectTheme,
            onMapLayerSelected = environment.preferences::selectMapLayer,
            onStartWithFavoritesChanged = environment.preferences::setStartWithFavorites,
            onForecastModelSelected = { model ->
                environment.preferences.selectForecastModel(model)
                onNavigate(routeState.copy(model = model, dayIndex = 0))
            },
            onShowLaunchSitesChanged = environment.preferences::setShowLaunchSites,
            onLanguageSelected = environment.preferences::selectLanguage,
            modifier = contentModifier,
        )
        WebDestination.About -> WebAboutDestination(modifier = contentModifier)
    }
}

private fun SavedPlace.toLocation(): PlaceLocation {
    return PlaceLocation(latitude = latitude, longitude = longitude, name = name)
}

private const val AUTO_OPEN_FAVORITE_THRESHOLD = 2
private val FORECAST_MAP_PANEL_PEEK = 28.dp

@Preview(name = "Web about shell content", showBackground = true, widthDp = 1024, heightDp = 760)
@Composable
private fun CloudbaseWebAppPreview() {
    MaterialTheme {
        WebFavoritesDialog(
            savedPlaces = WebDestinationPreviewData.favoritePlaces,
            onPlaceSelected = {},
            onPlaceDeleted = {},
            onDismiss = {},
        )
    }
}
