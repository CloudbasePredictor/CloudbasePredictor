# Cloudbase Predictor

Weather forecast app for soaring and free-flight pilots (paragliding, hang-gliding, sailplanes).

Built with Jetpack Compose, it visualises atmospheric sounding data from [Open-Meteo](https://open-meteo.com/) so you can quickly assess thermic conditions, wind profiles, and cloud cover for your flying site.

## Preview

<p align="center">
  <img src="docs/screenshots/v0.0.1-light-thermic-forecast.png" width="180" alt="Light theme thermic forecast" />
  <img src="docs/screenshots/v0.0.1-light-wind-forecast-selected.png" width="180" alt="Light theme wind forecast" />
  <img src="docs/screenshots/v0.0.1-light-stuve-forecast-selected.png" width="180" alt="Light theme Stüve diagram" />
  <img src="docs/screenshots/v0.0.1-light-cloud-forecast.png" width="180" alt="Light theme cloud forecast" />
</p>

<p align="center">
  <img src="docs/screenshots/v0.0.1-dark-thermic-forecast-selected.png" width="180" alt="Dark theme thermic forecast" />
  <img src="docs/screenshots/v0.0.1-dark-wind-forecast-selected.png" width="180" alt="Dark theme wind forecast" />
  <img src="docs/screenshots/v0.0.1-dark-stuve-forecast-selected.png" width="180" alt="Dark theme Stüve diagram" />
  <img src="docs/screenshots/v0.0.1-dark-cloud-forecast-map-expanded.png" width="180" alt="Dark theme cloud forecast with expanded map" />
</p>

## Features

- **Thermic forecast** — thermal strength, thermal top, and cloudbase estimates across the day
- **Wind forecast** — wind speed and direction at every pressure level, colour-coded
- **Stüve diagram** — classic atmospheric sounding plot with temperature and dew-point profiles
- **Cloud forecast** — cloud cover at low, mid, and high levels
- **Multiple models** — ICON Seamless, ICON D2, GFS Seamless, and more via Open-Meteo
- **Favourite places** — save your flying sites and switch between them instantly
- **Interactive map** — pick any location on an OpenStreetMap-based map to get a forecast
- **Selectable map layers** — switch between OpenFreeMap, NASA GIBS true-colour imagery, and Esri World Imagery
- **Pinch-to-zoom** — adjust the visible altitude range on all chart views
- **Dark theme** — full Material 3 dark-mode support

## Tech Stack

- Kotlin and Jetpack Compose for the Android UI
- Material 3 for app theming and components
- MapLibre Compose for interactive map rendering
- Retrofit, OkHttp, and Kotlinx Serialization for forecast API access
- Room for local forecast and place storage

## Building

```bash
git clone https://github.com/CloudbasePredictor/CloudbasePredictor.git
cd CloudbasePredictor
./gradlew :app:assembleDebug
```

## Testing

```bash
# Unit tests (JVM)
./gradlew :app:testDebugUnitTest --rerun

# Instrumentation tests (requires emulator)
./gradlew :app:connectedInstrumentationTest --rerun
```

## Data Sources and Maps

- Forecast data is provided by [Open-Meteo](https://open-meteo.com/), including pressure-level forecast profiles from models such as ICON and GFS.
- Paragliding launch site data is provided by [ParaglidingEarth](https://paragliding.earth/) and licensed under Creative Commons Attribution-ShareAlike 3.0.
- Map layers include the [OpenFreeMap](https://openfreemap.org/) Liberty style, [OpenTopoMap](https://opentopomap.org/about), [NASA GIBS](https://www.earthdata.nasa.gov/engage/open-data-services-software/earthdata-developer-portal/gibs-api) true-colour imagery, and [Esri World Imagery](https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer).
- OpenFreeMap uses [OpenMapTiles](https://openmaptiles.org/) and data from [OpenStreetMap contributors](https://www.openstreetmap.org/copyright). OpenTopoMap uses data from OpenStreetMap contributors and SRTM, with map style by OpenTopoMap (CC-BY-SA).
- Maps are rendered in the app with [MapLibre Compose](https://maplibre.org/maplibre-compose/) and MapLibre for Android.

## License

This project is licensed under the [GNU General Public License v3.0 or later](LICENSE).
