/**
 * Small helpers that reproduce the Kotlin collection operators used by the
 * engine, with the same ordering guarantees.
 *
 * `Array.prototype.sort` is a stable sort in modern JS engines (and in Node,
 * which runs the tests), so `sortedBy` / `sortedByDescending` preserve the
 * relative order of equal keys exactly like Kotlin's stable sorts.
 */

function compareNumbersAscending(a: number, b: number): number {
  if (a < b) return -1;
  if (a > b) return 1;
  return 0;
}

/** Kotlin `Iterable<T>.sortedBy { selector }` (ascending, stable). */
export function sortedBy<T>(list: readonly T[], selector: (item: T) => number): T[] {
  return [...list].sort((a, b) => compareNumbersAscending(selector(a), selector(b)));
}

/** Kotlin `Iterable<T>.sortedByDescending { selector }` (descending, stable). */
export function sortedByDescending<T>(list: readonly T[], selector: (item: T) => number): T[] {
  return [...list].sort((a, b) => compareNumbersAscending(selector(b), selector(a)));
}

/** Kotlin `Iterable<T>.distinctBy { key }` — keeps the first element per key. */
export function distinctBy<T, K>(list: readonly T[], key: (item: T) => K): T[] {
  const seen = new Set<K>();
  const result: T[] = [];
  for (const item of list) {
    const k = key(item);
    if (!seen.has(k)) {
      seen.add(k);
      result.push(item);
    }
  }
  return result;
}

/** Kotlin `Iterable<T>.distinct()` — keeps the first occurrence of each value. */
export function distinct<T>(list: readonly T[]): T[] {
  const seen = new Set<T>();
  const result: T[] = [];
  for (const item of list) {
    if (!seen.has(item)) {
      seen.add(item);
      result.push(item);
    }
  }
  return result;
}
