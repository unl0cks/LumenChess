# M23 — Unified Games Library

Status: implemented and verified; **awaiting manual M23 review, not approved or promoted**.

## Authority and baseline

M23 in the implementation plan is “One canonical library with source tags/filtering/search and scalable queries.” Full design specification §§15.1/15.7, DECISIONS/Library, UI-FLOWS §7, and ADR 0009 supply the source-neutral cards, flags and canonical identity constraints.

M22 approval was recorded and fast-forward promoted at `ca875e719e8cc36d6269696af1ed59a447effbf0`. Promoted-main gate [34401821714](https://github.com/unl0cks/LumenChess/actions/runs/34401821714) passed. M23 starts from that commit on `codex/m23-unified-games-library`. The rejected P6 renderer remains outside the lineage.

## Implemented behavior

- One existing canonical Room database; no parallel game or engine model.
- All, Local, Engine Arena, Chess.com, Lichess, Imported, Branches / Analysis, and Favorites filters; multiple sources do not duplicate a card.
- Literal local substring search over available names/engines/headers. SQL punctuation is text, not a wildcard or injected query. Matching uses SQLite's built-in ASCII case folding; this is not a locale-aware full-text search engine.
- Newest-first stable UUID-tiebroken keyset pages (40 cards, repository maximum 100); three bounded metadata queries, no per-card game-tree reconstruction. Explicit Load more; refresh preserves the loaded list context.
- Available names, results, dates, clocks, variant, ratings, sources and review status; no invented missing metadata.
- Durable Favorite and Protect flags exclude heavyweight retention. Non-destructive schema 3 migration preserves prior canonical IDs/content and both older schemas.
- Confirmed manual Delete, blocked while Play/Arena ownership is unresolved or the game is currently owned/resumable. Restoration and first-save readiness are presentation-only; runtime and engine ownership are unchanged.
- Read-only canonical opening with mainline/variation navigation, orientation, comments and metadata. UUID selection, child-index history path and list state restore through SavedStateHandle. Viewing cannot make moves or start engines.
- Existing Arena branching remains in Arena. Future Review/Analyze/Export and the Library branch editor are honestly unavailable; M24 and later milestones are not implemented here.

## Verification record so far

- Focused API-37 run [34823937886](https://github.com/unl0cks/LumenChess/actions/runs/34823937886), `c71a8b2`: **34/34 persistence and 10/10 Library UI PASS**. Includes v1/v2 migrations, retention, dedup, M22 branch persistence, canonical Standard/Chess960 viewing, list restoration and stale query/open protection.
- Proportional Android CI [34823937755](https://github.com/unl0cks/LumenChess/actions/runs/34823937755): PASS. This is not the final cumulative native gate.
- Ownership-readiness regression compiled RED before the API existed and GREEN after `c1d2d68`; its subsequent focused and cumulative native executions passed.
- QA harness `256ee35` compiled successfully; runner `265ae7f` resets only the disposable CI app before its first capture and retains data between the two process phases. Native run 34906102336 was cancelled during setup after source inspection found a QA-only hidden-navigation precondition; it is not a test failure or a passing evidence run.
- Independent persistence and UI task reviews resolved the invalid migration fixture, Android EXPLAIN diagnostic API, obsolete Games QA tag, and ownership-readiness race. No test thresholds were weakened.
- CodeRabbit CLI review through `a9ab24c` completed with one Minor suggestion to disable animator scale. Not applied: the native evidence intentionally retains the established P6 animation-enabled device contract.
- Independent whole-branch source review through `08911f8` resolved the QA navigation and restoration-wording findings; no open Critical, Important, or Minor source findings. This does not substitute for native execution or manual visual approval.
- Focused native run [34950695910](https://github.com/unl0cks/LumenChess/actions/runs/34950695910) at `08911f8`: downloaded XML confirms **34/34 persistence and 11/11 Library UI PASS**, including ownership-readiness deletion protection. The separate capture method failed when its test-only observer requested a no-argument Library ViewModel before composition had supplied the app factory. Capture correction/rerun remains pending; this run is not final evidence success. Its actual Arena saves and safe Stop navigation completed before that failure.

## Native review / final gate

Focused run [34967965553](https://github.com/unl0cks/LumenChess/actions/runs/34967965553) at `fb7901e` passed: 34 persistence tests, 11 Library UI tests, and both separate-process native capture methods. Downloaded XML and method outputs were checked. The earlier QA observer race is resolved without production changes. Proportional Android CI [34967965625](https://github.com/unl0cks/LumenChess/actions/runs/34967965625) also passed.

Actual captures use API 37, 1344×2992 at 489 dpi, Public Lumen (`lumen-vector`). This host lacks a working accelerated emulator, so the established public CI device is used. No evidence is labeled Neo and no private artwork is uploaded. The first successful captures used system light appearance; the final QA setup explicitly selects the existing Dark appearance and records/asserts it, without changing the application default.

The successful focused run recorded 18 settled Library-viewer samples at `[49,268,1295,1514]` (1246×1246 pixels), all four rectangle deltas exactly zero, across Standard/history/variation/flip/Chess960/Arena/branch and restored views. This is the Library layout lane, not a claim that its rectangle equals Live. Repository restoration passed with process IDs 5560 then 5848 and unchanged canonical trees, persistent identities, sources, flags and branch origin. Synthetic external-source metadata is labeled; the Arena and branch records were saved through actual product UI.

Final cumulative gate [34969249118](https://github.com/unl0cks/LumenChess/actions/runs/34969249118) passed at `bbe90deb32b28b863acbce4afb55c9ffef4f871e` in 23m56s. Downloaded reports: **302 JVM PASS** (app 129, core-chess 71, engine-api 36, engine-host 8, game-runtime 58); **172 native PASS** (app 85, persistence 68, engine-host 19), zero failures. The app report also contains 36 opt-in QA skips; these are not counted as passes. The two M23 QA methods then ran separately and passed. Historical screenshot-only suites were not regenerated.

Lint/debug assemblies, native engine ABI/16-KiB alignment, committed schema history, and decompressed public APK privacy checks all passed. The debug APK audit found zero private entries and zero local-source tokens; no APK is included in the review ZIP. Personal packaging/piece resolution was not modified, so the prior 39-style/468-piece private catalog was not rebuilt or uploaded.

The final dark native evidence proves renderer `lumen-vector`, 18 identical board rectangles `[49,268,1295,1514]`, and repository restoration from PID 8213 to PID 8413. Root visually inspected the actual native list, action dialog, Standard variation, Chess960, saved Arena variation and restored branch images. No obvious overlap, clipping or board-layout instability was observed. A non-blocking display limitation is disclosed: Arena's existing stored clock token (for example `600000+0`) is shown literally rather than reformatted into minutes.

Standard, Chess960, Stockfish, Reckless, M20 Arena independence, M21 takeover/manual controls, M22 branch/cancellation behavior, Play stale-search/premove behavior, and P1 human/thinking/engine-result stability passed their existing cumulative tests. Runtime, engine and board-renderer source remain unchanged. No release task was used.

Evidence distinguishes SavedStateHandle reconstruction/Compose saved-state tests from a host force-stop followed by canonical database reopening; it does not claim OS activity-state restoration from force-stop. The local review ZIP includes measured rectangles, per-file hashes, exact test results and the public-package audit. Final documentation-only closure does not change the gated production/test tree.

## Boundaries

P5/P6 closed. M20/M21/M22 chess behavior preserved. No board/piece/feedback/motion redesign, no private assets tracked, no M24, no M23 merge/promotion, and no release/signed APK.
