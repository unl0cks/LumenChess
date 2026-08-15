# ADR 0004 — Engine moves require independent core validation

**Status:** Accepted  
**Date:** 2026-08-15

## Context
Native/UCI engine output is external input. A malformed, stale, buggy, or desynchronized engine move must never corrupt authoritative game state.

## Decision
Every engine-returned move is parsed into a candidate and independently checked against the current `core-chess` legal move set before the runtime accepts it. Engine adapters cannot bypass this gate.

## Consequences
- Illegal/stale engine output becomes an engine/runtime error, not a corrupted game.
- Search IDs/position revisions remain necessary later, but they supplement rather than replace legality validation.
- Any bug discovered in this validation path must gain a regression test with the fix.
