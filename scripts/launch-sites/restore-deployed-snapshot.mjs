#!/usr/bin/env node

// Restores the launch-site snapshot that the deployed site is currently serving.
//
// The deploy normally rebuilds the snapshot from the live ParaglidingEarth dataset. That service is
// run by volunteers and has been unavailable for weeks at a time; while it was, no deploy could
// succeed, including deploys of unrelated fixes. The site itself is the durable copy of the last
// good dataset: every deploy publishes the snapshot under data/launch-sites/. This tool downloads
// that published snapshot, checks every tile against the manifest (size and SHA-256), and writes it
// where the web build expects it. The manifest keeps its original `generatedAt`, so the age of the
// dataset stays visible and check-deployed-freshness.mjs can report a dataset that is not refreshed.
//
// Nothing is written unless the whole snapshot was downloaded and verified.

import path from "node:path";
import crypto from "node:crypto";
import process from "node:process";
import { pathToFileURL } from "node:url";

import { writeSnapshot } from "./build-snapshot.mjs";

const USER_AGENT =
  "CloudbasePredictor/launch-sites-snapshot (+https://github.com/CloudbasePredictor/CloudbasePredictor)";
const MAX_MANIFEST_BYTES = 5 * 1024 * 1024;
const MAX_TILE_BYTES = 5_000_000;
const MAX_TILES = 16_200; // 90 x 180 tiles of 2 x 2 degrees
const TILE_PATH_PATTERN = /^tiles\/\d{1,3}\/\d{1,3}\.json$/u;
const SHA256_PATTERN = /^[0-9a-f]{64}$/u;
const DAY_MILLIS = 24 * 60 * 60 * 1000;

const DEFAULTS = Object.freeze({
  concurrency: 8,
  attempts: 3,
  timeoutMillis: 30_000,
  retryDelayMillis: 1000,
});

