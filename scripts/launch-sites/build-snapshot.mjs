#!/usr/bin/env node

// Build-time snapshot generator for ParaglidingEarth launch sites.
//
// Fetches (or reads from a local file) a GeoJSON FeatureCollection of launch
// sites, validates and tiles the sites into 2x2 degree buckets, and writes a
// deterministic, content-addressed snapshot (manifest.json + tiles/**.json)
// that the Kotlin/Wasm web app bundles and loads at runtime.

import { mkdir, readFile, rename, rm, writeFile } from "node:fs/promises";
import path from "node:path";
import crypto from "node:crypto";
import process from "node:process";
import { pathToFileURL } from "node:url";

const LIVE_URL =
  "https://www.paragliding.earth/api/geojson/getBoundingBoxSites.php?north=90&south=-90&west=-180&east=180&style=detailled";
const USER_AGENT =
  "CloudbasePredictor/launch-sites-snapshot (+https://github.com/borodin-dmitry/CloudbasePredictor)";
const MAX_RESPONSE_BYTES = 100 * 1024 * 1024;
const FETCH_TIMEOUT_MS = 90_000;
const MAX_FETCH_ATTEMPTS = 3;
const TILE_SIZE_DEGREES = 2;
const DATASET_ID_LENGTH = 32;
const SOURCE = Object.freeze({ name: "ParaglidingEarth", license: "CC BY-SA 3.0" });

function isFiniteNumber(value) {
  return typeof value === "number" && Number.isFinite(value);
}

