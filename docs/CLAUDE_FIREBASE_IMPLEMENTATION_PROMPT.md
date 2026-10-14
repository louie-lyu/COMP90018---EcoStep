# EcoStep Firebase integration: implementation and testing prompt

Usage: provide this complete brief, beginning with Role and objectives, to the implementation agent. Audit the current repository first, then implement and validate in phases. This document is an implementation brief, not evidence that all listed work has been completed.

## Role and objectives

Act as a senior Android/Kotlin/Firebase engineer. Inspect and modify the repository to productionize the data layer and provide repeatable automated tests; do not stop at advice or pseudocode.

Complete the Tracking -> Review -> History loop through the same FirestoreJourneyRepository. Replace mock/in-memory production behavior for missions, mission results, profiles, cross-device preferences, EcoPoints transactions, reward redemption, friendships and rankings with clear repository interfaces. Keep weather, route, public-transport and map caches local. Protect awards, redemption and rankings with Security Rules, idempotent operations and trusted server calculations. Deliver JVM tests, Firebase Emulator integration tests, rules tests, offline tests and manual acceptance steps.

Do not connect automated tests to production Firebase or modify production data. Use fakes, memory implementations or Firebase Emulator.

## Repository audit and baseline

Read AppContainer, NavGraph, FirebaseAuthRepository, FirestoreJourneyRepository, AuthRepository, JourneyRepository, JourneySummary, tracking/review/history ViewModels, mock profile/rewards/weekly sources, carbon and points calculators, EcoMission and MissionResult, and Gradle/version configuration. Establish the current data flow and report the audit in at most twenty lines before implementation. Use reasonable defaults instead of repeatedly asking about routine choices; ask when an architectural decision is required.

```powershell
git status --short --branch
git log -8 --oneline --decorate
java -version
.\gradlew.bat test
```


Use the required JDK (the original baseline used JDK 21). The original Windows baseline recorded six DataStore `.tmp` rename failures; re-establish the current baseline, record it and do not hide failures. Preserve uncommitted changes. Do not commit secrets, place Admin SDK credentials in the Android client, persist UI mocks as production data, allow clients to mint points, or upload raw sensor streams.

The original audit found real authentication and tracking alongside mocked Review/History and `mock_user` consumers, mock profile/rewards/weekly data, incomplete award fields and absent rules. Prioritize a real journey loop before broadening scope; verify which gaps still exist now.

## Layering and contracts

```text
Compose Screen
    -> ViewModel
        -> Repository interface (data/repository)
            -> Firestore implementation (data/firebase)
            -> Fake implementation (test only)
        -> Algorithm interface (algorithm)
```

```text
ProfileRepository
MissionRepository
MissionResultRepository
EcoPointsRepository
RewardsRepository
FriendsRepository
LeaderboardRepository
```


Keep manual AppContainer injection and injectable constructors for tests. ViewModels depend on interfaces and must not call FirebaseFirestore.getInstance directly. Separate storage DTOs from UI state and retain backward compatibility. Obtain UID from authenticated state rather than hard-coding or trusting caller-supplied ownership. Store event times as UTC epoch values and audit times as server timestamps. Validate numbers as finite, nonnegative and within sensible ranges. Introduce adapters gradually so previews and tests can retain fakes while production uses real repositories.

## Data model

### Private user profile

```text
users/{uid}
```

```text
schemaVersion: 1
displayName: string
createdAt: server timestamp
updatedAt: server timestamp
preferences: {
  routineLearningEnabled: boolean,
  missionNotificationsEnabled: boolean,
  defaultReminderMinutes: number,
  automaticJourneyDetectionEnabled: boolean,
  communityRankingEnabled: boolean
}
```


Create the profile idempotently after authentication. Keep email in Firebase Auth rather than duplicating it unnecessarily. Synchronize the listed preferences across devices; retain only device-specific state in DataStore. Use merge/field updates to avoid replacing unrelated preferences.

### Public profile

```text
publicProfiles/{uid}
```

```text
displayName: string
communityRankingEnabled: boolean
updatedAt: server timestamp
```


Expose only the whitelisted public fields. Do not include email, precise location, private preferences or journeys. Any email-based discovery must use a controlled backend rather than permitting client-side enumeration.

### Journeys

```text
users/{uid}/journeys/{journeyId}
```

```text
schemaVersion: 2
detectedTransportMode: string
confirmedTransportMode: string
confirmationStatus: "pending" | "confirmed"
carbonSavedGrams: number
ecoPoints: number
linkedMissionId: string | null
routePolyline: string | null
createdAt: server timestamp
updatedAt: server timestamp
```


Retain existing fields and add the schema-v2 fields compatibly. Resolve transport mode as confirmedTransportMode, then legacy transportMode, then UNKNOWN. Tracking creates a pending journey, Review confirms it, and History observes the authenticated user's actual repository. Preserve createdAt once assigned. Handle null server timestamps in pending local writes, and use merge writes that do not discard future fields. Store an encoded routePolyline only if the UI actually needs it; otherwise keep it null for future use. Do not upload raw GPS/sensor streams.

