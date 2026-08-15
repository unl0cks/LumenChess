# LumenChess — Full Product & Technical Design Specification

**Status:** Design complete enough for implementation planning; implementation has not started.  
**Spec date:** 2026-08-15  
**Initial target device:** Google Pixel 8 Pro  
**Initial target OS:** Android 17 / API 37  
**Initial CPU ABI:** ARM64 (`arm64-v8a`)  
**Primary mandatory engines:** Stockfish 18 and Reckless 0.9.0  
**Initial chess modes:** Standard and Chess960  
**Default visual identity:** dark, polished, blue-accented; inspired by modern Chess.com interaction patterns without copying Chess.com branding or assets.

---

## 1. Product vision

LumenChess is an offline-first Android chess application centered around four things:

1. **Playing against strong and configurable chess engines.**
2. **Watching and controlling engine-vs-engine games in a dedicated Arena.**
3. **Reviewing and analyzing games deeply on-device, including games imported from Chess.com, Lichess, PGN files, URLs, and local play.**
4. **Providing a polished, highly customizable mobile chess experience with sensible defaults and advanced controls available when wanted.**

The app should feel immediately usable to someone familiar with Chess.com, but it must have its own visual identity, navigation, defaults, and implementation. The goal is not to clone Chess.com; the goal is to use proven interaction patterns and then go further in areas Chess.com does not prioritize, especially offline engine play, engine-vs-engine experimentation, branching, and user-controlled analysis.

The design rule that should guide the whole project is:

> **Simple by default, configurable underneath.**

The live playing screen should remain deliberately uncluttered. More technical information belongs in Review, Analysis, Explorer, or optional overlays/settings.

---

## 2. Scope and sequencing

### 2.1 Initial scope

The first serious version should prioritize:

- Android only.
- Native Kotlin + Jetpack Compose.
- Pixel 8 Pro / Android 17 / ARM64 first.
- Full offline play and review.
- Standard chess.
- Chess960.
- Stockfish 18.
- Reckless 0.9.0.
- Human vs engine.
- Engine vs engine Arena.
- Local games library.
- PGN import/export.
- Chess.com and Lichess game import/sync.
- Game Review with classifications, accuracy, game/performance rating, evaluation graph, phase stats, and explanations.
- Analysis with evaluation bar, arrows, engine lines, move classifications, move list, branching, and Opening Explorer.
- Opening Explorer with an offline base and optional online Lichess enrichment.
- General Insights/statistics.
- Board/piece/background/theme customization.
- Sound-pack customization and import.
- Local rating / strength tracking and Match Your Elo.

### 2.2 Explicitly deferred or optional later

These should be supported by architecture but not allowed to derail the first implementation:

- Maia / Maia-3.
- Komodo Dragon personalities.
- Fairy-Stockfish.
- Crazyhouse.
- Three-check.
- King of the Hill.
- Bughouse.
- Four Player Chess.
- Additional variants.
- Coach/avatar character in Game Review.
- Broad legacy Android / 32-bit compatibility.
- Large cloud backend.
- Multiplayer hosted directly by LumenChess.

The architecture should not hardcode assumptions that make these impossible later, but they are not blockers for v1.

---

## 3. Design principles

### 3.1 Offline first

Core functionality must work with no internet connection:

- Play against engines.
- Engine-vs-engine Arena.
- Standard / Chess960 rules.
- Game saving.
- Local Game Review.
- Local Analysis.
- Local opening identification.
- Local ratings and Insights.
- Local themes and sound packs.

Online services should **enrich** the app rather than become a dependency.

### 3.2 Clean live play

The live game screen should prioritize:

- Board.
- Player/engine cards.
- Clocks.
- A small bottom action row.

Optional overlays such as evaluation bar, engine lines, move list, detailed captured material, engine depth/nodes, suggestion arrows, and extra controls should be configurable and hidden by default during normal human-vs-engine play.

### 3.3 Powerful analysis separated from play

Game Review and Analysis are intentionally richer screens.

- **Game Review** = guided explanation and key moments.
- **Analysis** = sandbox/power-user exploration.
- **Explorer** = opening statistics and theory.

Do not turn the normal playing screen into a permanent analysis cockpit.

### 3.4 Strong internal boundaries

The UI must not directly manipulate engine processes. Engines must not mutate game state. Review algorithms should not depend on UI components. The chess core should be independently testable.

### 3.5 User data should be hard to lose

- Never delete local/manual games just to reclaim cache space.
- Cache cleanup should discard reproducible heavy analysis before original game data.
- Favorites/protected games should be excluded from automatic cleanup.
- Imported PGNs should be preserved unless the user explicitly deletes them.

---

## 4. Platform and technology

### 4.1 Android target

Initial optimization target:

- Google Pixel 8 Pro.
- Android 17.
- API 37.
- ARM64.

Broader Android support should be added after the application foundation is stable.

### 4.2 App stack

Recommended stack:

- **Kotlin** for application code.
- **Jetpack Compose** for UI.
- **Navigation Compose** for navigation.
- **ViewModel + StateFlow** for screen state.
- **Room** for structured local persistence.
- **DataStore** for typed preferences/settings where appropriate.
- **WorkManager** for persistent sync/review queues that should survive process death and respect charging/network conditions.
- **Kotlin coroutines** for cancellable async work.
- **JNI/NDK and/or native libraries** for engine integration as needed.

Architecture should follow a local single-source-of-truth model and unidirectional data flow.

### 4.3 Why native Android

Native Android is preferred because the app needs:

- Tight lifecycle control.
- Native engine integration.
- High-performance board rendering/animation.
- Background analysis.
- Local database work.
- Audio/haptics.
- Persistent queues.
- Good battery/thermal behavior.

A cross-platform layer would add complexity without meaningful benefit for the initial Android-only target.

---

## 5. Main navigation

Use a five-tab purpose-first bottom navigation:

1. **Play**
2. **Arena**
3. **Games**
4. **Insights**
5. **Settings**

Default startup destination: **Play**.

A future setting can choose another startup tab.

Contextual screens should not become permanent main tabs:

- Game Review.
- Analysis.
- Explorer.
- Branch/Sandbox.
- Board Editor.
- Import flow.

---

## 6. Play — Human vs engine

### 6.1 Default layout philosophy

