# P4 Sound Provenance

Date: 2026-08-17

LumenChess P4 uses seven built-in feedback cues for move, capture, check, castle, promotion, game start, and game end.

## Provenance

The built-in cues are original LumenChess audio. They are synthesized deterministically by `BuiltInSoundAssets` at runtime from code in this repository. No third-party recordings, samples, downloaded sound effects, or audio from Chess.com or any other chess product are embedded, copied, or fetched.

The synthesis recipe uses short layered sine partials with event-specific frequencies and weights, a fast attack, exponential damping, and a short end fade. Output is mono 44.1 kHz, 16-bit PCM WAV. Peak synthesis amplitude is capped below full scale.

The longer semantic cues (promotion, game start, and game end) are 150 ms. Other cues are intentionally short (90–165 ms) to keep feedback immediate and to avoid adding a material APK-size payload.

## Built-in cue definitions

| Event | Duration | Frequencies (Hz) | Weights | Decay |
| --- | ---: | --- | --- | ---: |
| Move | 90 ms | 430, 690 | 0.72, 0.28 | 24 |
| Capture | 125 ms | 330, 515, 825 | 0.55, 0.30, 0.15 | 18 |
| Check | 150 ms | 610, 915 | 0.65, 0.35 | 15 |
| Castle | 165 ms | 285, 430, 570 | 0.48, 0.34, 0.18 | 13 |
| Promotion | 150 ms | 520, 780, 1040 | 0.48, 0.32, 0.20 | 11 |
| Game start | 150 ms | 392, 523.25, 659.25 | 0.38, 0.34, 0.28 | 10 |
| Game end | 150 ms | 523.25, 392, 293.66 | 0.34, 0.35, 0.31 | 9 |

## Redistribution

The synthesis code and resulting cues are project-owned original material and may be redistributed with LumenChess under the repository's licensing terms. Custom user-imported sounds remain user-provided app-private files and are not part of the distributed project assets.
