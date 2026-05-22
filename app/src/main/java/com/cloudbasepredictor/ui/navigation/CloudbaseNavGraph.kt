package com.cloudbasepredictor.ui.navigation

import android.widget.Toast
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.cloudbasepredictor.R
import com.cloudbasepredictor.model.PlaceLocation
import com.cloudbasepredictor.ui.screens.about.AboutRoute
import com.cloudbasepredictor.ui.screens.forecast.ForecastRoute
import com.cloudbasepredictor.ui.screens.map.MapRoute
import com.cloudbasepredictor.ui.screens.settings.SettingsRoute
import com.cloudbasepredictor.ui.theme.CloudbasePredictorTheme
import timber.log.Timber

private const val NAV_ANIM_DURATION = 300

@Composable
fun CloudbaseNavGraph(
    navController: NavHostController,
    modifier: Modifier = Modifier,
    mapDestination: @Composable (
        onOpenForecast: (PlaceLocation) -> Unit,
        onOpenSettings: () -> Unit,
    ) -> Unit = { onOpenForecast, onOpenSettings ->
        MapRoute(onOpenForecast = onOpenForecast, onOpenSettings = onOpenSettings)
    },
    forecastDestination: @Composable (
        placeLocation: PlaceLocation,
        onOpenMap: () -> Unit,
        onPlaceLocationChanged: (PlaceLocation) -> Unit,
    ) -> Unit = { placeLocation, onOpenMap, onPlaceLocationChanged ->
        ForecastRoute(
            placeLocation = placeLocation,
            onOpenMap = onOpenMap,
            onPlaceLocationChanged = onPlaceLocationChanged,
        )
    },
) {
    NavHost(
        navController = navController,
        startDestination = TopLevelDestination.Map.route,
        modifier = modifier,
    ) {
        composable(route = TopLevelDestination.Map.route) {
            mapDestination(
                { placeLocation ->
                    navController.navigate(TopLevelDestination.forecastRoute(placeLocation)) {
                        launchSingleTop = true
                    }
                },
                {
                    navController.navigate(TopLevelDestination.Settings.route) {
                        launchSingleTop = true
                    }
                },
            )
        }
        composable(
            route = TopLevelDestination.Forecast.route,
            arguments = listOf(
                navArgument(TopLevelDestination.FORECAST_PLACE_LOCATION_ARGUMENT) {
                    type = NavType.StringType
                },
            ),
            enterTransition = {
                if (initialState.destination.route == TopLevelDestination.Forecast.route) {
                    EnterTransition.None
                } else {
                    fadeIn(animationSpec = tween(NAV_ANIM_DURATION))
                }
            },
            exitTransition = {
                if (targetState.destination.route == TopLevelDestination.Forecast.route) {
                    ExitTransition.None
                } else {
                    fadeOut(animationSpec = tween(NAV_ANIM_DURATION))
                }
            },
        ) { backStackEntry ->
            val placeLocation = backStackEntry.placeLocationArgument()
            if (placeLocation == null) {
                InvalidForecastDestination(
                    reason = "Invalid forecast placeLocation route argument: ${
                        backStackEntry.arguments?.getString(TopLevelDestination.FORECAST_PLACE_LOCATION_ARGUMENT)
                    }",
                    onOpenCleanMap = { navController.openCleanMapDestination() },
                )
            } else {
                forecastDestination(
                    placeLocation,
                    { navController.openMapDestination() },
                    { newPlaceLocation ->
                        navController.replaceCurrentForecastLocation(newPlaceLocation)
                    },
                )
            }
        }
        composable(
            route = TopLevelDestination.LEGACY_FORECAST_ROUTE,
            enterTransition = { fadeIn(animationSpec = tween(NAV_ANIM_DURATION)) },
            exitTransition = { fadeOut(animationSpec = tween(NAV_ANIM_DURATION)) },
        ) {
            InvalidForecastDestination(
                reason = "Legacy forecast route restored without placeLocation",
                onOpenCleanMap = { navController.openCleanMapDestination() },
            )
        }
        composable(
            route = TopLevelDestination.Settings.route,
            enterTransition = { fadeIn(animationSpec = tween(NAV_ANIM_DURATION)) },
            exitTransition = { fadeOut(animationSpec = tween(NAV_ANIM_DURATION)) },
        ) {
            SettingsRoute(
                onBack = { navController.popBackStack() },
                onOpenAbout = {
                    navController.navigate(TopLevelDestination.About.route) {
                        launchSingleTop = true
                    }
                },
            )
        }
        composable(
            route = TopLevelDestination.About.route,
            enterTransition = { fadeIn(animationSpec = tween(NAV_ANIM_DURATION)) },
            exitTransition = { fadeOut(animationSpec = tween(NAV_ANIM_DURATION)) },
        ) {
            AboutRoute(
                onBack = { navController.popBackStack() },
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun CloudbaseNavGraphPreview() {
    CloudbasePredictorTheme {
        val navController = rememberNavController()
        CloudbaseNavGraph(
            navController = navController,
            mapDestination = { _, _ ->
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(text = "Map destination preview")
                }
            },
            forecastDestination = { _, _, _ ->
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(text = "Forecast destination preview")
                }
            },
        )
    }
}

@Composable
private fun InvalidForecastDestination(
    reason: String,
    onOpenCleanMap: () -> Unit,
) {
    val context = LocalContext.current
    val message = stringResource(R.string.forecast_location_restore_failed)

    LaunchedEffect(reason) {
        Timber.w("Resetting invalid forecast destination. %s", reason)
        Toast.makeText(context.applicationContext, message, Toast.LENGTH_SHORT).show()
        onOpenCleanMap()
    }
}

private fun NavBackStackEntry.placeLocationArgument(): PlaceLocation? {
    return arguments
        ?.getString(TopLevelDestination.FORECAST_PLACE_LOCATION_ARGUMENT)
        ?.let(PlaceLocation::fromRouteValue)
}

private fun NavHostController.openMapDestination() {
    val popped = popBackStack(
        route = TopLevelDestination.Map.route,
        inclusive = false,
    )
    if (!popped) {
        navigate(TopLevelDestination.Map.route) {
            launchSingleTop = true
            restoreState = true
        }
    }
}

private fun NavHostController.openCleanMapDestination() {
    navigate(TopLevelDestination.Map.route) {
        popUpTo(TopLevelDestination.Map.route) {
            inclusive = false
            saveState = false
        }
        launchSingleTop = true
        restoreState = false
    }
}

private fun NavHostController.replaceCurrentForecastLocation(placeLocation: PlaceLocation) {
    val currentRoute = currentBackStackEntry?.destination?.route
    val routeToReplace = currentRoute?.takeIf { route ->
        route == TopLevelDestination.Forecast.route ||
            route == TopLevelDestination.LEGACY_FORECAST_ROUTE
    }
    navigate(TopLevelDestination.forecastRoute(placeLocation)) {
        launchSingleTop = true
        if (routeToReplace != null) {
            popUpTo(routeToReplace) {
                inclusive = true
            }
        }
    }
}
