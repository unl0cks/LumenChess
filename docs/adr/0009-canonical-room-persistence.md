# ADR 0009 — Canonical Room persistence model

**Status:** Accepted  
**Date:** 2026-08-15

## Context
M7 established `GameTree` as the canonical in-memory game representation and `GameNodeId` as deterministic domain identity. That identity is intentionally local to a reconstructed tree and is not suitable as database identity. LumenChess also needs one source-neutral persistence model that can later hold local games, imported PGNs, Engine Arena games, externally sourced games, branches, reviews, saved positions, and rating history without creating separate core game tables per source.

The chess/rules layer must remain pure Kotlin. Room is an Android persistence concern and must not become a dependency of `core-chess`.

## Decision
- Persistence lives in one Android library module, `data-persistence`, with dependency direction `app -> data-persistence -> core-chess`. Room/KSP/Android types do not enter `core-chess`.
- Room database version 1 is `LumenDatabase`. Schema export is enabled and generated schemas are checked into `data-persistence/schemas/`. No destructive-migration fallback is configured; every future version change is expected to add an explicit migration plus schema/migration verification.
- Persistent identity is separate from domain identity. Games, nodes, participants, sources, reviews, saved positions, and rating events use app-generated UUID surrogate keys stored as text. These IDs are stable across process death/restarts, do not depend on names or list positions, and can remain stable when M9 later introduces merge/dedup rules. Reconstructed M7 `GameNodeId` values are never used as Room primary keys.
- `games` is the one canonical game table regardless of source. It stores variant, exact starting FEN, result/termination, timestamps, rated state, time-control metadata, and participant associations. Arbitrary PGN headers remain normalized rows in `game_headers`, preserving unknown tags and deterministic order instead of adding a column for every tag.
- The persisted game tree is structural rather than an opaque PGN blob. `game_nodes` stores persistent node ID, game ID, persistent parent ID, sibling order, explicit move encoding (`from`, `to`, promotion), and canonical SAN as a consistency/diagnostic value. Root state lives on `games` as the exact start FEN; there is no synthetic persisted root move row.
- Resulting `Position` snapshots are not duplicated per ply in schema v1. Load reconstructs positions from start FEN plus stored moves through the existing legality-backed `GameTree.addMove` path. This keeps chess legality in `core-chess` and avoids duplicating board state for every node.
- Parent foreign keys include `gameId`, so a node cannot legally attach to a parent from another game. `siblingOrder` is explicit and constrained within a parent, preserving mainline/variation order and arbitrary nested RAV structure.
- Root, leading, and trailing comments are stored separately from SAN. NAGs and extensible M7 annotations are normalized node metadata. Annotation values remain opaque strings at this boundary; M8 does not reinterpret comments or annotations as future Game Review semantics.
- Participants are source-neutral records. A game may reference local humans, other humans, engines, imported/external players, or unknown participants. M8 creates participant records per save request and deliberately performs no name-based/global identity merge.
- Provenance is separate from the canonical game. A game may have multiple `game_sources`; typed source fields cover source type, external ID/reference, account identity placeholder, and import/sync timestamps. Extensible source-specific key/value metadata is normalized in `game_source_metadata` rather than replacing canonical typed fields with a generic JSON document. M8 defines storage only; synchronization and deduplication are deferred.
- Review persistence is split into lightweight `reviews`/`review_plies` summary rows and separately deletable `review_heavy_analysis` payload rows. M8 provides storage shape only and does not define Accuracy, Game Rating, move classification algorithms, or engine-analysis behavior.
- Saved positions are independent of games and store variant, canonical FEN, title, notes, and timestamps.
- Rating persistence starts as appendable `rating_events` associated with a participant and optionally a game. The schema can preserve rating history/state inputs without implementing Elo/Glicko calculations in M8.
- Domain/database mapping is explicit. Room entities are not exposed as the canonical domain model. Load validates enum values, FEN, move encoding, parent connectivity, sibling order, cycles/disconnected nodes, chess legality, and stored SAN consistency; corrupt rows fail with `PersistenceMappingException` instead of manufacturing a `GameTree`.
- Canonical game save uses one Room write transaction; canonical game load uses one Room read transaction. A failed tree/source insert therefore rolls back the game row rather than leaving a partial graph.
- Game-owned rows cascade on game deletion: headers, nodes and node metadata, source associations/metadata, reviews/per-ply summaries, and heavy review data. Participant rows do not cascade when a game is deleted. Rating-event game references use `SET NULL`, preserving rating history when a related game disappears. Heavy review payloads can be deleted independently while retaining the game and review summary.
- Loading one game uses bounded table-level queries followed by in-memory reconstruction, rather than issuing a query per ply. Full Library paging/search optimization remains later work.

## Consequences
- Standard, Chess960, and arbitrary-FEN games share one database and reconstruct through the same M7 legality model.
- Chess960 castling remains unambiguous because the persisted move retains the exact M6 move representation, including king-start to rook-start cases; SAN is not the canonical move source.
- Unknown PGN headers, comments, NAGs, and M7 annotations survive persistence without coupling M8 to later Review or synchronization semantics.
- M9 can add deduplication, retention, and persistence-hardening rules without redefining the canonical game/tree schema or conflating database IDs with `GameNodeId`.
- A future schema version must use explicit Room migrations and migration tests; schema version 1 intentionally has no migration path to test yet.

## Reference sources
- AndroidX Room3 releases: https://developer.android.com/jetpack/androidx/releases/room3
- Room 2 to Room 3 migration guidance: https://developer.android.com/training/data-storage/room/migration-2-to-3
- Room schema export and database migration testing: https://developer.android.com/training/data-storage/room/migrating-db-versions
- AndroidX SQLite releases: https://developer.android.com/jetpack/androidx/releases/sqlite
- Kotlin Symbol Processing releases: https://github.com/google/ksp/releases
