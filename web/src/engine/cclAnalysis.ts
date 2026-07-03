/**
 * Convective Condensation Level (CCL) analysis.
 *
 * 1:1 port of `domain/forecast/CclAnalysis.kt`. Function names, control flow and
 * floating-point operation order are preserved. All arithmetic is float32 to
 * match the Kotlin `Float` engine — see `float32.ts`.
 */

import { distinct, distinctBy, sortedByDescending } from "./collections";
import {
  coerceAtLeast,
  coerceIn,
  f,
  fadd,
  fdiv,
  fexp,
  fln,
  fmul,
  fpow,
  fsub,
  toInt,
} from "./float32";

export type CclMethod = "SURFACE" | "ML50" | "ML100";
export type CclIntersectionType = "BOTTOM" | "INTERMEDIATE" | "TOP";

export interface CclPressureLevel {
  pressureHpa: number;
  temperatureC: number;
  dewPointC: number | null;
  heightMslM: number | null;
  isSynthetic: boolean;
}

export interface CclHourlyInput {
  time: string;
  surfaceTemperatureC: number;
  surfaceDewPointC: number;
  surfacePressureHpa: number;
  surfaceElevationM: number;
  pressureLevels: CclPressureLevel[];
  takeoffElevationM: number | null;
}

export interface CclIntersection {
  pressureHpa: number;
  temperatureC: number;
  heightMslM: number;
  heightAglGridM: number;
  heightAboveTakeoffM: number | null;
  type: CclIntersectionType;
}

export interface CclHourlyResult {
  time: string;
  method: CclMethod;
  cclPressureHpa: number | null;
  cclTemperatureC: number | null;
  cclHeightMslM: number | null;
  cclHeightAglGridM: number | null;
  cclHeightAboveTakeoffM: number | null;
  convectiveTemperatureC: number | null;
  temperature2mC: number;
  heatingMarginC: number | null;
  reachable: boolean;
  intersections: CclIntersection[];
  warnings: string[];
}

interface CclProfileLevel {
  pressureHpa: number;
  temperatureC: number;
  dewPointC: number | null;
  heightMslM: number;
}

interface PressureMixingRatio {
  pressureHpa: number;
  mixingRatioKgKg: number;
}

interface CclIntersectionCandidate {
  pressureHpa: number;
  temperatureC: number;
  heightMslM: number;
}

export function analyzeCclHourly(input: CclHourlyInput): CclHourlyResult[] {
  const profile = buildCclProfile(input);
  return [
    analyzeCclMethod(
      input,
      profile,
      "SURFACE",
      mixingRatioFromDewPointKgKg(input.surfacePressureHpa, input.surfaceDewPointC),
      null,
    ),
    analyzeCclMethod(
      input,
      profile,
      "ML50",
      mixedLayerMixingRatioKgKg(input, MIXED_LAYER_50_HPA),
      "Mixed-layer humidity unavailable: insufficient real dewpoint data in the lowest 50 hPa",
    ),
    analyzeCclMethod(
      input,
      profile,
      "ML100",
      mixedLayerMixingRatioKgKg(input, MIXED_LAYER_100_HPA),
      "Mixed-layer humidity unavailable: insufficient real dewpoint data in the lowest 100 hPa",
    ),
  ];
}

export function primaryCclResult(results: CclHourlyResult[]): CclHourlyResult | null {
  return (
    results.find((it) => it.method === "ML50" && it.cclPressureHpa !== null) ??
    results.find((it) => it.method === "SURFACE" && it.cclPressureHpa !== null) ??
    results.find((it) => it.method === "ML50") ??
    results.find((it) => it.method === "SURFACE") ??
    null
  );
}

