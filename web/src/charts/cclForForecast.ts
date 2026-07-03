/**
 * Per-hour CCL analysis helper shared by the Stüve and Wind chart builders.
 *
 * Port of `HourlyPoint.analyzeCclForForecast` from
 * `ui/screens/forecast/ForecastChartBuilders.kt`. Float conversions mirror the
 * Kotlin `.toFloat()` narrowing.
 */

import type { HourlyPoint } from "../api/types";
import {
  analyzeCclHourly,
  type CclHourlyResult,
  type CclPressureLevel,
} from "../engine/cclAnalysis";
import { f } from "../engine/float32";
import { estimateSurfacePressure } from "../engine/parcelAnalysis";

export function analyzeCclForForecast(hp: HourlyPoint, elevationM: number): CclHourlyResult[] {
  if (hp.temperature2mC === null) return [];
  if (hp.dewPoint2mC === null) return [];
  const surfaceTemperatureC = f(hp.temperature2mC);
  const surfaceDewPointC = f(hp.dewPoint2mC);
  const surfacePressureHpa =
    hp.surfacePressureHpa !== null ? f(hp.surfacePressureHpa) : estimateSurfacePressure(elevationM);

  return analyzeCclHourly({
    time: `${hp.date}T${String(hp.hour).padStart(2, "0")}:00`,
    surfaceTemperatureC,
    surfaceDewPointC,
    surfacePressureHpa,
    surfaceElevationM: elevationM,
    pressureLevels: hp.pressureLevels.map(
      (level): CclPressureLevel => ({
        pressureHpa: f(level.pressureHpa),
        temperatureC: f(level.temperatureC),
        dewPointC: level.dewPointC !== null ? f(level.dewPointC) : null,
        heightMslM: level.geopotentialHeightM !== null ? f(level.geopotentialHeightM) : null,
        isSynthetic: level.isSynthetic,
      }),
    ),
    takeoffElevationM: null,
  });
}
