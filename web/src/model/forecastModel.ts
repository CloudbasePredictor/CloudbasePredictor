/**
 * Weather model available through the Open-Meteo API.
 *
 * Ported 1:1 from `model/ForecastModel.kt`. The Kotlin enum becomes a set of
 * immutable descriptor objects keyed by a stable id, plus the fallback-chain
 * lookup helpers.
 */

export type ForecastModelId =
  | "BEST_MATCH"
  | "ICON_SEAMLESS"
  | "ECMWF_IFS"
  | "GFS_SEAMLESS"
  | "METEOFRANCE_AROME"
  | "METEOFRANCE_ARPEGE"
  | "ICON_D2"
  | "ICON_EU"
  | "ICON_GLOBAL";

export interface ForecastModel {
  /** Stable identifier, matching the Kotlin enum constant name. */
  readonly id: ForecastModelId;
  /** Value passed to the `models` query parameter. */
  readonly apiName: string;
  /** Human-readable label shown in the UI. */
  readonly displayName: string;
  /** Short description of resolution and coverage. */
  readonly description: string;
  /** Maximum forecast horizon typically exposed by this model. */
  readonly availableForecastDays: number;
  /**
   * Next model to try when this one is unavailable for the requested location.
   * `null` means the model is always available (global). The Kotlin source keeps
   * this `null` for every entry and expresses the real chain in
   * {@link FALLBACK_CHAINS}.
   */
  readonly fallback: ForecastModelId | null;
  /** Typical update interval for this model, in milliseconds. */
  readonly updateIntervalMillis: number;
}

const HOUR_MILLIS = 3_600_000;

export const FORECAST_MODELS: Record<ForecastModelId, ForecastModel> = {
  /** Open-Meteo best-match: automatic model selection for the location. */
  BEST_MATCH: {
    id: "BEST_MATCH",
    apiName: "best_match",
    displayName: "Best Effort",
    description: "Auto-selected (default)",
    availableForecastDays: 7,
    fallback: null,
    updateIntervalMillis: 3 * HOUR_MILLIS,
  },
  /** DWD ICON Seamless: auto-blends D2 -> EU -> Global (recommended default). */
  ICON_SEAMLESS: {
    id: "ICON_SEAMLESS",
    apiName: "icon_seamless",
    displayName: "ICON Seamless",
    description: "DWD blend (D2->EU->Global)",
    availableForecastDays: 7,
    fallback: null,
    updateIntervalMillis: 3 * HOUR_MILLIS,
  },
  /** ECMWF IFS: 9 km, global, ~10 days. */
  ECMWF_IFS: {
    id: "ECMWF_IFS",
    apiName: "ecmwf_ifs025",
    displayName: "ECMWF IFS",
    description: "9 km, Global, 10 days",
    availableForecastDays: 10,
    fallback: null,
    updateIntervalMillis: 6 * HOUR_MILLIS,
  },
  /** NCEP GFS Seamless: auto-blends HRRR -> GFS, ~16 days. */
  GFS_SEAMLESS: {
    id: "GFS_SEAMLESS",
    apiName: "gfs_seamless",
    displayName: "GFS Seamless",
    description: "NCEP blend (HRRR->GFS), 16 days",
    availableForecastDays: 16,
    fallback: null,
    updateIntervalMillis: 6 * HOUR_MILLIS,
  },
  /** Meteo-France AROME: 1.3 km, France + neighbours, ~2 days. */
  METEOFRANCE_AROME: {
    id: "METEOFRANCE_AROME",
    apiName: "meteofrance_arome_france_hd",
    displayName: "AROME HD",
    description: "1.3 km, France, 2 days",
    availableForecastDays: 2,
    fallback: null,
    updateIntervalMillis: 6 * HOUR_MILLIS,
  },
  /** Meteo-France ARPEGE: 11 km Europe / 25 km global, ~4 days. */
  METEOFRANCE_ARPEGE: {
    id: "METEOFRANCE_ARPEGE",
    apiName: "meteofrance_arpege_europe",
    displayName: "ARPEGE EU",
    description: "11 km, Europe, 4 days",
    availableForecastDays: 4,
    fallback: null,
    updateIntervalMillis: 6 * HOUR_MILLIS,
  },
  /** DWD ICON-D2: 2 km, Central Europe, ~2 days. */
  ICON_D2: {
    id: "ICON_D2",
    apiName: "icon_d2",
    displayName: "ICON D2",
    description: "2 km, Central Europe, 2 days",
    availableForecastDays: 2,
    fallback: null,
    updateIntervalMillis: 3 * HOUR_MILLIS,
  },
  /** DWD ICON-EU: 7 km, Europe, ~5 days. */
  ICON_EU: {
    id: "ICON_EU",
    apiName: "icon_eu",
    displayName: "ICON EU",
    description: "7 km, Europe, 5 days",
    availableForecastDays: 5,
    fallback: null,
    updateIntervalMillis: 6 * HOUR_MILLIS,
  },
  /** DWD ICON Global: 11 km, worldwide, ~7 days. */
  ICON_GLOBAL: {
    id: "ICON_GLOBAL",
    apiName: "icon_global",
    displayName: "ICON Global",
    description: "11 km, Global, 7 days",
    availableForecastDays: 7,
    fallback: null,
    updateIntervalMillis: 6 * HOUR_MILLIS,
  },
};

/** Display order, matching the declaration order of the Kotlin enum. */
export const FORECAST_MODEL_ORDER: readonly ForecastModelId[] = [
  "BEST_MATCH",
  "ICON_SEAMLESS",
  "ECMWF_IFS",
  "GFS_SEAMLESS",
  "METEOFRANCE_AROME",
  "METEOFRANCE_ARPEGE",
  "ICON_D2",
  "ICON_EU",
  "ICON_GLOBAL",
];

/**
 * Fallback chain: ICON D2 -> ICON EU -> ICON Global -> BEST_MATCH.
 * Separate chains for AROME, ECMWF, and GFS lead to BEST_MATCH as the ultimate
 * fallback.
 */
export const FALLBACK_CHAINS: Partial<Record<ForecastModelId, ForecastModelId>> = {
  ICON_D2: "ICON_EU",
  ICON_EU: "ICON_GLOBAL",
  ICON_GLOBAL: "BEST_MATCH",
  ICON_SEAMLESS: "BEST_MATCH",
  METEOFRANCE_AROME: "METEOFRANCE_ARPEGE",
  METEOFRANCE_ARPEGE: "BEST_MATCH",
  ECMWF_IFS: "BEST_MATCH",
  GFS_SEAMLESS: "BEST_MATCH",
};

export function fallbackFor(model: ForecastModelId): ForecastModelId {
  return FALLBACK_CHAINS[model] ?? "BEST_MATCH";
}

export function forecastModelFromApiName(apiName: string): ForecastModel | null {
  return (
    FORECAST_MODEL_ORDER.map((id) => FORECAST_MODELS[id]).find(
      (model) => model.apiName === apiName,
    ) ?? null
  );
}
