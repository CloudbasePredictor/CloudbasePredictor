/**
 * Cloud forecast chart UI model and data builder.
 *
 * Ports `ui/screens/forecast/CloudForecastChartUiModel.kt` (types) and
 * `ForecastChartBuilders.buildCloudChartFromData`.
 */

import { pointsByDate } from "../api/conversion";
import type { HourlyForecastData } from "../api/types";
import { f } from "../engine/float32";

export interface CloudLayerUiModel {
  hour: number;
  lowCloudPercent: number;
  midCloudPercent: number;
  highCloudPercent: number;
}

export interface CloudPrecipitationUiModel {
  hour: number;
  probabilityPercent: number;
  amountMm: number;
}

export interface CloudRadiationUiModel {
  hour: number;
  radiationWm2: number;
}

export interface CloudSunshineUiModel {
  hour: number;
  durationS: number;
}

export interface CloudForecastChartUiModel {
  hours: number[];
  layers: CloudLayerUiModel[];
  precipitation: CloudPrecipitationUiModel[];
  radiation: CloudRadiationUiModel[];
  sunshine: CloudSunshineUiModel[];
}

export function buildCloudChartFromData(
  hourlyData: HourlyForecastData,
  dayIndex: number,
): CloudForecastChartUiModel | null {
  const grouped = pointsByDate(hourlyData);
  const dates = [...grouped.keys()].sort();
  const dateKey = dates[dayIndex];
  if (dateKey === undefined) return null;
  const dayPoints = grouped.get(dateKey);
  if (dayPoints === undefined) return null;

  const daytimePoints = dayPoints.filter((it) => it.hour >= 6 && it.hour <= 22);
  if (daytimePoints.length === 0) return null;

  const hours = daytimePoints.map((it) => it.hour);

  return {
    hours,
    layers: daytimePoints.map((hp) => ({
      hour: hp.hour,
      lowCloudPercent: hp.cloudCoverLowPercent !== null ? f(hp.cloudCoverLowPercent) : 0,
      midCloudPercent: hp.cloudCoverMidPercent !== null ? f(hp.cloudCoverMidPercent) : 0,
      highCloudPercent: hp.cloudCoverHighPercent !== null ? f(hp.cloudCoverHighPercent) : 0,
    })),
    precipitation: daytimePoints.map((hp) => ({
      hour: hp.hour,
      probabilityPercent:
        hp.precipitationProbabilityPercent !== null ? f(hp.precipitationProbabilityPercent) : 0,
      amountMm: hp.precipitationMm !== null ? f(hp.precipitationMm) : 0,
    })),
    radiation: daytimePoints.map((hp) => ({
      hour: hp.hour,
      radiationWm2: hp.shortwaveRadiationWm2 !== null ? f(hp.shortwaveRadiationWm2) : 0,
    })),
    sunshine: daytimePoints.map((hp) => ({
      hour: hp.hour,
      durationS: hp.sunshineDurationS !== null ? f(hp.sunshineDurationS) : 0,
    })),
  };
}
