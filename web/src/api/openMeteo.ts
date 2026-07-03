/**
 * Open-Meteo `/v1/forecast` client.
 *
 * Ported from `data/remote/OpenMeteoApi.kt` and
 * `data/remote/OpenMeteoRemoteDataSource.kt`. The `HOURLY_VARIABLES` list, the
 * `daily` variables, and the fallback-on-400 behaviour are copied verbatim so
 * the web app requests exactly the same data as the Android app.
 *
 * Timezone rule: `timezone=auto` is requested and the naive local timestamp
 * strings the API returns are kept as-is. They are never parsed through a
 * browser-zone `Date`.
 */

import { FORECAST_MODELS, type ForecastModelId, fallbackFor } from "../model/forecastModel";
import { toHourlyForecastData } from "./conversion";
import type {
  HourlyForecastData,
  OpenMeteoForecastResponse,
  OpenMeteoHourlyForecastResponse,
} from "./types";

const BASE_URL = "https://api.open-meteo.com/v1/forecast";

const DAILY_VARIABLES = "temperature_2m_max,temperature_2m_min,weather_code";

/**
 * Surface + pressure-level variables requested for chart construction.
 *
 * Pressure levels: 1000, 975, 950, 925, 900, 875, 850, 800, 750, 700, 650, 600,
 * 550, 500, 450, 400, 350, 300, 250 hPa.
 * Copied verbatim from `OpenMeteoApi.kt` (`HOURLY_VARIABLES`).
 */
export const HOURLY_VARIABLES =
  "temperature_2m,dew_point_2m," +
  "cloud_cover_low,cloud_cover_mid,cloud_cover_high," +
  "precipitation,precipitation_probability," +
  "wind_speed_10m,wind_direction_10m," +
  "cape,lifted_index,convective_inhibition,boundary_layer_height,freezing_level_height," +
  "surface_pressure,shortwave_radiation,sunshine_duration,is_day," +
  "temperature_1000hPa,temperature_975hPa,temperature_950hPa,temperature_925hPa,temperature_900hPa," +
  "temperature_875hPa,temperature_850hPa,temperature_800hPa,temperature_750hPa," +
  "temperature_700hPa,temperature_650hPa,temperature_600hPa,temperature_550hPa,temperature_500hPa," +
  "temperature_450hPa,temperature_400hPa,temperature_350hPa,temperature_300hPa,temperature_250hPa," +
  "dew_point_1000hPa,dew_point_975hPa,dew_point_950hPa,dew_point_925hPa,dew_point_900hPa," +
  "dew_point_875hPa,dew_point_850hPa,dew_point_800hPa,dew_point_750hPa," +
  "dew_point_700hPa,dew_point_650hPa,dew_point_600hPa,dew_point_550hPa,dew_point_500hPa," +
  "dew_point_450hPa,dew_point_400hPa,dew_point_350hPa,dew_point_300hPa,dew_point_250hPa," +
  "relative_humidity_1000hPa,relative_humidity_975hPa,relative_humidity_950hPa,relative_humidity_925hPa," +
  "relative_humidity_900hPa,relative_humidity_875hPa,relative_humidity_850hPa,relative_humidity_800hPa," +
  "relative_humidity_750hPa,relative_humidity_700hPa,relative_humidity_650hPa,relative_humidity_600hPa," +
  "relative_humidity_550hPa,relative_humidity_500hPa,relative_humidity_450hPa,relative_humidity_400hPa," +
  "relative_humidity_350hPa,relative_humidity_300hPa,relative_humidity_250hPa," +
  "cloud_cover_1000hPa,cloud_cover_975hPa,cloud_cover_950hPa,cloud_cover_925hPa," +
  "cloud_cover_900hPa,cloud_cover_875hPa,cloud_cover_850hPa,cloud_cover_800hPa," +
  "cloud_cover_750hPa,cloud_cover_700hPa,cloud_cover_650hPa,cloud_cover_600hPa," +
  "cloud_cover_550hPa,cloud_cover_500hPa,cloud_cover_450hPa,cloud_cover_400hPa," +
  "cloud_cover_350hPa,cloud_cover_300hPa,cloud_cover_250hPa," +
  "wind_speed_1000hPa,wind_speed_975hPa,wind_speed_950hPa,wind_speed_925hPa,wind_speed_900hPa," +
  "wind_speed_875hPa,wind_speed_850hPa,wind_speed_800hPa,wind_speed_750hPa," +
  "wind_speed_700hPa,wind_speed_650hPa,wind_speed_600hPa,wind_speed_550hPa,wind_speed_500hPa," +
  "wind_speed_450hPa,wind_speed_400hPa,wind_speed_350hPa,wind_speed_300hPa,wind_speed_250hPa," +
  "wind_direction_1000hPa,wind_direction_975hPa,wind_direction_950hPa,wind_direction_925hPa,wind_direction_900hPa," +
  "wind_direction_875hPa,wind_direction_850hPa,wind_direction_800hPa,wind_direction_750hPa," +
  "wind_direction_700hPa,wind_direction_650hPa,wind_direction_600hPa,wind_direction_550hPa,wind_direction_500hPa," +
  "wind_direction_450hPa,wind_direction_400hPa,wind_direction_350hPa,wind_direction_300hPa,wind_direction_250hPa," +
  "geopotential_height_1000hPa,geopotential_height_975hPa,geopotential_height_950hPa,geopotential_height_925hPa,geopotential_height_900hPa," +
  "geopotential_height_875hPa,geopotential_height_850hPa,geopotential_height_800hPa,geopotential_height_750hPa," +
  "geopotential_height_700hPa,geopotential_height_650hPa,geopotential_height_600hPa,geopotential_height_550hPa,geopotential_height_500hPa," +
  "geopotential_height_450hPa,geopotential_height_400hPa,geopotential_height_350hPa,geopotential_height_300hPa,geopotential_height_250hPa";

