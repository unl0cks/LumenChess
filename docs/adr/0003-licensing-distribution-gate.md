# ADR 0003 — Licensing is a public-distribution gate

**Status:** Accepted  
**Date:** 2026-08-15

## Context
Mandatory engines have copyleft licenses and the project also relies on third-party Android libraries and datasets.

## Decision
Maintain exact dependency/source/version/license provenance from the beginning. Public APK/AAB distribution is blocked until the chosen LumenChess distribution/license model has been checked for compatibility with every shipped component and required source/notice obligations are satisfied.

## Consequences
- Local development can continue without pretending the final licensing decision is already solved.
- Source/tag/commit/license data is recorded when a dependency is introduced.
- Proprietary planning references are never treated as redistributable production assets.
