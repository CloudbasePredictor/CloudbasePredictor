/**
 * Stüve / Skew-T chart with Windy-like parcel interaction.
 *
 * Canvas 2D port of `StuveDiagramCanvas.kt` + `StuveDiagramCursorOverlay.kt` +
 * `StuveDiagramGestures.kt`. Implements:
 *  - tap to pin / drag to scrub a parcel anchor (2D x,y -> pressure,temperature),
 *  - a draggable bottom heating handle for the parcel start temperature,
 *  - pinch/wheel zoom for the visible top altitude.
 *
 * Simplifications vs Compose are noted inline (bottom-axis label collision
 * layout, and the on-hover thermo guide lines are omitted like the Android
 * canvas which also passes showThermoGuides = false).
 */

import { useCallback, useMemo, useRef, useState } from "react";
import {
  dryAdiabatTempC,
  mixingRatioTemperatureC,
  moistAdiabatTempC,
} from "../../engine/parcelAnalysis";
import { type DisplayUnits, formatAltitudeMeters, formatWindSpeed } from "../../model/units";
import {
  type CanvasLayout,
  type Ctx,
  drawLine,
  drawText,
  fillCircle,
  fillPolygon,
  fillRect,
  fillRoundRect,
  measureText,
  strokeCircle,
  strokeRect,
  type TextPaint,
  withClip,
} from "../canvasKit";
import { gridBackground, mixRgb, rgba, type ThemeColors } from "../theme";
import { useAltitudeZoom } from "../useAltitudeZoom";
import { useChartCanvas } from "../useChartCanvas";
import { buildCursorReadout, type CursorReadout, type SkewTCursorState } from "./cursor";
import {
  altitudeKmToApproxPressureHpa,
  buildInteractiveParcelFromPoint,
  buildInteractiveParcelFromSurface,
  buildReferencePressures,
  buildSkewTProjection,
  buildTemperatureAxisLabels,
  recommendedStuveTopAltitudeKm,
  SKEWT_BOTTOM_PRESSURE,
  SKEWT_MIN_TOP_PRESSURE,
  type SkewTProjection,
  STUVE_DRY_REFERENCE_PRESSURES,
  selectPressureLabels,
  tempAxisSpanC,
} from "./geometry";
import {
  buildMinimalProfileLevels,
  buildRenderableParcelPressures,
  interpolateProfileHeightMeters,
  pressureToApproxHeightMeters,
  STUVE_DRY_ADIABAT_THETAS_K,
  STUVE_MIXING_RATIO_VALUES_GKG,
  STUVE_MOIST_ADIABAT_THETAS_K,
  type StuveForecastChartUiModel,
  type StuveProfilePoint,
} from "./model";
import { buildWindBarbGeometry } from "./primitives";

const LEFT_AXIS_WIDTH = 40;
const RIGHT_ALTITUDE_WIDTH = 42;
const RIGHT_WIND_WIDTH = 58;
const BOTTOM_AXIS_HEIGHT = 34;
const TOP_PADDING = 16;
const HANDLE_TOUCH_RADIUS = 28;
const DRAG_SLOP = 6;

const TEMP_COLOR = "#D83A3A";
const DEWPOINT_COLOR = "#2E6FB5";
const PARCEL_COLOR = "#59A36A";

interface StuveFrame {
  projection: SkewTProjection;
  plotLeft: number;
  plotRight: number;
  plotTop: number;
  plotBottom: number;
  plotWidth: number;
  topPressure: number;
  chartBottomPressure: number;
  handleX: number;
  defaultParcelStartTempC: number;
}

function clamp(value: number, min: number, max: number): number {
  return Math.min(Math.max(value, min), max);
}

export interface StuveChartViewProps {
  chart: StuveForecastChartUiModel;
  visibleTopAltitudeKm: number;
  displayUnits: DisplayUnits;
  theme: ThemeColors;
  onVisibleTopAltitudeChange: (topAltitudeKm: number) => void;
}

