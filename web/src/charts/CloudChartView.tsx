/**
 * Cloud forecast chart (sunshine / radiation / cloud layers / rain rows).
 *
 * Canvas 2D port of `ui/screens/forecast/views/CloudForecastView.kt`. The
 * Android version scrolls the rows above a pinned time axis; here the four rows
 * are laid out to fill the available height with a fixed bottom time axis
 * (honest simplification: no inner scroll — the rows always fit the viewport).
 */

import { useCallback } from "react";
import {
  type CanvasLayout,
  drawLine,
  drawText,
  fillCircle,
  fillRect,
  fillRoundRect,
  measureText,
  type TextPaint,
} from "./canvasKit";
import type { CloudForecastChartUiModel } from "./cloudChart";
import { gridBackground, mixRgb, type Rgb, rgba, type ThemeColors } from "./theme";
import { useChartCanvas } from "./useChartCanvas";

const LEFT_AXIS_WIDTH = 60;
const TOP_CLEARANCE = 12;
const SUNSHINE_ROW_HEIGHT = 32;
const RADIATION_ROW_HEIGHT = 48;
const CLOUD_LAYERS_HEIGHT = 144;
const RAIN_ROW_HEIGHT = 48;
const TIME_AXIS_HEIGHT = 28;
const ROW_GAP_COUNT = 4;
const CLOUD_COLOR: Rgb = [0x78, 0x90, 0x9c];

function precipColor(amountMm: number): Rgb {
  const normalized = Math.min(Math.max(amountMm / 8, 0), 1);
  const light: Rgb = [0x90, 0xca, 0xf9];
  const medium: Rgb = [0x42, 0xa5, 0xf5];
  const heavy: Rgb = [0x15, 0x65, 0xc0];
  return normalized <= 0.5
    ? mixRgb(light, medium, normalized / 0.5)
    : mixRgb(medium, heavy, (normalized - 0.5) / 0.5);
}

function radiationColor(wm2: number): Rgb {
  const normalized = Math.min(Math.max(wm2 / 800, 0), 1);
  const low: Rgb = [0xff, 0xf9, 0xc4];
  const mid: Rgb = [0xff, 0xb3, 0x00];
  const high: Rgb = [0xff, 0x6f, 0x00];
  return normalized <= 0.5
    ? mixRgb(low, mid, normalized / 0.5)
    : mixRgb(mid, high, (normalized - 0.5) / 0.5);
}

export function CloudChartView({
  chart,
  theme,
}: {
  chart: CloudForecastChartUiModel;
  theme: ThemeColors;
}): React.JSX.Element {
  const draw = useCallback(
    (layout: CanvasLayout) => {
      drawCloud(layout, chart, theme);
    },
    [chart, theme],
  );
  const ref = useChartCanvas(draw);
  return <canvas ref={ref} className="chart-canvas" />;
}

