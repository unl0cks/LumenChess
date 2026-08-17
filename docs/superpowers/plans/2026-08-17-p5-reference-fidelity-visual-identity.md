# P5 Reference-Fidelity Visual Identity & Motion Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the remaining stock-Material visual language with a compact, reference-faithful LumenChess design system while preserving all M0–M19 and P1–P4 runtime behavior.

**Architecture:** Keep Jetpack Compose as the rendering/input framework but move visual authority into dedicated Lumen tokens and reusable primitives. Migrate Play, live game, navigation, Settings, customization, and feedback screens onto those primitives, then verify the resulting UI with API37 screenshots against the supplied concept images before the cumulative branch gate and ARM64 APK build.

**Tech Stack:** Kotlin, Jetpack Compose, Material3 only as low-level primitive where useful, AndroidX Compose UI tests, DataStore-backed existing appearance settings, GitHub Actions, API37 emulator, project-owned vector chess pieces.

## Global Constraints

- Branch: `p5-reference-fidelity-visual-identity`.
- Approved branch root: `de2fe5e7c3d4096527a4e24602f68144c4b2d67c`.
- Approved spec commit before planning: `c944025b1af9a717d07c3273e2c8dda111fef7ed`.
- Primary visual reference: `LumenChess_UI_Concept_Blue.png`.
- Secondary structural/accent reference: `ChatGPT Image Aug 15, 2026, 12_26_20 PM.png`.
- P5 is presentation-only unless a genuine regression requires otherwise.
- Do not change chess legality, GameTree/Position authority, clocks, engine move authority, persistence authority, premove semantics, or P4 feedback observer authority.
- Do not begin M20.
- Do not promote P5 to `main` until the user explicitly approves the physical P5 APK.
- Default Lumen Blue board targets warm ivory `#E7E6C8` and muted steel/cyan `#4E8191` from the primary reference.
- Visual controls may be compact but semantic/touch targets remain at least 48dp.
- Avoid giant Material ripple/pill/capsule selected states.
- Mandatory visual QA uses fixed Pixel 8 Pro/API37 screenshots and direct inspection against both concept images.

---

## File structure locked by this plan

### Design system
- Modify `app/src/main/java/dev/lumenchess/design/LumenTheme.kt` — palette, typography, shape and spacing-compatible Material bridge.
- Replace/expand `app/src/main/java/dev/lumenchess/design/LumenControls.kt` — shared control primitives and interaction state.
- Create `app/src/main/java/dev/lumenchess/design/LumenMotion.kt` — centralized motion durations/easing/spring tokens.
- Create `app/src/main/java/dev/lumenchess/design/LumenSurfaces.kt` — panels, rows, tabs, top bars, clocks, badges, switches and visual state helpers.

### Navigation and shell
- Modify `app/src/main/java/dev/lumenchess/ui/LumenNavigation.kt` — compact nav geometry, active marker, future preview copy.
- Modify `app/src/main/java/dev/lumenchess/ui/LumenChessApp.kt` — animated tab/subpage content while keeping stable bottom-bar bounds.

### Play and board
- Modify `app/src/main/java/dev/lumenchess/play/PolishedPlayRoute.kt` — dense New Game and board-first live layout using Lumen primitives.
- Modify `app/src/main/java/dev/lumenchess/board/ChessboardModels.kt` — reference-derived default board/highlight palette.
- Modify `app/src/main/java/dev/lumenchess/board/LumenChessboard.kt` — piece movement/drag visual motion without board remeasurement.
- Modify `app/src/main/java/dev/lumenchess/board/LumenVectorPieces.kt` — production default Staunton-inspired vector family and optical occupancy.
- Modify `app/src/main/java/dev/lumenchess/customization/BoardThemeCatalog.kt` — reference-derived default board definition/copy.

### Settings/customization/feedback
- Modify `app/src/main/java/dev/lumenchess/settings/SettingsScreen.kt` — compact Settings list and appearance control.
- Modify `app/src/main/java/dev/lumenchess/settings/BoardAppearanceScreen.kt` — compact top bar, larger preview, underline tabs, visual option rows.
- Modify `app/src/main/java/dev/lumenchess/settings/BoardPreview.kt` — responsive large preview rather than fixed 204dp board.
- Modify `app/src/main/java/dev/lumenchess/settings/SoundsHapticsScreen.kt` — compact rows, no architecture copy, custom Lumen controls.

