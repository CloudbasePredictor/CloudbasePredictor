@file:Suppress("FunctionNaming")

package com.cloudbasepredictor.web.favorites

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.cloudbasepredictor.model.SavedPlace
import com.cloudbasepredictor.util.toFixedDecimalString
import com.cloudbasepredictor.web.presentation.destinationColumnCount
import com.cloudbasepredictor.web.presentation.destinationRows
import com.cloudbasepredictor.web.preview.WebDestinationPreviewData

@Composable
fun WebFavoritesDestination(
    savedPlaces: List<SavedPlace>,
    onPlaceSelected: (SavedPlace) -> Unit,
    onPlaceDeleted: (SavedPlace) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .widthIn(max = DestinationMaxWidth)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(DestinationPadding),
            verticalArrangement = Arrangement.spacedBy(SectionSpacing),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(HeaderSpacing)) {
                Text(
                    text = "Favorite locations",
                    modifier = Modifier.semantics { heading() },
                    style = MaterialTheme.typography.headlineMedium,
                )
                Text(
                    text = "Open a saved flying site or remove locations you no longer need.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyLarge,
                )
            }

            if (savedPlaces.isEmpty()) {
                EmptyFavoritesCard()
            } else {
                FavoritePlaceGrid(
                    savedPlaces = savedPlaces,
                    onPlaceSelected = onPlaceSelected,
                    onPlaceDeleted = onPlaceDeleted,
                )
            }
        }
    }
}

@Composable
private fun EmptyFavoritesCard() {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = "No favorite locations saved" },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(
            modifier = Modifier.padding(EmptyStatePadding),
            verticalArrangement = Arrangement.spacedBy(CardContentSpacing),
        ) {
            Text(
                text = "No favorite locations yet",
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                text = "Choose a location on the map and save it to keep forecasts one click away.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun FavoritePlaceGrid(
    savedPlaces: List<SavedPlace>,
    onPlaceSelected: (SavedPlace) -> Unit,
    onPlaceDeleted: (SavedPlace) -> Unit,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val columnCount = destinationColumnCount(maxWidth.value)
        Column(verticalArrangement = Arrangement.spacedBy(GridSpacing)) {
            destinationRows(savedPlaces, columnCount).forEach { rowPlaces ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(GridSpacing),
                ) {
                    rowPlaces.forEach { place ->
                        FavoritePlaceCard(
                            place = place,
                            onPlaceSelected = onPlaceSelected,
                            onPlaceDeleted = onPlaceDeleted,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    repeat(columnCount - rowPlaces.size) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun FavoritePlaceCard(
    place: SavedPlace,
    onPlaceSelected: (SavedPlace) -> Unit,
    onPlaceDeleted: (SavedPlace) -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedCard(modifier = modifier) {
        Column(
            modifier = Modifier.padding(CardPadding),
            verticalArrangement = Arrangement.spacedBy(CardContentSpacing),
        ) {
            Text(
                text = place.name,
                modifier = Modifier.semantics { heading() },
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = favoriteCoordinatesLabel(place),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(ActionSpacing, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(
                    onClick = { onPlaceDeleted(place) },
                    modifier = Modifier.semantics {
                        contentDescription = "Remove ${place.name} from favorites"
                    },
                ) {
                    Text("Remove")
                }
                Button(
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
}

internal fun favoriteCoordinatesLabel(place: SavedPlace): String {
    return "${place.latitude.toFixedDecimalString(COORDINATE_DECIMAL_PLACES)}, " +
        place.longitude.toFixedDecimalString(COORDINATE_DECIMAL_PLACES)
}

private const val COORDINATE_DECIMAL_PLACES = 4
private val DestinationMaxWidth = 1_080.dp
private val DestinationPadding = 24.dp
private val SectionSpacing = 24.dp
private val HeaderSpacing = 8.dp
private val GridSpacing = 16.dp
private val EmptyStatePadding = 32.dp
private val CardPadding = 20.dp
private val CardContentSpacing = 12.dp
private val ActionSpacing = 8.dp

@Preview
@Composable
private fun WebFavoritesDestinationPreview() {
    MaterialTheme {
        WebFavoritesDestination(
            savedPlaces = WebDestinationPreviewData.favoritePlaces,
            onPlaceSelected = {},
            onPlaceDeleted = {},
        )
    }
}

@Preview
@Composable
private fun EmptyWebFavoritesDestinationPreview() {
    MaterialTheme {
        WebFavoritesDestination(
            savedPlaces = emptyList(),
            onPlaceSelected = {},
            onPlaceDeleted = {},
        )
    }
}