Show the common setup first and move advanced configuration into an expandable Advanced section.

### 6.2 Common controls

Always-visible setup should include:

- **Mode:** Standard / Chess960.
- **Opponent:** Stockfish / Reckless.
- **Strength:** manual Elo or Match Your Elo.
- **Time Control.**
- **Side:** White / Black / Random.
- **Start Game** button.

The app should remember the user's last complete setup.

Example remembered setup:

> Reckless · Match Your Elo · Rapid 10 min · Random · Standard

### 6.3 Engine Elo range

Manual engine strength:

- 400–3000 Elo.
- 50 Elo increments by default.
- Full Strength / Maximum option.

### 6.4 Strength Model

Three selectable strength models:

1. **Hybrid — default**
   - Native engine limiting where available.
   - Humanization layer added on top.
   - Humanization decreases as target Elo rises.

2. **Engine Native**
   - Only the engine's own limiting mechanism/settings.

3. **Humanized**
   - LumenChess humanization layer has stronger influence.

The displayed target remains conceptually the same Elo target; the setting changes how that target is approximated.

### 6.5 Humanization philosophy

Low-strength engine play should fail plausibly, not by deliberate random sabotage.

Examples of believable weaknesses:

- Missing tactical opportunities.
- Failing to notice opponent threats.
- Choosing a slightly inferior positional plan.
- Misjudging exchanges.
- Poor time-pressure choices.
- Selecting a plausible but second/third-best candidate move.

Avoid the classic bad bot pattern of playing perfectly for 20 moves and then randomly hanging a queen.

Suggested strength behavior:

- 400–1000: substantial human-like errors.
- 1000–1600: moderate tactical/positional mistakes.
- 1600–2000: smaller imperfections.
- 2000+: increasingly native behavior.
- 2600+: effectively normal engine play unless explicitly humanized.

Exact calibration must be tested empirically and versioned.

### 6.6 Match Your Elo

Add a prominent **Match Your Elo** control.

It should choose a target from one of these rating sources:

- Local Performance Estimate.
- Local Rated rating.
- Chess.com rating.
- Lichess rating.

When a network source is selected, cache the most recent rating locally so engine matching still works offline.

Matchmaking should not use exactly the same Elo every game. Default range:

- **Normal: ±100 Elo**.

Other options:

- Tight: ±50.
- Wide: ±200.
- Custom.

The chosen engine strength is fixed when the game starts. It should **not secretly adjust mid-game** based on the player's performance.

The rating source should be time-control aware:

- Bullet uses Bullet rating.
- Blitz uses Blitz rating.
- Rapid uses Rapid rating.

Local Standard and Chess960 ratings should remain separate.

### 6.7 Time controls

Use the following predefined groups.

#### Recent

Shows the **three most recently played time controls**.

#### Bullet

- 1 min
- 1 + 1
- 2 + 1
- 30 sec
- 20 sec + 1

#### Blitz

- 3 min
- 3 + 2
- 5 min
- 5 + 3

#### Rapid

- 10 min
- 10 + 5
- 15 + 10
- 30 min
- 20 min
- 60 min

#### Custom

Allow:

- minutes
- seconds
- increment

The time-control picker should visually resemble the easy-to-scan grouped mobile interaction seen in Chess.com without copying their exact styling.

### 6.8 Advanced Play settings

Advanced may contain:

- Strength Model.
- Matchmaking range.
- Starting position / opening setup.
- Engine-specific options.
- Takeback policy.
- Live assistance overrides.
- Premove settings overrides.
- Clock behavior.
- Humanization advanced controls when exposed later.

---

## 7. Starting positions, custom positions, and odds

Use one reusable starting-position subsystem shared across Play, Arena, Analysis, and branching.

Supported initial starting-position modes:

- **Normal**.
- **Opening Position** selected from Explorer/opening database.
- **FEN** paste/import.
- **Board Editor**.
- **Material Odds**.
- **Move Odds**.
- **Saved Positions**.

### 7.1 Material Odds

Quick presets should include common options such as:

- Pawn odds.
- Knight odds.
- Rook odds.
- Queen odds.

Also allow arbitrary manual editing in Board Editor.

### 7.2 Move Odds

Allow one side to receive one or more free setup moves before clocks begin.

### 7.3 Validation

Before a custom game starts, validate:

- Both kings exist where required.
- Side-to-move is legal.
- Castling rights are consistent with the position.
- FEN is syntactically valid.
- Position does not violate core rule constraints that would make gameplay undefined.

Warn clearly rather than silently accepting nonsense.

---

## 8. Engine Arena

Arena is a first-class main tab for engine-vs-engine play and experimentation.

### 8.1 Setup

Allow independent configuration of:

- White engine.
- Black engine.
- White strength/Elo.
- Black strength/Elo.
- White Strength Model.
- Black Strength Model.
- Time control.
- Standard / Chess960.
- Which engine gets White/Black or random assignment.

### 8.2 Opening Setup

Arena opening setup options:

- Normal Start.
- Random Opening.
- Opening Family.
- Custom FEN.
- Random Chess960 position.

For Random Opening, allow book handoff depth such as:

- 4 plies.
- 8 plies.
- 12 plies.
- Custom.

Normal Start remains the default.

### 8.3 Manual opening control

Allow the user to manually make the first X moves for:

- White only.
- Black only.
- Both sides.

Also allow:

- **Until I release control**.

Show a clear indicator, e.g.:

> You control White · 3 moves remaining

At handoff:

> Stockfish has taken over

Default clock behavior during manual opening:

- **Clocks paused.**

Optional setting:

- Count manual opening time against the clocks.

### 8.4 Mid-game takeover

During an Arena match, optionally allow:

- Take Over White.
- Take Over Black.
- Take Over Both.
- Return Control to Engine.

Default clock policy may pause clocks while manually controlling, with an option to keep them running.

### 8.5 Branching from Arena

After any Arena game, the user can return to any position and create a branch.

From the branch, allow:

- Play a different move.
- Continue Stockfish vs Reckless.
- Stockfish vs Stockfish.
- Reckless vs Reckless.
- Manual White.
- Manual Black.
- Manual both sides.
- One human side + one engine side.
- Change either engine's strength.
- Change time control.
- Run without clocks.

### 8.6 Branch storage behavior

Default:

