/**
 * Converts the Open-Meteo hourly response into the intermediate
 * {@link HourlyForecastData} domain model.
 *
 * Ported 1:1 from `data/remote/HourlyForecastConversion.kt` and the daily
 * conversion in `OpenMeteoForecastResponse.kt`. Operation order and edge cases
 * (linear interpolation, linear regression extrapolation, angle unwrapping,
 * geopotential-height synthesis) are preserved to keep results numerically
 * identical to the Kotlin engine.
 */

import type { DailyForecast } from "../model/dailyForecast";
import type {
  HourlyForecastData,
  HourlyPoint,
  NullableNumberArray,
  OpenMeteoDailyResponse,
  OpenMeteoForecastResponse,
  OpenMeteoHourlyForecastResponse,
  OpenMeteoHourlyResponse,
  PressureLevelPoint,
} from "./types";

export const STANDARD_PRESSURE_LEVELS: readonly number[] = [
  1000, 975, 950, 925, 900, 875, 850, 800, 750, 700, 650, 600, 550, 500, 450, 400, 350, 300, 250,
];

const MIN_DEWPOINT_SPREAD_C = 1.5;
const MAX_SYNTHETIC_WIND_SPEED_KMH = 180.0;

interface AtmosphericSample {
  readonly heightMeters: number;
  readonly value: number;
}

/** Mirrors Kotlin `List<Double?>.getOrNull(i)`: element, or null when absent. */
function at(list: NullableNumberArray | undefined, i: number): number | null {
  if (list === undefined) return null;
  if (i < 0 || i >= list.length) return null;
  return list[i] ?? null;
}

function substringBefore(value: string, delimiter: string): string {
  const index = value.indexOf(delimiter);
  return index === -1 ? value : value.slice(0, index);
}

function substringAfter(value: string, delimiter: string): string {
  const index = value.indexOf(delimiter);
  return index === -1 ? value : value.slice(index + delimiter.length);
}

/** Mirrors Kotlin `String.toIntOrNull()` for the non-negative hour string. */
function toIntOrNull(value: string): number | null {
  return /^[+-]?\d+$/.test(value) ? Number(value) : null;
}

function collectByPressure(
  hourly: OpenMeteoHourlyResponse,
  prefix: string,
): Map<number, NullableNumberArray> {
  const map = new Map<number, NullableNumberArray>();
  for (const pressure of STANDARD_PRESSURE_LEVELS) {
    const list = hourly[`${prefix}_${pressure}hPa`] as NullableNumberArray | undefined;
    if (list !== undefined) map.set(pressure, list);
  }
  return map;
}

