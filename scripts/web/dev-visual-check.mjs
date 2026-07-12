#!/usr/bin/env node

import { spawn, spawnSync } from "node:child_process";
import { createServer } from "node:http";
import {
  existsSync,
  mkdirSync,
  readFileSync,
  statSync,
  writeFileSync,
} from "node:fs";
import { dirname, extname, isAbsolute, join, relative, resolve, sep } from "node:path";
import { fileURLToPath } from "node:url";
import { chromium } from "playwright";

const repositoryRoot = resolve(dirname(fileURLToPath(import.meta.url)), "../..");
const defaultDistDirectory = "webApp/build/dist/wasmJs/productionExecutable";
const defaultOutputDirectory = "webApp/build/reports/visual-check";
const CONTENT_TYPES = new Map([
  [".css", "text/css; charset=utf-8"],
  [".html", "text/html; charset=utf-8"],
  [".js", "text/javascript; charset=utf-8"],
  [".json", "application/json; charset=utf-8"],
  [".mjs", "text/javascript; charset=utf-8"],
  [".png", "image/png"],
  [".svg", "image/svg+xml"],
  [".wasm", "application/wasm"],
  [".webmanifest", "application/manifest+json; charset=utf-8"],
]);
const profiles = [
  {
    id: "desktop",
    viewport: { width: 1280, height: 900 },
    deviceScaleFactor: 1,
    isMobile: false,
    hasTouch: false,
  },
  {
    id: "phone",
    viewport: { width: 390, height: 844 },
    deviceScaleFactor: 3,
    isMobile: true,
    hasTouch: true,
  },
];

const options = parseArguments(process.argv.slice(2));
if (options.help) {
  printUsage();
  process.exit(0);
}

ensureVirtualDisplay();

const outputDirectory = resolveFromRepository(options.outputDirectory);
const reportPath = join(outputDirectory, "report.json");
const results = [];
let staticServer;
let targetUrl = options.url;

try {
  mkdirSync(outputDirectory, { recursive: true });
  if (!targetUrl) {
    const distDirectory = resolveFromRepository(options.distDirectory);
    if (!existsSync(join(distDirectory, "index.html"))) {
      throw new Error(
        `Production index does not exist: ${relative(repositoryRoot, distDirectory)}. ` +
          "Build it with :webApp:wasmJsBrowserDistribution or pass --url.",
      );
    }
    staticServer = await startStaticServer(distDirectory);
    targetUrl = `http://127.0.0.1:${staticServer.address().port}/`;
  }

  for (const profile of profiles) {
    results.push(await inspectProfile(profile, targetUrl, outputDirectory));
  }

  const report = {
    schemaVersion: 1,
    targetUrl,
    outputDirectory: toPosix(relative(repositoryRoot, outputDirectory)),
    profiles: results,
    passed: results.every((result) => result.passed),
  };
  writeFileSync(reportPath, `${JSON.stringify(report, null, 2)}\n`);
  printSummary(report);
  if (!report.passed) process.exitCode = 1;
} catch (error) {
  const message = error instanceof Error ? error.stack ?? error.message : String(error);
  mkdirSync(outputDirectory, { recursive: true });
  writeFileSync(
    reportPath,
    `${JSON.stringify({ schemaVersion: 1, targetUrl, profiles: results, passed: false, error: message }, null, 2)}\n`,
  );
  console.error(message);
  process.exitCode = 1;
} finally {
  if (staticServer) await new Promise((resolveClose) => staticServer.close(resolveClose));
}

