/**
 * Production driver that turns converted {@link HourlyForecastData} into the
 * per-hour engine inputs the forecast views need.
 *
 * This formalises the input construction that previously lived only in
 * `goldenFixture.test.ts` (a mirror of the Android
 * `ForecastChartBuilders.buildThermicChartFromData` loop). Both the golden test
 * and the Phase-3 chart builders reuse it, so there is a single source of truth
 * for how model data maps onto `ThermalForecastInput` / `analyzeParcel` inputs.
 *
 * All Float arithmetic mirrors the Kotlin `Float` engine via `Math.fround`
 * (see `float32.ts`); never widen these operations to plain doubles.
 */

import { pointsByDate } from "../api/conversion";
import type { HourlyForecastData, HourlyPoint } from "../api/types";
import { sortedByDescending } from "./collections";
import { f } from "./float32";
import {
  estimateSurfacePressure,
  type ProfileLevel,
  type SurfaceHeatingInput,
} from "./parcelAnalysis";
import type { ThermalForecastInput } from "./thermalForecastEngine";

/** First and last daytime hours (inclusive) used for the forecast charts. */
export const DAYTIME_START_HOUR = 6;
export const DAYTIME_END_HOUR = 22;

/** Everything needed to run the engine and parcel analysis for one hour. */
export interface HourEngineInputs {
  date: string;
  hour: number;
  point: HourlyPoint;
  /** Surface anchor + model pressure levels, sorted by descending pressure. */
  profile: ProfileLevel[];
  /** Model pressure levels only (no surface anchor), descending pressure. */
  pressureProfile: ProfileLevel[];
  surfaceTemperatureC: number;
  surfaceDewPointC: number;
  surfacePressureHpa: number;
  elevationKm: number;
  heatingInput: SurfaceHeatingInput;
  thermalInput: ThermalForecastInput;
  modelCapeJKg: number | null;
}

function toProfileLevel(pl: HourlyPoint["pressureLevels"][number]): ProfileLevel {
  return {
    pressureHpa: f(pl.pressureHpa),
    temperatureC: f(pl.temperatureC),
    dewPointC: pl.dewPointC !== null ? f(pl.dewPointC) : null,
    heightKm: f((pl.geopotentialHeightM as number) / 1000.0),
    relativeHumidityPercent:
      pl.relativeHumidityPercent !== null ? f(pl.relativeHumidityPercent) : null,
    cloudCoverPercent: pl.cloudCoverPercent !== null ? f(pl.cloudCoverPercent) : null,
    windSpeedKmh: pl.windSpeedKmh !== null ? f(pl.windSpeedKmh) : null,
    isSynthetic: pl.isSynthetic,
  };
}

/**
 * Build the engine inputs for a single hour, or `null` when the hour lacks the
 * required surface fields or has fewer than two profile levels.
 *
 * @param elevation Station elevation in metres (raw, not km).
 */
