# Live Gemini evaluation

Owner: Rui Fang. This opt-in host test complements the controlled Android AI evaluation.
It sends real requests to Google Gemini using **synthetic facts**, and measures raw model
JSON parsing, app-compatible field types, selected business rules and individual request latency.
It does not install or modify the Android app, create an account, or use fallback replies.

## Run

Requires Node.js 22+ and an existing `GEMINI_API_KEY` environment variable. The key is read
in memory and sent only in Google's `x-goog-api-key` header. Do not put it in source,
command-line arguments, screenshots or reports. A developer key belongs only on the local
test host; the Android app continues to use the authenticated Cloudflare proxy.

```powershell
# One real request to confirm access first.
powershell -ExecutionPolicy Bypass -File .\scripts\run-gemini-live-evaluation.ps1 -Smoke

# Six cases, three samples per case: 18 billable requests, sequential, no retries.
powershell -ExecutionPolicy Bypass -File .\scripts\run-gemini-live-evaluation.ps1 -SamplesPerCase 3 -NetworkConditions "host Wi-Fi"

# Node entry point is also usable directly.
node .\scripts\run-gemini-live-evaluation.mjs --case weekly_completed --samples-per-case 3

# Offline tests for measurement, validation and error handling; no API calls.
node --test .\scripts\gemini-live-evaluation.test.mjs
```

The default model is read from `DEFAULT_MODEL` in `cloud/ai-proxy/worker.js`.
Override with `-Model` / `--model` or `GEMINI_MODEL` to match an actual deployment override.
The report records the requested model and the returned model version; a direct run cannot
discover the Cloudflare deployment's secret model setting.

`-SamplesPerCase` is 1–20 (default 3); `-IntervalMs` is 0–60000 (default 1000);
`-TimeoutMs` is 1000–120000 (default 35000). `-Case` selects one of:

- `mission_walking_only`
- `mission_walking_or_cycling`
- `mission_public_transport_only`
- `mission_rain_with_transit`
- `weekly_completed`
- `weekly_none_completed`

The weekly cases both have accepted missions: the production coach bypasses AI when none
were accepted. Fixture prompt layouts mirror `DefaultMissionPromptBuilder` and
`DefaultWeeklyCoach`, and the system instruction mirrors the Worker. The script does not
execute the Kotlin builders. Review these fixtures and validators when production prompts,
models or rules change. Full synthetic prompts and the script SHA-256 are stored for comparison.

## Metrics and denominators

JSON reports are saved under `evaluation_results/gemini-live-evaluation-<timestamp>.json`.
Results are checkpointed after every request, including failed requests. HTTP 400/401/403/404/429
stop the run immediately; remaining requests are marked skipped in the summary. There are
no automatic retries or warm-ups, so their costs and latency cannot hide first-attempt failures.
A nonzero exit code means the requested run did not finish with every reply usable, or it could
not start. There is no latency pass/fail threshold.

| Metric | Numerator / denominator |
|---|---|
| Completion rate | Attempted / planned requests |
| HTTP success rate | HTTP 2xx / attempted requests |
| JSON parse rate | Raw model texts parsed as JSON / replies containing model text |
| App schema rate | Correct required types and transport enum / parsed JSON replies |
| Business validation rate | Local lengths, confidence and allowed mode checks / schema-valid replies |
| App usable rate | Schema + business checks + `STOP` finish reason / attempted requests |

Rates store numerator and denominator alongside the fraction; absent denominators are `null`.
Network, quota and blocked responses are retained but are not called JSON parsing failures.
Counts and rates are reported overall and separately for mission and weekly calls.

App-compatible schema checks require the fields in `AiMissionSuggestion` / `AiWeeklyAdvice`,
and ignore extra keys like the app's `ignoreUnknownKeys` decoder. Business checks mirror
`DefaultAiMissionGenerator` (listed mode; confidence 0–100; trimmed explanation/title/message
limits 1000/80/240) and `DefaultAiWeeklyCoach` (trimmed insight/action limits 1000 each).
They are host checks, not a Kotlin deserialization test or a test of the full generator's retry logic.
They do not evaluate truthfulness, hallucinations, appropriateness or coaching quality.

P50/P95 use nearest rank on **individual requests**, separately for all attempts, HTTP successes
and usable replies. Elapsed latency runs from `fetch` start through receipt of the complete
response body, including host DNS/TLS/network and Gemini generation. Pacing, validation and
file writing are excluded; connections may be reused. These are neither phone/App latency,
provider-only inference time, nor time-to-first-token. Small samples make P95 descriptive only.
The report also retains finish reasons, model versions, token usage and raw synthetic replies.

## Proxy scope

The production Worker parses and validates Gemini output before returning `{task,result}`.
Consequently, measuring raw Gemini parse rate **only from successful proxy replies** would
hide model failures. This separate direct test deliberately observes the original model text.
It does not establish Firebase login, Worker validation, Android decoding, task integration or
end-to-end production latency. Those should be tested through the authenticated app separately.
No production code or proxy deployment is changed by this evaluation.
