# ADR 0016: Engine strength and humanization

- Status: Accepted
- Milestone: M15
- Calibration version: `m15-v1-2026-08-17`

## Context

LumenChess needs a single user-facing strength model across engines whose native capabilities differ substantially. Stockfish 18 exposes `UCI_LimitStrength` and `UCI_Elo` over a native 1320–3190 range. The pinned Reckless 0.9.0 release exposes no native Elo limiter. The application still needs finite targets from 400 through 3000 Elo, Full Strength, a more human-feeling style option, deterministic replay for tests and reproducibility, and the existing M11–M14 typed/isolated engine boundary.

A raw engine option passthrough would leak UCI and engine-specific implementation details across Binder and into application state. Pretending that Reckless supports native Elo limiting would also be misleading. Strength therefore remains typed at the API boundary and is resolved inside the isolated engine host against the capabilities of the engine that was actually opened.

The initial M15 calibration is an engineering approximation. It is explicitly versioned so later match-based calibration can replace it without silently changing the meaning of recorded settings. `m15-v1-2026-08-17` is not a claim that a configured number is already measured to equal an exact human FIDE/online rating.

## Decision

### User-facing strength intent

- Strength targets are Full Strength or finite Elo from 400 through 3000 inclusive.
- The API does not require 50-point increments; 50-point stepping remains a UI/default presentation choice.
- Finite strength has three models:
  - **Engine Native** — use only the engine's advertised native strength mechanism.
  - **Humanized** — engine-agnostic seeded candidate selection using calibrated MultiPV/depth/loss parameters.
  - **Hybrid** — the default finite model; combine native limiting where available with humanization, fading humanization as target Elo rises.
- Full Strength bypasses both native Elo limiting and humanized candidate selection.

### Capability behavior

- Stockfish remains pinned to upstream `sf_18`, exact commit `cb3d4ee9b47d0c5aae855b12379378ea1439675c`. Finite Engine Native uses its advertised `UCI_LimitStrength` / `UCI_Elo` capability and clamps the requested target to the pinned release's 1320–3190 native range.
- Reckless remains pinned to upstream `v0.9.0`, exact commit `0e92358f5acd66e5ac77b1bf558202e47c515435`. Finite Engine Native is unsupported and must return a typed `SESSION` failure; it must never silently behave as Full Strength or pretend to honor a native Elo option that v0.9.0 does not expose.
- Humanized works without native Elo support.
- Hybrid uses native limiting when available. On engines without a native limiter, including Reckless 0.9.0, Hybrid falls back to the calibrated humanized path rather than failing or silently upgrading the engine.

### Isolation and protocol boundary

- `EngineSearchRequest` carries only typed strength intent: model, target, and deterministic seed.
- Binder transports only the model identifier, finite target (or the Full Strength sentinel), and seed alongside the existing typed search identity/revision data.
- The isolated host independently resolves the plan against the capabilities of the engine actually opened. Caller-provided capability objects are not trusted to enable engine-specific native options.
- Engine-specific UCI commands, MultiPV `info` parsing, and candidate snapshots remain inside `engine-host`. Raw UCI does not cross Binder.
- Stockfish native strength options are explicitly disabled for searches that should not use them, preventing a previous finite search from leaking state into a later Humanized or Full Strength search.
- Reckless is never sent Stockfish-only native strength options.

### Humanized candidate selection

- Calibration anchors cover the product range and define candidate count, maximum tolerated centipawn loss, integer selection temperature, and optional depth cap. Values are deterministically interpolated between anchors.
- A search may raise effective MultiPV to obtain the candidate count required by its calibrated profile, but never beyond the engine's advertised MultiPV capability.
- The host accepts candidate lines only when they contain an exact score and principal variation. Missing `multipv` means rank 1.
- Candidate ranks from different search depths are never mixed. A deeper snapshot replaces the previous one only after all expected ranks for that depth have arrived.
- Candidate state is owned by one search envelope and cannot leak through cancellation, replacement, close/reopen, or a later search.
- If no coherent candidate snapshot exists, the host safely falls back to the engine's terminal `bestmove` rather than fabricating a result.
- Mate scores are kept typed and ordered above ordinary centipawn-winning scores for deterministic selection.

### Determinism

- Candidate selection uses no wall-clock time and no ambient randomness.
- The deterministic draw is derived from the configured seed, `EngineSearchId`, `PositionRevision`, and a stable ordering of the candidate snapshot.
- Therefore the same seed/search/revision/candidate snapshot produces the same selected move independent of input iteration ordering.
- Different seeds may select different candidates, but only among candidates inside the calibrated loss window.

### Legality and lifecycle

- Humanization does not become a second chess rules implementation. Every engine result remains untrusted until the existing `EngineMoveValidator` / `core-chess` boundary accepts it.
- Standard and Chess960 use the same strength architecture.
- M12 search ID/revision/host-generation correlation, stale-output rejection, cancellation, crash/restart, session close/reopen, and the two non-exported `isolatedProcess=true` Slot A / Slot B services remain controlling.
- The M14 Reckless process-global initialization regression remains permanent coverage.

## Calibration consequences

The initial curve provides deterministic behavior suitable for product integration and reproducible testing, but it is intentionally versioned as a first calibration rather than presented as tournament-proven Elo equivalence. Future empirical calibration may adjust anchors or selection behavior only under a new calibration version, with migration/replay implications considered explicitly.

At high finite Elo, Hybrid humanization diminishes and eventually yields effectively native behavior where the engine has a native limiter. At low and medium Elo it can consider more plausible alternatives and tolerate a larger bounded score loss. Engines without native limiting retain humanization so finite Hybrid remains meaningful.

## CI resource decision

The M14/M15 API 37 gate uses an 8192 MiB emulator allocation. This is evidence-based rather than a substitute for leak detection: earlier 4096 MiB and 6144 MiB dual-engine runs recorded Android `lowmemorykiller` terminating a real engine slot under guest pressure, while the same engine lifecycle had previously passed before the second native engine increased aggregate pressure.

The focused engine-device gate permanently records `/proc/meminfo`, `dumpsys meminfo`, logcat, process/service snapshots, JUnit XML, crash/tombstone evidence, and an explicit engine-slot LMK extract. Any LMK kill of Slot A or Slot B fails the gate, so future native memory regressions cannot be hidden merely by the larger guest allocation.

## Verification

Permanent M15 verification must prove:

- Elo bounds, Full Strength bypass, capability-aware Engine Native behavior, Hybrid fallback, and versioned calibration;
- deterministic seeded candidate selection, bounded candidate loss, mate handling, stable ordering, and terminal-bestmove fallback;
- coherent MultiPV snapshot handling with no cross-depth or cross-search contamination;
- API 37 real Stockfish 18 finite Hybrid determinism, Standard and Chess960 core legality, and finite Engine Native acceptance;
- API 37 real Reckless 0.9.0 finite Hybrid determinism, Standard and Chess960 core legality, and typed rejection of unsupported finite Engine Native;
- all pre-existing M12–M14 isolation, crash/restart, cancel/replacement, stale-output, dual-slot, and Reckless session-reopen regressions remain green;
- ARM64/x86_64 packaging and at least 16 KiB ELF load alignment remain green for both pinned engines.

Batch A is not complete until the exact M15 checkpoint SHA passes the cumulative branch gate covering Gate A and M6–M15 regressions, API 37 app/persistence/engine instrumentation, Room migration/schema freshness, lint/build/assemblies, both pinned real engines, Standard/Chess960, and native alignment checks. Only that exact fully green SHA may then be fast-forwarded to `main` and re-verified by a fresh full `main` CI run.