export function mixedLayerMixingRatioKgKg(input: CclHourlyInput, depthHpa: number): number | null {
  const pressureProfile = buildCclProfile(input);
  const vaporSamples = sortedByDescending(
    pressureProfile.flatMap((level) => {
      if (level.dewPointC === null) return [];
      return [
        {
          pressureHpa: level.pressureHpa,
          mixingRatioKgKg: mixingRatioFromDewPointKgKg(level.pressureHpa, level.dewPointC),
        },
      ];
    }),
    (sample) => sample.pressureHpa,
  );

  if (vaporSamples.length < 2) return null;

  const surfacePressure = input.surfacePressureHpa;
  const topPressure = fsub(surfacePressure, depthHpa);
  const layerSamples: PressureMixingRatio[] = [];
  layerSamples.push(vaporSamples[0]);

  for (const sample of vaporSamples.slice(1)) {
    if (
      sample.pressureHpa < surfacePressure &&
      sample.pressureHpa > fadd(topPressure, PRESSURE_EPSILON_HPA)
    ) {
      layerSamples.push(sample);
    }
  }

  const topSample = interpolatedMixingRatioAtPressure(vaporSamples, topPressure);
  if (topSample !== null) {
    layerSamples.push(topSample);
    return trapezoidalPressureMean(
      sortedByDescending(layerSamples, (sample) => sample.pressureHpa),
    );
  }

  const fallbackUpper = vaporSamples.slice(1).find((sample) => {
    const delta = fsub(surfacePressure, sample.pressureHpa);
    return delta >= FALLBACK_MIN_DEPTH_HPA && delta <= FALLBACK_MAX_DEPTH_HPA;
  });
  if (fallbackUpper === undefined) return null;

  return trapezoidalPressureMean([vaporSamples[0], fallbackUpper]);
}

export function cclMixingRatioTemperatureC(mixingRatioKgKg: number, pressureHpa: number): number {
  const vaporPressureHpa = fdiv(
    fmul(pressureHpa, mixingRatioKgKg),
    fadd(CCL_EPSILON, mixingRatioKgKg),
  );
  return inverseSaturationVaporPressureC(vaporPressureHpa);
}

export function mixingRatioFromDewPointKgKg(pressureHpa: number, dewPointC: number): number {
  const vaporPressureHpa = saturationVaporPressureHpa(dewPointC);
  return fdiv(
    fmul(CCL_EPSILON, vaporPressureHpa),
    coerceAtLeast(fsub(pressureHpa, vaporPressureHpa), f(0.01)),
  );
}

function analyzeCclMethod(
  input: CclHourlyInput,
  profile: CclProfileLevel[],
  method: CclMethod,
  mixingRatioKgKg: number | null,
  humidityWarning: string | null,
): CclHourlyResult {
  const baseWarnings: string[] = [];
  const surfaceNearSaturation =
    fsub(input.surfaceTemperatureC, input.surfaceDewPointC) <= SURFACE_SATURATION_SPREAD_C;
  if (surfaceNearSaturation) {
    baseWarnings.push(WARNING_SURFACE_NEAR_SATURATION);
  }

  if (mixingRatioKgKg === null) {
    if (humidityWarning !== null) baseWarnings.push(humidityWarning);
    return emptyCclResult(input, method, baseWarnings);
  }

  if (method === "SURFACE" && surfaceNearSaturation) {
    const surfaceIntersection: CclIntersection = {
      pressureHpa: input.surfacePressureHpa,
      temperatureC: input.surfaceTemperatureC,
      heightMslM: input.surfaceElevationM,
      heightAglGridM: 0,
      heightAboveTakeoffM:
        input.takeoffElevationM !== null
          ? fsub(input.surfaceElevationM, input.takeoffElevationM)
          : null,
      type: "BOTTOM",
    };
    return resultFromIntersections(input, method, [surfaceIntersection], baseWarnings);
  }

  if (profile.length < 2) {
    return emptyCclResult(input, method, [...baseWarnings, WARNING_NO_CCL]);
  }

  const intersections = findCclIntersections(input, profile, mixingRatioKgKg);

  if (intersections.length === 0) {
    return emptyCclResult(input, method, [...baseWarnings, WARNING_NO_CCL]);
  }

  return resultFromIntersections(input, method, intersections, baseWarnings);
}

