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
- the DOM-hosted MapLibre adapter, its visibly sized real canvas,
  accessibility-tree layer and geolocation controls, and its lazy chunk after
  startup (by stable name when available, otherwise by a MapLibre content
  marker after minification);
- keyboard entry into the accessible manual-add form, saving the typed location
  as a favorite, selecting it by tapping its map marker, and `Show forecast`
  navigation;
- saving a favorite, displaying it in Favorites, and retaining both the UI and
  durable storage after a page reload;
- changing units, theme, map layer, startup behavior, and forecast model, then
  observing their selected accessibility states again after a page reload;
- named navigation controls, unnamed interactive controls, and manual-add
  keyboard input in Chromium's accessibility tree;
- uncaught exceptions during startup and the complete parity journey.

Forecast requests are fulfilled through CDP so the release gate does not depend
on Open-Meteo availability or mutable live data. Forecast responses use the
repository's simulated Brauneck snapshot. This keeps the test focused on
production Kotlin/Wasm networking, presentation, navigation, and persistence
code while remaining deterministic. The production application does not contain
or use these mocks.

The CDP interceptor validates request method, endpoint, coordinates, selected
model, forecast horizon, and pressure-level variables before serving a fixture.
It therefore fails if the production client silently calls the wrong Open-Meteo
contract instead of merely returning a fixture for any request on the host.

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

## Assert transitions, not states

A browser gate must never wait on one condition and then assert a different one.
The app renders through Kotlin and Compose, so a DOM side effect — a hidden form,
a native input's value — lands *before* the Kotlin state it stands for. Waiting on
the DOM proxy and then reading the Kotlin result races that pipeline.

This is not hypothetical. An earlier revision of the WebKit gate tapped a search
result, waited for the map selection card to become visible, then read its label.
A deep link had *already* opened that card, so the wait returned immediately and
the read raced the state update. The check passed or failed depending on how
loaded the runner was, and because the flake landed in the deploy workflow it
skipped the Pages deploy without turning any commit red. On the surviving
manual-add path the same window is measurably open: the form hides 1–48 ms before
the favorite reaches `localStorage`.

So, in every gate:

- prove the value is **absent** before the action, so the assertion cannot be
  vacuously satisfied by pre-existing state (a deep link, a previous run, a
  restored session);
- perform the action;
- poll **the same value you assert** until it appears, rather than polling a
  proxy and then reading.

`checkEventually` in `check-webkit-input.mjs` is that pattern. It still fails a
genuine regression — with the stale value in the report — but it no longer fails
a slow machine.

The focused WebKit input gate runs the same production distribution with
Playwright's `iPhone 15` Mobile Safari device profile. It opens the map's
manual-add form, touch-focuses its native name and coordinate fields, enters
text through WebKit keyboard events, saves, and requires the typed location to
be parsed and written to durable favorite storage.
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

For an explicit developer-side check of the pixels composited on screen, run:

```bash
npm run check:web:visual
npm run check:web:visual -- --url https://cloudbasepredictor.github.io/CloudbasePredictor/
```

This non-CI check automatically starts Xvfb when no display is available, uses
headful Chromium with Mesa software WebGL, and captures the X root with
ImageMagick. It checks both a 1280x900 desktop viewport and a 390x844 touch
viewport. For each profile it requires visible MapLibre geometry and nonblank
pixels in a crop taken from the composited X screen, rather than from the WebGL
backbuffer or Playwright's page screenshot. Full-screen captures, map crops,
pixel metrics, browser errors, and request failures are written under
`webApp/build/reports/visual-check/`.

The command requires `xvfb-run`, ImageMagick's `import` and `convert`, a working
Playwright Chromium installation, and software OpenGL such as Mesa llvmpipe.

## Did the build actually ship?

Every gate above answers *is this build good?*. None answer *did that build reach
production?* — and that gap is how a broken deploy stays invisible. When a release
gate fails inside `web-deploy.yml`, the Deploy job is **skipped**, not failed:
GitHub Pages keeps serving the previous build while the same commit still shows a
green Web CI check. The site has silently fallen behind this way before.

`:webApp` therefore emits `build-info.json` (schema version, app version, and the
built commit) into the distribution, and `check-deployed-freshness.mjs` compares
the commit the live site reports against the default branch:

```bash
node scripts/web/check-deployed-freshness.mjs
SITE_URL=http://127.0.0.1:8080 GRACE_MINUTES=90 node scripts/web/check-deployed-freshness.mjs
```

`web-freshness.yml` runs it every six hours. The check is deliberately
outcome-based: it does not care *why* the site is stale — a failed gate, a skipped
deploy, a broken cron, a Pages outage — so it also catches failure modes nobody
predicted. It allows a grace window (default 90 minutes) so an in-flight deploy is
not reported as a stale site.

## One definition of the gates

`web-ci.yml`, `web-deploy.yml`, and `release.yml` all call
`web-verify.yml` (`workflow_call`) rather than keeping their own copy of the
steps. They used to keep copies, and the copies drifted: `release.yml` never grew
the launch-site snapshot step, so the next tag would have failed its browser gate
on a missing dataset — a breakage nobody could see, because `release.yml` only
runs on tags.

The callers differ only in inputs:

| Workflow | Snapshot | `min-sites` | Publishes |
| --- | --- | --- | --- |
| `web-ci.yml` | fixture | 1 | nothing |
| `web-deploy.yml` | live (ParaglidingEarth) | 8000 | GitHub Pages |
| `release.yml` | fixture | 1 | nothing (APK/AAB only) |

`min-sites` guards the unattended weekly cron. `verify-snapshot.mjs` defaults to a
floor of 1, so a truncated ParaglidingEarth response would otherwise publish a
near-empty map with every gate green; the live dataset carries ~11.5k sites.

Note that `env` does **not** propagate from a calling workflow into a reusable
one, so `BINARYEN_CORES` is set inside `web-verify.yml`. Without it `wasm-opt`
segfaults on multi-core runners.
