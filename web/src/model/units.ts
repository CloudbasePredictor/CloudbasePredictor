/**
 * Display-unit presets and value formatting.
 *
 * Ported 1:1 from `data/units/DisplayUnits.kt`. Kotlin `Float` values become
 * JavaScript `number`; formatting mirrors `String.format(Locale.US, ...)` with
 * `toFixed` and `Math.round` (both round half away from / toward +infinity in
 * the same way the Kotlin code relies on).
 */

export type UnitPreset = "METRIC_KMH" | "METRIC_MPS" | "IMPERIAL" | "AVIATION";

export type WindSpeedUnit = "KMH" | "MPS" | "MPH" | "KT";
export type AltitudeUnit = "METERS" | "FEET";
export type VerticalSpeedUnit = "MPS" | "FPM";

export const WIND_SPEED_UNIT_LABEL: Record<WindSpeedUnit, string> = {
  KMH: "km/h",
  MPS: "m/s",
  MPH: "mph",
  KT: "kt",
};

export const ALTITUDE_UNIT_SHORT_LABEL: Record<AltitudeUnit, string> = {
  METERS: "m",
  FEET: "ft",
};

export const VERTICAL_SPEED_UNIT_LABEL: Record<VerticalSpeedUnit, string> = {
  MPS: "m/s",
  FPM: "ft/min",
};

export interface DisplayUnits {
  readonly windSpeed: WindSpeedUnit;
  readonly altitude: AltitudeUnit;
  readonly verticalSpeed: VerticalSpeedUnit;
}

const FEET_PER_METER = 3.28084;
const FEET_PER_KILOMETER = 3280.84;
const SECONDS_PER_MINUTE = 60;
const MILES_PER_KILOMETER = 0.621371;
const KNOTS_PER_KMH = 0.539957;

const metricKmhUnits: DisplayUnits = {
  windSpeed: "KMH",
  altitude: "METERS",
  verticalSpeed: "MPS",
};

const metricMpsUnits: DisplayUnits = {
  windSpeed: "MPS",
  altitude: "METERS",
  verticalSpeed: "MPS",
};

const imperialUnits: DisplayUnits = {
  windSpeed: "MPH",
  altitude: "FEET",
  verticalSpeed: "FPM",
};

const aviationUnits: DisplayUnits = {
  windSpeed: "KT",
  altitude: "FEET",
  verticalSpeed: "FPM",
};

export function resolveDisplayUnits(preset: UnitPreset): DisplayUnits {
  switch (preset) {
    case "METRIC_KMH":
      return metricKmhUnits;
    case "METRIC_MPS":
      return metricMpsUnits;
    case "IMPERIAL":
      return imperialUnits;
    case "AVIATION":
      return aviationUnits;
  }
}

export function convertWindSpeedKmh(speedKmh: number, unit: WindSpeedUnit): number {
  switch (unit) {
    case "KMH":
      return speedKmh;
    case "MPS":
      return speedKmh / 3.6;
    case "MPH":
      return speedKmh * MILES_PER_KILOMETER;
    case "KT":
      return speedKmh * KNOTS_PER_KMH;
  }
}

export function formatWindSpeed(speedKmh: number, units: DisplayUnits, withUnit = true): string {
  const converted = convertWindSpeedKmh(speedKmh, units.windSpeed);
  let value: string;
  switch (units.windSpeed) {
    case "KMH":
    case "MPH":
    case "KT":
      value = String(Math.round(converted));
      break;
    case "MPS":
      value = converted.toFixed(1);
      break;
  }
  return withUnit ? `${value} ${WIND_SPEED_UNIT_LABEL[units.windSpeed]}` : value;
}

export function formatAltitudeKm(
  altitudeKm: number,
  units: DisplayUnits,
  compact = false,
  withUnit = true,
): string {
  switch (units.altitude) {
    case "METERS": {
      const value = altitudeKm >= 1 ? altitudeKm.toFixed(1) : String(Math.round(altitudeKm * 1000));
      if (!withUnit) return value;
      if (altitudeKm >= 1) {
        return compact ? `${value}km` : `${value} km`;
      }
      return compact ? `${value}m` : `${value} m`;
    }
    case "FEET": {
      const feet = altitudeKm * FEET_PER_KILOMETER;
      const useKft = compact || feet >= 10_000;
      const value = useKft ? (feet / 1000).toFixed(1) : String(Math.round(feet));
      if (!withUnit) return value;
      if (useKft) {
        return compact ? `${value}kft` : `${value} kft`;
      }
      return compact ? `${value}ft` : `${value} ft`;
    }
  }
}

export function formatAltitudeMeters(
  altitudeMeters: number,
  units: DisplayUnits,
  compact = false,
  withUnit = true,
): string {
  return formatAltitudeKm(altitudeMeters / 1000, units, compact, withUnit);
}

export function formatAltitudeAxisValue(altitudeKm: number, units: DisplayUnits): string {
  switch (units.altitude) {
    case "METERS":
      return altitudeKm.toFixed(1);
    case "FEET":
      return ((altitudeKm * FEET_PER_KILOMETER) / 1000).toFixed(1);
  }
}

export function altitudeAxisUnitLabel(units: DisplayUnits): string {
  switch (units.altitude) {
    case "METERS":
      return "km";
    case "FEET":
      return "kft";
  }
}

export function formatVerticalSpeed(
  speedMps: number,
  units: DisplayUnits,
  withUnit = true,
): string {
  let value: string;
  switch (units.verticalSpeed) {
    case "MPS":
      value = speedMps.toFixed(1);
      break;
    case "FPM":
      value = String(Math.round(speedMps * FEET_PER_METER * SECONDS_PER_MINUTE));
      break;
  }
  return withUnit ? `${value} ${VERTICAL_SPEED_UNIT_LABEL[units.verticalSpeed]}` : value;
}

export function formatVerticalSpeedRange(
  lowMps: number,
  highMps: number,
  units: DisplayUnits,
  withUnit = true,
): string {
  const low = formatVerticalSpeed(lowMps, units, false);
  const high = formatVerticalSpeed(highMps, units, false);
  const value = Math.abs(highMps - lowMps) < 0.05 ? high : `${low}-${high}`;
  return withUnit ? `${value} ${VERTICAL_SPEED_UNIT_LABEL[units.verticalSpeed]}` : value;
}
