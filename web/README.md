# Cloudbase Predictor — Web

A standalone, fully client-side web version of Cloudbase Predictor: thermic,
wind, cloud and Stüve forecasts for soaring pilots. It reuses the Open-Meteo
data and the forecast math from the Android app, ported to TypeScript.

The site is a static single-page app deployed to GitHub Pages at
`https://cloudbasepredictor.github.io/CloudbasePredictor/`. No backend, no
accounts, no API keys.

## Status

The data layer (models, Open-Meteo client, response conversion, in-memory
cache), the bit-exact forecast engine (verified by golden fixtures), and the
four forecast views (Thermic / Wind / Cloud / Stüve, rendered on Canvas 2D) are
implemented. The app shell has a place header, model picker, view tabs and a day
selector, and is mobile-first (responsive to 360px). Interactions: tap/drag/pin
cursor and a draggable heating handle on the Stüve chart, plus wheel/pinch zoom
for the altitude range on the chart views. The Phase 1 data debug page is still
reachable at `#/debug`.

Location, favorites and sharing are implemented:

- **Map location picker** (`maplibre-gl`, lazy-loaded as a separate chunk) with
  the four base layers ported from the Android app — OpenFreeMap Liberty,
  OpenTopoMap, NASA GIBS true-color and Esri World Imagery — each with its own
  attribution shown on the map.
- **Browser geolocation** ("use my location") with graceful denial/timeout
  handling.
- **Favorites** stored in `localStorage` (mirroring `SavedPlace`): a header star
  toggles the current place, and a favorites panel lists/opens/removes them.
- **Shareable URL state** in the hash
  (`#/forecast?lat=..&lon=..&model=..&day=..&hour=..&view=..`): opening a link
  reproduces the exact view, and back/forward navigate between states. In live
  mode the model picker switches models and refetches (fixture mode keeps a
  single model, with a hint).

Polish (settings, theming, attribution, resilience) is implemented:

- **Settings** (gear in the header) for the **display-unit preset**
  (`Metric km/h` / `Metric m/s` / `Imperial` / `Aviation`, ported from
  `DisplayUnits.kt`) and the **theme** (`System` / `Light` / `Dark`). Both persist
  to `localStorage` and take effect immediately — units re-render the charts,
  theme flips a `data-theme` attribute (CSS variables) and the Canvas palette.
- **Dark theme** with colors from `ui/theme/Theme.kt` (`darkColorScheme`),
  defaulting to `prefers-color-scheme` with the manual override above. A tiny
  inline script in `index.html` applies the saved theme before first paint to
  avoid a flash.
- **Attribution footer**: Open-Meteo data credit, per-layer map attributions
  (reused from `map/layers.ts`), the GPL-3.0 note, and links to the GitHub repo
  and the F-Droid listing. Collapsed on mobile behind a `<details>`.
- **Loading / error / fallback states**: a spinner while fetching, a readable
  error panel with a Retry button, and the model fallback chain
  (`fetchHourlyForecastWithFallback` + `FALLBACK_CHAINS`) so an out-of-coverage
  model degrades to the next model in its chain with a visible hint instead of
  erroring.
- **Installable (PWA)**: a web manifest and cloud icon so pilots can add it to a
  home screen. Deliberately **no service worker / offline caching** (deferred).

## Tech stack

| Concern | Choice |
|---|---|
| Build | Vite (`base: '/CloudbasePredictor/'`) |
| UI | React 19 + TypeScript (strict) |
| Map | maplibre-gl (lazy-loaded chunk) |
| State | React hooks + small `localStorage`-backed external stores |
| Theming | CSS variables + `data-theme`; Canvas palette from `charts/theme.ts` |
| PWA | Hand-authored `manifest.webmanifest` + icons (no service worker) |
| Tests | Vitest |
| Lint/format | Biome |
| License | GPL-3.0-or-later (same as the app) |

## Commands

Run these from the `web/` directory.

```bash
npm install       # install dependencies
npm run dev       # start the Vite dev server
npm run build     # type-check and produce a production build in dist/
npm run preview   # preview the production build locally
npm test          # run the unit tests once (vitest run)
npm run test:watch # run the unit tests in watch mode
npm run typecheck # tsc --noEmit
npm run lint      # biome check .
npm run lint:fix  # biome check --write .
```

### Dev data mode

To render fixed, deterministic data instead of hitting the network, append
`?data=fixture` to the URL. It loads the bundled Brauneck ICON-Seamless capture
(`src/api/__fixtures__/brauneck_icon_seamless_20260418.json`). `npm run dev`
defaults to fixture mode (offline-friendly); the production build defaults to a
live fetch. Force either with `?data=fixture` or `?data=live`.

### Visual verification (optional dev tool)

