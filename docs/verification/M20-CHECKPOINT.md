# M20 — Arena base checkpoint

This document records the durable implementation and manual-review boundary for
M20 on the isolated `codex/m20-arena-base` branch. It does not authorize promotion
to `main`, M21, M22, a release, or a signed APK.

## Boundary and source of truth

- Recovered and approved starting `main`:
  `3af3fe36184cf35f4fb0878e83c3adc233189b7e`.
- M20 branch: `codex/m20-arena-base`.
- Fully tested implementation/native candidate:
  `f7084d73a628ddbcba4c5416184a4c5fde175a6f`.
- A later documentation-only handoff record does not change that tested source
  or native-evidence identity.
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
API-37 device suite (19 engine-host, 48 persistence, and 85 app cases: 59 passed,
26 opt-in capture cases skipped), but its
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

Checkpoint `01c29a8` passed both the full and proportional Android CI gates,
including cumulative API-37 instrumentation and seven native Arena captures.
Pixel inspection then found a capture-configuration omission, not a production
theme defect: the fresh emulator was light while appearance remained `SYSTEM`.
The bounded QA correction selects Dark through the existing Settings UI, as the
P5 reference lane does, and verifies stored appearance plus stored/resolved public
Lumen identity for every image. Application defaults and production pixels are
unchanged. The replacement native evidence must be inspected before handoff.

The same native inspection exposed an M20-only evaluation-readout defect: a score
straddling the dark/light bar split inherited insufficient text contrast (the
captured `+0.36` ink measured 1.19:1 against its dark segment). The score and depth
now have solid neutral backings independent of split position or page appearance.
A native rendering regression samples the actual text and both padding backgrounds
for positive, neutral, and negative scores in Dark and Light. This correction does
not change the board or any approved P5/P6 component.

The first contrast-regression run sampled inside Text semantics, which exclude
surrounding padding, and hit an antialiased glyph edge. The QA probe now samples
the real backing immediately outside the text bounds in the root capture. The
4.5:1 threshold and production colors are unchanged; the cumulative existing
engine, persistence, board, and Arena integration tests passed that run.

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

## Verified native candidate — 2026-09-02

