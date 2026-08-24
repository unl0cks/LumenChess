# P5 — Reference-Fidelity Visual Identity & Motion Pass

**Status:** Approved corrective design contract; implementation pending
**Date:** 2026-08-17
**Base:** `de2fe5e7c3d4096527a4e24602f68144c4b2d67c`
**Branch:** `p5-reference-fidelity-visual-identity`
**Scope:** presentation-only unless a genuine regression requires otherwise
**Blocked:** M20
**Promotion policy:** never promote P5 automatically; physical visual approval is mandatory

## 1. Goal

P5 corrects the remaining visual identity failure after P1–P4. The app is technically green, but it still reads as a generic Material/Compose Android app rather than the approved LumenChess client.

The finished application must be immediately recognizable as the same product represented in the approved concept references.

Primary visual reference:

- `LumenChess_UI_Concept_Blue.png`

Secondary reference for layout-vs-accent separation:

- `ChatGPT Image Aug 15, 2026, 12_26_20 PM.png`

The blue reference defines the primary identity. The green version demonstrates that the design system must remain accent-swappable without changing component geometry.

## 2. Reference inspection findings

The reference images were inspected directly, including enlarged crops of the New Game and active-game screens.

### 2.1 Sampled visual family

Representative sampled values from the blue concept:

- default board light square: approximately `#E7E6C8`
- default board dark square: approximately `#4E8191`
- primary CTA gradient family: approximately `#3D748A` through `#5790A5`
- shell background: near-black neutral graphite, approximately `#0E1011`–`#111315`
- primary panel: approximately `#17191B`–`#1B1E20`
- raised/input panel: approximately `#202326`
- hairline border: approximately `#33373B`
- primary text: soft white near `#F2F3F3`

The accent must therefore move away from the current saturated royal Material blue and toward muted steel/cyan illumination.

### 2.2 Geometry and density

The reference consistently uses:

- compact 8–12dp panel/control radii;
- 1dp borders instead of large tonal fills;
- tight 6–10dp related-control spacing;
- ~14–18dp horizontal screen margins;
- ~10–14dp panel padding;
- compact application headers instead of marketing hero text;
- bottom navigation with small icon + label tint, not a selected pill;
- high information density while retaining 48dp semantic hit targets.

### 2.3 Board and pieces

The board is the visual anchor on the active game screen. It is nearly full-width, minimally framed, and uses warm ivory + steel blue rather than cold gray/navy.

The default piece family is substantial and classical rather than icon-like. Major pieces occupy roughly 76–84% of square height; pawns remain somewhat smaller but still visually weighted. White pieces use warm ivory with graphite separation. Black pieces use near-black graphite with restrained edge light.

## 3. Existing implementation problems to replace

Current P2/P3 presentation code still exposes stock Material behavior in several places:

- theme delegates heavily to `MaterialTheme` color schemes and `Shapes`;
- current shape tokens extend through 16/20/28dp;
- `LumenButton` is still a stock Material `Button` with stock `ButtonDefaults`;
- setup uses stock Material `Slider` and `Button`;
- selected segmented states use large accent-filled surfaces;
- Settings has giant marketing headings and architecture copy;
- Settings categories and setup groups commonly use 17–18dp cards;
- bottom navigation uses a 38×28dp selected capsule;
- future surfaces use giant centered 22dp cards and developer-style preview copy.

P5 must replace those visual defaults without disturbing runtime authority or game state.

## 4. Approaches considered

### A. Centralized Lumen design system + targeted screen refactor — selected

Build stable Lumen tokens/components first, then migrate the existing screens onto them.

**Advantages:** coherent identity, future M20+ screens inherit the correct language, easier screenshot QA, prevents stock Material regression.

**Cost:** more up-front component work.

### B. Restyle each screen locally

Change colors/radii/padding directly in Play, Settings and customization screens.

**Advantage:** faster initially.

**Rejected because:** guarantees drift and makes future screens fall back into stock Material again.