`scripts/screenshots.mjs` and `scripts/interactions.mjs` drive the built app with
a headless Chromium and write PNGs to `web/screenshots/` (gitignored) for
comparison with the Android screenshots in `../docs/screenshots/`.
`screenshots.mjs` captures the four views, the settings popover, the footer and
the map in **both light and dark** themes and exits non-zero on any console
error. `scripts/acceptance.mjs` scripts the full Phase 4 flow (open → pick a
point on the map → forecast → favorite → share URL → open a fresh clean-storage
context → same forecast) and asserts it, including browser geolocation and the
URL round-trip. `scripts/icons.mjs` re-rasterises `public/icon.svg` to the PWA
PNG sizes. They need a running preview server and `playwright-core`:

```bash
npm i -D --no-save playwright-core                # not a saved dependency
npm run build
npm run preview -- --port 4173 --strictPort &     # in another shell
CHROMIUM_PATH=/usr/bin/chromium node scripts/screenshots.mjs
CHROMIUM_PATH=/usr/bin/chromium node scripts/interactions.mjs
CHROMIUM_PATH=/usr/bin/chromium node scripts/acceptance.mjs
CHROMIUM_PATH=/usr/bin/chromium node scripts/icons.mjs      # only after editing icon.svg
```

The map needs WebGL; when running headless Chromium without a GPU, pass the
SwiftShader flags the acceptance script already uses
(`--enable-unsafe-swiftshader --use-gl=angle --use-angle=swiftshader`).

## Project layout

```
web/
  index.html            includes the manifest link + pre-paint theme script
  public/               static assets copied verbatim: manifest.webmanifest, icon.svg, icon PNGs
  scripts/              headless-Chromium screenshot / acceptance / icon helpers (dev tools)
  src/
    main.tsx            app entry
    App.tsx             hash router: forecast app + #/debug page
    forecast/
      ForecastApp.tsx   app shell: header, tabs, day selector, view switching
      AppFooter.tsx     data/map attributions + license + repo/F-Droid links
      urlState.ts       shareable forecast route <-> URL-hash encode/parse
    settings/
      settingsStore.ts  localStorage unit-preset + theme-mode store (framework-free)
      useSettings.ts    React bindings; applies data-theme to <html>
      SettingsMenu.tsx  header gear + units/theme popover
    map/
      layers.ts         four base-map styles + attributions (no maplibre import)
      geolocation.ts    browser geolocation helper (denial/timeout handling)
      MapPicker.tsx     lazy-loaded map picker (the only maplibre-gl importer)
    favorites/
      favoritesStore.ts localStorage favorites CRUD + external store
      FavoritesPanel.tsx favorites list UI
    data/forecastData.ts fixture/live forecast source, parameterized by location + model
    charts/
      theme.ts          light + dark Canvas palettes extracted from ui/theme
      canvasKit.ts       Canvas 2D drawing helpers (mirror Compose DrawScope/Paint)
      colorScales.ts     thermic-strength + wind-speed color scales
      viewport.ts        altitude-range zoom model
      altitudeAxis.ts    shared altitude-axis geometry (Thermic/Wind)
      useChartCanvas.ts  DPR-aware canvas hook
      useAltitudeZoom.ts wheel/pinch zoom gesture
      thermicChart.ts    Thermic UI model + display aggregation + builder
      windChart.ts       Wind UI model + builder
      cloudChart.ts      Cloud UI model + builder
      cclForForecast.ts  per-hour CCL helper (shared by Stüve/Wind)
      ThermicChartView.tsx / WindChartView.tsx / CloudChartView.tsx
      stuve/
        model.ts         Stüve UI model + parcel/moisture builders
        geometry.ts      Skew-T projection + temperature-axis geometry
        primitives.ts    wind-barb geometry
        cursor.ts        cursor state + readout
        builder.ts       buildStuveChartFromData
        StuveChartView.tsx
    engine/
      engineDriver.ts    shared model-data -> engine-input driver (used by the
                         Thermic builder and the golden test)
      ...                ported forecast engine (ParcelAnalysis, CclAnalysis, ...)
    model/              ported data models (ForecastModel, units, ...)
    api/                Open-Meteo client, types, conversion, cache, __fixtures__/
```

## Source-of-truth mapping (Kotlin → TypeScript)

The math and data model are ported 1:1 from the Android sources so files stay
diffable across languages:

| Kotlin (`app/src/main/java/com/cloudbasepredictor/`) | TypeScript (`web/src/`) |
|---|---|
| `model/ForecastModel.kt` | `model/forecastModel.ts` |
| `model/WeatherCode.kt` | `model/weatherCode.ts` |
| `model/PlaceLocation.kt` | `model/placeLocation.ts` |
| `model/SavedPlace.kt` | `model/savedPlace.ts` |
| `model/DailyForecast.kt`, `model/ForecastMode.kt` | `model/dailyForecast.ts`, `model/forecastMode.ts` |
| `data/units/DisplayUnits.kt` | `model/units.ts` |
| `data/units/UnitSettingsRepository.kt` | `settings/settingsStore.ts` (localStorage) |
| `data/remote/OpenMeteoApi.kt` | `api/openMeteo.ts` |
| `data/remote/OpenMeteoHourlyForecastResponse.kt` | `api/types.ts` |
| `data/remote/HourlyForecastConversion.kt` | `api/conversion.ts` |
| `data/forecast/ForecastCachePolicy.kt` | `api/cache.ts` (simplified) |
| `ui/map/MapLayerStyle.kt`, `data/map/MapLayerRepository.kt` | `map/layers.ts` |
| `data/place/PlaceRepository.kt`, `FavoritePlacesBackupStore.kt` | `favorites/favoritesStore.ts` (localStorage) |
| `ui/screens/forecast/ForecastChartBuilders.kt` | `charts/{thermicChart,windChart,cloudChart}.ts`, `charts/stuve/builder.ts` |
| `ui/screens/forecast/StuveForecastChartUiModel.kt` | `charts/stuve/model.ts` |
| `ui/screens/forecast/{Thermic,Wind,Cloud}ForecastChartUiModel.kt` | `charts/{thermic,wind,cloud}Chart.ts` |
| `ui/screens/forecast/ForecastColorScales.kt` / `ForecastChartViewport.kt` | `charts/colorScales.ts` / `charts/viewport.ts` |
| `ui/screens/forecast/views/StuveDiagram{Geometry,Primitives,Labels,Canvas,CursorOverlay}.kt` | `charts/stuve/{geometry,primitives,cursor}.ts`, `charts/stuve/StuveChartView.tsx` |
| `ui/screens/forecast/views/{Thermic,Wind,Cloud}ForecastView.kt` | `charts/{Thermic,Wind,Cloud}ChartView.tsx` |
| `ui/theme/{Color,Theme}.kt` (light + dark) | `charts/theme.ts` (Canvas) + `src/styles.css` (`data-theme`) |

## Timezone rule

The Open-Meteo request uses `timezone=auto`. The API returns naive local
timestamp strings (for example `2026-04-18T14:00`). The web app keeps these
strings exactly as-is and derives the date and hour by string operations. They
are never parsed through a browser-zone `Date`, which would otherwise shift the
displayed hours by the difference between the site's zone and the browser's
zone. The cache derives the local "today" from the response
`utc_offset_seconds` for the same reason.

## Test fixtures

`src/api/__fixtures__/brauneck_icon_seamless_20260418.json` is a copy of the
captured Open-Meteo response under `app/src/main/assets/simulated/`. It backs the
conversion unit tests and is the input to the engine golden test.

`src/engine/__fixtures__/brauneck_icon_seamless.json` is the **golden engine
fixture**: the full `ThermalForecastEngine` output for that input, exported from
the Kotlin unit tests. `src/engine/goldenFixture.test.ts` runs the TypeScript
engine on the same input and asserts every field matches within tolerance, so
the TS port stays bit-for-bit faithful to the Kotlin source of truth.

### Regenerating the golden engine fixture

If the Kotlin engine changes, regenerate the fixture from the JVM unit test and
then update the TypeScript port until the golden test passes again. From the
repository root:

```bash
./gradlew :app:testDebugUnitTest --rerun \
  --tests "com.cloudbasepredictor.webfixtures.EngineFixtureExportTest" \
  -DupdateEngineFixtures=true
```

Without `-DupdateEngineFixtures=true` the same test instead **asserts** the
committed fixture is up to date, so it doubles as a regression guard in CI. After
regenerating, run `npm test` here and reconcile any TypeScript engine diffs.

## Deployment

`../.github/workflows/web-deploy.yml` builds and deploys to GitHub Pages on
pushes to `master` that touch `web/**` (and via manual dispatch).
`../.github/workflows/web-ci.yml` type-checks, tests and builds on pull requests
that touch `web/**`.

GitHub Pages must be enabled once in repository Settings → Pages → Source:
"GitHub Actions" before the first deploy can publish.

## Deferred (not implemented yet)

- ParaglidingEarth launch sites: the API sends no CORS header, so browser calls
  are blocked. Deferred until CORS is added or a proxy is available.
- Internationalisation: English only for now; strings will be centralised so the
  Android translations can be reused later.
- Offline support: the PWA manifest makes the site installable, but there is **no
  service worker**, so there is no offline forecast cache (parity with the
  Android Room cache is out of scope). Favorites and settings live in
  `localStorage`; the session forecast cache is in-memory.
