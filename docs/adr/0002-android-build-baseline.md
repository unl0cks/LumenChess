# ADR 0002 — Initial Android build baseline

**Status:** Accepted  
**Date:** 2026-08-15

## Context
The product is intentionally optimized first for Pixel 8 Pro on Android 17/API 37. Current Compose releases require API 37-capable Android Gradle Plugin versions.

## Decision
Initial build baseline:
- `compileSdk = 37`
- `targetSdk = 37`
- `minSdk = 37` during the first compatibility phase; M47 may lower this after the foundation is stable.
- Android Gradle Plugin 9.4.0.
- Gradle 9.6.0.
- JDK 17 toolchain.
- NDK 28.2.13676358 baseline for future native engine work.
- Compose BOM 2026.06.00.

## Consequences
- Early implementation is free to use Android 17 APIs without pretending older versions are currently supported.
- Broader compatibility is a deliberate later pass, not accidental scope creep.
- Native artifacts must be tested for modern Android packaging/16KB requirements before distribution.
