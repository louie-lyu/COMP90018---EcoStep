# Journey tracking implementation guide

Owner: Zongcheng. Original target: finish on 26 September, freeze on 27 September. This is the historical implementation plan following the verified authentication work on `feature/zongcheng-auth`, with [SensorFeatures](SENSOR_DATA_FORMAT.md) proposed for the shared interface. Examples are implementation sketches, not evidence that every requirement is currently complete.

## Acceptance criteria

1. A recording shows increasing GPS, accelerometer and gyroscope sample counts when the sensors are available.
2. Recording continues with the screen locked, using a location foreground service.
3. The UI updates elapsed time, distance, location accuracy and sensor counts about once per second.
4. Sensor callbacks and network work stay off the main thread.
5. Permission denial has a retry or application-settings path.
6. Stop saves a real JourneySummary and opens Review with its actual ID, replacing the temporary `test_1` flow.
7. An offline stop opens Review while Firestore queues the write and syncs after connectivity returns.
8. Upload only aggregates, without raw GPS or motion streams.

## Architecture and ownership

TrackingRoute displays recording state and start/stop controls. TrackingViewModel exposes StateFlow<TrackingUiState>, checks permission before starting JourneyTrackingService, and on stop calls tracker.finish(), saves the result and navigates with its ID. AppContainer owns one JourneyTracker for the current recording; both the service and UI use it, without service binding. JourneyTrackingService collects LocationTracker and MotionSensorTracker flows, serializes their updates, and unregisters listeners on stop. JourneySummaryBuilder converts RecordingResult for JourneyRepository.

New files belong in `sensors/tracking/` (RunningStats, FeatureAccumulator, JourneyTracker, TrackingState, JourneySummaryBuilder), `sensors/location/LocationTracker.kt`, `sensors/motion/MotionSensorTracker.kt`, `sensors/service/JourneyTrackingService.kt` and `sensors/ui/` (TrackingRoutes, TrackingViewModel, TrackingRoute). RunningStats and FeatureAccumulator are plain Kotlin for JVM testing. Add `data/model/SensorFeatures.kt` through the interface-change process. Use a local `TrackingRoutes.TRACKING = "tracking"` rather than modifying Yu-Han's Routes.kt.

Keep shared changes minimal: add service/permissions in AndroidManifest; add the approved nullable SensorFeatures to JourneySummary and its Firestore mapping; expose a lazy tracker in AppContainer; add a NavGraph route and replace the temporary post-login flow. Add coroutine Play Services `await` support only if absent and required. Do not change unrelated package ownership or dependency versions.

## Permissions and foreground service

Keep FINE and COARSE location permissions; add FOREGROUND_SERVICE, FOREGROUND_SERVICE_LOCATION and POST_NOTIFICATIONS, plus a non-exported service of type `location`. Do not request background location: recording starts explicitly while the app is visible. SENSOR_DELAY_GAME is about 50 Hz, below the rate requiring HIGH_SAMPLING_RATE_SENSORS. Accelerometer and gyroscope do not require runtime permission.

On Start, request FINE and COARSE together, and notifications on API 33+. Fine location starts recording; coarse-only access explains why precise location is needed and does not start. Recoverable denial offers Retry; permanent denial opens app settings. Notification denial must not block recording, though the notification is not shown in the ordinary notification drawer. Check location permission before starting a location foreground service, especially on Android 14+.

Use SupervisorJob with Dispatchers.Default and `limitedParallelism(1)` so tracker updates are serialized. Handle ACTION_START and ACTION_STOP, use ServiceCompat.startForeground with the location type, and return START_NOT_STICKY so process death does not silently create partial recordings. Create a low-importance ongoing notification whose tap returns to MainActivity. Cancel child collectors to trigger awaitClose and unregister listeners; cancel the whole scope on destroy.

## GPS and motion collection

