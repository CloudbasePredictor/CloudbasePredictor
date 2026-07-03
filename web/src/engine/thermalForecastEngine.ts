/**
 * Thermal forecast engine: turns an atmospheric profile into per-layer updraft
 * strengths, thermal top scenarios, cloud base status and confidence.
 *
 * 1:1 port of `domain/forecast/ThermalForecastEngine.kt`. Function names,
 * control flow and floating-point operation order are preserved. All arithmetic
 * is float32 to match the Kotlin `Float` engine — see `float32.ts`.
 */

import {
  analyzeCclHourly,
  type CclHourlyInput,
  type CclHourlyResult,
  primaryCclResult,
} from "./cclAnalysis";
import { distinctBy, sortedBy, sortedByDescending } from "./collections";
import {
  averageOfFloats,
  coerceAtLeast,
  coerceAtMost,
  coerceIn,
  f,
  fadd,
  fdiv,
  fmul,
  fsqrt,
  fsub,
  toInt,
} from "./float32";
import {
  dryAdiabatTempC,
  estimateThermalHeatingEstimate,
  moistAdiabatTempFromPointC,
  type ProfileLevel,
  potentialTemperatureK,
  type SurfaceHeatingInput,
  satMixingRatioGKg,
} from "./parcelAnalysis";

export type ThermalForecastConfidence = "HIGH" | "MEDIUM" | "LOW";

export type ThermalLimitingReason =
  | "SURFACE_HEATING"
  | "INVERSION"
  | "CLOUD_BASE"
  | "PROFILE_TOP"
  | "PRECIPITATION"
  | "WEAK_RADIATION"
  | "WIND_SHEAR"
  | "MISSING_DATA";

export type ThermalForecastWarning =
  | "MISSING_PBL"
  | "PBL_EXCEEDED"
  | "MISSING_CIN"
  | "MISSING_LIFTED_INDEX"
  | "MISSING_CCL"
  | "MOUNTAIN_OROGRAPHIC_OVERRIDE"
  | "NEAR_SURFACE_PROFILE_MISMATCH";

export type ThermalCloudBaseStatus = "REACHABLE" | "UNREACHABLE" | "NO_CCL" | "UNKNOWN";

export type ThermalLayerSourceQuality = "REAL" | "INTERPOLATED" | "SYNTHETIC";

/** Ordinal order for `ThermalForecastConfidence` (Kotlin enum `.ordinal`). */
export const CONFIDENCE_ORDINAL: Record<ThermalForecastConfidence, number> = {
  HIGH: 0,
  MEDIUM: 1,
  LOW: 2,
};

/** Ordinal order for `ThermalLayerSourceQuality` (Kotlin enum `.ordinal`). */
export const SOURCE_QUALITY_ORDINAL: Record<ThermalLayerSourceQuality, number> = {
  REAL: 0,
  INTERPOLATED: 1,
  SYNTHETIC: 2,
};

/** Ordinal order for `ThermalCloudBaseStatus` (Kotlin enum `.ordinal`). */
export const CLOUD_BASE_STATUS_ORDINAL: Record<ThermalCloudBaseStatus, number> = {
  REACHABLE: 0,
  UNREACHABLE: 1,
  NO_CCL: 2,
  UNKNOWN: 3,
};

export interface ThermalSourceLevel {
  pressureHpa: number;
  altitudeKm: number;
  isSynthetic: boolean;
}

export interface ThermalForecastInput {
  profile: ProfileLevel[];
  surfaceTemperatureC: number;
  surfaceDewPointC: number;
  surfacePressureHpa: number;
  elevationKm: number;
  heatingInput: SurfaceHeatingInput;
  modelCapeJKg: number | null;
  modelCinJKg: number | null;
  liftedIndexC: number | null;
  boundaryLayerHeightM: number | null;
}

export interface ThermalForecastLayer {
  startAltitudeKm: number;
  endAltitudeKm: number;
  updraftLowMps: number;
  updraftNominalMps: number;
  updraftHighMps: number;
  confidence: ThermalForecastConfidence;
  visualDepthM: number;
  effectiveDepthM: number;
  pressureBottomHpa: number | null;
  pressureTopHpa: number | null;
  sourceQuality: ThermalLayerSourceQuality;
  warnings: ThermalForecastWarning[];
}

export interface ThermalForecastResult {
  topLowKm: number;
  topNominalKm: number;
  topHighKm: number;
  updraftLowMps: number;
  updraftNominalMps: number;
  updraftHighMps: number;
  confidence: ThermalForecastConfidence;
  limitingReason: ThermalLimitingReason;
  lowerSourceLevel: ThermalSourceLevel | null;
  upperSourceLevel: ThermalSourceLevel | null;
  lclKm: number;
  cclKm: number | null;
  cloudBaseKm: number | null;
  moistEquilibriumTopKm: number | null;
  thermalEnergyJKg: number;
  modelCapeJKg: number | null;
  modelCinJKg: number | null;
  normalizedCinJKg: number | null;
  liftedIndexC: number | null;
  boundaryLayerHeightM: number | null;
  triggerExcessC: number;
  dryTopExcessC: number;
  effectiveRadiationWm2: number | null;
  surfaceTemperatureC: number;
  surfacePressureHpa: number;
  elevationKm: number;
  parcelStartTemperatureC: number;
  dryTopAglM: number;
  computedCinJKg: number;
  cloudBaseStatus: ThermalCloudBaseStatus;
  warnings: ThermalForecastWarning[];
  usedPressureLevels: ThermalSourceLevel[];
  layers: ThermalForecastLayer[];
  pressureLevelAltitudesKm: number[];
}

interface ThermalScenario {
  name: string;
  surfaceHeatingC: number;
}

interface ThermalTopScenarioResult {
  topKm: number;
  bracketLower: ProfileLevel | null;
  bracketUpper: ProfileLevel | null;
  profileTopLimited: boolean;
}

function bracketDepthKm(result: ThermalTopScenarioResult): number {
  return result.bracketLower !== null && result.bracketUpper !== null
    ? Math.abs(fsub(result.bracketUpper.heightKm, result.bracketLower.heightKm))
    : 0;
}

interface ThermalDamping {
  factor: number;
  maxUpdraftMps: number;
  weakRadiation: boolean;
  precipitation: boolean;
  strongWindShear: boolean;
  zeroCape: boolean;
  strongCin: boolean;
  heavyLowCloud: boolean;
  shallowBoundaryLayer: boolean;
  missingBoundaryLayer: boolean;
}

interface ValidatedThermalProfile {
  levels: ProfileLevel[];
  warnings: ThermalForecastWarning[];
}

interface PblSanity {
  exceeded: boolean;
  severelyExceeded: boolean;
  mountainOrographicOverride: boolean;
  pblTopKm: number | null;
}

interface InterpolatedProfilePoint {
  pressureHpa: number;
  temperatureC: number;
  sourceQuality: ThermalLayerSourceQuality;
}

interface DryCapeCin {
  capeJKg: number;
  cinJKg: number;
}

