# Evaluation tests

Connect one device or emulator (API 26+) and run commands from the project root. JSON reports are saved to `evaluation_results/`.

## Weather latency

Requires internet access. Defaults to 20 network samples and 100 cache samples:

- Network: remove the evaluation entry before every sample and verify one provider call.
- Cache: perform a separate successful network warm-up, verify persistence, then verify zero
  provider calls for every cache sample. If warm-up fails, skip this group with a reason.
- Each group reports success rate (successful / recorded), failures, timeouts, skipped samples
  and nearest-rank P50/P95 of successful samples. No samples means null metrics, not zero latency.
- Timings include repository work, parsing, disk access and source validation. Removal, warm-up,
  report writing and pacing are outside the sample percentiles. Network connections may be reused.

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\run-weather-evaluation.ps1
```

Optional `-IntervalMs` (default 1000) paces network requests; `-TimeoutMs` (default 30000)
bounds each request. Cache samples run consecutively after warm-up. A run passes only when all
planned samples and warm-up succeed. Socket timeouts count as timeouts even when wrapped by the
repository; raw outcomes retain the distinction from coroutine timeouts.

## Weather cache integration

Runs nine deterministic cases on a device or emulator: fresh cache, refresh at 30 minutes,
stale offline fallback, fallback exactly at 6 hours, rejection after 6 hours, offline without
cache, recovery after failure, persistent readback and persistent removal.

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\run-weather-cache-evaluation.ps1
```

Uses the real repository and Android DataStore with an isolated evaluation file. A controlled
OpenMeteo provider returns fixed weather or throws IOException; a fixed clock tests exact expiry
boundaries without waiting. No internet or network switching is needed. Persistence closes and
reopens DataStore in the same process; it does not claim app-restart or real network recovery coverage.

Both new evaluations export Git commit and dirty state, device/Android API/build type, network
state at start/end, cases or samples, and structured failure types. `-NetworkConditions` adds a
manual label (letters, digits, dots, hyphens and underscores; no spaces). A dirty run is not fully
identified by its commit alone. Direct Android Studio runs record unknown Git metadata unless
instrumentation arguments are provided. Reports are exported even on test failure.

Latency and cache integration use disposable private caches and do not change the app's weather
cache. Run evaluations sequentially. Treat small samples and emulator debug-build timings as
descriptive evidence, not representative phone performance or a speed pass/fail threshold.

## Weather network recovery

Checks online success, offline cached weather, and refresh after restoring the network.
Emulator only; run separately from other network tests. This existing test uses the app cache
and toggles emulator networking; it retains its original report format.

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\run-weather-evaluation.ps1 -NetworkRecovery
```

## Carbon calculator

Checks transport modes, distance boundaries, proportional scaling, and savings consistency. Exports failed cases too; no internet required.

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\run-carbon-calculator-evaluation.ps1
```
