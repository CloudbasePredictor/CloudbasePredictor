/**
 * Parcel analysis (LCL, dry thermal top, CCL-derived cloud base, moist
 * equilibrium level, CAPE/CIN and thermal cells).
 *
 * 1:1 port of `domain/forecast/ParcelAnalysis.kt`. Function names, control flow
 * and floating-point operation order are preserved. All arithmetic is float32
 * to match the Kotlin `Float` engine — see `float32.ts`.
 */

import { analyzeCclHourly, primaryCclResult } from "./cclAnalysis";
import { sortedByDescending } from "./collections";
import {
  coerceAtLeast,
  coerceAtMost,
  coerceIn,
  f,
  fadd,
  fdiv,
  fexp,
  fln,
  fmul,
  fpow,
  fsqrt,
  fsub,
  toInt,
} from "./float32";

/**
 * Result of a full parcel analysis for one time slot. All altitudes are km ASL.
 */
export interface ParcelAnalysisResult {
  dryThermalTopKm: number;
  lclKm: number;
  lclPressureHpa: number;
  cclKm: number | null;
  cclPressureHpa: number | null;
  tconC: number | null;
  cloudBaseKm: number | null;
  moistEquilibriumTopKm: number | null;
  computedCapeJKg: number;
  computedCinJKg: number;
  modelCapeJKg: number | null;
  thermalCells: ThermalCell[];
  surfaceHeatingC: number;
}

export interface ThermalCell {
  startAltitudeKm: number;
  endAltitudeKm: number;
  strengthMps: number;
  buoyancyC: number;
}

/** One level in the atmospheric profile used for parcel analysis. */
export interface ProfileLevel {
  pressureHpa: number;
  temperatureC: number;
  dewPointC: number | null;
  heightKm: number;
  relativeHumidityPercent: number | null;
  cloudCoverPercent: number | null;
  windSpeedKmh: number | null;
  isSynthetic: boolean;
}

export interface SurfaceHeatingInput {
  hourOfDay: number;
  shortwaveRadiationWm2: number | null;
  previousShortwaveRadiationWm2: number | null;
  cloudCoverLowPercent: number | null;
  cloudCoverMidPercent: number | null;
  cloudCoverHighPercent: number | null;
  precipitationMm: number | null;
  isDay: boolean | null;
}

export interface SurfaceHeatingEstimate {
  triggerExcessC: number;
  conservativeDryTopExcessC: number;
  nominalDryTopExcessC: number;
  optimisticDryTopExcessC: number;
  effectiveRadiationWm2: number | null;
}

// ────────────────────────────────────────────────────────────────────
// Thermodynamic constants
// ────────────────────────────────────────────────────────────────────

/** Poisson constant R_d / C_pd for dry air. */
const KAPPA = f(0.286);

/** Gravity, m/s². */
const G = f(9.81);

/** Specific gas constant for dry air, J/(kg·K). */
const RD = 287;

/** Specific heat of dry air at constant pressure, J/(kg·K). */
const CPD = 1004;

/** Ratio of gas constants Rd/Rv used in mixing-ratio formulas. */
const EPSILON = f(0.622);

/** Minimum parcel buoyancy (°C) to count as a usable thermal. */
const MIN_BUOYANCY_C = f(0.3);

/** Cap for surface heating estimate, °C. */
const MAX_SURFACE_HEATING_C = 8;

/** Default conservative surface heating when no radiation data available, °C. */
const DEFAULT_SURFACE_HEATING_C = 2;

/** Minimum surface heating during daytime, °C. */
const MIN_DAYTIME_SURFACE_HEATING_C = f(0.5);

/** Maximum reference shortwave radiation for scaling, W/m². */
const REFERENCE_RADIATION_WM2 = 900;

/** Scaling factor to convert local buoyancy (K) into updraft m/s. */
const BUOYANCY_TO_UPDRAFT_SCALE = f(0.75);

/** Maximum thermal strength cap, m/s. */
const MAX_THERMAL_STRENGTH_MPS = 10;

