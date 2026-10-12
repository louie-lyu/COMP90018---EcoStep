package com.ecostep.app.firebase

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.test.platform.app.InstrumentationRegistry
import com.ecostep.app.data.cache.publictransport.DataStorePublicTransportCache
import com.ecostep.app.data.cache.publictransport.PublicTransportCacheEntry
import com.ecostep.app.data.cache.publictransport.PublicTransportCacheKey
import com.ecostep.app.data.cache.route.DataStoreRouteCache
import com.ecostep.app.data.cache.route.RouteCacheEntry
import com.ecostep.app.data.cache.route.RouteCacheKey
import com.ecostep.app.data.cache.weather.DataStoreWeatherCache
import com.ecostep.app.data.cache.weather.WeatherCacheEntry
import com.ecostep.app.data.cache.weather.WeatherLocationKey
import com.ecostep.app.data.firebase.FirebaseAuthRepository
import com.ecostep.app.data.firebase.FirestoreJourneyRepository
import com.ecostep.app.data.firebase.FirestoreMissionRepository
import com.ecostep.app.data.firebase.FirestoreProfileRepository
import com.ecostep.app.data.model.*
import com.ecostep.app.data.repository.WriteOutcome
import com.google.android.gms.tasks.Tasks
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.PersistentCacheSettings
import com.google.firebase.firestore.Source
import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Run prepare, force-stop the app, then read, force-stop again, then recover.
 * Uses a named FirebaseApp and dedicated local stores; never accesses a live project.
 */
