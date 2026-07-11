@file:Suppress("FunctionNaming")

package com.cloudbasepredictor.ui.screens.forecast.views

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.cloudbasepredictor.ui.preview.ForecastPreviewData
import com.cloudbasepredictor.ui.preview.ForecastPreviewTheme
import androidx.compose.ui.tooling.preview.Preview

@Composable
fun ForecastInformationView(
    message: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(32.dp),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ForecastInformationViewPreview() {
    ForecastPreviewTheme {
        ForecastInformationView(
            message = ForecastPreviewData.noThermalsMessage,
        )
    }
}

@Preview(
    showBackground = true,
)
@Composable
private fun ForecastInformationViewDarkPreview() {
    ForecastPreviewTheme(darkTheme = true) {
        ForecastInformationView(
            message = ForecastPreviewData.noThermalsMessage,
        )
    }
}
