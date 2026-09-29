# Evaluation tests

Connect one device or emulator (API 26+) and run commands from the project root. JSON reports are saved to `evaluation_results/`.

## Weather latency

Measures 100 weather requests, including success rate and P50/P95 latency. Requires internet access.

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\run-weather-evaluation.ps1
```

## Weather network recovery

Checks online success, expected offline failure, and success after restoring the network. Emulator only; run separately from other network tests.

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\run-weather-evaluation.ps1 -NetworkRecovery
```

## Carbon calculator

Checks transport modes, distance boundaries, proportional scaling, and savings consistency. Exports failed cases too; no internet required.

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\run-carbon-calculator-evaluation.ps1
```