export function toHourlyForecastData(
  response: OpenMeteoHourlyForecastResponse,
): HourlyForecastData {
  const hourly = response.hourly;
  const times = hourly.time; // ISO-8601 local, e.g. "2026-04-12T14:00"

  const tempByPressure = collectByPressure(hourly, "temperature");
  const dewByPressure = collectByPressure(hourly, "dew_point");
  const relativeHumidityByPressure = collectByPressure(hourly, "relative_humidity");
  const cloudCoverByPressure = collectByPressure(hourly, "cloud_cover");
  const windSpeedByPressure = collectByPressure(hourly, "wind_speed");
  const windDirByPressure = collectByPressure(hourly, "wind_direction");
  const geoHeightByPressure = collectByPressure(hourly, "geopotential_height");

  const hourlyPoints: HourlyPoint[] = times.map((isoTime, i) => {
    const dateStr = substringBefore(isoTime, "T"); // "2026-04-12"
    const hourStr = substringBefore(substringAfter(isoTime, "T"), ":");
    const hour = toIntOrNull(hourStr) ?? 0;

    const pressureLevelData: PressureLevelPoint[] = [];
    for (const pHpa of STANDARD_PRESSURE_LEVELS) {
      const temp = at(tempByPressure.get(pHpa), i);
      if (temp === null) continue;
      const dew = at(dewByPressure.get(pHpa), i);
      const relativeHumidity = at(relativeHumidityByPressure.get(pHpa), i);
      const cloudCover = at(cloudCoverByPressure.get(pHpa), i);
      const ws = at(windSpeedByPressure.get(pHpa), i);
      const wd = at(windDirByPressure.get(pHpa), i);
      const gh = at(geoHeightByPressure.get(pHpa), i);
      pressureLevelData.push({
        pressureHpa: pHpa,
        temperatureC: temp,
        dewPointC: dew,
        windSpeedKmh: ws,
        windDirectionDeg: wd,
        geopotentialHeightM: gh,
        relativeHumidityPercent: relativeHumidity,
        cloudCoverPercent: cloudCover,
        isSynthetic: false,
      });
    }
    const completedPressureLevelData = completePressureLevels(pressureLevelData);

    return {
      date: dateStr,
      hour,
      temperature2mC: at(hourly.temperature_2m, i),
      dewPoint2mC: at(hourly.dew_point_2m, i),
      cloudCoverLowPercent: at(hourly.cloud_cover_low, i),
      cloudCoverMidPercent: at(hourly.cloud_cover_mid, i),
      cloudCoverHighPercent: at(hourly.cloud_cover_high, i),
      precipitationMm: at(hourly.precipitation, i),
      precipitationProbabilityPercent: at(hourly.precipitation_probability, i),
      windSpeed10mKmh: at(hourly.wind_speed_10m, i),
      windDirection10mDeg: at(hourly.wind_direction_10m, i),
      capeJKg: at(hourly.cape, i),
      freezingLevelHeightM: at(hourly.freezing_level_height, i),
      surfacePressureHpa: at(hourly.surface_pressure, i),
      shortwaveRadiationWm2: at(hourly.shortwave_radiation, i),
      sunshineDurationS: at(hourly.sunshine_duration, i),
      isDay: at(hourly.is_day, i),
      pressureLevels: completedPressureLevelData,
      liftedIndexC: at(hourly.lifted_index, i),
      convectiveInhibitionJKg: at(hourly.convective_inhibition, i),
      boundaryLayerHeightM: at(hourly.boundary_layer_height, i),
    };
  });

  const dailyForecasts = response.daily ? dailyToDomainModels(response.daily) : [];

  return {
    latitude: response.latitude,
    longitude: response.longitude,
    elevation: response.elevation ?? null,
    hourlyPoints,
    dailyForecasts,
    utcOffsetSeconds: response.utc_offset_seconds ?? 0,
    timezone: response.timezone ?? null,
  };
}

/** Group hourly points by their date string (yyyy-MM-dd). */
export function pointsByDate(data: HourlyForecastData): Map<string, HourlyPoint[]> {
  const grouped = new Map<string, HourlyPoint[]>();
  for (const point of data.hourlyPoints) {
    const bucket = grouped.get(point.date);
    if (bucket) {
      bucket.push(point);
    } else {
      grouped.set(point.date, [point]);
    }
  }
  return grouped;
}

export function dailyToDomainModels(daily: OpenMeteoDailyResponse): DailyForecast[] {
  const itemsCount = daily.time.length;
  if (itemsCount !== daily.temperature_2m_max.length) {
    throw new Error("Open-Meteo response contains mismatched max temperature data.");
  }
  if (itemsCount !== daily.temperature_2m_min.length) {
    throw new Error("Open-Meteo response contains mismatched min temperature data.");
  }
  if (itemsCount !== daily.weather_code.length) {
    throw new Error("Open-Meteo response contains mismatched weather code data.");
  }

  const result: DailyForecast[] = [];
  for (let index = 0; index < itemsCount; index++) {
    const maxTemp = daily.temperature_2m_max[index];
    const minTemp = daily.temperature_2m_min[index];
    const weatherCode = daily.weather_code[index];
    if (maxTemp === null || maxTemp === undefined) continue;
    if (minTemp === null || minTemp === undefined) continue;
    if (weatherCode === null || weatherCode === undefined) continue;
    result.push({
      date: daily.time[index],
      maxTemperatureCelsius: maxTemp,
      minTemperatureCelsius: minTemp,
      weatherCode,
    });
  }
  return result;
}

/** Convenience wrapper mirroring the daily-only response conversion. */
export function forecastToDomainModels(response: OpenMeteoForecastResponse): DailyForecast[] {
  return dailyToDomainModels(response.daily);
}

