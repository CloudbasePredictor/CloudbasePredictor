/**
 * Builds the Stüve chart UI model from real forecast data.
 *
 * 1:1 port of `ForecastChartBuilders.buildStuveChartFromData`. Returns `null`
 * when the selected day/hour lacks the required surface fields (the Android app
 * falls back to a synthetic placeholder chart, which is out of Phase-3 scope).
 * Float conversions mirror the Kotlin `.toFloat()` narrowing.
 */

import { pointsByDate } from "../../api/conversion";
import type { HourlyForecastData, HourlyPoint, PressureLevelPoint } from "../../api/types";
import { primaryCclResult } from "../../engine/cclAnalysis";
import { f } from "../../engine/float32";
import {
  estimateSurfaceHeating,
  estimateSurfacePressure,
  type ProfileLevel,
} from "../../engine/parcelAnalysis";
import { analyzeCclForForecast } from "../cclForForecast";
import {
  buildMoistureBands,
  buildParcelAscentPath,
  buildRenderableParcelPressures,
  pressureToApproxHeightMeters,
  STUVE_PRESSURE_LEVELS,
  type StuveForecastChartUiModel,
  type StuveProfilePoint,
  type StuveWindBarb,
  stuveProfilePoint,
} from "./model";

export function buildStuveChartFromData(
  hourlyData: HourlyForecastData,
  dayIndex: number,
  hour: number,
): StuveForecastChartUiModel | null {
  const grouped = pointsByDate(hourlyData);
  const dates = [...grouped.keys()].sort();
  const dateKey = dates[dayIndex];
  if (dateKey === undefined) return null;
  const dayPoints = grouped.get(dateKey);
  if (dayPoints === undefined) return null;

  let hourPoint: HourlyPoint | undefined = dayPoints.find((it) => it.hour === hour);
  if (hourPoint === undefined) {
    hourPoint = dayPoints.reduce<HourlyPoint | undefined>((best, it) => {
      if (best === undefined) return it;
      return Math.abs(it.hour - hour) < Math.abs(best.hour - hour) ? it : best;
    }, undefined);
  }
  if (hourPoint === undefined) return null;
  const point = hourPoint;

  const pressureLevels = [...point.pressureLevels].sort((a, b) => b.pressureHpa - a.pressureHpa);
  const surfacePressure =
    point.surfacePressureHpa !== null
      ? f(point.surfacePressureHpa)
      : estimateSurfacePressure(hourlyData.elevation ?? 0);
  const surfaceHeightMeters =
    hourlyData.elevation !== null
      ? f(hourlyData.elevation)
      : pressureToApproxHeightMeters(surfacePressure);
  if (point.temperature2mC === null) return null;
  if (point.dewPoint2mC === null) return null;
  const surfaceTemperatureC = f(point.temperature2mC);
  const surfaceDewPointC = f(point.dewPoint2mC);

  const levelHeightMeters = (pl: PressureLevelPoint): number =>
    pl.geopotentialHeightM !== null
      ? f(pl.geopotentialHeightM)
      : pressureToApproxHeightMeters(f(pl.pressureHpa));

  const temperatureProfile: StuveProfilePoint[] = [
    stuveProfilePoint(surfacePressure, surfaceTemperatureC, surfaceHeightMeters, true),
    ...pressureLevels
      .filter((pl) => f(pl.pressureHpa) < surfacePressure)
      .map((pl) =>
        stuveProfilePoint(f(pl.pressureHpa), f(pl.temperatureC), levelHeightMeters(pl), true),
      ),
  ];
  if (temperatureProfile.length === 0) return null;

  const dewpointProfile: StuveProfilePoint[] = [
    stuveProfilePoint(surfacePressure, surfaceDewPointC, surfaceHeightMeters, true),
    ...pressureLevels
      .filter((pl) => f(pl.pressureHpa) < surfacePressure && pl.dewPointC !== null)
      .map((pl) =>
        stuveProfilePoint(
          f(pl.pressureHpa),
          f(pl.dewPointC as number),
          levelHeightMeters(pl),
          true,
        ),
      ),
  ];

  const profileLevels: ProfileLevel[] = [
    {
      pressureHpa: surfacePressure,
      temperatureC: surfaceTemperatureC,
      dewPointC: surfaceDewPointC,
      heightKm: f(surfaceHeightMeters / 1000),
      relativeHumidityPercent: null,
      cloudCoverPercent: null,
      windSpeedKmh: null,
      isSynthetic: false,
    },
    ...pressureLevels
      .filter((pl) => f(pl.pressureHpa) < surfacePressure)
      .map(
        (pl): ProfileLevel => ({
          pressureHpa: f(pl.pressureHpa),
          temperatureC: f(pl.temperatureC),
          dewPointC: pl.dewPointC !== null ? f(pl.dewPointC) : null,
          heightKm: f(levelHeightMeters(pl) / 1000),
          relativeHumidityPercent: null,
          cloudCoverPercent: null,
          windSpeedKmh: null,
          isSynthetic: pl.isSynthetic,
        }),
      ),
  ];

  const previousPoint = dayPoints.find((it) => it.hour === point.hour - 1);
  const surfaceHeatingC = estimateSurfaceHeating({
    hourOfDay: point.hour,
    shortwaveRadiationWm2:
      point.shortwaveRadiationWm2 !== null ? f(point.shortwaveRadiationWm2) : null,
    previousShortwaveRadiationWm2:
      previousPoint?.shortwaveRadiationWm2 != null ? f(previousPoint.shortwaveRadiationWm2) : null,
    cloudCoverLowPercent:
      point.cloudCoverLowPercent !== null ? f(point.cloudCoverLowPercent) : null,
    cloudCoverMidPercent:
      point.cloudCoverMidPercent !== null ? f(point.cloudCoverMidPercent) : null,
    cloudCoverHighPercent:
      point.cloudCoverHighPercent !== null ? f(point.cloudCoverHighPercent) : null,
    precipitationMm: point.precipitationMm !== null ? f(point.precipitationMm) : null,
    isDay: point.isDay !== null ? point.isDay > 0.5 : null,
  });
  const cclResults = analyzeCclForForecast(point, surfaceHeightMeters);
  const primaryCcl = primaryCclResult(cclResults);

  const parcelAscentPath = buildParcelAscentPath(
    buildRenderableParcelPressures(
      surfacePressure,
      profileLevels.map((it) => it.pressureHpa),
    ),
    profileLevels,
    surfaceTemperatureC,
    surfaceDewPointC,
    surfacePressure,
    surfaceHeatingC,
  );

  const windBarbs: StuveWindBarb[] = [];
  for (const pl of pressureLevels) {
    if (pl.windSpeedKmh === null || pl.windDirectionDeg === null) continue;
    windBarbs.push({
      pressureHpa: f(pl.pressureHpa),
      speedKmh: f(pl.windSpeedKmh),
      directionDeg: f(pl.windDirectionDeg),
    });
  }

  return {
    pressureLevels: STUVE_PRESSURE_LEVELS,
    temperatureProfile,
    dewpointProfile,
    parcelAscentPath,
    windBarbs,
    cclPressureHpa: primaryCcl?.cclPressureHpa ?? null,
    tconC: primaryCcl?.convectiveTemperatureC ?? null,
    cclResults,
    moistureBands: buildMoistureBands(temperatureProfile, dewpointProfile),
    selectedHour: hour,
    surfacePressureHpa: surfacePressure,
  };
}
