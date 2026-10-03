# Live AI proxy evaluation

Owner: Rui Fang. The live test now uses a local Firebase test account:

Host -> Firebase password sign-in -> Firebase ID token -> Cloudflare AI Worker -> Gemini.

The Gemini API key stays in the Worker's server-side Secret. This runner never reads
GEMINI_API_KEY, sends a password to the Worker, or installs/modifies the Android app.
All mission and weekly-coaching facts are synthetic.

## Local credentials and Git

Requires Node.js 22+ and the project's existing app/google-services.json.
That Firebase client configuration is already ignored by Git.

Copy the tracked blank template to the ignored local account file, then fill in the email
and password locally. Do not put credentials into a script, PR, report or command-line argument.

~~~powershell
Copy-Item .\ai-evaluation.local.json.example .\ai-evaluation.local.json
# Edit ai-evaluation.local.json locally.
git check-ignore -v ai-evaluation.local.json
~~~

The .gitignore rule /ai-evaluation.local.json excludes the account file from normal git add.
Only ai-evaluation.local.json.example (empty placeholders) is intended for GitHub.
Never force-add the private file. This run's account is already configured locally.

Alternatively, set ECOSTEP_TEST_EMAIL and ECOSTEP_TEST_PASSWORD in the local process
environment. Environment values override the account file. The Firebase client API key is
read from app/google-services.json, or ECOSTEP_FIREBASE_API_KEY if that config is unavailable.
These are Firebase login settings, not a personal Gemini API key.

The runner prints only login status and timings. Its report omits email, password,
Firebase API key, user ID, ID/refresh tokens, Authorization headers, arbitrary server fields
and raw error bodies. Before each file write it blocks an export containing the actual
email/password/client key/ID token, including their JSON-escaped representations.

## Run

~~~powershell
# One authenticated AI request.
powershell -ExecutionPolicy Bypass -File .\scripts\run-gemini-live-evaluation.ps1 -Smoke

# Six cases x three samples: 18 real AI requests, no retries.
powershell -ExecutionPolicy Bypass -File .\scripts\run-gemini-live-evaluation.ps1 -SamplesPerCase 3 -NetworkConditions "host Wi-Fi"

# One selected scenario.
node .\scripts\run-gemini-live-evaluation.mjs --case weekly_completed --samples-per-case 3

# Offline checks: no login, no network calls.
node --test .\scripts\gemini-live-evaluation.test.mjs
~~~

The default AI Worker URL comes from AiWorkerClient.kt, matching the app.
It is the AI proxy, not the route proxy. Override with -ProxyUrl / --proxy-url only when
using another trusted project AI Worker. HTTPS Worker base URLs must have no embedded
credentials, query parameters or endpoint path. Model selection is controlled by the Worker;
the former -Model / --model options are removed.

-SamplesPerCase is 1–20 (default 3), -IntervalMs is 0–60000 (default 1000), and
-TimeoutMs is 1000–120000 (default 35000). The cases are:

- mission_walking_only
- mission_walking_or_cycling
- mission_public_transport_only
- mission_rain_with_transit
- weekly_completed
- weekly_none_completed

Weekly scenarios have at least one accepted mission because the app skips AI when none
were accepted. Host prompt fixtures mirror DefaultMissionPromptBuilder and DefaultWeeklyCoach;
the runner does not execute the Kotlin builders. The Worker supplies the system instruction.
Review fixtures and local validation when production rules change.

## Reports and metric definitions

New results are saved as evaluation_results/ai-proxy-live-evaluation-<timestamp>.json,
with schemaVersion 2 and transport firebase-ai-proxy. Code, the blank template and these
credential-free reports can be committed. The earlier direct-Gemini result has been removed;
this update retains the authenticated proxy evaluation result.

One Firebase sign-in is timed separately and excluded from AI request P50/P95.
A login failure saves an authentication-only report and marks all AI requests skipped.
Requests are sequential; no warm-ups, retries or fallback are used. A 400/401/403/404/429
response stops the run, with unattempted requests recorded as skipped. The test uses one
login token per run; an expired/rejected token ends the run rather than silently reauthenticating.
The default 18-request run consumes the project's Gemini quota.

| Metric | Numerator / denominator |
|---|---|
| Completion rate | Attempted / planned AI requests |
| HTTP success rate | HTTP 2xx / attempted requests |
| Proxy JSON parse rate | Parsed Worker response bodies / nonempty received bodies, including error envelopes |
| App schema rate | Correct required field types and transport enum / valid task-and-result envelopes |
| Business validation rate | Local lengths, confidence and allowed mode checks / schema-valid replies |
| App usable rate | Successful replies passing all local checks / attempted requests |

Counts, explicit denominators and fractions are reported overall and by mission/weekly task.
Missing denominators are null. A JSON-formatted error can count as parseable proxy JSON
while still counting as an unusable reply. Client timeouts are distinct from
UPSTREAM_TIMEOUT responses returned by the Worker; INVALID_AI_RESPONSE responses are
also counted separately. Unknown server error codes are replaced by UNKNOWN_ERROR.

App-compatible checks mirror AiMissionSuggestion / AiWeeklyAdvice field types and ignore
unknown fields, which are never persisted. Business checks follow the listed candidate modes,
confidence 0–100 and trimmed explanation/title/message limits of 1000/80/240 characters;
weekly insight/action limits are 1000 each.

P50/P95 use nearest rank on individual requests, separately for all attempts, HTTP successes
and usable replies. Timings include request preparation, host network, Worker authentication
and parsing, Gemini generation and receipt of the complete response body. Local response
validation, pacing and report writes are excluded. This is host-to-proxy elapsed latency,
not Android UI latency, provider-only inference time or time-to-first-token.
Connections may be reused; small-sample percentiles are descriptive. Nonzero exit status
means the requested run did not complete with every reply usable; there is no latency threshold.

## What the proxy cannot reveal

The Worker parses and validates Gemini output before returning {task,result}.
It does not expose original model text, finish reasons, actual model version or token usage.
Therefore geminiRawJsonParseRate and modelVersion are null; the test cannot claim
Gemini's original JSON parse rate from proxy replies. INVALID_AI_RESPONSE can indicate
malformed/incomplete generation or invalid fields; it cannot identify the exact raw failure.

This test covers Firebase login and the real authenticated AI service contract, using host
fixtures and host validation. It does not execute Android/Kotlin decoding, test UI integration,
generator retry/fallback, factual correctness, hallucinations or recommendation quality.
Production Android code and the Worker deployment are unchanged.
