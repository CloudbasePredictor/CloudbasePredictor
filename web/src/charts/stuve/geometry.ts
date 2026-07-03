/**
 * Stüve / Skew-T projection and axis geometry.
 *
 * 1:1 port of `ui/screens/forecast/views/StuveDiagramGeometry.kt`. The screen
 * projection (pressure↔y, temperature↔x with skew) is pure geometry, so it runs
 * in plain doubles — this is *not* engine-derived data. Parcel guides reuse the
 * ported thermodynamic engine functions.
 */

import {
  dryAdiabatTempC,
  moistAdiabatTempFromPointC,
  type ProfileLevel,
  potentialTemperatureK,
} from "../../engine/parcelAnalysis";
import { DEFAULT_TOP_ALTITUDE_KM } from "../viewport";
import {
  buildParcelAscentPath,
  interpolateProfileHeightMeters,
  interpolateProfileTemperature,
  pressureToApproxHeightMeters,
  type StuveForecastChartUiModel,
  type StuveProfilePoint,
  stuveProfilePoint,
} from "./model";

export const SKEWT_MIN_TOP_PRESSURE = 250;
export const SKEWT_BOTTOM_PRESSURE = 1050;
export const SKEWT_SKEW_RATIO = 0.45;
const STUVE_TEMP_AXIS_HALF_WIDTH_C = 20;

export const STUVE_DRY_REFERENCE_PRESSURES: number[] = [
  1050, 1000, 975, 950, 925, 900, 875, 850, 825, 800, 775, 750, 725, 700, 675, 650, 625, 600, 575,
  550, 525, 500, 475, 450, 425, 400, 375, 350, 325, 300, 275, 250,
];

const TEMP_MIN = -30;
const TEMP_MAX = 40;
export const TEMP_STEP = 10;
const STUVE_AUTO_FIT_MARGIN_KM = 0.35;
const TEMP_AXIS_FOCUS_TOP_PRESSURE_HPA = 650;
const TEMP_AXIS_LEFT_PADDING_C = 6;
const TEMP_AXIS_RIGHT_PADDING_C = 10;
const TEMP_AXIS_MIN_SPAN_C = 34;
const TEMP_AXIS_MIN_STABLE_SPAN_C = 6;
const TEMP_AXIS_MAX_SPAN_C = 48;
const TEMP_AXIS_MAX_DEWPOINT_EXTENSION_C = 14;
const STUVE_INITIAL_AUTO_FIT_MAX_KM = 6.5;

const ISOBAR_LABELS = [
  1000, 950, 900, 850, 800, 750, 700, 650, 600, 550, 500, 450, 400, 350, 300, 250,
];

function clamp(value: number, min: number, max: number): number {
  return Math.min(Math.max(value, min), max);
}

export interface TempAxisRange {
  minC: number;
  maxC: number;
}

export function tempAxisSpanC(range: TempAxisRange): number {
  return range.maxC - range.minC;
}

export function tempAxisCenterC(range: TempAxisRange): number {
  return (range.minC + range.maxC) / 2;
}

export function pressureToY(
  pressureHpa: number,
  plotTop: number,
  plotBottom: number,
  topPressure: number,
  bottomPressure: number = SKEWT_BOTTOM_PRESSURE,
): number {
  const logPressure = Math.log(pressureHpa);
  const logBottom = Math.log(bottomPressure);
  const logTop = Math.log(topPressure);
  const fraction = (logPressure - logTop) / (logBottom - logTop);
  return plotTop + fraction * (plotBottom - plotTop);
}

export function yToPressure(
  y: number,
  plotTop: number,
  plotBottom: number,
  topPressure: number,
  bottomPressure: number = SKEWT_BOTTOM_PRESSURE,
): number {
  const logBottom = Math.log(bottomPressure);
  const logTop = Math.log(topPressure);
  const fraction = clamp((y - plotTop) / (plotBottom - plotTop), 0, 1);
  const logPressure = logTop + fraction * (logBottom - logTop);
  return Math.exp(logPressure);
}

export function altitudeKmToApproxPressureHpa(altitudeKm: number): number {
  const heightMeters = Math.max(altitudeKm * 1000, 0);
  return 1013.25 * (1 - 0.0000225577 * heightMeters) ** 5.25588;
}

