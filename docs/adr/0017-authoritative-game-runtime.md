# ADR 0017: Single authoritative serialized game runtime

- Status: Accepted
- Milestone: M17

## Context

Play and Arena need one authoritative owner for live chess state. The existing boundaries already establish that `core-chess` owns legality, `LumenChessboard` is position-in / legal-move-out presentation, engine results are untrusted and correlated by `EngineSearchId` + `PositionRevision`, isolated engine services own engine execution rather than game state, and Room owns durable canonical storage rather than live orchestration.

Without one serialized runtime owner, human input, clock expiry, engine completion, host restart, persistence callbacks, pause/resume, and later premove behavior could race and independently mutate the live position or game tree. Coroutine scheduling order is not a valid correctness mechanism for chess state.

## Decision

### Authoritative owner

`game-runtime` owns the live authoritative `Position`, `GameTree` cursor, deterministic clock state, controller assignments, terminal state, pause state, existing `PositionRevision`, current engine-search correlation, engine-host availability, and runtime-event deduplication.

`GameRuntime.dispatch(event)` is the only live mutation entry point. Dispatch is serialized under one owner lock. The reducer is pure with respect to external systems: it returns a new immutable runtime state and typed effects. UI, Binder/native engine services, and persistence receive no mutation path into `RuntimeState`.

Effects may execute asynchronously, but can affect the game only by returning a new typed runtime event through `dispatch`.

### Event-time ordering

Each serialized dispatch samples the injected monotonic time source exactly once. Every clock transition performed while reducing that event observes that frozen sample. This means timeout-vs-move and pause/switch accounting are deterministic and do not depend on instruction timing, UI frames, wall-clock changes, or coroutine scheduling.

At an event boundary, elapsed clock time is settled before the event may alter the position. Therefore a side whose clock has already reached zero cannot rescue the game with a late move event.

### Chess and revisions

`core-chess` remains the only legality authority. Human moves are matched against `MoveGenerator.legalMoves`; accepted moves enter the canonical immutable `GameTree` through `GameTree.addMove`. Checkmate and stalemate use `Rules.termination`.

M11/M12 `PositionRevision` is reused directly rather than introducing a competing revision counter. Every authoritative position change increments it exactly once.

An engine result is accepted only when all of the following remain true:

- the runtime is non-terminal and not paused;
- the side to move is still engine-controlled;
- an engine search is currently pending;
- `EngineSearchId` matches that pending search;
- `PositionRevision` matches that pending search;
- the returned move is independently accepted by the existing `EngineMoveValidator` / `core-chess` boundary.

Cancellation/restart at the same position revision allocates a new monotonically increasing `EngineSearchId`, so late output from an older host/search remains stale even without a position change.

### Engines and host lifecycle

Starting, cancelling, and restarting engine work are typed runtime effects. Engine-host death clears only in-flight correlation and host availability; it cannot mutate `Position` or `GameTree`. Recovery creates fresh search correlation if an engine still owns the turn. Late output from a dead or replaced host cannot become authoritative.

### Persistence and restore

Persistence is a typed `PersistSnapshot` effect, never a second runtime owner. Runtime snapshots deliberately exclude in-flight engine search state. Restoring a snapshot pauses the clock, clears pending search correlation, and marks the host unavailable until the process observes a real host-recovered/connected event. Restoration therefore cannot manufacture a move from partial search state or accidentally accept output correlated to a previous process lifetime.

### Terminal state

Timeout, resignation, draw agreement, checkmate, and stalemate become authoritative terminal runtime state and update the canonical `GameTree` result. Terminal transitions stop clocks, clear/cancel pending engine work, and are idempotent: later move, engine, or terminal events cannot alter the authoritative game.

## Consequences

- Compose remains a projection/intent layer rather than a game-state owner.
- Engines and Binder/native services remain effect executors with untrusted outputs.
- Persistence remains durable storage rather than a live controller.
- Clock ownership is singular and deterministic.
- Future M18 premoves must be implemented as runtime events/state and validated only after the preceding authoritative move changes the position.
- Future Play/Arena coordinators may run effects concurrently, but ordering becomes authoritative only when their result events enter the serialized runtime owner.

## Verification

Permanent M17 verification covers deterministic scenarios for:

- human move vs engine completion ordering;
- stale and legal-but-stale engine results;
- illegal engine results;
- cancel/restart vs late old output at the same revision;
- timeout vs move arrival;
- pause, resume, and engine completion while paused;
- duplicate engine completion and duplicate user events;
- controller changes;
- engine-host death and recovery;
- restore/background boundary without in-flight search restoration;
- exactly-once authoritative legal move application;
- resignation, draw, timeout, and other terminal idempotency;
- deterministic replay of an explicitly ordered event sequence.

M18 may not begin until the exact M17 checkpoint commit passes `:game-runtime:test` together with the preserved core/engine checkpoint regressions.