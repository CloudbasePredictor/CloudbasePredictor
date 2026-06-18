package com.cloudbasepredictor.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cloudbasepredictor.model.SavedPlace
import com.cloudbasepredictor.ui.preview.PreviewData
import com.cloudbasepredictor.ui.theme.CloudbasePredictorTheme
import org.maplibre.compose.camera.CameraState
import org.maplibre.spatialk.geojson.Position

private val FavoriteLabelMaxWidth = 120.dp
private val FavoriteLabelShape: Shape = RoundedCornerShape(6.dp)

@Composable
fun MapFavoriteLabelsOverlay(
    favoritePlaces: List<SavedPlace>,
    cameraState: CameraState,
    markerRadius: Dp,
    fontSize: TextUnit,
    modifier: Modifier = Modifier,
) {
    val projection = cameraState.projection ?: return
    val cameraPosition = cameraState.position
    var mapSize by remember { mutableStateOf(IntSize.Zero) }

    val labelPositions = remember(favoritePlaces, projection, cameraPosition, mapSize) {
        favoritePlaces.mapNotNull { place ->
            val screenOffset = runCatching {
                projection.screenLocationFromPosition(
                    Position(longitude = place.longitude, latitude = place.latitude),
                )
            }.getOrNull() ?: return@mapNotNull null

            FavoriteLabelPosition(
                name = place.name,
                screenOffset = screenOffset,
            )
        }
    }

    FavoriteLabelsOverlayContent(
        labels = labelPositions,
        markerRadius = markerRadius,
        fontSize = fontSize,
        mapSize = mapSize,
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { mapSize = it },
    )
}

@Composable
private fun FavoriteLabelsOverlayContent(
    labels: List<FavoriteLabelPosition>,
    markerRadius: Dp,
    fontSize: TextUnit,
    mapSize: IntSize,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val mapWidth = with(density) { mapSize.width.toDp() }
    val mapHeight = with(density) { mapSize.height.toDp() }

    Box(
        modifier = modifier,
    ) {
        labels
            .filter { label ->
                label.screenOffset.x >= -FavoriteLabelMaxWidth &&
                    label.screenOffset.x <= mapWidth + FavoriteLabelMaxWidth &&
                    label.screenOffset.y >= -markerRadius &&
                    label.screenOffset.y <= mapHeight + FavoriteLabelMaxWidth
            }
            .forEach { label ->
                FavoriteLabel(
                    label = label,
                    markerRadius = markerRadius,
                    fontSize = fontSize,
                )
            }
    }
}

/**
 * A single favorite name rendered as a translucent, theme-aware pill so the text
 * stays legible across every map layer (street, topo and satellite imagery).
 *
 * The pill wraps its text (capped at [FavoriteLabelMaxWidth]) and is centered
 * under the marker using its measured width.
 */
@Composable
private fun FavoriteLabel(
    label: FavoriteLabelPosition,
    markerRadius: Dp,
    fontSize: TextUnit,
) {
    val density = LocalDensity.current
    var labelWidth by remember { mutableStateOf(0) }
    val halfWidth = with(density) { (labelWidth / 2).toDp() }

    Text(
        text = label.name,
        modifier = Modifier
            .widthIn(max = FavoriteLabelMaxWidth)
            .offset(
                x = label.screenOffset.x - halfWidth,
                y = label.screenOffset.y + markerRadius + 2.dp,
            )
            .onSizeChanged { labelWidth = it.width }
            .clip(FavoriteLabelShape)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.80f))
            .border(
                width = Dp.Hairline,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                shape = FavoriteLabelShape,
            )
            .padding(horizontal = 7.dp, vertical = 2.dp),
        style = MaterialTheme.typography.labelSmall.merge(
            TextStyle(
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = fontSize,
            )
        ),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        textAlign = TextAlign.Center,
    )
}

private data class FavoriteLabelPosition(
    val name: String,
    val screenOffset: DpOffset,
)

@Preview(showBackground = true, widthDp = 360, heightDp = 240)
@Composable
private fun MapFavoriteLabelsOverlayPreview() {
    CloudbasePredictorTheme {
        FavoriteLabelsOverlayContent(
            labels = PreviewData.favoritePlaces.mapIndexed { index, place ->
                FavoriteLabelPosition(
                    name = place.name,
                    screenOffset = DpOffset(
                        x = (92 + index * 72).dp,
                        y = (72 + index * 34).dp,
                    ),
                )
            },
            markerRadius = 8.dp,
            fontSize = 10.sp,
            mapSize = IntSize(width = 360, height = 240),
            modifier = Modifier.fillMaxSize(),
        )
    }
}
