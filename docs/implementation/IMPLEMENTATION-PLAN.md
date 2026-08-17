# LumenChess Implementation Plan

**Status:** Approved 2026-08-15; sequencing amended 2026-08-17 after physical Batch B review.  
**Source of truth:** `PROJECT-HANDOFF.md`, `docs/design/LumenChess-Full-Design-Spec.md`, `docs/design/DECISIONS.md`, `docs/design/UI-FLOWS.md`, `docs/research/RESEARCH-NOTES.md`.

## Global invariants

- Native Android: Kotlin + Jetpack Compose.
- Initial target: Pixel 8 Pro, Android 17 / API 37, ARM64 (`arm64-v8a`).
- Offline-first.
- Mandatory initial engines: Stockfish 18 and Reckless 0.9.0.
- Initial variants: Standard and Chess960.
- Chess state is deterministic and owned by `core-chess`; UI and engines never mutate authoritative chess state directly.
- Every engine-returned move is independently validated by `core-chess` before the runtime may accept it.
- Two isolated engine processes are the preferred architecture. M12 determines the exact Binder/AIDL/native-wrapper implementation.
- Every discovered chess/runtime/engine bug receives a regression test with its fix.
- Accessibility semantics and appropriate touch targets are foundational UI requirements from the first components; M47 remains the full accessibility/device-adaptation pass.
- Exact third-party dependency/source/version/license records are maintained from the beginning.
- Licensing is a mandatory gate before public distribution.
- Production builds must not redistribute proprietary Chess.com screenshots, sounds, or other assets without established rights.

## Gate discipline

Development proceeds in small verified milestones. Do not continue beyond a gate until its required tests and acceptance criteria are demonstrated.

---

## Phase 0 — Implementation baseline

### M0 — Pre-code decision and compliance gate
- Pin Stockfish `sf_18` and Reckless `v0.9.0` by exact upstream commit.
- Record license and source provenance.
- Record Android/Gradle/NDK baseline.
- Create `docs/adr/` and document architecture decisions as they become important.
- Record two isolated engine processes as preferred architecture while leaving exact transport/native wrapping to M12.
- Record licensing as a public-distribution gate.
- Record engine-move validation as a global runtime invariant.

**Verification:** no unresolved decision changes the M1–M5 chess-core model; dependency/source/license ledger exists.

## Phase 1 — Project foundation

### M1 — Android project scaffold
- Kotlin + Jetpack Compose project.
- API 37 compile/target baseline; ARM64-first native strategy.
- AGP/Gradle/JDK/NDK versions pinned.
- Modules begin with `app` and pure JVM `core-chess`; additional boundaries are added only when used.
- CI runs tests, lint, and debug assembly.

**Verification:** clean environment can run the documented Gradle verification command; debug app launches on the target environment when Android SDK/device access is available.

### M2 — Design system shell
- Dark polished theme with blue default accent.
- Semantic color/typography/spacing tokens.
- Foundational controls enforce 48dp minimum touch targets where interactive and require meaningful accessibility semantics.
- Placeholder five-tab navigation: Play · Arena · Games · Insights · Settings.

**Verification:** Compose semantics tests cover foundational controls; screenshot/device verification follows when Android tooling is available.

## Phase 2 — Chess core

### M3 — Core chess data model
Pure Kotlin, Android-independent types for pieces, squares, moves, position state, castling rights, variant, result/termination and deterministic repetition keys.

**Verification:** deterministic value/hash tests.

### M4 — FEN and position construction
- Standard initial position.
- Parse/serialize six-field FEN.
- Validate board syntax, active color, castling token, en-passant square, clocks and king count.
- Keep the model ready for Chess960/X-FEN work in M6 without implementing Chess960 castling early.

**Verification:** FEN round trips and malformed-input corpus.

### M5 — Standard chess legality
- Pseudo-legal generation for all pieces.
- Legal filtering by king safety.
- Castling, en passant, promotion.
- Check/checkmate/stalemate.
- Repetition keys/history.
- 50-move claim, 75-move automatic draw, threefold claim, fivefold automatic draw, insufficient material.
- Make/apply operations are deterministic.

