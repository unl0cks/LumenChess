# ADR 0001 — Prefer two isolated engine processes

**Status:** Accepted  
**Date:** 2026-08-15

## Context
LumenChess requires engine-v-engine Arena, crash isolation, deterministic cancellation, and protection of the game/UI process from native engine failures.

## Decision
Prefer two isolated Android engine processes (logical Engine Slot A and Engine Slot B). Higher layers communicate only through a typed engine boundary. M12 is explicitly allowed to determine the best exact Binder/AIDL/native-wrapper implementation after an integration spike.

## Consequences
- Arena can host two independent engines.
- Native crashes should be containable to an engine process where Android permits.
- The UI/game runtime never directly controls native engine internals.
- We accept Binder/process lifecycle complexity in exchange for isolation.
- This ADR does not choose the exact native ABI/wrapper shape; M12 owns that decision.