- Branches open as **separate sandbox sessions**.
- Original game remains untouched.

Explicit action:

- **Save as Variation** to attach it to the original game tree/PGN.

This behavior should apply not only to Arena but also to local games, imported PGNs, Chess.com/Lichess games, and Game Review positions.

### 8.7 Arena live screen

Default bottom actions:

- Options
- Pause
- Take Over
- Branch

Additional actions can be pinned or placed under Options.

Playback speed controls should be available where meaningful:

- Real Time.
- ½×.
- 2×.
- 4×.
- Instant.

For actively clocked real-time engine matches, speed changes must not fake clock behavior. Faster playback is most appropriate for unclocked/simulated/replay scenarios.

---

## 9. Chessboard interaction

### 9.1 Move input

Support both simultaneously by default:

- Tap piece, then tap destination.
- Drag piece to destination.

Settings can enable either or both.

### 9.2 Board feedback

Configurable:

- Legal move indicators.
- Capture indicators.
- Last move highlight.
- Check highlight.
- Selected square.
- Premove highlight.
- Haptic feedback.
- Animation speed.
- Illegal move snap-back animation.
- Promotion behavior.

### 9.3 Promotion

Options:

- Auto-queen.
- Always ask.
- Possibly remember last promotion choice where appropriate.

Promotion premoves must respect these settings.

### 9.4 Premoves

Premoves enabled by default.

Default:

- Single premove.
- 100 ms clock cost per executed premove.

Optional:

- Multiple premoves.
- 0 ms cost.
- Custom premove delay.
- Show/hide premove path/highlight.

When multiple premoves are enabled, show a subtle numbered queue.

If a queued move becomes illegal, cancel it cleanly and continue with remaining logic only where still valid.

---

## 10. Live game screen

### 10.1 Core philosophy

Keep live play less cluttered than Analysis or Review.

Default portrait hierarchy:

1. Minimal top bar.
2. Opponent/engine card + clock.
3. Board at maximum practical width.
4. User card + clock.
5. Small contextual bottom action row.

### 10.2 Player/engine cards

Show:

- Name.
- Rating/target Elo.
- Clock.
- Optional avatar/icon.
- Optional captured material.

For engines, additional details can be revealed on tap:

- Engine version.
- Strength Model.
- Search depth.
- Nodes.
- NPS.

These technical details should be hidden by default.

### 10.3 Human vs engine default assistance

Default OFF:

- Evaluation bar.
- Engine lines.
- Best-move arrows.
- Move feedback/classification during the live game.

Default optional/low-noise:

- Opening name.
- Captured pieces.
- Collapsible move list.

Allow independent overrides in Settings.

### 10.4 Engine vs engine default assistance

Default ON:

- Evaluation bar.

Optional:

- Engine lines.
- Move list.
- Thinking info.
- Engine details.

### 10.5 Configurable quick actions

The user should be able to select which actions are pinned to the bottom row.

Human-vs-engine candidates:

- Options.
- Resign / Abort.
- Draw.
- Hint.
- Undo / Takeback.
- Flip.
- Moves.
- Sound.

Arena candidates:

- Options.
- Pause.
- Take Over.
- Branch.
- Speed.
- Flip.

### 10.6 Landscape

Landscape should use the extra horizontal space intelligently:

- Board on left.
- Moves/Analysis/Info panel on right.

Do not simply enlarge the board until it consumes all vertical space.

---

## 11. Post-game behavior

Configurable setting:

- **End Screen — default**.
- Auto-open Review.

End screen actions:

- Review Game.
- Rematch.
- New Game.
- Save/Export PGN.
- Share.

Even when the End Screen is shown, LumenChess should begin review analysis silently in the background when configured/appropriate so pressing Review may feel nearly instant.

---

## 12. Game Review

### 12.1 Purpose

Game Review is a guided breakdown, not a freeform analysis board.

It should provide:

- Evaluation graph.
- Accuracy for both sides.
- Game/Performance Rating for both sides.
- Move classifications.
- Opening/middlegame/endgame stats.
- Key moments.
- Explanations.
- Retry opportunities.
- Branch From Here.

No coach avatar is required in the initial UI.

### 12.2 Review quality presets

Presets:

1. **Fast**
2. **Balanced — default**
3. **Deep**
4. **Custom**

Balanced should be adaptive rather than mechanically spending the same time on every position.

Suggested behavior:

- Easy/forced positions get less time.
- Tactical/ambiguous positions get more time.
- Borderline move classifications get a second/deeper pass.

Custom can expose:

- Engine.
- Maximum time.
- Depth limit.
- Node limit.
- Threads.
- Hash.
- MultiPV.
- Critical-position rechecks.

### 12.3 Background behavior

Review jobs must be:

- Cancellable.
- Resumable.
- Safe across process death where possible.
- Able to persist partial progress.

### 12.4 Whose moves are analyzed

Default:

- **Both players**.

Guided Review should primarily focus on the user's key moments where a user side is known.

Settings:

- My Moves.
- Both.
- Ask Each Time.

Neutral imported games and engine-vs-engine games default to Both.

### 12.5 Move classifications

Required classifications:

- Brilliant.
- Great.
- Book.
- Best.
- Excellent.
- Good.
- Inaccuracy.
- Mistake.
- Miss.
- Blunder.

The baseline classification model should use **expected-points loss**, not only fixed centipawn thresholds.

Published Chess.com V2-like thresholds are useful as a reference baseline:

- Best: 0 expected-points loss.
- Excellent: 0–0.02.
- Good: 0.02–0.05.
- Inaccuracy: 0.05–0.10.
- Mistake: 0.10–0.20.
- Blunder: 0.20–1.00.

Special classifications need extra rules:

- **Great:** critical move / only good move / meaningful outcome shift.
- **Brilliant:** best/nearly-best move with a meaningful piece sacrifice under suitable position conditions.
- **Miss:** failure to exploit an opponent mistake/tactical opportunity.
- **Book:** recognized opening-book move.

LumenChess should not claim to reproduce Chess.com's proprietary backend exactly. The algorithm should be our own transparent/versioned implementation inspired by public definitions and then empirically calibrated.

### 12.6 Borderline stability

If a move sits near a classification threshold, automatically reanalyze deeper before finalizing.

Goal:

- avoid unstable classifications caused by tiny search fluctuations.

### 12.7 Accuracy

Show a simple **0–100 Accuracy** score by default.

Do not pretend to use Chess.com's proprietary CAPS2 formula exactly.

LumenChess should build a calibrated score from:

- expected-points losses.
- move difficulty / forcedness.
- game length.
- quality distribution.
- engine confidence.

The detailed underlying data can remain available in Technical Stats.

### 12.8 Game / Performance Rating

Show an estimated single-game performance rating.

The model should compare the move-quality profile to what is expected from players in different rating bands.

Important:

- This is not the player's permanent rating.
- It can vary wildly game to game.
- It should be calibrated against historical/reference data where possible.

### 12.9 Review algorithm versioning

Every review stores:

- Review Model version.
- Engine version.
- Engine configuration.
- Analysis preset/settings.

When algorithms improve, old reviews should remain stable unless the user chooses:

- **Reanalyze with Latest Model**.

### 12.10 Technical Stats

Normal UI stays simple.

Expandable Technical Stats can show:

- ACPL.
- Expected-points loss.
- Engine depth.
- Nodes.
- NPS.
- MultiPV details.
- Classification confidence.
- Review Model version.
- Engine/version used.

### 12.11 Summary screen

Game Review summary should include:

- Evaluation graph.
- Both players.
- Accuracy.
- Classification counts.
- Game Rating.
- Opening/middlegame/endgame performance.
- Start Review.
- New Game / contextual actions.

### 12.12 Guided Review vs Full Review

Provide two navigation styles:

#### Guided Review — default

- Focus on key moments.
- Move-by-move explanation cards.
- Next/Previous.
- Best.
- Retry.
- Branch From Here.

#### Full Review

- Freely navigate every move.
- Classification/explanation still available.

Add **Key Moments Only** toggle so long games do not require stepping through every quiet move.

### 12.13 Horizontal move rail

Guided Review uses a horizontally scrollable move rail below the board.

Each move displays classification color/icon where applicable.

Example:

> ‹ 14...Kd7 15.Nxf7 ? 15...Re8+ 16.Ne5 ?? ›

Behavior:

- Current move clearly selected.
- Auto-scroll to keep current move visible/centered.
- Swipe horizontally.
- Tap any move to jump there.
- Update board, explanation, eval, arrows, and suggested continuation immediately.

---

## 13. Analysis

### 13.1 Purpose

Analysis is the freeform power-user sandbox.

It should support:

- Evaluation bar.
- Suggestion arrows.
- Threat arrows.
- Engine lines / MultiPV.
- Move feedback/classifications.
- Opening name.
- Scrollable move list.
- Opening Explorer.
- Back/Forward.
- Branching.
- Engine switching.
- Depth/time changes.
- Free move entry.
- Human/engine continuation from current position.

### 13.2 Assistance menu

Independent toggles:

- Evaluation Bar.
- Suggestion Arrows.
- Threat Arrows.
- Engine Lines.
- Move Feedback.

### 13.3 Move list

Analysis shows a vertically scrollable full move list below the board.

Selecting a move:

- updates the board.
- updates eval.
- updates engine suggestions.
- highlights/centers selected notation.
- updates opening information.

Long-press move context menu:

- Branch From Here.
- Copy FEN.
- Copy position.
- Add comment.
- Add variation.
- Analyze deeper.

### 13.4 Analysis options

Should include:

- Reset.
- Flip Board.
- Practice vs Computer.
- Share.
- Copy PGN/FEN.
- Engine settings.

---

## 14. Opening Explorer

### 14.1 Moves ↔ Explorer interaction

Analysis bottom navigation includes an **Explorer** action.

When tapped:

- lower pane transitions from move list to Opening Explorer.
- button changes to **Moves**.

Tap Moves to return to the notation list without leaving Analysis.

### 14.2 Explorer content

Show:

- Opening name.
- Variation/subvariation.
- ECO code.
- Candidate moves.
- Number of games.
- Move frequency.
- White win percentage.
- Draw percentage.
- Black win percentage.
- Average rating where available.
- Recent/top games where useful.

Tapping an Explorer move temporarily plays it on the analysis board and allows exploring deeper continuations.

### 14.3 Offline opening identification

Bundle an optimized local database built from the Lichess `chess-openings` CC0 dataset.

Opening classification should handle transpositions by position, not only by naïve move-prefix matching.

### 14.4 Offline statistics packs

Use a tiered model:

- **Essential** built in.
- **Extended** optional download.
- **Strong Players** optional.
- **Recent Theory** optional.
- Potential **Huge** pack later.

Do not ship raw multi-gigabyte PGN dumps in the app. Preprocess them into position statistics/indexes.

### 14.5 Online enrichment

Default Explorer source mode:

- **Auto**.

Auto behavior:

1. Return local data immediately.
2. If connected and enabled, query Lichess Opening Explorer.
3. Refresh statistics when response arrives.
4. Cache the result locally.
5. Reuse cached result offline later.

Show source/freshness metadata, e.g.:

> Lichess · Live · 4.3M games

or

> Cached online · updated 2 days ago

### 14.6 Explorer data sources

Selectable sources:

- Auto.
- Lichess Players.
- Masters.
- My Games.
- Local Database.
- Imported Games.
- Combined Comparison later.

Do not blindly merge fundamentally different pools into one unlabeled percentage.

### 14.7 Filters

Lichess-backed Explorer can expose filters such as:

- Ratings: All / 1000+ / 1400+ / 1800+ / 2000+ / 2200+ / 2500+.
- Speeds: Bullet / Blitz / Rapid / Classical.
- Time period: All Time / Last Year / Last 5 Years / Custom.

Allow saved filter presets later.

### 14.8 My Games Explorer

Build player-specific Explorer statistics from:

- connected Lichess games.
- imported Chess.com games.
- local games.

Useful output:

- how often the user chooses each move.
- score from each continuation.
- average opponent strength.
- openings where results are weak/strong.

### 14.9 Cloud evaluations

When online, optionally use Lichess Cloud Evaluation for occasional positions.

Default behavior:

- Local Stockfish remains the offline authority.
- Cached cloud results can provide an instant starting eval/PV.
- Local engine can refine/verify afterwards.

Do not bulk-query an API intended for occasional lookups.

### 14.10 Third-party databases

