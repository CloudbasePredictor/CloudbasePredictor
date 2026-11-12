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
  { name: "landscape", width: 844, height: 390 },
];
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
      if (profile.name === "landscape") {
        await page.mouse.move(profile.width / 2, profile.height / 2);
        await page.mouse.wheel(0, 400);
        await page.waitForTimeout(300);
      }
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
      const selections = [
        { hourIndex: 0, y: (height) => height - 92, expected: [/06h/u, /10 m AGL/u, /8 km\/h/u, /350°/u] },
        { hourIndex: 1, y: (height) => height - 92, expected: [/07h/u, /No data/u], absent: [/km\/h/u] },
        { hourIndex: 2, y: (height) => height - 92, expected: [/08h/u, /10 m AGL/u, /0 km\/h/u, /0°/u] },
        { hourIndex: 3, y: () => 5, xOffset: 5, expected: [/09h/u, /No data/u], absent: [/km\/h/u] },
        { hourIndex: 0, y: (height) => (height - 112) * 0.7, xOffset: 5, expected: [/06h/u, /≈/u] },
        { hourIndex: 0, y: () => 5, xOffset: 5, expected: [/06h/u, /No data/u] },
        { hourIndex: 0,
          y: (height) => height - (profile.name === "landscape" ? 126 : 154),
          xOffset: profile.name === "landscape" ? 125 : 49,
          expected: [/06h/u, /620 m ASL/u, /16 km\/h/u, /90°/u], absent: [/≈/u] },
        { hourIndex: 16, y: (height) => height - 92,
          expected: [/22h/u, /10 m AGL/u, /No data/u], absent: [/km\/h/u] },
      ];

      for (const [index, selection] of selections.entries()) {
        await clickHour(page, renderer, selection.hourIndex, selection.y, selection.xOffset);
        for (let attempt = 0; attempt < 30; attempt++) {
          const current = await readCursorState(renderer);
          if (selection.expected.every((pattern) => pattern.test(current.value))) break;
          await page.waitForTimeout(100);
        }
        const state = await readCursorState(renderer);
        cursorStates.push(state);
        for (const pattern of selection.expected) {
          check(failures, `${profile.name}: selection ${index + 1} includes ${pattern}`, pattern.test(state.value));
        }
        for (const pattern of selection.absent ?? []) {
          check(failures, `${profile.name}: selection ${index + 1} omits ${pattern}`, !pattern.test(state.value));
        }
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

async function clickHour(page, renderer, hourIndex, yForHeight, xOffset) {
  const box = await renderer.boundingBox();
  if (!box) throw new Error("Wind renderer has no visible bounds");
  const canvasHeight = page.viewportSize().height < 480 ? 160 : box.height;
  const labelCapacity = Math.max(1, Math.floor((canvasHeight - 112) / 28) - 1);
  const columnWidth = Math.max(76 * Math.ceil(3 / labelCapacity), (box.width - 60) / 17);
  // Accessibility bounds are clipped to the viewport; scrolling belongs to Compose.
  const probeX = page.viewportSize().width / 2;
  await page.mouse.move(probeX, box.y + Math.min(60, box.height / 2));
  await page.mouse.wheel(-10_000, 0);
  await page.waitForTimeout(150);
  const bounds = await renderer.boundingBox();
  const y = bounds.y + bounds.height - canvasHeight + yForHeight(canvasHeight);
  if (hourIndex === 16) {
    await page.mouse.wheel(10_000, 0);
    await page.waitForTimeout(300);
    await page.mouse.click(bounds.x + bounds.width - columnWidth / 2, y);
  } else {
    const targetX = bounds.x + 60 + hourIndex * columnWidth + (xOffset ?? columnWidth / 2);
    if (targetX >= page.viewportSize().width) throw new Error("The selected fixture column is offscreen");
    await page.mouse.click(targetX, y);
  }
  await page.waitForTimeout(100);
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

function check(failuresList, description, passed) {
  if (!passed) failuresList.push(description);
}
