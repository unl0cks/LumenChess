# P4 — Sounds and haptics checkpoint

This document is the durable checkpoint record for P4 in the Physical Polish & Customization batch. The checkpoint SHA is the `checkpoint(P4): complete sounds and haptics` commit containing this file.

## Feedback architecture

- Presentation feedback observes committed runtime state only; it has no legality, Position/GameTree, clock, engine-move, premove-execution, or persistence authority.
- `GameFeedbackProjector` projects move, capture, check, castle, promotion, game-start, and game-end feedback from committed transitions.
- The approved P4 design does not define premove as a separate audible/haptic event; an executed premove is represented by the resulting committed move event.
- Revision and node guards prevent clock refreshes, recomposition, and repeated observation of the same committed state from replaying feedback.
- `CommittedFeedbackObserver` retains only presentation-side previous-state memory.
- `GameFeedbackDispatcher` isolates every sound and haptic output call so device/output failure cannot escape into or block authoritative runtime progress.

## Sounds

- `SoundPlayer` uses Android `SoundPool` for low-latency presentation playback.
- Built-in move/capture/check/castle/promotion/game-start/game-end WAV cues are deterministically synthesized by LumenChess code from project-owned tone/envelope definitions.
- No Chess.com audio or third-party recordings/samples are embedded or downloaded.
- `docs/implementation/P4-SOUND-PROVENANCE.md` records project ownership and generation details.
- Built-in files are validated before reuse and regenerated if missing or corrupt.
- Custom SoundPool load/decode failures fall back to the built-in event cue and do not affect chess progress.

## Haptics and preferences

- Haptics use Android vibration APIs with short event-specific patterns/amplitudes and remain presentation-only.
- DataStore-backed settings include independent master sound/haptic switches, per-event sound and haptic sets, and selected sound-pack ID.
- The Sounds & Haptics settings surface exposes master controls, per-event controls, sound-pack import, individual event overrides, and preview behavior.

## Custom sounds and ZIP packs

- Imports are written only under app-private `filesDir/feedback` roots.
- ZIP extraction uses a staging directory and canonical direct-child checks; entry paths are never trusted.
- Absolute paths, traversal/nested paths, directory entries, unknown event names, duplicate/conflicting mappings, and unsupported extensions are rejected.
- Actual extracted bytes are capped at 4 MiB per entry and 24 MiB per pack.
- WAV/OGG/MP3/WebM payloads receive lightweight container-signature validation instead of extension-only trust.
- Malformed/empty ZIPs fail without publishing a pack; failed imports clean staging/final destinations.
- Individual event imports use the same private-storage, bounded-copy, extension, container, and staging/publish rules.

## Resolution and fallback

Resolution is:

1. valid individual event override;
2. valid event in the selected custom pack;
3. project-owned built-in/default cue.

A deleted, missing, corrupt, unreadable, or decoder-rejected custom file cannot suppress the built-in fallback.

## Recovery regressions retained

Recovery of the interrupted P4 session found two correctness boundaries that the earlier green run had not actually proven:

- feedback output exceptions could escape `GameFeedbackDispatcher`;
- custom audio selection/import trusted extension and existence without validating media/container data, allowing an invalid custom file to suppress fallback.

The tests-only commit `d3cab254c040df7c26dca9bbe3605238c3504229` deliberately exposed these gaps. Android CI run `32067380652` ran 58 app tests and failed exactly the four new regressions: output-failure isolation, corrupt built-in regeneration, corrupt ZIP audio rejection, and corrupt override fallback.

Commit `1b66c6ddd0807d1dd129e8b394b797eca957de84` fixed those boundaries narrowly while preserving the existing feedback architecture. Android CI run `32067960900` then passed proportional JVM/app checks, lint/assemblies, ARM64+x86_64 native verification, and 16 KiB ELF alignment.

These regressions remain permanent coverage.

## Gate P

The exact checkpoint SHA must pass the dedicated `Gate P` workflow plus the existing P4 checkpoint workflows. Together they cover cumulative JVM/module tests, Stockfish 18 and Reckless 0.9.0 integration/native ABIs, lint/assemblies, Room schema freshness, API 37 instrumentation, prior Play/UI/customization regressions, P4 feedback/importer coverage, 16 KiB verification, APK-size reporting/budgets, and the ARM64 physical-build path.
