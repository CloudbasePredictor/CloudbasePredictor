@file:Suppress("FunctionNaming")

package com.cloudbasepredictor.web.favorites

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.cloudbasepredictor.model.SavedPlace
import com.cloudbasepredictor.util.toFixedDecimalString
import com.cloudbasepredictor.web.preview.WebDestinationPreviewData

/**
 * Favorite locations dialog, opened from the app-shell star in the mobile top bar or desktop rail.
 * Replaces the previous Favorites navigation tab so the web navigation matches the Android app's
 * four destinations (Map, Forecast, Settings, About), while keeping the saved-favorites list
 * reachable (open + remove).
 *
 * Rendered as an in-canvas overlay rather than a Compose `Dialog`/`Popup`: on the wasmJs
 * Compose target the popup layer is unreliable (dismissing it can tear down the shared canvas),
 * so the whole app is drawn in a single composition.
 */
@Suppress("LongMethod")
@Composable
fun WebFavoritesDialog(
    savedPlaces: List<SavedPlace>,
    onPlaceSelected: (SavedPlace) -> Unit,
    onPlaceDeleted: (SavedPlace) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrimInteraction = remember { MutableInteractionSource() }
    val cardInteraction = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = SCRIM_ALPHA))
            .clickable(
                interactionSource = scrimInteraction,
                indication = null,
                onClick = onDismiss,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier
                .widthIn(max = DialogMaxWidth)
                .fillMaxWidth()
                .padding(DialogMargin)
                // Consume taps on the card so they do not fall through to the scrim.
                .clickable(interactionSource = cardInteraction, indication = null, onClick = {}),
            shape = RoundedCornerShape(CornerRadius),
            tonalElevation = DialogElevation,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Column(
                modifier = Modifier.padding(DialogPadding),
                verticalArrangement = Arrangement.spacedBy(SectionSpacing),
            ) {
                Text(
                    text = "Favorite locations",
                    modifier = Modifier.semantics { heading() },
                    style = MaterialTheme.typography.headlineSmall,
                )
                if (savedPlaces.isEmpty()) {
                    Text(
                        text = "No favorite locations yet. Choose a location on the map and save it " +
                            "to keep forecasts one tap away.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = ListMaxHeight)
                            .verticalScroll(rememberScrollState()),
                    ) {
                        savedPlaces.forEachIndexed { index, place ->
                            if (index > 0) HorizontalDivider()
                            FavoriteDialogRow(
                                place = place,
                                onPlaceSelected = onPlaceSelected,
                                onPlaceDeleted = onPlaceDeleted,
                            )
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(ActionSpacing, Alignment.End),
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Close")
                    }
                }
            }
        }
    }
}

@Composable
private fun FavoriteDialogRow(
    place: SavedPlace,
    onPlaceSelected: (SavedPlace) -> Unit,
    onPlaceDeleted: (SavedPlace) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = RowSpacing),
        verticalArrangement = Arrangement.spacedBy(RowTextSpacing),
    ) {
        Text(
            text = place.name,
            modifier = Modifier.semantics { heading() },
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = favoriteCoordinatesLabel(place),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(ActionSpacing, Alignment.End),
        ) {
            TextButton(
                onClick = { onPlaceDeleted(place) },
                modifier = Modifier.semantics {
                    contentDescription = "Remove ${place.name} from favorites"
                },
            ) {
                Text("Remove")
            }
            TextButton(
                onClick = { onPlaceSelected(place) },
                modifier = Modifier.semantics {
                    contentDescription = "Open forecast for ${place.name}"
                },
            ) {
                Text("Open forecast")
            }
        }
    }
}

internal fun favoriteCoordinatesLabel(place: SavedPlace): String {
    return "${place.latitude.toFixedDecimalString(COORDINATE_DECIMAL_PLACES)}, " +
        place.longitude.toFixedDecimalString(COORDINATE_DECIMAL_PLACES)
}

private const val COORDINATE_DECIMAL_PLACES = 4
private const val SCRIM_ALPHA = 0.5f
private val DialogMaxWidth = 480.dp
private val DialogMargin = 24.dp
private val DialogPadding = 24.dp
private val DialogElevation = 6.dp
private val CornerRadius = 28.dp
private val ListMaxHeight = 360.dp
private val SectionSpacing = 16.dp
private val RowSpacing = 12.dp
private val RowTextSpacing = 2.dp
private val ActionSpacing = 8.dp

@Preview(name = "Web favorites dialog", showBackground = true, widthDp = 600, heightDp = 600)
@Composable
private fun WebFavoritesDialogPreview() {
    MaterialTheme {
        WebFavoritesDialog(
            savedPlaces = WebDestinationPreviewData.favoritePlaces,
            onPlaceSelected = {},
            onPlaceDeleted = {},
            onDismiss = {},
        )
    }
}

@Preview(name = "Web favorites dialog empty", showBackground = true, widthDp = 600, heightDp = 600)
@Composable
private fun WebFavoritesDialogEmptyPreview() {
    MaterialTheme {
        WebFavoritesDialog(
            savedPlaces = emptyList(),
            onPlaceSelected = {},
            onPlaceDeleted = {},
            onDismiss = {},
        )
    }
}
