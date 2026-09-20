# Latency evaluation

Owner: Rui Fang. A small in-memory tool for timing operations; no automatic collection or uploads.

Create one tracker per evaluation run. Keep device, network and build conditions consistent:

```kotlin
val tracker = LatencyTracker(context = "Pixel 8; Android 15; Wi-Fi; build abc123")
```

## Time a function

Call from a coroutine. The result and any exception are passed back unchanged:

```kotlin
val weather = tracker.measure("weather.fetch") {
    repository.getWeather(location)
}
```

Success, failure, cancellation and coroutine timeout are recorded separately. For APIs that
return a failure value instead of throwing, use explicit completion below. Other timeout
exceptions (such as socket timeouts) are recorded as FAILURE with their exception type.

## Time across callbacks or layers

Keep the returned measurement with that particular operation, not in a shared global token:

```kotlin
val measurement = tracker.start("journey.save")
// Pass the measurement along with this operation.
measurement.finish() // Call when the agreed completion condition is reached.
// On failure instead: measurement.finish(Outcome.FAILURE, "PermissionDenied")
```

Complete each measurement on success, failure, cancellation or timeout. Duplicate completion
is ignored. Unfinished operations do not appear in results; this tool does not enforce timeouts.
Finish outside a cancelled coroutine if necessary, e.g. in its cleanup callback.

## Read results

```kotlin
val records = tracker.snapshot()
val summaries = tracker.summaries() // Count, P50 and P95 per scenario AND outcome.
val csv = tracker.toCsv()          // Write this string to a file off the UI thread.
tracker.clear()                   // After finishing all operations and exporting the run.
```

Percentiles use nearest rank (with few samples they are only descriptive). Records remain in
memory until cleared; export and clear between bounded test runs. Use simple scenario names
and error types, not user locations, credentials or raw exception messages.

## Choose the boundary before testing

| Scenario | Start | Finish |
|---|---|---|
| `weather.fetch` | Repository call | Parsed weather returned |
| `transport.classify` | Classifier call | Classification returned |
| `journey.save.server` | Save request | Server acknowledgement |
| `journey.review.ready` | User action | Review UI reaches the agreed usable state |

Use separate names for local saves and server-confirmed saves, cached and network responses,
and immediate button feedback versus completed work. A ViewModel update alone does not prove
the UI has drawn. Timing includes suspension and uses a monotonic clock; it is elapsed latency,
not CPU time. This tool does not measure dropped frames, battery usage or usability.

Unit tests use an injected clock, not sleeps. Their timings are not real app benchmarks.
