import { test } from "node:test";
import assert from "node:assert/strict";
import { createServer } from "node:http";
import { mkdtemp, readFile, readdir, rm } from "node:fs/promises";
import path from "node:path";
import os from "node:os";
import { fileURLToPath } from "node:url";

import { buildSnapshot, writeSnapshot } from "./build-snapshot.mjs";
import { restoreSnapshot, snapshotAgeDays, validateManifest } from "./restore-deployed-snapshot.mjs";
import { verifySnapshot } from "./verify-snapshot.mjs";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const FIXTURE_PATH = path.join(__dirname, "fixtures", "sample.geojson");
const FAST = { retryDelayMillis: 1, timeoutMillis: 5000 };

async function builtFixture() {
  return buildSnapshot(JSON.parse(await readFile(FIXTURE_PATH, "utf8")));
}

/**
 * Serves a built snapshot the way the deployed site does. `intercept(requestPath, hitCount)` may
 * return `{ status, body }` to replace the answer for a path.
 */
async function serveSnapshot(built, intercept = () => null) {
  const files = new Map([["/manifest.json", `${JSON.stringify(built.manifest, null, 2)}\n`]]);
  for (const tile of built.tiles) files.set(`/${tile.path}`, tile.json);
  const hits = new Map();

  const server = createServer((request, response) => {
    const requestPath = new URL(request.url, "http://localhost").pathname;
    const hitCount = (hits.get(requestPath) ?? 0) + 1;
    hits.set(requestPath, hitCount);
    const override = intercept(requestPath, hitCount);
    if (override) {
      response.writeHead(override.status, { "Content-Type": "text/html" });
      response.end(override.body ?? "");
      return;
    }
    const body = files.get(requestPath);
    if (body === undefined) {
      response.writeHead(404);
      response.end("not found");
      return;
    }
    response.writeHead(200, { "Content-Type": "application/json" });
    response.end(body);
  });
  await new Promise((resolve) => server.listen(0, "127.0.0.1", resolve));
  return {
    baseUrl: `http://127.0.0.1:${server.address().port}`,
    hits,
    close: () =>
      new Promise((resolve) => {
        server.close(resolve);
        // Do not wait for idle keep-alive connections of the client.
        server.closeAllConnections();
      }),
  };
}

test("restores a published snapshot that is byte-identical and passes verification", async () => {
  const built = await builtFixture();
  const site = await serveSnapshot(built);
  const base = await mkdtemp(path.join(os.tmpdir(), "launch-sites-restore-"));
  const outputDir = path.join(base, "snapshot");
  try {
    const restored = await restoreSnapshot(`${site.baseUrl}/`, FAST);
    await writeSnapshot(outputDir, restored);

    assert.equal(restored.manifest.generatedAt, built.manifest.generatedAt, "the dataset date must survive");
    assert.equal(restored.manifest.datasetId, built.manifest.datasetId);
    for (const tile of built.tiles) {
      assert.equal(await readFile(path.join(outputDir, ...tile.path.split("/")), "utf8"), tile.json);
    }
    const verification = await verifySnapshot(outputDir, { minSites: built.manifest.siteCount });
    assert.deepEqual(verification, { ok: true, errors: [] });
  } finally {
    await site.close();
    await rm(base, { recursive: true, force: true });
  }
});

test("retries a tile that fails with a server error and then succeeds", async () => {
  const built = await builtFixture();
  const flakyPath = `/${built.tiles[0].path}`;
  const site = await serveSnapshot(built, (requestPath, hitCount) =>
    requestPath === flakyPath && hitCount < 3 ? { status: 503, body: "maintenance" } : null,
  );
  try {
    const restored = await restoreSnapshot(site.baseUrl, FAST);
    assert.equal(restored.tiles.length, built.tiles.length);
    assert.equal(site.hits.get(flakyPath), 3);
  } finally {
    await site.close();
  }
});

test("fails when a tile does not match the manifest, without retrying forever", async () => {
  const built = await builtFixture();
  const corruptPath = `/${built.tiles[0].path}`;
  const sameLengthGarbage = "x".repeat(Buffer.byteLength(built.tiles[0].json, "utf8"));
  const site = await serveSnapshot(built, (requestPath) =>
    requestPath === corruptPath ? { status: 200, body: sameLengthGarbage } : null,
  );
  try {
    await assert.rejects(restoreSnapshot(site.baseUrl, FAST), /does not match the sha256/u);
  } finally {
    await site.close();
  }
});

test("fails when a tile is missing from the site, and does not retry a 404", async () => {
  const built = await builtFixture();
  const missingPath = `/${built.tiles[0].path}`;
  const site = await serveSnapshot(built, (requestPath) =>
    requestPath === missingPath ? { status: 404, body: "" } : null,
  );
  try {
    await assert.rejects(restoreSnapshot(site.baseUrl, FAST), /HTTP 404/u);
    assert.equal(site.hits.get(missingPath), 1);
  } finally {
    await site.close();
  }
});

