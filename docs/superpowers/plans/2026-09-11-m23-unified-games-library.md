# M23 — Unified Games Library Implementation Plan

> **For agentic workers:** Use superpowers:subagent-driven-development. Execute the bounded milestone through one final manual-review boundary; the user has authorized implementation without intermediate permission gates.

**Goal:** Replace the Games preview with a usable, source-neutral canonical library.

**Architecture:** Query the existing Room games/source/participant/header tables in bounded pages. Store library flags alongside, not instead of, canonical games. Open the existing legality-reconstructed GameTree in a read-only viewer; UI owns only selection/navigation, never engine execution or chess state.

**Tech Stack:** Kotlin, existing Room3/SQLite, Compose, existing Lumen components.

**Spec:** IMPLEMENTATION-PLAN M23; Full-Design-Spec §§15.1/15.7; DECISIONS/Library; UI-FLOWS §7; ADR 0009 and accepted M9 identity/retention decisions.

## Global Constraints

- Starting promoted main: `ca875e719e8cc36d6269696af1ed59a447effbf0`; M22 main gate `34401821714` passed all full-job checks.
- One canonical game database and GameTree; persistent UUIDs are not runtime GameNodeId.
- Core-chess remains legality authority; Library opening does not start engine work or change a saved game.
- Standard/Chess960, M20/M21/M22, approved P5/P6 renderer/feedback/motion remain intact.
- Non-destructive explicit Room migrations, retain all previous schema files and tests.
- Public builds contain zero private assets. Native private evidence is local only. No release/signed APK.
- M24 import/export, M26 analysis, M27–31 review and M34–36 sync/background orchestration remain outside this implementation. Future actions must be honestly unavailable, not fake functionality.
- Do not merge M23 or begin M24. Use existing typography/surfaces/icons, not a visual redesign.

## Scope decisions

- Filters: All, Local, Engine Arena, Chess.com, Lichess, Imported, Branches / Analysis, Favorites. Multiple sources never duplicate a game row.
- Search is a literal case-insensitive substring over names/engine names and useful preserved headers (players/event/site/opening/ECO); `%`, `_`, quotes are text, not injected SQL/wildcards. No network search.
- Deterministic newest-created ordering then UUID; keyset pagination of 40 entries, hard maximum 100; page queries never reconstruct every GameTree.
- Cards expose available player/engine, result, date, time-control, variant, rating/header, source and review-state metadata without fabricated values.
- Favorite and Protect are durable flags and exclude heavyweight retention; manual delete is explicit and confirmed. Do not allow deletion of a currently owned Play/Arena session that could be recreated by its pending save.
- Tap opens a read-only canonical board/history/variation viewer. Existing Arena branching remains available through Arena; broad Library analysis/branch-editor integration is not invented ahead of later milestones. Explain unavailable context actions honestly.

### Task 1: Canonical bounded library persistence

**Files:** New `data-persistence/.../GameLibraryRepository.kt`, `GameLibraryModels.kt`, `GameLibraryDao.kt`; modify `Entities.kt`, `LumenDatabase.kt`, `Migrations.kt`, `Daos.kt` retention queries as needed, `PersistenceRetention.kt`; new focused `GameLibraryRepositoryTest.kt` and `Migration2To3Test.kt`; export schema 3.

**Interfaces:** `GameLibraryRepository(database)` provides suspend `page(LibraryQuery, LibraryCursor?, Int): LibraryPage`, `setFavorite(PersistentGameId, Boolean)`, `setProtected(PersistentGameId, Boolean)`, `delete(PersistentGameId)`. Use typed `LibraryFilter`, `LibraryEntry` (id, metadata, sources, flags), `LibraryPage(entries,nextCursor)`. Existing `GamePersistenceRepository.loadGame` remains the canonical opener. Implementer may choose exact model field names, then record them for Task 2.

- [ ] Add failing native tests before behavior: mixed/multisource games, all eight filters, literal search punctuation, empty query, equal-date UUID ordering, page boundaries/no duplicates, bounded large-library queries, missing IDs, flags survive reopen, retention excludes flags, delete cascades without changing other games.
- [ ] Example independently derived fixture: Local `Alpha` at time 30, Arena `Beta` at 20 with second Local source, Imported `100% Real` at 10. All yields 3 rows, Local 2, Arena 1; searching `%` yields only `100% Real`; a 2-entry page then cursor returns the remaining 1.
- [ ] Add populated v2→v3 and v1→latest migration verification retaining game/node/source/review identities and exact Standard/Chess960 contents. Use existing migration-test patterns; no destructive fallback.
- [ ] Run affected compile/focused tests and observe missing behavior; implement smallest SQL/model/migration change; rerun green.
- [ ] Query-plan/large-fixture evidence: bounded cards with no per-card tree load or unbounded list. Preserve existing M9 dedup and retention semantics except explicit new flag exclusions.
- [ ] Commit focused persistence change with RED/GREEN evidence in local task report.

### Task 2: Games route and canonical read-only opening

**Files:** New `app/.../games/GameLibraryViewModel.kt`, `GameLibraryScreen.kt`, `GameLibraryViewer.kt`; modify only Games routing in `ui/LumenChessApp.kt` and minimal approved navigation test expectations; focused JVM/native tests in `games/`.

**Interfaces:** Consume Task 1 repository/model contract. ViewModel cancels or generation-guards search/page/open work, retains filter/query/selected game/node through SavedStateHandle, uses application-context database ownership with cleanup. Expose current Play/Arena game IDs to guard unsafe deletion. Read-only viewer uses existing ThemedLumenChessboard, existing GameTree positions, no new chess tree or runtime.

- [ ] Test latest-query-wins, stale load cancellation, filtering/search/page append, refresh after flag/delete, missing/corrupt-game error, selected game/history restoration.
- [ ] UI tests cover eight scrollable filter options, actual database cards, tap opening, long-press Favorite/Protect/Delete, delete confirmation/cancel, return to same list context; future Review/Analyze/Export/Library Branch editor unavailable with explicit scope copy.
- [ ] Read-only viewer supports root/mainline/variation navigation, orientation, metadata and exact Standard/Chess960 position with no canonical mutation or engine search. Preserve board bounds across history/flip/restoration in this layout lane.
- [ ] Implement using existing Lumen components. No unrelated navigation or settings redesign.
- [ ] Run focused JVM/native tests, then commit app integration with RED/GREEN report.

### Task 3: Native review and regression closure

**Files:** New opt-in `GameLibraryReviewQaTest.kt`; checkpoint `docs/verification/M23-CHECKPOINT.md`; M23 implementation status only in roadmap.

- [ ] Native API-37 captures of actual library/filter/search/actions, canonical Standard/Chess960 and M22 branch opening/history, favorite/protect and process restoration. Representative data uses existing canonical repositories; clearly label fixtures, and include real Play/Arena saves where practical.
- [ ] Use Neo only with stored/resolved `private.chesscom.ejgfv` proof. Do not change private inputs. Capture only relevant new surfaces.
- [ ] Measure exact board bounds before/during/after/restored navigation in the same viewer lane; require zero delta.
- [ ] Run focused tests first then one final cumulative JVM/native/lint/debug assembly gate. Preserve opt-in skipped capture distinction. Inspect actual public APK privacy; no release tasks.
- [ ] Obtain independent code review and supported CodeRabbit review; address concrete issues test-first.
- [ ] Commit/push M23, monitor automatic branch CI, record results. Keep main at approved M22.
- [ ] Create compact local review ZIP with start/final SHA, scope/changes, test results, native evidence, bounds, privacy and per-file SHA256 manifest; reopen and verify every member. Stop for manual approval.