Android CI run [33610971968](https://github.com/unl0cks/LumenChess/actions/runs/33610971968)
executed at `f7084d73a628ddbcba4c5416184a4c5fde175a6f`:

- Proportional checkpoint: **passed**.
- Full cumulative gate: **passed** — core-chess, runtime, engine API/host,
  persistence and app unit tests, lint, debug and Android-test assembly, native
  ABI/16 KiB checks, Room schema check, public packaging and API-37 devices.
- Engine-host instrumentation: **19 passed**.
- Persistence instrumentation: **48 passed**.
- App cumulative instrumentation: **86 cases**, **60 passed**, **26 skipped**,
  **0 failed**. The runner's final `112` display counts skipped events twice;
  it is not 112 executed passing tests.
- Explicit Arena native-capture invocation: **1 passed**, producing seven PNGs
  and measured metadata after the cumulative run.
- Separate `play-device` and `engine-device` jobs: **skipped by branch policy**,
  not passes. Relevant integration tests ran in the full cumulative lane.
- The new native score-contrast regression passed both appearances with positive,
  neutral and negative values. Cancelled-search analysis regression and real
  Stockfish/Reckless progression passed.

The seven final, unmodified native PNGs were inspected. Dark appearance,
stored/resolved `lumen-vector`, readable evaluation score/depth, both orientations,
Standard and Chess960 are visible. No concrete new clipping, internal compositing
seam, renderer substitution or board-layout defect was observed in these static
frames. This is an agent inspection, **not user visual approval** and not a claim
to have recorded every animation frame.

Measured Arena stage at 1344 x 2992 / 489 dpi:

| State | Native bounds | Size |
| --- | --- | --- |
| Standard progression | `(21,350)-(1323,1652)` | 1302 x 1302 px |
| Chess960 progression | `(21,350)-(1323,1652)` | 1302 x 1302 px |

The native lane asserts exact before/after equality on flip. Integration tests
assert exact stage equality across multiple authoritative revisions and required
host loss/recovery: **0 px layout delta**. This is the new Arena stage; it does not
replace or relabel the distinct approved Play Live geometry.

The public APK scan found **0 generated personal entries and 0 local-source
tokens**. Read-only external inventory still finds **39 complete styles / 468
canonical PNGs**. Tracked-file scans found no private source/generated assets,
APKs, keystores, private archives or absolute personal-source paths.

Production changes are bounded to eight new `arena/` files, four app routing/
gateway files, four engine-transport files and one live persistence repository
(17 files total). `core-chess`, `game-runtime`, board rendering, piece catalog/
cache/fitting, feedback/motion, Settings and design-system production files have
no M20 delta. Existing Play regression covers human moves, both engines, Chess960,
premoves, persistence and restart; existing board motion tests cover castling,
promotion, en passant, capture, drag and cancellation.

The transferable manual-review package is `LumenChess-M20-review-f7084d7.zip`.
It contains original public native PNGs, exact board metadata, a README,
verification results and a SHA-256 manifest. Private artwork and APKs are excluded.
Archive size: **4,055,722 bytes**; SHA-256:
`a7cbe7ba9aa00189169a69ac7bc45d72f74b949933c4434fcfce07dbc923c27a`.
All 10 declared members verified; the eleventh ZIP member is the manifest itself.

## Known limitations and next boundary

- User approval of the M20 native UI/interaction remains pending. The complete
  candidate CI/device gate and agent image inspection are finished.
- Local Windows Gradle verification is blocked by the host loopback limitation
  described above; no local pass is claimed.
- A fresh personal APK and Neo/3D Staunton M20 native captures were **not run**.
  The existing Ubuntu environment also lacks a configured Android/JDK toolchain.
  The personal mechanism is unchanged and inventory is verified, but these are
  not substitutes for a new personal packaging/device pass.
- CodeRabbit is unavailable through the supported tools/installed CLI; a focused
  source review was performed, not an external CodeRabbit review.
- M21 manual opening/takeover/return is not implemented.
- M22 branching is not implemented.
- No release or signed APK has been built.
- This branch has not been merged or promoted to `main`.

The next boundary is manual M20 review with the private-device verification
limitation above disclosed. Promotion, M21, M22, and release work each require
separate explicit authorization.

## Final evidence closure — 2026-09-02

This section supersedes the earlier local-build, personal-renderer and unpushed-QA
limitations. It does not declare manual M20 approval or authorize promotion.

Recovery found local `e6ce1be918238bbaaedd058650ad4a203bf118b2` one commit ahead of
remote `f14cf9aa1b08d7d7eb1634d33ba9c1d07aaed73d`. The exact existing QA/documentation
commit was pushed normally with its SHA preserved. `main` remains
`3af3fe36184cf35f4fb0878e83c3adc233189b7e`, and rejected `c21a2f6...` remains outside
the M20 ancestry.

Final native QA source: `62e17872a4e6a49a4066725a0286c3d170913d9e`. Its only code
change is to the opt-in `ArenaReviewCompletionQaTest.kt`. There is no production
delta from the already-reviewed `f14cf9a...` candidate. The test now checks the
persisted Neo selection after process restart without selecting Neo again, records
actual gateway/host-slot identity, and bounds Random assignment attempts at 12.

### Reversed Random assignment and restoration

The first fresh Random start through the actual setup UI produced:

| Canonical side | Engine | Strength/model | Actual host slot |
| --- | --- | --- | --- |
| White | Reckless 0.9.0 | 1200 / Humanized | A |
| Black | Stockfish 18 | 2000 / Native | B |

No seed or RNG override was injected. Native cards, gateway identity, real Binder
search requests, canonical participant records and persisted engine metadata all
agreed with this reversal before and after restoration.

Neo was selected through Settings before starting. Stored and resolved ID remained
`private.chesscom.ejgfv` (`pieces/ejgfv`) through process stop/reopen, Resume Arena,
and resumed engine progression. Every observed board-piece semantics tag agreed;
the original native pixels visibly show Neo.

The canonical game restored at revision 5 with FEN:

`rnbqkbnr/ppp2ppp/4p3/3p4/4P3/2N2N2/PPPP1PPP/R1BQKB1R b KQkq - 1 3`

White/Black participant configurations and saved clocks restored exactly:
598681 ms White, 596413 ms Black, paused and not running. No in-flight search was
persisted as authoritative state. The actual Resume Arena action resumes the saved
game; QA then pauses through the product UI for a stable first-restored screenshot,
so clock time may elapse after Resume. Subsequent real-host play reached revision 9.
The new-game setup defaults above the Resume card are independent of the saved
game; they must not be misread as its restored configuration.

### Native geometry and visual inspection

Fresh API-37 evidence used 1344 x 2992 at 489 dpi, 60 Hz, Google APIs x86_64 with
16 KiB pages. The installed personal APK hash matched the locally audited APK.

| Required state | Native Compose stage bounds | Size | Delta |
| --- | --- | --- | --- |
| Before force-stop | `(21,428)-(1323,1730)` | 1302 x 1302 | 0 px |
| First restored Live | `(21,428)-(1323,1730)` | 1302 x 1302 | 0 px |
| Resumed progression | `(21,428)-(1323,1730)` | 1302 x 1302 | 0 px |
| Reversed Random start | `(21,428)-(1323,1730)` | 1302 x 1302 | 0 px |
| Reversed Random progression | `(21,428)-(1323,1730)` | 1302 x 1302 | 0 px |

The setup/Resume entry contains no board; it is not assigned a fabricated board
rectangle. Exact equality was also asserted while waiting through engine thinking
and results. Fresh full-context screenshots were inspected: no new clipping,
renderer substitution, baseline anomaly, board movement or compositing artifact
was found. Existing accepted setup/opening and 3D/Public Lumen evidence was carried
forward unchanged rather than recreated.

### Focused verification and package

- ArenaSetupTest: 9 passed; ArenaRuntimeCoordinatorTest: 6 passed;
  ArenaSnapshotCodecTest: 2 passed. Total **17 passed, 0 failed, 0 skipped**.
- Fresh native prepare/reversed-assignment invocation: **1 passed** (22.128 s).
- Separate force-stop/reopen native restoration invocation: **1 passed** (15.576 s).
- Updated debug-test assembly: **passed**. `git diff --check`: **passed**.
- Existing personal APK rescanned: **39 complete styles / 468 canonical piece
  PNGs / 8 supported board PNGs**. Installed APK SHA-256 matched
  `0bb1183662946a0ae76b1bbfbe16c4eb29c99ffd6735c76d2bd80c465541de81`.
- Existing public APK rescanned: **0 private pieces, 0 private boards, 0 generated
  staging entries, 0 personal-source path leaks**. No public rebuild was required
  for this QA-only change. No source scripts/ZIPs/venv or tracked private assets
  were found in either scan.
- CI for the newly durable original QA commit: run
  [33640080424](https://github.com/unl0cks/LumenChess/actions/runs/33640080424),
  checkpoint **passed**; full/play-device/engine-device **skipped by branch
  policy**, not passes. Historical multi-hour gates were **not rerun** in this
  final evidence pass. The native tests above executed locally on API 37.
- CodeRabbit: **not run**, no supported callable integration. The focused QA diff
  was reviewed directly; no production defect or correction was identified.

Replacement package: `LumenChess-M20-final-review-62e1787.zip`, native/test-equivalent
to the QA source above. Size **13,435,625 bytes**; SHA-256:

`1bf173454b81a1fe193f7fccfc2e5fee7d6ea15a9d9856ce4be74b0d47d527fb`

It contains **29 ZIP members / 28 manifest members / 0 mismatches**: nine accepted
native images, nine fresh native images, exact IDs/configuration/snapshot/bounds
metadata, two native test logs, README and SHA-256 manifest. No private source
assets, APKs or unnecessary absolute local paths are packaged. The archive stays
local for manual review and is not uploaded to GitHub.

The two requested evidence gaps are now demonstrated. **M20 remains pending the
user's final manual approval.** No promotion/merge, M21, M22, release or signed
final APK is authorized by this checkpoint.
