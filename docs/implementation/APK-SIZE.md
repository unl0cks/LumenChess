# LumenChess APK size tracking

## Batch B physical-test baseline

Approved source: `06baccdfb3696371750e6d6a5288e50c1add3d24`  
APK: `LumenChess-BatchB-M19-06baccdf-debug.apk`  
SHA-256: `e3bf2c47ad7279ad13f694371e5faf1a59313cca1628d120e6d4993b0a449530`

Measured ZIP/APK size:

- File size: **390,409,457 bytes / 372.32 MiB**.
- Native payload: **342.70 MiB**.
- DEX: **29.05 MiB**.
- Resources: **~0.47 MiB**.
- Other/metadata: negligible relative to engines.

Native payload by ABI:

| ABI | Uncompressed native payload |
|---|---:|
| x86_64 | 171.39 MiB |
| arm64-v8a | 171.29 MiB |
| x86 | ~0.01 MiB |
| armeabi-v7a | ~0.01 MiB |

Largest individual entries:

| Entry | Size |
|---|---:|
| `lib/x86_64/liblumen_stockfish18.so` | 108.75 MiB |
| `lib/arm64-v8a/liblumen_stockfish18.so` | 108.71 MiB |
| `lib/x86_64/liblumen_reckless09.so` | 62.63 MiB |
| `lib/arm64-v8a/liblumen_reckless09.so` | 62.57 MiB |
| `classes.dex` | 17.83 MiB |
| `classes15.dex` | 9.89 MiB |

## Why it is large

The Batch B debug APK was effectively a universal ARM64+x86_64 engine package. Each supported engine ABI carries the engine code plus its required evaluation-network material. Stockfish 18's native image contains the pinned default NNUE networks referenced by its source; Reckless 0.9.0 is likewise built against its pinned network. The two engine libraries are already stripped, so native debug symbols do **not** explain the bulk of the APK.

No large accidental app assets/resources were found. The dominant reducible factor for a Pixel 8 Pro testing artifact is therefore the duplicate x86_64 engine payload, not required engine functionality.

## Packaging policy

- **Normal CI/universal debug:** retains ARM64 and x86_64 engine coverage for device/emulator verification.
- **Physical Pixel 8 Pro debug:** uses `-PlumenPhysicalArm64Only=true`, packaging only `arm64-v8a` native payload while leaving normal CI support intact.
- Required Stockfish/Reckless network data must not be deleted or weakened merely to improve the number.
- Release size is reported separately once a representative release configuration exists; debug size is not treated as a release-size prediction.

Initial reporting budgets:

- ARM64 physical debug: **230 MiB**.
- Universal debug CI: **410 MiB**.
- Release: reporting-only until a representative release configuration exists.

`scripts/report-apk-size.py` is the repository-owned deterministic reporter used by CI. It verifies ZIP integrity and reports total size, compressed/uncompressed groups, native ABI/library totals, and the largest entries in descending order.
