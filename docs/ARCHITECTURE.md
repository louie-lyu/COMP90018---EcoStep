# Architecture

Owner: Chi Hong Tam. This records the technical decisions behind the project setup, so the
rest of the team can build on it without re-deriving the same choices, and so this doubles as
source material for the report's architecture section and its "how every rubric criterion is
met" section (see `Assignment 2 - Mobile App Rubric.pdf`).

## Technical decisions

| Decision | Choice | Why |
|---|---|---|
| Language | Kotlin | Google's preferred language for Android; coroutines suit the sensor/network/Firebase work every module does |
| UI | Jetpack Compose (Material3) | Less boilerplate than XML; aligns with the rubric's UI-Guidelines criterion (Material Design) |
| Architecture | MVVM | Standard Android Architecture Components pattern; pairs naturally with Compose |
| minSdk | 26 (Android 8.0) | Covers the large majority of active devices without much compatibility burden |
| compileSdk / targetSdk | 37 (Android 17) | Current AndroidX/Compose/Firebase releases require compileSdk 36+; 37 is what's installed locally |
| applicationId / package | `com.ecostep.app` | Simple, product-focused; baked into Firebase config and every manifest, so it isn't changed casually |
| Module structure | Single `:app` Gradle module, feature packages | Multi-module Gradle overhead isn't worth it for a 6-person team on a 1-month deadline; packages still enforce boundaries |
| Dependency injection | None (manual `AppContainer`) | Lower learning curve/build risk than Hilt for a team that was still deciding Kotlin vs Java a week ago |
| Local offline-cache library | **Open** — Room vs Firestore's built-in offline persistence vs DataStore | Left to Zongcheng/Jianing as part of their own 27 Sep offline-handling tasks |
| Build system | Gradle 9.7.1 + AGP 9.4.0 + Kotlin 2.4.20 | Current stable versions, verified against a real build on this machine (see "Toolchain notes" below) |

## Package ownership

```
com.ecostep.app
├── EcoStepApp.kt, MainActivity.kt         — Chi Hong Tam (architecture)
├── core/theme, core/navigation, core/di   — Chi Hong Tam (architecture)
├── data/model/                            — shared data types (this doc's authors); every module reads these
├── data/repository/                       — contract interfaces; Zongcheng (Journey), Jianing (External)
├── algorithm/                             — contract interfaces; Duo implements all of them
├── ui/screens/                            — Yu-Han
└── sensors/, network/                     — not yet created; Zongcheng and Jianing add these packages
    when they start their own implementation work
```

Each interface under `data/repository/` and `algorithm/` carries a KDoc naming its owner and due
date (from `docs/WORK_PLAN.md`). Other modules should code against these interfaces, not wait for
the real implementation — see `docs/DEPENDENCIES.md` "Using Mock Data" for a mock implementation
pattern, and `app/src/test/assets/mock_journeys.json` for a ready-to-use `JourneySummary` fixture.

## Rubric alignment (`Assignment 2 - Mobile App Rubric.pdf`)

- **Connectivity (12 pts, the single highest-weighted Implementation criterion)** — Retrofit +
  OkHttp (foundational HTTP client) and the Firebase BOM (Auth + Firestore) are wired into
  `app/build.gradle.kts` from day one.
- **Sensors (10 pts)** — GPS via `play-services-location` is wired in; **the criterion is GPS
  *and* accelerometer *and* gyroscope** — easy to ship GPS+accelerometer only and quietly drop
  the gyroscope, so don't.
- **Responsiveness (6 pts)** — every sensor/network/Firebase call must run via coroutines off the
  main thread; never block the UI thread.
- **Reactiveness (6 pts)** — ViewModels expose `StateFlow`; screens collect via
  `collectAsStateWithLifecycle`. Wire new ViewModels through `AppContainer`'s `ViewModelFactory`
  (see `core/di/AppContainer.kt`) so this pattern stays consistent across all six owners.
- **UI Guidelines (6 pts)** — Compose + Material3 is the framework choice specifically for this.
- **Material — Screenshot (2 pts)** — the report needs a screenshot of the Android Studio console
  showing the app compiles. `./gradlew assembleDebug` succeeding (see "Verified build" below) is
  the same thing; capture the equivalent from an actual Android Studio run for the report.
- Everything else (Quality, Technical depth, UI Appeal/Flow/Language, all of Innovation) depends
  on the feature work each owner does from here — this setup doesn't block any of it.

## Firebase setup (Zongcheng)

1. Create/open the Firebase project, add an Android app with package name `com.ecostep.app`.
2. Download the real `google-services.json` and save it as `app/google-services.json` (this path
   is gitignored — never commit it). `app/google-services.json.example` shows the expected shape.
3. Uncomment the `com.google.gms.google-services` plugin line in `app/build.gradle.kts` (it's left
   off until step 2 is done, so the build doesn't break for everyone else in the meantime).
4. Add `journeyRepository` to `AppContainer` (see the TODO there), backed by `FirebaseAuth` /
   `FirebaseFirestore`.

## Toolchain notes (read this before you fight the build)

This scaffold was set up and verified in September 2026. The Android/Kotlin ecosystem moves
fast, and a few things changed in ways worth flagging so nobody re-discovers them the hard way:

- **AGP 9+ no longer uses the `org.jetbrains.kotlin.android` plugin** — Kotlin support is now
  built into the Android Gradle Plugin itself. Don't re-add it; AGP will refuse to build if you
  do.
- **Firebase's `-ktx` artifacts are deprecated** — use `firebase-auth` / `firebase-firestore`
  directly; the Kotlin extensions now ship in the base artifact.
- **If your machine's default `java` is very new (JDK 25/26 here), older Gradle versions can't
  run at all.** This project pins Gradle 9.7.1 (which does run fine on JDK 26 on this machine).
  If Android Studio complains about the Gradle JDK, or you're regenerating the wrapper with an
  older cached Gradle, point `JAVA_HOME`/the IDE's "Gradle JDK" setting at a JDK 17–21 for that
  one operation (Settings → Build, Execution, Deployment → Build Tools → Gradle).
- All dependency versions in `gradle/libs.versions.toml` were checked against Maven Central /
  Google's Maven repo metadata at setup time, not guessed — if a future bump breaks the build,
  check `maven-metadata.xml` for the artifact rather than assuming the old pin is still current.

## Verified build

Run from the repository root:

```bash
./gradlew assembleDebug   # compiles the app
./gradlew testDebugUnitTest   # runs unit tests
```

Both were run successfully during setup: `assembleDebug` produced `app/build/outputs/apk/debug/app-debug.apk`,
and `testDebugUnitTest` passed both tests in `JourneySummaryTest` (round-trip serialization, and
parsing `app/src/test/assets/mock_journeys.json`).

## Getting started (all owners)

1. Open the project root in Android Studio; let Gradle sync run (first sync downloads Gradle
   9.7.1 and all dependencies — takes a few minutes).
2. If prompted about the Gradle JDK and your system JDK is newer than 21, pick a bundled/17-21
   JDK in Settings → Build, Execution, Deployment → Build Tools → Gradle.
3. Build and run `app` on an emulator or device (minSdk 26+).
4. Find your package (`sensors`, `network`, `algorithm`, `ui`, or `data/repository`) per the
   ownership table above, and the interface(s) you're implementing.
