/**
 * Favorite places persisted in `localStorage`.
 *
 * Mirrors the Android favorites: a list of {@link SavedPlace} entries (id, name,
 * latitude, longitude, isFavorite) deduplicated by id, matching
 * `data/place/FavoritePlacesBackupStore.kt` semantics. The Android app keeps a
 * richer backup payload (unit preset, map layer, theme); the web version only
 * needs the favorite list here — settings land in a later phase.
 *
 * The module exposes a small external store (subscribe + snapshot) so React can
 * consume it via `useSyncExternalStore`, and reacts to `storage` events so
 * favoriting in one tab reflects in another.
 */

import { type SavedPlace, savedPlaceFromCoordinates } from "../model/savedPlace";

const STORAGE_KEY = "cbp.favorites.v1";
const SCHEMA_VERSION = 1;

interface FavoritesPayload {
  schemaVersion: number;
  places: SavedPlace[];
}

type Listener = () => void;

const listeners = new Set<Listener>();
let cache: SavedPlace[] | null = null;

function isLocalStorageAvailable(): boolean {
  try {
    return typeof window !== "undefined" && window.localStorage !== null;
  } catch {
    return false;
  }
}

function sanitizePlace(raw: unknown): SavedPlace | null {
  if (typeof raw !== "object" || raw === null) return null;
  const candidate = raw as Record<string, unknown>;
  const latitude = candidate.latitude;
  const longitude = candidate.longitude;
  if (typeof latitude !== "number" || !Number.isFinite(latitude)) return null;
  if (typeof longitude !== "number" || !Number.isFinite(longitude)) return null;
  if (latitude < -90 || latitude > 90 || longitude < -180 || longitude > 180) return null;

  const base = savedPlaceFromCoordinates(latitude, longitude);
  const id = typeof candidate.id === "string" && candidate.id.length > 0 ? candidate.id : base.id;
  const name =
    typeof candidate.name === "string" && candidate.name.trim().length > 0
      ? candidate.name.trim()
      : base.name;
  return { id, name, latitude, longitude, isFavorite: true };
}

function readFromStorage(): SavedPlace[] {
  if (!isLocalStorageAvailable()) return [];
  let serialized: string | null;
  try {
    serialized = window.localStorage.getItem(STORAGE_KEY);
  } catch {
    return [];
  }
  if (serialized === null) return [];

  try {
    const parsed = JSON.parse(serialized) as Partial<FavoritesPayload> | SavedPlace[];
    const rawPlaces = Array.isArray(parsed) ? parsed : (parsed.places ?? []);
    const seen = new Set<string>();
    const places: SavedPlace[] = [];
    for (const entry of rawPlaces) {
      const place = sanitizePlace(entry);
      if (place !== null && !seen.has(place.id)) {
        seen.add(place.id);
        places.push(place);
      }
    }
    return places;
  } catch {
    return [];
  }
}

function writeToStorage(places: SavedPlace[]): void {
  if (!isLocalStorageAvailable()) return;
  const payload: FavoritesPayload = { schemaVersion: SCHEMA_VERSION, places };
  try {
    window.localStorage.setItem(STORAGE_KEY, JSON.stringify(payload));
  } catch {
    // Ignore quota / private-mode write failures; the in-memory cache still updates.
  }
}

function notify(): void {
  for (const listener of listeners) listener();
}

/** Current favorites snapshot. Referentially stable until the list changes. */
export function getFavorites(): SavedPlace[] {
  if (cache === null) {
    cache = readFromStorage();
  }
  return cache;
}

/** Subscribe to favorite-list changes (in-tab mutations and cross-tab `storage` events). */
export function subscribeFavorites(listener: Listener): () => void {
  listeners.add(listener);
  return () => {
    listeners.delete(listener);
  };
}

function setFavorites(places: SavedPlace[]): void {
  cache = places;
  writeToStorage(places);
  notify();
}

export function isFavorite(id: string): boolean {
  return getFavorites().some((place) => place.id === id);
}

/** Add or replace a favorite (keyed by id); most-recent additions go first. */
export function addFavorite(place: SavedPlace): void {
  const favorite: SavedPlace = { ...place, isFavorite: true };
  const rest = getFavorites().filter((existing) => existing.id !== favorite.id);
  setFavorites([favorite, ...rest]);
}

export function removeFavorite(id: string): void {
  const current = getFavorites();
  const next = current.filter((place) => place.id !== id);
  if (next.length !== current.length) {
    setFavorites(next);
  }
}

/** Toggle a place's favorite state; returns the resulting membership. */
export function toggleFavorite(place: SavedPlace): boolean {
  if (isFavorite(place.id)) {
    removeFavorite(place.id);
    return false;
  }
  addFavorite(place);
  return true;
}

// Keep the in-memory cache consistent when another tab edits the same key.
if (isLocalStorageAvailable()) {
  window.addEventListener("storage", (event) => {
    if (event.key === STORAGE_KEY || event.key === null) {
      cache = readFromStorage();
      notify();
    }
  });
}

/** Test-only: reset the in-memory cache so a fresh `localStorage` read happens. */
export function resetFavoritesCacheForTests(): void {
  cache = null;
}
