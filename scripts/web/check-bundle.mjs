#!/usr/bin/env node

import { createReadStream, existsSync, mkdirSync, readFileSync, statSync, writeFileSync } from "node:fs";
import { readdir } from "node:fs/promises";
import { dirname, extname, isAbsolute, join, relative, resolve, sep } from "node:path";
import { fileURLToPath } from "node:url";
import { createGzip } from "node:zlib";

const repositoryRoot = resolve(dirname(fileURLToPath(import.meta.url)), "../..");
const configPath = resolve(repositoryRoot, process.argv[2] ?? "config/web-release-gates.json");
const config = JSON.parse(readFileSync(configPath, "utf8"));
const distDirectory = resolveFromRepository(config.distDirectory);
const reportPath = resolve(
  repositoryRoot,
  process.env.WEB_RELEASE_REPORT_DIR ?? "webApp/build/reports/release-gates",
  "bundle.json",
);
const COMPRESSIBLE_EXTENSIONS = new Set([
  ".css",
  ".html",
  ".js",
  ".json",
  ".map",
  ".mjs",
  ".svg",
  ".wasm",
  ".webmanifest",
]);

if (!existsSync(distDirectory)) {
  fail(`Production distribution does not exist: ${relative(repositoryRoot, distDirectory)}`);
}

const files = await listFiles(distDirectory);
if (files.length === 0) {
  fail(`Production distribution is empty: ${relative(repositoryRoot, distDirectory)}`);
}

const fileMetrics = [];
for (const file of files) {
  const bytes = statSync(file).size;
  const gzipBytes = COMPRESSIBLE_EXTENSIONS.has(extname(file))
    ? await compressedSize(file)
    : bytes;
  fileMetrics.push({
    path: toPosix(relative(distDirectory, file)),
    bytes,
    gzipBytes,
  });
}

const distributionBytes = sum(fileMetrics.map((file) => file.bytes));
const distributionGzipBytes = sum(fileMetrics.map((file) => file.gzipBytes));
const directInitialScripts = directScriptAssets(distDirectory);
const markerHits = findForbiddenMarkers(
  directInitialScripts,
  config.bundle.forbiddenInitialJavaScriptMarkers ?? [],
);
const failures = [];

check(
  failures,
  "production index exists",
  fileMetrics.some((file) => file.path === "index.html"),
);
check(
  failures,
  "production JavaScript exists",
  fileMetrics.some((file) => extname(file.path) === ".js"),
);
check(
  failures,
  "production WebAssembly exists",
  fileMetrics.some((file) => extname(file.path) === ".wasm"),
);
check(
  failures,
  `distribution size <= ${formatBytes(config.bundle.maximumDistributionBytes)}`,
  distributionBytes <= config.bundle.maximumDistributionBytes,
  formatBytes(distributionBytes),
);
check(
  failures,
  `distribution gzip size <= ${formatBytes(config.bundle.maximumDistributionGzipBytes)}`,
  distributionGzipBytes <= config.bundle.maximumDistributionGzipBytes,
  formatBytes(distributionGzipBytes),
);
check(
  failures,
  "MapLibre is absent from directly loaded JavaScript",
  markerHits.length === 0,
  markerHits.join(", "),
);

const report = {
  schemaVersion: 1,
  config: toPosix(relative(repositoryRoot, configPath)),
  distribution: toPosix(relative(repositoryRoot, distDirectory)),
  distributionBytes,
  distributionGzipBytes,
  directInitialScripts: directInitialScripts.map((file) => toPosix(relative(distDirectory, file))),
  markerHits,
  largestFiles: [...fileMetrics]
    .sort((left, right) => right.bytes - left.bytes)
    .slice(0, 20),
  files: fileMetrics,
  passed: failures.length === 0,
  failures,
};
mkdirSync(dirname(reportPath), { recursive: true });
writeFileSync(reportPath, `${JSON.stringify(report, null, 2)}\n`);

console.log(`Distribution: ${formatBytes(distributionBytes)} raw, ${formatBytes(distributionGzipBytes)} gzip`);
console.log(`Report: ${toPosix(relative(repositoryRoot, reportPath))}`);
if (failures.length > 0) {
  console.error(`Bundle gate failed:\n- ${failures.join("\n- ")}`);
  process.exitCode = 1;
} else {
  console.log("Bundle gate passed.");
}

function resolveFromRepository(path) {
  return isAbsolute(path) ? resolve(path) : resolve(repositoryRoot, path);
}

async function listFiles(directory) {
  const result = [];
  for (const entry of await readdir(directory, { withFileTypes: true })) {
    const path = join(directory, entry.name);
    if (entry.isDirectory()) {
      result.push(...await listFiles(path));
    } else if (entry.isFile()) {
      result.push(path);
    }
  }
  return result.sort();
}

function compressedSize(file) {
  return new Promise((resolveSize, reject) => {
    let bytes = 0;
    const gzip = createGzip({ level: 9 });
    gzip.on("data", (chunk) => {
      bytes += chunk.length;
    });
    gzip.on("end", () => resolveSize(bytes));
    gzip.on("error", reject);
    createReadStream(file).on("error", reject).pipe(gzip);
  });
}

function directScriptAssets(directory) {
  const indexPath = join(directory, "index.html");
  if (!existsSync(indexPath)) return [];
  const html = readFileSync(indexPath, "utf8");
  const scripts = [];
  const sourcePattern = /<script\b[^>]*\bsrc\s*=\s*["']([^"']+)["'][^>]*>/giu;
  for (const match of html.matchAll(sourcePattern)) {
    const source = match[1];
    if (/^(?:[a-z]+:)?\/\//iu.test(source) || source.startsWith("data:")) continue;
    const resolved = resolve(directory, source.replace(/^\.\//u, "").replace(/^\//u, ""));
    if (isInside(directory, resolved) && existsSync(resolved)) scripts.push(resolved);
  }
  return [...new Set(scripts)];
}

function findForbiddenMarkers(scripts, markers) {
  const hits = [];
  for (const script of scripts) {
    const contents = readFileSync(script, "utf8");
    for (const marker of markers) {
      if (contents.includes(marker)) {
        hits.push(`${toPosix(relative(distDirectory, script))}: ${marker}`);
      }
    }
  }
  return hits;
}

function isInside(parent, child) {
  const path = relative(parent, child);
  return path !== ".." && !path.startsWith(`..${sep}`) && !isAbsolute(path);
}

function check(failures, label, passed, detail = "") {
  console.log(`${passed ? "PASS" : "FAIL"}  ${label}${detail ? ` (${detail})` : ""}`);
  if (!passed) failures.push(`${label}${detail ? `: ${detail}` : ""}`);
}

function sum(values) {
  return values.reduce((total, value) => total + value, 0);
}

function formatBytes(bytes) {
  if (bytes < 1000) return `${bytes} B`;
  if (bytes < 1_000_000) return `${(bytes / 1000).toFixed(1)} kB`;
  return `${(bytes / 1_000_000).toFixed(2)} MB`;
}

function toPosix(path) {
  return path.split(sep).join("/");
}

function fail(message) {
  console.error(message);
  process.exit(1);
}