test("fails when the site answers with a maintenance page instead of a manifest", async () => {
  const built = await builtFixture();
  const site = await serveSnapshot(built, (requestPath) =>
    requestPath === "/manifest.json" ? { status: 200, body: "<!DOCTYPE html><title>Site en maintenance</title>" } : null,
  );
  try {
    await assert.rejects(restoreSnapshot(site.baseUrl, FAST), /not valid JSON/u);
  } finally {
    await site.close();
  }
});

test("gives up after the configured attempts when the manifest keeps failing", async () => {
  const built = await builtFixture();
  const site = await serveSnapshot(built, (requestPath) =>
    requestPath === "/manifest.json" ? { status: 503, body: "" } : null,
  );
  try {
    await assert.rejects(restoreSnapshot(site.baseUrl, { ...FAST, attempts: 2 }), /HTTP 503/u);
    assert.equal(site.hits.get("/manifest.json"), 2);
  } finally {
    await site.close();
  }
});

test("a failed restore leaves an existing snapshot directory untouched", async () => {
  const built = await builtFixture();
  const base = await mkdtemp(path.join(os.tmpdir(), "launch-sites-restore-"));
  const outputDir = path.join(base, "snapshot");
  await writeSnapshot(outputDir, built);
  const before = (await readdir(outputDir, { recursive: true })).sort();
  const site = await serveSnapshot(built, (requestPath) =>
    requestPath === `/${built.tiles[0].path}` ? { status: 404, body: "" } : null,
  );
  try {
    await assert.rejects(async () => writeSnapshot(outputDir, await restoreSnapshot(site.baseUrl, FAST)));
    assert.deepEqual((await readdir(outputDir, { recursive: true })).sort(), before);
  } finally {
    await site.close();
    await rm(base, { recursive: true, force: true });
  }
});

test("rejects manifests whose tile paths could escape the snapshot directory", async () => {
  const built = await builtFixture();
  const withPath = (tilePath) => ({
    ...built.manifest,
    tiles: [{ ...built.manifest.tiles[0], path: tilePath }],
  });

  for (const tilePath of ["../../etc/passwd", "tiles/../../x.json", "/tiles/1/2.json", "tiles/1/2.json?x", "https://example.org/a.json"]) {
    assert.throws(() => validateManifest(withPath(tilePath)), /tile path is not of the form/u, tilePath);
  }
  assert.doesNotThrow(() => validateManifest(withPath("tiles/67/93.json")));
});

test("rejects manifests with a wrong schema, no tiles, bad sizes or bad hashes", async () => {
  const built = await builtFixture();
  const firstTile = built.manifest.tiles[0];

  assert.throws(() => validateManifest(null), /not a JSON object/u);
  assert.throws(() => validateManifest({ ...built.manifest, schemaVersion: 2 }), /schemaVersion must be 1/u);
  assert.throws(() => validateManifest({ ...built.manifest, generatedAt: "yesterday" }), /generatedAt/u);
  assert.throws(() => validateManifest({ ...built.manifest, tiles: [] }), /non-empty array/u);
  assert.throws(
    () => validateManifest({ ...built.manifest, tiles: [{ ...firstTile, bytes: 50_000_000 }] }),
    /invalid size/u,
  );
  assert.throws(
    () => validateManifest({ ...built.manifest, tiles: [{ ...firstTile, sha256: "abc" }] }),
    /invalid sha256/u,
  );
  assert.throws(
    () => validateManifest({ ...built.manifest, tiles: [firstTile, firstTile] }),
    /listed twice/u,
  );
});

test("only https base URLs are accepted, except for localhost", async () => {
  await assert.rejects(restoreSnapshot("http://example.org/data/launch-sites", FAST), /must use https/u);
  await assert.rejects(restoreSnapshot("file:///etc", FAST), /must use https/u);
  await assert.rejects(restoreSnapshot("not a url", FAST), /not a valid URL/u);
});

test("snapshotAgeDays counts whole days since generatedAt", () => {
  const now = Date.parse("2026-11-14T12:00:00Z");
  assert.equal(snapshotAgeDays({ generatedAt: "2026-11-14T11:00:00Z" }, now), 0);
  assert.equal(snapshotAgeDays({ generatedAt: "2026-10-19T08:55:00Z" }, now), 26);
  assert.equal(snapshotAgeDays({ generatedAt: "2026-11-15T00:00:00Z" }, now), 0, "clock skew is not negative age");
  assert.equal(snapshotAgeDays({ generatedAt: "not a date" }, now), null);
  assert.equal(snapshotAgeDays({}, now), null);
});
