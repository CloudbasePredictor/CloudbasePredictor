/**
 * Browser geolocation helper with graceful denial / timeout handling.
 *
 * Mirrors the intent of the Android "current location" button
 * (`ui/screens/map/MapScreen.kt`): request the device position on explicit user
 * action and surface a clear message on denial or timeout, never blocking the
 * rest of the flow.
 */

export type GeolocationErrorKind = "unsupported" | "permission-denied" | "unavailable" | "timeout";

export class GeolocationRequestError extends Error {
  readonly kind: GeolocationErrorKind;

  constructor(kind: GeolocationErrorKind, message: string) {
    super(message);
    this.name = "GeolocationRequestError";
    this.kind = kind;
  }
}

export interface DeviceLocation {
  latitude: number;
  longitude: number;
  accuracyMeters: number | null;
}

const DEFAULT_TIMEOUT_MS = 10_000;

/**
 * Resolve the device's current position, or reject with a
 * {@link GeolocationRequestError} whose `kind` describes what to tell the user.
 */
export function requestDeviceLocation(
  timeoutMs: number = DEFAULT_TIMEOUT_MS,
): Promise<DeviceLocation> {
  return new Promise((resolve, reject) => {
    if (typeof navigator === "undefined" || navigator.geolocation === undefined) {
      reject(
        new GeolocationRequestError("unsupported", "Geolocation is not available in this browser."),
      );
      return;
    }

    navigator.geolocation.getCurrentPosition(
      (position) => {
        resolve({
          latitude: position.coords.latitude,
          longitude: position.coords.longitude,
          accuracyMeters: Number.isFinite(position.coords.accuracy)
            ? position.coords.accuracy
            : null,
        });
      },
      (error) => {
        reject(mapGeolocationError(error));
      },
      { enableHighAccuracy: true, timeout: timeoutMs, maximumAge: 60_000 },
    );
  });
}

export function mapGeolocationError(error: GeolocationPositionError): GeolocationRequestError {
  switch (error.code) {
    case error.PERMISSION_DENIED:
      return new GeolocationRequestError("permission-denied", "Location permission was denied.");
    case error.POSITION_UNAVAILABLE:
      return new GeolocationRequestError("unavailable", "Your location is currently unavailable.");
    case error.TIMEOUT:
      return new GeolocationRequestError("timeout", "Timed out while getting your location.");
    default:
      return new GeolocationRequestError("unavailable", "Could not determine your location.");
  }
}
