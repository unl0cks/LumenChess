# M7 SAN, PGN, and Game Tree Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add canonical SAN generation/parsing, an immutable in-memory variation tree, and robust PGN import/export for Standard and Chess960 without introducing a second legality engine.

**Architecture:** `San` resolves notation exclusively through `MoveGenerator.legalMoves(position)`. `GameTree` is immutable and stores validated position snapshots plus parent/ordered-child IDs so branching returns a new tree. PGN uses a tokenizer plus stateful parser/writer; RAVs branch from the parent position of the immediately preceding move. Chess960 PGN uses `Variant "Chess960"`, `SetUp "1"`, and canonical M6 Shredder-FEN.

**Tech Stack:** Pure Kotlin/JVM, JUnit 5, existing `core-chess` legality/FEN APIs.

## Global Constraints

- M7 only; no Room, Android UI, Compose, engines, Review, online APIs, or Library implementation.
- Preserve approved Gate A and M6 behavior from `19cb20ee994e5c482627cd0db959952d9f7cb8ec`.
- Legality remains owned exclusively by `MoveGenerator`.
- Every production behavior starts with a failing test.
- Standard SAN castling is `O-O`/`O-O-O`; Chess960 classification uses `MoveGenerator.castlingSide(position, move)`.
- Standard initial games omit unnecessary `SetUp`/`FEN`; Chess960 exports always carry `Variant`, `SetUp`, and canonical FEN.
- Malformed or illegal PGN fails atomically with contextual diagnostics.

---

### Task 1: SAN generation and parsing

**Files:**
- Create: `core-chess/src/main/kotlin/dev/lumenchess/core/chess/San.kt`
- Create: `core-chess/src/test/kotlin/dev/lumenchess/core/chess/SanTest.kt`

**Interfaces:**
- Produces: `San.generate(position: Position, move: Move): String`
- Produces: `San.parse(position: Position, text: String): Move`
- Produces notation exceptions distinguishing invalid, illegal, and ambiguous SAN.

- [ ] Write failing SAN tests covering ordinary moves/captures, check/mate, promotion, en passant, all disambiguation forms, pinned pseudo-competitors, Standard castling, all required Chess960 castling shapes, illegal/ambiguous SAN, and generate→parse identity.
- [ ] Run `:core-chess:test` and confirm RED is caused by missing SAN implementation.
- [ ] Implement canonical SAN generation using legal-move competition for minimal disambiguation and position-aware castling classification.
- [ ] Implement SAN parsing by matching parsed SAN constraints against `MoveGenerator.legalMoves(position)`; tolerate `0-0`/`0-0-0` input while canonical output uses `O`.
- [ ] Run `:core-chess:test` and confirm GREEN.

### Task 2: Immutable canonical game tree

**Files:**
- Create: `core-chess/src/main/kotlin/dev/lumenchess/core/chess/GameTree.kt`
- Create: `core-chess/src/test/kotlin/dev/lumenchess/core/chess/GameTreeTest.kt`

**Interfaces:**
- `GameTree` stores root/start position, headers, result, immutable node map, ordered children, and next deterministic node ID.
- Each node stores parent ID, move, canonical SAN, resulting immutable `Position`, leading/trailing comments, NAGs, and extensible annotations.
- `withVariation(parentId, move, ...)` returns a new tree plus created node ID; first child is mainline.

- [ ] Write failing tests for root metadata, mainline and sibling/nested variations, parent/child navigation, position reconstruction, immutable branching, comments/NAG structure, and correct branch position.
- [ ] Run `:core-chess:test` and confirm RED.
- [ ] Implement the immutable tree and validated variation insertion through `MoveGenerator.applyLegalMove`/`San.generate`.
- [ ] Run `:core-chess:test` and confirm GREEN.

### Task 3: PGN tokenizer and diagnostics

**Files:**
- Create: `core-chess/src/main/kotlin/dev/lumenchess/core/chess/PgnTokenizer.kt`
- Create: `core-chess/src/test/kotlin/dev/lumenchess/core/chess/PgnTokenizerTest.kt`

**Interfaces:**
- Token stream includes tags, SAN/movetext symbols, move-number punctuation, comments, NAGs, parentheses, results, and source offsets.
- `PgnParseException` includes source index/token/ply where available.

- [ ] Write failing tests for escaped tag values, brace and semicolon comments, NAGs, parentheses, results, malformed tags, unterminated strings/comments, and useful offsets.
- [ ] Run `:core-chess:test` and confirm RED.
- [ ] Implement deterministic scanner/tokenizer without a monolithic regex.
- [ ] Run `:core-chess:test` and confirm GREEN.

### Task 4: PGN parser into the game tree

**Files:**
- Create: `core-chess/src/main/kotlin/dev/lumenchess/core/chess/Pgn.kt`
- Create: `core-chess/src/test/kotlin/dev/lumenchess/core/chess/PgnTest.kt`

**Interfaces:**
- `Pgn.parseGame(text: String): GameTree`
- `Pgn.parseGames(text: String): List<GameTree>`
- Parsing resolves every SAN token with `San.parse` from the exact branch position.

- [ ] Write failing tests for Standard/custom-FEN/Chess960 games, results, comments, symbolic and numeric annotations, sibling/nested RAVs, black-to-move `...`, illegal moves, invalid FEN, unclosed variations, contradictory results, and illegal variation branch moves.
- [ ] Run `:core-chess:test` and confirm RED.
- [ ] Implement header normalization/start-position selection, symbolic-NAG mapping, stateful movetext parsing, and RAV stack semantics that branch from the position before the immediately preceding move.
- [ ] Ensure any parse error aborts without returning a partial game.
- [ ] Run `:core-chess:test` and confirm GREEN.

### Task 5: Deterministic PGN serialization and semantic round trips

**Files:**
- Modify: `core-chess/src/main/kotlin/dev/lumenchess/core/chess/Pgn.kt`
- Modify: `core-chess/src/test/kotlin/dev/lumenchess/core/chess/PgnTest.kt`

**Interfaces:**
- `Pgn.serialize(game: GameTree): String`

- [ ] Write failing round-trip tests for Standard, FEN-started, Chess960, and nested variation trees.
- [ ] Run `:core-chess:test` and confirm RED.
- [ ] Implement deterministic header ordering/escaping, move-number emission, ordered variation writing, comments/NAGs, and result termination.
- [ ] Canonicalize Standard setup tags away only for the ordinary Standard start; always emit `Variant "Chess960"`, `SetUp "1"`, and M6 canonical FEN for Chess960.
- [ ] Run `:core-chess:test` and confirm GREEN.

### Task 6: Architecture documentation and full regression gate

**Files:**
- Create: `docs/adr/0008-san-pgn-and-game-tree.md`
- Delete before landing: `docs/superpowers/plans/2026-08-15-m7-san-pgn-game-tree.md`

- [ ] Document the immutable node-ID tree, structured comments/NAGs, SAN legality resolution, strict/atomic PGN errors, RAV branch semantics, and Chess960 PGN convention.
- [ ] Run the complete CI command: `./gradlew :core-chess:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest`.
- [ ] Run `./gradlew :app:connectedDebugAndroidTest` on the existing Android 17 CI emulator.
- [ ] Compare M7 branch against approved M6 and ensure only M7 core/tests/ADR remain.
- [ ] Fast-forward `main` only after the branch is green, then verify a fresh GitHub Actions run on the final `main` SHA.