Wrap Fused Location in callbackFlow. Request high accuracy every 2,000 ms with a 1,000 ms minimum. Forward callbacks through the flow and remove location updates in awaitClose. Reject accuracy worse than 30 m and implied speed above 60 m/s. Count distance only for displacement of at least 3 m. Prefer native speed, otherwise derive distance/time. Retain first and last accepted locations.

Use a HandlerThread for SensorManager callbacks and SENSOR_DELAY_GAME for both motion sensors. Use a bounded flow buffer of 256; if full, trySend drops new samples without blocking the sensor thread. Missing gyroscope support is allowed and produces zero samples. Use raw accelerometer magnitude minus gravity as described in SensorFeatures; do not require a linear-acceleration sensor, which some devices lack. A 50 Hz stream produces about 180,000 samples/hour: aggregate online, never retain or upload that raw list.

Use Welford's population mean/standard deviation. Acceleration mean uses abs(magnitude - 9.80665); standard deviation uses raw magnitude. Keep GPS speeds for p95; the example uses sorted index `((size - 1) * .95).toInt()`. Use a plain LocationSample data class for JVM tests and a haversine calculation or Android Location distance at the platform boundary.

## State, persistence and navigation

State transitions: Idle -> Recording -> finish -> Idle. Publish elapsed time, distance, accuracy and all three counts at most once per second. `finish()` returns null if data is insufficient and resets the tracker. A null accuracy means no location fix yet.

Build JourneySummary using a UUID, the authenticated UID, first/last accepted locations and timestamps, and SensorFeatures. Reject/discard recordings shorter than 30 seconds or with fewer than two valid GPS samples. Use the integrated classifier when available; otherwise store UNKNOWN so Review can confirm the mode later.

Firestore's local set occurs before its Task receives server acknowledgement. Awaiting that task offline can stall indefinitely. The proposed UI path waits at most 3 seconds, then continues to cached Review with a pending-sync state. Apply the same reasoning to Review's confirmation save; timeout is not evidence that the queued write failed. Pass the real journeyId to Review and pop the recording route. Check the actual repository's queue/acknowledgement behavior during integration.

Persist SensorFeatures as a nested map, read missing maps as null, default featureVersion to 1 and convert Firestore Numbers to the expected Double/Int types.

## Tests and manual verification

JVM tests: RunningStats on `[2,4,4,4,5,5,7,9]` gives mean 5 and population standard deviation 2; empty input returns zero. Test GPS filtering, distance, p95, stop ratio and absent gyroscope; builder duration/sample minimums, endpoints and feature mapping; Firestore round-trip and missing legacy fields.

Emulator: play a GPS route in Extended Controls, exercise virtual motion sensors, lock the screen for 30 seconds, finish offline in airplane mode, verify Review opens from cached data, restore connectivity and verify sync. Physical-device follow-up: walk for 5-10 minutes, then test cycling/public transport and five stationary minutes; compare distance with a map and capture tracking, review and Firestore evidence. Log a compact `EcoStepTracking` summary about every ten seconds. Rui should measure stop-to-review latency for ten online and ten offline runs each.

Watch for missing foreground permissions/service type, expensive main-thread callbacks, listeners left registered, no emulator location playback, GPS drift, and offline server waits. START_NOT_STICKY intentionally does not recover a recording after process death.

Original 26 September schedule: one hour for statistics/tests, 1.5 hours for sensor collectors, 1.5 hours for service/permissions, one hour for UI/persistence, then one hour for emulator verification and PR. If time is short, defer permanent-denial settings navigation or notification-tap handling first. A last-resort ViewModel-only foreground prototype must still use all three sensors and explicitly document that lock-screen recording is unsupported.

## Implementation examples

The following examples retain the original APIs and calculation sketches. Fill their placeholders during implementation and check against the current source tree.

### Manifest

```xml
<!-- Existing -->
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
<uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />

<!-- Add -->
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_LOCATION" />  <!-- Android 14+ -->
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />           <!-- Android 13+ -->

<application ...>
    <service
        android:name=".sensors.service.JourneyTrackingService"
        android:exported="false"
        android:foregroundServiceType="location" />
</application>
```

