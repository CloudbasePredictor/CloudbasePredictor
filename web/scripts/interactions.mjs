// Interaction verification (dev tool). Drives the Stüve tap/heating and the
// Thermic wheel zoom, capturing before/after screenshots to web/screenshots/.

import { mkdirSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import { chromium } from "playwright-core";

const __dirname = dirname(fileURLToPath(import.meta.url));
const outDir = join(__dirname, "..", "screenshots");
mkdirSync(outDir, { recursive: true });

const BASE = process.env.PREVIEW_URL ?? "http://localhost:4173/CloudbasePredictor/?data=fixture";
const EXECUTABLE = process.env.CHROMIUM_PATH ?? "/usr/bin/chromium";

const browser = await chromium.launch({
  executablePath: EXECUTABLE,
  args: ["--no-sandbox", "--disable-gpu", "--hide-scrollbars"],
});
const context = await browser.newContext({
  viewport: { width: 412, height: 915 },
  deviceScaleFactor: 2,
});
const page = await context.newPage();
await page.goto(BASE, { waitUntil: "networkidle" });
await page.waitForSelector(".forecast-tabs");

// --- Stüve: tap inside the plot to place a pinned parcel cursor ---
await page.getByRole("button", { name: /Stüve/ }).click();
await page.waitForTimeout(400);
const canvas = page.locator("canvas.chart-canvas");
const box = await canvas.boundingBox();
await page.mouse.click(box.x + box.width * 0.55, box.y + box.height * 0.5);
await page.waitForTimeout(300);
await page.screenshot({ path: join(outDir, "phone-stuve-tapped.png") });
console.log("wrote phone-stuve-tapped.png");

// --- Stüve: drag the bottom heating handle to the right ---
const handleY = box.y + box.height - 44; // near plotBottom where the handle sits
await page.mouse.move(box.x + box.width * 0.5, handleY);
await page.mouse.down();
await page.mouse.move(box.x + box.width * 0.68, handleY, { steps: 8 });
await page.mouse.up();
await page.waitForTimeout(300);
await page.screenshot({ path: join(outDir, "phone-stuve-heating.png") });
console.log("wrote phone-stuve-heating.png");

// --- Thermic: wheel zoom in (scroll up lowers the visible top altitude) ---
await page.getByRole("button", { name: /Thermic/ }).click();
await page.waitForTimeout(300);
const tbox = await canvas.boundingBox();
await page.mouse.move(tbox.x + tbox.width / 2, tbox.y + tbox.height / 2);
for (let i = 0; i < 6; i++) {
  await page.mouse.wheel(0, -120);
  await page.waitForTimeout(60);
}
await page.waitForTimeout(300);
await page.screenshot({ path: join(outDir, "phone-thermic-zoomed.png") });
console.log("wrote phone-thermic-zoomed.png");

await context.close();
await browser.close();
console.log("done");
