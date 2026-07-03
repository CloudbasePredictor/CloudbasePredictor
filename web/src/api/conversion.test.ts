import { describe, expect, it } from "vitest";
import brauneckFixture from "./__fixtures__/brauneck_icon_seamless_20260418.json";
import { dailyToDomainModels, pointsByDate, toHourlyForecastData } from "./conversion";
import type { OpenMeteoHourlyForecastResponse, PressureLevelPoint } from "./types";

const response = brauneckFixture as unknown as OpenMeteoHourlyForecastResponse;
const data = toHourlyForecastData(response);

// The reference model returns pressure levels 1000..500 hPa (14 real levels);
// 450, 400, 350, 300, 250 hPa are synthesized by the conversion.
const REAL_LEVELS = [1000, 975, 950, 925, 900, 875, 850, 800, 750, 700, 650, 600, 550, 500];
const SYNTHETIC_LEVELS = [450, 400, 350, 300, 250];

function levelAt(levels: PressureLevelPoint[], pressureHpa: number): PressureLevelPoint {
  const level = levels.find((entry) => entry.pressureHpa === pressureHpa);
  if (level === undefined) throw new Error(`Missing pressure level ${pressureHpa}`);
  return level;
}

describe("toHourlyForecastData (Brauneck fixture)", () => {
  it("preserves response metadata", () => {
    expect(data.latitude).toBe(47.66);
    expect(data.longitude).toBe(11.5);
    expect(data.elevation).toBe(1523.0);
    expect(data.utcOffsetSeconds).toBe(7200);
    expect(data.timezone).toBe("Europe/Berlin");
  });

  it("produces one hourly point per time step", () => {
    expect(data.hourlyPoints).toHaveLength(24);
  });

  it("keeps naive local timestamps without applying a browser time zone", () => {
    // "2026-04-18T14:00" must stay at date 2026-04-18, hour 14 regardless of the
    // machine's local zone. Routing through Date would shift these.
    const point = data.hourlyPoints[14];
    expect(point.date).toBe("2026-04-18");
    expect(point.hour).toBe(14);
    // Hours are sequential 0..23.
    expect(data.hourlyPoints.map((p) => p.hour)).toEqual(
      Array.from({ length: 24 }, (_, index) => index),
    );
    expect(data.hourlyPoints.every((p) => p.date === "2026-04-18")).toBe(true);
  });

  it("maps surface variables exactly", () => {
    const point = data.hourlyPoints[14];
    expect(point.temperature2mC).toBe(11.4);
    expect(point.dewPoint2mC).toBe(2.2);
    expect(point.windSpeed10mKmh).toBe(8.6);
    expect(point.windDirection10mDeg).toBe(2);
    expect(point.capeJKg).toBe(10.0);
    expect(point.freezingLevelHeightM).toBe(2530.0);
    expect(point.surfacePressureHpa).toBe(852.6);
    expect(point.shortwaveRadiationWm2).toBe(483.4);
    expect(point.precipitationMm).toBe(0.0);
    expect(point.sunshineDurationS).toBe(2900.4);
    expect(point.isDay).toBe(1);
  });

  it("leaves variables the model did not return as null", () => {
    const point = data.hourlyPoints[14];
    // The fixture has no lifted_index / convective_inhibition / boundary_layer_height.
    expect(point.liftedIndexC).toBeNull();
    expect(point.convectiveInhibitionJKg).toBeNull();
    expect(point.boundaryLayerHeightM).toBeNull();
  });

  it("keeps real pressure levels with exact values and completes missing ones", () => {
    const levels = data.hourlyPoints[14].pressureLevels;
    // 14 real + 5 synthetic = 19 levels, sorted by descending pressure.
    expect(levels).toHaveLength(19);
    expect(levels.map((l) => l.pressureHpa)).toEqual([...REAL_LEVELS, ...SYNTHETIC_LEVELS]);

    const surface = levelAt(levels, 1000);
    expect(surface.isSynthetic).toBe(false);
    expect(surface.temperatureC).toBe(19.5);
    expect(surface.dewPointC).toBe(9.7);
    expect(surface.windSpeedKmh).toBe(8.6);
    expect(surface.windDirectionDeg).toBe(2);
    expect(surface.geopotentialHeightM).toBe(170.0);

    const top = levelAt(levels, 500);
    expect(top.isSynthetic).toBe(false);
    expect(top.temperatureC).toBe(-18.9);
    expect(top.geopotentialHeightM).toBe(5696.0);
  });

  it("flags synthesized levels and extrapolates plausibly", () => {
    const levels = data.hourlyPoints[14].pressureLevels;
    for (const pressure of SYNTHETIC_LEVELS) {
      const level = levelAt(levels, pressure);
      expect(level.isSynthetic).toBe(true);
      expect(Number.isFinite(level.temperatureC)).toBe(true);
      expect(level.geopotentialHeightM === null || Number.isFinite(level.geopotentialHeightM)).toBe(
        true,
      );
    }
    // 450 hPa is higher than the 500 hPa top real level, so it must be colder.
    expect(levelAt(levels, 450).temperatureC).toBeLessThan(levelAt(levels, 500).temperatureC);
  });

  it("groups hourly points by date", () => {
    const grouped = pointsByDate(data);
    expect([...grouped.keys()]).toEqual(["2026-04-18"]);
    expect(grouped.get("2026-04-18")).toHaveLength(24);
  });

  it("converts the daily block", () => {
    expect(data.dailyForecasts).toHaveLength(1);
    expect(data.dailyForecasts[0]).toEqual({
      date: "2026-04-18",
      maxTemperatureCelsius: 12.5,
      minTemperatureCelsius: 5.5,
      weatherCode: 3,
    });
  });

  it("skips daily entries with missing values", () => {
    const converted = dailyToDomainModels({
      time: ["2026-04-18", "2026-04-19"],
      temperature_2m_max: [12.5, null],
      temperature_2m_min: [5.5, 6.0],
      weather_code: [3, 1],
    });
    expect(converted).toHaveLength(1);
    expect(converted[0].date).toBe("2026-04-18");
  });
});
