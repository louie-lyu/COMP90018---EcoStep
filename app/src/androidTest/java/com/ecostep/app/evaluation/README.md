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
  Schema 6 reports contain `summary`, `perMode` (true-label recall/precision/F1), `perPrediction`
  (predicted-label share/error rate), `confusionMatrix`, per-sample records and planner diagnostics.
  `successfulOnlineSummary` covers requests that succeeded and completed classification; an HTTP
  success can still return no transit candidates. Check `withTransitCandidates`, `plannerStatusCounts`
  and `timeouts` separately. Reports include dataset SHA-256, Git/device metadata and current-time policy.
  Intervals that cannot produce a recording are excluded before classification and are outside all
  accuracy denominators. `dataset.excludedRecordingSegments` and its per-mode counts document
  these exclusions; `evaluatedSegmentsPerMode` gives the retained sample counts. The source ZIP
  remains unchanged. With the current preview dataset, the default run retains 138 of 149 intervals
  (64 walking, 13 cycling, 45 public transport, 16 car), excluding 11 unavailable recordings.
  `SamplesPerMode` caps intervals selected before this filter; it does not split or duplicate trips.
  Unexpected classifier exceptions retain UNKNOWN but fail the test. Expected network failures and
  timeouts are measured outcomes; passing is not an accuracy threshold or proof of planner success.
  Ground-truth labels split single-mode intervals; automatic segmentation and mixed walking/transit
  trips are outside scope. Live Google recognition, Firebase saving, UI and user corrections
  are not evaluated. Latency includes network/classification, excludes replay and request spacing.
  Motion: when the archive contains `Hand_Motion.txt`, its accelerometer and gyroscope columns are
  streamed into the production JourneyTracker at about 50 Hz (one row per 20 ms, like
  SENSOR_DELAY_GAME); otherwise motion features stay empty. `dataset.motionReplay` reports files
  found/missing and rows replayed. Each record includes its `sensorFeatures` for offline threshold
  calibration. `sensorFallbackSummary` covers samples without an Activity hint of confidence 60 or
  more, the only ones the motion rule can change. Passing `-MotionSteadyAccelStd` and
  `-MotionMinAccelSamples` adds `motionVariant` (summary, perMode, confusion matrix, fallback subset):
  the same samples and transit evidence classified with that accelerometer rule, without extra requests.
  The main results always use `productionMotionThresholds` (`CALIBRATED_MOTION_THRESHOLDS`).
  For threshold selection, add `-CalibrateMotion`. This adds a separate `sensorCalibration`
  experiment with Activity hints masked for every sample, an explicit speed-only baseline
  (motion thresholds null), and a grid of accelerometer thresholds. The main production results
  and recorded Activity hints remain available. All candidates reuse the same transit evidence;
  no additional network requests are sent. Calibration computation is excluded from `durationMs`.
  Selection maximizes overall accuracy across the same evaluated dataset, without a training or
  validation split. Timeouts remain UNKNOWN for every candidate and stay in the denominator.
  Ties prefer the speed-only baseline, then lower standard deviation, then higher minimum sample
  count. `tiedBestCandidateIds` lists all tied candidates. Incomplete runs, runtime failures or runs
  with no completed calibration predictions do not select a winner. No production threshold is
  changed automatically. A winning speed-only baseline has `bestThresholds: null`.
  Defaults are standard deviations `0.2,0.4,0.6,0.8,1.0,1.5,2.0,3.0` m/s² crossed with
  sample counts `250,500,1000` (24 candidates plus baseline); these are search candidates, not
  calibrated values. Override them with `-MotionStdCandidates` and `-MotionSampleCandidates`
  (PowerShell arrays, at most 200 combinations). Each record's `sensorCalibrationPredictions`
  follows the report's `sensorCalibration.candidates` order. Candidate summaries include per-mode
  metrics and confusion matrices, but only overall accuracy determines the winner.
  Calibration requires matching `Hand_Motion.txt` files and nonzero accelerometer samples for
  every selected recording. The old GPS/API-only preview archive cannot calibrate motion.
  To extend that archive, place the official `SHLDataset_preview_v1_part1.zip`, `part2.zip`
  and `part3.zip` beside it and run `python scripts/prepare-shl-motion-dataset.py --segments-report <existing-full-evaluation.json>`.
  The report's dataset SHA-256 must match the original ZIP. Its original recording IDs and
  [start,end) boundaries determine which motion samples are retained; no new journeys are added.
  This backs up the old ZIP as `SHL-preview-evaluation.gps-only.zip`, retains all existing
  data entries, adds matching Hand motion files with only timestamp, accelerometer XYZ and
  gyroscope XYZ within those existing recordings, and writes DEFLATE.
  Motion timestamps such as `1498120298000.000000` are normalized to exact integer milliseconds
  for the existing reader. The six sensor values keep their original precision and 100 Hz
  sampling inside selected intervals, including NaN. Other columns and rows outside the existing
  recordings are omitted. Non-integral
  timestamps are rejected rather than rounded. The old manifest is retained as
  `manifest.gps-only.json`; the updated manifest records source/output hashes and normalization.
  Every output entry is SHA-256/CRC verified before replacing the default ZIP.
  `ShlMotionArchiveCompatibilityTest` is an opt-in offline device check (`verifyShlMotionArchive=true`)
  using `shl-preview.zip` and `shl-preview-gps-only.zip` in the device evaluation directory.
  It replays all selected recordings, checks that non-motion fields match, and checks that both
  motion sensors feed the calibration path. It does not measure online classification accuracy.
  An optional `shl-motion-full-reference.json` from a prior full-motion compatibility run also
  checks that the compact ZIP produces exactly the same motion counts, means and standard deviations.
  Example from PowerShell (replace the ZIP path with a motion-inclusive archive):

  ```powershell
  .\scripts\run-activity-hint-transport-evaluation.ps1 `
      -DatasetPath 'D:\datasets\SHL-preview-with-motion.zip' `
      -CalibrateMotion -MotionStdCandidates @(0.2, 0.4, 0.8, 1.2, 2.0) `
      -MotionSampleCandidates @(250, 500, 1000)
  ```

  Code is split into `ActivityHintTransportEvaluationTest` (online flow), `ShlEvaluationDataset`
  (sampling/replay), `MotionThresholdCalibration` (forced-sensor sweep), and
  `TransportEvaluationMetrics` (report calculations).
- P50/P95 use successful samples only (nearest rank); unavailable metrics are `null`.
- EcoPoints, AI missions, weekly coach, mission triggers and local flow report batch time and average time per call; their percentiles describe batch averages.
- Timing excludes fixture creation, warm-up, validation and report writing. Weather source checks,
  loop overhead and carbon-to-points object creation are included where applicable.
- Weather latency/cache use isolated caches. Persistence reopens DataStore in the same process.
- Weather latency/cache, EcoPoints, AI missions, weekly coach, mission triggers, local flow and recurring reports include Git and device metadata.
  Android Studio runs need arguments for Git metadata; dirty runs need the corresponding source.
- Synthetic checks and debug emulator timings do not establish real-user accuracy or phone performance.
  Carbon factors are provisional; performance has no pass/fail threshold. JIT/GC and group order affect timings.

## User-visible journey latency (real UI and Firebase)

Run with exactly one unlocked emulator or physical Android device. Sign in to a dedicated
Firebase test account first, and end any active mission/recording. This test creates and
confirms real journeys which remain in that account: client security rules prohibit deletion.
It does not read credentials or change the signed-in account.

```powershell
# Three-journey smoke run.
powershell -ExecutionPolicy Bypass -File .\scripts\run-journey-ui-latency-evaluation.ps1 -TestAccount -Samples 3 -NetworkConditions emulator-online

