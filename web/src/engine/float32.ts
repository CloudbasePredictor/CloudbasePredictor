/**
 * 32-bit floating-point emulation for the engine port.
 *
 * The Kotlin forecast engine (`domain/forecast/*.kt`) performs all of its
 * arithmetic in `Float` (IEEE-754 single precision). JavaScript `number` is
 * always double precision, so a naive double port would diverge from the Kotlin
 * results — most visibly at the `(x * 10f).toInt() / 10f` update-strength
 * quantisation boundaries, where a sub-ULP difference flips an integer
 * truncation and produces a 0.1 m/s jump.
 *
 * To keep the TypeScript engine numerically identical to the Kotlin `Float`
 * math, every arithmetic operation is rounded to float32 with `Math.fround`,
 * exactly mirroring how the JVM rounds each `Float` operation. Kotlin
 * `Float.pow` / `sqrt` / `exp` / `ln` are `(nativeMath.op(x.toDouble())).toFloat()`,
 * i.e. a double computation rounded to float — reproduced here the same way.
 *
 * Naming: `f(x)` rounds a constant/result to float32; `fadd/fsub/fmul/fdiv`
 * wrap the four binary operators; `fpow/fexp/fln/fsqrt` wrap the math
 * functions. `Math.abs/min/max` need no rounding because they return one of
 * their (already float32) inputs unchanged.
 */

/** Round a double to the nearest float32 value (Kotlin `Double.toFloat()` / a `Float` literal). */
export const f = Math.fround;

/** Kotlin `Float + Float`. */
export function fadd(a: number, b: number): number {
  return Math.fround(a + b);
}

/** Kotlin `Float - Float`. */
export function fsub(a: number, b: number): number {
  return Math.fround(a - b);
}

/** Kotlin `Float * Float`. */
export function fmul(a: number, b: number): number {
  return Math.fround(a * b);
}

/** Kotlin `Float / Float`. */
export function fdiv(a: number, b: number): number {
  return Math.fround(a / b);
}

/** Kotlin `Float.pow(Float)`. */
export function fpow(base: number, exponent: number): number {
  return Math.fround(base ** exponent);
}

/** Kotlin `exp(Float)`. */
export function fexp(x: number): number {
  return Math.fround(Math.exp(x));
}

/** Kotlin `ln(Float)`. */
export function fln(x: number): number {
  return Math.fround(Math.log(x));
}

/** Kotlin `sqrt(Float)`. */
export function fsqrt(x: number): number {
  return Math.fround(Math.sqrt(x));
}

/** Kotlin `Float.toInt()` — truncation toward zero. */
export function toInt(x: number): number {
  return Math.trunc(x);
}

/** Kotlin `Float.coerceIn(min, max)`. */
export function coerceIn(value: number, minimum: number, maximum: number): number {
  if (value < minimum) return minimum;
  if (value > maximum) return maximum;
  return value;
}

/** Kotlin `Float.coerceAtLeast(min)`. */
export function coerceAtLeast(value: number, minimum: number): number {
  return value < minimum ? minimum : value;
}

/** Kotlin `Float.coerceAtMost(max)`. */
export function coerceAtMost(value: number, maximum: number): number {
  return value > maximum ? maximum : value;
}

/**
 * Kotlin `List<Float>.average()` — sums the values as `Double` and divides by
 * the count without an intermediate float32 rounding; the caller applies
 * `.toFloat()` afterwards where Kotlin does.
 */
export function averageOfFloats(values: number[]): number {
  let sum = 0;
  for (const value of values) sum += value;
  return sum / values.length;
}
