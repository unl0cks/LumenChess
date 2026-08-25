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

The external directory is never registered as an Android asset source. A Gradle staging task discovers complete piece directories, copies only the twelve canonical piece PNGs (`bb`, `bk`, `bn`, `bp`, `bq`, `br`, `wb`, `wk`, `wn`, `wp`, `wq`, and `wr`), and copies only the known board PNGs used by the existing personal-board catalog. Scripts, archives, virtual environments, metadata, symlinks, and unexpected files are ignored. The disposable output lives under `app/build/generated/lumenPersonalAssets/` and is not tracked.

When the property is absent, the staging output is cleaned and public CI/normal builds package only project-owned fallback assets. When present, complete local piece collections are exposed through the normal `PieceSet` catalog with stable IDs of the form `private.chesscom.<source-directory>`. Incomplete collections are omitted. Stored private IDs are preserved when assets are temporarily unavailable, while rendering falls back visibly to the public Lumen set; restoring the same private pack restores the selection.

Decoded raster pieces use a bounded application cache and alpha-bound, isotropic fitting. Source artwork is not recolored, stretched, traced, sharpened, or otherwise modified.

Do not configure `lumen.personalAssetsDir` in public GitHub Actions unless redistribution rights for the supplied assets have been established.