### Permission requests

```kotlin
val permissions = buildList {
    add(Manifest.permission.ACCESS_FINE_LOCATION)
    add(Manifest.permission.ACCESS_COARSE_LOCATION)
    if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
}.toTypedArray()

val launcher = rememberLauncherForActivityResult(
    ActivityResultContracts.RequestMultiplePermissions()
) { result ->
    val fineGranted = result[Manifest.permission.ACCESS_FINE_LOCATION] == true
    viewModel.onPermissionResult(fineGranted)   // Start the service after permission is granted.
}

// Button click
Button(onClick = {
    if (hasFineLocation(context)) viewModel.start() else launcher.launch(permissions)
}) { Text("Start journey") }

// Open application settings
context.startActivity(
    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
)
```

### Service lifecycle

```kotlin
class JourneyTrackingService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    // Serialize callbacks so JourneyTracker does not need locks.
    private val serial = Dispatchers.Default.limitedParallelism(1)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> start()
            ACTION_STOP -> stop()
        }
        return START_NOT_STICKY   // Do not restart after process death and create incomplete journeys.
    }

    private fun start() {
        createChannel()
        ServiceCompat.startForeground(
            this, NOTIFICATION_ID, buildNotification(),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION,
        )
        val container = (application as EcoStepApp).appContainer
        val tracker = container.journeyTracker
        tracker.begin(System.currentTimeMillis())

        scope.launch(serial) {
            LocationTracker(this@JourneyTrackingService).locations()
                .collect { tracker.onLocation(it) }
        }
        scope.launch(serial) {
            MotionSensorTracker(this@JourneyTrackingService).samples()
                .collect { tracker.onMotion(it) }
        }
    }

    private fun stop() {
        scope.coroutineContext.cancelChildren()   // Trigger awaitClose and unregister listeners.
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() { scope.cancel(); super.onDestroy() }

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID, "Journey tracking", NotificationManager.IMPORTANCE_LOW,
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification() = NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(android.R.drawable.ic_menu_mylocation)
        .setContentTitle("EcoStep is recording your journey")
        .setOngoing(true)
        .setContentIntent(/* PendingIntent that opens MainActivity */)
        .build()

    companion object {
        const val ACTION_START = "com.ecostep.app.tracking.START"
        const val ACTION_STOP = "com.ecostep.app.tracking.STOP"
        private const val CHANNEL_ID = "journey_tracking"
        private const val NOTIFICATION_ID = 1001
    }
}
```

### Location request

```kotlin
ContextCompat.startForegroundService(
    context, Intent(context, JourneyTrackingService::class.java).setAction(ACTION_START)
)
```

### Location flow

```kotlin
class LocationTracker(context: Context) {
    private val client = LocationServices.getFusedLocationProviderClient(context)

    @SuppressLint("MissingPermission")   // The UI checks permission before calling.
    fun locations(): Flow<Location> = callbackFlow {
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 2_000L)
            .setMinUpdateIntervalMillis(1_000L)
            .build()
        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.locations.forEach { trySend(it) }
            }
        }
        client.requestLocationUpdates(request, callback, Looper.getMainLooper())
        awaitClose { client.removeLocationUpdates(callback) }
    }
}
```

### Motion flow

```kotlin
data class MotionSample(val type: Int, val x: Float, val y: Float, val z: Float)

class MotionSensorTracker(context: Context) {
    private val sm = context.getSystemService(SensorManager::class.java)
    val hasGyroscope = sm.getDefaultSensor(Sensor.TYPE_GYROSCOPE) != null

    fun samples(): Flow<MotionSample> = callbackFlow {
        // Receive callbacks on a dedicated thread, off the main thread.
        val thread = HandlerThread("EcoStepSensors").apply { start() }
        val handler = Handler(thread.looper)
        val listener = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                trySend(MotionSample(e.sensor.type, e.values[0], e.values[1], e.values[2]))
            }
            override fun onAccuracyChanged(s: Sensor?, a: Int) = Unit
        }
        listOf(Sensor.TYPE_ACCELEROMETER, Sensor.TYPE_GYROSCOPE).forEach { type ->
            sm.getDefaultSensor(type)?.let {
                sm.registerListener(listener, it, SensorManager.SENSOR_DELAY_GAME, handler)
            }
        }
        awaitClose {
            sm.unregisterListener(listener)
            thread.quitSafely()
        }
    }.buffer(256)   // Bounded buffer: trySend drops new samples when full without blocking sensor callbacks.
}
```