function sleep(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

/**
 * Extracts and validates [lon, lat] coordinates from a raw GeoJSON feature.
 * Returns null when the feature has no usable coordinates.
 */
function extractCoordinates(feature) {
  const geometry = feature && feature.geometry;
  if (!geometry || !Array.isArray(geometry.coordinates)) return null;
  const [lon, lat] = geometry.coordinates;
  if (!isFiniteNumber(lon) || !isFiniteNumber(lat)) return null;
  if (lon < -180 || lon > 180) return null;
  if (lat < -90 || lat > 90) return null;
  return [lon, lat];
}

function computeTileIndex(lon, lat) {
  let latIndex = Math.floor((lat + 90) / TILE_SIZE_DEGREES);
  let lonIndex = Math.floor((lon + 180) / TILE_SIZE_DEGREES);
  if (lat === 90) latIndex = 89;
  if (lon === 180) lonIndex = 179;
  return { latIndex, lonIndex };
}

function tileBoundingBox(latIndex, lonIndex) {
  const south = latIndex * TILE_SIZE_DEGREES - 90;
  const north = south + TILE_SIZE_DEGREES;
  const west = lonIndex * TILE_SIZE_DEGREES - 180;
  const east = west + TILE_SIZE_DEGREES;
  return { south, west, north, east };
}

/**
 * Stable per-site id used for tiling, deduplication and cross-tile duplicate
 * detection. Prefers the ParaglidingEarth site id, then the GeoJSON feature
 * id, then falls back to a coordinate+name composite key.
 */
function stableIdFor(feature, lon, lat) {
  const properties = feature.properties || {};
  if (properties.pge_site_id !== undefined && properties.pge_site_id !== null && properties.pge_site_id !== "") {
    return String(properties.pge_site_id);
  }
  if (feature.id !== undefined && feature.id !== null && feature.id !== "") {
    return String(feature.id);
  }
  const name = properties.name !== undefined && properties.name !== null ? String(properties.name) : "";
  return `${lon.toFixed(5)},${lat.toFixed(5)}:${name}`;
}

/**
 * Reduces a raw GeoJSON feature to the flat shape stored in the snapshot:
 * `{ id, geometry: { coordinates }, properties }`, dropping only the
 * top-level `type` and geometry `type`. `properties` is kept unchanged so
 * Kotlin and Node never diverge on field cleaning.
 */
function canonicalFeature(feature, lon, lat) {
  const out = {};
  if (feature.id !== undefined) out.id = feature.id;
  out.geometry = { coordinates: [lon, lat] };
  out.properties = feature.properties !== undefined ? feature.properties : {};
  return out;
}

function tileFileJson(features) {
  return `${JSON.stringify({ features }, null, 2)}\n`;
}

/**
 * Pure transformation from a raw GeoJSON FeatureCollection to a tiled
 * snapshot. Does not touch the filesystem or the network.
 *
 * Returns `{ manifest, tiles }` where `tiles` is an array of
 * `{ path, latIndex, lonIndex, key, features, json, bytes, sha256,
 *   south, west, north, east, siteCount }`, sorted by (latIndex, lonIndex).
 */
export function buildSnapshot(featureCollection) {
  const rawFeatures = Array.isArray(featureCollection?.features) ? featureCollection.features : [];

  // key "latIndex:lonIndex" -> { latIndex, lonIndex, entries: Map(stableId -> canonicalFeature) }
  const tilesByKey = new Map();

  for (const feature of rawFeatures) {
    if (!feature || typeof feature !== "object") continue;
    const coordinates = extractCoordinates(feature);
    if (!coordinates) continue;
    const [lon, lat] = coordinates;
    const { latIndex, lonIndex } = computeTileIndex(lon, lat);
    const key = `${latIndex}:${lonIndex}`;
    const stableId = stableIdFor(feature, lon, lat);

    let tile = tilesByKey.get(key);
    if (!tile) {
      tile = { latIndex, lonIndex, entries: new Map() };
      tilesByKey.set(key, tile);
    }
    // Keep the first occurrence in input order per stable id (deterministic dedup).
    if (!tile.entries.has(stableId)) {
      tile.entries.set(stableId, canonicalFeature(feature, lon, lat));
    }
  }

  const orderedTiles = [...tilesByKey.values()]
    .filter((tile) => tile.entries.size > 0)
    .sort((a, b) => a.latIndex - b.latIndex || a.lonIndex - b.lonIndex);

  const tiles = [];
  let siteCount = 0;

  for (const tile of orderedTiles) {
    const sortedStableIds = [...tile.entries.keys()].sort();
    const features = sortedStableIds.map((id) => tile.entries.get(id));
    const json = tileFileJson(features);
    const bytes = Buffer.byteLength(json, "utf8");
    const sha256 = crypto.createHash("sha256").update(json, "utf8").digest("hex");
    const bbox = tileBoundingBox(tile.latIndex, tile.lonIndex);

    siteCount += features.length;
    tiles.push({
      key: `${tile.latIndex}:${tile.lonIndex}`,
      path: `tiles/${tile.latIndex}/${tile.lonIndex}.json`,
      latIndex: tile.latIndex,
      lonIndex: tile.lonIndex,
      features,
      json,
      bytes,
      sha256,
      siteCount: features.length,
      ...bbox,
    });
  }

  // datasetId is derived from stable tile content only (never generatedAt),
  // so rebuilding identical input yields an identical datasetId.
  const stableContent = tiles.map((tile) => tile.json).join("\n");
  const datasetId = crypto
    .createHash("sha256")
    .update(stableContent, "utf8")
    .digest("hex")
    .slice(0, DATASET_ID_LENGTH);

  const manifest = {
    schemaVersion: 1,
    datasetId,
    generatedAt: new Date().toISOString(),
    siteCount,
    tileSizeDegrees: TILE_SIZE_DEGREES,
    source: { name: SOURCE.name, license: SOURCE.license },
    tiles: tiles.map((tile) => ({
      key: tile.key,
      path: tile.path,
      south: tile.south,
      west: tile.west,
      north: tile.north,
      east: tile.east,
      siteCount: tile.siteCount,
      bytes: tile.bytes,
      sha256: tile.sha256,
    })),
  };

  return { manifest, tiles };
}

/**
 * Atomically writes a built snapshot to `outputDir`: stages every file in a
 * sibling temp directory, then removes any existing `outputDir` and renames
 * the temp directory into place. A failure at any point leaves the existing
 * `outputDir` untouched (never a partially written snapshot).
 */
export async function writeSnapshot(outputDir, built) {
  const resolvedOutputDir = path.resolve(outputDir);
  const parentDir = path.dirname(resolvedOutputDir);
  const tmpDir = path.join(parentDir, `${path.basename(resolvedOutputDir)}.tmp-${process.pid}`);

  await rm(tmpDir, { recursive: true, force: true });
  await mkdir(tmpDir, { recursive: true });

  try {
    for (const tile of built.tiles) {
      const tileFilePath = path.join(tmpDir, ...tile.path.split("/"));
      await mkdir(path.dirname(tileFilePath), { recursive: true });
      await writeFile(tileFilePath, tile.json, "utf8");
    }

    const manifestJson = `${JSON.stringify(built.manifest, null, 2)}\n`;
    await writeFile(path.join(tmpDir, "manifest.json"), manifestJson, "utf8");

    await rm(resolvedOutputDir, { recursive: true, force: true });
    await rename(tmpDir, resolvedOutputDir);
  } catch (error) {
    await rm(tmpDir, { recursive: true, force: true }).catch(() => {});
    throw error;
  }
}

/**
 * Reads a fetch Response body while enforcing a maximum byte size, aborting
 * the stream as soon as the limit is exceeded instead of buffering the
 * entire (potentially huge) payload first.
 */
async function readResponseTextWithLimit(response, maxBytes) {
  if (!response.body) {
    const text = await response.text();
    if (Buffer.byteLength(text, "utf8") > maxBytes) {
      throw new Error(`Response body exceeded maximum allowed size of ${maxBytes} bytes`);
    }
    return text;
  }

  const reader = response.body.getReader();
  const chunks = [];
  let totalBytes = 0;
  for (;;) {
    const { done, value } = await reader.read();
    if (done) break;
    totalBytes += value.byteLength;
    if (totalBytes > maxBytes) {
      await reader.cancel().catch(() => {});
      throw new Error(`Response body exceeded maximum allowed size of ${maxBytes} bytes`);
    }
    chunks.push(value);
  }
  return Buffer.concat(chunks.map((chunk) => Buffer.from(chunk))).toString("utf8");
}

/**
 * Fetches the live ParaglidingEarth FeatureCollection with a timeout, retry
 * and exponential backoff. Throws on any network, HTTP-status, size-limit or
 * parse failure; callers must not write a snapshot when this throws.
 */
async function fetchLiveFeatureCollection() {
  // The endpoint is fixed. Only a trusted env var may override it, for
  // manual debugging -- it must never be settable from CLI arguments.
  const url = process.env.PARAGLIDING_EARTH_SNAPSHOT_URL || LIVE_URL;

  let lastError;
  for (let attempt = 1; attempt <= MAX_FETCH_ATTEMPTS; attempt += 1) {
    const controller = new AbortController();
    const timeoutId = setTimeout(() => controller.abort(), FETCH_TIMEOUT_MS);
    try {
      const response = await fetch(url, {
        signal: controller.signal,
        headers: { "User-Agent": USER_AGENT },
      });
      if (!response.ok) {
        throw new Error(`ParaglidingEarth request failed with HTTP ${response.status} ${response.statusText}`);
      }
      const contentLength = Number(response.headers.get("content-length"));
      if (Number.isFinite(contentLength) && contentLength > MAX_RESPONSE_BYTES) {
        throw new Error(`ParaglidingEarth response too large: ${contentLength} bytes`);
      }
      const text = await readResponseTextWithLimit(response, MAX_RESPONSE_BYTES);
      return JSON.parse(text);
    } catch (error) {
      lastError = error;
      if (attempt < MAX_FETCH_ATTEMPTS) {
        await sleep(1000 * 2 ** (attempt - 1));
      }
    } finally {
      clearTimeout(timeoutId);
    }
  }

  throw new Error(
    `Failed to fetch ParaglidingEarth launch sites after ${MAX_FETCH_ATTEMPTS} attempts: ${
      lastError?.message ?? lastError
    }`,
  );
}

function parseArgs(argv) {
  const args = { output: undefined, input: undefined };
  for (let i = 0; i < argv.length; i += 1) {
    const arg = argv[i];
    if (arg === "--output") {
      args.output = argv[(i += 1)];
    } else if (arg === "--input") {
      args.input = argv[(i += 1)];
    } else {
      throw new Error(`Unknown argument: ${arg}`);
    }
  }
  if (!args.output) {
    throw new Error("Missing required --output <dir> argument");
  }
  return args;
}

async function main() {
  const args = parseArgs(process.argv.slice(2));

  const featureCollection = args.input
    ? JSON.parse(await readFile(path.resolve(args.input), "utf8"))
    : await fetchLiveFeatureCollection();

  const built = buildSnapshot(featureCollection);
  const outputDir = path.resolve(args.output);
  await writeSnapshot(outputDir, built);

  console.log(
    `Snapshot written to ${outputDir}: ${built.manifest.siteCount} sites across ${built.manifest.tiles.length} tiles (datasetId=${built.manifest.datasetId})`,
  );
}

const isMainModule = process.argv[1] !== undefined && import.meta.url === pathToFileURL(process.argv[1]).href;
if (isMainModule) {
  main().catch((error) => {
    console.error(error?.stack || error?.message || String(error));
    process.exitCode = 1;
  });
}