### Missions

```text
users/{uid}/missions/{missionId}
```

```text
schemaVersion: 1
title: string
description: string
missionType: string
targetTransportMode: string | null
targetDistanceMeters: number | null
status: "suggested" | "accepted" | "active" | "completed" | "dismissed" | "archived"
recurrence: {
  type: "none" | "daily" | "weekly" | "custom",
  interval: number,
  daysOfWeek: [number],
  timezone: string
}
nextOccurrenceDate: "YYYY-MM-DD" | null
createdAt: server timestamp
updatedAt: server timestamp
```


Use a production domain model rather than persisting a UI Mock type. Support the declared status and recurrence options. Skip today writes an occurrence result; it must not delete the recurring mission. CRUD, acceptance, edits, completion, dismissal and archiving must survive restart and synchronize across devices.

### Mission results

```text
users/{uid}/missionResults/{resultId}
```

```text
schemaVersion: 1
missionId: string
occurrenceDate: "YYYY-MM-DD"
accepted: boolean
completed: boolean
skipped: boolean
linkedJourneyId: string | null
actualTransportMode: string | null
actualCarbonSavingGrams: number | null
ecoPointsAwarded: number
completedAtMillis: number | null
createdAt: server timestamp
updatedAt: server timestamp
```


Treat `(missionId, occurrenceDate)` as the idempotency boundary. A repeated confirmation must not award points twice. Weekly coaching must read actual stored results; award only accepted/completed/valid occurrences according to the existing policy.

### EcoPoints ledger and aggregate statistics

```text
users/{uid}/pointTransactions/{transactionId}
```

```text
type: "mission_award" | "reward_redemption" | "adjustment"
amount: integer
sourceId: string
idempotencyKey: string
createdAt: server timestamp
metadata: map
```

```text
userStats/{uid}
  pointsBalance
  totalJourneys
  carbonSavedGrams
  completedMissions
  updatedAt
```


The ledger is the audit source of truth. Update balance and statistics through trusted backend transactions. Use a deterministic mission-result transaction identity; establish a redemption idempotency key once and reuse it on retries. Clients must not create arbitrary positive entries or write userStats. Server-side code validates ownership, completion and award eligibility, then updates ledger and aggregates atomically.

### Rewards and redemptions

```text
rewards/{rewardId}
users/{uid}/rewardRedemptions/{redemptionId}
```

```text
merchantName
title
description
pointsRequired
category
active
validFrom
validUntil
inventory
```

```text
rewardId
pointsSpent
redemptionCode
status: "available" | "used" | "expired" | "cancelled"
redeemedAt
expiresAt
usedAt
```


Use a callable function with authenticated identity. Validate that the reward is active, within its validity period and in stock, then atomically validate/debit the balance, write a negative ledger entry and create the redemption. Generate an unpredictable redemption code and make retries idempotent. Do not implement a fake balance deduction in the ViewModel. Prevent repeated taps from causing duplicate requests or charges.

### Friends and requests

```text
users/{uid}/friends/{friendUid}
users/{uid}/friendRequests/{requestId}
```

```text
senderUid
receiverUid
status: "pending" | "accepted" | "declined" | "cancelled"
createdAt
updatedAt
```


Only participants may read a request. The sender may cancel; the receiver may accept or decline. Acceptance uses trusted atomic work to establish both friendship directions. Clients must not inject arbitrary friendship documents or impersonate either participant.

### Rankings

```text
leaderboardStats/{uid}
leaderboards/{period}/entries/{uid}
```


Compute aggregates from confirmed journeys and valid mission results on the server. Deny client writes to ranking documents. Exclude users who opt out of community rankings. Friend rankings must use the actual friend set; show the top ten and the current user's position as required by the UI.

## Client/server boundary

Clients collect data, submit requests, read state and queue permitted ordinary writes. Trusted backend code owns carbon/award validation, the ledger, redemption/inventory changes, friendship aggregation and rankings. If no Functions project exists, add TypeScript Functions outside the Android source tree, using Admin SDK and emulator configuration. Do not deploy production Functions, Rules or indexes without explicit authorization.

Calculate savings as `max(0, sameDistanceCarEmissions - actualModeEmissions)` using shared, consistent factors. Do not trust client-submitted carbon/points numbers simply because the user owns the document.

## Implementation phases

1. **Journey loop:** schema-1/2 compatible mapping, authenticated IDs, shared real repositories in Tracking/Review/History, confirmation fields and offline visibility.
2. **Profiles/preferences:** idempotent creation, cross-device field updates and rollback/error behavior; keep fake implementations for previews/tests.
3. **Missions/results:** real CRUD and state transitions, per-occurrence skip/completion, idempotent results and real weekly sources. Seed only test environments.
4. **Points/rewards:** trusted Functions, ledger and balance transactions, deterministic request identity, redemption retries and duplicate-tap handling.
5. **Friends/rankings:** authenticated requests, valid transitions, atomic relationships, opt-in ranking queries, rules and indexes.

