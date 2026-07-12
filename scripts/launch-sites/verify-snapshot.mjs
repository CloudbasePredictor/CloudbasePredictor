#!/usr/bin/env node

// Verifies the integrity of a launch-sites snapshot produced by
// build-snapshot.mjs: manifest shape, tile file hashes/sizes, dedup and
// bounding-box consistency.

import { readdir, readFile } from "node:fs/promises";
import path from "node:path";
import crypto from "node:crypto";
import process from "node:process";
import { pathToFileURL } from "node:url";

const DEFAULT_MIN_SITES = 1;
const DEFAULT_MAX_TILE_BYTES = 5_000_000;

function isFiniteNumber(value) {
  return typeof value === "number" && Number.isFinite(value);
}

/**
 * Recomputes the same stable id used by build-snapshot.mjs, from an already
 * built (canonical) snapshot feature.
 */
function stableIdFor(feature) {
  const properties = feature.properties || {};
  if (properties.pge_site_id !== undefined && properties.pge_site_id !== null && properties.pge_site_id !== "") {
    return String(properties.pge_site_id);
  }
  if (feature.id !== undefined && feature.id !== null && feature.id !== "") {
    return String(feature.id);
  }
  const coordinates = feature.geometry?.coordinates;
  const lon = coordinates?.[0];
  const lat = coordinates?.[1];
  const lonKey = isFiniteNumber(lon) ? lon.toFixed(5) : String(lon);
  const latKey = isFiniteNumber(lat) ? lat.toFixed(5) : String(lat);
  const name = properties.name !== undefined && properties.name !== null ? String(properties.name) : "";
  return `${lonKey},${latKey}:${name}`;
}

async function listFilesRecursive(dir) {
  const results = [];
  async function walk(currentDir) {
    let entries;
    try {
      entries = await readdir(currentDir, { withFileTypes: true });
    } catch (error) {
      if (error.code === "ENOENT") return;
      throw error;
    }
    for (const entry of entries) {
      const fullPath = path.join(currentDir, entry.name);
      if (entry.isDirectory()) {
        await walk(fullPath);
      } else if (entry.isFile()) {
        results.push(fullPath);
      }
    }
  }
  await walk(dir);
  return results;
}

function isWithinTileBounds(lon, lat, tileMeta) {
  const latOk = lat >= tileMeta.south && (lat < tileMeta.north || (tileMeta.north === 90 && lat === 90));
  const lonOk = lon >= tileMeta.west && (lon < tileMeta.east || (tileMeta.east === 180 && lon === 180));
  return latOk && lonOk;
}

/**
 * Verifies a snapshot directory against its own manifest.
 * Returns `{ ok, errors }`; never throws for expected validation failures
 * (missing/corrupt files, mismatched counts, etc).
 */
