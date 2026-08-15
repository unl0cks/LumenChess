# Dependency, Source, and License Ledger

Updated: 2026-08-15. Exact records are required for every shipped dependency/source.

| Component | Version / ref | Exact source identity | License | Intended use |
|---|---|---|---|---|
| Stockfish | `sf_18` | `official-stockfish/Stockfish` commit `cb3d4ee9b47d0c5aae855b12379378ea1439675c` | GPL-3.0-or-later | Mandatory engine; integration begins M13 |
| Reckless | `v0.9.0` | `codedeliveryservice/Reckless` commit `0e92358f5acd66e5ac77b1bf558202e47c515435` | AGPL-3.0 | Mandatory engine; integration begins M14 |
| Android Gradle Plugin | 9.3.0 | Google Android build tooling; current stable channel | Apache-2.0 / component notices | Build tooling |
| Gradle | 9.5.0 | Gradle distribution; AGP 9.3 documented minimum/default | Apache-2.0 | Build tooling |
| Kotlin | 2.3.21 plugin line | JetBrains Kotlin; version used by Google's current Compose Compiler setup guidance | Apache-2.0 | Kotlin/JVM and Compose compiler plugin |
| Jetpack Compose BOM | 2026.06.00 | AndroidX stable BOM | Apache-2.0 | UI version alignment |
| Android SDK Platform | Android 17 / API 37 | Google Android SDK; `compileSdk = 37`, `targetSdk = 37` | Android SDK license | Locked initial platform target |
| Android SDK Build Tools | 36.0.0 | Google Android SDK; AGP 9.3 documented default | Android SDK license | Android packaging/build tools |
| Android NDK | 28.2.13676358 | Google Android NDK; AGP 9.3 documented default | Android NDK licenses/notices | Pinned baseline for later native engine integration; no M0-M5 native source yet |
| AndroidX Activity | 1.13.0 | AndroidX | Apache-2.0 | Compose activity host |
| AndroidX Lifecycle | 2.11.0 | AndroidX | Apache-2.0 | Lifecycle/ViewModel baseline |
| Room | 2.8.4 | AndroidX | Apache-2.0 | Persistence from M8 |
| DataStore | 1.2.1 | AndroidX | Apache-2.0 | Preferences/settings from M9 |
| WorkManager | 2.11.2 | AndroidX | Apache-2.0 | Persistent queues from M36 |
| AndroidX Test Runner | 1.7.0 | AndroidX | Apache-2.0 | Instrumented test runtime |
| AndroidX Test JUnit extensions | 1.3.0 | AndroidX | Apache-2.0 | Instrumented JUnit integration |
| JUnit Jupiter | 5.13.4 | JUnit team | EPL-2.0 | `core-chess` JVM test runner |

## Build-baseline verification sources

Authoritative Android/Google sources checked on 2026-08-15:

- Android Studio preview channel table (AGP 9.3.0 is Stable; AGP 9.4 is preview): https://developer.android.com/studio/preview/features
- AGP 9.3.0 compatibility table (API 37; Gradle 9.5.0; Build Tools 36.0.0; NDK 28.2.13676358; JDK 17): https://developer.android.com/build/releases/agp-9-3-0-release-notes
- Android 17 SDK setup (`compileSdk = 37`, `targetSdk = 37`; SDK Manager currently presents the platform as Cinnamon Bun Preview): https://developer.android.com/about/versions/17/setup-sdk
- `sdkmanager` channels (`--channel=3` includes canary/preview packages): https://developer.android.com/tools/sdkmanager
- Compose Compiler Gradle plugin (Kotlin/Compose compiler plugin 2.3.21): https://developer.android.com/develop/ui/compose/setup-compose-dependencies-and-compiler
- Compose BOM (current stable BOM 2026.06.00): https://developer.android.com/develop/ui/compose/bom

The existence of an AGP 9.4 release-notes page is not evidence that 9.4 is stable. Google's current Android Studio channel table identifies AGP 9.3.0 as Stable, while the 9.4 release notes currently enumerate `9.4.0-alpha*` builds.

## Distribution gate
This file records provenance; it is not legal advice. Before any public APK/AAB distribution, verify the complete transitive/shipped dependency set, required notices/source-offer obligations, and the compatibility of the chosen LumenChess license/distribution model.
