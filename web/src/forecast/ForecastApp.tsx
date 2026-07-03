/**
 * Forecast app shell: header (place, favorite star, map/favorites/share, model
 * picker, view tabs), the active chart view, and a bottom day selector.
 * Mobile-first and responsive down to 360px.
 *
 * Phase 4 scope: the location, model, day, hour and view live in the URL hash
 * (`#/forecast?lat=..&lon=..&model=..&day=..&hour=..&view=..`) so a link
 * reproduces the exact view and back/forward navigate between states. Location
 * comes from a lazy-loaded map picker, browser geolocation, or favorites
 * (persisted in localStorage).
 */

import {
  lazy,
  Suspense,
  useCallback,
  useEffect,
  useMemo,
  useRef,
  useState,
  useSyncExternalStore,
} from "react";
import { pointsByDate } from "../api/conversion";
import type { HourlyForecastData } from "../api/types";
import { CloudChartView } from "../charts/CloudChartView";
import { buildCloudChartFromData } from "../charts/cloudChart";
import { buildStuveChartFromData } from "../charts/stuve/builder";
import { StuveChartView } from "../charts/stuve/StuveChartView";
import { ThermicChartView } from "../charts/ThermicChartView";
import type { ThemeColors } from "../charts/theme";
import { buildThermicChartFromData } from "../charts/thermicChart";
import { DEFAULT_TOP_ALTITUDE_KM } from "../charts/viewport";
import { WindChartView } from "../charts/WindChartView";
import { buildWindChartFromData } from "../charts/windChart";
import { BRAUNECK, FIXTURE_MODEL, isFixtureMode, loadForecast } from "../data/forecastData";
import { FavoritesPanel } from "../favorites/FavoritesPanel";
import {
  addFavorite,
  getFavorites,
  removeFavorite,
  subscribeFavorites,
  toggleFavorite,
} from "../favorites/favoritesStore";
import {
  FORECAST_MODEL_ORDER,
  FORECAST_MODELS,
  type ForecastModelId,
} from "../model/forecastModel";
import { type PlaceLocation, toSavedPlace } from "../model/placeLocation";
import { savedPlaceFromCoordinates } from "../model/savedPlace";
import { type DisplayUnits, resolveDisplayUnits } from "../model/units";
import { SettingsMenu } from "../settings/SettingsMenu";
import { useResolvedTheme, useSettings } from "../settings/useSettings";
import { AppFooter } from "./AppFooter";
import {
  buildForecastHash,
  type ForecastRoute,
  type ForecastTab,
  parseForecastHash,
} from "./urlState";

const MapPicker = lazy(() => import("../map/MapPicker"));

const TABS: Array<{ id: ForecastTab; label: string; icon: string }> = [
  { id: "thermic", label: "Thermic", icon: "↑" },
  { id: "wind", label: "Wind", icon: "≋" },
  { id: "cloud", label: "Cloud", icon: "☁" },
  { id: "stuve", label: "Stüve", icon: "☀" },
];

const WEEKDAYS = ["Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"];
const MONTHS = ["Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"];

function dayLabel(dateStr: string, index: number): { top: string; bottom: string } {
  const date = new Date(`${dateStr}T12:00:00Z`);
  const bottom = `${date.getUTCDate()} ${MONTHS[date.getUTCMonth()]}`;
  return { top: index === 0 ? "Today" : WEEKDAYS[date.getUTCDay()], bottom };
}

function placeDisplayName(location: PlaceLocation): string {
  return location.name ?? `${location.latitude.toFixed(4)}, ${location.longitude.toFixed(4)}`;
}

function defaultRoute(): ForecastRoute {
  return {
    location: BRAUNECK,
    model: isFixtureMode() ? FIXTURE_MODEL : "ICON_SEAMLESS",
    day: 0,
    hour: 12,
    view: "thermic",
  };
}

function routeFromHash(): ForecastRoute {
  return parseForecastHash(window.location.hash) ?? defaultRoute();
}

