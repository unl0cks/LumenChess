# LumenChess — Project Handoff

This file is the quickest way for a new ChatGPT/Codex session to resume LumenChess without needing the original planning conversation.

## Read in this order

1. `docs/design/LumenChess-Full-Design-Spec.md`
2. `docs/design/DECISIONS.md`
3. `docs/design/UI-FLOWS.md`
4. `docs/research/RESEARCH-NOTES.md`
5. `docs/references/REFERENCE-MANIFEST.md`

## Current state

- Product/design planning: substantially complete.
- Production implementation: not started.
- Primary next step: review the written design, then create a detailed implementation plan broken into small, independently testable milestones before coding.

## Locked foundation

- Native Android app: Kotlin + Jetpack Compose.
- Offline-first architecture.
- Initial device target: Google Pixel 8 Pro, Android 17 / API 37, ARM64 (`arm64-v8a`).
- Broader Android compatibility is deferred until the core app is stable.
- Mandatory initial engines: Stockfish and Reckless.
- Engine framework must remain generic so additional engines can be added later.
- Initial chess modes: Standard and Chess960.
- Custom positions / odds are part of the initial foundation.
- Main navigation: `Play · Arena · Games · Insights · Settings`.
- Default visual identity: dark, polished UI with a blue accent; accent color is configurable.
- Live play is deliberately uncluttered; Review and Analysis contain the heavier analytical UI.

## Major product decisions

### Engine strength

- Elo range: roughly 400–3000 plus Full Strength.
- Default strength model: Hybrid.
- Optional models: Engine Native and Humanized.
- `Match Your Elo` can use local performance/rated history or connected Chess.com/Lichess ratings and selects opponents within a configurable range around the source rating.

### Ratings

- Default local mode: performance estimate based on demonstrated move quality rather than win/loss alone.
- Optional result-based local ratings.
- Supported result-based systems: Glicko-2, Glicko-1, and FIDE Elo.
- Standard/Chess960 and relevant time classes retain separate ratings.

### Play

- Simple setup first, advanced controls collapsed underneath.
- Chess.com-style time controls and a three-item Recent group.
- White / Black / Random side selection.
- Tap and drag move input.
- Premoves enabled by default; multiple premoves optional.
- Default premove clock cost: 100 ms, configurable.

### Engine Arena

- Engine-vs-engine is a first-class feature.
- White/Black engines and strengths are configured independently.
- Opening control supports normal start, random opening, opening family, custom FEN, and random Chess960 start.
- User can manually control White, Black, or both for the first X moves or until released; clocks are paused during this setup by default.
- Mid-game takeover is supported for either/both sides, followed by returning control to the engines.
- Any earlier position can be branched into a sandbox continuation; original games remain untouched unless a branch is explicitly saved as a variation.

### Game Review / Analysis

- Analyze both sides by default; Guided Review focuses mainly on the user's key moments.
- Move classes: Brilliant, Great, Book, Best, Excellent, Good, Inaccuracy, Mistake, Miss, Blunder.
- Accuracy and Game Rating are simple headline metrics; technical statistics are available on demand.
- Review algorithms are versioned so old reviews can be reanalyzed with newer models later.
- Review modes: Guided and Full; Key Moments can skip ordinary moves.
- Game Review uses a horizontally scrolling classified move rail.
- Analysis uses a full scrollable notation list, evaluation bar, arrows, optional engine lines, and a Moves ↔ Explorer lower-pane toggle.
- No coach/avatar character in the initial UI.

### Explorer

- Offline opening names/ECO data plus optional offline statistic packs.
- Online enrichment from Lichess when available; results are cached for later offline use.
- Lichess Masters, player-game Explorer and cloud evaluations may be used where appropriate.
- Online data must never be required for basic operation.

### Library / sync

- One unified Games library with source tags rather than separate histories.
- Sources include local, Arena, Chess.com, Lichess, imported PGNs and branches.
- Manual import and optional automatic account syncing are both supported.
- Auto-review of newly synced games is off by default; optional conditions include Wi-Fi, charging state and battery threshold.
- Duplicate games are detected primarily by starting position + variant + complete move sequence and merged without discarding richer metadata.
- Old synced games can shed heavy review caches while retaining PGN and compact statistical summaries.
- Local/manual games are never silently deleted for cache management; favorites/protected games are exempt from automatic cleanup.

### Appearance / sound

- Board customization follows a `Boards · Pieces · Background · Presets` model with a live preview.
- Presets can be customized and saved; app accent is independent from board colors.
- Sound themes support whole-pack ZIP import plus per-event audio overrides.
- The public repo must not redistribute raw Chess.com screenshots or scraped Chess.com sound files unless rights are established.

## Important implementation principles

- Chess state must be deterministic and independently tested.
- UI must not directly manipulate engine processes.
- Engines must not directly mutate game state.
- Long-running analysis must be cancellable and resumable.
- Engine failures must not crash the app.
- Heavy work stays off the main thread.
- Database migrations, PGN/FEN round-trips, clocks, move legality, UCI parsing and review calculations need automated tests.
- Prefer boring, isolated modules over giant cross-wired feature classes.

## Reference assets

The public repository contains only original/public-safe documentation and the generated LumenChess blue UI concept. Raw Chess.com screenshots and a third-party scraped sound archive were used as private planning references and should not be committed publicly without permission.

## Instruction for a continuing session

Treat the design documents as the source of truth. Do not silently redesign locked defaults. If implementation reveals a genuine technical conflict, document the conflict, propose the smallest viable adjustment, and preserve the product behavior where possible.
