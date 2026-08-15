# ADR 0002 — Initial Android build baseline

**Status:** Accepted  
**Date:** 2026-08-15

## Context
The product target remains intentionally locked to Pixel 8 Pro on Android 17/API 37. Gate A's first GitHub Actions run failed before Gradle because CI attempted to install `platforms;android-37` from sdkmanager's default stable channel and the package was not discoverable there.

The original scaffold also pinned Android Gradle Plugin 9.4.0 and Gradle 9.6.0. Re-verification against current Android/Google documentation found that this was a release-channel mistake: Google's Android Studio channel table identifies AGP 9.3.0 as the current Stable release, while AGP 9.4 is still on `9.4.0-alpha*` preview builds. The AGP 9.3 compatibility table documents API 37 support with Gradle 9.5.0, Build Tools 36.0.0, NDK 28.2.13676358, and JDK 17.

Android's current Android 17 setup guidance still specifies `compileSdk = 37` and `targetSdk = 37`, while SDK Manager presents the Android 17 platform as Cinnamon Bun Preview. `sdkmanager` requires an explicit preview/canary channel when a required package is not yet published on the default stable package channel.

Current Compose guidance uses Kotlin/Compose compiler plugin 2.3.21 and the stable Compose BOM 2026.06.00.

## Decision
Initial build baseline:
- `compileSdk = 37`.
- `targetSdk = 37`.
- `minSdk = 37` during the first compatibility phase; M47 may lower this after the foundation is stable.
- Android Gradle Plugin 9.3.0 (current Stable channel).
- Gradle 9.5.0 (AGP 9.3 documented minimum/default).
- JDK 17 toolchain.
- Kotlin / Compose Compiler Gradle plugin 2.3.21.
- Compose BOM 2026.06.00.
- Android SDK Build Tools 36.0.0.
- NDK 28.2.13676358 retained as the pinned baseline for later native engine work. No M0-M5 native source currently depends on the NDK.
- Headless CI installs the API 37 platform with sdkmanager channel 3 enabled until the package is available through the default stable channel. This does **not** opt the project into preview AGP; it only makes the currently published Android 17 SDK package discoverable to sdkmanager.

## Verification sources
- https://developer.android.com/studio/preview/features
- https://developer.android.com/build/releases/agp-9-3-0-release-notes
- https://developer.android.com/about/versions/17/setup-sdk
- https://developer.android.com/tools/sdkmanager
- https://developer.android.com/develop/ui/compose/setup-compose-dependencies-and-compiler
- https://developer.android.com/develop/ui/compose/bom

## Consequences
- The locked Android 17/API 37 product target is preserved; Gate A does not downgrade the app to API 36 to work around CI package discovery.
- The build uses Google's current stable AGP line rather than an AGP preview assumption.
- Gradle is pinned to the version explicitly paired with AGP 9.3 by Google's compatibility table.
- CI may remove `--channel=3` once the API 37 platform is published on sdkmanager's default stable channel.
- Early implementation remains free to use Android 17 APIs without pretending older versions are currently supported.
- Broader compatibility remains a deliberate later pass, not accidental scope creep.
- Native artifacts must be tested for modern Android packaging/16KB requirements before distribution.
