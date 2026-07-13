#!/usr/bin/env node

import { createServer } from "node:http";
import { Buffer } from "node:buffer";
import { existsSync, mkdirSync, mkdtempSync, readFileSync, rmSync, statSync, writeFileSync } from "node:fs";
import { dirname, extname, isAbsolute, join, relative, resolve, sep } from "node:path";
import { tmpdir } from "node:os";
import { spawn } from "node:child_process";
import { fileURLToPath } from "node:url";
import { gzipSync } from "node:zlib";

const repositoryRoot = resolve(dirname(fileURLToPath(import.meta.url)), "../..");
const configPath = resolve(repositoryRoot, process.argv[2] ?? "config/web-release-gates.json");
const config = JSON.parse(readFileSync(configPath, "utf8"));
const distDirectory = resolveFromRepository(config.distDirectory);
const reportPath = resolve(
  repositoryRoot,
  process.env.WEB_RELEASE_REPORT_DIR ?? "webApp/build/reports/release-gates",
  "browser.json",
);
const forecastFixturePath = resolve(
  repositoryRoot,
  "app/src/main/assets/simulated/brauneck_icon_seamless_20260418.json",
);
const MOCK_LOCATION = Object.freeze({
  name: "Brauneck, Bavaria, Germany",
  latitude: 47.6631,
  longitude: 11.5217,
});
const FAVORITES_STORAGE_KEY = "cbp.kmp.favorites";
const USER_STATE_STORAGE_KEY = "cbp.kmp.user-state";
const TEXT_INPUT_ROLES = new Set(["textbox", "searchbox"]);
const INTERACTIVE_ROLES = new Set([
  "button",
  "checkbox",
  "combobox",
  "link",
  "option",
  "radio",
  "searchbox",
  "slider",
  "switch",
  "tab",
  "textbox",
]);
const COMPRESSIBLE_EXTENSIONS = new Set([
  ".css",
  ".html",
  ".js",
  ".json",
  ".map",
  ".mjs",
  ".svg",
  ".wasm",
]);
const CONTENT_TYPES = new Map([
  [".css", "text/css; charset=utf-8"],
  [".html", "text/html; charset=utf-8"],
  [".ico", "image/x-icon"],
  [".js", "text/javascript; charset=utf-8"],
  [".json", "application/json; charset=utf-8"],
  [".map", "application/json; charset=utf-8"],
  [".mjs", "text/javascript; charset=utf-8"],
  [".png", "image/png"],
  [".svg", "image/svg+xml"],
  [".wasm", "application/wasm"],
  [".webmanifest", "application/manifest+json; charset=utf-8"],
]);
const failures = [];
const runtimeExceptions = [];
const consoleMessages = [];
const networkFailures = [];
const transfers = [];
const requestedUrls = [];
let chrome;
let browser;
let server;
let profileDirectory;
let manualInputValues = null;
let selectionState = null;

class CdpClient {
  static async connect(url) {
    const client = new CdpClient(url);
    await client.opened;
    return client;
  }

  constructor(url) {
    this.nextId = 1;
    this.pending = new Map();
    this.listeners = new Map();
    this.socket = new WebSocket(url);
    this.opened = new Promise((resolveOpen, reject) => {
      this.socket.addEventListener("open", resolveOpen, { once: true });
      this.socket.addEventListener("error", reject, { once: true });
    });
    this.socket.addEventListener("message", (event) => this.handleMessage(event.data));
  }

  send(method, params = {}) {
    const id = this.nextId++;
    this.socket.send(JSON.stringify({ id, method, params }));
    return new Promise((resolveResult, reject) => {
      this.pending.set(id, { resolve: resolveResult, reject });
    });
  }

  on(method, listener) {
    const listeners = this.listeners.get(method) ?? [];
    listeners.push(listener);
    this.listeners.set(method, listeners);
  }

  close() {
    this.socket.close();
  }

  handleMessage(data) {
    const message = JSON.parse(String(data));
    if (message.id) {
      const pending = this.pending.get(message.id);
      if (!pending) return;
      this.pending.delete(message.id);
      if (message.error) pending.reject(new Error(`${message.error.message} (${message.error.code})`));
      else pending.resolve(message.result);
      return;
    }
    for (const listener of this.listeners.get(message.method) ?? []) listener(message.params);
  }
}

