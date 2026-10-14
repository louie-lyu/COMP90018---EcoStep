# Android Studio evaluations

All new device tests are in this directory inside the existing `app` module. The original evaluation classes and host scripts remain available. Evaluation 13 (UI latency) is unchanged.

## Run from Android Studio

1. Sync Gradle and start an API 26+ emulator in Device Manager. Pixel 8 / API 36 is the verified configuration.
2. Select a shared **Evaluation - ...** configuration in the top toolbar and click **Run**, not Debug.
3. Open the report directory printed as `Evaluation reports:` in the Run console.

No PowerShell script, separate evaluation module, or manual Build Variant change is needed. Gradle connected tests target connected devices; connect only the intended emulator. The configurations supply their own build properties. Use these presets for device tests: a gutter-generated configuration can select the wrong variant or omit the dataset and report export.

| Configuration | Measurement and default workload |
|---|---|
| Evaluation - 1 Weather latency | Original repository network latency: 20 requests, separate warm-up, 100 disk-cache hits. Network timing includes parsing and cache writes; pacing and warm-up are excluded from percentiles. |
| Evaluation - 1 Weather latency smoke | 1 network request, separate warm-up, 3 cache hits. |
| Evaluation - 2 Weather cache | Original 9 deterministic freshness-boundary, offline fallback/expiry, recovery, persistence and removal checks. No live provider required. |
| Evaluation - 3 Weather recovery | Original online fetch, offline cached response, and network recovery/refresh flow. Temporarily changes emulator connectivity and restores it in cleanup. |
| Evaluation - 7 AI proxy | 6 synthetic fixtures, 3 samples each: 4 mission cases and 2 weekly cases through the authenticated Worker and Gemini. |
| Evaluation - 7 AI proxy smoke | One request for `mission_walking_only`. |
| Evaluation - 12 Transport classification | Original SHL loading, filtering, seed 42 and full available sample set; real current transit plans and production classifier. |
| Evaluation - 12 Transport smoke | At most 1 selected SHL segment per evaluated transport mode; a connectivity and execution check, not an accuracy estimate. |
| Evaluation - correctness | 314 original correctness cases on the host JVM; no device required. |
| Evaluation - benchmarks | All 33 Microbenchmark groups with normal warm-up and timing. |
| Evaluation - benchmark smoke | All 33 groups without timing or trace output. |
| Evaluation - EcoPoints | 8 algorithm groups. |
| Evaluation - AI mission | 2 groups: validator and fallback. This is local algorithm testing, separate from live evaluation 7. |
| Evaluation - Weekly coach | 6 algorithm groups. |
| Evaluation - Mission trigger | 5 algorithm groups. |
| Evaluation - Local flow | 4 algorithm groups. |
| Evaluation - Recurring journey | 8 algorithm groups. |

You do not need to run the 33 groups separately. For a single method, duplicate its class configuration in **Run > Edit Configurations** and append `#methodName` to the instrumentation `class` argument, for example `com.ecostep.app.evaluation.new.AiMissionBenchmark#validator`. A parameterized method runs all of its parameter sets. JVM correctness classes and methods can also run from their green gutter buttons.

## Live device prerequisites

Evaluations 1, 3, 7 and 12 need Internet access. They run on emulators without physical sensors. Provider failures, authentication failures and rate limits remain visible in reports. Weather and AI assert their original success criteria. SHL preserves its original completion criterion: network fallback and timeout outcomes remain in the accuracy/latency report rather than automatically failing the run; no accuracy threshold is imposed.

For evaluation 7, first run the normal debug app on the same emulator and sign in to a dedicated Firebase test account. Stop the app, then run the AI configuration. It retrieves the existing session's ID token; passwords and tokens are not included in configuration files, assets or reports. Keep the same debug application ID and signing key, and do not clear app data or uninstall between sign-in and testing. The report measures token retrieval separately, not the host script's password sign-in. Requests have no retries or warm-up; default pacing is 1 second. HTTP 400/401/403/404/429 stops the remaining requests. Reports distinguish HTTP success, envelope/schema/business validity, accepted replies and skipped requests. Prompt fixtures and validation rules match the retained host runner; this does not measure semantic quality or raw Gemini JSON parsing.

Evaluation 12 packages `app/src/androidTest/java/com/ecostep/app/evaluation/data/SHL-preview-evaluation.zip` into the test APK automatically, only for the transport configuration. Obtain that archive using the original dataset instructions if it is absent. To use a different local copy, add `-Pecostep.shlDataset=C:/path/SHL-preview-evaluation.zip` to the configuration. No `adb push` or dataset hash is needed. Historical SHL coordinates are combined with current timetables, so these results are not a general real-world accuracy claim. Full runs take several minutes. HTTP 403/429 stops further planner requests.

To change workload, duplicate a configuration and edit its arguments, for example `-Pandroid.testInstrumentationRunnerArguments.samplesPerCase=1`. Weather supports `networkSamples`, `cacheSamples`, `intervalMs`, `timeoutMs`; AI supports `samplesPerCase`, `caseName`, `intervalMs`, `timeoutMs`; SHL supports `samplesPerMode` and `seed`. Keep smoke reports separate from full measurements.

## Source layout and correctness coverage