export function buildHourEngineInputs(
  hp: HourlyPoint,
  dayPointsByHour: Map<number, HourlyPoint>,
  elevation: number,
): HourEngineInputs | null {
  if (hp.temperature2mC === null) return null;
  if (hp.dewPoint2mC === null) return null;

  const elevationKm = f(f(elevation) / 1000);
  const surfaceTemp = f(hp.temperature2mC);
  const surfaceDew = f(hp.dewPoint2mC);
  const surfacePressure =
    hp.surfacePressureHpa !== null ? f(hp.surfacePressureHpa) : estimateSurfacePressure(elevation);

  const pressureProfile = sortedByDescending(
    hp.pressureLevels.filter((pl) => pl.geopotentialHeightM !== null).map(toProfileLevel),
    (it) => it.pressureHpa,
  );

  const surfaceLevel: ProfileLevel = {
    pressureHpa: surfacePressure,
    temperatureC: surfaceTemp,
    dewPointC: surfaceDew,
    heightKm: elevationKm,
    relativeHumidityPercent: null,
    cloudCoverPercent: null,
    windSpeedKmh: hp.windSpeed10mKmh !== null ? f(hp.windSpeed10mKmh) : null,
    isSynthetic: false,
  };
  const profile = sortedByDescending([surfaceLevel, ...pressureProfile], (it) => it.pressureHpa);

  if (profile.length < 2) return null;

  const previousPoint = dayPointsByHour.get(hp.hour - 1);
  const heatingInput: SurfaceHeatingInput = {
    hourOfDay: hp.hour,
    shortwaveRadiationWm2: hp.shortwaveRadiationWm2 !== null ? f(hp.shortwaveRadiationWm2) : null,
    previousShortwaveRadiationWm2:
      previousPoint !== undefined && previousPoint.shortwaveRadiationWm2 !== null
        ? f(previousPoint.shortwaveRadiationWm2)
        : null,
    cloudCoverLowPercent: hp.cloudCoverLowPercent !== null ? f(hp.cloudCoverLowPercent) : null,
    cloudCoverMidPercent: hp.cloudCoverMidPercent !== null ? f(hp.cloudCoverMidPercent) : null,
    cloudCoverHighPercent: hp.cloudCoverHighPercent !== null ? f(hp.cloudCoverHighPercent) : null,
    precipitationMm: hp.precipitationMm !== null ? f(hp.precipitationMm) : null,
    isDay: hp.isDay !== null ? hp.isDay > 0.5 : null,
  };

  const modelCapeJKg = hp.capeJKg !== null ? f(hp.capeJKg) : null;
  const thermalInput: ThermalForecastInput = {
    profile,
    surfaceTemperatureC: surfaceTemp,
    surfaceDewPointC: surfaceDew,
    surfacePressureHpa: surfacePressure,
    elevationKm,
    heatingInput,
    modelCapeJKg,
    modelCinJKg: hp.convectiveInhibitionJKg !== null ? f(hp.convectiveInhibitionJKg) : null,
    liftedIndexC: hp.liftedIndexC !== null ? f(hp.liftedIndexC) : null,
    boundaryLayerHeightM: hp.boundaryLayerHeightM !== null ? f(hp.boundaryLayerHeightM) : null,
  };

  return {
    date: hp.date,
    hour: hp.hour,
    point: hp,
    profile,
    pressureProfile,
    surfaceTemperatureC: surfaceTemp,
    surfaceDewPointC: surfaceDew,
    surfacePressureHpa: surfacePressure,
    elevationKm,
    heatingInput,
    thermalInput,
    modelCapeJKg,
  };
}

/**
 * Iterate every daytime hour (06:00–22:00) across all dates in ascending order,
 * invoking `callback` with the built engine inputs for each valid hour.
 */
export function forEachDaytimeHour(
  data: HourlyForecastData,
  callback: (inputs: HourEngineInputs) => void,
): void {
  const grouped = pointsByDate(data);
  const dates = [...grouped.keys()].sort();
  const elevation = data.elevation ?? 0;

  for (const dateKey of dates) {
    const dayPoints = grouped.get(dateKey);
    if (dayPoints === undefined) continue;
    const dayPointsByHour = new Map<number, HourlyPoint>();
    for (const point of dayPoints) dayPointsByHour.set(point.hour, point);
    const daytimePoints = dayPoints.filter(
      (it) => it.hour >= DAYTIME_START_HOUR && it.hour <= DAYTIME_END_HOUR,
    );

    for (const hp of daytimePoints) {
      const inputs = buildHourEngineInputs(hp, dayPointsByHour, elevation);
      if (inputs !== null) callback(inputs);
    }
  }
}

/** Day points grouped and sorted by date; keys are `yyyy-MM-dd`. */
export function sortedDates(data: HourlyForecastData): string[] {
  return [...pointsByDate(data).keys()].sort();
}