function completePressureLevels(existingLevels: PressureLevelPoint[]): PressureLevelPoint[] {
  if (existingLevels.length === 0) return [];

  const levelsByPressure = new Map<number, PressureLevelPoint>();
  for (const level of existingLevels) {
    levelsByPressure.set(level.pressureHpa, level);
  }

  for (const pressure of STANDARD_PRESSURE_LEVELS) {
    if (levelsByPressure.get(pressure) === undefined) {
      const synthesized = synthesizePressureLevel(pressure, [...levelsByPressure.values()]);
      if (synthesized !== null) {
        levelsByPressure.set(pressure, synthesized);
      }
    }
  }

  return [...levelsByPressure.values()].sort((a, b) => b.pressureHpa - a.pressureHpa);
}

function synthesizePressureLevel(
  targetPressureHpa: number,
  sourceLevels: PressureLevelPoint[],
): PressureLevelPoint | null {
  if (sourceLevels.length === 0) return null;

  const targetHeightMeters = approxHeightForPressureHpa(targetPressureHpa);
  const temperatureC = extrapolateField(
    targetHeightMeters,
    sourceLevels,
    (level) => level.temperatureC,
  );
  if (temperatureC === null) return null;

  const dewRaw = extrapolateField(targetHeightMeters, sourceLevels, (level) => level.dewPointC);
  const dewPointC = dewRaw === null ? null : Math.min(dewRaw, temperatureC - MIN_DEWPOINT_SPREAD_C);

  const windRaw = extrapolateField(targetHeightMeters, sourceLevels, (level) => level.windSpeedKmh);
  const windSpeedKmh =
    windRaw === null ? null : coerceIn(windRaw, 0.0, MAX_SYNTHETIC_WIND_SPEED_KMH);

  const windDirectionDeg = extrapolateDirection(targetHeightMeters, sourceLevels);
  const geopotentialHeightM = synthesizeGeopotentialHeight(targetPressureHpa, sourceLevels);

  return {
    pressureHpa: targetPressureHpa,
    temperatureC,
    dewPointC,
    windSpeedKmh,
    windDirectionDeg,
    geopotentialHeightM,
    relativeHumidityPercent: null,
    cloudCoverPercent: null,
    isSynthetic: true,
  };
}

function extrapolateField(
  targetHeightMeters: number,
  sourceLevels: PressureLevelPoint[],
  valueSelector: (level: PressureLevelPoint) => number | null,
): number | null {
  const samples: AtmosphericSample[] = [];
  for (const level of sourceLevels) {
    const value = valueSelector(level);
    if (value === null) continue;
    samples.push({
      heightMeters: level.geopotentialHeightM ?? approxHeightForPressureHpa(level.pressureHpa),
      value,
    });
  }
  samples.sort((a, b) => a.heightMeters - b.heightMeters);

  if (samples.length < 2) return samples.length === 0 ? null : samples[0].value;

  const lowerIndex = samples.findLastIndex((s) => s.heightMeters <= targetHeightMeters);
  const upperIndex = samples.findIndex((s) => s.heightMeters >= targetHeightMeters);
  if (lowerIndex >= 0 && upperIndex >= 0 && lowerIndex !== upperIndex) {
    const lower = samples[lowerIndex];
    const upper = samples[upperIndex];
    return interpolateLinear(
      targetHeightMeters,
      lower.heightMeters,
      lower.value,
      upper.heightMeters,
      upper.value,
    );
  }

  const regressionSamples =
    targetHeightMeters > samples[samples.length - 1].heightMeters
      ? samples.slice(Math.max(0, samples.length - Math.min(samples.length, 4)))
      : samples.slice(0, Math.min(samples.length, 4));
  return regressLinear(targetHeightMeters, regressionSamples);
}