/** HTTP error carrying the response status, so callers can react to 400s. */
export class OpenMeteoHttpError extends Error {
  readonly status: number;

  constructor(status: number, message: string) {
    super(message);
    this.name = "OpenMeteoHttpError";
    this.status = status;
  }
}

async function fetchJson<T>(url: string, signal?: AbortSignal): Promise<T> {
  const response = await fetch(url, { signal });
  if (!response.ok) {
    const body = await response.text().catch(() => "");
    throw new OpenMeteoHttpError(
      response.status,
      `Open-Meteo request failed (${response.status}): ${body.slice(0, 200)}`,
    );
  }
  return (await response.json()) as T;
}

export interface HourlyForecastRequest {
  latitude: number;
  longitude: number;
  model: ForecastModelId;
  forecastDays?: number;
  signal?: AbortSignal;
}

function buildHourlyUrl(request: HourlyForecastRequest): string {
  const params = new URLSearchParams({
    latitude: String(request.latitude),
    longitude: String(request.longitude),
    hourly: HOURLY_VARIABLES,
    daily: DAILY_VARIABLES,
    forecast_days: String(request.forecastDays ?? 7),
    timezone: "auto",
  });
  // BEST_MATCH omits the `models` parameter so Open-Meteo auto-selects.
  if (request.model !== "BEST_MATCH") {
    params.set("models", FORECAST_MODELS[request.model].apiName);
  }
  return `${BASE_URL}?${params.toString()}`;
}

/**
 * Fetch hourly + pressure-level forecast for a specific weather model and
 * convert it to {@link HourlyForecastData}.
 */
export async function fetchHourlyForecast(
  request: HourlyForecastRequest,
): Promise<HourlyForecastData> {
  const raw = await fetchJson<OpenMeteoHourlyForecastResponse>(
    buildHourlyUrl(request),
    request.signal,
  );
  return toHourlyForecastData(raw);
}

export interface ResolvedHourlyForecast {
  model: ForecastModelId;
  data: HourlyForecastData;
}

/**
 * Fetch hourly forecast, falling back through the model's fallback chain if the
 * requested model is not available for that location (HTTP 400).
 */
export async function fetchHourlyForecastWithFallback(
  request: HourlyForecastRequest,
): Promise<ResolvedHourlyForecast> {
  let currentModel = request.model;
  while (true) {
    try {
      const data = await fetchHourlyForecast({ ...request, model: currentModel });
      return { model: currentModel, data };
    } catch (error) {
      if (
        error instanceof OpenMeteoHttpError &&
        error.status === 400 &&
        currentModel !== "BEST_MATCH"
      ) {
        currentModel = fallbackFor(currentModel);
      } else {
        throw error;
      }
    }
  }
}

/** Fetch the lightweight daily-only forecast (no hourly / pressure levels). */
export async function fetchDailyForecast(
  latitude: number,
  longitude: number,
  forecastDays = 14,
  signal?: AbortSignal,
): Promise<OpenMeteoForecastResponse> {
  const params = new URLSearchParams({
    latitude: String(latitude),
    longitude: String(longitude),
    daily: DAILY_VARIABLES,
    forecast_days: String(forecastDays),
    timezone: "auto",
  });
  return fetchJson<OpenMeteoForecastResponse>(`${BASE_URL}?${params.toString()}`, signal);
}
