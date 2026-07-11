@file:Suppress("FunctionNaming", "LongMethod", "MagicNumber")

package com.cloudbasepredictor.ui.screens.forecast.views

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.cloudbasepredictor.model.ForecastMode
import com.cloudbasepredictor.ui.preview.ForecastPreviewData
import com.cloudbasepredictor.ui.preview.ForecastPreviewTheme
import com.cloudbasepredictor.ui.screens.forecast.ForecastReadyUiState
import com.cloudbasepredictor.ui.screens.forecast.ForecastTestTags.STUVE_SELECTED_HOUR
import com.cloudbasepredictor.ui.screens.forecast.ForecastTestTags.STUVE_TIME_SLIDER
import com.cloudbasepredictor.ui.screens.forecast.ForecastTestTags.STUVE_VIEW
import kotlin.math.abs
import androidx.compose.ui.tooling.preview.Preview

@Composable
fun StuveForecastView(
    uiState: ForecastReadyUiState,
    modifier: Modifier = Modifier,
    onVisibleTopAltitudeChange: (Float) -> Unit = {},
    onStuveHourChanged: (Int) -> Unit = {},
    overlayBackHandler: ForecastOverlayBackHandler = ignoreForecastOverlayBack,
) {
    val stuveChart = uiState.stuveChart
    val chartSessionKey = Triple(
        uiState.selectedPlace?.id,
        uiState.selectedDayIndex,
        uiState.resolvedModel ?: uiState.selectedModel,
    )
    val autoFitTopAltitudeKm = remember(stuveChart.temperatureProfile, stuveChart.windBarbs) {
        recommendedStuveTopAltitudeKm(stuveChart)
    }
    val initialRequestedTopAltitudeKm = remember(chartSessionKey) {
        uiState.chartViewport.visibleTopAltitudeKm
    }
    var effectiveTopAltitudeKm by remember(chartSessionKey) {
        mutableFloatStateOf(
            maxOf(
                uiState.chartViewport.visibleTopAltitudeKm,
                autoFitTopAltitudeKm,
            ),
        )
    }

    LaunchedEffect(chartSessionKey, autoFitTopAltitudeKm) {
        val fittedTopAltitudeKm = maxOf(
            uiState.chartViewport.visibleTopAltitudeKm,
            autoFitTopAltitudeKm,
        )
        effectiveTopAltitudeKm = fittedTopAltitudeKm
        if (abs(uiState.chartViewport.visibleTopAltitudeKm - fittedTopAltitudeKm) > 0.01f) {
            onVisibleTopAltitudeChange(fittedTopAltitudeKm)
        }
    }

    LaunchedEffect(uiState.chartViewport.visibleTopAltitudeKm, chartSessionKey, autoFitTopAltitudeKm) {
        val requestedTopAltitudeKm = uiState.chartViewport.visibleTopAltitudeKm
        val isInitialUnderZoomedRequest =
            abs(requestedTopAltitudeKm - initialRequestedTopAltitudeKm) <= 0.01f &&
                requestedTopAltitudeKm < autoFitTopAltitudeKm - 0.01f
        if (!isInitialUnderZoomedRequest && abs(requestedTopAltitudeKm - effectiveTopAltitudeKm) > 0.01f) {
            effectiveTopAltitudeKm = requestedTopAltitudeKm
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .testTag(STUVE_VIEW),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            StuveDiagramCanvas(
                chart = stuveChart,
                visibleTopAltitudeKm = effectiveTopAltitudeKm,
                displayUnits = uiState.displayUnits,
                onVisibleTopAltitudeChange = { topAltitudeKm ->
                    effectiveTopAltitudeKm = topAltitudeKm
                    onVisibleTopAltitudeChange(topAltitudeKm)
                },
                overlayBackHandler = overlayBackHandler,
                modifier = Modifier.fillMaxSize(),
            )
        }

        StuveTimeSlider(
            selectedHour = stuveChart.selectedHour,
            onHourChanged = onStuveHourChanged,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun StuveTimeSlider(
    selectedHour: Int,
    onHourChanged: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var sliderValue by remember(selectedHour) {
        mutableFloatStateOf(selectedHour.toFloat())
    }

    Column(modifier = modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(STUVE_TIME_SLIDER),
        ) {
            Text(
                text = "06",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Slider(
                value = sliderValue,
                onValueChange = {
                    sliderValue = it
                    onHourChanged(it.toInt())
                },
                valueRange = 6f..22f,
                steps = 15,
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary,
                ),
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "22",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = "${formatPaddedInt(sliderValue.toInt(), minimumDigits = 2)}:00",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .testTag(STUVE_SELECTED_HOUR),
        )
    }
}

@Preview(name = "Stuve Default", showBackground = true, widthDp = 420, heightDp = 720)
@Composable
private fun SkewTForecastViewPreview() {
    ForecastPreviewTheme {
        StuveForecastView(
            uiState = ForecastPreviewData.stateForMode(ForecastMode.STUVE),
        )
    }
}

@Preview(
    name = "Stuve Dark",
    showBackground = true,
    widthDp = 420,
    heightDp = 720,
)
@Composable
private fun SkewTForecastViewDarkPreview() {
    ForecastPreviewTheme(darkTheme = true) {
        StuveForecastView(
            uiState = ForecastPreviewData.stateForMode(ForecastMode.STUVE),
        )
    }
}