/** Minimum displayable thermal strength, m/s. */
const MIN_DISPLAY_STRENGTH_MPS = f(0.2);

const KELVIN = f(273.15);

// ────────────────────────────────────────────────────────────────────
// Public API
// ────────────────────────────────────────────────────────────────────

export function analyzeParcel(
  profile: ProfileLevel[],
  surfaceTemperatureC: number,
  surfaceDewPointC: number,
  surfacePressureHpa: number,
  elevationKm: number,
  heatingInput: SurfaceHeatingInput,
  modelCapeJKg: number | null = null,
): ParcelAnalysisResult | null {
  const aboveSurface = sortedByDescending(
    profile.filter(
      (it) =>
        it.heightKm >= fsub(elevationKm, f(0.01)) && it.pressureHpa < fadd(surfacePressureHpa, 1),
    ),
    (it) => it.pressureHpa,
  );

  if (aboveSurface.length < 2) return null;

  const surfaceHeatingC = estimateSurfaceHeating(heatingInput);
  const parcelTempC = fadd(surfaceTemperatureC, surfaceHeatingC);
  const parcelThetaK = potentialTemperatureK(parcelTempC, surfacePressureHpa);
  const surfaceMixingRatio = satMixingRatioGKg(surfaceDewPointC, surfacePressureHpa);

  const lclResult = findLcl(
    parcelThetaK,
    surfaceMixingRatio,
    surfacePressureHpa,
    aboveSurface,
    elevationKm,
  );

  const cclPrimary = primaryCclResult(
    analyzeCclHourly({
      time: "",
      surfaceTemperatureC,
      surfaceDewPointC,
      surfacePressureHpa,
      surfaceElevationM: fmul(elevationKm, 1000),
      pressureLevels: aboveSurface
        .filter(
          (it) =>
            Math.abs(fsub(it.pressureHpa, surfacePressureHpa)) > 1 ||
            Math.abs(fsub(it.heightKm, elevationKm)) > f(0.03),
        )
        .map((level) => ({
          pressureHpa: level.pressureHpa,
          temperatureC: level.temperatureC,
          dewPointC: level.dewPointC,
          heightMslM: fmul(level.heightKm, 1000),
          isSynthetic: level.isSynthetic,
        })),
      takeoffElevationM: null,
    }),
  );
  const cclKm = cclPrimary?.cclHeightMslM != null ? fdiv(cclPrimary.cclHeightMslM, 1000) : null;
  const cclPressureHpa = cclPrimary?.cclPressureHpa ?? null;
  const tconC = cclPrimary?.convectiveTemperatureC ?? null;

  const dryTopKm = findDryThermalTop(parcelThetaK, aboveSurface, elevationKm);
  const lclTemperatureC = dryAdiabatTempC(parcelThetaK, lclResult.pressureHpa);

  const cloudBaseKm =
    cclPrimary?.reachable === true && cclKm !== null && dryTopKm >= fsub(cclKm, f(0.05))
      ? cclKm
      : null;

  let moistEquilibriumTopKm: number | null = null;
  if (cloudBaseKm !== null && cclPrimary !== null) {
    moistEquilibriumTopKm = findMoistEquilibriumTop(
      cclPrimary.cclTemperatureC ?? lclTemperatureC,
      cclPrimary.cclPressureHpa ?? lclResult.pressureHpa,
      aboveSurface,
      cloudBaseKm,
    );
  }

  const capeCin = computeCapeCin(
    parcelThetaK,
    lclTemperatureC,
    lclResult.pressureHpa,
    surfaceMixingRatio,
    surfacePressureHpa,
    aboveSurface,
  );

  const thermalCells = buildThermalCells(
    parcelThetaK,
    surfaceMixingRatio,
    lclResult.pressureHpa,
    aboveSurface,
    elevationKm,
    dryTopKm,
  );

  return {
    dryThermalTopKm: dryTopKm,
    lclKm: lclResult.heightKm,
    lclPressureHpa: lclResult.pressureHpa,
    cclKm,
    cclPressureHpa,
    tconC,
    cloudBaseKm,
    moistEquilibriumTopKm,
    computedCapeJKg: capeCin[0],
    computedCinJKg: capeCin[1],
    modelCapeJKg,
    thermalCells,
    surfaceHeatingC,
  };
}