function extrapolateDirection(
  targetHeightMeters: number,
  sourceLevels: PressureLevelPoint[],
): number | null {
  const directionSamples: AtmosphericSample[] = [];
  for (const level of sourceLevels) {
    const direction = level.windDirectionDeg;
    if (direction === null) continue;
    directionSamples.push({
      heightMeters: level.geopotentialHeightM ?? approxHeightForPressureHpa(level.pressureHpa),
      value: direction,
    });
  }
  directionSamples.sort((a, b) => a.heightMeters - b.heightMeters);

  if (directionSamples.length < 2) {
    return directionSamples.length === 0 ? null : directionSamples[0].value;
  }

  const unwrappedValues = unwrapAngles(directionSamples.map((s) => s.value));
  const regressionSamples: AtmosphericSample[] = directionSamples.map((sample, index) => ({
    heightMeters: sample.heightMeters,
    value: unwrappedValues[index],
  }));

  const lowerIndex = regressionSamples.findLastIndex((s) => s.heightMeters <= targetHeightMeters);
  const upperIndex = regressionSamples.findIndex((s) => s.heightMeters >= targetHeightMeters);
  let interpolated: number | null;
  if (lowerIndex >= 0 && upperIndex >= 0 && lowerIndex !== upperIndex) {
    const lower = regressionSamples[lowerIndex];
    const upper = regressionSamples[upperIndex];
    interpolated = interpolateLinear(
      targetHeightMeters,
      lower.heightMeters,
      lower.value,
      upper.heightMeters,
      upper.value,
    );
  } else {
    const fitSamples =
      targetHeightMeters > regressionSamples[regressionSamples.length - 1].heightMeters
        ? regressionSamples.slice(
            Math.max(0, regressionSamples.length - Math.min(regressionSamples.length, 4)),
          )
        : regressionSamples.slice(0, Math.min(regressionSamples.length, 4));
    interpolated = regressLinear(targetHeightMeters, fitSamples);
  }
  if (interpolated === null) return null;

  return normalizeAngle(interpolated);
}

function synthesizeGeopotentialHeight(
  targetPressureHpa: number,
  sourceLevels: PressureLevelPoint[],
): number {
  const offsets: number[] = [];
  for (const level of [...sourceLevels].sort((a, b) => a.pressureHpa - b.pressureHpa)) {
    if (level.geopotentialHeightM !== null) {
      offsets.push(level.geopotentialHeightM - approxHeightForPressureHpa(level.pressureHpa));
    }
  }
  const lastFour = offsets.slice(Math.max(0, offsets.length - 4));
  const heightOffsetMeters = lastFour.length === 0 ? 0.0 : average(lastFour);
  return approxHeightForPressureHpa(targetPressureHpa) + heightOffsetMeters;
}

function approxHeightForPressureHpa(pressureHpa: number): number {
  return 44330.0 * (1.0 - (pressureHpa / 1013.25) ** 0.1903);
}

function interpolateLinear(x: number, x0: number, y0: number, x1: number, y1: number): number {
  if (Math.abs(x1 - x0) < 1e-6) return y0;
  const fraction = (x - x0) / (x1 - x0);
  return y0 + fraction * (y1 - y0);
}

function regressLinear(targetX: number, samples: AtmosphericSample[]): number | null {
  if (samples.length === 0) return null;
  if (samples.length === 1) return samples[0].value;

  const meanX = average(samples.map((s) => s.heightMeters));
  const meanY = average(samples.map((s) => s.value));
  let denominator = 0;
  for (const sample of samples) {
    denominator += (sample.heightMeters - meanX) * (sample.heightMeters - meanX);
  }
  if (Math.abs(denominator) < 1e-6) return samples[samples.length - 1].value;

  let numerator = 0;
  for (const sample of samples) {
    numerator += (sample.heightMeters - meanX) * (sample.value - meanY);
  }
  const slope = numerator / denominator;
  return meanY + slope * (targetX - meanX);
}

function unwrapAngles(values: number[]): number[] {
  if (values.length === 0) return [];

  const result: number[] = [values[0]];
  for (let index = 1; index < values.length; index++) {
    let adjusted = values[index];
    const previous = result[result.length - 1];
    while (adjusted - previous > 180.0) adjusted -= 360.0;
    while (previous - adjusted > 180.0) adjusted += 360.0;
    result.push(adjusted);
  }
  return result;
}

function normalizeAngle(angleDeg: number): number {
  let normalized = angleDeg % 360.0;
  if (normalized < 0.0) normalized += 360.0;
  return normalized;
}

function coerceIn(value: number, minimum: number, maximum: number): number {
  return Math.min(Math.max(value, minimum), maximum);
}

function average(values: number[]): number {
  let sum = 0;
  for (const value of values) sum += value;
  return sum / values.length;
}
