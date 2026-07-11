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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.cloudbasepredictor.model.ForecastModel
import com.cloudbasepredictor.ui.preview.ForecastPreviewTheme

// Display order mirrors the Android model selector: the "Best Effort" auto-match is listed last.
private val WebForecastModelOrder: List<ForecastModel> =
    ForecastModel.entries.filterNot { it == ForecastModel.BEST_MATCH } + ForecastModel.BEST_MATCH

/**
 * Pill button opening the forecast-model selector, mirroring Android's `ModelSelectorOverlay`
 * (pill + bottom sheet). Shows "Selected (Resolved)" when the requested model resolved to a
 * different one via the fallback chain.
 */
@Composable
internal fun WebForecastModelPill(
    selectedModel: ForecastModel,
    resolvedModel: ForecastModel?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val resolvedSuffix = resolvedModel
        ?.takeIf { it != selectedModel }
        ?.let { " (resolved: ${it.displayName})" }
        .orEmpty()
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.semantics {
            contentDescription = "Forecast model: ${selectedModel.displayName}$resolvedSuffix"
        },
    ) {
        Text("Model: ${selectedModel.displayName}$resolvedSuffix")
    }
}

@Suppress("LongMethod")
@Composable
internal fun WebForecastModelSheet(
    selectedModel: ForecastModel,
    onModelSelected: (ForecastModel) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrimInteraction = remember { MutableInteractionSource() }
    val cardInteraction = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = SCRIM_ALPHA))
            .clickable(interactionSource = scrimInteraction, indication = null, onClick = onDismiss),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Surface(
            modifier = Modifier
                .widthIn(max = SheetMaxWidth)
                .fillMaxWidth()
                .padding(SheetMargin)
                .clickable(interactionSource = cardInteraction, indication = null, onClick = {}),
            shape = RoundedCornerShape(CornerRadius),
            tonalElevation = SheetElevation,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Column(
                modifier = Modifier.padding(SheetPadding),
                verticalArrangement = Arrangement.spacedBy(SectionSpacing),
            ) {
                Text(
                    text = "Forecast model",
                    modifier = Modifier.semantics { heading() },
                    style = MaterialTheme.typography.headlineSmall,
                )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = ListMaxHeight)
                        .verticalScroll(rememberScrollState()),
                ) {
                    WebForecastModelOrder.forEachIndexed { index, model ->
                        if (index > 0) HorizontalDivider()
                        ModelRow(
                            model = model,
                            selected = model == selectedModel,
                            onClick = {
                                onModelSelected(model)
                                onDismiss()
                            },
                        )
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
private fun ModelRow(
    model: ForecastModel,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .semantics(mergeDescendants = true) {
                contentDescription = if (selected) {
                    "Forecast model option: ${model.displayName} selected"
                } else {
                    "Forecast model option: ${model.displayName} not selected"
                }
            }
            .padding(vertical = RowSpacing),
        horizontalArrangement = Arrangement.spacedBy(RowSpacing),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = model.displayName,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
            )
            Text(
                text = model.description,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

private const val SCRIM_ALPHA = 0.5f
private val SheetMaxWidth = 560.dp
private val SheetMargin = 12.dp
private val SheetPadding = 20.dp
private val SheetElevation = 6.dp
private val CornerRadius = 28.dp
private val ListMaxHeight = 420.dp
private val SectionSpacing = 12.dp
private val RowSpacing = 12.dp
private val ActionSpacing = 8.dp

@Preview(name = "Web forecast model sheet", showBackground = true, widthDp = 600, heightDp = 720)
@Composable
private fun WebForecastModelSheetPreview() {
    ForecastPreviewTheme {
        WebForecastModelSheet(
            selectedModel = ForecastModel.ICON_SEAMLESS,
            onModelSelected = {},
            onDismiss = {},
        )
    }
}