// ────────────────────────────────────────────────────────────────────
// Surface heating estimation
// ────────────────────────────────────────────────────────────────────

export function estimateSurfaceHeating(input: SurfaceHeatingInput): number {
  if (input.isDay === false || input.hourOfDay < 6 || input.hourOfDay > 20) {
    return 0;
  }

  const radiationFraction =
    input.shortwaveRadiationWm2 !== null
      ? coerceIn(fdiv(input.shortwaveRadiationWm2, REFERENCE_RADIATION_WM2), 0, f(1.2))
      : null;

  let cloudPenalty: number;
  if (radiationFraction !== null) {
    const low = input.cloudCoverLowPercent ?? 0;
    cloudPenalty = low >= 85 ? f(0.6) : low >= 70 ? f(0.4) : low >= 50 ? f(0.2) : 0;
  } else {
    const low = fdiv(input.cloudCoverLowPercent ?? 0, 100);
    const mid = fdiv(input.cloudCoverMidPercent ?? 0, 100);
    const high = fdiv(input.cloudCoverHighPercent ?? 0, 100);
    cloudPenalty = coerceIn(
      fadd(fadd(fmul(low, f(0.7)), fmul(mid, f(0.4))), fmul(high, f(0.15))),
      0,
      f(0.85),
    );
  }

  const precipPenalty = (input.precipitationMm ?? 0) > f(0.1) ? f(0.6) : 0;

  const solarFactor = solarElevationFactor(input.hourOfDay);

  const baseHeating =
    radiationFraction !== null
      ? fmul(fmul(radiationFraction, MAX_SURFACE_HEATING_C), solarFactor)
      : fmul(fmul(DEFAULT_SURFACE_HEATING_C, solarFactor), fsub(1, cloudPenalty));

  const heating = fmul(fmul(baseHeating, fsub(1, cloudPenalty)), fsub(1, precipPenalty));
  return coerceIn(heating, MIN_DAYTIME_SURFACE_HEATING_C, MAX_SURFACE_HEATING_C);
}

export function estimateThermalHeatingEstimate(input: SurfaceHeatingInput): SurfaceHeatingEstimate {
  const effectiveRadiation =
    input.shortwaveRadiationWm2 !== null && input.previousShortwaveRadiationWm2 !== null
      ? fadd(
          fmul(input.shortwaveRadiationWm2, f(0.65)),
          fmul(input.previousShortwaveRadiationWm2, f(0.35)),
        )
      : input.shortwaveRadiationWm2;
  const triggerExcess = estimateSurfaceHeating({
    ...input,
    shortwaveRadiationWm2: effectiveRadiation,
  });
  const precipitation = input.precipitationMm ?? 0;
  const lowCloud = input.cloudCoverLowPercent ?? 100;
  const optimisticAllowed = precipitation <= f(0.1) && lowCloud < 40;
  const optimisticExcess = optimisticAllowed
    ? coerceAtMost(fmul(triggerExcess, f(0.8)), 5)
    : coerceAtMost(fmul(triggerExcess, f(0.55)), 3);
  return {
    triggerExcessC: triggerExcess,
    conservativeDryTopExcessC: coerceAtMost(fmul(triggerExcess, f(0.35)), 2),
    nominalDryTopExcessC: coerceAtMost(fmul(triggerExcess, f(0.55)), 3),
    optimisticDryTopExcessC: optimisticExcess,
    effectiveRadiationWm2: effectiveRadiation,
  };
}

/** Simple solar elevation factor peaking at 13:00 local. */
export function solarElevationFactor(hourOfDay: number): number {
  const dist = Math.abs(fsub(hourOfDay, 13));
  return coerceIn(fsub(1, fdiv(dist, 8)), 0, 1);
}

