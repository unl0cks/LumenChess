# M21 — Manual takeover

## Scope and source of truth

M21 implements manual opening control and midgame takeover/return for either or
both Arena sides, with locked clocks by default. The scope was recovered from
`docs/implementation/IMPLEMENTATION-PLAN.md` (M21), §§8.3–8.4 of
`docs/design/LumenChess-Full-Design-Spec.md`, `docs/design/DECISIONS.md`,
`docs/design/UI-FLOWS.md`, and ADRs 0011, 0017 and 0018. M22 branching,
sandbox sessions and Save as Variation remain excluded.

The implementation is rooted at promoted M20 `57781d586bb55f4509ffa301d163b4716aca59fc`
on `codex/m21-manual-takeover`. A finite limit counts accepted moves per
controlled side; an unlimited lease lasts until explicit release. Manual
opening and takeover lock both clocks by default, without pausing the game;
`Count time` is an explicit alternative. Runtime/core state remains the sole
authority for controllers, clocks, legality, engine correlation and persistence.

## Implementation

Runtime owns a typed per-side manual-control lease and clock policy. An atomic
manual-control event changes White/Black/Both, invalidates premoves, cancels
stale engine work and hands finite leases back to the engine in the same legal
move reduction. Existing engine search ID/revision validation and host routing
remain in force. Restored snapshots preserve manual leases and policy while
retaining the established paused/no-in-flight-search restore boundary.

Arena setup exposes White, Black or Both manual opening, finite moves or until
release, and paused/counting clock policy in the existing scrollable component
grammar. Live exposes Take Over/Return to Engine for either/both sides; board
input is enabled only for the current runtime HUMAN controller and is guarded
by session and position revision. The existing P5/P6 board, feedback, motion,
piece catalog and layout are reused unchanged.

## Durable commits

- `ca023ba` — runtime manual-control leases and locked-clock reduction.
- `7722fe4` — Arena coordinator, metadata v2/legacy restore, and focused tests.
- `4fd1e94` — Arena setup/Live manual-control UI and guarded board input.
- `e15577d` — Arena manual-move routing regression coverage.

## Original candidate verification (historical)

The pre-edit `:game-runtime:test` baseline passed. Post-edit verification was
attempted with the configured Gradle 9.5 distribution and JDK 17 using
`--offline --no-daemon`, but the host failed before project configuration with
`java.io.IOException: Unable to establish loopback connection`. The wrapper
also cannot fetch its distribution in this restricted environment. Therefore
post-edit Gradle tests, lint, assemblies and API-37 native verification are
**NOT RUN / BLOCKED**, not passes. Source-level review and focused test additions
are present, but a native manual-review capture is still required because M21
adds Arena interaction UI.

`git diff --check` and the tracked privacy scan are required before publication.
No private Chess.com PNGs, generated personal assets, APKs, keystores, archives
or absolute personal asset-source paths are part of the branch. Existing
untracked QA/reference directories are preserved and are not implementation
inputs.

## Review boundary and exclusions

Manual API-37 review should exercise setup, per-side finite/unlimited control,
locked/counting clocks, takeover/return, engine handoff, pause/flip and restore
with Standard and Chess960 where applicable. Verify that P1 board bounds remain
stable. Public and personal packaging lanes should be rerun when the Android
toolchain is available; the private asset source remains external and ignored.

P5, P6.1–P6.5 and M20 approved behavior is unchanged. No board feedback or
motion redesign, schema migration, M22 branch behavior, promotion/merge, or
release/signed APK work is included. M21 stops at this candidate/manual-review
boundary.

## Verification/publication continuation — 2026-09-03

Recovered local `1a57f37ee67ad22782e8538ab992a4281faea54c` with all five M21
commits intact. A fresh origin fetch confirmed `main` remained
`57781d586bb55f4509ffa301d163b4716aca59fc`; the M21 branch had not yet been
published. The rejected `c21a2f6...` renderer remains outside the lineage.
Existing untracked QA/reference directories were preserved, not staged.

The old host blockers were reproduced and resolved without application workarounds:
host-authorized Git access, the already-configured JDK 17/selector setup, and
the existing local short-build-directory Gradle init script. These disposable
host settings and absolute paths are not repository configuration.

### Concrete findings and bounded corrections

- Fixed the missing `RuntimeController` import in `ArenaSnapshotCodecTest`.
- Corrected a test that configured the default finite three-move lease but
  incorrectly expected the unlimited metadata string.
- Added lease/policy snapshot round-trip, partial release, and clock-policy
  accounting coverage. No clock or persistence production correction was needed.
