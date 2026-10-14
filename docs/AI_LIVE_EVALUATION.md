# Live AI proxy evaluation

Run the authenticated Android device evaluation from Android Studio. The implementation is
`app/src/androidTest/java/com/ecostep/app/evaluation/AiProxyDeviceTest.kt`.

## Run

1. Sync Gradle and connect one emulator or Android device.
2. Run the normal debug app and sign in to a dedicated Firebase test account, then stop the app.
3. Select **Evaluation - 7 AI proxy smoke** for one request, or **Evaluation - 7 AI proxy** for
   all six fixtures with three samples each (18 requests). Click Run.
4. Open the directory printed as `Evaluation reports:` in the Run console.

Keep the same debug application ID and signing key, and keep the signed-in app's data intact.
The evaluation retrieves the existing Firebase session's ID token. It does not read a local
email/password file. Credentials, tokens and raw response bodies are not included in reports.
The Gemini API key stays in the Worker's server-side secret.

## Cases and parameters

Synthetic fixtures are packaged in `app/src/androidTest/assets/evaluation/ai-proxy-cases.json`:

- `mission_walking_only`
- `mission_walking_or_cycling`
- `mission_public_transport_only`
- `mission_rain_with_transit`
- `weekly_completed`
- `weekly_none_completed`

Duplicate a configuration and change `-Pandroid.testInstrumentationRunnerArguments.<name>=<value>`:
`samplesPerCase` (1-20, default 3), `caseName` (optional fixture filter), `intervalMs` (0-60000,
default 1000) or `timeoutMs` (1000-120000, default 35000). The endpoint comes from `AiWorkerClient`.
Requests are sequential, with no retry or warm-up. HTTP 400/401/403/404/429 stops the remaining
requests. A full run uses the project's Gemini quota.

## Reports and interpretation

JSON and CSV are exported to `evaluation_results/ai-proxy/<timestamp>/`, including
partial reports after an instrumentation failure. Token retrieval is timed separately. Request
latency runs from Android request preparation through receipt of the response body; validation,
pacing and reporting are excluded.

Reports contain attempted/skipped requests, HTTP success, proxy JSON parsing, envelope/schema
validity, business validation, accepted replies and P50/P95 latency. Rates have explicit
numerators and denominators; an absent denominator produces null. Counts and latency summaries
are available overall and by task. Every requested reply must be accepted for a passing run.

The Worker parses Gemini output before returning `{task,result}`. Raw Gemini JSON parsing,
actual model version, semantic quality and hallucinations are not measured. `geminiRawJsonParseRate`
and `modelVersion` remain null. This evaluation does not measure UI latency or local generator
retry/fallback; those local algorithms have separate correctness tests and Microbenchmarks.

See the [evaluation guide](../app/src/androidTest/java/com/ecostep/app/evaluation/README.md)
for all presets, prerequisites, test scope and report locations.
