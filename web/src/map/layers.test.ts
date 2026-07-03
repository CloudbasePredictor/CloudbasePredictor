import { describe, expect, it } from "vitest";
import {
  buildMapStyle,
  MAP_LAYER_ORDER,
  MAP_LAYERS,
  nasaGibsTileDateUtc,
  nasaGibsTrueColorTileUrl,
  openTopoMapTileUrls,
  type RasterStyleSpec,
} from "./layers";

describe("map layers", () => {
  it("uses the OpenFreeMap Liberty vector style URL", () => {
    expect(buildMapStyle("OPENFREEMAP")).toBe("https://tiles.openfreemap.org/styles/liberty");
  });

  it("builds a raster style for OpenTopoMap with the correct native zoom", () => {
    const style = buildMapStyle("OPENTOPOMAP") as RasterStyleSpec;
    expect(style.version).toBe(8);
    const source = style.sources.opentopomap;
    expect(source.type).toBe("raster");
    expect(source.tiles).toEqual(openTopoMapTileUrls());
    expect(source.maxzoom).toBe(17);
    expect(source.tileSize).toBe(256);
    expect(source.attribution).toContain("OpenTopoMap");
    expect(style.layers[0]).toEqual({
      id: "opentopomap",
      type: "raster",
      source: "opentopomap",
    });
  });

  it("computes yesterday's UTC date for NASA GIBS tiles", () => {
    const noon = Date.UTC(2026, 6, 3, 12, 0, 0); // 2026-07-03T12:00Z
    expect(nasaGibsTileDateUtc(noon)).toBe("2026-07-02");
  });

  it("embeds the tile date and matrix set in the NASA GIBS URL", () => {
    const url = nasaGibsTrueColorTileUrl("2026-07-02");
    expect(url).toContain("MODIS_Terra_CorrectedReflectance_TrueColor");
    expect(url).toContain("/2026-07-02/GoogleMapsCompatible_Level9/{z}/{y}/{x}.jpg");
  });

  it("builds the NASA GIBS raster style with the dated tile URL", () => {
    const noon = Date.UTC(2026, 6, 3, 12, 0, 0);
    const style = buildMapStyle("NASA_GIBS", noon) as RasterStyleSpec;
    const source = style.sources["nasa-gibs-true-color"];
    expect(source.tiles[0]).toContain("/2026-07-02/");
    expect(source.maxzoom).toBe(9);
  });

  it("uses the Esri World Imagery tile URL with its high native zoom", () => {
    const style = buildMapStyle("ESRI_WORLD_IMAGERY") as RasterStyleSpec;
    const source = style.sources["esri-world-imagery"];
    expect(source.tiles[0]).toContain("server.arcgisonline.com");
    expect(source.maxzoom).toBe(23);
  });

  it("exposes all four layers in enum order with attributions", () => {
    expect(MAP_LAYER_ORDER).toEqual([
      "OPENFREEMAP",
      "OPENTOPOMAP",
      "NASA_GIBS",
      "ESRI_WORLD_IMAGERY",
    ]);
    for (const id of MAP_LAYER_ORDER) {
      expect(MAP_LAYERS[id].attributionCompact.length).toBeGreaterThan(0);
      expect(MAP_LAYERS[id].attributionFull.length).toBeGreaterThan(0);
    }
  });
});
