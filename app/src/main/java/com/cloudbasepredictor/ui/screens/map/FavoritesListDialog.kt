package com.cloudbasepredictor.ui.screens.map

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.cloudbasepredictor.R
import com.cloudbasepredictor.model.SavedPlace
import com.cloudbasepredictor.ui.components.MapTestTags
import com.cloudbasepredictor.ui.preview.PreviewData
import com.cloudbasepredictor.ui.theme.CloudbasePredictorTheme
import java.util.Locale

@Composable
internal fun FavoritesListDialog(
    favorites: List<SavedPlace>,
    onPlaceClick: (SavedPlace) -> Unit,
    onAddManualClick: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AlertDialog(
        modifier = modifier,
        onDismissRequest = onDismiss,
        title = {
            Text(text = stringResource(R.string.dialog_title_favorites))
        },
        text = {
            if (favorites.isEmpty()) {
                FavoritesEmptyContent()
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 400.dp),
                ) {
                    items(favorites, key = { it.id }) { place ->
                        FavoriteListItem(
                            place = place,
                            onClick = {
                                onPlaceClick(place)
                                onDismiss()
                            },
                        )
                        HorizontalDivider()
                    }
                }
            }
        },
        confirmButton = {
            AddManualFavoriteButton(onClick = onAddManualClick)
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.action_cancel))
            }
        },
    )
}

@Composable
private fun FavoritesEmptyContent(
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(R.string.favorites_empty_message),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun AddManualFavoriteButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier
            .testTag(MapTestTags.ADD_MANUAL_FAVORITE_BUTTON)
            .padding(vertical = 4.dp),
    ) {
        Icon(
            imageVector = Icons.Filled.Add,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = stringResource(R.string.action_add_manually))
    }
}

@Composable
private fun FavoriteListItem(
    place: SavedPlace,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 4.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = place.name,
            style = MaterialTheme.typography.bodyLarge,
        )
        Text(
            text = String.format(
                Locale.US,
                "%.4f, %.4f",
                place.latitude,
                place.longitude,
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun FavoritesListDialogPreview() {
    CloudbasePredictorTheme {
        FavoritesListDialog(
            favorites = PreviewData.favoritePlaces,
            onPlaceClick = {},
            onAddManualClick = {},
            onDismiss = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun FavoritesListDialogEmptyPreview() {
    CloudbasePredictorTheme {
        FavoritesListDialog(
            favorites = emptyList(),
            onPlaceClick = {},
            onAddManualClick = {},
            onDismiss = {},
        )
    }
}
