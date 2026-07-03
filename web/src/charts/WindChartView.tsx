/**
 * Wind forecast chart (per-altitude speed heatmap + interpolated wind arrows).
 *
 * Canvas 2D port of `ui/screens/forecast/views/WindForecastView.kt`. Wheel/pinch
 * zoom rebuilds the chart with the new visible top altitude, matching the Android
 * behaviour where `buildWindChartFromData(maxAltitudeKm = visibleTopAltitudeKm)`.
 */

import { useCallback, useRef, useState } from "react";
import {
  altitudeAxisUnitLabel,
  type DisplayUnits,
  formatAltitudeAxisValue,
  formatAltitudeKm,
  formatWindSpeed,
  WIND_SPEED_UNIT_LABEL,
} from "../model/units";
import { altitudeToY, buildAltitudeTicks, yToAltitude } from "./altitudeAxis";
import {
  type CanvasLayout,
  drawLine,
  drawText,
  fillRect,
  fillRoundRect,
  measureText,
  strokeCircle,
  strokeRect,
  strokeRoundRect,
  type TextPaint,
} from "./canvasKit";
import { windSpeedColor } from "./colorScales";
import { gridBackground, rgba, type ThemeColors } from "./theme";
import { useAltitudeZoom } from "./useAltitudeZoom";
import { useChartCanvas } from "./useChartCanvas";
import type { WindForecastCellUiModel, WindForecastChartUiModel } from "./windChart";

const WIND_AXIS_WIDTH = 60;
const WIND_BOTTOM_AXIS_HEIGHT = 48;
const WIND_MIN_VISIBLE_ALTITUDE_RANGE_KM = 0.75;

interface WindSample {
  altKm: number;
  speedKmh: number;
  directionDeg: number;
}

function windUV(speedKmh: number, directionDeg: number): [number, number] {
  const rad = (directionDeg * Math.PI) / 180;
  return [speedKmh * Math.sin(rad), speedKmh * Math.cos(rad)];
}

function interpolateWind(profile: WindSample[], targetKm: number): WindSample | null {
  if (profile.length === 0) return null;
  const first = profile[0];
  const last = profile[profile.length - 1];
  if (targetKm <= first.altKm) return first;
  if (targetKm >= last.altKm) return last;

  let lowerIndex = 0;
  while (lowerIndex < profile.length - 1 && profile[lowerIndex + 1].altKm <= targetKm) lowerIndex++;
  const lower = profile[lowerIndex];
  const upper = profile[lowerIndex + 1];
  const span = upper.altKm - lower.altKm;
  if (span <= 0) return lower;
  const t = Math.min(Math.max((targetKm - lower.altKm) / span, 0), 1);
  const [lu, lv] = windUV(lower.speedKmh, lower.directionDeg);
  const [uu, uv] = windUV(upper.speedKmh, upper.directionDeg);
  const u = lu + (uu - lu) * t;
  const v = lv + (uv - lv) * t;
  const speed = Math.hypot(u, v);
  const direction = ((Math.atan2(u, v) * 180) / Math.PI + 360) % 360;
  return { altKm: targetKm, speedKmh: speed, directionDeg: direction };
}

function drawWindArrow(
  ctx: CanvasLayout["ctx"],
  centerX: number,
  centerY: number,
  directionDeg: number,
  arrowSize: number,
  speedKmh: number,
  color: string,
): void {
  const goingToDeg = (directionDeg + 180) % 360;
  const angleRad = ((goingToDeg - 90) * Math.PI) / 180;
  const halfSize = (arrowSize / 2) * 0.7;
  const tipX = centerX + Math.cos(angleRad) * halfSize;
  const tipY = centerY + Math.sin(angleRad) * halfSize;
  const tailX = centerX - Math.cos(angleRad) * halfSize;
  const tailY = centerY - Math.sin(angleRad) * halfSize;
  const strokeWidth = Math.min(2 + speedKmh / 50, 3.5);
  drawLine(ctx, tailX, tailY, tipX, tipY, { color, width: strokeWidth, cap: "round" });
  const arrowLen = halfSize * 0.35;
  const arrowAngle = Math.PI / 6;
  drawLine(
    ctx,
    tipX,
    tipY,
    tipX - Math.cos(angleRad - arrowAngle) * arrowLen,
    tipY - Math.sin(angleRad - arrowAngle) * arrowLen,
    { color, width: strokeWidth, cap: "round" },
  );
  drawLine(
    ctx,
    tipX,
    tipY,
    tipX - Math.cos(angleRad + arrowAngle) * arrowLen,
    tipY - Math.sin(angleRad + arrowAngle) * arrowLen,
    { color, width: strokeWidth, cap: "round" },
  );
}

