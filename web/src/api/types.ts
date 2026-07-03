/**
 * Open-Meteo response types and the intermediate forecast domain model.
 *
 * Raw response interfaces (`OpenMeteo*`) mirror the JSON `/v1/forecast` payload
 * with snake_case keys, ported from `OpenMeteoHourlyForecastResponse.kt` and
 * `OpenMeteoForecastResponse.kt`. Domain types (`HourlyForecastData`,
 * `HourlyPoint`, `PressureLevelPoint`) mirror the outputs of
 * `HourlyForecastConversion.kt`.
 */

import type { DailyForecast } from "../model/dailyForecast";

/** Parallel array: one nullable value per hourly time step. */
export type NullableNumberArray = (number | null)[];

/**
 * Hourly data block from Open-Meteo.
 *
 * Timestamps are ISO-8601 local time (e.g. "2026-04-12T14:00"). Lists are
 * parallel: index `i` corresponds to `time[i]`. Pressure-level fields use the
 * suffix pattern `_<level>hPa` (e.g. `temperature_1000hPa`) and are read
 * dynamically during conversion via the index signature.
 */
export interface OpenMeteoHourlyResponse {
  time: string[];

  temperature_2m?: NullableNumberArray;
  dew_point_2m?: NullableNumberArray;

  cloud_cover_low?: NullableNumberArray;
  cloud_cover_mid?: NullableNumberArray;
  cloud_cover_high?: NullableNumberArray;

  precipitation?: NullableNumberArray;
  precipitation_probability?: NullableNumberArray;

  wind_speed_10m?: NullableNumberArray;
  wind_direction_10m?: NullableNumberArray;

  cape?: NullableNumberArray;
  lifted_index?: NullableNumberArray;
  convective_inhibition?: NullableNumberArray;
  boundary_layer_height?: NullableNumberArray;

  freezing_level_height?: NullableNumberArray;
  surface_pressure?: NullableNumberArray;
  shortwave_radiation?: NullableNumberArray;
  sunshine_duration?: NullableNumberArray;
  is_day?: NullableNumberArray;

  // Pressure-level fields such as `temperature_1000hPa`, `dew_point_850hPa`, etc.
  [key: string]: string[] | NullableNumberArray | undefined;
}

export interface OpenMeteoDailyResponse {
  time: string[];
  temperature_2m_max: NullableNumberArray;
  temperature_2m_min: NullableNumberArray;
  weather_code: (number | null)[];
}

/**
 * Response from the Open-Meteo `/v1/forecast` endpoint when requesting hourly
 * and daily data, including pressure-level variables.
 */
export interface OpenMeteoHourlyForecastResponse {
  latitude: number;
  longitude: number;
  elevation?: number | null;
  utc_offset_seconds?: number;
  timezone?: string | null;
  daily?: OpenMeteoDailyResponse | null;
  hourly: OpenMeteoHourlyResponse;
}

/** Light daily-only forecast response. */
export interface OpenMeteoForecastResponse {
  daily: OpenMeteoDailyResponse;
}

/**
 * Atmospheric data at a single pressure level for one hour.
 */
export interface PressureLevelPoint {
  /** Pressure level, hPa. */
  pressureHpa: number;
  /** Temperature, degrees C. */
  temperatureC: number;
  /** Dewpoint temperature, degrees C. Null if not available. */
  dewPointC: number | null;
  /** Wind speed, km/h. */
  windSpeedKmh: number | null;
  /** Wind direction, degrees (meteorological). */
  windDirectionDeg: number | null;
  /** Geopotential height, metres above sea level. */
  geopotentialHeightM: number | null;
  /** Relative humidity at this pressure level, percent. Null if unavailable. */
  relativeHumidityPercent: number | null;
  /** Cloud cover at this pressure level, percent. Null if unavailable. */
  cloudCoverPercent: number | null;
  /** True when this level was synthesized by interpolation/extrapolation. */
  isSynthetic: boolean;
}

/**
 * One hour of forecast data combining surface and pressure-level variables.
 */
export interface HourlyPoint {
  /** Date in yyyy-MM-dd format. */
  date: string;
  /** Local hour (0-23). */
  hour: number;
  temperature2mC: number | null;
  dewPoint2mC: number | null;
  cloudCoverLowPercent: number | null;
  cloudCoverMidPercent: number | null;
  cloudCoverHighPercent: number | null;
  precipitationMm: number | null;
  precipitationProbabilityPercent: number | null;
  windSpeed10mKmh: number | null;
  windDirection10mDeg: number | null;
  capeJKg: number | null;
  freezingLevelHeightM: number | null;
  surfacePressureHpa: number | null;
  shortwaveRadiationWm2: number | null;
  sunshineDurationS: number | null;
  isDay: number | null;
  pressureLevels: PressureLevelPoint[];
  liftedIndexC: number | null;
  convectiveInhibitionJKg: number | null;
  boundaryLayerHeightM: number | null;
}

/**
 * Intermediate domain model holding all forecast data needed for chart
 * construction.
 */
export interface HourlyForecastData {
  latitude: number;
  longitude: number;
  elevation: number | null;
  hourlyPoints: HourlyPoint[];
  dailyForecasts: DailyForecast[];
  utcOffsetSeconds: number;
  timezone: string | null;
}