export async function verifySnapshot(dir, options = {}) {
  const minSites = options.minSites ?? DEFAULT_MIN_SITES;
  const maxTileBytes = options.maxTileBytes ?? DEFAULT_MAX_TILE_BYTES;
  const errors = [];

  let manifest;
  try {
    const manifestRaw = await readFile(path.join(dir, "manifest.json"), "utf8");
    manifest = JSON.parse(manifestRaw);
  } catch (error) {
    return { ok: false, errors: [`Failed to read manifest.json: ${error.message}`] };
  }

  if (manifest.schemaVersion !== 1) {
    errors.push(`schemaVersion must be 1, got ${JSON.stringify(manifest.schemaVersion)}`);
  }
  if (typeof manifest.datasetId !== "string" || manifest.datasetId.length === 0) {
    errors.push("datasetId must be a non-empty string");
  }
  if (!manifest.source || typeof manifest.source.name !== "string" || manifest.source.name.length === 0) {
    errors.push("source.name is required");
  }
  if (!manifest.source || typeof manifest.source.license !== "string" || manifest.source.license.length === 0) {
    errors.push("source.license is required");
  }
  if (!isFiniteNumber(manifest.siteCount) || manifest.siteCount < minSites) {
    errors.push(`siteCount ${manifest.siteCount} is below the minimum of ${minSites}`);
  }

  const tiles = Array.isArray(manifest.tiles) ? manifest.tiles : [];
  const manifestTilePaths = new Set();
  const seenStableIds = new Map(); // stableId -> tile path where first seen
  let summedSiteCount = 0;

  for (const tileMeta of tiles) {
    manifestTilePaths.add(tileMeta.path);
    const tileFilePath = path.join(dir, ...tileMeta.path.split("/"));

    let raw;
    try {
      raw = await readFile(tileFilePath, "utf8");
    } catch (error) {
      errors.push(`Tile file missing or unreadable: ${tileMeta.path} (${error.message})`);
      continue;
    }

    const actualBytes = Buffer.byteLength(raw, "utf8");
    if (actualBytes !== tileMeta.bytes) {
      errors.push(`Tile ${tileMeta.path} byte size mismatch: manifest says ${tileMeta.bytes}, actual ${actualBytes}`);
    }
    const actualSha256 = crypto.createHash("sha256").update(raw, "utf8").digest("hex");
    if (actualSha256 !== tileMeta.sha256) {
      errors.push(`Tile ${tileMeta.path} sha256 mismatch: manifest says ${tileMeta.sha256}, actual ${actualSha256}`);
    }
    if (actualBytes > maxTileBytes) {
      errors.push(`Tile ${tileMeta.path} exceeds maxTileBytes: ${actualBytes} > ${maxTileBytes}`);
    }

    let tileData;
    try {
      tileData = JSON.parse(raw);
    } catch (error) {
      errors.push(`Tile ${tileMeta.path} is not valid JSON: ${error.message}`);
      continue;
    }

    const features = Array.isArray(tileData.features) ? tileData.features : [];
    summedSiteCount += features.length;
    if (features.length !== tileMeta.siteCount) {
      errors.push(`Tile ${tileMeta.path} siteCount mismatch: manifest says ${tileMeta.siteCount}, actual ${features.length}`);
    }

    for (const feature of features) {
      const coordinates = feature?.geometry?.coordinates;
      const lon = coordinates?.[0];
      const lat = coordinates?.[1];
      const validCoordinates =
        isFiniteNumber(lon) && isFiniteNumber(lat) && lon >= -180 && lon <= 180 && lat >= -90 && lat <= 90;

      if (!validCoordinates) {
        errors.push(`Tile ${tileMeta.path} contains a feature with invalid coordinates: ${JSON.stringify(coordinates)}`);
      } else if (!isWithinTileBounds(lon, lat, tileMeta)) {
        errors.push(`Tile ${tileMeta.path} contains a feature outside its bounding box: [${lon}, ${lat}]`);
      }

      const stableId = stableIdFor(feature ?? {});
      if (seenStableIds.has(stableId)) {
        errors.push(`Duplicate stable id "${stableId}" found in ${seenStableIds.get(stableId)} and ${tileMeta.path}`);
      } else {
        seenStableIds.set(stableId, tileMeta.path);
      }
    }
  }

  if (summedSiteCount !== manifest.siteCount) {
    errors.push(`Sum of tile siteCounts (${summedSiteCount}) does not match manifest.siteCount (${manifest.siteCount})`);
  }

  const tilesDir = path.join(dir, "tiles");
  const onDiskTileFiles = await listFilesRecursive(tilesDir);
  for (const filePath of onDiskTileFiles) {
    const relativePath = path.relative(dir, filePath).split(path.sep).join("/");
    if (!manifestTilePaths.has(relativePath)) {
      errors.push(`Tile file on disk is not referenced by the manifest: ${relativePath}`);
    }
  }

  return { ok: errors.length === 0, errors };
}

function parseArgs(argv) {
  if (argv.length === 0 || argv[0].startsWith("--")) {
    throw new Error("Usage: verify-snapshot.mjs <snapshot-dir> [--min-sites <n>] [--max-tile-bytes <n>]");
  }
  const dir = argv[0];
  let minSites = DEFAULT_MIN_SITES;
  let maxTileBytes = DEFAULT_MAX_TILE_BYTES;
  for (let i = 1; i < argv.length; i += 1) {
    const arg = argv[i];
    if (arg === "--min-sites") {
      minSites = Number(argv[(i += 1)]);
    } else if (arg === "--max-tile-bytes") {
      maxTileBytes = Number(argv[(i += 1)]);
    } else {
      throw new Error(`Unknown argument: ${arg}`);
    }
  }
  return { dir, minSites, maxTileBytes };
}

async function main() {
  const { dir, minSites, maxTileBytes } = parseArgs(process.argv.slice(2));
  const resolvedDir = path.resolve(dir);
  const result = await verifySnapshot(resolvedDir, { minSites, maxTileBytes });

  if (!result.ok) {
    console.error(`Snapshot verification FAILED for ${resolvedDir}:`);
    for (const error of result.errors) {
      console.error(`  - ${error}`);
    }
    process.exit(1);
    return;
  }

  const manifest = JSON.parse(await readFile(path.join(resolvedDir, "manifest.json"), "utf8"));
  const largestTile = manifest.tiles.reduce((max, tile) => (max === null || tile.bytes > max.bytes ? tile : max), null);

  console.log(`Snapshot verification OK for ${resolvedDir}`);
  console.log(`  siteCount:   ${manifest.siteCount}`);
  console.log(`  tileCount:   ${manifest.tiles.length}`);
  console.log(`  largestTile: ${largestTile ? `${largestTile.path} (${largestTile.bytes} bytes)` : "n/a"}`);
  console.log(`  datasetId:   ${manifest.datasetId}`);
}

const isMainModule = process.argv[1] !== undefined && import.meta.url === pathToFileURL(process.argv[1]).href;
if (isMainModule) {
  main().catch((error) => {
    console.error(error?.stack || error?.message || String(error));
    process.exitCode = 1;
  });
}
