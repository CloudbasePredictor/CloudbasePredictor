/**
 * Base-map layer styles and attributions.
 *
 * Ported from `ui/map/MapLayerStyle.kt` and `data/map/MapLayerRepository.kt`.
 * The four layers, tile URLs, native zoom limits and attribution strings are
 * copied verbatim from the Android sources (attribution wording matches
 * `res/values/strings.xml`). All sources are keyless.
 *
 * This module is deliberately free of any `maplibre-gl` runtime import so it
 * stays in the small main bundle and is trivially unit-testable; it only
 * produces plain style descriptors that {@link MapPicker} hands to MapLibre.
 */

export type MapLayerId = "OPENFREEMAP" | "OPENTOPOMAP" | "NASA_GIBS" | "ESRI_WORLD_IMAGERY";

export const DEFAULT_MAP_LAYER: MapLayerId = "OPENFREEMAP";

export interface MapLayerDescriptor {
  readonly id: MapLayerId;
  readonly label: string;
  readonly attributionCompact: string;
  readonly attributionFull: string;
}

const OPENFREEMAP_STYLE_URL = "https://tiles.openfreemap.org/styles/liberty";

const OPENTOPOMAP_LAYER_ID = "opentopomap";
const OPENTOPOMAP_MAX_NATIVE_ZOOM = 17;

const NASA_GIBS_LAYER_ID = "nasa-gibs-true-color";
const NASA_GIBS_TILE_MATRIX_SET = "GoogleMapsCompatible_Level9";
const NASA_GIBS_MAX_NATIVE_ZOOM = 9;

const ESRI_WORLD_IMAGERY_LAYER_ID = "esri-world-imagery";
const ESRI_WORLD_IMAGERY_TILE_URL =
  "https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}";
const ESRI_WORLD_IMAGERY_MAX_NATIVE_ZOOM = 23;

const TILE_SIZE = 256;

export const MAP_LAYERS: Record<MapLayerId, MapLayerDescriptor> = {
  OPENFREEMAP: {
    id: "OPENFREEMAP",
    label: "Streets",
    attributionCompact: "© OpenMapTiles · © OpenStreetMap",
    attributionFull:
      "Map service: OpenFreeMap\nVector tiles: © OpenMapTiles\n" +
      "Map data: © OpenStreetMap contributors (ODbL)",
  },
  OPENTOPOMAP: {
    id: "OPENTOPOMAP",
    label: "Topo",
    attributionCompact: "© OpenTopoMap CC-BY-SA · © OpenStreetMap/SRTM",
    attributionFull:
      "Map data: © OpenStreetMap contributors (ODbL), SRTM\nMap style: © OpenTopoMap (CC-BY-SA)",
  },
  NASA_GIBS: {
    id: "NASA_GIBS",
    label: "Satellite (GIBS)",
    attributionCompact: "NASA GIBS",
    attributionFull: "Imagery: NASA Global Imagery Browse Services (GIBS)",
  },
  ESRI_WORLD_IMAGERY: {
    id: "ESRI_WORLD_IMAGERY",
    label: "Satellite (Esri)",
    attributionCompact: "Powered by Esri · Sources",
    attributionFull:
      "Powered by Esri\nSources: Esri, Vantor, GeoEye, Earthstar Geographics, CNES/Airbus DS, " +
      "USDA, USGS, AeroGRID, IGN, © OpenStreetMap contributors, TomTom, Garmin, FAO, NOAA, " +
      "and the GIS User Community",
  },
};

/** Layer picker order, matching the Kotlin `MapLayerPreference` enum. */
export const MAP_LAYER_ORDER: readonly MapLayerId[] = [
  "OPENFREEMAP",
  "OPENTOPOMAP",
  "NASA_GIBS",
  "ESRI_WORLD_IMAGERY",
];

export function openTopoMapTileUrls(): string[] {
  return [
    "https://a.tile.opentopomap.org/{z}/{x}/{y}.png",
    "https://b.tile.opentopomap.org/{z}/{x}/{y}.png",
    "https://c.tile.opentopomap.org/{z}/{x}/{y}.png",
  ];
}

/** Yesterday's date in UTC (yyyy-MM-dd); GIBS true-color lags the current day. */
export function nasaGibsTileDateUtc(nowMillis: number = Date.now()): string {
  const yesterday = new Date(nowMillis - 24 * 60 * 60 * 1000);
  const year = yesterday.getUTCFullYear();
  const month = String(yesterday.getUTCMonth() + 1).padStart(2, "0");
  const day = String(yesterday.getUTCDate()).padStart(2, "0");
  return `${year}-${month}-${day}`;
}

export function nasaGibsTrueColorTileUrl(date: string): string {
  return (
    "https://gibs.earthdata.nasa.gov/wmts/epsg3857/best/" +
    "MODIS_Terra_CorrectedReflectance_TrueColor/default/" +
    `${date}/${NASA_GIBS_TILE_MATRIX_SET}/{z}/{y}/{x}.jpg`
  );
}

export function esriWorldImageryTileUrl(): string {
  return ESRI_WORLD_IMAGERY_TILE_URL;
}

/** A minimal MapLibre raster source, kept local so this module needs no maplibre types. */
interface RasterSourceSpec {
  type: "raster";
  tiles: string[];
  tileSize: number;
  minzoom: number;
  maxzoom: number;
  attribution: string;
}

/** A minimal MapLibre style spec for a single full-screen raster layer. */
export interface RasterStyleSpec {
  version: 8;
  sources: Record<string, RasterSourceSpec>;
  layers: Array<{ id: string; type: "raster"; source: string }>;
}

function rasterStyle(
  layerId: string,
  tiles: string[],
  maxNativeZoom: number,
  attribution: string,
): RasterStyleSpec {
  return {
    version: 8,
    sources: {
      [layerId]: {
        type: "raster",
        tiles,
        tileSize: TILE_SIZE,
        minzoom: 0,
        maxzoom: maxNativeZoom,
        attribution,
      },
    },
    layers: [{ id: layerId, type: "raster", source: layerId }],
  };
}

/**
 * Build the MapLibre style for a layer: the OpenFreeMap vector style URL, or a
 * self-contained raster style spec for the tile-based layers.
 */
export function buildMapStyle(
  layer: MapLayerId,
  nowMillis: number = Date.now(),
): string | RasterStyleSpec {
  const descriptor = MAP_LAYERS[layer];
  switch (layer) {
    case "OPENFREEMAP":
      return OPENFREEMAP_STYLE_URL;
    case "OPENTOPOMAP":
      return rasterStyle(
        OPENTOPOMAP_LAYER_ID,
        openTopoMapTileUrls(),
        OPENTOPOMAP_MAX_NATIVE_ZOOM,
        descriptor.attributionFull,
      );
    case "NASA_GIBS":
      return rasterStyle(
        NASA_GIBS_LAYER_ID,
        [nasaGibsTrueColorTileUrl(nasaGibsTileDateUtc(nowMillis))],
        NASA_GIBS_MAX_NATIVE_ZOOM,
        descriptor.attributionFull,
      );
    case "ESRI_WORLD_IMAGERY":
      return rasterStyle(
        ESRI_WORLD_IMAGERY_LAYER_ID,
        [ESRI_WORLD_IMAGERY_TILE_URL],
        ESRI_WORLD_IMAGERY_MAX_NATIVE_ZOOM,
        descriptor.attributionFull,
      );
  }
}
