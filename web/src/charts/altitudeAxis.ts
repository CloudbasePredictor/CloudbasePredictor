/**
 * Shared altitude-axis helpers used by the Thermic and Wind chart views.
 *
 * Ports the `altitudeToY` / `yToAltitude` / `buildAltitudeTicks` helpers that are
 * duplicated across `ThermicForecastView.kt` and `WindForecastView.kt`. These are
 * pure screen geometry (plain doubles).
 */

export function altitudeToY(
  altitudeKm: number,
  minAltitudeKm: number,
  maxAltitudeKm: number,
  plotTop: number,
  plotBottom: number,
): number {
  const normalized = (altitudeKm - minAltitudeKm) / (maxAltitudeKm - minAltitudeKm);
  return plotBottom - normalized * (plotBottom - plotTop);
}

export function yToAltitude(
  y: number,
  minAltitudeKm: number,
  maxAltitudeKm: number,
  plotTop: number,
  plotBottom: number,
): number {
  const normalized = (plotBottom - y) / (plotBottom - plotTop);
  return minAltitudeKm + normalized * (maxAltitudeKm - minAltitudeKm);
}

export function buildAltitudeTicks(
  minAltitudeKm: number,
  maxAltitudeKm: number,
  stepKm: number,
): number[] {
  const ticks = [minAltitudeKm];
  let nextTick = Math.ceil(minAltitudeKm / stepKm) * stepKm;
  while (nextTick < maxAltitudeKm) {
    if (nextTick > minAltitudeKm + 0.001) ticks.push(nextTick);
    nextTick += stepKm;
  }
  if (maxAltitudeKm - ticks[ticks.length - 1] > 0.001) ticks.push(maxAltitudeKm);

  const seen = new Set<number>();
  const result: number[] = [];
  for (const tick of ticks) {
    const key = Math.trunc(tick * 100);
    if (!seen.has(key)) {
      seen.add(key);
      result.push(tick);
    }
  }
  return result;
}
