import { describe, expect, it } from "vitest";
import {
  placeLocationFromRouteValue,
  placeLocationFromSavedPlace,
  toRouteValue,
  toSavedPlace,
} from "./placeLocation";
import { savedPlaceFromCoordinates } from "./savedPlace";

describe("placeLocation", () => {
  it("formats a route value with six decimals", () => {
    expect(toRouteValue({ latitude: 47.68, longitude: 11.63 })).toBe("47.680000,11.630000");
  });

  it("round-trips through a route value", () => {
    const parsed = placeLocationFromRouteValue("47.680000,11.630000");
    expect(parsed).toEqual({ latitude: 47.68, longitude: 11.63 });
  });

  it("rejects malformed or out-of-range route values", () => {
    expect(placeLocationFromRouteValue("47.68")).toBeNull();
    expect(placeLocationFromRouteValue("a,b")).toBeNull();
    expect(placeLocationFromRouteValue("91.0,11.0")).toBeNull();
    expect(placeLocationFromRouteValue("47.0,181.0")).toBeNull();
    expect(placeLocationFromRouteValue("")).toBeNull();
  });

  it("keeps a trimmed name when converting to a saved place", () => {
    const place = toSavedPlace({ latitude: 47.68, longitude: 11.63, name: "  Brauneck  " });
    expect(place.name).toBe("Brauneck");
    expect(place.latitude).toBe(47.68);
  });

  it("falls back to coordinate name when no name is given", () => {
    const place = toSavedPlace({ latitude: 47.68, longitude: 11.63 });
    expect(place.name).toBe(savedPlaceFromCoordinates(47.68, 11.63).name);
  });

  it("extracts coordinates from a saved place", () => {
    const location = placeLocationFromSavedPlace(savedPlaceFromCoordinates(47.68, 11.63));
    expect(location).toEqual({ latitude: 47.68, longitude: 11.63 });
  });
});
