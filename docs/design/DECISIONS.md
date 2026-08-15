# LumenChess — Locked Decisions Quick Reference

This is the short handoff file. For full detail, read `LumenChess-Full-Design-Spec.md`.

## Platform
- Android-first, fully offline-first.
- Optimize initially for Pixel 8 Pro, Android 17 / API 37, ARM64.
- Kotlin + Jetpack Compose.
- Broader compatibility later.

## Main navigation
`Play · Arena · Games · Insights · Settings`

Default startup: Play.

## Engines
- Mandatory first: Stockfish 18 + Reckless 0.9.0.
- Generic engine abstraction so Maia, Komodo, Fairy-Stockfish, etc. can be added later.

## Variants
- First: Standard + Chess960.
- Custom FEN/Board Editor/Odds supported as starting-position tools.
- Other variants later.

## Engine strength
- 400–3000 Elo, 50-point steps + Full Strength.
- Strength models: Hybrid (default), Engine Native, Humanized.
- Hybrid = native limiting + plausible humanization.
- Match Your Elo available.
- Default matchmaking range ±100; ±50/±200/custom available.
- Match rating source: Local Performance, Local Rated, Chess.com, Lichess.

## Rating systems
- Local mode: Performance Estimate (default) or Rated Results.
- Rating system options: Glicko-2, Glicko-1/Chess.com-style, FIDE Elo.
- Standard/Chess960 separate; Bullet/Blitz/Rapid separate.

## Time controls
Recent = 3 most recent.

Bullet: 1, 1+1, 2+1, 30 sec, 20 sec+1.  
Blitz: 3, 3+2, 5, 5+3.  
Rapid: 10, 10+5, 15+10, 30, 20, 60.  
Custom: min/sec/inc.

## Live game UX
- Clean by default.
- Board + player/engine cards + clocks + small bottom row.
- Human-vs-engine eval/lines/arrows off by default.
- Arena eval bar on by default.
- Quick actions configurable.
- Tap + drag enabled by default.
- Premoves enabled; 1 premove default; multiple optional; 100 ms default premove cost.

## Arena
- Independent engine/strength per side.
- Opening modes: Normal, Random Opening, Opening Family, Custom FEN, random Chess960.
- Manual opening control for White/Black/Both for X moves or until released.
- Manual opening clocks paused by default.
- Mid-game takeover White/Black/Both.
- Return control to engine.
- Branch from any earlier position.

## Branching
- Separate sandbox session by default.
- Original game untouched.
- `Save as Variation` explicitly attaches branch to original game/PGN.
- Works for Arena/local/imported/Review games.

## Post-game
- End Screen default.
- Auto-open Review optional.
- Background review can begin silently after game ends.

## Game Review
- Fast / Balanced / Deep / Custom; Balanced default.
- Both sides analyzed by default; guided review focuses on user's key moments.
- Classifications: Brilliant, Great, Book, Best, Excellent, Good, Inaccuracy, Mistake, Miss, Blunder.
- Expected-points-loss style classification baseline.
- Borderline classifications get deeper recheck.
- Accuracy + Game Rating simple by default.
- Technical Stats expandable.
- Review algorithm/version saved.
- Reanalyze with latest model supported.
- No coach avatar initially.
- Guided Review + Full Review.
- Horizontal classified move rail in Guided Review.

## Analysis
- Separate power-user screen.
- Eval bar, arrows, MultiPV lines, classifications, full move list, free play, branching.
- Assistance toggles independent.
- Long-press moves for branch/FEN/comment/variation/deeper analysis.

## Explorer
- Analysis lower pane toggles `Moves ↔ Explorer`.
- Offline opening names/ECO bundled.
- Optional offline stats packs.
- Online Lichess enrichment in Auto mode.
- Local result first, online refresh second, result cached.
- Sources: Auto, Lichess Players, Masters, My Games, Local, Imported.
- Lichess Cloud Eval optional for occasional cached-position assistance.

## Library
- Unified library with source tags.
- Local, Arena, Chess.com, Lichess, Imported, Branches, Favorites filters.
- Manual + optional automatic sync.
- Auto sync off by default.
- Auto-review synced games off by default; Wi-Fi/charging/battery conditions available.
- Rolling full-detail retention, compact archival after threshold.
- Keep PGN/stat summary so old games can be reanalyzed.
- Never auto-delete local/manual games by default.
- Automatic deduplication; merge richer metadata; Allow Duplicate Copy optional.

## Insights
- General, not absurdly granular.
- Filters for game type/time/source/variant/date/rated.
- Overview, strength/rating trend, move quality, phase performance, openings, time-control comparison, recent trend.
- Stats can drill into games where practical.

## Appearance
- Chess.com-like clarity, not a clone.
- Default accent BLUE.
- Accent configurable.
- Board & Pieces tabs: Boards / Pieces / Background / Presets.
- Live preview.
- App appearance independent of board appearance.

## Sounds
- Built-in original/openly licensed packs only.
- Individual event overrides + ZIP pack import.
- Events include move/capture/castle/check/promotion/premove/illegal/start/end/drawoffer/low-time.
- Fallback: individual override → selected pack → default.
- Third-party scraped Chess.com sounds are private reference/import material only, not public redistribution assets.
