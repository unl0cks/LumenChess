# ADR 0012 — Engine API and UCI protocol boundary

**Status:** Accepted  
**Milestone:** M11 — Engine API and UCI protocol

## Context

LumenChess needs a stable engine-facing contract before Stockfish, Reckless, Android process isolation, and native wrappers are introduced. The engine boundary must not leak raw UCI text into UI/runtime code or let asynchronous engine output bypass the chess rules already established in `core-chess`.

M12 owns the isolated Android process/Binder/native transport spike. M13 and M14 own the concrete Stockfish and Reckless adapters. M11 therefore defines only a transport-neutral protocol and API layer.

## Decision

### Pure Kotlin module

`engine-api` is a pure JVM/Kotlin module that depends on `core-chess`. It contains no Android, Binder, AIDL, JNI, process-launching, or engine-binary code.

### Typed search identity

Every search request/result carries:

- an `EngineSearchId` identifying the search instance;
- a `PositionRevision` identifying the authoritative position revision the search was started from.

A result is stale if either value no longer matches the caller's expected search/revision. Staleness is checked before move acceptance.

### Engine moves are untrusted input

Engine best moves are external input. `EngineMoveValidator`:

1. rejects a mismatched search ID;
2. rejects a mismatched position revision;
3. parses the returned UCI token;
4. accepts it only when the exact `Move` is present in `MoveGenerator.legalMoves(position)`.

`core-chess` therefore remains the sole legality authority.

For Chess960, this preserves ADR 0007: castling uses the internal/UCI_Chess960 king-to-rook-origin representation. No visual c/g-file castling translation is performed by the engine API.

### UCI confinement

Raw UCI protocol lines are parsed/encoded only by the pure UCI protocol layer. UI code, `LumenChessboard`, persistence, and later authoritative runtime code consume typed engine data rather than parsing raw engine text.

The parser represents recognized handshake, option, `info`, score, and `bestmove` messages with typed models. Unknown engine chatter is represented explicitly rather than being silently mistaken for a known message; malformed known messages fail loudly.

### Session contract

`EngineSession` and `EngineSessionCommand` describe a transport-neutral session boundary. They do not prescribe callbacks, Binder transactions, processes, threads, JNI handles, or crash recovery. Those transport/lifecycle choices remain M12 work.

## Consequences

- M12 implementations must adapt their transport to this API rather than moving raw UCI parsing into app/UI code.
- M13/M14 adapters must attach the correct search ID and position revision to emitted results.
- Later runtime code must discard stale results and validate engine moves through `EngineMoveValidator`/`core-chess` before applying them.
- `LumenChessboard` remains position-in/legal-core-move-out and owns no engine session state.
- M8/M9 persistence identity, deduplication, migration, and retention behavior are unchanged.
- Real engine process isolation, Binder/AIDL/native wrappers, binary integration, clocks, runtime ownership, and premove execution are outside M11.
