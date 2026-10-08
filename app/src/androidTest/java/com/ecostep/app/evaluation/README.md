# Evaluation tests

Start one emulator (API 26+) and run from the project root. Run evaluations sequentially.
Scripts build/install the app and export JSON to `evaluation_results/`, including failed results.

## Run

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\run-weather-evaluation.ps1
powershell -ExecutionPolicy Bypass -File .\scripts\run-weather-cache-evaluation.ps1
powershell -ExecutionPolicy Bypass -File .\scripts\run-weather-evaluation.ps1 -NetworkRecovery
powershell -ExecutionPolicy Bypass -File .\scripts\run-carbon-calculator-evaluation.ps1
powershell -ExecutionPolicy Bypass -File .\scripts\run-ecopoints-evaluation.ps1
powershell -ExecutionPolicy Bypass -File .\scripts\run-ai-mission-evaluation.ps1
powershell -ExecutionPolicy Bypass -File .\scripts\run-weekly-coach-evaluation.ps1
powershell -ExecutionPolicy Bypass -File .\scripts\run-mission-trigger-evaluation.ps1
powershell -ExecutionPolicy Bypass -File .\scripts\run-local-flow-evaluation.ps1
powershell -ExecutionPolicy Bypass -File .\scripts\run-recurring-journey-evaluation.ps1
powershell -ExecutionPolicy Bypass -File .\scripts\run-activity-hint-transport-evaluation.ps1
```

## Coverage

| Evaluation | Checks | Defaults |
|---|---|---|
| Weather latency | Live requests and verified cache hits; requires internet | 20 network + 100 cache samples |
| Weather cache | 30-minute/6-hour boundaries, offline fallback, recovery, persistent read/removal | 9 controlled cases; no internet |
| Network recovery | Online → offline cache → restored network refresh; changes emulator networking and app cache | 3 stages |
| Carbon | Modes, distance boundaries, scaling and savings consistency | 56 cases |
| EcoPoints | Bonuses, task states, rounding, cap, invalid inputs, consistency and carbon-to-points integration | 110 checks; 8 timing groups |
| AI missions | Controlled replies, validation, retries, fallback, cancellation and prompt privacy | 34 cases; 2 local timing groups; no internet/login |
| Weekly coach | Weekly totals, date boundaries, mission states, fallback thresholds and prompt privacy | 19 cases; 6 timing groups; no internet/login |
| Mission triggers | Lead time, weekday/month/year boundaries, UTC/offset zones and DST | 29 cases; 5 timing groups; no internet/login |
| Local flow | Carbon → fallback/validation → completion → points → weekly totals; corrected modes and rejected inputs | 25 cases; 4 timing groups; no internet/login |
| Recurring journeys | Counts, location/time boundaries, reverse routes, midnight, duplicate IDs and output fields | 41 cases; 8 timing groups |
| Transport classification (SHL) | Live Transitous plans + SHL GPS/recorded Activity; accuracy, planner diagnostics and end-to-end latency | Up to 10000 intervals per mode; seed 42; 5-second timeout; 2-second request spacing |

## Parameters and results

- Weather: `-IntervalMs 1000`, `-TimeoutMs 30000`; `-NetworkConditions` labels the network.
- EcoPoints: `-Warmups 10 -Samples 50 -BatchSize 1000`.
- AI missions: `-Warmups 10 -Samples 50 -BatchSize 100`; timings cover the local validator and fallback.
- AI replies/errors are controlled fixtures; Gemini latency, parse rate and text quality are not measured.
  For the separate opt-in **authenticated AI proxy host test** (proxy reply parse rate, field/business validation,
  latency and saved JSON reports), see [AI_LIVE_EVALUATION.md](../../../../../../../../docs/AI_LIVE_EVALUATION.md).
  That test uses synthetic prompts and an ignored local Firebase test account. It measures host-to-proxy latency;
  original Gemini JSON parsing, Android latency and text quality are not measured.
- Weekly coach: `-Warmups 10 -Samples 50 -BatchSize 10`; histories of 0–10,000 entries, all or 10% in the week.
- Mission triggers: same batch parameters; plans timestamps only, not Android notification delivery. DST gaps shift forward; overlaps use the earlier offset.
- Local flow: same batch parameters; real local algorithms with synthetic candidate availability and completion events. One timed call processes one or three journeys, including object construction; no UI, sensors or storage.
- Recurring: `-Warmups 10 -Samples 50`; histories of 10–10,000 entries, all or 10% matching.
- Transport classification: uses the pooled User1/User2/User3 SHL Hand recordings (nine days).
  Keep `SHL-preview-evaluation.zip` in ignored `data/`, or supply `-DatasetPath`.
  Run `scripts/run-activity-hint-transport-evaluation.ps1 -SamplesPerMode 10000 -Seed 42`
  with one connected Android device and internet access. A small smoke run uses `-SamplesPerMode 1`.
  The runner enables `live=true`; a general Android test run skips this network evaluation.
  Each valid recording sends its endpoints to [Transitous](https://transitous.org/api/), with
  [source attribution](https://transitous.org/sources/). No full track, Activity series or true labels
  are sent. Requests are sequential with a 2-second pause; no online warm-ups or extra retries.
  HTTP 403/429 stops remaining requests and exports an incomplete report.
  The planner `time` parameter is omitted, so the service uses its current timetable. SHL's original
  timestamps remain in GPS/Activity replay. This is historical GPS evaluated against present-day
  route plans; it is not a reconstruction of 2017 services. Results can change with timetables/network.
  The production JourneyTracker, summary builder, shared HTTP client, evidence provider, stop mapper
  and classifier are reused. The 5-second timeout and UNKNOWN fallback match TrackingViewModel.
  Only the planner query time differs. No offline transit files or local candidate filtering remain.
  Schema 3 reports contain `summary`, `perMode` (true-label recall/precision/F1), `perPrediction`
  (predicted-label share/error rate), `confusionMatrix`, per-sample records and planner diagnostics.
  `successfulOnlineSummary` covers requests that succeeded and completed classification; an HTTP
  success can still return no transit candidates. Check `withTransitCandidates`, `plannerStatusCounts`
  and `timeouts` separately. Reports include dataset SHA-256, Git/device metadata and current-time policy.
  Missing recordings count as UNKNOWN in total accuracy; the App would not save these recordings.
  Unexpected classifier exceptions retain UNKNOWN but fail the test. Expected network failures and
  timeouts are measured outcomes; passing is not an accuracy threshold or proof of planner success.
  Ground-truth labels split single-mode intervals; automatic segmentation and mixed walking/transit
  trips are outside scope. Live Google recognition, IMU, Firebase saving, UI and user corrections
  are not evaluated. Latency includes network/classification, excludes replay and request spacing.
  Code is split into `ActivityHintTransportEvaluationTest` (online flow), `ShlEvaluationDataset`
  (sampling/replay), and `TransportEvaluationMetrics` (report calculations).
- P50/P95 use successful samples only (nearest rank); unavailable metrics are `null`.
- EcoPoints, AI missions, weekly coach, mission triggers and local flow report batch time and average time per call; their percentiles describe batch averages.
- Timing excludes fixture creation, warm-up, validation and report writing. Weather source checks,
  loop overhead and carbon-to-points object creation are included where applicable.
- Weather latency/cache use isolated caches. Persistence reopens DataStore in the same process.
- Weather latency/cache, EcoPoints, AI missions, weekly coach, mission triggers, local flow and recurring reports include Git and device metadata.
  Android Studio runs need arguments for Git metadata; dirty runs need the corresponding source.
- Synthetic checks and debug emulator timings do not establish real-user accuracy or phone performance.
  Carbon factors are provisional; performance has no pass/fail threshold. JIT/GC and group order affect timings.
