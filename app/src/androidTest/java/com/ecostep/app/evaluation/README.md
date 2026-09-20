# Run the test and save results to your computer

Start one Android emulator with internet access (API 26+), or connect one phone with USB debugging enabled.
Open PowerShell in the project root and run:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\run-weather-evaluation.ps1
```

The script installs the app, performs 100 weather requests, and automatically saves the JSON report under the project root:

```text
evaluation_results/weather-latency-<timestamp>.json
```

If your Android SDK is not in the default location, specify the path to `adb.exe`:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\run-weather-evaluation.ps1 -AdbPath 'D:\Android\Sdk\platform-tools\adb.exe'
```

## Offline and recovery

Start one emulator with internet access, then run:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\run-weather-evaluation.ps1 -NetworkRecovery
```

This emulator-only test uses the same real weather repository for three requests: online
success, expected network failure with Wi-Fi/mobile data disabled, and success after restoring
the original network switches. Do not run other network tests at the same time.

Results are exported to `evaluation_results/weather-recovery-<timestamp>.json`. The offline
record should have outcome `FAILURE`; the overall `passed` field should be `true`.

The test restores network switches in `finally`. If the process is forcibly stopped while
offline, re-enable networking in emulator settings or run (using your SDK's adb path):

```powershell
adb shell svc wifi enable
adb shell svc data enable
```