try {
  if (!existsSync(join(distDirectory, "index.html"))) {
    throw new Error(`Production index does not exist: ${relative(repositoryRoot, distDirectory)}`);
  }

  const staticServer = await startStaticServer();
  server = staticServer.server;
  const origin = `http://127.0.0.1:${staticServer.port}`;
  const baseUrl = `${origin}${normalizeBasePath(config.basePath)}`;
  const launched = await launchChrome();
  chrome = launched.process;
  profileDirectory = launched.profileDirectory;
  browser = await CdpClient.connect(await createPage(launched.debuggerUrl, "about:blank"));

  browser.on("Runtime.exceptionThrown", (params) => {
    runtimeExceptions.push(
      params.exceptionDetails?.exception?.description ??
        params.exceptionDetails?.text ??
        "Uncaught browser exception",
    );
  });
  browser.on("Runtime.consoleAPICalled", (params) => {
    consoleMessages.push(
      (params.args ?? [])
        .map((argument) => argument.value ?? argument.description ?? argument.type)
        .join(" "),
    );
  });
  browser.on("Network.loadingFailed", (params) => {
    networkFailures.push(`${params.errorText}: ${params.blockedReason ?? params.type ?? "request"}`);
  });
  browser.on("Network.requestWillBeSent", (params) => {
    const requestedUrl = params.request?.url;
    if (requestedUrl) requestedUrls.push(requestedUrl);
  });
  const apiMocks = installDeterministicApiMocks(browser);
  await Promise.all([
    browser.send("Page.enable"),
    browser.send("Runtime.enable"),
    browser.send("DOM.enable"),
    browser.send("Accessibility.enable"),
    browser.send("Network.enable"),
    browser.send("Fetch.enable", {
      patterns: [
        { urlPattern: "https://api.open-meteo.com/*", requestStage: "Request" },
      ],
    }),
  ]);
  await browser.send("Page.addScriptToEvaluateOnNewDocument", {
    source: `(() => {
      globalThis.__cloudbaseCopiedText = null;
      const clipboard = {
        writeText(value) {
          globalThis.__cloudbaseCopiedText = String(value);
          return Promise.resolve();
        }
      };
      try {
        Object.defineProperty(navigator, "clipboard", { configurable: true, value: clipboard });
      } catch {
        Object.defineProperty(Navigator.prototype, "clipboard", {
          configurable: true,
          get() { return clipboard; }
        });
      }
    })();`,
  });
  await browser.send("Emulation.setDeviceMetricsOverride", {
    width: 390,
    height: 844,
    deviceScaleFactor: 2,
    mobile: true,
  });

  const shareableHash = "#/?lat=47.6631&lon=11.5217&name=Release%20Gate&model=icon_seamless&view=thermic&day=0&hour=12";
  const navigationStarted = performance.now();
  await browser.send("Page.navigate", { url: `${baseUrl}${shareableHash}` });
  await waitForExpression(
    browser,
    `(() => {
      const viewport = document.querySelector("#cloudbase-app")?.firstElementChild;
      const bounds = viewport?.getBoundingClientRect();
      return document.readyState === "complete" &&
        viewport != null &&
        bounds != null &&
        bounds.width > 0 &&
        bounds.height > 0;
    })()`,
    config.browser.startupBudgetMillis,
    "Compose application startup",
  );
  const startupMillis = Math.round(performance.now() - navigationStarted);
  const forecastReadyAccessibility = await waitForForecastReady(
    browser,
    config.browser.forecastReadyTimeoutMillis,
    "initial forecast",
  );
  const forecastReadyMillis = Math.round(performance.now() - navigationStarted);
  await waitForTransferQuiet();
  const initialTransferCount = transfers.length;
  const initialTransfers = transfers.slice(0, initialTransferCount);
  const initialTransferBytes = sum(initialTransfers.map((transfer) => transfer.bytes));
  const initialTransferGzipBytes = sum(initialTransfers.map((transfer) => transfer.gzipBytes));

  const documentState = await evaluate(browser, `({
    title: document.title,
    language: document.documentElement.lang,
    viewport: document.querySelector('meta[name="viewport"]')?.content ?? "",
    manifest: document.querySelector('link[rel="manifest"]')?.getAttribute("href") ?? "",
    hash: location.hash,
    viewportWidth: document.querySelector("#cloudbase-app")?.firstElementChild?.getBoundingClientRect().width ?? 0,
    viewportHeight: document.querySelector("#cloudbase-app")?.firstElementChild?.getBoundingClientRect().height ?? 0
  })`);
  check("document title", documentState.title === "Cloudbase Predictor", documentState.title);
  check("document language", documentState.language === "en", documentState.language);
  check("responsive viewport metadata", documentState.viewport.includes("width=device-width"));
  check("web app manifest", documentState.manifest.length > 0, documentState.manifest);
  check("shareable forecast URL survives startup", documentState.hash === shareableHash, documentState.hash);
  check(
    "Compose viewport is visible",
    documentState.viewportWidth > 0 && documentState.viewportHeight > 0,
    `${documentState.viewportWidth}x${documentState.viewportHeight}`,
  );
  check(
    "loaded forecast UI is ready (not a canvas-only loading or error state)",
    forecastReadyAccessibility !== null,
  );
  check(
    `forecast ready <= ${config.browser.forecastReadyTimeoutMillis} ms`,
    forecastReadyMillis <= config.browser.forecastReadyTimeoutMillis,
    `${forecastReadyMillis} ms`,
  );
  check(
    `startup <= ${config.browser.startupBudgetMillis} ms`,
    startupMillis <= config.browser.startupBudgetMillis,
    `${startupMillis} ms`,
  );
  check(
    `initial transfer <= ${formatBytes(config.bundle.maximumInitialTransferBytes)}`,
    initialTransferBytes <= config.bundle.maximumInitialTransferBytes,
    formatBytes(initialTransferBytes),
  );
  check(
    `initial gzip transfer <= ${formatBytes(config.bundle.maximumInitialTransferGzipBytes)}`,
    initialTransferGzipBytes <= config.bundle.maximumInitialTransferGzipBytes,
    formatBytes(initialTransferGzipBytes),
  );

  const initialExceptions = [...runtimeExceptions];
  check("startup has no uncaught browser exceptions", initialExceptions.length === 0, initialExceptions.join("; "));

  const accessibility = forecastReadyAccessibility;
  const interactiveNodes = accessibility.filter((node) => INTERACTIVE_ROLES.has(node.role));
  const unnamedInteractiveNodes = interactiveNodes.filter((node) => node.name.trim().length === 0);
  check(
    `at least ${config.browser.minimumNamedInteractiveNodes} named interactive controls`,
    interactiveNodes.length - unnamedInteractiveNodes.length >= config.browser.minimumNamedInteractiveNodes,
    String(interactiveNodes.length - unnamedInteractiveNodes.length),
  );
  check(
    `at most ${config.browser.maximumUnnamedInteractiveNodes} unnamed interactive controls`,
    unnamedInteractiveNodes.length <= config.browser.maximumUnnamedInteractiveNodes,
    unnamedInteractiveNodes.map((node) => node.role).join(", "),
  );
  for (const requiredName of config.browser.requiredNavigationNames) {
    check(
      `accessible navigation control: ${requiredName}`,
      interactiveNodes.some((node) => node.name.trim() === requiredName),
    );
  }
  check(
    "shareable forecast exposes an accessible Copy link control",
    findAccessibleControl(accessibility, "Copy link") !== undefined,
  );
  check(
    "deterministic forecast fixture was requested",
    apiMocks.forecastRequests > 0,
    String(apiMocks.forecastRequests),
  );

  const forecastModeResults = await exerciseForecastModes(browser);

  // Forecast model selector: pill opens an in-canvas sheet listing every model with Best Effort
  // last (mirrors Android's ModelSelectorOverlay). Opened and closed without changing the model so
  // the deterministic forecast request contract (icon_seamless) is preserved.
  await clickAccessibleControl(browser, "Forecast model: ICON Seamless", config.browser.interactionTimeoutMillis);
  await waitForAccessibleControl(
    browser,
    "Forecast model option: ICON Seamless selected",
    config.browser.interactionTimeoutMillis,
    "forecast model sheet",
  );
  const modelSheetNodes = await accessibilitySnapshot(browser);
  const modelSheetListsBestEffort =
    findAccessibleControl(modelSheetNodes, "Forecast model option: Best Effort not selected") !== undefined;
  check("forecast model sheet lists all models including Best Effort", modelSheetListsBestEffort);
  await clickAccessibleControl(browser, "Close", config.browser.interactionTimeoutMillis);

  await clickAccessibleControl(browser, "Map", config.browser.interactionTimeoutMillis);
  await waitForExpression(
    browser,
    `document.querySelector(".cloudbase-map-root") !== null &&
      document.querySelector(".cloudbase-map-canvas") !== null`,
    config.browser.mapMountTimeoutMillis,
    "MapLibre DOM adapter mount",
  );
  await waitForExpression(
    browser,
    `document.querySelector(".cloudbase-map-canvas canvas") !== null`,
    config.browser.mapMountTimeoutMillis,
    "MapLibre canvas mount",
  );
  await waitForTransferQuiet();
  const mapState = await evaluate(browser, `({
    rootLabel: document.querySelector(".cloudbase-map-root")?.getAttribute("aria-label") ?? "",
    geolocationLabel: document.querySelector(".cloudbase-map-geolocate")?.getAttribute("aria-label") ?? "",
    mapCanvasCount: document.querySelectorAll(".cloudbase-map-canvas canvas").length
  })`);
  check("map adapter has an accessible label", mapState.rootLabel.length > 0, mapState.rootLabel);
  check(
    "map geolocation control has an accessible label",
    mapState.geolocationLabel.length > 0,
    mapState.geolocationLabel,
  );
  check("MapLibre canvas mounts", mapState.mapCanvasCount > 0, String(mapState.mapCanvasCount));

  // The map camera is written to the durable user-state document on load, so it can be restored
  // (default Berlin) on the next open/reload, matching Android's SharedPreferencesMapCameraStore.
  let cameraPersisted = false;
  try {
    await waitForExpression(
      browser,
      `(localStorage.getItem(${JSON.stringify(USER_STATE_STORAGE_KEY)}) ?? "").includes("map_camera_latitude")`,
      config.browser.interactionTimeoutMillis,
      "map camera persistence",
    );
    cameraPersisted = true;
  } catch {
    cameraPersisted = false;
  }
  check("map camera is persisted to durable storage after opening the map", cameraPersisted);

  const mapAccessibility = await accessibilitySnapshot(browser);
  const geolocationControl = findAccessibleControl(mapAccessibility, "Use my location");
  check(
    "map geolocation control is present in the accessibility tree",
    geolocationControl !== undefined,
  );
  for (const layerName of config.browser.requiredMapLayerNames) {
    check(
      `accessible map-layer control: ${layerName}`,
      findAccessibleControl(mapAccessibility, layerName) !== undefined,
    );
  }
  const initialLayerControl = findAccessibleControl(mapAccessibility, "Streets");
  check(
    "default map layer exposes pressed accessibility state",
    accessibleNodeMatchesState(initialLayerControl, "selected"),
    describeAccessibleState(initialLayerControl),
  );
  // The manual-add form is the map's only text input: it accepts a name plus coordinates in decimal,
  // DMS, or N/E notation and saves the result as a favorite, exactly like Android's ManualFavoriteDialog.
  await clickAccessibleControl(browser, "Add a location manually", config.browser.interactionTimeoutMillis);
  const manualFormAccessibility = await waitForAccessibility(
    browser,
    (nodes) => findAccessibleTextInput(nodes, "Favorite name") !== undefined &&
      findAccessibleTextInput(nodes, "Coordinates") !== undefined,
    config.browser.interactionTimeoutMillis,
    "manual-favorite form",
  );
  const manualNameInput = findAccessibleTextInput(manualFormAccessibility, "Favorite name");
  const manualCoordinatesInput = findAccessibleTextInput(manualFormAccessibility, "Coordinates");
  check("accessible manual-favorite name input", manualNameInput !== undefined);
  check("accessible manual-favorite coordinates input", manualCoordinatesInput !== undefined);
  check(
    "manual-favorite inputs are focusable",
    manualNameInput?.backendDOMNodeId !== undefined && manualCoordinatesInput?.backendDOMNodeId !== undefined,
  );
  await exerciseTextInput(browser, manualNameInput.backendDOMNodeId, MOCK_LOCATION.name);
  await exerciseTextInput(
    browser,
    manualCoordinatesInput.backendDOMNodeId,
    `${MOCK_LOCATION.latitude}, ${MOCK_LOCATION.longitude}`,
  );
  const typedManualInput = await accessibilitySnapshot(browser);
  manualInputValues = {
    name: findAccessibleTextInput(typedManualInput, "Favorite name")?.value ?? "",
    coordinates: findAccessibleTextInput(typedManualInput, "Coordinates")?.value ?? "",
  };
  check(
    "manual-favorite form accepts keyboard input",
    manualInputValues.name === MOCK_LOCATION.name &&
      manualInputValues.coordinates === `${MOCK_LOCATION.latitude}, ${MOCK_LOCATION.longitude}`,
    JSON.stringify(manualInputValues),
  );

  await clickAccessibleControl(browser, "Save", config.browser.interactionTimeoutMillis);
  const favoriteMarkerSelector =
    `.cloudbase-map-canvas [aria-label=${JSON.stringify(MOCK_LOCATION.name)}]`;
  await waitForExpression(
    browser,
    `document.querySelector(${JSON.stringify(favoriteMarkerSelector)}) !== null &&
      document.querySelector(".cloudbase-map-manual-form")?.style.display === "none"`,
    config.browser.interactionTimeoutMillis,
    "favorite saved from the manual-add form",
  );
  check("manually added location is written to durable browser storage", await localStorageHas(browser, FAVORITES_STORAGE_KEY));

  // Tapping the favorite marker selects it (Android snaps to a favorite within ~200 m of the tap).
  await evaluate(browser, `document.querySelector(${JSON.stringify(favoriteMarkerSelector)}).click()`);
  await waitForExpression(
    browser,
    `document.querySelector(".cloudbase-map-selection-card")?.style.display === "flex" &&
      document.querySelector(".cloudbase-map-confirm")?.disabled === false &&
      document.querySelector(".cloudbase-map-selection-label")?.textContent === ${JSON.stringify(MOCK_LOCATION.name)}`,
    config.browser.interactionTimeoutMillis,
    "deterministic map selection",
  );
  selectionState = await evaluate(browser, `({
    label: document.querySelector(".cloudbase-map-selection-label")?.textContent ?? "",
    confirmLabel: document.querySelector(".cloudbase-map-confirm")?.textContent ?? ""
  })`);
  check(
    "tapping a favorite marker selects that location",
    selectionState.label === MOCK_LOCATION.name,
    selectionState.label,
  );
  check(
    "map selection exposes Show forecast",
    selectionState.confirmLabel === "Show forecast",
    selectionState.confirmLabel,
  );

  await waitForTransferQuiet();
  const mapTransferEndCount = transfers.length;
  const mapTransfers = transfers.slice(initialTransferCount, mapTransferEndCount);
  const mapLazyAssetPattern = new RegExp(config.browser.mapLazyAssetPattern, "iu");
  const isMapLibreTransfer = (transfer) => transferMatchesMapLibre(
    transfer,
    mapLazyAssetPattern,
    config.browser.mapLazyAssetMarkers ?? [],
  );
  check(
    "MapLibre assets are absent from initial startup transfers",
    initialTransfers.every((transfer) => !isMapLibreTransfer(transfer)),
    initialTransfers.map((transfer) => transfer.path).join(", "),
  );
  check(
    "MapLibre chunk loads only after opening the map",
    mapTransfers.some(isMapLibreTransfer),
    mapTransfers.map((transfer) => transfer.path).join(", "),
  );

  await clickAccessibleControl(browser, "Show forecast", config.browser.interactionTimeoutMillis);
  const confirmedForecastAccessibility = await waitForForecastReady(
    browser,
    config.browser.forecastReadyTimeoutMillis,
    "forecast selected from the map",
  );
  const confirmedHash = await evaluate(browser, "location.hash");
  check(
    "map selection navigates to a complete shareable forecast URL",
    isCompleteForecastHash(confirmedHash, MOCK_LOCATION),
    confirmedHash,
  );
  check(
    "confirmed forecast retains the Copy link control",
    findAccessibleControl(confirmedForecastAccessibility, "Copy link") !== undefined,
  );
  await evaluate(browser, "globalThis.__cloudbaseCopiedText = null");
  await clickAccessibleControl(browser, "Copy link", config.browser.interactionTimeoutMillis);
  const expectedShareUrl = `${baseUrl}${confirmedHash}`;
  await waitForExpression(
    browser,
    `globalThis.__cloudbaseCopiedText === ${JSON.stringify(expectedShareUrl)}`,
    config.browser.interactionTimeoutMillis,
    "Copy link clipboard write",
  );
  const copiedShareUrl = await evaluate(browser, "globalThis.__cloudbaseCopiedText");
  check("Copy link writes the complete shareable URL", copiedShareUrl === expectedShareUrl, copiedShareUrl);

  // The location was saved from the map's manual-add form, so the forecast opens already favorited.
  // Toggle it off and on again to exercise both directions of the forecast favorite control.
  await clickAccessibleControl(browser, "Remove favorite", config.browser.interactionTimeoutMillis);
  await waitForAccessibleControl(
    browser,
    "Save favorite",
    config.browser.interactionTimeoutMillis,
    "favorite toggle after removal",
  );
  await clickAccessibleControl(browser, "Save favorite", config.browser.interactionTimeoutMillis);
  await waitForAccessibleControl(
    browser,
    "Remove favorite",
    config.browser.interactionTimeoutMillis,
    "favorite toggle",
  );
  const favoriteStorageBeforeReload = await localStorageHas(browser, FAVORITES_STORAGE_KEY);
  check("favorite is written to durable browser storage", favoriteStorageBeforeReload);

  // Favorites are now reached from the top-app-bar star (a dialog), not a navigation tab, so the
  // web navigation matches the Android app's four destinations (Map, Forecast, Settings, About).
  const favoriteOpenControlName = `Open forecast for ${MOCK_LOCATION.name}`;
  await clickAccessibleControl(browser, "Favorite locations", config.browser.interactionTimeoutMillis);
  await waitForAccessibleControl(
    browser,
    favoriteOpenControlName,
    config.browser.interactionTimeoutMillis,
    "Favorites dialog",
  );
  const favoriteBeforeReload = await accessibilityContainsName(browser, MOCK_LOCATION.name);
  check("Favorites dialog displays the saved location", favoriteBeforeReload, MOCK_LOCATION.name);
  await clickAccessibleControl(browser, "Close", config.browser.interactionTimeoutMillis);

  await reloadCurrentPage(browser);
  await clickAccessibleControl(browser, "Favorite locations", config.browser.interactionTimeoutMillis);
  await waitForAccessibleControl(
    browser,
    favoriteOpenControlName,
    config.browser.interactionTimeoutMillis,
    "Favorites dialog after reload",
  );
  const favoriteAfterReload = await accessibilityContainsName(browser, MOCK_LOCATION.name);
  const favoriteStorageAfterReload = await localStorageHas(browser, FAVORITES_STORAGE_KEY);
  check("favorite remains visible after reload", favoriteAfterReload, MOCK_LOCATION.name);
  check("favorite storage remains present after reload", favoriteStorageAfterReload);
  await clickAccessibleControl(browser, "Close", config.browser.interactionTimeoutMillis);

  await clickAccessibleControl(browser, "Settings", config.browser.interactionTimeoutMillis);
  await waitForAccessibility(
    browser,
    (nodes) => nodes.some((node) => node.name.trim() === "Settings") &&
      config.browser.persistenceSelections.every(
        (selection) => findAccessibleControl(nodes, selection.controlName) !== undefined,
      ),
    config.browser.interactionTimeoutMillis,
    "Settings controls",
  );
  const preferenceResultsBeforeReload = await selectAndVerifyPreferences(browser);
  const preferenceStorageBeforeReload = await localStorageHas(browser, USER_STATE_STORAGE_KEY);
  check("preferences are written to durable browser storage", preferenceStorageBeforeReload);

  await reloadCurrentPage(browser);
  await waitForAccessibility(
    browser,
    (nodes) => nodes.some((node) => node.name.trim() === "Settings") &&
      config.browser.persistenceSelections.every(
        (selection) => accessibleControlHasExpectedState(nodes, selection),
      ),
    config.browser.interactionTimeoutMillis,
    "persisted Settings controls",
  );
  const preferenceResultsAfterReload = await readPreferenceStates(browser);
  const preferenceStorageAfterReload = await localStorageHas(browser, USER_STATE_STORAGE_KEY);
  for (const result of preferenceResultsAfterReload) {
    check(`${result.id} preference survives reload`, result.matches, result.state);
  }
  check("preference storage remains present after reload", preferenceStorageAfterReload);

  // --- ParaglidingEarth launch-site snapshot journey ---
  // The build-time snapshot must be published in the same distribution and be structurally valid.
  const launchManifestFile = join(distDirectory, "data", "launch-sites", "manifest.json");
  const launchSnapshotPresent = existsSync(launchManifestFile);
  check("launch-site snapshot is published in the distribution", launchSnapshotPresent);
  if (launchSnapshotPresent) {
    const launchManifest = JSON.parse(readFileSync(launchManifestFile, "utf8"));
    check(
      "launch-site manifest declares a positive site count",
      Number(launchManifest.siteCount) > 0,
      String(launchManifest.siteCount),
    );
    check(
      "launch-site manifest declares ParaglidingEarth attribution and license",
      launchManifest.source?.name === "ParaglidingEarth" &&
        String(launchManifest.source?.license ?? "").length > 0,
      `${launchManifest.source?.name} / ${launchManifest.source?.license}`,
    );
  }

  // Default OFF: nothing under data/launch-sites is requested during startup (before the toggle was
  // enabled in the persistence journey above).
  check(
    "launch-site data is not requested during startup while disabled",
    initialTransfers.every((transfer) => !transfer.path.startsWith("data/launch-sites")),
    initialTransfers.filter((transfer) => transfer.path.startsWith("data/launch-sites")).map((transfer) => transfer.path).join(", "),
  );

  // The persistence journey already enabled "Show paragliding launch sites"; open the map (route is
  // centered on the deterministic location) and confirm the tiles for the viewport are loaded.
  const launchTransfersStart = transfers.length;
  await clickAccessibleControl(browser, "Map", config.browser.interactionTimeoutMillis);
  await waitForExpression(
    browser,
    `document.querySelector(".cloudbase-map-canvas canvas") !== null`,
    config.browser.mapMountTimeoutMillis,
    "map re-mount with launch sites enabled",
  );
  // The map must be VISIBLY composited, not merely mounted: MapLibre's lazy-loaded CSS once
  // overrode the host's position and collapsed it to zero height, so the fully drawn canvas was
  // clipped to a grey rectangle on every platform while all DOM-presence checks stayed green.
  const mapCanvasGeometry = await evaluate(browser, `(() => {
    const host = document.querySelector(".cloudbase-map-canvas");
    const canvas = document.querySelector(".cloudbase-map-canvas canvas");
    const rect = canvas ? canvas.getBoundingClientRect() : null;
    return {
      hostHeight: host ? host.clientHeight : 0,
      hostPosition: host ? getComputedStyle(host).position : "",
      canvasWidth: rect ? Math.round(rect.width) : 0,
      canvasHeight: rect ? Math.round(rect.height) : 0
    };
  })()`);
  check(
    "map canvas is visibly composited",
    mapCanvasGeometry.hostPosition === "absolute" &&
      mapCanvasGeometry.hostHeight >= 200 &&
      mapCanvasGeometry.canvasWidth >= 200 &&
      mapCanvasGeometry.canvasHeight >= 200,
    `host=${mapCanvasGeometry.hostHeight}px/${mapCanvasGeometry.hostPosition} ` +
      `canvas=${mapCanvasGeometry.canvasWidth}x${mapCanvasGeometry.canvasHeight}`,
  );
  await waitForExpression(
    browser,
    `document.querySelectorAll(".cloudbase-launch-marker").length > 0`,
    config.browser.mapMountTimeoutMillis,
    "launch-site markers",
  );
  const launchMarkerState = await evaluate(browser, `({
    markerCount: document.querySelectorAll(".cloudbase-launch-marker").length,
    attributionVisible: document.querySelector(".cloudbase-map-launch-attribution")?.style.display === "block",
    attributionText: document.querySelector(".cloudbase-map-launch-attribution")?.textContent ?? ""
  })`);
  check(
    "launch-site markers render after enabling the feature",
    launchMarkerState.markerCount > 0,
    String(launchMarkerState.markerCount),
  );
  check(
    "launch-site attribution is visible with ParaglidingEarth and CC BY-SA 3.0",
    launchMarkerState.attributionVisible &&
      launchMarkerState.attributionText.includes("ParaglidingEarth") &&
      launchMarkerState.attributionText.includes("CC BY-SA 3.0"),
    launchMarkerState.attributionText,
  );

  await waitForTransferQuiet();
  const launchDataTransfers = transfers
    .slice(launchTransfersStart)
    .filter((transfer) => transfer.path.startsWith("data/launch-sites"));
  check(
    "launch-site manifest is fetched from the same-origin snapshot",
    launchDataTransfers.some((transfer) => transfer.path === "data/launch-sites/manifest.json"),
    launchDataTransfers.map((transfer) => transfer.path).join(", "),
  );
  check(
    "launch-site tiles are fetched from the same-origin snapshot",
    launchDataTransfers.some((transfer) => transfer.path.startsWith("data/launch-sites/tiles/")),
    launchDataTransfers.map((transfer) => transfer.path).join(", "),
  );

  // Clicking a launch marker opens a detailed selection card with attribution and Show forecast.
  await evaluate(
    browser,
    `document.querySelector(".cloudbase-launch-marker").dispatchEvent(new MouseEvent("click", { bubbles: true }))`,
  );
  await waitForExpression(
    browser,
    `document.querySelector(".cloudbase-map-selection-detail")?.style.display === "block"`,
    config.browser.interactionTimeoutMillis,
    "launch-site selection card",
  );
  const launchCard = await evaluate(browser, `({
    label: document.querySelector(".cloudbase-map-selection-label")?.textContent ?? "",
    detail: document.querySelector(".cloudbase-map-selection-detail")?.textContent ?? "",
    source: document.querySelector(".cloudbase-map-selection-source")?.textContent ?? "",
    confirmLabel: document.querySelector(".cloudbase-map-confirm")?.textContent ?? "",
    confirmEnabled: document.querySelector(".cloudbase-map-confirm")?.disabled === false
  })`);
  check("launch-site selection card shows the site name", launchCard.label.length > 0, launchCard.label);
  check(
    "launch-site selection card shows launch details",
    /Altitude|Wind|Activities/u.test(launchCard.detail),
    launchCard.detail.replace(/\n/gu, " | "),
  );
  check(
    "launch-site selection card shows ParaglidingEarth attribution",
    launchCard.source.includes("ParaglidingEarth") && launchCard.source.includes("CC BY-SA 3.0"),
    launchCard.source,
  );
  check(
    "launch-site selection exposes an enabled Show forecast control",
    launchCard.confirmLabel === "Show forecast" && launchCard.confirmEnabled,
    `${launchCard.confirmLabel} (enabled=${launchCard.confirmEnabled})`,
  );

  // The browser must never contact ParaglidingEarth directly — the snapshot is same-origin only.
  const paraglidingRequests = requestedUrls.filter((url) => /paragliding\.?earth|paraglidingearth\.com/iu.test(url));
  check(
    "browser never requests ParaglidingEarth directly",
    paraglidingRequests.length === 0,
    paraglidingRequests.join(", "),
  );

  await clickAccessibleControl(browser, "About", config.browser.interactionTimeoutMillis);
  const aboutSourceControl = await waitForAccessibleControl(
    browser,
    "Source code on GitHub",
    config.browser.interactionTimeoutMillis,
    "About screen",
  );
  const aboutForecastAttribution = findAccessibleControl(
    await accessibilitySnapshot(browser),
    "Open-Meteo",
  );
  check("About screen exposes the source-code link", aboutSourceControl !== undefined);
  check("About screen exposes data-source attribution", aboutForecastAttribution !== undefined);

  check(
    "deterministic API interception has no failures",
    apiMocks.failures.length === 0,
    apiMocks.failures.join("; "),
  );
  check(
    "complete parity journey has no uncaught browser exceptions",
    runtimeExceptions.length === 0,
    runtimeExceptions.join("; "),
  );

  const report = {
    schemaVersion: 1,
    config: toPosix(relative(repositoryRoot, configPath)),
    baseUrl,
    startupMillis,
    forecastReadyMillis,
    initialTransferBytes,
    initialTransferGzipBytes,
    initialTransfers,
    mapTransfers,
    accessibility: {
      interactiveNodeCount: interactiveNodes.length,
      namedInteractiveNodeCount: interactiveNodes.length - unnamedInteractiveNodes.length,
      unnamedInteractiveNodes,
      manualFavoriteInput: manualInputValues,
    },
    parity: {
      forecastModes: forecastModeResults,
      confirmedHash,
      shareControlName: "Copy link",
      copiedShareUrl,
      mapSelection: selectionState,
      favorite: {
        name: MOCK_LOCATION.name,
        visibleBeforeReload: favoriteBeforeReload,
        visibleAfterReload: favoriteAfterReload,
        storageBeforeReload: favoriteStorageBeforeReload,
        storageAfterReload: favoriteStorageAfterReload,
      },
      preferences: {
        beforeReload: preferenceResultsBeforeReload,
        afterReload: preferenceResultsAfterReload,
        storageBeforeReload: preferenceStorageBeforeReload,
        storageAfterReload: preferenceStorageAfterReload,
      },
    },
    deterministicApiMocks: {
      forecastFixture: toPosix(relative(repositoryRoot, forecastFixturePath)),
      forecastRequests: apiMocks.forecastRequests,
      forecastRequestParams: apiMocks.forecastRequestParams,
      failures: apiMocks.failures,
    },
    initialExceptions,
    runtimeExceptions,
    consoleMessages,
    networkFailures,
    passed: failures.length === 0,
    failures,
  };
  mkdirSync(dirname(reportPath), { recursive: true });
  writeFileSync(reportPath, `${JSON.stringify(report, null, 2)}\n`);

  console.log(`Startup: ${startupMillis} ms`);
  console.log(`Forecast ready: ${forecastReadyMillis} ms`);
  console.log(
    `Initial transfer: ${formatBytes(initialTransferBytes)} raw, ` +
      `${formatBytes(initialTransferGzipBytes)} gzip`,
  );
  console.log(`Report: ${toPosix(relative(repositoryRoot, reportPath))}`);
  if (failures.length > 0) {
    console.error(`Browser release gate failed:\n- ${failures.join("\n- ")}`);
    process.exitCode = 1;
  } else {
    console.log("Browser release gate passed.");
  }
} catch (error) {
  const message = error instanceof Error ? error.message : String(error);
  console.error(error instanceof Error ? error.stack : error);
  const browserState = browser
    ? await evaluate(
      browser,
      `({
        readyState: document.readyState,
        rootChildren: document.querySelector("#cloudbase-app")?.childElementCount ?? -1,
        canvases: document.querySelectorAll("canvas").length,
        rootHtml: document.querySelector("#cloudbase-app")?.innerHTML?.slice(0, 1000) ?? "",
        firstChildShadowHtml: document.querySelector("#cloudbase-app")?.firstElementChild?.shadowRoot?.innerHTML?.slice(0, 1000) ?? "",
        bodyText: document.body?.innerText?.slice(0, 500) ?? "",
        location: location.href
      })`,
    ).catch((diagnosticError) => ({ diagnosticError: String(diagnosticError) }))
    : null;
  const accessibilityState = browser
    ? await accessibilitySnapshot(browser)
      .then((nodes) => nodes.filter((node) => INTERACTIVE_ROLES.has(node.role)))
      .catch((diagnosticError) => [{ diagnosticError: String(diagnosticError) }])
    : [];
  const caughtFailures = [
    ...failures,
    message,
    ...runtimeExceptions.map((runtimeError) => `Browser exception: ${runtimeError}`),
    ...networkFailures.map((networkError) => `Network failure: ${networkError}`),
  ];
  console.error(`Browser diagnostics: ${JSON.stringify({
    browserState,
    accessibilityState,
    runtimeExceptions,
    consoleMessages,
    networkFailures,
    transfers,
  })}`);
  mkdirSync(dirname(reportPath), { recursive: true });
  writeFileSync(
    reportPath,
    `${JSON.stringify({
      schemaVersion: 1,
      passed: false,
      failures: caughtFailures,
      browserState,
      accessibilityState,
      runtimeExceptions,
      consoleMessages,
      networkFailures,
      transfers,
    }, null, 2)}\n`,
  );
  process.exitCode = 1;
} finally {
  browser?.close();
  if (chrome) await stopChrome(chrome);
  if (server) await new Promise((resolveClose) => server.close(resolveClose));
  if (profileDirectory) {
    try {
      rmSync(profileDirectory, { recursive: true, force: true });
    } catch (error) {
      console.warn(`Could not remove temporary Chromium profile: ${error}`);
    }
  }
}

