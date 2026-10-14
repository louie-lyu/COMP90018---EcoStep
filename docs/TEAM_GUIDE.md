# EcoStep development guide

This guide introduces team responsibilities and the original project scaffold. See [ARCHITECTURE.md](ARCHITECTURE.md) for technical decisions, [WORK_PLAN.md](WORK_PLAN.md) for tasks and deadlines, and [DEPENDENCIES.md](DEPENDENCIES.md) for data flow and mock data. Scaffold descriptions and September deadlines below are historical planning context, not a current implementation audit.

## Project and architecture

EcoStep records journeys using GPS, accelerometer and gyroscope data, identifies transport modes, calculates carbon emissions, and suggests lower-carbon EcoMissions. Completing missions earns EcoPoints.

Flow: record journey -> classify mode -> user confirms/corrects -> fetch weather/routes -> calculate carbon -> suggest mission -> accept and complete -> earn points.

The scaffold uses Kotlin, Compose/Material3, MVVM, a single `:app` module organized by packages, and manual dependency injection through `AppContainer`. Its documented SDK/build baseline was minSdk 26, compileSdk 37, Gradle 9.7.1 and AGP 9.4.0; the checked-in Gradle files determine the actual current versions.

| Package/file | Purpose in the original scaffold |
|---|---|
| `data/model/` | Shared models, including JourneySummary, TransportResult and CarbonResult |
| `data/repository/` | Repository interfaces for Zongcheng and Jianing to implement |
| `algorithm/` | Algorithm interfaces for Duo |
| `ui/screens/` | Originally six placeholder screens; see the later UI implementation note below |
| `core/theme/` | Default Compose colors and typography |
| `core/navigation/` | Screen navigation |
| `core/di/` | AppContainer, shared HTTP client and repository wiring |
| `EcoStepApp.kt` | Application entry point |
| `MainActivity.kt` | Single Compose activity |

Dependencies were prepared in [app/build.gradle.kts](../app/build.gradle.kts) so owners could start independently. Interface KDoc records owners/deadlines; historical `TODO(owner)` markers in AppContainer identify integration points.

## Responsibilities

### Zongcheng Jiang: sensors, tracking, authentication and database

Collect GPS, accelerometer and gyroscope data in `sensors/`, produce JourneySummary, implement JourneyRepository, and connect Firebase Auth/Firestore. The original setup sequence was to create a Firebase project, replace `app/google-services.json.example` with ignored `app/google-services.json`, enable the Google Services plugin, and wire the repository through AppContainer. Check current code before repeating completed setup. JourneySummary was due 20 September and Firebase setup 13 September. Use `app/src/test/assets/mock_journeys.json` when integration is blocked.

### Jianing Xia: weather, maps and public transport

Implement provider Retrofit clients in `network/` and ExternalDataRepository in `data/repository/`, returning WeatherData, RouteInfo and PublicTransportInfo. Reuse AppContainer's pooled `okHttpClient`, with HTTP logging restricted to debug builds, and wire the repository there. Deadline: 27 September. Provide fake weather/routes while blocked so Duo can test MissionContext assembly.

### Duo Lyu: algorithms, carbon, EcoPoints and AI

Implement CarbonCalculator by 14 September, EcoPointsCalculator by 17 September, TransportClassifier and MissionGenerator by 20 September, and MissionValidator by 23 September. Validation must reject AI replies that alter protected data and trigger fallback. Add JUnit tests under `app/src/test/java/com/ecostep/app/algorithm/`; JourneySummaryTest demonstrates JUnit and serialization. Start with mock_journeys.json rather than waiting for sensor integration. Both TransportResult.confidence and EcoMission.confidence use the **0-100** scale.

### Yu-Han Wang: UI

The original assignment was to replace six placeholders. A later update recorded these implemented screens: LoginScreen, HomeScreen, JourneyReviewScreen, MissionScreen, JourneyHistoryScreen, ProfileScreen, RewardsScreen and WeeklyInsightScreen. The unused shared PlaceholderScreen was removed. Add journey ID and other route arguments in NavGraph/Routes as required. Theme changes are optional. Obtain data through ViewModels constructed with AppContainer's ViewModelFactory pattern. Journey review was due 20 September; other screens were due 27 September.

### Rui Fang: evaluation

Measure transport classification accuracy and latency, and verify recurring-route detection. Device tests belong under `app/src/androidTest/java/com/ecostep/app/`; Espresso and Compose testing dependencies were prepared in app Gradle. Measure Duo's actual classifier once integrated. Deadline: 27 September. Current Android Studio evaluation instructions are in [the evaluation README](../app/src/androidTest/java/com/ecostep/app/evaluation/README.md).

## Getting started and collaboration

Clone the repository, open its root in Android Studio, and sync Gradle. The original setup guide suggested JDK 17-21 in Settings > Build, Execution, Deployment > Build Tools > Gradle; use the current build's required JDK/toolchain. Run `app` on an emulator, then work in your assigned package. The original scaffold showed six navigable TODO screens; that is a historical description.

Shared model changes follow [Interface Changes](DEPENDENCIES.md#interface-changes): open an issue, notify affected owners, and agree the interface. Remove package placeholder READMEs once real implementations replace them. Work on a feature branch, link its issue in the PR, and obtain review before merging; see [How We Work](../README.md#how-we-work). When blocked, use [mock data](DEPENDENCIES.md#using-mock-data) so dependent work can continue.
