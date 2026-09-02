# M21 — Manual takeover implementation plan

## Goal and authority

Complete only M21 on `codex/m21-manual-takeover`, rooted at promoted M20
`57781d586bb55f4509ffa301d163b4716aca59fc`. Authoritative scope is
`docs/implementation/IMPLEMENTATION-PLAN.md` M21, full design specification §§8.3–8.4,
`docs/design/DECISIONS.md`, `docs/design/UI-FLOWS.md`, and ADRs 0011/0017/0018.
M22 branching/sandbox/Save as Variation is excluded. Work autonomously through a
single final native/manual-review boundary; do not promote or build a release.

## Architecture and global constraints

- Runtime remains sole canonical position, controller, clock and search authority.
  Human moves and engine results use existing core legality and revision gates.
- Add an explicit runtime-owned manual-control lease per side, optional finite
  move budget, and clock policy. A lease is distinct from ordinary Play's HUMAN
  controller; unchanged Play defaults must behave identically.
- Count “X moves” per controlled side, starting from the selected Arena opening
  position. With Both, each side has X accepted moves. Illegal inputs, engine moves,
  duplicate events and restored book plies never consume another side's budget.
- Manual opening and takeover default to locked clocks for the whole manual-control
  period (including intervening engine turns). No time charge or increment while
  locked; active side still follows the canonical position. Explicit Count time
  retains normal clocks/increments. Game Pause remains separate and blocks moves.
- Set/release White/Black/Both atomically. Finite-budget handoff occurs inside the
  same authoritative move reduction, before scheduling the next engine. No UI
  counters, intermediate chess positions, stale acceptance or search queue.
- M20 engine identities/configurations and canonical participant records remain
  unchanged during human control. Versioned Arena metadata stores manual policy,
  controllers and remaining budgets; v1 records remain readable. Restoration is
  paused with no in-flight search; resume uses the existing product flow.
- P5/P6 geometry, pieces, feedback and motion remain unchanged. Arena consumes the
  same board and source-specific motion classification. No new piece/render path.
- Private source artwork, generated assets, APKs, source paths and secrets never
  enter Git. No schema/destructive migration, no M22, no history rewrite.

## Execution and verification

Use focused RED/GREEN tests per behavior, then one cumulative gate when stable.
Only one writer/build owner at a time for shared surfaces; read-only inspections
may run in parallel. Commits group complete runtime, Arena integration, and final
UI/verification work rather than placeholder checkpoints. Final native captures
cover manual opening, takeover/return, restoration and stable board bounds.

### Task 1 — Authoritative manual control and clock lock

**Files:** `game-runtime/src/main/kotlin/dev/lumenchess/runtime/GameRuntimeModels.kt`,
`GameRuntime.kt`, `GameRuntimeReducer.kt`, an optional focused `RuntimeManualControl.kt`,
and focused tests under `game-runtime/src/test/kotlin/dev/lumenchess/runtime/`.
Only touch `clock/GameClock.kt` if a small clock primitive is genuinely needed.

1. Add failing fake-time tests before behavior: White/Black/Both finite and unlimited
   control; locked moves and engine replies; Count time; timeout-before-takeover;
   atomic take/release both; stale result cancellation/new search ID; per-side budget
   handoff; illegal/duplicate input; paused/restore state; premove invalidation;
   Standard and Chess960 legality. Preserve existing runtime/race/premove tests.
2. Implement a small typed runtime manual-control state, defaulting inactive for
   existing callers, with an atomic event to replace the selected controlled sides
   and clock policy. Lease `remainingMoves == null` means until release; positive
   values count accepted moves of that side. No lease means engine-controlled Arena
   side. Initial leases must agree with controllers. Preserve legacy ChangeController.
3. Settle elapsed time before takeover; lock without game pause; unlock on final
   handoff; align clock active side with initial side-to-move, including Black FENs.
   Persist lease state in RuntimeSnapshot; restore stops clocks/searches as before.
4. Test smallest new class first, then `:game-runtime:test`; review and commit only
   runtime source/tests plus this plan. Report the finalized API to dependent work.

### Task 2 — Arena coordination, setup model and backward-compatible restore

**Files:** `app/src/main/java/dev/lumenchess/arena/ArenaModels.kt`,
`ArenaRuntimeCoordinator.kt`, `ArenaSnapshotCodec.kt`, `ArenaViewModel.kt`; focused
Arena unit tests and persistence instrumentation if needed. A small dedicated
manual-control presentation/model helper is allowed; no unrelated refactor.

1. Test optional manual opening (None/White/Black/Both), positive custom move limit
   or until release, locked/Count time policy; independently preserve resolved
   engines and color assignment. Default remains engine-vs-engine M20 behavior.
2. Coordinator forwards legal human move and atomic manual-control intents to the
   runtime. Serialize event IDs/effect routing with callback ownership; preserve
   host/search correlation and unchanged independent engine configurations.
3. Version Arena metadata additively: current controllers, leases, clock policy and
   setup choices. Read M20 v1 as engine-vs-engine/no lease. Reject malformed state
   rather than silently resuming different authority. No Room schema change.
4. ViewModel exposes setup/takeover/return intents and derives visible controller,
   remaining moves, clock state and handoff status from runtime results. Stale board
   intents must not apply to a replacement/revised position. No UI counter authority.
5. Focused tests cover routing to Stockfish/Reckless configurations after handoff,
   stale result/info rejection, round-trip/legacy restore and clock policy. Commit.

### Task 3 — Existing-grammar Arena UI and final M21 verification

**Files:** `app/src/main/java/dev/lumenchess/arena/ArenaScreen.kt`, optional focused
`ArenaManualControls.kt`; relevant Arena tests/native instrumentation;
`docs/verification/M21-CHECKPOINT.md`. CI policy only if needed for the established
milestone checkpoint lane; do not add a separate giant workflow.

1. Add manual-opening controls to existing scrollable setup using its component
   grammar. Clearly explain per-side count and locked default, support custom X and
   until release. Preserve Start/Resume layout/insets and M20 configuration.
2. Add a restrained Take Over/Return control surface in Live without moving the
   board. White/Black/Both, explicit clock option, visible control/remaining count,
   handoff indication; preserve engine identity and pause/stop/flip behavior.
3. Enable the existing board's tap/drag only for active manual controller, unpaused
   and nonterminal. Use runtime-revision guarded input and correct HUMAN/ENGINE move
   presentation; promotion/castling/feedback remain existing P6 implementations.
4. Native API-37 tests exercise real UI setup → manual moves → automatic handoff,
   midgame takeover/return, pause, flip and restoration; Standard and Chess960, both
   engine paths, public renderer plus a personal Neo sanity capture where available.
   Measure exact board bounds before/during/after controller and engine transitions.
5. Once stable, run affected modules + cumulative core/runtime/engine/persistence/
   app tests, lint, debug and Android-test assembly, native affected lane and existing
   board/P6/Play safety regressions. Scan tracked privacy and `git diff --check`.
   Reuse packaging contracts; no exhaustive 39-style visual repeat.
6. Review actual screenshots, perform focused code review, record truthful tests/
   skips/native evidence and known limits in the established checkpoint. Commit and
   push isolated M21 branch; verify CI. Stop for manual M21 approval, no promotion.