function resolveFromRepository(path) {
  return isAbsolute(path) ? resolve(path) : resolve(repositoryRoot, path);
}

function installDeterministicApiMocks(client) {
  if (!existsSync(forecastFixturePath)) {
    throw new Error(`Forecast fixture does not exist: ${relative(repositoryRoot, forecastFixturePath)}`);
  }
  const forecastBody = readFileSync(forecastFixturePath, "utf8");
  JSON.parse(forecastBody);
  const state = {
    forecastRequests: 0,
    forecastRequestParams: [],
    failures: [],
  };

  client.on("Fetch.requestPaused", (params) => {
    void fulfillDeterministicApiRequest(client, params, forecastBody, state)
      .catch(async (error) => {
        state.failures.push(error instanceof Error ? error.message : String(error));
        try {
          await client.send("Fetch.failRequest", {
            requestId: params.requestId,
            errorReason: "Failed",
          });
        } catch {
          // The request may already have been completed while reporting the mock failure.
        }
      });
  });
  return state;
}

async function fulfillDeterministicApiRequest(client, params, forecastBody, state) {
  const url = new URL(params.request.url);
  const method = params.request.method;
  const isPreflight = method === "OPTIONS";
  let body;
  if (url.hostname === "api.open-meteo.com") {
    validateForecastMockRequest(url, method, state);
    body = isPreflight ? "{}" : forecastBody;
  } else {
    await client.send("Fetch.continueRequest", { requestId: params.requestId });
    return;
  }

  await client.send("Fetch.fulfillRequest", {
    requestId: params.requestId,
    responseCode: 200,
    responsePhrase: "OK",
    responseHeaders: [
      { name: "Access-Control-Allow-Headers", value: "*" },
      { name: "Access-Control-Allow-Methods", value: "GET, OPTIONS" },
      { name: "Access-Control-Allow-Origin", value: "*" },
      { name: "Cache-Control", value: "no-store" },
      { name: "Content-Type", value: "application/json; charset=utf-8" },
      { name: "Timing-Allow-Origin", value: "*" },
    ],
    body: Buffer.from(body, "utf8").toString("base64"),
  });
}

