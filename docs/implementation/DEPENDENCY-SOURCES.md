# Dependency, Source, and License Ledger

Updated: 2026-08-15. Exact records are required for every shipped dependency/source.

| Component | Version / ref | Exact source identity | License | Intended use |
|---|---|---|---|---|
| Stockfish | `sf_18` | `official-stockfish/Stockfish` commit `cb3d4ee9b47d0c5aae855b12379378ea1439675c` | GPL-3.0-or-later | Mandatory engine; integration begins M13 |
| Reckless | `v0.9.0` | `codedeliveryservice/Reckless` commit `0e92358f5acd66e5ac77b1bf558202e47c515435` | AGPL-3.0 | Mandatory engine; integration begins M14 |
| Android Gradle Plugin | 9.3.0 | Google Android build tooling; current stable channel | Apache-2.0 / component notices | Build tooling |
| Gradle | 9.5.0 | Gradle binary distribution; standard generated wrapper retained with CI wrapper validation enabled | Apache-2.0 | Build tooling |
| Kotlin | 2.3.21 plugin line | JetBrains Kotlin; version used by Google's current Compose Compiler setup guidance | Apache-2.0 | Kotlin/JVM and Compose compiler plugin |
| Jetpack Compose BOM | 2026.06.00 | AndroidX stable BOM | Apache-2.0 | UI version alignment |
| Android SDK Platform | Android 17 / API 37 (`platforms;android-37.0`) | Google Android SDK; Gradle uses `compileSdk = 37`, `targetSdk = 37`; SDK repository package uses the 37.0 identifier | Android SDK license | Locked initial platform target |
| Android SDK Command-Line Tools | build 15859902 | Current Google Linux command-line-tools download on 2026-08-15 | Android SDK license | CI SDK package resolution |
| Android SDK Build Tools | 36.0.0 | Google Android SDK; AGP 9.3 documented default | Android SDK license | Android packaging/build tools |
| Android 17 emulator image | `system-images;android-37.0;google_apis_ps16k;x86_64` | Google Android SDK system image; Google-hosted Chromium AVD config records API 37 r5 | Android SDK/system-image licenses | Gate A Compose instrumentation tests |
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
- Android API/AGP compatibility table (API 37.0 is supported by AGP 9.1.1+): https://developer.android.com/build/releases/about-agp
- Android 17 SDK/API documentation (`compileSdk = 37`, `targetSdk = 37`; Android 17 is API level 37): https://developer.android.com/about/versions/17/setup-sdk and https://developer.android.com/guide/topics/manifest/uses-sdk-element
- SDK Platform release notes (newest SDK components require the most recent Command-Line Tools): https://developer.android.com/tools/releases/platforms
- Current Android Studio / command-line-tools download page (Linux build 15859902): https://developer.android.com/studio/
- `sdkmanager` package-path and channel syntax: https://developer.android.com/tools/sdkmanager
- Android Emulator release notes (API 37 phone AVDs require at least 4 GB RAM): https://developer.android.com/studio/releases/emulator
- Google-hosted Chromium Android SDK roll showing Android SDK 37.0, Build Tools 37.0.0, and API 37 system images: https://chromium.googlesource.com/chromium/src/third_party/android_sdk/+/558f465abde520c47e52eb6dcfff15bd75994cf8
- Google-hosted Chromium correction explicitly renaming `android-37` to `android-37.0` because the decimal was missing: https://chromium.googlesource.com/chromium/src/third_party/android_sdk/+/1584c97931e42d0edb9335a657f0f4ba9484c43f
- Google-hosted Chromium Android 17 AVD config (`system-images;android-37.0;google_apis_ps16k;x86_64`, r5): https://chromium.googlesource.com/chromium/src/+/main/tools/android/avd/proto/android_37_google_apis_ps16k_x64.textpb
- Compose Compiler Gradle plugin (Kotlin/Compose compiler plugin 2.3.21): https://developer.android.com/develop/ui/compose/setup-compose-dependencies-and-compiler
- Compose BOM (current stable BOM 2026.06.00): https://developer.android.com/develop/ui/compose/bom
- Gradle distribution/wrapper checksum reference: https://gradle.org/release-checksums/

The existence of an AGP 9.4 release-notes page is not evidence that 9.4 is stable. Google's current Android Studio channel table identifies AGP 9.3.0 as Stable, while the 9.4 release notes currently enumerate `9.4.0-alpha*` builds.

Gate A's API 37 CI failure was ultimately a package-identifier mismatch. Both the runner's older sdkmanager and current command-line tools build 15859902 rejected `platforms;android-37`. Google's SDK packaging for this release uses `platforms;android-37.0`; a Google-hosted Chromium packaging change explicitly corrected the missing decimal. The Gradle-facing Android API level remains 37, so `compileSdk = 37` and `targetSdk = 37` stay unchanged.

CI still pins current command-line tools because Google's SDK Platform guidance requires current tools to expose the newest components, but the command-line-tools version was not the root cause of the failed package lookup.

The imported snapshot's custom Gradle bootstrap JAR was also rejected by `gradle/actions/setup-gradle` wrapper validation. It has been replaced with Gradle-generated wrapper artifacts; the validation check remains enabled rather than being bypassed.

## Distribution gate
This file records provenance; it is not legal advice. Before any public APK/AAB distribution, verify the complete transitive/shipped dependency set, required notices/source-offer obligations, and the compatibility of the chosen LumenChess license/distribution model.