export function ForecastApp(): React.JSX.Element {
  const fixtureMode = isFixtureMode();

  const settings = useSettings();
  const { colors: themeColors } = useResolvedTheme();
  const displayUnits = useMemo(
    () => resolveDisplayUnits(settings.unitPreset),
    [settings.unitPreset],
  );

  const [route, setRoute] = useState<ForecastRoute>(routeFromHash);
  const [data, setData] = useState<HourlyForecastData | null>(null);
  const [resolvedModel, setResolvedModel] = useState<ForecastModelId | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [visibleTopAltitudeKm, setVisibleTopAltitudeKm] = useState(DEFAULT_TOP_ALTITUDE_KM);

  const [mapOpen, setMapOpen] = useState(false);
  const [favoritesOpen, setFavoritesOpen] = useState(false);
  const [shareCopied, setShareCopied] = useState(false);

  const favorites = useSyncExternalStore(subscribeFavorites, getFavorites);
  const currentPlaceId = useMemo(
    () => savedPlaceFromCoordinates(route.location.latitude, route.location.longitude).id,
    [route.location.latitude, route.location.longitude],
  );
  const currentIsFavorite = favorites.some((place) => place.id === currentPlaceId);

  // Canonicalize the URL on first load so a bare visit becomes a shareable link.
  useEffect(() => {
    if (parseForecastHash(window.location.hash) === null) {
      window.history.replaceState(null, "", buildForecastHash(routeFromHash()));
    }
  }, []);

  // Reproduce the view when the hash changes via back/forward or an edited link.
  useEffect(() => {
    function sync(): void {
      if (window.location.hash.startsWith("#/debug")) return;
      const parsed = parseForecastHash(window.location.hash);
      if (parsed !== null) setRoute(parsed);
    }
    window.addEventListener("popstate", sync);
    window.addEventListener("hashchange", sync);
    return () => {
      window.removeEventListener("popstate", sync);
      window.removeEventListener("hashchange", sync);
    };
  }, []);

  const updateRoute = useCallback(
    (partial: Partial<ForecastRoute>, push: boolean): void => {
      const next: ForecastRoute = { ...route, ...partial };
      const hash = buildForecastHash(next);
      if (push) {
        window.history.pushState(null, "", hash);
      } else {
        window.history.replaceState(null, "", hash);
      }
      setRoute(next);
    },
    [route],
  );

  const openLocation = useCallback(
    (location: PlaceLocation): void => {
      updateRoute({ location, day: 0 }, true);
      setMapOpen(false);
      setFavoritesOpen(false);
    },
    [updateRoute],
  );

  // Load the forecast for the current location + model. Reused by the initial
  // fetch effect and the error-state Retry button; the previous in-flight
  // request is aborted so the newest response always wins.
  const loadController = useRef<AbortController | null>(null);
  const loadForecastNow = useCallback(() => {
    loadController.current?.abort();
    const controller = new AbortController();
    loadController.current = controller;
    setLoading(true);
    setError(null);
    setResolvedModel(null);
    loadForecast({ location: route.location, model: route.model, signal: controller.signal })
      .then((loaded) => {
        if (controller.signal.aborted) return;
        setData(loaded.data);
        setResolvedModel(loaded.resolvedModel);
      })
      .catch((caught: unknown) => {
        if (controller.signal.aborted) return;
        setError(caught instanceof Error ? caught.message : String(caught));
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false);
      });
  }, [route.location, route.model]);

  // Fetch on explicit location/model change only (fair use: never poll).
  useEffect(() => {
    loadForecastNow();
    return () => loadController.current?.abort();
  }, [loadForecastNow]);

  const fellBack = resolvedModel !== null && resolvedModel !== route.model;

  const dates = useMemo(() => (data === null ? [] : [...pointsByDate(data).keys()].sort()), [data]);
  const safeDayIndex = Math.min(route.day, Math.max(0, dates.length - 1));
  const elevationKm = (data?.elevation ?? 0) / 1000;

  const thermicChart = useMemo(
    () => (data === null ? null : buildThermicChartFromData(data, safeDayIndex)),
    [data, safeDayIndex],
  );
  const windChart = useMemo(
    () => (data === null ? null : buildWindChartFromData(data, safeDayIndex, visibleTopAltitudeKm)),
    [data, safeDayIndex, visibleTopAltitudeKm],
  );
  const cloudChart = useMemo(
    () => (data === null ? null : buildCloudChartFromData(data, safeDayIndex)),
    [data, safeDayIndex],
  );
  const stuveChart = useMemo(
    () => (data === null ? null : buildStuveChartFromData(data, safeDayIndex, route.hour)),
    [data, safeDayIndex, route.hour],
  );

  const handleToggleFavorite = useCallback((): void => {
    toggleFavorite(toSavedPlace(route.location));
  }, [route.location]);

  const handleShare = useCallback((): void => {
    const url = window.location.href;
    const done = (): void => {
      setShareCopied(true);
      window.setTimeout(() => setShareCopied(false), 1500);
    };
    if (navigator.clipboard?.writeText !== undefined) {
      navigator.clipboard.writeText(url).then(done).catch(done);
    } else {
      done();
    }
  }, []);

  return (
    <div className="forecast-app">
      <header className="forecast-header">
        <div className="forecast-place">
          <button
            type="button"
            className={`star-button${currentIsFavorite ? " active" : ""}`}
            onClick={handleToggleFavorite}
            aria-pressed={currentIsFavorite}
            aria-label={currentIsFavorite ? "Remove from favorites" : "Add to favorites"}
            title={currentIsFavorite ? "Remove from favorites" : "Add to favorites"}
          >
            {currentIsFavorite ? "★" : "☆"}
          </button>
          <span className="place-name">{placeDisplayName(route.location)}</span>
        </div>

        <div className="forecast-header-actions">
          <button type="button" className="header-button" onClick={() => setMapOpen(true)}>
            Map
          </button>
          <button type="button" className="header-button" onClick={() => setFavoritesOpen(true)}>
            Favorites
          </button>
          <button type="button" className="header-button" onClick={handleShare}>
            {shareCopied ? "Copied" : "Share"}
          </button>
          <SettingsMenu />
        </div>

        <nav className="forecast-tabs" aria-label="Forecast view">
          {TABS.map((entry) => (
            <button
              key={entry.id}
              type="button"
              className={`tab-button${route.view === entry.id ? " active" : ""}`}
              onClick={() => updateRoute({ view: entry.id }, false)}
              aria-pressed={route.view === entry.id}
            >
              <span className="tab-icon" aria-hidden="true">
                {entry.icon}
              </span>
              <span className="tab-label">{entry.label}</span>
            </button>
          ))}
        </nav>

        <div className="forecast-model">
          <select
            className="model-select"
            value={route.model}
            disabled={fixtureMode}
            onChange={(event) =>
              updateRoute({ model: event.target.value as ForecastModelId, day: 0 }, true)
            }
            aria-label="Weather model"
            title={fixtureMode ? "Model switching is disabled in demo data mode" : undefined}
          >
            {FORECAST_MODEL_ORDER.map((id) => (
              <option key={id} value={id}>
                {FORECAST_MODELS[id].displayName}
              </option>
            ))}
          </select>
          <span className="attribution">by open-meteo.com{fixtureMode ? " (demo data)" : ""}</span>
        </div>
      </header>

      <main className="forecast-body">
        {loading && (
          <div className="chart-status" role="status" aria-live="polite">
            <span className="spinner" aria-hidden="true" />
            <span>Loading forecast…</span>
          </div>
        )}
        {!loading && error !== null && (
          <div className="chart-status chart-status-error" role="alert">
            <p className="chart-status-title">Could not load the forecast.</p>
            <p className="chart-status-detail">{error}</p>
            <button type="button" className="button-primary" onClick={loadForecastNow}>
              Retry
            </button>
          </div>
        )}
        {!loading && error === null && data !== null && (
          <>
            {fellBack && resolvedModel !== null && (
              <div className="fallback-hint" role="status">
                {FORECAST_MODELS[route.model].displayName} is not available here — showing{" "}
                {FORECAST_MODELS[resolvedModel].displayName}.
              </div>
            )}
            <ActiveView
              tab={route.view}
              thermicChart={thermicChart}
              windChart={windChart}
              cloudChart={cloudChart}
              stuveChart={stuveChart}
              visibleTopAltitudeKm={visibleTopAltitudeKm}
              elevationKm={elevationKm}
              displayUnits={displayUnits}
              theme={themeColors}
              onVisibleTopAltitudeChange={setVisibleTopAltitudeKm}
              hour={route.hour}
              onHourChange={(hour) => updateRoute({ hour }, false)}
            />
          </>
        )}
      </main>

      <footer className="forecast-footer">
        <div className="day-selector" role="tablist" aria-label="Forecast day">
          {dates.map((dateStr, index) => {
            const label = dayLabel(dateStr, index);
            return (
              <button
                key={dateStr}
                type="button"
                role="tab"
                aria-selected={index === safeDayIndex}
                className={`day-chip${index === safeDayIndex ? " active" : ""}`}
                onClick={() => updateRoute({ day: index }, false)}
              >
                <span className="day-top">{label.top}</span>
                <span className="day-bottom">{label.bottom}</span>
              </button>
            );
          })}
        </div>
        <AppFooter />
      </footer>

      {favoritesOpen && (
        <div className="overlay-scrim">
          <button
            type="button"
            className="overlay-scrim-close"
            aria-label="Dismiss favorites"
            onClick={() => setFavoritesOpen(false)}
          />
          <FavoritesPanel
            favorites={favorites}
            currentLocation={route.location}
            isCurrentFavorite={currentIsFavorite}
            onOpen={(place) =>
              openLocation({
                latitude: place.latitude,
                longitude: place.longitude,
                name: place.name,
              })
            }
            onRemove={(id) => removeFavorite(id)}
            onSaveCurrent={() => addFavorite(toSavedPlace(route.location))}
            onClose={() => setFavoritesOpen(false)}
          />
        </div>
      )}

      {mapOpen && (
        <Suspense fallback={<div className="map-loading">Loading map…</div>}>
          <MapPicker
            initialLocation={route.location}
            favorites={favorites}
            onPick={openLocation}
            onClose={() => setMapOpen(false)}
          />
        </Suspense>
      )}
    </div>
  );
}