export class SkewTProjection {
  readonly plotWidth: number;
  readonly plotHeight: number;

  constructor(
    readonly topPressure: number,
    readonly bottomPressure: number,
    readonly temperatureRange: TempAxisRange,
    readonly plotLeft: number,
    readonly plotRight: number,
    readonly plotTop: number,
    readonly plotBottom: number,
  ) {
    this.plotWidth = plotRight - plotLeft;
    this.plotHeight = plotBottom - plotTop;
  }

  pressureToY(pressureHpa: number): number {
    return pressureToY(
      pressureHpa,
      this.plotTop,
      this.plotBottom,
      this.topPressure,
      this.bottomPressure,
    );
  }

  yToPressure(y: number): number {
    return yToPressure(y, this.plotTop, this.plotBottom, this.topPressure, this.bottomPressure);
  }

  temperatureToX(temperatureC: number, pressureHpa: number): number {
    const temperatureSpan = tempAxisSpanC(this.temperatureRange);
    if (this.plotWidth <= 0 || temperatureSpan <= 0) return this.plotLeft;
    const normalizedTemperature = (temperatureC - this.temperatureRange.minC) / temperatureSpan;
    const heightFraction = this.pressureHeightFraction(pressureHpa);
    return (
      this.plotLeft + (normalizedTemperature + heightFraction * SKEWT_SKEW_RATIO) * this.plotWidth
    );
  }

  xToTemperature(x: number, pressureHpa: number): number {
    const temperatureSpan = tempAxisSpanC(this.temperatureRange);
    if (this.plotWidth <= 0 || temperatureSpan <= 0) return this.temperatureRange.minC;
    const heightFraction = this.pressureHeightFraction(pressureHpa);
    const normalizedTemperature =
      (x - this.plotLeft) / this.plotWidth - heightFraction * SKEWT_SKEW_RATIO;
    return normalizedTemperature * temperatureSpan + this.temperatureRange.minC;
  }

  private pressureHeightFraction(pressureHpa: number): number {
    const logPressure = Math.log(pressureHpa);
    const logBottom = Math.log(this.bottomPressure);
    const logTop = Math.log(this.topPressure);
    const logSpan = logBottom - logTop;
    if (logSpan <= 0) return 0;
    return clamp((logBottom - logPressure) / logSpan, 0, 1);
  }
}

export function buildSkewTProjection(
  chart: StuveForecastChartUiModel,
  topPressure: number,
  bottomPressure: number,
  plotLeft: number,
  plotRight: number,
  plotTop: number,
  plotBottom: number,
): SkewTProjection {
  return new SkewTProjection(
    topPressure,
    bottomPressure,
    buildSkewTTemperatureAxisRange(chart, topPressure, bottomPressure),
    plotLeft,
    plotRight,
    plotTop,
    plotBottom,
  );
}

export function buildReferencePressures(
  bottomPressure: number,
  topPressure: number,
  stepHpa: number,
): number[] {
  const result: number[] = [];
  let pressure = bottomPressure;
  while (pressure >= topPressure) {
    result.push(pressure);
    pressure -= stepHpa;
  }
  return result;
}

export function selectPressureLabels(topPressure: number, plotHeight: number): number[] {
  const visibleLabels = ISOBAR_LABELS.filter((it) => it >= topPressure);
  if (visibleLabels.length === 0) return [];
  if (plotHeight / visibleLabels.length >= 28) return visibleLabels;
  const special = [950, 850, 700, 500, 300, 250];
  return visibleLabels.filter(
    (pressure) => Math.trunc(pressure) % 100 === 0 || special.includes(pressure),
  );
}

export function recommendedStuveTopAltitudeKm(chart: StuveForecastChartUiModel): number {
  const lastTemp = chart.temperatureProfile[chart.temperatureProfile.length - 1];
  const lastDew = chart.dewpointProfile[chart.dewpointProfile.length - 1];
  let topHeightMeters: number | null = lastTemp?.heightMeters ?? lastDew?.heightMeters ?? null;
  if (topHeightMeters === null && chart.windBarbs.length > 0) {
    const minBarb = chart.windBarbs.reduce((a, b) => (b.pressureHpa < a.pressureHpa ? b : a));
    topHeightMeters = pressureToApproxHeightMeters(minBarb.pressureHpa);
  }
  if (topHeightMeters === null) {
    topHeightMeters = pressureToApproxHeightMeters(lastTemp?.pressureHpa ?? SKEWT_MIN_TOP_PRESSURE);
  }
  return clamp(
    topHeightMeters / 1000 + STUVE_AUTO_FIT_MARGIN_KM,
    DEFAULT_TOP_ALTITUDE_KM,
    STUVE_INITIAL_AUTO_FIT_MAX_KM,
  );
}