interface PressureHeight {
  pressureHpa: number;
  heightKm: number;
}

const HEATING_CONSERVATIVE = "conservative";
const HEATING_NOMINAL = "nominal";
const HEATING_OPTIMISTIC = "optimistic";
const GRAVITY_MPS2 = f(9.81);
const KELVIN_OFFSET = f(273.15);
const UPDRAFT_SCALE = f(0.56);
const MAX_UPDRAFT_MPS = 10;
const MAX_EFFECTIVE_UPDRAFT_LAYER_DEPTH_M = 230;
const THERMAL_LAYER_BIN_DEPTH_KM = f(0.23);
const MIN_DISPLAY_UPDRAFT_MPS = f(0.2);
const MIN_LAYER_BUOYANCY_C = f(0.25);
const MIN_TOP_BUOYANCY_C = 0;
const HEIGHT_EPSILON_KM = f(0.001);
const SURFACE_MATCH_TOLERANCE_KM = f(0.03);
const SURFACE_PRESSURE_TOLERANCE_HPA = 1;
const PRESSURE_LEVEL_MIN_AGL_KM = f(0.02);
const NEAR_SURFACE_PROFILE_CHECK_M = 80;
const NEAR_SURFACE_MAX_T2M_DIFFERENCE_C = f(2.5);
const CLOUD_BASE_REACH_TOLERANCE_KM = f(0.05);
const SHALLOW_THERMAL_DEPTH_KM = f(0.2);
const TOP_MOISTURE_CONTEXT_KM = f(0.75);
const DRY_THERMAL_SUPPORT_RADIATION_WM2 = 500;
const DRY_THERMAL_SUPPORT_LOW_CLOUD_PERCENT = 40;
const HEAVY_LOW_CLOUD_PERCENT = 80;
const DRY_THERMAL_SUPPORT_BOUNDARY_LAYER_M = 700;
const SHALLOW_BOUNDARY_LAYER_M = 700;
const STRONG_CIN_JKG = 150;
const PBL_EXCEED_FACTOR = f(1.35);
const PBL_EXCEED_MARGIN_M = 300;
const PBL_SEVERE_EXCEED_FACTOR = f(1.8);
const PBL_SEVERE_EXCEED_MARGIN_M = 600;
const MOUNTAIN_OVERRIDE_MIN_ELEVATION_KM = f(0.8);
const MOUNTAIN_OVERRIDE_MIN_PBL_M = 700;
const MOUNTAIN_OVERRIDE_MIN_POSITIVE_INTERVALS = 2;
const MISSING_CCL_FALLBACK_DISPLAY_DEPTH_KM = f(1.2);

export function analyze(input: ThermalForecastInput): ThermalForecastResult | null {
  const validatedProfile = buildValidatedProfileWithSurface(input);
  const profile = validatedProfile.levels;
  if (profile.length < 2) return null;

  const warnings = new OrderedWarningSet();
  warnings.addAll(validatedProfile.warnings);
  const normalizedCinJKg = input.modelCinJKg !== null ? Math.abs(input.modelCinJKg) : null;
  if (input.boundaryLayerHeightM === null) warnings.add("MISSING_PBL");
  if (input.modelCinJKg === null) warnings.add("MISSING_CIN");
  if (input.liftedIndexC === null) warnings.add("MISSING_LIFTED_INDEX");

  const heatingEstimate = estimateThermalHeatingEstimate(input.heatingInput);
  const optimisticDryTopExcessC = optimisticDryTopAllowed(input, normalizedCinJKg)
    ? heatingEstimate.optimisticDryTopExcessC
    : heatingEstimate.nominalDryTopExcessC;
  const scenarios: ThermalScenario[] = [
    { name: HEATING_CONSERVATIVE, surfaceHeatingC: heatingEstimate.conservativeDryTopExcessC },
    { name: HEATING_NOMINAL, surfaceHeatingC: heatingEstimate.nominalDryTopExcessC },
    { name: HEATING_OPTIMISTIC, surfaceHeatingC: optimisticDryTopExcessC },
  ];
  const conservativeTop = analyzeTopScenario(input, profile, scenarios[0].surfaceHeatingC);
  const nominalTop = analyzeTopScenario(input, profile, scenarios[1].surfaceHeatingC);
  const optimisticTop = analyzeTopScenario(input, profile, scenarios[2].surfaceHeatingC);

  const nominalThetaK = potentialTemperatureK(
    fadd(input.surfaceTemperatureC, scenarios[1].surfaceHeatingC),
    input.surfacePressureHpa,
  );
  const surfaceMixingRatio = satMixingRatioGKg(input.surfaceDewPointC, input.surfacePressureHpa);
  const lcl = findThermalLcl(nominalThetaK, surfaceMixingRatio, profile, input.elevationKm);
  const cclResults = analyzeCclHourly(toCclInput(input, profile));
  const primaryCcl = primaryCclResult(cclResults);
  const cclKm =
    primaryCcl?.reachable === true && primaryCcl.cclHeightMslM !== null
      ? fdiv(primaryCcl.cclHeightMslM, 1000)
      : null;
  const cloudBaseStatus = resolveCloudBaseStatus(
    primaryCcl,
    profile,
    input.elevationKm,
    nominalTop.topKm,
  );
  if (cloudBaseStatus === "UNKNOWN") {
    warnings.add("MISSING_CCL");
  }
  const cloudBaseKm =
    cloudBaseStatus === "REACHABLE" &&
    cclKm !== null &&
    nominalTop.topKm >= fsub(cclKm, CLOUD_BASE_REACH_TOLERANCE_KM)
      ? cclKm
      : null;
  const moistTopKm =
    cloudBaseKm !== null && primaryCcl !== null
      ? findMoistTop(
          primaryCcl.cclTemperatureC ?? dryAdiabatTempC(nominalThetaK, lcl.pressureHpa),
          primaryCcl.cclPressureHpa ?? lcl.pressureHpa,
          profile,
          cloudBaseKm,
        )
      : null;

  const pblSanity = resolvePblSanity(input, profile, nominalThetaK, nominalTop.topKm);
  if (pblSanity.exceeded) warnings.add("PBL_EXCEEDED");
  if (pblSanity.mountainOrographicOverride) warnings.add("MOUNTAIN_OROGRAPHIC_OVERRIDE");

  const damping = dampingFactor(
    input,
    profile,
    nominalTop.topKm,
    heatingEstimate.effectiveRadiationWm2,
    normalizedCinJKg,
  );
  const layerTopKm = resolveDisplayLayerTopKm(input, nominalTop.topKm, cloudBaseStatus, pblSanity);
  const layers = buildLayers(input, profile, scenarios, layerTopKm, damping, pblSanity);
  const updraftLow = maxOfLayers(layers, (it) => it.updraftLowMps);
  const updraftNominal = maxOfLayers(layers, (it) => it.updraftNominalMps);
  const updraftHigh = maxOfLayers(layers, (it) => it.updraftHighMps);
  const topLowKm = coerceAtLeast(
    Math.min(conservativeTop.topKm, nominalTop.bracketLower?.heightKm ?? nominalTop.topKm),
    input.elevationKm,
  );
  const topHighKm = coerceAtLeast(
    Math.max(optimisticTop.topKm, nominalTop.bracketUpper?.heightKm ?? nominalTop.topKm),
    topLowKm,
  );
  let confidence = resolveConfidence(profile, nominalTop, damping, input);
  if (cloudBaseStatus === "UNKNOWN") {
    confidence = capAt(confidence, "LOW");
  }
  if (pblSanity.exceeded && !pblSanity.mountainOrographicOverride) {
    confidence =
      pblSanity.severelyExceeded || !hasClearDryThermalSupport(input)
        ? capAt(confidence, "LOW")
        : capAt(confidence, "MEDIUM");
  } else if (pblSanity.mountainOrographicOverride) {
    confidence = capAt(confidence, "MEDIUM");
  }
  const limitingReason = resolveLimitingReason(input, nominalTop, cloudBaseKm, damping);
  const dryCapeCin = computeDryCapeCin(nominalThetaK, profile);

  return {
    topLowKm,
    topNominalKm: nominalTop.topKm,
    topHighKm,
    updraftLowMps: updraftLow,
    updraftNominalMps: updraftNominal,
    updraftHighMps: updraftHigh,
    confidence,
    limitingReason,
    lowerSourceLevel:
      nominalTop.bracketLower !== null ? toSourceLevel(nominalTop.bracketLower) : null,
    upperSourceLevel:
      nominalTop.bracketUpper !== null ? toSourceLevel(nominalTop.bracketUpper) : null,
    lclKm: lcl.heightKm,
    cclKm,
    cloudBaseKm,
    moistEquilibriumTopKm: moistTopKm,
    thermalEnergyJKg: computeDryThermalEnergy(nominalThetaK, profile, nominalTop.topKm),
    modelCapeJKg: input.modelCapeJKg,
    modelCinJKg: input.modelCinJKg,
    normalizedCinJKg,
    liftedIndexC: input.liftedIndexC,
    boundaryLayerHeightM: input.boundaryLayerHeightM,
    triggerExcessC: heatingEstimate.triggerExcessC,
    dryTopExcessC: heatingEstimate.nominalDryTopExcessC,
    effectiveRadiationWm2: heatingEstimate.effectiveRadiationWm2,
    surfaceTemperatureC: input.surfaceTemperatureC,
    surfacePressureHpa: input.surfacePressureHpa,
    elevationKm: input.elevationKm,
    parcelStartTemperatureC: fadd(input.surfaceTemperatureC, heatingEstimate.nominalDryTopExcessC),
    dryTopAglM: coerceAtLeast(fmul(fsub(nominalTop.topKm, input.elevationKm), 1000), 0),
    computedCinJKg: dryCapeCin.cinJKg,
    cloudBaseStatus,
    warnings: warnings.toList(),
    usedPressureLevels: profile.map((it) => toSourceLevel(it)),
    layers,
    pressureLevelAltitudesKm: sortedBy(
      distinctBy(
        profile
          .filter(
            (it) =>
              !it.isSynthetic && it.heightKm >= fadd(input.elevationKm, PRESSURE_LEVEL_MIN_AGL_KM),
          )
          .map((it) => it.heightKm),
        (it) => toInt(fmul(it, 1000)),
      ),
      (it) => it,
    ),
  };
}

