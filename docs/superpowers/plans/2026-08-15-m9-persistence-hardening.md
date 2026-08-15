# M9 Persistence Hardening Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Harden LumenChess persistence with conservative deduplication, canonical semantic fingerprints, idempotent provenance operations, disposable-heavy-data retention, and a verified Room v1→v2 migration while preserving all canonical game data.

**Architecture:** Keep `core-chess` unchanged and implement M9 entirely in `data-persistence` plus persistence documentation/CI. Room v2 adds only the indexed/constraint-backed fields and participant identity mapping required by M9; semantic game identity remains conservative, with fingerprints treated as candidate signals rather than automatic merge keys. Higher-level repository/services own transactional dedup/merge/retention intent so callers never reproduce races through raw DAO sequences.

**Tech Stack:** Kotlin 2.3.21, Android/AGP 9.3.0, Room3 3.0.1, KSP 2.3.10, AndroidX SQLite 2.7.0, JUnit/Android instrumentation on API 37.

## Global Constraints

- Start from approved M8 `a238f16948176164965773b7ff2643dc2cc4d84c` and preserve Gate A, M6, M7, and M8 behavior.
- Do not modify `core-chess` to support persistence convenience.
- Do not implement M10 or later functionality, networking, sync, UI, engines, Review analysis, rating calculations, or schedulers.
- Prefer false-negative deduplication over false-positive merging; semantic fingerprint equality alone must never auto-merge independent games.
- Never use destructive migration fallback; retain the generated v1 schema and add an explicit tested v1→v2 migration.
- Strong external game identity is source scoped and database constrained.
- Canonical games, trees, comments, variations, NAGs, annotations, headers, source identity, saved positions, and rating history are durable; only heavyweight analysis payloads are disposable in M9.

---

### Task 1: Room v2 identity/fingerprint schema and migration RED→GREEN

**Files:**
- Modify: `data-persistence/src/main/kotlin/dev/lumenchess/data/persistence/Entities.kt`
- Modify: `data-persistence/src/main/kotlin/dev/lumenchess/data/persistence/LumenDatabase.kt`
- Create: `data-persistence/src/main/kotlin/dev/lumenchess/data/persistence/Migrations.kt`
- Modify: `data-persistence/src/main/kotlin/dev/lumenchess/data/persistence/Daos.kt`
- Create/Test: `data-persistence/src/androidTest/kotlin/dev/lumenchess/data/persistence/Migration1To2Test.kt`

**Produces:** Room v2 with nullable indexed `games.contentFingerprint`, normalized non-null `game_sources.sourceAccountScope`, a unique `(sourceType, sourceAccountScope, externalGameId)` strong-identity index, and `participant_external_identities` keyed by `(sourceType, sourceAccountScope, externalParticipantId)`.

- [ ] Write migration tests that create/populate a real v1 database with Standard/Chess960 games, variations, comments/NAGs/annotations, participants, sources/metadata, saved positions, reviews/heavy rows, and rating events.
- [ ] Verify tests fail before v2/migration exists.
- [ ] Bump database to version 2, add minimal schema fields/table/indices, and register explicit `MIGRATION_1_2` on all production database builders.
- [ ] Migration preserves all v1 rows, normalizes `sourceAccountScope = COALESCE(sourceAccountId, '')`, leaves historical fingerprints nullable for application backfill, and creates the new uniqueness/index structures transactionally.
- [ ] Run migration tests and all M8 instrumentation tests.

### Task 2: Canonical semantic fingerprint RED→GREEN

**Files:**
- Create: `data-persistence/src/main/kotlin/dev/lumenchess/data/persistence/GameContentFingerprint.kt`
- Create/Test: `data-persistence/src/test/kotlin/dev/lumenchess/data/persistence/GameContentFingerprintTest.kt`
- Modify: `data-persistence/src/main/kotlin/dev/lumenchess/data/persistence/GamePersistenceRepository.kt`
- Modify: `data-persistence/src/main/kotlin/dev/lumenchess/data/persistence/Daos.kt`

**Produces:** `GameContentFingerprint.compute(tree): String` using SHA-256 over an unambiguous versioned byte encoding of canonical variant, canonical starting FEN, and ordered mainline raw move tuples (`from`, `to`, promotion). Result/headers/SAN/comments/timestamps/UUIDs/source data are excluded. Fingerprint lookup is indexed but non-unique.

- [ ] Add unit tests for deterministic restart behavior, PGN whitespace/SAN-presentation independence, Standard/Chess960/custom-FEN equivalence, and different move/start/variant divergence.
- [ ] Confirm RED.
- [ ] Implement versioned length-delimited canonical encoding + SHA-256 hex.
- [ ] Persist fingerprints for all new games and expose bounded candidate lookup/backfill for migrated null fingerprints.
- [ ] Test that two separately saved identical semantic games retain distinct canonical UUIDs despite matching fingerprints.

### Task 3: Strong source identity and idempotent provenance APIs RED→GREEN

**Files:**
- Modify: `data-persistence/src/main/kotlin/dev/lumenchess/data/persistence/PersistenceModels.kt`
- Modify: `data-persistence/src/main/kotlin/dev/lumenchess/data/persistence/Daos.kt`
- Modify: `data-persistence/src/main/kotlin/dev/lumenchess/data/persistence/GamePersistenceRepository.kt`
- Create/Test: `data-persistence/src/androidTest/kotlin/dev/lumenchess/data/persistence/DeduplicationPersistenceTest.kt`

**Produces:** intent-level APIs such as `persistExternalGame`, `attachVerifiedSource`, `refreshSourceMetadata`, and fingerprint candidate lookup. Strong identity is exactly `(sourceType, normalized sourceAccountScope, externalGameId)` and is enforced by Room/SQLite, not only query-before-insert.