function collectProfileTemperatures(
  profile: StuveProfilePoint[],
  topPressure: number,
  bottomPressure: number,
): number[] {
  const result: number[] = [];
  for (const point of profile) {
    if (point.pressureHpa >= topPressure && point.pressureHpa <= bottomPressure) {
      result.push(point.temperatureC);
    }
  }
  const atTop = interpolateProfileTemperature(profile, topPressure);
  if (atTop !== null) result.push(atTop);
  const atBottom = interpolateProfileTemperature(profile, bottomPressure);
  if (atBottom !== null) result.push(atBottom);
  return result;
}

function minOrNull(values: number[]): number | null {
  return values.length === 0 ? null : Math.min(...values);
}

function maxOrNull(values: number[]): number | null {
  return values.length === 0 ? null : Math.max(...values);
}

export function buildVisibleTemperatureAxisRange(
  chart: StuveForecastChartUiModel,
  topPressure: number,
  bottomPressure: number,
): TempAxisRange {
  const visibleTemperatures = collectProfileTemperatures(
    chart.temperatureProfile,
    topPressure,
    bottomPressure,
  );
  const visibleDewpoints = collectProfileTemperatures(
    chart.dewpointProfile,
    topPressure,
    bottomPressure,
  );

  if (visibleTemperatures.length === 0 && visibleDewpoints.length === 0) {
    return { minC: TEMP_MIN, maxC: TEMP_MAX };
  }

  const focusTopPressure = Math.max(topPressure, TEMP_AXIS_FOCUS_TOP_PRESSURE_HPA);
  const focusedTemperatures = collectProfileTemperatures(
    chart.temperatureProfile,
    focusTopPressure,
    bottomPressure,
  );
  const focusedDewpoints = collectProfileTemperatures(
    chart.dewpointProfile,
    focusTopPressure,
    bottomPressure,
  );
  const temperatureReference =
    maxOrNull(focusedTemperatures.length > 0 ? focusedTemperatures : visibleTemperatures) ??
    maxOrNull(visibleTemperatures) ??
    TEMP_MAX;
  const lowerReferenceTemperatures =
    focusedTemperatures.length > 0 ? focusedTemperatures : visibleTemperatures;
  const lowerReferenceDewpoints = focusedDewpoints.length > 0 ? focusedDewpoints : visibleDewpoints;
  const temperatureMin = minOrNull(lowerReferenceTemperatures) ?? temperatureReference;
  const rawDewpointMin = minOrNull(lowerReferenceDewpoints);
  const boundedDewpointMin =
    rawDewpointMin !== null
      ? Math.max(rawDewpointMin, temperatureMin - TEMP_AXIS_MAX_DEWPOINT_EXTENSION_C)
      : temperatureMin;
  const heatedSurfaceMax = Math.max(
    chart.temperatureProfile[0]?.temperatureC ?? temperatureReference,
    chart.parcelAscentPath[0]?.temperatureC ?? temperatureReference,
    chart.tconC ?? temperatureReference,
    temperatureReference,
  );

  const rawMin = Math.min(temperatureMin, boundedDewpointMin) - TEMP_AXIS_LEFT_PADDING_C;
  const rawMax = heatedSurfaceMax + TEMP_AXIS_RIGHT_PADDING_C;
  const span = clamp(rawMax - rawMin, TEMP_AXIS_MIN_SPAN_C, TEMP_AXIS_MAX_SPAN_C);
  const center = (rawMin + rawMax) / 2;
  return {
    minC: floorToStep(center - span / 2, TEMP_STEP),
    maxC: ceilToStep(center + span / 2, TEMP_STEP),
  };
}

