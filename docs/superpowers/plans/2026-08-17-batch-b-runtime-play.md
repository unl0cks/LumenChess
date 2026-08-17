# Batch B Runtime + Play Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Deliver M16–M19 as the first complete Human-vs-Engine playable runtime vertical slice while preserving the approved chess, persistence, engine, isolation, and UI boundaries from Batch A.

**Architecture:** Add a pure JVM `game-runtime` module. M16 contributes only deterministic clocks driven by an injectable monotonic time source. M17 turns that module into the single serialized owner of authoritative game state and emits typed effects; engines and persistence can only return events/results. M18 adds one runtime-owned premove. M19 wires the runtime to the existing Compose board, isolated engine host, and persistence repository through an Android app coordinator/ViewModel without moving authoritative state into Compose or Binder.

**Tech Stack:** Kotlin/JVM, Kotlin coroutines, Jetpack Compose, Room, existing `core-chess`, `engine-api`, `engine-host`, Android 17/API 37 CI.

## Global Constraints

- Baseline SHA: `06ad7a2dd488a89e316d1d24e7c0d751c3c12de0`.
- Standard and Chess960 remain supported.
- `core-chess` remains the legality authority.
- `EngineSearchId` + `PositionRevision` remain mandatory for engine correlation.
- Engine results remain untrusted until independently validated.
- Engine-host and persistence never mutate authoritative runtime state.
- Runtime event application is serialized and deterministic.
- Clock time uses monotonic elapsed time, never wall clock or frame callbacks.
- M18 supports exactly one premove with a default 100 ms execution cost.
- M19 remains a clean Play vertical slice; Analysis/Review/Arena/M20 are out of scope.
- No destructive Room migration fallback.

---

### Task 1: M16 deterministic clocks

**Files:**
- Modify: `settings.gradle.kts`
- Create: `game-runtime/build.gradle.kts`
- Create: `game-runtime/src/main/kotlin/dev/lumenchess/runtime/clock/MonotonicTimeSource.kt`
- Create: `game-runtime/src/main/kotlin/dev/lumenchess/runtime/clock/GameClock.kt`
- Test: `game-runtime/src/test/kotlin/dev/lumenchess/runtime/clock/GameClockTest.kt`
- Modify: `.github/workflows/android.yml`

**Interfaces:**
- `fun interface MonotonicTimeSource { fun nowMillis(): Long }`
- `data class ClockConfig(val initialMillis: Long, val incrementMillis: Long)`
- `enum class ClockSide { WHITE, BLACK }`
- immutable `ClockState` with stored remaining times, active side, running/paused/terminal state, and last monotonic sample.
- pure transitions: `start`, `read`, `switchTurn`, `pause`, `resume`, `charge`, and timeout clamping; all return a new state/result and never depend on UI frame rate.

- [ ] Write clock tests first for initial state, active side, elapsed accounting, increment, zero cases, pause/resume, timeout once, switching, repeated reads, delayed delivery, fake time, non-negative time, and terminal idempotency.
- [ ] Commit the RED tests and run branch CI to confirm failure is due to missing clock implementation.
- [ ] Implement the minimal pure clock state machine and fake monotonic source support.
- [ ] Extend checkpoint CI with `:game-runtime:test` but do not boot the emulator for M16-only changes.
- [ ] Run the M16 JVM gate and create `checkpoint(M16): deterministic clocks`.

### Task 2: M17 authoritative runtime hard gate

**Files:**
- Create: `game-runtime/src/main/kotlin/dev/lumenchess/runtime/GameRuntimeModels.kt`
- Create: `game-runtime/src/main/kotlin/dev/lumenchess/runtime/GameRuntime.kt`
- Create: `game-runtime/src/main/kotlin/dev/lumenchess/runtime/GameRuntimeReducer.kt`
- Create: `game-runtime/src/main/kotlin/dev/lumenchess/runtime/GameRuntimeEffects.kt`
- Test: `game-runtime/src/test/kotlin/dev/lumenchess/runtime/GameRuntimeRaceTest.kt`
- Modify: `game-runtime/build.gradle.kts`
- Create: `docs/adr/0017-authoritative-game-runtime.md`

**Interfaces:**
- Runtime owns `Position`, `GameTree`, `ClockState`, controller assignments, terminal state, `PositionRevision`, pending engine search correlation, pause state, and dedupe/event ids.
- `GameRuntime.dispatch(event)` serializes authoritative events through one owner.
- Reducer is pure: `(RuntimeState, RuntimeEvent) -> RuntimeTransition(state, effects)`.
- Effects are typed requests such as engine search/cancel and persistence snapshot; effect handlers may only return new runtime events.
- Engine completions include existing `EngineSearchId` and `PositionRevision` and are rejected if stale, duplicate, illegal, paused where inappropriate, or terminal.