- Independent source review found that accepted checkmating moves return
  `TERMINAL`, not `APPLIED`, leaving Arena's previous human/engine presentation
  source flag unchanged. Two real API-37 regressions failed on the recovered
  implementation (human mate and Stockfish mate). The only production correction
  is in `ArenaViewModel`: source attribution follows committed revision change.
  Both tests then passed. A timeout without a committed move cannot change that
  attribution. Runtime authority, P6 timing/easing and artwork are unchanged.

### Native product review

`ArenaManualReviewQaTest` is explicitly opt-in (`m21Review=true`), uses the actual
MainActivity/Settings/Arena UI, and observes real runtime, engine transport and
persistence state. It does not inject piece preferences or canonical positions.
The two tests are separated by an external app force-stop. Both passed on the
corrected production build at API 37, **1344 × 2992 / 489 dpi**.

Verified White, Black and Both takeover, partial/Both return, until-release and
finite leases, finite handoff, legal tap/drag, default locked clocks and explicit
Count time. Real Stockfish 18 / 2000 Elo / Native and Reckless 0.9.0 / 1200 Elo /
Humanized searches retained their independent side configurations through return
and restoration. Standard and Chess960 both progressed.

Neo was selected through Settings. Stored/resolved ID
`private.chesscom.ejgfv`, source `pieces/ejgfv`, and actual piece renderer tags
agree throughout. The force-stop/reopen flow restored the exact Chess960 FEN at
revision 2, both one-move manual leases, engine configurations, and 600000 ms per
side paused snapshot. Resume Arena kept those leases and no pending search;
their subsequent expiry returned both engines and progressed to revision 6.
No in-flight engine request was restored as authoritative state.

All **29 recorded Arena state samples** measured **(21,428)–(1323,1730)**,
**1302 × 1302 px**, maximum movement/resize **0 px**, including takeover,
tap/drag release, return, thinking/result, flip, finite expiry and restoration.
The wait-for-revision loop also checks the rectangle while engines work.

The compact local review ZIP contains ten native screenshots, renderer/runtime
diagnostics, exact bounds, test results and a verified SHA-256 manifest. The
Control dialog capture is the actual native window over Live; other images use
the actual Compose root. No simulated evidence. Native inspection found no
renderer substitution, clipping, rectangular shadow haze, malformed feedback
or board movement. This is not manual visual approval.

### Executed gates and privacy

- Focused runtime: **10 PASS**; Arena JVM: **26 PASS**.
- Public native Arena baseline: **6 PASS**; terminal-move regression: **2 PASS**
  after reproducing **2 FAIL** before the correction.
- Personal native takeover/restoration flows: **2 PASS**.
- Cumulative JVM: core-chess **71**, runtime **56**, engine-api **36**,
  engine-host **8**, app **122**, all **PASS**. Persistence JVM is **NO-SOURCE**,
  not a passing suite; persistence is exercised natively.
- Applicable engine-host/persistence/app lint and debug/app-test assemblies:
  **PASS**. No release task was invoked.
- Final cumulative API-37 gate: engine-host **19 PASS**, persistence **48 PASS**,
  app **63 PASS / 32 SKIPPED / 0 FAIL**, counted from JUnit test cases. The 32
  opt-in screenshot assumptions are not passes; the two M21 personal review
  flows were explicitly enabled and passed separately. The final gate ran
  after the terminal-source correction and includes both new mating regressions.
- Fresh public APK: **0 personal styles/piece PNGs/board PNGs**, no private
  source tokens. Fresh personal APK: **39 styles / 468 canonical piece PNGs /
  8 supported board PNGs**, no unexpected private input entries or source paths.
- Tracked privacy scan and `git diff --check`: **PASS**. No private source art,
  generated personal assets, APKs, archives, keystores or absolute personal paths
  are staged. Native review evidence remains local/private.

CodeRabbit has no callable integration or installed CLI on this host; nothing
was installed and no CodeRabbit review is claimed. Independent read-only source
review verified controller/clock/search/persistence boundaries and the narrow
terminal attribution fix. The unused legacy `ChangeController` event is not an
Arena route: M21 uses atomic `SetManualControl` exclusively. Mixing the legacy
event with active manual leases is outside this supported Arena path.

Normal branch publication preserves all original M21 history. GitHub's existing
branch `checkpoint` job is the applicable remote gate; `full`, `engine-device`
and `play-device` are policy-skipped for the final M21 commit message. Report
the actual run separately rather than predeclaring a future CI result here.

**M21 awaits manual approval.** No merge/promotion, M22, release or signed/final
APK is included. P5/P6 and the M20 product composition were not redesigned.
