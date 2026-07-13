#!/usr/bin/env node

import { createServer } from "node:http";
import { existsSync, mkdirSync, readFileSync, statSync, writeFileSync } from "node:fs";
import { dirname, extname, isAbsolute, relative, resolve, sep } from "node:path";
import { fileURLToPath } from "node:url";
import { devices, webkit } from "playwright";

const repositoryRoot = resolve(dirname(fileURLToPath(import.meta.url)), "../..");
const configPath = resolve(repositoryRoot, process.argv[2] ?? "config/web-release-gates.json");
const config = JSON.parse(readFileSync(configPath, "utf8"));
const distDirectory = resolveFromRepository(config.distDirectory);
const reportPath = resolve(
  repositoryRoot,
  process.env.WEB_RELEASE_REPORT_DIR ?? "webApp/build/reports/release-gates",
  "webkit-input.json",
);
const forecastFixturePath = resolve(
  repositoryRoot,
  "app/src/main/assets/simulated/brauneck_icon_seamless_20260418.json",
);
const DEVICE_NAME = "iPhone 15";
const TEXT_INPUT_PROBE = "Brauneck";
const MOCK_LOCATION = Object.freeze({
  name: "Brauneck, Bavaria, Germany",
  latitude: 47.6631,
  longitude: 11.5217,
});
const TYPED_COORDINATES = `${MOCK_LOCATION.latitude}, ${MOCK_LOCATION.longitude}`;
const FAVORITES_STORAGE_KEY = "cbp.kmp.favorites";
const CONTENT_TYPES = new Map([
  [".css", "text/css; charset=utf-8"],
  [".html", "text/html; charset=utf-8"],
  [".ico", "image/x-icon"],
  [".js", "text/javascript; charset=utf-8"],
  [".json", "application/json; charset=utf-8"],
  [".mjs", "text/javascript; charset=utf-8"],
  [".png", "image/png"],
  [".svg", "image/svg+xml"],
  [".wasm", "application/wasm"],
  [".webmanifest", "application/manifest+json; charset=utf-8"],
]);

const checks = [];
const failures = [];
const runtimeErrors = [];
const consoleErrors = [];
const apiState = {
  forecastRequests: [],
  failures: [],
};
let browser;
let context;
let server;
let browserVersion = null;
let baseUrl = null;
let userAgent = null;
let deviceHasTouch = null;
let maxTouchPoints = null;
let touchEvents = [];
let inputEvents = [];
let typedValue = null;
let typedCoordinates = null;
let savedFavorites = null;