class OfflinePersistenceProbeTest {
    @Test fun persistedWritesAcrossProcessRestart() = runBlocking {
        val args = InstrumentationRegistry.getArguments()
        val host = args.getString("firebaseEmulatorHost")
        val stage = args.getString("offlineProbeStage")
        assumeTrue("Explicit local emulator and probe stage required", !host.isNullOrBlank() && stage != null)
        require(stage in setOf("prepare", "read", "recover"))
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val state = context.getSharedPreferences("offline-probe-state", 0)
        val app = FirebaseApp.initializeApp(context, FirebaseOptions.Builder()
            .setProjectId("demo-ecostep").setApplicationId("1:000000000000:android:0000000000000000")
            .setApiKey("demo-api-key").build(), "offline-persistence-probe")
        val firebaseAuth = FirebaseAuth.getInstance(app).apply { useEmulator(host!!, 9099) }
        val auth = FirebaseAuthRepository(firebaseAuth)
        val db = FirebaseFirestore.getInstance(app).apply {
            useEmulator(host!!, 8080)
            firestoreSettings = FirebaseFirestoreSettings.Builder()
                .setLocalCacheSettings(PersistentCacheSettings.newBuilder().build()).build()
        }
        val journeys = FirestoreJourneyRepository(auth, db, 300)
        val missions = FirestoreMissionRepository(auth, db, 300)
        val profiles = FirestoreProfileRepository(auth, db, 300)
        val storeJob = SupervisorJob()
        val storeScope = CoroutineScope(storeJob + Dispatchers.IO)
        fun store(name: String) = PreferenceDataStoreFactory.create(scope = storeScope,
            produceFile = { File(context.filesDir, "offline-probe-$name.preferences_pb") })
        val weatherStore = store("weather")
        val routeStore = store("route")
        val transitStore = store("transit")
        val weather = DataStoreWeatherCache(weatherStore)
        val routes = DataStoreRouteCache(routeStore)
        val transit = DataStorePublicTransportCache(transitStore)
        val start = GeoPoint(-37.81, 144.96)
        val end = GeoPoint(-37.82, 144.97)
        val weatherEntry = WeatherCacheEntry(WeatherData(18.0, "Clear"), 1_800_000_000_000L)
        val routeEntry = RouteCacheEntry(listOf(RouteInfo(TransportMode.WALKING, 2000.0, 1200)), 1_800_000_000_000L)
        val transitEntry = PublicTransportCacheEntry(listOf(PublicTransportInfo("Tram 19", 1_800_000_600_000L, 1200)), 1_800_000_000_000L)
        try {
            withTimeout(45_000) {
                if (stage == "prepare") {
                    val id = "probe-${UUID.randomUUID()}"
                    auth.signUp("$id@example.com", "offline-probe-123")
                    assertTrue(state.edit().putString("uid", auth.currentUserId).putString("id", id).commit())
                    profiles.ensureProfile("Offline Probe")
                    Tasks.await(db.disableNetwork(), 10, TimeUnit.SECONDS)
                    val journey = JourneySummary(journeyId = id, userId = auth.currentUserId!!,
                        startLocation = start, endLocation = end, startTimeMillis = 1_800_000_000_000L,
                        endTimeMillis = 1_800_000_120_000L, distanceMeters = 2000.0,
                        transportMode = TransportMode.WALKING)
                    assertEquals(WriteOutcome.QUEUED, journeys.createJourney(journey))
                    assertEquals(WriteOutcome.QUEUED, journeys.confirmTransportMode(id, TransportMode.CYCLING))
                    assertEquals(WriteOutcome.QUEUED, missions.createMission(Mission(id, "Offline mission")))
                    assertEquals(WriteOutcome.QUEUED, profiles.updatePreference(PreferenceUpdate.RoutineLearning(false)))
                    weather.save(start, weatherEntry)
                    routes.save(start, end, routeEntry)
                    transit.save(start, end, transitEntry)
                    assertTrue(db.collection("users").document(auth.currentUserId!!).collection("journeys")
                        .document(id).get(Source.CACHE).let { Tasks.await(it, 10, TimeUnit.SECONDS) }.metadata.hasPendingWrites())
                } else {
                    // Disable before any reads; the read stage must not sync prior to verification.
                    Tasks.await(db.disableNetwork(), 10, TimeUnit.SECONDS)
                    val uid = state.getString("uid", null)!!
                    val id = state.getString("id", null)!!
                    assertEquals("Firebase Auth session survives a process restart", uid, auth.currentUserId)
                    val pending = withTimeout(10_000) { journeys.observeJourney(id).first { it != null } }!!
                    assertTrue("Queued write must survive a process restart", pending.hasPendingWrites)
                    assertEquals(TransportMode.CYCLING, pending.journey.confirmedTransportMode)
                    assertEquals(id, journeys.getJourney(id)?.journeyId)
                    assertTrue(withTimeout(10_000) { missions.observeMissions().first { it.any { mission -> mission.missionId == id } } }.any { it.title == "Offline mission" })
                    assertFalse(profiles.getProfile()!!.preferences.routineLearningEnabled)
                    assertEquals(weatherEntry, weather.get(start))
                    assertEquals(routeEntry, routes.get(start, end))
                    assertEquals(transitEntry, transit.get(start, end))
                    if (stage == "recover") {
                        Tasks.await(db.enableNetwork(), 10, TimeUnit.SECONDS)
                        Tasks.await(db.waitForPendingWrites(), 20, TimeUnit.SECONDS)
                        val user = db.collection("users").document(uid)
                        val serverJourney = Tasks.await(user.collection("journeys").document(id).get(Source.SERVER), 10, TimeUnit.SECONDS)
                        assertFalse(serverJourney.metadata.hasPendingWrites())
                        assertEquals("CYCLING", serverJourney.getString("confirmedTransportMode"))
                        assertTrue(Tasks.await(user.collection("missions").document(id).get(Source.SERVER), 10, TimeUnit.SECONDS).exists())
                        assertEquals(false, Tasks.await(user.get(Source.SERVER), 10, TimeUnit.SECONDS).getBoolean("preferences.routineLearningEnabled"))
                        weather.remove(start)
                        routes.remove(start, end)
                        transit.remove(start, end)
                        assertNull(weather.get(start))
                        assertNull(routes.get(start, end))
                        assertNull(transit.get(start, end))
                        val weatherKey = stringPreferencesKey(WeatherLocationKey.from(start).value)
                        val routeKey = stringPreferencesKey(RouteCacheKey.from(start, end).value)
                        val transitKey = stringPreferencesKey(PublicTransportCacheKey.from(start, end).value)
                        weatherStore.edit { it[weatherKey] = "{broken-json" }
                        routeStore.edit { it[routeKey] = "{broken-json" }
                        transitStore.edit { it[transitKey] = "{broken-json" }
                        assertNull(weather.get(start))
                        assertNull(routes.get(start, end))
                        assertNull(transit.get(start, end))
                        assertNull(weatherStore.data.first()[weatherKey])
                        assertNull(routeStore.data.first()[routeKey])
                        assertNull(transitStore.data.first()[transitKey])
                        Tasks.await(firebaseAuth.currentUser!!.delete(), 10, TimeUnit.SECONDS)
                        state.edit().clear().commit()
                    }
                }
            }
        } finally {
            storeJob.cancelAndJoin()
        }
    }
}
