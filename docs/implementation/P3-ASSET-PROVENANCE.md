# P3 visual asset provenance

All built-in P3 visual customization assets listed here are project-owned LumenChess work and do not depend on third-party artwork redistribution.

| Asset | Identity | Provenance / production method | License handling |
|---|---|---|---|
| Lumen vector pieces | `lumen-vector` | Original chess-piece silhouettes authored for LumenChess on 2026-08-17 as scalable Jetpack Compose Canvas/Path geometry. Shapes, edge treatment and highlights were created directly in project source; no external piece SVG, font glyph, bitmap or traced source is embedded. | Project-owned source; distributed under the LumenChess project license. |
| Lumen Outline pieces | `lumen-outline` | Original alternate treatment built from the same project-owned Lumen geometry with a distinct outline/fill rendering treatment. No external artwork or font dependency. | Project-owned source; distributed under the LumenChess project license. |
| Lumen Blue board | `lumen-blue` | Project-authored color palette derived from the approved LumenChess blue visual system. | Project-owned configuration data. |
| Midnight OLED board | `midnight-oled` | Project-authored near-black/navy board palette for OLED use. | Project-owned configuration data. |
| Graphite board | `graphite` | Project-authored neutral stone/graphite palette. | Project-owned configuration data. |
| Lumen Night / Void / Graphite Haze backgrounds | `lumen-night`, `void`, `graphite-haze` | Project-authored solid/gradient color treatments rendered directly by Compose; no bitmap source material. | Project-owned configuration data. |

The built-in piece renderer intentionally replaces the previous Unicode chess-glyph presentation so rendering quality and redistribution no longer depend on the device font's chess symbols. The chess model, legal move generator and runtime remain independent of these presentation assets.
