# LumenChess — Research Notes

**Research snapshot:** 2026-08-15. Re-verify time-sensitive details before implementation/release.

## Android

- Android 17 is API level 37 (`CINNAMON_BUN`).
- Jetpack Compose is Google's recommended Android UI toolkit.
- Android architecture guidance strongly supports unidirectional data flow, local single-source-of-truth patterns, Room for structured local data, and WorkManager for persistent queued work in offline-first apps.
- Android 17 introduces app memory-limit behavior that reinforces the need to bound engine hash/threads/caches.

Primary sources:
- https://developer.android.com/guide/topics/manifest/uses-sdk-element
- https://developer.android.com/about/versions/17/behavior-changes-17
- https://developer.android.com/about/versions/17/behavior-changes-all
- https://developer.android.com/develop/ui/compose/architecture
- https://developer.android.com/topic/architecture/data-layer/offline-first
- https://developer.android.com/training/data-storage/room/

## Stockfish

- Stockfish 18 was announced 2026-01-31 and is the current major release at this research snapshot.
- It should be the primary local analysis/review engine.

Primary source:
- https://stockfishchess.org/blog/2026/stockfish-18/

## Reckless

- Official repository: `codedeliveryservice/Reckless`.
- Latest version listed in README: v0.9.0, released 2026-03-01.
- Rust engine.
- AGPLv3.
- UCI-compatible.
- README lists Hash, Threads, MultiPV, `UCI_Chess960`, MoveOverhead, and SyzygyPath among options.

Primary source:
- https://github.com/codedeliveryservice/Reckless

## Rating systems

### Lichess
- Uses Glicko-2.

Source:
- https://lichess.org/page/rating-systems

### Chess.com
- Chess.com's own current help says it uses the Glicko system with rating deviation.
- Lichess's rating-system reference specifically identifies Chess.com as Glicko-1.
- Treat implementation as a Glicko-1 / Chess.com-style selectable mode, while noting that server-specific pool/initialization details can differ.

Sources:
- https://support.chess.com/en/articles/8566476-how-do-ratings-work-on-chess-com
- https://lichess.org/page/rating-systems

### FIDE
- FIDE uses Elo with federation-specific rules.
- Current regulations include a 1400 publication floor and K-factor rules such as K=40 for new players until 30 games, K=20 below 2400, K=10 after reaching 2400, with youth-specific rules.
- LumenChess should support practical local FIDE Elo and an optional strict-regulations mode.

Source:
- https://handbook.fide.com/chapter/B022024

## Chess.com-style review research

### Move classifications
Current Chess.com public documentation describes Classification V2 with an Expected Points Model.

Published expected-points-loss ranges:
- Best: 0
- Excellent: 0–0.02
- Good: 0.02–0.05
- Inaccuracy: 0.05–0.10
- Mistake: 0.10–0.20
- Blunder: 0.20–1.00

Special classes:
- Great: critical/only-good/outcome-changing moves.
- Brilliant: best/nearly-best move involving a suitable piece sacrifice and other position conditions.
- Miss: failure to capitalize on opponent mistake/opportunity.

Source:
- https://support.chess.com/en/articles/8572705-how-are-moves-classified-what-is-a-blunder-or-brilliant-etc

### Accuracy
- Chess.com calls its current system CAPS2.
- Public docs describe intent and behavior but not a complete reproducible formula.
- LumenChess should therefore implement its own transparent calibrated accuracy model instead of falsely claiming exact CAPS2 equivalence.

Source:
- https://support.chess.com/en/articles/8708970-how-is-accuracy-in-analysis-determined

### Game Rating
- Chess.com describes Game Rating / Performance Rating as comparing move quality against what is expected for players around a rating level.
- Exact formula is not public.
- LumenChess should implement and version its own calibrated performance-strength estimate.

Source:
- https://support.chess.com/en/articles/10773754-how-is-game-rating-calculated-in-game-review

## Lichess opening data and Explorer

### Opening names
- `lichess-org/chess-openings` is an aggregated opening-name dataset.
- Contains ECO, name, PGN and generated UCI/EPD representations.
- CC0/public-domain dedication.
- Designed to classify with transpositions by walking positions/moves backwards to a named position.

Source:
- https://github.com/lichess-org/chess-openings

### Opening Explorer
Lichess's `lila-openingexplorer` supports:
- master games
- rated Lichess games
- player-specific opening exploration
- curated opening names
- multiple variants

The API exposes Lichess and Masters Explorer endpoints and filters such as speed, rating buckets, dates, FEN/play sequence, and move count.

Sources:
- https://github.com/lichess-org/lila-openingexplorer
- https://github.com/lichess-org/api/tree/master/doc/specs/tags/openingexplorer

### Cloud evaluation
Lichess exposes a cached cloud-evaluation endpoint for occasional position lookups, with up to multiple PVs where available. Their docs explicitly recommend bulk database downloads rather than mass API querying.

Source:
- https://github.com/lichess-org/api/blob/master/doc/specs/tags/analysis/api-cloud-eval.yaml

### Bulk data
Lichess publishes large standard-game and evaluation database dumps suitable for preprocessing into optional offline Explorer packs.

Source:
- https://database.lichess.org/

## Chess.com visual and audio references

The project used user-supplied screenshots of Chess.com's current mobile/desktop UI as interaction references for:
- New Game time-control layout.
- Board & Pieces settings.
- Settings hierarchy.
- Live game cleanliness.
- Game Review horizontal move rail.
- Analysis full move list.
- Moves ↔ Explorer switching.
- Insights filtering.

The user also provided a ZIP containing scraped Chess.com sound themes as a private reference corpus. Do not assume those assets can be publicly redistributed. LumenChess should ship its own/openly licensed sound packs and support user-local imports.
