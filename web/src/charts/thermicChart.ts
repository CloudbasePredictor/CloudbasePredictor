/**
 * Thermic forecast chart UI model, display aggregation, and data builder.
 *
 * Ports `ui/screens/forecast/ThermicForecastChartUiModel.kt` (model +
 * `visibleSegment` + `aggregatedForDisplay`) and
 * `ForecastChartBuilders.buildThermicChartFromData`. The engine inputs are built
 * by the shared {@link buildHourEngineInputs} driver so the Thermic view and the
 * golden test agree on how model data maps onto `ThermalForecastInput`.
 *
 * Float math mirrors the Kotlin `Float` engine: `f(...)` around Float ops,
 * `Math.round` for Kotlin `roundToInt` (round half up).
 */

import { pointsByDate } from "../api/conversion";
import type { HourlyForecastData, HourlyPoint } from "../api/types";
import { distinct } from "../engine/collections";
import { buildHourEngineInputs } from "../engine/engineDriver";
import { coerceAtLeast, coerceIn, f } from "../engine/float32";
import {
  CLOUD_BASE_STATUS_ORDINAL,
  CONFIDENCE_ORDINAL,
  SOURCE_QUALITY_ORDINAL,
  type ThermalCloudBaseStatus,
  type ThermalForecastConfidence,
  ThermalForecastEngine,
  type ThermalForecastWarning,
  type ThermalLayerSourceQuality,
  type ThermalLimitingReason,
  type ThermalSourceLevel,
} from "../engine/thermalForecastEngine";

const THERMIC_MIN_DISPLAY_STRENGTH_MPS = f(0.2);
const THERMIC_ALTITUDE_STEP_KM = f(0.05);
const THERMIC_DISPLAY_MIN_FULL_BIN_KM = f(0.1);
const THERMIC_CLOUD_BASE_CLEARANCE_KM = f(0.05);
const MAX_THERMIC_STRENGTH_MPS = 10;
const THERMIC_EPSILON = f(0.0001);

