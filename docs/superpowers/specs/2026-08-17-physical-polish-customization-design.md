# Physical Polish & Customization Batch — Design Specification

**Approved:** 2026-08-17  
**Required base:** `06baccdfb3696371750e6d6a5288e50c1add3d24`  
**Branch:** `physical-polish-customization`  
**Position in roadmap:** immediately after M19 / Gate B and before M20.  

## Purpose

Physical Pixel 8 Pro testing showed that the first playable Human-vs-Engine slice is technically correct but still has a physical layout regression, unexplained debug-package bloat, and an intentionally provisional UI. Before Arena or later product features are allowed to build on that surface, LumenChess will complete one focused Physical Polish & Customization batch:

1. **P1 — Physical-device regressions + APK-size audit**
2. **P2 — Full LumenChess UI redesign**
3. **P3 — Themes / boards / pieces / backgrounds / presets**
4. **P4 — Sounds / haptics**

M20 is blocked until this complete batch is green, promoted, rebuilt from the exact promoted SHA, and physically reviewed by the user.

## Global invariants

All M0–M19 architecture and correctness gates remain binding.

- `core-chess` remains the authority for chess legality/state.
- The M17 serialized runtime remains the only authoritative runtime owner.
- Engines never mutate chess/game state directly.
- UI, customization, sounds and haptics remain presentation concerns.
- Engine-returned moves remain independently validated before acceptance.
- Existing Stockfish 18 and Reckless 0.9.0 provenance/version pins remain intact.
- Standard and Chess960 remain mandatory.
- Persistence, clocks, premoves, crash recovery and stale-result rejection remain regression-gated.
- Accessibility semantics, contrast and minimum touch targets must not regress.
- No copyright-risk Chess.com visual/audio assets may be redistributed.

## P1 — Physical regressions and APK-size audit

### Stable board geometry

Physical-device invariant:

> The board's screen-space bounds must remain stable across human move submitted → engine thinking/search state → engine result unless the user explicitly opens or closes another UI surface.

The current M19 layout has a strong causal candidate: the live board is a square child inside a weighted stage, while the premove input overlay is conditionally inserted only during engine-owned turns and uses `fillMaxSize()`. That overlay can participate in measurement and expand the inner container, changing the square board's vertical alignment immediately after a human move.

Implementation order:

1. Add a Compose/instrumentation regression that records board bounds across human-turn → engine-turn/premove-enabled → engine-result states and demonstrates the current instability.
2. Introduce an explicitly square `BoardStage` whose geometry is independent of transient children.
3. Make premove and future board overlays non-measuring (`matchParentSize()`/equivalent) within that stage.
4. Keep engine status, clock and participant surfaces fixed-footprint enough that status-copy transitions cannot move the board.
5. Retain the geometry regression permanently.

No compensating animation, hardcoded delay or visually opposing motion is acceptable as the fix.

### APK-size observability

The approved Batch B debug APK is approximately 390 MB. Audit and report:

- compressed and uncompressed APK totals;
- ABI totals (`arm64-v8a`, `x86_64`, incidental 32-bit dependency payloads);
- Stockfish/Reckless native libraries;
- NNUE/network contribution;
- native debug symbols;
- AndroidX native libraries;
- DEX, resources and assets;
- duplicated data and dependency packaging.

Initial audit indicates native engine payload dominates and both ARM64 and x86_64 copies are packaged in the universal debug artifact. Required NNUE data must not be removed merely to improve the number.

Add repository-owned automated APK-size reporting, top-entry reporting and budget status. Initial reporting budgets:

- **ARM64 physical-debug:** ~230 MB maximum.
- **Universal/debug CI:** ~410 MB maximum.
- **Release:** report separately once a representative release configuration exists; do not falsely equate debug size with final release size.

Provide a narrow ARM64-only physical-device APK path while preserving x86_64 builds for emulator CI. Do not remove x86_64 support from normal CI.

## P2 — Full LumenChess UI redesign

P2 is a genuine composition/design pass, not a recolor of M19 controls.

Before implementation reread and follow:

- `docs/design/LumenChess-Full-Design-Spec.md`
- `docs/design/DECISIONS.md`
- `docs/design/UI-FLOWS.md`
- `docs/references/REFERENCE-MANIFEST.md`

**Mandatory visual reference use:** implementation must actively inspect and use `docs/references/LumenChess_UI_Concept_Blue.png` in addition to the written design documents. The concept remains inspiration rather than a pixel-perfect specification, but the resulting app must be visually recognizable as the established LumenChess product direction. “Not pixel-perfect” is not permission to produce another generic Material developer UI.

### Navigation

Retain `Play · Arena · Games · Insights · Settings`. Replace letter placeholders with polished icon + label navigation. Future tabs may remain unavailable, but must use an intentional preview/disabled state rather than debug/milestone copy.

### Play setup

Keep Standard/Chess960, Stockfish/Reckless, White/Black/Random, Elo/Full Strength, strength model and time control. Redesign into compact grouped selectors, strong hierarchy, accessible sliders/controls and a prominent Start action. Avoid a vertical stack of giant outlined/cards-as-form-fields.

### Live Play

