/**
 * Shareable forecast state encoded in the URL hash.
 *
 * The Android app cannot share a forecast by URL; the web version can. The hash
 * carries everything needed to reproduce a view:
 *
 *   #/forecast?lat=47.680000&lon=11.630000&model=icon_seamless&day=0&hour=12&view=thermic&name=Brauneck
 *
 * `lat`/`lon` are required for a concrete route; the rest fall back to sensible
 * defaults. Coordinates are formatted with six decimals (matching
 * `PlaceLocation.toRouteValue`); the model uses the Open-Meteo `apiName` so the
 * link stays readable and stable. Parsing is total: malformed values fall back
 * to defaults rather than throwing.
 */

import {
  FORECAST_MODELS,
  type ForecastModelId,
  forecastModelFromApiName,
} from "../model/forecastModel";
import type { PlaceLocation } from "../model/placeLocation";

export type ForecastTab = "thermic" | "wind" | "cloud" | "stuve";

const FORECAST_TABS: readonly ForecastTab[] = ["thermic", "wind", "cloud", "stuve"];

/** Hour range exposed by the Stüve time slider. */
export const MIN_HOUR = 6;
export const MAX_HOUR = 22;
export const DEFAULT_HOUR = 12;

/** Everything needed to reproduce a forecast view from a shared link. */
export interface ForecastRoute {
  readonly location: PlaceLocation;
  readonly model: ForecastModelId;
  /** Day index into the available forecast days (0 = first/today). */
  readonly day: number;
  /** Selected hour for the Stüve view, clamped to [MIN_HOUR, MAX_HOUR]. */
  readonly hour: number;
  readonly view: ForecastTab;
}

const HASH_PREFIX = "#/forecast";

function clampHour(hour: number): number {
  if (!Number.isFinite(hour)) return DEFAULT_HOUR;
  return Math.min(MAX_HOUR, Math.max(MIN_HOUR, Math.round(hour)));
}

function parseCoordinate(raw: string | null): number | null {
  if (raw === null) return null;
  const trimmed = raw.trim();
  if (trimmed.length === 0) return null;
  const value = Number(trimmed);
  return Number.isFinite(value) ? value : null;
}

function isValidLatitude(value: number): boolean {
  return value >= -90 && value <= 90;
}

function isValidLongitude(value: number): boolean {
  return value >= -180 && value <= 180;
}

/**
 * Parse a location from the hash query, or null when latitude/longitude are
 * missing or invalid (the caller then uses its default location).
 */
export function parseLocationFromParams(params: URLSearchParams): PlaceLocation | null {
  const latitude = parseCoordinate(params.get("lat"));
  const longitude = parseCoordinate(params.get("lon"));
  if (latitude === null || longitude === null) return null;
  if (!isValidLatitude(latitude) || !isValidLongitude(longitude)) return null;

  const rawName = params.get("name");
  const name = rawName?.trim();
  if (name !== undefined && name.length > 0) {
    return { latitude, longitude, name };
  }
  return { latitude, longitude };
}

/**
 * Parse a full forecast route from a location hash. Returns null when no valid
 * location is present so the caller can fall back to its default route while
 * still honouring any model/day/hour/view already in the hash.
 */
export function parseForecastHash(hash: string): ForecastRoute | null {
  const withoutHash = hash.startsWith("#") ? hash.slice(1) : hash;
  const queryIndex = withoutHash.indexOf("?");
  if (queryIndex < 0) return null;

  const path = withoutHash.slice(0, queryIndex);
  if (path !== "/forecast" && path !== "/") return null;

  const params = new URLSearchParams(withoutHash.slice(queryIndex + 1));
  const location = parseLocationFromParams(params);
  if (location === null) return null;

  return {
    location,
    model: parseModel(params.get("model")),
    day: parseDay(params.get("day")),
    hour: parseHour(params.get("hour")),
    view: parseView(params.get("view")),
  };
}

function parseHour(raw: string | null): number {
  if (raw === null || raw.trim().length === 0) return DEFAULT_HOUR;
  return clampHour(Number(raw));
}

function parseModel(apiName: string | null): ForecastModelId {
  if (apiName === null) return "ICON_SEAMLESS";
  return forecastModelFromApiName(apiName.trim())?.id ?? "ICON_SEAMLESS";
}

function parseDay(raw: string | null): number {
  if (raw === null) return 0;
  const value = Number.parseInt(raw, 10);
  if (!Number.isFinite(value) || value < 0) return 0;
  return value;
}

function parseView(raw: string | null): ForecastTab {
  const candidate = raw?.trim() as ForecastTab | undefined;
  return candidate !== undefined && FORECAST_TABS.includes(candidate) ? candidate : "thermic";
}

/** Build a shareable location hash (including the leading `#`) from a route. */
export function buildForecastHash(route: ForecastRoute): string {
  const params = new URLSearchParams();
  params.set("lat", route.location.latitude.toFixed(6));
  params.set("lon", route.location.longitude.toFixed(6));
  params.set("model", FORECAST_MODELS[route.model].apiName);
  params.set("day", String(route.day));
  params.set("hour", String(route.hour));
  params.set("view", route.view);
  const name = route.location.name?.trim();
  if (name !== undefined && name.length > 0) {
    params.set("name", name);
  }
  return `${HASH_PREFIX}?${params.toString()}`;
}
