/**
 * Chart color palette extracted from the Android Material 3 theme.
 *
 * Values come 1:1 from `ui/theme/Color.kt` and `ui/theme/Theme.kt`
 * (`lightColorScheme` and `darkColorScheme`). The four Canvas views take the
 * active {@link ThemeColors} as a parameter so light/dark render identically to
 * the Android app.
 *
 * Colors are stored as `[r, g, b]` (0-255) so they can be blended and rendered
 * to `rgba(...)` strings for Canvas 2D.
 */

export type Rgb = readonly [number, number, number];

function hex(value: string): Rgb {
  const normalized = value.replace("#", "");
  const r = Number.parseInt(normalized.slice(0, 2), 16);
  const g = Number.parseInt(normalized.slice(2, 4), 16);
  const b = Number.parseInt(normalized.slice(4, 6), 16);
  return [r, g, b];
}

// --- Light Material 3 scheme (from Theme.kt lightColorScheme) ---
export const lightTheme = {
  primary: hex("#0F4C81"), // DeepBlue
  onPrimary: hex("#FBFDFF"), // ColorWhite
  primaryContainer: hex("#D8E9FA"),
  onPrimaryContainer: hex("#071F34"),
  secondary: hex("#57A6F6"), // SkyBlue
  secondaryContainer: hex("#D6EAFB"),
  onSecondaryContainer: hex("#092235"),
  tertiary: hex("#F2B14C"), // SunAmber
  surface: hex("#FBFDFF"), // ColorWhite
  onSurface: hex("#10202E"),
  onSurfaceVariant: hex("#4A5B66"),
  surfaceContainer: hex("#DCE8EF"), // MintGray
  surfaceContainerHigh: hex("#F1F6FA"),
  background: hex("#FBFDFF"),
  outline: hex("#6E7F8A"),
  outlineVariant: hex("#C4D1D9"),
} as const;

export type ThemeColors = typeof lightTheme;

// --- Dark Material 3 scheme (from Theme.kt darkColorScheme) ---
export const darkTheme: ThemeColors = {
  primary: hex("#9FC9F3"), // IceBlue
  onPrimary: hex("#0A2236"), // NightBlue
  primaryContainer: hex("#1E4E73"),
  onPrimaryContainer: hex("#D4EAFF"),
  secondary: hex("#57A6F6"), // SkyBlue
  secondaryContainer: hex("#264B68"),
  onSecondaryContainer: hex("#D8ECFF"),
  tertiary: hex("#E9D4B6"), // Sand
  surface: hex("#0A2236"), // NightBlue
  onSurface: hex("#EAF3FA"),
  onSurfaceVariant: hex("#B6C7D4"),
  surfaceContainer: hex("#12344D"),
  surfaceContainerHigh: hex("#18425F"),
  background: hex("#0A2236"),
  outline: hex("#8294A2"),
  outlineVariant: hex("#31495C"),
};

/** Resolve the active canvas palette from the effective dark/light state. */
export function resolveThemeColors(isDark: boolean): ThemeColors {
  return isDark ? darkTheme : lightTheme;
}

/**
 * Simple sRGB channel interpolation.
 *
 * NOTE (simplification): Jetpack Compose's `Color.lerp` interpolates in the
 * Oklab space. For the small tints used here (grid backgrounds, adjacent color
 * stops) the perceptual difference is negligible, so we blend directly in sRGB.
 */
export function mixRgb(start: Rgb, stop: Rgb, fraction: number): Rgb {
  const t = Math.min(1, Math.max(0, fraction));
  return [
    start[0] + (stop[0] - start[0]) * t,
    start[1] + (stop[1] - start[1]) * t,
    start[2] + (stop[2] - start[2]) * t,
  ];
}

/** Render an `[r,g,b]` color to a CSS `rgb(...)`/`rgba(...)` string. */
export function rgba(color: Rgb, alpha = 1): string {
  const r = Math.round(color[0]);
  const g = Math.round(color[1]);
  const b = Math.round(color[2]);
  if (alpha >= 1) return `rgb(${r}, ${g}, ${b})`;
  return `rgba(${r}, ${g}, ${b}, ${alpha})`;
}

/** Grid background tint used by the chart plot areas. */
export function gridBackground(theme: ThemeColors, fraction: number): Rgb {
  return mixRgb(theme.surface, theme.onSurface, fraction);
}
