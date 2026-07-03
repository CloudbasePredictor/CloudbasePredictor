import { describe, expect, it } from "vitest";
import {
  convertWindSpeedKmh,
  formatAltitudeKm,
  formatVerticalSpeedRange,
  formatWindSpeed,
  resolveDisplayUnits,
} from "./units";

const metricKmh = resolveDisplayUnits("METRIC_KMH");
const aviation = resolveDisplayUnits("AVIATION");

describe("units", () => {
  it("formats wind speed per preset", () => {
    expect(formatWindSpeed(20, metricKmh)).toBe("20 km/h");
    // 20 km/h -> ~10.8 kt, rounded.
    expect(formatWindSpeed(20, aviation)).toBe("11 kt");
  });

  it("converts wind speed between units", () => {
    expect(convertWindSpeedKmh(36, "MPS")).toBeCloseTo(10, 9);
    expect(convertWindSpeedKmh(100, "KMH")).toBe(100);
  });

  it("formats altitude in metres and kilometres", () => {
    expect(formatAltitudeKm(0.5, metricKmh)).toBe("500 m");
    expect(formatAltitudeKm(2.5, metricKmh)).toBe("2.5 km");
  });

  it("formats altitude in feet for aviation preset", () => {
    // 2.5 km -> 8202 ft, below the 10k kft threshold.
    expect(formatAltitudeKm(2.5, aviation)).toBe("8202 ft");
  });

  it("collapses vertical speed range when endpoints are close", () => {
    expect(formatVerticalSpeedRange(1.0, 1.02, metricKmh)).toBe("1.0 m/s");
    expect(formatVerticalSpeedRange(1.0, 3.0, metricKmh)).toBe("1.0-3.0 m/s");
  });
});
