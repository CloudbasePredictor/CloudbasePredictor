/**
 * Stüve / Skew-T chart UI model, standard reference line sets, and the pure
 * builder helpers the diagram needs.
 *
 * 1:1 port of the non-placeholder parts of
 * `ui/screens/forecast/StuveForecastChartUiModel.kt`. Thermodynamic helpers
 * (`potentialTemperatureK`, `dryAdiabatTempC`, `satMixingRatioGKg`,
 * `moistAdiabatTempFromPointC`, `interpolateHeightKmAtPressure`,
 * `relativeHumidityFraction`) are reused from the ported engine so parcel
 * geometry never diverges. Float math mirrors the Kotlin `Float` engine.
 */

import type { CclHourlyResult } from "../../engine/cclAnalysis";
import { f, toInt } from "../../engine/float32";
import {
  dryAdiabatTempC,
  interpolateHeightKmAtPressure,
  moistAdiabatTempFromPointC,
  type ProfileLevel,
  potentialTemperatureK,
  relativeHumidityFraction,
  satMixingRatioGKg,
} from "../../engine/parcelAnalysis";

export interface StuveProfilePoint {
  /** Pressure level, hPa. */
  pressureHpa: number;
  /** Temperature at this pressure level, °C. */
  temperatureC: number;
  /** Height of this point in metres ASL, if available. */
  heightMeters: number | null;
  /** True when this point comes from real backend model data. */
  isRealData: boolean;
}

export interface StuveWindBarb {
  pressureHpa: number;
  speedKmh: number;
  /** Meteorological direction FROM, degrees. */
  directionDeg: number;
}

export interface StuveMoistureBand {
  topPressureHpa: number;
  bottomPressureHpa: number;
  relativeHumidityFraction: number;
}

export interface StuveForecastChartUiModel {
  /** Pressure levels for the Y-axis, hPa, descending. */
  pressureLevels: number[];
  temperatureProfile: StuveProfilePoint[];
  dewpointProfile: StuveProfilePoint[];
  parcelAscentPath: StuveProfilePoint[];
  windBarbs: StuveWindBarb[];
  cclPressureHpa: number | null;
  tconC: number | null;
  cclResults: CclHourlyResult[];
  moistureBands: StuveMoistureBand[];
  /** Currently displayed hour of the day (local, 6–22). */
  selectedHour: number;
  /** Station surface pressure, hPa. */
  surfacePressureHpa: number;
}

export function stuveProfilePoint(
  pressureHpa: number,
  temperatureC: number,
  heightMeters: number | null = null,
  isRealData = false,
): StuveProfilePoint {
  return { pressureHpa, temperatureC, heightMeters, isRealData };
}

// --- Standard pressure levels used in the diagram ---
export const STUVE_PRESSURE_LEVELS: number[] = [
  1050, 1000, 950, 900, 850, 800, 750, 700, 650, 600, 550, 500, 450, 400, 350, 300, 250,
].map(f);

// Approximate height in metres for standard pressure levels (ISA).
const PRESSURE_TO_HEIGHT_MAP: Array<readonly [number, number]> = [
  [1050, -300],
  [1000, 111],
  [950, 540],
  [900, 988],
  [850, 1457],
  [800, 1949],
  [750, 2466],
  [700, 3013],
  [650, 3591],
  [600, 4206],
  [550, 4865],
  [500, 5574],
  [450, 6344],
  [400, 7185],
  [350, 8117],
  [300, 9164],
  [250, 10363],
];

export function pressureToApproxHeightMeters(pressureHpa: number): number {
  const sorted = [...PRESSURE_TO_HEIGHT_MAP].sort((a, b) => b[0] - a[0]);
  let lower: readonly [number, number] | null = null;
  for (const entry of sorted) {
    if (entry[0] >= pressureHpa) lower = entry;
  }
  let upper: readonly [number, number] | null = null;
  for (const entry of sorted) {
    if (entry[0] < pressureHpa) {
      upper = entry;
      break;
    }
  }
  if (lower === null) return sorted[sorted.length - 1][1];
  if (upper === null) return sorted[0][1];
  const frac = f((lower[0] - pressureHpa) / (lower[0] - upper[0]));
  return toInt(f(lower[1] + f(frac * (upper[1] - lower[1]))));
}

// --- Reference line sets ---
export const STUVE_DRY_ADIABAT_THETAS_K: number[] = [
  253, 263, 273, 283, 293, 303, 313, 323, 333, 343, 353, 363, 373,
].map(f);

export const STUVE_MOIST_ADIABAT_THETAS_K: number[] = [
  263, 273, 278, 283, 288, 293, 298, 303, 313, 323,
].map(f);

export const STUVE_MIXING_RATIO_VALUES_GKG: number[] = [0.4, 1, 2, 3, 5, 8, 12, 16, 20, 28].map(f);

