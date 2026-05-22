package com.cloudbasepredictor.ui.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.WbCloudy
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.cloudbasepredictor.model.PlaceLocation
import com.cloudbasepredictor.ui.theme.CloudbasePredictorTheme

private const val FORECAST_ROUTE_BASE_VALUE = "forecast"
private const val FORECAST_PLACE_LOCATION_ARGUMENT_VALUE = "placeLocation"

enum class TopLevelDestination(
    val route: String,
    val label: String,
    val iconVector: ImageVector,
) {
    Map(
        route = "map",
        label = "Map",
        iconVector = Icons.Outlined.Map,
    ),
    Forecast(
        route = "$FORECAST_ROUTE_BASE_VALUE/{$FORECAST_PLACE_LOCATION_ARGUMENT_VALUE}",
        label = "Forecast",
        iconVector = Icons.Outlined.WbCloudy,
    ),
    Settings(
        route = "settings",
        label = "Settings",
        iconVector = Icons.Outlined.Settings,
    ),
    About(
        route = "about",
        label = "About",
        iconVector = Icons.Outlined.Info,
    ),
    ;

    @Composable
    fun icon() {
        Icon(
            imageVector = iconVector,
            contentDescription = label,
        )
    }

    companion object {
        const val FORECAST_ROUTE_BASE = "forecast"
        const val FORECAST_PLACE_LOCATION_ARGUMENT = "placeLocation"
        const val LEGACY_FORECAST_ROUTE = FORECAST_ROUTE_BASE

        fun forecastRoute(placeLocation: PlaceLocation): String {
            return "$FORECAST_ROUTE_BASE/${placeLocation.toRouteValue()}"
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun TopLevelDestinationPreview() {
    CloudbasePredictorTheme {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            TopLevelDestination.entries.forEach { destination ->
                destination.icon()
                Text(text = destination.label)
            }
        }
    }
}
