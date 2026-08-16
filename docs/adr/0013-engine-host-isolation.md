# ADR 0013 — Isolated Android engine-host transport

**Status:** Accepted for M12  
**Date:** 2026-08-16  
**Milestone:** M12 — Isolated engine-host spike

## Context

ADR 0001 prefers two isolated engine processes so an engine crash cannot take down the app or the other Arena engine. ADR 0012 established a pure, transport-neutral `engine-api` and deliberately left Binder/AIDL/native lifecycle to M12. The transport must preserve `EngineSessionId`, `EngineSearchId`, and `PositionRevision` without turning Android parcelables or raw UCI text into application-domain contracts.

UCI itself carries no LumenChess search identity. In particular, an engine may still emit `bestmove` after `stop`. Starting a newer UCI search before the old search reaches that terminal output would make it possible to accidentally label an old `bestmove` with a new application search ID.

## Decision

- Add an Android `engine-host` library that depends only on `engine-api` and `core-chess`. It has no dependency on `app`, persistence, UI, or future runtime modules and therefore cannot mutate authoritative game state.
- Logical Engine Slot A and Slot B are separate non-exported services declared with distinct process names and `android:isolatedProcess="true"`.
- Binder transport uses AIDL primitives at the Android edge. Session identity, search ID, position revision, FEN, variant, search limits, and typed failure/result fields cross Binder. Android parcelable types do not enter `engine-api`.
- Raw UCI lines never cross Binder. The isolated host owns the UCI backend, command encoding, and `UciProtocolParser`; callers receive only typed M11 results/failures.
- Each isolated slot owns at most one engine session at a time. Session identity is supplied by the caller and must be returned unchanged by the host.
- Every service instance has a host-generation token. App-side callbacks must match both the expected session identity and host generation. Process death/rebind therefore cannot make an old callback look like output from the replacement host.
- The app-side session tracks submitted search IDs and revisions. A callback that does not correspond to an in-flight `(searchId, positionRevision)` is rejected as stale transport output before it can reach a future runtime.
- UCI searches are serialized. After `stop`, the active search remains the protocol owner until its terminal `bestmove` is consumed and discarded; only then may a queued replacement search begin. Late output is never relabelled with the replacement search identity.
- Engine-host output is still untrusted. A correctly correlated `EngineSearchResult` must subsequently pass `EngineMoveValidator` / `core-chess` before any future runtime may apply it.
- Host shutdown closes the session/backend and unbinds deterministically. Binder death is surfaced to the caller; reconnecting creates a new host generation.
- M12 includes debug-only mock UCI backends for normal, malformed-output, and deliberate-process-crash tests. Release builds reject those mock engine IDs. Real Stockfish/Reckless backends remain M13/M14.

## Verification

M12 instrumentation must prove:

- Slot A and Slot B execute in different isolated PIDs, both different from the test/app process;
- simultaneous searches preserve session/search/revision identity;
- the returned mock move still requires and passes the existing core legality validator;
- cancel followed by deliberately late `bestmove` cannot produce a stale callback or relabel it as the next search;
- malformed known UCI becomes a typed protocol failure, not a move;
- killing one engine process leaves the caller and other slot alive;
- the killed slot can be rebound and used with a new host generation.

## Consequences

- M13 and M14 must implement their native/UCI backends behind this host boundary rather than bypassing it.
- Native crashes are expected to kill at most the isolated slot process; Binder death/recovery is the application-facing failure mode.
- M17 remains the first authoritative game-state owner. The engine host can report candidate moves but has no API for applying them.
- If a future engine cannot conform to serialized lifecycle/correlation without weakening stale-output guarantees, that is an architecture conflict rather than a reason to bypass this ADR.