function interpolateProfileValue(
  profile: StuveProfilePoint[],
  pressureHpa: number,
  selector: (point: StuveProfilePoint) => number,
): number | null {
  const sorted = [...profile].sort((a, b) => b.pressureHpa - a.pressureHpa);
  if (sorted.length === 0) return null;
  const exact = sorted.find((it) => it.pressureHpa === pressureHpa);
  if (exact !== undefined) return selector(exact);
  for (let i = 0; i < sorted.length - 1; i++) {
    const lower = sorted[i];
    const upper = sorted[i + 1];
    if (pressureHpa <= lower.pressureHpa && pressureHpa >= upper.pressureHpa) {
      const fraction = f(
        (lower.pressureHpa - pressureHpa) / (lower.pressureHpa - upper.pressureHpa),
      );
      return f(selector(lower) + f(fraction * f(selector(upper) - selector(lower))));
    }
  }
  return null;
}

export function interpolateProfileTemperature(
  profile: StuveProfilePoint[],
  pressureHpa: number,
): number | null {
  return interpolateProfileValue(profile, pressureHpa, (it) => it.temperatureC);
}

export function interpolateProfileHeightMeters(
  profile: StuveProfilePoint[],
  pressureHpa: number,
): number | null {
  return interpolateProfileValue(
    profile,
    pressureHpa,
    (point) => point.heightMeters ?? pressureToApproxHeightMeters(point.pressureHpa),
  );
}

export function buildRenderableParcelPressures(
  surfacePressureHpa: number,
  profilePressures: number[],
): number[] {
  const limit = f(surfacePressureHpa + 0.5);
  const seen = new Set<number>();
  const result: number[] = [];
  for (const pressure of [surfacePressureHpa, ...STUVE_PRESSURE_LEVELS, ...profilePressures]) {
    if (pressure <= limit && !seen.has(pressure)) {
      seen.add(pressure);
      result.push(pressure);
    }
  }
  return result.sort((a, b) => b - a);
}

export function buildParcelAscentPath(
  pressures: number[],
  profile: ProfileLevel[],
  surfaceTemperatureC: number,
  surfaceDewPointC: number,
  surfacePressureHpa: number,
  surfaceHeatingC: number,
): StuveProfilePoint[] {
  const parcelThetaK = potentialTemperatureK(
    f(surfaceTemperatureC + surfaceHeatingC),
    surfacePressureHpa,
  );
  const surfaceMixingRatio = satMixingRatioGKg(surfaceDewPointC, surfacePressureHpa);

  let reachedLcl = false;
  let lclTemperatureC: number | null = null;
  let lclPressureHpa: number | null = null;

  return pressures.map((pressure) => {
    const interpHeightKm = interpolateHeightKmAtPressure(profile, pressure);
    const heightMeters =
      interpHeightKm !== null ? f(interpHeightKm * 1000) : pressureToApproxHeightMeters(pressure);

    let temperatureC: number;
    if (!reachedLcl) {
      const dryTemp = dryAdiabatTempC(parcelThetaK, pressure);
      const satMixingRatio = satMixingRatioGKg(dryTemp, pressure);
      if (satMixingRatio <= surfaceMixingRatio) {
        reachedLcl = true;
        lclTemperatureC = dryTemp;
        lclPressureHpa = pressure;
      }
      temperatureC = dryTemp;
    } else {
      temperatureC = moistAdiabatTempFromPointC(
        lclTemperatureC ?? dryAdiabatTempC(parcelThetaK, pressure),
        lclPressureHpa ?? pressure,
        pressure,
      );
    }
    return stuveProfilePoint(pressure, temperatureC, heightMeters);
  });
}

export function buildMoistureBands(
  temperatureProfile: StuveProfilePoint[],
  dewpointProfile: StuveProfilePoint[],
): StuveMoistureBand[] {
  const sortedTemps = [...temperatureProfile].sort((a, b) => b.pressureHpa - a.pressureHpa);
  if (sortedTemps.length < 2) return [];

  const bands: StuveMoistureBand[] = [];
  for (let index = 0; index < sortedTemps.length - 1; index++) {
    const lower = sortedTemps[index];
    const upper = sortedTemps[index + 1];
    const dewLower = interpolateProfileTemperature(dewpointProfile, lower.pressureHpa);
    if (dewLower === null) continue;
    const dewUpper = interpolateProfileTemperature(dewpointProfile, upper.pressureHpa);
    if (dewUpper === null) continue;
    const averageRelativeHumidity = f(
      f(
        relativeHumidityFraction(lower.temperatureC, dewLower) +
          relativeHumidityFraction(upper.temperatureC, dewUpper),
      ) / 2,
    );
    bands.push({
      topPressureHpa: upper.pressureHpa,
      bottomPressureHpa: lower.pressureHpa,
      relativeHumidityFraction: averageRelativeHumidity,
    });
  }
  return bands;
}

/**
 * Builds minimal profile levels from the chart's temperature/dewpoint profiles,
 * used to recompute the parcel path during interaction.
 * Port of `StuveDiagramGeometry.buildMinimalProfileLevels`.
 */
export function buildMinimalProfileLevels(chart: StuveForecastChartUiModel): ProfileLevel[] {
  return chart.temperatureProfile.map((point) => ({
    pressureHpa: point.pressureHpa,
    temperatureC: point.temperatureC,
    dewPointC: interpolateProfileTemperature(chart.dewpointProfile, point.pressureHpa),
    heightKm: f((point.heightMeters ?? pressureToApproxHeightMeters(point.pressureHpa)) / 1000),
    relativeHumidityPercent: null,
    cloudCoverPercent: null,
    windSpeedKmh: null,
    isSynthetic: false,
  }));
}