function validateForecastMockRequest(url, method, state) {
  if (method !== "GET" && method !== "OPTIONS") {
    state.failures.push(`Unexpected forecast method: ${method}`);
    return;
  }
  if (url.pathname !== "/v1/forecast") {
    state.failures.push(`Unexpected forecast endpoint: ${url.pathname}`);
  }
  if (method === "OPTIONS") return;

  const request = {
    latitude: url.searchParams.get("latitude"),
    longitude: url.searchParams.get("longitude"),
    model: url.searchParams.get("models"),
    forecastDays: url.searchParams.get("forecast_days"),
    timezone: url.searchParams.get("timezone"),
    hasHourlyChartData: url.searchParams.get("hourly")?.includes("temperature_1000hPa") === true,
  };
  state.forecastRequests++;
  state.forecastRequestParams.push(request);
  if (Math.abs(Number(request.latitude) - MOCK_LOCATION.latitude) >= 0.000001) {
    state.failures.push(`Unexpected forecast latitude: ${request.latitude}`);
  }
  if (Math.abs(Number(request.longitude) - MOCK_LOCATION.longitude) >= 0.000001) {
    state.failures.push(`Unexpected forecast longitude: ${request.longitude}`);
  }
  if (request.model !== "icon_seamless") {
    state.failures.push(`Unexpected forecast model: ${request.model}`);
  }
  if (request.forecastDays !== "7") {
    state.failures.push(`Unexpected forecast horizon: ${request.forecastDays}`);
  }
  if (request.timezone !== "auto") {
    state.failures.push(`Unexpected forecast timezone: ${request.timezone}`);
  }
  if (!request.hasHourlyChartData) {
    state.failures.push("Forecast request does not include pressure-level chart data");
  }
}