### Structural regressions
- Modify `app/src/androidTest/java/dev/lumenchess/design/LumenControlsAccessibilityTest.kt`.
- Modify `app/src/androidTest/java/dev/lumenchess/ui/LumenNavigationTest.kt`.
- Modify `app/src/androidTest/java/dev/lumenchess/settings/SettingsUiTest.kt`.
- Modify `app/src/androidTest/java/dev/lumenchess/settings/SoundsHapticsUiTest.kt`.
- Modify `app/src/androidTest/java/dev/lumenchess/play/PlayUiIntegrationTest.kt` and/or `PlayM19IntegrationTest.kt` only where presentation tags/geometry assertions belong.
- Modify `app/src/androidTest/java/dev/lumenchess/board/LumenChessboardTest.kt` only for non-authoritative motion/geometry regression coverage.
- Create `app/src/androidTest/java/dev/lumenchess/design/P5ReferenceStructureTest.kt` for compact-title/copy/geometry expectations.

### CI / QA records
- Create `.github/workflows/p5-reference.yml` — exact checkpoint screenshot/cumulative visual verification trigger without changing prior gates.
- Create `docs/verification/P5-VISUAL-QA.md` after screenshot review.
- Create `docs/verification/P5-CHECKPOINT.md` at the final immutable P5 checkpoint.

---

### Task 1: Add failing P5 structural regression tests

**Files:**
- Create: `app/src/androidTest/java/dev/lumenchess/design/P5ReferenceStructureTest.kt`
- Modify: `app/src/androidTest/java/dev/lumenchess/design/LumenControlsAccessibilityTest.kt`
- Modify: `app/src/androidTest/java/dev/lumenchess/ui/LumenNavigationTest.kt`
- Modify: `app/src/androidTest/java/dev/lumenchess/settings/SettingsUiTest.kt`

**Interfaces:**
- Consumes existing test tags from Play, navigation, Settings and customization.
- Produces regression expectations for compact headers, no marketing/architecture copy, stable navigation geometry and 48dp semantic hit targets.

- [ ] **Step 1: Write tests that assert the old presentation is rejected**

Tests must check all of the following:

```kotlin
composeRule.onAllNodesWithText("Make LumenChess yours").assertCountEquals(0)
composeRule.onAllNodesWithText("Human vs Engine").assertCountEquals(0)
composeRule.onAllNodes(hasText("presentation-only", substring = true)).assertCountEquals(0)
composeRule.onAllNodes(hasText("later milestone", substring = true)).assertCountEquals(0)
```

Add geometry assertions through explicit P5 test tags for top bars, nav item bounds, segmented controls and board preview. The selected/unselected navigation state must report identical bounds after tab changes.

- [ ] **Step 2: Run the proportional app instrumentation job and confirm failure on the pre-P5 UI**

Use the existing API37 instrumentation workflow or a temporary test-only commit. Expected result: FAIL on at least the removed hero/developer-copy or nav-geometry assertions.

- [ ] **Step 3: Commit tests only**

Commit message: `test(P5): lock reference-fidelity structure`.

---

### Task 2: Centralize Lumen tokens and motion

**Files:**
- Modify: `app/src/main/java/dev/lumenchess/design/LumenTheme.kt`
- Create: `app/src/main/java/dev/lumenchess/design/LumenMotion.kt`

**Interfaces:**
- Produces `LumenColors`, `LumenDimensions`, `LumenRadii`, `LumenSpacing`, and `LumenMotion` values used by all later tasks.

- [ ] **Step 1: Replace royal-blue/dark-navy defaults with reference-derived graphite/steel palette**

Dark base targets:

```kotlin
accent = Color(0xFF4F879B)
accentBright = Color(0xFF68A3B7)
background = Color(0xFF0F1112)
surface = Color(0xFF181A1C)
surfaceRaised = Color(0xFF202326)
surfaceHighest = Color(0xFF272A2D)
outline = Color(0xFF35393C)
onSurface = Color(0xFFF2F3F3)
onSurfaceMuted = Color(0xFFA4AAAD)
```

Preserve custom accent settings by deriving soft/bright variants from the persisted accent rather than hard-coding reference blue into every component.

- [ ] **Step 2: Tighten Material bridge typography/shapes**

Set normal screen titles to ~20sp, section/control hierarchy to 12–16sp, nav label to 10–11sp. Keep Material `Shapes` as a fallback bridge with ordinary radii near 8/10/12/14dp instead of 16/20/28dp.

