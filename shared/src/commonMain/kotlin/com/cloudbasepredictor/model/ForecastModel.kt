package com.cloudbasepredictor.model

/**
 * Weather model available through the Open-Meteo API.
 *
 * @property apiName Value passed to the `models` query parameter.
 * @property displayName Human-readable label shown in the UI.
 * @property description Short description of resolution and coverage.
 * @property availableForecastDays Maximum forecast horizon typically exposed by this model.
 * @property fallback Next model to try when this one is unavailable for the requested location.
 *                    `null` means the model is always available (global).
 */
enum class ForecastModel(
    val apiName: String,
    val displayName: String,
    val description: String,
    val availableForecastDays: Int,
    val fallback: ForecastModel?,
    /** Typical update interval for this model, in milliseconds. */
    val updateIntervalMillis: Long,
) {
    /** Open-Meteo best-match: automatic model selection for the location. */
    BEST_MATCH(
        apiName = "best_match",
        displayName = "Best Effort",
        description = "Auto-selected (default)",
        availableForecastDays = 7,
        fallback = null,
        updateIntervalMillis = 3 * 3_600_000L, // 3 hours
    ),

    /** DWD ICON Seamless: auto-blends D2 → EU → Global (recommended default). */
    ICON_SEAMLESS(
        apiName = "icon_seamless",
        displayName = "ICON Seamless",
        description = "DWD blend (D2→EU→Global)",
        availableForecastDays = 7,
        fallback = null,
        updateIntervalMillis = 3 * 3_600_000L, // 3 hours
    ),

    /** ECMWF IFS: 9 km, global, ~10 days. */
    ECMWF_IFS(
        apiName = "ecmwf_ifs025",
        displayName = "ECMWF IFS",
        description = "9 km, Global, 10 days",
        availableForecastDays = 10,
        fallback = null,
        updateIntervalMillis = 6 * 3_600_000L, // 6 hours
    ),

    /** NCEP GFS Seamless: auto-blends HRRR → GFS, ~16 days. */
    GFS_SEAMLESS(
        apiName = "gfs_seamless",
        displayName = "GFS Seamless",
        description = "NCEP blend (HRRR→GFS), 16 days",
        availableForecastDays = 16,
        fallback = null,
        updateIntervalMillis = 6 * 3_600_000L, // 6 hours
    ),

    /** Météo-France AROME: 1.3 km, France + neighbours, ~2 days. */
    METEOFRANCE_AROME(
        apiName = "meteofrance_arome_france_hd",
        displayName = "AROME HD",
        description = "1.3 km, France, 2 days",
        availableForecastDays = 2,
        fallback = null,
        updateIntervalMillis = 6 * 3_600_000L, // 6 hours
    ),

    /** Météo-France ARPEGE: 11 km Europe / 25 km global, ~4 days. */
    METEOFRANCE_ARPEGE(
        apiName = "meteofrance_arpege_europe",
        displayName = "ARPEGE EU",
        description = "11 km, Europe, 4 days",
        availableForecastDays = 4,
        fallback = null,
        updateIntervalMillis = 6 * 3_600_000L, // 6 hours
    ),

    /** DWD ICON-D2: 2 km, Central Europe, ~2 days. */
    ICON_D2(
        apiName = "icon_d2",
        displayName = "ICON D2",
        description = "2 km, Central Europe, 2 days",
        availableForecastDays = 2,
        fallback = null,
        updateIntervalMillis = 3 * 3_600_000L, // 3 hours
    ),

    /** DWD ICON-EU: 7 km, Europe, ~5 days. */
    ICON_EU(
        apiName = "icon_eu",
        displayName = "ICON EU",
        description = "7 km, Europe, 5 days",
        availableForecastDays = 5,
        fallback = null,
        updateIntervalMillis = 6 * 3_600_000L, // 6 hours
    ),

    /** DWD ICON Global: 11 km, worldwide, ~7 days. */
    ICON_GLOBAL(
        apiName = "icon_global",
        displayName = "ICON Global",
        description = "11 km, Global, 7 days",
        availableForecastDays = 7,
        fallback = null,
        updateIntervalMillis = 6 * 3_600_000L, // 6 hours
    );

    companion object {
        /**
         * Fallback chain: ICON D2 → ICON EU → ICON Global → BEST_MATCH.
         * Separate chains for AROME, ECMWF, and GFS lead to BEST_MATCH as ultimate fallback.
         */
        val FALLBACK_CHAINS: Map<ForecastModel, ForecastModel> = mapOf(
            ICON_D2 to ICON_EU,
            ICON_EU to ICON_GLOBAL,
            ICON_GLOBAL to BEST_MATCH,
            ICON_SEAMLESS to BEST_MATCH,
            METEOFRANCE_AROME to METEOFRANCE_ARPEGE,
            METEOFRANCE_ARPEGE to BEST_MATCH,
            ECMWF_IFS to BEST_MATCH,
            GFS_SEAMLESS to BEST_MATCH,
        )

        fun fallbackFor(model: ForecastModel): ForecastModel {
            return FALLBACK_CHAINS[model] ?: BEST_MATCH
        }

        fun fromApiName(apiName: String): ForecastModel? {
            return entries.firstOrNull { it.apiName == apiName }
        }
    }
}