async function exerciseForecastModes(client) {
  const results = [];
  const fingerprints = [];
  for (const mode of config.browser.forecastModes) {
    await clickAccessibleControl(client, mode.controlName, config.browser.interactionTimeoutMillis);
    await waitForExpression(
      client,
      `new URLSearchParams(location.hash.split("?")[1] ?? "").get("view") === ${JSON.stringify(mode.routeValue)}`,
      config.browser.interactionTimeoutMillis,
      `${mode.controlName} shareable route state`,
    );
    await waitForAccessibility(
      client,
      (snapshot) => isForecastReadyAccessibility(snapshot) &&
        accessibleControlHasExpectedState(snapshot, {
          controlName: mode.controlName,
          expectedState: "selected",
        }),
      config.browser.interactionTimeoutMillis,
      `${mode.controlName} accessible selected state`,
    );
    await waitForBrowserPaint(client);
    const nodes = await waitForAccessibility(
      client,
      (snapshot) => findAccessibleNode(snapshot, mode.rendererName) !== undefined,
      config.browser.interactionTimeoutMillis,
      `${mode.controlName} renderer accessibility node`,
    );
    const control = findAccessibleControl(nodes, mode.controlName);
    const renderer = findAccessibleNode(nodes, mode.rendererName);
    const unexpectedRendererNames = config.browser.forecastModes
      .filter((candidate) => candidate.rendererName !== mode.rendererName)
      .map((candidate) => candidate.rendererName)
      .filter((name) => findAccessibleNode(nodes, name) !== undefined);
    const accessibleSelected = accessibleNodeMatchesState(control, "selected");
    const visual = await captureRendererVisualMetrics(client, renderer.backendDOMNodeId);
    const visualConfig = config.browser.chartVisual;
    const rendererBoundsVisible =
      visual.bounds.width >= visualConfig.minimumWidthCssPixels &&
      visual.bounds.height >= visualConfig.minimumHeightCssPixels;
    const nonBlankPixels =
      visual.uniqueQuantizedColors >= visualConfig.minimumQuantizedColors &&
      visual.nonDominantPixelRatio >= visualConfig.minimumNonDominantPixelRatio &&
      visual.edgePixelRatio >= visualConfig.minimumEdgePixelRatio;
    const comparisons = fingerprints.map((previous) => ({
      comparedWith: previous.controlName,
      differenceRatio: fingerprintDifferenceRatio(visual.fingerprint, previous.fingerprint),
    }));
    const fingerprintHash = hashFingerprint(visual.fingerprint);
    const result = {
      controlName: mode.controlName,
      routeValue: mode.routeValue,
      rendererName: mode.rendererName,
      unexpectedRendererNames,
      accessibleSelected,
      accessibleState: describeAccessibleState(control),
      visual: {
        bounds: visual.bounds,
        screenshotPixels: `${visual.pixelWidth}x${visual.pixelHeight}`,
        sampledPixels: visual.sampledPixels,
        uniqueQuantizedColors: visual.uniqueQuantizedColors,
        nonDominantPixelRatio: visual.nonDominantPixelRatio,
        luminanceRange: visual.luminanceRange,
        edgePixelRatio: visual.edgePixelRatio,
        fingerprintHash,
        comparisons,
      },
    };
    check(
      `${mode.controlName} control activates the expected chart renderer`,
      accessibleSelected,
      result.accessibleState,
    );
    check(
      `${mode.controlName} exposes only its renderer`,
      unexpectedRendererNames.length === 0,
      unexpectedRendererNames.join(", "),
    );
    check(
      `${mode.controlName} chart occupies a visible browser region`,
      rendererBoundsVisible,
      `${visual.bounds.width.toFixed(1)}x${visual.bounds.height.toFixed(1)} CSS px`,
    );
    check(
      `${mode.controlName} chart paints nonblank browser pixels`,
      nonBlankPixels,
      `${visual.uniqueQuantizedColors} colors, ` +
        `${formatRatio(visual.nonDominantPixelRatio)} non-dominant, ` +
        `${formatRatio(visual.edgePixelRatio)} edges`,
    );
    for (const comparison of comparisons) {
      check(
        `${mode.controlName} chart pixels differ from ${comparison.comparedWith}`,
        comparison.differenceRatio >= visualConfig.minimumPairwiseFingerprintDifferenceRatio,
        formatRatio(comparison.differenceRatio),
      );
    }
    fingerprints.push({
      controlName: mode.controlName,
      fingerprint: visual.fingerprint,
    });
    results.push(result);
  }
  return results;
}