function resultFromIntersections(
  input: CclHourlyInput,
  method: CclMethod,
  intersections: CclIntersection[],
  warnings: string[],
): CclHourlyResult {
  const sortedIntersections = sortedByDescending(intersections, (it) => it.pressureHpa);
  const bottom = sortedIntersections[0];
  const convective = convectiveTemperatureC(
    bottom.temperatureC,
    bottom.pressureHpa,
    input.surfacePressureHpa,
  );
  const heatingMarginC = fsub(input.surfaceTemperatureC, convective);
  const reachable = input.surfaceTemperatureC >= fsub(convective, REACHABLE_TOLERANCE_C);
  const resultWarnings = [...warnings];

  if (sortedIntersections.length > 1) {
    resultWarnings.push(WARNING_MULTIPLE_INTERSECTIONS);
  }
  if (!reachable) {
    resultWarnings.push(WARNING_THEORETICAL_ONLY);
  }
  if (bottom.heightAglGridM < LOW_CCL_AGL_M) {
    resultWarnings.push(WARNING_VERY_LOW_CCL);
  }
  const ceilingReferenceM = bottom.heightAboveTakeoffM ?? bottom.heightAglGridM;
  if (ceilingReferenceM > HIGH_CCL_ABOVE_REFERENCE_M) {
    resultWarnings.push(WARNING_HIGH_CCL);
  }
  if ((bottom.heightAboveTakeoffM ?? FLOAT_MAX_VALUE) <= LOW_CCL_AGL_M) {
    resultWarnings.push(WARNING_CCL_NEAR_TAKEOFF);
  }

  return {
    time: input.time,
    method,
    cclPressureHpa: bottom.pressureHpa,
    cclTemperatureC: bottom.temperatureC,
    cclHeightMslM: bottom.heightMslM,
    cclHeightAglGridM: bottom.heightAglGridM,
    cclHeightAboveTakeoffM: bottom.heightAboveTakeoffM,
    convectiveTemperatureC: convective,
    temperature2mC: input.surfaceTemperatureC,
    heatingMarginC,
    reachable,
    intersections: sortedIntersections,
    warnings: distinct(resultWarnings),
  };
}

function emptyCclResult(
  input: CclHourlyInput,
  method: CclMethod,
  warnings: string[],
): CclHourlyResult {
  return {
    time: input.time,
    method,
    cclPressureHpa: null,
    cclTemperatureC: null,
    cclHeightMslM: null,
    cclHeightAglGridM: null,
    cclHeightAboveTakeoffM: null,
    convectiveTemperatureC: null,
    temperature2mC: input.surfaceTemperatureC,
    heatingMarginC: null,
    reachable: false,
    intersections: [],
    warnings: distinct(warnings),
  };
}

function buildCclProfile(input: CclHourlyInput): CclProfileLevel[] {
  const surface: CclProfileLevel = {
    pressureHpa: input.surfacePressureHpa,
    temperatureC: input.surfaceTemperatureC,
    dewPointC: input.surfaceDewPointC,
    heightMslM: input.surfaceElevationM,
  };
  const pressureLevels = distinctBy(
    sortedByDescending(
      input.pressureLevels
        .filter((it) => !it.isSynthetic)
        .filter((it) => it.pressureHpa >= MIN_CCL_PRESSURE_HPA)
        .filter(
          (it) =>
            it.pressureHpa <
            fsub(input.surfacePressureHpa, PRESSURE_LEVEL_BELOW_SURFACE_MARGIN_HPA),
        )
        .filter((it) => it.heightMslM !== null)
        .filter(
          (it) =>
            (it.heightMslM as number) >
            fadd(input.surfaceElevationM, HEIGHT_ABOVE_SURFACE_MARGIN_M),
        )
        .map(
          (level): CclProfileLevel => ({
            pressureHpa: level.pressureHpa,
            temperatureC: level.temperatureC,
            dewPointC: level.dewPointC,
            heightMslM: level.heightMslM as number,
          }),
        ),
      (it) => it.pressureHpa,
    ),
    (it) => toInt(fmul(it.pressureHpa, 10)),
  );

  return sortedByDescending([surface, ...pressureLevels], (it) => it.pressureHpa);
}