ChessBase / ChessTempo integrations should **not** be assumed.

Only add if an approved API/license exists.

Do not build the product around scraping services whose terms prohibit it.

---

## 15. Games / Library

### 15.1 Unified library

One canonical Library with source tags.

Filters:

- All.
- Local.
- Engine Arena.
- Chess.com.
- Lichess.
- Imported.
- Branches / Analysis.
- Favorites.

Game card can show:

- players/engines.
- result.
- date.
- time control.
- variant.
- ratings.
- source.
- review status.

Long-press actions:

- Review.
- Analyze.
- Branch.
- Export PGN.
- Favorite.
- Protect.
- Delete.

### 15.2 Import methods

Support:

- Paste PGN.
- Open `.pgn` file.
- Paste Chess.com URL.
- Paste Lichess URL.
- Chess.com username sync.
- Lichess account/username sync.
- Local games.

### 15.3 Account syncing

Use both:

- Manual import.
- Optional automatic sync.

Auto Sync default: **Off**.

Settings:

- Off.
- Wi-Fi only.
- Any connection.
- Number of recent games to sync.
- Time controls to sync.
- Rated only / all.
- Standard / Chess960.
- Ignore aborted/very short games.
- Auto-review new games.

### 15.4 Auto-review synced games

Default: **Off**.

Optional constraints:

- Wi-Fi only.
- Charging only.
- Battery above configurable percentage.
- Maximum reviews per session/sync.

Background review queue must be cancellable/resumable.

### 15.5 Rolling full-detail retention

Recommended default model:

- Keep recent X games with full heavy review data.
- Archive older games into lightweight records.

Suggested default: 250 fully analyzed recent games, configurable.

Keep in archive:

- PGN.
- Players/engines.
- Result.
- Date.
- Variant.
- Time control.
- Ratings.
- Opening/ECO.
- Accuracy.
- Game Rating.
- phase performance.
- classification counts.
- compact evaluation-loss metrics.
- major turning points.
- source.

Discard first when cleaning:

- bulky PV caches.
- repeated engine traces.
- reconstructible deep analysis intermediates.

Because PGN is retained, archived games can always be **Reanalyzed** later.

### 15.6 Source-specific retention

Allow separate policies.

Reasonable defaults:

- Chess.com: rolling retention allowed.
- Lichess: rolling retention allowed.
- Local games: never auto-delete.
- Arena games: never auto-delete by default.
- Manual PGN imports: never auto-delete.

### 15.7 Favorites / protection

Favoriting, protecting, or attaching important manual annotations/branches can exclude a game from automatic heavy-data cleanup.

### 15.8 Deduplication

Deduplicate automatically using a canonical fingerprint based primarily on:

- Variant.
- Starting position.
- Full move sequence.

Use player/date/source metadata as supporting evidence.

If duplicate:

- keep one canonical game.
- merge source tags.
- preserve richest metadata.
- preserve review data.
- preserve comments/variations where possible.

Provide **Allow Duplicate Copy** when the user intentionally wants a separate sandbox copy.

---

## 16. Ratings and strength tracking

### 16.1 Two local rating modes

1. **Performance Estimate — default**
   - Derived from demonstrated move quality.
   - Does not treat engine wins/losses as normal rated results.

2. **Rated Results**
   - Updates a formal rating from wins/draws/losses against configured opponents.

### 16.2 Rating systems

At minimum support:

- **Glicko-2 (Lichess-style)**.
- **Glicko-1 / Chess.com-style Glicko**.
- **FIDE Elo**.

Store each system's full underlying state independently so switching systems does not erase the previous one.

Examples:

- Glicko rating + RD.
- Glicko-2 rating + RD + volatility.
- FIDE Elo rating + game count/K-factor state.

### 16.3 FIDE mode

Provide:

- practical FIDE-like Elo mode.
- optional **Strict FIDE Regulations** mode.

Strict mode may enforce current publication floor/rules; practical mode should allow sub-1400 local estimates so the app remains useful for beginners.

### 16.4 Separate rating pools

Keep separate local ratings by:

- Bullet.
- Blitz.
- Rapid.

Also separate:

- Standard.
- Chess960.

### 16.5 Match Your Elo source

Settings:

- Local Performance.
- Local Rated.
- Chess.com.
- Lichess.

Cached remote rating remains usable offline.

---

## 17. Insights

Insights should remain useful and general rather than attempting to recreate every Chess.com statistic.

### 17.1 Filters

Top-level filters:

- Game Type: Human / Engine / Arena / All.
- Time Class: Bullet / Blitz / Rapid / All.
- Variant: Standard / Chess960 / All.
- Source: Local / Chess.com / Lichess / Imported / All.
- Date Range: 7 days / 30 days / 90 days / 1 year / All time / Custom.
- Rated: Rated / Unrated / All.

Default game-type filter:

- Human games when enough human data exists.
- Otherwise All, clearly labeled.

### 17.2 General sections

Keep the initial Insights feature focused on:

#### Overview

- Games played.
- Wins/draws/losses.
- Average accuracy.
- Average estimated strength.
- Average opponent rating.

#### Rating / Strength Trend

- simple trend graph.

#### Move Quality

- average inaccuracies per game.
- mistakes per game.
- misses per game.
- blunders per game.

#### Game Phases

- Opening accuracy/strength.
- Middlegame accuracy/strength.
- Endgame accuracy/strength.

#### Openings

- Most played.
- Best performing.
- Worst performing.
- White/Black separation.

#### Time Controls

- Bullet / Blitz / Rapid comparisons.

#### Recent Trend

- Whether accuracy, strength, and error rate are improving/declining.

### 17.3 Drill-down

Where practical, tapping a stat should show the games/positions behind it.

Example:

> 8 blunders in last 30 days

Tap → show those games/positions.

### 17.4 Compare periods

A compact optional Compare mode can compare:

- Last 30 vs previous 30 days.
- Last 50 vs previous 50 games.
- Rapid vs Blitz.
- Chess.com vs Lichess.

Do not let this become a blocker for v1.

---

## 18. Appearance and customization

### 18.1 Visual direction

Reference characteristics:

- dark layered surfaces.
- strong information hierarchy.
- rounded cards/controls.
- smooth animations.
- live previews.
- restrained accent use.

