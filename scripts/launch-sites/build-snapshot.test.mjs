import { test } from "node:test";
import assert from "node:assert/strict";
import { mkdir, mkdtemp, readFile, rm, writeFile } from "node:fs/promises";
import path from "node:path";
import os from "node:os";
import crypto from "node:crypto";
import { fileURLToPath } from "node:url";

import { buildSnapshot, writeSnapshot } from "./build-snapshot.mjs";
import { verifySnapshot } from "./verify-snapshot.mjs";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const FIXTURE_PATH = path.join(__dirname, "fixtures", "sample.geojson");

async function loadFixture() {
  const raw = await readFile(FIXTURE_PATH, "utf8");
  return JSON.parse(raw);
}

async function makeTempDir() {
  return mkdtemp(path.join(os.tmpdir(), "launch-sites-snapshot-"));
}

async function buildIntoTempDir() {
  const base = await makeTempDir();
  const outputDir = path.join(base, "snapshot");
  const featureCollection = await loadFixture();
  const built = buildSnapshot(featureCollection);
  await writeSnapshot(outputDir, built);
  return { base, outputDir, built };
}

function stableIdOf(feature) {
  return feature.properties?.pge_site_id ?? feature.id;
}

test("building the same input twice yields an identical datasetId and byte-identical tile files", async () => {
  const featureCollection = await loadFixture();
  const base = await makeTempDir();
  const outputDirA = path.join(base, "a");
  const outputDirB = path.join(base, "b");

  const builtA = buildSnapshot(featureCollection);
  const builtB = buildSnapshot(featureCollection);
  assert.equal(builtA.manifest.datasetId, builtB.manifest.datasetId);
  assert.equal(builtA.tiles.length, builtB.tiles.length);

  await writeSnapshot(outputDirA, builtA);
  await writeSnapshot(outputDirB, builtB);

  for (const tile of builtA.tiles) {
    const bytesA = await readFile(path.join(outputDirA, ...tile.path.split("/")));
    const bytesB = await readFile(path.join(outputDirB, ...tile.path.split("/")));
    assert.deepEqual(bytesA, bytesB, `tile ${tile.path} bytes should be identical across builds`);
  }
});

test("assigns sites to the expected 2x2 degree tiles", async () => {
  const featureCollection = await loadFixture();
  const built = buildSnapshot(featureCollection);

  const tileByStableId = new Map();
  for (const tile of built.tiles) {
    for (const feature of tile.features) {
      tileByStableId.set(String(stableIdOf(feature)), tile.path);
    }
  }

  assert.equal(tileByStableId.get("1001"), "tiles/68/93.json");
  assert.equal(tileByStableId.get("2001"), "tiles/71/96.json");
  assert.equal(tileByStableId.get("geojson-3"), "tiles/68/94.json");
  assert.equal(tileByStableId.get("5001"), "tiles/68/95.json");
});

test("deduplicates repeated pge_site_id, keeping the first occurrence in input order", async () => {
  const featureCollection = await loadFixture();
  const built = buildSnapshot(featureCollection);

  const tile6893 = built.tiles.find((tile) => tile.path === "tiles/68/93.json");
  assert.ok(tile6893, "tile 68/93 should exist");
  assert.equal(tile6893.features.length, 2);

  const site1001 = tile6893.features.find((feature) => feature.properties.pge_site_id === "1001");
  assert.ok(site1001, "site 1001 should be present");
  assert.equal(site1001.properties.name, "Beatenberg");

  assert.equal(built.manifest.siteCount, 6);
});

test("drops features with invalid or missing coordinates", async () => {
  const featureCollection = await loadFixture();
  const built = buildSnapshot(featureCollection);

  const allStableIds = built.tiles.flatMap((tile) => tile.features.map((feature) => String(stableIdOf(feature))));

  assert.ok(!allStableIds.includes("9999"), "out-of-range coordinate feature must be dropped");
  assert.ok(!allStableIds.includes("9998"), "null geometry feature must be dropped");
  assert.equal(allStableIds.length, 6);
});

test("tile sha256 hashes are correct and tile siteCounts sum to manifest.siteCount", async () => {
  const { built } = await buildIntoTempDir();

  let sum = 0;
  for (const tile of built.tiles) {
    const recomputed = crypto.createHash("sha256").update(tile.json, "utf8").digest("hex");
    assert.equal(tile.sha256, recomputed, `tile ${tile.path} sha256 should match its content`);
    sum += tile.siteCount;
  }
  assert.equal(sum, built.manifest.siteCount);
});

test("write is atomic: a stale file from a previous snapshot does not survive a rebuild", async () => {
  const base = await makeTempDir();
  const outputDir = path.join(base, "snapshot");

  await mkdir(path.join(outputDir, "tiles", "0"), { recursive: true });
  await writeFile(path.join(outputDir, "tiles", "0", "0.json"), '{"features":[]}\n', "utf8");

  const featureCollection = await loadFixture();
  const built = buildSnapshot(featureCollection);
  await writeSnapshot(outputDir, built);

  await assert.rejects(() => readFile(path.join(outputDir, "tiles", "0", "0.json")));
  // And the fresh snapshot content is actually present.
  const manifest = JSON.parse(await readFile(path.join(outputDir, "manifest.json"), "utf8"));
  assert.equal(manifest.siteCount, 6);
});

test("verifySnapshot reports ok:true for a freshly built snapshot", async () => {
  const { outputDir } = await buildIntoTempDir();
  const result = await verifySnapshot(outputDir);
  assert.deepEqual(result, { ok: true, errors: [] });
});

test("verifySnapshot reports ok:false with an sha256/size error when a tile file is corrupted", async () => {
  const { outputDir, built } = await buildIntoTempDir();
  const firstTile = built.tiles[0];
  const tileFilePath = path.join(outputDir, ...firstTile.path.split("/"));
  await writeFile(tileFilePath, "this is not the original tile content", "utf8");

  const result = await verifySnapshot(outputDir);
  assert.equal(result.ok, false);
  assert.ok(
    result.errors.some((message) => /sha256/i.test(message) || /size/i.test(message)),
    `expected an sha256/size error, got: ${JSON.stringify(result.errors)}`,
  );
});

test("verifySnapshot reports ok:false with a missing-file error when a tile file is deleted", async () => {
  const { outputDir, built } = await buildIntoTempDir();
  const firstTile = built.tiles[0];
  const tileFilePath = path.join(outputDir, ...firstTile.path.split("/"));
  await rm(tileFilePath);

  const result = await verifySnapshot(outputDir);
  assert.equal(result.ok, false);
  assert.ok(
    result.errors.some((message) => /missing/i.test(message)),
    `expected a missing-file error, got: ${JSON.stringify(result.errors)}`,
  );
});
