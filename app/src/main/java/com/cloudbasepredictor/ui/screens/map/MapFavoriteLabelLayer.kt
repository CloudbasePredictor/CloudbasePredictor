package com.cloudbasepredictor.ui.screens.map

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cloudbasepredictor.model.SavedPlace
import org.maplibre.compose.expressions.dsl.const
import org.maplibre.compose.expressions.dsl.image
import org.maplibre.compose.expressions.value.SymbolAnchor
import org.maplibre.compose.layers.SymbolLayer
import org.maplibre.compose.sources.GeoJsonData
import org.maplibre.compose.sources.rememberGeoJsonSource
import org.maplibre.compose.util.MaplibreComposable

/**
 * Renders favorite place names as native symbol layers.
 *
 * Each label is drawn into a self-contained icon bitmap (text on a rounded,
 * theme-aware "pill"). Using an icon rather than a `textField` avoids depending
 * on a style `glyphs` source — the raster base layers (topo/satellite) use an
 * empty style with no glyphs, so native text would not render at all. Drawing
 * the labels inside the map (instead of as a Compose overlay) also makes them
 * pan and zoom in perfect sync with the base map instead of lagging behind, and
 * the pill keeps the names legible on every base layer.
 *
 * @param places favorites to label.
 * @param markerRadius radius of the marker icon the label sits under.
 * @param idPrefix unique layer-id prefix (each map needs distinct layer ids).
 */
@Composable
@MaplibreComposable
internal fun FavoriteLabelsLayer(
    places: List<SavedPlace>,
    markerRadius: Dp,
    idPrefix: String,
    textSize: TextUnit = FAVORITE_LABEL_TEXT_SIZE,
) {
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val textColor = MaterialTheme.colorScheme.onSurface
    val pillFill = MaterialTheme.colorScheme.surface.copy(alpha = FAVORITE_LABEL_PILL_ALPHA)
    val pillBorder = MaterialTheme.colorScheme.outlineVariant.copy(alpha = FAVORITE_LABEL_BORDER_ALPHA)

    places.forEach { place ->
        key(place.id) {
            FavoriteLabel(
                place = place,
                markerRadius = markerRadius,
                layerId = "$idPrefix-${place.id}",
                textSize = textSize,
                textColor = textColor,
                pillFill = pillFill,
                pillBorder = pillBorder,
                measurer = measurer,
                density = density,
            )
        }
    }
}

@Composable
@MaplibreComposable
private fun FavoriteLabel(
    place: SavedPlace,
    markerRadius: Dp,
    layerId: String,
    textSize: TextUnit,
    textColor: Color,
    pillFill: Color,
    pillBorder: Color,
    measurer: TextMeasurer,
    density: Density,
) {
    val source = rememberGeoJsonSource(
        data = GeoJsonData.JsonString(buildMarkerFeatureCollection(place)),
    )

    val painter = remember(place.name, textColor, textSize, pillFill, pillBorder, density) {
        val textStyle = TextStyle(color = textColor, fontSize = textSize)
        val maxWidthPx = with(density) { FAVORITE_LABEL_MAX_WIDTH.toPx().toInt() }
        val layout = measurer.measure(
            text = place.name,
            style = textStyle,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            constraints = Constraints(maxWidth = maxWidthPx),
        )
        FavoriteLabelPainter(
            textLayout = layout,
            fill = pillFill,
            border = pillBorder,
            padH = with(density) { FAVORITE_LABEL_PILL_PADDING_H.toPx() },
            padV = with(density) { FAVORITE_LABEL_PILL_PADDING_V.toPx() },
        )
    }

    val sizeDp = with(density) {
        DpSize(painter.intrinsicSize.width.toDp(), painter.intrinsicSize.height.toDp())
    }

    SymbolLayer(
        id = layerId,
        source = source,
        iconImage = image(value = painter, size = sizeDp),
        iconAnchor = const(SymbolAnchor.Top),
        iconOffset = const(DpOffset(0.dp, markerRadius + FAVORITE_LABEL_GAP)),
        iconAllowOverlap = const(true),
        iconIgnorePlacement = const(true),
    )
}

/** Draws a favorite label (rounded pill background + centered text) into a bitmap. */
private class FavoriteLabelPainter(
    private val textLayout: TextLayoutResult,
    private val fill: Color,
    private val border: Color,
    private val padH: Float,
    private val padV: Float,
) : Painter() {
    override val intrinsicSize: Size = Size(
        width = textLayout.size.width + padH * 2f,
        height = textLayout.size.height + padV * 2f,
    )

    override fun DrawScope.onDraw() {
        val radius = CornerRadius(size.height / 2f)
        drawRoundRect(color = fill, cornerRadius = radius)
        drawRoundRect(
            color = border,
            cornerRadius = radius,
            style = Stroke(width = size.minDimension * FAVORITE_LABEL_BORDER_FRACTION),
        )
        drawText(
            textLayoutResult = textLayout,
            topLeft = Offset(
                x = (size.width - textLayout.size.width) / 2f,
                y = (size.height - textLayout.size.height) / 2f,
            ),
        )
    }
}

private val FAVORITE_LABEL_TEXT_SIZE = 10.sp
private val FAVORITE_LABEL_MAX_WIDTH = 120.dp
private val FAVORITE_LABEL_PILL_PADDING_H = 7.dp
private val FAVORITE_LABEL_PILL_PADDING_V = 3.dp
// Gap between the bottom of the marker icon and the top of the label pill.
private val FAVORITE_LABEL_GAP = 2.dp
private const val FAVORITE_LABEL_PILL_ALPHA = 0.85f
private const val FAVORITE_LABEL_BORDER_ALPHA = 0.6f
private const val FAVORITE_LABEL_BORDER_FRACTION = 0.04f