export interface WindChartViewProps {
  chart: WindForecastChartUiModel;
  visibleTopAltitudeKm: number;
  elevationKm: number;
  displayUnits: DisplayUnits;
  theme: ThemeColors;
  onVisibleTopAltitudeChange: (topAltitudeKm: number) => void;
}

export function WindChartView(props: WindChartViewProps): React.JSX.Element {
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
      drawWind(layout, chart, visibleTopAltitudeKm, elevationKm, displayUnits, theme, crosshair);
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
          if (e.pressure === 0 && e.pointerType === "touch") return;
          setCrosshair(toLocal(e));
        }}
      />
    </div>
  );
}

function drawWind(
  layout: CanvasLayout,
  chart: WindForecastChartUiModel,
  visibleTopAltitudeKm: number,
  elevationKm: number,
  displayUnits: DisplayUnits,
  theme: ThemeColors,
  crosshair: { x: number; y: number } | null,
): void {
  const { ctx, width, height } = layout;
  const axisColor = theme.onSurfaceVariant;
  const onSurface = theme.onSurface;
  const outline = theme.outlineVariant;
  const bg = gridBackground(theme, 0.035);
  fillRect(ctx, 0, 0, width, height, rgba(theme.surface));
  if (chart.hours.length === 0) return;

  const axisWidth = WIND_AXIS_WIDTH;
  const arrowSizePx = 48;
  const plotLeft = axisWidth;
  const plotTop = 0;
  const plotRight = width;
  const plotBottom = height - WIND_BOTTOM_AXIS_HEIGHT;
  const plotWidth = plotRight - plotLeft;
  const plotHeight = plotBottom - plotTop;

  const minAltitudeKm = elevationKm;
  const effectiveTop = Math.max(
    elevationKm + visibleTopAltitudeKm,
    minAltitudeKm + WIND_MIN_VISIBLE_ALTITUDE_RANGE_KM,
  );
  if (plotWidth <= 0 || plotHeight <= 0) return;

  fillRect(ctx, 0, plotTop, axisWidth, plotHeight, rgba(bg));
  fillRect(ctx, plotLeft, plotTop, plotWidth, plotHeight, rgba(bg));

  const columnWidth = plotWidth / chart.hours.length;
  const visibleBands = chart.altitudeBands.filter(
    (b) => b.topKm > minAltitudeKm && b.bottomKm < effectiveTop,
  );
  if (visibleBands.length === 0) return;
  const hourCluster = Math.max(1, Math.ceil((arrowSizePx * 1.1) / columnWidth));

  const cellLookup = new Map<string, WindForecastCellUiModel>();
  for (const c of chart.cells) {
    const key = `${c.hour}|${c.altitudeKm}`;
    if (!cellLookup.has(key)) cellLookup.set(key, c);
  }

  const yFor = (altKm: number): number =>
    altitudeToY(altKm, minAltitudeKm, effectiveTop, plotTop, plotBottom);

  // Wind-speed background cells.
  chart.hours.forEach((hour, hourIndex) => {
    const x = plotLeft + hourIndex * columnWidth;
    for (const band of visibleBands) {
      const cell = cellLookup.get(`${hour}|${band.centerKm}`);
      if (cell === undefined) continue;
      const topY = yFor(Math.min(band.topKm, effectiveTop));
      const bottomY = yFor(Math.max(band.bottomKm, minAltitudeKm));
      fillRect(
        ctx,
        x,
        topY,
        columnWidth,
        bottomY - topY,
        rgba(windSpeedColor(cell.speedKmh), 0.68),
      );
    }
  });

  const firstHour = chart.hours[0];
  chart.hours.forEach((hour, index) => {
    const x = plotLeft + index * columnWidth;
    const alpha = (hour - firstHour) % 3 === 0 ? 0.5 : 0.22;
    drawLine(ctx, x, plotTop, x, plotBottom, { color: rgba(outline, alpha) });
  });

  const altitudeTicks = buildAltitudeTicks(
    minAltitudeKm,
    effectiveTop,
    effectiveTop <= 3.5 ? 0.5 : 1,
  );
  for (const altKm of altitudeTicks) {
    const y = yFor(altKm);
    drawLine(ctx, 0, y, plotRight, y, { color: rgba(outline, 0.35) });
  }
  strokeRect(ctx, plotLeft, plotTop, plotWidth, plotHeight, rgba(outline, 0.4));

  const linePath = (
    markers: { hour: number; altitudeKm: number }[],
    color: string,
    widthPx: number,
  ): void => {
    ctx.save();
    ctx.beginPath();
    ctx.strokeStyle = color;
    ctx.lineWidth = widthPx;
    ctx.lineCap = "round";
    ctx.lineJoin = "round";
    let started = false;
    for (const marker of markers) {
      const hourIndex = chart.hours.indexOf(marker.hour);
      if (hourIndex < 0) continue;
      const x = plotLeft + hourIndex * columnWidth + columnWidth / 2;
      const y = yFor(marker.altitudeKm);
      if (y < plotTop || y > plotBottom) continue;
      if (!started) {
        ctx.moveTo(x, y);
        started = true;
      } else ctx.lineTo(x, y);
    }
    if (started) ctx.stroke();
    ctx.restore();
  };
  linePath(chart.cclKm, rgba([0xff, 0x8c, 0x00]), 2.5);
  linePath(chart.freezingLevelKm, rgba([0x00, 0xbc, 0xd4]), 2);

  // Wind arrows: resample the per-hour profile onto an even grid.
  const clusteredHours = chart.hours.filter((_, i) => i % hourCluster === 0);
  const minArrowSpacingPx = arrowSizePx * 1.1;
  const arrowDrawSize = Math.min(arrowSizePx, columnWidth * hourCluster * 0.8);
  const profileByHour = new Map<number, WindSample[]>();
  for (const hour of chart.hours) {
    const samples: WindSample[] = [];
    for (const altKm of chart.altitudeBandsKm) {
      const cell = cellLookup.get(`${hour}|${altKm}`);
      if (cell === undefined) continue;
      samples.push({ altKm, speedKmh: cell.speedKmh, directionDeg: cell.directionDeg });
    }
    if (samples.length > 0) profileByHour.set(hour, samples);
  }
  const lowAltKm = Math.max(minAltitudeKm, visibleBands[0].centerKm);
  const highAltKm = Math.min(effectiveTop, visibleBands[visibleBands.length - 1].centerKm);
  const kmPerPx = (effectiveTop - minAltitudeKm) / plotHeight;
  const altStepKm = Math.max(minArrowSpacingPx * kmPerPx, 0.01);
  const arrowAltitudes: number[] = [];
  for (let a = lowAltKm; a <= highAltKm + 0.0001; a += altStepKm) arrowAltitudes.push(a);
  if (arrowAltitudes.length === 0) arrowAltitudes.push(lowAltKm);

  for (const hour of clusteredHours) {
    const hourIndex = chart.hours.indexOf(hour);
    const cellCenterX = plotLeft + hourIndex * columnWidth + (columnWidth * hourCluster) / 2;
    const profile = profileByHour.get(hour);
    if (profile === undefined) continue;
    for (const altKm of arrowAltitudes) {
      const sample = interpolateWind(profile, altKm);
      if (sample === null) continue;
      drawWindArrow(
        ctx,
        cellCenterX,
        yFor(altKm),
        sample.directionDeg,
        arrowDrawSize,
        sample.speedKmh,
        rgba(onSurface),
      );
    }
  }

  // Axis labels + legend + time labels + speed labels.
  const axisPaint: TextPaint = { color: rgba(axisColor), sizePx: 12 };
  for (const altKm of altitudeTicks) {
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

  const legendSteps = [0, 5, 10, 15, 20, 30, 40, 50, 60];
  const legendY = plotBottom + 4;
  const swatchH = 6;
  const legendItemWidth = plotWidth / legendSteps.length;
  const legendLabelPaint: TextPaint = { color: rgba(axisColor), sizePx: 9, align: "center" };
  legendSteps.forEach((speed, index) => {
    const lx = plotLeft + index * legendItemWidth;
    const swatchW = legendItemWidth * 0.6;
    fillRoundRect(
      ctx,
      lx + (legendItemWidth - swatchW) / 2,
      legendY,
      swatchW,
      swatchH,
      2,
      rgba(windSpeedColor(speed), 0.92),
    );
    drawText(
      ctx,
      formatWindSpeed(speed, displayUnits, false),
      lx + legendItemWidth / 2,
      legendY + swatchH + 9 + 1,
      legendLabelPaint,
    );
  });
  drawText(
    ctx,
    WIND_SPEED_UNIT_LABEL[displayUnits.windSpeed],
    plotLeft - 20,
    legendY + swatchH + 9 + 1,
    legendLabelPaint,
  );

  const timePaint: TextPaint = {
    color: rgba(axisColor),
    sizePx: 11,
    align: "center",
    weight: "bold",
  };
  const timeBaseline = height - 4;
  chart.hours.forEach((hour, index) => {
    if ((hour - firstHour) % 3 !== 0) return;
    drawText(
      ctx,
      String(hour).padStart(2, "0"),
      plotLeft + index * columnWidth + columnWidth / 2,
      timeBaseline,
      timePaint,
    );
  });

  const speedPaint: TextPaint = {
    color: rgba(onSurface),
    sizePx: 9,
    align: "center",
    weight: "bold",
  };
  for (const hour of clusteredHours) {
    const hourIndex = chart.hours.indexOf(hour);
    const cellCenterX = plotLeft + hourIndex * columnWidth + (columnWidth * hourCluster) / 2;
    const profile = profileByHour.get(hour);
    if (profile === undefined) continue;
    for (const altKm of arrowAltitudes) {
      const sample = interpolateWind(profile, altKm);
      if (sample === null) continue;
      const labelBaseline = yFor(altKm) + arrowDrawSize / 2 + 9 + 1;
      if (labelBaseline > plotBottom - 2) continue;
      drawText(
        ctx,
        formatWindSpeed(sample.speedKmh, displayUnits, false),
        cellCenterX,
        labelBaseline,
        speedPaint,
      );
    }
  }

  const firstCcl = chart.cclKm[0];
  if (firstCcl !== undefined) {
    const y = yFor(firstCcl.altitudeKm);
    if (y >= plotTop && y <= plotBottom) {
      drawText(ctx, "CCL", 30, y - 4, {
        color: rgba([0xff, 0x8c, 0x00]),
        sizePx: 10,
        weight: "bold",
      });
    }
  }
  const firstFl = chart.freezingLevelKm[0];
  if (firstFl !== undefined) {
    const y = yFor(firstFl.altitudeKm);
    if (y >= plotTop && y <= plotBottom) {
      drawText(ctx, "❄ 0°C", 30, y - 4, {
        color: rgba([0x00, 0xbc, 0xd4]),
        sizePx: 10,
        weight: "bold",
      });
    }
  }

  // Crosshair overlay + tooltip.
  if (crosshair !== null) {
    const cx = Math.min(Math.max(crosshair.x, plotLeft), plotRight);
    const cy = Math.min(Math.max(crosshair.y, plotTop), plotBottom);
    drawLine(ctx, cx, plotTop, cx, plotBottom, { color: rgba(onSurface, 0.5), dash: [8, 6] });
    drawLine(ctx, plotLeft, cy, plotRight, cy, { color: rgba(onSurface, 0.5), dash: [8, 6] });
    const reticleR = 18;
    strokeCircle(ctx, cx, cy, reticleR, rgba(onSurface, 0.7), 2);
    const tick = 4;
    for (const angleDeg of [0, 90, 180, 270]) {
      const rad = (angleDeg * Math.PI) / 180;
      drawLine(
        ctx,
        cx + Math.cos(rad) * (reticleR - tick),
        cy + Math.sin(rad) * (reticleR - tick),
        cx + Math.cos(rad) * (reticleR + tick),
        cy + Math.sin(rad) * (reticleR + tick),
        { color: rgba(onSurface, 0.7), width: 2 },
      );
    }
    const altKm = yToAltitude(cy, minAltitudeKm, effectiveTop, plotTop, plotBottom);
    const hourIdx = Math.min(
      Math.max(Math.trunc((cx - plotLeft) / columnWidth), 0),
      chart.hours.length - 1,
    );
    const hour = chart.hours[hourIdx];
    let nearestCell: WindForecastCellUiModel | null = null;
    let nearestDiff = Number.POSITIVE_INFINITY;
    for (const c of chart.cells) {
      if (c.hour !== hour) continue;
      const diff = Math.abs(c.altitudeKm - altKm);
      if (diff < nearestDiff) {
        nearestDiff = diff;
        nearestCell = c;
      }
    }
    const tooltipPaint: TextPaint = { color: rgba(onSurface), sizePx: 12, weight: "bold" };
    const tooltipLines = [
      `${String(hour).padStart(2, "0")}h  ${formatAltitudeKm(altKm, displayUnits)}`,
    ];
    if (nearestCell !== null) {
      tooltipLines.push(
        `${formatWindSpeed(nearestCell.speedKmh, displayUnits)}  ${Math.trunc(nearestCell.directionDeg)}°`,
      );
    }
    const lineH = 12 * 1.3;
    const maxTextW = Math.max(...tooltipLines.map((l) => measureText(ctx, l, tooltipPaint)));
    const padH = 8;
    const padV = 6;
    const ttW = maxTextW + padH * 2;
    const ttH = lineH * tooltipLines.length + padV * 2;
    const ttX = cx + reticleR + ttW + 8 < plotRight ? cx + reticleR + 8 : cx - reticleR - ttW - 8;
    const ttY = Math.min(Math.max(cy - ttH / 2, plotTop), plotBottom - ttH);
    fillRoundRect(ctx, ttX, ttY, ttW, ttH, 4, rgba(bg, 0.92));
    strokeRoundRect(ctx, ttX, ttY, ttW, ttH, 4, rgba(onSurface, 0.3), 1);
    tooltipLines.forEach((line, idx) => {
      drawText(ctx, line, ttX + padH, ttY + padV + (idx + 1) * lineH - lineH * 0.15, tooltipPaint);
    });
  }
}
