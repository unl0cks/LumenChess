# ADR 0007 — Chess960 core representation and interoperability

**Status:** Accepted  
**Date:** 2026-08-15

## Context
M6 adds Chess960 legality to the pure Kotlin `core-chess` model. Chess960 cannot be represented correctly by four castling booleans alone because castling rights identify a specific rook origin, and legal castling may involve arbitrary king/rook starts, king-only movement, rook-only movement, or king/rook transposition.

FIDE Chess960 rules keep the final castled arrangement fixed: on the g-side the king finishes on the g-file and rook on the f-file; on the c-side the king finishes on the c-file and rook on the d-file. Path occupancy and the normal king attacked-square restrictions still apply.

Chess960 FEN has multiple conventions in common use. Stockfish explicitly accepts normal FEN, Shredder-FEN file-letter castling rights, and X-FEN. Its Chess960 FEN serializer emits Shredder-FEN, because file letters preserve the exact rook associated with each castling right without ambiguity when promoted/additional rooks exist.

Stockfish also represents a Chess960 castling move for UCI_Chess960 as king-start to castling-rook-start. In non-Chess960 UCI output the same logical castling move is rendered as king-start to the normal c/g king destination.

## Decision
- `CastlingRights` stores the exact rook-origin `Square?` for each color and side instead of storing only four booleans.
- Compatibility boolean properties and the existing boolean constructor remain available so Standard chess callers are not needlessly broken.
- A castling side is defined relative to the king and the registered rook origin; the registered kingside rook must be to the king's right and the queenside rook to its left.
- Castling always finishes with king/rook on `g/f` or `c/d`, regardless of their starting squares.
- Castling occupancy is checked across the union of the king start-to-final path and rook start-to-final path, allowing only the castling king and castling rook to occupy their own starting squares. This handles king-only, rook-only, transposition, destination-overlap, and path-overlap cases without special-case board hacks.
- The king may not castle while in check, through an attacked transit square, or onto an attacked final square. The final post-castling position is also checked so moving the rook cannot expose a line attack on a stationary/final king.
- Standard chess keeps its existing move encoding (`e1g1`, `e1c1`, etc.).
- Chess960 castling moves use UCI_Chess960 encoding: king-start to the registered castling-rook-start. `MoveGenerator.castlingSide(position, move)` provides context-aware classification without adding engine-specific code.
- `Fen.parse(fen, Variant.CHESS960)` accepts both:
  - Shredder-FEN rook-file rights (`A`–`H` / `a`–`h`), which identify the exact rook; and
  - X-FEN `KQkq`, resolved from the board by scanning from the corresponding edge toward the king and selecting the first rook on that side, matching Stockfish behavior.
- Chess960 serialization is canonicalized to Shredder-FEN file letters. Standard FEN serialization remains `KQkq`.
- FEN castling-right validation remains strict in LumenChess: a declared right must resolve to a real same-color rook on the home rank with the king on its home rank. We do not silently sanitize malformed rights.
- `Position.repetitionKey` includes the exact rook origin for every castling right and includes the variant, because both affect legal move state.
- `Chess960.startingPosition(index)` supports all 960 arrangements using conventional Scharnagl numbering (`0..959`); index 518 is the orthodox back rank. Generation enforces opposite-colored bishops and the king between rooks, with Black mirrored by file.
- The immutable core uses `MoveTransition` for make/unmake verification. `make` produces a new `Position`; `unmake` restores the exact immutable pre-move position rather than reverse-mutating individual fields.

## Reference sources
- FIDE Laws of Chess, Chess960 appendix: https://handbook.fide.com/chapter/e012023
- Stockfish `src/position.cpp` (exact castling rook origins; Normal/Shredder/X-FEN parsing; Shredder-FEN serialization; Chess960 castling legality): https://github.com/official-stockfish/Stockfish/blob/master/src/position.cpp
- Stockfish `src/uci.cpp` (UCI_Chess960 castling representation): https://github.com/official-stockfish/Stockfish/blob/master/src/uci.cpp
- Stockfish Chess960 reference perft fixtures: https://github.com/official-stockfish/Stockfish/blob/master/tests/perft.sh

## Consequences
- Standard chess remains behaviorally compatible while the state model can now distinguish multiple possible rook origins on the same castling side.
- M7 can classify castling from position context without introducing SAN/PGN logic into M6.
- Later Stockfish/Reckless integration can translate Chess960 castling directly from the core move representation without UI-specific castling exceptions.
- Persisted/repetition state must preserve exact castling rook origins, not merely `KQkq` booleans.
- Chess960 FEN output is deterministic even when input used X-FEN shorthand.