interface ActiveViewProps {
  tab: ForecastTab;
  thermicChart: ReturnType<typeof buildThermicChartFromData>;
  windChart: ReturnType<typeof buildWindChartFromData>;
  cloudChart: ReturnType<typeof buildCloudChartFromData>;
  stuveChart: ReturnType<typeof buildStuveChartFromData>;
  visibleTopAltitudeKm: number;
  elevationKm: number;
  displayUnits: DisplayUnits;
  theme: ThemeColors;
  onVisibleTopAltitudeChange: (topAltitudeKm: number) => void;
  hour: number;
  onHourChange: (hour: number) => void;
}

function ActiveView(props: ActiveViewProps): React.JSX.Element {
  const { tab, displayUnits, theme } = props;
  if (tab === "thermic") {
    return props.thermicChart === null ? (
      <NoData />
    ) : (
      <ThermicChartView
        chart={props.thermicChart}
        visibleTopAltitudeKm={props.visibleTopAltitudeKm}
        elevationKm={props.elevationKm}
        displayUnits={displayUnits}
        theme={theme}
        onVisibleTopAltitudeChange={props.onVisibleTopAltitudeChange}
      />
    );
  }
  if (tab === "wind") {
    return props.windChart === null ? (
      <NoData />
    ) : (
      <WindChartView
        chart={props.windChart}
        visibleTopAltitudeKm={props.visibleTopAltitudeKm}
        elevationKm={props.elevationKm}
        displayUnits={displayUnits}
        theme={theme}
        onVisibleTopAltitudeChange={props.onVisibleTopAltitudeChange}
      />
    );
  }
  if (tab === "cloud") {
    return props.cloudChart === null ? (
      <NoData />
    ) : (
      <CloudChartView chart={props.cloudChart} theme={theme} />
    );
  }
  return props.stuveChart === null ? (
    <NoData />
  ) : (
    <div className="stuve-wrap">
      <StuveChartView
        chart={props.stuveChart}
        visibleTopAltitudeKm={props.visibleTopAltitudeKm}
        displayUnits={displayUnits}
        theme={theme}
        onVisibleTopAltitudeChange={props.onVisibleTopAltitudeChange}
      />
      <div className="stuve-time">
        <span className="stuve-time-end">06</span>
        <input
          type="range"
          min={6}
          max={22}
          step={1}
          value={props.hour}
          onChange={(event) => props.onHourChange(Number(event.target.value))}
          aria-label="Forecast hour"
        />
        <span className="stuve-time-end">22</span>
        <span className="stuve-time-value">{String(props.hour).padStart(2, "0")}:00</span>
      </div>
    </div>
  );
}

function NoData(): React.JSX.Element {
  return <div className="chart-message">No forecast data for this day.</div>;
}
