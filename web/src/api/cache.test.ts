import { describe, expect, it } from "vitest";
import { ForecastCache, type ForecastCacheKey } from "./cache";
import type { HourlyForecastData } from "./types";

const KEY: ForecastCacheKey = { latitude: 47.68, longitude: 11.63, model: "ICON_SEAMLESS" };

function makeData(firstDate: string): HourlyForecastData {
  return {
    latitude: 47.68,
    longitude: 11.63,
    elevation: 1500,
    hourlyPoints: [],
    dailyForecasts: [
      { date: firstDate, maxTemperatureCelsius: 12, minTemperatureCelsius: 5, weatherCode: 3 },
    ],
    utcOffsetSeconds: 7200,
    timezone: "Europe/Berlin",
  };
}

// 2026-04-18T12:00Z; with +7200s offset the local date is still 2026-04-18.
const NOW = Date.parse("2026-04-18T12:00:00Z");

describe("ForecastCache", () => {
  it("returns fresh data within the model update interval", () => {
    const cache = new ForecastCache(() => NOW);
    cache.put(KEY, makeData("2026-04-18"));
    expect(cache.getFresh(KEY)).not.toBeNull();
  });

  it("expires data older than the model update interval", () => {
    let clock = NOW;
    const cache = new ForecastCache(() => clock);
    cache.put(KEY, makeData("2026-04-18"));
    // ICON_SEAMLESS updates every 3 hours.
    clock = NOW + 3 * 3_600_000 + 1;
    expect(cache.getFresh(KEY)).toBeNull();
    // Still retrievable as a stale peek.
    expect(cache.peek(KEY)).not.toBeNull();
  });

  it("expires data whose first forecast date is before today", () => {
    const cache = new ForecastCache(() => NOW);
    cache.put(KEY, makeData("2026-04-17"));
    expect(cache.getFresh(KEY)).toBeNull();
  });

  it("misses on unknown keys", () => {
    const cache = new ForecastCache(() => NOW);
    expect(cache.getFresh(KEY)).toBeNull();
    expect(cache.peek(KEY)).toBeNull();
  });
});
