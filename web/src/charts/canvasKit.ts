/**
 * Thin Canvas 2D drawing helpers that mirror the Compose `DrawScope` /
 * `android.graphics.Paint` primitives the Kotlin chart views use. This keeps the
 * ports mechanical: only the paint/text calls change, geometry stays identical.
 *
 * All helpers work in CSS pixels. Callers first prepare the context with
 * {@link setupHiDpiCanvas} so `devicePixelRatio` scaling is transparent. Because
 * the web viewport is already density-independent, Android `dp`/`sp` map 1:1 to
 * CSS pixels (`1.dp.toPx()` -> `1`).
 */

export type Ctx = CanvasRenderingContext2D;

export interface CanvasLayout {
  ctx: Ctx;
  /** Logical (CSS) width in pixels. */
  width: number;
  /** Logical (CSS) height in pixels. */
  height: number;
}

/**
 * Size a canvas for the current `devicePixelRatio` and return a context already
 * scaled so that all drawing uses logical CSS pixels. Clears any prior content.
 */
export function setupHiDpiCanvas(canvas: HTMLCanvasElement): CanvasLayout | null {
  const rect = canvas.getBoundingClientRect();
  const width = rect.width;
  const height = rect.height;
  if (width <= 0 || height <= 0) return null;
  const dpr = window.devicePixelRatio || 1;
  canvas.width = Math.round(width * dpr);
  canvas.height = Math.round(height * dpr);
  const ctx = canvas.getContext("2d");
  if (ctx === null) return null;
  ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
  ctx.clearRect(0, 0, width, height);
  return { ctx, width, height };
}

export type StrokeCap = "butt" | "round" | "square";

export interface LineOptions {
  color: string;
  width?: number;
  dash?: number[];
  cap?: StrokeCap;
}

export function drawLine(
  ctx: Ctx,
  x1: number,
  y1: number,
  x2: number,
  y2: number,
  options: LineOptions,
): void {
  if (
    !Number.isFinite(x1) ||
    !Number.isFinite(y1) ||
    !Number.isFinite(x2) ||
    !Number.isFinite(y2)
  ) {
    return;
  }
  ctx.save();
  ctx.beginPath();
  ctx.strokeStyle = options.color;
  ctx.lineWidth = options.width ?? 1;
  ctx.lineCap = options.cap ?? "butt";
  ctx.setLineDash(options.dash ?? []);
  ctx.moveTo(x1, y1);
  ctx.lineTo(x2, y2);
  ctx.stroke();
  ctx.restore();
}

export function fillRect(
  ctx: Ctx,
  x: number,
  y: number,
  w: number,
  h: number,
  color: string,
): void {
  ctx.fillStyle = color;
  ctx.fillRect(x, y, w, h);
}

export function strokeRect(
  ctx: Ctx,
  x: number,
  y: number,
  w: number,
  h: number,
  color: string,
  width = 1,
): void {
  ctx.save();
  ctx.strokeStyle = color;
  ctx.lineWidth = width;
  ctx.setLineDash([]);
  ctx.strokeRect(x, y, w, h);
  ctx.restore();
}

function roundRectPath(ctx: Ctx, x: number, y: number, w: number, h: number, r: number): void {
  const radius = Math.min(r, Math.abs(w) / 2, Math.abs(h) / 2);
  ctx.beginPath();
  ctx.moveTo(x + radius, y);
  ctx.arcTo(x + w, y, x + w, y + h, radius);
  ctx.arcTo(x + w, y + h, x, y + h, radius);
  ctx.arcTo(x, y + h, x, y, radius);
  ctx.arcTo(x, y, x + w, y, radius);
  ctx.closePath();
}

export function fillRoundRect(
  ctx: Ctx,
  x: number,
  y: number,
  w: number,
  h: number,
  radius: number,
  color: string,
): void {
  if (w <= 0 || h <= 0) return;
  ctx.fillStyle = color;
  roundRectPath(ctx, x, y, w, h, radius);
  ctx.fill();
}

export function strokeRoundRect(
  ctx: Ctx,
  x: number,
  y: number,
  w: number,
  h: number,
  radius: number,
  color: string,
  width = 1,
): void {
  if (w <= 0 || h <= 0) return;
  ctx.save();
  ctx.strokeStyle = color;
  ctx.lineWidth = width;
  ctx.setLineDash([]);
  roundRectPath(ctx, x, y, w, h, radius);
  ctx.stroke();
  ctx.restore();
}

export function fillCircle(ctx: Ctx, cx: number, cy: number, r: number, color: string): void {
  if (!Number.isFinite(cx) || !Number.isFinite(cy) || r <= 0) return;
  ctx.beginPath();
  ctx.fillStyle = color;
  ctx.arc(cx, cy, r, 0, Math.PI * 2);
  ctx.fill();
}

export function strokeCircle(
  ctx: Ctx,
  cx: number,
  cy: number,
  r: number,
  color: string,
  width = 1,
): void {
  if (!Number.isFinite(cx) || !Number.isFinite(cy) || r <= 0) return;
  ctx.save();
  ctx.beginPath();
  ctx.strokeStyle = color;
  ctx.lineWidth = width;
  ctx.setLineDash([]);
  ctx.arc(cx, cy, r, 0, Math.PI * 2);
  ctx.stroke();
  ctx.restore();
}

export function fillPolygon(ctx: Ctx, points: Array<[number, number]>, color: string): void {
  if (points.length < 2) return;
  ctx.beginPath();
  ctx.fillStyle = color;
  ctx.moveTo(points[0][0], points[0][1]);
  for (let i = 1; i < points.length; i++) {
    ctx.lineTo(points[i][0], points[i][1]);
  }
  ctx.closePath();
  ctx.fill();
}

export type TextAlign = "left" | "center" | "right";
export type FontWeight = "normal" | "bold";

export interface TextPaint {
  color: string;
  sizePx: number;
  align?: TextAlign;
  weight?: FontWeight;
  family?: string;
}

function applyFont(ctx: Ctx, paint: TextPaint): void {
  const weight = paint.weight === "bold" ? "bold " : "";
  const family = paint.family ?? "ui-monospace, 'SF Mono', 'Roboto Mono', Menlo, monospace";
  ctx.font = `${weight}${paint.sizePx}px ${family}`;
  ctx.fillStyle = paint.color;
  ctx.textAlign = paint.align ?? "left";
  ctx.textBaseline = "alphabetic";
}

/** Draw text with the baseline at `y` (matches Android `drawText`). */
export function drawText(ctx: Ctx, text: string, x: number, y: number, paint: TextPaint): void {
  if (!Number.isFinite(x) || !Number.isFinite(y)) return;
  ctx.save();
  applyFont(ctx, paint);
  ctx.fillText(text, x, y);
  ctx.restore();
}

export function measureText(ctx: Ctx, text: string, paint: TextPaint): number {
  ctx.save();
  applyFont(ctx, paint);
  const width = ctx.measureText(text).width;
  ctx.restore();
  return width;
}

/** Run `body` with a rectangular clip applied, mirroring `clipRect { ... }`. */
export function withClip(
  ctx: Ctx,
  left: number,
  top: number,
  right: number,
  bottom: number,
  body: () => void,
): void {
  ctx.save();
  ctx.beginPath();
  ctx.rect(left, top, right - left, bottom - top);
  ctx.clip();
  body();
  ctx.restore();
}
