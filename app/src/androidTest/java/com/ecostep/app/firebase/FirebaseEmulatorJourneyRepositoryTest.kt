package com.ecostep.app.firebase

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ecostep.app.data.firebase.FirebaseAuthRepository
import com.ecostep.app.data.firebase.FirestoreJourneyRepository
import com.ecostep.app.data.firebase.FirestoreProfileRepository
import com.ecostep.app.data.model.GeoPoint
import com.ecostep.app.data.model.JourneyConfirmationStatus
import com.ecostep.app.data.model.JourneySummary
import com.ecostep.app.data.model.PreferenceUpdate
import com.ecostep.app.data.model.TransportMode
import com.ecostep.app.data.repository.WriteOutcome
import com.google.android.gms.tasks.Tasks
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.PersistentCacheSettings
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Production Firestore repositories against the local Firebase Emulator Suite (project
 * demo-ecostep), never a real project. Skipped unless the emulator host is passed. API 37
 * blocks app connections to the host LAN address 10.0.2.2, so tunnel the ports over adb:
 *
 *   adb reverse tcp:9099 tcp:9099 && adb reverse tcp:8080 tcp:8080
 *   ./gradlew connectedDebugAndroidTest \
 *     -Pandroid.testInstrumentationRunnerArguments.class=com.ecostep.app.firebase.FirebaseEmulatorJourneyRepositoryTest \
 *     -Pandroid.testInstrumentationRunnerArguments.firebaseEmulatorHost=127.0.0.1
 */
@RunWith(AndroidJUnit4::class)
class FirebaseEmulatorJourneyRepositoryTest {

    private val auth: FirebaseAuth get() = Emulator.auth
    private val firestore: FirebaseFirestore get() = Emulator.firestore