Keep live play deliberately sparse: participant/engine rows, clocks, board and compact essential actions. The board is the visual center. Participant rows are polished, compact and fixed-footprint. Clock prominence must not require oversized containers. Pause/Resume, Resign and Exit use deliberate iconography/hierarchy rather than generic equal-weight outlined buttons.

Do not pull evaluation lines, Review or Analysis UI forward.

### Motion and accessibility

Use restrained motion for screen/state transitions, selection, navigation and cards. Do not animate authoritative board geometry or reintroduce the P1 jump. Preserve TalkBack-friendly labeling, contrast, scalable text where practical and minimum touch targets. Add structural/screenshot-style Compose regression coverage where practical.

## P3 — Visual customization (pulled forward from M41)

P3 intentionally consumes the relevant M41 scope now so subsequent Arena/Analysis/Review UI can share the same appearance architecture.

### Settings source of truth

Introduce a typed DataStore-backed presentation/settings source of truth. It is independent from Room game persistence and authoritative runtime state.

### App appearance

Support:

- System
- Dark
- OLED Dark
- Light

Blue remains the default LumenChess accent. Keep app accent architecturally independent from board colors so later accent customization can plug in without coupling the two.

### Board customization

Support board palette selection/custom light/dark square colors plus highlight colors, legal-move indicators, last-move styling, selected-square styling and coordinate appearance where supported. Provide a live preview.

### Piece-set abstraction

`LumenChessboard` must no longer hardwire its current piece representation. Define a reusable piece-set abstraction consumable by Play and future Arena/Analysis/Review.

Ship at least two selectable built-in treatments to prove the abstraction, **but the primary/default LumenChess set must itself be genuinely production-quality**. It must be polished enough that the user could reasonably keep using it as their normal piece set. Do not satisfy the requirement with two placeholder-quality renderers.

Only ship original or distribution-compatible assets. Record provenance/license. If the default set is project-owned, record that explicitly.

### Background and presets

Support the settled Boards / Pieces / Background / Presets model with live preview. Background choices must preserve board readability. Presets bundle board/pieces/background/highlight choices but resolve into editable individual preferences; applying a preset never locks component overrides.

Do not pull M42's later Analysis/Review assistance settings forward.

## P4 — Sounds and haptics (pulled forward from M43)

P4 intentionally consumes the relevant M43 scope now.

### Runtime boundary

Sounds/haptics observe already-committed game/runtime transitions. They never participate in legality, clocks, engine search authority, persistence authority or game-state mutation. Playback stays off latency-sensitive authoritative paths.

Use revision/event deduplication so recomposition and clock refreshes cannot replay feedback.

### Events and configuration

At minimum cover move, capture, check, castle, promotion, game start/end and justified UI interactions. The architecture supports per-event configuration and haptic enablement/intensity patterns where appropriate.

### Audio quality and licensing

Original/project-owned sounds are preferred. Deterministic/generated audio is acceptable only when it produces genuinely polished, finished-product chess sounds. Do not substitute crude beeps merely to satisfy architecture.

If higher-quality original static assets are the better solution, use them and record provenance explicitly as project-owned assets. Any third-party built-in asset must have distribution-compatible licensing and documented provenance.

Support safe user-local individual sound overrides and ZIP sound packs where feasible in this pulled-forward scope. Protect ZIP extraction from traversal/duplicate-entry abuse, validate supported media, and keep imports in app-private storage. Never commit scraped Chess.com sounds.

## Roadmap consumption rules

- P3 **pulls forward and consumes M41 — Board & Pieces customization**.
- P4 **pulls forward and consumes M43 — Sounds/haptics**.
- M41 and M43 remain visible in the historical milestone numbering but are marked satisfied by this batch; they must not silently be implemented again later.
- M42 remains later and retains only advanced assistance/Analysis/Review customization that depends on features not yet built.
- P2 may establish the Settings shell needed by P3/P4, but may not implement later feature-specific settings early.

## Checkpoints and CI

Use one branch rooted exactly at `06baccdfb3696371750e6d6a5288e50c1add3d24` and strict checkpoints:

- `checkpoint(P1): ...`
- `checkpoint(P2): ...`
- `checkpoint(P3): ...`
- `checkpoint(P4): ...`

Run proportional CI after each checkpoint. After P4, run **Gate P**, a complete cumulative branch gate covering all M0–M19 behavior plus P1–P4. Promote only the exact Gate-P-green SHA. Verify `main` is still at the expected pre-promotion SHA before fast-forwarding. Run fresh complete `main` CI on the exact promoted SHA.

## Physical-device gate

After fresh `main` CI is green, build an ARM64-only Pixel 8 Pro / Android 17 debug APK from the exact promoted SHA. Report SHA, APK hash, package/build metadata, before/after size and major contributors.

Physical review must cover:

- stable board geometry;
- redesigned setup/live Play/navigation;
- System/Dark/OLED/Light;
- board themes;
- production-quality default piece set plus alternate set;
- backgrounds/presets;
- sounds/haptics;
- Stockfish/Reckless;
- Standard/Chess960;
- clocks/premoves;
- persistence;
- background/reopen behavior.

Then **STOP**. M20 remains blocked until explicit user approval of the physical result.
