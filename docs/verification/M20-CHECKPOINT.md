# M20 — Arena base checkpoint

This document records the durable implementation and manual-review boundary for
M20 on the isolated `codex/m20-arena-base` branch. It does not authorize promotion
to `main`, M21, M22, a release, or a signed APK.

## Boundary and source of truth

- Recovered and approved starting `main`:
  `3af3fe36184cf35f4fb0878e83c3adc233189b7e`.
- M20 branch: `codex/m20-arena-base`.
- The checkpoint SHA is the `checkpoint(M20): ...` commit containing this file.
- P5 and P6 remain promoted and unchanged.
- Rejected P6 renderer lineage beginning at `c21a2f6` remains excluded.

The authoritative scope was recovered from:

- `docs/implementation/IMPLEMENTATION-PLAN.md` M20;
- `docs/design/LumenChess-Full-Design-Spec.md` Arena, engine, opening, clock,
  evaluation, and persistence sections;
- `docs/design/DECISIONS.md`;
- `docs/design/UI-FLOWS.md`;
- the applicable architecture decision records;
- `docs/verification/P5-CHECKPOINT.md` and
  `docs/verification/P6-CHECKPOINT.md`.

M20 is **Arena base**: independent engine configuration per side, authoritative
engine-v-engine clocks and progression, bounded opening setup, Standard and
Chess960 starts, canonical Arena persistence, and an evaluation display enabled
by default. M21 manual opening/takeover/return, M22 branching, M24 generalized
imports/editor/saved positions, analysis, unrelated visual redesign, promotion,
and release packaging remain outside this checkpoint.

## Implemented Arena system

### Setup and opening resolution

Arena setup now provides independently configurable White and Black engine,
strength/Elo, and model controls; fixed or random colors; Standard or Chess960;
time control; and all documented M20 opening modes:

- Normal;
- Random Opening;
- Opening Family;
- Custom FEN;
- Random Chess960.

The project-owned opening catalog contains six legality-validated 12-ply lines
across multiple families. Presets expose 4, 8, and 12 plies, plus a validated
custom handoff from 1 through 12. Every bundled line is tested through its maximum
handoff. Standard book lines cannot be applied to Chess960. Custom FEN remains an
Arena-local setup input and does not pull M24's reusable position subsystem into
M20.

### Runtime and engine ownership

Arena reuses the serialized `GameRuntime` with both controllers set to `ENGINE`.
`ArenaRuntimeCoordinator` routes each typed engine-search effect to the gateway
assigned to the authoritative side to move. Slot A and Slot B have independent
engine sessions and configurations; neither engine mutates runtime state.

Search results and evaluation updates carry search and position-revision identity.
Stale or mismatched callbacks are rejected. Required-host readiness is aggregated,
and loss of either required host cancels all tracked Arena searches, including an
active sibling search, before recovery. Runtime remains the sole owner of the
canonical position, clocks, endings, and pause state.

### Evaluation transport

`EngineSearchInfo` carries correlated depth, score, nodes, NPS, and principal
variation across the typed engine API and Binder callback. Engine host projection
accepts only scored rank-one UCI information. Arena converts score presentation to
White's perspective and treats it as disposable UI data; it never enters legality
or state mutation. Existing Play consumers retain a no-op default listener and
unchanged behavior.

### Persistence

Arena games persist with canonical source `ENGINE_ARENA` and explicit engine
participants. Versioned Arena metadata preserves setup, engine assignments,
opening identity, and restore information. Restore is paused and recreates no
in-flight search. A separate last-Arena preference pointer avoids corrupting or
reusing Play restore state. The live persistence repository retains backwards-
compatible defaults, requires no schema change, and performs no destructive Room
migration.

### Arena UI

The existing Arena tab and Play hero now route to a first-class Arena setup and
Live surface. Setup is scrollable and uses the approved P5 typography, graphite
surfaces, cyan identity, touch geometry, and navigation vocabulary.

Arena Live is sparse and board-first: two engine rows and authoritative clocks,
the existing `LumenChessboard` with input disabled, a horizontal evaluation bar,
P6 last-move feedback and engine travel, board flip, pause/resume, stop, and exit.
The evaluation bar is outside board measurement. M21 takeover/return and M22
branching actions are deliberately absent. Piece/theme resolution uses the same
global catalog path as Play, preserving Public Lumen and personal renderer
compatibility.

## Architecture invariants