export function StuveChartView(props: StuveChartViewProps): React.JSX.Element {
  const { chart, visibleTopAltitudeKm, displayUnits, theme, onVisibleTopAltitudeChange } = props;
  const containerRef = useRef<HTMLDivElement | null>(null);
  const autoFitTopKm = useMemo(() => recommendedStuveTopAltitudeKm(chart), [chart]);
  const effectiveTopKm = Math.max(visibleTopAltitudeKm, autoFitTopKm);

  const [cursor, setCursor] = useState<SkewTCursorState | null>(null);
  const [heatingDeltaC, setHeatingDeltaC] = useState(0);

  // Reset interaction when the sounding changes (new hour / surface pressure).
  const sessionKey = `${chart.selectedHour}:${chart.surfacePressureHpa}`;
  const sessionRef = useRef(sessionKey);
  if (sessionRef.current !== sessionKey) {
    sessionRef.current = sessionKey;
    if (cursor !== null) setCursor(null);
    if (heatingDeltaC !== 0) setHeatingDeltaC(0);
  }

  const topRef = useRef(effectiveTopKm);
  topRef.current = effectiveTopKm;
  useAltitudeZoom(
    containerRef,
    useCallback(() => topRef.current, []),
    onVisibleTopAltitudeChange,
  );

  const frameRef = useRef<StuveFrame | null>(null);
  const profileLevels = useMemo(() => buildMinimalProfileLevels(chart), [chart]);
  const parcelPressures = useMemo(
    () => buildRenderableParcelPressures(chart.surfacePressureHpa, chart.pressureLevels),
    [chart.surfacePressureHpa, chart.pressureLevels],
  );

  const draw = useCallback(
    (layout: CanvasLayout) => {
      frameRef.current = drawStuve(
        layout,
        chart,
        effectiveTopKm,
        displayUnits,
        theme,
        cursor,
        heatingDeltaC,
        profileLevels,
        parcelPressures,
      );
    },
    [
      chart,
      effectiveTopKm,
      displayUnits,
      theme,
      cursor,
      heatingDeltaC,
      profileLevels,
      parcelPressures,
    ],
  );
  const canvasRef = useChartCanvas(draw);

  // --- Interaction state (refs so handlers stay stable) ---
  const pointers = useRef(new Set<number>());
  const heatingDrag = useRef<{ prevX: number } | null>(null);
  const cursorDrag = useRef<{ startX: number; startY: number; dragged: boolean } | null>(null);

  const toLocal = (event: React.PointerEvent): { x: number; y: number } => {
    const rect = event.currentTarget.getBoundingClientRect();
    return { x: event.clientX - rect.left, y: event.clientY - rect.top };
  };

  const inHeatingZone = (x: number, y: number): boolean => {
    const frame = frameRef.current;
    if (frame === null || frame.plotWidth <= 0) return false;
    return Math.hypot(x - frame.handleX, y - frame.plotBottom) < HANDLE_TOUCH_RADIUS;
  };

  const onPointerDown = (event: React.PointerEvent): void => {
    pointers.current.add(event.pointerId);
    if (pointers.current.size >= 2) {
      setCursor(null);
      heatingDrag.current = null;
      cursorDrag.current = null;
      return;
    }
    const { x, y } = toLocal(event);
    if (inHeatingZone(x, y)) {
      heatingDrag.current = { prevX: x };
    } else {
      cursorDrag.current = { startX: x, startY: y, dragged: false };
      setCursor({ x, y, isPinned: false });
    }
  };

  const onPointerMove = (event: React.PointerEvent): void => {
    if (pointers.current.size >= 2) return;
    const { x, y } = toLocal(event);
    const frame = frameRef.current;
    if (heatingDrag.current !== null && frame !== null && frame.plotWidth > 0) {
      const delta = x - heatingDrag.current.prevX;
      const span = tempAxisSpanC(frame.projection.temperatureRange);
      setHeatingDeltaC((prev) => clamp(prev + (delta / frame.plotWidth) * span, -20, 20));
      heatingDrag.current.prevX = x;
      return;
    }
    if (cursorDrag.current !== null) {
      if (
        Math.abs(x - cursorDrag.current.startX) > DRAG_SLOP ||
        Math.abs(y - cursorDrag.current.startY) > DRAG_SLOP
      ) {
        cursorDrag.current.dragged = true;
      }
      setCursor({ x, y, isPinned: false });
    }
  };

  const onPointerUp = (event: React.PointerEvent): void => {
    pointers.current.delete(event.pointerId);
    if (heatingDrag.current !== null) {
      heatingDrag.current = null;
      return;
    }
    if (cursorDrag.current !== null) {
      const dragged = cursorDrag.current.dragged;
      cursorDrag.current = null;
      if (dragged) setCursor(null);
      else setCursor((prev) => (prev === null ? null : { ...prev, isPinned: true }));
    }
  };

  return (
    <div className="chart-host" ref={containerRef}>
      <canvas
        ref={canvasRef}
        className="chart-canvas"
        onPointerDown={onPointerDown}
        onPointerMove={onPointerMove}
        onPointerUp={onPointerUp}
        onPointerCancel={onPointerUp}
      />
    </div>
  );
}