### Online statistics

```kotlin
class RunningStats {
    var count = 0L; private set
    private var mean = 0.0
    private var m2 = 0.0

    fun add(x: Double) {
        count++
        val delta = x - mean
        mean += delta / count
        m2 += delta * (x - mean)
    }

    fun mean(): Double = if (count == 0L) 0.0 else mean
    /** Population standard deviation. */
    fun std(): Double = if (count == 0L) 0.0 else sqrt(m2 / count)
}
```

### Feature accumulation

```kotlin
class FeatureAccumulator {
    private val accelMag = RunningStats()       // Raw magnitude for standard deviation.
    private val accelDyn = RunningStats()       // abs(magnitude - gravity) for the mean.
    private val gyroMag = RunningStats()
    private val gpsAccuracy = RunningStats()
    private val speeds = ArrayList<Double>()    // Retain the small GPS speed list for p95.
    var distanceMeters = 0.0; private set
    var first: LocationSample? = null; private set   // Plain Kotlin sample type described below.
    var last: LocationSample? = null; private set

    fun onMotion(s: MotionSample) {
        val m = sqrt((s.x * s.x + s.y * s.y + s.z * s.z).toDouble())
        when (s.type) {
            Sensor.TYPE_ACCELEROMETER -> {
                accelMag.add(m)
                accelDyn.add(abs(m - SensorManager.GRAVITY_EARTH))
            }
            Sensor.TYPE_GYROSCOPE -> gyroMag.add(m)
        }
    }

    fun onLocation(loc: LocationSample) { /* Filter, accumulate distance and record speed as described above. */ }

    fun toFeatures(durationMillis: Long): SensorFeatures {
        val sorted = speeds.sorted()
        val p95 = if (sorted.isEmpty()) 0.0 else sorted[((sorted.size - 1) * 0.95).toInt()]
        return SensorFeatures(
            averageSpeedMps = if (durationMillis > 0) distanceMeters / (durationMillis / 1000.0) else 0.0,
            p95SpeedMps = p95,
            maxSpeedMps = sorted.lastOrNull() ?: 0.0,
            stopRatio = if (speeds.isEmpty()) 0.0 else speeds.count { it < 0.5 }.toDouble() / speeds.size,
            averageGpsAccuracyMeters = gpsAccuracy.mean(),
            gpsSampleCount = speeds.size,
            accelMagnitudeMean = accelDyn.mean(),
            accelMagnitudeStd = accelMag.std(),
            accelSampleCount = accelMag.count.toInt(),
            gyroMagnitudeMean = gyroMag.mean(),
            gyroMagnitudeStd = gyroMag.std(),
            gyroSampleCount = gyroMag.count.toInt(),
        )
    }
}
```

### Recording state and tracker

```kotlin
data class TrackingState(
    val isRecording: Boolean = false,
    val startTimeMillis: Long = 0L,
    val elapsedSeconds: Long = 0L,
    val distanceMeters: Double = 0.0,
    val lastAccuracyMeters: Float? = null,   // Null until the first location fix.
    val gpsCount: Int = 0,
    val accelCount: Long = 0,
    val gyroCount: Long = 0,
)

class JourneyTracker {
    private val _state = MutableStateFlow(TrackingState())
    val state: StateFlow<TrackingState> = _state.asStateFlow()
    private var acc = FeatureAccumulator()

    fun begin(now: Long) { acc = FeatureAccumulator(); _state.value = TrackingState(true, now) }
    fun onLocation(l: Location) { acc.onLocation(l.toSample()); publishThrottled() }
    fun onMotion(s: MotionSample) { acc.onMotion(s); publishThrottled() }   // Publish at most once per second.
    fun finish(now: Long): RecordingResult? { /* Build a result, reset to Idle; return null for insufficient data. */ }
}
```

