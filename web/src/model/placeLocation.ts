/**
 * A geographic point, optionally named.
 *
 * Ported 1:1 from `model/PlaceLocation.kt`.
 */

import { type SavedPlace, savedPlaceFromCoordinates } from "./savedPlace";

export interface PlaceLocation {
  readonly latitude: number;
  readonly longitude: number;
  readonly name?: string;
}

export function toRouteValue(location: PlaceLocation): string {
  return `${location.latitude.toFixed(6)},${location.longitude.toFixed(6)}`;
}

export function toSavedPlace(location: PlaceLocation): SavedPlace {
  const coordinatePlace = savedPlaceFromCoordinates(location.latitude, location.longitude);
  const routeName = location.name?.trim();
  if (routeName !== undefined && routeName.length > 0) {
    return { ...coordinatePlace, name: routeName };
  }
  return coordinatePlace;
}

export function placeLocationFromSavedPlace(place: SavedPlace): PlaceLocation {
  return { latitude: place.latitude, longitude: place.longitude };
}

export function placeLocationFromRouteValue(value: string): PlaceLocation | null {
  const parts = value.split(",");
  if (parts.length !== 2) return null;

  const latitude = parseCoordinate(parts[0]);
  const longitude = parseCoordinate(parts[1]);
  if (latitude === null || longitude === null) return null;
  if (!Number.isFinite(latitude) || latitude < -90.0 || latitude > 90.0) return null;
  if (!Number.isFinite(longitude) || longitude < -180.0 || longitude > 180.0) return null;

  return { latitude, longitude };
}

/**
 * Mirrors Kotlin `String.toDoubleOrNull()`: returns null for blank or
 * non-numeric input instead of JavaScript's `NaN`.
 */
function parseCoordinate(raw: string | undefined): number | null {
  if (raw === undefined) return null;
  const trimmed = raw.trim();
  if (trimmed.length === 0) return null;
  const value = Number(trimmed);
  return Number.isNaN(value) ? null : value;
}
