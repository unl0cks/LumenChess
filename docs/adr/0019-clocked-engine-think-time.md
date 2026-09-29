# ADR 0019: Engines play on their own clock (think-time model) and games end by rule

- Status: Accepted
- Supersedes the M19 "small deterministic time-management policy" in `PlayRuntimeCoordinator`

## Context

Physical-device review found engine replies effectively instant. Humanized and Hybrid strength are
depth-limited (depth 4-10, small MultiPV) and finish in milliseconds, so the reply landed in the same
frame as the human move: the human move's animation was cancelled by the two-revision jump, the engine
consumed almost no clock, and it could never lose on time. Separately, the runtime never ended a game by
insufficient material, threefold repetition or the fifty-move rule although `core-chess` implements them.

## Decision

**Think time is a pure function.** `EngineThinkTimePolicy` (`game-runtime`) maps (runtime state, strength
settings, initial clock, search id) to an `EngineThinkPlan { searchMillis, minimumTotalMillis, stalled }`.
It depends on the time control and remaining clock, target strength (weaker opponents manage the clock
worse and vary more), game phase, forcing positions (only move, check, recapture) and seeded per-move
variance. The seed is recorded with the game, so replay is exact.

- `searchMillis` is the real wall-time budget passed to the engine (`go movetime`).
- Depth-limited models finish early, so the finished move is **held** until `minimumTotalMillis` has elapsed
  since the search started. Native Elo and full-strength searches really use their budget; full strength is
  not padded beyond a perceptual floor.
- Only Elo-limited opponents can freeze in time trouble (weak, low on clock), so they can lose on time.

**Ownership is unchanged.** The hold is presentation pacing in the app layer (`PlayRuntimeCoordinator`,
`ArenaRuntimeCoordinator`, view models). The runtime clock keeps running for the engine side for the whole
interval and remains the only owner of clocks, validation and move application. Pause/resign/timeout cancel
the pending search; a result that arrives afterwards, or for a superseded search, is refused by the runtime
(`STALE_ENGINE_RESULT` / `TERMINAL`). A late stale result can never cancel a valid pending release.

**Automatic draws live in the reducer** and consume `core-chess` (`Rules.isInsufficientMaterial`,
`repetitionKey`): insufficient material, threefold repetition and the fifty-move rule end the game
(checkmate on the same move still wins). A flag against an opponent who cannot possibly mate is a draw.
New terminals map to existing persisted terminations; no schema change.

**Strength seeds are per game.** New Play and Arena games draw a fresh non-zero seed (the default `0`
replayed identical Humanized/Hybrid choices every game); the resolved seed is persisted with the game.

## Consequences

- Engine replies take believable, clock-aware time; the human move animation completes before the reply.
- Fixed calibration constants (tempo, caps, stall probability) live in `EngineThinkTimePolicy` and are
  covered by property-style tests; they are tunable without touching runtime ownership.
- Only Fischer increment exists (the design specification defines no delay mode); there is no separate
  "delay" clock behaviour to model.
- Arena engines are paced the same way, so an Arena game plays at the pace of its time control.