export interface ThermicForecastCellUiModel {
  startMinuteOfDayLocal: number;
  startAltitudeKm: number;
  endAltitudeKm: number;
  strengthMps: number;
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

export interface ThermicForecastCloudMarkerUiModel {
  startMinuteOfDayLocal: number;
  altitudeKm: number;
}

export interface ThermicSlotDiagnostics {
  startMinuteOfDayLocal: number;
  topLowKm: number;
  topNominalKm: number;
  topHighKm: number;
  updraftLowMps: number;
  updraftNominalMps: number;
  updraftHighMps: number;
  confidence: ThermalForecastConfidence;
  limitingReason: ThermalLimitingReason;
  topLowerPressureHpa: number | null;
  topUpperPressureHpa: number | null;
  cloudBaseKm: number | null;
  moistEquilibriumTopKm: number | null;
  modelCapeJKg: number | null;
  modelCinJKg: number | null;
  normalizedCinJKg: number | null;
  liftedIndexC: number | null;
  boundaryLayerHeightM: number | null;
  triggerExcessC: number;
  dryTopExcessC: number;
  effectiveRadiationWm2: number | null;
  surfaceTemperatureC: number | null;
  surfacePressureHpa: number | null;
  elevationKm: number | null;
  parcelStartTemperatureC: number | null;
  dryTopAglM: number | null;
  computedCapeJKg: number;
  computedCinJKg: number;
  lclKm: number;
  cclKm: number | null;
  cloudBaseStatus: ThermalCloudBaseStatus;
  warnings: ThermalForecastWarning[];
  usedPressureLevels: ThermalSourceLevel[];
}

export interface ThermicForecastChartUiModel {
  timeSlots: number[];
  cells: ThermicForecastCellUiModel[];
  cloudMarkers: ThermicForecastCloudMarkerUiModel[];
  slotDiagnostics: ThermicSlotDiagnostics[];
  pressureLevelAltitudesKm: number[];
}

export interface ThermicVisibleCellSegment {
  startAltitudeKm: number;
  endAltitudeKm: number;
}

export function visibleSegment(
  cell: ThermicForecastCellUiModel,
  minAltitudeKm: number,
  maxAltitudeKm: number,
  cloudBaseKm: number | null,
): ThermicVisibleCellSegment | null {
  const visibleStartAltitudeKm = Math.max(cell.startAltitudeKm, minAltitudeKm);
  const cloudLimitedTopKm =
    cloudBaseKm !== null ? cloudBaseKm - THERMIC_CLOUD_BASE_CLEARANCE_KM : null;
  const visibleEndAltitudeKm = Math.min(
    cell.endAltitudeKm,
    maxAltitudeKm,
    cloudLimitedTopKm ?? Number.MAX_VALUE,
  );
  if (visibleEndAltitudeKm <= visibleStartAltitudeKm + THERMIC_EPSILON) return null;
  return { startAltitudeKm: visibleStartAltitudeKm, endAltitudeKm: visibleEndAltitudeKm };
}

// --- Builder from real data ---

export function buildThermicChartFromData(
  hourlyData: HourlyForecastData,
  dayIndex: number,
): ThermicForecastChartUiModel | null {
  const grouped = pointsByDate(hourlyData);
  const dates = [...grouped.keys()].sort();
  const dateKey = dates[dayIndex];
  if (dateKey === undefined) return null;
  const dayPoints = grouped.get(dateKey);
  if (dayPoints === undefined) return null;

  const daytimePoints = dayPoints.filter((it) => it.hour >= 6 && it.hour <= 22);
  if (daytimePoints.length === 0) return null;

  const timeSlots = daytimePoints.map((it) => it.hour * 60);
  const elevation = hourlyData.elevation ?? 0;
  const elevationKm = f(f(elevation) / 1000);

  const cells: ThermicForecastCellUiModel[] = [];
  const diagnostics: ThermicSlotDiagnostics[] = [];
  const pressureLevelAltitudes = new Set<number>();
  const dayPointsByHour = new Map<number, HourlyPoint>();
  for (const point of dayPoints) dayPointsByHour.set(point.hour, point);

  for (const hp of daytimePoints) {
    const startMinute = hp.hour * 60;
    const inputs = buildHourEngineInputs(hp, dayPointsByHour, elevation);
    if (inputs === null) continue;
    const forecast = ThermalForecastEngine.analyze(inputs.thermalInput);
    if (forecast === null) continue;

    for (const pl of inputs.pressureProfile) {
      if (pl.heightKm >= elevationKm - 0.01 && !pl.isSynthetic) {
        pressureLevelAltitudes.add(pl.heightKm);
      }
    }

    for (const layer of forecast.layers) {
      const confidence =
        CONFIDENCE_ORDINAL[forecast.confidence] >= CONFIDENCE_ORDINAL[layer.confidence]
          ? forecast.confidence
          : layer.confidence;
      cells.push({
        startMinuteOfDayLocal: startMinute,
        startAltitudeKm: layer.startAltitudeKm,
        endAltitudeKm: layer.endAltitudeKm,
        strengthMps: layer.updraftNominalMps,
        updraftLowMps: layer.updraftLowMps,
        updraftNominalMps: layer.updraftNominalMps,
        updraftHighMps: layer.updraftHighMps,
        confidence,
        visualDepthM: layer.visualDepthM,
        effectiveDepthM: layer.effectiveDepthM,
        pressureBottomHpa: layer.pressureBottomHpa,
        pressureTopHpa: layer.pressureTopHpa,
        sourceQuality: layer.sourceQuality,
        warnings: layer.warnings,
      });
    }

    diagnostics.push({
      startMinuteOfDayLocal: startMinute,
      topLowKm: forecast.topLowKm,
      topNominalKm: forecast.topNominalKm,
      topHighKm: forecast.topHighKm,
      updraftLowMps: forecast.updraftLowMps,
      updraftNominalMps: forecast.updraftNominalMps,
      updraftHighMps: forecast.updraftHighMps,
      confidence: forecast.confidence,
      limitingReason: forecast.limitingReason,
      topLowerPressureHpa: forecast.lowerSourceLevel?.pressureHpa ?? null,
      topUpperPressureHpa: forecast.upperSourceLevel?.pressureHpa ?? null,
      cloudBaseKm: forecast.cloudBaseKm,
      moistEquilibriumTopKm: forecast.moistEquilibriumTopKm,
      modelCapeJKg: forecast.modelCapeJKg,
      modelCinJKg: forecast.modelCinJKg,
      normalizedCinJKg: forecast.normalizedCinJKg,
      liftedIndexC: forecast.liftedIndexC,
      boundaryLayerHeightM: forecast.boundaryLayerHeightM,
      triggerExcessC: forecast.triggerExcessC,
      dryTopExcessC: forecast.dryTopExcessC,
      effectiveRadiationWm2: forecast.effectiveRadiationWm2,
      surfaceTemperatureC: forecast.surfaceTemperatureC,
      surfacePressureHpa: forecast.surfacePressureHpa,
      elevationKm: forecast.elevationKm,
      parcelStartTemperatureC: forecast.parcelStartTemperatureC,
      dryTopAglM: forecast.dryTopAglM,
      computedCapeJKg: forecast.thermalEnergyJKg,
      computedCinJKg: forecast.computedCinJKg,
      lclKm: forecast.lclKm,
      cclKm: forecast.cclKm,
      cloudBaseStatus: forecast.cloudBaseStatus,
      warnings: forecast.warnings,
      usedPressureLevels: forecast.usedPressureLevels,
    });
  }

  const displayCells = cells.filter((it) => it.strengthMps >= THERMIC_MIN_DISPLAY_STRENGTH_MPS);

  return {
    timeSlots,
    cells: displayCells,
    cloudMarkers: [],
    slotDiagnostics: diagnostics,
    pressureLevelAltitudesKm: [...pressureLevelAltitudes].sort((a, b) => a - b),
  };
}

// --- Display aggregation (port of aggregatedForDisplay) ---

function roundDisplayedStrength(value: number): number {
  return coerceIn(f(Math.round(f(value * 10)) / 10), 0, MAX_THERMIC_STRENGTH_MPS);
}

function intervalsOverlap(
  firstStart: number,
  firstEnd: number,
  secondStart: number,
  secondEnd: number,
): boolean {
  return Math.min(firstEnd, secondEnd) - Math.max(firstStart, secondStart) > THERMIC_EPSILON;
}

function weightedAverageValue(
  cells: ThermicForecastCellUiModel[],
  bucketStartAltitudeKm: number,
  bucketEndAltitudeKm: number,
  selector: (cell: ThermicForecastCellUiModel) => number,
): number {
  let weightedValueSum = 0;
  let weightSum = 0;
  for (const cell of cells) {
    const overlapStart = Math.max(cell.startAltitudeKm, bucketStartAltitudeKm);
    const overlapEnd = Math.min(cell.endAltitudeKm, bucketEndAltitudeKm);
    const overlapHeight = coerceAtLeast(f(overlapEnd - overlapStart), 0);
    if (overlapHeight > THERMIC_EPSILON) {
      weightedValueSum = f(weightedValueSum + f(selector(cell) * overlapHeight));
      weightSum = f(weightSum + overlapHeight);
    }
  }
  return weightSum <= THERMIC_EPSILON ? 0 : f(weightedValueSum / weightSum);
}

function representativeAltitudeStepKm(cells: ThermicForecastCellUiModel[]): number {
  const steps = cells
    .map((it) => coerceAtLeast(f(it.endAltitudeKm - it.startAltitudeKm), 0))
    .filter((it) => it > THERMIC_EPSILON);
  if (steps.length === 0) return THERMIC_ALTITUDE_STEP_KM;
  const nonPartialSteps = steps.filter((it) => it >= THERMIC_DISPLAY_MIN_FULL_BIN_KM);
  const candidate =
    nonPartialSteps.length > 0
      ? Math.min(...nonPartialSteps)
      : steps.length > 0
        ? Math.max(...steps)
        : THERMIC_ALTITUDE_STEP_KM;
  return coerceAtLeast(candidate, THERMIC_ALTITUDE_STEP_KM);
}

function lowestOrdinal<T extends string>(items: T[], ordinal: Record<T, number>, fallback: T): T {
  if (items.length === 0) return fallback;
  return items.reduce((best, current) => (ordinal[current] > ordinal[best] ? current : best));
}

function averageFloat(values: number[]): number {
  if (values.length === 0) return 0;
  let sum = 0;
  for (const v of values) sum += v;
  return f(sum / values.length);
}

function averageFloatOrNull(values: number[]): number | null {
  return values.length === 0 ? null : averageFloat(values);
}

export function aggregatedForDisplay(
  chart: ThermicForecastChartUiModel,
  timeBucketSlotCount: number,
  altitudeBucketStepKm: number,
): ThermicForecastChartUiModel {
  if (chart.timeSlots.length === 0) return chart;

  const slotCount = Math.max(1, timeBucketSlotCount);
  const baseAltitudeStepKm = representativeAltitudeStepKm(chart.cells);
  const altitudeStepMultiplier = Math.max(1, Math.ceil(altitudeBucketStepKm / baseAltitudeStepKm));
  const resolvedAltitudeBucketStepKm =
    chart.pressureLevelAltitudesKm.length === 0
      ? f(baseAltitudeStepKm * altitudeStepMultiplier)
      : baseAltitudeStepKm;

  if (slotCount === 1 && resolvedAltitudeBucketStepKm <= baseAltitudeStepKm + THERMIC_EPSILON) {
    return chart;
  }

  const groupedSlots: number[][] = [];
  for (let i = 0; i < chart.timeSlots.length; i += slotCount) {
    groupedSlots.push(chart.timeSlots.slice(i, i + slotCount));
  }

  const aggregatedCells: ThermicForecastCellUiModel[] = [];
  const aggregatedCloudMarkers: ThermicForecastCloudMarkerUiModel[] = [];

  for (const slotGroup of groupedSlots) {
    const groupStartMinute = slotGroup[0];
    const slotSet = new Set(slotGroup);
    const slotCells = chart.cells.filter((it) => slotSet.has(it.startMinuteOfDayLocal));
    const slotCloudMarkers = chart.cloudMarkers.filter((it) =>
      slotSet.has(it.startMinuteOfDayLocal),
    );

    if (slotCells.length > 0) {
      const minimumAltitudeKm = Math.min(...slotCells.map((it) => it.startAltitudeKm));
      const thermalTopKm = Math.max(...slotCells.map((it) => it.endAltitudeKm));
      let currentAltitudeKm = minimumAltitudeKm;

      while (currentAltitudeKm < thermalTopKm - THERMIC_EPSILON) {
        const nextAltitudeKm = Math.min(
          f(currentAltitudeKm + resolvedAltitudeBucketStepKm),
          thermalTopKm,
        );
        const bucketStart = currentAltitudeKm;
        const bucketEnd = nextAltitudeKm;
        const overlappingCells = slotCells.filter((cell) =>
          intervalsOverlap(cell.startAltitudeKm, cell.endAltitudeKm, bucketStart, bucketEnd),
        );

        if (overlappingCells.length > 0) {
          const averagedNominal = weightedAverageValue(
            overlappingCells,
            bucketStart,
            bucketEnd,
            (c) => c.updraftNominalMps,
          );
          aggregatedCells.push({
            startMinuteOfDayLocal: groupStartMinute,
            startAltitudeKm: bucketStart,
            endAltitudeKm: bucketEnd,
            strengthMps: roundDisplayedStrength(averagedNominal),
            updraftLowMps: roundDisplayedStrength(
              weightedAverageValue(
                overlappingCells,
                bucketStart,
                bucketEnd,
                (c) => c.updraftLowMps,
              ),
            ),
            updraftNominalMps: roundDisplayedStrength(averagedNominal),
            updraftHighMps: roundDisplayedStrength(
              weightedAverageValue(
                overlappingCells,
                bucketStart,
                bucketEnd,
                (c) => c.updraftHighMps,
              ),
            ),
            confidence: lowestOrdinal(
              overlappingCells.map((c) => c.confidence),
              CONFIDENCE_ORDINAL,
              "LOW",
            ),
            visualDepthM: coerceAtLeast(f(f(bucketEnd - bucketStart) * 1000), 0),
            effectiveDepthM:
              chart.pressureLevelAltitudesKm.length === 0
                ? coerceAtLeast(f(f(bucketEnd - bucketStart) * 1000), 0)
                : Math.min(...overlappingCells.map((c) => c.effectiveDepthM)),
            pressureBottomHpa: overlappingCells.reduce((a, b) =>
              b.startAltitudeKm < a.startAltitudeKm ? b : a,
            ).pressureBottomHpa,
            pressureTopHpa: overlappingCells.reduce((a, b) =>
              b.endAltitudeKm > a.endAltitudeKm ? b : a,
            ).pressureTopHpa,
            sourceQuality: lowestOrdinal(
              overlappingCells.map((c) => c.sourceQuality),
              SOURCE_QUALITY_ORDINAL,
              "REAL",
            ),
            warnings: distinct(overlappingCells.flatMap((c) => c.warnings)),
          });
        }
        currentAltitudeKm = nextAltitudeKm;
      }
    }

    const seenMarkerAlt = new Set<number>();
    for (const marker of [...slotCloudMarkers].sort((a, b) => a.altitudeKm - b.altitudeKm)) {
      const key = Math.round(marker.altitudeKm);
      if (seenMarkerAlt.has(key)) continue;
      seenMarkerAlt.add(key);
      aggregatedCloudMarkers.push({ ...marker, startMinuteOfDayLocal: groupStartMinute });
    }
  }

  const diagnosticsBySlot = new Map<number, ThermicSlotDiagnostics>();
  for (const diag of chart.slotDiagnostics) diagnosticsBySlot.set(diag.startMinuteOfDayLocal, diag);

  const aggregatedDiagnostics: ThermicSlotDiagnostics[] = [];
  for (const slotGroup of groupedSlots) {
    const groupStartMinute = slotGroup[0];
    const slotDiags = slotGroup
      .map((it) => diagnosticsBySlot.get(it))
      .filter((it): it is ThermicSlotDiagnostics => it !== undefined);
    if (slotDiags.length === 0) continue;
    const nonNull = <T>(values: (T | null)[]): T[] => values.filter((v): v is T => v !== null);
    aggregatedDiagnostics.push({
      startMinuteOfDayLocal: groupStartMinute,
      topLowKm: Math.min(...slotDiags.map((it) => it.topLowKm)),
      topNominalKm: averageFloat(slotDiags.map((it) => it.topNominalKm)),
      topHighKm: Math.max(...slotDiags.map((it) => it.topHighKm)),
      updraftLowMps: Math.min(...slotDiags.map((it) => it.updraftLowMps)),
      updraftNominalMps: averageFloat(slotDiags.map((it) => it.updraftNominalMps)),
      updraftHighMps: Math.max(...slotDiags.map((it) => it.updraftHighMps)),
      confidence: lowestOrdinal(
        slotDiags.map((it) => it.confidence),
        CONFIDENCE_ORDINAL,
        "LOW",
      ),
      limitingReason:
        slotDiags.find((it) => it.limitingReason !== "SURFACE_HEATING")?.limitingReason ??
        slotDiags[0].limitingReason,
      topLowerPressureHpa: maxOrNull(nonNull(slotDiags.map((it) => it.topLowerPressureHpa))),
      topUpperPressureHpa: minOrNull(nonNull(slotDiags.map((it) => it.topUpperPressureHpa))),
      cloudBaseKm: averageFloatOrNull(nonNull(slotDiags.map((it) => it.cloudBaseKm))),
      moistEquilibriumTopKm: averageFloatOrNull(
        nonNull(slotDiags.map((it) => it.moistEquilibriumTopKm)),
      ),
      modelCapeJKg: averageFloatOrNull(nonNull(slotDiags.map((it) => it.modelCapeJKg))),
      modelCinJKg: averageFloatOrNull(nonNull(slotDiags.map((it) => it.modelCinJKg))),
      normalizedCinJKg: averageFloatOrNull(nonNull(slotDiags.map((it) => it.normalizedCinJKg))),
      liftedIndexC: averageFloatOrNull(nonNull(slotDiags.map((it) => it.liftedIndexC))),
      boundaryLayerHeightM: averageFloatOrNull(
        nonNull(slotDiags.map((it) => it.boundaryLayerHeightM)),
      ),
      triggerExcessC: averageFloat(slotDiags.map((it) => it.triggerExcessC)),
      dryTopExcessC: averageFloat(slotDiags.map((it) => it.dryTopExcessC)),
      effectiveRadiationWm2: averageFloatOrNull(
        nonNull(slotDiags.map((it) => it.effectiveRadiationWm2)),
      ),
      surfaceTemperatureC: averageFloatOrNull(
        nonNull(slotDiags.map((it) => it.surfaceTemperatureC)),
      ),
      surfacePressureHpa: averageFloatOrNull(nonNull(slotDiags.map((it) => it.surfacePressureHpa))),
      elevationKm: averageFloatOrNull(nonNull(slotDiags.map((it) => it.elevationKm))),
      parcelStartTemperatureC: averageFloatOrNull(
        nonNull(slotDiags.map((it) => it.parcelStartTemperatureC)),
      ),
      dryTopAglM: averageFloatOrNull(nonNull(slotDiags.map((it) => it.dryTopAglM))),
      computedCapeJKg: averageFloat(slotDiags.map((it) => it.computedCapeJKg)),
      computedCinJKg: averageFloat(slotDiags.map((it) => it.computedCinJKg)),
      lclKm: averageFloat(slotDiags.map((it) => it.lclKm)),
      cclKm: averageFloatOrNull(nonNull(slotDiags.map((it) => it.cclKm))),
      cloudBaseStatus: lowestOrdinal(
        slotDiags.map((it) => it.cloudBaseStatus),
        CLOUD_BASE_STATUS_ORDINAL,
        "NO_CCL",
      ),
      warnings: distinct(slotDiags.flatMap((it) => it.warnings)),
      usedPressureLevels:
        slotDiags.find((it) => it.usedPressureLevels.length > 0)?.usedPressureLevels ?? [],
    });
  }

  return {
    timeSlots: groupedSlots.map((it) => it[0]),
    cells: aggregatedCells,
    cloudMarkers: aggregatedCloudMarkers,
    slotDiagnostics: aggregatedDiagnostics,
    pressureLevelAltitudesKm: chart.pressureLevelAltitudesKm,
  };
}

function minOrNull(values: number[]): number | null {
  return values.length === 0 ? null : Math.min(...values);
}

function maxOrNull(values: number[]): number | null {
  return values.length === 0 ? null : Math.max(...values);
}
