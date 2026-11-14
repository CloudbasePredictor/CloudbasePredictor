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
    /** Suffix for heights above mean sea level, e.g. "2357 m ASL" and the "km ASL" axis title. */
    val aboveSeaLevel: String = "ASL",
    /** Suffix for heights above ground, e.g. "10 m AGL". */
    val aboveGround: String = "AGL",
)