function drawStuve(
  layout: CanvasLayout,
  chart: StuveForecastChartUiModel,
  visibleTopAltitudeKm: number,
  displayUnits: DisplayUnits,
  theme: ThemeColors,
  cursor: SkewTCursorState | null,
  heatingDeltaC: number,
  profileLevels: ReturnType<typeof buildMinimalProfileLevels>,
  parcelPressures: number[],
): StuveFrame | null {
  const { ctx, width, height } = layout;
  const axisColor = theme.onSurfaceVariant;
  const onSurface = theme.onSurface;
  const outline = theme.outlineVariant;
  const bg = gridBackground(theme, 0.02);
  fillRect(ctx, 0, 0, width, height, rgba(theme.surface));

  const chartBottomPressure = Math.min(chart.surfacePressureHpa + 20, SKEWT_BOTTOM_PRESSURE);
  const topPressure = clamp(
    altitudeKmToApproxPressureHpa(visibleTopAltitudeKm),
    SKEWT_MIN_TOP_PRESSURE,
    chartBottomPressure - 50,
  );

  const plotLeft = LEFT_AXIS_WIDTH;
  const plotTop = TOP_PADDING;
  const plotRight = width - RIGHT_ALTITUDE_WIDTH - RIGHT_WIND_WIDTH;
  const plotBottom = height - BOTTOM_AXIS_HEIGHT;
  const plotWidth = plotRight - plotLeft;
  const plotHeight = plotBottom - plotTop;
  if (plotWidth <= 0 || plotHeight <= 0) return null;

  const projection = buildSkewTProjection(
    chart,
    topPressure,
    chartBottomPressure,
    plotLeft,
    plotRight,
    plotTop,
    plotBottom,
  );
  const tempAxisLabels = buildTemperatureAxisLabels(projection.temperatureRange);
  const pressureToY = (p: number): number => projection.pressureToY(p);
  const temperatureToX = (t: number, p: number): number => projection.temperatureToX(t, p);
  const yToPressure = (y: number): number => projection.yToPressure(y);
  const mapX = (t: number, p: number): number => temperatureToX(t, p);
  const mapY = (p: number): number => pressureToY(p);

  const pressureLabels = selectPressureLabels(topPressure, plotHeight);
  const defaultParcelStartTempC =
    chart.parcelAscentPath[0]?.temperatureC ?? chart.temperatureProfile[0]?.temperatureC ?? 15;

  const drawAnchorTemperatureC =
    cursor !== null
      ? projection.xToTemperature(
          cursor.x,
          clamp(yToPressure(cursor.y), topPressure, chartBottomPressure),
        )
      : null;

  fillRect(ctx, plotLeft, plotTop, plotWidth, plotHeight, rgba(bg));

  // Moisture cue strip (right of the plot).
  if (chart.moistureBands.length > 0) {
    const stripLeft = plotRight + 2;
    for (const band of chart.moistureBands) {
      const bandTopY = clamp(pressureToY(band.topPressureHpa), plotTop, plotBottom);
      const bandBottomY = clamp(pressureToY(band.bottomPressureHpa), plotTop, plotBottom);
      if (bandBottomY <= bandTopY) continue;
      const intensity = clamp((band.relativeHumidityFraction - 0.55) / 0.45, 0, 1);
      if (intensity <= 0) continue;
      const color = mixRgb([0xd7, 0xea, 0xf4], [0x4e, 0x7c, 0x9a], intensity);
      fillRect(
        ctx,
        stripLeft,
        bandTopY,
        9,
        bandBottomY - bandTopY,
        rgba(color, 0.16 + intensity * 0.42),
      );
    }
  }

  const drawAdiabat = (
    pressures: number[],
    computeTemp: (p: number) => number,
    color: string,
    widthPx: number,
    dash?: number[],
  ): void => {
    const points: Array<[number, number]> = [];
    for (const p of pressures) {
      const x = mapX(computeTemp(p), p);
      const y = mapY(p);
      if (x >= plotLeft - 24 && x <= plotRight + 24 && y >= plotTop && y <= plotBottom)
        points.push([x, y]);
    }
    for (let i = 0; i < points.length - 1; i++) {
      drawLine(ctx, points[i][0], points[i][1], points[i + 1][0], points[i + 1][1], {
        color,
        width: widthPx,
        dash,
      });
    }
  };

  const drawProfile = (
    points: StuveProfilePoint[],
    color: string,
    widthPx: number,
    dash?: number[],
    dots = false,
    dotRadius = 0,
  ): void => {
    const offsets = points.map(
      (pt) => [mapX(pt.temperatureC, pt.pressureHpa), mapY(pt.pressureHpa)] as [number, number],
    );
    for (let i = 0; i < offsets.length - 1; i++) {
      const [sx, sy] = offsets[i];
      const [ex, ey] = offsets[i + 1];
      if ((sy >= plotTop && sy <= plotBottom) || (ey >= plotTop && ey <= plotBottom)) {
        drawLine(ctx, sx, sy, ex, ey, { color, width: widthPx, dash });
      }
    }
    if (dots && dotRadius > 0) {
      points.forEach((pt, i) => {
        if (!pt.isRealData) return;
        const [ox, oy] = offsets[i];
        if (ox >= plotLeft && ox <= plotRight && oy >= plotTop && oy <= plotBottom)
          fillCircle(ctx, ox, oy, dotRadius, color);
      });
    }
  };

  withClip(ctx, plotLeft, plotTop, plotRight, plotBottom, () => {
    for (const isotherm of tempAxisLabels) {
      const alpha = Math.trunc(isotherm) % 20 === 0 ? 0.35 : 0.15;
      drawLine(
        ctx,
        temperatureToX(isotherm, chartBottomPressure),
        pressureToY(chartBottomPressure),
        temperatureToX(isotherm, topPressure),
        pressureToY(topPressure),
        { color: rgba(outline, alpha) },
      );
    }
    for (const pressure of pressureLabels) {
      const alpha = Math.trunc(pressure) % 200 === 0 ? 0.4 : 0.2;
      drawLine(ctx, plotLeft, pressureToY(pressure), plotRight, pressureToY(pressure), {
        color: rgba(outline, alpha),
      });
    }
    const dryPressures = STUVE_DRY_REFERENCE_PRESSURES.filter(
      (p) => p >= topPressure && p <= chartBottomPressure,
    );
    for (const theta of STUVE_DRY_ADIABAT_THETAS_K) {
      drawAdiabat(
        dryPressures,
        (p) => dryAdiabatTempC(theta, p),
        rgba([0x4e, 0x9b, 0x64], 0.32),
        1,
      );
    }
    const moistPressures = buildReferencePressures(chartBottomPressure, topPressure, 25);
    for (const theta of STUVE_MOIST_ADIABAT_THETAS_K) {
      drawAdiabat(
        moistPressures,
        (p) => moistAdiabatTempC(theta, p),
        rgba([0x2f, 0x8b, 0xaa], 0.28),
        1,
        [6, 4],
      );
    }
    const mixingPressures = buildReferencePressures(chartBottomPressure, topPressure, 50);
    for (const mixingRatio of STUVE_MIXING_RATIO_VALUES_GKG) {
      drawAdiabat(
        mixingPressures,
        (p) => mixingRatioTemperatureC(mixingRatio, p),
        rgba([0x6e, 0x93, 0xc0], 0.24),
        1,
        [3, 4],
      );
    }

    drawProfile(chart.temperatureProfile, TEMP_COLOR, 2.6, undefined, true, 2.6);
    drawProfile(chart.dewpointProfile, DEWPOINT_COLOR, 2.1, undefined, true, 2.2);

    if (cursor === null) {
      drawProfile(chart.parcelAscentPath, rgba(onSurface, 0.58), 2, [8, 5]);
    }

    // Interactive parcel.
    let interactiveParcel: StuveProfilePoint[] | null = null;
    if (drawAnchorTemperatureC !== null && cursor !== null) {
      const anchorPressure = clamp(yToPressure(cursor.y), topPressure, chartBottomPressure);
      interactiveParcel = buildInteractiveParcelFromPoint(
        drawAnchorTemperatureC,
        anchorPressure,
        chart,
        parcelPressures,
      );
      const anchorPoint: StuveProfilePoint = {
        pressureHpa: anchorPressure,
        temperatureC: drawAnchorTemperatureC,
        heightMeters: null,
        isRealData: false,
      };
      const drySegment = [
        anchorPoint,
        ...interactiveParcel
          .filter((p) => p.pressureHpa > anchorPressure + 0.01)
          .sort((a, b) => a.pressureHpa - b.pressureHpa),
      ];
      const moistSegment = [
        anchorPoint,
        ...interactiveParcel
          .filter((p) => p.pressureHpa < anchorPressure - 0.01)
          .sort((a, b) => b.pressureHpa - a.pressureHpa),
      ];
      if (drySegment.length > 1)
        drawProfile(drySegment, rgba([0x59, 0xa3, 0x6a], 0.88), 0.8, [6, 4]);
      if (moistSegment.length > 1) drawProfile(moistSegment, rgba([0x59, 0xa3, 0x6a], 0.88), 2.4);
    } else if (heatingDeltaC !== 0) {
      interactiveParcel = buildInteractiveParcelFromSurface(
        defaultParcelStartTempC + heatingDeltaC,
        chart,
        profileLevels,
        parcelPressures,
      );
      drawProfile(interactiveParcel, rgba([0x59, 0xa3, 0x6a], 0.88), 2.4, [10, 5]);
    }
    const activeParcelPath = interactiveParcel ?? chart.parcelAscentPath;

    if (chart.cclPressureHpa !== null) {
      const y = pressureToY(chart.cclPressureHpa);
      drawLine(ctx, plotLeft, y, plotRight, y, {
        color: rgba([0xb3, 0x6a, 0x27], 0.5),
        width: 1.5,
        dash: [7, 4],
      });
    }

    if (cursor !== null) {
      const readout = buildCursorReadout(
        chart,
        yToPressure(cursor.y),
        drawAnchorTemperatureC,
        activeParcelPath,
      );
      const cursorY = pressureToY(readout.pressureHpa);
      if (cursorY >= plotTop && cursorY <= plotBottom) {
        drawCursorOverlay(ctx, readout, cursorY, plotLeft, plotRight, onSurface, temperatureToX);
      }
    }
  });

  strokeRect(ctx, plotLeft, plotTop, plotWidth, plotHeight, rgba(outline, 0.5), 1);

  // Heating handle.
  const activeParcelStartTempC = defaultParcelStartTempC + heatingDeltaC;
  const handleX = clamp(
    temperatureToX(activeParcelStartTempC, chartBottomPressure),
    plotLeft,
    plotRight,
  );
  drawLine(ctx, handleX, plotBottom, handleX, plotBottom + 8, {
    color: rgba([0x59, 0xa3, 0x6a], 0.75),
    width: 2,
  });
  fillCircle(ctx, handleX, plotBottom + 8, 6, PARCEL_COLOR);
  drawLine(ctx, handleX, plotBottom - 4, handleX, plotBottom, { color: PARCEL_COLOR, width: 2 });

  // Wind barbs.
  for (const barb of chart.windBarbs) {
    const y = pressureToY(barb.pressureHpa);
    if (y < plotTop || y > plotBottom) continue;
    drawWindBarbShape(
      ctx,
      plotRight + RIGHT_ALTITUDE_WIDTH + RIGHT_WIND_WIDTH / 2,
      y,
      barb.speedKmh,
      barb.directionDeg,
      20,
      rgba(onSurface),
    );
  }

  // Axis text.
  const pressurePaint: TextPaint = { color: rgba(axisColor), sizePx: 10, align: "right" };
  for (const pressure of pressureLabels) {
    const y = pressureToY(pressure);
    if (y < plotTop || y > plotBottom) continue;
    drawText(ctx, String(Math.trunc(pressure)), LEFT_AXIS_WIDTH - 4, y + 10 * 0.35, pressurePaint);
  }
  const tempPaint: TextPaint = {
    color: rgba(axisColor),
    sizePx: 10,
    align: "center",
    weight: "bold",
  };
  const tempBaseline = plotBottom + 10 + 6;
  for (const tempLabel of tempAxisLabels) {
    const x = temperatureToX(tempLabel, chartBottomPressure);
    if (x < plotLeft || x > plotRight) continue;
    drawText(ctx, `${Math.trunc(tempLabel)}°`, x, tempBaseline, tempPaint);
  }
  const mixingPaint: TextPaint = { color: rgba([0x5c, 0x88, 0xb4]), sizePx: 8, align: "center" };
  for (const mixingRatio of STUVE_MIXING_RATIO_VALUES_GKG) {
    const x = temperatureToX(mixingRatioTemperatureC(mixingRatio, topPressure), topPressure);
    if (x < plotLeft || x > plotRight) continue;
    drawText(
      ctx,
      mixingRatio < 1 ? mixingRatio.toFixed(1) : String(Math.trunc(mixingRatio)),
      x,
      plotTop - 2,
      mixingPaint,
    );
  }
  if (chart.cclPressureHpa !== null) {
    drawText(ctx, "CCL", plotLeft + 4, pressureToY(chart.cclPressureHpa) - 3 + 12, {
      color: rgba([0xb3, 0x6a, 0x27]),
      sizePx: 9,
      weight: "bold",
    });
  }
  const windLabelPaint: TextPaint = { color: rgba(axisColor), sizePx: 8, align: "center" };
  const altitudePaint: TextPaint = { color: rgba(axisColor), sizePx: 9, align: "left" };
  for (const barb of chart.windBarbs) {
    const y = pressureToY(barb.pressureHpa);
    if (y < plotTop || y > plotBottom) continue;
    drawText(
      ctx,
      formatWindSpeed(barb.speedKmh, displayUnits, false),
      plotRight + RIGHT_ALTITUDE_WIDTH + RIGHT_WIND_WIDTH / 2,
      y + 22,
      windLabelPaint,
    );
  }
  for (const pressure of pressureLabels) {
    const y = pressureToY(pressure);
    if (y < plotTop || y > plotBottom) continue;
    const heightMeters =
      interpolateProfileHeightMeters(chart.temperatureProfile, pressure) ??
      pressureToApproxHeightMeters(pressure);
    drawText(
      ctx,
      formatAltitudeMeters(heightMeters, displayUnits, true),
      plotRight + 4,
      y + 9 * 0.35,
      altitudePaint,
    );
  }

  // Cursor inline labels.
  if (cursor !== null) {
    let activeParcelPath = chart.parcelAscentPath;
    if (drawAnchorTemperatureC !== null) {
      const anchorPressure = clamp(yToPressure(cursor.y), topPressure, chartBottomPressure);
      activeParcelPath = buildInteractiveParcelFromPoint(
        drawAnchorTemperatureC,
        anchorPressure,
        chart,
        parcelPressures,
      );
    } else if (heatingDeltaC !== 0) {
      activeParcelPath = buildInteractiveParcelFromSurface(
        defaultParcelStartTempC + heatingDeltaC,
        chart,
        profileLevels,
        parcelPressures,
      );
    }
    const readout = buildCursorReadout(
      chart,
      yToPressure(cursor.y),
      drawAnchorTemperatureC,
      activeParcelPath,
    );
    const cursorY = pressureToY(readout.pressureHpa);
    if (cursorY >= plotTop && cursorY <= plotBottom) {
      drawCursorInlineLabels(
        ctx,
        readout,
        cursorY,
        plotLeft,
        plotRight,
        plotTop,
        plotBottom,
        chartBottomPressure,
        plotRight + RIGHT_ALTITUDE_WIDTH + RIGHT_WIND_WIDTH / 2,
        displayUnits,
        temperatureToX,
      );
    }
  }

  return {
    projection,
    plotLeft,
    plotRight,
    plotTop,
    plotBottom,
    plotWidth,
    topPressure,
    chartBottomPressure,
    handleX,
    defaultParcelStartTempC,
  };
}