- [ ] **Step 3: Add motion tokens**

```kotlin
object LumenMotion {
    const val InstantMs = 75
    const val FastMs = 130
    const val NormalMs = 190
    const val LargeMs = 240
    val PressScale = 0.98f
    val IconPressScale = 0.94f
}
```

Use `FastOutSlowInEasing`/`LinearOutSlowInEasing` and a low-bounce spring for releases; no large overshoot.

- [ ] **Step 4: Run design/app unit compilation and commit**

Commit message: `feat(P5): establish reference-derived Lumen tokens`.

---

### Task 3: Build reusable Lumen presentation primitives

**Files:**
- Replace/expand: `app/src/main/java/dev/lumenchess/design/LumenControls.kt`
- Create: `app/src/main/java/dev/lumenchess/design/LumenSurfaces.kt`
- Modify test: `app/src/androidTest/java/dev/lumenchess/design/LumenControlsAccessibilityTest.kt`

**Interfaces:**
- Produces reusable composables:
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
  - `LumenTopBar`
  - `LumenEngineBadge`
  - `LumenClock`
  - `LumenSettingRow`
  - `LumenToggle`

- [ ] **Step 1: Implement a shared press-state modifier**

Use `MutableInteractionSource.collectIsPressedAsState()` and `animateFloatAsState` to scale the visible surface while wrapping it in a minimum 48dp semantic target. Suppress stock unbounded ripple; use bounded low-alpha wash plus luminance/border change.

- [ ] **Step 2: Implement bordered panels and list rows**

Normal panel: `Surface`/`Box` with `LumenColors.Surface`, 1dp `LumenColors.Outline`, 10dp radius, 10–14dp internal padding. Selected: 1dp accent border and faint accent wash, never a solid accent tile.

- [ ] **Step 3: Implement primary/secondary/danger buttons**

Primary uses a compact 46–48dp visible rounded rectangle with steel/cyan vertical gradient, bright edge, white text and subtle glow. Secondary/danger remain dark outlined surfaces.

- [ ] **Step 4: Implement segmented control, tabs, dropdown row and slider**

Segments keep fixed geometry across selection. Slider uses a thin custom track and compact circular thumb. Tabs use tint + 2dp underline instead of filled cards.

- [ ] **Step 5: Implement top bar, clock, engine badge, setting row and toggle**

Top bar is arrow/icon + 18–22sp title. Clock is dedicated near-black rectangle. Toggle uses custom track/thumb motion with ~160ms transition while preserving semantics.

- [ ] **Step 6: Run accessibility tests and commit**

Expected: all clickable primitives expose >=48dp semantic bounds. Commit message: `feat(P5): add custom Lumen component system`.

---

### Task 4: Replace bottom navigation and shell transitions

**Files:**
- Modify: `app/src/main/java/dev/lumenchess/ui/LumenNavigation.kt`
- Modify: `app/src/main/java/dev/lumenchess/ui/LumenChessApp.kt`
- Modify: `app/src/androidTest/java/dev/lumenchess/ui/LumenNavigationTest.kt`

**Interfaces:**
- Consumes `LumenMotion`, compact colors and shared press state.
- Produces fixed-height 56dp visual nav, selected accent tint/indicator, and content-only tab transitions.

- [ ] **Step 1: Remove selected 38x28dp capsule**

Selected icon/label become accent colored; optional 2dp marker moves without changing item geometry.

- [ ] **Step 2: Normalize icon geometry**

All tab icons share the same 20dp box and stroke mass. Keep project-owned Canvas icons.

- [ ] **Step 3: Add page fade + 8–12dp directional slide**

Use `AnimatedContent`/`ContentTransform` around page content only, ~190ms. Bottom bar remains geometrically fixed.

- [ ] **Step 4: Replace developer preview copy**

Future tabs show intentional product preview copy and no milestone/build-internal language.

- [ ] **Step 5: Run navigation geometry regression and commit**

Commit message: `feat(P5): replace Material-style navigation treatment`.

---

### Task 5: Rebuild Play/New Game setup against the reference

**Files:**
- Modify: `app/src/main/java/dev/lumenchess/play/PolishedPlayRoute.kt`
- Modify: `app/src/androidTest/java/dev/lumenchess/play/PlayUiIntegrationTest.kt`

