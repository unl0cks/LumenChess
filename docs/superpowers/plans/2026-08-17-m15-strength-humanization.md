# M15 Strength and Humanization Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement the approved Engine Native, Humanized, and Hybrid strength models with deterministic seeded candidate selection and a versioned initial calibration while preserving the M11–M14 engine/process boundaries.

**Architecture:** `engine-api` owns transport-neutral strength intent, planning, calibration, and deterministic candidate selection. The isolated `engine-host` resolves the plan against the actual engine capability, applies native strength options only where supported, collects parsed MultiPV `info` lines internally, selects a legal engine candidate deterministically, and returns only the selected UCI move through the existing typed result callback. Raw UCI never crosses Binder.

**Tech Stack:** Kotlin/JVM, Android Binder/AIDL, existing UCI parser, Stockfish 18, Reckless 0.9.0, JUnit/kotlin.test, API 37 instrumentation.

## Global Constraints

- Preserve exact Stockfish 18 source `sf_18` / `cb3d4ee9b47d0c5aae855b12379378ea1439675c`.
- Preserve exact Reckless `v0.9.0` / `0e92358f5acd66e5ac77b1bf558202e47c515435`.
- Keep Standard and Chess960 support.
- Keep authoritative chess legality in `core-chess`; every returned engine move remains independently validated there.
- Keep Slot A and Slot B as separate non-exported isolated processes.
- Keep search ID, position revision, host generation, cancel, stale-output, crash/restart, and session-reopen behavior unchanged.
- Strength target domain is 400–3000 Elo plus Full Strength; 50-point steps are a UI default, not an API restriction.
- Hybrid is the default finite-strength model; humanization fades as target Elo rises.
- Deterministic selection must produce the same move for the same seed/search/revision/candidate snapshot.
- Calibration must be explicitly versioned and must not claim exact real-world Elo equivalence without tournament calibration.
- Do not begin M16.

---

### Task 1: Typed strength contract, planner, and deterministic selector

**Files:**
- Modify: `engine-api/src/main/kotlin/dev/lumenchess/engine/api/EngineContracts.kt`
- Create: `engine-api/src/main/kotlin/dev/lumenchess/engine/api/EngineStrength.kt`
- Modify: `engine-api/src/test/kotlin/dev/lumenchess/engine/api/EngineContractsTest.kt`
- Create: `engine-api/src/test/kotlin/dev/lumenchess/engine/api/EngineStrengthTest.kt`

**Interfaces:**
- Produces: `EngineStrengthModel`, `EngineStrengthTarget`, `EngineStrengthSettings`, `EngineMoveCandidate`, `EngineCandidateScore`, `EngineStrengthPlan`, `EngineStrengthPlanning`, `EngineStrengthPlanner.plan(...)`, `EngineCandidateSelector.select(...)`.
- `EngineSearchRequest` gains `strength: EngineStrengthSettings = EngineStrengthSettings.fullStrength()` so all existing callers remain source-compatible and full-strength by default.

- [ ] **Step 1: Write failing contract/planner tests**

Tests must prove: Elo bounds 400–3000; Full Strength bypasses all limiting; Stockfish Engine Native resolves to `UCI_LimitStrength` with target clamped to 1320–3190; finite Engine Native is explicitly unsupported when `capabilities.strength == null`; Hybrid uses native limiting when available and humanization when needed; Reckless Hybrid falls back to humanization; Humanized does not require native Elo support; high-Elo Hybrid influence is lower than low-Elo influence; calibration exposes a nonblank immutable version.

- [ ] **Step 2: Run `./gradlew :engine-api:test` and verify RED**

Expected: compilation/test failure because the new M15 strength types and planner do not exist.

- [ ] **Step 3: Implement the minimal typed model and versioned calibration**

Use a small anchor table for 400, 800, 1200, 1600, 2000, 2200, 2400, 2600, 2800, and 3000 Elo. Each anchor supplies candidate count, maximum tolerated centipawn loss, integer selection temperature, and optional depth cap. Interpolate deterministically between anchors. Hybrid scales humanization below Humanized and approaches native behavior at high Elo; when no native limiter exists it retains enough humanization to approximate the requested finite target.

- [ ] **Step 4: Write failing deterministic selector tests**

Use typed candidate fixtures with MultiPV ranks and cp/mate scores. Assert: best move is returned when humanization is disabled; candidates outside the calibrated loss window are never chosen; same seed/search/revision/snapshot always yields the same move; different known seeds can choose different plausible candidates; mate-winning candidates outrank centipawn candidates; input ordering does not change the result.

- [ ] **Step 5: Run selector tests and verify RED**

Expected: failure because `EngineCandidateSelector` is absent.

- [ ] **Step 6: Implement deterministic selector**

Normalize mate scores to a monotonic score above ordinary centipawn values, sort by rank/score/move for stable ordering, filter by plan candidate count and max loss, and use an integer-weighted SplitMix64-style seeded draw mixed with `searchId` and `positionRevision`. Do not use ambient randomness or wall-clock time.

- [ ] **Step 7: Run `./gradlew :engine-api:test` and verify GREEN**

Expected: all M11 + M15 engine-api tests pass.

### Task 2: Isolated-host strength execution

**Files:**
- Modify: `engine-host/src/main/aidl/dev/lumenchess/engine/host/transport/IEngineHost.aidl`
- Modify: `engine-host/src/main/kotlin/dev/lumenchess/engine/host/transport/EngineHostTransport.kt`
- Modify: `engine-host/src/main/kotlin/dev/lumenchess/engine/host/EngineHostService.kt`
- Create: `engine-host/src/main/kotlin/dev/lumenchess/engine/host/EngineCandidateAccumulator.kt`
- Create: `engine-host/src/test/kotlin/dev/lumenchess/engine/host/EngineCandidateAccumulatorTest.kt`