function buildValidatedProfileWithSurface(input: ThermalForecastInput): ValidatedThermalProfile {
  const warnings: ThermalForecastWarning[] = [];
  const surfaceLevel: ProfileLevel = {
    pressureHpa: input.surfacePressureHpa,
    temperatureC: input.surfaceTemperatureC,
    dewPointC: input.surfaceDewPointC,
    heightKm: input.elevationKm,
    relativeHumidityPercent: null,
    cloudCoverPercent: null,
    windSpeedKmh: null,
    isSynthetic: false,
  };
  const realEnvelopeHeights = input.profile
    .filter((it) => !it.isSynthetic)
    .filter((it) => geometricallyAboveSurface(it, input))
    .map((it) => it.heightKm);
  const realEnvelope =
    realEnvelopeHeights.length > 0
      ? { start: Math.min(...realEnvelopeHeights), endInclusive: Math.max(...realEnvelopeHeights) }
      : null;

  const levels = distinctBy(
    input.profile
      .filter(
        (level) =>
          !(
            Math.abs(fsub(level.heightKm, input.elevationKm)) <= SURFACE_MATCH_TOLERANCE_KM ||
            Math.abs(fsub(level.pressureHpa, input.surfacePressureHpa)) <=
              SURFACE_PRESSURE_TOLERANCE_HPA
          ),
      )
      .filter((level) => geometricallyAboveSurface(level, input))
      .filter(
        (level) =>
          !level.isSynthetic ||
          realEnvelope === null ||
          (level.heightKm >= fsub(realEnvelope.start, HEIGHT_EPSILON_KM) &&
            level.heightKm <= fadd(realEnvelope.endInclusive, HEIGHT_EPSILON_KM)),
      )
      .filter((level) => {
        const aglM = fmul(fsub(level.heightKm, input.elevationKm), 1000);
        if (
          aglM <= NEAR_SURFACE_PROFILE_CHECK_M &&
          Math.abs(fsub(level.temperatureC, input.surfaceTemperatureC)) >
            NEAR_SURFACE_MAX_T2M_DIFFERENCE_C
        ) {
          warnings.push("NEAR_SURFACE_PROFILE_MISMATCH");
          return false;
        }
        return true;
      })
      .sort((a, b) => {
        if (a.pressureHpa < b.pressureHpa) return 1;
        if (a.pressureHpa > b.pressureHpa) return -1;
        return (a.isSynthetic ? 1 : 0) - (b.isSynthetic ? 1 : 0);
      }),
    (it) => toInt(fmul(it.pressureHpa, 10)),
  );

  return {
    levels: sortedByDescending([surfaceLevel, ...levels], (it) => it.pressureHpa),
    warnings: distinctWarnings(warnings),
  };
}

function toCclInput(input: ThermalForecastInput, profile: ProfileLevel[]): CclHourlyInput {
  return {
    time: "",
    surfaceTemperatureC: input.surfaceTemperatureC,
    surfaceDewPointC: input.surfaceDewPointC,
    surfacePressureHpa: input.surfacePressureHpa,
    surfaceElevationM: fmul(input.elevationKm, 1000),
    pressureLevels: profile
      .filter(
        (level) =>
          Math.abs(fsub(level.pressureHpa, input.surfacePressureHpa)) >
            SURFACE_PRESSURE_TOLERANCE_HPA ||
          Math.abs(fsub(level.heightKm, input.elevationKm)) > SURFACE_MATCH_TOLERANCE_KM,
      )
      .map((level) => ({
        pressureHpa: level.pressureHpa,
        temperatureC: level.temperatureC,
        dewPointC: level.dewPointC,
        heightMslM: fmul(level.heightKm, 1000),
        isSynthetic: level.isSynthetic,
      })),
    takeoffElevationM: null,
  };
}

