/**
 * Thermal-strength and wind-speed color scales.
 *
 * 1:1 port of `ui/screens/forecast/ForecastColorScales.kt`. The Kotlin code
 * uses Compose `Color.lerp` (Oklab); here {@link mixRgb} blends in sRGB, a minor
 * visual simplification for the dense stop sets used here.
 */

import { coerceIn, f } from "../engine/float32";
import { mixRgb, type Rgb } from "./theme";

export const THERMIC_STRENGTH_COLOR_SCALE_MAX_MPS = f(5);
export const WIND_SPEED_COLOR_SCALE_MAX_KMH = f(60);

type ColorStop = readonly [number, Rgb];

function hexRgb(value: number): Rgb {
  return [(value >> 16) & 0xff, (value >> 8) & 0xff, value & 0xff];
}

const thermicStrengthColorStops: ColorStop[] = [
  [0.0, hexRgb(0xf4faff)],
  [0.4, hexRgb(0xd7f0ff)],
  [0.8, hexRgb(0xa8d8ff)],
  [1.2, hexRgb(0x4ba3f2)],
  [1.6, hexRgb(0x00a896)],
  [2.0, hexRgb(0x43a047)],
  [3.0, hexRgb(0xd1c300)],
  [4.0, hexRgb(0xfb8c00)],
  [THERMIC_STRENGTH_COLOR_SCALE_MAX_MPS, hexRgb(0xd32f2f)],
];

const windSpeedColorStops: ColorStop[] = [
  [0, hexRgb(0x1565c0)],
  [5, hexRgb(0x0288d1)],
  [10, hexRgb(0x00acc1)],
  [15, hexRgb(0x00a86b)],
  [20, hexRgb(0x43a047)],
  [30, hexRgb(0x7cb342)],
  [40, hexRgb(0xfdd835)],
  [50, hexRgb(0xfb8c00)],
  [WIND_SPEED_COLOR_SCALE_MAX_KMH, hexRgb(0xd32f2f)],
];

function interpolateColorStops(value: number, colorStops: ColorStop[]): Rgb {
  let lowerStop = colorStops[0];
  for (const stop of colorStops) {
    if (stop[0] <= value) lowerStop = stop;
  }
  let upperStop = colorStops[colorStops.length - 1];
  for (let i = colorStops.length - 1; i >= 0; i--) {
    if (colorStops[i][0] >= value) upperStop = colorStops[i];
  }

  if (lowerStop[0] === upperStop[0]) return lowerStop[1];

  const fraction = (value - lowerStop[0]) / (upperStop[0] - lowerStop[0]);
  return mixRgb(lowerStop[1], upperStop[1], fraction);
}

export function thermicStrengthColor(strengthMps: number): Rgb {
  return interpolateColorStops(
    coerceIn(strengthMps, 0, THERMIC_STRENGTH_COLOR_SCALE_MAX_MPS),
    thermicStrengthColorStops,
  );
}

export function windSpeedColor(speedKmh: number): Rgb {
  return interpolateColorStops(
    coerceIn(speedKmh, 0, WIND_SPEED_COLOR_SCALE_MAX_KMH),
    windSpeedColorStops,
  );
}
