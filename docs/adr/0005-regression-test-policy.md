# ADR 0005 — Chess/runtime/engine bugs require regression tests

**Status:** Accepted  
**Date:** 2026-08-15

## Context
Chess and engine-runtime bugs are often position/order dependent and can quietly return after refactors.

## Decision
Every discovered bug in `core-chess`, game runtime, clocks/premoves, engine protocol/adapters, or engine-host lifecycle must gain an automated regression test that reproduces the failure before the fix and passes with the fix.

## Consequences
Bug fixes without a reproducer are incomplete unless the failure is genuinely impossible to automate; any exception must be documented in the change itself.
