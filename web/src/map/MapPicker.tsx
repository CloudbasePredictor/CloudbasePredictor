/**
 * Full-screen map location picker (lazy-loaded chunk).
 *
 * This module is the ONLY place that imports `maplibre-gl`, so Vite splits it
 * (and the ~800 kB MapLibre runtime + its CSS) into a separate chunk that loads
 * on demand when the user opens the map — keeping the forecast bundle small.
 *
 * Ported behaviour from `ui/screens/map/MapScreen.kt`: tap to pick a point,
 * choose one of the four base layers, use the device location, tap a favorite
 * star, and confirm to open the forecast. Per-layer attribution is shown at the
 * bottom (see `map/layers.ts`, mirroring `MapAttributionOverlay`).
 */

import "maplibre-gl/dist/maplibre-gl.css";
import type { MapMouseEvent, StyleSpecification } from "maplibre-gl";
import { Map as MapLibreMap, Marker } from "maplibre-gl";
import { useEffect, useRef, useState } from "react";
import type { PlaceLocation } from "../model/placeLocation";
import type { SavedPlace } from "../model/savedPlace";
import { GeolocationRequestError, requestDeviceLocation } from "./geolocation";
import {
  buildMapStyle,
  DEFAULT_MAP_LAYER,
  MAP_LAYER_ORDER,
  MAP_LAYERS,
  type MapLayerId,
} from "./layers";

const SELECTED_COLOR = "#e64a5b";
const FAVORITE_COLOR = "#ffc107";
const INITIAL_ZOOM = 10;
const DEVICE_LOCATION_ZOOM = 12;

export interface MapPickerProps {
  initialLocation: PlaceLocation;
  favorites: readonly SavedPlace[];
  onPick: (location: PlaceLocation) => void;
  onClose: () => void;
}

interface GeoStatus {
  kind: "idle" | "loading" | "error";
  message?: string;
}

function styleFor(layer: MapLayerId): string | StyleSpecification {
  return buildMapStyle(layer) as unknown as string | StyleSpecification;
}

function formatCoords(location: PlaceLocation): string {
  return `${location.latitude.toFixed(4)}, ${location.longitude.toFixed(4)}`;
}