function analyzeTopScenario(
  input: ThermalForecastInput,
  profile: ProfileLevel[],
  surfaceHeatingC: number,
): ThermalTopScenarioResult {
  const thetaK = potentialTemperatureK(
    fadd(input.surfaceTemperatureC, surfaceHeatingC),
    input.surfacePressureHpa,
  );
  let previous = profile[0];
  let previousDiff = fsub(dryAdiabatTempC(thetaK, previous.pressureHpa), previous.temperatureC);
  if (previousDiff <= MIN_TOP_BUOYANCY_C) {
    return {
      topKm: input.elevationKm,
      bracketLower: previous,
      bracketUpper: profile.length > 1 ? profile[1] : null,
      profileTopLimited: false,
    };
  }
  for (let index = 1; index < profile.length; index++) {
    const current = profile[index];
    const currentDiff = fsub(dryAdiabatTempC(thetaK, current.pressureHpa), current.temperatureC);
    if (currentDiff <= MIN_TOP_BUOYANCY_C) {
      const fraction = coerceIn(
        fsub(previousDiff, currentDiff) > f(0.001)
          ? fdiv(previousDiff, fsub(previousDiff, currentDiff))
          : f(0.5),
        0,
        1,
      );
      return {
        topKm: fadd(previous.heightKm, fmul(fraction, fsub(current.heightKm, previous.heightKm))),
        bracketLower: previous,
        bracketUpper: current,
        profileTopLimited: false,
      };
    }
    previous = current;
    previousDiff = currentDiff;
  }
  return {
    topKm: profile[profile.length - 1].heightKm,
    bracketLower: profile.length - 2 >= 0 ? profile[profile.length - 2] : null,
    bracketUpper: profile[profile.length - 1],
    profileTopLimited: true,
  };
}

function buildLayers(
  input: ThermalForecastInput,
  profile: ProfileLevel[],
  scenarios: ThermalScenario[],
  nominalTopKm: number,
  damping: ThermalDamping,
  pblSanity: PblSanity,
): ThermalForecastLayer[] {
  if (nominalTopKm <= fadd(input.elevationKm, HEIGHT_EPSILON_KM)) return [];
  const heightProfile = sortedBy(profile, (it) => it.heightKm);
  const maxDisplayTopKm = Math.min(
    nominalTopKm,
    highestRealProfileHeightKm(profile) ?? heightProfile[heightProfile.length - 1].heightKm,
  );
  const result: ThermalForecastLayer[] = [];
  let startKm = input.elevationKm;
  while (startKm < fsub(maxDisplayTopKm, HEIGHT_EPSILON_KM)) {
    const endKm = Math.min(fadd(startKm, THERMAL_LAYER_BIN_DEPTH_KM), maxDisplayTopKm);
    if (endKm <= fadd(startKm, HEIGHT_EPSILON_KM)) break;

    const midKm = fdiv(fadd(startKm, endKm), 2);
    const midPoint = interpolateProfileAtHeight(heightProfile, midKm);
    if (midPoint === null) break;
    const bottomPoint = interpolateProfileAtHeight(heightProfile, startKm);
    if (bottomPoint === null) break;
    const topPoint = interpolateProfileAtHeight(heightProfile, endKm);
    if (topPoint === null) break;
    const updrafts = scenarios.map((scenario) =>
      layerUpdraftMps(
        input,
        startKm,
        endKm,
        midPoint.pressureHpa,
        midPoint.temperatureC,
        scenario.surfaceHeatingC,
        damping,
        pblSanity,
      ),
    );
    const low = Math.min(...updrafts);
    const nominal = updrafts[1];
    const high = Math.max(...updrafts);
    const visualDepthM = coerceAtLeast(fmul(fsub(endKm, startKm), 1000), 0);
    const effectiveDepthM = coerceAtMost(visualDepthM, MAX_EFFECTIVE_UPDRAFT_LAYER_DEPTH_M);
    const sourceQuality = maxSourceQuality([
      midPoint.sourceQuality,
      bottomPoint.sourceQuality,
      topPoint.sourceQuality,
    ]);
    if (high >= MIN_DISPLAY_UPDRAFT_MPS) {
      result.push({
        startAltitudeKm: startKm,
        endAltitudeKm: endKm,
        updraftLowMps: roundUpdraft(low),
        updraftNominalMps: roundUpdraft(nominal),
        updraftHighMps: roundUpdraft(high),
        confidence: sourceQuality === "SYNTHETIC" ? "MEDIUM" : "HIGH",
        visualDepthM,
        effectiveDepthM,
        pressureBottomHpa: bottomPoint.pressureHpa,
        pressureTopHpa: topPoint.pressureHpa,
        sourceQuality,
        warnings:
          pblSanity.exceeded &&
          !pblSanity.mountainOrographicOverride &&
          pblSanity.pblTopKm !== null &&
          endKm > pblSanity.pblTopKm
            ? ["PBL_EXCEEDED"]
            : [],
      });
    }
    startKm = endKm;
  }
  return result;
}

function layerUpdraftMps(
  input: ThermalForecastInput,
  startKm: number,
  endKm: number,
  midPressureHpa: number,
  envTempC: number,
  surfaceHeatingC: number,
  damping: ThermalDamping,
  pblSanity: PblSanity,
): number {
  const thetaK = potentialTemperatureK(
    fadd(input.surfaceTemperatureC, surfaceHeatingC),
    input.surfacePressureHpa,
  );
  const parcelTempC = dryAdiabatTempC(thetaK, midPressureHpa);
  const buoyancyC = coerceAtLeast(fsub(parcelTempC, envTempC), 0);
  if (buoyancyC < MIN_LAYER_BUOYANCY_C) return 0;

  const dzMeters = Math.min(fmul(fsub(endKm, startKm), 1000), MAX_EFFECTIVE_UPDRAFT_LAYER_DEPTH_M);
  const buoyancyAccel = fdiv(fmul(GRAVITY_MPS2, buoyancyC), fadd(envTempC, KELVIN_OFFSET));
  const raw = fmul(
    fmul(
      fmul(fsqrt(Math.max(0, fmul(fmul(2, buoyancyAccel), dzMeters))), UPDRAFT_SCALE),
      damping.factor,
    ),
    pblLayerFactor(startKm, endKm, pblSanity),
  );
  return coerceIn(raw, 0, Math.min(damping.maxUpdraftMps, dampingFactorLimit(damping.factor)));
}

function computeDryThermalEnergy(thetaK: number, profile: ProfileLevel[], topKm: number): number {
  let energy = 0;
  for (let index = 0; index < profile.length - 1; index++) {
    const lower = profile[index];
    const upper = profile[index + 1];
    if (lower.heightKm >= topKm) break;
    const endKm = Math.min(upper.heightKm, topKm);
    const dz = fmul(fsub(endKm, lower.heightKm), 1000);
    if (dz <= 0) continue;
    const midPressure = fdiv(fadd(lower.pressureHpa, upper.pressureHpa), 2);
    const envTempC = fdiv(fadd(lower.temperatureC, upper.temperatureC), 2);
    const parcelTempC = dryAdiabatTempC(thetaK, midPressure);
    const buoyancy = fmul(
      fdiv(fmul(GRAVITY_MPS2, fsub(parcelTempC, envTempC)), fadd(envTempC, KELVIN_OFFSET)),
      dz,
    );
    if (buoyancy > 0) energy = fadd(energy, buoyancy);
  }
  return energy;
}

