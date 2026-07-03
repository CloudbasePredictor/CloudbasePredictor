/**
 * Altitude-range viewport for the chart views.
 *
 * 1:1 port of `ui/screens/forecast/ForecastChartViewport.kt`. Governs the
 * pinch/wheel zoom behaviour for the visible top altitude on the Thermic, Wind
 * and Stüve charts. Float math is mirrored with `Math.fround`.
 */

import { coerceIn, f } from "../engine/float32";

export const DEFAULT_TOP_ALTITUDE_KM = f(4.5);
export const MIN_TOP_ALTITUDE_KM = f(1.5);
export const MAX_TOP_ALTITUDE_KM = f(12.5);
const ZOOM_AMPLIFICATION_FACTOR = f(2.5);

export interface ForecastChartViewport {
  visibleTopAltitudeKm: number;
  defaultTopAltitudeKm: number;
  maxTopAltitudeKm: number;
}

export function createChartViewport(
  visibleTopAltitudeKm: number = DEFAULT_TOP_ALTITUDE_KM,
): ForecastChartViewport {
  return {
    visibleTopAltitudeKm,
    defaultTopAltitudeKm: DEFAULT_TOP_ALTITUDE_KM,
    maxTopAltitudeKm: MAX_TOP_ALTITUDE_KM,
  };
}

export function sanitizeTopAltitudeKm(
  topAltitudeKm: number,
  minTopAltitudeKm: number = DEFAULT_TOP_ALTITUDE_KM,
  maxTopAltitudeKm: number = MAX_TOP_ALTITUDE_KM,
): number {
  if (!Number.isFinite(topAltitudeKm)) {
    return minTopAltitudeKm;
  }
  return coerceIn(topAltitudeKm, minTopAltitudeKm, maxTopAltitudeKm);
}

export function withVisibleTopAltitudeKm(
  viewport: ForecastChartViewport,
  topAltitudeKm: number,
): ForecastChartViewport {
  return {
    ...viewport,
    visibleTopAltitudeKm: sanitizeTopAltitudeKm(
      topAltitudeKm,
      MIN_TOP_ALTITUDE_KM,
      viewport.maxTopAltitudeKm,
    ),
  };
}

/**
 * Apply a pinch/wheel zoom change to the current top altitude.
 * `zoomChange > 1` zooms in (lower top altitude), `< 1` zooms out.
 */
export function zoomedTopAltitudeKm(
  currentTopAltitudeKm: number,
  zoomChange: number,
  minTopAltitudeKm: number = MIN_TOP_ALTITUDE_KM,
  maxTopAltitudeKm: number = MAX_TOP_ALTITUDE_KM,
): number {
  if (!Number.isFinite(zoomChange) || zoomChange <= 0) {
    return sanitizeTopAltitudeKm(currentTopAltitudeKm, minTopAltitudeKm, maxTopAltitudeKm);
  }

  // Amplify the zoom gesture for a more responsive feel similar to maps.
  const amplifiedZoom = f(1 + f(f(zoomChange - 1) * ZOOM_AMPLIFICATION_FACTOR));

  return sanitizeTopAltitudeKm(
    f(currentTopAltitudeKm / amplifiedZoom),
    minTopAltitudeKm,
    maxTopAltitudeKm,
  );
}