function drawWindBarbShape(
  ctx: Ctx,
  centerX: number,
  centerY: number,
  speedKmh: number,
  directionDeg: number,
  barbSize: number,
  color: string,
): void {
  const geometry = buildWindBarbGeometry(centerX, centerY, speedKmh, directionDeg, barbSize);
  if (geometry.calmRadius !== null) {
    strokeCircle(ctx, centerX, centerY, geometry.calmRadius, color, 1.5);
    return;
  }
  drawLine(
    ctx,
    geometry.shaft.start.x,
    geometry.shaft.start.y,
    geometry.shaft.end.x,
    geometry.shaft.end.y,
    { color, width: 1.5, cap: "round" },
  );
  for (const flag of geometry.flags) {
    fillPolygon(
      ctx,
      flag.points.map((p) => [p.x, p.y] as [number, number]),
      color,
    );
  }
  for (const feather of geometry.feathers) {
    drawLine(ctx, feather.start.x, feather.start.y, feather.end.x, feather.end.y, {
      color,
      width: 1.5,
      cap: "round",
    });
  }
}

function drawCursorOverlay(
  ctx: Ctx,
  readout: CursorReadout,
  cursorY: number,
  plotLeft: number,
  plotRight: number,
  onSurface: readonly [number, number, number],
  temperatureToX: (t: number, p: number) => number,
): void {
  drawLine(ctx, plotLeft, cursorY, plotRight, cursorY, {
    color: rgba(onSurface, 0.58),
    dash: [4, 3],
  });
  if (readout.guideTemperatureC !== null) {
    // Isotherm guide through the placed point (drawn as a short vertical cue near the cursor row).
    const gx = temperatureToX(readout.guideTemperatureC, readout.pressureHpa);
    drawLine(ctx, gx, cursorY - 40, gx, cursorY + 40, {
      color: rgba([0xd8, 0x3a, 0x3a], 0.45),
      width: 1.2,
      dash: [6, 4],
    });
  }
  if (readout.temperatureC !== null)
    fillCircle(
      ctx,
      temperatureToX(readout.temperatureC, readout.pressureHpa),
      cursorY,
      4,
      TEMP_COLOR,
    );
  if (readout.dewpointC !== null)
    fillCircle(
      ctx,
      temperatureToX(readout.dewpointC, readout.pressureHpa),
      cursorY,
      3.6,
      DEWPOINT_COLOR,
    );
  if (readout.parcelTemperatureC !== null) {
    const parcelX = temperatureToX(readout.parcelTemperatureC, readout.pressureHpa);
    strokeCircle(ctx, parcelX, cursorY, 3.2, rgba(onSurface, 0.65), 1.6);
    if (readout.temperatureC !== null) {
      drawLine(
        ctx,
        temperatureToX(readout.temperatureC, readout.pressureHpa),
        cursorY,
        parcelX,
        cursorY,
        { color: rgba([0xe2, 0xa8, 0x5f], 0.55), width: 1.8 },
      );
    }
  }
  if (readout.guideTemperatureC !== null) {
    strokeCircle(
      ctx,
      temperatureToX(readout.guideTemperatureC, readout.pressureHpa),
      cursorY,
      4.2,
      PARCEL_COLOR,
      1.8,
    );
  }
}