- `core-chess` remains the ultimate legality authority.
- Every engine move remains untrusted until runtime validation.
- Runtime remains the only canonical owner of game state and clocks.
- UI never owns engine execution; engines never mutate game state.
- `LumenChessboard` remains presentation/input only.
- Arena evaluation is correlated presentation data, not chess state.
- Standard and Chess960 use the established runtime and P6 board architecture.
- P5 layout and visual language remain intact.
- P6 piece rendering, feedback tokens, motion timings, special-move presentation,
  and P1 board geometry remain unchanged.
- Room remains non-destructive and canonical durable data is not replaced by a
  disposable cache.

## Focused verification implemented

The M20 test surface covers:

- setup validation, deterministic opening selection, opening-family restriction,
  Custom FEN, Chess960 index bounds, and every bundled line through 12 plies;
- typed evaluation scores and correlated search/revision identities;
- rank-one host projection and stale callback rejection;
- independent side-to-slot routing and per-engine search limits;
- two-host readiness, host death, cancellation, and recovery;
- authoritative clocks, pause/resume, sequential replies, and stale result/info
  rejection;
- Arena snapshot round trips and paused restore without in-flight work;
- canonical `ENGINE_ARENA` persistence and engine participants;
- setup navigation, independent controls, opening handoff controls, board-first
  Live, input-disabled board behavior, and default evaluation display;
- Standard Stockfish/Reckless progression and Chess960 progression/recovery;
- zero-delta board bounds through multiple authoritative Arena revisions.

The branch CI checkpoint at `876f263` compiled and passed the complete cumulative
API-37 device suite (48 engine-host, 19 persistence, and 111 app tests), but its
separate native-evidence launch exposed a QA-only setup defect: the cumulative
instrumentation task had uninstalled the APKs before the direct Arena capture.
Checkpoint `6653cde` restored both APKs before that capture. Its complete JVM,
lint, assembly, ABI/schema, public-packaging, and app device lanes passed, while
the cumulative device run exposed a real cancellation race in the existing
Reckless host test: a final disposable evaluation callback could arrive after
`StopSearch` removed its local correlation and was incorrectly promoted to a
session failure.

The focused correction discards only cancelled or superseded analysis callbacks.
Session/generation mismatches, malformed current analysis, and authoritative move
results remain strict. A pure correlation regression test freezes delivery for
the current search and disposal for missing/mismatched correlations. The final
`checkpoint(M20)` commit intentionally retriggers the complete build, lint,
packaging, API-37 instrumentation, and native Arena evidence lane. Results from
that exact SHA belong in the manual-review report; this document does not
predeclare them green.

Local Gradle execution on the current Windows host remains unavailable before
project configuration because Gradle cannot establish its required loopback IPC
connection. This host limitation reproduced across available JDK versions and
daemon settings. It is not represented as an application pass; compilation,
tests, lint, assembly, packaging, and device execution must be established by the
Linux GitHub workflow for the exact checkpoint SHA.

## Native manual-review contract

The final API-37 lane uses the established 1344×2992, 489 dpi viewport and must
produce native evidence for:

1. complete Arena setup;
2. opening-mode and handoff controls;
3. Standard Arena Live;
4. an uncropped Standard board;
5. flipped Standard board state;
6. Chess960 Arena Live;
7. an uncropped Chess960 board;
8. exact before/during/after board-bound measurements.

The evidence must be inspected for P5/P6 visual regressions, clipping, board
movement, renderer substitution, finite compositing boundaries, seams, and Live
layout instability. Subjective visual quality is not self-approved by this
checkpoint.

## Packaging and privacy boundary

The M20 implementation does not change the approved P6 personal-asset mechanism.
Public packaging must contain zero private piece/board assets, generated personal
staging files, private source paths, or local environment paths. Personal builds
remain expected to discover 39 complete styles and 468 canonical piece PNGs only
when the existing external property is supplied. Source artwork, generated private
assets, personal APKs, archives, and keystores remain ignored and untracked.

## Known limitations and next boundary

- Native visual approval remains pending until the exact checkpoint SHA passes the
  complete CI/device lane and its native artifacts are manually inspected.
- The cancelled-analysis race found by the cumulative Reckless device lane is
  corrected without weakening authoritative result correlation; its exact
  checkpoint rerun remains part of the final gate.
- Local Windows Gradle verification is blocked by the host loopback limitation
  described above; no local pass is claimed.
- M21 manual opening/takeover/return is not implemented.
- M22 branching is not implemented.
- No release or signed APK has been built.
- This branch has not been merged or promoted to `main`.

If the exact checkpoint is objectively green, the next authorized action is manual
M20 review. Promotion, M21, M22, and release work each require separate explicit
authorization.
