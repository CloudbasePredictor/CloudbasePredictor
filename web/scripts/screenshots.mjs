// Visual-verification screenshot helper (dev tool; not part of the app bundle).
//
// Captures the four forecast views, the settings popover, the footer and the
// map picker in BOTH light and dark themes, and fails if any console error or
// page error is seen. Requires a running preview/dev server and a Chromium
// binary. Run:
//   npm run build
//   npm run preview -- --port 4173 --strictPort        # in another shell
//   npm i -D --no-save playwright-core                  # if not present
//   CHROMIUM_PATH=/usr/bin/chromium node scripts/screenshots.mjs
//
// Output goes to web/screenshots/ (gitignored). Uses fixture data mode so the
// render is deterministic. Theme is driven by emulated `prefers-color-scheme`
// (the app defaults to themeMode = "system").

import { mkdirSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import { chromium } from "playwright-core";

const __dirname = dirname(fileURLToPath(import.meta.url));
const outDir = join(__dirname, "..", "screenshots");
mkdirSync(outDir, { recursive: true });

const BASE = process.env.PREVIEW_URL ?? "http://localhost:4173/CloudbasePredictor/?data=fixture";
const EXECUTABLE = process.env.CHROMIUM_PATH ?? "/usr/bin/chromium";
const TABS = ["thermic", "wind", "cloud", "stuve"];
const THEMES = ["light", "dark"];
const LABELS = { thermic: "Thermic", wind: "Wind", cloud: "Cloud", stuve: "Stüve" };

const viewports = {
  phone: { width: 412, height: 915, deviceScaleFactor: 2 },
  desktop: { width: 1280, height: 900, deviceScaleFactor: 1 },
};

const consoleIssues = [];

const browser = await chromium.launch({
  executablePath: EXECUTABLE,
  // Software WebGL so the map picker can render tiles headlessly.
  args: [
    "--no-sandbox",
    "--hide-scrollbars",
    "--enable-unsafe-swiftshader",
    "--use-gl=angle",
    "--use-angle=swiftshader",
  ],
});

function watch(page, tag) {
  page.on("console", (msg) => {
    if (msg.type() === "error") consoleIssues.push(`[${tag}] console.error: ${msg.text()}`);
  });
  page.on("pageerror", (err) => consoleIssues.push(`[${tag}] pageerror: ${err.message}`));
}

for (const theme of THEMES) {
  for (const [device, viewport] of Object.entries(viewports)) {
    const context = await browser.newContext({
      viewport,
      deviceScaleFactor: viewport.deviceScaleFactor,
      colorScheme: theme,
    });
    const page = await context.newPage();
    watch(page, `${theme}-${device}`);
    await page.goto(BASE, { waitUntil: "networkidle" });
    await page.waitForSelector(".forecast-tabs", { timeout: 15000 });

    for (const tab of TABS) {
      await page.getByRole("button", { name: new RegExp(LABELS[tab]) }).click();
      await page.waitForTimeout(600);
      const file = join(outDir, `${theme}-${device}-${tab}.png`);
      await page.screenshot({ path: file });
      console.log("wrote", file);
    }

    if (device === "phone") {
      // Settings popover.
      await page.getByRole("button", { name: "Settings" }).click();
      await page.waitForSelector(".settings-popover", { timeout: 5000 });
      await page.screenshot({ path: join(outDir, `${theme}-settings.png`) });
      await page.keyboard.press("Escape").catch(() => {});

      // Footer attributions expanded.
      await page.locator(".app-footer-sources > summary").click();
      await page.waitForTimeout(200);
      await page.screenshot({ path: join(outDir, `${theme}-footer.png`) });

      // Map picker.
      await page.getByRole("button", { name: "Map", exact: true }).click();
      await page.waitForSelector(".maplibregl-canvas", { timeout: 15000 });
      await page.waitForTimeout(3000);
      await page.screenshot({ path: join(outDir, `${theme}-map.png`) });
    }

    await context.close();
  }
}

await browser.close();

if (consoleIssues.length > 0) {
  console.log(`\nCONSOLE ISSUES (${consoleIssues.length}):`);
  for (const issue of consoleIssues) console.log(`  ${issue}`);
  process.exit(1);
}
console.log("\ndone — no console errors");