function ensureVirtualDisplay() {
  if (process.env.DISPLAY) return;
  if (process.env.CLOUDBASE_VISUAL_CHECK_XVFB === "1") {
    throw new Error("xvfb-run did not provide a virtual DISPLAY");
  }
  const command = process.argv[1];
  const child = spawnSync(
    "xvfb-run",
    ["-a", "-s", "-screen 0 1280x940x24", process.execPath, command, ...process.argv.slice(2)],
    {
      cwd: process.cwd(),
      env: {
        ...process.env,
        CLOUDBASE_VISUAL_CHECK_XVFB: "1",
        GALLIUM_DRIVER: process.env.GALLIUM_DRIVER ?? "llvmpipe",
        LIBGL_ALWAYS_SOFTWARE: process.env.LIBGL_ALWAYS_SOFTWARE ?? "1",
      },
      stdio: "inherit",
    },
  );
  if (child.error) throw child.error;
  process.exit(child.status ?? 1);
}

async function inspectProfile(profile, url, artifactsDirectory) {
  const pageErrors = [];
  const consoleErrors = [];
  const requestFailures = [];
  const browser = await chromium.launch({
    headless: false,
    env: {
      ...process.env,
      GALLIUM_DRIVER: process.env.GALLIUM_DRIVER ?? "llvmpipe",
      LIBGL_ALWAYS_SOFTWARE: process.env.LIBGL_ALWAYS_SOFTWARE ?? "1",
    },
    args: [
      "--disable-dev-shm-usage",
      "--ignore-gpu-blocklist",
      "--window-position=0,0",
      `--window-size=${profile.viewport.width},${profile.viewport.height}`,
    ],
  });

  try {
    const context = await browser.newContext({
      viewport: profile.viewport,
      deviceScaleFactor: profile.deviceScaleFactor,
      isMobile: profile.isMobile,
      hasTouch: profile.hasTouch,
      locale: "en-US",
    });
    const page = await context.newPage();
    page.on("pageerror", (error) => pageErrors.push(error.message));
    page.on("console", (message) => {
      if (message.type() === "error") consoleErrors.push(message.text());
    });
    page.on("requestfailed", (request) => {
      const failure = request.failure()?.errorText ?? "request failed";
      if (failure !== "net::ERR_ABORTED") requestFailures.push(`${failure}: ${request.url()}`);
    });

    const cacheBuster = `${url.includes("?") ? "&" : "?"}visual-check=${Date.now()}`;
    await page.goto(`${url}${cacheBuster}`, { waitUntil: "domcontentloaded", timeout: 60_000 });
    await page.waitForSelector(".cloudbase-map-canvas .maplibregl-canvas", {
      state: "visible",
      timeout: 45_000,
    });
    await page.waitForFunction(
      () => {
        const host = document.querySelector(".cloudbase-map-canvas");
        const canvas = document.querySelector(".cloudbase-map-canvas .maplibregl-canvas");
        const bounds = canvas?.getBoundingClientRect();
        return host != null &&
          getComputedStyle(host).position === "absolute" &&
          host.clientHeight >= 200 &&
          bounds != null &&
          bounds.width >= 200 &&
          bounds.height >= 200;
      },
      undefined,
      { timeout: 30_000 },
    );
    await page.waitForFunction(
      () => document.querySelector(".cloudbase-map-status")?.textContent?.trim() === "",
      undefined,
      { timeout: 30_000 },
    );
    await page.waitForLoadState("networkidle", { timeout: 20_000 }).catch(() => {});
    await page.waitForTimeout(3_000);

    const cdp = await context.newCDPSession(page);
    const { windowId } = await cdp.send("Browser.getWindowForTarget");
    const { bounds: browserWindow } = await cdp.send("Browser.getWindowBounds", { windowId });
    await cdp.detach();
    const geometry = await page.evaluate((windowBounds) => {
      const host = document.querySelector(".cloudbase-map-canvas");
      const canvas = document.querySelector(".cloudbase-map-canvas .maplibregl-canvas");
      const bounds = canvas?.getBoundingClientRect();
      return {
        hostHeight: host?.clientHeight ?? 0,
        hostPosition: host ? getComputedStyle(host).position : "",
        canvas: bounds
          ? { x: bounds.x, y: bounds.y, width: bounds.width, height: bounds.height }
          : null,
        window: {
          left: windowBounds.left ?? window.screenX,
          top: windowBounds.top ?? window.screenY,
          width: windowBounds.width ?? window.outerWidth,
          height: windowBounds.height ?? window.outerHeight,
          innerWidth: window.innerWidth,
          innerHeight: window.innerHeight,
        },
      };
    }, browserWindow);

    const screenPath = join(artifactsDirectory, `${profile.id}-screen.png`);
    await runCommand("import", ["-window", "root", "-silent", screenPath]);
    const crop = centralMapCrop(geometry);
    const mapPath = join(artifactsDirectory, `${profile.id}-map.png`);
    await runCommand("convert", [
      screenPath,
      "-crop",
      `${crop.width}x${crop.height}+${crop.x}+${crop.y}`,
      "+repage",
      mapPath,
    ]);
    const screenPixels = inspectPixels(mapPath);
    const failures = [];
    if (geometry.hostPosition !== "absolute" || geometry.hostHeight < 200 || !geometry.canvas) {
      failures.push("map canvas geometry is not visibly mounted");
    }
    if (screenPixels.quantizedColors < 8 || screenPixels.standardDeviation < 0.003) {
      failures.push(
        `composited map crop is visually blank (${screenPixels.quantizedColors} colors, ` +
          `deviation ${screenPixels.standardDeviation.toFixed(4)})`,
      );
    }
    if (pageErrors.length > 0) failures.push(`${pageErrors.length} uncaught page error(s)`);
    if (consoleErrors.length > 0) failures.push(`${consoleErrors.length} browser console error(s)`);
    if (requestFailures.length > 0) failures.push(`${requestFailures.length} failed network request(s)`);

    return {
      id: profile.id,
      viewport: profile.viewport,
      deviceScaleFactor: profile.deviceScaleFactor,
      geometry,
      compositedPixels: screenPixels,
      artifacts: {
        screen: toPosix(relative(repositoryRoot, screenPath)),
        mapCrop: toPosix(relative(repositoryRoot, mapPath)),
      },
      pageErrors,
      consoleErrors,
      requestFailures,
      passed: failures.length === 0,
      failures,
    };
  } finally {
    await browser.close();
  }
}

