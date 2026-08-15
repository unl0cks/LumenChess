# ADR 0008 — SAN, PGN, and canonical game-tree representation

**Status:** Accepted  
**Date:** 2026-08-15

## Context
M7 adds notation and branching game semantics to the pure Kotlin `core-chess` layer. SAN and PGN are textual interchange formats, not alternate chess rule engines: accepting notation must never bypass the legality model established by M5/M6. Analysis, review navigation, future "Save as Variation", and later persistence also require a real variation tree rather than a flat move list.

PGN Recursive Annotation Variations (RAVs) are especially easy to model incorrectly. A variation after a move starts from the position before that immediately preceding move, because it represents an alternative to that move. Chess960 adds another interoperability constraint: SAN still writes castling as `O-O`/`O-O-O`, while the M6 move representation may be king-start to rook-start and the exact starting arrangement/castling rook rights must survive PGN round trips.

## Decision
- SAN generation takes `Position + legal Move`. It validates legality through `MoveGenerator` and uses `MoveGenerator.castlingSide(position, move)` for castling classification; raw destination squares or king travel distance are never used to decide castling SAN.
- SAN parsing first parses notation constraints, then resolves them exclusively against `MoveGenerator.legalMoves(position)`. Zero candidates are illegal; multiple candidates are ambiguous. There is no second legality implementation in the notation layer.
- SAN disambiguation considers only legal competing moves. Pinned pseudo-legal competitors therefore do not create false ambiguity.
- Canonical SAN output uses `O-O`/`O-O-O`; input also tolerates the common `0-0`/`0-0-0` forms.
- The canonical in-memory game is an immutable ordered tree keyed by deterministic `GameNodeId`s:
  - the root stores the exact start position;
  - every non-root node stores its parent, move, canonical SAN, and exact resulting `Position` snapshot;
  - child index 0 is the mainline;
  - later children are sibling variations in stable order;
  - adding or annotating a branch returns a new `GameTree`, so callers cannot accidentally mutate an existing mainline.
- Comments and NAGs are structured node metadata rather than text embedded in SAN. A generic annotation map is retained for extensibility without introducing Game Review semantics.
- PGN import is split into a source-indexed tokenizer and a stateful parser. The tokenizer handles tag strings/escapes, comments, NAGs, punctuation, symbols, and result tokens. The parser owns grammar/state and delegates every chess move to `San.parse`.
- PGN parsing is strict and atomic: malformed syntax, invalid FEN, illegal/ambiguous SAN, contradictory results, or malformed/unclosed variations throw `PgnParseException` with source index and token/ply context where available. No partial `GameTree` is returned.
- PGN RAV parsing explicitly branches from the parent of the immediately preceding move (the pre-move position), then restores the outer parser cursor after the variation closes.
- PGN serialization is deterministic. It preserves headers semantically, structured comments, NAGs, ordered/nested variations, result, start position, and variant; whitespace is canonicalized rather than byte-preserved.
- Ordinary Standard games beginning at `Position.initial()` do not emit redundant `SetUp`/`FEN` tags. Standard games from another position emit `SetUp "1"` and `FEN`.
- Chess960 PGN canonical output uses:
  - `Variant "Chess960"`;
  - `SetUp "1"`; and
  - `FEN` serialized by the M6 Chess960 FEN implementation (canonical Shredder-FEN rook-file castling rights).
  Chess960 import also accepts common Fischer Random variant-name aliases, but output normalizes to `Chess960`.
- Semicolon comments are accepted on import. Export uses deterministic brace comments; comment text remains ordinary structured text, including unknown conventions such as `[%eval ...]`/`[%clk ...]`, without assigning Review-specific meaning in M7.

## Reference sources
- PGN Complete specification / standard PGN grammar and RAV semantics: https://www.saremba.de/chessgml/standards/pgn/pgn-complete.htm
- python-chess PGN/SAN implementation (legality-backed SAN resolution and established PGN handling): https://github.com/niklasf/python-chess
- Lichess/scalachess PGN fixtures and variant handling: https://github.com/lichess-org/scalachess
- Stockfish Chess960 conventions referenced by ADR 0007: https://github.com/official-stockfish/Stockfish

## Consequences
- M8 persistence can map stable tree/node chess semantics without redefining what a variation means, although database identity/storage strategy remains an M8 decision.
- Analysis and Review can navigate exact positions directly from nodes while still reconstructing/validating them through parent history when needed.
- Import cannot silently "repair" illegal games by skipping moves; callers receive an explicit diagnostic and can decide how to surface it.
- Chess960 PGN remains reconstructible without relying on Standard king/rook starting squares or interpreting SAN destination geometry.
- Engine annotations can later be parsed into richer typed metadata without changing SAN, move legality, or game-tree topology.
