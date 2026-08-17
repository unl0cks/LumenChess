# Physical Polish & Customization Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Complete P1–P4 before M20: fix the physical board jump, make APK size observable and provide an ARM64 physical build, redesign the app into the established LumenChess visual direction, add reusable visual customization, and add polished sound/haptic feedback without weakening M0–M19 correctness boundaries.

**Architecture:** Keep `core-chess`, engine host, Room game persistence and the M17 serialized runtime authoritative and unchanged unless a regression demands otherwise. Build new presentation-only boundaries around them: a fixed-geometry board stage, a typed DataStore appearance repository, reusable board/piece/background renderers, and a committed-event feedback observer. CI keeps universal ARM64+x86_64 coverage while a dedicated physical artifact narrows packaging to ARM64.

**Tech Stack:** Kotlin 2.3.21, Jetpack Compose 2026.06 BOM, Material 3, Android 17/API 37, DataStore Preferences, Android SoundPool/MediaPlayer-compatible local audio, VibratorManager/VibrationEffect, Gradle/AGP 9.3, GitHub Actions.

## Global Constraints

- Base exactly `06baccdfb3696371750e6d6a5288e50c1add3d24` plus approved batch documentation.
- Do not begin M20.
- Preserve all M0–M19 runtime/chess/persistence/engine invariants.
- Stockfish 18 and Reckless 0.9.0 pins remain unchanged.
- Standard + Chess960 remain green.
- Pixel 8 Pro / Android 17 / ARM64 is the physical optimization target.
- Normal CI retains x86_64 engine coverage.
- ARM64 physical-debug budget: 230 MB; universal-debug budget: 410 MB.
- P2 must actively use `docs/references/LumenChess_UI_Concept_Blue.png` and settled design docs.
- P3 default piece set must be production-quality, not a placeholder.
- P4 built-in audio must be polished and redistribution-safe; no scraped Chess.com assets and no crude placeholder beeps.
- Accessibility semantics/minimum touch targets must not regress.

---

### Task 1: P1 failing board-geometry regression

**Files:**
- Modify: `app/src/main/java/dev/lumenchess/play/PlayScreens.kt`
- Modify: `app/src/androidTest/java/dev/lumenchess/play/PlayUiIntegrationTest.kt`

**Interfaces:**
- Consumes: `PLAY_LIVE_TEST_TAG`, `CHESSBOARD_TEST_TAG`, current `PlayViewModel`/runtime.
- Produces: `PLAY_BOARD_STAGE_TEST_TAG` and an instrumentation invariant that compares root-space board bounds through human→engine→human transitions.

- [ ] **Step 1: Expose a stable stage test tag without changing geometry yet**

Add:

```kotlin
const val PLAY_BOARD_STAGE_TEST_TAG = "play-board-stage"
```

and tag the current weighted board wrapper so the failing test can observe it without fixing it.

- [ ] **Step 2: Write the failing instrumentation test**

Add a test that starts Standard/White, records `onNodeWithTag(CHESSBOARD_TEST_TAG).fetchSemanticsNode().boundsInRoot`, submits a deterministic legal human move through the coordinator/test hook, waits until engine-owned state/premove overlay is visible, records the bounds again, then waits for the engine result and records a third value. Assert identical `top`, `bottom`, `width`, and `height` within <=1 px tolerance.

Core assertion helper:

```kotlin
private fun assertStableBounds(expected: Rect, actual: Rect) {
    assertTrue(abs(expected.top - actual.top) <= 1f)
    assertTrue(abs(expected.bottom - actual.bottom) <= 1f)
    assertTrue(abs(expected.width - actual.width) <= 1f)
    assertTrue(abs(expected.height - actual.height) <= 1f)
}
```

- [ ] **Step 3: Run only the new test and prove it fails on pre-fix geometry**

Run in API 37 emulator CI:

```bash
./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=dev.lumenchess.play.PlayUiIntegrationTest#boardBoundsStayStableAcrossHumanMoveEngineThinkingAndEngineResult
```

