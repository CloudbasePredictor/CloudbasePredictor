@file:Suppress("MatchingDeclarationName")

package com.cloudbasepredictor.ui.screens.forecast.views

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import kotlin.math.abs
import kotlin.math.roundToLong

internal enum class CanvasTextAnchor {
    START,
    CENTER,
    END,
}

internal fun TextMeasurer.measureCanvasText(
    text: String,
    style: TextStyle,
): TextLayoutResult = measure(
    text = text,
    style = style,
    maxLines = 1,
    softWrap = false,
)

/** Draws one line while retaining the baseline-based positioning used by the forecast charts. */
internal fun DrawScope.drawCanvasText(
    textMeasurer: TextMeasurer,
    text: String,
    x: Float,
    baselineY: Float,
    style: TextStyle,
    anchor: CanvasTextAnchor = CanvasTextAnchor.START,
): TextLayoutResult {
    val layout = textMeasurer.measureCanvasText(text, style)
    val left = when (anchor) {
        CanvasTextAnchor.START -> x
        CanvasTextAnchor.CENTER -> x - layout.size.width / 2f
        CanvasTextAnchor.END -> x - layout.size.width
    }
    drawText(
        textLayoutResult = layout,
        topLeft = Offset(left, baselineY - layout.firstBaseline),
    )
    return layout
}

internal fun formatFixedDecimal(
    value: Float,
    fractionDigits: Int,
    alwaysShowSign: Boolean = false,
    minimumIntegerDigits: Int = 1,
): String {
    require(fractionDigits >= 0)
    require(minimumIntegerDigits >= 1)
    if (!value.isFinite()) return value.toString()

    var scale = 1L
    repeat(fractionDigits) { scale *= DECIMAL_RADIX }
    val scaled = (abs(value.toDouble()) * scale).roundToLong()
    val whole = scaled / scale
    val fraction = scaled % scale
    val sign = when {
        value.toBits() < 0 -> "-"
        alwaysShowSign -> "+"
        else -> ""
    }
    val wholeText = whole.toString().padStart(minimumIntegerDigits, '0')
    return if (fractionDigits == 0) {
        "$sign$wholeText"
    } else {
        "$sign$wholeText.${fraction.toString().padStart(fractionDigits, '0')}"
    }
}

internal fun formatPaddedInt(
    value: Int,
    minimumDigits: Int,
): String {
    require(minimumDigits >= 1)
    val sign = if (value < 0) "-" else ""
    val magnitude = if (value == Int.MIN_VALUE) {
        -(value.toLong())
    } else {
        kotlin.math.abs(value).toLong()
    }
    return sign + magnitude.toString().padStart(minimumDigits, '0')
}

private const val DECIMAL_RADIX = 10L
