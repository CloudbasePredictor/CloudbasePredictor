@file:Suppress("FunctionNaming")

package com.cloudbasepredictor.web.forecast

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
import androidx.compose.material3.Button
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
import com.cloudbasepredictor.ui.preview.ForecastPreviewTheme
import com.cloudbasepredictor.web.dismissibleOverlay
import com.cloudbasepredictor.web.i18n.LocalWebStrings
import com.cloudbasepredictor.web.preview.WebPreviewData

/**
 * In-canvas favorite editor opened from the forecast title, mirroring the actions of Android's
 * `SaveFavoriteDialog` that do not require text entry: save/remove the current location, or jump to
 * another saved favorite. Uses an in-canvas overlay (not a Compose `Dialog`) because dialogs are
 * unreliable on wasmJs. Renaming is offered via the map's manual-add form (DOM text input), which is
 * both mobile-WebKit-safe and avoids pulling the heavy Compose text-editing subsystem into the bundle.
 */
@Suppress("LongMethod")
@Composable
internal fun WebSaveFavoriteDialog(
    currentName: String,
    isFavorite: Boolean,
    otherFavorites: List<SavedPlace>,
    onToggleFavorite: () -> Unit,
    onJumpToFavorite: (SavedPlace) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrimInteraction = remember { MutableInteractionSource() }
    val cardInteraction = remember { MutableInteractionSource() }
    val strings = LocalWebStrings.current
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = SCRIM_ALPHA))
            .dismissibleOverlay(onDismiss)
            .clickable(interactionSource = scrimInteraction, indication = null, onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier
                .widthIn(max = DialogMaxWidth)
                .fillMaxWidth()
                .padding(DialogMargin)
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
                    text = currentName,
                    modifier = Modifier.semantics { heading() },
                    style = MaterialTheme.typography.headlineSmall,
                )
                Button(
                    onClick = {
                        onToggleFavorite()
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (isFavorite) strings.removeFromFavorites else strings.saveToFavorites)
                }
                if (otherFavorites.isNotEmpty()) {
                    Text(
                        text = strings.jumpToAnotherFavorite,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelLarge,
                    )
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = ListMaxHeight)
                            .verticalScroll(rememberScrollState()),
                    ) {
                        otherFavorites.forEachIndexed { index, place ->
                            if (index > 0) HorizontalDivider()
                            FavoriteJumpRow(
                                place = place,
                                onClick = {
                                    onJumpToFavorite(place)
                                    onDismiss()
                                },
                            )
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(ActionSpacing, Alignment.End),
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(strings.actionClose)
                    }
                }
            }
        }
    }
}

@Composable
private fun FavoriteJumpRow(
    place: SavedPlace,
    onClick: () -> Unit,
) {
    val strings = LocalWebStrings.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) {
                contentDescription = strings.jumpToFavoriteContentDescription.replace("%s", place.name)
            }
            .padding(vertical = RowSpacing),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = place.name, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = "${place.latitude}, ${place.longitude}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

private const val SCRIM_ALPHA = 0.5f
private val DialogMaxWidth = 480.dp
private val DialogMargin = 12.dp
private val DialogPadding = 20.dp
private val DialogElevation = 6.dp
private val CornerRadius = 28.dp
private val ListMaxHeight = 260.dp
private val SectionSpacing = 12.dp
private val RowSpacing = 12.dp
private val ActionSpacing = 8.dp

@Preview(name = "Web save favorite dialog", showBackground = true, widthDp = 520, heightDp = 520)
@Composable
private fun WebSaveFavoriteDialogPreview() {
    ForecastPreviewTheme {
        WebSaveFavoriteDialog(
            currentName = "Brauneck",
            isFavorite = true,
            otherFavorites = WebPreviewData.favoritePlaces,
            onToggleFavorite = {},
            onJumpToFavorite = {},
            onDismiss = {},
        )
    }
}