// ────────────────────────────────────────────────────────────────────
// Thermodynamic helpers (pure functions)
// ────────────────────────────────────────────────────────────────────

export function potentialTemperatureK(temperatureC: number, pressureHpa: number): number {
  return fmul(fadd(temperatureC, KELVIN), fpow(fdiv(1000, pressureHpa), KAPPA));
}

export function dryAdiabatTempC(thetaK: number, pressureHpa: number): number {
  return fsub(fmul(thetaK, fpow(fdiv(pressureHpa, 1000), KAPPA)), KELVIN);
}

export function satMixingRatioGKg(temperatureC: number, pressureHpa: number): number {
  const es = satVaporPressureHpa(temperatureC);
  const denom = fsub(pressureHpa, es);
  return denom > f(0.01) ? fdiv(fmul(622, es), denom) : fdiv(fmul(622, es), f(0.01));
}

export function moistAdiabatTempC(thetaWK: number, pressureHpa: number): number {
  let tempK = fmul(thetaWK, fpow(fdiv(pressureHpa, 1000), KAPPA));
  for (let i = 0; i < 4; i++) {
    const es = satVaporPressureHpa(fsub(tempK, KELVIN));
    const ws = fdiv(fmul(f(0.622), es), coerceAtLeast(fsub(pressureHpa, es), f(0.01)));
    const lv = fsub(f(2.501e6), fmul(2370, fsub(tempK, KELVIN)));
    const correction = fdiv(fmul(lv, ws), fmul(1004, tempK));
    tempK = fmul(thetaWK, fpow(fdiv(pressureHpa, 1000), fdiv(KAPPA, fadd(1, correction))));
  }
  return fsub(tempK, KELVIN);
}

export function moistAdiabatTempFromPointC(
  startTemperatureC: number,
  startPressureHpa: number,
  targetPressureHpa: number,
  stepHpa = 2,
): number {
  if (Math.abs(fsub(targetPressureHpa, startPressureHpa)) < f(0.01)) return startTemperatureC;

  let temperatureK = fadd(startTemperatureC, KELVIN);
  let pressureHpa = startPressureHpa;
  const direction = targetPressureHpa < startPressureHpa ? -1 : 1;
  const step = fmul(stepHpa, direction);

  while (
    (direction < 0 && pressureHpa > fadd(targetPressureHpa, f(0.01))) ||
    (direction > 0 && pressureHpa < fsub(targetPressureHpa, f(0.01)))
  ) {
    const nextPressureHpa =
      Math.abs(fsub(targetPressureHpa, pressureHpa)) <= Math.abs(step)
        ? targetPressureHpa
        : fadd(pressureHpa, step);
    const midpointPressureHpa = fdiv(fadd(pressureHpa, nextPressureHpa), 2);
    const temperatureC = fsub(temperatureK, KELVIN);
    const saturationVaporPressureHpa = satVaporPressureHpa(temperatureC);
    const saturationMixingRatioKgKg = fdiv(
      fmul(EPSILON, saturationVaporPressureHpa),
      coerceAtLeast(fsub(midpointPressureHpa, saturationVaporPressureHpa), f(0.01)),
    );
    const latentHeatJKg = fsub(f(2.501e6), fmul(2370, temperatureC));
    const moistLapseRateKPerM = fdiv(
      fmul(
        G,
        fadd(1, fdiv(fmul(latentHeatJKg, saturationMixingRatioKgKg), fmul(RD, temperatureK))),
      ),
      fadd(
        CPD,
        fdiv(
          fmul(fmul(fmul(latentHeatJKg, latentHeatJKg), saturationMixingRatioKgKg), EPSILON),
          fmul(fmul(RD, temperatureK), temperatureK),
        ),
      ),
    );
    const virtualTemperatureK = fmul(
      temperatureK,
      fadd(1, fmul(f(0.61), saturationMixingRatioKgKg)),
    );
    const dTemperatureDpHpa = fdiv(
      fmul(fmul(moistLapseRateKPerM, RD), virtualTemperatureK),
      fmul(G, midpointPressureHpa),
    );

    temperatureK = fadd(temperatureK, fmul(dTemperatureDpHpa, fsub(nextPressureHpa, pressureHpa)));
    pressureHpa = nextPressureHpa;
  }

  return fsub(temperatureK, KELVIN);
}