- [ ] Add RED tests for repeated same source/external ID, same external ID in unrelated namespaces, canonical UUID stability, metadata idempotency, and constraint-backed duplicate attempts.
- [ ] Implement atomic source resolution; repeated strong identity returns the original game ID and does not create another canonical game.
- [ ] If incoming content conflicts under the same strong identity, fail explicitly without rewriting the canonical tree or user annotations.
- [ ] `attachVerifiedSource` preserves the target game UUID; a source already mapped to a different game fails instead of silently merging games.
- [ ] Source metadata refresh is non-destructive: incoming keys upsert, absent keys remain, user game-tree metadata is untouched.
- [ ] Add rollback test proving a failed provenance operation leaves the prior state intact.

### Task 4: Conservative participant external identity RED→GREEN

**Files:**
- Modify: `data-persistence/src/main/kotlin/dev/lumenchess/data/persistence/PersistenceModels.kt`
- Modify: `data-persistence/src/main/kotlin/dev/lumenchess/data/persistence/Daos.kt`
- Modify: `data-persistence/src/main/kotlin/dev/lumenchess/data/persistence/GamePersistenceRepository.kt`
- Create/Test: `data-persistence/src/androidTest/kotlin/dev/lumenchess/data/persistence/ParticipantIdentityTest.kt`

**Produces:** optional source-scoped external participant identities that can intentionally reuse a participant UUID; display names alone never reconcile participants.

- [ ] Add tests showing same-name unrelated players remain distinct and local/engine participants never merge with imported online players by name.
- [ ] Add test showing repeated resolution of the same explicit external identity reuses one participant UUID.
- [ ] Implement atomic participant identity resolution through `participant_external_identities`; identity conflicts fail rather than reassigning an identity.
- [ ] Verify deleting game/source data leaves unrelated participant identities/participants intact according to FK ownership.

### Task 5: Heavy-analysis retention classes and pruning RED→GREEN

**Files:**
- Create: `data-persistence/src/main/kotlin/dev/lumenchess/data/persistence/PersistenceRetention.kt`
- Modify: `data-persistence/src/main/kotlin/dev/lumenchess/data/persistence/Daos.kt`
- Create/Test: `data-persistence/src/androidTest/kotlin/dev/lumenchess/data/persistence/RetentionPersistenceTest.kt`

**Produces:** explicit retention classification metadata and deterministic `pruneHeavyAnalysis` logic supporting age and/or max-count pruning over `review_heavy_analysis` only.

- [ ] Add tests classifying current tables as canonical durable, lightweight durable, or heavyweight disposable.
- [ ] Add RED tests for age/count pruning and deterministic oldest-first tie-breaking.
- [ ] Implement indexed/bounded heavy-row selection and transactional deletion.
- [ ] Verify games, trees, variations, comments, NAGs, annotations, headers, sources, lightweight reviews/review plies, saved positions, and rating events survive pruning.
- [ ] Add forced-failure rollback test proving retention cleanup cannot partially commit.

### Task 6: Integrity/recovery/open hardening RED→GREEN

**Files:**
- Modify: `data-persistence/src/main/kotlin/dev/lumenchess/data/persistence/GamePersistenceRepository.kt`
- Modify: `data-persistence/src/main/kotlin/dev/lumenchess/data/persistence/LumenDatabase.kt`
- Create/Test: `data-persistence/src/androidTest/kotlin/dev/lumenchess/data/persistence/PersistenceRecoveryTest.kt`

**Produces:** controlled failure on malformed enum/source/fingerprint/tree data, reopen consistency, and no destructive database opening path.

- [ ] Add tests for malformed source enum/account scope/fingerprint, orphan prevention by FK, and existing disconnected/cyclic/sibling-order protections.
- [ ] Add reopen-after-success and reopen-after-failed-transaction tests.
- [ ] Validate stored non-null fingerprint against reconstructed tree; mismatch raises `PersistenceMappingException`, while migrated null fingerprint is allowed until explicit backfill.
- [ ] Ensure database builders register migrations and never call destructive fallback.

### Task 7: Documentation, generated schema, CI gate, and final promotion

**Files:**
- Modify: `docs/adr/0009-canonical-room-persistence.md` or create focused M9 ADR if clearer
- Modify: `.github/workflows/android.yml`
- Generate/check in: `data-persistence/schemas/dev.lumenchess.data.persistence.LumenDatabase/2.json`
- Preserve unchanged: `data-persistence/schemas/dev.lumenchess.data.persistence.LumenDatabase/1.json`

**Produces:** durable documentation of strong-vs-candidate identity, fingerprint non-authority, retention classes, migration policy, and a CI gate that validates both schema history and migration/instrumentation tests.

- [ ] Document that fingerprint equality is never sufficient by itself to prove two separately recorded real-world games are identical.
- [ ] Generate the exact compiler-produced v2 schema; never hand-write it.
- [ ] Update schema freshness CI to require both v1 and v2 and fail on generated differences/untracked schema changes.
- [ ] Run full branch verification: Gate A/M6/M7/M8/M9 tests, `:core-chess:test`, data unit+instrumentation, lint/assemblies, app unit/lint/assemblies, API 37 connected tests.
- [ ] Remove this temporary implementation-plan file before final promotion if it is the only non-product planning artifact.
- [ ] Confirm `main` still equals approved M8 SHA, fast-forward to the fully verified M9 tree, and require a fresh green GitHub Actions run on the final `main` SHA.
- [ ] Report M9 and stop; do not begin M10.