function dampingFactor(
  input: ThermalForecastInput,
  profile: ProfileLevel[],
  topKm: number,
  effectiveRadiationWm2: number | null,
  normalizedCinJKg: number | null,
): ThermalDamping {
  const radiation = effectiveRadiationWm2;
  const radiationFactor =
    radiation === null ? f(0.85) : radiation < 150 ? f(0.55) : radiation < 300 ? f(0.75) : 1;
  let cloudPenalty: number;
  if (radiation !== null) {
    const lowCloud = input.heatingInput.cloudCoverLowPercent ?? 0;
    cloudPenalty =
      lowCloud >= 85 ? f(0.25) : lowCloud >= 70 ? f(0.15) : lowCloud >= 50 ? f(0.08) : 0;
  } else {
    cloudPenalty = coerceIn(
      fadd(
        fadd(
          fmul(fdiv(input.heatingInput.cloudCoverLowPercent ?? 0, 100), f(0.45)),
          fmul(fdiv(input.heatingInput.cloudCoverMidPercent ?? 0, 100), f(0.25)),
        ),
        fmul(fdiv(input.heatingInput.cloudCoverHighPercent ?? 0, 100), f(0.1)),
      ),
      0,
      f(0.55),
    );
  }
  let profileCloudPenalty: number;
  if (radiation === null) {
    const cloudSamples = profile
      .filter((it) => it.heightKm <= fadd(topKm, f(0.2)))
      .flatMap((it) => (it.cloudCoverPercent !== null ? [it.cloudCoverPercent] : []));
    profileCloudPenalty =
      cloudSamples.length > 0
        ? coerceIn(fmul(fdiv(f(averageOfFloats(cloudSamples)), 100), f(0.25)), 0, f(0.35))
        : 0;
  } else {
    profileCloudPenalty = 0;
  }
  const precipFactor = (input.heatingInput.precipitationMm ?? 0) > f(0.1) ? f(0.55) : 1;
  const shearKmh = lowLevelWindShearKmh(profile, topKm);
  const shearFactor = shearKmh === null ? 1 : shearKmh > 40 ? f(0.65) : shearKmh > 25 ? f(0.8) : 1;
  const capeFactor = modelCapeStrengthFactor(input);
  const cinFactor = modelCinStrengthFactor(normalizedCinJKg);
  const liftedIndexFactor = liftedIndexStrengthFactor(input.liftedIndexC);
  const boundaryLayerFactor = boundaryLayerStrengthFactor(input.boundaryLayerHeightM);
  const heavyLowCloudFactor = heavyLowCloudStrengthFactor(input.heatingInput.cloudCoverLowPercent);

  let factor = radiationFactor;
  factor = fmul(factor, fsub(1, cloudPenalty));
  factor = fmul(factor, fsub(1, profileCloudPenalty));
  factor = fmul(factor, precipFactor);
  factor = fmul(factor, shearFactor);
  factor = fmul(factor, capeFactor);
  factor = fmul(factor, cinFactor);
  factor = fmul(factor, liftedIndexFactor);
  factor = fmul(factor, boundaryLayerFactor);
  factor = fmul(factor, heavyLowCloudFactor);
  return {
    factor: coerceIn(factor, f(0.25), 1),
    maxUpdraftMps: diagnosticUpdraftCapMps(input, normalizedCinJKg),
    weakRadiation: radiation !== null && radiation < 150,
    precipitation: (input.heatingInput.precipitationMm ?? 0) > f(0.1),
    strongWindShear: shearKmh !== null && shearKmh > 25,
    zeroCape: input.modelCapeJKg !== null && input.modelCapeJKg <= 0,
    strongCin: (normalizedCinJKg ?? 0) >= STRONG_CIN_JKG,
    heavyLowCloud: (input.heatingInput.cloudCoverLowPercent ?? 0) >= HEAVY_LOW_CLOUD_PERCENT,
    shallowBoundaryLayer:
      input.boundaryLayerHeightM !== null && input.boundaryLayerHeightM < SHALLOW_BOUNDARY_LAYER_M,
    missingBoundaryLayer: input.boundaryLayerHeightM === null,
  };
}

function dampingFactorLimit(dampingFactorValue: number): number {
  return dampingFactorValue <= f(0.3)
    ? f(2.4)
    : dampingFactorValue <= f(0.45)
      ? 3
      : dampingFactorValue <= f(0.65)
        ? f(4.4)
        : dampingFactorValue <= f(0.85)
          ? f(5.6)
          : MAX_UPDRAFT_MPS;
}

function modelCapeStrengthFactor(input: ThermalForecastInput): number {
  const cape = input.modelCapeJKg;
  if (cape === null) return f(0.95);
  if (cape <= 0) return hasClearDryThermalSupport(input) ? f(0.68) : f(0.5);
  if (cape < 100) return f(0.66);
  if (cape < 300) return f(0.82);
  if (cape < 800) return f(0.96);
  return f(1.08);
}

function hasClearDryThermalSupport(input: ThermalForecastInput): boolean {
  const radiation = input.heatingInput.shortwaveRadiationWm2;
  if (radiation === null) return false;
  const lowCloud = input.heatingInput.cloudCoverLowPercent ?? 100;
  const precipitation = input.heatingInput.precipitationMm ?? 0;
  const boundaryLayer = input.boundaryLayerHeightM;
  if (boundaryLayer === null) return false;
  return (
    radiation >= DRY_THERMAL_SUPPORT_RADIATION_WM2 &&
    lowCloud <= DRY_THERMAL_SUPPORT_LOW_CLOUD_PERCENT &&
    precipitation <= f(0.1) &&
    boundaryLayer >= DRY_THERMAL_SUPPORT_BOUNDARY_LAYER_M
  );
}

function modelCinStrengthFactor(modelCinJKg: number | null): number {
  const cin = modelCinJKg;
  if (cin === null) return 1;
  if (cin >= 250) return f(0.5);
  if (cin >= STRONG_CIN_JKG) return f(0.68);
  if (cin >= 75) return f(0.85);
  if (cin >= 25) return f(0.95);
  return 1;
}

function liftedIndexStrengthFactor(liftedIndexC: number | null): number {
  const liftedIndex = liftedIndexC;
  if (liftedIndex === null) return 1;
  if (liftedIndex <= -4) return f(1.08);
  if (liftedIndex <= -2) return f(1.02);
  if (liftedIndex >= 6) return f(0.75);
  if (liftedIndex >= 3) return f(0.9);
  return 1;
}