export function satVaporPressureHpa(temperatureC: number): number {
  return fmul(f(6.112), fexp(fdiv(fmul(f(17.67), temperatureC), fadd(temperatureC, f(243.5)))));
}

export function relativeHumidityFraction(temperatureC: number, dewPointC: number): number {
  const saturationAtTemp = satVaporPressureHpa(temperatureC);
  const saturationAtDewPoint = satVaporPressureHpa(dewPointC);
  return coerceIn(fdiv(saturationAtDewPoint, saturationAtTemp), 0, 1);
}

export function mixingRatioTemperatureC(mixingRatioGKg: number, pressureHpa: number): number {
  const vaporPressure = fdiv(fmul(mixingRatioGKg, pressureHpa), fadd(622, mixingRatioGKg));
  const lnRatio = fln(fdiv(vaporPressure, f(6.112)));
  return fdiv(fmul(f(243.5), lnRatio), fsub(f(17.67), lnRatio));
}

/** Estimate surface pressure (hPa) from elevation (m) using ISA barometric formula. */
export function estimateSurfacePressure(elevationM: number): number {
  return f(1013.25 * (1.0 - (0.0065 * elevationM) / 288.15) ** 5.2561);
}

export function interpolateTemperatureCAtPressure(
  profile: ProfileLevel[],
  pressureHpa: number,
): number | null {
  const sorted = sortedByDescending(profile, (it) => it.pressureHpa);
  if (sorted.length === 0) return null;
  const exact = sorted.find((it) => it.pressureHpa === pressureHpa);
  if (exact !== undefined) return exact.temperatureC;
  for (let i = 0; i < sorted.length - 1; i++) {
    const lower = sorted[i];
    const upper = sorted[i + 1];
    if (pressureHpa <= lower.pressureHpa && pressureHpa >= upper.pressureHpa) {
      const fraction = fdiv(
        fsub(lower.pressureHpa, pressureHpa),
        fsub(lower.pressureHpa, upper.pressureHpa),
      );
      return fadd(lower.temperatureC, fmul(fraction, fsub(upper.temperatureC, lower.temperatureC)));
    }
  }
  return null;
}

export function interpolateHeightKmAtPressure(
  profile: ProfileLevel[],
  pressureHpa: number,
): number | null {
  const sorted = sortedByDescending(profile, (it) => it.pressureHpa);
  if (sorted.length === 0) return null;
  const exact = sorted.find((it) => it.pressureHpa === pressureHpa);
  if (exact !== undefined) return exact.heightKm;
  for (let i = 0; i < sorted.length - 1; i++) {
    const lower = sorted[i];
    const upper = sorted[i + 1];
    if (pressureHpa <= lower.pressureHpa && pressureHpa >= upper.pressureHpa) {
      const fraction = fdiv(
        fsub(lower.pressureHpa, pressureHpa),
        fsub(lower.pressureHpa, upper.pressureHpa),
      );
      return fadd(lower.heightKm, fmul(fraction, fsub(upper.heightKm, lower.heightKm)));
    }
  }
  return null;
}

// ────────────────────────────────────────────────────────────────────
// Internal analysis steps
// ────────────────────────────────────────────────────────────────────

interface PressureHeightResult {
  pressureHpa: number;
  heightKm: number;
}