Expected: FAIL showing a vertical `boundsInRoot` delta during the engine-thinking state.

### Task 2: P1 square BoardStage fix

**Files:**
- Modify: `app/src/main/java/dev/lumenchess/play/PlayScreens.kt`
- Test: `app/src/androidTest/java/dev/lumenchess/play/PlayUiIntegrationTest.kt`

**Interfaces:**
- Produces: `BoardStage(...)` presentation primitive whose own measurement is explicitly square; all transient overlays use `Modifier.matchParentSize()`.

- [ ] **Step 1: Replace the measuring overlay structure**

Use a square stage whose maximum width is constrained by available height/width and whose children cannot change measurement:

```kotlin
@Composable
private fun BoardStage(
    modifier: Modifier = Modifier,
    board: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .testTag(PLAY_BOARD_STAGE_TEST_TAG),
        content = board,
    )
}
```

The outer live layout may still reserve flexible vertical space, but the stage itself is square. Replace premove overlay `fillMaxSize()` with `matchParentSize()` and ensure the underlying board also uses `matchParentSize()` inside the square stage rather than deriving stage height.

- [ ] **Step 2: Keep dynamic participant/status copy fixed-footprint**

Constrain participant detail to one line with ellipsis and fixed minimum row height; do not insert/remove status rows around the board.

- [ ] **Step 3: Re-run the failing regression**

Expected: PASS with <=1 px board bounds delta across all three states.

- [ ] **Step 4: Run existing Play/board instrumentation**

```bash
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest
```

and API 37 focused Play instrumentation. Expected: green.

### Task 3: P1 APK-size reporter and ARM64 physical artifact

**Files:**
- Modify: `app/build.gradle.kts`
- Create: `scripts/report-apk-size.py`
- Modify: `.github/workflows/android.yml`
- Create: `.github/workflows/physical-apk.yml`
- Create: `docs/implementation/APK-SIZE.md`

**Interfaces:**
- Gradle property: `-PlumenPhysicalArm64Only=true`.
- Reporter CLI: `python scripts/report-apk-size.py <apk> --budget-mib <value> --output <json-or-md>`.

- [ ] **Step 1: Gate physical ABI filtering behind one property**

In `app/build.gradle.kts`, read:

```kotlin
val physicalArm64Only = providers.gradleProperty("lumenPhysicalArm64Only")
    .map(String::toBoolean)
    .orElse(false)
```

When true, apply `ndk.abiFilters += "arm64-v8a"` for the app packaging path only. Do not change `engine-host`'s default ARM64+x86_64 support.

- [ ] **Step 2: Add deterministic ZIP/APK accounting**

`report-apk-size.py` must use Python `zipfile` and output total file size, summed compressed/uncompressed entry sizes, totals by ABI, native library, DEX/resources/assets groups, and top 25 entries descending. Exit non-zero only when a supplied budget is exceeded.

- [ ] **Step 3: Document the Batch B baseline**

Record the measured ~390 MB universal debug baseline, native-library dominance, dual-ABI duplication and NNUE/`.rodata` findings in `APK-SIZE.md`. State explicitly that stripped native binaries and required engine networks must not be removed blindly.

- [ ] **Step 4: Add CI reporting**

Normal checkpoint/full CI runs reporter with `--budget-mib 410` against universal debug APK and uploads the report. Physical workflow checks out a requested exact SHA, builds:

```bash
./gradlew :app:assembleDebug -PlumenPhysicalArm64Only=true
```

runs reporter with `--budget-mib 230`, verifies only `arm64-v8a` engine libraries are present, computes SHA-256 and uploads APK + report.

- [ ] **Step 5: Run P1 proportional gate and checkpoint**

Require JVM/Play tests, lint, universal assembly/native ABI checks, focused API37 Play instrumentation, size report, and ARM64 artifact dry-run. Commit final P1 state as:

```text
checkpoint(P1): fix physical board geometry and audit APK size
```

### Task 4: P2 Lumen design system and navigation

