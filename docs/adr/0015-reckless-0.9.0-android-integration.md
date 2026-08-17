# ADR 0015: Reckless 0.9.0 Android integration

- Status: Accepted
- Milestone: M14

## Context

LumenChess requires Reckless 0.9.0 as the second initial engine while preserving the M11 typed engine boundary and the M12 two-slot isolated-process host. The engine must run on Android ARM64 and on the x86_64 API 37 CI emulator without making raw UCI or native implementation details part of Binder or application state.

Fresh authoritative inspection of `codedeliveryservice/Reckless` shows that tag `v0.9.0` resolves to commit `0e92358f5acd66e5ac77b1bf558202e47c515435`. An earlier research note associated the tag with `23089be21ee18c6d461469800acb0368821bc1da`; that SHA is not the official v0.9.0 commit and must not be used for the pinned integration.

The v0.9.0 source is AGPL-3.0. ADR 0003 remains controlling: public APK/AAB distribution stays blocked until the project intentionally satisfies all applicable source/license obligations. M14 does not claim that distribution work is complete.

## Decision

- Pin Reckless to upstream tag `v0.9.0`, exact commit `0e92358f5acd66e5ac77b1bf558202e47c515435`.
- Pin the upstream NNUE model `v54-5478683c.nnue` by full SHA-256 `5478683cb1bababde29ae8f29468a99846726548fc6a0ed54cac40ab6d38efbf`.
- Pin Rust `1.88.0` and build with the release's committed `Cargo.lock` using `--locked`.
- Cross-compile for `arm64-v8a` and `x86_64`; package Reckless as `liblumen_reckless09.so` and require 16 KiB-compatible ELF load-segment alignment.
- Disable the upstream default `syzygy` feature for the Android integration with `--no-default-features`. Tablebases are not part of M14, and this avoids adding the optional Fathom/bindgen native boundary to the engine host.
- Keep the upstream Rust engine modules and build script unchanged. A small generated library target exposes only an integration entry point that calls the same `lookup::initialize()`, `nnue::initialize()`, and `uci::message_loop(...)` path as upstream `main`.
- Redirect stdin/stdout only inside the isolated engine process through the same pipe model used by Stockfish. Raw UCI remains internal to `engine-host`.
- Preserve the existing M12 production services: Slot A and Slot B remain non-exported, `isolatedProcess=true`, and separate Android processes.
- Preserve the M11 API and validation boundary. `EngineSearchId`, `PositionRevision`, and session identity remain transport-correlated; a returned best move remains untrusted until `EngineMoveValidator`/`core-chess` accepts it.
- Expose only capabilities present in v0.9.0: Standard, Chess960, MultiPV up to 256. Reckless 0.9.0 has no native UCI Elo limiting and no ponder option, so `strength = null` and `supportsPonder = false` are deliberate.

## Consequences

The engine-host module now has two independently packaged native engines but one unchanged typed/isolated lifecycle architecture. Reckless can be selected by engine ID without adding engine-specific behavior to UI, runtime, persistence, or chess legality.

The Rust toolchain and Reckless source/network fetch become native build prerequisites for `engine-host`. CI installs the exact Rust toolchain/targets and verifies both packaged ABIs and 16 KiB alignment.

Disabling Syzygy means Reckless tablebase capability is intentionally absent from the M14 Android adapter. Adding it later requires an explicit scoped decision rather than silently expanding the native boundary.

## Verification

M14 must keep permanent tests that prove:

- pinned capability/provenance constants;
- real Standard and Chess960 searches through the non-exported isolated service and final core legality validation;
- cancellation followed by replacement cannot leak or relabel terminal output;
- native session close/reopen works in one host;
- real Reckless instances can run simultaneously in independent Slot A/Slot B processes;
- ARM64 and x86_64 libraries are built and have at least 16 KiB PT_LOAD alignment.
