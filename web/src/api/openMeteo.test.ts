import { afterEach, describe, expect, it, vi } from "vitest";
import rawAsset from "./__fixtures__/brauneck_icon_seamless_20260418.json";
import { fetchHourlyForecastWithFallback, OpenMeteoHttpError } from "./openMeteo";

function okResponse(body: unknown): Response {
  return {
    ok: true,
    status: 200,
    json: async () => body,
    text: async () => JSON.stringify(body),
  } as unknown as Response;
}

function errorResponse(status: number): Response {
  return {
    ok: false,
    status,
    json: async () => ({}),
    text: async () => "no data for this location",
  } as unknown as Response;
}

function modelOf(url: string): string | null {
  return new URLSearchParams(url.split("?")[1] ?? "").get("models");
}

afterEach(() => {
  vi.unstubAllGlobals();
});

describe("fetchHourlyForecastWithFallback", () => {
  it("walks the ICON chain past out-of-coverage models (HTTP 400)", async () => {
    const seen: string[] = [];
    vi.stubGlobal(
      "fetch",
      vi.fn(async (url: string) => {
        const model = modelOf(url) ?? "best_match";
        seen.push(model);
        if (model === "icon_d2" || model === "icon_eu") return errorResponse(400);
        return okResponse(rawAsset);
      }),
    );

    const result = await fetchHourlyForecastWithFallback({
      latitude: 47.68,
      longitude: 11.63,
      model: "ICON_D2",
    });

    expect(seen).toEqual(["icon_d2", "icon_eu", "icon_global"]);
    expect(result.model).toBe("ICON_GLOBAL");
    expect(result.data.hourlyPoints.length).toBeGreaterThan(0);
  });

  it("falls back all the way to BEST_MATCH (no `models` param) when every model 400s", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn(async (url: string) => {
        // BEST_MATCH omits the `models` parameter; let it succeed.
        return modelOf(url) === null ? okResponse(rawAsset) : errorResponse(400);
      }),
    );

    const result = await fetchHourlyForecastWithFallback({
      latitude: 1,
      longitude: 2,
      model: "ICON_SEAMLESS",
    });

    expect(result.model).toBe("BEST_MATCH");
  });

  it("rethrows non-400 errors instead of falling back", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn(async () => errorResponse(500)),
    );

    await expect(
      fetchHourlyForecastWithFallback({ latitude: 1, longitude: 2, model: "ICON_D2" }),
    ).rejects.toBeInstanceOf(OpenMeteoHttpError);
  });
});
