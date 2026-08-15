# LumenChess M0–M5 Gate A Snapshot

**Snapshot date:** 2026-08-15  
**Scope:** M0 through M5 only. M6 / Chess960 legality has **not** been started.

## Implemented milestones

### M0 — Implementation baseline
- Approved implementation plan recorded in `docs/implementation/IMPLEMENTATION-PLAN.md`.
- ADR structure created in `docs/adr/`.
- Exact dependency/source/license ledger recorded in `docs/implementation/DEPENDENCY-SOURCES.md`.
- Two isolated engine processes recorded as the preferred architecture; M12 is explicitly responsible for selecting the exact Binder/AIDL/native-wrapper implementation.
- Regression-test policy recorded: every discovered chess/runtime/engine bug receives a regression test with its fix.
- Licensing is a mandatory gate before public distribution.
- Every future engine-returned move must be independently validated by `core-chess` before runtime acceptance.

### M1 — Android project scaffold
- Kotlin/Jetpack Compose Android project scaffold.
- Modules: `app` and pure-JVM `core-chess`.
- API 37 compile/target baseline.
- JDK 17 / NDK 28.2.13676358 build baseline.
- Version catalog and CI workflow included.
- Gradle 9.6.0 bootstrap files are included in this packaged tree.

### M2 — Foundational design system shell
- Dark UI foundation with blue default accent.
- Five-tab shell: Play / Arena / Games / Insights / Settings.
- Foundational interactive components target at least 48dp and expose accessibility semantics.
- Compose accessibility instrumentation test is included.

### M3 — Core chess data model
- Pure Kotlin chess types and position state.
- Deterministic repetition keys.
- Position state defensively copies caller-owned board storage.

### M4 — Standard FEN / position construction
- Six-field Standard FEN parsing and serialization.
- Position validation and malformed-FEN rejection.

### M5 — Standard chess legality
- Legal move generation and king-safety filtering.
- Castling, en passant, promotion.
- Check, checkmate and stalemate.
- Threefold/fivefold repetition handling.
- 50/75-move rules and insufficient material.
- Illegal move rejection through the authoritative `core-chess` API.
- Standard perft suite.

## Gate A local verification

The pure Kotlin Gate A runner was compiled directly with `kotlinc` and executed with `java` against the packaged source tree.

Result: **14/14 Gate A core test groups passed.**

```text
PASS testSquareRoundTrip
PASS testPositionDefensivelyCopiesBoard
PASS testInitialFenRoundTripAndKey
PASS testMalformedFenRejected
PASS testStartPositionPerft
PASS testKiwipetePerft
PASS testAdditionalStandardPerftSuite
PASS testEnPassantThatExposesKingIsIllegal
PASS testCastlingThroughAttackIsIllegal
PASS testPromotionGeneratesFourChoices
PASS testCheckmateAndStalemate
PASS testDrawRules
PASS testThreefoldAndFivefoldRepetition
PASS testApplyLegalMoveRejectsIllegalMove
Gate A core tests: 14/14 passed
```

### Perft results

| Position | d1 | d2 | d3 | d4 | d5 |
|---|---:|---:|---:|---:|---:|
| Initial position | 20 | 400 | 8,902 | 197,281 | 4,865,609 |
| Kiwipete | 48 | 2,039 | 97,862 | 4,085,603 | — |
| Position 3 | 14 | 191 | 2,812 | 43,238 | — |
| Position 5 | 44 | 1,486 | 62,379 | 2,103,487 | — |
| Position 6 | 46 | 2,079 | 89,890 | 3,894,594 | — |

## Regression discovered during M3–M5

`Position` initially retained caller-owned mutable board storage. This allowed external code to mutate a position that was intended to be immutable. A regression test (`testPositionDefensivelyCopiesBoard`) was observed failing before the defensive-copy fix and passes with the packaged implementation.

## Known unverified Android checks

The local execution environment used for M0–M5 did not have a usable Android SDK/Gradle installation, so these checks remain unverified and must be run on a proper Android toolchain or CI before Gate A is considered fully complete:

- `./gradlew :core-chess:test`
- `./gradlew :app:lintDebug`
- `./gradlew :app:assembleDebug`
- currently applicable Compose/instrumented accessibility tests
- `.github/workflows/android.yml` end-to-end GitHub Actions run

The CI workflow is present but has never successfully run from this local snapshot because it was never pushed to the remote repository.

## Exact mandatory engine pins

| Engine | Ref | Exact upstream commit | License |
|---|---|---|---|
| Stockfish | `sf_18` | `cb3d4ee9b47d0c5aae855b12379378ea1439675c` | GPL-3.0-or-later |
| Reckless | `v0.9.0` | `0e92358f5acd66e5ac77b1bf558202e47c515435` | AGPL-3.0 |

No engine source or native binary is included yet; engine integration begins later at M13/M14.

## Deviations / packaging notes

1. **Android verification is still outstanding.** M3–M5 are locally verified through the pure Kotlin runner, but M1/M2 Android build/lint/instrumentation checks have not been executed.
2. **Build baseline research was updated before implementation** to AGP 9.4.0, Gradle 9.6.0, Kotlin/Compose plugin 2.3.21, API 37, JDK 17 and NDK 28.2.13676358. This did not change product behavior.
3. **The original local M0–M5 tree did not contain Gradle wrapper files.** To make this ZIP self-contained enough to continue development, packaging adds `gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.properties`, and a small self-contained Java bootstrap JAR pinned to the Gradle 9.6.0 distribution. Its source is included under `gradle/wrapper/bootstrap-src/`. This is a packaging/scaffold completion only; no M3–M5 chess production code was changed.
4. **Existing public design/research Markdown was restored exactly from the remote repository.** Git blob SHA checks matched the remote source for README, project handoff, full design spec, decisions, UI flows, research notes and reference manifest.
5. Proprietary/private Chess.com screenshots and scraped sound archives are intentionally not included. The generated blue UI concept PNG referenced by the manifest was not present in the local M0–M5 working tree and is not required for compiling or testing Gate A.
6. The repository's original README/project-handoff text still says production implementation had not started; it is preserved verbatim as historical source documentation. This `SNAPSHOT-INFO.md` is the authoritative status note for this package.

## Integrity

**Packaged payload tree SHA-256:** `35eeb565f8811ed6a21b10b78b4b6b6eb44f31bc1d196bad9a172a633e04946f`

The payload-tree hash is SHA-256 over a sorted manifest of `<file SHA-256>  <relative path>` for every file in the package **except this `SNAPSHOT-INFO.md`**.

### Final ZIP SHA-256

A ZIP cannot truthfully contain its own final SHA-256 value inside itself: inserting that value changes the ZIP bytes and therefore changes the hash. The final archive SHA-256 is therefore supplied alongside the ZIP in `LumenChess_M0-M5_GateA.zip.sha256` and in the download response.
