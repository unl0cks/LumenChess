# P5 Derivative and Customization Screens Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Translate every currently implemented non-primary P5 route onto the approved Lumen visual vocabulary without changing product behavior or reopening the four approved primary screens.

**Architecture:** Add a derivative-only Compose surface family that consumes the existing P5 palette and motion tokens, then migrate Play Settings, customization, feedback, and unavailable preview shells onto it. Keep runtime, persistence, navigation, import, and catalog ownership unchanged; screenshot coverage is added as a separate canonical derivative lane.

**Tech Stack:** Kotlin 2.3.21, Jetpack Compose BOM 2026.06.00, Android API 37 instrumentation, Gradle 9.5.0, GitHub Actions Pixel 8 Pro emulator.

**Spec:** `docs/superpowers/specs/2026-08-17-p5-reference-fidelity-visual-identity-design.md`

## Global Constraints

- Do not modify the approved Play Overview, New Game, Live Game, or Settings root compositions.
- Do not begin Arena runtime, Games Library, Insights, Gate P5, APK promotion, or M20.
- Preserve appearance persistence, board/piece/background/preset IDs, sound import and preview behavior, runtime ownership, accessibility semantics, and 48dp interaction targets.
- Use continuous rounded-shape gradients and borders; no finite top-highlight lines, rectangular compositing overlays, or semi-transparent selected-face fills.
- Category-A preview routes remain unavailable product previews and do not acquire new behavior.

---

### Task 1: Add the derivative surface family

**Files:**
- Create: `app/src/main/java/dev/lumenchess/design/LumenDerivativeSurfaces.kt`
- Create: `app/src/test/java/dev/lumenchess/design/LumenDerivativeSurfaceModelTest.kt`

**Interfaces:**
- Consumes: `LumenP5IdentityPalette`, `lumenP5IdentityPalette()`, `LumenMotion`, and `LumenDimensions.MinimumTouchTarget`.
- Produces: `LumenDerivativePage`, `LumenDerivativeTopBar`, `LumenDerivativeRow`, `LumenDerivativeTray`, `LumenDerivativeSegment`, `LumenDerivativeTabs`, `LumenDerivativeAction`, and `DerivativeSurfaceRole`.

- [ ] **Step 1: Write role-model tests**

Assert that selected faces are opaque, disabled surfaces have weaker contrast than neutral rows, pressed values preserve shape geometry, and no role exposes a finite-highlight flag.

- [ ] **Step 2: Verify the tests reject the missing model**

Run `./gradlew :app:testDebugUnitTest --tests 'dev.lumenchess.design.LumenDerivativeSurfaceModelTest'` in an Android-SDK-capable environment. Expected result before implementation: compilation failure because `DerivativeSurfaceRole` and its model do not exist.

- [ ] **Step 3: Implement continuous shape-aware surfaces**

Use opaque vertical face gradients, rounded outline strokes, full-shape radial illumination, restrained shadow/elevation, no Material ripple, and press translation/scale that does not change measured bounds.

- [ ] **Step 4: Verify the role-model tests pass**

Run the same focused unit-test command. Expected result: all `LumenDerivativeSurfaceModelTest` cases pass.

- [ ] **Step 5: Commit**

Commit message: `feat(P5): add derivative surface vocabulary`.

### Task 2: Translate category-A routes

**Files:**
- Modify: `app/src/main/java/dev/lumenchess/settings/PlaySettingsScreen.kt`
- Modify: `app/src/main/java/dev/lumenchess/play/PlayOverviewScreen.kt`
- Modify: `app/src/main/java/dev/lumenchess/ui/LumenNavigation.kt`
- Modify: `app/src/androidTest/java/dev/lumenchess/settings/SettingsUiTest.kt`
- Modify: `app/src/androidTest/java/dev/lumenchess/ui/LumenNavigationTest.kt`

**Interfaces:**
- Consumes: derivative surface family from Task 1.
- Produces: approved-vocabulary Play Settings and intentional unavailable preview shells while retaining all existing route tags.

- [ ] **Step 1: Add structural assertions**

Require the Play Settings derivative root/tray tags, require product-facing unavailable preview copy, and reject the developer sentence `will arrive with its engine-battle runtime`.

- [ ] **Step 2: Translate Play Settings**

Use derivative page/top bar/rows, a recessed Appearance tray, and opaque selected Appearance segments. Keep the existing callbacks and test tags unchanged.

- [ ] **Step 3: Translate unavailable previews**