try {
  assertRequiredInputs();
  const staticServer = await startStaticServer();
  server = staticServer.server;
  baseUrl = `http://127.0.0.1:${staticServer.port}${normalizeBasePath(config.basePath)}`;

  const device = devices[DEVICE_NAME];
  if (!device) throw new Error(`Playwright does not define the ${DEVICE_NAME} device profile`);
  browser = await webkit.launch({ headless: true });
  browserVersion = browser.version();
  context = await browser.newContext({
    ...device,
    locale: "en-US",
    serviceWorkers: "block",
    timezoneId: "Europe/Berlin",
  });
  await installDeterministicRoutes(context);

  const page = await context.newPage();
  page.on("pageerror", (error) => runtimeErrors.push(error.stack ?? error.message));
  page.on("crash", () => runtimeErrors.push("The WebKit page crashed"));
  page.on("console", (message) => {
    if (message.type() === "error") consoleErrors.push(message.text());
  });

  const shareableHash = "#/?lat=47.6631&lon=11.5217&name=Release%20Gate&model=icon_seamless&view=thermic&day=0&hour=12";
  await page.goto(`${baseUrl}${shareableHash}`, {
    waitUntil: "load",
    timeout: config.browser.forecastReadyTimeoutMillis,
  });
  userAgent = await page.evaluate(() => navigator.userAgent);
  deviceHasTouch = device.hasTouch === true;
  maxTouchPoints = await page.evaluate(() => navigator.maxTouchPoints);
  check(
    "Mobile Safari device profile is active",
    /AppleWebKit/u.test(userAgent) && /Mobile/u.test(userAgent) && /Safari/u.test(userAgent),
    userAgent,
  );
  check(
    "Mobile Safari device profile requests touch input",
    deviceHasTouch,
    `hasTouch=${String(device.hasTouch)}`,
  );

  const mapControl = page.getByRole("button", { name: "Map", exact: true });
  await mapControl.waitFor({
    state: "attached",
    timeout: config.browser.forecastReadyTimeoutMillis,
  });
  await mapControl.evaluate((element) => element.click());

  // The manual-add form is the map's only text input. It is a native DOM input layered over the
  // Skiko canvas, which is exactly the combination Mobile Safari has historically broken.
  const addPoint = page.locator(".cloudbase-map-add-point");
  await addPoint.waitFor({
    state: "visible",
    timeout: config.browser.mapMountTimeoutMillis,
  });
  await addPoint.tap();

  const nameInput = page.locator('[data-testid="manual-favorite-name"]');
  const coordinatesInput = page.locator('[data-testid="manual-favorite-coordinates"]');
  await nameInput.waitFor({
    state: "visible",
    timeout: config.browser.interactionTimeoutMillis,
  });
  check("native manual-favorite inputs are visible", await nameInput.isVisible() && await coordinatesInput.isVisible());
  check(
    "manual-favorite inputs have accessible names",
    (await nameInput.getAttribute("aria-label")) === "Favorite name" &&
      (await coordinatesInput.getAttribute("aria-label")) === "Coordinates",
  );

  await nameInput.evaluate((element) => {
    globalThis.__cloudbaseWebKitInputEvents = [];
    globalThis.__cloudbaseWebKitTouchEvents = [];
    for (const type of ["beforeinput", "input", "keydown", "keyup", "change"]) {
      element.addEventListener(type, () => globalThis.__cloudbaseWebKitInputEvents.push(type));
    }
    for (const type of ["touchstart", "touchend", "pointerdown", "pointerup"]) {
      element.addEventListener(type, (event) => {
        globalThis.__cloudbaseWebKitTouchEvents.push({
          type,
          pointerType: event.pointerType ?? null,
        });
      });
    }
  });
  await nameInput.tap();
  touchEvents = await page.evaluate(() => globalThis.__cloudbaseWebKitTouchEvents ?? []);
  check(
    "touch activation dispatches touch input events",
    touchEvents.some((event) => event.type === "touchstart" || event.pointerType === "touch"),
    JSON.stringify(touchEvents),
  );
  check(
    "touch activation focuses the manual-favorite name input",
    await nameInput.evaluate((element) => document.activeElement === element),
  );
  await nameInput.pressSequentially(TEXT_INPUT_PROBE, { delay: 30 });
  typedValue = await nameInput.inputValue();
  inputEvents = await page.evaluate(() => globalThis.__cloudbaseWebKitInputEvents ?? []);
  check("manual-favorite input accepts WebKit keyboard input", typedValue === TEXT_INPUT_PROBE, typedValue);
  check("WebKit dispatches input events", inputEvents.includes("input"), inputEvents.join(", "));

  await coordinatesInput.tap();
  await coordinatesInput.pressSequentially(TYPED_COORDINATES, { delay: 30 });
  typedCoordinates = await coordinatesInput.inputValue();
  check("manual-favorite coordinates accept WebKit keyboard input", typedCoordinates === TYPED_COORDINATES, typedCoordinates);

  // Saving proves the typed text reached Kotlin: the coordinates are parsed in :shared and the
  // favorite is written to durable storage, which is what a real Safari user would get.
  await page.locator(".cloudbase-map-manual-save").tap();
  await waitFor(
    async () => (await page.locator(".cloudbase-map-manual-form").evaluate((element) => element.style.display)) === "none",
    config.browser.interactionTimeoutMillis,
    "manual-favorite form submission",
  );
  savedFavorites = await page.evaluate(
    (key) => localStorage.getItem(key) ?? "",
    FAVORITES_STORAGE_KEY,
  );
  check(
    "typed favorite is parsed and written to durable storage",
    savedFavorites.includes(TEXT_INPUT_PROBE) && savedFavorites.includes(String(MOCK_LOCATION.latitude)),
    savedFavorites,
  );
  check("deterministic API routes have no contract failures", apiState.failures.length === 0, apiState.failures.join("; "));
  check("WebKit journey has no uncaught runtime errors", runtimeErrors.length === 0, runtimeErrors.join("; "));
} catch (error) {
  const message = error instanceof Error ? error.stack ?? error.message : String(error);
  failures.push(message);
  console.error(message);
} finally {
  if (context) await context.close().catch(() => undefined);
  if (browser) await browser.close().catch(() => undefined);
  if (server) await new Promise((resolveClose) => server.close(resolveClose));

  const report = {
    schemaVersion: 1,
    config: toPosix(relative(repositoryRoot, configPath)),
    engine: "webkit",
    browserVersion,
    device: DEVICE_NAME,
    baseUrl,
    userAgent,
    touch: {
      deviceHasTouch,
      maxTouchPoints,
      events: touchEvents,
    },
    input: {
      probe: TEXT_INPUT_PROBE,
      typedValue,
      typedCoordinates,
      events: inputEvents,
      savedFavorites,
    },
    api: apiState,
    runtimeErrors,
    consoleErrors,
    checks,
    passed: failures.length === 0,
    failures,
  };
  mkdirSync(dirname(reportPath), { recursive: true });
  writeFileSync(reportPath, `${JSON.stringify(report, null, 2)}\n`);
  console.log(`WebKit input report: ${toPosix(relative(repositoryRoot, reportPath))}`);
  if (failures.length > 0) {
    console.error(`WebKit mobile-input release gate failed with ${failures.length} failure(s).`);
    process.exitCode = 1;
  } else {
    console.log(`WebKit mobile-input release gate passed (${browserVersion}, ${DEVICE_NAME}).`);
  }
}