Default accent: **Blue**.

Do not use Chess.com's green as the default brand accent.

### 18.2 App appearance

Settings:

- System.
- Dark.
- OLED Black.
- Light.

Accent options:

- Blue — default.
- Purple.
- Red.
- Green.
- Amber/Gold.
- Cyan.
- Monochrome.
- Custom color.

Board colors remain independent from app accent.

### 18.3 Board & Pieces screen

Use tabs:

- **Boards**.
- **Pieces**.
- **Background**.
- **Presets**.

Always show a live board preview.

#### Boards

- bundled colors/textures.
- custom light square color.
- custom dark square color.
- texture options.

#### Pieces

- bundled original/openly licensed sets.
- future custom SVG/PNG import.

#### Background

- solid.
- gradient.
- subtle texture.
- custom image.

#### Presets

Preset combines:

- board.
- pieces.
- background.
- highlights.
- optional sound theme.

Allow:

- Save as Preset.
- Duplicate.
- Reset component.
- Import/export custom theme.

### 18.4 Board display settings

Examples:

- Coordinates: Off / Inside / Outside.
- Highlight last move.
- Highlight check.
- Legal move indicators.
- Capture indicators.
- Premove indicators.
- Selected square style.
- Orientation behavior.

### 18.5 Effects / accessibility

Configurable:

- Win celebration.
- Check animation.
- Checkmate animation.
- Capture effects.
- Piece animations.
- Animation speed.
- Reduced Motion override.

Decorative effects should always be independently disableable.

---

## 19. Sounds and haptics

### 19.1 Sound themes

Ship only original/openly licensed sounds.

Potential built-in themes:

- Modern — default.
- Classic Wood.
- Tournament.
- Glass.
- Soft.
- Arcade.
- Minimal.
- Silent.

### 19.2 Sound event schema

At minimum support:

- moveSelf.
- moveOpponent.
- capture.
- castle.
- check.
- promotion.
- premove.
- illegal.
- gameStart.
- gameEnd.
- drawOffer.
- lowTime.

Optional extended events:

- win.
- loss.
- draw.
- checkmate.
- notification.
- clock tick.
- branch created.

### 19.3 Overrides

Fallback chain:

1. Individual event override.
2. Selected sound theme.
3. Built-in default.

Every event should have a preview button.

### 19.4 Volumes

Separate sliders:

- Moves.
- Game Events.
- UI.
- Alerts.

### 19.5 Import

Support both:

- Individual audio file override.
- ZIP sound-pack import.

Recognize common filenames automatically.

Supported formats should include common Android-decodable formats such as:

- WAV.
- MP3.
- OGG.
- WEBM where supported.

If a ZIP contains multiple formats for the same event, import only one preferred representation rather than wasting storage on duplicates.

Allow custom packs to be:

- renamed.
- duplicated.
- exported.
- deleted.
- reset.

### 19.6 Copyright rule

Do not redistribute Chess.com sound files merely because public CDN URLs exist.

User-owned/private imports can be supported locally. Public LumenChess distributions should contain only assets whose redistribution rights are established.

---

## 20. Settings information architecture

Primary Settings categories:

- Appearance.
- Board & Pieces.
- Gameplay.
- Engines.
- Game Review.
- Ratings.
- Sounds & Haptics.
- Accounts & Imports.
- Explorer Data Sources.
- Storage.
- Accessibility.
- Advanced.

Portrait mobile uses category screens/cards.

Landscape/tablet/foldable can use a two-pane layout:

> Categories | Current settings page

Add Settings search.

---

## 21. Engine framework

### 21.1 Core rule

The app should talk to engines through one common interface.

Conceptually:

```text
ChessEngine
  configure()
  setPosition()
  startSearch()
  stopSearch()
  newGame()
  capabilities()
  state/events -> EngineResult
```

Higher layers should not care whether the engine is C++, Rust, or future neural inference.

### 21.2 Mandatory engines

#### Stockfish 18

Use as:

- primary analysis engine.
- playable opponent.
- review engine.
- possible Arena participant.

#### Reckless 0.9.0

Use as:

- playable opponent.
- Arena participant.
- optional analysis engine.

Reckless currently supports UCI, MultiPV, and Chess960 according to its official README.

### 21.3 Future engines

Adapters can later add:

- Maia-3.
- Komodo Dragon.
- Fairy-Stockfish.
- other UCI-compatible engines.

### 21.4 Android packaging

Do not design around casually downloading arbitrary executables to app-private writable storage and executing them.

Prefer native integration/packaged libraries appropriate for modern Android.

Exact per-engine JNI/native boundary should be validated during the implementation spike.

### 21.5 Engine lifecycle

Requirements:

- Engine crash must not crash the app.
- Search cancellation must be deterministic.
- Old search output must never apply to a newer board position.
- Engine instance ownership must be explicit.
- Background engines should release resources when no longer needed.
- Engine search must remain off the UI thread.

### 21.6 Capability model

Each engine adapter should expose capabilities such as:

- Standard.
- Chess960.
- MultiPV.
- Native Elo limiting.
- Threads.
- Hash.
- Syzygy.
- Variant support.

The UI should hide unsupported controls rather than showing broken settings.

---

## 22. Chess core

Separate core module(s) should own:

- Board representation.
- Position state.
- Legal move generation.
- Standard rules.
- Chess960 rules.
- Check/checkmate/stalemate.
- Draw rules.
- Repetition.
- Fifty-move rule.
- Insufficient material.
- Castling.
- En passant.
- Promotion.
- FEN.
- PGN.
- SAN.
- UCI move representation.
- Game termination.

The UI must never be the authoritative source of legal moves.

### 22.1 Chess960

Chess960 support is included early specifically to prevent hardcoding Standard-only assumptions.

Important areas:

- randomized legal start generation.
- castling semantics.
- FEN/X-FEN/Shredder-FEN considerations where required.
- PGN metadata.
- engine `UCI_Chess960` configuration.

---

## 23. Data model

Use one canonical game model for:

- Local play.
- Arena.
- Chess.com imports.
- Lichess imports.
- PGN imports.
- Branches.

Conceptually:

```text
Game
├── id
├── variant
├── initialPosition
├── moves
├── clocks
├── result / termination
├── players / engines
├── metadata
├── sourceLinks[]
├── annotations
├── variations
├── opening
├── reviews[]
├── ratingSnapshots
├── compactStats
└── protection/favorite state
```