Give Engine Arena, Arena, Games, and Insights the same intentional graphite preview surface without creating runtime actions or new destinations.

- [ ] **Step 4: Compile and run focused Settings/navigation tests**

Run `./gradlew :app:compileDebugKotlin :app:compileDebugAndroidTestKotlin` and the API-37 Settings/navigation instrumentation selection.

- [ ] **Step 5: Commit**

Commit message: `fix(P5): inherit approved derivative shells`.

### Task 3: Translate Board & Pieces

**Files:**
- Modify: `app/src/main/java/dev/lumenchess/settings/BoardAppearanceScreen.kt`
- Modify: `app/src/main/java/dev/lumenchess/settings/BoardPreview.kt`
- Modify: `app/src/androidTest/java/dev/lumenchess/settings/SettingsUiTest.kt`

**Interfaces:**
- Consumes: derivative page, top bar, tabs, and selectable catalog row.
- Produces: visually coherent Board, Pieces, Background, and Presets states with the existing immediate-preview behavior.

- [ ] **Step 1: Add visual-structure assertions**

Require a stable preview frame tag, a single continuous tab bed, opaque selected catalog rows, and all existing catalog selection tags.

- [ ] **Step 2: Restyle the preview and tab region**

Use a recessed graphite preview frame and continuous underline tabs while preserving current board bounds and `board-preview` semantics.

- [ ] **Step 3: Restyle visual catalog rows**

Use one raised neutral row family and one opaque selected variant; keep actual board swatches, piece miniatures, background thumbnails, preset miniatures, and callbacks.

- [ ] **Step 4: Compile and run focused customization tests**

Run the focused Settings instrumentation case through all four tabs and confirm the live preview changes after selections.

- [ ] **Step 5: Commit**

Commit message: `fix(P5): inherit approved customization surfaces`.

### Task 4: Translate Sounds & Haptics and event detail

**Files:**
- Modify: `app/src/main/java/dev/lumenchess/settings/SoundsHapticsScreen.kt`
- Modify: `app/src/androidTest/java/dev/lumenchess/settings/SoundsHapticsUiTest.kt`

**Interfaces:**
- Consumes: derivative page, row, tray, switch-row, and action treatments.
- Produces: coherent feedback overview and detail states with unchanged import/preview behavior.

- [ ] **Step 1: Add overview/detail structure assertions**

Require derivative master tray, sound-pack row, event list rows, event-detail tray, and paired Preview/Custom actions while keeping all legacy behavioral tags.

- [ ] **Step 2: Translate the overview**

Apply restrained depth hierarchy to master controls, sound pack, import action, and event rows without changing event enablement logic.

- [ ] **Step 3: Translate the detail state**

Apply the same page and row grammar to per-event toggles and paired actions without changing feedback output or file launchers.

- [ ] **Step 4: Compile and run focused feedback tests**

Run `SoundsHapticsUiTest` and the existing feedback projector/import unit tests.

- [ ] **Step 5: Commit**

Commit message: `fix(P5): inherit approved feedback surfaces`.

### Task 5: Capture and review the derivative batch

**Files:**
- Modify: `app/src/androidTest/java/dev/lumenchess/visual/P5ScreenshotQaTest.kt`
- Create: `.github/workflows/p5-derivative-reference.yml`
- Create: `design-qa.md`

**Interfaces:**
- Consumes: the frozen four primary screenshots as vocabulary sources and the translated derivative routes.
- Produces: native 390×844 screenshots for Play Settings, four customization states, Sounds overview/detail, and unavailable preview shells, plus primary-screen regression captures.

- [ ] **Step 1: Harden deterministic derivative capture**

Capture each derivative state from fresh application data, retain exact route interactions, and save canonical filenames without altering production state.

- [ ] **Step 2: Run one API-37 derivative workflow**

Build, run focused unit/instrumentation tests, capture the derivative batch and frozen-primary regressions, and upload one artifact from the durable final SHA.

- [ ] **Step 3: Compare combined evidence**

Place each implementation screenshot beside the closest frozen approved vocabulary screen at 390×844, inspect full screens and focused controls, and record findings in `design-qa.md`.

- [ ] **Step 4: Fix blocking P0/P1/P2 findings and rerun once if required**

Do not chase ordinary Android typography rasterization. Any finite seam, rectangular boundary, selected-face compositing patch, or primary-screen drift is blocking.

- [ ] **Step 5: Stop for manual review**

Return the actual derivative screenshots, comparison sheets, workflow run, artifact ID, regression results, and concrete residual mismatches without self-approval.
