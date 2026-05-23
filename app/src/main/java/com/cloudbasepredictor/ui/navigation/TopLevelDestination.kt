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
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

private const val FORECAST_ROUTE_BASE_VALUE = "forecast"
private const val FORECAST_PLACE_LOCATION_ARGUMENT_VALUE = "placeLocation"
private const val FORECAST_PLACE_NAME_ARGUMENT_VALUE = "placeName"

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
        route = "$FORECAST_ROUTE_BASE_VALUE/{$FORECAST_PLACE_LOCATION_ARGUMENT_VALUE}" +
            "?$FORECAST_PLACE_NAME_ARGUMENT_VALUE={$FORECAST_PLACE_NAME_ARGUMENT_VALUE}",
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
        const val FORECAST_PLACE_NAME_ARGUMENT = "placeName"
        const val LEGACY_FORECAST_ROUTE = FORECAST_ROUTE_BASE

        fun forecastRoute(placeLocation: PlaceLocation): String {
            val baseRoute = "$FORECAST_ROUTE_BASE/${placeLocation.toRouteValue()}"
            val placeName = placeLocation.name?.trim()?.takeIf { it.isNotEmpty() } ?: return baseRoute
            return "$baseRoute?$FORECAST_PLACE_NAME_ARGUMENT=${placeName.encodeRouteQueryValue()}"
        }
    }
}

private fun String.encodeRouteQueryValue(): String {
    return URLEncoder.encode(this, StandardCharsets.UTF_8.toString())
        .replace("+", "%20")
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
