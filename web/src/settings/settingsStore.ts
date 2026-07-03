/**
 * User settings persisted in `localStorage`: display-unit preset and theme mode.
 *
 * Mirrors the Android settings the web version needs — the unit preset from
 * `data/units/UnitSettingsRepository.kt` (default `METRIC_KMH`) and a theme
 * choice that maps onto `CloudbasePredictorTheme(darkTheme = ...)`. The Android
 * app always follows the system dark mode; the web adds an explicit
 * `system | light | dark` toggle on top of `prefers-color-scheme`.
 *
 * Built on the same small external-store shape as {@link file://../favorites/favoritesStore.ts}
 * (subscribe + snapshot) so React can read it via `useSyncExternalStore`, with a
 * `storage` listener so a change in one tab reflects in another.
 */

import type { UnitPreset } from "../model/units";

export type ThemeMode = "system" | "light" | "dark";

export interface Settings {
  readonly unitPreset: UnitPreset;
  readonly themeMode: ThemeMode;
}

const STORAGE_KEY = "cbp.settings.v1";
const SCHEMA_VERSION = 1;

export const DEFAULT_SETTINGS: Settings = {
  unitPreset: "METRIC_KMH",
  themeMode: "system",
};

const UNIT_PRESETS: readonly UnitPreset[] = ["METRIC_KMH", "METRIC_MPS", "IMPERIAL", "AVIATION"];
const THEME_MODES: readonly ThemeMode[] = ["system", "light", "dark"];

interface SettingsPayload {
  schemaVersion: number;
  settings: Settings;
}

type Listener = () => void;

const listeners = new Set<Listener>();
let cache: Settings | null = null;

function isLocalStorageAvailable(): boolean {
  try {
    return typeof window !== "undefined" && window.localStorage !== null;
  } catch {
    return false;
  }
}

function sanitize(raw: unknown): Settings {
  if (typeof raw !== "object" || raw === null) return DEFAULT_SETTINGS;
  const candidate = raw as Record<string, unknown>;
  const unitPreset =
    typeof candidate.unitPreset === "string" &&
    (UNIT_PRESETS as readonly string[]).includes(candidate.unitPreset)
      ? (candidate.unitPreset as UnitPreset)
      : DEFAULT_SETTINGS.unitPreset;
  const themeMode =
    typeof candidate.themeMode === "string" &&
    (THEME_MODES as readonly string[]).includes(candidate.themeMode)
      ? (candidate.themeMode as ThemeMode)
      : DEFAULT_SETTINGS.themeMode;
  return { unitPreset, themeMode };
}

function readFromStorage(): Settings {
  if (!isLocalStorageAvailable()) return DEFAULT_SETTINGS;
  let serialized: string | null;
  try {
    serialized = window.localStorage.getItem(STORAGE_KEY);
  } catch {
    return DEFAULT_SETTINGS;
  }
  if (serialized === null) return DEFAULT_SETTINGS;
  try {
    const parsed = JSON.parse(serialized) as Partial<SettingsPayload> | Settings;
    const rawSettings =
      typeof parsed === "object" && parsed !== null && "settings" in parsed
        ? (parsed as SettingsPayload).settings
        : parsed;
    return sanitize(rawSettings);
  } catch {
    return DEFAULT_SETTINGS;
  }
}

function writeToStorage(settings: Settings): void {
  if (!isLocalStorageAvailable()) return;
  const payload: SettingsPayload = { schemaVersion: SCHEMA_VERSION, settings };
  try {
    window.localStorage.setItem(STORAGE_KEY, JSON.stringify(payload));
  } catch {
    // Ignore quota / private-mode write failures; the in-memory cache still updates.
  }
}

function notify(): void {
  for (const listener of listeners) listener();
}

/** Current settings snapshot. Referentially stable until a value changes. */
export function getSettings(): Settings {
  if (cache === null) {
    cache = readFromStorage();
  }
  return cache;
}

/** Subscribe to settings changes (in-tab mutations and cross-tab `storage` events). */
export function subscribeSettings(listener: Listener): () => void {
  listeners.add(listener);
  return () => {
    listeners.delete(listener);
  };
}

function update(partial: Partial<Settings>): void {
  const next: Settings = { ...getSettings(), ...partial };
  cache = next;
  writeToStorage(next);
  notify();
}

export function setUnitPreset(unitPreset: UnitPreset): void {
  if (getSettings().unitPreset === unitPreset) return;
  update({ unitPreset });
}

export function setThemeMode(themeMode: ThemeMode): void {
  if (getSettings().themeMode === themeMode) return;
  update({ themeMode });
}

/** Whether the OS currently prefers a dark color scheme (guarded for SSR/tests). */
export function prefersColorSchemeDark(): boolean {
  try {
    return (
      typeof window !== "undefined" &&
      typeof window.matchMedia === "function" &&
      window.matchMedia("(prefers-color-scheme: dark)").matches
    );
  } catch {
    return false;
  }
}

/** Resolve the effective dark state from a theme mode and the OS preference. */
export function resolveIsDark(mode: ThemeMode, systemDark: boolean): boolean {
  if (mode === "dark") return true;
  if (mode === "light") return false;
  return systemDark;
}

/** Subscribe to OS `prefers-color-scheme` changes (guarded; no-op when unsupported). */
export function subscribeSystemColorScheme(listener: Listener): () => void {
  try {
    if (typeof window === "undefined" || typeof window.matchMedia !== "function") {
      return () => {};
    }
    const query = window.matchMedia("(prefers-color-scheme: dark)");
    query.addEventListener("change", listener);
    return () => query.removeEventListener("change", listener);
  } catch {
    return () => {};
  }
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
export function resetSettingsCacheForTests(): void {
  cache = null;
}