function drawBadge(
  ctx: Ctx,
  lines: string[],
  centerX: number,
  centerY: number,
  textColor: string,
  bgColor: string,
  minWidth: number,
): void {
  const paint: TextPaint = { color: textColor, sizePx: 9, align: "center", weight: "bold" };
  const padH = 6;
  const padV = 4;
  const lineSpacing = 2;
  const lineHeight = 9;
  const maxTextWidth = Math.max(...lines.map((l) => measureText(ctx, l, paint)));
  const boxWidth = Math.max(minWidth, maxTextWidth + padH * 2);
  const boxHeight = lineHeight * lines.length + lineSpacing * (lines.length - 1) + padV * 2;
  fillRoundRect(
    ctx,
    centerX - boxWidth / 2,
    centerY - boxHeight / 2,
    boxWidth,
    boxHeight,
    4,
    bgColor,
  );
  const firstBaseline = centerY - boxHeight / 2 + padV + lineHeight * 0.8;
  lines.forEach((line, idx) => {
    drawText(ctx, line, centerX, firstBaseline + idx * (lineHeight + lineSpacing), paint);
  });
}

function drawCursorInlineLabels(
  ctx: Ctx,
  readout: CursorReadout,
  cursorY: number,
  plotLeft: number,
  plotRight: number,
  plotTop: number,
  plotBottom: number,
  bottomPressure: number,
  rightWindCenterX: number,
  displayUnits: DisplayUnits,
  temperatureToX: (t: number, p: number) => number,
): void {
  const dark = rgba([0x2b, 0x2b, 0x2b]);
  const badgeBg = rgba([0xf3, 0xf3, 0xf3]);
  drawBadge(
    ctx,
    [
      formatAltitudeMeters(readout.altitudeMeters, displayUnits),
      `${Math.round(readout.pressureHpa)} hPa`,
    ],
    plotLeft - 10,
    cursorY,
    dark,
    badgeBg,
    46,
  );
  drawBadge(
    ctx,
    [formatAltitudeMeters(readout.altitudeMeters, displayUnits, true)],
    plotRight + 18,
    cursorY,
    dark,
    badgeBg,
    34,
  );

  const pointLabel = (
    text: string,
    x: number,
    y: number,
    color: string,
    align: "left" | "right",
  ): void => {
    const paint: TextPaint = { color, sizePx: 9, align, weight: "bold" };
    const measured = measureText(ctx, text, paint);
    const minX = plotLeft + 4;
    const maxX = plotRight - 4;
    const drawX =
      align === "right"
        ? Math.max(Math.min(x, maxX), minX + measured)
        : Math.min(Math.max(x, minX), maxX - measured);
    drawText(ctx, text, drawX, y, paint);
  };
  if (readout.temperatureC !== null) {
    pointLabel(
      `T ${readout.temperatureC.toFixed(1)}°`,
      temperatureToX(readout.temperatureC, readout.pressureHpa) + 6,
      cursorY - 6,
      rgba([0xd8, 0x3a, 0x3a]),
      "left",
    );
  }
  if (readout.dewpointC !== null) {
    pointLabel(
      `Td ${readout.dewpointC.toFixed(1)}°`,
      temperatureToX(readout.dewpointC, readout.pressureHpa) - 6,
      cursorY + 16,
      rgba([0x2e, 0x6f, 0xb5]),
      "right",
    );
  }
  if (readout.parcelTemperatureC !== null) {
    pointLabel(
      `Parcel ${readout.parcelTemperatureC.toFixed(1)}°`,
      temperatureToX(readout.parcelTemperatureC, readout.pressureHpa) + 6,
      cursorY + 28,
      PARCEL_COLOR,
      "left",
    );
  }

  // Projected temperature cue on the bottom axis (simplified: no collision layout).
  const bottomPaint: TextPaint = { color: rgba([0xd8, 0x3a, 0x3a]), sizePx: 9, align: "center" };
  if (readout.temperatureC !== null) {
    drawText(
      ctx,
      `T ${Math.round(readout.temperatureC)}°`,
      clamp(temperatureToX(readout.temperatureC, bottomPressure), plotLeft + 12, plotRight - 12),
      plotBottom + 10 + 22,
      bottomPaint,
    );
  }

  if (readout.windSpeedKmh !== null && readout.windDirectionDeg !== null) {
    const windBadgeY =
      cursorY - 28 >= plotTop + 10
        ? cursorY - 28
        : clamp(cursorY + 28, plotTop + 10, plotBottom - 10);
    drawBadge(
      ctx,
      [
        `${formatWindSpeed(readout.windSpeedKmh, displayUnits)} ${String(Math.round(readout.windDirectionDeg)).padStart(3, "0")}°`,
      ],
      rightWindCenterX,
      windBadgeY,
      dark,
      badgeBg,
      74,
    );
  }
}
