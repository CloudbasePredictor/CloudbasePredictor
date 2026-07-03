/**
 * Thermic forecast chart (updraft-strength heatmap + top/cloud-base/moist-top
 * diagnostic lines + crosshair readout).
 *
 * Canvas 2D port of `ui/screens/forecast/views/ThermicForecastView.kt`. The
 * cursor info panel is drawn on the canvas (the Android app uses a floating
 * Compose Surface) — an honest simplification that keeps layout in one place.
 */

import { useCallback, useRef, useState } from "react";
import type {
  ThermalForecastConfidence,
  ThermalLimitingReason,
} from "../engine/thermalForecastEngine";
import {
  altitudeAxisUnitLabel,
  type DisplayUnits,
  formatAltitudeAxisValue,
  formatAltitudeKm,
  formatAltitudeMeters,
  formatVerticalSpeedRange,
} from "../model/units";
import { altitudeToY, buildAltitudeTicks, yToAltitude } from "./altitudeAxis";
import {
  type CanvasLayout,
  type Ctx,
  drawLine,
  drawText,
  fillRect,
  fillRoundRect,
  measureText,
  strokeCircle,
  strokeRoundRect,
  type TextPaint,
  withClip,
} from "./canvasKit";
import { thermicStrengthColor } from "./colorScales";
import { gridBackground, rgba, type ThemeColors } from "./theme";
import {
  aggregatedForDisplay,
  type ThermicForecastChartUiModel,
  type ThermicSlotDiagnostics,
  visibleSegment,
} from "./thermicChart";
import { useAltitudeZoom } from "./useAltitudeZoom";
import { useChartCanvas } from "./useChartCanvas";

const AXIS_WIDTH = 60;
const BOTTOM_AXIS_HEIGHT = 38;
const MIN_VISIBLE_ALTITUDE_RANGE_KM = 0.75;
const ALTITUDE_EPSILON = 0.001;
const DATA_ALTITUDE_STEP_KM = 0.05;
const MAJOR_TIME_STEP_MINUTES = 180;
const MIN_TIME_BUCKET_WIDTH_PX = 28;
const MIN_ALTITUDE_BUCKET_HEIGHT_PX = 20;
const THERMAL_TOP_COLOR = "#E07020";
const CLOUD_BASE_COLOR = "#2088E0";
const MOIST_TOP_COLOR = "#A040C0";

function resolveTimeBucketSlotCount(plotWidth: number, rawTimeSlotCount: number): number {
  const rawColumnWidth = plotWidth / Math.max(rawTimeSlotCount, 1);
  return Math.max(1, Math.ceil(MIN_TIME_BUCKET_WIDTH_PX / rawColumnWidth));
}

function resolveAltitudeBucketStepKm(
  plotHeight: number,
  visibleTopAltitudeKm: number,
  rawAltitudeStepKm: number,
): number {
  const rawRowHeight =
    plotHeight * (rawAltitudeStepKm / Math.max(visibleTopAltitudeKm, rawAltitudeStepKm));
  const bucketRowCount = Math.max(1, Math.ceil(MIN_ALTITUDE_BUCKET_HEIGHT_PX / rawRowHeight));
  return rawAltitudeStepKm * bucketRowCount;
}

function thermicMajorAltitudeStepKm(maxAltitudeKm: number): number {
  return maxAltitudeKm <= 3.5 ? 0.5 : 1;
}

function shouldDrawTimeLabel(startMinute: number, displayedSlotCount: number): boolean {
  return displayedSlotCount <= 8 ? true : startMinute % MAJOR_TIME_STEP_MINUTES === 0;
}

function formatTimeLabel(startMinute: number): string {
  const hour = Math.trunc(startMinute / 60);
  const minute = startMinute % 60;
  return `${String(hour).padStart(2, "0")}:${String(minute).padStart(2, "0")}`;
}

function confidenceLabel(confidence: ThermalForecastConfidence): string {
  return confidence === "HIGH" ? "HIGH" : confidence === "MEDIUM" ? "MED" : "LOW";
}