function findLcl(
  parcelThetaK: number,
  surfaceMixingRatio: number,
  _surfacePressureHpa: number,
  profile: ProfileLevel[],
  elevationKm: number,
): PressureHeightResult {
  let prevLevel: ProfileLevel | null = null;
  for (const level of profile) {
    const dryTemp = dryAdiabatTempC(parcelThetaK, level.pressureHpa);
    const satMr = satMixingRatioGKg(dryTemp, level.pressureHpa);
    if (satMr <= surfaceMixingRatio) {
      if (prevLevel !== null) {
        const prevDryTemp = dryAdiabatTempC(parcelThetaK, prevLevel.pressureHpa);
        const prevSatMr = satMixingRatioGKg(prevDryTemp, prevLevel.pressureHpa);
        const frac =
          fsub(prevSatMr, satMr) > f(0.001)
            ? fdiv(fsub(prevSatMr, surfaceMixingRatio), fsub(prevSatMr, satMr))
            : f(0.5);
        const interpHeight = fadd(
          prevLevel.heightKm,
          fmul(frac, fsub(level.heightKm, prevLevel.heightKm)),
        );
        const interpPressure = fadd(
          prevLevel.pressureHpa,
          fmul(frac, fsub(level.pressureHpa, prevLevel.pressureHpa)),
        );
        return { pressureHpa: interpPressure, heightKm: coerceAtLeast(interpHeight, elevationKm) };
      }
      return {
        pressureHpa: level.pressureHpa,
        heightKm: coerceAtLeast(level.heightKm, elevationKm),
      };
    }
    prevLevel = level;
  }
  const top = profile[profile.length - 1];
  return { pressureHpa: top.pressureHpa, heightKm: top.heightKm };
}

function findDryThermalTop(
  parcelThetaK: number,
  profile: ProfileLevel[],
  elevationKm: number,
): number {
  let prevLevel: ProfileLevel | null = null;
  for (const level of profile) {
    const parcelTemp = dryAdiabatTempC(parcelThetaK, level.pressureHpa);
    if (parcelTemp < level.temperatureC) {
      if (prevLevel !== null) {
        const prevParcelTemp = dryAdiabatTempC(parcelThetaK, prevLevel.pressureHpa);
        const prevDiff = fsub(prevParcelTemp, prevLevel.temperatureC);
        const currDiff = fsub(parcelTemp, level.temperatureC);
        const frac =
          fsub(prevDiff, currDiff) > f(0.001) ? fdiv(prevDiff, fsub(prevDiff, currDiff)) : f(0.5);
        return coerceAtLeast(
          fadd(prevLevel.heightKm, fmul(frac, fsub(level.heightKm, prevLevel.heightKm))),
          elevationKm,
        );
      }
      return coerceAtLeast(level.heightKm, elevationKm);
    }
    prevLevel = level;
  }
  return profile.length > 0 ? profile[profile.length - 1].heightKm : elevationKm;
}

function findMoistEquilibriumTop(
  saturationPointTemperatureC: number,
  saturationPointPressureHpa: number,
  profile: ProfileLevel[],
  cloudBaseKm: number,
): number | null {
  const aboveCloudBase = profile.filter((it) => it.heightKm >= fsub(cloudBaseKm, f(0.01)));
  if (aboveCloudBase.length < 2) return null;

  let foundBuoyant = false;
  let prevLevel: ProfileLevel | null = null;
  for (const level of aboveCloudBase) {
    const moistTemp = moistAdiabatTempFromPointC(
      saturationPointTemperatureC,
      saturationPointPressureHpa,
      level.pressureHpa,
    );
    if (moistTemp > level.temperatureC) {
      foundBuoyant = true;
    } else if (foundBuoyant) {
      if (prevLevel !== null) {
        const prevMoistTemp = moistAdiabatTempFromPointC(
          saturationPointTemperatureC,
          saturationPointPressureHpa,
          prevLevel.pressureHpa,
        );
        const prevDiff = fsub(prevMoistTemp, prevLevel.temperatureC);
        const currDiff = fsub(moistTemp, level.temperatureC);
        const frac =
          fsub(prevDiff, currDiff) > f(0.001) ? fdiv(prevDiff, fsub(prevDiff, currDiff)) : f(0.5);
        return fadd(prevLevel.heightKm, fmul(frac, fsub(level.heightKm, prevLevel.heightKm)));
      }
      return level.heightKm;
    }
    prevLevel = level;
  }

  return foundBuoyant ? profile[profile.length - 1].heightKm : null;
}