**Interfaces:**
- Consumes Lumen top bar, panels, segments, dropdown rows, slider and primary button.
- Preserves existing `PlayViewModel` methods and setup model without adding future gameplay scope.

- [ ] **Step 1: Remove hero hierarchy**

Delete `LUMEN PLAY`, `Human vs Engine`, and marketing subtitle. Render compact `New Game` top bar.

- [ ] **Step 2: Replace giant setup groups with reference composition**

Game Mode as two compact segments; Opponent as one engine dropdown-style row; Strength as value + custom slider + endpoint labels; Strength Model and Side as compact segmented controls; Time Control as compact dropdown-style selector; Start Game as Lumen primary CTA.

- [ ] **Step 3: Keep existing data model honest**

Do not fake `Match My Elo` or increment/delay configuration if not currently implemented. Present only supported controls using reference grammar.

- [ ] **Step 4: Tighten spacing**

Horizontal 16dp, related 6–8dp, sections 14–18dp, no routine 24–32dp card padding.

- [ ] **Step 5: Run Play setup tests and commit**

Commit message: `feat(P5): rebuild New Game with compact Lumen grammar`.

---

### Task 6: Rebuild active-game composition without changing authority

**Files:**
- Modify: `app/src/main/java/dev/lumenchess/play/PolishedPlayRoute.kt`
- Modify: relevant Play instrumentation test for stable board stage.

**Interfaces:**
- Consumes `LumenClock`, `LumenEngineBadge`, compact action controls.
- Leaves `PlayViewModel`, `RuntimeState`, move/premove callbacks and board authority unchanged.

- [ ] **Step 1: Compact participant rows**

Reduce visual height to roughly 54–58dp while maintaining 48dp interactions. Use small engine/user badge, one-line name/detail and dedicated clock rectangle.

- [ ] **Step 2: Make board the visual anchor**

Use nearly full available width, minimal/no card wrapper, tiny 2–4dp edge treatment. Keep board stage bounds independent of turn state.

- [ ] **Step 3: Remove unnecessary dead status space**

Status appears only when non-empty or uses a compact overlay/line that does not steal permanent board height.

- [ ] **Step 4: Replace 64dp filled action tiles**

Use compact icon+label actions with neutral dark surfaces, subtle destructive treatment, and preserved semantics.

- [ ] **Step 5: Run P1 geometry and Play runtime instrumentation; commit**

Commit message: `feat(P5): make live play board-first and compact`.

---

### Task 7: Correct default board palette and interaction highlights

**Files:**
- Modify: `app/src/main/java/dev/lumenchess/board/ChessboardModels.kt`
- Modify: `app/src/main/java/dev/lumenchess/customization/BoardThemeCatalog.kt`
- Modify tests in `app/src/androidTest/java/dev/lumenchess/board/` if color/value assertions exist.

**Interfaces:**
- Produces the reference-faithful default Lumen Blue palette for all board consumers.

- [ ] **Step 1: Set default board colors**

Use light `#E7E6C8`, dark `#4E8191`, warm ivory white pieces and graphite black pieces.

- [ ] **Step 2: Retune overlays**

Selected/last move/legal/capture/check overlays remain readable but lower saturation than current Material-like accent colors. Keep coordinate readability.

- [ ] **Step 3: Run board presentation tests and commit**

Commit message: `feat(P5): match Lumen Blue board reference palette`.

---

### Task 8: Redesign the default Lumen vector pieces and piece motion

**Files:**
- Modify: `app/src/main/java/dev/lumenchess/board/LumenVectorPieces.kt`
- Modify: `app/src/main/java/dev/lumenchess/board/LumenChessboard.kt`
- Modify: `app/src/androidTest/java/dev/lumenchess/board/LumenChessboardTest.kt`

**Interfaces:**
- Keeps `PieceSet` interface and `LumenVectorPieceSet.id` stable.
- Produces larger Staunton-inspired original vector geometry and visual-only drag/tap/engine movement animation.

- [ ] **Step 1: Increase optical occupancy**

Redraw normalized coordinates so major-piece silhouettes span about 0.12–0.88 vertically with consistent bases near 0.86–0.90; pawn somewhat smaller. Preserve internal padding for stroke clipping.

- [ ] **Step 2: Improve classical silhouettes**

King: clear cross/crown/body; Queen: five-point/ball crown and tapered body; Bishop: mitre/slit; Knight: readable horse head/neck/chest; Rook: broad crenellations and tower body; Pawn: substantial head/neck/base. Use consistent base family and outlines/highlights.

