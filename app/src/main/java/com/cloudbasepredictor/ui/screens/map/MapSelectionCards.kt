package com.cloudbasepredictor.ui.screens.map

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.cloudbasepredictor.R
import com.cloudbasepredictor.model.ParaglidingLaunchSite
import com.cloudbasepredictor.model.SavedPlace
import com.cloudbasepredictor.ui.components.MapTestTags
import com.cloudbasepredictor.ui.preview.PreviewData
import com.cloudbasepredictor.ui.theme.CloudbasePredictorTheme
import java.util.Locale

@Composable
internal fun MapSelectionCards(
    selectedPlace: SavedPlace?,
    selectedLaunchSite: ParaglidingLaunchSite?,
    selectedFavoriteLaunchSite: FavoriteLaunchSiteMarker?,
    onOpenForecast: () -> Unit,
    onDismissSelection: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (selectedFavoriteLaunchSite == null && selectedLaunchSite == null && selectedPlace == null) {
        return
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 32.dp),
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        when {
            selectedFavoriteLaunchSite != null -> {
                LaunchSiteCard(
                    launchSite = selectedFavoriteLaunchSite.launchSite,
                    favoritePlace = selectedFavoriteLaunchSite.favoritePlace,
                    onOpenForecast = onOpenForecast,
                    onDismiss = onDismissSelection,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            selectedLaunchSite != null -> {
                LaunchSiteCard(
                    launchSite = selectedLaunchSite,
                    onOpenForecast = onOpenForecast,
                    onDismiss = onDismissSelection,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            selectedPlace != null -> {
                SelectedPointCard(
                    selectedPlace = selectedPlace,
                    onOpenForecast = onOpenForecast,
                    onDismiss = onDismissSelection,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
internal fun LaunchSiteCard(
    launchSite: ParaglidingLaunchSite,
    favoritePlace: SavedPlace? = null,
    onOpenForecast: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cardDisplay = launchSiteCardDisplay(
        launchSite = launchSite,
        favoritePlace = favoritePlace,
    )

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SelectionCardHeader(
                title = cardDisplay.title,
                onDismiss = onDismiss,
            )
            cardDisplay.originalName?.let { originalName ->
                Text(
                    text = stringResource(R.string.map_launch_site_original_name_format, originalName),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = stringResource(R.string.map_launch_site_source),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )

            launchSite.altitudeMeters?.let { altitudeMeters ->
                Text(
                    text = stringResource(R.string.map_launch_site_altitude_format, altitudeMeters),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            launchSite.windSummary()?.let { windSummary ->
                Text(
                    text = stringResource(R.string.map_launch_site_wind_format, windSummary),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            launchSite.activities.takeIf { it.isNotEmpty() }?.let { activities ->
                Text(
                    text = stringResource(
                        R.string.map_launch_site_activity_format,
                        activities.joinToString(", "),
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            launchSite.landingName?.let { landingName ->
                Text(
                    text = stringResource(R.string.map_launch_site_landing_format, landingName),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            launchSite.cardDescription()?.let { description ->
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                )
            }

            Text(
                text = String.format(
                    Locale.US,
                    stringResource(R.string.coordinates_lat_lon_format),
                    launchSite.latitude,
                    launchSite.longitude,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Text(
                text = stringResource(R.string.map_launch_site_data_attribution),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            SelectionCardActions(
                onDismiss = onDismiss,
                onOpenForecast = onOpenForecast,
            )
        }
    }
}

internal data class LaunchSiteCardDisplay(
    val title: String,
    val originalName: String?,
)

internal fun launchSiteCardDisplay(
    launchSite: ParaglidingLaunchSite,
    favoritePlace: SavedPlace?,
): LaunchSiteCardDisplay {
    val launchSiteName = launchSite.name.trim()
    val favoriteName = favoritePlace?.name?.trim()?.takeIf { name -> name.isNotEmpty() }
    if (favoriteName == null || favoriteName == launchSiteName) {
        return LaunchSiteCardDisplay(
            title = launchSite.name,
            originalName = null,
        )
    }
    return LaunchSiteCardDisplay(
        title = favoriteName,
        originalName = launchSite.name,
    )
}

@Composable
internal fun MapUnavailableCard(
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.map_unavailable_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(R.string.map_unavailable_message),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = onRetry) {
                Text(text = stringResource(R.string.action_retry))
            }
        }
    }
}

@Composable
internal fun SelectedPointCard(
    selectedPlace: SavedPlace,
    onOpenForecast: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SelectionCardHeader(
                title = selectedPlace.name,
                onDismiss = onDismiss,
            )
            Text(
                text = String.format(
                    Locale.US,
                    stringResource(R.string.coordinates_lat_lon_format),
                    selectedPlace.latitude,
                    selectedPlace.longitude,
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SelectionCardActions(
                onDismiss = onDismiss,
                onOpenForecast = onOpenForecast,
            )
        }
    }
}

@Composable
private fun SelectionCardHeader(
    title: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f),
        )
        IconButton(
            onClick = onDismiss,
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f))
                .testTag(MapTestTags.SELECTION_CARD_DISMISS_ICON),
        ) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = stringResource(R.string.action_close),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun SelectionCardActions(
    onDismiss: () -> Unit,
    onOpenForecast: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedButton(
            onClick = onDismiss,
            modifier = Modifier.testTag(MapTestTags.SELECTION_CARD_CLOSE_BUTTON),
        ) {
            Text(text = stringResource(R.string.action_close))
        }
        Button(
            onClick = onOpenForecast,
            modifier = Modifier.testTag(MapTestTags.SELECTION_CARD_OPEN_BUTTON),
        ) {
            Text(text = stringResource(R.string.action_open))
        }
    }
}

private fun ParaglidingLaunchSite.windSummary(): String? {
    if (orientations.isEmpty()) return null
    val best = orientations.filter { it.rating >= 2 }.map { it.direction }
    val possible = orientations.filter { it.rating == 1 }.map { it.direction }
    return when {
        best.isNotEmpty() && possible.isNotEmpty() -> {
            "best ${best.joinToString(", ")}; possible ${possible.joinToString(", ")}"
        }
        best.isNotEmpty() -> best.joinToString(", ")
        possible.isNotEmpty() -> possible.joinToString(", ")
        else -> null
    }
}

private fun ParaglidingLaunchSite.cardDescription(): String? {
    val text = listOfNotNull(description, weather, flightRules).firstOrNull { it.isNotBlank() } ?: return null
    return text.shortenForCard(maxLength = 220)
}

private fun String.shortenForCard(maxLength: Int): String {
    if (length <= maxLength) return this
    return take(maxLength)
        .trimEnd()
        .trimEnd('.', ',', ';', ':')
        .plus("...")
}

@Preview(showBackground = true)
@Composable
private fun SelectedPointCardPreview() {
    CloudbasePredictorTheme {
        SelectedPointCard(
            selectedPlace = PreviewData.mapUiState.selectedPlace ?: PreviewData.savedPlace,
            onOpenForecast = {},
            onDismiss = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun LaunchSiteCardPreview() {
    CloudbasePredictorTheme {
        LaunchSiteCard(
            launchSite = PreviewData.paraglidingLaunchSite,
            onOpenForecast = {},
            onDismiss = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun MapSelectionCardsPreview() {
    CloudbasePredictorTheme {
        MapSelectionCards(
            selectedPlace = null,
            selectedLaunchSite = null,
            selectedFavoriteLaunchSite = FavoriteLaunchSiteMarker(
                favoritePlace = PreviewData.savedPlace,
                launchSite = PreviewData.paraglidingLaunchSite,
            ),
            onOpenForecast = {},
            onDismissSelection = {},
        )
    }
}
