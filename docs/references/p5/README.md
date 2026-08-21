# P5 approved references

## Live — board-first

The approved board-first Live source is retained byte-for-byte at
`live-approved/03-live-board-first-approved.png`. Its PNG SHA-256 is
`ae30bd1dc538416d742a22190cce731ccaed3ef9d624dfcda224bf3b3572f157`;
it is `390 × 844`, with decoded RGB SHA-256
`2511adbe04c860cb5cffa2077336fbe029b35e54a33d8d942d8c86cb03136c37`.

The integrated API-37 workflow verifies both hashes before normalizing the native
Live capture with the same visible-content crop and Lanczos resize used for the
other canonical P5 comparisons. It uploads `03-live.png`,
`03-live-approved-comparison.png`, and `03-live-press-state.png` as the dedicated
`p5-live-${{ github.sha }}` artifact. This reference is QA material only and must
not be rewritten to match a native capture.

## Settings — Iteration 3

The approved Iteration 3 Settings reference is stored as one deterministic lossless transport split across these active Base64 chunks:

- `.settings-iter3-approved-lossless.part00.b64` through `part03.b64`
- `.settings-iter3-approved-lossless.part04.00.b64` through `part04.05.b64`
- `.settings-iter3-approved-lossless.part05.00.b64` through `part05.05.b64`
- `.settings-iter3-approved-lossless.part06.b64`
- `.settings-iter3-approved-lossless.part07.b64`

`.github/workflows/p5-reference.yml` concatenates that exact list, validates the 85,352-character Base64 payload and transport SHA-256 `9fcfa2a94fbcf635c7d5b0dec3c1ae3af955a87377272350f18367c2d2842fca`, decodes the image, requires `390 × 844`, and verifies decoded RGB SHA-256 `791a5d6cac599e83a3083d369b42e2c39b09099cc59033255d6360b0ad906452` before generating the native comparison.

For visual QA, the decoded RGB digest is authoritative. Losslessly equivalent PNG/container bytes do not need the same file hash when their decoded pixels are identical. If the decoded pixels differ, the verification must fail.

## Play Overview — Iteration 2

The visually approved Play Overview Iteration 2 sandbox archive had SHA-256 `6f326cd6584295ff7625cdde94a116a44532f237fd455b177f97bab959ad1e94`. Its frozen browser-source files are retained under `play-overview-iter2/` with these exact SHA-256 values:

- `index.html`: `c273cbc7c52187f6d0e3a0fd715905c0a1298e85bbd23becf5c973e738a94086`
- `styles.css`: `a298588b5e24b769148e27e9dd064531c0d2e91384c6de12236abaf2b7187ccf`
- `prototype.js`: `20c553207d470d02676940bb7f039e8afbaed1d0d497efcc3ca73d8cc02b2070`

The sandbox used temporary native-render hero extracts only because its design environment could not read the repository PNG binary payloads. `play-overview-iter2/render_reference.py` verifies the frozen source hashes and canonicalizes the QA reference at render time by replacing **only** those two image sources with the exact repository PNG bytes:

- `app/src/main/assets/play-overview/lumen_play_vs_engine_hero.png` — SHA-256 `43a6accd71c5f9f1bfba552e0c409f5a95f25b1567b617c5c8851b5186d40e00`
- `app/src/main/assets/play-overview/lumen_engine_arena_hero.png` — SHA-256 `2554fb301501a9f667652ab0631147bd7b38d868812b2dfecc0ea5bfa0aa12f2`

No frozen HTML/CSS/JS geometry, typography, lighting, spacing, press-state values or navigation styling is modified by canonicalization. The renderer requires an exact `390 × 844` output. The normal P5 reference workflow then normalizes the native device screenshot to the same visible-content aspect before producing `01-play-overview-approved-comparison.png`.

These files are reference/QA material only. They do not introduce web architecture into the Android application and must not become a second production implementation of the screen.
