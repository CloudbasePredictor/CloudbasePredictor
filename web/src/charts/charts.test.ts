import { describe, expect, it } from "vitest";
import rawAsset from "../api/__fixtures__/brauneck_icon_seamless_20260418.json";
import { pointsByDate, toHourlyForecastData } from "../api/conversion";
import type { OpenMeteoHourlyForecastResponse } from "../api/types";
import goldenFixture from "../engine/__fixtures__/brauneck_icon_seamless.json";
import { buildCloudChartFromData } from "./cloudChart";
import { thermicStrengthColor, windSpeedColor } from "./colorScales";
import { buildStuveChartFromData } from "./stuve/builder";
import {
  altitudeKmToApproxPressureHpa,
  buildSkewTProjection,
  recommendedStuveTopAltitudeKm,
} from "./stuve/geometry";
import {
  buildParcelAscentPath,
  buildRenderableParcelPressures,
  pressureToApproxHeightMeters,
} from "./stuve/model";
import { windBarbSpeedParts } from "./stuve/primitives";
import { buildThermicChartFromData } from "./thermicChart";
import { MAX_TOP_ALTITUDE_KM, MIN_TOP_ALTITUDE_KM, zoomedTopAltitudeKm } from "./viewport";
import { buildWindChartFromData } from "./windChart";

const converted = toHourlyForecastData(rawAsset as unknown as OpenMeteoHourlyForecastResponse);
const dates = [...pointsByDate(converted).keys()].sort();

const golden = goldenFixture as unknown as {
  thermalForecasts: Array<{
    date: string;
    hour: number;
    result: { topNominalKm: number; updraftNominalMps: number };
  }>;
};

describe("Thermic chart builder", () => {
  const chart = buildThermicChartFromData(converted, 0);

  it("returns 17 daytime slots (06:00-22:00) for the Brauneck fixture", () => {
    expect(chart).not.toBeNull();
    expect(chart?.timeSlots).toEqual(Array.from({ length: 17 }, (_, i) => (6 + i) * 60));
  });

  it("keeps cell strengths within the display range", () => {
    for (const cell of chart?.cells ?? []) {
      expect(cell.strengthMps).toBeGreaterThanOrEqual(0.2);
      expect(cell.strengthMps).toBeLessThanOrEqual(10);
      expect(cell.endAltitudeKm).toBeGreaterThan(cell.startAltitudeKm);
    }
  });

  it("matches the golden engine top/updraft for day 0 daytime hours", () => {
    const day0 = golden.thermalForecasts.filter((e) => e.date === dates[0]);
    expect(day0.length).toBeGreaterThan(0);
    const bySlot = new Map((chart?.slotDiagnostics ?? []).map((d) => [d.startMinuteOfDayLocal, d]));
    for (const entry of day0) {
      const diag = bySlot.get(entry.hour * 60);
      expect(diag, `diagnostics for hour ${entry.hour}`).toBeDefined();
      expect(diag?.topNominalKm).toBeCloseTo(entry.result.topNominalKm, 6);
      expect(diag?.updraftNominalMps).toBeCloseTo(entry.result.updraftNominalMps, 6);
    }
  });
});

describe("Wind chart builder", () => {
  const chart = buildWindChartFromData(converted, 0, MAX_TOP_ALTITUDE_KM);

  it("produces daytime hours and a sorted altitude ladder", () => {
    expect(chart).not.toBeNull();
    expect(chart?.hours).toEqual(Array.from({ length: 17 }, (_, i) => 6 + i));
    const alts = chart?.altitudeBandsKm ?? [];
    for (let i = 1; i < alts.length; i++) expect(alts[i]).toBeGreaterThan(alts[i - 1]);
    expect((chart?.cells.length ?? 0) > 0).toBe(true);
  });
});

describe("Cloud chart builder", () => {
  const chart = buildCloudChartFromData(converted, 0);

  it("produces aligned per-hour rows", () => {
    expect(chart).not.toBeNull();
    expect(chart?.layers.length).toBe(chart?.hours.length);
    expect(chart?.precipitation.length).toBe(chart?.hours.length);
    expect(chart?.radiation.length).toBe(chart?.hours.length);
    expect(chart?.sunshine.length).toBe(chart?.hours.length);
    for (const layer of chart?.layers ?? []) {
      expect(layer.lowCloudPercent).toBeGreaterThanOrEqual(0);
      expect(layer.lowCloudPercent).toBeLessThanOrEqual(100);
    }
  });
});

