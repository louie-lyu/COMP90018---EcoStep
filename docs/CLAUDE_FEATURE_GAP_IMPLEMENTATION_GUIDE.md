# EcoStep feature integration implementation guide

This is the implementation brief based on the repository review dated 2026-10-04. Its gap descriptions are historical findings, not a fresh audit or a claim that all proposed features are now implemented. Inspect the current tree before applying any phase.

## Working constraints

Inspect git status and existing changes before every phase. Preserve other contributors' work. Keep the current architecture: Screen -> ViewModel -> Repository/Coordinator -> Data source or Algorithm, with dependencies supplied by AppContainer. Composables must not perform repository/network work directly. Reuse production components and shared clients; do not replace missing integration with display-only mocks. Complete and validate one phase before expanding scope. External failures must have explicit fallback behavior and must not prevent journey confirmation. Send only aggregate context to AI. Avoid unnecessary Firestore schema expansion, new collections, dependency injection frameworks or module changes.

## Original gap audit and phase order

| Area | Original finding |
|---|---|
| Ordinary recording | Home's onStart callback was not wired; tracking mainly served missions. |
| Weather | Home used fixed Melbourne coordinates despite having a live current location. |
| Route search | MockHomeRouteDataSource remained in production while external APIs were available. |
| Carbon and points | Algorithms were not connected to Home estimates or Review alternatives. |
| AI | AiModule existed but was not instantiated in AppContainer. |
| Recurring missions | Recurring detection and trigger planning were unused; EcoMission was not mapped into persisted missions. |
| Preferences | Stored settings did not consistently drive behavior. |
| Automatic detection | Activity recognition ran during recordings rather than initiating eligible recordings. |
| Weekly insight | Local statistics lacked AI insight/action integration. |

Implement in six phases: (1) ordinary tracking and live weather, P0; (2) real routes, PT timetables and estimates, P0; (3) Review carbon alternatives, P0; (4) AI mission coordination, P1; (5) reminders, preferences and automatic detection, P1; (6) weekly coaching and cleanup/documentation, P2. Coordinate shared NavGraph and AppContainer integration with Chi Hong Tam.

## Phase 1: ordinary recording and location-aware weather

Wire Home Start through the existing permission callback to the existing tracker/service. Ordinary recording must not create a mission or linkedMissionId and should use Home's existing recording panel rather than adding a duplicate legacy tracking screen. Respect an already-recording state. Stop must save a real journey and navigate to Review with its ID. Preserve the mission-specific start order: missionStore.start followed by recording.

Add an idempotent HomeViewModel.updateCurrentLocation(GeoPoint). Refresh weather on the first live fix or meaningful movement, not every location callback. Keep Melbourne only as the explicit pre-location fallback and reuse the existing repository cache. In NavGraph, forward liveLocation through LaunchedEffect. Permission denial must leave usable fallback state, and location updates must not create request storms.

```kotlin
fun updateCurrentLocation(location: GeoPoint)
```

```kotlin
LaunchedEffect(liveLocation) {
    liveLocation?.let(homeViewModel::updateCurrentLocation)
}
```

## Phase 2: production routes, public transport and estimates

Replace the mock route data source with a repository adapter. The old parameterless contract and proposed coordinate-based contracts are retained below. A combined HomeRouteSearchResult can carry routes and PT options; a separate PT lookup is also acceptable if failure states stay independent.

```kotlin
suspend fun getRouteOptions(): List<HomeRouteOption>
```

```kotlin
suspend fun getRouteOptions(
    start: GeoPoint,
    end: GeoPoint,
): HomeRouteSearchResult
```

```kotlin
data class HomeRouteSearchResult(
    val routes: List<HomeRouteOption>,
    val publicTransportOptions: List<PublicTransportInfo> = emptyList(),
)
```

```kotlin
class RepositoryHomeRouteDataSource(
    private val externalDataRepository: ExternalDataRepository,
    private val locate: suspend (String, GeoPoint) -> GeoPoint?,
    private val carbonCalculator: CarbonCalculator,
    private val ecoPointsCalculator: EcoPointsCalculator,
)
```

```kotlin
data class HomeUiState(
    // existing fields...
    val publicTransportOptions: List<PublicTransportInfo> = emptyList(),
    val publicTransportErrorMessage: String? = null,
)
```

Resolve labels using PlaceNameResolver.locate: an empty start means Current location, using the live location; otherwise geocode near current location. Resolve the destination near the start. Geocoder failure must be visible, never replaced with an invented Melbourne route. Inject ExternalDataRepository, the suspend location resolver, CarbonCalculator and EcoPointsCalculator. Remember the adapter with the app/resolver dependencies to avoid recreating it on every recomposition.

Use real route geometry and duration/distance. Display carbon savings relative to the same-distance car baseline, not raw emissions mislabeled as savings. If the points rule rewards only completed missions, show zero estimated points for ordinary routes instead of fabricating an award. Do not write preview estimates into the user's balance.

