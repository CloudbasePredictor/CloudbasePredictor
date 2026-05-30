package com.cloudbasepredictor.ui.screens.forecast

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.cloudbasepredictor.R
import com.cloudbasepredictor.data.units.DisplayUnits
import com.cloudbasepredictor.data.units.formatVerticalSpeed
import com.cloudbasepredictor.data.units.formatWindSpeed
import com.cloudbasepredictor.model.ForecastMode
import com.cloudbasepredictor.model.ForecastModel
import com.cloudbasepredictor.ui.preview.PreviewData
import com.cloudbasepredictor.ui.screens.forecast.ForecastTestTags.HELP_BUTTON
import com.cloudbasepredictor.ui.theme.CloudbasePredictorTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
internal fun HelpButtonOverlay(
    uiState: ForecastReadyUiState,
    modifier: Modifier = Modifier,
) {
    val helpContent = rememberForecastHelpContent(uiState)
    var isDialogVisible by rememberSaveable { mutableStateOf(false) }

    FloatingActionButton(
        onClick = { isDialogVisible = true },
        modifier = modifier
            .size(48.dp)
            .testTag(HELP_BUTTON),
        containerColor = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Icon(
            imageVector = Icons.Outlined.Info,
            contentDescription = stringResource(R.string.cd_open_forecast_help),
        )
    }

    if (isDialogVisible) {
        AlertDialog(
            onDismissRequest = { isDialogVisible = false },
            title = {
                Text(text = helpContent.title)
            },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    ForecastHelpModelInfo(uiState = uiState)

                    HorizontalDivider()

                    // Mode-specific content with legends
                    when (uiState.selectedForecastMode) {
                        ForecastMode.THERMIC -> {
                            Text(
                                text = helpContent.summary,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            ThermicStrengthLegend(displayUnits = uiState.displayUnits)
                            ThermicDiagnosticLineLegend()
                            Text(
                                text = stringResource(R.string.help_thermic_model_blocks_info),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = stringResource(R.string.help_thermic_cursor_info),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        ForecastMode.STUVE -> {
                            Text(
                                text = helpContent.summary,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            StuveDiagramLegend()
                            StuveControlsHelp()
                        }
                        ForecastMode.WIND -> {
                            Text(
                                text = helpContent.summary,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            WindSpeedLegend(displayUnits = uiState.displayUnits)
                            Spacer(modifier = Modifier.height(4.dp))
                            WindMoistureLegend()
                        }
                        ForecastMode.CLOUD -> {
                            Text(
                                text = helpContent.summary,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            CloudForecastLegend()
                        }
                    }

                    if (helpContent.statusMessage.isNotBlank()) {
                        Text(
                            text = helpContent.statusMessage,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { isDialogVisible = false }) {
                    Text(text = stringResource(R.string.action_close))
                }
            },
        )
    }
}

@Composable
private fun ForecastHelpModelInfo(uiState: ForecastReadyUiState) {
    val resolvedModel = uiState.resolvedModel ?: uiState.selectedModel
    val modelText = if (uiState.resolvedModel != null && uiState.selectedModel != uiState.resolvedModel) {
        "${uiState.selectedModel.displayName} -> ${uiState.resolvedModel.displayName}"
    } else {
        resolvedModel.displayName
    }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        ForecastHelpInfoRow(
            label = stringResource(R.string.help_model_label),
            value = modelText,
        )
        uiState.forecastUpdatedAtMillis?.let { downloadedAtMillis ->
            ForecastHelpInfoRow(
                label = stringResource(R.string.help_data_downloaded_label),
                value = formatHelpDateTime(downloadedAtMillis),
            )
        }
        uiState.modelGeneratedAtMillis?.let { modelRunAtMillis ->
            ForecastHelpInfoRow(
                label = stringResource(R.string.help_model_generated_label),
                value = formatHelpDateTime(modelRunAtMillis),
            )
        }
    }
}

@Composable
private fun ForecastHelpInfoRow(
    label: String,
    value: String,
) {
    Text(
        text = "$label - $value",
        style = MaterialTheme.typography.bodySmall,
        fontWeight = FontWeight.Medium,
    )
}

private fun formatHelpDateTime(timestampMillis: Long): String {
    return SimpleDateFormat("dd.MM.yyyy h:mma", Locale.getDefault()).format(Date(timestampMillis))
}

@Composable
private fun rememberForecastHelpContent(uiState: ForecastReadyUiState): ForecastHelpContent {
    return when (uiState.selectedForecastMode) {
        ForecastMode.THERMIC -> ForecastHelpContent(
            title = stringResource(R.string.help_thermic_title),
            summary = stringResource(R.string.help_thermic_summary)
                .replace("m/s", uiState.displayUnits.verticalSpeed.label),
            statusMessage = forecastStatusMessage(uiState),
        )
        ForecastMode.STUVE -> ForecastHelpContent(
            title = stringResource(R.string.help_stuve_title),
            summary = stringResource(R.string.help_stuve_summary),
            statusMessage = forecastStatusMessage(uiState),
        )
        ForecastMode.WIND -> ForecastHelpContent(
            title = stringResource(R.string.help_wind_title),
            summary = stringResource(R.string.help_wind_summary)
                .replace("km/h", uiState.displayUnits.windSpeed.label),
            statusMessage = forecastStatusMessage(uiState),
        )
        ForecastMode.CLOUD -> ForecastHelpContent(
            title = stringResource(R.string.help_cloud_title),
            summary = stringResource(R.string.help_cloud_summary),
            statusMessage = forecastStatusMessage(uiState),
        )
    }
}

@Composable
private fun forecastStatusMessage(uiState: ForecastReadyUiState): String {
    return when {
        uiState.selectedPlace == null -> stringResource(R.string.help_status_no_place)
        else -> ""
    }
}

@Composable
private fun CloudForecastLegend() {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = stringResource(R.string.help_cloud_legend_sun),
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            text = stringResource(R.string.help_cloud_legend_radiation),
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            text = stringResource(R.string.help_cloud_legend_layers),
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            text = stringResource(R.string.help_cloud_legend_rain),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

private data class ForecastHelpContent(
    val title: String,
    val summary: String,
    val statusMessage: String,
)

@Composable
private fun StuveDiagramLegend() {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = stringResource(R.string.help_stuve_lines_title),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
        )
        ForecastLineLegendRow(
            color = Color(0xFFD83A3A),
            dashOnDp = 0f,
            dashOffDp = 0f,
            label = stringResource(R.string.help_stuve_temperature_line),
        )
        ForecastLineLegendRow(
            color = Color(0xFF2E6FB5),
            dashOnDp = 0f,
            dashOffDp = 0f,
            label = stringResource(R.string.help_stuve_dewpoint_line),
        )
        ForecastLineLegendRow(
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.58f),
            dashOnDp = 8f,
            dashOffDp = 5f,
            label = stringResource(R.string.help_stuve_parcel_line),
        )
        ForecastLineLegendRow(
            color = Color(0xFF59A36A),
            dashOnDp = 10f,
            dashOffDp = 5f,
            label = stringResource(R.string.help_stuve_active_parcel_line),
        )
        ForecastLineLegendRow(
            color = Color(0xFFB36A27),
            dashOnDp = 7f,
            dashOffDp = 4f,
            label = stringResource(R.string.help_stuve_ccl_line),
        )
        Text(
            text = stringResource(R.string.help_stuve_reference_grid),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun StuveControlsHelp() {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = stringResource(R.string.help_stuve_controls_title),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = stringResource(R.string.help_stuve_control_hour),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = stringResource(R.string.help_stuve_control_cursor),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = stringResource(R.string.help_stuve_control_heating_zoom),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ── Thermic strength legend (0 → 5+ m/s) ────────────────────────────────

@Composable
private fun ThermicDiagnosticLineLegend() {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = stringResource(R.string.help_thermic_dashed_lines_title),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
        )
        ForecastLineLegendRow(
            color = Color(0xFFE07020),
            dashOnDp = 0f,
            dashOffDp = 0f,
            label = stringResource(R.string.help_thermic_dry_top_line),
        )
        ForecastLineLegendRow(
            color = Color(0xFF2088E0),
            dashOnDp = 10f,
            dashOffDp = 4f,
            label = stringResource(R.string.help_thermic_cloud_base_line),
        )
        ForecastLineLegendRow(
            color = Color(0xFFA040C0),
            dashOnDp = 4f,
            dashOffDp = 6f,
            label = stringResource(R.string.help_thermic_moist_top_line),
        )
        ForecastLineLegendRow(
            color = MaterialTheme.colorScheme.outline,
            dashOnDp = 2f,
            dashOffDp = 6f,
            label = stringResource(R.string.help_thermic_pressure_levels_line),
        )
    }
}

@Composable
private fun ForecastLineLegendRow(
    color: Color,
    dashOnDp: Float,
    dashOffDp: Float,
    label: String,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Canvas(
            modifier = Modifier
                .width(36.dp)
                .height(12.dp),
        ) {
            drawLine(
                color = color,
                start = Offset(0f, size.height / 2f),
                end = Offset(size.width, size.height / 2f),
                strokeWidth = 2.dp.toPx(),
                cap = StrokeCap.Round,
                pathEffect = if (dashOnDp > 0f && dashOffDp > 0f) {
                    PathEffect.dashPathEffect(
                        floatArrayOf(dashOnDp.dp.toPx(), dashOffDp.dp.toPx()),
                    )
                } else {
                    null
                },
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ThermicStrengthLegend(displayUnits: DisplayUnits) {
    val steps = listOf(0f, 1f, 2f, 3f, 4f, 5f).map { speedMps ->
        val label = formatVerticalSpeed(speedMps, displayUnits, withUnit = false)
        if (speedMps >= THERMIC_STRENGTH_COLOR_SCALE_MAX_MPS) "$label+" else label
    }
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            steps.forEach { value ->
                Text(
                    text = value,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(16.dp)
                .clip(RoundedCornerShape(4.dp)),
        ) {
            // Draw gradient bar using thermic color scale
            val colorCount = 40
            repeat(colorCount) { i ->
                val strength = i.toFloat() / (colorCount - 1) * THERMIC_STRENGTH_COLOR_SCALE_MAX_MPS
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(16.dp)
                        .background(thermicStrengthColor(strength)),
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(text = "weak air lift", style = MaterialTheme.typography.labelSmall)
            Text(text = "scale maximum", style = MaterialTheme.typography.labelSmall)
        }
    }
}

// ── Wind speed legend (0 → 60 km/h) ────────────────────────────────

@Composable
private fun WindMoistureLegend() {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = stringResource(R.string.help_wind_moisture_title),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
        )
        WindCclLegendRow()
    }
}

@Composable
private fun WindCclLegendRow() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Canvas(
            modifier = Modifier
                .width(36.dp)
                .height(12.dp),
        ) {
            drawLine(
                color = Color(0xFFFF8C00),
                start = Offset(0f, size.height / 2f),
                end = Offset(size.width, size.height / 2f),
                strokeWidth = 2.5.dp.toPx(),
                cap = StrokeCap.Round,
            )
        }
        Text(
            text = stringResource(R.string.help_wind_ccl_line),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun WindSpeedLegend(displayUnits: DisplayUnits) {
    val steps = listOf(0, 5, 10, 15, 20, 30, 40, 50, 60)
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            steps.forEach { value ->
                Text(
                    text = formatWindSpeed(value.toFloat(), displayUnits, withUnit = false),
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(16.dp)
                .clip(RoundedCornerShape(4.dp)),
        ) {
            val colorCount = 40
            repeat(colorCount) { i ->
                val speedKmh = i.toFloat() / (colorCount - 1) * WIND_SPEED_COLOR_SCALE_MAX_KMH
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(16.dp)
                        .background(windSpeedColor(speedKmh)),
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(text = "calm", style = MaterialTheme.typography.labelSmall)
            Text(text = "scale maximum", style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Preview(name = "Forecast Help Overlay", showBackground = true)
@Composable
private fun HelpButtonOverlayPreview() {
    CloudbasePredictorTheme {
        HelpButtonOverlay(
            uiState = PreviewData.forecastUiStateForMode(ForecastMode.THERMIC),
        )
    }
}
