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
| Transport classification (SHL) | Offline real transit stop sequences with/without recorded Google Activity hints, plus no-transit controls; accuracy, confusion matrices and classifier latency | Up to 10000 intervals per mode; seed 42; 10 warm-ups per mode per variant |

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
- Transport classification: place `SHL-preview-evaluation.zip` in the ignored `data/` directory
  beside this README, or supply `-DatasetPath`. The merged local archive includes User1, User2 and
  User3 (nine days); results are pooled while original paths preserve journey boundaries.
  Run with `-SamplesPerMode 10000 -Warmups 10 -Seed 42`; exactly one Android device is required.
  Offline transit data is required at `data/transit/transit-routes.json`, or use `-TransitRoutesPath`.
  Prepare it from the repository root using Python 3.10+ (standard library only):
  ```powershell
  python ./scripts/download-shl-transit-gtfs.py
  python ./scripts/prepare-shl-transit-routes.py
  ```
  Existing local snapshots can be reused without downloading again. Public source:
  [Aubin Great Britain GTFS](https://beta.aubin.app/gtfs/great_britain_gtfs.zip), as listed by
  [Transitous](https://transitous.org/sources-great-britain/). Compressed source tables, provenance
  and extracted routes stay in ignored `data/transit/`. Preparation verifies source CRCs and
  preserves GTFS stop order; patterns are deduplicated by route ID and ordered stop IDs.
  Region selection uses GPS bounds plus a fixed margin, never transport labels.
  Each sample selects ordered origin/destination stop pairs within 1000 m of its endpoints,
  ranks by the minimum summed endpoint distance, and keeps at most 50 sequences. The same rule
  applies to every mode and both Activity variants; no labels, Activity predictions or interior
  stop matches are used for candidate selection. These are spatial candidates, not planned trips.
  All four variants use the same intervals and the unchanged production classifier/evidence mapper.
  Schema 2 reports make `summary`, `records`, `perMode`, `confusionMatrix` the **Activity + transit**
  results; `gpsOnly*` is **GPS + transit**. `baseline*` and `baselineGpsOnly*` contain the respective
  **no-transit** controls. Reports include route/SHL hashes, source metadata, candidate IDs/counts
  and Git/device metadata. Passing means no runtime errors, not an accuracy threshold.
  The local GTFS snapshot is from 2026, while SHL is from 2017. No service calendar, timetable,
  walking access, transfers or historical route compatibility are validated. The test is offline
  and does not call the live journey planner. Ground-truth labels split single-mode intervals;
  mixed walking/transit journeys and automatic boundary detection are not evaluated.
  Google Activity results are recorded data; live recognition, IMU, UI, Firebase and user
  corrections are outside scope. Missing recordings remain UNKNOWN in the accuracy denominator.
  Classification timings include station matching but exclude candidate selection and replay.
- P50/P95 use successful samples only (nearest rank); unavailable metrics are `null`.
- EcoPoints, AI missions, weekly coach, mission triggers and local flow report batch time and average time per call; their percentiles describe batch averages.
- Timing excludes fixture creation, warm-up, validation and report writing. Weather source checks,
  loop overhead and carbon-to-points object creation are included where applicable.
- Weather latency/cache use isolated caches. Persistence reopens DataStore in the same process.
- Weather latency/cache, EcoPoints, AI missions, weekly coach, mission triggers, local flow and recurring reports include Git and device metadata.
  Android Studio runs need arguments for Git metadata; dirty runs need the corresponding source.
- Synthetic checks and debug emulator timings do not establish real-user accuracy or phone performance.
  Carbon factors are provisional; performance has no pass/fail threshold. JIT/GC and group order affect timings.
