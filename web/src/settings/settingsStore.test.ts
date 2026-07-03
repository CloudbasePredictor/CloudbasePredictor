import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { resolveDisplayUnits } from "../model/units";
import {
  DEFAULT_SETTINGS,
  getSettings,
  resetSettingsCacheForTests,
  resolveIsDark,
  setThemeMode,
  setUnitPreset,
} from "./settingsStore";

function createMemoryStorage(): Storage {
  const map = new Map<string, string>();
  return {
    get length() {
      return map.size;
    },
    clear: () => map.clear(),
    getItem: (key: string) => (map.has(key) ? (map.get(key) as string) : null),
    key: (index: number) => Array.from(map.keys())[index] ?? null,
    removeItem: (key: string) => {
      map.delete(key);
    },
    setItem: (key: string, value: string) => {
      map.set(key, String(value));
    },
  };
}

beforeEach(() => {
  vi.stubGlobal("window", {
    localStorage: createMemoryStorage(),
    addEventListener: () => {},
  });
  resetSettingsCacheForTests();
});

afterEach(() => {
  vi.unstubAllGlobals();
  resetSettingsCacheForTests();
});

describe("settingsStore", () => {
  it("defaults to metric km/h and system theme", () => {
    expect(getSettings()).toEqual(DEFAULT_SETTINGS);
    expect(getSettings()).toEqual({ unitPreset: "METRIC_KMH", themeMode: "system" });
  });

  it("persists the unit preset and reads it back after a cache reset", () => {
    setUnitPreset("AVIATION");
    expect(getSettings().unitPreset).toBe("AVIATION");
    resetSettingsCacheForTests();
    expect(getSettings().unitPreset).toBe("AVIATION");
  });

  it("persists the theme mode independently of the unit preset", () => {
    setUnitPreset("IMPERIAL");
    setThemeMode("dark");
    resetSettingsCacheForTests();
    expect(getSettings()).toEqual({ unitPreset: "IMPERIAL", themeMode: "dark" });
  });

  it("threads the persisted preset through resolveDisplayUnits", () => {
    setUnitPreset("AVIATION");
    const units = resolveDisplayUnits(getSettings().unitPreset);
    expect(units).toEqual({ windSpeed: "KT", altitude: "FEET", verticalSpeed: "FPM" });
  });

  it("falls back to defaults for malformed stored values", () => {
    window.localStorage.setItem(
      "cbp.settings.v1",
      JSON.stringify({ schemaVersion: 1, settings: { unitPreset: "BOGUS", themeMode: 42 } }),
    );
    resetSettingsCacheForTests();
    expect(getSettings()).toEqual(DEFAULT_SETTINGS);
  });

  it("resolves the effective dark state from mode and system preference", () => {
    expect(resolveIsDark("dark", false)).toBe(true);
    expect(resolveIsDark("light", true)).toBe(false);
    expect(resolveIsDark("system", true)).toBe(true);
    expect(resolveIsDark("system", false)).toBe(false);
  });
});
