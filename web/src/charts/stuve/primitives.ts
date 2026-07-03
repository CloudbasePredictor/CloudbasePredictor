/**
 * Wind-barb geometry for the Stüve diagram.
 *
 * 1:1 port of the pure-geometry parts of
 * `ui/screens/forecast/views/StuveDiagramPrimitives.kt` (the `Paint`/`DrawScope`
 * drawing itself lives in the canvas component). Screen geometry runs in plain
 * doubles.
 */

export interface Vec2 {
  x: number;
  y: number;
}

export interface WindBarbSpeedParts {
  roundedKnots: number;
  flags: number;
  fullFeathers: number;
  halfFeathers: number;
}

export interface WindBarbLine {
  start: Vec2;
  end: Vec2;
}

export interface WindBarbFlag {
  points: [Vec2, Vec2, Vec2];
}

export interface WindBarbGeometry {
  shaft: WindBarbLine;
  flags: WindBarbFlag[];
  feathers: WindBarbLine[];
  calmRadius: number | null;
}

const KMH_PER_KNOT = 1.852;

function add(a: Vec2, b: Vec2): Vec2 {
  return { x: a.x + b.x, y: a.y + b.y };
}

function sub(a: Vec2, b: Vec2): Vec2 {
  return { x: a.x - b.x, y: a.y - b.y };
}

function scale(v: Vec2, s: number): Vec2 {
  return { x: v.x * s, y: v.y * s };
}

export function windBarbSpeedParts(speedKmh: number): WindBarbSpeedParts {
  const roundedKnots = Math.round(Math.max(speedKmh, 0) / KMH_PER_KNOT / 5) * 5;
  let remainingKnots = roundedKnots;
  const flags = Math.trunc(remainingKnots / 50);
  remainingKnots %= 50;
  const fullFeathers = Math.trunc(remainingKnots / 10);
  remainingKnots %= 10;
  const halfFeathers = remainingKnots >= 5 ? 1 : 0;
  return { roundedKnots, flags, fullFeathers, halfFeathers };
}

export function buildWindBarbGeometry(
  centerX: number,
  centerY: number,
  speedKmh: number,
  directionDeg: number,
  barbSize: number,
): WindBarbGeometry {
  const speedParts = windBarbSpeedParts(speedKmh);
  const center: Vec2 = { x: centerX, y: centerY };
  if (speedParts.roundedKnots === 0) {
    return {
      shaft: { start: center, end: center },
      flags: [],
      feathers: [],
      calmRadius: barbSize * 0.18,
    };
  }

  const angleRad = ((directionDeg - 90) * Math.PI) / 180;
  const shaftUnit: Vec2 = { x: Math.cos(angleRad), y: Math.sin(angleRad) };
  // Conventional northern-hemisphere barb side; no latitude input here.
  const featherSideUnit: Vec2 = { x: -shaftUnit.y, y: shaftUnit.x };
  const halfSize = barbSize / 2;
  const fromEnd = add(center, scale(shaftUnit, halfSize));
  const toEnd = sub(center, scale(shaftUnit, halfSize));

  const symbolCount = speedParts.flags + speedParts.fullFeathers + speedParts.halfFeathers;
  const spacing =
    symbolCount > 0 ? Math.min(barbSize * 0.16, (barbSize * 0.78) / symbolCount) : barbSize * 0.16;
  const featherBack = barbSize * 0.32;
  const featherOut = barbSize * 0.23;
  const flagBack = barbSize * 0.28;
  const flagOut = barbSize * 0.26;

  const flags: WindBarbFlag[] = [];
  const feathers: WindBarbLine[] = [];
  let offsetAlongShaft = 0;

  for (let i = 0; i < speedParts.flags; i++) {
    const attach = sub(fromEnd, scale(shaftUnit, offsetAlongShaft));
    const nextAttach = sub(fromEnd, scale(shaftUnit, offsetAlongShaft + spacing));
    const outer = add(sub(attach, scale(shaftUnit, flagBack)), scale(featherSideUnit, flagOut));
    flags.push({ points: [attach, outer, nextAttach] });
    offsetAlongShaft += spacing;
  }

  for (let i = 0; i < speedParts.fullFeathers; i++) {
    const attach = sub(fromEnd, scale(shaftUnit, offsetAlongShaft));
    feathers.push({
      start: attach,
      end: add(sub(attach, scale(shaftUnit, featherBack)), scale(featherSideUnit, featherOut)),
    });
    offsetAlongShaft += spacing;
  }

  for (let i = 0; i < speedParts.halfFeathers; i++) {
    const attach = sub(fromEnd, scale(shaftUnit, offsetAlongShaft));
    feathers.push({
      start: attach,
      end: add(
        sub(attach, scale(shaftUnit, featherBack * 0.68)),
        scale(featherSideUnit, featherOut * 0.58),
      ),
    });
  }

  return { shaft: { start: toEnd, end: fromEnd }, flags, feathers, calmRadius: null };
}
