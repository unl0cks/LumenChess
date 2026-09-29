# M24 — Imports, exports and starting positions

Status: implementation candidate; manual review pending.

## Scope recovered

The roadmap and full design specification define M24 as the reusable starting-position subsystem and local interchange boundary: PGN/FEN paste or file/clipboard flows, a validated board-editor-compatible position input, saved positions, and material/move odds tools. M24 extends the existing core-chess PGN/FEN authority, canonical Room persistence, and approved Play/Arena setup surfaces. Online Chess.com/Lichess sync, opening identification, analysis, review, and branch editing remain later milestones.

## Implemented in this candidate

- Core `StartingPositionInput` / `StartingPositionResolver` accepts Normal, validated FEN, saved Position, and material-odds removal inputs.
- PGN import/export delegates exclusively to the existing strict legality-backed `Pgn` parser/writer.
- Play setup accepts an optional validated starting FEN and persists it through existing M19 restore metadata.
- Saved-game viewer exposes Copy PGN and Copy FEN from the canonical loaded tree/position.
- Existing Arena Custom FEN and Room SavedPosition APIs remain the authoritative paths; no parallel game tree or persistence authority was added.

## Verification

- `StartingPositionTest`: 3/3 focused JVM tests passed.
- `PlaySetupConfigTest`: 7/7 focused app JVM tests passed (including custom-FEN validation/resolution).
- Public/private asset mechanisms were not modified; no private imagery or APK is included.
- Native API-37 M24 capture is pending manual-review packaging; this environment has no usable local emulator.

## Boundaries

No release/signed APK, merge, promotion, M25, or changes to P5/P6/M20–M23 behavior are included.