function logPressureSpan(bottomPressure: number, topPressure: number): number {
  if (bottomPressure <= 0 || topPressure <= 0 || bottomPressure <= topPressure) return 0;
  return Math.log(bottomPressure) - Math.log(topPressure);
}

export function buildSkewTTemperatureAxisRange(
  chart: StuveForecastChartUiModel,
  topPressure: number,
  bottomPressure: number,
): TempAxisRange {
  const referenceTopPressure = clamp(
    altitudeKmToApproxPressureHpa(recommendedStuveTopAltitudeKm(chart)),
    SKEWT_MIN_TOP_PRESSURE,
    bottomPressure - 50,
  );
  const referenceRange = buildVisibleTemperatureAxisRange(
    chart,
    referenceTopPressure,
    bottomPressure,
  );
  const referenceLogSpan = logPressureSpan(bottomPressure, referenceTopPressure);
  const visibleLogSpan = logPressureSpan(bottomPressure, topPressure);
  const referenceSpanC = STUVE_TEMP_AXIS_HALF_WIDTH_C * 2;
  const span = Math.max(
    referenceLogSpan > 0 ? referenceSpanC * (visibleLogSpan / referenceLogSpan) : referenceSpanC,
    TEMP_AXIS_MIN_STABLE_SPAN_C,
  );
  const center = tempAxisCenterC(referenceRange);
  return { minC: center - span / 2, maxC: center + span / 2 };
}

export function shouldDrawDefaultParcelGuide(isCursorActive: boolean): boolean {
  return !isCursorActive;
}

export function buildTemperatureAxisLabels(range: TempAxisRange): number[] {
  const labels: number[] = [];
  const step = temperatureAxisStep(tempAxisSpanC(range));
  let value = ceilToStep(range.minC, step);
  while (value <= range.maxC + 0.01) {
    labels.push(value);
    value += step;
  }
  return labels.length > 0 ? labels : [range.minC, range.maxC];
}

function temperatureAxisStep(spanC: number): number {
  if (spanC <= 8) return 1;
  if (spanC <= 18) return 2;
  if (spanC <= 34) return 5;
  return TEMP_STEP;
}

export function buildInteractiveParcelFromPoint(
  anchorTemperatureC: number,
  anchorPressureHpa: number,
  chart: StuveForecastChartUiModel,
  parcelPressures: number[],
): StuveProfilePoint[] {
  const dryThetaK = potentialTemperatureK(anchorTemperatureC, anchorPressureHpa);
  const denseReferencePressures = buildReferencePressures(
    Math.max(anchorPressureHpa, maxOrNull(parcelPressures) ?? anchorPressureHpa),
    Math.min(anchorPressureHpa, minOrNull(parcelPressures) ?? anchorPressureHpa),
    10,
  );
  const seen = new Set<number>();
  const renderablePressures: number[] = [];
  for (const pressure of [...parcelPressures, ...denseReferencePressures, anchorPressureHpa]) {
    if (!seen.has(pressure)) {
      seen.add(pressure);
      renderablePressures.push(pressure);
    }
  }
  renderablePressures.sort((a, b) => b - a);

  return renderablePressures.map((pressure) => {
    const heightMeters =
      interpolateProfileHeightMeters(chart.temperatureProfile, pressure) ??
      pressureToApproxHeightMeters(pressure);
    const temperatureC =
      pressure >= anchorPressureHpa
        ? dryAdiabatTempC(dryThetaK, pressure)
        : moistAdiabatTempFromPointC(anchorTemperatureC, anchorPressureHpa, pressure);
    return stuveProfilePoint(pressure, temperatureC, heightMeters);
  });
}

export function buildInteractiveParcelFromSurface(
  parcelStartTempC: number,
  chart: StuveForecastChartUiModel,
  profileLevels: ProfileLevel[],
  parcelPressures: number[],
): StuveProfilePoint[] {
  const surfaceDewPointC = chart.dewpointProfile[0]?.temperatureC ?? parcelStartTempC - 8;
  return buildParcelAscentPath(
    parcelPressures,
    profileLevels,
    parcelStartTempC,
    surfaceDewPointC,
    chart.surfacePressureHpa,
    0,
  );
}

export function floorToStep(value: number, step: number): number {
  return Math.floor(value / step) * step;
}

function ceilToStep(value: number, step: number): number {
  return Math.ceil(value / step) * step;
}