function drawCloud(
  layout: CanvasLayout,
  chart: CloudForecastChartUiModel,
  theme: ThemeColors,
): void {
  const { ctx, width, height } = layout;
  const axisColor = theme.onSurfaceVariant;
  const outline = theme.outlineVariant;
  const bg = gridBackground(theme, 0.035);
  fillRect(ctx, 0, 0, width, height, rgba(theme.surface));
  if (chart.hours.length === 0) return;

  const plotLeft = LEFT_AXIS_WIDTH;
  const plotWidth = width - plotLeft;
  if (plotWidth <= 0) return;
  const columnWidth = plotWidth / chart.hours.length;

  const minRows =
    TOP_CLEARANCE +
    SUNSHINE_ROW_HEIGHT +
    RADIATION_ROW_HEIGHT +
    CLOUD_LAYERS_HEIGHT +
    RAIN_ROW_HEIGHT;
  const scrollViewport = Math.max(0, height - TIME_AXIS_HEIGHT);
  const rowSpacing = scrollViewport > minRows ? (scrollViewport - minRows) / ROW_GAP_COUNT : 0;

  const firstHour = chart.hours[0];
  const hourIndex = (hour: number): number => chart.hours.indexOf(hour);

  const verticalGrid = (top: number, bottom: number): void => {
    chart.hours.forEach((hour, index) => {
      const x = plotLeft + index * columnWidth;
      const alpha = (hour - firstHour) % 3 === 0 ? 0.4 : 0.18;
      drawLine(ctx, x, top, x, bottom, { color: rgba(outline, alpha) });
    });
  };
  const bottomRule = (y: number): void =>
    drawLine(ctx, 0, y, width, y, { color: rgba(outline, 0.4) });

  const labelPaint = (
    color: Rgb,
    size: number,
    weight: "normal" | "bold" = "normal",
  ): TextPaint => ({
    color: rgba(color),
    sizePx: size,
    weight,
  });
  const clusterFor = (sample: string, paint: TextPaint): number =>
    Math.max(1, Math.ceil((measureText(ctx, sample, paint) * 1.3) / columnWidth));

  let y = TOP_CLEARANCE;

  // --- Sunshine row ---
  {
    const rowTop = y;
    const rowH = SUNSHINE_ROW_HEIGHT;
    fillRect(ctx, 0, rowTop, width, rowH, rgba(bg));
    verticalGrid(rowTop, rowTop + rowH);
    const midY = rowTop + rowH / 2;
    for (const sun of chart.sunshine) {
      const idx = hourIndex(sun.hour);
      if (idx < 0) continue;
      const cx = plotLeft + idx * columnWidth + columnWidth / 2;
      const fraction = Math.min(Math.max(sun.durationS / 3600, 0), 1);
      if (fraction > 0.01) {
        const maxRadius = Math.min(columnWidth, rowH) * 0.35;
        fillCircle(
          ctx,
          cx,
          midY,
          maxRadius * (0.4 + 0.6 * fraction),
          rgba([0xff, 0xb3, 0x00], 0.3 + 0.5 * fraction),
        );
      }
    }
    drawText(ctx, "☀ h", 8, midY + 10 * 0.35, labelPaint(axisColor, 10));
    const valuePaint: TextPaint = {
      color: rgba([0xff, 0x8f, 0x00]),
      sizePx: 9,
      align: "center",
      weight: "bold",
    };
    const cluster = clusterFor("0.9", valuePaint);
    chart.sunshine.forEach((sun, idx) => {
      if (idx % cluster !== Math.floor(cluster / 2)) return;
      const hi = hourIndex(sun.hour);
      if (hi < 0) return;
      const hours = sun.durationS / 3600;
      if (hours > 0.05) {
        drawText(
          ctx,
          hours.toFixed(1),
          plotLeft + hi * columnWidth + columnWidth / 2,
          midY + 9 * 0.35,
          valuePaint,
        );
      }
    });
    bottomRule(rowTop + rowH);
    y = rowTop + rowH + rowSpacing;
  }

  // --- Radiation row ---
  {
    const rowTop = y;
    const rowH = RADIATION_ROW_HEIGHT;
    fillRect(ctx, 0, rowTop, width, rowH, rgba(bg));
    verticalGrid(rowTop, rowTop + rowH);
    for (const rad of chart.radiation) {
      const idx = hourIndex(rad.hour);
      if (idx < 0) continue;
      const x = plotLeft + idx * columnWidth;
      if (rad.radiationWm2 > 0) {
        const barHeight = Math.min(Math.max(rad.radiationWm2 / 800, 0), 1) * (rowH * 0.7);
        fillRoundRect(
          ctx,
          x + columnWidth * 0.15,
          rowTop + rowH - barHeight - 2,
          columnWidth * 0.7,
          barHeight,
          2,
          rgba(radiationColor(rad.radiationWm2)),
        );
      }
    }
    drawText(ctx, "W/m²", 8, rowTop + rowH / 2 + 10 * 0.35, labelPaint(axisColor, 10));
    const valuePaint: TextPaint = { color: rgba([0xff, 0x8f, 0x00]), sizePx: 9, align: "center" };
    const cluster = clusterFor("999", valuePaint);
    chart.radiation.forEach((rad, idx) => {
      if (idx % cluster !== Math.floor(cluster / 2)) return;
      const hi = hourIndex(rad.hour);
      if (hi < 0) return;
      if (rad.radiationWm2 > 5) {
        drawText(
          ctx,
          String(Math.trunc(rad.radiationWm2)),
          plotLeft + hi * columnWidth + columnWidth / 2,
          rowTop + 9 + 2,
          valuePaint,
        );
      }
    });
    bottomRule(rowTop + rowH);
    y = rowTop + rowH + rowSpacing;
  }

  // --- Cloud layers ---
  {
    const rowTop = y;
    const rowH = CLOUD_LAYERS_HEIGHT;
    fillRect(ctx, 0, rowTop, width, rowH, rgba(bg));
    const layerSpacing = 4;
    const layerHeight = (rowH - layerSpacing * 2) / 3;
    const layerTopY = (index: number): number => rowTop + index * (layerHeight + layerSpacing);
    verticalGrid(rowTop, rowTop + rowH);
    for (let i = 1; i <= 2; i++) {
      const dividerY = layerTopY(i) - layerSpacing / 2;
      drawLine(ctx, 0, dividerY, width, dividerY, { color: rgba(outline, 0.3) });
    }
    const cloudCell = (x: number, cellTop: number, percent: number): void => {
      const alpha = Math.min(Math.max(percent / 100, 0), 1) * 0.7;
      if (alpha > 0.02)
        fillRect(ctx, x, cellTop, columnWidth, layerHeight, rgba(CLOUD_COLOR, alpha));
    };
    for (const layer of chart.layers) {
      const idx = hourIndex(layer.hour);
      if (idx < 0) continue;
      const x = plotLeft + idx * columnWidth;
      cloudCell(x, layerTopY(0), layer.highCloudPercent);
      cloudCell(x, layerTopY(1), layer.midCloudPercent);
      cloudCell(x, layerTopY(2), layer.lowCloudPercent);
    }
    for (let i = 0; i <= 2; i++) {
      drawLine(ctx, plotLeft, layerTopY(i), plotLeft + plotWidth, layerTopY(i), {
        color: rgba(outline, 0.4),
      });
      drawLine(
        ctx,
        plotLeft,
        layerTopY(i) + layerHeight,
        plotLeft + plotWidth,
        layerTopY(i) + layerHeight,
        { color: rgba(outline, 0.4) },
      );
    }
    const names = ["High", "Mid", "Low"];
    names.forEach((name, index) => {
      drawText(
        ctx,
        name,
        8,
        layerTopY(index) + layerHeight / 2 + 11 * 0.35,
        labelPaint(axisColor, 11),
      );
    });
    const percentPaint: TextPaint = {
      color: rgba(axisColor),
      sizePx: 9,
      align: "center",
      weight: "bold",
    };
    const cluster = clusterFor("99%", percentPaint);
    chart.layers.forEach((layer, idx) => {
      if (idx % cluster !== Math.floor(cluster / 2)) return;
      const hi = hourIndex(layer.hour);
      if (hi < 0) return;
      const cx = plotLeft + hi * columnWidth + columnWidth / 2;
      if (layer.highCloudPercent > 5)
        drawText(
          ctx,
          `${Math.trunc(layer.highCloudPercent)}%`,
          cx,
          layerTopY(0) + layerHeight / 2 + 9 * 0.35,
          percentPaint,
        );
      if (layer.midCloudPercent > 5)
        drawText(
          ctx,
          `${Math.trunc(layer.midCloudPercent)}%`,
          cx,
          layerTopY(1) + layerHeight / 2 + 9 * 0.35,
          percentPaint,
        );
      if (layer.lowCloudPercent > 5)
        drawText(
          ctx,
          `${Math.trunc(layer.lowCloudPercent)}%`,
          cx,
          layerTopY(2) + layerHeight / 2 + 9 * 0.35,
          percentPaint,
        );
    });
    bottomRule(rowTop + rowH);
    y = rowTop + rowH + rowSpacing;
  }

  // --- Rain row ---
  {
    const rowTop = y;
    const rowH = RAIN_ROW_HEIGHT;
    fillRect(ctx, 0, rowTop, width, rowH, rgba(bg));
    verticalGrid(rowTop, rowTop + rowH);
    for (const precip of chart.precipitation) {
      const idx = hourIndex(precip.hour);
      if (idx < 0) continue;
      const x = plotLeft + idx * columnWidth;
      if (precip.amountMm > 0) {
        const barHeight = Math.min(Math.max(precip.amountMm / 8, 0), 1) * (rowH * 0.6);
        fillRoundRect(
          ctx,
          x + columnWidth * 0.15,
          rowTop + rowH - barHeight - 2,
          columnWidth * 0.7,
          barHeight,
          2,
          rgba(precipColor(precip.amountMm)),
        );
      }
    }
    drawText(ctx, "Rain", 8, rowTop + rowH / 2 + 10 * 0.35, labelPaint(axisColor, 10));
    const precipPaint: TextPaint = { color: rgba([0x15, 0x65, 0xc0]), sizePx: 9, align: "center" };
    const cluster = clusterFor("99%", precipPaint);
    chart.precipitation.forEach((precip, idx) => {
      if (idx % cluster !== Math.floor(cluster / 2)) return;
      const hi = hourIndex(precip.hour);
      if (hi < 0) return;
      const cx = plotLeft + hi * columnWidth + columnWidth / 2;
      if (precip.amountMm > 0) {
        drawText(ctx, `${Math.trunc(precip.probabilityPercent)}%`, cx, rowTop + 9 + 2, precipPaint);
        drawText(ctx, precip.amountMm.toFixed(1), cx, rowTop + 9 * 2 + 4, precipPaint);
      } else if (precip.probabilityPercent > 10) {
        drawText(ctx, `${Math.trunc(precip.probabilityPercent)}%`, cx, rowTop + 9 + 2, precipPaint);
      }
    });
    bottomRule(rowTop + rowH);
  }

  // --- Time axis (pinned bottom) ---
  {
    const axisTop = height - TIME_AXIS_HEIGHT;
    fillRect(ctx, 0, axisTop, width, TIME_AXIS_HEIGHT, rgba(bg));
    const hourPaint: TextPaint = {
      color: rgba(axisColor),
      sizePx: 12,
      align: "center",
      weight: "bold",
    };
    chart.hours.forEach((hour, index) => {
      if ((hour - firstHour) % 3 !== 0) return;
      const cx = plotLeft + index * columnWidth + columnWidth / 2;
      drawText(ctx, String(hour).padStart(2, "0"), cx, axisTop + 12 + 4, hourPaint);
    });
  }
}
