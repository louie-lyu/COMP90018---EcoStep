# Offline save audit — 2026-10-12

The existing offline save path works for completed journeys, transport confirmation,
missions and profile preferences. Firestore uses a persistent disk cache. Writes return
`QUEUED` after a two-second acknowledgement timeout and continue uploading later.
External weather, route and public-transport results use Preferences DataStore.

## Verified on Android

`OfflinePersistenceProbeTest` was run in three separate instrumentation processes, with
the target and test app force-stopped between stages. APKs were not reinstalled between
stages, because Gradle's connected test task uninstalls the apps when it finishes.

1. `prepare`: sign in to the local demo project, disable Firestore networking, queue a
   journey, transport confirmation, mission and preference change, and save three caches.
2. `read`: after force-stop, keep Firestore offline. Verify the restored Auth session,
   pending journey and confirmation, mission, preference and all three caches.
3. `recover`: after another force-stop, verify the same data offline again, enable
   networking, wait for pending writes, and read the actual server documents. Verify
   cache removal and automatic removal of malformed JSON for all three caches.

All three stages passed. These exercise the production repositories and cache classes
in an isolated named FirebaseApp/local stores, rather than the full tracking UI or
physical airplane mode. No live Firebase project was modified.

Additional checks:

- 46 targeted JVM tests passed: journey loop, mission creation, external-data fallback
  and cache lifetime policies.
- 6 Android integration tests passed: 5 Firebase repository cases and the weather-cache
  evaluation (9 controlled scenarios).
- The previously failing Windows DataStore cache-removal/corrupt-JSON operations passed
  on Android for weather, routes and public transport.

Raw logs: `app/build/reports/offline-audit/{prepare,read,recover,integration,weather}.txt`.

## Gaps found

### A queued write can be rejected without a save failure reaching the UI

Reproduced with `offlineDuplicateCreateIsQueuedThenRejectedByServer`: create an existing
journey online, attempt to replace it offline, receive `QUEUED`, reconnect, and observe
the server rejecting the replacement and restoring the original document.

`FirebaseTasks.awaitWriteOutcome()` only logs a failure arriving after its timeout. It
does not retain the failed operation or publish a failure event. The review UI derives
sync state from `hasPendingWrites`, so a rollback can look like a completed sync instead
of a rejected change. Add a durable operation/failure record and a retry/error state
before treating queued writes as a fully robust save mechanism.

### Recording and the pre-persistence save window have no recovery draft

`JourneyTracker` holds GPS/motion accumulators in memory and clears them in `finish()`.
`TrackingViewModel.stop()` then classifies the local result and saves it from
`viewModelScope`. Killing the process before the Firestore write loses this result; a
save exception only updates the message and does not retain a retryable summary. The
stop method cannot retry because the tracker is no longer recording.

The restart test proves persistence after a write reaches the local cache. It does not
prove recovery of an unfinished recording or a summary not yet persisted. Save a draft
before clearing the recording, and retain it until local persistence is confirmed.

### Pending-sync visibility is inconsistent

Journey review/history use Firestore pending-write metadata. Profile update adapters
discard `WriteOutcome`, and mission/profile UI generally cannot distinguish a queued
change from a server-acknowledged change. Data is still saved locally, but the UI does
not expose the same sync status across these features.

## Offline availability limits

| Data | Behaviour |
| --- | --- |
| Completed journeys, confirmations, missions, preferences | Local persistent writes; upload when connectivity returns |
| Weather | Cached fallback up to 6 hours |
| Routes | Cached fallback up to 24 hours |
| Public transport | Cached fallback up to 15 minutes; past departures filtered out |
| Uncached cloud data / first registration and sign-in | Requires connectivity |
| Recording not yet saved | In-memory only; no process-restart recovery |

## Reproduce the restart probe

Start `demo-ecostep` Auth/Firestore emulators and an Android emulator. Build the APKs,
install them once with `adb install -r`, and reverse ports 9099 and 8080. Then run:

```powershell
foreach ($stage in @('prepare', 'read', 'recover')) {
    adb shell am force-stop com.ecostep.app
    adb shell am force-stop com.ecostep.app.test
    adb shell am instrument -w -e class com.ecostep.app.firebase.OfflinePersistenceProbeTest -e firebaseEmulatorHost 127.0.0.1 -e offlineProbeStage $stage com.ecostep.app.test/androidx.test.runner.AndroidJUnitRunner
}
```

The probe requires both explicit arguments and skips without them. `recover` deletes
its temporary Auth account; Firestore test documents remain only in the local emulator.
This audit added regression/probe tests and documentation, and did not change the
production offline-saving implementation.
