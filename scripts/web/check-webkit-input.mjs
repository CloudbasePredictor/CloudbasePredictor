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
  geocodingRequests: [],
  failures: [],
};
let browser;
let context;
let server;
let browserVersion = null;
let baseUrl = null;
let userAgent = null;
let inputEvents = [];
let typedValue = null;
let selectedValue = null;
let selectionLabel = null;
let confirmEnabled = false;

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
  check(
    "Mobile Safari device profile is active",
    /AppleWebKit/u.test(userAgent) && /Mobile/u.test(userAgent) && /Safari/u.test(userAgent),
    userAgent,
  );
  check(
    "touch input is enabled",
    await page.evaluate(() => navigator.maxTouchPoints > 0),
    `maxTouchPoints=${await page.evaluate(() => navigator.maxTouchPoints)}`,
  );

  const mapControl = page.getByRole("button", { name: "Map", exact: true });
  await mapControl.waitFor({
    state: "attached",
    timeout: config.browser.forecastReadyTimeoutMillis,
  });
  await mapControl.evaluate((element) => element.click());

  const searchInput = page.locator('[data-testid="location-search"]');
  await searchInput.waitFor({
    state: "visible",
    timeout: config.browser.mapMountTimeoutMillis,
  });
  check("native location-search input is visible", await searchInput.isVisible());
  check("location-search input uses search semantics", await searchInput.getAttribute("type") === "search");
  check(
    "location-search input has an accessible name",
    (await searchInput.getAttribute("aria-label")) === "Search location",
  );

  await searchInput.evaluate((element) => {
    globalThis.__cloudbaseWebKitInputEvents = [];
    for (const type of ["beforeinput", "input", "keydown", "keyup", "change"]) {
      element.addEventListener(type, () => globalThis.__cloudbaseWebKitInputEvents.push(type));
    }
  });
  await searchInput.tap();
  check(
    "touch activation focuses location-search input",
    await searchInput.evaluate((element) => document.activeElement === element),
  );
  await searchInput.pressSequentially(TEXT_INPUT_PROBE, { delay: 30 });
  typedValue = await searchInput.inputValue();
  inputEvents = await page.evaluate(() => globalThis.__cloudbaseWebKitInputEvents ?? []);
  check("location-search accepts WebKit keyboard input", typedValue === TEXT_INPUT_PROBE, typedValue);
  check("WebKit dispatches input events", inputEvents.includes("input"), inputEvents.join(", "));

  await searchInput.press("Enter");
  await waitFor(
    () => apiState.geocodingRequests.length > 0,
    config.browser.interactionTimeoutMillis,
    "Enter-triggered geocoding request",
  );
  const geocodingRequest = apiState.geocodingRequests.at(-1);
  check("Enter submits the typed query", geocodingRequest?.name === TEXT_INPUT_PROBE, geocodingRequest?.name);
  check(
    "geocoding request keeps the production response contract",
    geocodingRequest?.language === "en" && geocodingRequest?.format === "json",
    JSON.stringify(geocodingRequest),
  );

  const searchResult = page.getByRole("option", { name: `Select ${MOCK_LOCATION.name}`, exact: true });
  await searchResult.waitFor({
    state: "visible",
    timeout: config.browser.interactionTimeoutMillis,
  });
  check("location-search result is visible", await searchResult.isVisible(), MOCK_LOCATION.name);
  await searchResult.tap();

  const selectionCard = page.locator('[data-testid="map-selection-card"]');
  await selectionCard.waitFor({
    state: "visible",
    timeout: config.browser.interactionTimeoutMillis,
  });
  await waitFor(
    async () => !(await page.locator(".cloudbase-map-confirm").isDisabled()),
    config.browser.interactionTimeoutMillis,
    "selected location confirmation",
  );
  selectedValue = await searchInput.inputValue();
  selectionLabel = (await page.locator(".cloudbase-map-selection-label").textContent())?.trim() ?? "";
  confirmEnabled = !(await page.locator(".cloudbase-map-confirm").isDisabled());
  check("touch selection updates the search field", selectedValue === MOCK_LOCATION.name, selectedValue);
  check("touch selection exposes the selected location", selectionLabel === MOCK_LOCATION.name, selectionLabel);
  check("selected location can continue to forecast", confirmEnabled);
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
    input: {
      probe: TEXT_INPUT_PROBE,
      typedValue,
      events: inputEvents,
      selectedValue,
      selectionLabel,
      confirmEnabled,
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
  const geocodingBody = JSON.stringify({
    results: [{
      name: "Brauneck",
      latitude: MOCK_LOCATION.latitude,
      longitude: MOCK_LOCATION.longitude,
      country: "Germany",
      admin1: "Bavaria",
    }],
  });

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

  await browserContext.route("https://geocoding-api.open-meteo.com/**", async (route) => {
    const request = route.request();
    const url = new URL(request.url());
    const method = request.method();
    if (method !== "GET" && method !== "OPTIONS") {
      apiState.failures.push(`Unexpected geocoding method: ${method}`);
    }
    if (url.pathname !== "/v1/search") {
      apiState.failures.push(`Unexpected geocoding endpoint: ${url.pathname}`);
    }
    if (method === "GET") {
      apiState.geocodingRequests.push({
        name: url.searchParams.get("name"),
        language: url.searchParams.get("language"),
        format: url.searchParams.get("format"),
      });
    }
    await fulfillJson(route, method === "OPTIONS" ? "{}" : geocodingBody);
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
