# P5 — Reference fidelity and visual identity checkpoint

This document is the durable checkpoint record for P5. The checkpoint SHA is the
`checkpoint(P5): close reference fidelity gate candidate` commit containing this file.

## Boundary

- Approved pre-P5 `main` baseline: `de2fe5e7c3d4096527a4e24602f68144c4b2d67c`.
- Candidate branch: `p5-reference-fidelity-visual-identity`.
- P5 is limited to the approved reference-fidelity, visual-identity, customization,
  reference-QA, and closure work on that branch.
- Promotion to `main`, a final release APK, and M20 remain separate explicit approval
  boundaries.

## Approved presentation state

The user-approved reachable presentation state includes:

- Play / App Overview;
- New Game, including REST and PRESSED states;
- sparse board-first Live Game;
- Settings and Play Settings;
- Appearance and all four Board & Pieces tabs;
- Sounds & Haptics and feedback-event detail;
- Arena, Engine Arena unavailable, Games, and Insights preview shells.

The approved vocabulary retains a near-black page, graphite neutral surfaces,
localized cyan identity, opaque selected faces, continuous shape-aware depth, and
isotropic custom glyphs. Detached finite highlights, rectangular compositing
boundaries, gratuitous glow, and default Live analysis clutter remain prohibited.

## Architecture retained

- `core-chess` remains the legality authority.
- Runtime owns authoritative game state, clocks, premoves, pause, and game endings.
- Engine output is validated before application; engines do not mutate runtime state.
- `LumenChessboard` remains presentation/input only and preserves the P1 stable square
  board bounds.
- DataStore/Room persistence and customization behavior remain outside visual
  authority.
- Sounds and haptics remain presentation-side effects with the P4 validation and
  private-storage boundaries intact.

## Reference and closure evidence

- The canonical `.github/workflows/p5-reference.yml` lane owns permanent primary and
  derivative screenshot QA; superseded one-off calibration workflows are not part of
  this checkpoint.
- P5 reference run `32721726738` passed after the closure lint fix on commit
  `561d490e313b45e492a9c1d3bd440a0c49583758`.
- Its New Game REST, approved-comparison, and PRESSED PNG SHA-256 values were
  byte-identical to the approved pre-fix artifacts, proving the shared-slider lint
  correction caused no rendered-pixel change.
- The closure cleanup removes only unreferenced diagnostic workflows and an
  unreferenced superseded presentation file; canonical reference carriers,
  provenance QA, permanent tests, and approved production routes remain.

## Gate P5 contract

The exact checkpoint SHA must pass the durable `Gate P5` workflow without weakened
checks. The cumulative gate verifies:

- core chess, runtime, engine API/host, persistence, and app unit tests;
- app, engine-host, and persistence lint, debug assemblies, and Android-test
  assemblies;
- Stockfish 18 and Reckless 0.9.0 for `arm64-v8a` and `x86_64`;
- 16 KiB native ELF `PT_LOAD` alignment;
- committed Room schemas 1 and 2 and a clean schema worktree;
- Android 17/API-37 engine-host, persistence, and app instrumentation;
- uploaded instrumentation evidence.

Passing Gate P5 makes this branch ready for a separate promotion decision. It does
not itself authorize promotion, release packaging, or M20.
