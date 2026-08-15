# LumenChess — Core UI Flows

## 1. Play

`Play tab`  
→ choose Standard/Chess960  
→ choose Stockfish/Reckless  
→ Elo or Match Your Elo  
→ time control  
→ White/Black/Random  
→ optional Advanced  
→ Start Game  
→ clean live game screen  
→ End Screen  
→ Review / Rematch / New / Export / Share

## 2. Engine Arena

`Arena tab`  
→ White engine + strength  
→ Black engine + strength  
→ time control + mode  
→ opening setup  
→ optional manual opening control  
→ Start Arena  
→ live engine-v-engine screen  
→ optional Pause / Take Over / Return to Engine / Branch  
→ end  
→ Review / Analysis / Branch / Save

## 3. Branching

Open any game/review/analysis  
→ navigate to a move  
→ Branch From Here  
→ play alternative move  
→ choose continuation controller(s)  
→ sandbox session  
→ optionally Save as Variation

Original game stays untouched unless explicitly saved as a variation.

## 4. Game Review

Open finished game  
→ summary with graph, accuracy, classifications, Game Rating, phase stats  
→ Start Review  
→ Guided Review  
→ horizontal classified move rail  
→ explanation card + board arrows  
→ Show Best / Retry / Branch From Here / Next  
→ optional Full Review / Key Moments Only

No coach avatar in initial version.

## 5. Analysis

Open game/position  
→ full Analysis screen  
→ evaluation bar + board + move list  
→ optional Assistance toggles  
→ tap move to navigate  
→ long-press move for branch/FEN/comment/variation/deeper analysis  
→ Explorer button swaps lower pane from Moves to Opening Explorer  
→ Moves button swaps back

## 6. Explorer

Analysis → Explorer  
→ local opening data appears immediately  
→ optional online Lichess enrichment updates values  
→ tap candidate move  
→ board advances temporarily  
→ explore deeper  
→ Back traverses previous position  
→ cached results remain usable offline

## 7. Games / Library

`Games tab`  
→ filters: All / Local / Arena / Chess.com / Lichess / Imported / Branches / Favorites  
→ tap game → open  
→ long-press → Review / Analyze / Branch / Export / Favorite / Protect / Delete

Imports:
- paste PGN
- open PGN
- paste Chess.com URL
- paste Lichess URL
- account/username sync

## 8. Insights

`Insights tab`  
→ filters for game type/time/source/variant/date/rated  
→ overview cards  
→ trend graph  
→ move quality  
→ phase stats  
→ openings  
→ time controls  
→ tap stat where possible to view underlying games

## 9. Board & Pieces

Settings → Board & Pieces  
→ tabs Boards / Pieces / Background / Presets  
→ choose visually from grid  
→ live preview updates immediately  
→ deeper board/effect/movement controls below or in related sections  
→ Save

## 10. Sounds

Settings → Sounds & Haptics  
→ select theme + preview  
→ optional individual event overrides  
→ import individual file or ZIP pack  
→ automatic event-name detection  
→ preview each event  
→ save/export custom pack