- [ ] **Step 3: Improve white/black contrast**

White uses warm ivory body plus graphite edge/shadow. Black uses near-black body with cool subtle highlight/edge. Avoid Material-icon flatness.

- [ ] **Step 4: Add visual movement without board layout animation**

Drag scales piece ~1.05 and adds subtle shadow/illumination. Tap/engine move interpolates piece origin→destination ~150ms; capture target fades early. Illegal drag returns ~120ms. Board container constraints and measured size remain unchanged.

- [ ] **Step 5: Run board geometry/input tests and commit**

Commit message: `feat(P5): redraw Lumen pieces and add restrained piece motion`.

---

### Task 9: Rebuild Settings main and appearance controls

**Files:**
- Modify: `app/src/main/java/dev/lumenchess/settings/SettingsScreen.kt`
- Modify: `app/src/androidTest/java/dev/lumenchess/settings/SettingsUiTest.kt`

**Interfaces:**
- Preserves existing appearance/settings callbacks.
- Produces reference-like compact Settings list; unavailable future destinations remain non-functional/disabled product rows.

- [ ] **Step 1: Remove marketing and architecture copy**

Top is only compact `Settings` title/top bar.

- [ ] **Step 2: Add compact rows**

Use icon + title + subtitle + chevron. Board & Pieces and Sounds & Haptics remain working destinations. Engines/Play/Game Review/Ratings/Accounts & Sync/Advanced may be disabled/preview rows only where functionality is not present.

- [ ] **Step 3: Replace 2x2 appearance tiles**

Use compact `System | Dark | OLED | Light` segmented control or radio rows with outline/wash selected state.

- [ ] **Step 4: Run Settings tests and commit**

Commit message: `feat(P5): replace Settings dashboard with compact client list`.

---

### Task 10: Rebuild Board & Pieces customization

**Files:**
- Modify: `app/src/main/java/dev/lumenchess/settings/BoardAppearanceScreen.kt`
- Modify: `app/src/main/java/dev/lumenchess/settings/BoardPreview.kt`
- Modify: `app/src/androidTest/java/dev/lumenchess/settings/SettingsUiTest.kt`

**Interfaces:**
- Preserves appearance persistence and catalog IDs.
- Produces larger responsive board preview, compact underline tabs and visual catalog rows.

- [ ] **Step 1: Replace Back card with Lumen top bar**

Arrow icon button + `Board & Pieces` title.

- [ ] **Step 2: Make board preview responsive and large**

Use `BoxWithConstraints`; board size should target 70–85% of available phone width and be test-tagged for geometry. Do not use fixed 204dp.

- [ ] **Step 3: Replace four giant tab cards**

Use `LumenTabs` with stable geometry and 2dp selected underline.

- [ ] **Step 4: Add visual option affordances**

Board rows show 2x2 square swatch; Piece rows render miniature king/knight/rook from the actual piece set; Background rows show palette/gradient thumbnail; Presets show tiny board/piece/background composite.

- [ ] **Step 5: Run customization tests and commit**

Commit message: `feat(P5): make board customization visual and reference-dense`.

---

### Task 11: Rebuild Sounds & Haptics with compact settings grammar

**Files:**
- Modify: `app/src/main/java/dev/lumenchess/settings/SoundsHapticsScreen.kt`
- Modify: `app/src/androidTest/java/dev/lumenchess/settings/SoundsHapticsUiTest.kt`

**Interfaces:**
- Preserves all P4 import, preview and appearance-setting callbacks exactly.
- Uses compact rows and custom toggle/button primitives only at the presentation layer.

- [ ] **Step 1: Remove architecture/developer copy and TextButton Back**

Use compact top bar `Sounds & Haptics`.

- [ ] **Step 2: Convert master switches and event controls to Lumen setting rows**

Master sound/haptic rows are compact. Sound pack is one compact navigation/action row. Each event is a compact row or small grouped section rather than an 18dp giant card.

- [ ] **Step 3: Preserve import/preview behavior**

Do not alter `SoundPackImporter`, safe extraction, P4 resolution, observer authority or preview output boundaries.

- [ ] **Step 4: Run P4 feedback/settings regressions and commit**

Commit message: `feat(P5): apply compact Lumen language to feedback settings`.

---

