// Phase 4 acceptance-flow driver (dev tool; not part of the app bundle).
//
// Scripts the full flow with headless Chromium and asserts it end-to-end:
//   open site -> pick a point on the map -> forecast -> favorite it ->
//   share URL -> open a fresh (clean-storage) context with that URL ->
//   same forecast. Also checks browser geolocation and the URL round-trip.
//
// Requires a running preview server and a Chromium binary:
//   npm run build
//   npm run preview -- --port 4173 --strictPort   # in another shell
//   npm i -D playwright-core                        # if not present
//   CHROMIUM_PATH=/usr/bin/chromium node scripts/acceptance.mjs
//
// Screenshots go to web/screenshots/ (gitignored). Uses fixture data mode so the
// forecast render is deterministic; the map still loads real tiles/styles.

import { mkdirSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import { chromium } from "playwright-core";

const __dirname = dirname(fileURLToPath(import.meta.url));
const outDir = join(__dirname, "..", "screenshots");
mkdirSync(outDir, { recursive: true });

const BASE = process.env.PREVIEW_URL ?? "http://localhost:4173/CloudbasePredictor/";
const EXECUTABLE = process.env.CHROMIUM_PATH ?? "/usr/bin/chromium";
const START = `${BASE}?data=fixture`;
const MOCK_GEO = { latitude: 47.5, longitude: 11.5 };

const failures = [];
function check(label, ok, detail = "") {
  console.log(`${ok ? "PASS" : "FAIL"}  ${label}${detail ? ` — ${detail}` : ""}`);
  if (!ok) failures.push(label);
}

const browser = await chromium.launch({
  executablePath: EXECUTABLE,
  // Software WebGL (SwiftShader) so MapLibre can render tiles headlessly.
  args: [
    "--no-sandbox",
    "--hide-scrollbars",
    "--enable-unsafe-swiftshader",
    "--use-gl=angle",
    "--use-angle=swiftshader",
  ],
});

const context = await browser.newContext({
  viewport: { width: 412, height: 915, deviceScaleFactor: 2 },
  geolocation: MOCK_GEO,
  permissions: ["geolocation"],
});
const page = await context.newPage();
page.on("pageerror", (error) => console.log("PAGEERROR:", error.message));

const tileResponses = [];
page.on("response", (response) => {
  const url = response.url();
  if (/openfreemap|opentopomap|arcgisonline|earthdata\.nasa|\/tiles?\//.test(url)) {
    tileResponses.push({ status: response.status(), url });
  }
});

// 1. Open the site: forecast renders and the URL is canonicalized.
await page.goto(START, { waitUntil: "networkidle" });
await page.waitForSelector(".forecast-tabs", { timeout: 15000 });
await page.waitForSelector(".chart-canvas", { timeout: 15000 });
check("forecast renders on load", (await page.locator(".chart-canvas").count()) > 0);
check("URL canonicalized to a shareable hash", /#\/forecast\?lat=/.test(page.url()), page.url());
await page.screenshot({ path: join(outDir, "accept-1-forecast.png") });

// 2. Open the map picker (lazy chunk) and wait for tiles.
await page.getByRole("button", { name: "Map", exact: true }).click();
await page.waitForSelector(".maplibregl-canvas", { timeout: 15000 });
await page.waitForTimeout(3500);
check("map canvas mounts", (await page.locator(".maplibregl-canvas").count()) > 0);
const okTiles = tileResponses.filter((r) => r.status >= 200 && r.status < 400);
check(
  "map tiles/style loaded over network",
  okTiles.length > 0,
  okTiles.length > 0 ? `${okTiles.length} responses` : "no tile responses (report as UNVERIFIED)",
);
check("per-layer attribution visible", (await page.locator(".map-attribution-toggle").count()) > 0);
await page.screenshot({ path: join(outDir, "accept-2-map-open.png") });

// 3. Browser geolocation: use-my-location selects the mocked position.
await page.getByRole("button", { name: "Use my location" }).click();
await page.waitForSelector(".map-selection-coords", { timeout: 10000 });
const geoText = (await page.locator(".map-selection-coords").textContent()) ?? "";
check(
  "geolocation selects mocked position",
  geoText.includes("47.5000") && geoText.includes("11.5000"),
  geoText,
);

// 4. Pick a point on the map, then open its forecast.
const canvasBox = await page.locator(".maplibregl-canvas").boundingBox();
check(
  "map canvas fills its container",
  canvasBox !== null && canvasBox.height > 500,
  `${canvasBox?.height}px tall`,
);
await page.mouse.click(canvasBox.x + canvasBox.width / 2, canvasBox.y + canvasBox.height * 0.55);
await page.waitForSelector('[data-testid="map-selection-card"]', { timeout: 10000 });
const pickedCoords = (await page.locator(".map-selection-coords").textContent()) ?? "";
check("map click selects a point", /-?\d+\.\d{4}, -?\d+\.\d{4}/.test(pickedCoords), pickedCoords);
await page.getByTestId("map-show-forecast").click();
await page.waitForSelector(".chart-canvas", { timeout: 15000 });
const [pickLat, pickLon] = pickedCoords.split(",").map((s) => Number(s.trim()));
const afterPickUrl = page.url();
// The URL carries full 6-decimal precision; the card shows 4 decimals (rounded).
const urlParams = new URLSearchParams(afterPickUrl.split("?").pop());
check(
  "picked location written to URL",
  Math.abs(Number(urlParams.get("lat")) - pickLat) < 0.001 &&
    Math.abs(Number(urlParams.get("lon")) - pickLon) < 0.001,
  afterPickUrl,
);
await page.screenshot({ path: join(outDir, "accept-3-forecast-after-pick.png") });

// 5. Favorite the picked place; it persists and appears in the favorites list.
await page.getByRole("button", { name: "Add to favorites" }).click();
const stored = await page.evaluate(() => localStorage.getItem("cbp.favorites.v1"));
check(
  "favorite persisted to localStorage",
  stored?.includes(pickLat.toFixed(4)) === true,
  stored ?? "",
);
await page.getByRole("button", { name: "Favorites", exact: true }).click();
await page.waitForSelector(".favorites-list", { timeout: 10000 });
check("favorite shown in list", (await page.getByTestId("favorite-open").count()) >= 1);
await page.screenshot({ path: join(outDir, "accept-4-favorites-list.png") });
await page.keyboard.press("Escape").catch(() => {});
await page.getByRole("button", { name: "Close favorites" }).click();

// 6. Share the URL and open it in a fresh, clean-storage context.
const shareUrl = page.url();
const freshContext = await browser.newContext({
  viewport: { width: 412, height: 915, deviceScaleFactor: 2 },
});
const freshPage = await freshContext.newPage();
await freshPage.goto(shareUrl, { waitUntil: "networkidle" });
await freshPage.waitForSelector(".chart-canvas", { timeout: 15000 });
check("fresh context reproduces the shared hash", freshPage.url() === shareUrl, freshPage.url());
const freshFavorites = await freshPage.evaluate(() => localStorage.getItem("cbp.favorites.v1"));
check("fresh context has clean favorites storage", freshFavorites === null);
const freshPlace = (await freshPage.locator(".place-name").textContent()) ?? "";
check(
  "shared link reproduces the picked location",
  freshPlace.trim() === pickedCoords.trim(),
  `${freshPlace} vs ${pickedCoords}`,
);
await freshPage.screenshot({ path: join(outDir, "accept-5-shared-fresh.png") });

await browser.close();

console.log(
  `\n${failures.length === 0 ? "ALL CHECKS PASSED" : `FAILURES: ${failures.join(", ")}`}`,
);
process.exit(failures.length === 0 ? 0 : 1);
