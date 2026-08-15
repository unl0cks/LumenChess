# ADR 0006 — Accessibility starts in foundational UI components

**Status:** Accepted  
**Date:** 2026-08-15

## Context
Deferring all accessibility until the final polish pass makes component APIs harder to correct and usually produces inconsistent touch/semantic behavior.

## Decision
Foundational interactive Compose components require useful semantics and Android-appropriate minimum touch targets from their first implementation. The full M47 pass still owns comprehensive TalkBack flows, large-text behavior, reduced motion, contrast review, and broader device adaptation.

## Consequences
Accessibility is part of component correctness rather than a decorative late-stage layer.
