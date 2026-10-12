# Authentication flow

The login navigation route renders `ui/screens/LoginScreen` with an explicitly injected
`RepositoryAuthDataSource` backed by `FirebaseAuthRepository`. It supports email/password
sign-in, registration with a name and matching passwords, and password-reset emails.
There is no default mock dependency in the active screen or its ViewModel.

Registration saves the name in Firebase Auth. `AppContainer.onSignedIn()` uses that name
to bootstrap the Firestore profile without overwriting existing profiles. A failed profile
bootstrap is retried on the next launch/sign-in. If Auth account creation succeeds but
saving the name fails, the screen explains that the account exists and asks the user to
sign in and edit their name in Profile.

## Run the integration tests

Start an Android emulator/device and the local Firebase services:

```powershell
./functions/node_modules/.bin/firebase.cmd emulators:start --project demo-ecostep --only auth,firestore
adb reverse tcp:9099 tcp:9099
adb reverse tcp:8080 tcp:8080
./gradlew.bat :app:connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=com.ecostep.app.firebase.FirebaseAuthFlowTest,com.ecostep.app.firebase.FirebaseEmulatorJourneyRepositoryTest' '-Pandroid.testInstrumentationRunnerArguments.firebaseEmulatorHost=127.0.0.1'
```

The new Auth test exercises registration and login through Compose, password mismatch
validation, name persistence, ID tokens, duplicate email and wrong-password errors,
idempotent Firestore profile creation, and the forgot-password screen. It consumes the
emulator's reset code and verifies that the new password succeeds and the old one fails.

To explicitly test the live project configured by `app/google-services.json`:

```powershell
./gradlew.bat :app:connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=com.ecostep.app.firebase.FirebaseAuthFlowTest' '-Pandroid.testInstrumentationRunnerArguments.firebaseLiveTest=true'
```

The live test creates a unique temporary Auth account, exercises registration, name
persistence, token retrieval, duplicate/wrong-password handling and login, then deletes
that account. It does not write live Firestore profiles because client rules prohibit
deleting them, and does not test live email delivery. Tests skip unless a target is
explicitly supplied. Do not pass both target arguments together.

## Verification on 2026-10-12

- Debug APK and instrumentation APK compilation: passed.
- Firebase Emulator: all 5 integration tests passed, including the new Compose Auth
  flow and the 4 existing repository/security/offline/profile tests.
- Live Firebase Auth: registration flow passed; temporary account deleted.
- Android JVM suite on the branch prepared for main: 369 passed, 6 failed (375 total). Failures are confined to the DataStore route,
  weather and public-transport cache tests, with Windows `.preferences_pb.tmp` rename
  errors. These failures do not involve authentication.

Saved integration results: `app/build/reports/firebase-auth/emulator.xml` and
`app/build/reports/firebase-auth/live.xml` (generated files, not committed).