describe("Stuve chart builder", () => {
  const chart = buildStuveChartFromData(converted, 0, 12);

  it("builds temperature/dewpoint/parcel profiles anchored at the surface", () => {
    expect(chart).not.toBeNull();
    expect((chart?.temperatureProfile.length ?? 0) > 1).toBe(true);
    expect((chart?.parcelAscentPath.length ?? 0) > 1).toBe(true);
    expect(chart?.surfacePressureHpa).toBeGreaterThan(0);
    expect(chart?.selectedHour).toBe(12);
    // Surface anchor is the highest pressure (lowest altitude) point.
    const surface = chart?.temperatureProfile[0];
    expect(surface?.pressureHpa).toBe(chart?.surfacePressureHpa);
  });
});

describe("Stuve geometry", () => {
  const chart = buildStuveChartFromData(converted, 0, 12);
  if (chart === null) throw new Error("expected chart");
  const projection = buildSkewTProjection(chart, 500, 1000, 40, 400, 16, 600);

  it("round-trips pressure <-> y", () => {
    for (const pressure of [1000, 850, 700, 500]) {
      const y = projection.pressureToY(pressure);
      expect(projection.yToPressure(y)).toBeCloseTo(pressure, 3);
    }
  });

  it("round-trips temperature <-> x at a fixed pressure", () => {
    for (const t of [-10, 0, 12, 25]) {
      const x = projection.temperatureToX(t, 850);
      expect(projection.xToTemperature(x, 850)).toBeCloseTo(t, 3);
    }
  });

  it("maps higher altitude to lower pressure monotonically", () => {
    expect(altitudeKmToApproxPressureHpa(0)).toBeGreaterThan(altitudeKmToApproxPressureHpa(3));
    expect(altitudeKmToApproxPressureHpa(3)).toBeGreaterThan(altitudeKmToApproxPressureHpa(6));
  });

  it("recommends an auto-fit top altitude within the allowed window", () => {
    const top = recommendedStuveTopAltitudeKm(chart);
    expect(top).toBeGreaterThanOrEqual(4.5);
    expect(top).toBeLessThanOrEqual(6.5);
  });
});

describe("Stuve model helpers", () => {
  it("interpolates approximate ISA heights and orders parcel pressures", () => {
    expect(pressureToApproxHeightMeters(1000)).toBe(111);
    expect(pressureToApproxHeightMeters(500)).toBe(5574);
    const pressures = buildRenderableParcelPressures(950, [900, 850, 700]);
    for (let i = 1; i < pressures.length; i++) expect(pressures[i]).toBeLessThan(pressures[i - 1]);
    expect(Math.max(...pressures)).toBeLessThanOrEqual(950.5);
  });

  it("builds a parcel path that cools with height", () => {
    const profile = [
      {
        pressureHpa: 950,
        temperatureC: 20,
        dewPointC: 8,
        heightKm: 0.5,
        relativeHumidityPercent: null,
        cloudCoverPercent: null,
        windSpeedKmh: null,
        isSynthetic: false,
      },
      {
        pressureHpa: 500,
        temperatureC: -12,
        dewPointC: -20,
        heightKm: 5.5,
        relativeHumidityPercent: null,
        cloudCoverPercent: null,
        windSpeedKmh: null,
        isSynthetic: false,
      },
    ];
    const path = buildParcelAscentPath([950, 850, 700, 500], profile, 20, 8, 950, 2);
    expect(path[0].temperatureC).toBeGreaterThan(path[path.length - 1].temperatureC);
  });
});

describe("Color scales", () => {
  it("thermicStrengthColor runs from pale (weak) to red (strong)", () => {
    const weak = thermicStrengthColor(0);
    const strong = thermicStrengthColor(5);
    expect(weak[0]).toBeGreaterThan(200); // near-white
    expect(strong[0]).toBeGreaterThan(strong[2]); // red dominant
  });

  it("windSpeedColor clamps to the scale ends", () => {
    expect(windSpeedColor(-10)).toEqual(windSpeedColor(0));
    expect(windSpeedColor(200)).toEqual(windSpeedColor(60));
  });
});

describe("Wind barb speed decomposition", () => {
  it("rounds to 5-knot increments and splits flags/feathers", () => {
    expect(windBarbSpeedParts(0)).toMatchObject({ roundedKnots: 0 });
    // ~50 kt -> one pennant.
    const parts = windBarbSpeedParts(50 * 1.852);
    expect(parts.roundedKnots).toBe(50);
    expect(parts.flags).toBe(1);
  });
});

describe("Viewport zoom", () => {
  it("zooming in lowers the visible top and clamps to bounds", () => {
    const zoomedIn = zoomedTopAltitudeKm(4.5, 1.5);
    expect(zoomedIn).toBeLessThan(4.5);
    expect(zoomedTopAltitudeKm(2, 10)).toBe(MIN_TOP_ALTITUDE_KM);
    // Gentle zoom-out (factor 0.7) raises the visible top, clamped to the max.
    expect(zoomedTopAltitudeKm(10, 0.7)).toBe(MAX_TOP_ALTITUDE_KM);
  });
});
