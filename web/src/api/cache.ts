/**
 * In-memory forecast cache keyed by (latitude, longitude, model).
 *
 * A deliberately simplified port of `data/forecast/ForecastCachePolicy.kt`. The
 * Android app estimates the model run time to align refreshes with model update
 * cycles; the web app keeps only the two freshness rules that matter offline:
 *
 *   1. Age: an entry expires once it is older than the model's update interval,
 *      measured from when it was fetched (we do not estimate the model run
 *      time).
 *   2. Date rollover: an entry is stale once its first forecast date is before
 *      "today" in the forecast location's local time.
 *
 * "Today" is derived from the response `utc_offset_seconds` (never the browser
 * time zone), matching the timezone rule used across the data layer.
 */

import { FORECAST_MODELS, type ForecastModelId } from "../model/forecastModel";
import type { HourlyForecastData } from "./types";

export interface ForecastCacheKey {
  latitude: number;
  longitude: number;
  model: ForecastModelId;
}

interface CacheEntry {
  data: HourlyForecastData;
  fetchedAtMillis: number;
  resolvedModel: ForecastModelId;
}

function forecastLocalDate(nowMillis: number, utcOffsetSeconds: number): string {
  const shifted = new Date(nowMillis + utcOffsetSeconds * 1000);
  const year = shifted.getUTCFullYear();
  const month = String(shifted.getUTCMonth() + 1).padStart(2, "0");
  const day = String(shifted.getUTCDate()).padStart(2, "0");
  return `${year}-${month}-${day}`;
}

function firstForecastDate(data: HourlyForecastData): string | null {
  const firstDaily = data.dailyForecasts[0]?.date;
  if (firstDaily !== undefined) return firstDaily;
  let earliest: string | null = null;
  for (const point of data.hourlyPoints) {
    if (earliest === null || point.date < earliest) earliest = point.date;
  }
  return earliest;
}

export class ForecastCache {
  private readonly entries = new Map<string, CacheEntry>();
  private readonly now: () => number;

  constructor(now: () => number = () => Date.now()) {
    this.now = now;
  }

  private static keyOf(key: ForecastCacheKey): string {
    return `${key.latitude.toFixed(4)},${key.longitude.toFixed(4)},${key.model}`;
  }

  /** Store a forecast. `resolvedModel` may differ from the requested model after fallback. */
  put(
    key: ForecastCacheKey,
    data: HourlyForecastData,
    resolvedModel: ForecastModelId = key.model,
  ): void {
    this.entries.set(ForecastCache.keyOf(key), {
      data,
      fetchedAtMillis: this.now(),
      resolvedModel,
    });
  }

  /** Return cached data only when it is still fresh; otherwise null. */
  getFresh(key: ForecastCacheKey): HourlyForecastData | null {
    const entry = this.entries.get(ForecastCache.keyOf(key));
    if (entry === undefined) return null;
    return this.isFresh(entry) ? entry.data : null;
  }

  /** Return cached data regardless of freshness (may be stale). */
  peek(key: ForecastCacheKey): HourlyForecastData | null {
    return this.entries.get(ForecastCache.keyOf(key))?.data ?? null;
  }

  clear(): void {
    this.entries.clear();
  }

  private isFresh(entry: CacheEntry): boolean {
    const nowMillis = this.now();
    const model = FORECAST_MODELS[entry.resolvedModel];
    if (nowMillis >= entry.fetchedAtMillis + model.updateIntervalMillis) return false;

    const firstDate = firstForecastDate(entry.data);
    if (firstDate !== null) {
      const today = forecastLocalDate(nowMillis, entry.data.utcOffsetSeconds);
      if (firstDate < today) return false;
    }
    return true;
  }
}