function limitingReasonLabel(reason: ThermalLimitingReason): string {
  const map: Record<ThermalLimitingReason, string> = {
    SURFACE_HEATING: "heating",
    INVERSION: "inversion",
    CLOUD_BASE: "cloud base",
    PROFILE_TOP: "profile top",
    PRECIPITATION: "rain",
    WEAK_RADIATION: "weak sun",
    WIND_SHEAR: "wind shear",
    MISSING_DATA: "missing data",
  };
  return map[reason];
}

function signed(value: number): string {
  return `${value >= 0 ? "+" : ""}${value.toFixed(1)}`;
}

function drawHatchedBand(
  ctx: Ctx,
  left: number,
  top: number,
  right: number,
  bottom: number,
  fill: string,
  hatch: string,
): void {
  const resolvedTop = Math.min(top, bottom);
  const resolvedBottom = Math.max(top, bottom);
  const w = right - left;
  const h = resolvedBottom - resolvedTop;
  if (w <= 0 || h <= 0) return;
  fillRoundRect(ctx, left, resolvedTop, w, h, 2, fill);
  withClip(ctx, left, resolvedTop, right, resolvedBottom, () => {
    let x = left - h;
    const step = 8;
    while (x < right + h) {
      drawLine(ctx, x, resolvedBottom, x + h, resolvedTop, { color: hatch });
      x += step;
    }
  });
}

interface CursorInfo {
  lines: string[];
  x: number;
  y: number;
}

export interface ThermicChartViewProps {
  chart: ThermicForecastChartUiModel;
  visibleTopAltitudeKm: number;
  elevationKm: number;
  displayUnits: DisplayUnits;
  theme: ThemeColors;
  onVisibleTopAltitudeChange: (topAltitudeKm: number) => void;
}

export function ThermicChartView(props: ThermicChartViewProps): React.JSX.Element {
  const {
    chart,
    visibleTopAltitudeKm,
    elevationKm,
    displayUnits,
    theme,
    onVisibleTopAltitudeChange,
  } = props;
  const containerRef = useRef<HTMLDivElement | null>(null);
  const [crosshair, setCrosshair] = useState<{ x: number; y: number } | null>(null);
  const topRef = useRef(visibleTopAltitudeKm);
  topRef.current = visibleTopAltitudeKm;
  useAltitudeZoom(
    containerRef,
    useCallback(() => topRef.current, []),
    onVisibleTopAltitudeChange,
  );

  const draw = useCallback(
    (layout: CanvasLayout) => {
      drawThermic(layout, chart, visibleTopAltitudeKm, elevationKm, displayUnits, theme, crosshair);
    },
    [chart, visibleTopAltitudeKm, elevationKm, displayUnits, theme, crosshair],
  );
  const canvasRef = useChartCanvas(draw);

  const toLocal = (event: React.PointerEvent): { x: number; y: number } => {
    const rect = event.currentTarget.getBoundingClientRect();
    return { x: event.clientX - rect.left, y: event.clientY - rect.top };
  };

  return (
    <div className="chart-host" ref={containerRef}>
      <canvas
        ref={canvasRef}
        className="chart-canvas"
        onPointerDown={(e) => {
          if (e.pointerType === "touch" && e.isPrimary === false) return;
          setCrosshair(toLocal(e));
        }}
        onPointerMove={(e) => {
          if (e.buttons === 0 && e.pointerType !== "touch") return;
          setCrosshair(toLocal(e));
        }}
      />
      {chart.cells.length === 0 && (
        <div className="chart-empty">Unfortunately, no thermals are expected.</div>
      )}
    </div>
  );
}

