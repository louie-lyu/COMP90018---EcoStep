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
