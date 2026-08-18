# Personal board and piece overrides

LumenChess keeps public/reproducible board and piece assets separate from optional personal assets.
Third-party personal PNGs must never be committed to this repository.

## Local layout

Extract a personal asset pack outside tracked source, for example:

```text
local-assets/chesscom/
  boards/
    blue.png
    brown.png
    tournament.png
    ...
  pieces/
    tournament/
      wk.png wq.png wr.png wb.png wn.png wp.png
      bk.png bq.png br.png bb.png bn.png bp.png
    classic/
    club/
    bases/
    3d_staunton/
    ...
```

`local-assets/` is gitignored.

## Personal testing build

Enable the optional asset source with a project-relative or external path:

```bash
./gradlew :app:assembleDebug -Plumen.personalAssetsDir=local-assets/chesscom
```

or:

```bash
./gradlew :app:assembleDebug -Plumen.personalAssetsDir=/path/to/private/assets
```

When the property is absent, public CI and normal builds package only project-owned fallback assets.
When present, the build exposes the local board/piece entries through the normal `BoardTheme` / `PieceSet` catalogs. The default IDs retain public fallbacks so a personal selection degrades cleanly when the private pack is unavailable.

Do not configure `lumen.personalAssetsDir` in public GitHub Actions unless redistribution rights for the supplied assets have been established.
