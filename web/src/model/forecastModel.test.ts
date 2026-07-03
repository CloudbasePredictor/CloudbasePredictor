import { describe, expect, it } from "vitest";
import {
  FORECAST_MODEL_ORDER,
  FORECAST_MODELS,
  fallbackFor,
  forecastModelFromApiName,
} from "./forecastModel";

describe("forecastModel", () => {
  it("resolves models by api name", () => {
    expect(forecastModelFromApiName("icon_seamless")?.id).toBe("ICON_SEAMLESS");
    expect(forecastModelFromApiName("ecmwf_ifs025")?.id).toBe("ECMWF_IFS");
    expect(forecastModelFromApiName("does_not_exist")).toBeNull();
  });

  it("follows the ICON fallback chain to BEST_MATCH", () => {
    expect(fallbackFor("ICON_D2")).toBe("ICON_EU");
    expect(fallbackFor("ICON_EU")).toBe("ICON_GLOBAL");
    expect(fallbackFor("ICON_GLOBAL")).toBe("BEST_MATCH");
    expect(fallbackFor("METEOFRANCE_AROME")).toBe("METEOFRANCE_ARPEGE");
    expect(fallbackFor("METEOFRANCE_ARPEGE")).toBe("BEST_MATCH");
    // Models without an explicit chain default to BEST_MATCH.
    expect(fallbackFor("BEST_MATCH")).toBe("BEST_MATCH");
  });

  it("keeps ordering and descriptor integrity", () => {
    expect(FORECAST_MODEL_ORDER[0]).toBe("BEST_MATCH");
    for (const id of FORECAST_MODEL_ORDER) {
      expect(FORECAST_MODELS[id].id).toBe(id);
      expect(FORECAST_MODELS[id].apiName.length).toBeGreaterThan(0);
    }
  });
});
