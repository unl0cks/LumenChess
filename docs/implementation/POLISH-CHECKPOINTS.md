# Physical Polish & Customization checkpoints

## P1 — physical regressions and APK-size audit

Checkpoint: `279bff8b71e99e3c06711500d7ae8e36d7e48c56`

- Board geometry is owned by an explicitly square live board stage; premove/presentation overlays no longer participate in parent measurement.
- The human move → engine-thinking/premove-enabled → engine-result bounds regression remains permanent instrumentation coverage.
- APK reporting records compressed/uncompressed totals, ABI/native-library totals, DEX/resources/assets, largest entries, and budget status.
- Normal native coverage retains `arm64-v8a` + `x86_64`; the physical Pixel build can package ARM64 only without removing required NNUE payload.
- P1 checkpoint workflows were green, including focused geometry red/green, universal APK-size reporting, and the Pixel ARM64 artifact.

## P2 — full LumenChess UI redesign

Checkpoint: the `checkpoint(P2): redesign LumenChess navigation and Play UI` commit containing this entry.

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

### Regression discovered during P2

The recovered branch still contained one positional Compose `Modifier.selectable(...)` call in `LumenNavigation.kt` after the other compiler-directed fixes had landed. It kept the proportional P2 checkpoint red. Commit `acf422769b17d4fff96e75626bb2256266d8256a` converted the call to explicit named arguments. Its proportional JVM/Play checkpoint and ARM64/x86_64 + 16 KiB native verification passed. The obsolete self-patching workflow used during that diagnosis was then removed before this checkpoint.

### Gate required for this checkpoint

The exact checkpoint SHA must pass:

- Android proportional JVM/Play checks, lint and assemblies;
- ARM64/x86_64 native and 16 KiB verification;
- cumulative API 37 app instrumentation from `polish-device.yml`, including navigation/preview and existing Play/board regressions.

P3 begins only after those exact-SHA checks are green.
