package com.cloudbasepredictor.ui.screens.forecast

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import com.cloudbasepredictor.R
import com.cloudbasepredictor.model.ForecastMode
import com.cloudbasepredictor.ui.preview.PreviewData
import com.cloudbasepredictor.ui.theme.CloudbasePredictorTheme

@Composable
internal fun ForecastTopBar(
    placeName: String?,
    isFavorite: Boolean,
    selectedMode: ForecastMode,
    onModeSelected: (ForecastMode) -> Unit,
    onFavoriteClick: () -> Unit,
    onOpenMap: () -> Unit,
) {
    val density = LocalDensity.current
    val minimumTitleWidth = with(density) { MINIMUM_TITLE_WIDTH_PX.toDp() }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .heightIn(min = 64.dp)
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clickable(onClick = onFavoriteClick),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(
                    imageVector = if (isFavorite) Icons.Filled.Star else Icons.Outlined.StarBorder,
                    contentDescription = stringResource(R.string.cd_favorites),
                    modifier = Modifier.size(18.dp),
                )

                AutoScalingTitle(
                    text = placeName ?: stringResource(R.string.title_forecast),
                    modifier = Modifier
                        .weight(1f)
                        .widthIn(min = minimumTitleWidth),
                )
            }

            ForecastModePicker(
                selectedMode = selectedMode,
                onModeSelected = onModeSelected,
            )

            IconButton(onClick = onOpenMap) {
                Icon(
                    imageVector = Icons.Outlined.Map,
                    contentDescription = stringResource(R.string.cd_open_map),
                )
            }
        }
    }
}

@Composable
private fun AutoScalingTitle(
    text: String,
    modifier: Modifier = Modifier,
) {
    val baseStyle = MaterialTheme.typography.titleLarge
    val textMeasurer = rememberTextMeasurer()

    BoxWithConstraints(modifier = modifier) {
        val density = LocalDensity.current
        val availableWidthPx = with(density) { maxWidth.toPx() }.coerceAtLeast(1f)
        val measuredWidthPx = remember(text, baseStyle, availableWidthPx) {
            textMeasurer.measure(
                text = AnnotatedString(text),
                style = baseStyle,
                maxLines = 1,
                softWrap = false,
            ).size.width.toFloat().coerceAtLeast(1f)
        }
        val scale = (availableWidthPx / measuredWidthPx).coerceAtMost(1f)

        Text(
            text = text,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Clip,
            style = baseStyle.scaled(scale),
        )
    }
}

private fun TextStyle.scaled(scale: Float): TextStyle {
    return copy(
        fontSize = fontSize.scaled(scale),
        lineHeight = lineHeight.scaled(scale),
    )
}

private fun TextUnit.scaled(scale: Float): TextUnit {
    return if (this == TextUnit.Unspecified) {
        this
    } else {
        this * scale
    }
}

private const val MINIMUM_TITLE_WIDTH_PX = 300f

@Preview(showBackground = true)
@Composable
private fun ForecastTopBarPreview() {
    CloudbasePredictorTheme {
        ForecastTopBar(
            placeName = "${PreviewData.savedPlace.latitude}, ${PreviewData.savedPlace.longitude}",
            isFavorite = false,
            selectedMode = ForecastMode.THERMIC,
            onModeSelected = {},
            onFavoriteClick = {},
            onOpenMap = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ForecastTopBarFavoritePreview() {
    CloudbasePredictorTheme {
        ForecastTopBar(
            placeName = PreviewData.savedPlace.name,
            isFavorite = PreviewData.savedPlace.isFavorite,
            selectedMode = ForecastMode.THERMIC,
            onModeSelected = {},
            onFavoriteClick = {},
            onOpenMap = {},
        )
    }
}