export default function MapPicker({
  initialLocation,
  favorites,
  onPick,
  onClose,
}: MapPickerProps): React.JSX.Element {
  const containerRef = useRef<HTMLDivElement | null>(null);
  const mapRef = useRef<MapLibreMap | null>(null);
  const selectedMarkerRef = useRef<Marker | null>(null);
  const favoriteMarkersRef = useRef<Marker[]>([]);
  const isFirstLayerRun = useRef(true);

  const [layer, setLayer] = useState<MapLayerId>(DEFAULT_MAP_LAYER);
  const [selection, setSelection] = useState<PlaceLocation | null>(null);
  const [showAttribution, setShowAttribution] = useState(false);
  const [geoStatus, setGeoStatus] = useState<GeoStatus>({ kind: "idle" });

  // Create the map once. Later state changes (layer, markers) are applied by the
  // effects below via mapRef, so we intentionally do not recreate the map.
  useEffect(() => {
    const container = containerRef.current;
    if (container === null) return;

    const map = new MapLibreMap({
      container,
      style: styleFor(DEFAULT_MAP_LAYER),
      center: [initialLocation.longitude, initialLocation.latitude],
      zoom: INITIAL_ZOOM,
      attributionControl: false,
    });
    mapRef.current = map;
    // Ornaments are omitted to match the Android map (OrnamentOptions.AllDisabled);
    // scroll / pinch / double-click zoom remain available.
    map.once("load", () => map.resize());
    map.on("click", (event: MapMouseEvent) => {
      setSelection({ latitude: event.lngLat.lat, longitude: event.lngLat.lng });
    });

    // Keep the map sized to its container (the overlay may not have its final
    // height at construction time).
    const resizeObserver = new ResizeObserver(() => map.resize());
    resizeObserver.observe(container);

    return () => {
      resizeObserver.disconnect();
      for (const marker of favoriteMarkersRef.current) marker.remove();
      favoriteMarkersRef.current = [];
      selectedMarkerRef.current?.remove();
      selectedMarkerRef.current = null;
      map.remove();
      mapRef.current = null;
    };
  }, [initialLocation.latitude, initialLocation.longitude]);

  // Switch base layer without recreating the map (markers survive setStyle).
  useEffect(() => {
    const map = mapRef.current;
    if (map === null) return;
    if (isFirstLayerRun.current) {
      isFirstLayerRun.current = false;
      return;
    }
    map.setStyle(styleFor(layer));
  }, [layer]);

  // Move / show the selected-point marker.
  useEffect(() => {
    const map = mapRef.current;
    if (map === null) return;
    if (selection === null) {
      selectedMarkerRef.current?.remove();
      selectedMarkerRef.current = null;
      return;
    }
    if (selectedMarkerRef.current === null) {
      selectedMarkerRef.current = new Marker({ color: SELECTED_COLOR });
    }
    selectedMarkerRef.current.setLngLat([selection.longitude, selection.latitude]).addTo(map);
  }, [selection]);

  // Render favorite stars; tapping one selects it (keeping its name).
  useEffect(() => {
    const map = mapRef.current;
    if (map === null) return;
    for (const marker of favoriteMarkersRef.current) marker.remove();
    favoriteMarkersRef.current = favorites.map((place) => {
      const marker = new Marker({ color: FAVORITE_COLOR })
        .setLngLat([place.longitude, place.latitude])
        .addTo(map);
      const element = marker.getElement();
      element.style.cursor = "pointer";
      element.title = place.name;
      element.addEventListener("click", (event) => {
        event.stopPropagation();
        setSelection({ latitude: place.latitude, longitude: place.longitude, name: place.name });
        map.flyTo({
          center: [place.longitude, place.latitude],
          zoom: Math.max(map.getZoom(), DEVICE_LOCATION_ZOOM),
        });
      });
      return marker;
    });
    return () => {
      for (const marker of favoriteMarkersRef.current) marker.remove();
      favoriteMarkersRef.current = [];
    };
  }, [favorites]);

  async function handleGeolocate(): Promise<void> {
    setGeoStatus({ kind: "loading" });
    try {
      const location = await requestDeviceLocation();
      setGeoStatus({ kind: "idle" });
      setSelection({ latitude: location.latitude, longitude: location.longitude });
      mapRef.current?.flyTo({
        center: [location.longitude, location.latitude],
        zoom: DEVICE_LOCATION_ZOOM,
      });
    } catch (error) {
      const message =
        error instanceof GeolocationRequestError
          ? error.message
          : "Could not determine your location.";
      setGeoStatus({ kind: "error", message });
    }
  }

  const descriptor = MAP_LAYERS[layer];

  return (
    <div className="map-overlay" role="dialog" aria-modal="true" aria-label="Pick a location">
      <div ref={containerRef} className="map-canvas-host" data-testid="map-canvas-host" />

      <div className="map-toolbar-top">
        <button type="button" className="map-icon-button" onClick={onClose} aria-label="Close map">
          ✕
        </button>
        <div className="map-layer-switch">
          {MAP_LAYER_ORDER.map((id) => (
            <button
              key={id}
              type="button"
              className={`map-layer-button${layer === id ? " active" : ""}`}
              onClick={() => setLayer(id)}
              aria-pressed={layer === id}
            >
              {MAP_LAYERS[id].label}
            </button>
          ))}
        </div>
      </div>

      <button
        type="button"
        className="map-icon-button map-geolocate"
        onClick={handleGeolocate}
        disabled={geoStatus.kind === "loading"}
        aria-label="Use my location"
        title="Use my location"
      >
        {geoStatus.kind === "loading" ? "…" : "◎"}
      </button>

      {geoStatus.kind === "error" && (
        <div className="map-geo-error" role="alert">
          {geoStatus.message}
        </div>
      )}

      {selection === null && (
        <p className="map-hint">
          Tap the map to choose a point
          {favorites.length > 0 ? " or tap a saved star" : ""}.
        </p>
      )}

      {selection !== null && (
        <div className="map-selection-card" data-testid="map-selection-card">
          <div className="map-selection-info">
            <span className="map-selection-name">{selection.name ?? formatCoords(selection)}</span>
            <span className="map-selection-coords">{formatCoords(selection)}</span>
          </div>
          <div className="map-selection-actions">
            <button type="button" className="button-ghost" onClick={() => setSelection(null)}>
              Clear
            </button>
            <button
              type="button"
              className="button-primary"
              onClick={() => onPick(selection)}
              data-testid="map-show-forecast"
            >
              Show forecast
            </button>
          </div>
        </div>
      )}

      <div className="map-attribution">
        <button
          type="button"
          className="map-attribution-toggle"
          onClick={() => setShowAttribution((value) => !value)}
          aria-expanded={showAttribution}
        >
          {descriptor.attributionCompact}
        </button>
        {showAttribution && (
          <div className="map-attribution-full">{descriptor.attributionFull}</div>
        )}
      </div>
    </div>
  );
}
