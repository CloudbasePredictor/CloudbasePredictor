# Web release gates

The production Kotlin/Wasm site is built into
`webApp/build/dist/wasmJs/productionExecutable` and published at
`/CloudbasePredictor/`. CI and the Pages deployment both apply the same release
gates from `config/web-release-gates.json`.

Run the checks after creating a production distribution:

```bash
BINARYEN_CORES=1 ./gradlew :webApp:wasmJsBrowserDistribution
node scripts/web/check-bundle.mjs
CHROME_BIN=/path/to/chrome node scripts/web/check-browser-release.mjs
npm ci
npx playwright install webkit
npm run check:web:webkit-input
```

The single Binaryen worker keeps peak production-optimization behavior stable
across local machines and GitHub-hosted runners; it does not change the emitted
optimization level or release artifact.

`check-bundle.mjs` records the uncompressed and gzip-compressed distribution
size and prevents MapLibre from being linked into a script loaded directly by
`index.html`. `check-browser-release.mjs` serves the production files with
gzip, starts headless Chromium at a mobile viewport, and uses the Chrome
DevTools Protocol (CDP) to measure startup and exercise the release build.

The browser gate verifies:

- time until the Compose viewport is visible;
- a fully loaded forecast state with model status, attribution, favorite, and
  share controls, rather than accepting a loading spinner, error, or unrelated
  canvas as success;
- the initial raw and compressed transfer before opening the map;
- a complete shareable forecast route and an accessible `Copy link` control
  that writes the exact current forecast URL to an instrumented clipboard;
- all four Thermic, Stüve, Wind, and Cloud controls, including their selected
  accessibility state and route state, plus each expected renderer's accessible
  drawing region, nonblank screenshot pixels, and a visual fingerprint distinct
  from the other three renderers;
- the DOM-hosted MapLibre adapter, its real canvas, accessibility-tree layer
  and geolocation controls, and its lazy chunk after startup (by stable name
  when available, otherwise by a MapLibre content marker after minification);
- keyboard entry and Enter submission through the accessible location-search
  field, deterministic result selection, and `Show forecast` navigation;
- saving a favorite, displaying it in Favorites, and retaining both the UI and
  durable storage after a page reload;
- changing units, theme, map layer, startup behavior, and forecast model, then
  observing their selected accessibility states again after a page reload;
- named navigation controls, unnamed interactive controls, and location-search
  keyboard input in Chromium's accessibility tree;
- uncaught exceptions during startup and the complete parity journey.

Forecast and geocoding requests are fulfilled through CDP so the release gate
does not depend on Open-Meteo availability or mutable live data. Forecast
responses use the repository's simulated Brauneck snapshot, and geocoding
returns one fixed Brauneck result. This keeps the test focused on production
Kotlin/Wasm networking, presentation, navigation, and persistence code while
remaining deterministic. The production application does not contain or use
these mocks.

The CDP interceptor validates request method, endpoint, coordinates, selected
model, forecast horizon, pressure-level variables, and geocoding query before
serving a fixture. It therefore fails if the production client silently calls
the wrong Open-Meteo contract instead of merely returning a fixture for any
request on the host.

Each chart renderer exposes a stable accessible name on its drawing surface. The
gate crops that surface from a real Chromium screenshot, verifies that the crop
has a visible size, color variation, and drawn edges, then compares a coarse
visual fingerprint with every other mode. Together with the renderer-specific
accessible name, this fails for a blank surface, a stale renderer, or a route
that displays the wrong chart without relying on brittle golden-image matching.

Compose Multiplatform 1.11 currently exposes some selectable canvas controls as
accessibility buttons without a native checked property. Those controls include
an explicit `selected` or `not selected` accessible state in their name, and the
gate accepts that state only when Chrome does not expose `checked`, `selected`,
or `pressed` directly.

All release gates write machine-readable reports under
`webApp/build/reports/release-gates/`. CI uploads the reports even when a gate
fails.

The focused WebKit input gate runs the same production distribution with
Playwright's `iPhone 15` Mobile Safari device profile. It touch-focuses the
native location-search field, enters text through WebKit keyboard events,
submits with Enter, validates the deterministic geocoding request and visible
result, selects that result by touch, and requires an enabled forecast action.
It also fails on uncaught page errors or a WebKit page crash and writes
`webkit-input.json` beside the Chromium report. On Linux, install browser and
system dependencies with `npx playwright install --with-deps webkit`; CI runs
this gate on macOS because that is the closest automated approximation of
shipping Safari. This remains WebKit engine and Mobile Safari device emulation,
not a physical-iPhone or App Store Safari test.

The budgets intentionally leave limited headroom over the Compose/Skiko
baseline. A budget increase must include the old and new JSON reports in the
pull-request rationale. Optimize or lazy-load regressions before raising a
budget. The browser measurement is a deterministic CI comparison on localhost;
it is not a claim about real-user network latency.

The gate does not replace separate real-backend E2E tests, golden-image visual
regression review, geolocation permission tests, or live map-tile/provider
monitoring. It proves that the production MapLibre module is deferred and can
mount a real map canvas, but intentionally does not compare map pixels or
require a third-party tile response.
