# Minimal reliability fixes — 10 October 2026

The application architecture and Firestore schema are unchanged.

## Changes

- Mission Start and Skip now require a scheduled day and an occurrence that has not
  already completed or been skipped. Mission and Home cards disable unavailable actions.
- Rebinding the same account recalculates today's state. A cancellable coroutine refreshes
  it at the next local midnight; sign-out cancels the timer.
- Creation waits for the existing repository's SYNCED or QUEUED write outcome before
  closing the editor. A failure retains the form and mission ID for retry, and concurrent
  submissions are ignored. The existing route estimation is reused.
- Weekly Insight shows loading, failure and Retry states without concurrent loads.
- The three non-observable locale reads now use the Compose configuration locale.
- Automatic detection settings explicitly describe foreground automatic start and manual
  end/save. The automatic detection implementation is unchanged.

## Validation

- `gradlew.bat assembleDebug lintDebug`: passed; 0 Lint errors, 11 existing warnings.
- Windows full unit suite: 372 tests, 366 passed; the same six DataStore overwrite tests
  fail while renaming a temporary file over an existing file.
- Linux/WSL Java 21, the same compiled tests and runtime dependencies: 372 tests passed.
  The JUnit runner was started from `app/`, matching Gradle's working directory for fixtures.
- The unchanged weather, route and public transport cache suites also passed separately
  on Linux: 12 tests. No production cache changes or test exclusions were introduced.
- Regression coverage includes off-schedule actions, skipped/completed occurrences,
  midnight and same-account refresh, creation failure/retry with one ID, queued saves,
  duplicate submit protection, and weekly report retry.

## Repeating the cache check on Windows with WSL

Requires Java 21 in the chosen Linux distribution and the normal Windows Android build
environment. The script compiles the existing tests, exports their classpath, and runs them
with Linux Java. It does not install a runtime or modify production cache code.

```powershell
.\scripts\verify-cache-linux.ps1 -Distribution Ubuntu -LinuxJavaPath /usr/bin/java
```

For this local verification a Linux Temurin 21 JRE was extracted under the ignored
`build/cache-verification/` directory and passed via `-LinuxJavaPath`.

## Deferred

Transport recognition evaluation was deferred at the user's request. Existing evaluation
reports were not replaced. Physical-device UI/end-to-end testing and production Firebase
write verification were not performed in this pass.
