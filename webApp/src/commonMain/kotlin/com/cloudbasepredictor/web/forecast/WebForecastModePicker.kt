@file:Suppress("FunctionNaming")

package com.cloudbasepredictor.web.forecast

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Air
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.cloudbasepredictor.model.ForecastMode
import com.cloudbasepredictor.ui.preview.ForecastPreviewTheme

/**
 * Icon segmented control mirroring the Android forecast mode picker. Display order and icons match
 * Android exactly (Stüve → Wind → Thermic → Cloud); the per-item accessibility contract
 * (`Role.RadioButton` plus a "<mode> selected/not selected" name) is unchanged from the previous
 * text chips so the shareable `view=` route values and release gates keep working.
 */
private val WebForecastModeOrder = listOf(
    ForecastMode.STUVE,
    ForecastMode.WIND,
    ForecastMode.THERMIC,
    ForecastMode.CLOUD,
)

@Composable
internal fun WebForecastModePicker(
    selectedMode: ForecastMode,
    onModeSelected: (ForecastMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            WebForecastModeOrder.forEach { mode ->
                WebForecastModeItem(
                    mode = mode,
                    selected = mode == selectedMode,
                    onClick = { onModeSelected(mode) },
                )
            }
        }
    }
}

@Composable
private fun WebForecastModeItem(
    mode: ForecastMode,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val backgroundColor = if (selected) {
        MaterialTheme.colorScheme.secondaryContainer
    } else {
        Color.Transparent
    }
    val tintColor = if (selected) {
        MaterialTheme.colorScheme.onSecondaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(backgroundColor)
            .selectable(
                selected = selected,
                role = Role.RadioButton,
                onClick = onClick,
            )
            .semantics(mergeDescendants = true) {
                contentDescription = if (selected) {
                    "${mode.webLabel} selected"
                } else {
                    "${mode.webLabel} not selected"
                }
            }
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Icon(
            imageVector = mode.pickerIcon,
            contentDescription = null,
            tint = tintColor,
        )
    }
}

private val ForecastMode.pickerIcon: ImageVector
    get() = when (this) {
        ForecastMode.STUVE -> Icons.Outlined.WbSunny
        ForecastMode.WIND -> Icons.Outlined.Air
        ForecastMode.THERMIC -> Icons.Outlined.ArrowUpward
        ForecastMode.CLOUD -> Icons.Outlined.Cloud
    }

@Preview(name = "Web forecast mode picker", showBackground = true)
@Composable
private fun WebForecastModePickerPreview() {
    ForecastPreviewTheme {
        WebForecastModePicker(
            selectedMode = ForecastMode.STUVE,
            onModeSelected = {},
        )
    }
}