Compile and test each phase before proceeding; do not leave every layer broken until the final phase.

## Security Rules and indexes

```text
firebase.json
firestore.rules
firestore.indexes.json
```


Deny unauthenticated private access. Permit owners only within strict field/type constraints. A journey's userId must match authentication; reject spoofing and client changes to protected award, carbon and audit fields. Ledgers and aggregates are client-read-only. Redemption creation is server-controlled or constrained to a request path; rewards are readable by authenticated users and not client-writable. Requests are visible only to their participants with allowed transitions. Ranking data is read-only to clients, and public profiles expose only their whitelist.

Validate required fields, immutable fields, string length, numeric bounds and allowed enum values. UID checks alone are insufficient. Rules are the final security boundary, independent of UI validation. Add indexes required by actual queries, with emulator tests for allowed and denied operations.

## Offline and synchronization behavior

Keep Firestore caching enabled. Offline confirmation should immediately display locally queued data, then distinguish queued, synchronized and failed states. A three-second server wait expiring does not mean a local write failed. Represent isSaving, isPendingSync, isSynced and errors explicitly where the UI needs them. Repeated confirmation is idempotent.

Merge independent preference fields with updatedAt and appropriate last-write-wins behavior. Do not apply that policy to balances, redemption or friendship transactions. Remove snapshot listeners when Flow collectors cancel, and do not show a false server acknowledgement for local pending writes.

## Automated and manual validation

### JVM tests

Cover schema-1 migration, schema-2 round trips, missing defaults, unknown enums and invalid numbers. Test the complete journey flow with a shared fake, including selected mode and confirmation fields. Cover mission transitions, skip-today behavior, unique occurrence results, one-time points, profile update/rollback, reward request debouncing and authenticated UID selection. Use injected interfaces; no live Firebase, networking or geocoding in JVM tests.

### Firebase Emulator integration

Use `demo-ecostep`, not production. Verify registration/login/logout/session restoration; journey CRUD/listeners; cross-user rejection; offline queue and reconnection; mission/result idempotency; insufficient points, unavailable rewards and repeated redemption; friendship transitions; and rejected client ranking writes.

### Rules tests

Use `@firebase/rules-unit-testing`. Cover unauthenticated denial, owner access, cross-user access and forged user IDs, protected-field changes, invalid/missing/oversized fields, unknown enums and negative values, reward read/write restrictions, request participant visibility/transitions and ranking read/write policy.

### Manual UI acceptance

Register/login; verify Home; record and end a journey; open Review with the real ID; select a mode and confirm; verify History; restart and confirm persistence; finish offline and observe queued then synchronized status; change preferences and verify persistence; accept/edit/complete missions and verify stored results; redeem with repeated taps and verify a single debit. Check a second account/device where cross-user isolation or synchronization matters.

### Commands and honest reporting

```powershell
.\gradlew.bat test
.\gradlew.bat assembleDebug
.\gradlew.bat connectedDebugAndroidTest
```

```powershell
firebase emulators:exec --project demo-ecostep "npm test"
```


If an environment dependency prevents a command, report the exact missing condition and which checks did run. Do not replace evidence with “should pass.” Run git diff --check. Keep known baseline failures visible and identify new failures separately.

## Quality and acceptance checklist

Use existing Kotlin conventions, manual injection and suspend/Flow interfaces; do not expose Firebase Tasks in ViewModels or SDK types in domain models. Use current Firebase modules without reintroducing old `-ktx` artifacts. Keep listeners cancellable, operations idempotent and schemas versioned. Avoid sensitive logs. Retain Mock classes in preview/debug/test paths until their production consumers are replaced and tested.

- [ ] Tracking, Review and History share the real repository and authenticated UID.
- [ ] Legacy and new journeys read correctly; confirmation survives restart and offline sync.
- [ ] Profiles and preferences persist across devices with explicit error states.
- [ ] Mission states and occurrence results persist without duplicate awards.
- [ ] Ledger, balance and redemption use trusted, atomic, idempotent operations.
- [ ] Friend requests enforce participant transitions and symmetric relationships.
- [ ] Rankings use trusted aggregates and respect opt-out.
- [ ] Rules/indexes are checked in and tested; forbidden client writes fail.
- [ ] Relevant JVM, emulator, rules and UI checks have actual results; baseline failures remain documented.
- [ ] git diff --check passes; no secrets, raw sensor streams or accidental production changes.

## Final delivery format

Report (1) phases completed, (2) architecture/data flow, (3) actual schema, (4) security boundaries and rules, (5) exact commands/test counts/results, (6) offline/pending-sync behavior, (7) compatibility/migration, (8) manual verification steps, (9) remaining risks or blockers, and (10) changed files with purposes. Do not deploy production Functions, Rules or indexes until authorized.