function drawThermic(
  layout: CanvasLayout,
  rawChart: ThermicForecastChartUiModel,
  visibleTopAltitudeKm: number,
  elevationKm: number,
  displayUnits: DisplayUnits,
  theme: ThemeColors,
  crosshair: { x: number; y: number } | null,
): void {
  const { ctx, width, height } = layout;
  const axisColor = theme.onSurfaceVariant;
  const outline = theme.outlineVariant;
  const bg = gridBackground(theme, 0.035);
  fillRect(ctx, 0, 0, width, height, rgba(theme.surface));
  if (rawChart.timeSlots.length === 0) return;

  const tileInset = 1;
  const plotLeft = AXIS_WIDTH;
  const plotTop = 0;
  const plotRight = width;
  const plotBottom = height - BOTTOM_AXIS_HEIGHT;
  const plotWidth = plotRight - plotLeft;
  const plotHeight = plotBottom - plotTop;
  if (plotWidth <= 0 || plotHeight <= 0) return;

  const effectiveTop = Math.max(
    elevationKm + visibleTopAltitudeKm,
    elevationKm + MIN_VISIBLE_ALTITUDE_RANGE_KM,
  );
  const majorTicks = buildAltitudeTicks(
    elevationKm,
    effectiveTop,
    thermicMajorAltitudeStepKm(effectiveTop - elevationKm),
  );
  const minorTicks = buildAltitudeTicks(elevationKm, effectiveTop, 0.25);

  const chart = aggregatedForDisplay(
    rawChart,
    resolveTimeBucketSlotCount(plotWidth, rawChart.timeSlots.length),
    resolveAltitudeBucketStepKm(plotHeight, effectiveTop, DATA_ALTITUDE_STEP_KM),
  );
  if (chart.timeSlots.length === 0) return;

  fillRect(ctx, 0, plotTop, AXIS_WIDTH, plotHeight, rgba(bg));
  fillRect(ctx, plotLeft, plotTop, plotWidth, plotHeight, rgba(bg));

  const columnWidth = plotWidth / chart.timeSlots.length;
  const timeIndexLookup = new Map<number, number>();
  chart.timeSlots.forEach((minute, index) => {
    timeIndexLookup.set(minute, index);
  });
  const yFor = (altKm: number): number =>
    altitudeToY(altKm, elevationKm, effectiveTop, plotTop, plotBottom);

  for (const altKm of minorTicks) {
    drawLine(ctx, plotLeft, yFor(altKm), plotRight, yFor(altKm), { color: rgba(outline, 0.15) });
  }

  chart.timeSlots.forEach((startMinute, index) => {
    const x = plotLeft + index * columnWidth;
    const boundaryAlpha = startMinute % MAJOR_TIME_STEP_MINUTES === 0 ? 0.42 : 0.18;
    drawLine(ctx, x, plotTop, x, plotBottom, { color: rgba(outline, boundaryAlpha) });
    drawLine(ctx, x + columnWidth / 2, plotTop, x + columnWidth / 2, plotBottom, {
      color: rgba(outline, 0.12),
    });
    drawLine(ctx, x + columnWidth / 2, plotBottom + 4, x + columnWidth / 2, plotBottom + 10, {
      color: rgba(outline, 0.4),
    });
  });

  const diagnosticsBySlot = new Map<number, ThermicSlotDiagnostics>();
  for (const diag of chart.slotDiagnostics) diagnosticsBySlot.set(diag.startMinuteOfDayLocal, diag);
  const cloudAltitudeBySlot = new Map<number, number>();
  for (const [minute, diag] of diagnosticsBySlot) {
    if (diag.cloudBaseKm !== null) cloudAltitudeBySlot.set(minute, diag.cloudBaseKm);
  }

  // Pressure-level dashed guides.
  const seenLevel = new Set<number>();
  for (const altKm of chart.pressureLevelAltitudesKm) {
    const key = Math.trunc(altKm * 1000);
    if (seenLevel.has(key)) continue;
    seenLevel.add(key);
    if (altKm < elevationKm || altKm > effectiveTop) continue;
    const y = yFor(altKm);
    drawLine(ctx, plotLeft, y, plotRight, y, { color: rgba(outline, 0.28), dash: [2, 6] });
    drawLine(ctx, plotLeft - 7, y, plotLeft, y, { color: rgba(outline, 0.5) });
  }

  // Top-range hatch + cloud layer bands.
  chart.timeSlots.forEach((minute, index) => {
    const diag = diagnosticsBySlot.get(minute);
    if (diag === undefined) return;
    const columnLeft = plotLeft + index * columnWidth;
    const bandLeft = columnLeft + tileInset;
    const bandRight = columnLeft + columnWidth - tileInset;

    const rangeLow = Math.min(Math.max(diag.topLowKm, elevationKm), effectiveTop);
    const rangeHigh = Math.min(Math.max(diag.topHighKm, elevationKm), effectiveTop);
    if (rangeHigh > rangeLow + ALTITUDE_EPSILON) {
      drawHatchedBand(
        ctx,
        bandLeft,
        yFor(rangeHigh),
        bandRight,
        yFor(rangeLow),
        rgba([0xe0, 0x70, 0x20], 0.12),
        rgba([0xe0, 0x70, 0x20], 0.3),
      );
    }
    const cloudBaseKm = diag.cloudBaseKm;
    const cloudTopKm =
      diag.moistEquilibriumTopKm !== null
        ? Math.max(diag.moistEquilibriumTopKm, cloudBaseKm ?? 0)
        : null;
    if (cloudBaseKm !== null && cloudBaseKm <= effectiveTop) {
      const baseY = yFor(Math.min(Math.max(cloudBaseKm, elevationKm), effectiveTop));
      const topY = yFor(Math.min(Math.max(cloudTopKm ?? cloudBaseKm, elevationKm), effectiveTop));
      if (baseY - topY > 2) {
        fillRoundRect(
          ctx,
          bandLeft,
          topY,
          bandRight - bandLeft,
          baseY - topY,
          2,
          rgba([0x20, 0x88, 0xe0], 0.1),
        );
      }
    }
  });

  // Strength cells.
  for (const cell of chart.cells) {
    const timeIndex = timeIndexLookup.get(cell.startMinuteOfDayLocal);
    if (timeIndex === undefined) continue;
    const segment = visibleSegment(
      cell,
      elevationKm,
      effectiveTop,
      cloudAltitudeBySlot.get(cell.startMinuteOfDayLocal) ?? null,
    );
    if (segment === null) continue;
    const topY = yFor(segment.endAltitudeKm);
    const bottomY = yFor(segment.startAltitudeKm);
    const cellHeight = bottomY - topY;
    if (cellHeight <= tileInset * 2) continue;
    fillRoundRect(
      ctx,
      plotLeft + timeIndex * columnWidth + tileInset,
      topY + tileInset,
      columnWidth - tileInset * 2,
      cellHeight - tileInset * 2,
      tileInset * 2,
      rgba(thermicStrengthColor(cell.strengthMps)),
    );
  }

  for (const altKm of majorTicks) {
    drawLine(ctx, 0, yFor(altKm), plotRight, yFor(altKm), { color: rgba(outline, 0.34) });
  }
  drawLine(ctx, plotRight, plotTop, plotRight, plotBottom, { color: rgba(outline, 0.35) });
  strokeRoundRect(ctx, plotLeft, plotTop, plotWidth, plotHeight, 0, rgba(outline, 0.4), 1);

  // Diagnostic lines.
  const drawDiagnosticLine = (
    color: string,
    dash: number[] | undefined,
    strokeWidth: number,
    selector: (d: ThermicSlotDiagnostics) => number | null,
  ): void => {
    let prevX: number | null = null;
    let prevY: number | null = null;
    chart.timeSlots.forEach((minute, index) => {
      const diag = diagnosticsBySlot.get(minute);
      if (diag === undefined) {
        prevX = null;
        prevY = null;
        return;
      }
      const altKm = selector(diag);
      if (altKm === null || altKm < elevationKm || altKm > effectiveTop) {
        prevX = null;
        prevY = null;
        return;
      }
      const x = plotLeft + index * columnWidth + columnWidth / 2;
      const y = yFor(altKm);
      if (prevX !== null && prevY !== null) {
        drawLine(ctx, prevX, prevY, x, y, { color, width: strokeWidth, dash });
      }
      prevX = x;
      prevY = y;
    });
  };
  drawDiagnosticLine(MOIST_TOP_COLOR, [4, 6], 2, (d) => d.moistEquilibriumTopKm);
  drawDiagnosticLine(CLOUD_BASE_COLOR, [10, 4], 2, (d) => d.cloudBaseKm);
  drawDiagnosticLine(THERMAL_TOP_COLOR, undefined, 2.4, (d) => d.topNominalKm);

  // Axis labels.
  const axisPaint: TextPaint = { color: rgba(axisColor), sizePx: 12 };
  for (const altKm of majorTicks) {
    // Clamp the baseline so the top-most tick label is not clipped by the plot edge.
    drawText(
      ctx,
      formatAltitudeAxisValue(altKm, displayUnits),
      8,
      Math.max(yFor(altKm) + 12 * 0.35, 12),
      axisPaint,
    );
  }
  drawText(ctx, altitudeAxisUnitLabel(displayUnits), 8, plotTop + 28, {
    color: rgba(axisColor),
    sizePx: 11,
  });
  const hourPaint: TextPaint = {
    color: rgba(axisColor),
    sizePx: 12,
    align: "center",
    weight: "bold",
  };
  chart.timeSlots.forEach((startMinute, index) => {
    if (!shouldDrawTimeLabel(startMinute, chart.timeSlots.length)) return;
    drawText(
      ctx,
      formatTimeLabel(startMinute),
      plotLeft + index * columnWidth + columnWidth / 2,
      plotBottom + 12 + 14,
      hourPaint,
    );
  });

  // Crosshair + info.
  if (crosshair !== null) {
    const cx = Math.min(Math.max(crosshair.x, plotLeft), plotRight);
    const cy = Math.min(Math.max(crosshair.y, plotTop), plotBottom);
    drawLine(ctx, cx, plotTop, cx, plotBottom, { color: rgba(outline, 0.6), dash: [8, 6] });
    drawLine(ctx, plotLeft, cy, plotRight, cy, { color: rgba(outline, 0.6), dash: [8, 6] });
    const reticleR = 18;
    strokeCircle(ctx, cx, cy, reticleR, rgba(outline, 0.8), 2);
    const tick = 4;
    for (const angleDeg of [0, 90, 180, 270]) {
      const rad = (angleDeg * Math.PI) / 180;
      drawLine(
        ctx,
        cx + Math.cos(rad) * (reticleR - tick),
        cy + Math.sin(rad) * (reticleR - tick),
        cx + Math.cos(rad) * (reticleR + tick),
        cy + Math.sin(rad) * (reticleR + tick),
        { color: rgba(outline, 0.8), width: 2 },
      );
    }

    const info = buildThermicCursorInfo(
      chart,
      cx,
      cy,
      plotLeft,
      plotTop,
      plotBottom,
      columnWidth,
      elevationKm,
      effectiveTop,
      cloudAltitudeBySlot,
      diagnosticsBySlot,
      displayUnits,
    );
    if (info !== null)
      drawInfoPanel(ctx, info, plotLeft, plotRight, plotTop, plotBottom, reticleR, theme);
  }
}

