# ADR 0010 — Persistence identity, deduplication, retention, and migration hardening

**Status:** Accepted  
**Date:** 2026-08-15

## Context

ADR 0009 established the canonical Room persistence model, stable surrogate UUIDs, structural game-tree storage, and the separation between canonical game data and heavyweight review payloads. M9 must make repeated imports/sync operations safe without collapsing distinct real-world games that merely happen to contain the same chess moves.

Chess files and future external services expose multiple kinds of identity with very different reliability. An external game ID is meaningful only inside its source/account namespace. Player display names are labels, not identities. A semantic move fingerprint can show that two records contain the same chess content, but two genuinely separate games can still have identical positions and moves.

M9 also introduces the first Room schema migration and therefore establishes the long-term migration discipline for user-owned databases.

## Decision

### Strong game identity

A strongly identified external game uses the tuple:

`(sourceType, sourceAccountScope, externalGameId)`

`sourceAccountScope` is the normalized persisted form of `sourceAccountId`, using the empty string when no account scope is available. Room v2 enforces this tuple with a unique index. Repeating the exact identity is idempotent and resolves to the already-existing canonical game UUID.

A source identity that is already attached to another canonical game is never silently reassigned. An explicit verified-provenance operation may attach a new, previously unused source identity to an existing canonical game while preserving that game's UUID.

### Canonical semantic fingerprint

Every newly persisted game stores a versioned semantic fingerprint in `games.contentFingerprint`. Version 1 is formatted as:

`gcf1:<64 lowercase SHA-256 hex characters>`

The digest input is an unambiguous binary encoding of:

1. fingerprint format/domain version;
2. exact chess `Variant`;
3. canonical starting FEN from the chess core;
4. ordered mainline move tuples containing from-square, to-square, and promotion.

The fingerprint deliberately excludes SAN, PGN whitespace, headers, comments, variations, NAGs, annotations, result metadata, timestamps, source URLs/metadata, and all database/domain UUIDs.

**A matching content fingerprint is not sufficient by itself to prove that two independently recorded real-world games are the same game.** Fingerprint lookup is therefore indexed but non-unique. It is a candidate/corroboration signal only and never auto-merges independently saved games.

Historical v1 rows migrate with a null fingerprint because SQL migration code must not invent chess semantics independently of `core-chess`. An explicit repository backfill reconstructs each game through the legality-backed domain mapper and stores the resulting fingerprint transactionally.

### Cross-source policy

M9 does not perform speculative cross-source auto-merging. Shared chess content, similar headers, matching display names, or a matching result are insufficient evidence on their own. Cross-source records remain separate unless a caller later provides verified provenance and explicitly attaches the additional source to an existing canonical UUID.

This deliberately prefers false-negative duplicates over false-positive merges. Later sync milestones may add source-specific corroboration rules without changing the canonical identity model.

### Source refresh and user data

Refreshing an already-known strong source identity updates only provenance-owned fields. Incoming source metadata keys are upserted; absent previously stored keys remain. Import/sync timestamps are merged deterministically (earliest import, latest sync), and a supplied source URL may refresh the source record.

A source refresh does not rewrite the canonical tree, PGN headers, comments, branches, NAGs, annotations, participant associations, review summaries, or rating history. If the same strong source identity arrives with a different semantic chess fingerprint, persistence fails with a conflict rather than replacing user-owned canonical data.

### Participant identity

Display names never cause participant reconciliation. Local humans and engines are never merged with imported players merely because names match.

Where a later source can provide a stable external participant ID, Room v2 stores the strong participant identity tuple:

`(sourceType, sourceAccountScope, externalParticipantId)`

in `participant_external_identities`. Resolving the same tuple reuses the same participant UUID; different source/account namespaces remain distinct. Participant reconciliation beyond explicit strong external identities is deferred to later source/sync work.

### Retention classes

Current persistence tables have explicit retention classes rather than relying on naming conventions:

**Canonical durable**
- `participants`
- `participant_external_identities`
- `games`
- `game_headers`
- `game_nodes`
- `game_node_comments`
- `game_node_nags`
- `game_node_annotations`
- `game_sources`
- `game_source_metadata`
- `saved_positions`
- `rating_events`

**Lightweight durable**
- `reviews`
- `review_plies`

**Heavyweight disposable**
- `review_heavy_analysis`

M9 cleanup operates only on heavyweight disposable rows. Age/count selection is deterministic and bounded, and deletion is transactional. Cleanup never deletes the canonical game, game tree, user variations, comments, NAGs, annotations, PGN headers, source identities, saved positions, rating history, lightweight review records, or review-ply summaries.

No Android cleanup scheduler is introduced in M9; scheduling belongs to a later milestone.

### Room v2 migration policy

Room database version 2 adds:

- nullable indexed `games.contentFingerprint`;
- normalized non-null `game_sources.sourceAccountScope`;
- unique strong game-source identity index;
- `participant_external_identities`;
- deterministic heavyweight-retention ordering index.

The v1 generated schema remains permanently checked in. The exact compiler-generated v2 schema is also checked in. `MIGRATION_1_2` is explicitly registered by the production file-backed database builder; no destructive migration fallback exists.

The migration preserves v1 canonical rows and normalizes source account scope. If existing v1 rows contain ambiguous duplicate source identities that cannot satisfy the new strong-identity constraint, migration fails instead of guessing which canonical game should win. The existing database is not deliberately destroyed or rewritten around that ambiguity, leaving future recovery/export tooling possible.

Future schema versions must continue to provide explicit migrations and populated migration tests from committed historical schemas.

### Integrity and interrupted writes

Canonical saves, strong-identity resolution, provenance attachment/refresh, fingerprint backfill, and heavyweight pruning use Room transactions at their logical operation boundaries. A failure exposes either the prior complete state or the new complete state, never an application-authored half-merge.

Load continues to reconstruct trees through the chess core. In v2 it additionally validates stored fingerprint syntax/equality and normalized source-account scope. Ambiguous corruption is rejected with persistence errors rather than silently repaired.

## Consequences

- Canonical game UUIDs remain stable when verified provenance is added.
- Database uniqueness protects strong source identities against application races.
- Two separate games with identical moves remain separate unless stronger evidence explicitly connects them.
- Strong participant identities can be reused without introducing name-based identity mistakes.
- Candidate detection does not require loading every game tree because fingerprints are indexed.
- Retention can reclaim expensive analysis data without risking durable user chess history.
- Migration failure is conservative where legacy data is ambiguous; no destructive fallback masks the problem.
- Later M10+ work can build on these guarantees without redefining M8's canonical game/tree storage.