**Interfaces:**
- Binder `startSearch(...)` adds `strengthModel`, `targetElo` (`0` means Full Strength), and `strengthSeed`.
- `HostSession` retains parsed `UciEvent.Info` internally and returns only the selected `bestMoveUci` through the existing callback.

- [ ] **Step 1: Write failing accumulator tests**

Assert that the accumulator keeps coherent MultiPV candidates from the deepest complete-enough snapshot, ignores `info` lines without both score and PV, treats missing `multipv` as line 1, resets between searches, and never lets stale candidates from a prior search affect a later one.

- [ ] **Step 2: Run `./gradlew :engine-host:testDebugUnitTest` and verify RED**

Expected: failure because the accumulator does not exist.

- [ ] **Step 3: Implement accumulator and host wiring**

Resolve the strength plan per search from the session engine capabilities. For Stockfish finite native plans send `setoption name UCI_LimitStrength value true` then `setoption name UCI_Elo value <resolved Elo>`; when native limiting is not used, explicitly send `UCI_LimitStrength false` so a prior search cannot leak settings. Never send those options to Reckless. Raise typed `SESSION` failure for finite Engine Native when the engine advertises no native strength capability.

For humanized plans, set effective `MultiPV` to `max(requestedMultiPv, plan.candidateCount)`, apply the calibrated depth cap as an additional `go depth` ceiling, collect candidate `info` lines, and at terminal `bestmove` use `EngineCandidateSelector`; if a usable candidate snapshot is absent, safely fall back to the engine terminal best move. Cancellation clears candidates before a replacement search begins.

- [ ] **Step 4: Transport the typed request fields**

Serialize only model enum name, finite target Elo or `0`, and deterministic seed through AIDL. Preserve all existing search/revision/generation correlation logic.

- [ ] **Step 5: Run `./gradlew :engine-api:test :engine-host:testDebugUnitTest` and verify GREEN**

Expected: all pure JVM engine tests pass.

### Task 3: Real-engine API 37 strength gate

**Files:**
- Modify: `engine-host/src/debug/kotlin/dev/lumenchess/engine/host/testing/Stockfish18ReliabilityProbeActivity.kt`
- Modify: `engine-host/src/debug/kotlin/dev/lumenchess/engine/host/testing/Reckless09ReliabilityProbeActivity.kt`
- Modify: `engine-host/src/androidTest/kotlin/dev/lumenchess/engine/host/EngineHostIsolationTest.kt`

**Interfaces:**
- Adds target-process scenarios for deterministic finite-strength searches without exposing raw UCI.

- [ ] **Step 1: Add failing API 37 scenarios/tests**

Prove on both pinned engines: a finite Hybrid search returns a move that passes `EngineMoveValidator`; repeating the same position/search identity/seed in a fresh session chooses the same move; Standard and Chess960 still pass core validation. For Stockfish additionally prove Engine Native finite strength is accepted. For Reckless prove finite Engine Native is rejected as a typed failure instead of silently pretending to limit strength.

- [ ] **Step 2: Run the focused API 37 engine-host instrumentation gate and verify RED before production host changes are considered complete**

Expected: M15 scenarios fail until request transport/host execution is wired.

- [ ] **Step 3: Complete minimal production wiring and rerun the focused gate**

Expected: all M12–M15 engine-host instrumentation tests pass on API 37 with 16,384-byte pages, no engine-slot LMK kill, no native crash/tombstone, and the Reckless close/reopen regression still green.

### Task 4: Calibration record and cumulative Batch A gate

**Files:**
- Create: `docs/adr/0016-engine-strength-humanization.md`
- Modify: `.github/workflows/android.yml` only if a new M15-specific diagnostic/trigger is required; preserve existing diagnostics and 8 GiB evidence-backed emulator allocation.

**Interfaces:**
- Documents calibration version, native-support behavior, deterministic seed semantics, candidate selection, and the fact that the initial curve is an approximation pending larger empirical match calibration.

- [ ] **Step 1: Document the settled M15 behavior and calibration version**

Record that Engine Native is capability-gated, Hybrid degrades to humanization when native limiting is unavailable, Humanized is engine-agnostic, and no model silently upgrades Stockfish/Reckless or bypasses `core-chess` legality validation.

- [ ] **Step 2: Run proportional M15 checkpoint checks**

Expected: `:core-chess:test`, `:engine-api:test`, `:engine-host:testDebugUnitTest`, engine-host lint/assemblies, and ARM64/x86_64 16 KiB native verification all green.

- [ ] **Step 3: Run the full cumulative Batch A branch gate on the exact M15 SHA**

Expected: Gate A + M6–M11 regressions; M12–M15; API 37 engine-host, persistence, and app instrumentation; both real engines; crash/restart/cancel/stale; Standard + Chess960; ARM64/16KiB; Room migration/schema freshness; lint/build/assemblies all green.

- [ ] **Step 4: Verify `main` is still `249f927eee953ec3ba120e68470f9e3e445e5bc0`, then promote only the exact fully tested Batch A SHA**

Do not squash checkpoint history.

- [ ] **Step 5: Run and verify a fresh full `main` CI gate on the promoted exact SHA**

Expected: full success. Report Batch A completion and stop. Do not begin M16.
