/**
 * Forecast-data source for the app shell.
 *
 * Supports a deterministic dev/fixture mode that loads the bundled Open-Meteo
 * capture (`api/__fixtures__/brauneck_icon_seamless_20260418.json`) instead of
 * the network, so the views render fixed data for screenshots and offline dev.
 * Live mode fetches the selected location + model from Open-Meteo.
 *
 * Live fetches go through an in-memory {@link ForecastCache}: a location/model
 * already fetched this session is served from cache instead of re-hitting the
 * API, honouring Open-Meteo fair use (refetch only on explicit change / stale
 * cache, never poll).
 */

import rawBrauneckAsset from "../api/__fixtures__/brauneck_icon_seamless_20260418.json";
import { ForecastCache } from "../api/cache";
import { toHourlyForecastData } from "../api/conversion";
import { fetchHourlyForecastWithFallback } from "../api/openMeteo";
import type { HourlyForecastData, OpenMeteoHourlyForecastResponse } from "../api/types";
import { FORECAST_MODELS, type ForecastModelId } from "../model/forecastModel";
import type { PlaceLocation } from "../model/placeLocation";

/** Reference site used as the default location and offline fixture. */
export const BRAUNECK: PlaceLocation = {
  name: "Brauneck",
  latitude: 47.68,
  longitude: 11.63,
};

/** Model whose data ships as the offline fixture. */
export const FIXTURE_MODEL: ForecastModelId = "ICON_SEAMLESS";

const cache = new ForecastCache();

/**
 * Whether the app should use the bundled fixture instead of the network.
 * Defaults to fixture in dev (offline-friendly); prod defaults to live. Both are
 * overridable via `?data=fixture` / `?data=live`.
 */
export function isFixtureMode(): boolean {
  const params = new URLSearchParams(window.location.search);
  const requested = params.get("data");
  if (requested === "fixture") return true;
  if (requested === "live") return false;
  return import.meta.env.DEV;
}

export function loadFixtureForecast(): HourlyForecastData {
  return toHourlyForecastData(rawBrauneckAsset as unknown as OpenMeteoHourlyForecastResponse);
}

export interface LoadForecastRequest {
  location: PlaceLocation;
  model: ForecastModelId;
  signal?: AbortSignal;
}

export interface LoadForecastResult {
  data: HourlyForecastData;
  /**
   * Model that actually produced the data. Differs from the requested model
   * when Open-Meteo has no coverage there and the fallback chain kicked in
   * (`ForecastModel.kt` chains, e.g. ICON D2 -> EU -> Global -> Best Effort).
   */
  resolvedModel: ForecastModelId;
}

/**
 * Load the forecast for a location + model. Fixture mode always returns the
 * bundled capture; live mode serves fresh cache hits or fetches once (following
 * the model fallback chain for out-of-coverage models) and caches.
 */
export async function loadForecast(request: LoadForecastRequest): Promise<LoadForecastResult> {
  if (isFixtureMode()) {
    return { data: loadFixtureForecast(), resolvedModel: FIXTURE_MODEL };
  }

  const { location, model, signal } = request;
  const key = { latitude: location.latitude, longitude: location.longitude, model };
  const cached = cache.getFresh(key);
  if (cached !== null) {
    return { data: cached, resolvedModel: model };
  }

  const { model: resolvedModel, data } = await fetchHourlyForecastWithFallback({
    latitude: location.latitude,
    longitude: location.longitude,
    model,
    forecastDays: FORECAST_MODELS[model].availableForecastDays,
    signal,
  });
  cache.put(key, data);
  return { data, resolvedModel };
}
