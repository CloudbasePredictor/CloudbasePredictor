/**
 * Unobtrusive site footer: data + map attributions (matching the repository
 * README "Data Sources and Maps" section), the GPL-3.0 license note, and links
 * to the source and the F-Droid listing.
 *
 * The per-layer map attributions are reused verbatim from {@link file://../map/layers.ts}
 * (`attributionFull`) so there is a single source of truth. This module keeps a
 * slim always-visible credit line and tucks the full attributions behind a
 * `<details>` so it stays out of the way on a phone.
 */

import { MAP_LAYER_ORDER, MAP_LAYERS } from "../map/layers";

const REPO_URL = "https://github.com/CloudbasePredictor/CloudbasePredictor";
const FDROID_URL = "https://f-droid.org/packages/com.cloudbasepredictor/";
const OPEN_METEO_URL = "https://open-meteo.com/";
const LICENSE_URL = `${REPO_URL}/blob/master/LICENSE`;

export function AppFooter(): React.JSX.Element {
  return (
    <div className="app-footer">
      <details className="app-footer-sources">
        <summary>Data &amp; map attributions</summary>
        <div className="app-footer-sources-body">
          <p>
            Forecast data by{" "}
            <a href={OPEN_METEO_URL} target="_blank" rel="noreferrer noopener">
              Open-Meteo
            </a>{" "}
            (CC-BY 4.0), including pressure-level model profiles such as ICON and GFS.
          </p>
          <ul className="app-footer-layers">
            {MAP_LAYER_ORDER.map((id) => (
              <li key={id}>
                <span className="app-footer-layer-name">{MAP_LAYERS[id].label}</span>
                <span className="app-footer-layer-attr">{MAP_LAYERS[id].attributionFull}</span>
              </li>
            ))}
          </ul>
        </div>
      </details>

      <p className="app-footer-meta">
        <a href={LICENSE_URL} target="_blank" rel="noreferrer noopener">
          GPL-3.0-or-later
        </a>
        <span aria-hidden="true"> · </span>
        <a href={REPO_URL} target="_blank" rel="noreferrer noopener">
          Source on GitHub
        </a>
        <span aria-hidden="true"> · </span>
        <a href={FDROID_URL} target="_blank" rel="noreferrer noopener">
          Android app on F-Droid
        </a>
      </p>
    </div>
  );
}
