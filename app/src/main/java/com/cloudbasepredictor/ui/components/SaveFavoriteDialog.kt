package com.cloudbasepredictor.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.cloudbasepredictor.R
import com.cloudbasepredictor.model.SavedPlace
import com.cloudbasepredictor.ui.preview.PreviewData
import com.cloudbasepredictor.ui.theme.CloudbasePredictorTheme
import java.util.Locale

@Composable
fun SaveFavoriteDialog(
    currentName: String,
    isFavorite: Boolean,
    favoritePlaces: List<SavedPlace> = emptyList(),
    selectedPlaceId: String? = null,
    onSave: (String) -> Unit,
    onDelete: () -> Unit,
    onFavoriteSelected: (SavedPlace) -> Unit = {},
    onDismiss: () -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(currentName) }
    val selectableFavoritePlaces = favoritePlaces.filterNot { it.id == selectedPlaceId }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = if (isFavorite) stringResource(R.string.dialog_title_edit_favorite) else stringResource(R.string.dialog_title_save_favorite))
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(text = stringResource(R.string.label_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (selectableFavoritePlaces.isNotEmpty()) {
                    HorizontalDivider(modifier = Modifier.padding(top = 8.dp))
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 260.dp),
                    ) {
                        items(selectableFavoritePlaces, key = { it.id }) { place ->
                            FavoritePlaceListItem(
                                place = place,
                                onClick = {
                                    onFavoriteSelected(place)
                                    onDismiss()
                                },
                            )
                            HorizontalDivider()
                        }
                    }
                }
            }
        },
        confirmButton = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (isFavorite) {
                    TextButton(onClick = {
                        onDelete()
                        onDismiss()
                    }) {
                        Text(
                            text = stringResource(R.string.action_delete),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
                TextButton(
                    onClick = {
                        onSave(name.trim())
                        onDismiss()
                    },
                    enabled = name.isNotBlank(),
                ) {
                    Text(text = stringResource(R.string.action_save))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.action_cancel))
            }
        },
    )
}

@Composable
private fun FavoritePlaceListItem(
    place: SavedPlace,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(
            modifier = Modifier.weight(1f),
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
}

@Preview(showBackground = true)
@Composable
private fun SaveFavoriteDialogNewPreview() {
    CloudbasePredictorTheme {
        SaveFavoriteDialog(
            currentName = "",
            isFavorite = false,
            onSave = {},
            onDelete = {},
            onDismiss = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun SaveFavoriteDialogEditPreview() {
    CloudbasePredictorTheme {
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