async function waitForForecastReady(client, timeoutMillis, label) {
  return waitForAccessibility(
    client,
    isForecastReadyAccessibility,
    timeoutMillis,
    `${label} loaded UI`,
  );
}

function isForecastReadyAccessibility(nodes) {
  const hasModeControls = config.browser.forecastModes.every(
    (mode) => findAccessibleControl(nodes, mode.controlName) !== undefined,
  );
  const hasShare = findAccessibleControl(nodes, "Copy link") !== undefined;
  const hasFavorite = ["Save favorite", "Remove favorite"].some(
    (name) => findAccessibleControl(nodes, name) !== undefined,
  );
  const hasLoadedForecastStatus = nodes.some(
    (node) => /(?:live|saved) forecast/iu.test(node.name),
  );
  const hasAttribution = nodes.some(
    (node) => node.name.includes("Forecast data by Open-Meteo.com"),
  );
  return hasModeControls && hasShare && hasFavorite && hasLoadedForecastStatus && hasAttribution;
}

async function selectAndVerifyPreferences(client) {
  const results = [];
  for (const selection of config.browser.persistenceSelections) {
    let nodes = await accessibilitySnapshot(client);
    const before = findAccessibleControl(nodes, selection.controlName);
    const alreadyMatched = accessibleNodeMatchesState(before, selection.expectedState);
    if (!alreadyMatched) {
      await clickAccessibleControl(client, selection.controlName, config.browser.interactionTimeoutMillis);
      nodes = await waitForAccessibility(
        client,
        (snapshot) => accessibleControlHasExpectedState(snapshot, selection),
        config.browser.interactionTimeoutMillis,
        `${selection.id} preference selection`,
      );
    }
    const control = findAccessibleControl(nodes, selection.controlName);
    const result = {
      id: selection.id,
      controlName: selection.controlName,
      expectedState: selection.expectedState,
      changed: !alreadyMatched,
      matches: accessibleNodeMatchesState(control, selection.expectedState),
      state: describeAccessibleState(control),
    };
    check(
      `${selection.id} preference changes from the fresh-profile default`,
      result.changed,
      result.state,
    );
    check(`${selection.id} preference can be selected`, result.matches, result.state);
    results.push(result);
  }
  return results;
}

async function readPreferenceStates(client) {
  const nodes = await accessibilitySnapshot(client);
  return config.browser.persistenceSelections.map((selection) => {
    const control = findAccessibleControl(nodes, selection.controlName);
    return {
      id: selection.id,
      controlName: selection.controlName,
      expectedState: selection.expectedState,
      matches: accessibleNodeMatchesState(control, selection.expectedState),
      state: describeAccessibleState(control),
    };
  });
}

function accessibleControlHasExpectedState(nodes, selection) {
  return accessibleNodeMatchesState(
    findAccessibleControl(nodes, selection.controlName),
    selection.expectedState,
  );
}

function accessibleNodeMatchesState(node, expectedState) {
  if (!node) return false;
  const value = accessibleToggleValue(node);
  if (expectedState === "selected" || expectedState === "checked") return value === true;
  if (expectedState === "unselected" || expectedState === "unchecked") return value === false;
  throw new Error(`Unsupported accessible state in release-gate config: ${expectedState}`);
}

function accessibleToggleValue(node) {
  for (const property of ["checked", "selected", "pressed"]) {
    const value = node?.properties?.[property];
    if (value === true || value === "true") return true;
    if (value === false || value === "false") return false;
  }
  const accessibleText = `${node?.name ?? ""} ${node?.description ?? ""}`.toLowerCase();
  if (/\b(?:not selected|not checked|unchecked)\b/u.test(accessibleText)) return false;
  if (/\b(?:selected|checked)\b/u.test(accessibleText)) return true;
  return undefined;
}

function describeAccessibleState(node) {
  if (!node) return "control not found";
  const exposed = ["checked", "selected", "pressed"]
    .filter((property) => node.properties?.[property] !== undefined)
    .map((property) => `${property}=${node.properties[property]}`);
  if (exposed.length > 0) return exposed.join(", ");
  const fallback = accessibleToggleValue(node);
  return fallback === undefined ? "selection state not exposed" : `accessible-name state=${fallback}`;
}

async function waitForAccessibleControl(client, name, timeoutMillis, label = name) {
  const nodes = await waitForAccessibility(
    client,
    (snapshot) => findAccessibleControl(snapshot, name) !== undefined,
    timeoutMillis,
    label,
  );
  return findAccessibleControl(nodes, name);
}

async function clickAccessibleControl(client, name, timeoutMillis) {
  const node = await waitForAccessibleControl(client, name, timeoutMillis, `${name} control`);
  if (!node?.backendDOMNodeId) {
    throw new Error(`${name} is accessible but has no DOM node for interaction`);
  }
  await clickBackendNode(client, node.backendDOMNodeId);
}

function findAccessibleControl(nodes, name) {
  const controls = nodes.filter((node) => INTERACTIVE_ROLES.has(node.role));
  return controls.find((node) => node.name.trim() === name) ??
    controls.find((node) => node.name.trim().startsWith(`${name} `));
}

function findAccessibleNode(nodes, name) {
  return nodes.find((node) => node.name.trim() === name);
}

