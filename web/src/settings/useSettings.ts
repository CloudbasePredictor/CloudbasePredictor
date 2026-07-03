/**
 * React bindings for {@link file://./settingsStore.ts}.
 *
 * `useSettings` exposes the persisted settings snapshot; `useResolvedTheme`
 * combines the theme mode with the live `prefers-color-scheme` and applies the
 * result to `document.documentElement` (`data-theme`) so the CSS variables and
 * the Canvas palette stay in sync. Kept out of the store module so the store
 * stays framework-free and node-testable.
 */

import { useEffect, useSyncExternalStore } from "react";
import { resolveThemeColors, type ThemeColors } from "../charts/theme";
import {
  getSettings,
  prefersColorSchemeDark,
  resolveIsDark,
  type Settings,
  subscribeSettings,
  subscribeSystemColorScheme,
} from "./settingsStore";

export function useSettings(): Settings {
  return useSyncExternalStore(subscribeSettings, getSettings, getSettings);
}

function useSystemDark(): boolean {
  return useSyncExternalStore(subscribeSystemColorScheme, prefersColorSchemeDark, () => false);
}

export interface ResolvedTheme {
  isDark: boolean;
  colors: ThemeColors;
}

/** Resolve the effective theme, apply it to `<html>`, and return the palette. */
export function useResolvedTheme(): ResolvedTheme {
  const settings = useSettings();
  const systemDark = useSystemDark();
  const isDark = resolveIsDark(settings.themeMode, systemDark);

  useEffect(() => {
    document.documentElement.dataset.theme = isDark ? "dark" : "light";
  }, [isDark]);

  return { isDark, colors: resolveThemeColors(isDark) };
}
