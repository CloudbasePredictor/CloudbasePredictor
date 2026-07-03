/**
 * Stüve cursor interaction state and readout.
 *
 * 1:1 port of `ui/screens/forecast/views/StuveDiagramReadout.kt`. The readout's
 * thermodynamic anchors (`dryAdiabatTempC`, `potentialTemperatureK`) reuse the
 * ported engine so the guide never diverges from the diagram.
 */

import { dryAdiabatTempC, potentialTemperatureK } from "../../engine/parcelAnalysis";
import {
  interpolateProfileHeightMeters,
  interpolateProfileTemperature,
  pressureToApproxHeightMeters,
  type StuveForecastChartUiModel,
  type StuveProfilePoint,
} from "./model";

/** 2D interaction state for a placed/tracked cursor (x, y in canvas px). */
export interface SkewTCursorState {
  x: number;
  y: number;
  isPinned: boolean;
}

export interface CursorReadout {
  pressureHpa: number;
  altitudeMeters: number;
  temperatureC: number | null;
  dewpointC: number | null;
  parcelTemperatureC: number | null;
  guideTemperatureC: number | null;
  guideDryThetaK: number | null;
  parcelSurfaceTemperatureC: number | null;
  windSpeedKmh: number | null;
  windDirectionDeg: number | null;
}

function clamp(value: number, min: number, max: number): number {
  if (min > max) return value;
  return Math.min(Math.max(value, min), max);
}

export function buildCursorReadout(
  chart: StuveForecastChartUiModel,
  pressureHpa: number,
  anchorTemperatureC: number | null = null,
  parcelPath: StuveProfilePoint[] = chart.parcelAscentPath,
): CursorReadout {
  const lastTemp = chart.temperatureProfile[chart.temperatureProfile.length - 1];
  const clampedPressure = clamp(
    pressureHpa,
    lastTemp?.pressureHpa ?? pressureHpa,
    chart.surfacePressureHpa,
  );
  const altitudeMeters = Math.round(
    interpolateProfileHeightMeters(chart.temperatureProfile, clampedPressure) ??
      pressureToApproxHeightMeters(clampedPressure),
  );

  let nearestWind = null as StuveForecastChartUiModel["windBarbs"][number] | null;
  let nearestDiff = Number.POSITIVE_INFINITY;
  for (const barb of chart.windBarbs) {
    const diff = Math.abs(barb.pressureHpa - clampedPressure);
    if (diff < nearestDiff) {
      nearestDiff = diff;
      nearestWind = barb;
    }
  }
  if (nearestWind !== null && nearestDiff > 60) nearestWind = null;

  const envTemperatureC = interpolateProfileTemperature(chart.temperatureProfile, clampedPressure);
  const guideTemperatureC = anchorTemperatureC ?? envTemperatureC;

  return {
    pressureHpa: clampedPressure,
    altitudeMeters,
    temperatureC: envTemperatureC,
    dewpointC: interpolateProfileTemperature(chart.dewpointProfile, clampedPressure),
    parcelTemperatureC: interpolateProfileTemperature(parcelPath, clampedPressure),
    guideTemperatureC,
    guideDryThetaK:
      guideTemperatureC !== null ? potentialTemperatureK(guideTemperatureC, clampedPressure) : null,
    parcelSurfaceTemperatureC:
      guideTemperatureC !== null
        ? dryAdiabatTempC(
            potentialTemperatureK(guideTemperatureC, clampedPressure),
            chart.surfacePressureHpa,
          )
        : null,
    windSpeedKmh: nearestWind?.speedKmh ?? null,
    windDirectionDeg: nearestWind?.directionDeg ?? null,
  };
}
