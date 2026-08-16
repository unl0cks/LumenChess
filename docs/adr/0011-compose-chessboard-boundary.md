# ADR 0011: Reusable Compose Chessboard Boundary

- Status: Accepted for M10
- Date: 2026-08-16

## Context

M10 introduces the reusable Jetpack Compose chessboard used by later Play, Analysis, Arena, and review surfaces. M6 and M7 already established `core-chess` as the legality and canonical move authority, including Chess960 castling represented with UCI_Chess960 king-to-rook-origin moves. M8 and M9 separately established canonical persistence, identity, deduplication, transaction, and retention guarantees.

The board therefore needs rich interaction without becoming a second game controller, a second legality implementation, or an alternate persistence owner.

## Decision

### The board is position-in / legal-move-out

`LumenChessboard` receives an authoritative `core-chess` `Position` and emits a `core-chess` `Move` through its callback.

The board may own only ephemeral presentation state such as:

- selected square;
- active drag gesture;
- pending promotion choice;
- visual highlights and arrows supplied by its caller.

It does not apply moves to create authoritative game state, persist games, merge identities, update clocks, run engines, or own a game session.

### `core-chess` remains the legality authority

Every move emitted by the board must come from `MoveGenerator.legalMoves(position)`. Tap, drag, promotion, and castling input all resolve against that list. The UI must not synthesize a move and then assume it is legal.

### Chess960 castling input is translated conservatively

The core encoding from ADR 0007 remains unchanged: a Chess960 castling move is represented as king-to-rook-origin.

For human board input:

1. an exact legal core destination always wins;
2. only when there is no exact legal move may the UI translate the conventional king destination (`g`-file kingside, `c`-file queenside) to a legal core castling move;
3. the rook-origin target remains accepted, which is required for ambiguous positions and for positions where the king already occupies its final castling square.

This prevents the board from incorrectly choosing castling when the same visual square is also a legal ordinary king move.

### Promotion policy chooses among legal promotion candidates

`ALWAYS_ASK` shows a promotion choice and emits only the selected legal promotion candidate. `AUTO_QUEEN` selects the legal queen candidate when available. Neither policy constructs a new move outside the legal list.

### Overlays are presentation data

Selected-square, legal-move, capture, last-move, check, premove-style, custom-square, and arrow overlays are visual state. They do not change chess legality.

M10 provides premove-style highlighting only. Authoritative premove queueing, execution, cancellation, and clock effects remain deferred to M18.

### Accessibility is part of the reusable component

Squares expose useful piece/square descriptions and interaction state. Tap interaction is available through Compose semantics when enabled. The broader accessibility and adaptive-layout audit remains M47.

## Consequences

- M17 may compose the board into an authoritative game runtime, but must keep runtime state outside the board.
- M18 may add premove behavior by supplying/consuming board presentation and move events; it must not move legality into UI code.
- M11 and later engine work may provide arrows/evaluations as presentation inputs; engines do not become board legality authorities.
- M41 may provide configurable board/piece themes using the board's presentation API without changing its interaction contract.
- M8/M9 persistence identity, deduplication, UUID stability, transaction, migration, and retention behavior are unaffected by M10.
