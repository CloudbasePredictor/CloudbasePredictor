#!/usr/bin/env node

import { mkdir, readFile, writeFile } from "node:fs/promises";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import { chromium } from "playwright";

const repositoryRoot = resolve(dirname(fileURLToPath(import.meta.url)), "../..");
const outputDirectory = resolve(repositoryRoot, "webApp/build/reports/wind-check");
const fixturePath = resolve(
  repositoryRoot,
  "app/src/main/assets/simulated/brauneck_icon_seamless_20260418.json",
);
const baseUrl = process.argv[2];
const route = "#/?lat=47.6631&lon=11.5217&name=Brauneck&model=icon_seamless&view=wind&day=0&hour=12";
const rendererName = "Wind forecast chart renderer";
const profiles = [
  { name: "desktop", width: 1280, height: 900 },
  { name: "phone", width: 390, height: 844 },
  // Less than 480 dp of forecast content: the page scrolls and the chart keeps a fixed height.
  { name: "landscape", width: 844, height: 390, shortScreen: true },
];
// Mirrors WindForecastView.kt (1 dp is 1 px here): a 60 dp altitude axis (WIND_AXIS_WIDTH), and below the plot
// a 26 dp 10 m row, a 28 dp legend and a 16 dp hour axis. The whole day, 06-22 h, fits the width.
// Declared before the top-level checks run so they are initialized.
const WIND_AXIS_WIDTH = 60;
const WIND_SURFACE_ROW_HEIGHT = 26;
const WIND_BOTTOM_CHROME = WIND_SURFACE_ROW_HEIGHT + 28 + 16;
const FIXTURE_HOURS = 17;
const FIXTURE_ELEVATION_KM = 0.58;
// The plot spans from the terrain up to elevationKm + visibleTopAltitudeKm (WindForecastView.kt), and a fresh
// browser profile starts at DEFAULT_VISIBLE_TOP_ALTITUDE_KM = 4 km (WebForecastDestination.kt).
const VISIBLE_ALTITUDE_RANGE_KM = 4;
// ShortScreenWindChartHeight in WebForecastDestination.kt. At 844x390 it is taller than the area between the
// top app bar and the navigation bar, so each tap scrolls its target into view first.
const SHORT_SCREEN_WIND_CHART_HEIGHT = 396;
const REVEAL_MARGIN = 10;
const failures = [];
const results = [];

if (!baseUrl || !/^https?:\/\//u.test(baseUrl)) {
  throw new Error("Usage: node scripts/web/check-wind-forecast.mjs <local-built-web-base-url>");
}

await mkdir(outputDirectory, { recursive: true });
const fixture = sparseWindFixture(JSON.parse(await readFile(fixturePath, "utf8")));
const browser = await chromium.launch({
  headless: true,
  args: ["--enable-unsafe-swiftshader", "--use-gl=angle", "--use-angle=swiftshader"],
});

