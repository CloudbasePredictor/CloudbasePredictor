@file:Suppress("FunctionNaming")

package com.cloudbasepredictor.ui.screens.forecast.views

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.cloudbasepredictor.ui.preview.ForecastPreviewData
import com.cloudbasepredictor.ui.preview.ForecastPreviewTheme
import androidx.compose.ui.tooling.preview.Preview

/** Platform hook used by a chart to dismiss its transient cursor before navigating back. */
typealias ForecastOverlayBackHandler = @Composable (enabled: Boolean, onBack: () -> Unit) -> Unit

/** Default for platforms whose navigation shell does not expose a back event to shared UI. */
val ignoreForecastOverlayBack: ForecastOverlayBackHandler = { _, _ -> }

@Preview(showBackground = true)
@Composable
private fun ForecastOverlayBackHandlerPreview() {
    ForecastPreviewTheme {
        Text(text = ForecastPreviewData.readyState.forecastText)
    }
}