**Files:**
- Modify: `app/src/main/java/dev/lumenchess/design/LumenTheme.kt`
- Modify: `app/src/main/java/dev/lumenchess/design/LumenControls.kt`
- Create: `app/src/main/java/dev/lumenchess/design/LumenIcons.kt`
- Modify: `app/src/main/java/dev/lumenchess/ui/LumenChessApp.kt`
- Test: `app/src/androidTest/java/dev/lumenchess/design/LumenControlsAccessibilityTest.kt`
- Create/Test: `app/src/androidTest/java/dev/lumenchess/ui/LumenNavigationTest.kt`

**Interfaces:**
- Produces reusable semantic colors, typography, shapes, elevation and icon treatments aligned with the blue concept reference.

- [ ] **Step 1: Inspect `LumenChess_UI_Concept_Blue.png` before coding and translate its durable traits**

Use dark navy layered surfaces, restrained luminous blue selection/accent, rounded but not pill-everything geometry, compact typography hierarchy and board-centered composition. Do not copy proprietary external assets.

- [ ] **Step 2: Expand theme tokens**

Create explicit background/surface tiers, outline, success/warning/error, clock-active/inactive, navigation-selected colors, typography and shape tokens while preserving minimum-touch dimensions.

- [ ] **Step 3: Replace letter navigation**

Use vector icons for Play/Arena/Games/Insights/Settings, icon+label semantics, compact bar height and restrained selected indicator. Future surfaces display a polished preview surface without “Coming in a later milestone”.

- [ ] **Step 4: Add semantics tests**

Assert each navigation item has its full label/content description and selected state; unavailable surfaces remain navigable previews without claiming implemented functionality.

### Task 5: P2 Play setup redesign

**Files:**
- Split/Modify: `app/src/main/java/dev/lumenchess/play/PlayScreens.kt`
- Create: `app/src/main/java/dev/lumenchess/play/PlaySetupScreen.kt`
- Create: `app/src/main/java/dev/lumenchess/play/PlaySetupComponents.kt`
- Test: `app/src/androidTest/java/dev/lumenchess/play/PlayUiIntegrationTest.kt`

**Interfaces:**
- Consumes existing typed `PlaySetupConfig`/ViewModel update methods unchanged.
- Produces compact setup UI only; no runtime behavior change.

- [ ] **Step 1: Move setup presentation into focused files**

Keep `PlayRoute` lifecycle behavior unchanged. Extract only presentation.

- [ ] **Step 2: Compose compact hierarchy**

Use header + optional resume surface; compact segmented controls for Variant/Engine/Side; strength mode + prominent Elo value/slider; model selector only when applicable; horizontally efficient time-control chips; one primary Start button. Preserve every existing test tag and add stable section tags rather than depending solely on text.

- [ ] **Step 3: Preserve validation and accessibility**

Unsupported Reckless native strength must remain clearly explained and Start disabled. Touch targets >=48dp.

- [ ] **Step 4: Run focused setup tests**

Existing Standard, Chess960, Reckless constraint and lifecycle tests must pass.

### Task 6: P2 live Play redesign

**Files:**
- Create: `app/src/main/java/dev/lumenchess/play/PlayLiveScreen.kt`
- Create: `app/src/main/java/dev/lumenchess/play/PlayerStrip.kt`
- Modify: `app/src/main/java/dev/lumenchess/play/PlayScreens.kt`
- Test: `app/src/androidTest/java/dev/lumenchess/play/PlayUiIntegrationTest.kt`

**Interfaces:**
- Keeps `BoardStage` from P1 as immutable geometry boundary.

- [ ] **Step 1: Implement compact participant strips**

Participant strip shows icon/identity, name, one-line secondary detail and prominent clock, with active-turn treatment that changes color/elevation only—not height.

- [ ] **Step 2: Implement board-centered live composition**

Use top opponent strip → flexible square BoardStage → bottom user strip → compact action rail. Eliminate excessive empty vertical space without changing board bounds between runtime substates.

- [ ] **Step 3: Replace generic action buttons**

Use icon-led Pause/Resume, Resign and Exit controls with destructive hierarchy for Resign and neutral/navigation hierarchy for Exit. Keep semantics labels explicit.

