# M22 — Arena branching: implementation / manual-review boundary

## Recovered authority and lineage

Recovered main `56e47db29481c6d8a52b487af237deef3641716e` and local/remote
`codex/m22-arena-branching` at `7ffb516eff64ff8f189a891b356ef9633544653d`.
The preceding P6 evidence correction changed only its documentation; all eleven
unfinished M22 source/test files were preserved and completed, not restarted.
Existing `624478b` branch-persistence/untimed-clock work remains in history.
Rejected `c21a2f6` is not an ancestor. Main, M20/M21 approval, and closed P6
remain intact.

Scope follows IMPLEMENTATION-PLAN M22, design specification sections 8.5–8.7
and 23.2, DECISIONS/Branching, UI-FLOWS section 3, and ADRs 0008–0011/0017:
**sandbox-first Arena branching, original immutable, explicit Save as Variation**.
The detailed existing implementation plan is
`docs/superpowers/plans/2026-09-03-m22-arena-branching.md`.

## Implemented behavior

- Pause and browse current/earlier mainline positions as presentation only.
  Historical browsing suppresses unrelated live evaluation and board travel.
- Create a separate persisted BRANCH session at the exact source FEN/variant,
  with durable source game UUID and move UUID (null only at root). Runtime node
  identities are not persistence identities.
- Inherit the independently resolved engine/strength/model/seed settings.
  Sandbox engines, strength, manual controllers and timed/untimed settings are
  editable through the established Arena components. Default sandbox control is
  Both manual until release; clocks start from the selected configuration, not
  an invented historical reading. Ordinary Arena defaults are unchanged.
- Save as Variation is explicit, transactional, append-only and idempotent.
  It loads the latest original, validates moves through core-chess, preserves
  metadata/UUIDs/result/mainline/other branches, and adds only missing variation
  nodes. A historical leaf continuation uses a sibling RAV of its incoming move
  so the original mainline does not grow implicitly.
- Return to Original restores its canonical position paused. Process restoration
  preserves branch identity, FEN, Chess960 castling state, configuration, manual
  leases and clock mode; no in-flight engine search is restored.
- Superseded branch capture/original-load callbacks cannot override later Resume,
  history changes, screen departure, Stop, or a new session. Engine results retain
  existing runtime search-ID/revision validation and session guards.

Runtime/core remain authoritative. No board renderer, imported piece cache/optics,
P6 feedback/motion, Play/Settings composition, or Room schema was changed.

## Focused corrections and tests

Nine new regressions first failed on the recovered implementation: five delayed
branch-capture cancellation/selection cases, historical live-evaluation leakage,
and three delayed Return-to-Original cancellation cases. Minimal presentation/
gateway fixes then passed all **11 native branch integration tests**, including
the two preserved original integration cases. Arena JVM: **30 PASS**.

Focused source commits:

- `d1ca1fe` — sandbox origin/configuration/codec and untimed engine routing.
- `17d606e` — native history/branch/save/return UI, guarded callbacks, regressions.

Independent read-only source review found no remaining important/critical issue
in identity, source immutability, clocks/controllers, Chess960, or cancellation.
CodeRabbit has no installed CLI or callable integration on this host; no
CodeRabbit review is claimed and no tooling was installed for this optional check.

## Final executed cumulative verification — 2026-09-09

- Fresh JVM execution: core-chess **71**, runtime **58**, engine-api **36**,
  engine-host **8**, app **126**: **299 PASS / 0 FAIL**.
- Engine-host, persistence and app lint/debug/androidTest assemblies: **PASS**.
  Persistence JVM is **NO-SOURCE**, not a passing test suite.
- Full API-37 gate: engine-host **19 PASS**, persistence **59 PASS**,
  app **74 PASS / 34 opt-in captures SKIPPED / 0 FAIL**. Counts come from
  individual JUnit cases, not Gradle's duplicate assumption-progress lines.
- Existing private-input discovery/sanitization tests: **4 PASS**.
- Standard/Chess960, both real engines, manual takeover/return, branch attachment,
  persistence, stale-search protection and existing board regressions passed.
- No checks, assertions, lint severity, or workflow gates were weakened.

The local gate used the existing Android CI task list and API-37 emulator contract.
The already-established host-only JDK/short-build-path setup was reused; it is not
committed configuration. Only debug/test assemblies were invoked.

## Native review evidence and restoration

Two separately enabled `ArenaBranchReviewQaTest` methods passed through the real
MainActivity/Settings/Arena UI, separated by an explicit host app force-stop.
Neo was selected in Settings. Stored/resolved ID `private.chesscom.ejgfv` and
the actual board piece tags agree throughout; captures visibly show Neo.