**Verification:** Standard perft suite plus targeted regression tests for special rules.

### Gate A — Standard chess core
Do not proceed until M3–M5 are green. Report exact test/perft output and any deviations/discoveries.

---

## Later approved milestones

### M6 — Chess960 legality
All 960 starts, generalized castling semantics, X-FEN/Shredder-FEN considerations, UCI Chess960 notation and perft/regression corpus.

### M7 — SAN, PGN and game tree
SAN parse/generate, PGN headers/comments/NAGs/variations, canonical tree model and round-trip tests.

### M8 — Canonical Room schema
Unified local/arena/imported/branch game model; source metadata separate from chess content; review model/versioned data and disposable heavy caches.

### M9 — Persistence integrity and migrations
Transactional saves, dedupe fingerprint/merge, DataStore preferences, schema exports and migration tests.

### M10 — Reusable Compose chessboard
Tap/drag, promotion, orientation, highlights and overlays; legality remains owned by `core-chess`.

### M11 — Engine API and UCI protocol
Typed engine capabilities/session/search/results, search IDs and position revisions, pure UCI parser tests.

### M12 — Isolated engine-host spike
Preferred two isolated engine processes. Determine exact Binder/AIDL/native-wrapper design using mock engine/crash/cancel/restart tests before real adapters.

### M13 — Stockfish 18 integration
Pinned `sf_18`, ARM64/16KB-compatible native build, real capability exposure and reliability suite.

### M14 — Reckless 0.9.0 integration
Pinned `v0.9.0`; solve Android library/wrapper boundary without silently upgrading engine behavior.

### M15 — Strength and humanization
Native, Humanized and Hybrid models; deterministic seeded candidate selection and empirical calibration.

### M16 — Deterministic clocks
Monotonic time source, increment, pause/resume, timeout and fake-time tests.

### M17 — Runtime state machine
Single serialized owner for game events/controllers; engines never mutate state.

### M18 — Premoves
Single 100ms-cost default, configurable queue later; validate only against resulting authoritative position.

### M19 — Human vs Engine Play
First vertical slice: Standard/Chess960, engine, side, Elo/model/time, clean live UI, persistence, crash-safe behavior.

### Gate B — Playable runtime
Correct chess + persistence + both engines + clocks/premoves + clean Play UI.

---

## Physical Polish & Customization Batch — inserted before M20

**Required base:** approved/promoted Batch B SHA `06baccdfb3696371750e6d6a5288e50c1add3d24`.  
**Detailed specification:** `docs/superpowers/specs/2026-08-17-physical-polish-customization-design.md`.

### P1 — Physical-device regressions + APK-size audit
- Reproduce and permanently regress the physical board Y-axis jump.
- Stabilize the square BoardStage and ensure transient overlays do not participate in measurement.
- Audit universal debug APK composition and largest contributors.
- Add automated APK-size reporting/budgets.
- Add a narrow ARM64-only physical-debug artifact path while retaining x86_64 CI coverage.

### P2 — Full LumenChess UI redesign
- Replace provisional M2/M19 developer-style composition with the established LumenChess visual direction.
- Actively use the committed `LumenChess_UI_Concept_Blue.png` reference plus settled design docs.
- Polish navigation, Play setup and live Play without pulling Analysis/Review UI forward.
- Preserve accessibility and stable board geometry.

### P3 — Visual customization — **consumes M41**
- DataStore-backed presentation/settings source of truth.
- System/Dark/OLED Dark/Light.
- Boards / Pieces / Background / Presets with live preview.
- Reusable piece-set abstraction with a genuinely production-quality default LumenChess set and at least one polished selectable alternative.
- Record asset provenance/licensing.

### P4 — Sounds/haptics — **consumes M43**
- Event-observing audio/haptic feedback outside authoritative runtime paths.
- Polished original/project-owned or redistribution-safe built-in sounds; no crude placeholder beeps.
- Per-event configuration plus safe local individual/ZIP sound-pack import where feasible.
- Record asset provenance/licensing.

