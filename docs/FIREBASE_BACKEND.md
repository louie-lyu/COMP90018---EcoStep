# Firebase data layer and trusted backend

How EcoStep stores data in Firestore, what clients may write, what only Cloud Functions write,
and how to test everything locally without touching a real Firebase project.

## Layers

```
Compose screen -> ViewModel -> repository interface (data/repository)
                                 -> Firestore implementation (data/firebase)
                                 -> fakes (app/src/test/.../testing)
                ui/adapters bridge existing UI data-source contracts to the repositories
functions/      Cloud Functions (TypeScript): points ledger, rewards, friends, leaderboards
firestore.rules the final authorization boundary
```

`AppContainer` wires every production implementation. The `ui/mock` classes remain for previews and
tests only; production navigation no longer uses them, except `MockHomeRouteDataSource`
(route suggestions belong to the routing module and stay local).

## Collections

| Path | Written by | Notes |
|---|---|---|
| `users/{uid}` | owner | `schemaVersion`, `displayName`, `preferences{5 flags}`, `createdAt`, `updatedAt`. No email (Auth is the source). Preferences use field-level merge, last-write-wins. |
| `users/{uid}/journeys/{id}` | owner: create, confirm. Backend: trusted fields | v2 adds `detectedTransportMode`, `confirmedTransportMode`, `confirmationStatus`, `linkedMissionId`, `routePolyline` (always null for now), `createdAt/updatedAt`. Backend writes `carbonSavedGrams`, `ecoPoints`, `verificationStatus/Reason`, `awardedMissionResultId`. |
| `users/{uid}/missions/{id}` | owner | status `suggested/accepted/active/completed/dismissed/archived`, `recurrence{type,interval,daysOfWeek,timezone}`, `scheduledMinuteOfDay`, `nextOccurrenceDate`, `activeOccurrenceDate`, `estimates[]`. |
| `users/{uid}/missionResults/{missionId}_{date}` | owner: actions. Backend: award | One doc per occurrence, so it's idempotent. Backend writes `actualTransportMode`, `actualCarbonSavingGrams`, `ecoPointsAwarded`, `awardStatus`, `createdAt`. Completion is final. |
| `users/{uid}/pointTransactions/{id}` | backend | Immutable ledger: `mission_{resultId}`, `redeem_{requestId}`. |
| `users/{uid}/rewardRedemptions/{requestId}` | backend | Reward details copied in; unpredictable code. |
| `users/{uid}/friendRequests/{sender_receiver}` | backend | Mirrored to sender and receiver only. |
| `users/{uid}/friends/{friendUid}` | backend | Created for both users atomically on accept. |
| `userStats/{uid}` | backend | `pointsBalance`, `totalJourneys`, `carbonSavedGrams`, `completedMissions`. |
| `publicProfiles/{uid}` | backend | Only `displayName`, `displayNameLower`, `communityRankingEnabled`, `updatedAt`. |
| `rewards/{id}` | admin | Catalog: `pointsRequired`, `active`, `validFrom/Until`, `inventory`. |
| `leaderboardStats/{uid}` | backend | Readable by the user and their friends. |
| `leaderboards/{all_time or week-YYYY-Www}/entries/{uid}` | backend | Opted-out users are removed. Weeks are UTC ISO weeks. |

Weather, routes, public transport and map tiles stay in local DataStore and osmdroid caches. Raw
GPS and motion samples never leave the device; only the aggregated `sensorFeatures` are stored.

## Trust boundary

- EcoPoints come only from completed missions. A plain journey earns carbon credit, which feeds
  the stats and leaderboards, and shows `ecoPoints = 0`.
- Carbon saving is `max(0, car - mode)` for the same distance, with factors from
  `functions/src/config/scoring.json`. `ScoringConfigContractTest` keeps that file equal to
  `EmissionFactors` and `DefaultEcoPointsCalculator`.
- A confirmed journey whose average speed is impossible for its mode is kept, but it gets
  `verificationStatus = rejected` and no credit.
- A mission award requires the user's own confirmed journey, recorded for that mission and not
  already used by another occurrence. The ledger ID `mission_{resultId}` makes the award
  exactly-once.
- `redeemReward` (callable) checks the reward and the balance and writes the ledger, balance,
  inventory and redemption in one transaction. The client's `requestId` makes retries
  idempotent.
- Friend requests change only through callables (`sendFriendRequest`, `respondToFriendRequest`,
  `cancelFriendRequest`). `searchUsers` matches an exact email via Admin Auth (the result shows a
  masked email) or a display-name prefix.

Journeys are still sensor data asserted by the client. The rules and plausibility checks limit
abuse, but they can't prove a journey happened.

## Offline behaviour

Firestore persistence is enabled explicitly. Writes return `WriteOutcome.SYNCED` when the server
acknowledges them within 2 s, and `QUEUED` otherwise. A queued write is already in the local
cache, so Review reads it immediately and Review/History show "waiting to sync" until the
snapshot's `hasPendingWrites` clears. Snapshot listeners are removed when their Flow closes.

## Tests

```powershell
.\gradlew.bat test                                    # JVM unit tests
cd functions; npm install; npm run test:unit          # backend logic
cd functions; npm test                                # rules + integration on emulators (demo-ecostep)
```

`npm test` starts the Auth, Firestore and Functions emulators. Every emulator test refuses to run
without `FIRESTORE_EMULATOR_HOST`, and the project IDs start with `demo-`, so they can't reach a
real project. On Windows the Firestore emulator JVM can survive the run. If port 8080 is busy
afterwards, run `scripts\stop-orphaned-emulators.ps1`.

Instrumented repository tests (real Android Firestore SDK against the emulators):

```powershell
cd functions; npx firebase emulators:start --project demo-ecostep --only auth,firestore
adb reverse tcp:9099 tcp:9099; adb reverse tcp:8080 tcp:8080
.\gradlew.bat connectedDebugAndroidTest `
  -Pandroid.testInstrumentationRunnerArguments.class=com.ecostep.app.firebase.FirebaseEmulatorJourneyRepositoryTest `
  -Pandroid.testInstrumentationRunnerArguments.firebaseEmulatorHost=127.0.0.1
```

On API 37, apps can't connect to the host address 10.0.2.2, which is why the ports go through
`adb reverse`. To run the whole app against the emulators, build with
`-Pecostep.firebaseEmulatorHost=127.0.0.1` and also reverse port 5001.

## Deploying (manual, needs explicit approval)

`.firebaserc` defaults to `demo-ecostep`, so an accidental `firebase deploy` can't hit the real
project. To deploy for real:

1. Upgrade the project to the Blaze plan (Cloud Functions requires it).
2. `firebase deploy --project <real-project-id> --only firestore:rules,firestore:indexes,functions`
3. Seed `rewards/` in the console or with an Admin script. Clients can't write the catalog.
4. Existing `userStats` start empty. Any pre-existing balances must be migrated as
   `adjustment` ledger entries by an admin script.