function centralMapCrop(geometry) {
  if (!geometry.canvas) throw new Error("Map canvas has no screen geometry");
  const bounds = geometry.canvas;
  const width = Math.max(1, Math.floor(bounds.width * 0.5));
  const height = Math.max(1, Math.floor(bounds.height * 0.5));
  const x = Math.max(
    0,
    Math.floor(geometry.window.left + bounds.x + bounds.width * 0.25),
  );
  const browserChromeHeight = Math.max(0, geometry.window.height - geometry.window.innerHeight);
  const y = Math.max(
    0,
    Math.floor(geometry.window.top + browserChromeHeight + bounds.y + bounds.height * 0.25),
  );
  return { x, y, width, height };
}

function inspectPixels(imagePath) {
  const result = spawnSync(
    "convert",
    [imagePath, "-colors", "256", "-format", "%k %[fx:standard_deviation]", "info:"],
    { encoding: "utf8" },
  );
  if (result.error) throw result.error;
  if (result.status !== 0) throw new Error(result.stderr.trim() || "ImageMagick pixel check failed");
  const [colors, deviation] = result.stdout.trim().split(/\s+/u).map(Number);
  if (!Number.isFinite(colors) || !Number.isFinite(deviation)) {
    throw new Error(`Could not parse ImageMagick pixel metrics: ${result.stdout}`);
  }
  return { quantizedColors: colors, standardDeviation: deviation };
}