function buildThermicCursorInfo(
  chart: ThermicForecastChartUiModel,
  cx: number,
  cy: number,
  plotLeft: number,
  plotTop: number,
  plotBottom: number,
  columnWidth: number,
  elevationKm: number,
  effectiveTop: number,
  cloudAltitudeBySlot: Map<number, number>,
  diagnosticsBySlot: Map<number, ThermicSlotDiagnostics>,
  displayUnits: DisplayUnits,
): CursorInfo | null {
  if (columnWidth <= 0) return null;
  const altKm = yToAltitude(cy, elevationKm, effectiveTop, plotTop, plotBottom);
  const timeIdx = Math.min(
    Math.max(Math.trunc((cx - plotLeft) / columnWidth), 0),
    chart.timeSlots.length - 1,
  );
  const timeSlot = chart.timeSlots[timeIdx];
  const cell = chart.cells
    .filter((c) => c.startMinuteOfDayLocal === timeSlot)
    .find((candidate) => {
      const segment = visibleSegment(
        candidate,
        elevationKm,
        effectiveTop,
        cloudAltitudeBySlot.get(timeSlot) ?? null,
      );
      if (segment === null) return false;
      return altKm >= segment.startAltitudeKm && altKm <= segment.endAltitudeKm;
    });
  const diag = diagnosticsBySlot.get(timeSlot);

  const lines: string[] = [
    `${formatTimeLabel(timeSlot)}  Alt ${formatAltitudeKm(altKm, displayUnits)}`,
  ];
  if (cell !== undefined) {
    lines.push(
      `Air lift ${formatVerticalSpeedRange(cell.updraftLowMps, cell.updraftHighMps, displayUnits)}`,
    );
  }
  if (diag !== undefined) {
    lines.push(
      `Top ${formatAltitudeKm(diag.topNominalKm, displayUnits)}  raw ${formatAltitudeKm(diag.topLowKm, displayUnits)}-${formatAltitudeKm(diag.topHighKm, displayUnits)}`,
    );
    lines.push(
      `Conf ${confidenceLabel(diag.confidence)}  limit ${limitingReasonLabel(diag.limitingReason)}`,
    );
    if (diag.cloudBaseKm !== null) {
      const moistTop = diag.moistEquilibriumTopKm;
      lines.push(
        moistTop !== null && moistTop > diag.cloudBaseKm + 0.1
          ? `Cloud layer ${formatAltitudeKm(diag.cloudBaseKm, displayUnits)}-${formatAltitudeKm(moistTop, displayUnits)}`
          : `Cloud base ${formatAltitudeKm(diag.cloudBaseKm, displayUnits)}`,
      );
    }
    const conv: string[] = [];
    if (diag.modelCapeJKg !== null) conv.push(`CAPE ${Math.trunc(diag.modelCapeJKg)}`);
    if (diag.normalizedCinJKg !== null) conv.push(`CIN ${Math.trunc(diag.normalizedCinJKg)}`);
    if (diag.liftedIndexC !== null) conv.push(`LI ${signed(diag.liftedIndexC)}`);
    if (conv.length > 0) lines.push(`Diag ${conv.join("  ")}`);
    if (diag.boundaryLayerHeightM !== null)
      lines.push(`PBL ${formatAltitudeMeters(diag.boundaryLayerHeightM, displayUnits)}`);
  }
  return { lines, x: cx, y: cy };
}

