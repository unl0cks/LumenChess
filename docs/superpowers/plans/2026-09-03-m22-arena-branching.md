# M22 — Arena branching implementation plan

**Goal:** Sandbox-first Arena branches from historical positions, with explicit
Save as Variation and no implicit rewrite of the original game.

**Baseline:** `56e47db29481c6d8a52b487af237deef3641716e`; branch
`codex/m22-arena-branching`. M21 approval is recorded in that baseline. Main CI
33738330754: full PASS; checkpoint/play-device/engine-device policy SKIPPED.

**Authority:** implementation plan M22; full design specification §§8.5–8.7 and
23.2; DECISIONS Branching; UI-FLOWS §3; ADRs 0008–0011 and 0017. M23 Library,
M24 general import/export/editor, M26 Analysis and later Review UI are excluded.

## Required behavior and boundaries

- Browse Arena mainline positions without editing the canonical runtime tree.
  Pause before browsing; stale searches are cancelled through existing runtime.
- Create a separate persisted BRANCH game at the selected exact Standard/Chess960
  position. Store source game UUID and source node UUID (null only for root), not
  in-memory GameNodeId. The source game remains intact until explicit attachment.
- Reuse Arena engine/strength/model, manual White/Black/Both and time controls.
  Branch default is Both manual until release so an alternative can be played.
  Support Stockfish/Reckless in every pairing, human/engine, and untimed play.
- Untimed is an explicit deterministic clock capability; never fake a long clock,
  pause engine execution, or let UI own time. Existing timed defaults are unchanged.
- Restore sandbox identity, exact position, controllers/configuration and clock mode
  through the existing paused snapshot boundary, without in-flight engine work.
- Save as Variation loads the newest original and appends only missing variation
  nodes transactionally. Preserve original mainline, metadata, result, comments,
  IDs, sources and other branches. Repeat saves are idempotent; missing/mismatched
  source identity fails visibly. A continuation at a historical leaf can repeat
  its incoming move as a sibling RAV so it does not extend the historical mainline.
- Return to Original loads the original persisted Arena state; neither branch
  navigation nor old callbacks can replace a newer session. Mainline browsing is
  UI presentation only and suppresses travel between nonconsecutive positions.
- P5/P6 renderer, geometry, feedback and motion remain unchanged. New controls go
  beneath the board or in existing setup/dialog grammar. No schema migration.
- Source-neutral branch persistence is reusable by later local/import/review flows;
  their not-yet-existing screens are not pulled into M22.

## Tasks and verification

### 1. Source-neutral branch persistence

Files: new `data-persistence/.../BranchPersistenceRepository.kt` and native tests.
Interfaces: `BranchOrigin(gameId: PersistentGameId, nodeId: String?, fen: String)`;
`captureOrigin(gameId, mainlinePly): BranchOrigin`; `saveAsVariation(origin,
branch: GameTree): Int` returns appended-node count. Root uses null persistent ID.

- [ ] RED: save keeps original unchanged until attachment; attachment preserves
  original mainline/metadata/UUIDs; repeated save appends zero; extension appends
  only missing nodes; leaf continuation uses sibling variation; wrong FEN/missing
  node fails atomically; Standard/Chess960 and root anchors reconstruct legally.
- [ ] Implement transactional append-only attachment through core legality.
- [ ] Run focused real Room instrumentation; review the persistence boundary.

### 2. Untimed runtime and branch metadata

Files: `runtime/clock/GameClock.kt`, Arena models/coordinator/snapshot codec,
focused clock/runtime/Arena tests. Add backward-compatible enabled clock flag.

- [ ] RED: untimed start/move/charge/pause/resume never consumes time or times out,
  active side still switches, engines still progress, finite manual handoff works.
- [ ] Implement clock behavior and explicit untimed engine search budget.
- [ ] Round-trip branch origin and clock mode; retain v1/v2 snapshot compatibility.

### 3. Native Arena branch integration

Files: Arena ViewModel, persistence gateway, screen and a focused branch screen
component. Keep shared board code and existing ordinary Arena defaults intact.

- [ ] RED native flow: earlier position -> configure sandbox -> alternative legal
  move -> engines/manual continuation -> explicit save -> original unchanged.
- [ ] Add guarded history browsing/configuration, persistent branch/session flow,
  Save as Variation, Return to Original, and restoration; use existing controls.
- [ ] Verify session cancellation, independent engines, untimed/timed/manual modes,
  source immutability and visible failure handling.

### 4. Cumulative gate, review and compact evidence

- [ ] Focused Standard/Chess960, both engines, M21 handoff, source/branch restoration.
- [ ] One cumulative JVM/lint/debug/androidTest/API-37 gate when stable.
- [ ] Inspect 5–10 actual product captures; record exact board bounds and require
  zero movement through browse/branch/move/engine/return/restore. Verify Neo identity.
- [ ] Public APK/privacy, tracked-private scan, diff check, independent source review.
- [ ] Commit/push coherent increments and verify remote SHA and applicable CI.
- [ ] Compact native ZIP with README, diagnostics and verified SHA-256 manifest.
  Stop for M22 manual approval; no promotion, M23 or release/signed APK.