- [ ] **Step 4: Add structural regressions**

Assert board remains displayed/stable, actions retain 48dp targets, live navigation hides bottom main navigation, and engine status transitions do not create extra vertical surfaces.

- [ ] **Step 5: P2 checkpoint/gate**

Run app unit/lint/assembly, all Compose instrumentation, M19 Play integration and size budget. Commit:

```text
checkpoint(P2): redesign LumenChess navigation and Play UI
```

### Task 7: P3 typed appearance/settings repository

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts`
- Create: `app/src/main/java/dev/lumenchess/settings/AppearanceSettings.kt`
- Create: `app/src/main/java/dev/lumenchess/settings/AppearanceSettingsRepository.kt`
- Create: `app/src/test/java/dev/lumenchess/settings/AppearanceSettingsCodecTest.kt`

**Interfaces:**

```kotlin
enum class AppAppearance { SYSTEM, DARK, OLED_DARK, LIGHT }
data class AppearanceSettings(...)
interface AppearanceSettingsRepository {
    val settings: Flow<AppearanceSettings>
    suspend fun update(transform: (AppearanceSettings) -> AppearanceSettings)
}
```

- [ ] **Step 1: Add AndroidX DataStore Preferences dependency**
- [ ] **Step 2: Define typed defaults with blue accent independent from board theme**
- [ ] **Step 3: Persist enum/color/preset identifiers defensively with fallback defaults**
- [ ] **Step 4: Unit-test unknown/corrupt stored values and default round trips**

### Task 8: P3 app theme, board theme and presets

**Files:**
- Modify: `app/src/main/java/dev/lumenchess/design/LumenTheme.kt`
- Modify: `app/src/main/java/dev/lumenchess/board/ChessboardModels.kt`
- Create: `app/src/main/java/dev/lumenchess/customization/BoardThemeCatalog.kt`
- Create: `app/src/main/java/dev/lumenchess/customization/LumenPreset.kt`
- Test: `app/src/test/java/dev/lumenchess/customization/CustomizationModelTest.kt`

**Interfaces:**
- App theme resolves SYSTEM/DARK/OLED_DARK/LIGHT independently of board palette.
- Board themes expose stable IDs and square/highlight/coordinate styling.
- Presets are values that apply component settings, not locked modes.

- [ ] **Step 1: Write model tests proving preset application remains overridable**
- [ ] **Step 2: Add at least three polished board palettes including Lumen default and an OLED-compatible option**
- [ ] **Step 3: Wire appearance selection into root `LumenTheme`**
- [ ] **Step 4: Keep custom color serialization ARGB-based and deterministic**

### Task 9: P3 production-quality piece-set abstraction

**Files:**
- Create: `app/src/main/java/dev/lumenchess/board/PieceSet.kt`
- Create: `app/src/main/java/dev/lumenchess/board/LumenVectorPieces.kt`
- Create: `app/src/main/java/dev/lumenchess/board/LumenOutlinePieces.kt`
- Modify: `app/src/main/java/dev/lumenchess/board/LumenChessboard.kt`
- Modify: `docs/implementation/DEPENDENCY-SOURCES.md`
- Test: `app/src/androidTest/java/dev/lumenchess/board/LumenChessboardTest.kt`

**Interfaces:**

```kotlin
interface PieceSet {
    val id: String
    val displayName: String
    @Composable fun Piece(piece: Piece, modifier: Modifier = Modifier)
}
```

- [ ] **Step 1: Remove Unicode glyph ownership from board squares**

Board consumes a `PieceSet` parameter with default `LumenVectorPieceSet`.

- [ ] **Step 2: Build the default Lumen set as original project-owned vector artwork**

Use consistent silhouette language, matching optical weight across all 12 color/piece combinations, crisp scalable Vector/Canvas paths, subtle two-tone/highlight treatment suitable on both light/dark squares, and no text glyph dependency.

- [ ] **Step 3: Build a second polished selectable treatment**

Use a coherent outline/geometric treatment, not placeholder letters/glyphs.

- [ ] **Step 4: Record provenance**

Document both built-in sets as original/project-owned assets created for LumenChess, with no third-party redistribution dependency.

- [ ] **Step 5: Instrument rendering/selection**

Assert changing piece-set preference changes board piece semantics/style ID while move input/legality remains unchanged.

### Task 10: P3 Settings UX and live preview

**Files:**
- Create: `app/src/main/java/dev/lumenchess/settings/SettingsScreen.kt`
- Create: `app/src/main/java/dev/lumenchess/settings/BoardAppearanceScreen.kt`
- Create: `app/src/main/java/dev/lumenchess/settings/BoardPreview.kt`
- Modify: `app/src/main/java/dev/lumenchess/ui/LumenChessApp.kt`
- Test: `app/src/androidTest/java/dev/lumenchess/settings/SettingsUiTest.kt`

**Interfaces:**
- Settings categories expose only Appearance, Board & Pieces, Sounds & Haptics and other existing-safe shell categories; no M42 assistance controls.

- [ ] **Step 1: Implement polished Settings landing hierarchy**
- [ ] **Step 2: Implement Board/Pieces/Background/Presets tabs with always-visible live board preview**
- [ ] **Step 3: Implement System/Dark/OLED/Light and background selection**
- [ ] **Step 4: Verify preset then per-component override behavior through UI test**
- [ ] **Step 5: P3 checkpoint/gate**

Run unit, Compose, M19 Play, lint, assembly, DataStore tests and size report. Commit:

```text
checkpoint(P3): add LumenChess visual customization
```

### Task 11: P4 committed-event feedback projector and haptics

**Files:**
- Create: `app/src/main/java/dev/lumenchess/feedback/GameFeedbackEvent.kt`
- Create: `app/src/main/java/dev/lumenchess/feedback/GameFeedbackProjector.kt`
- Create: `app/src/main/java/dev/lumenchess/feedback/HapticFeedbackPlayer.kt`
- Modify: `app/src/main/java/dev/lumenchess/play/PlayViewModel.kt`
- Test: `app/src/test/java/dev/lumenchess/feedback/GameFeedbackProjectorTest.kt`

**Interfaces:**

```kotlin
sealed interface GameFeedbackEvent { data object Move; data object Capture; ... }
class GameFeedbackProjector { fun project(previous: RuntimeState?, current: RuntimeState): List<GameFeedbackEvent> }
```

- [ ] **Step 1: Test event classification and revision deduplication**

Cover move, capture, castle, promotion, check, game start/end and no duplicate event on clock-only projection refresh.

- [ ] **Step 2: Implement pure projector from committed runtime transitions**

Never emit from speculative board input or raw Binder callbacks.

- [ ] **Step 3: Implement haptic player**

Use `VibratorManager`/`VibrationEffect` with short differentiated predefined/primitive effects when supported and safe fallback otherwise. Respect global/per-event preferences.

- [ ] **Step 4: Observe after runtime projection commit**

The ViewModel may notify presentation feedback only after coordinator state has committed. Playback must not block runtime methods.

### Task 12: P4 polished original sounds and safe local packs

**Files:**
- Create: `app/src/main/java/dev/lumenchess/feedback/SoundEvent.kt`
- Create: `app/src/main/java/dev/lumenchess/feedback/SoundPack.kt`
- Create: `app/src/main/java/dev/lumenchess/feedback/SoundPlayer.kt`
- Create: `app/src/main/java/dev/lumenchess/feedback/SoundPackImporter.kt`
- Create: `app/src/main/res/raw/lumen_move.*`
- Create: `app/src/main/res/raw/lumen_capture.*`
- Create: `app/src/main/res/raw/lumen_check.*`
- Create: `app/src/main/res/raw/lumen_castle.*`
- Create: `app/src/main/res/raw/lumen_promotion.*`
- Create: `app/src/main/res/raw/lumen_game_start.*`
- Create: `app/src/main/res/raw/lumen_game_end.*`
- Modify: `docs/implementation/DEPENDENCY-SOURCES.md`
- Test: `app/src/test/java/dev/lumenchess/feedback/SoundPackImporterTest.kt`

**Interfaces:**
- Fallback chain: per-event override → selected pack → built-in Lumen default.

- [ ] **Step 1: Produce original project-owned static sound assets**

Use a restrained finished-chess sonic palette: short woody/ceramic move transient, denser capture impact, bright but non-alarming check accent, two-part castle, resolved promotion flourish, subtle start/end cues. Assets must be normalized consistently, clipped-free, short enough for responsive playback and substantially more polished than synthetic beeps.

- [ ] **Step 2: Record provenance**

Document assets as original/project-owned for LumenChess, including generation/editing method and no third-party source material.

- [ ] **Step 3: Implement low-latency playback**

Preload short built-ins via SoundPool; local imported files may use an appropriate local decoder/player. All errors are swallowed/reported to presentation diagnostics, never runtime authority.

- [ ] **Step 4: Implement safe ZIP import**

Reject absolute paths, `..`, duplicate normalized paths, oversized aggregate extraction and unsupported extensions. Recognize canonical event filenames (`move`, `capture`, `check`, `castle`, `promotion`, `gameStart`, `gameEnd`, etc.) and choose one preferred supported representation per event.

- [ ] **Step 5: Test malicious ZIP cases and event mapping**

Unit-test traversal, duplicates, size cap, unknown files and canonical mappings.

### Task 13: P4 Settings UI and checkpoint

**Files:**
- Create: `app/src/main/java/dev/lumenchess/settings/SoundsHapticsScreen.kt`
- Modify: `app/src/main/java/dev/lumenchess/settings/AppearanceSettings.kt`
- Modify: `app/src/main/java/dev/lumenchess/settings/AppearanceSettingsRepository.kt`
- Test: `app/src/androidTest/java/dev/lumenchess/settings/SettingsUiTest.kt`

- [ ] **Step 1: Add master sound/haptic controls plus per-event toggles/preview actions**
- [ ] **Step 2: Add local individual/ZIP import entry points without exposing copyright-risk built-ins**
- [ ] **Step 3: Verify disabling feedback cannot affect runtime revisions/clocks/moves**
- [ ] **Step 4: P4 checkpoint/gate**

Run all feedback/settings tests, Play integration, engine/persistence proportional coverage, lint/assembly/size. Commit:

```text
checkpoint(P4): add polished sounds and haptics
```

### Task 14: Gate P, exact promotion and physical APK

**Files:**
- Modify only CI/docs if a verification defect is discovered; no feature scope beyond P1–P4.

- [ ] **Step 1: Run complete cumulative branch Gate P on exact P4 checkpoint SHA**

Require M0–M19 JVM/module tests, lint, assemblies, native engine ABI + 16KiB checks, Room schema freshness, full API37 instrumentation, P1 geometry regression, P2/P3/P4 UI/settings/feedback tests and universal APK-size budget.

- [ ] **Step 2: Verify `main` has not moved unexpectedly**

Expected pre-promotion `main`: `06baccdfb3696371750e6d6a5288e50c1add3d24` unless an explicitly reviewed change exists. Hard-stop on unexpected divergence.

- [ ] **Step 3: Fast-forward only exact Gate-P-green SHA to `main`**
- [ ] **Step 4: Run fresh complete `main` CI and require green**
- [ ] **Step 5: Build ARM64 physical APK from exact promoted SHA**

```bash
./gradlew :app:assembleDebug -PlumenPhysicalArm64Only=true
```

Verify package `dev.lumenchess`, debug signing, SDK 37, ARM64 engine libraries, Stockfish/Reckless presence, structural installability, APK SHA-256 and <=230 MiB physical budget or explicitly explain a justified overage.

- [ ] **Step 6: Compare size against Batch B physical baseline**

Report before/after compressed size and largest contributors.

- [ ] **Step 7: Upload APK as GitHub Actions artifact and provide run/artifact identifiers**

Then STOP. Do not begin M20 until explicit physical approval.
