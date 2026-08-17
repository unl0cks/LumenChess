# ADR 0018: Single runtime-owned premove

- Status: Accepted
- Milestone: M18

## Context

The Compose board can visualize premove intent, but the M17 runtime is the only authoritative owner that can decide whether a queued action becomes a move. A premove cannot be validated against the position in which the user queued it because the opponent's next authoritative move can change occupancy, checks, captures, castling rights, promotions, or Chess960 castling legality.

## Decision

M18 supports exactly one queued premove. The queue lives only in `RuntimeState`; it is ephemeral user intent and is deliberately excluded from crash/restore snapshots.

Queueing a premove:

- does not modify `Position` or `GameTree`;
- does not perform a legality check;
- does not charge clock time;
- records the human side, requested `Move`, and current `PositionRevision`;
- replaces the previous single queued premove rather than creating a multi-premove queue.

After the opponent/engine move is authoritatively applied, the runtime:

1. creates the resulting authoritative `Position` through `core-chess`;
2. verifies the queued action belongs to the new side to move and was queued at the immediately preceding `PositionRevision`;
3. obtains legal moves from `MoveGenerator.legalMoves` for that resulting position;
4. executes the queued `Move` only if that exact move is legal there;
5. otherwise discards it immediately so it cannot fire on an unrelated later position.

Standard and Chess960 castling therefore use the same established `core-chess` move representation as normal board/engine input, including Chess960 king-to-rook-square castling encoding.

## Clock semantics

The committed default premove cost is 100 ms.

- The 100 ms charge occurs exactly once and only when the queued premove is legal and about to execute.
- A discarded/cancelled/invalidated premove costs zero.
- If the queued side has 100 ms or less remaining, the charge may cause timeout; timeout becomes authoritative before the premove is added to the game tree.
- If the premove executes, the ordinary turn-switch/increment transition then runs exactly once through the M16 clock state machine.

## Invalidation

The queue is cleared when:

- it is executed or found illegal in the resulting position;
- the user cancels it;
- a controller assignment changes;
- the runtime pauses;
- the game becomes terminal;
- the process restores from persistence.

Engine-host death by itself does not change the authoritative position, so it does not need to destroy a queued premove. Any eventual replacement search still has to pass the existing M17 search/revision gate before changing the position, after which the premove is validated normally.

## Verification

Permanent tests cover:

- legal execution after an opponent move;
- queueing a move that is not legal in the current position;
- source/destination and occupancy changes;
- captures appearing/disappearing;
- promotion;
- Standard castling;
- Chess960 castling representation;
- terminal game and timeout before execution;
- explicit cancellation and controller invalidation;
- engine completion while queued;
- exactly-once execution and exactly-once 100 ms charge;
- zero charge for discarded queues;
- premove-cost timeout;
- stale queue isolation from later positions.

During M18 verification, three initial test fixtures were discovered to place the moving side in check, making their setup moves illegal. The production implementation was left unchanged; the fixtures were corrected and retained as legal chess positions. This regression reinforces that premove tests themselves must respect the same `core-chess` legality boundary they are intended to verify.