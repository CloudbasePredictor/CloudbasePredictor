package com.cloudbasepredictor.ui.screens.map

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.cloudbasepredictor.R
import com.cloudbasepredictor.data.map.MapLayerPreference
import com.cloudbasepredictor.ui.components.MapTestTags
import com.cloudbasepredictor.ui.theme.CloudbasePredictorTheme
import kotlin.math.min

private const val NORTH_BUTTON_VISIBILITY_THRESHOLD_DEGREES = 1.0

@Composable
internal fun MapChrome(
    mapLayer: MapLayerPreference,
    bearing: Double,
    onFavoritesClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onCurrentLocationClick: () -> Unit,
    onResetNorthClick: () -> Unit,
    onMapLayerSelected: (MapLayerPreference) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showMapLayerMenu by rememberSaveable { mutableStateOf(false) }
    val normalizedCameraBearing = normalizedBearingDegrees(bearing)

    Box(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(start = 12.dp, top = 42.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            MapChromeIconButton(
                onClick = onFavoritesClick,
                imageVector = Icons.Filled.Star,
                contentDescription = stringResource(R.string.cd_favorites),
                modifier = Modifier.testTag(MapTestTags.FAVORITES_BUTTON),
                contentColor = Color(0xFFFFD700),
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(end = 12.dp, top = 42.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.End,
        ) {
            MapChromeIconButton(
                onClick = onSettingsClick,
                imageVector = Icons.Outlined.Settings,
                contentDescription = stringResource(R.string.cd_settings),
                modifier = Modifier.testTag(MapTestTags.SETTINGS_BUTTON),
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            MapChromeIconButton(
                onClick = onCurrentLocationClick,
                imageVector = Icons.Outlined.MyLocation,
                contentDescription = stringResource(R.string.cd_current_location),
                modifier = Modifier.testTag(MapTestTags.CURRENT_LOCATION_BUTTON),
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (shouldShowNorthButton(bearing)) {
                MapChromeIconButton(
                    onClick = onResetNorthClick,
                    imageVector = Icons.Outlined.Explore,
                    contentDescription = stringResource(R.string.cd_reset_north),
                    modifier = Modifier.testTag(MapTestTags.NORTH_BUTTON),
                    contentColor = MaterialTheme.colorScheme.primary,
                    iconModifier = Modifier.rotate(-normalizedCameraBearing.toFloat()),
                )
            }

            Box {
                MapChromeIconButton(
                    onClick = { showMapLayerMenu = true },
                    imageVector = Icons.Outlined.Layers,
                    contentDescription = stringResource(R.string.cd_map_layer),
                    modifier = Modifier.testTag(MapTestTags.LAYER_BUTTON),
                    contentColor = if (mapLayer != MapLayerPreference.OPENFREEMAP) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )

                DropdownMenu(
                    expanded = showMapLayerMenu,
                    onDismissRequest = { showMapLayerMenu = false },
                ) {
                    MapLayerPreference.entries.forEach { layer ->
                        DropdownMenuItem(
                            text = { Text(text = stringResource(layer.labelRes())) },
                            leadingIcon = {
                                RadioButton(
                                    selected = layer == mapLayer,
                                    onClick = null,
                                )
                            },
                            onClick = {
                                onMapLayerSelected(layer)
                                showMapLayerMenu = false
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MapChromeIconButton(
    imageVector: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    iconModifier: Modifier = Modifier,
) {
    FloatingActionButton(
        onClick = onClick,
        modifier = modifier.size(40.dp),
        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
        contentColor = contentColor,
        elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 2.dp),
    ) {
        Icon(
            imageVector = imageVector,
            contentDescription = contentDescription,
            modifier = iconModifier,
        )
    }
}

private fun MapLayerPreference.labelRes(): Int {
    return when (this) {
        MapLayerPreference.OPENFREEMAP -> R.string.map_layer_openfreemap
        MapLayerPreference.OPENTOPOMAP -> R.string.map_layer_opentopomap
        MapLayerPreference.NASA_GIBS -> R.string.map_layer_nasa_gibs
        MapLayerPreference.ESRI_WORLD_IMAGERY -> R.string.map_layer_esri_world_imagery
    }
}

internal fun normalizedBearingDegrees(bearing: Double): Double {
    val normalized = bearing % 360.0
    return if (normalized < 0.0) normalized + 360.0 else normalized
}

internal fun shouldShowNorthButton(
    bearing: Double,
    thresholdDegrees: Double = NORTH_BUTTON_VISIBILITY_THRESHOLD_DEGREES,
): Boolean {
    val normalizedBearing = normalizedBearingDegrees(bearing)
    val distanceToNorth = min(normalizedBearing, 360.0 - normalizedBearing)
    return distanceToNorth >= thresholdDegrees
}

@Preview(showBackground = true)
@Composable
private fun MapChromePreview() {
    CloudbasePredictorTheme {
        MapChrome(
            mapLayer = MapLayerPreference.ESRI_WORLD_IMAGERY,
            bearing = 32.0,
            onFavoritesClick = {},
            onSettingsClick = {},
            onCurrentLocationClick = {},
            onResetNorthClick = {},
            onMapLayerSelected = {},
            modifier = Modifier.size(width = 320.dp, height = 220.dp),
        )
    }
}