function boundaryLayerStrengthFactor(boundaryLayerHeightM: number | null): number {
  const boundaryLayer = boundaryLayerHeightM;
  if (boundaryLayer === null) return f(0.95);
  if (boundaryLayer < 300) return f(0.6);
  if (boundaryLayer < SHALLOW_BOUNDARY_LAYER_M) return f(0.85);
  if (boundaryLayer > 1800) return f(1.05);
  return 1;
}

function heavyLowCloudStrengthFactor(lowCloudPercent: number | null): number {
  const lowCloud = lowCloudPercent;
  if (lowCloud === null) return 1;
  if (lowCloud >= HEAVY_LOW_CLOUD_PERCENT) return f(0.65);
  if (lowCloud >= 60) return f(0.78);
  if (lowCloud >= 40) return f(0.9);
  return 1;
}

function diagnosticUpdraftCapMps(
  input: ThermalForecastInput,
  normalizedCinJKg: number | null,
): number {
  const cape = input.modelCapeJKg;
  const baseCap =
    cape === null
      ? f(5.5)
      : cape <= 0
        ? hasClearDryThermalSupport(input)
          ? f(4.2)
          : f(3.2)
        : cape < 100
          ? f(4.2)
          : cape < 300
            ? 5
            : cape < 800
              ? f(5.8)
              : f(7.5);
  const cinCap =
    normalizedCinJKg !== null
      ? normalizedCinJKg >= 250
        ? f(2.4)
        : normalizedCinJKg >= STRONG_CIN_JKG
          ? f(3.2)
          : normalizedCinJKg >= 75
            ? f(4.5)
            : MAX_UPDRAFT_MPS
      : MAX_UPDRAFT_MPS;
  const boundaryLayerCap =
    input.boundaryLayerHeightM !== null
      ? input.boundaryLayerHeightM < 300
        ? f(2.4)
        : input.boundaryLayerHeightM < SHALLOW_BOUNDARY_LAYER_M
          ? f(3.6)
          : MAX_UPDRAFT_MPS
      : MAX_UPDRAFT_MPS;

  return Math.min(baseCap, cinCap, boundaryLayerCap, MAX_UPDRAFT_MPS);
}

function lowLevelWindShearKmh(profile: ProfileLevel[], topKm: number): number | null {
  const samples = profile
    .filter((it) => it.heightKm <= fadd(coerceAtMost(topKm, fadd(profile[0].heightKm, 2)), f(0.01)))
    .flatMap((it) => (it.windSpeedKmh !== null ? [it.windSpeedKmh] : []));
  if (samples.length < 2) return null;
  return fsub(Math.max(...samples), Math.min(...samples));
}

function resolveConfidence(
  profile: ProfileLevel[],
  nominalTop: ThermalTopScenarioResult,
  damping: ThermalDamping,
  input: ThermalForecastInput,
): ThermalForecastConfidence {
  let score = 4;
  const depthKm = bracketDepthKm(nominalTop);
  const hasTopMoistureContext = hasHumidityOrCloudNearTop(profile, nominalTop.topKm);
  if (depthKm > f(0.5)) score -= 1;
  if (depthKm > 1) score -= 1;
  if (nominalTop.profileTopLimited) score -= 1;
  if (
    nominalTop.bracketLower?.isSynthetic === true ||
    nominalTop.bracketUpper?.isSynthetic === true
  )
    score -= 1;
  if (!hasTopMoistureContext) score -= 1;
  if (damping.precipitation) score -= 1;
  if (damping.weakRadiation) score -= 1;
  if (damping.strongWindShear) score -= 1;
  if (damping.zeroCape && !hasClearDryThermalSupport(input)) score -= 1;
  if (damping.strongCin) score -= 1;
  if (damping.heavyLowCloud) score -= 1;
  if (damping.shallowBoundaryLayer) score -= 1;
  if (damping.missingBoundaryLayer) score -= 1;
  if (input.modelCinJKg === null || input.liftedIndexC === null) score -= 1;

  let confidence: ThermalForecastConfidence = score >= 3 ? "HIGH" : score >= 1 ? "MEDIUM" : "LOW";
  if (!hasTopMoistureContext || damping.missingBoundaryLayer) {
    confidence = capAt(confidence, "MEDIUM");
  }
  return confidence;
}

function hasHumidityOrCloudNearTop(profile: ProfileLevel[], topKm: number): boolean {
  return profile
    .filter((it) => Math.abs(fsub(it.heightKm, topKm)) <= TOP_MOISTURE_CONTEXT_KM)
    .some(
      (it) =>
        it.dewPointC !== null ||
        it.relativeHumidityPercent !== null ||
        it.cloudCoverPercent !== null,
    );
}

function resolveLimitingReason(
  input: ThermalForecastInput,
  nominalTop: ThermalTopScenarioResult,
  cloudBaseKm: number | null,
  damping: ThermalDamping,
): ThermalLimitingReason {
  if (input.heatingInput.isDay === false) return "SURFACE_HEATING";
  if (damping.precipitation) return "PRECIPITATION";
  if (damping.weakRadiation) return "WEAK_RADIATION";
  if (damping.heavyLowCloud) return "WEAK_RADIATION";
  if (damping.strongCin) return "INVERSION";
  if (damping.shallowBoundaryLayer) return "INVERSION";
  if (nominalTop.profileTopLimited) return "PROFILE_TOP";
  if (nominalTop.topKm <= fadd(input.elevationKm, SHALLOW_THERMAL_DEPTH_KM)) return "INVERSION";
  if (cloudBaseKm !== null && cloudBaseKm <= fadd(nominalTop.topKm, CLOUD_BASE_REACH_TOLERANCE_KM))
    return "CLOUD_BASE";
  if (damping.strongWindShear) return "WIND_SHEAR";
  return "SURFACE_HEATING";
}

function findThermalLcl(
  parcelThetaK: number,
  surfaceMixingRatio: number,
  profile: ProfileLevel[],
  elevationKm: number,
): PressureHeight {
  let previous: ProfileLevel | null = null;
  for (const level of profile) {
    const dryTemp = dryAdiabatTempC(parcelThetaK, level.pressureHpa);
    const saturationMixingRatio = satMixingRatioGKg(dryTemp, level.pressureHpa);
    if (saturationMixingRatio <= surfaceMixingRatio) {
      if (previous !== null) {
        const previousDryTemp = dryAdiabatTempC(parcelThetaK, previous.pressureHpa);
        const previousMixingRatio = satMixingRatioGKg(previousDryTemp, previous.pressureHpa);
        const fraction = coerceIn(
          fsub(previousMixingRatio, saturationMixingRatio) > f(0.001)
            ? fdiv(
                fsub(previousMixingRatio, surfaceMixingRatio),
                fsub(previousMixingRatio, saturationMixingRatio),
              )
            : f(0.5),
          0,
          1,
        );
        return {
          pressureHpa: fadd(
            previous.pressureHpa,
            fmul(fraction, fsub(level.pressureHpa, previous.pressureHpa)),
          ),
          heightKm: coerceAtLeast(
            fadd(previous.heightKm, fmul(fraction, fsub(level.heightKm, previous.heightKm))),
            elevationKm,
          ),
        };
      }
      return {
        pressureHpa: level.pressureHpa,
        heightKm: coerceAtLeast(level.heightKm, elevationKm),
      };
    }
    previous = level;
  }
  const top = profile[profile.length - 1];
  return { pressureHpa: top.pressureHpa, heightKm: top.heightKm };
}

