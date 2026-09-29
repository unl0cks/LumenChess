# P4 Sound Provenance

Date: 2026-08-17

LumenChess P4 uses seven built-in feedback cues for move, capture, check, castle, promotion, game start, and game end.

## Provenance

The built-in cues are original LumenChess audio. They are synthesized deterministically by `BuiltInSoundAssets` at runtime from code in this repository. No third-party recordings, samples, downloaded sound effects, or audio from Chess.com or any other chess product are embedded, copied, or fetched.

**Recipe v2 (product completion pass).** The original P4 cues were short sine blips and did not read as pieces on a board. Piece cues are now modal "struck wood" hits: a handful of exponentially damped sinusoids (the body modes of a small wooden block) plus a few-millisecond, low-passed noise click from a fixed-seed xorshift generator. Check adds a soft harmonic ping; promotion and the two game cues use bell partials (ratios 1 : 2.76 : 5.40). Every cue is peak-normalised so loudness (RMS) stays in one band across events. Output is mono 44.1 kHz, 16-bit PCM WAV.

Generated files are cached on disk by name and carry the recipe version (`move.v2.wav`); unversioned files from older recipes are deleted on first use so an update cannot keep playing the old cues. The Kotlin implementation was verified sample-for-sample against an independent reference implementation of the same math.

Sound packs and per-event overrides (whole-pack ZIP import, user files) are unchanged and still take precedence over the built-in cues.

## Built-in cue definitions (v2)

| Event | Duration | Structure |
| --- | ---: | --- |
| Move | 160 ms | one wood hit (220 / 365 / 610 / 1250 Hz modes) |
| Capture | 220 ms | heavy hit (170 Hz body) + a second lighter hit 30 ms later (the captured piece) |
| Check | 340 ms | wood hit + soft 1568 Hz ping at 60 ms |
| Castle | 300 ms | two hits 105 ms apart: king (200 Hz body) then rook (260 Hz body) |
| Promotion | 550 ms | wood hit + rising bell notes 988 Hz, 1319 Hz |
| Game start | 600 ms | bell notes 659 Hz, 880 Hz |
| Game end | 700 ms | bell notes 784 Hz, 587 Hz |

Exact partial frequencies, decays and gains live in the tables in `BuiltInSoundAssets.kt`, which is the only place to tune them.

## Redistribution

The synthesis code and resulting cues are project-owned original material and may be redistributed with LumenChess under the repository's licensing terms. Custom user-imported sounds remain user-provided app-private files and are not part of the distributed project assets.
