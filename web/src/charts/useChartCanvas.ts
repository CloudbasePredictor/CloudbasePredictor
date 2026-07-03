/**
 * React hook that wires a `<canvas>` to a draw callback with `devicePixelRatio`
 * scaling and automatic redraw on resize / state change.
 */

import { useEffect, useRef } from "react";
import { type CanvasLayout, setupHiDpiCanvas } from "./canvasKit";

export function useChartCanvas(
  draw: (layout: CanvasLayout) => void,
): React.RefObject<HTMLCanvasElement | null> {
  const ref = useRef<HTMLCanvasElement | null>(null);
  const drawRef = useRef(draw);
  drawRef.current = draw;

  // Repaint whenever `draw` changes. `draw` is memoised on the caller's state
  // (data, cursor, zoom…), so this covers every visual update.
  useEffect(() => {
    const canvas = ref.current;
    if (canvas === null) return;
    const layout = setupHiDpiCanvas(canvas);
    if (layout === null) return;
    draw(layout);
  }, [draw]);

  // Repaint with the latest `draw` on resize.
  useEffect(() => {
    const canvas = ref.current;
    if (canvas === null) return;
    const observer = new ResizeObserver(() => {
      const layout = setupHiDpiCanvas(canvas);
      if (layout === null) return;
      drawRef.current(layout);
    });
    observer.observe(canvas);
    return () => observer.disconnect();
  }, []);

  return ref;
}
