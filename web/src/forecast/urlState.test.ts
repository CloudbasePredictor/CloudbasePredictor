import { describe, expect, it } from "vitest";
import { buildForecastHash, DEFAULT_HOUR, type ForecastRoute, parseForecastHash } from "./urlState";

const brauneckRoute: ForecastRoute = {
  location: { latitude: 47.68, longitude: 11.63, name: "Brauneck" },
  model: "ICON_SEAMLESS",
  day: 2,
  hour: 14,
  view: "wind",
};

describe("urlState", () => {
  it("round-trips a full route through the hash", () => {
    const hash = buildForecastHash(brauneckRoute);
    const parsed = parseForecastHash(hash);
    expect(parsed).toEqual({
      location: { latitude: 47.68, longitude: 11.63, name: "Brauneck" },
      model: "ICON_SEAMLESS",
      day: 2,
      hour: 14,
      view: "wind",
    });
  });

  it("builds a readable hash with the model apiName", () => {
    const hash = buildForecastHash(brauneckRoute);
    expect(hash).toContain("#/forecast?");
    expect(hash).toContain("lat=47.680000");
    expect(hash).toContain("lon=11.630000");
    expect(hash).toContain("model=icon_seamless");
    expect(hash).toContain("day=2");
    expect(hash).toContain("hour=14");
    expect(hash).toContain("view=wind");
    expect(hash).toContain("name=Brauneck");
  });

  it("omits the name when the location has none", () => {
    const hash = buildForecastHash({
      ...brauneckRoute,
      location: { latitude: 47.68, longitude: 11.63 },
    });
    expect(hash).not.toContain("name=");
    const parsed = parseForecastHash(hash);
    expect(parsed?.location).toEqual({ latitude: 47.68, longitude: 11.63 });
  });

  it("returns null when latitude or longitude is missing", () => {
    expect(parseForecastHash("#/forecast?model=icon_seamless")).toBeNull();
    expect(parseForecastHash("#/forecast?lat=47.68")).toBeNull();
    expect(parseForecastHash("#/")).toBeNull();
    expect(parseForecastHash("")).toBeNull();
  });

  it("rejects out-of-range coordinates", () => {
    expect(parseForecastHash("#/forecast?lat=91&lon=11")).toBeNull();
    expect(parseForecastHash("#/forecast?lat=47&lon=181")).toBeNull();
  });

  it("falls back to defaults for missing or invalid optional fields", () => {
    const parsed = parseForecastHash("#/forecast?lat=47.68&lon=11.63");
    expect(parsed).toEqual({
      location: { latitude: 47.68, longitude: 11.63 },
      model: "ICON_SEAMLESS",
      day: 0,
      hour: DEFAULT_HOUR,
      view: "thermic",
    });
  });

  it("falls back to a known model for an unknown apiName", () => {
    const parsed = parseForecastHash("#/forecast?lat=47.68&lon=11.63&model=nope");
    expect(parsed?.model).toBe("ICON_SEAMLESS");
  });

  it("clamps the hour into the slider range", () => {
    expect(parseForecastHash("#/forecast?lat=47.68&lon=11.63&hour=3")?.hour).toBe(6);
    expect(parseForecastHash("#/forecast?lat=47.68&lon=11.63&hour=30")?.hour).toBe(22);
  });

  it("clamps negative day indices to zero", () => {
    expect(parseForecastHash("#/forecast?lat=47.68&lon=11.63&day=-4")?.day).toBe(0);
  });

  it("decodes an encoded place name", () => {
    const hash = buildForecastHash({
      ...brauneckRoute,
      location: { latitude: 46.0, longitude: 7.0, name: "Val d'Aosta" },
    });
    expect(parseForecastHash(hash)?.location.name).toBe("Val d'Aosta");
  });
});
