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

## Verification status

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
