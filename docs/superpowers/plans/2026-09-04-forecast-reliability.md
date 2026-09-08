# Forecast Reliability Implementation Plan

> Execute with the subagent-driven-development workflow. Do not commit or push.

**Goal:** Complete the four reliability improvements approved after the project review.

**Architecture:** Keep `engine` pure, place forecast validation and refresh coordination in
`shared`, and preserve Android/Browser storage and lifecycle adapters. Invalid or missing
hourly data must never become a ready forecast. Refresh failure preserves the last usable
forecast and schedules another attempt with bounded backoff. Cache invalidation must prevent
older loads from publishing results after a clear.

**Tech stack:** Kotlin Multiplatform, coroutines, Compose, Room, Ktor, Kotlin test/JUnit.

**Spec:** The project review and the user's instruction to implement its priorities.

## Constraints

- Preserve platform navigation, localization, display units, and progressive Android day loading.
- Keep source and documentation in English; keep preview fixtures in the preview data layer.
- Do not change scientific equations merely to match a new reference test.
- Run Gradle tasks sequentially. Use targeted tests for changed behavior; no real-network E2E.
- Do not change git history or publish artifacts.

## Tasks

- [x] Add regression tests for missing hourly fields, mismatched daily/hourly dates, and empty
  payloads in shared/web forecast presentation tests. Centralize validated ready-state creation
  and use it in both platforms without silently displaying another day's chart.
- [x] Extract a shared coroutine refresh coordinator, test failure/retry, cancellation, and
  manual refresh with virtual time. Integrate it into web and Android scheduling while keeping
  previous usable data visible during errors.
- [x] Add repository concurrency tests in Android. Coordinate cache publication and clearing,
  preserve unrelated keys, and prevent invalidated loads from restoring cleared data.
- [x] Add independently derived atmospheric reference cases in `engine/commonTest`, documenting
  equations, source provenance, units, and numerical tolerances separately from golden fixtures.
- [x] Run relevant JVM, browser-Wasm, static analysis, and compile checks where available; review
  the complete diff, address findings, and record any environment limitations.

## Verification record

- Android JVM: all 28 targeted repository, refresh ViewModel, and reducer tests passed.
- Shared JVM: all 147 tests passed, including validation and refresh controller regressions.
- Engine: all seven independent atmospheric reference tests passed on both JVM and Wasm/Node.
- Web/Wasm: all 66 browser tests passed, including repository and presentation validation.
- Regression sensitivity: restoring the original implementation caused the expected failures
  in two cache tests, one Android refresh test, and eight shared validation tests. The fixed
  sources were restored and the Android/shared tests rerun successfully.
- Android and Web/Wasm production/test sources compiled as part of these test tasks.
- Detekt passed for `app`, `engine`, `shared`, and `webApp`. Its two Android complexity findings
  were resolved by separating snapshot fetching from cache coordination; all 28 targeted Android
  tests passed again after that refactor. No new baseline entries were added.
- Device instrumentation tests could not run because no Android emulator or device is connected.
  Real-network E2E and screenshot tests were not run.
- Review findings addressed: manual refresh retries on Android, cancellation of web manual
  requests on target change, validation before durable cache replacement, and rejection of
  malformed cache records written by older versions.

## Partial-hour follow-up (2026-09-08)

- Review reproduced a noon-valid/evening-invalid response replacing the usable browser cache.
  Cache validation now checks every supplied daytime hour before writing, while accepting sparse
  schedules and shorter model horizons. Legacy partial-hour cache entries are rejected on read.
- The new Android regression failed before the fix. Afterward, 29 targeted Android JVM tests,
  15 shared validation/presentation tests, and 67 browser tests passed; Detekt passed in all
  three affected modules. A production Chromium probe confirmed unchanged IndexedDB values
  and timestamp after the malformed refresh, with the previous chart still visible.
- The preceding emulator review passed 21 instrumentation tests and captured all four charts on
  Android 16/API 36. The latest debug build including this fix is installed on that emulator.
- The earlier Chromium startup-budget failure remains a separate unresolved performance finding.
