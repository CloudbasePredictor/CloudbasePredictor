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

const launchSiteDirectory = (config.launchSiteData?.directory ?? "data/launch-sites").replace(/\/+$/u, "");
const isLaunchSiteFile = (posixPath) =>
  posixPath === launchSiteDirectory || posixPath.startsWith(`${launchSiteDirectory}/`);
const appFiles = fileMetrics.filter((file) => !isLaunchSiteFile(file.path));
const launchSiteFiles = fileMetrics.filter((file) => isLaunchSiteFile(file.path));

// Application budgets exclude the static launch-site dataset so it can never inflate the app limits.
const distributionBytes = sum(appFiles.map((file) => file.bytes));
const distributionGzipBytes = sum(appFiles.map((file) => file.gzipBytes));
const launchSiteRawBytes = sum(launchSiteFiles.map((file) => file.bytes));
const launchSiteGzipBytes = sum(launchSiteFiles.map((file) => file.gzipBytes));
const launchSiteMetrics = summarizeLaunchSiteData(distDirectory, launchSiteDirectory, launchSiteFiles);
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

const launchConfig = config.launchSiteData ?? {};
if (launchSiteFiles.length > 0) {
  if (launchConfig.minimumSiteCount > 0) {
    check(
      failures,
      `launch-site dataset has >= ${launchConfig.minimumSiteCount} sites`,
      launchSiteMetrics.siteCount >= launchConfig.minimumSiteCount,
      String(launchSiteMetrics.siteCount),
    );
  }
  if (launchConfig.maximumRawBytes > 0) {
    check(
      failures,
      `launch-site data size <= ${formatBytes(launchConfig.maximumRawBytes)}`,
      launchSiteRawBytes <= launchConfig.maximumRawBytes,
      formatBytes(launchSiteRawBytes),
    );
  }
  if (launchConfig.maximumGzipBytes > 0) {
    check(
      failures,
      `launch-site data gzip size <= ${formatBytes(launchConfig.maximumGzipBytes)}`,
      launchSiteGzipBytes <= launchConfig.maximumGzipBytes,
      formatBytes(launchSiteGzipBytes),
    );
  }
  if (launchConfig.maximumTileBytes > 0) {
    check(
      failures,
      `largest launch-site tile <= ${formatBytes(launchConfig.maximumTileBytes)}`,
      launchSiteMetrics.largestTileBytes <= launchConfig.maximumTileBytes,
      formatBytes(launchSiteMetrics.largestTileBytes),
    );
  }
}

const report = {
  schemaVersion: 1,
  config: toPosix(relative(repositoryRoot, configPath)),
  distribution: toPosix(relative(repositoryRoot, distDirectory)),
  distributionBytes,
  distributionGzipBytes,
  launchSiteData: {
    directory: launchSiteDirectory,
    present: launchSiteFiles.length > 0,
    rawBytes: launchSiteRawBytes,
    gzipBytes: launchSiteGzipBytes,
    siteCount: launchSiteMetrics.siteCount,
    tileCount: launchSiteMetrics.tileCount,
    largestTileBytes: launchSiteMetrics.largestTileBytes,
    largestTilePath: launchSiteMetrics.largestTilePath,
    datasetId: launchSiteMetrics.datasetId,
  },
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

console.log(`Application: ${formatBytes(distributionBytes)} raw, ${formatBytes(distributionGzipBytes)} gzip`);
if (launchSiteFiles.length > 0) {
  console.log(
    `Launch-site data: ${formatBytes(launchSiteRawBytes)} raw, ${formatBytes(launchSiteGzipBytes)} gzip ` +
      `(${launchSiteMetrics.siteCount} sites, ${launchSiteMetrics.tileCount} tiles, ` +
      `largest ${formatBytes(launchSiteMetrics.largestTileBytes)}, dataset ${launchSiteMetrics.datasetId || "?"})`,
  );
} else {
  console.log("Launch-site data: not present (generated in CI before the production build).");
}
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

function summarizeLaunchSiteData(distDir, directory, launchFiles) {
  const manifestRelative = `${directory}/manifest.json`;
  const tileFiles = launchFiles.filter(
    (file) => file.path !== manifestRelative && file.path.endsWith(".json"),
  );
  const largestTile = tileFiles.reduce(
    (largest, file) => (file.bytes > largest.bytes ? file : largest),
    { path: "", bytes: 0 },
  );
  let siteCount = 0;
  let datasetId = "";
  const manifestPath = join(distDir, directory, "manifest.json");
  if (existsSync(manifestPath)) {
    try {
      const manifest = JSON.parse(readFileSync(manifestPath, "utf8"));
      siteCount = Number(manifest.siteCount) || 0;
      datasetId = String(manifest.datasetId ?? "");
    } catch {
      siteCount = 0;
    }
  }
  return {
    siteCount,
    datasetId,
    tileCount: tileFiles.length,
    largestTileBytes: largestTile.bytes,
    largestTilePath: largestTile.path,
  };
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