### Gate P — Physical Polish & Customization
Run the complete cumulative M0–M19 suite plus P1–P4 tests/checks. Promote only the exact green SHA, run fresh complete `main` CI, then generate an ARM64-only Pixel 8 Pro / Android 17 debug APK from that exact promoted SHA. Physical user approval is mandatory before M20.

---

### M20 — Arena base — **blocked until Gate P physical approval**
Independent engines/configs, clocks, openings and default eval display.

### M21 — Manual takeover
Manual opening and midgame takeover/return for either/both sides with locked clock defaults.

### M22 — Arena branching
Sandbox-first branches, original immutable, explicit Save as Variation.

### M23 — Unified Games Library
One canonical library with source tags/filtering/search and scalable queries.

### M24 — Imports/exports and starting positions
PGN/FEN/file/clipboard, board editor, saved positions and odds tools.

### M25 — Local opening identification
Bundled CC0 Lichess opening data indexed by position/transposition.

### M26 — Analysis
Eval bar, PVs/arrows, MultiPV, full move list, variation creation and Moves ↔ Explorer lower pane.

### M27 — Review analysis pipeline
Checkpointed, cancellable/resumable per-ply analysis with engine/model/version metadata.

### M28 — Classification model
Book/Best/Excellent/Good/Inaccuracy/Mistake/Miss/Blunder/Great/Brilliant using versioned expected-points-loss logic and deeper boundary rechecks.

### M29 — Accuracy and technical stats
Transparent Lumen accuracy model, phase stats and technical metrics.

### M30 — Game Rating
Versioned calibrated single-game performance estimate, explicitly not Chess.com's private formula.

### M31 — Review UI
Summary, graph/bar, Accuracy, Game Rating, classifications, horizontal move rail, explanations, Guided/Full/Key Moments and technical details.

### Gate C — Review/analysis complete

### M32 — Local Explorer statistics
Offline candidate stats with labeled data source.

### M33 — Lichess enrichment
OAuth2 PKCE, serial/rate-limited requests, local-first cache and optional cloud eval.

### M34 — Chess.com sync
Public read-only incremental/cached sync into canonical library.

### M35 — Lichess sync
Connected-account import through same canonical model.

### M36 — Background queues
WorkManager orchestration for sync/review/cleanup with resumable chunks and constraints.

### M37 — Rating infrastructure
Performance Estimate + Glicko-2 + Glicko-1 + FIDE-style event-sourced rating pools.

### M38 — Match Your Elo
Resolve local/remote rating source, default ±100 matching range, fixed game-start target.

### M39 — Insights aggregation
Cached/filterable aggregates backed by canonical library/review/rating data.

### M40 — Insights UI
General, drillable statistics without excessive live-play clutter.

### M41 — Board & Pieces customization — **satisfied early by P3; do not reimplement**
Historical milestone number retained for roadmap continuity. Relevant Boards/Pieces/Background/Presets/live-preview/app-appearance infrastructure is implemented in P3. When this number is reached, only verify that later screens consume the shared P3 architecture; do not duplicate customization work.

### M42 — Advanced assistance customization
Optional eval/lines/arrows/moves/material/premove/engine/review settings while fresh-install defaults remain sparse. P3 intentionally does not pull these not-yet-existing feature controls forward.

### M43 — Sounds/haptics — **satisfied early by P4; do not reimplement**
Historical milestone number retained for roadmap continuity. Relevant built-in sounds, haptics, per-event configuration and local sound-pack infrastructure are implemented in P4. When this number is reached, only integrate newly existing later-feature events if needed; do not duplicate the P4 system.

### M44 — Storage lifecycle
Heavy-cache compaction without silent deletion of canonical user games.

### M45 — Engine/resource torture tests
Crash, cancel, memory, process death, thermal, 16KB and Android 17 stress testing.

### M46 — Performance profiling
Macrobenchmark/Baseline Profiles and measurable frame/startup budgets.

### M47 — Full accessibility/device adaptation
TalkBack, large text, reduced motion, contrast, landscape/tablet/foldable and broader Android compatibility.