Evidence covers source revision 4, browsing ply 3 without changing that revision,
new independent untimed branch, alternative `d7d6`, Stockfish/Reckless continuation
to branch revision 5, explicit attachment of five moves, return to the unchanged
source, a representative Chess960 branch, Resume and process-restored continuation.
Source game ID, FEN/revision, engines, manual state and clock mode matched on return.
The restored Chess960 branch matched its saved game ID, exact FEN/revision, durable
origin and manual leases, with castling field `GCgc`, untimed clock and no pending
search before continuation. Source immutability is also asserted transactionally
by the real Room tests, including repeated/extended attachments and failures.

**All 54 measured board samples:** Compose-root physical rectangle
**(21,428)–(1323,1730), 1302 × 1302 px, maximum position/size delta 0 px**.
Samples include source/history, branch origin, move, engine thinking/result,
save, return, Chess960 and restoration. API 37, 1344 × 2992, 489 dpi, 16-KiB pages.

Native PNGs are full 1344 × 2992 captures. The video is an actual emulator recording,
672 × 1496 at original speed; full-resolution encoder configuration was unsupported.
The emulator reports 60 Hz, but screenrecord has variable/sparse capture cadence:
this is semantic-flow evidence, not a new motion/frame-timing approval. The initial
capture attempt failed because a shell-created output folder was not app-writable;
the empty failed output was removed and the app recreated its own folder. No
production change was needed for either QA-only issue. No design simulation is used.

Native inspection found distinct source/history/branch states, legible existing
controls, correct Neo identity, no clipped board or renderer substitution. Source
before/return screenshots are not byte-identical: a small difference is confined
to the last-move c6 cell (maximum channel delta 9), with identical position and
geometry. No P6 calibration was performed to erase that capture-state difference.
Manual aesthetic/flow approval remains the user's decision.

## Packaging, privacy and boundary

Fresh public APK inspection: **0 private styles/piece PNGs/board PNGs**, no private
source tokens in inspected code/resources. Fresh personal APK: **39 complete styles,
468 canonical piece PNGs, 8 supported board PNGs**, no unexpected private inputs or
source-path leakage. Both packaged Stockfish/Reckless ABIs and all inspected ELF
PT_LOAD segments satisfy at least `0x4000` alignment. Private inputs remain external
and generated/ignored; no artwork, APK, archive, key or personal path is tracked.

One explicit boundary: a zero-move original cannot receive a RAV without creating
its first mainline move, so attachment is safely rejected; the separate sandbox
can still be played. Historical evaluation is neutral, not a new analysis feature.

The compact native review package, hashes, detailed results and exact final SHA
are delivered locally, not committed or uploaded as public private-art evidence.
Publication uses the existing branch checkpoint CI; any full/device jobs skipped
by that workflow's branch policy are not counted as passes. The cumulative local
gate above is separate executed evidence.

**M22 implementation is ready for manual review, not self-approved.** No merge,
promotion, M23, release/signed APK, or historical P5/P6 reopening is included.

## Manual approval and promotion authorization — 2026-09-09

The user manually approved **M22 — Arena branching**, implementation
`1f3abb2766fd2f4109971be2de30327f68f92a2c`, after inspecting the native review
ZIP (30 members, 29 manifest-declared files, zero checksum/size mismatches;
SHA-256 `4178c334d5fba4f0e22ce6ac23c83e8bce319cc5ad009a888e097ed2d96a49b8`).
This supersedes the historical manual-review boundary above.

Manual review accepted the native source/history/sandbox flow, original-game
preservation, explicit Save as Variation, Standard and Chess960, Stockfish and
Reckless progression, M21 manual takeover compatibility, process restoration,
and stale-search/cancellation behavior. Actual Neo identity
`private.chesscom.ejgfv`, zero-delta board stability across 54 samples, and
public/private packaging boundaries were accepted. The documented zero-move
original attachment boundary and native recording limitations remain explicit.

Approved branch CI **34398043844** passed its checkpoint; branch-policy skipped
jobs are not counted as passes. The independently executed cumulative local
gate above remains part of the accepted evidence. This approval record changes
documentation only; approved production and native evidence are not regenerated.

The user authorizes a history-preserving fast-forward promotion to `main`,
followed by one fresh main promotion gate. Once that gate is green, **M23 —
Unified Games Library** is authorized on its own branch using the authoritative
repository scope. M23 requires its own final manual review and must not be
promoted automatically. P5/P6 remain closed; no M24 or release/signed APK is
authorized by this record. The private native-review ZIP and imagery remain
outside Git.