try {
  for (const profile of profiles) {
    const context = await browser.newContext({
      viewport: { width: profile.width, height: profile.height },
      deviceScaleFactor: 1,
      locale: "en-US",
    });
    const page = await context.newPage();
    const runtimeExceptions = [];
    let forecastRequests = 0;
    page.on("pageerror", (error) => runtimeExceptions.push(error.stack ?? error.message));
    await page.route("https://api.open-meteo.com/v1/forecast**", async (requestRoute) => {
      forecastRequests++;
      await requestRoute.fulfill({
        status: 200,
        contentType: "application/json; charset=utf-8",
        headers: {
          "Access-Control-Allow-Origin": "*",
          "Access-Control-Allow-Methods": "GET, OPTIONS",
          "Access-Control-Allow-Headers": "*",
          "Cache-Control": "no-store",
        },
        body: fixture,
      });
    });

    try {
      await page.goto(`${baseUrl.replace(/\/$/u, "")}/${route}`, {
        waitUntil: "domcontentloaded",
        timeout: 60_000,
      });
      const renderer = page.getByLabel(new RegExp(`^${rendererName}`, "u"));
      await renderer.waitFor({ state: "attached", timeout: 60_000 });
      if (profile.shortScreen) await scrollToEnd(page, renderer, 1);
      await renderer.waitFor({ state: "visible", timeout: 10_000 });
      await page.mouse.move(profile.width / 2, profile.height / 2);
      await page.waitForTimeout(500);
      check(failures, `${profile.name}: sparse fixture requested`, forecastRequests > 0);

      // Force a composited frame after the Wasm canvas finishes loading.
      await page.setViewportSize({ width: profile.width + 1, height: profile.height });
      await page.setViewportSize({ width: profile.width, height: profile.height });
      await page.waitForTimeout(200);
      const initialPath = resolve(outputDirectory, `${profile.name}.png`);
      await page.screenshot({ path: initialPath, fullPage: true });
      const cursorStates = [];
      // Each readout is the whole cursor label, "<hour and height>; <value>", matched exactly. Only heights and
      // values that depend on the tap position or on interpolation are patterns, and those match whole tokens.
      const selections = [
        { hourIndex: 0, y: surfaceRowY, expected: "06h  10 m AGL; 8 km/h  350°" },
        { hourIndex: 1, y: surfaceRowY, expected: "07h  10 m AGL; No data" },
        { hourIndex: 2, y: surfaceRowY, expected: "08h  10 m AGL; 0 km/h  0°" },
        { hourIndex: 3, y: () => 5, xOffset: 5, expected: /^09h {2}\d+ m ASL; No data$/u },
        { hourIndex: 0, y: (height) => altitudeY(height, 1.5), xOffset: 5,
          expected: /^06h {2}\d+ m ASL; ≈ \d+ km\/h {2}\d+°$/u },
        // Above the highest source level the top level is held.
        { hourIndex: 0, y: () => 5, xOffset: 5, expected: /^06h {2}\d+ m ASL; ≈ 30 km\/h {2}270°$/u },
        // A tap near a source glyph snaps to its exact hourly height.
        { hourIndex: 0, y: (height) => altitudeY(height, 2.357) + 6, expected: "06h  2357 m ASL; 30 km/h  270°" },
        { hourIndex: 16, y: surfaceRowY, expected: "22h  10 m AGL; No data" },
      ];

      for (const [index, selection] of selections.entries()) {
        await clickChart(page, renderer, profile, selection);
        for (let attempt = 0; attempt < 30; attempt++) {
          const current = await readCursorState(renderer);
          if (readoutMatches(selection.expected, current.value)) break;
          await page.waitForTimeout(100);
        }
        const state = await readCursorState(renderer);
        cursorStates.push(state);
        check(
          failures,
          `${profile.name}: selection ${index + 1} reads ${selection.expected}, got ${JSON.stringify(state.value)}`,
          readoutMatches(selection.expected, state.value),
        );
        const shotPath = resolve(outputDirectory, `${profile.name}-selection-${index + 1}.png`);
        await page.screenshot({ path: shotPath, fullPage: true });
      }

      check(failures, `${profile.name}: no uncaught browser exceptions`, runtimeExceptions.length === 0);
      results.push({
        profile,
        forecastRequests,
        runtimeExceptions,
        initialScreenshot: initialPath,
        cursorStates,
      });
    } catch (error) {
      failures.push(`${profile.name}: ${error instanceof Error ? error.stack ?? error.message : String(error)}`);
      results.push({ profile, forecastRequests, runtimeExceptions });
    } finally {
      await context.close();
    }
  }
} finally {
  await browser.close();
}

const report = {
  schemaVersion: 1,
  baseUrl,
  route,
  fixture: "sparse synthetic wind derived from the checked-in Brauneck snapshot",
  outputDirectory,
  profiles: results,
  passed: failures.length === 0,
  failures,
};
const reportPath = resolve(outputDirectory, "report.json");
await writeFile(reportPath, `${JSON.stringify(report, null, 2)}\n`);
console.log(JSON.stringify({ passed: report.passed, profiles: results.length, reportPath, failures }, null, 2));
if (failures.length > 0) process.exitCode = 1;

