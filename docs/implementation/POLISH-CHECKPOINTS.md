# Physical Polish & Customization checkpoints

## P1 — physical regressions and APK-size audit

Checkpoint: `279bff8b71e99e3c06711500d7ae8e36d7e48c56`

- Board geometry is owned by an explicitly square live board stage; premove/presentation overlays no longer participate in parent measurement.
- The human move → engine-thinking/premove-enabled → engine-result bounds regression remains permanent instrumentation coverage.
- APK reporting records compressed/uncompressed totals, ABI/native-library totals, DEX/resources/assets, largest entries, and budget status.
- Normal native coverage retains `arm64-v8a` + `x86_64`; the physical Pixel build can package ARM64 only without removing required NNUE payload.
- P1 checkpoint workflows were green, including focused geometry red/green, universal APK-size reporting, and the Pixel ARM64 artifact.

## P2 — full LumenChess UI redesign

Checkpoint: `d3bbd27b8583a7b4b8b685a31dad4c4fff99aa81`

### Architecture

- Presentation remains above the existing M0–M19 chess/runtime/engine/persistence boundaries.
- `LumenTheme` supplies the expanded blue/dark visual language used by the redesigned shell.
- `LumenChessApp` owns the five-tab shell (`Play`, `Arena`, `Games`, `Insights`, `Settings`) and hides main navigation during authoritative live Play.
- `PolishedPlayRoute` owns redesigned setup/live composition while continuing to consume the existing `PlayViewModel` and runtime state.
- The P1 square board-stage geometry boundary is retained unchanged; dynamic engine/status copy has a fixed layout footprint.

### Main visual changes

- Replaced letter/placeholder navigation with icon + label navigation and intentional preview states for future tabs.
- Reworked Play setup into compact variant/engine/side/strength/model/time controls with one primary Start Game action.
- Reworked live Play around compact participant rows, prominent clocks, a board-centered square stage, and a restrained action strip.
- Kept the committed LumenChess blue concept/reference and settled design documents as visual direction rather than reverting to generic Material developer UI.

### Regressions discovered during P2

- The recovered branch still contained one positional Compose `Modifier.selectable(...)` call in `LumenNavigation.kt` after the other compiler-directed fixes had landed. Commit `acf422769b17d4fff96e75626bb2256266d8256a` converted the call to explicit named arguments; its proportional JVM/Play checkpoint and ARM64/x86_64 + 16 KiB native verification passed.
- The first cumulative API 37 checkpoint then ran 23 instrumentation tests and found one failure in `LumenNavigationTest`: the test incorrectly required text `Arena` to resolve to a single node even though the approved P2 UI intentionally renders both the selected bottom-navigation label and the future-surface heading. Commit `7a2b98c90bea3b15084a44fdb928ffb6d91fed08` corrected only the assertion to require both intentional nodes. No product/runtime behavior changed.
- The obsolete self-patching workflow used during compiler diagnosis was removed. The device workflow now retains and surfaces instrumentation reports on failure so future device-test regressions are directly identifiable.

### Gate result

The exact P2 checkpoint passed proportional JVM/Play checks, lint/assemblies, ARM64+x86_64 native/16 KiB verification, cumulative API 37 app instrumentation, and universal APK-size reporting.

## P3 — themes, boards, pieces, backgrounds and presets

Checkpoint: the latest `checkpoint(P3): add LumenChess visual customization` commit containing this entry after the P3 API 37 regressions below were resolved.

### Architecture

- `AppearanceSettings` is the typed presentation source of truth and is persisted with AndroidX DataStore Preferences 1.2.1.
- Appearance supports System, Dark, OLED and Light independently of chess runtime state and independently of the selected board palette.
- `LumenTheme` resolves persisted appearance, accent and background into the Compose color system; the default Lumen identity remains blue.
- Board palettes and piece sets are injected through a presentation-only `ChessboardPresentationStyle` CompositionLocal. Existing Play board call sites therefore inherit appearance without acquiring preference or runtime ownership.
- Presets compose board, piece-set and background IDs. Any later component override clears the preset identity while preserving the untouched sibling selections.

### Built-in customization

- Board palettes: Lumen Blue, Midnight OLED and Graphite, including interaction/highlight/arrow colors.
- Piece sets: original project-owned Lumen vector geometry and Lumen Outline treatment. The previous Unicode device-font glyph renderer has been removed.
- Backgrounds: Lumen Night, Void and Graphite Haze, each with coherent dark/light treatments.
- Presets: Lumen, Midnight and Graphite Focus.
- Settings now exposes System/Dark/OLED/Light and a dedicated Board & Pieces surface with an always-visible board preview and Board/Pieces/Background/Presets tabs.
- Asset provenance and redistribution notes are recorded in `docs/implementation/P3-ASSET-PROVENANCE.md`.

### Regression coverage and recovered API 37 fixes

- Preference codec tests cover defaults, defensive fallback, deterministic ARGB storage and no-preset persistence.
- Customization model tests cover preset composition, individual override behavior and custom board-color fallback.
- Board instrumentation verifies changing piece treatments does not change legal move submission.
- Board presentation instrumentation verifies the presentation provider reaches an unchanged board call site.
- Settings instrumentation exercises OLED appearance, the live preview, preset application, background override and board override.
- The preview follows the resolved System light/dark theme instead of assuming System means dark.
- The first P3 checkpoint `bf19f29d97d7bf332ef9322760610d2c407f5298` found three P3-only API 37 failures. Two were caused by attaching the piece style test tag directly to `Canvas`, which existed in semantics but did not have a stable displayed layout node. Commit `751e9d21fc037301e7967e192a1231cb52afd101` places the artwork Canvas inside a tagged layout container without changing board input or chess state.
- The third failure was a Settings timeout after a preset component override. DataStore persistence was correct but UI state waited for asynchronous storage round-trip before reflecting the selection. Commit `27637dc390ca0aa40cc6fedfd75f6e96e3155ee0` makes presentation settings updates optimistic while still persisting through the same DataStore source of truth; runtime/game ownership remains untouched.
- Both fixes passed proportional JVM/Play checks, lint/assemblies, ARM64+x86_64 native verification and 16 KiB checks before this replacement checkpoint.

### Gate required for this checkpoint

The exact replacement P3 checkpoint SHA must pass:

- Android proportional JVM/Play checks, lint and assemblies;
- ARM64/x86_64 native and 16 KiB verification;
- cumulative API 37 app instrumentation, including prior board/Play/P2 regressions and new P3 customization tests;
- universal APK-size reporting/budgets.

P4 begins only after those exact-SHA checks are green.