function findAccessibleTextInput(nodes, name) {
  return nodes.find((node) => TEXT_INPUT_ROLES.has(node.role) && node.name.trim() === name);
}

async function clickBackendNode(client, backendDOMNodeId) {
  const resolved = await client.send("DOM.resolveNode", { backendNodeId: backendDOMNodeId });
  const objectId = resolved.object?.objectId;
  if (objectId) {
    try {
      const result = await client.send("Runtime.callFunctionOn", {
        objectId,
        functionDeclaration: `function () {
          if (this.disabled) throw new Error("Control is disabled");
          this.click();
        }`,
      });
      if (!result.exceptionDetails) return;
    } finally {
      await client.send("Runtime.releaseObject", { objectId });
    }
  }

  try {
    await client.send("DOM.scrollIntoViewIfNeeded", { backendNodeId: backendDOMNodeId });
    const { model } = await client.send("DOM.getBoxModel", { backendNodeId: backendDOMNodeId });
    const quad = model.content ?? model.border;
    const xs = quad.filter((_, index) => index % 2 === 0);
    const ys = quad.filter((_, index) => index % 2 === 1);
    const x = (Math.min(...xs) + Math.max(...xs)) / 2;
    const y = (Math.min(...ys) + Math.max(...ys)) / 2;
    await client.send("Input.dispatchMouseEvent", { type: "mouseMoved", x, y });
    await client.send("Input.dispatchMouseEvent", {
      type: "mousePressed",
      x,
      y,
      button: "left",
      clickCount: 1,
    });
    await client.send("Input.dispatchMouseEvent", {
      type: "mouseReleased",
      x,
      y,
      button: "left",
      clickCount: 1,
    });
  } catch (pointerError) {
    throw new Error(`Could not activate accessible DOM node: ${pointerError}`);
  }
}

async function waitForBrowserPaint(client) {
  await evaluate(
    client,
    "new Promise((resolve) => requestAnimationFrame(() => requestAnimationFrame(resolve)))",
  );
}

async function captureRendererVisualMetrics(client, backendDOMNodeId) {
  if (!backendDOMNodeId) {
    throw new Error("Chart renderer is accessible but has no DOM node for visual inspection");
  }

  const [{ model }, layoutMetrics] = await Promise.all([
    client.send("DOM.getBoxModel", { backendNodeId: backendDOMNodeId }),
    client.send("Page.getLayoutMetrics"),
  ]);
  const quad = model.content ?? model.border;
  const xs = quad.filter((_, index) => index % 2 === 0);
  const ys = quad.filter((_, index) => index % 2 === 1);
  const viewport = layoutMetrics.cssVisualViewport ?? layoutMetrics.visualViewport;
  const viewportLeft = viewport.pageX ?? 0;
  const viewportTop = viewport.pageY ?? 0;
  const viewportRight = viewportLeft + viewport.clientWidth;
  const viewportBottom = viewportTop + viewport.clientHeight;
  const left = Math.max(Math.min(...xs), viewportLeft);
  const top = Math.max(Math.min(...ys), viewportTop);
  const right = Math.min(Math.max(...xs), viewportRight);
  const bottom = Math.min(Math.max(...ys), viewportBottom);
  const width = right - left;
  const height = bottom - top;
  if (width <= 0 || height <= 0) {
    throw new Error(`Chart renderer is outside the visual viewport: ${width}x${height}`);
  }

  const screenshot = await client.send("Page.captureScreenshot", {
    format: "png",
    fromSurface: true,
    captureBeyondViewport: false,
    clip: { x: left, y: top, width, height, scale: 1 },
  });
  const pixels = await analyzeScreenshotPixels(client, screenshot.data);
  return {
    bounds: { x: left, y: top, width, height },
    ...pixels,
  };
}

async function analyzeScreenshotPixels(client, base64Png) {
  return evaluate(
    client,
    `(async () => {
      const binary = atob(${JSON.stringify(base64Png)});
      const bytes = new Uint8Array(binary.length);
      for (let index = 0; index < binary.length; index++) bytes[index] = binary.charCodeAt(index);
      const image = await createImageBitmap(new Blob([bytes], { type: "image/png" }));
      const canvas = new OffscreenCanvas(image.width, image.height);
      const context = canvas.getContext("2d", { willReadFrequently: true });
      context.drawImage(image, 0, 0);
      const imageData = context.getImageData(0, 0, image.width, image.height).data;
      const step = Math.max(1, Math.floor(Math.sqrt((image.width * image.height) / 30000)));
      const histogram = new Map();
      let sampledPixels = 0;
      let luminanceMin = 255;
      let luminanceMax = 0;
      let edgePixels = 0;
      let edgeComparisons = 0;
      const colorDistance = (first, second) =>
        Math.abs(imageData[first] - imageData[second]) +
        Math.abs(imageData[first + 1] - imageData[second + 1]) +
        Math.abs(imageData[first + 2] - imageData[second + 2]);
      for (let y = 0; y < image.height; y += step) {
        for (let x = 0; x < image.width; x += step) {
          const offset = (y * image.width + x) * 4;
          const red = imageData[offset];
          const green = imageData[offset + 1];
          const blue = imageData[offset + 2];
          const key = ((red >> 4) << 8) | ((green >> 4) << 4) | (blue >> 4);
          histogram.set(key, (histogram.get(key) ?? 0) + 1);
          const luminance = 0.2126 * red + 0.7152 * green + 0.0722 * blue;
          luminanceMin = Math.min(luminanceMin, luminance);
          luminanceMax = Math.max(luminanceMax, luminance);
          sampledPixels++;
          if (x >= step) {
            edgePixels += colorDistance(offset, offset - step * 4) >= 36 ? 1 : 0;
            edgeComparisons++;
          }
          if (y >= step) {
            edgePixels += colorDistance(offset, offset - step * image.width * 4) >= 36 ? 1 : 0;
            edgeComparisons++;
          }
        }
      }
      const dominantPixels = Math.max(...histogram.values());
      const fingerprintSize = 24;
      const fingerprintCanvas = new OffscreenCanvas(fingerprintSize, fingerprintSize);
      const fingerprintContext = fingerprintCanvas.getContext("2d", { willReadFrequently: true });
      fingerprintContext.drawImage(image, 0, 0, fingerprintSize, fingerprintSize);
      const fingerprintData = fingerprintContext.getImageData(
        0,
        0,
        fingerprintSize,
        fingerprintSize,
      ).data;
      const fingerprint = [];
      for (let offset = 0; offset < fingerprintData.length; offset += 4) {
        fingerprint.push(
          ((fingerprintData[offset] >> 4) << 8) |
            ((fingerprintData[offset + 1] >> 4) << 4) |
            (fingerprintData[offset + 2] >> 4),
        );
      }
      image.close();
      return {
        pixelWidth: canvas.width,
        pixelHeight: canvas.height,
        sampledPixels,
        uniqueQuantizedColors: histogram.size,
        nonDominantPixelRatio: sampledPixels === 0 ? 0 : 1 - dominantPixels / sampledPixels,
        luminanceRange: luminanceMax - luminanceMin,
        edgePixelRatio: edgeComparisons === 0 ? 0 : edgePixels / edgeComparisons,
        fingerprint,
      };
    })()`,
  );
}

function fingerprintDifferenceRatio(first, second) {
  if (first.length !== second.length || first.length === 0) return 1;
  let different = 0;
  for (let index = 0; index < first.length; index++) {
    if (first[index] !== second[index]) different++;
  }
  return different / first.length;
}

function hashFingerprint(fingerprint) {
  let hash = 2166136261;
  for (const value of fingerprint) {
    hash ^= value;
    hash = Math.imul(hash, 16777619);
  }
  return (hash >>> 0).toString(16).padStart(8, "0");
}

function formatRatio(value) {
  return `${(value * 100).toFixed(1)}%`;
}

async function waitForAccessibility(client, predicate, timeoutMillis, label) {
  const deadline = performance.now() + timeoutMillis;
  while (performance.now() < deadline) {
    const nodes = await accessibilitySnapshot(client);
    if (predicate(nodes)) return nodes;
    await delay(100);
  }
  throw new Error(`${label} did not complete within ${timeoutMillis} ms`);
}

async function accessibilityContainsName(client, name) {
  const nodes = await accessibilitySnapshot(client);
  return nodes.some((node) => node.name.trim() === name);
}

async function localStorageHas(client, key) {
  return evaluate(client, `localStorage.getItem(${JSON.stringify(key)}) !== null`);
}