### Journey construction

```kotlin
JourneySummary(
    journeyId = UUID.randomUUID().toString(),
    userId = authRepository.currentUserId ?: error("Not signed in"),
    startLocation = GeoPoint(first.lat, first.lon),
    endLocation = GeoPoint(last.lat, last.lon),
    startTimeMillis = start,
    endTimeMillis = end,
    distanceMeters = acc.distanceMeters,
    transportMode = TransportMode.UNKNOWN,      // Until the classifier is integrated, as described above.
    sensorFeatures = acc.toFeatures(end - start),
)
```

### Bounded server acknowledgement

```kotlin
viewModelScope.launch {
    _ui.update { it.copy(isSaving = true) }
    val summary = builder.build(result)
    // The local cache is written; wait at most 3 seconds for server acknowledgement, then continue.
    withTimeoutOrNull(3_000) { journeyRepository.saveJourney(summary) }
    _ui.update { it.copy(isSaving = false, savedJourneyId = summary.journeyId) }
}
```

### Navigation

```kotlin
// Add this route in NavGraph.
composable(TrackingRoutes.TRACKING) {
    TrackingRoute(
        viewModel = /* Construct with ViewModelFactory */,
        onJourneySaved = { id -> navController.navigate(Routes.journeyReview(id)) },
    )
}
// After login: navController.navigate(TrackingRoutes.TRACKING) { popUpTo(Routes.LOGIN) { inclusive = true } }
```

### Firestore mapping

```kotlin
// Write
"sensorFeatures" to journey.sensorFeatures?.let { f ->
    mapOf(
        "featureVersion" to f.featureVersion,
        "averageSpeedMps" to f.averageSpeedMps,
        // Other fields use the same property names.
    )
}

// Read: return null when the field is absent.
val sf = (data["sensorFeatures"] as? Map<*, *>)?.let { m ->
    SensorFeatures(
        featureVersion = (m["featureVersion"] as? Number)?.toInt() ?: 1,
        averageSpeedMps = (m["averageSpeedMps"] as Number).toDouble(),
        // Convert integer fields using (x as Number).toInt().
    )
}
```

## Copyable implementation prompt

Implement the EcoStep journey recorder (GPS, accelerometer, gyroscope and foreground service). Read this guide and SENSOR_DATA_FORMAT.md first.

Before editing, report git status and preserve existing changes without stashing/discarding them. Work on the original feature/zongcheng-auth branch, or if merged, create feature/zongcheng-tracking from current main. Fetch origin and identify incoming Yu-Han/Duo work; stop and report merge conflicts. Do not use reset --hard, clean or force-push.

Place new code in sensors/ and data/model/SensorFeatures.kt. Restrict shared edits to the manifest (three permissions and one service), the nullable JourneySummary field and mapper, AppContainer tracker, NavGraph tracking/login route, and dependencies only when required. Do not modify unrelated network/, data/cache/, algorithm/, evaluation/, ui/, core/theme/, Routes.kt or existing libs.versions.toml versions. Keep the implementation minimal: no map UI, background-location permission, Room or visual redesign. Process sensor callbacks off the main thread and expose StateFlow through a ViewModelFactory-created ViewModel.

Verify assembleDebug and the new JVM tests. The original baseline noted two Windows DataStore failures unrelated to this task; establish the actual current baseline instead of hiding failures. Provide emulator manual-test steps.

Final report: (1) branch/sync/conflict status, (2) each new file's purpose, (3) complete shared-file diff, (4) status and reason for each of the eight acceptance criteria, (5) build/test results, (6) remaining manual work and owner coordination, (7) a suggested commit message. Do not execute the commit.