async function startStaticServer(distDirectory) {
  const server = createServer((request, response) => {
    try {
      const requestUrl = new URL(request.url ?? "/", "http://127.0.0.1");
      const relativePath = decodeURIComponent(requestUrl.pathname).replace(/^\/+|\/+$/gu, "");
      const candidate = resolve(distDirectory, relativePath || "index.html");
      const insideDist = candidate === distDirectory || candidate.startsWith(`${distDirectory}${sep}`);
      if (!insideDist || !existsSync(candidate) || !statSync(candidate).isFile()) {
        response.writeHead(404).end("Not found");
        return;
      }
      response.writeHead(200, {
        "cache-control": "no-store",
        "content-type": CONTENT_TYPES.get(extname(candidate)) ?? "application/octet-stream",
      });
      response.end(readFileSync(candidate));
    } catch (error) {
      response.writeHead(500).end(error instanceof Error ? error.message : String(error));
    }
  });
  await new Promise((resolveListen, reject) => {
    server.once("error", reject);
    server.listen(0, "127.0.0.1", resolveListen);
  });
  return server;
}

function runCommand(command, args) {
  return new Promise((resolveCommand, reject) => {
    const child = spawn(command, args, { stdio: ["ignore", "pipe", "pipe"] });
    let stderr = "";
    child.stderr.on("data", (chunk) => {
      stderr += chunk;
    });
    child.once("error", reject);
    child.once("close", (code) => {
      if (code === 0) resolveCommand();
      else reject(new Error(`${command} failed (${code}): ${stderr.trim()}`));
    });
  });
}

function parseArguments(args) {
  const parsed = {
    distDirectory: defaultDistDirectory,
    outputDirectory: defaultOutputDirectory,
    url: null,
    help: false,
  };
  for (let index = 0; index < args.length; index++) {
    const argument = args[index];
    if (argument === "--help" || argument === "-h") parsed.help = true;
    else if (argument === "--url") parsed.url = requiredValue(args, ++index, argument);
    else if (argument === "--dist") parsed.distDirectory = requiredValue(args, ++index, argument);
    else if (argument === "--output-dir") parsed.outputDirectory = requiredValue(args, ++index, argument);
    else throw new Error(`Unknown argument: ${argument}`);
  }
  if (parsed.url && !/^https?:\/\//u.test(parsed.url)) {
    throw new Error(`--url must use http or https: ${parsed.url}`);
  }
  return parsed;
}

function requiredValue(args, index, option) {
  const value = args[index];
  if (!value || value.startsWith("--")) throw new Error(`${option} requires a value`);
  return value;
}

function printUsage() {
  console.log(`Usage: node scripts/web/dev-visual-check.mjs [options]

Captures and checks the actually composited map pixels with headful Chromium, Xvfb,
software WebGL, and ImageMagick. Runs both desktop and phone-like profiles.

Options:
  --url URL          Check a live HTTP(S) deployment instead of the local dist
  --dist PATH        Local distribution directory (default: ${defaultDistDirectory})
  --output-dir PATH  Screenshot/report directory (default: ${defaultOutputDirectory})
  -h, --help         Show this help`);
}

function printSummary(report) {
  for (const profile of report.profiles) {
    const geometry = profile.geometry.canvas;
    console.log(
      `${profile.passed ? "PASS" : "FAIL"} ${profile.id}: ` +
        `host=${profile.geometry.hostHeight}px/${profile.geometry.hostPosition}, ` +
        `canvas=${Math.round(geometry?.width ?? 0)}x${Math.round(geometry?.height ?? 0)}, ` +
        `screen=${profile.compositedPixels.quantizedColors} colors/` +
        `${profile.compositedPixels.standardDeviation.toFixed(4)} deviation`,
    );
    for (const failure of profile.failures) console.log(`  - ${failure}`);
  }
  console.log(`Artifacts: ${toPosix(relative(repositoryRoot, outputDirectory))}`);
  console.log(report.passed ? "Visual map check passed." : "Visual map check failed.");
}

function resolveFromRepository(path) {
  return isAbsolute(path) ? resolve(path) : resolve(repositoryRoot, path);
}

function toPosix(path) {
  return path.split(sep).join("/");
}