- [ ] Write deterministic race/order tests first: human vs engine completion; stale revision; cancel/restart late output; timeout vs move; pause/resume; duplicate engine/user events; terminal idempotency; controller change; host death/recovery; restore boundary; exactly-once legal move; illegal and legal-but-stale engine moves.
- [ ] Confirm RED before production runtime code.
- [ ] Implement the single serialized owner/reducer/effect boundary without changing the engine/Binder ownership model.
- [ ] Run `:game-runtime:test` plus `:core-chess:test` and `:engine-api:test`.
- [ ] Create ADR 0017 and `checkpoint(M17): authoritative runtime state machine` only when the full M17 race suite is green. M18 is forbidden before this checkpoint.

### Task 3: M18 single premove

**Files:**
- Modify: `game-runtime/src/main/kotlin/dev/lumenchess/runtime/GameRuntimeModels.kt`
- Modify: `game-runtime/src/main/kotlin/dev/lumenchess/runtime/GameRuntimeReducer.kt`
- Test: `game-runtime/src/test/kotlin/dev/lumenchess/runtime/PremoveRuntimeTest.kt`
- Create: `docs/adr/0018-single-premove-runtime.md`

**Interfaces:**
- At most one queued `Move` plus the `PositionRevision` at which it was queued.
- Queueing does not make the move authoritative and does not charge clock time.
- After the opponent move is authoritatively applied, the runtime validates the queued move against the resulting `core-chess` position; if legal, apply exactly once and charge 100 ms exactly once; otherwise discard.
- Cancellation/controller/terminal/timeout invalidation clears the queue.

- [ ] Write RED tests for legal execution, resulting-position illegality, source/destination changes, captures appearing/disappearing, promotion, Standard/Chess960 castling, terminal/timeout/cancel/controller invalidation, engine completion while queued, exactly-once execution/cost, no cost for non-executed premove, and stale queue isolation.
- [ ] Implement minimum runtime queue/cancel/execution semantics.
- [ ] Run M17+M18 runtime tests and regressions.
- [ ] Create ADR 0018 and `checkpoint(M18): single runtime premove`.

### Task 4: M19 Human-vs-Engine Play vertical slice

**Files:**
- Modify: `app/build.gradle.kts`
- Create: `app/src/main/java/dev/lumenchess/play/PlayModels.kt`
- Create: `app/src/main/java/dev/lumenchess/play/PlayRuntimeCoordinator.kt`
- Create: `app/src/main/java/dev/lumenchess/play/PlayViewModel.kt`
- Create: `app/src/main/java/dev/lumenchess/play/PlaySetupScreen.kt`
- Create: `app/src/main/java/dev/lumenchess/play/LivePlayScreen.kt`
- Modify: `app/src/main/java/dev/lumenchess/ui/LumenChessApp.kt`
- Add app unit/instrumentation tests under `app/src/test/.../play` and `app/src/androidTest/.../play`.
- Extend persistence deliberately only if restore metadata cannot be represented through the existing canonical repository; any Room change requires schema + explicit migration + migration tests.
- Create: `docs/adr/0019-human-vs-engine-play.md`
- Modify: `.github/workflows/android.yml`

**Interfaces:**
- Setup supports Standard/Chess960, Stockfish/Reckless, White/Black/Random, 400–3000/Full Strength, M15 model, and committed time-control presets/custom control needed for M19.
- `PlayViewModel` exposes immutable screen state and dispatches user intents to the runtime; Compose never mutates `Position`/`GameTree`.
- `PlayRuntimeCoordinator` executes runtime effects through the existing engine host and persistence repository and returns typed runtime events.
- Unsupported engine strength capability is surfaced as typed validation/fallback, never raw UCI.

- [ ] Write focused unit tests for setup validation, side/orientation selection, capability handling, coordinator stale-result/crash recovery, persistence snapshots/restoration, and no duplicate actions across lifecycle/recomposition.
- [ ] Write Compose instrumentation tests for setup navigation, clean live hierarchy, board semantics/input, clocks, promotion and Chess960 input/orientation.
- [ ] Implement minimal Play setup/live UI using `LumenChessboard`, clocks, runtime, engine API/host and persistence boundaries.
- [ ] Exercise engine death/restart/stale output, background/restore, terminal persistence, and no manufactured moves.
- [ ] Create ADR 0019 and final `checkpoint(M19): complete Gate B playable runtime` SHA.

### Task 5: Gate B and promotion

- [ ] Extend branch CI so M19 checkpoint runs the complete cumulative Gate B: Gate A + M6–M15 + M16–M19 JVM/instrumentation, both engines, API 37, Room schema/migrations, lint, assemblies, native ABI/16 KiB checks.
- [ ] Preserve focused engine diagnostics and add runtime/app test failure names/artifacts where needed.
- [ ] Verify exact branch SHA is green.
- [ ] Verify `main` still equals `06ad7a2dd488a89e316d1d24e7c0d751c3c12de0`; fast-forward only the exact tested SHA.
- [ ] Run fresh complete `main` CI and verify it uses the promoted SHA.
- [ ] If no physical Pixel 8 Pro is connected to the execution environment, mark device verification pending and provide the exact manual checklist; do not fabricate it.
- [ ] Stop before M20.
