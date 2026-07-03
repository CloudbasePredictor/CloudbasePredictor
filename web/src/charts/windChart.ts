/**
 * Wind forecast chart UI model and data builder.
 *
 * Ports `ui/screens/forecast/WindForecastChartUiModel.kt` (types) and
 * `ForecastChartBuilders.buildWindChartFromData`. The per-pressure representative
 * altitude uses `round(x*20)/20` snapping; per the Phase-2 handoff, Kotlin
 * `round` maps to `Math.round` here (round half up) — a negligible difference at
 * exact halves for averaged geopotential heights.
 */

import { pointsByDate } from "../api/conversion";
import type { HourlyForecastData } from "../api/types";
import type { CclHourlyResult } from "../engine/cclAnalysis";
import { primaryCclResult } from "../engine/cclAnalysis";
import { f } from "../engine/float32";
import { analyzeCclForForecast } from "./cclForForecast";

export interface WindAltitudeBand {
  centerKm: number;
  bottomKm: number;
  topKm: number;
}

export interface WindLevelMarker {
  hour: number;
  altitudeKm: number;
}

export interface WindForecastCellUiModel {
  hour: number;
  altitudeKm: number;
  speedKmh: number;
  /** Meteorological direction FROM, degrees. */
  directionDeg: number;
}

export interface WindForecastChartUiModel {
  hours: number[];
  altitudeBandsKm: number[];
  altitudeBands: WindAltitudeBand[];
  cells: WindForecastCellUiModel[];
  freezingLevelKm: WindLevelMarker[];
  cclKm: WindLevelMarker[];
  cclResults: CclHourlyResult[];
}

interface WindPressureSample {
  hour: number;
  pressureHpa: number;
  heightAslKm: number;
  speedKmh: number;
  directionDeg: number;
}

export function buildWindChartFromData(
  hourlyData: HourlyForecastData,
  dayIndex: number,
  maxAltitudeKm: number,
): WindForecastChartUiModel | null {
  const grouped = pointsByDate(hourlyData);
  const dates = [...grouped.keys()].sort();
  const dateKey = dates[dayIndex];
  if (dateKey === undefined) return null;
  const dayPoints = grouped.get(dateKey);
  if (dayPoints === undefined) return null;

  const daytimePoints = dayPoints.filter((it) => it.hour >= 6 && it.hour <= 22);
  if (daytimePoints.length === 0) return null;

  const elevation = hourlyData.elevation ?? 0;
  const elevationKm = f(f(elevation) / 1000);
  const hours = daytimePoints.map((it) => it.hour);

  const altitudeSet = new Set<number>();
  const cellList: WindForecastCellUiModel[] = [];
  const pressureSamples: WindPressureSample[] = [];

  for (const hp of daytimePoints) {
    const surfWindSpeed = hp.windSpeed10mKmh;
    const surfWindDir = hp.windDirection10mDeg;
    if (surfWindSpeed !== null && surfWindDir !== null) {
      const surfAlt = f(elevationKm + 0.01);
      altitudeSet.add(surfAlt);
      cellList.push({
        hour: hp.hour,
        altitudeKm: surfAlt,
        speedKmh: f(surfWindSpeed),
        directionDeg: f(surfWindDir),
      });
    }

    for (const pl of hp.pressureLevels) {
      if (pl.geopotentialHeightM === null) continue;
      const rawHeightAsl = f(f(pl.geopotentialHeightM) / 1000);
      if (pl.windSpeedKmh === null || pl.windDirectionDeg === null) continue;
      pressureSamples.push({
        hour: hp.hour,
        pressureHpa: pl.pressureHpa,
        heightAslKm: rawHeightAsl,
        speedKmh: f(pl.windSpeedKmh),
        directionDeg: f(pl.windDirectionDeg),
      });
    }
  }

  // One representative altitude per pressure level for the whole day.
  const byPressure = new Map<number, WindPressureSample[]>();
  for (const sample of pressureSamples) {
    const list = byPressure.get(sample.pressureHpa);
    if (list === undefined) byPressure.set(sample.pressureHpa, [sample]);
    else list.push(sample);
  }
  const representativeAltitudeByPressure = new Map<number, number>();
  for (const [pressure, samples] of byPressure) {
    let sum = 0;
    for (const s of samples) sum += s.heightAslKm;
    const averageHeightKm = f(sum / samples.length);
    const snapped = f(Math.round(f(averageHeightKm * 20)) / 20);
    if (snapped >= elevationKm && snapped <= f(elevationKm + maxAltitudeKm)) {
      representativeAltitudeByPressure.set(pressure, snapped);
    }
  }

  for (const sample of pressureSamples) {
    const heightAslKm = representativeAltitudeByPressure.get(sample.pressureHpa);
    if (heightAslKm === undefined) continue;
    altitudeSet.add(heightAslKm);
    cellList.push({
      hour: sample.hour,
      altitudeKm: heightAslKm,
      speedKmh: sample.speedKmh,
      directionDeg: sample.directionDeg,
    });
  }

  if (cellList.length === 0) return null;

  const freezingLevelMarkers: WindLevelMarker[] = [];
  for (const hp of daytimePoints) {
    if (hp.freezingLevelHeightM === null) continue;
    const flAslKm = f(hp.freezingLevelHeightM / 1000);
    if (flAslKm < elevationKm) continue;
    freezingLevelMarkers.push({ hour: hp.hour, altitudeKm: flAslKm });
  }

  const cclResultsByHour = new Map<number, CclHourlyResult[]>();
  for (const hp of daytimePoints) {
    cclResultsByHour.set(hp.hour, analyzeCclForForecast(hp, f(elevation)));
  }
  const cclMarkers: WindLevelMarker[] = [];
  for (const hp of daytimePoints) {
    const primaryCcl = primaryCclResult(cclResultsByHour.get(hp.hour) ?? []);
    if (primaryCcl === null) continue;
    const cclMslM = primaryCcl.cclHeightMslM;
    if (cclMslM === null) continue;
    if (!primaryCcl.reachable) continue;
    const cclMslKm = f(cclMslM / 1000);
    if (cclMslKm < elevationKm) continue;
    cclMarkers.push({ hour: hp.hour, altitudeKm: cclMslKm });
  }

  const sortedAlts = [...altitudeSet].sort((a, b) => a - b);
  const altitudeBands: WindAltitudeBand[] = sortedAlts.map((alt, idx) => {
    const lowerDist = idx > 0 ? f((alt - sortedAlts[idx - 1]) / 2) : 0.2;
    const upperDist = idx < sortedAlts.length - 1 ? f((sortedAlts[idx + 1] - alt) / 2) : 0.2;
    return { centerKm: alt, bottomKm: f(alt - lowerDist), topKm: f(alt + upperDist) };
  });

  const cclResults: CclHourlyResult[] = [];
  for (const list of cclResultsByHour.values()) cclResults.push(...list);

  return {
    hours,
    altitudeBandsKm: sortedAlts,
    altitudeBands,
    cells: cellList,
    freezingLevelKm: freezingLevelMarkers,
    cclKm: cclMarkers,
    cclResults,
  };
}
