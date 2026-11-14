package com.cloudbasepredictor.ui.screens.forecast.views

/**
 * Localizable words the wind chart draws on its canvas and exposes in its cursor readout.
 *
 * Shared code has no string resources, so each platform passes its translations; the defaults are
 * English and keep the readout format that tests assert on.
 */
data class WindChartLabels(
    /** Readout value when the selected hour has no wind at the selected height. */
    val noData: String = "No data",
    /** Word for heights above mean sea level, e.g. "2357 m ASL" and the "km ASL" axis title. */
    val aboveSeaLevel: String = "ASL",
    /** Word for heights above ground, e.g. "10 m AGL". */
    val aboveGround: String = "AGL",
    /**
     * Whether [aboveSeaLevel] and [aboveGround] come before the height, as in Chinese, instead of after it
     * ("2357 m ASL", "10 m AGL" and the "km ASL" axis title in English).
     */
    val wordsBeforeHeight: Boolean = false,
) {
    /** [height] ("2357 m", "km") with [word] on the side this language puts it, [separator] between them. */
    internal fun withHeightWord(height: String, word: String, separator: String = " "): String =
        if (wordsBeforeHeight) "$word$separator$height" else "$height$separator$word"
}