| Original evaluation | JVM correctness class | Cases | Microbenchmark class | Groups |
|---|---|---:|---|---:|
| 4 | CarbonCorrectnessTest | 56 | None: original coverage is correctness only | 0 |
| 5 | EcoPointsCorrectnessTest | 110 | EcoPointsBenchmark | 8 |
| 6 | AiMissionCorrectnessTest | 34 | AiMissionBenchmark | 2 |
| 8 | WeeklyCoachCorrectnessTest | 19 | WeeklyCoachBenchmark | 6 |
| 9 | MissionTriggerCorrectnessTest | 29 | MissionTriggerBenchmark | 5 |
| 10 | LocalFlowCorrectnessTest | 25 | LocalFlowBenchmark | 4 |
| 11 | RecurringJourneyCorrectnessTest | 41 | RecurringJourneyBenchmark | 8 |
| Total | | 314 | | 33 |

Correctness sources remain in `app/src/test/java/com/ecostep/app/evaluation/`. `AiProxyChecksTest` separately tests the new live-reply validator. Original `*EvaluationCases.kt` fixtures stay in their original directory and are copied by Gradle to `build/generated/evaluationCases` for JVM reuse. Shared checks are copied to `build/generated/evaluationChecks` for instrumentation; do not edit generated sources.

`BenchmarkChecks.kt` checks results outside timing. There are no added performance pass thresholds. EcoPoints measures a batch of 1,000 calls; other groups measure one operation, including the original full one- or three-journey Local flow. Inputs and outer assertions are excluded from timing; validation inside Local flow remains part of the operation. Suspend algorithms use one `runBlocking` around the measurement loop.

The non-debuggable `benchmark` build uses the `.benchmark` application suffix, AOT compilation and an isolated application without AppContainer or Firebase initialization. Only the EMULATOR restriction is suppressed. Normal warm-up and sampling remain enabled. Emulator measurements support comparisons on the same host and emulator configuration; they do not represent physical-phone performance. Original wall-clock reports and Microbenchmark measurements use different sampling methods and must not be merged.

Default debug instrumentation excludes this `new` package. Benchmark configurations explicitly select the six algorithm classes, so they cannot start live network tests. New device configurations select exactly one device test class. All tests belong to `app`; no sibling `evaluation` folder is needed. The previous standalone module was archived in ignored `app/build/previous-evaluation/`.

## Reports

Every preset device run exports JSON and CSV to:

`app/build/reports/evaluation/<weather-latency|weather-cache|weather-recovery|ai-proxy|transport|algorithms>/<run timestamp>/`

Device-specific subdirectories preserve the original report names. JSON contains metadata, summaries and records; CSV contains per-record fields, with nested values encoded as JSON. Export also runs after a failed connected-test task, so partial results remain available when instrumentation produced them. Build/install failures before instrumentation cannot produce a device report.

Algorithm runs retain AndroidX JSON and add `summary.json` / `summary.csv` with device, scenario, calls per measurement, sample count, median, p95, minimum, maximum, per-call mean/median, allocation median and warm-up/repeat counts. Values come from AndroidX `timeNs`, not a second stopwatch. p95 uses nearest rank over warmed measurement averages; it is not individual-call tail latency. EcoPoints per-call fields divide by 1,000. Dry runs have zero timing rows and never reuse an earlier run's timings.

- JVM HTML: `app/build/reports/tests/testDebugUnitTest/index.html`.
- Device pass/fail HTML: `app/build/reports/androidTests/connected/debug/index.html`.
- Benchmark pass/fail HTML: `app/build/reports/androidTests/connected/benchmark/index.html`.
- Original AndroidX JSON and traces: `app/build/outputs/connected_android_test_additional_output/benchmarkAndroidTest/connected/`.
- Original device JSON/CSV: `app/build/outputs/connected_android_test_additional_output/debugAndroidTest/connected/`.

The current connected-test HTML is replaced by subsequent runs; exported timestamp directories keep each invocation separate. Reports are local build outputs and are removed by `clean`. Copy a desired report before cleaning.

Reference: [Android Microbenchmark setup](https://developer.android.com/topic/performance/benchmarking/microbenchmark-write).

## Verification baseline

On 2026-10-13, Pixel 8 / API 36 completed all 33 normal benchmark groups with warm-up and timing, zero failures and zero skips; all 33 JSON entries contained `timeNs` measurements (about 8 minutes total). The 314 original JVM correctness cases also passed. These are the retained migration results; see each timestamped report for subsequent device and smoke runs.

On the same emulator, the final integration validation completed:

- 33/33 normal benchmark groups (8 minutes 10 seconds) and 33/33 smoke groups, with no failures or skips. The automatic full export contained 33 timing rows; smoke contained zero. Exported medians and nearest-rank p95 values were checked against AndroidX JSON.
- 333/333 evaluation JVM tests: 314 migrated correctness cases, 7 new AI reply-validator tests and 12 existing helper tests. The final debug instrumentation APK compiled successfully.
- Evaluation 1 smoke: 1 network sample, 1 separate warm-up and 3 cache samples passed.
- Evaluation 2: all 9 cache scenarios passed. Evaluation 3: online fetch, offline cache and recovery refresh passed.
- Evaluation 12 smoke: 4 selected segments completed and exported. This was not a new full-dataset accuracy run.
- Evaluation 7: the unsigned-in emulator correctly failed before sending requests and exported its authentication-failure JSON/CSV. Live AI success remains unverified until a test account is signed in.
- All six AI prompt fixtures matched the retained host runner, whose 15 offline tests passed. Device report CSV row counts matched JSON records. Source/documentation scanning found no remaining Chinese text.

Evaluation 13 and its pre-existing UI-test changes were left as they were at the start of this update.