### C. Replace most Compose primitives with a bespoke rendering layer

**Advantage:** maximum control.

**Rejected because:** unnecessary risk to accessibility, input behavior and existing test coverage. Compose remains the implementation technology; P5 replaces the visual language, not the framework.

## 5. Design-system architecture

Create/expand reusable presentation primitives so screens do not style themselves ad hoc.

Required component family:

- `LumenPanel`
- `LumenListRow`
- `LumenPrimaryButton`
- `LumenSecondaryButton`
- `LumenDangerButton`
- `LumenIconButton`
- `LumenSegmentedControl`
- `LumenDropdownRow`
- `LumenSlider`
- `LumenTabs`
- `LumenBottomNavigation`
- `LumenTopBar`
- `LumenEngineBadge`
- `LumenClock`
- `LumenSettingRow`

Material components may remain internal primitives where useful, but their stock shape, state layer, padding, typography, indicator and elevation must not leak through.

### 5.1 Token families

Add explicit centralized tokens for:

- color;
- typography;
- shape;
- border;
- spacing/density;
- minimum semantic hit target;
- motion durations/easing/spring;
- active/pressed state luminance and scale.

Visible controls may be compact while their interaction wrapper preserves a minimum 48dp target.

### 5.2 Motion tokens

Use four central timing classes:

- Instant: 60–90ms
- Fast: 110–150ms
- Normal: 170–220ms
- Large navigation/sheet: 220–280ms

Buttons press to approximately 0.98 scale, darken slightly, brighten the active border, then return over a damped fast/normal release. Icon buttons press to approximately 0.93–0.95. Avoid giant Material ripples.

## 6. Screen composition

### 6.1 Play / New Game

Remove marketing hierarchy (`LUMEN PLAY`, `Human vs Engine`, marketing subtitle).

Use compact `New Game` top bar and dense reference composition:

1. Game Mode segmented row: Standard / Chess960.
2. Opponent dropdown row with engine badge.
3. Strength label/value + thin custom slider + endpoints.
4. compact Match My Elo affordance where supported.
5. Strength Model segmented row: Hybrid / Engine Native / Humanized.
6. Side segmented row.
7. Time Control dropdown.
8. Inc / Delay dropdown where supported by current model.
9. compact full-width Start Game CTA.

Do not pull future functionality forward solely to mimic the mockup.

### 6.2 Active game

Board-first composition:

- compact opponent row immediately above board;
- dedicated dark clock surface at right;
- board nearly full usable width with minimal frame;
- compact human row immediately below board;
- clean compact action strip below;
- no default human-vs-engine evaluation clutter;
- no giant outlined Material action buttons.

Board geometry remains stable through human→engine transitions.

### 6.3 Settings main

Replace dashboard composition with compact list architecture.

Top bar: `Settings`.

Rows should visually match the concept: icon, title, short subtitle, chevron, restrained border.

Current/future categories can include Engines, Play/Appearance, Game Review, Ratings, Accounts & Sync, Advanced, Board & Pieces, Sounds & Haptics as appropriate to existing scope. Unavailable destinations use disabled/intentional product preview behavior, never milestone/developer copy.

Remove all runtime-boundary/architecture explanations from user-facing UI.

### 6.4 Appearance

Use a compact segmented selector or radio-row list for System / Dark / OLED / Light. Selected state uses accent outline, indicator and faint wash rather than large filled tiles.

### 6.5 Board & Pieces

Use compact top bar with arrow, large live preview, then an underline-style tab strip:

- Board
- Pieces
- Background
- Presets

Board rows include visual swatches. Piece rows include miniatures. Background rows include thumbnails. Presets are visual miniature compositions rather than text-only rows.

### 6.6 Sounds & Haptics

Use the same compact settings row language. Keep all P4 runtime feedback logic observer-only and unchanged unless required by a regression.

## 7. Board visual specification

Default Lumen Blue board:

- light: approximately `#E7E6C8`
- dark: approximately `#4E8191`

Preserve theme alternatives for OLED/Graphite/etc.

Board frame: square/minimally rounded, no giant card.

Highlights:

- last move: soft readable overlay;
- selected: slightly stronger controlled highlight;
- legal move: small translucent center dot;
- capture: ring/corner treatment;
- check: restrained red/radial king treatment.

Coordinates remain low-contrast and integrated into edge squares.

## 8. Piece redesign

Redraw the original project-owned Lumen vector set into a coherent Staunton-inspired family with stronger optical weight.

Targets:

- major pieces: ~76–84% square height;
- pawn: slightly smaller but substantial;
- consistent bases and family proportions;
- no Unicode glyphs;
- no external proprietary art;
- warm ivory white pieces;
- graphite black pieces;
- clean outline/shadow separation;
- high-quality vector edges and no clipping.

`Lumen Outline` remains a secondary geometric/outlined set. The solid `Lumen` set is the polished default.

## 9. Navigation

Bottom navigation visual content height: approximately 52–60dp excluding system inset.

Each tab:

- 18–22dp coherent-stroke icon;
- 10–11sp label;
- neutral gray when inactive;
- steel/cyan accent when active;
- optional thin line/tiny luminous marker;
- no selected pill/circle.

Selection transitions change tint and subtle scale without changing layout bounds.

Subpages use compact arrow + title top bar and ~8–12dp fade/slide navigation motion.

## 10. Theme variants

### Dark

Near-black graphite shell, charcoal panels, steel/cyan accent, subtle borders.

### OLED

True black base with near-black bordered surfaces so hierarchy remains visible.

### Light

Cool/off-white environment with gray-blue surfaces, graphite text and the same steel/cyan accent. It must remain Lumen-branded rather than stock Material white/blue.

## 11. Authority boundaries

P5 is presentation-only.

Do not alter:

- chess legality;
- `Position`/`GameTree` authority;
- clock authority;
- engine move application;
- premove execution semantics;
- persistence authority;
- P4 feedback observer semantics.

Sound/haptic presentation remains failure-isolated and observer-only.

## 12. Automated structural regressions

Retain existing geometry/accessibility tests and add/strengthen coverage for:

- board bounds stable through human→engine turn;
- board preview occupies an explicitly large fraction of available width;
- bottom-nav geometry does not resize when selection changes;
- segmented controls do not change bounds when selection changes;
- compact visual controls retain 48dp semantic/touch targets;
- Pixel 8 Pro screens do not overflow at normal scale;
- reasonable enlarged text behavior;
- reference-critical screen titles do not regress into giant hero hierarchy;
- no user-facing milestone/developer architecture copy.

## 13. Mandatory visual QA

Before P5 can be called complete, capture fixed Pixel 8 Pro/API37 screenshots for at least:

1. Play overview/setup
2. active game vs Stockfish
3. active game vs Reckless
4. Settings main
5. Board customization
6. Pieces customization
7. Background customization
8. Presets
9. Sounds & Haptics
10. Light theme
11. OLED theme

Compare directly against both supplied concept images and record:

- hierarchy;
- density;
- card radius;
- navigation;
- accent use;
- typography;
- board scale;
- piece scale;
- spacing;
- Settings composition.

If any primary screenshot still reads as generic Material 3 before it reads as the supplied Lumen concept, continue iterating.

## 14. Completion gate

P5 completion sequence:

1. proportional P5 tests;
2. exact-SHA visual screenshot set + QA report;
3. complete cumulative M0–M19 + P1–P5 branch gate;
4. verify engine/runtime/persistence remain green;
5. do **not** promote;
6. build ARM64-only Pixel 8 Pro APK from exact green P5 SHA;
7. publish artifact and screenshots;
8. STOP and wait for explicit physical visual approval.

Only the exact user-approved P5 APK SHA may later be fast-forwarded to `main`.

M20 remains blocked until that approval and promotion.
