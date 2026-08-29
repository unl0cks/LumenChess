# P6 — Board, pieces, and motion polish checkpoint

This document records the durable manual-approval boundary reached on the isolated
`codex/p6-personal-piece-integration` branch. P6 remains unmerged and M20 remains
blocked.

## Boundary

- Approved pre-P6 `main`: `54ff48fcc05e97e61b7a4075806c8c512bc5a9b7`.
- Approved P5 application tree: `771d61627fdd6c5ed2e18119e27fd066f67b9843`.
- P6 working branch: `codex/p6-personal-piece-integration`.
- Approved P6.3 durable state:
  `3948a026e7165690832801c2d33caefbba22076b`.
- P6.3 approval record:
  `ed1bcdcd288b29c9106b1fa48f386e10bb41f658`.
- Manually approved P6.4 production state:
  `48ab1c4469c34b05c08b935ff160edf8f2e0175f`.
- The rejected custom-piece renderer beginning at `c21a2f6` is not part of the P6
  working lineage.

## Approved through P6.4

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

### Grounded Precision board motion

The manually approved presentation-only motion vocabulary is:

- 70 ms pickup using `CubicBezier(0.2, 0, 0, 1)`, scaling from 1.00 to
  1.04 with a 2 dp optical lift;
- immediate one-to-one drag tracking without positional smoothing;
- a piece-alpha-following black drag shadow at 20% opacity, 1.3 dp blur,
  and 1.5 dp vertical offset, with no rectangular graphics-layer shadow;
- 90 ms legal settle and 120 ms continuous illegal snap-back;
- 145 ms human tap travel, 155 ms engine travel, and 110 ms premove travel;
- a 55 ms captured-piece fade while the attacker uses normal travel;
- cancellation of stale transient presentation on a newer authoritative revision
  or board flip;
- immediate authoritative rendering with transient state cleared when Android
  animations are disabled;
- atomic final-state presentation for Standard and Chess960 castling until a
  separately approved dual-piece motion phase exists.

Runtime commits remain authoritative and occur independently of presentation
motion. The approved implementation preserves the final-square-flash correction.

Corrected native Neo evidence verified that `private.chesscom.ejgfv` remains the
resolved renderer through pickup, drag, legal release, and snap-back. The reviewed
package `LumenChess-P6.4-Neo-corrected-review-48ab1c44.zip` has SHA-256
`56aac1e4622e7da7732780e1219e573ff43ab4c03c7095000f64c16009ba38cb`;
all 19 manifest entries matched their declared sizes and digests.

## Architecture retained

- `core-chess` remains the legality authority.
- Runtime remains the sole owner of authoritative game state, engines, clocks, and
  premove semantics.
- `LumenChessboard` remains presentation/input only.
- Private/public piece rendering uses the shared board architecture.
- P1 board bounds, approved P5 composition, board colors, imported artwork, and
  navigation remain unchanged.

## Next boundary

P6.5 has not yet been defined by the durable repository roadmap or checkpoint
material. No P6.5 implementation is authorized until that scope is recorded.
This checkpoint does not authorize promotion, a signed APK, later P6 categories,
or M20.
