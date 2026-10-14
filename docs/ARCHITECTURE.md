# Architecture

This records the technical decisions behind EcoStep so the rest of the team can build on them without re-deriving the same choices, and doubles as source material for the report's architecture section.

## Technical decisions

| Decision | Choice | Why |
|---|---|---|
| Language | Kotlin | Google's preferred language for Android; coroutines suit the sensor, network and Firebase work every module does |
| UI | Jetpack Compose (Material3) | Less boilerplate than XML; follows Material Design for the UI-Guidelines criterion |
| Architecture | MVVM | Each screen has a ViewModel that exposes an immutable `uiState: StateFlow`; screens only render state and send events |
| minSdk | 26 (Android 8.0) | Covers the large majority of active devices without much compatibility burden |
| compileSdk / targetSdk | 37 (Android 17) | Current AndroidX, Compose and Firebase releases require compileSdk 36 or later |
| applicationId / package | `com.ecostep.app` | Baked into the Firebase configuration and every manifest, so it is not changed casually |
| Module structure | Single `:app` Gradle module with feature packages, plus `functions/` and `cloud/` for backend code | Multi-module Gradle overhead is not worth it for a six-person, one-month project |
| Dependency injection | None; manual `AppContainer` and a shared `ViewModelFactory` | Lower learning curve and build risk than Hilt; one place wires every production implementation |
| Local caching | **Resolved:** Preferences DataStore for weather, route and public-transport results (`data/cache/`); Firestore's built-in offline persistence for user data | Short-lived API results need an expiry policy; user data needs synchronisation, which Firestore provides |
| Map | osmdroid with OpenStreetMap tiles | Free, no API key and no billing account |
| Backend | Firebase Authentication, Cloud Firestore and Cloud Functions (TypeScript) | Per-user data with server-side rules, and trusted logic for points, rewards, friends and leaderboards |
| Third-party API access | Cloudflare Workers for routes (OpenRouteService) and AI (Gemini) | API keys stay on the server; the Workers verify the Firebase ID token |
| Reminders | `AlarmManager` with a notification channel | Mission reminders without a foreground service |
| Build system | Gradle 9.7.1, Android Gradle Plugin 9.3.0, Kotlin 2.4.20 | Pinned in `gradle/libs.versions.toml` and the wrapper |

## Data flow

```
Compose screen -> ViewModel -> repository interface (data/repository, algorithm)
                                 -> implementation (data/firebase, network, data/cache)
                ui/adapters bridge the UI data-source contracts to the repositories
functions/      Cloud Functions: points ledger, rewards, friends, leaderboards
firestore.rules the final authorization boundary
cloud/          Cloudflare Workers: route proxy and AI proxy
```

- `AppContainer` provides every production dependency, and `ViewModelFactory` builds ViewModels
  from it, so all screens are wired the same way.
- The `ui/mock` package keeps the UI data-source interfaces and the mock implementations used for
  previews and tests. Production navigation uses the repository-backed classes in `ui/adapters`.
- Shared data models in `data/model` and the interfaces in `data/repository` and `algorithm` are
  the contracts between modules, so a module can be developed and tested against a mock until the
  real implementation is ready.

### Main user flow

```
Login -> Home (Map tab)
      -> search a destination, choose a route
      -> Start -> journey tracking (GPS, accelerometer, gyroscope)
      -> End -> Journey Review (confirm transport mode) -> saved to Firestore
      -> carbon and alternatives calculated, mission suggested (AI, local fallback)
      -> Missions, Weekly Insight, Rewards, Profile
```

## Firebase setup

1. The Firebase project has an Android app registered with package name `com.ecostep.app`.
2. The real `app/google-services.json` is placed in `app/`. It is git-ignored, so it is supplied
   separately; `app/google-services.json.example` shows the expected shape.
3. The `com.google.gms.google-services` plugin is already applied in `app/build.gradle.kts`, so
   the build needs that file to be present.
4. To use the local Firebase Emulator Suite, build with
   `-Pecostep.firebaseEmulatorHost=127.0.0.1` and forward ports 9099, 8080 and 5001 with
   `adb reverse`.
5. Backend deployment (Cloud Functions, Firestore rules and indexes) is manual and targets the
   real project explicitly; `.firebaserc` defaults to a demo project so an accidental deploy
   cannot reach production.

## Toolchain notes

- **AGP 9 and later no longer use the `org.jetbrains.kotlin.android` plugin.** Kotlin support is
  built into the Android Gradle Plugin. Do not add it back.
- **Firebase's `-ktx` artifacts are deprecated.** Use `firebase-auth` and `firebase-firestore`
  directly.
- **JDK:** run Gradle on JDK 17 or later. The build selects JDK 21 as its toolchain, and the
  foojay resolver in `settings.gradle.kts` downloads it if it is missing. If Android Studio
  complains about the Gradle JDK, choose a JDK 17 to 21 under Settings, Build, Execution,
  Deployment, Build Tools, Gradle.
- **Versions:** all dependency versions are pinned in `gradle/libs.versions.toml`. If a bump
  breaks the build, check the artifact's Maven metadata rather than assuming the old pin is still
  current.
- **Windows:** use `gradlew.bat` instead of `./gradlew`.

## Verified build

Run from the project root:

```bash
./gradlew assembleDebug        # compiles the app
./gradlew testDebugUnitTest    # runs unit tests
./gradlew lintDebug            # runs Android lint
```

## Getting started

1. Open the project root in Android Studio and let the Gradle sync finish. The first sync
   downloads Gradle and all dependencies, which takes a few minutes.
2. Make sure `app/google-services.json` is in place.
3. Build and run the `app` module on an emulator or a device with Android 8.0 or later.
4. For emulator testing, set the location near your destination under Extended controls,
   Location. Destination search only covers places within about 30 km of the current location.
