# ADR 0014: Stockfish 18 Android integration

- Status: Accepted
- Milestone: M13
- Date: 2026-08-17

## Context

ADR 0012 makes the pure `engine-api` transport-neutral and ADR 0013 places engine execution in two non-exported isolated Android service processes. M13 must attach the exact Stockfish 18 release without moving raw UCI, native state, or authoritative chess state across those boundaries.

Stockfish 18 is the upstream `sf_18` release at commit `cb3d4ee9b47d0c5aae855b12379378ea1439675c` and is licensed GPL-3.0-or-later. Its default NNUE networks are `nn-c288c895ea92.nnue` and `nn-37f18f62d772.nnue`.

## Decision

1. Build the exact pinned Stockfish source as the shared library `liblumen_stockfish18.so`; do not extract or execute a writable engine executable.
2. The build fetches only the exact upstream commit. The two release NNUE networks are embedded into that library and validated using Stockfish's own filename convention: the filename contains the first 12 hexadecimal digits of the file SHA-256.
3. A minimal JNI wrapper reproduces the upstream `main.cpp` initialization (`Bitboards`, `Position`, `UCIEngine`, `Tune`) and runs the upstream UCI loop against process-local pipes.
4. Kotlin `Stockfish18UciBackend` owns those pipes inside the isolated host. Raw UCI never crosses Binder. M12 `HostSession` remains the lifecycle/cancellation/correlation layer and M11 `EngineMoveValidator` remains the authority before any result can be accepted.
5. Android builds produce `arm64-v8a` for the initial device target and baseline `x86_64` for API 37 emulator verification. The linker is explicitly configured for 16 KiB load-segment alignment.
6. Stockfish capabilities are exposed as typed metadata: Standard and Chess960, MultiPV up to 256, ponder support, and native UCI Elo limiting from 1320 through 3190.
7. M13 does not resolve APK/AAB distribution obligations. ADR 0003 continues to block public distribution until the project's complete GPL/source/notice obligations are intentionally satisfied.

## Consequences

- Engine crashes remain isolated from the app and from the other engine slot.
- No M11 Binder-neutral contract changes are required.
- Stockfish UCI_Chess960 continues to use the existing king-start-to-rook-origin representation, so no UI or engine-specific castling exception is introduced.
- The native build has a network dependency for the exact pinned source and NNUE assets; runtime play remains fully offline.