function drawInfoPanel(
  ctx: Ctx,
  info: CursorInfo,
  plotLeft: number,
  plotRight: number,
  plotTop: number,
  plotBottom: number,
  avoidRadius: number,
  theme: ThemeColors,
): void {
  const headerPaint: TextPaint = { color: rgba(theme.onSurface), sizePx: 12, weight: "bold" };
  const detailPaint: TextPaint = { color: rgba(theme.onSurface), sizePx: 11 };
  const padH = 10;
  const padV = 8;
  const lineGap = 3;
  const lineHeight = 14;
  const textWidths = info.lines.map((line, idx) =>
    measureText(ctx, line, idx === 0 ? headerPaint : detailPaint),
  );
  const panelWidth = Math.min(Math.max(...textWidths) + padH * 2, plotRight - plotLeft - 8);
  const panelHeight = info.lines.length * lineHeight + (info.lines.length - 1) * lineGap + padV * 2;

  let x = info.x - panelWidth / 2;
  let y =
    info.y > (plotTop + plotBottom) / 2
      ? info.y - avoidRadius - 8 - panelHeight
      : info.y + avoidRadius + 8;
  x = Math.min(Math.max(x, plotLeft + 4), plotRight - panelWidth - 4);
  y = Math.min(Math.max(y, plotTop + 4), plotBottom - panelHeight - 4);

  fillRoundRect(ctx, x, y, panelWidth, panelHeight, 6, rgba(theme.surface, 0.96));
  strokeRoundRect(ctx, x, y, panelWidth, panelHeight, 6, rgba(theme.outlineVariant, 0.6), 1);
  info.lines.forEach((line, idx) => {
    const baseline = y + padV + lineHeight * (idx + 1) - lineHeight * 0.25;
    drawText(ctx, line, x + padH, baseline, idx === 0 ? headerPaint : detailPaint);
  });
}
