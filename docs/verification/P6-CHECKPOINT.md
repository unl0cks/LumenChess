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

### P6.5 — Special-Move Motion & P6 Interaction Closure

P6.5 finishes the bounded presentation and motion edge cases deliberately left
outside the approved P6.4 Grounded Precision implementation. It must not change
chess legality, canonical game state, engine ownership, clocks, persistence
semantics, board geometry, approved board feedback, or imported-piece rendering.

P6.5 is split into two approval-gated phases:

- **P6.5A — design and technical checkpoint:** architecture audit, motion studies,
  native current-behavior evidence, simulated design references, renderer
  compatibility checks, and a manual approval boundary. Production special-move
  motion is unchanged during this phase.
- **P6.5B — bounded native implementation:** may begin only after explicit manual
  approval of the P6.5A design checkpoint.

The complete P6.5 scope is limited to:

1. Standard castling presentation.
2. Generalized Chess960 castling presentation, including zero-distance and
   crossing/overlap cases.
3. Promotion and capture-promotion presentation for supported promotion pieces,
   colors, and human/engine/premove paths.
4. En-passant presentation verification, with correction only if a concrete defect
   is proven during P6.5B.
5. Presentation cancellation on board flip, authoritative position replacement,
   a newer position revision, activity recreation where applicable, and disabled
   Android animations.
6. Compatibility across Neo, 3D Staunton, and Public Lumen.
7. P1 board-stability verification for each new presentation path.
8. Final P6 interaction and motion closure after manual approval.

P6.5 excludes new engine functionality, analysis, board or Live redesign, new
piece artwork, new feedback vocabulary, customization UI, sound or haptics work,
unrelated refactors, promotion-chooser redesign, a signed APK, promotion to
`main`, and M20.

### P6.5A design and technical checkpoint

P6.5A recovered from durable branch tip
`d961a65674164157600cb47a488a9d96b84e87c7`. Production code is unchanged by
this checkpoint. The local review set is stored outside Git at
`.tmp-p6-5a-design/`; its README distinguishes simulated design references from
native current-production evidence, and its manifest records artifact digests.

#### Castling architecture and design

Castling must be identified through the authoritative
`MoveGenerator.castlingSide(previous, move)` result, never inferred from a generic
two-piece diff. The presentation plan can then derive:

- king source from the move's source square;
- rook source from the previous position's castling-rights rook square;
- canonical king destination (`g` or `c` on the home rank);
- canonical rook destination (`f` or `d` on the home rank).

This covers Standard and Chess960 without moving ownership out of `core-chess`.
The king and rook travel concurrently with Grounded Precision easing. A leg whose
source equals its destination remains static; it does not receive a cosmetic
animation. Crossing and transposition cases use the same deterministic identities,
not coordinate guessing, and should draw the king above the rook during the brief
crossing interval. A newer authoritative revision cancels the entire pair as one
presentation object.

The timing study compared 145, 165, and 185 ms at board and phone scale. The
recommended duration is **165 ms**: 145 ms makes the two legs read too close to
independent snaps, while 185 ms is perceptibly slower than the approved restrained
motion language. All three studies use `CubicBezier(0.2, 0, 0, 1)`.

Required implementation cases are represented in the design evidence:

- Standard White/Black O-O and O-O-O;
- Chess960 ordinary dual travel;
- king-zero-distance and rook-zero-distance castling;
- king/rook transposition (`d1/c1` to `c1/d1`) and crossing geometry.

#### Promotion architecture and design

The runtime continues to commit the chosen promotion immediately. Presentation
should retain the moving pawn as the travel overlay for the normal human, engine,
or premove duration, hide the canonical destination piece during that travel, then
bridge to the already-authoritative promoted piece at the destination. The bridge
crossfades pawn alpha to zero and promoted-piece alpha to one while the promoted
piece scales only from 0.96 to 1.00. It contains no spin, bounce, glow, launch, or
layout movement. Capture-promotion retains the existing 55 ms victim fade on the
capture square before the same replacement bridge.

The comparison included immediate, 60 ms, and 80 ms replacement. Immediate reads
as a hard asset swap; 60 ms is barely distinguishable from a blink at phone scale;
**80 ms is recommended** because it communicates replacement without delaying the
board or competing with travel. The same bridge applies to Queen, Rook, Bishop,
and Knight, both colors, and supported human/engine/premove paths. The existing
promotion chooser remains topmost and unresolved selection produces no travel.

#### En-passant native audit