    /**
     * One FirebaseApp for the whole class: Firebase Auth cannot be reused after its app is
     * deleted, and emulator settings must be applied before first use.
     */
    private object Emulator {
        lateinit var host: String
        val app: FirebaseApp by lazy {
            FirebaseApp.initializeApp(
                InstrumentationRegistry.getInstrumentation().targetContext,
                FirebaseOptions.Builder()
                    .setProjectId("demo-ecostep")
                    .setApplicationId("1:000000000000:android:0000000000000000")
                    .setApiKey("demo-api-key")
                    .build(),
                "firebase-emulator-tests",
            )
        }
        val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance(app).apply { useEmulator(host, 9099) } }
        val firestore: FirebaseFirestore by lazy {
            FirebaseFirestore.getInstance(app).apply {
                useEmulator(host, 8080)
                firestoreSettings = FirebaseFirestoreSettings.Builder()
                    .setLocalCacheSettings(PersistentCacheSettings.newBuilder().build())
                    .build()
            }
        }
    }

    @Before
    fun setUp() {
        val host = InstrumentationRegistry.getArguments().getString("firebaseEmulatorHost")
        assumeTrue("Firebase emulator host not provided; skipping.", !host.isNullOrBlank())
        Emulator.host = host!!
        assertEmulatorReachable(host)
    }

    @After
    fun tearDown() {
        if (::emulatorChecked.isInitialized) {
            Tasks.await(firestore.enableNetwork(), 10, TimeUnit.SECONDS)
            auth.signOut()
        }
    }

    private lateinit var emulatorChecked: String

    /** Fails fast with a clear message when the device cannot reach the emulator. */
    private fun assertEmulatorReachable(host: String) {
        val connection = java.net.URL("http://$host:9099/emulator/v1/projects/demo-ecostep/config")
            .openConnection() as java.net.HttpURLConnection
        connection.connectTimeout = 5_000
        connection.readTimeout = 5_000
        try {
            assertEquals(200, connection.responseCode)
        } finally {
            connection.disconnect()
        }
        emulatorChecked = host
    }

    private fun signUp(): FirebaseAuthRepository {
        val repository = FirebaseAuthRepository(auth)
        runBlocking { repository.signUp("android-${UUID.randomUUID()}@example.com", "test-password-123") }
        return repository
    }

    private fun journey(uid: String, id: String) = JourneySummary(
        journeyId = id,
        userId = uid,
        startLocation = GeoPoint(-37.80, 144.96),
        endLocation = GeoPoint(-37.81, 144.97),
        startTimeMillis = System.currentTimeMillis() - 1_260_000,
        endTimeMillis = System.currentTimeMillis() - 60_000,
        distanceMeters = 2_000.0,
        transportMode = TransportMode.PUBLIC_TRANSPORT,
        detectedTransportMode = TransportMode.PUBLIC_TRANSPORT,
    )

    @Test
    fun createReadConfirmAndObserve() = runBlocking {
        val authRepository = signUp()
        val uid = authRepository.currentUserId!!
        val repository = FirestoreJourneyRepository(authRepository, firestore, ackTimeoutMillis = 10_000)

        assertEquals(WriteOutcome.SYNCED, repository.createJourney(journey(uid, "j1")))
        val loaded = repository.getJourney("j1")!!
        assertEquals(TransportMode.PUBLIC_TRANSPORT, loaded.detectedTransportMode)
        assertEquals(JourneyConfirmationStatus.PENDING, loaded.confirmationStatus)
        assertNotNull(loaded.createdAtMillis)

        assertEquals(WriteOutcome.SYNCED, repository.confirmTransportMode("j1", TransportMode.WALKING))
        // Confirming again is an idempotent update.
        assertEquals(WriteOutcome.SYNCED, repository.confirmTransportMode("j1", TransportMode.WALKING))

        val snapshot = withTimeout(10_000) {
            repository.observeJourney("j1").first { it?.journey?.confirmationStatus == JourneyConfirmationStatus.CONFIRMED }
        }!!
        assertEquals(TransportMode.WALKING, snapshot.journey.confirmedTransportMode)
        assertEquals(TransportMode.PUBLIC_TRANSPORT, snapshot.journey.detectedTransportMode)
        assertEquals(loaded.createdAtMillis, snapshot.journey.createdAtMillis)

        val history = withTimeout(10_000) { repository.observeJourneyHistory(uid).first { it.isNotEmpty() } }
        assertEquals(listOf("j1"), history.map { it.journeyId })
    }

    @Test
    fun offlineCreateIsQueuedReadableAndSyncsLater() = runBlocking {
        val authRepository = signUp()
        val uid = authRepository.currentUserId!!
        val repository = FirestoreJourneyRepository(authRepository, firestore, ackTimeoutMillis = 1_000)

        Tasks.await(firestore.disableNetwork(), 10, TimeUnit.SECONDS)
        assertEquals(WriteOutcome.QUEUED, repository.createJourney(journey(uid, "offline-1")))

        // Review can read the pending document straight away.
        val pending = repository.observeJourney("offline-1").first { it != null }!!
        assertTrue(pending.hasPendingWrites)
        assertEquals(null, pending.journey.createdAtMillis)
        assertEquals(WriteOutcome.QUEUED, repository.confirmTransportMode("offline-1", TransportMode.CYCLING))

        Tasks.await(firestore.enableNetwork(), 10, TimeUnit.SECONDS)
        val synced = withTimeout(15_000) {
            repository.observeJourney("offline-1").first { it != null && !it.hasPendingWrites }
        }!!
        assertFalse(synced.hasPendingWrites)
        assertEquals(TransportMode.CYCLING, synced.journey.confirmedTransportMode)
        assertNotNull(synced.journey.createdAtMillis)
    }

    @Test
    fun securityRulesBlockOverwritesAndOtherUsers() = runBlocking {
        val first = signUp()
        val firstUid = first.currentUserId!!
        val repository = FirestoreJourneyRepository(first, firestore, ackTimeoutMillis = 10_000)
        repository.createJourney(journey(firstUid, "j1"))

        // A second create with the same ID would overwrite: the rules reject it.
        try {
            repository.createJourney(journey(firstUid, "j1"))
            fail("Overwriting an existing journey must be denied")
        } catch (exception: FirebaseFirestoreException) {
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, exception.code)
        }

        auth.signOut()
        signUp()
        try {
            Tasks.await(
                firestore.collection("users").document(firstUid).collection("journeys").document("j1").get(
                    com.google.firebase.firestore.Source.SERVER,
                ),
                10,
                TimeUnit.SECONDS,
            )
            fail("Reading another user's journey must be denied")
        } catch (exception: java.util.concurrent.ExecutionException) {
            assertEquals(
                FirebaseFirestoreException.Code.PERMISSION_DENIED,
                (exception.cause as FirebaseFirestoreException).code,
            )
        }
    }

    @Test
    fun profileIsCreatedOnceAndPreferencesPersist() = runBlocking {
        val authRepository = signUp()
        val profiles = FirestoreProfileRepository(authRepository, firestore, ackTimeoutMillis = 10_000)

        assertTrue(profiles.ensureProfile("Ada"))
        profiles.updatePreference(PreferenceUpdate.CommunityRanking(false))
        // A second bootstrap (another device, a later login) must not reset preferences.
        assertFalse(profiles.ensureProfile("Someone Else"))

        val profile = profiles.getProfile()!!
        assertEquals("Ada", profile.displayName)
        assertFalse(profile.preferences.communityRankingEnabled)
    }
}