### 23.1 Review record

```text
Review
├── modelVersion
├── engineId + version
├── engineSettings
├── status
├── perMoveEvaluations
├── classifications
├── accuracy
├── performanceRating
├── phaseStats
├── graph
├── explanations
└── technicalMetrics
```

### 23.2 Branch model

Branches should be separate sandbox sessions by default and optionally attach to a game's variation tree.

Keep clear distinction between:

- original historical game.
- analysis variation.
- independent branch session.

---

## 24. Offline-first data flow

For network-enriched repositories:

- Local database is the canonical source used by UI.
- Network results update local data.
- UI observes local data.

Examples:

- Explorer.
- Chess.com sync.
- Lichess sync.
- cached remote ratings.
- cloud evaluations.

This prevents connection state from producing two completely different app behaviors.

---

## 25. Background work

Use persistent queues for:

- account sync.
- queued Game Reviews.
- optional offline-pack downloads/indexing.
- cache maintenance.

Constraints should support:

- Wi-Fi only.
- charging only.
- battery threshold.
- retry/backoff.
- cancellation.

Heavy analysis should not unexpectedly run in the background without user-configurable conditions.

---

## 26. Performance and thermal behavior

The Pixel 8 Pro is the first tuning target, but the app should behave like a good Android citizen.

### 26.1 Engine resource profiles

Provide sensible presets for analysis/play, possibly mapping to:

- Low power.
- Balanced.
- Maximum.

### 26.2 Thermal/battery concerns

Consider:

- thread limits.
- hash limits.
- adaptive analysis time.
- avoiding simultaneous unnecessary engines.
- pausing/degrading background analysis under severe thermal pressure.
- user-visible battery/thermal warnings only when useful, not constant nagging.

### 26.3 UI performance

Targets:

- board interaction should remain smooth even while engine works.
- no engine parsing/search work on main thread.
- avoid recomposing the full board every engine info update.
- benchmark move animations and scrolling on Pixel 8 Pro.
- add Baseline Profiles after major flows stabilize.

---

## 27. Reliability rules

Treat these as non-negotiable:

- Chess state is deterministic.
- Chess rules are independently tested.
- Engine output cannot mutate stale positions.
- Every long-running operation is cancellable.
- Partially completed reviews can resume.
- Engine crashes are isolated.
- Database migrations are tested.
- Imports are validated before persistence.
- User games are never silently destroyed to reclaim cache.
- Rotation/backgrounding/process recreation are handled appropriately.
- Humanization randomness can be seeded in tests.
- Stockfish and Reckless satisfy the same application-facing engine contract.

---

## 28. Testing strategy

### 28.1 Chess core tests

Must include:

- Perft validation for Standard positions.
- Chess960 castling edge cases.
- FEN parsing/serialization round trips.
- PGN parse/export round trips.
- SAN generation/parsing.
- repetition.
- draw conditions.
- en passant.
- promotion.
- checkmate/stalemate.

### 28.2 Engine adapter tests

Test:

- initialization.
- new game.
- position sync.
- search.
- stop/cancel.
- MultiPV.
- Chess960 flags.
- engine crash/restart.
- stale output rejection.

### 28.3 Clock tests

Test with deterministic/fake time source:

- increment.
- premove 100 ms cost.
- pause/resume.
- manual Arena opening clock pause.
- process/background transitions.
- timeout.

### 28.4 Review tests

Maintain a corpus of known positions/games for:

- classification stability.
- brilliant candidate behavior.
- great/only-move cases.
- misses.
- threshold reanalysis.
- score consistency across app versions.

Version changes should be explicit.

### 28.5 Import/dedupe tests

Test:

- same game from PGN + Chess.com.
- same game with richer annotations.
- same moves from different metadata where not actually same historical game.
- Chess960 starting positions.
- malformed PGN.

### 28.6 UI tests

Critical flows:

- Play setup → game → end screen → review.
- Arena setup → takeover → return to engine → branch.
- import → library → analysis.
- Analysis Moves ↔ Explorer.
- Settings theme preview.
- sound ZIP import.

### 28.7 Performance tests

Measure on Pixel 8 Pro:

- launch.
- board input latency.
- engine-start latency.
- analysis throughput.
- review throughput.
- Room query latency for large library.
- Explorer cache lookup.

---

## 29. Development order

Do not start by building every polished screen.

Recommended implementation sequence:

1. **Project foundation / module skeleton**.
2. **Chess Core**.
3. **Persistence / canonical Game model**.
4. **Board UI**.
5. **Engine framework**.
6. **Stockfish integration**.
7. **Reckless integration**.
8. **Clocks + runtime state machine**.
9. **Human vs Engine Play**.
10. **Engine Arena**.
11. **PGN import/export + Library**.
12. **Analysis**.
13. **Game Review**.
14. **Opening identification + Explorer**.
15. **Chess.com / Lichess import/sync**.
16. **Ratings + Match Your Elo**.
17. **Insights**.
18. **Themes / pieces / backgrounds**.
19. **Sounds / haptics / import**.
20. **Polish / accessibility / performance / thermal tuning**.
21. **Broader Android compatibility**.
22. **Additional engines/variants**.

Each phase should have tests and explicit acceptance criteria before the next major subsystem depends on it.

---

## 30. Suggested module boundaries

Conceptual structure:

```text
LumenChess
│
├── app
│
├── core-chess
│   ├── board
│   ├── rules-standard
│   ├── rules-chess960
│   ├── notation
│   └── game-state
│
├── core-engine
│   ├── engine-api
│   ├── stockfish
│   ├── reckless
│   ├── strength
│   └── scheduler
│
├── core-runtime
│   ├── clocks
│   ├── premoves
│   ├── player-control
│   └── branching
│
├── core-review
│   ├── evaluation
│   ├── classification
│   ├── accuracy
│   ├── performance-rating
│   ├── phase-detection
│   └── explanations
│
├── core-explorer
│   ├── opening-names
│   ├── local-explorer
│   ├── lichess-explorer
│   └── cache
│
├── data
│   ├── room
│   ├── settings
│   ├── sync
│   └── imports
│
├── feature-play
├── feature-arena
├── feature-games
├── feature-review
├── feature-analysis
├── feature-explorer
├── feature-insights
├── feature-settings
│
└── design-system
```

