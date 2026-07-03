/**
 * Favorites list panel: tap a place to open its forecast, remove entries, or
 * save the current location. Mirrors the Android `FavoritesListDialog`.
 */

import type { PlaceLocation } from "../model/placeLocation";
import type { SavedPlace } from "../model/savedPlace";

export interface FavoritesPanelProps {
  favorites: readonly SavedPlace[];
  currentLocation: PlaceLocation;
  isCurrentFavorite: boolean;
  onOpen: (place: SavedPlace) => void;
  onRemove: (id: string) => void;
  onSaveCurrent: () => void;
  onClose: () => void;
}

export function FavoritesPanel({
  favorites,
  currentLocation,
  isCurrentFavorite,
  onOpen,
  onRemove,
  onSaveCurrent,
  onClose,
}: FavoritesPanelProps): React.JSX.Element {
  const currentName =
    currentLocation.name ??
    `${currentLocation.latitude.toFixed(4)}, ${currentLocation.longitude.toFixed(4)}`;

  return (
    <div className="favorites-panel" role="dialog" aria-modal="true" aria-label="Favorites">
      <div className="favorites-panel-header">
        <h2>Favorites</h2>
        <button
          type="button"
          className="map-icon-button"
          onClick={onClose}
          aria-label="Close favorites"
        >
          ✕
        </button>
      </div>

      {favorites.length === 0 ? (
        <p className="favorites-empty">No favorites yet. Save a place to find it here.</p>
      ) : (
        <ul className="favorites-list">
          {favorites.map((place) => (
            <li key={place.id} className="favorites-item">
              <button
                type="button"
                className="favorites-open"
                onClick={() => onOpen(place)}
                data-testid="favorite-open"
              >
                <span className="favorites-star" aria-hidden="true">
                  ★
                </span>
                <span className="favorites-text">
                  <span className="favorites-name">{place.name}</span>
                  <span className="favorites-coords">
                    {place.latitude.toFixed(4)}, {place.longitude.toFixed(4)}
                  </span>
                </span>
              </button>
              <button
                type="button"
                className="favorites-remove"
                onClick={() => onRemove(place.id)}
                aria-label={`Remove ${place.name}`}
              >
                ✕
              </button>
            </li>
          ))}
        </ul>
      )}

      {!isCurrentFavorite && (
        <button
          type="button"
          className="button-primary favorites-save-current"
          onClick={onSaveCurrent}
        >
          Save “{currentName}”
        </button>
      )}
    </div>
  );
}
