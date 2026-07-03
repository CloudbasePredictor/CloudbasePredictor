import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { savedPlaceFromCoordinates } from "../model/savedPlace";
import {
  addFavorite,
  getFavorites,
  isFavorite,
  removeFavorite,
  resetFavoritesCacheForTests,
  toggleFavorite,
} from "./favoritesStore";

function createMemoryStorage(): Storage {
  const map = new Map<string, string>();
  return {
    get length() {
      return map.size;
    },
    clear: () => map.clear(),
    getItem: (key: string) => (map.has(key) ? (map.get(key) as string) : null),
    key: (index: number) => Array.from(map.keys())[index] ?? null,
    removeItem: (key: string) => {
      map.delete(key);
    },
    setItem: (key: string, value: string) => {
      map.set(key, String(value));
    },
  };
}

// The store guards all DOM access at call time, so a stubbed `window` with a
// memory-backed localStorage exercises the real persistence path under Node.
beforeEach(() => {
  vi.stubGlobal("window", {
    localStorage: createMemoryStorage(),
    addEventListener: () => {},
  });
  resetFavoritesCacheForTests();
});

afterEach(() => {
  vi.unstubAllGlobals();
  resetFavoritesCacheForTests();
});

describe("favoritesStore", () => {
  it("starts empty", () => {
    expect(getFavorites()).toEqual([]);
  });

  it("adds and persists a favorite", () => {
    const place = savedPlaceFromCoordinates(47.68, 11.63);
    addFavorite({ ...place, name: "Brauneck" });

    expect(isFavorite(place.id)).toBe(true);
    const favorites = getFavorites();
    expect(favorites).toHaveLength(1);
    expect(favorites[0]).toMatchObject({
      id: place.id,
      name: "Brauneck",
      latitude: 47.68,
      longitude: 11.63,
      isFavorite: true,
    });
  });

  it("survives a cache reset by reading back from storage", () => {
    addFavorite({ ...savedPlaceFromCoordinates(46.0, 7.0), name: "Verbier" });
    resetFavoritesCacheForTests();
    expect(getFavorites().map((p) => p.name)).toEqual(["Verbier"]);
  });

  it("deduplicates by id, keeping the newest entry first", () => {
    const first = savedPlaceFromCoordinates(47.68, 11.63);
    addFavorite({ ...first, name: "Old name" });
    addFavorite({ ...savedPlaceFromCoordinates(46.0, 7.0), name: "Verbier" });
    addFavorite({ ...first, name: "New name" });

    const favorites = getFavorites();
    expect(favorites).toHaveLength(2);
    expect(favorites[0]).toMatchObject({ id: first.id, name: "New name" });
    expect(favorites[1]).toMatchObject({ name: "Verbier" });
  });

  it("removes a favorite", () => {
    const place = savedPlaceFromCoordinates(47.68, 11.63);
    addFavorite(place);
    removeFavorite(place.id);
    expect(isFavorite(place.id)).toBe(false);
    expect(getFavorites()).toEqual([]);
  });

  it("toggles favorite membership", () => {
    const place = { ...savedPlaceFromCoordinates(47.68, 11.63), name: "Brauneck" };
    expect(toggleFavorite(place)).toBe(true);
    expect(isFavorite(place.id)).toBe(true);
    expect(toggleFavorite(place)).toBe(false);
    expect(isFavorite(place.id)).toBe(false);
  });

  it("ignores malformed stored entries", () => {
    window.localStorage.setItem(
      "cbp.favorites.v1",
      JSON.stringify({
        schemaVersion: 1,
        places: [
          { name: "bad", latitude: "x", longitude: 7 },
          { name: "out-of-range", latitude: 200, longitude: 7 },
          { name: "good", latitude: 46.0, longitude: 7.0 },
        ],
      }),
    );
    resetFavoritesCacheForTests();
    const favorites = getFavorites();
    expect(favorites).toHaveLength(1);
    expect(favorites[0].name).toBe("good");
  });
});
