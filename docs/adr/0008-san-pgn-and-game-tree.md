# ADR 0008: SAN, PGN, and canonical game tree

## Status

Accepted for M7.

## Context

M7 needs one notation and game-history model that can support PGN import/export now and later map cleanly to analysis, branching, review navigation, and persistence. Chess legality must remain owned by the existing pure Kotlin core rather than being reimplemented inside SAN or PGN.

PGN Recursive Annotation Variations (RAVs) are alternatives to the move that immediately precedes the `(` token. Parsing therefore has to branch from the parent position of that preceding move, not from the position after it.

Chess960 additionally needs notation that is independent of Standard king travel and can reconstruct exact castling rights. M6 already defines position-aware castling classification and canonical Shredder-FEN castling fields.

## Decision

### SAN

- `San.generate(position, move)` accepts a legal move and derives canonical SAN from the position.
- `San.parse(position, text)` resolves notation only against `MoveGenerator.legalMoves(position)`.
- SAN disambiguation considers only legal competing moves, so pinned pseudo-competitors do not add false file/rank qualifiers.
- Castling is classified through `MoveGenerator.castlingSide(position, move)`, including Chess960 king-start-to-rook-start moves. SAN output remains `O-O` / `O-O-O`; input also tolerates `0-0` / `0-0-0`.

### Game tree

- `GameTree` is immutable.
- Nodes have deterministic IDs, a parent ID, ordered child IDs, the move from the parent, canonical SAN, and the exact resulting immutable `Position` snapshot.
- The first child is the mainline; later children are sibling variations.
- Branch insertion validates the move through the existing legality APIs and returns a new tree, so creating a variation cannot mutate the original mainline by aliasing.
- Comments, NAGs, and extensible structured annotations are stored separately from SAN.

### PGN

- PGN import uses a character tokenizer followed by a stateful parser rather than a monolithic regular expression.
- Every movetext SAN token is resolved through `San.parse` at the exact current branch position.
- Entering an RAV branches from the parent of the immediately preceding move, matching PGN RAV semantics.
- Parse failures are atomic: malformed tags/comments/variations, invalid FEN, contradictory results, ambiguous SAN, or illegal moves throw `PgnParseException` with source index and token/ply context where available. No partial `GameTree` is returned.
- Serialization is deterministic and preserves ordered variations, structured comments/NAGs, headers, result, and nonstandard start positions semantically.

### Starting positions and Chess960

- Ordinary Standard games beginning from the normal initial position do not emit redundant `SetUp` or `FEN` tags.
- Nonstandard Standard starts emit `SetUp "1"` and canonical `FEN`.
- Chess960 serialization uses `Variant "Chess960"`, `SetUp "1"`, and M6 canonical Shredder-FEN. Import accepts `Chess960` and the common `Fischer Random` variant spelling, then normalizes serialization to `Chess960`.
- This preserves the exact Chess960 starting layout, side to move, clocks, en-passant state, and rook-file castling rights without inventing variant-specific movetext.

## Consequences

Later Room persistence should map this semantic model rather than replacing it with a flat move list. Persistence-specific entities, UI navigation state, engine evaluations, and Review semantics remain outside M7.