### Task 12: Verify theme variants and micro-interaction consistency

**Files:**
- Modify design files/screens only as required by screenshot findings.
- Modify/add theme structural tests where practical.

**Interfaces:**
- Uses centralized tokens only; no screen-specific magic duration/color where a token exists.

- [ ] **Step 1: Exercise Dark, OLED and Light on all primary screens**

Check border hierarchy, text contrast, board readability, selected states and disabled states.

- [ ] **Step 2: Verify motion tokens**

Button press/release, icon press, segments, tabs, top-level/subpage transitions, custom toggle/slider/dropdown and board piece motion use shared timings and never resize layout.

- [ ] **Step 3: Run proportional P5 verification and commit cleanup**

Commit message: `fix(P5): normalize theme and motion fidelity` only if changes are required.

---

### Task 13: API37 screenshot workflow and visual QA iteration

**Files:**
- Create: `.github/workflows/p5-reference.yml`
- Create/update: test-only screenshot/navigation harness if needed under `app/src/androidTest/...`.
- Create: `docs/verification/P5-VISUAL-QA.md`.

**Interfaces:**
- Produces screenshot artifact containing the mandatory 11 states.

- [ ] **Step 1: Add exact P5 checkpoint-capable API37 screenshot workflow**

The workflow installs/launches the app on API37 at Pixel 8 Pro resolution, captures stable states for setup, Stockfish live, Reckless live, Settings, Board, Pieces, Background, Presets, Sounds & Haptics, Light and OLED, and uploads PNGs.

- [ ] **Step 2: Run the workflow on the candidate branch SHA**

Do not declare success from CI alone.

- [ ] **Step 3: Download/open every screenshot and compare directly to both supplied concept images**

For each screen, write explicit observations for density, typography, spacing, radii, borders, accent use, navigation, hierarchy, piece scale and board scale.

- [ ] **Step 4: Iterate if any primary screen still reads as stock Material 3**

Return to the owning component/screen task, apply the smallest reusable fix, rerun proportional tests and recapture screenshots.

- [ ] **Step 5: Commit final QA report**

Commit message: `docs(P5): record reference-fidelity visual QA`.

---

### Task 14: P5 checkpoint, cumulative gate and physical APK

**Files:**
- Create: `docs/verification/P5-CHECKPOINT.md`
- Update gate workflow trigger only if current cumulative Gate P cannot run on `checkpoint(P5)`.

**Interfaces:**
- Produces immutable exact-SHA P5 branch candidate for physical approval.

- [ ] **Step 1: Create `checkpoint(P5)` only after proportional tests and screenshot QA are green**

Record branch SHA, visual architecture, retained runtime boundaries, screenshots, regressions found and test coverage.

- [ ] **Step 2: Run complete cumulative M0–M19 + P1–P5 branch gate**

Must include core-chess, engine-api, Stockfish 18, Reckless 0.9.0, clocks, serialized runtime, premoves, Room/persistence, Play, redesigned UI, customization, P4 feedback/import security, P5 structural visual regressions, lint, assemblies, API37 instrumentation, native ARM64/x86_64, 16KiB and APK budgets.

- [ ] **Step 3: Verify `main` remains `de2fe5e7c3d4096527a4e24602f68144c4b2d67c`**

If `main` moved unexpectedly, stop and report rather than promoting/rebasing automatically.

- [ ] **Step 4: Build ARM64-only Pixel 8 Pro debug APK from the exact green P5 branch SHA**

Collect filename, SHA-256 if emitted, byte/MiB size, run ID, artifact name/id/route.

- [ ] **Step 5: STOP without promotion**

Report exact branch SHA, gate results, APK artifact and screenshot artifact. Wait for explicit physical visual approval. Do not begin M20.

---

## Plan self-review

- Spec coverage: tokens, reusable primitives, typography/spacing/radii/borders, navigation, setup, live play, board, pieces, Settings, customization, feedback UI, Dark/OLED/Light, motion, screenshot QA, cumulative gate and non-promotion are all assigned to explicit tasks.
- Authority boundary: no task modifies game/rules/engine/persistence/feedback authority; `LumenChessboard` changes are presentation/input animation only.
- Placeholder scan: no `TBD`/`TODO` implementation gaps remain.
- Type consistency: component names match the approved P5 contract and are defined before downstream screen migrations.
- Scope: future features remain disabled/product-preview presentation only; P5 does not pull M20 scope forward.