A temporary QA-only instrumentation extension captured the existing P6.4 path on
the canonical API-37 emulator (`1344×2992 @ 489 dpi`). The test position used
`e5d6` from `7k/8/8/3pP3/8/8/8/7K w - d6 0 1` and sampled 16, 32, 48, 64, 96,
and 160 ms. Native pixels confirm that the attacker follows normal 145 ms travel,
the victim fades on `d5` during the existing 55 ms window, the destination remains
ghost-free, and no stale pawn survives. No en-passant redesign or production fix
is recommended. The temporary test edit was removed after capture.

#### Cancellation and stability contract

Castling and promotion presentation must reuse the existing revision/orientation
identity. Board flip, a newer position revision, authoritative replacement, engine
supersession, navigation away, game end, or activity recreation clears the full
transient plan and redraws only the latest authoritative board. Disabled Android
animations skip travel, fades, and replacement bridges and immediately clear all
transient objects. Rapid consecutive transitions replace the previous plan rather
than queueing it.

All proposed motion remains paint/translation/alpha-only inside the existing board
container. P6.5B must re-prove the canonical P1 bounds before, during, and after
castling, promotion, en passant, interruption, and engine progression; no layout
animation or board remeasurement is permitted.

#### Remaining manual and technical boundary

P6.5A recommends one coherent system: concurrent 165 ms castling with deterministic
king/rook identities and static zero-distance legs; normal move travel followed by
an 80 ms promotion replacement bridge; existing en-passant presentation unchanged;
and revision-aware immediate cancellation. The principal implementation risk is
Chess960 overlap/transposition draw ordering and suppression of both canonical
destination pieces without a one-frame flash. A secondary risk is ensuring the
Chess960 last-move history projection describes canonical castling destinations
without changing the approved P6.3 visual vocabulary. These require focused tests
and native evidence in P6.5B.

P6.5A is not self-approved. P6.5B, promotion, merge, signed artifacts, and M20 remain
outside the current authorization until explicit manual approval.

Local evidence index (not committed because it contains personal-use renderer
evidence):

- `01`–`07`: Standard and Chess960 castling cases and timestamped strips;
- `08`–`09`: castling timing comparison and phone-scale context;
- `10`–`14`: quiet/capture promotion, Queen/Knight coverage, timing comparison,
  and phone-scale context;
- `15`–`16`: board-flip and newer-revision cancellation references;
- `17`: Neo, 3D Staunton, and Public Lumen compatibility;
- `18`: native current-production en-passant GIF and timestamped frame strip.

The local transfer package is
`LumenChess-P6.5A-design-checkpoint-d961a656.zip` (2,040,280 bytes), SHA-256
`5d31955418f9ca7f5071bde2bd3239c330688d0e1e48dae652ddb870750dd9ce`.

### P6.5A and P6.5B manual approval

P6.5A's Special-Move Motion & P6 Interaction Closure design is manually approved.
The approved production implementation culminates at
`01eabc7f9af47e87880f1fa6c26ee7af302855fc`, with the corrected every-frame
native promotion evidence lane at
`917209a354861ad1c9ca05a6ca9203d2fbdee544`.

P6.5B is manually approved with:

- concurrent 165 ms Standard and generalized Chess960 castling travel;
- deterministic king-above-rook transient ordering and static zero-distance legs;
- explicit canonical-destination suppression and revision-bound cancellation;
- normal source-specific pawn travel followed by an 80 ms promotion replacement
  bridge using Grounded Precision easing;
- outgoing pawn alpha from 1 to 0, promoted-piece alpha from 0 to 1, and promoted
  scale from approximately 0.96 to 1.00;
- unchanged, approved en-passant presentation;
- verified Neo, 3D Staunton, and Public Lumen renderer compatibility;
- unchanged P1 board bounds through special-move presentation.

The initial sparse promotion evidence sampled relative to authoritative commit and
did not capture the active replacement frames. The corrected API-37 evidence
separately anchored pawn arrival and bridge start, then captured every 16 ms frame
through the full 80 ms bridge. It proved that production promotion behavior was
already correct, so no production promotion correction was required.

The corrected review package is
`LumenChess-P6.5B-promotion-review-917209a3.zip` (8,067,991 bytes), SHA-256
`3d77f0af77424f98314e39e2176dbbeccad856ccfa7bc76fff373abe04337878`.
All 155 declared manifest members matched their sizes and SHA-256 digests.

P6.5B approval does not merge P6, authorize a signed APK, or begin M20. A final
bounded P6 closure audit remains required before leaving the P6 lane.