function findCclIntersections(
  input: CclHourlyInput,
  profile: CclProfileLevel[],
  mixingRatioKgKg: number,
): CclIntersection[] {
  const candidates: CclIntersectionCandidate[] = [];
  let previous: CclProfileLevel | null = null;
  let previousDifference: number | null = null;

  for (const level of profile) {
    const mixingRatioTemperatureC = cclMixingRatioTemperatureC(mixingRatioKgKg, level.pressureHpa);
    const difference = fsub(level.temperatureC, mixingRatioTemperatureC);

    if (Math.abs(difference) <= ZERO_CROSSING_EPSILON_C) {
      addIfDistinct(candidates, toIntersectionCandidate(level));
    }

    const previousLevel = previous;
    const previousDiff = previousDifference;
    if (previousLevel !== null && previousDiff !== null && fmul(previousDiff, difference) < 0) {
      addIfDistinct(
        candidates,
        interpolateIntersectionCandidate(previousLevel, level, previousDiff, difference),
      );
    }

    previous = level;
    previousDifference = difference;
  }

  const sortedCandidates = sortedByDescending(candidates, (it) => it.pressureHpa);
  return sortedCandidates.map((candidate, index) => {
    const type: CclIntersectionType =
      index === 0 ? "BOTTOM" : index === sortedCandidates.length - 1 ? "TOP" : "INTERMEDIATE";
    return toIntersection(candidate, input, type);
  });
}

function addIfDistinct(
  list: CclIntersectionCandidate[],
  candidate: CclIntersectionCandidate,
): void {
  if (
    !list.some(
      (it) => Math.abs(fsub(it.pressureHpa, candidate.pressureHpa)) < DISTINCT_PRESSURE_EPSILON_HPA,
    )
  ) {
    list.push(candidate);
  }
}

function toIntersectionCandidate(level: CclProfileLevel): CclIntersectionCandidate {
  return {
    pressureHpa: level.pressureHpa,
    temperatureC: level.temperatureC,
    heightMslM: level.heightMslM,
  };
}

function interpolateIntersectionCandidate(
  lower: CclProfileLevel,
  upper: CclProfileLevel,
  lowerDifference: number,
  upperDifference: number,
): CclIntersectionCandidate {
  const alpha = coerceIn(fdiv(-lowerDifference, fsub(upperDifference, lowerDifference)), 0, 1);
  const lowerLnPressure = fln(lower.pressureHpa);
  const upperLnPressure = fln(upper.pressureHpa);
  const pressureHpa = fexp(
    fadd(lowerLnPressure, fmul(alpha, fsub(upperLnPressure, lowerLnPressure))),
  );
  const temperatureC = fadd(
    lower.temperatureC,
    fmul(alpha, fsub(upper.temperatureC, lower.temperatureC)),
  );
  const heightMslM = fadd(lower.heightMslM, fmul(alpha, fsub(upper.heightMslM, lower.heightMslM)));
  return { pressureHpa, temperatureC, heightMslM };
}

function toIntersection(
  candidate: CclIntersectionCandidate,
  input: CclHourlyInput,
  type: CclIntersectionType,
): CclIntersection {
  return {
    pressureHpa: candidate.pressureHpa,
    temperatureC: candidate.temperatureC,
    heightMslM: candidate.heightMslM,
    heightAglGridM: fsub(candidate.heightMslM, input.surfaceElevationM),
    heightAboveTakeoffM:
      input.takeoffElevationM !== null ? fsub(candidate.heightMslM, input.takeoffElevationM) : null,
    type,
  };
}

function interpolatedMixingRatioAtPressure(
  samples: PressureMixingRatio[],
  targetPressureHpa: number,
): PressureMixingRatio | null {
  const exact = samples.find(
    (it) => Math.abs(fsub(it.pressureHpa, targetPressureHpa)) < PRESSURE_EPSILON_HPA,
  );
  if (exact !== undefined) {
    return { pressureHpa: targetPressureHpa, mixingRatioKgKg: exact.mixingRatioKgKg };
  }
  for (let index = 0; index < samples.length - 1; index++) {
    const lower = samples[index];
    const upper = samples[index + 1];
    if (targetPressureHpa <= lower.pressureHpa && targetPressureHpa >= upper.pressureHpa) {
      const fraction = fdiv(
        fsub(lower.pressureHpa, targetPressureHpa),
        fsub(lower.pressureHpa, upper.pressureHpa),
      );
      return {
        pressureHpa: targetPressureHpa,
        mixingRatioKgKg: fadd(
          lower.mixingRatioKgKg,
          fmul(fraction, fsub(upper.mixingRatioKgKg, lower.mixingRatioKgKg)),
        ),
      };
    }
  }
  return null;
}