# Default: 20 journeys, each measuring both operations.
powershell -ExecutionPolicy Bypass -File .\scripts\run-journey-ui-latency-evaluation.ps1 -TestAccount -NetworkConditions phone-wifi
```

`-NetworkConditions` is a label, not a network switch (letters, digits, dot, underscore and
hyphen only). `-AdbPath` selects an alternative adb executable; `-TimeoutMs` defaults to 30000
per UI wait. Do not interact with the device during the run. Instrumentation progress is
printed after each completed journey. JSON and CSV are exported to `evaluation_results/`,
including partial failure reports. General instrumentation runs skip this opt-in live test.

`JourneyUiLatencyEvaluationTest` launches the real MainActivity and production navigation,
TrackingViewModel, classifier, JourneyReviewViewModel and Firestore repositories. Before each
End click it instantly replays 13 fixed GPS points with timestamps covering the preceding
60 seconds and a WALKING Activity hint. No real walking or 60-second sleep is necessary.
Sensor acquisition, classifier accuracy on real trips and app startup are outside scope.
The walking fixture is unlinked to missions and has no lower-carbon alternative; task/AI
recommendation generation and reward delivery are not measured.

- `stop_to_review`: injected End touch to the new journey-specific Review layout and visible
  "Journey complete" heading. This covers classification, initial persistence, navigation
  and accessibility-observed loaded content. It does not wait for place-name resolution or
  a confirm button below the fold. The mode selector is exercised separately before confirming.
- `confirm_to_feedback`: injected Confirm touch to the visible "Journey saved!" dialog and
  its Back to Map control. The same journey's confirmed mode is then checked in the real
  Firestore local cache outside timing. `pendingSyncAtVerification` distinguishes pending
  local writes; feedback does not prove server acknowledgement or verified points.

The test queries Android's accessibility tree and injects real touches; there is no Compose
virtual animation clock. Production controls have test tags (see Android's official
[Compose interoperability documentation](https://developer.android.com/develop/ui/compose/testing/interoperability)).
The device remains subject to normal scheduling and animation. Polling is every 50 ms, so
results include input dispatch, accessibility propagation and observation overhead, not
frame-exact rendering timestamps. Scrolling/button-position stabilization happens before
starting confirmation timing. Fixture preparation, scrolling, verification, CSV/JSON writes
and a two-second inter-journey pause are excluded.

Each scenario reports planned/attempted/successful/failed/timed-out/skipped counts, success
rate among attempts, and nearest-rank P50/P95/max of successful individual operations.
All numbered samples are retained, including the first operation after activity launch;
there are no hidden warm-ups or automatic retries. A UI observation timeout is different
from an internal network timeout that production code handles via fallback. On failure,
the run stops and remaining samples are marked skipped. A passed run requires both UI
operations and local confirmation verification for every requested journey; there is no
latency pass threshold. Few-sample percentiles are descriptive. Compare emulator and phone,
network conditions, first-use and subsequent operations separately.