Fetch PT options for the same endpoints. A timetable failure must not discard walking/cycling/car routes. Show PT information only for PT, remove already-departed services, sort upcoming departures and show the first three with line, local departure time and duration. Preserve cache/offline states and explain unavailable PT data without a fake timetable. Prevent stale search results from overwriting a newer query.

## Phase 3: Review carbon alternatives

Inject CarbonCalculator into JourneyReviewViewModel. Recompute from a copy of the journey with the user's confirmed mode and preserve the existing precedence of verified/confirmed transport. Add emissionsGrams and lowerCarbonAlternatives to state. Do not overwrite carbonSavedKg with emissions.

```kotlin
private val carbonCalculator: CarbonCalculator = DefaultCarbonCalculator()
```

```kotlin
val emissionsGrams: Double = 0.0
val lowerCarbonAlternatives: List<CarbonAlternative> = emptyList()
```

Place the read-only alternatives card between mode selection and Confirm. Sort by savings, show kilograms and an Estimated label, and hide it when empty. Changing the selected mode must update the displayed calculations. Keep journey confirmation and linked-mission completion working independently of this card.

## Phase 4: AI mission integration

Instantiate AiModule, the carbon calculator and recurring detector in AppContainer using existing implementations and the shared HTTP client.

```kotlin
val aiModule: AiModule by lazy {
    AiModule(okHttpClient)
}

val carbonCalculator: CarbonCalculator by lazy {
    DefaultCarbonCalculator()
}

val recurringJourneyDetector: RecurringJourneyDetector by lazy {
    DefaultRecurringJourneyDetector()
}
```

```kotlin
class MissionGenerationCoordinator(
    private val authRepository: AuthRepository,
    private val journeyRepository: JourneyRepository,
    private val missionRepository: MissionRepository,
    private val externalDataRepository: ExternalDataRepository,
    private val recurringJourneyDetector: RecurringJourneyDetector,
    private val carbonCalculator: CarbonCalculator,
    private val missionGenerator: DefaultAiMissionGenerator,
    private val clock: () -> Long = System::currentTimeMillis,
    private val zoneId: ZoneId = ZoneId.systemDefault(),
)
```

```kotlin
suspend fun onJourneyConfirmed(journey: JourneySummary): MissionGenerationOutcome
```

MissionGenerationCoordinator owns orchestration, not UI rendering. It needs authentication, journeys, missions, external data, recurring detection, carbon calculation, the existing DefaultAiMissionGenerator, clock and timezone. Return an explicit outcome such as created, not recurring, no alternative, disabled or failed with fallback.

On a confirmed journey: verify the current user and ownership; honor routine mission settings; load a bounded history; detect a recurring pattern; calculate feasible carbon alternatives; collect weather/routes/PT without making those calls a prerequisite for saving the journey; construct MissionContext with bounded aggregate history; invoke the existing generator's two-attempt validation and fallback path; map and persist the suggestion. If route lookup fails, use measured journey distance/time for suitable fallback calculations. Include PT only when a usable PT candidate exists; a failed PT lookup must not erase other candidate modes. Use the effective confirmed transport mode and conservative confidence when detection metadata is absent; do not falsely mark it verified.

The mapper must produce a stable mission ID so repeated confirmations cannot create duplicates. Map short title, explanation, validated mode, labels (coordinate fallback if place resolution fails), journey distance, SUGGESTED status, recurrence weekdays/timezone, planned minute and occurrence date. Derive savings and points from trusted algorithm candidates; AI must not invent routes, numbers, dates or times.

The Review callback first completes an associated mission occurrence, then invokes generation. A generation failure must not fail the saved journey. Rethrow cancellation appropriately, handle other failures as outcomes, and use repository/ID idempotency. Display a suggested mission in the existing location, with accepted/upcoming fallback when appropriate. Log outcome types only, without full prompts, precise coordinates or credentials.

```kotlin
onJourneyConfirmed: suspend (JourneySummary) -> Unit
```

```kotlin
onJourneyConfirmed = { journey ->
    journey.linkedMissionId?.let { missionId ->
        missionStore.completeOccurrence(missionId, journey.journeyId)
    }
    missionGenerationCoordinator.onJourneyConfirmed(journey)
}
```

Test disabled settings, non-recurring journeys, no alternative, PT failure with another mode available, AI failure with persisted fallback, repeated confirmation without duplication, correct mapping and a successful Review save even when generation fails.

## Phase 5: reminders, preferences and automatic detection

### Reminders

```kotlin
interface MissionReminderScheduler {
    fun schedule(mission: Mission, trigger: MissionTrigger, title: String, message: String)
    fun cancel(missionId: String)
    fun cancelAll()
}
```