function trapezoidalPressureMean(samples: PressureMixingRatio[]): number | null {
  if (samples.length < 2) return null;
  let integral = 0;
  let pressureDepth = 0;
  for (let index = 0; index < samples.length - 1; index++) {
    const lower = samples[index];
    const upper = samples[index + 1];
    const deltaPressure = fsub(lower.pressureHpa, upper.pressureHpa);
    if (deltaPressure <= 0) continue;
    integral = fadd(
      integral,
      fmul(fdiv(fadd(lower.mixingRatioKgKg, upper.mixingRatioKgKg), 2), deltaPressure),
    );
    pressureDepth = fadd(pressureDepth, deltaPressure);
  }
  return pressureDepth > PRESSURE_EPSILON_HPA ? fdiv(integral, pressureDepth) : null;
}

function convectiveTemperatureC(
  cclTemperatureC: number,
  cclPressureHpa: number,
  surfacePressureHpa: number,
): number {
  return fsub(
    fmul(
      fadd(cclTemperatureC, KELVIN_OFFSET),
      fpow(fdiv(surfacePressureHpa, cclPressureHpa), CCL_KAPPA),
    ),
    KELVIN_OFFSET,
  );
}

function saturationVaporPressureHpa(temperatureC: number): number {
  return fmul(f(6.112), fexp(fdiv(fmul(f(17.67), temperatureC), fadd(temperatureC, f(243.5)))));
}

function inverseSaturationVaporPressureC(vaporPressureHpa: number): number {
  const lnArg = fln(fdiv(vaporPressureHpa, f(6.112)));
  return fdiv(fmul(f(243.5), lnArg), fsub(f(17.67), lnArg));
}

const CCL_EPSILON = f(0.622);
const CCL_KAPPA = f(0.2854);
const KELVIN_OFFSET = f(273.15);
const MIN_CCL_PRESSURE_HPA = 500;
const PRESSURE_LEVEL_BELOW_SURFACE_MARGIN_HPA = 1;
const HEIGHT_ABOVE_SURFACE_MARGIN_M = 20;
const MIXED_LAYER_50_HPA = 50;
const MIXED_LAYER_100_HPA = 100;
const FALLBACK_MIN_DEPTH_HPA = 50;
const FALLBACK_MAX_DEPTH_HPA = 100;
const SURFACE_SATURATION_SPREAD_C = f(0.5);
const REACHABLE_TOLERANCE_C = f(0.5);
const LOW_CCL_AGL_M = 300;
const HIGH_CCL_ABOVE_REFERENCE_M = 2500;
const ZERO_CROSSING_EPSILON_C = f(0.001);
const DISTINCT_PRESSURE_EPSILON_HPA = f(0.05);
const PRESSURE_EPSILON_HPA = f(0.001);

/** Kotlin `Float.MAX_VALUE`. */
const FLOAT_MAX_VALUE = 3.4028234663852886e38;

const WARNING_SURFACE_NEAR_SATURATION = "Surface near saturation: fog/low cloud possible";
const WARNING_NO_CCL =
  "No CCL found below 500 hPa: likely very high cloud base, blue thermals, or insufficient profile depth";
const WARNING_MULTIPLE_INTERSECTIONS =
  "Layered profile/inversion. First Cu base may be near lowest CCL, but cloud-base estimate is uncertain.";
const WARNING_THEORETICAL_ONLY =
  "CCL theoretical only; surface heating is insufficient. Cu unlikely, blue thermals possible.";
const WARNING_VERY_LOW_CCL = "Very low CCL: fog/stratus/covered slopes risk.";
const WARNING_HIGH_CCL =
  "High CCL: good ceiling if reachable, but likely dry/blue sections and harder thermal finding.";
const WARNING_CCL_NEAR_TAKEOFF = "CCL below/near takeoff: fog/low cloud or covered slope risk.";
