# ADR 0002 — Initial Android build baseline

**Status:** Accepted  
**Date:** 2026-08-15

## Context
The product target remains intentionally locked to Pixel 8 Pro on Android 17/API 37. Gate A's first GitHub Actions run failed before Gradle because CI attempted to install `platforms;android-37`, which is not the SDK repository identifier currently published for the Android 17 platform.

The original scaffold also pinned Android Gradle Plugin 9.4.0 and Gradle 9.6.0. Re-verification against current Android/Google documentation found that this was a release-channel mistake: Google's Android Studio channel table identifies AGP 9.3.0 as the current Stable release, while AGP 9.4 is still on `9.4.0-alpha*` preview builds. The AGP 9.3 compatibility table documents API 37 support with Gradle 9.5.0, Build Tools 36.0.0, NDK 28.2.13676358, and JDK 17.

Android's Android 17 documentation identifies the platform as API level 37 and specifies `compileSdk = 37` and `targetSdk = 37`. The SDK repository package has a separate versioned identifier: Google's SDK packaging for this release uses `platforms;android-37.0`. A Google-hosted Chromium SDK packaging correction explicitly renamed `android-37` to `android-37.0` because the decimal was missing.

Gate A confirmed the distinction empirically: both the runner's older sdkmanager and current command-line tools build 15859902 rejected `platforms;android-37`. The latter rules out stale command-line tools as the root cause. Current command-line tools remain pinned because Google's SDK Platform guidance requires the most recent tools to expose the newest SDK components.

The imported M0-M5 snapshot also contained a hand-written `gradle-wrapper.jar` bootstrap rather than a standard Gradle-generated wrapper. `gradle/actions/setup-gradle` correctly rejected that unknown JAR during wrapper validation. Gate A replaced the custom bootstrap with Gradle-generated wrapper artifacts and kept wrapper validation enabled; bypassing the validation check was explicitly rejected as the wrong fix.

Current Compose guidance uses Kotlin/Compose compiler plugin 2.3.21 and the stable Compose BOM 2026.06.00. The existing M2 Compose semantics test is an instrumented Android test, so Gate A CI also needs a real Android 17 emulator rather than merely compiling the test APK. Google's current Android 17 x86_64 test image is `system-images;android-37.0;google_apis_ps16k;x86_64`; API 37 phone AVDs require at least 4 GB RAM.

When Gate A first executed the existing Compose instrumentation test on Android 17, the test runtime failed before reaching LumenChess assertions because an older transitive Espresso event injector reflected `InputManager.getInstance()`. AndroidX Test's current stable Espresso 3.7.0 explicitly fixes that path by using `getSystemService`, so the test dependency baseline must be corrected rather than changing or weakening the existing test.

## Decision
Initial build baseline:
- `compileSdk = 37`.
- `targetSdk = 37`.
- `minSdk = 37` during the first compatibility phase; M47 may lower this after the foundation is stable.
- Android Gradle Plugin 9.3.0 (current Stable channel).
- Gradle 9.5.0 (AGP 9.3 documented minimum/default).
- Standard Gradle-generated wrapper artifacts with wrapper validation left enabled.
- JDK 17 toolchain.
- Kotlin / Compose Compiler Gradle plugin 2.3.21.
- Compose BOM 2026.06.00.
- Android SDK Platform repository package `platforms;android-37.0` for Android 17/API 37.
- Android SDK Command-Line Tools build 15859902 in CI.
- Android SDK Build Tools 36.0.0 (AGP 9.3 documented default).
- NDK 28.2.13676358 retained as the pinned baseline for later native engine work. No M0-M5 native source currently depends on the NDK.
- AndroidX Test Runner 1.7.0, AndroidX Test JUnit extensions 1.3.0, and explicit Espresso Core 3.7.0 for Android 17 instrumentation-test runtime compatibility.
- Headless CI enables sdkmanager channel 3 while the Android 17 packages remain on a preview-capable SDK channel.
- Compose instrumentation tests execute on `system-images;android-37.0;google_apis_ps16k;x86_64` with a 4 GB AVD.

## Verification sources
- https://developer.android.com/studio/preview/features
- https://developer.android.com/build/releases/agp-9-3-0-release-notes
- https://developer.android.com/build/releases/about-agp
- https://developer.android.com/about/versions/17/setup-sdk
- https://developer.android.com/guide/topics/manifest/uses-sdk-element
- https://developer.android.com/tools/releases/platforms
- https://developer.android.com/studio/
- https://developer.android.com/tools/sdkmanager
- https://developer.android.com/studio/run/emulator-troubleshooting
- https://developer.android.com/studio/releases/emulator
- https://chromium.googlesource.com/chromium/src/third_party/android_sdk/+/adc8a6a38dc3af937727cb6c0102b60186aeb2a7
- https://chromium.googlesource.com/chromium/src/third_party/android_sdk/+/1584c97931e42d0edb9335a657f0f4ba9484c43f
- https://chromium.googlesource.com/chromium/src/+/main/tools/android/avd/proto/android_37_google_apis_ps16k_x64.textpb
- https://developer.android.com/jetpack/androidx/releases/test
- https://developer.android.com/develop/ui/compose/setup-compose-dependencies-and-compiler
- https://developer.android.com/develop/ui/compose/bom
- https://gradle.org/release-checksums/

## Consequences
- The locked Android 17/API 37 product target is preserved; Gate A does not downgrade the app to API 36 to work around CI package discovery.
- The build uses Google's current stable AGP line rather than an AGP preview assumption.
- Gradle is pinned to the version explicitly paired with AGP 9.3 by Google's compatibility table.
- CI uses the SDK repository's actual Android 17 platform identifier, `platforms;android-37.0`, while Gradle continues to use API level 37.
- CI pins current command-line tools rather than inheriting the older setup action default, even though stale tools were not the root cause of the failed platform lookup.
- The nonstandard wrapper bootstrap is removed; Gradle's normal wrapper scripts/JAR are used and CI's wrapper-integrity validation remains active.
- The existing Compose semantics test remains unchanged; the AndroidX Test runtime is corrected so the test can reach its real assertions on Android 17.
- The existing Compose semantics test is executed on an Android 17 16 KB-page-size emulator image instead of only being compiled.
- Early implementation remains free to use Android 17 APIs without pretending older versions are currently supported.
- Broader compatibility remains a deliberate later pass, not accidental scope creep.
- Native artifacts must be tested for modern Android packaging/16KB requirements before distribution.