Exact Gradle module count can be adjusted to avoid pointless micro-modules, but the **responsibility boundaries** should stay clear.

---

## 31. Error handling UX

Examples:

### Engine failure

- Preserve current game.
- Pause clock if necessary.
- Show concise failure.
- Offer Restart Engine / Choose Different Engine / Save Game.

### Failed review

- Keep completed analysis.
- Mark review partial.
- Retry from failed point.

### Sync failure

- Do not remove local games.
- Retry with backoff.
- Show last successful sync.

### Invalid PGN/FEN

- Show exact validation issue where possible.
- Do not silently import truncated/corrupt game.

### Offline Explorer

- Immediately use local/cached data.
- Do not show a blocking failure just because live enrichment is unavailable.

---

## 32. Security and privacy

- No server is required for core functionality.
- Account tokens, if used later, should be stored with appropriate Android secure-storage practices.
- Do not upload private games merely to provide local analysis.
- Make online data sources obvious and disableable.
- Custom imported assets should remain local unless user explicitly exports/shares them.

---

## 33. Licensing / redistribution guardrails

### Stockfish

Open source; preserve license obligations when integrating/distributing.

### Reckless

AGPLv3 according to its official repository. Distribution architecture must satisfy its license obligations.

### Lichess opening names

The `lichess-org/chess-openings` dataset is CC0/public-domain dedicated and is suitable for local opening-name data.

### Chess.com screenshots/sounds

Chess.com UI screenshots and scraped sound files used in planning are **reference/private-use material**, not assets that should automatically be included in a public APK or public Git repository.

LumenChess should create its own UI and ship only sounds/pieces/graphics with known redistribution rights.

---

## 34. Research-backed implementation notes

These are time-sensitive and should be rechecked when implementation begins.

### Stockfish

As of this spec, Stockfish 18 is the current major release (2026-01-31).

### Reckless

As of this spec, Reckless v0.9.0 is the latest listed release in its official README (2026-03-01). The README lists UCI, MultiPV, and Chess960 support.

### Android

Android 17 corresponds to API level 37. Android's current architecture guidance recommends Compose for UI, unidirectional data flow, local source-of-truth patterns for offline-first apps, Room for structured local data, and WorkManager for persistent queued work.

### Rating systems

- Lichess uses Glicko-2.
- Chess.com uses Glicko-1 according to Lichess's rating-system reference; Chess.com's own help calls it the Glicko system and discusses RD.
- FIDE uses Elo with current FIDE-specific K-factor and publication rules.

### Explorer

Lichess provides:

- Opening Explorer for Lichess games.
- Masters Explorer.
- player-specific Explorer.
- cloud evaluation endpoint for occasional position lookups.
- downloadable game/evaluation databases for bulk/offline processing.

Do not assume current authentication/rate-limit details will remain unchanged; verify at implementation time.

---

## 35. Acceptance criteria for the first cohesive milestone

A good first end-to-end milestone is reached when, on the Pixel 8 Pro:

1. App launches into Play with polished dark/blue UI.
2. User can choose Standard or Chess960.
3. User can choose Stockfish or Reckless.
4. User can choose side, Elo, Strength Model, and time control.
5. User can play a complete legal game with clocks and premoves.
6. Game saves to Library.
7. Engine crash/cancel paths do not destroy the game.
8. PGN export round-trips correctly.
9. Basic Analysis can navigate moves and evaluate current position.
10. No obvious UI jank while engine is thinking.

Game Review, Explorer, and sync can then build on a proven runtime rather than hiding bugs under polished screens.

---

## 36. Final locked decisions summary

- Fully offline-first foundation.
- Pixel 8 Pro / Android 17 / ARM64 optimization first.
- Kotlin + Jetpack Compose.
- Mandatory initial engines: Stockfish + Reckless.
- Standard + Chess960 first.
- Hybrid strength model default; Native and Humanized optional.
- Engine Elo 400–3000 in 50-point steps + Full Strength.
- Match Your Elo with variable range, default ±100.
- Local Performance Estimate default; Rated Results optional.
- Glicko-2, Glicko-1/Chess.com-style, and FIDE Elo rating options.
- Play setup simple first, Advanced expandable.
- Engine Arena is a main tab.
- Arena supports opening setup, manual opening moves, mid-game takeover, and branching.
- Branches are separate sandbox sessions by default; Save as Variation attaches them.
- Tap + drag both enabled by default.
- Premoves enabled; single premove default; multiple premoves optional; default 100 ms cost.
- Live playing screen intentionally uncluttered.
- Human-vs-engine eval/lines off by default.
- Arena eval bar on by default.
- End Screen default after game; Auto-open Review optional.
- Review starts background analysis where appropriate.
- Review presets Fast / Balanced / Deep / Custom; Balanced default.
- Analyze both players by default; Guided Review focuses on user's key moments.
- Review has all requested move classifications.
- Accuracy + Game Rating simple by default; Technical Stats expandable.
- Review algorithms/version stored; Reanalyze with Latest Model available.
- No coach avatar initially.
- Guided Review uses horizontal classified move rail.
- Analysis uses full scrollable move list.
- Analysis Explorer button swaps lower pane between Moves and Explorer.
- Explorer offline-first, enriched by Lichess online data and cached.
- Unified Games Library.
- Manual + optional automatic account sync.
- Auto-review synced games off by default, with Wi-Fi/charging/battery constraints.
- Recent games keep full analysis; older games archive compact stats/PGN.
- Local/manual games are never auto-deleted by default.
- Automatic deduplication with metadata merge.
- General Insights rather than exhaustive statistics.
- Main nav: Play / Arena / Games / Insights / Settings.
- Default app accent blue; accent configurable.
- Board/Pieces/Background/Presets tabs with live preview.
- Sound themes support full-pack ZIP import and individual event overrides.
- Public builds must not bundle third-party proprietary reference assets without rights.

---

## 37. Next step

This document should be reviewed once as a whole. After approval, create a **detailed implementation plan** broken into small milestones/tasks with explicit test gates before writing production code.

Do not let implementation casually override locked product decisions in this spec. If a decision must change for technical reasons, document the reason and update this spec/decision log first.