function findMoistTop(
  saturationPointTemperatureC: number,
  saturationPointPressureHpa: number,
  profile: ProfileLevel[],
  cloudBaseKm: number,
): number | null {
  const aboveCloud = profile.filter((it) => it.heightKm >= fsub(cloudBaseKm, HEIGHT_EPSILON_KM));
  if (aboveCloud.length < 2) return null;
  let foundBuoyancy = false;
  let previous: ProfileLevel | null = null;
  for (const level of aboveCloud) {
    const moistTemp = moistAdiabatTempFromPointC(
      saturationPointTemperatureC,
      saturationPointPressureHpa,
      level.pressureHpa,
    );
    if (moistTemp > level.temperatureC) {
      foundBuoyancy = true;
    } else if (foundBuoyancy) {
      if (previous !== null) {
        const previousMoistTemp = moistAdiabatTempFromPointC(
          saturationPointTemperatureC,
          saturationPointPressureHpa,
          previous.pressureHpa,
        );
        const prevDiff = fsub(previousMoistTemp, previous.temperatureC);
        const currentDiff = fsub(moistTemp, level.temperatureC);
        const fraction = coerceIn(
          fsub(prevDiff, currentDiff) > f(0.001)
            ? fdiv(prevDiff, fsub(prevDiff, currentDiff))
            : f(0.5),
          0,
          1,
        );
        return fadd(previous.heightKm, fmul(fraction, fsub(level.heightKm, previous.heightKm)));
      }
      return level.heightKm;
    }
    previous = level;
  }
  return foundBuoyancy ? aboveCloud[aboveCloud.length - 1].heightKm : null;
}

function optimisticDryTopAllowed(
  input: ThermalForecastInput,
  normalizedCinJKg: number | null,
): boolean {
  const lowCloud = input.heatingInput.cloudCoverLowPercent;
  if (lowCloud === null) return false;
  const precipitation = input.heatingInput.precipitationMm ?? 0;
  const liftedIndex = input.liftedIndexC;
  if (liftedIndex === null) return false;
  return (
    precipitation <= f(0.1) &&
    lowCloud < 40 &&
    input.boundaryLayerHeightM !== null &&
    input.modelCinJKg !== null &&
    normalizedCinJKg !== null &&
    normalizedCinJKg < STRONG_CIN_JKG &&
    liftedIndex < 6
  );
}

function resolveCloudBaseStatus(
  primaryCcl: CclHourlyResult | null,
  profile: ProfileLevel[],
  elevationKm: number,
  nominalTopKm: number,
): ThermalCloudBaseStatus {
  const hasTemperatureProfile =
    profile.filter(
      (level) =>
        !level.isSynthetic && level.heightKm >= fadd(elevationKm, PRESSURE_LEVEL_MIN_AGL_KM),
    ).length >= 1;
  const cclHeightKm =
    primaryCcl !== null && primaryCcl.cclHeightMslM !== null
      ? fdiv(primaryCcl.cclHeightMslM, 1000)
      : null;
  if (cclHeightKm === null) {
    return hasTemperatureProfile ? "NO_CCL" : "UNKNOWN";
  }
  return primaryCcl?.reachable === true &&
    nominalTopKm >= fsub(cclHeightKm, CLOUD_BASE_REACH_TOLERANCE_KM)
    ? "REACHABLE"
    : "UNREACHABLE";
}

function resolvePblSanity(
  input: ThermalForecastInput,
  profile: ProfileLevel[],
  nominalThetaK: number,
  dryTopKm: number,
): PblSanity {
  const boundaryLayerHeightM = input.boundaryLayerHeightM;
  const pblTopKm =
    boundaryLayerHeightM !== null
      ? fadd(input.elevationKm, fdiv(boundaryLayerHeightM, 1000))
      : null;
  const dryTopAglM = coerceAtLeast(fmul(fsub(dryTopKm, input.elevationKm), 1000), 0);
  const exceeded =
    boundaryLayerHeightM !== null &&
    dryTopAglM > fadd(fmul(boundaryLayerHeightM, PBL_EXCEED_FACTOR), PBL_EXCEED_MARGIN_M);
  const severelyExceeded =
    boundaryLayerHeightM !== null &&
    dryTopAglM >
      fadd(fmul(boundaryLayerHeightM, PBL_SEVERE_EXCEED_FACTOR), PBL_SEVERE_EXCEED_MARGIN_M);
  const positiveRealIntervals = countPositiveRealDryIntervals(
    profile,
    nominalThetaK,
    input.elevationKm,
  );
  const mountainOrographicOverride =
    boundaryLayerHeightM === null
      ? false
      : exceeded &&
        input.elevationKm >= MOUNTAIN_OVERRIDE_MIN_ELEVATION_KM &&
        boundaryLayerHeightM >= MOUNTAIN_OVERRIDE_MIN_PBL_M &&
        (input.heatingInput.precipitationMm ?? 0) <= f(0.1) &&
        (input.heatingInput.cloudCoverLowPercent ?? 100) < 40 &&
        positiveRealIntervals >= MOUNTAIN_OVERRIDE_MIN_POSITIVE_INTERVALS;
  return {
    exceeded,
    severelyExceeded,
    mountainOrographicOverride,
    pblTopKm,
  };
}

function resolveDisplayLayerTopKm(
  input: ThermalForecastInput,
  nominalTopKm: number,
  cloudBaseStatus: ThermalCloudBaseStatus,
  pblSanity: PblSanity,
): number {
  if (cloudBaseStatus !== "UNKNOWN" || pblSanity.mountainOrographicOverride) {
    return nominalTopKm;
  }
  const pblSanityTopKm =
    input.boundaryLayerHeightM !== null
      ? fadd(
          input.elevationKm,
          fdiv(
            fadd(fmul(input.boundaryLayerHeightM, PBL_EXCEED_FACTOR), PBL_EXCEED_MARGIN_M),
            1000,
          ),
        )
      : fadd(input.elevationKm, MISSING_CCL_FALLBACK_DISPLAY_DEPTH_KM);
  return Math.min(nominalTopKm, pblSanityTopKm);
}

function countPositiveRealDryIntervals(
  profile: ProfileLevel[],
  thetaK: number,
  elevationKm: number,
): number {
  const realProfile = sortedByDescending(
    profile.filter((it) => !it.isSynthetic),
    (it) => it.pressureHpa,
  );
  let count = 0;
  for (let index = 0; index < realProfile.length - 1; index++) {
    const lower = realProfile[index];
    const upper = realProfile[index + 1];
    if (upper.heightKm <= fadd(elevationKm, PRESSURE_LEVEL_MIN_AGL_KM)) continue;
    const midPressure = fdiv(fadd(lower.pressureHpa, upper.pressureHpa), 2);
    const envTempC = fdiv(fadd(lower.temperatureC, upper.temperatureC), 2);
    if (fsub(dryAdiabatTempC(thetaK, midPressure), envTempC) >= MIN_LAYER_BUOYANCY_C) {
      count += 1;
    }
  }
  return count;
}