function assertRequiredInputs() {
  const indexPath = resolve(distDirectory, "index.html");
  if (!existsSync(indexPath)) {
    throw new Error(`Production index does not exist: ${relative(repositoryRoot, indexPath)}`);
  }
  if (!existsSync(forecastFixturePath)) {
    throw new Error(`Forecast fixture does not exist: ${relative(repositoryRoot, forecastFixturePath)}`);
  }
}

async function installDeterministicRoutes(browserContext) {
  const forecastBody = readFileSync(forecastFixturePath, "utf8");
  JSON.parse(forecastBody);

  await browserContext.route("https://api.open-meteo.com/**", async (route) => {
    const request = route.request();
    const url = new URL(request.url());
    const method = request.method();
    if (method !== "GET" && method !== "OPTIONS") {
      apiState.failures.push(`Unexpected forecast method: ${method}`);
    }
    if (url.pathname !== "/v1/forecast") {
      apiState.failures.push(`Unexpected forecast endpoint: ${url.pathname}`);
    }
    if (method === "GET") {
      apiState.forecastRequests.push({
        latitude: url.searchParams.get("latitude"),
        longitude: url.searchParams.get("longitude"),
        model: url.searchParams.get("models"),
      });
    }
    await fulfillJson(route, method === "OPTIONS" ? "{}" : forecastBody);
  });

  await browserContext.route("https://tiles.openfreemap.org/styles/liberty**", async (route) => {
    await fulfillJson(route, JSON.stringify({ version: 8, sources: {}, layers: [] }));
  });
}

async function fulfillJson(route, body) {
  await route.fulfill({
    status: 200,
    body,
    headers: {
      "Access-Control-Allow-Headers": "*",
      "Access-Control-Allow-Methods": "GET, OPTIONS",
      "Access-Control-Allow-Origin": "*",
      "Cache-Control": "no-store",
      "Content-Type": "application/json; charset=utf-8",
    },
  });
}

async function startStaticServer() {
  const basePath = normalizeBasePath(config.basePath);
  const staticServer = createServer((request, response) => {
    try {
      const url = new URL(request.url ?? "/", "http://127.0.0.1");
      if (!url.pathname.startsWith(basePath)) {
        response.writeHead(404).end("Not found");
        return;
      }
      const relativePath = decodeURIComponent(url.pathname.slice(basePath.length)) || "index.html";
      const file = resolve(distDirectory, relativePath);
      if (!isInside(distDirectory, file) || !existsSync(file) || !statSync(file).isFile()) {
        response.writeHead(404).end("Not found");
        return;
      }
      const body = readFileSync(file);
      response.writeHead(200, {
        "Cache-Control": "no-store",
        "Content-Length": body.length,
        "Content-Type": CONTENT_TYPES.get(extname(file)) ?? "application/octet-stream",
      });
      response.end(body);
    } catch (error) {
      response.writeHead(500).end("Internal server error");
      console.error(error);
    }
  });
  await new Promise((resolveListen, reject) => {
    staticServer.once("error", reject);
    staticServer.listen(0, "127.0.0.1", resolveListen);
  });
  return { server: staticServer, port: staticServer.address().port };
}

async function waitFor(predicate, timeoutMillis, label) {
  const deadline = performance.now() + timeoutMillis;
  let lastError;
  while (performance.now() < deadline) {
    try {
      if (await predicate()) return;
    } catch (error) {
      lastError = error;
    }
    await delay(100);
  }
  const detail = lastError instanceof Error ? `: ${lastError.message}` : "";
  throw new Error(`${label} did not complete within ${timeoutMillis} ms${detail}`);
}

function check(label, passed, detail = "") {
  const normalizedDetail = detail === undefined || detail === null ? "" : String(detail);
  checks.push({ label, passed, detail: normalizedDetail });
  console.log(`${passed ? "PASS" : "FAIL"}  ${label}${normalizedDetail ? ` (${normalizedDetail})` : ""}`);
  if (!passed) failures.push(`${label}${normalizedDetail ? `: ${normalizedDetail}` : ""}`);
}

function resolveFromRepository(path) {
  return isAbsolute(path) ? resolve(path) : resolve(repositoryRoot, path);
}

function normalizeBasePath(path) {
  return `/${String(path).trim().replace(/^\/+|\/+$/gu, "")}/`;
}

function isInside(parent, child) {
  const path = relative(parent, child);
  return path !== ".." && !path.startsWith(`..${sep}`) && !isAbsolute(path);
}

function toPosix(path) {
  return path.split(sep).join("/");
}

function delay(milliseconds) {
  return new Promise((resolveDelay) => setTimeout(resolveDelay, milliseconds));
}