function sleep(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

function isPositiveInteger(value) {
  return Number.isInteger(value) && value > 0;
}

/**
 * Checks that a downloaded manifest is safe to act on before any tile is requested: the tile paths
 * are used to build both request URLs and file paths, so they must match the builder's own layout.
 * Throws with every problem found.
 */
export function validateManifest(manifest) {
  const errors = [];
  if (!manifest || typeof manifest !== "object" || Array.isArray(manifest)) {
    throw new Error("Deployed manifest is not a JSON object");
  }
  if (manifest.schemaVersion !== 1) {
    errors.push(`schemaVersion must be 1, got ${JSON.stringify(manifest.schemaVersion)}`);
  }
  if (typeof manifest.generatedAt !== "string" || Number.isNaN(Date.parse(manifest.generatedAt))) {
    errors.push(`generatedAt must be an ISO timestamp, got ${JSON.stringify(manifest.generatedAt)}`);
  }
  const tiles = Array.isArray(manifest.tiles) ? manifest.tiles : null;
  if (!tiles || tiles.length === 0) {
    errors.push("tiles must be a non-empty array");
  } else if (tiles.length > MAX_TILES) {
    errors.push(`tiles has ${tiles.length} entries, more than the ${MAX_TILES} a 2 degree grid can hold`);
  } else {
    const seenPaths = new Set();
    for (const tile of tiles) {
      const tilePath = tile?.path;
      if (typeof tilePath !== "string" || !TILE_PATH_PATTERN.test(tilePath)) {
        errors.push(`tile path is not of the form tiles/<lat>/<lon>.json: ${JSON.stringify(tilePath)}`);
        continue;
      }
      if (seenPaths.has(tilePath)) errors.push(`tile path is listed twice: ${tilePath}`);
      seenPaths.add(tilePath);
      if (!isPositiveInteger(tile.bytes) || tile.bytes > MAX_TILE_BYTES) {
        errors.push(`tile ${tilePath} has an invalid size: ${JSON.stringify(tile.bytes)}`);
      }
      if (typeof tile.sha256 !== "string" || !SHA256_PATTERN.test(tile.sha256)) {
        errors.push(`tile ${tilePath} has an invalid sha256`);
      }
    }
  }
  if (errors.length > 0) {
    throw new Error(`Deployed manifest is not usable:\n  - ${errors.slice(0, 20).join("\n  - ")}`);
  }
  return manifest;
}

/** Whole days between the manifest's `generatedAt` and `nowMillis`, or null when it has no valid date. */
export function snapshotAgeDays(manifest, nowMillis = Date.now()) {
  const generatedAtMillis = Date.parse(manifest?.generatedAt ?? "");
  if (Number.isNaN(generatedAtMillis)) return null;
  return Math.max(0, Math.floor((nowMillis - generatedAtMillis) / DAY_MILLIS));
}

function normalizeBaseUrl(baseUrl) {
  let url;
  try {
    url = new URL(baseUrl);
  } catch {
    throw new Error(`--base-url is not a valid URL: ${JSON.stringify(baseUrl)}`);
  }
  const isLoopback = url.hostname === "127.0.0.1" || url.hostname === "localhost";
  if (url.protocol !== "https:" && !(url.protocol === "http:" && isLoopback)) {
    throw new Error(`--base-url must use https (http is allowed for localhost only): ${baseUrl}`);
  }
  return url.href.replace(/\/+$/u, "");
}

/**
 * Downloads one file with a timeout, a size limit and retries. Retries cover network errors and
 * 5xx/429 answers; any other HTTP status fails immediately because retrying cannot fix it.
 */
async function fetchBytes(url, maxBytes, options) {
  let lastError;
  for (let attempt = 1; attempt <= options.attempts; attempt += 1) {
    try {
      const response = await options.fetchImpl(url, {
        cache: "no-store",
        headers: { "User-Agent": USER_AGENT, "Cache-Control": "no-cache" },
        signal: AbortSignal.timeout(options.timeoutMillis),
      });
      if (!response.ok) {
        const error = new Error(`HTTP ${response.status} for ${url}`);
        error.retryable = response.status >= 500 || response.status === 429;
        throw error;
      }
      const bytes = Buffer.from(await response.arrayBuffer());
      if (bytes.byteLength > maxBytes) {
        const error = new Error(`${url} is ${bytes.byteLength} bytes, above the limit of ${maxBytes}`);
        error.retryable = false;
        throw error;
      }
      return bytes;
    } catch (error) {
      lastError = error;
      if (error.retryable === false || attempt === options.attempts) break;
      await sleep(options.retryDelayMillis * 2 ** (attempt - 1));
    }
  }
  throw new Error(`Failed to download ${url}: ${lastError?.message ?? lastError}`);
}

/**
 * Downloads and verifies the snapshot published under `baseUrl`. Returns `{ manifest, tiles }` in the
 * shape `writeSnapshot` accepts. Throws when the manifest is unusable or any tile is missing or does
 * not match its manifest entry.
 */
export async function restoreSnapshot(baseUrl, overrides = {}) {
  const options = { ...DEFAULTS, fetchImpl: globalThis.fetch, ...overrides };
  const base = normalizeBaseUrl(baseUrl);

  const manifestBytes = await fetchBytes(`${base}/manifest.json`, MAX_MANIFEST_BYTES, options);
  let manifest;
  try {
    manifest = JSON.parse(manifestBytes.toString("utf8"));
  } catch (error) {
    throw new Error(`${base}/manifest.json is not valid JSON: ${error.message}`);
  }
  validateManifest(manifest);

  const tiles = new Array(manifest.tiles.length);
  let nextIndex = 0;
  let failed = false;
  async function downloadTile(tileMeta) {
    const bytes = await fetchBytes(`${base}/${tileMeta.path}`, MAX_TILE_BYTES, options);
    if (bytes.byteLength !== tileMeta.bytes) {
      throw new Error(
        `Tile ${tileMeta.path} is ${bytes.byteLength} bytes, but the manifest says ${tileMeta.bytes}`,
      );
    }
    const sha256 = crypto.createHash("sha256").update(bytes).digest("hex");
    if (sha256 !== tileMeta.sha256) {
      throw new Error(`Tile ${tileMeta.path} does not match the sha256 in the manifest`);
    }
    return { path: tileMeta.path, json: bytes.toString("utf8") };
  }
  async function worker() {
    // One bad tile makes the whole snapshot unusable, so the other workers stop asking for more.
    while (!failed) {
      const index = nextIndex;
      nextIndex += 1;
      if (index >= manifest.tiles.length) return;
      try {
        tiles[index] = await downloadTile(manifest.tiles[index]);
      } catch (error) {
        failed = true;
        throw error;
      }
    }
  }
  const workerCount = Math.max(1, Math.min(options.concurrency, manifest.tiles.length));
  await Promise.all(Array.from({ length: workerCount }, worker));

  return { manifest, tiles };
}

function parseArgs(argv) {
  const args = { baseUrl: undefined, output: undefined };
  for (let i = 0; i < argv.length; i += 1) {
    const arg = argv[i];
    if (arg === "--base-url") {
      args.baseUrl = argv[(i += 1)];
    } else if (arg === "--output") {
      args.output = argv[(i += 1)];
    } else {
      throw new Error(`Unknown argument: ${arg}`);
    }
  }
  if (!args.baseUrl) throw new Error("Missing required --base-url <url> argument");
  if (!args.output) throw new Error("Missing required --output <dir> argument");
  return args;
}

async function main() {
  const args = parseArgs(process.argv.slice(2));
  const restored = await restoreSnapshot(args.baseUrl);
  const outputDir = path.resolve(args.output);
  await writeSnapshot(outputDir, restored);

  const { manifest } = restored;
  const ageDays = snapshotAgeDays(manifest);
  const summary =
    `Reused the deployed launch-site snapshot: ${manifest.siteCount} sites across ${manifest.tiles.length} tiles, ` +
    `generated ${manifest.generatedAt} (${ageDays} day(s) ago), datasetId=${manifest.datasetId}`;
  console.log(`${summary}\nWritten to ${outputDir}`);
  if (process.env.GITHUB_ACTIONS === "true") {
    // Surfaces on the run summary page, so a deploy that did not refresh the dataset is not silent.
    console.log(`::warning title=Launch-site dataset was not refreshed::${summary}`);
  }
}

const isMainModule = process.argv[1] !== undefined && import.meta.url === pathToFileURL(process.argv[1]).href;
if (isMainModule) {
  main().catch((error) => {
    console.error(error?.stack || error?.message || String(error));
    process.exitCode = 1;
  });
}