function pblLayerFactor(startKm: number, endKm: number, pblSanity: PblSanity): number {
  const pblTopKm = pblSanity.pblTopKm;
  if (pblTopKm === null) return 1;
  if (!pblSanity.exceeded || pblSanity.mountainOrographicOverride) return 1;
  if (startKm >= pblTopKm) return f(0.55);
  if (endKm > pblTopKm) return f(0.75);
  return 1;
}

function geometricallyAboveSurface(level: ProfileLevel, input: ThermalForecastInput): boolean {
  return (
    level.heightKm >= fadd(input.elevationKm, PRESSURE_LEVEL_MIN_AGL_KM) &&
    level.pressureHpa <= fsub(input.surfacePressureHpa, SURFACE_PRESSURE_TOLERANCE_HPA)
  );
}

function highestRealProfileHeightKm(profile: ProfileLevel[]): number | null {
  const real = profile.filter((it) => !it.isSynthetic).map((it) => it.heightKm);
  return real.length > 0 ? Math.max(...real) : null;
}

function interpolateProfileAtHeight(
  profileByHeight: ProfileLevel[],
  heightKm: number,
): InterpolatedProfilePoint | null {
  if (profileByHeight.length === 0) return null;
  const first = profileByHeight[0];
  const last = profileByHeight[profileByHeight.length - 1];
  if (
    heightKm < fsub(first.heightKm, HEIGHT_EPSILON_KM) ||
    heightKm > fadd(last.heightKm, HEIGHT_EPSILON_KM)
  ) {
    return null;
  }
  const exact = profileByHeight.find(
    (it) => Math.abs(fsub(it.heightKm, heightKm)) <= HEIGHT_EPSILON_KM,
  );
  if (exact !== undefined) {
    return {
      pressureHpa: exact.pressureHpa,
      temperatureC: exact.temperatureC,
      sourceQuality: exact.isSynthetic ? "SYNTHETIC" : "REAL",
    };
  }
  for (let index = 0; index < profileByHeight.length - 1; index++) {
    const lower = profileByHeight[index];
    const upper = profileByHeight[index + 1];
    if (heightKm >= lower.heightKm && heightKm <= upper.heightKm) {
      const fraction = coerceIn(
        fdiv(fsub(heightKm, lower.heightKm), fsub(upper.heightKm, lower.heightKm)),
        0,
        1,
      );
      return {
        pressureHpa: fadd(
          lower.pressureHpa,
          fmul(fraction, fsub(upper.pressureHpa, lower.pressureHpa)),
        ),
        temperatureC: fadd(
          lower.temperatureC,
          fmul(fraction, fsub(upper.temperatureC, lower.temperatureC)),
        ),
        sourceQuality: lower.isSynthetic || upper.isSynthetic ? "SYNTHETIC" : "INTERPOLATED",
      };
    }
  }
  return null;
}

function computeDryCapeCin(thetaK: number, profile: ProfileLevel[]): DryCapeCin {
  let cape = 0;
  let cin = 0;
  let foundPositiveBuoyancy = false;
  const sortedProfile = sortedByDescending(profile, (it) => it.pressureHpa);
  for (let index = 0; index < sortedProfile.length - 1; index++) {
    const lower = sortedProfile[index];
    const upper = sortedProfile[index + 1];
    const dz = fmul(fsub(upper.heightKm, lower.heightKm), 1000);
    if (dz <= 0) continue;
    const midPressure = fdiv(fadd(lower.pressureHpa, upper.pressureHpa), 2);
    const envTempC = fdiv(fadd(lower.temperatureC, upper.temperatureC), 2);
    const parcelTempC = dryAdiabatTempC(thetaK, midPressure);
    const energy = fmul(
      fdiv(fmul(GRAVITY_MPS2, fsub(parcelTempC, envTempC)), fadd(envTempC, KELVIN_OFFSET)),
      dz,
    );
    if (energy > 0) {
      cape = fadd(cape, energy);
      foundPositiveBuoyancy = true;
    } else if (!foundPositiveBuoyancy) {
      cin = fadd(cin, -energy);
    }
  }
  return {
    capeJKg: coerceAtLeast(cape, 0),
    cinJKg: coerceAtLeast(cin, 0),
  };
}

function capAt(
  confidence: ThermalForecastConfidence,
  maxConfidence: ThermalForecastConfidence,
): ThermalForecastConfidence {
  return CONFIDENCE_ORDINAL[confidence] < CONFIDENCE_ORDINAL[maxConfidence]
    ? maxConfidence
    : confidence;
}

function toSourceLevel(level: ProfileLevel): ThermalSourceLevel {
  return {
    pressureHpa: level.pressureHpa,
    altitudeKm: level.heightKm,
    isSynthetic: level.isSynthetic,
  };
}

function roundUpdraft(value: number): number {
  return coerceIn(fdiv(toInt(fmul(value, 10)), 10), 0, MAX_UPDRAFT_MPS);
}

/** Kotlin `List<ThermalForecastLayer>.maxOfOrNull { selector } ?: 0f`. */
function maxOfLayers(
  layers: ThermalForecastLayer[],
  selector: (layer: ThermalForecastLayer) => number,
): number {
  if (layers.length === 0) return 0;
  return Math.max(...layers.map(selector));
}

/** Kotlin `maxByOrNull { it.ordinal }` — first element with the highest source-quality ordinal. */
function maxSourceQuality(values: ThermalLayerSourceQuality[]): ThermalLayerSourceQuality {
  return values.reduce((best, current) =>
    SOURCE_QUALITY_ORDINAL[current] > SOURCE_QUALITY_ORDINAL[best] ? current : best,
  );
}

function distinctWarnings(warnings: ThermalForecastWarning[]): ThermalForecastWarning[] {
  const seen = new Set<ThermalForecastWarning>();
  const result: ThermalForecastWarning[] = [];
  for (const warning of warnings) {
    if (!seen.has(warning)) {
      seen.add(warning);
      result.push(warning);
    }
  }
  return result;
}

/** Kotlin `linkedSetOf<ThermalForecastWarning>()` — insertion-ordered, de-duplicated. */
class OrderedWarningSet {
  private readonly seen = new Set<ThermalForecastWarning>();
  private readonly order: ThermalForecastWarning[] = [];

  add(warning: ThermalForecastWarning): void {
    if (!this.seen.has(warning)) {
      this.seen.add(warning);
      this.order.push(warning);
    }
  }

  addAll(warnings: ThermalForecastWarning[]): void {
    for (const warning of warnings) this.add(warning);
  }

  toList(): ThermalForecastWarning[] {
    return [...this.order];
  }
}

/** Namespace object mirroring the Kotlin `object ThermalForecastEngine`. */
export const ThermalForecastEngine = { analyze };
