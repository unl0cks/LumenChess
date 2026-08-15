# Dependency, Source, and License Ledger

Updated: 2026-08-15. Exact records are required for every shipped dependency/source.

| Component | Version / ref | Exact source identity | License | Intended use |
|---|---|---|---|---|
| Stockfish | `sf_18` | `official-stockfish/Stockfish` commit `cb3d4ee9b47d0c5aae855b12379378ea1439675c` | GPL-3.0-or-later | Mandatory engine; integration begins M13 |
| Reckless | `v0.9.0` | `codedeliveryservice/Reckless` commit `0e92358f5acd66e5ac77b1bf558202e47c515435` | AGPL-3.0 | Mandatory engine; integration begins M14 |
| Android Gradle Plugin | 9.4.0 | Google Android build tooling | Apache-2.0 / component notices | Build tooling |
| Gradle | 9.6.0 | Gradle distribution | Apache-2.0 | Build tooling |
| Kotlin | 2.3.21 plugin line | JetBrains Kotlin | Apache-2.0 | Kotlin/JVM and Compose compiler plugin |
| Jetpack Compose BOM | 2026.06.00 | AndroidX | Apache-2.0 | UI version alignment |
| AndroidX Activity | 1.13.0 | AndroidX | Apache-2.0 | Compose activity host |
| AndroidX Lifecycle | 2.11.0 | AndroidX | Apache-2.0 | Lifecycle/ViewModel baseline |
| Room | 2.8.4 | AndroidX | Apache-2.0 | Persistence from M8 |
| DataStore | 1.2.1 | AndroidX | Apache-2.0 | Preferences/settings from M9 |
| WorkManager | 2.11.2 | AndroidX | Apache-2.0 | Persistent queues from M36 |
| AndroidX Test Runner | 1.7.0 | AndroidX | Apache-2.0 | Instrumented test runtime |
| AndroidX Test JUnit extensions | 1.3.0 | AndroidX | Apache-2.0 | Instrumented JUnit integration |
| JUnit Jupiter | 5.13.4 | JUnit team | EPL-2.0 | `core-chess` JVM test runner |

## Distribution gate
This file records provenance; it is not legal advice. Before any public APK/AAB distribution, verify the complete transitive/shipped dependency set, required notices/source-offer obligations, and the compatibility of the chosen LumenChess license/distribution model.