function sparseWindFixture(source) {
  source.elevation = 580;
  const hourly = source.hourly;
  const count = hourly.time.length;
  for (const [key, values] of Object.entries(hourly)) {
    if (key.startsWith("wind_speed_") || key.startsWith("wind_direction_")) {
      hourly[key] = Array(count).fill(null);
    }
  }

  setWind("wind_speed_10m", 6, 8);
  setWind("wind_direction_10m", 6, 350);
  setWind("wind_speed_10m", 8, 0);
  setWind("wind_direction_10m", 8, 0);

  const levels = [
    { hour: 6, pressure: 925, height: 614, speed: 12, direction: 10 },
    { hour: 6, pressure: 900, height: 620, speed: 16, direction: 90 },
    { hour: 6, pressure: 700, height: 2357, speed: 30, direction: 270 },
    { hour: 7, pressure: 925, height: 625, speed: 0, direction: 0 },
    { hour: 7, pressure: 700, height: 2398, speed: 24, direction: 90 },
    { hour: 8, pressure: 700, height: 1500, speed: 18, direction: 180 },
  ];
  for (const level of levels) {
    setWind(`wind_speed_${level.pressure}hPa`, level.hour, level.speed);
    setWind(`wind_direction_${level.pressure}hPa`, level.hour, level.direction);
    hourly[`geopotential_height_${level.pressure}hPa`][level.hour] = level.height;
  }
  return JSON.stringify(source);

  function setWind(key, hour, value) {
    if (!hourly[key]) hourly[key] = Array(count).fill(null);
    hourly[key][hour] = value;
  }
}

function surfaceRowY(height) {
  return height - WIND_BOTTOM_CHROME + WIND_SURFACE_ROW_HEIGHT / 2;
}

function altitudeY(height, altitudeKm) {
  const plotBottom = height - WIND_BOTTOM_CHROME;
  return plotBottom * (1 - (altitudeKm - FIXTURE_ELEVATION_KM) / VISIBLE_ALTITUDE_RANGE_KM);
}

async function clickChart(page, renderer, profile, { hourIndex, y, xOffset }) {
  let box;
  let top;
  let relativeY;
  if (profile.shortScreen) {
    relativeY = y(SHORT_SCREEN_WIND_CHART_HEIGHT);
    ({ box, top } = await revealShortScreenChartY(page, renderer, SHORT_SCREEN_WIND_CHART_HEIGHT, relativeY));
  } else {
    box = await settledBox(page, renderer);
    if (!isShown(box)) throw new Error("Wind renderer has no visible bounds");
    relativeY = y(box.height);
    top = box.y;
  }
  const columnWidth = (box.width - WIND_AXIS_WIDTH) / FIXTURE_HOURS;
  const targetX = box.x + WIND_AXIS_WIDTH + hourIndex * columnWidth + (xOffset ?? columnWidth / 2);
  if (targetX >= page.viewportSize().width) throw new Error("The selected fixture column is offscreen");
  await page.mouse.click(targetX, top + relativeY);
  await page.waitForTimeout(100);
}