Use the existing trigger planner and the user's lead-time preference rather than a hard-coded five minutes. Replan after acceptance, edits and lead-time changes; cancel after dismiss/archive, disabling notifications and logout; rebuild for login or re-enabling. Use a stable mission PendingIntent identity so rescheduling replaces the previous alarm. Use an inexact AlarmManager alarm where sufficient; do not add exact-alarm permission unnecessarily. Handle API 33 notification permission, create a dedicated channel and open MainActivity (optionally the relevant mission) on tap. Boot restoration is outside P0.

### Preference behavior

Observe profile settings in a coordinator, not directly in Screen. Routine suggestions control future generation; notification settings cancel/rebuild reminders; lead time reschedules them; automatic detection controls its subscription. Leave community ranking behavior unchanged in this phase. Surface missing fine-location/activity-recognition permissions honestly rather than showing automatic detection as active.

### Automatic detection

Use a coordinator outside an already-running recording service. Subscribe only while logged in, enabled and permitted. Require at least two confident moving observations or roughly 20-30 seconds of sustained motion; consider walking, cycling and vehicle activity. Start the existing service only when fine location is allowed, an authenticated user exists and no recording is active. Keep its foreground notification. The first version supports manual End/Abort; it must not silently save a journey directly or introduce a second saver. Unsubscribe on logout, disable or permission revocation. Clearly document foreground-session limits rather than adding hidden background-location access. Test false positives, thresholds and cleanup.

## Phase 6: weekly coaching and cleanup

Keep local WeeklyCoachReport generation, then obtain insight and action through AiModule or an injected interface. Add loading, insight, action and usedAi state; distinguish a true data-source failure from an AI failure. If AI fails, retain statistics and show local fallback coaching. With no activity, show the existing no-activity fallback. Render This week and Coaching tip cards, optionally indicating AI personalization without presenting fallback as AI.

```kotlin
data class WeeklyInsightUiState(
    val report: WeeklyCoachReport? = null,
    val insight: String? = null,
    val action: String? = null,
    val usedAi: Boolean = false,
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
)
```

Search for PlaceholderScreen references before deleting anything; remove only unused code, never active screens. Update TEAM_GUIDE and WORK_PLAN to match verified integration. Mark Done only after testing; distinguish implemented but awaiting integration from in-progress work and link evidence.

```bash
rg "PlaceholderScreen" app/src/main docs
```

## Ownership and review surface

| Area | Main files/components |
|---|---|
| Shared wiring | AppContainer.kt and NavGraph.kt; coordinate with Chi Hong Tam |
| Home | HomeScreen, HomeViewModel, HomeUiState, HomeRouteDataSource and the production repository adapter |
| Review | JourneyReviewScreen/ViewModel and carbon state |
| Mission AI | Existing AiModule/DefaultAiMissionGenerator, MissionGenerationCoordinator and EcoMission-to-Mission mapper |
| Preferences/reminders | Profile settings observer, MissionReminderScheduler and trigger planning |
| Automatic recording | Automatic-detection coordinator and existing tracking service |
| Weekly | WeeklyInsightScreen/ViewModel and existing WeeklyCoach calculations |
| Documentation | TEAM_GUIDE.md and WORK_PLAN.md after verification |

## Validation and completion

Use fakes for JVM tests; do not require live Firebase, Retrofit, Geocoder or notifications. Test location updates, empty destinations, independent PT failure and stale-response handling; adapter location failures and carbon/points math; Review mode precedence; coordinator and mapper cases above; weekly AI/fallback behavior; reminder rebuilding/cancellation without duplicate alarms; automatic detection thresholds and cleanup.

Manual flow: login -> current location/weather -> real destination search -> routes/PT/estimates -> ordinary Start/End -> Review and correct mode -> inspect alternatives -> Confirm -> recurring threshold produces suggestion -> Accept -> reminder -> start/complete mission -> trusted backend points -> weekly statistics and coaching. Negative scenarios include offline operation, denied permissions, AI 401/403/429, empty geocoding, no PT service and pending Firestore writes.

Run these after each phase and record existing baseline failures without deleting assertions:

```powershell
.\gradlew.bat testDebugUnitTest
.\gradlew.bat assembleDebug
```

Done means production wiring replaces mocks for the completed flow, UI callbacks and loading/error/permission states work, existing implementations are reused, tests and manual evidence are recorded, sensitive data is absent from logs and WORK_PLAN reflects actual status. Do not block Review on AI, trust AI to override protected values, store preferences without behavior, confuse in-recording recognition with automatic detection, or expand package IDs/modules/collections/permissions without a concrete need. Do not use destructive Git commands.

Report each phase with: completed user behavior; modified files; reused components; new/changed interfaces; UI location; exact test commands/results; remaining gaps/risks; owner coordination needed. Preserve verified work when blocked rather than merely claiming implementation.
