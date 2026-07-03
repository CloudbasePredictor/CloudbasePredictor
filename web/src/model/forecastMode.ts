/**
 * The four forecast views the app can show.
 *
 * Ported 1:1 from `model/ForecastMode.kt`.
 */
export type ForecastMode = "THERMIC" | "STUVE" | "WIND" | "CLOUD";

export const FORECAST_MODES: readonly ForecastMode[] = ["THERMIC", "STUVE", "WIND", "CLOUD"];
