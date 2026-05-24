package com.cloudbasepredictor.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cloudbasepredictor.R
import com.cloudbasepredictor.ui.theme.CloudbasePredictorTheme

private val MapAttributionMinFontSize = 6.sp
private val MapAttributionAutoSizeStep = 0.25.sp

@Composable
fun MapAttributionOverlay(
    text: String = stringResource(R.string.map_attribution_compact),
    detailText: String? = null,
    modifier: Modifier = Modifier,
) {
    var showDetails by rememberSaveable { mutableStateOf(false) }
    val clickableModifier = if (detailText != null) {
        Modifier.clickable { showDetails = true }
    } else {
        Modifier
    }

    Surface(
        modifier = modifier
            .widthIn(max = 360.dp)
            .testTag(MapTestTags.ATTRIBUTION_OVERLAY)
            .then(clickableModifier),
        shape = RoundedCornerShape(4.dp),
        color = Color.White.copy(alpha = 0.68f),
        contentColor = Color(0xFF202124),
        tonalElevation = 1.dp,
    ) {
        val textStyle = MaterialTheme.typography.labelSmall
        Text(
            text = text,
            style = textStyle,
            autoSize = TextAutoSize.StepBased(
                minFontSize = MapAttributionMinFontSize,
                maxFontSize = textStyle.fontSize,
                stepSize = MapAttributionAutoSizeStep,
            ),
            maxLines = 1,
            overflow = TextOverflow.Clip,
            softWrap = false,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
        )
    }

    if (showDetails && detailText != null) {
        AlertDialog(
            onDismissRequest = { showDetails = false },
            title = { Text(text = stringResource(R.string.map_attribution_title)) },
            text = { Text(text = detailText) },
            confirmButton = {
                TextButton(onClick = { showDetails = false }) {
                    Text(text = stringResource(R.string.action_close))
                }
            },
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun MapAttributionOverlayPreview() {
    CloudbasePredictorTheme {
        MapAttributionOverlay()
    }
}
