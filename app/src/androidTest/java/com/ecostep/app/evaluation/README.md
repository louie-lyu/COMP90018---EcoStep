# Evaluations

## Algorithm correctness

| Evaluation | What it tests |
|---|---|
| Carbon emissions | Emissions across transport modes and distances, savings from alternatives, and zero, invalid, and large input values. |
| EcoPoints | Points across mission states, transport modes, and carbon savings, including rounding, caps, missing data, and consistent results. |
| AI mission generation | Valid and invalid AI responses, retries, local fallback, and privacy protection in prompts. |
| Weekly Coach | Accepted and completed missions, completion rate, total carbon savings, and common transport modes, including empty data, week boundaries, and invalid records. |
| Mission reminders | Next reminder times across departure times, active weekdays, time zones, and daylight saving changes. |
| Local workflow | Data consistency across journeys, emissions, fallback missions, completion results, points, and weekly summaries. |
| Recurring journeys | Route detection from endpoints, departure times, and history, including occurrence counts, typical times, and common transport modes. |

## Algorithm performance

| Evaluation | What it measures |
|---|---|
| EcoPoints | Execution time and memory allocation across point calculation branches. |
| Local AI mission logic | Execution time for mission validation and local fallback generation, excluding cloud AI requests. |
| Weekly Coach | Summary calculation time across history sizes and proportions of records from the current week. |
| Mission reminders | Reminder calculation time for common cases and time boundaries. |
| Local workflow | Time from emissions calculation to weekly summaries for individual and mixed journeys. |
| Recurring journeys | Detection time across history sizes and route match rates. |

## Device and live API evaluations

| Evaluation | What it tests |
|---|---|
| Weather latency | Duration, successes, and failures of live weather requests and local cache reads. |
| Weather cache | Freshness, expiry and refresh, offline fallback, missing cache, request recovery, and reading and deleting entries after reopening storage. |
| Weather network recovery | Live retrieval, cached reads with emulator networking disabled, and refresh after restoring the connection. |
| AI Proxy | Duration, HTTP results, response structure, fields, and business validity of live mission suggestions and weekly insights. |
| SHL transport classification | Accuracy, precision, recall, F1, and confusion matrices for walking, cycling, cars, and public transport using recorded GPS, activity, and motion sensor data. |
| SHL motion parameter comparison and sweep | Current parameters versus variants, plus classification results across acceleration and gyroscope settings with activity hints disabled. |

## UI end-to-end latency and frame statistics

| Evaluation | Timing interval |
|---|---|
| Route search | Search submission to route options being displayed. |
| Start journey | Tapping Start to recording controls being ready to end the journey. |
| End journey | Tapping End to the new journey's Review screen appearing. |
| Confirm and save | Tapping Confirm to the save confirmation dialog appearing. |
| Mission suggestions | The same Confirm tap to new suggestions appearing on the Missions screen, including intervening navigation. |
| Weekly Insight | Opening the weekly insight to statistics and advice finishing loading. |

All six flows record main-window frame durations, the count and proportion of frames exceeding system deadlines, and collection completeness.
Response measurements include p50, p95, maximum duration, successes, failures, and timeouts. Mission suggestions and weekly insights distinguish AI responses from local fallbacks.
These evaluations measure performance without pass/fail thresholds. Frame statistics exclude the map's separate rendering layer, dialog windows, and system windows.

## Evaluation tooling checks

Checks cover timers, concurrent recording, percentiles, CSV escaping, AI response validation, SHL data parsing, parameter sweeps, and frame statistics to prevent failures, timeouts, or missing data from being counted incorrectly.