function computeCapeCin(
  parcelThetaK: number,
  lclTemperatureC: number,
  lclPressureHpa: number,
  surfaceMixingRatio: number,
  _surfacePressureHpa: number,
  profile: ProfileLevel[],
): [number, number] {
  if (profile.length < 2) return [0, 0];

  let cape = 0;
  let cin = 0;
  let reachedLcl = false;

  for (let i = 0; i < profile.length - 1; i++) {
    const lower = profile[i];
    const upper = profile[i + 1];
    const dz = fmul(fsub(upper.heightKm, lower.heightKm), 1000);
    if (dz <= 0) continue;

    const midPressure = fdiv(fadd(lower.pressureHpa, upper.pressureHpa), 2);
    const envTempMid = fdiv(fadd(lower.temperatureC, upper.temperatureC), 2);

    let parcelTemp: number;
    if (!reachedLcl) {
      const dryTemp = dryAdiabatTempC(parcelThetaK, midPressure);
      const satMr = satMixingRatioGKg(dryTemp, midPressure);
      if (satMr <= surfaceMixingRatio) {
        reachedLcl = true;
        parcelTemp = moistAdiabatTempFromPointC(lclTemperatureC, lclPressureHpa, midPressure);
      } else {
        parcelTemp = dryTemp;
      }
    } else {
      parcelTemp = moistAdiabatTempFromPointC(lclTemperatureC, lclPressureHpa, midPressure);
    }

    const envTempK = fadd(envTempMid, KELVIN);
    const buoyancy = fmul(fdiv(fmul(G, fsub(parcelTemp, envTempMid)), envTempK), dz);

    if (buoyancy > 0) {
      cape = fadd(cape, buoyancy);
    } else {
      cin = fsub(cin, buoyancy);
    }
  }

  return [cape, cin];
}

function buildThermalCells(
  parcelThetaK: number,
  _surfaceMixingRatio: number,
  _lclPressureHpa: number,
  profile: ProfileLevel[],
  elevationKm: number,
  dryTopKm: number,
): ThermalCell[] {
  const cells: ThermalCell[] = [];
  if (profile.length < 2) return cells;

  for (let i = 0; i < profile.length - 1; i++) {
    const lower = profile[i];
    const upper = profile[i + 1];
    if (upper.heightKm <= elevationKm) continue;
    if (lower.heightKm >= dryTopKm) break;

    const cellBottom = Math.max(lower.heightKm, elevationKm);
    const cellTop = Math.min(upper.heightKm, dryTopKm);
    if (cellTop <= fadd(cellBottom, f(0.001))) continue;

    const midPressure = fdiv(fadd(lower.pressureHpa, upper.pressureHpa), 2);
    const parcelTemp = dryAdiabatTempC(parcelThetaK, midPressure);
    const envTemp = fdiv(fadd(lower.temperatureC, upper.temperatureC), 2);
    const buoyancyC = fsub(parcelTemp, envTemp);

    if (buoyancyC < MIN_BUOYANCY_C) continue;

    const dz = fmul(fsub(cellTop, cellBottom), 1000);
    const envTempK = fadd(envTemp, KELVIN);
    const buoyancyAccel = fdiv(fmul(G, buoyancyC), envTempK);
    const rawStrength = fmul(
      fsqrt(Math.max(0, fmul(fmul(2, buoyancyAccel), dz))),
      BUOYANCY_TO_UPDRAFT_SCALE,
    );
    const strength = coerceIn(rawStrength, 0, MAX_THERMAL_STRENGTH_MPS);

    if (strength >= MIN_DISPLAY_STRENGTH_MPS) {
      cells.push({
        startAltitudeKm: cellBottom,
        endAltitudeKm: cellTop,
        strengthMps: fdiv(toInt(fmul(strength, 10)), 10),
        buoyancyC,
      });
    }
  }

  return cells;
}
