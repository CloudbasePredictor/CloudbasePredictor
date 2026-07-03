/**
 * Attaches wheel + two-finger pinch zoom for the visible altitude range.
 *
 * Simplification vs the Compose `detectForecastZoomGestures`: mouse-wheel zoom is
 * added for desktop (Android has no wheel). Pinch reuses the ported
 * {@link zoomedTopAltitudeKm} amplification; wheel maps scroll delta directly to
 * a top-altitude scale so it never hits the amplification's negative branch.
 */

import { useEffect } from "react";
import {
  MAX_TOP_ALTITUDE_KM,
  MIN_TOP_ALTITUDE_KM,
  sanitizeTopAltitudeKm,
  zoomedTopAltitudeKm,
} from "./viewport";

const WHEEL_SENSITIVITY = 0.0016;

export function useAltitudeZoom(
  elementRef: React.RefObject<HTMLElement | null>,
  getCurrentTopKm: () => number,
  onChange: (topAltitudeKm: number) => void,
): void {
  useEffect(() => {
    const element = elementRef.current;
    if (element === null) return;

    const handleWheel = (event: WheelEvent): void => {
      event.preventDefault();
      const factor = Math.exp(event.deltaY * WHEEL_SENSITIVITY);
      onChange(
        sanitizeTopAltitudeKm(getCurrentTopKm() * factor, MIN_TOP_ALTITUDE_KM, MAX_TOP_ALTITUDE_KM),
      );
    };

    const pointers = new Map<number, { x: number; y: number }>();
    let previousDistance = 0;
    let gestureTopKm = getCurrentTopKm();

    const distance = (): number => {
      const [a, b] = [...pointers.values()];
      return Math.hypot(a.x - b.x, a.y - b.y);
    };

    const handlePointerDown = (event: PointerEvent): void => {
      pointers.set(event.pointerId, { x: event.clientX, y: event.clientY });
      if (pointers.size === 2) {
        previousDistance = distance();
        gestureTopKm = getCurrentTopKm();
      }
    };
    const handlePointerMove = (event: PointerEvent): void => {
      if (!pointers.has(event.pointerId)) return;
      pointers.set(event.pointerId, { x: event.clientX, y: event.clientY });
      if (pointers.size === 2 && previousDistance > 0) {
        const current = distance();
        if (current > 0) {
          gestureTopKm = zoomedTopAltitudeKm(gestureTopKm, current / previousDistance);
          onChange(gestureTopKm);
          previousDistance = current;
        }
      }
    };
    const handlePointerUp = (event: PointerEvent): void => {
      pointers.delete(event.pointerId);
      if (pointers.size < 2) previousDistance = 0;
    };

    element.addEventListener("wheel", handleWheel, { passive: false });
    element.addEventListener("pointerdown", handlePointerDown);
    element.addEventListener("pointermove", handlePointerMove);
    element.addEventListener("pointerup", handlePointerUp);
    element.addEventListener("pointercancel", handlePointerUp);
    element.addEventListener("pointerleave", handlePointerUp);

    return () => {
      element.removeEventListener("wheel", handleWheel);
      element.removeEventListener("pointerdown", handlePointerDown);
      element.removeEventListener("pointermove", handlePointerMove);
      element.removeEventListener("pointerup", handlePointerUp);
      element.removeEventListener("pointercancel", handlePointerUp);
      element.removeEventListener("pointerleave", handlePointerUp);
    };
  }, [elementRef, getCurrentTopKm, onChange]);
}
