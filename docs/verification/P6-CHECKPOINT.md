# P6 — Board, pieces, and motion polish checkpoint

This document records the durable manual-approval boundary reached on the isolated
`codex/p6-personal-piece-integration` branch. P6 remains unmerged and M20 remains
blocked.

## Boundary

- Approved pre-P6 `main`: `54ff48fcc05e97e61b7a4075806c8c512bc5a9b7`.
- Approved P5 application tree: `771d61627fdd6c5ed2e18119e27fd066f67b9843`.
- P6 working branch: `codex/p6-personal-piece-integration`.
- Approved P6.3 durable state before this record:
  `3948a026e7165690832801c2d33caefbba22076b`.
- The rejected custom-piece renderer beginning at `c21a2f6` is not part of the P6
  working lineage.

## Approved through P6.3

### Personal piece integration

- Personal builds discover and package sanitized local-only Chess.com piece styles
  through the existing configurable personal-assets boundary.
- All 39 complete locally supplied styles are available without changing the public
  Lumen default.
- Board Preview and Live resolve the same selected `PieceSet` identity.
- The selected private style persists, falls back safely while private assets are
  unavailable, and restores when they return.
- Private PNGs and generated private assets remain ignored and untracked; public
  builds package only project-owned styles.

### Quiet Classical Hybrid board feedback

The manually approved static feedback vocabulary is:

- cyan corner brackets for selection;
- adaptive light/dark center dots for legal empty destinations;
- continuous adaptive circular rings for legal captures;
- restrained warm washes for last-move origin and destination;
- a coral inset frame for check;
- vertical slate rails for premove origin;
- horizontal slate rails for premove destination;
- short vertical slate rails at 67% opacity for the first premove tap.

The approved semantic draw order is:

`square → history → premove → selection → legal/capture → piece → check → arrows → promotion`

Native API-37 evidence confirmed Neo, 3D Staunton, and Public Lumen compatibility,
phone-scale readability, combined-state behavior, P1-stable board bounds, and the
absence of internal rectangular compositing artifacts, seams, malformed circles,
detached endpoints, clipping, or piece-alpha contamination.

## Architecture retained

- `core-chess` remains the legality authority.
- Runtime remains the sole owner of authoritative game state, engines, clocks, and
  premove semantics.
- `LumenChessboard` remains presentation/input only.
- Private/public piece rendering uses the shared board architecture.
- P1 board bounds, approved P5 composition, board colors, imported artwork, and
  navigation remain unchanged.

## Next boundary

P6.4 may audit and design presentation-only board/piece motion. New production
motion requires a separate manual design approval. This checkpoint does not
authorize promotion, a signed APK, later P6 categories, or M20.
