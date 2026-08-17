# Stockfish 18 provenance

M13 integrates the exact upstream Stockfish 18 release.

| Field | Pinned value |
| --- | --- |
| Upstream | `official-stockfish/Stockfish` |
| Tag | `sf_18` |
| Commit | `cb3d4ee9b47d0c5aae855b12379378ea1439675c` |
| Release commit date | `2026-01-31` |
| License | `GPL-3.0-or-later` (upstream `Copying.txt`) |
| Big NNUE | `nn-c288c895ea92.nnue` |
| Small NNUE | `nn-37f18f62d772.nnue` |
| Android NDK | `28.2.13676358` |
| CMake | `3.22.1` |
| ABIs | `arm64-v8a`, `x86_64` |

The CMake build fetches the source by the full commit SHA rather than a moving branch. NNUE files are fetched from the upstream Stockfish network endpoints and validated the same way as upstream `scripts/net.sh`: the first 12 SHA-256 hexadecimal digits must match the digest encoded in each filename.

The integration compiles a shared library rather than copying an executable into writable app storage. It uses the pinned source's UCI loop unchanged behind a minimal JNI/stdin/stdout pipe wrapper in the already-isolated M12 engine host.

The current repository remains subject to ADR 0003. Recording this provenance does **not** declare the eventual public APK/AAB distribution obligations solved.
