/**
 * Golden regression test for the TypeScript engine port.
 *
 * The fixture `__fixtures__/brauneck_icon_seamless.json` is exported from the
 * Kotlin unit test `EngineFixtureExportTest.kt`, which runs the real conversion
 * plus `ThermalForecastEngine` / `analyzeParcel` pipeline and serialises every
 * value with full double precision. This test converts the same raw Open-Meteo
 * asset, runs the ported engine driven identically, and asserts every field
 * matches the Kotlin output.
 *
 * The Kotlin engine computes in `Float`; the TypeScript port emulates float32
 * (see `float32.ts`), so the values match essentially exactly. The tolerance
 * below is the tightest that passes.
 */

import { describe, expect, it } from "vitest";
import rawAsset from "../api/__fixtures__/brauneck_icon_seamless_20260418.json";
import { toHourlyForecastData } from "../api/conversion";
import type { HourlyForecastData, OpenMeteoHourlyForecastResponse } from "../api/types";
import goldenFixture from "./__fixtures__/brauneck_icon_seamless.json";
import { forEachDaytimeHour } from "./engineDriver";
import { analyzeParcel, type ParcelAnalysisResult } from "./parcelAnalysis";
import { ThermalForecastEngine, type ThermalForecastResult } from "./thermalForecastEngine";

/**
 * Combined absolute + relative tolerance. The float32 emulation reproduces the
 * Kotlin `Float` engine bit-for-bit on this fixture (it passes even at 0), but
 * 1e-9 is kept as a defensive bound against the rare double-rounding that JS
 * `Math.fround` of a `/`, `pow`, `exp` or `ln` can introduce on other data.
 */
const TOLERANCE = 1e-9;

interface ThermalForecastEntry {
  date: string;
  hour: number;
  result: ThermalForecastResult;
}

interface ParcelAnalysisEntry {
  date: string;
  hour: number;
  result: ParcelAnalysisResult;
}

/**
 * Runs the production {@link forEachDaytimeHour} driver (the single source of
 * truth for how model data maps onto engine inputs, shared with the Phase-3
 * chart builders) and collects the engine + parcel outputs per daytime hour.
 */
function runEnginePipeline(data: HourlyForecastData): {
  thermalForecasts: ThermalForecastEntry[];
  parcelAnalyses: ParcelAnalysisEntry[];
} {
  const thermalForecasts: ThermalForecastEntry[] = [];
  const parcelAnalyses: ParcelAnalysisEntry[] = [];

  forEachDaytimeHour(data, (inputs) => {
    const thermal = ThermalForecastEngine.analyze(inputs.thermalInput);
    if (thermal !== null) {
      thermalForecasts.push({ date: inputs.date, hour: inputs.hour, result: thermal });
    }

    const parcel = analyzeParcel(
      inputs.profile,
      inputs.surfaceTemperatureC,
      inputs.surfaceDewPointC,
      inputs.surfacePressureHpa,
      inputs.elevationKm,
      inputs.heatingInput,
      inputs.modelCapeJKg,
    );
    if (parcel !== null) {
      parcelAnalyses.push({ date: inputs.date, hour: inputs.hour, result: parcel });
    }
  });

  return { thermalForecasts, parcelAnalyses };
}

/** Deep structural comparison; numbers within combined absolute+relative tolerance. */
function expectClose(actual: unknown, expected: unknown, path: string): void {
  if (typeof expected === "number") {
    expect(typeof actual, `${path}: expected a number`).toBe("number");
    const a = actual as number;
    if (Number.isNaN(expected)) {
      expect(Number.isNaN(a), `${path}: expected NaN`).toBe(true);
      return;
    }
    const diff = Math.abs(a - expected);
    const bound = TOLERANCE * Math.max(1, Math.abs(a), Math.abs(expected));
    expect(diff <= bound, `${path}: ${a} !== ${expected} (diff ${diff} > ${bound})`).toBe(true);
    return;
  }
  if (expected === null) {
    expect(actual, `${path}: expected null`).toBeNull();
    return;
  }
  if (typeof expected === "boolean" || typeof expected === "string") {
    expect(actual, `${path}`).toBe(expected);
    return;
  }
  if (Array.isArray(expected)) {
    expect(Array.isArray(actual), `${path}: expected an array`).toBe(true);
    const actualArray = actual as unknown[];
    expect(actualArray.length, `${path}: array length`).toBe(expected.length);
    expected.forEach((item, index) => {
      expectClose(actualArray[index], item, `${path}[${index}]`);
    });
    return;
  }
  if (typeof expected === "object") {
    expect(typeof actual, `${path}: expected an object`).toBe("object");
    expect(actual, `${path}: expected non-null object`).not.toBeNull();
    const expectedObject = expected as Record<string, unknown>;
    const actualObject = actual as Record<string, unknown>;
    const keys = new Set([...Object.keys(expectedObject), ...Object.keys(actualObject)]);
    for (const key of keys) {
      expectClose(actualObject[key], expectedObject[key], `${path}.${key}`);
    }
    return;
  }
  throw new Error(`${path}: unsupported expected type ${typeof expected}`);
}

const golden = goldenFixture as unknown as {
  inputAsset: string;
  hourlyForecastData: unknown;
  thermalForecasts: ThermalForecastEntry[];
  parcelAnalyses: ParcelAnalysisEntry[];
};

const response = rawAsset as unknown as OpenMeteoHourlyForecastResponse;
const converted = toHourlyForecastData(response);
const pipeline = runEnginePipeline(converted);

describe(`engine golden fixture (${golden.inputAsset}, tolerance ${TOLERANCE})`, () => {
  it("reproduces the converted HourlyForecastData (locks Phase 1 conversion)", () => {
    expectClose(converted, golden.hourlyForecastData, "hourlyForecastData");
  });

  it("produces the same daytime hours as the Kotlin pipeline", () => {
    expect(pipeline.thermalForecasts.map((e) => `${e.date}@${e.hour}`)).toEqual(
      golden.thermalForecasts.map((e) => `${e.date}@${e.hour}`),
    );
    expect(pipeline.parcelAnalyses.map((e) => `${e.date}@${e.hour}`)).toEqual(
      golden.parcelAnalyses.map((e) => `${e.date}@${e.hour}`),
    );
  });

  it("matches every ThermalForecastEngine output field", () => {
    expect(pipeline.thermalForecasts.length).toBe(golden.thermalForecasts.length);
    pipeline.thermalForecasts.forEach((entry, index) => {
      const expectedEntry = golden.thermalForecasts[index];
      expect(entry.date).toBe(expectedEntry.date);
      expect(entry.hour).toBe(expectedEntry.hour);
      expectClose(
        entry.result,
        expectedEntry.result,
        `thermal[${index} ${entry.date}@${entry.hour}]`,
      );
    });
  });

  it("matches every analyzeParcel output field", () => {
    expect(pipeline.parcelAnalyses.length).toBe(golden.parcelAnalyses.length);
    pipeline.parcelAnalyses.forEach((entry, index) => {
      const expectedEntry = golden.parcelAnalyses[index];
      expect(entry.date).toBe(expectedEntry.date);
      expect(entry.hour).toBe(expectedEntry.hour);
      expectClose(
        entry.result,
        expectedEntry.result,
        `parcel[${index} ${entry.date}@${entry.hour}]`,
      );
    });
  });
});
