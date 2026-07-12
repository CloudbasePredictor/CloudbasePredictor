package com.cloudbasepredictor.ui.screens.map

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.cloudbasepredictor.R
import com.cloudbasepredictor.model.ManualFavoriteInputError
import com.cloudbasepredictor.model.ManualFavoriteInputResult
import com.cloudbasepredictor.model.SavedPlace
import com.cloudbasepredictor.model.parseManualFavoriteInput
import com.cloudbasepredictor.ui.preview.PreviewData
import com.cloudbasepredictor.ui.theme.CloudbasePredictorTheme

@Composable
internal fun ManualFavoriteDialog(
    onSave: (SavedPlace) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    initialName: String = "",
    initialCoordinates: String = "",
) {
    var name by rememberSaveable { mutableStateOf(initialName) }
    var coordinates by rememberSaveable { mutableStateOf(initialCoordinates) }
    var inputError by rememberSaveable { mutableStateOf<ManualFavoriteInputError?>(null) }

    AlertDialog(
        modifier = modifier,
        onDismissRequest = onDismiss,
        title = {
            Text(text = stringResource(R.string.dialog_title_add_manual_favorite))
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        inputError = null
                    },
                    label = { Text(text = stringResource(R.string.label_name)) },
                    singleLine = true,
                    isError = inputError == ManualFavoriteInputError.BLANK_NAME ||
                        inputError == ManualFavoriteInputError.NAME_TOO_LONG,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = coordinates,
                    onValueChange = {
                        coordinates = it
                        inputError = null
                    },
                    label = { Text(text = stringResource(R.string.label_coordinates)) },
                    placeholder = { Text(text = stringResource(R.string.manual_favorite_coordinates_placeholder)) },
                    isError = inputError == ManualFavoriteInputError.BLANK_COORDINATES ||
                        inputError == ManualFavoriteInputError.COORDINATES_FORMAT ||
                        inputError == ManualFavoriteInputError.LATITUDE_OUT_OF_RANGE ||
                        inputError == ManualFavoriteInputError.LONGITUDE_OUT_OF_RANGE,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = stringResource(R.string.manual_favorite_coordinates_help),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val currentError = inputError
                if (currentError != null) {
                    Text(
                        text = currentError.message(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    when (val result = parseManualFavoriteInput(name, coordinates)) {
                        is ManualFavoriteInputResult.Valid -> {
                            onSave(result.input.toSavedPlace())
                            onDismiss()
                        }
                        is ManualFavoriteInputResult.Invalid -> {
                            inputError = result.error
                        }
                    }
                },
            ) {
                Text(text = stringResource(R.string.action_save))
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
private fun ManualFavoriteInputError.message(): String {
    return when (this) {
        ManualFavoriteInputError.BLANK_NAME -> stringResource(R.string.manual_favorite_error_blank_name)
        ManualFavoriteInputError.NAME_TOO_LONG -> stringResource(R.string.manual_favorite_error_name_too_long)
        ManualFavoriteInputError.BLANK_COORDINATES -> stringResource(R.string.manual_favorite_error_blank_coordinates)
        ManualFavoriteInputError.COORDINATES_FORMAT -> stringResource(R.string.manual_favorite_error_coordinates_format)
        ManualFavoriteInputError.LATITUDE_OUT_OF_RANGE -> stringResource(R.string.manual_favorite_error_latitude_range)
        ManualFavoriteInputError.LONGITUDE_OUT_OF_RANGE -> stringResource(R.string.manual_favorite_error_longitude_range)
    }
}

@Preview(showBackground = true)
@Composable
private fun ManualFavoriteDialogPreview() {
    CloudbasePredictorTheme {
        ManualFavoriteDialog(
            onSave = {},
            onDismiss = {},
            initialName = PreviewData.manualFavoriteName,
            initialCoordinates = PreviewData.manualFavoriteCoordinates,
        )
    }
}
