/**
 * A place the user saved (favorite or recent).
 *
 * Ported 1:1 from `model/SavedPlace.kt`.
 */

export interface SavedPlace {
  readonly id: string;
  readonly name: string;
  readonly latitude: number;
  readonly longitude: number;
  readonly isFavorite: boolean;
}

const EARTH_RADIUS_M = 6_371_000.0;

function toRadians(degrees: number): number {
  return (degrees * Math.PI) / 180.0;
}

/**
 * Returns true if the given coordinates are within ~200 m of this place.
 * Uses a fast equi-rectangular approximation suitable for short distances.
 */
export function isNearby(
  place: SavedPlace,
  lat: number,
  lon: number,
  thresholdMeters = 200.0,
): boolean {
  const dLat = toRadians(place.latitude - lat);
  const dLon = toRadians(place.longitude - lon) * Math.cos(toRadians((place.latitude + lat) / 2.0));
  const distMeters = Math.sqrt(dLat * dLat + dLon * dLon) * EARTH_RADIUS_M;
  return distMeters <= thresholdMeters;
}

export function savedPlaceFromCoordinates(latitude: number, longitude: number): SavedPlace {
  const normalizedLatitude = latitude.toFixed(4);
  const normalizedLongitude = longitude.toFixed(4);
  const displayName = `${normalizedLatitude}, ${normalizedLongitude}`;

  return {
    id: `place:${normalizedLatitude}:${normalizedLongitude}`,
    name: displayName,
    latitude,
    longitude,
    isFavorite: false,
  };
}
