// PWA icon rasteriser (dev tool; not part of the app bundle).
//
// Renders public/icon.svg to the PNG sizes referenced by the web manifest and
// the apple-touch-icon. Committed PNGs mean the site needs no runtime image
// tooling. Re-run after editing public/icon.svg:
//
//   npm i -D --no-save playwright-core                 # if not present
//   CHROMIUM_PATH=/usr/bin/chromium node scripts/icons.mjs

import { readFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import { chromium } from "playwright-core";

const __dirname = dirname(fileURLToPath(import.meta.url));
const pub = join(__dirname, "..", "public");
const svg = readFileSync(join(pub, "icon.svg"), "utf8");
const EXECUTABLE = process.env.CHROMIUM_PATH ?? "/usr/bin/chromium";

const targets = [
  { name: "icon-192.png", size: 192 },
  { name: "icon-512.png", size: 512 },
  { name: "apple-touch-icon.png", size: 180 },
];

const browser = await chromium.launch({
  executablePath: EXECUTABLE,
  args: ["--no-sandbox", "--disable-gpu", "--force-color-profile=srgb"],
});

for (const { name, size } of targets) {
  const context = await browser.newContext({ viewport: { width: size, height: size } });
  const page = await context.newPage();
  const html = `<!doctype html><meta charset="utf8"><style>*{margin:0;padding:0}html,body{width:${size}px;height:${size}px}svg{display:block;width:${size}px;height:${size}px}</style>${svg}`;
  await page.setContent(html, { waitUntil: "networkidle" });
  await page.screenshot({ path: join(pub, name) });
  await context.close();
  console.log("wrote", name);
}

await browser.close();
console.log("done");