async function reloadCurrentPage(client) {
  await evaluate(client, "window.__cloudbaseReleaseGateReloadMarker = true");
  await client.send("Page.reload", { ignoreCache: true });
  await waitForExpression(
    client,
    `document.readyState === "complete" &&
      window.__cloudbaseReleaseGateReloadMarker === undefined &&
      document.querySelector("#cloudbase-app")?.childElementCount > 0`,
    config.browser.forecastReadyTimeoutMillis,
    "application reload",
  );
}

function isCompleteForecastHash(hash, location) {
  const marker = hash.indexOf("?");
  if (marker < 0) return false;
  // Forecasts now encode to "#/forecast?…"; legacy share links used the bare "#/?…" form.
  const prefix = hash.slice(0, marker);
  if (prefix !== "#/forecast" && prefix !== "#/") return false;
  const parameters = new URLSearchParams(hash.substring(marker + 1));
  const latitude = Number(parameters.get("lat"));
  const longitude = Number(parameters.get("lon"));
  return Math.abs(latitude - location.latitude) < 0.000001 &&
    Math.abs(longitude - location.longitude) < 0.000001 &&
    parameters.get("name") === location.name &&
    ["model", "view", "day", "hour"].every((name) => parameters.has(name));
}

async function startStaticServer() {
  const basePath = normalizeBasePath(config.basePath);
  const gzipCache = new Map();
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

      const raw = readFileSync(file);
      const supportsGzip = request.headers["accept-encoding"]?.includes("gzip") === true;
      const shouldCompress = supportsGzip && COMPRESSIBLE_EXTENSIONS.has(extname(file));
      const body = shouldCompress
        ? gzipCache.get(file) ?? gzipCache.set(file, gzipSync(raw, { level: 9 })).get(file)
        : raw;
      transfers.push({
        path: toPosix(relative(distDirectory, file)),
        bytes: raw.length,
        gzipBytes: shouldCompress ? body.length : raw.length,
      });
      response.writeHead(200, {
        "Cache-Control": "no-store",
        "Content-Type": contentType(file),
        "Content-Length": body.length,
        ...(shouldCompress ? { "Content-Encoding": "gzip", "Vary": "Accept-Encoding" } : {}),
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

async function launchChrome() {
  const executable = findChrome();
  const profile = mkdtempSync(join(tmpdir(), "cloudbase-web-gate-"));
  const args = [
    "--headless=new",
    "--no-sandbox",
    "--disable-dev-shm-usage",
    "--no-first-run",
    "--no-default-browser-check",
    "--enable-unsafe-swiftshader",
    "--use-gl=angle",
    "--use-angle=swiftshader",
    "--remote-debugging-port=0",
    `--user-data-dir=${profile}`,
    "about:blank",
  ];
  const process = spawn(executable, args, { stdio: ["ignore", "ignore", "pipe"] });
  const debuggerUrl = await new Promise((resolveUrl, reject) => {
    const timeout = setTimeout(() => reject(new Error("Timed out waiting for Chromium DevTools")), 15_000);
    let stderr = "";
    process.stderr.setEncoding("utf8");
    process.stderr.on("data", (chunk) => {
      stderr += chunk;
      const match = stderr.match(/DevTools listening on (ws:\/\/[^\s]+)/u);
      if (match) {
        clearTimeout(timeout);
        resolveUrl(match[1]);
      }
    });
    process.once("exit", (code) => {
      clearTimeout(timeout);
      reject(new Error(`Chromium exited before DevTools was ready (exit ${code})`));
    });
  });
  return { process, debuggerUrl, profileDirectory: profile };
}

async function stopChrome(chromeProcess) {
  if (chromeProcess.exitCode !== null) return;
  const exited = new Promise((resolveExit) => chromeProcess.once("exit", resolveExit));
  chromeProcess.kill("SIGTERM");
  await Promise.race([exited, delay(2000)]);
  if (chromeProcess.exitCode === null) chromeProcess.kill("SIGKILL");
}

function findChrome() {
  const candidates = [
    process.env.CHROME_BIN,
    process.env.CHROMIUM_PATH,
    "/usr/bin/google-chrome",
    "/usr/bin/google-chrome-stable",
    "/usr/bin/chromium",
    "/usr/bin/chromium-browser",
  ].filter(Boolean);
  const executable = candidates.find(existsSync);
  if (!executable) {
    throw new Error("Chromium was not found. Set CHROME_BIN to a Chrome or Chromium executable.");
  }
  return executable;
}

async function createPage(debuggerUrl, url) {
  const endpoint = new URL(debuggerUrl);
  const response = await fetch(
    `http://${endpoint.host}/json/new?${encodeURIComponent(url)}`,
    { method: "PUT" },
  );
  if (!response.ok) throw new Error(`Could not create Chromium page: HTTP ${response.status}`);
  return (await response.json()).webSocketDebuggerUrl;
}

async function evaluate(client, expression) {
  const result = await client.send("Runtime.evaluate", {
    expression,
    awaitPromise: true,
    returnByValue: true,
  });
  if (result.exceptionDetails) {
    throw new Error(result.exceptionDetails.text ?? `Evaluation failed: ${expression}`);
  }
  return result.result.value;
}

async function waitForExpression(client, expression, timeoutMillis, label) {
  const deadline = performance.now() + timeoutMillis;
  let lastError;
  while (performance.now() < deadline) {
    try {
      if (await evaluate(client, `Boolean(${expression})`)) return;
    } catch (error) {
      lastError = error;
    }
    await delay(100);
  }
  const detail = lastError instanceof Error ? `: ${lastError.message}` : "";
  throw new Error(`${label} did not complete within ${timeoutMillis} ms${detail}`);
}

async function accessibilitySnapshot(client) {
  const result = await client.send("Accessibility.getFullAXTree");
  return result.nodes
    .filter((node) => !node.ignored)
    .map((node) => ({
      role: node.role?.value ?? "",
      name: node.name?.value ?? "",
      value: String(node.value?.value ?? ""),
      description: node.description?.value ?? "",
      properties: Object.fromEntries(
        (node.properties ?? []).map((property) => [property.name, property.value?.value]),
      ),
      backendDOMNodeId: node.backendDOMNodeId,
    }));
}

async function exerciseTextInput(client, backendDOMNodeId, text) {
  await client.send("DOM.focus", { backendNodeId: backendDOMNodeId });
  await client.send("Input.dispatchKeyEvent", {
    type: "keyDown",
    key: "a",
    code: "KeyA",
    modifiers: 2,
  });
  await client.send("Input.dispatchKeyEvent", {
    type: "keyUp",
    key: "a",
    code: "KeyA",
    modifiers: 2,
  });
  await client.send("Input.dispatchKeyEvent", { type: "keyDown", key: "Backspace", code: "Backspace" });
  await client.send("Input.dispatchKeyEvent", { type: "keyUp", key: "Backspace", code: "Backspace" });
  await client.send("Input.insertText", { text });
  await delay(200);
}

function normalizeBasePath(path) {
  return `/${String(path).trim().replace(/^\/+|\/+$/gu, "")}/`;
}

function isInside(parent, child) {
  const path = relative(parent, child);
  return path !== ".." && !path.startsWith(`..${sep}`) && !isAbsolute(path);
}

function transferMatchesMapLibre(transfer, pathPattern, contentMarkers) {
  if (pathPattern.test(transfer.path)) return true;
  if (extname(transfer.path) !== ".js") return false;
  const file = resolve(distDirectory, transfer.path);
  if (!isInside(distDirectory, file) || !existsSync(file)) return false;
  const contents = readFileSync(file, "utf8");
  return contentMarkers.some((marker) => contents.includes(marker));
}

function check(label, passed, detail = "") {
  console.log(`${passed ? "PASS" : "FAIL"}  ${label}${detail ? ` (${detail})` : ""}`);
  if (!passed) failures.push(`${label}${detail ? `: ${detail}` : ""}`);
}

function contentType(file) {
  return CONTENT_TYPES.get(extname(file)) ?? "application/octet-stream";
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

function delay(milliseconds) {
  return new Promise((resolveDelay) => setTimeout(resolveDelay, milliseconds));
}

async function waitForTransferQuiet(quietMillis = 500, timeoutMillis = 5000) {
  const deadline = performance.now() + timeoutMillis;
  let previousCount = transfers.length;
  let quietSince = performance.now();
  while (performance.now() < deadline) {
    await delay(100);
    if (transfers.length !== previousCount) {
      previousCount = transfers.length;
      quietSince = performance.now();
    }
    if (performance.now() - quietSince >= quietMillis) return;
  }
  throw new Error(`Static asset transfers did not become quiet within ${timeoutMillis} ms`);
}