// Scrolls the page until a point of the partly visible chart is on screen and returns the chart top edge. The
// renderer's accessibility bounds cover only the visible part of the chart, so its full position is known only
// while one of its edges is visible.
async function revealShortScreenChartY(page, renderer, chartHeight, relativeY) {
  // At the end of the page the chart bottom sits above the footer and its top is clipped by the visible area.
  let box = await scrollToEnd(page, renderer, 1);
  if (!isShown(box) || box.height >= chartHeight - 0.5) {
    throw new Error(
      `Expected the ${chartHeight} px short-screen chart to be partly scrolled out; bounds: ${JSON.stringify(box)}`,
    );
  }
  const visibleTop = box.y;
  const bottomAnchoredTop = box.y + box.height - chartHeight;
  if (bottomAnchoredTop + relativeY >= visibleTop + REVEAL_MARGIN) return { box, top: bottomAnchoredTop };

  // Otherwise scroll down from the start of the page until the chart top edge is visible and the point fits
  // between it and the clipped chart bottom. Wheel units are calibrated against the visible top edge.
  box = await scrollToEnd(page, renderer, -1);
  let pixelsPerWheelUnit = 1;
  let hiddenWheel = 1;
  let maxWheel = Infinity;
  let lastWheel = 0;
  for (let attempt = 0; attempt < 60; attempt++) {
    const topVisible = isShown(box) && box.y > visibleTop + 0.5;
    if (isShown(box) && !topVisible) {
      // Scrolled past the chart top: undo the last step and continue with smaller ones.
      await wheelForecast(page, lastWheel > 0 ? -lastWheel : -5_000);
      if (lastWheel > 0) maxWheel = Math.max(1, Math.floor(lastWheel / 2));
      lastWheel = 0;
      box = await settledBox(page, renderer);
      continue;
    }
    let wheel;
    if (topVisible) {
      const highestTop = box.y + box.height - relativeY - REVEAL_MARGIN;
      if (box.y <= highestTop) return { box, top: box.y };
      // The chart bottom is clipped here, so box.y + box.height is the bottom of the visible area.
      if (highestTop <= visibleTop + 0.5) {
        throw new Error(`The visible area is too short to show the chart point ${relativeY} px below its top`);
      }
      wheel = Math.round((box.y - (visibleTop + highestTop) / 2) / pixelsPerWheelUnit);
    } else {
      wheel = hiddenWheel;
      hiddenWheel *= 2;
    }
    lastWheel = Math.min(maxWheel, Math.max(1, wheel));
    await wheelForecast(page, lastWheel);
    const next = await settledBox(page, renderer);
    if (topVisible && isShown(next) && next.y > visibleTop + 0.5 && next.y < box.y) {
      pixelsPerWheelUnit = (box.y - next.y) / lastWheel;
    }
    box = next;
  }
  throw new Error(`Could not scroll the short-screen chart point ${relativeY} px below its top into view`);
}

async function scrollToEnd(page, renderer, direction) {
  let box = await settledBox(page, renderer);
  for (let attempt = 0; attempt < 5; attempt++) {
    await wheelForecast(page, direction * 5_000);
    const next = await settledBox(page, renderer);
    if (sameBox(box, next)) return next;
    box = next;
  }
  return box;
}

async function wheelForecast(page, deltaY) {
  // The viewport center is inside the scrolling forecast content; the chart does not consume wheel events.
  const viewport = page.viewportSize();
  await page.mouse.move(viewport.width / 2, viewport.height / 2);
  await page.mouse.wheel(0, deltaY);
  await page.waitForTimeout(300);
}

async function settledBox(page, renderer) {
  let box = await renderer.boundingBox();
  for (let attempt = 0; attempt < 20; attempt++) {
    await page.waitForTimeout(150);
    const next = await renderer.boundingBox();
    if (sameBox(box, next)) return next;
    box = next;
  }
  return box;
}

function isShown(box) {
  return box != null && box.width >= 1 && box.height >= 1;
}

function sameBox(first, second) {
  if (!isShown(first) || !isShown(second)) return isShown(first) === isShown(second);
  return ["x", "y", "width", "height"].every((key) => Math.abs(first[key] - second[key]) < 0.5);
}

async function readCursorState(renderer) {
  return renderer.evaluate((element) => {
    const attributes = Object.fromEntries(
      Array.from(element.attributes, (attribute) => [attribute.name, attribute.value]),
    );
    const value = (attributes["aria-label"] ?? "").replace(/^Wind forecast chart renderer;?\s*/u, "");
    return { value, attributes };
  });
}

// A string must equal the readout; a pattern must be anchored to match all of it.
function readoutMatches(expected, value) {
  return typeof expected === "string" ? value === expected : expected.test(value);
}

function check(failuresList, description, passed) {
  if (!passed) failuresList.push(description);
}
