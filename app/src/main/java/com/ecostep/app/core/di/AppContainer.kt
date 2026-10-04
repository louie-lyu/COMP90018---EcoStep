package com.ecostep.app.core.di

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.ecostep.app.BuildConfig
import com.ecostep.app.algorithm.CarbonCalculator
import com.ecostep.app.algorithm.DefaultCarbonCalculator
import com.ecostep.app.algorithm.DefaultEcoPointsCalculator
import com.ecostep.app.algorithm.DefaultRecurringJourneyDetector
import com.ecostep.app.algorithm.EcoPointsCalculator
import com.ecostep.app.algorithm.RecurringJourneyDetector
import com.ecostep.app.algorithm.TransportEvidenceProvider
import com.ecostep.app.core.integration.MissionGenerationCoordinator
import com.ecostep.app.core.integration.MissionRouteEstimator
import com.ecostep.app.data.model.GeoPoint
import kotlinx.coroutines.flow.MutableStateFlow
import com.ecostep.app.core.integration.UserPreferenceCoordinator
import com.ecostep.app.core.notifications.AndroidMissionReminderScheduler
import com.ecostep.app.core.notifications.MissionReminderSync
import com.ecostep.app.sensors.autodetect.AutoJourneyDetectionCoordinator
import com.ecostep.app.sensors.autodetect.PlayServicesActivityUpdates
import com.ecostep.app.sensors.tracking.RecordingStarter
import com.ecostep.app.network.ai.AiModule
import com.ecostep.app.sensors.location.PlaceNameResolver
import com.ecostep.app.data.cache.publictransport.DataStorePublicTransportCache
import com.ecostep.app.data.cache.publictransport.PublicTransportCache
import com.ecostep.app.data.cache.publictransport.publicTransportDataStore
import com.ecostep.app.data.cache.route.DataStoreRouteCache
import com.ecostep.app.data.cache.route.RouteCache
import com.ecostep.app.data.cache.route.routeDataStore
import com.ecostep.app.data.cache.weather.DataStoreWeatherCache
import com.ecostep.app.data.cache.weather.WeatherCache
import com.ecostep.app.data.cache.weather.weatherDataStore
import com.ecostep.app.data.firebase.FirebaseAuthRepository
import com.ecostep.app.data.firebase.FirestoreJourneyRepository
import com.ecostep.app.data.repository.AuthRepository
import com.ecostep.app.data.repository.DefaultExternalDataRepository
import com.ecostep.app.data.repository.ExternalDataRepository
import com.ecostep.app.data.repository.JourneyRepository
import com.ecostep.app.network.publictransport.DefaultTransportEvidenceProvider
import com.ecostep.app.network.publictransport.TransitousClient
import com.ecostep.app.network.publictransport.TransitousPublicTransportDataSource
import com.ecostep.app.network.route.RouteAccessTokenProvider
import com.ecostep.app.network.route.RouteProxyClient
import com.ecostep.app.network.route.RouteProxyDataSource
import com.ecostep.app.network.weather.OpenMeteoApi
import com.ecostep.app.network.weather.OpenMeteoClient
import com.ecostep.app.network.weather.OpenMeteoWeatherDataSource
import com.ecostep.app.sensors.tracking.JourneyTracker
import com.ecostep.app.sensors.tracking.RecordingResult
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import android.util.Log
import com.ecostep.app.data.firebase.FirebaseCallableClient
import com.ecostep.app.data.firebase.FirestoreEcoPointsRepository
import com.ecostep.app.data.firebase.FirestoreFriendsRepository
import com.ecostep.app.data.firebase.FirestoreLeaderboardRepository
import com.ecostep.app.data.firebase.FirestoreProfileRepository
import com.ecostep.app.data.firebase.FirestoreRewardsRepository
import com.ecostep.app.data.repository.CloudFunctionsClient
import com.ecostep.app.data.repository.EcoPointsRepository
import com.ecostep.app.data.repository.FriendsRepository
import com.ecostep.app.data.repository.LeaderboardRepository
import com.ecostep.app.data.repository.ProfileRepository
import com.ecostep.app.data.repository.RewardsRepository
import com.ecostep.app.data.firebase.FirestoreMissionRepository
import com.ecostep.app.data.firebase.FirestoreMissionResultRepository
import com.ecostep.app.data.repository.MissionRepository
import com.ecostep.app.data.repository.MissionResultRepository
import com.ecostep.app.ui.adapters.RepositoryMissionStore
import com.ecostep.app.ui.adapters.RepositoryProfileDataSource
import com.ecostep.app.ui.adapters.RepositoryRewardsDataSource
import com.ecostep.app.ui.adapters.RepositoryWeeklyInsightDataSource
import com.ecostep.app.ui.adapters.toSeedMissions
import com.ecostep.app.ui.mock.MockMissionScreenDataSource
import com.ecostep.app.ui.mock.WeeklyInsightDataSource
import java.time.ZoneId
import com.ecostep.app.ui.mock.ProfileDataSource
import com.ecostep.app.ui.mock.RewardsDataSource
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.PersistentCacheSettings
import com.google.firebase.functions.FirebaseFunctions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor

/**
 * Manual dependency provisioning (no DI framework) — see docs/ARCHITECTURE.md for why.
 * One shared instance, held by [com.ecostep.app.EcoStepApp].
 */
class AppContainer(context: Context) {

    private val applicationContext = context.applicationContext

    val journeyTracker: JourneyTracker by lazy {
        JourneyTracker()
    }

    /** Shared carbon rules for Home route estimates, Review alternatives and missions. */
    val carbonCalculator: CarbonCalculator by lazy {
        DefaultCarbonCalculator()
    }

    val ecoPointsCalculator: EcoPointsCalculator by lazy {
        DefaultEcoPointsCalculator()
    }

    val recurringJourneyDetector: RecurringJourneyDetector by lazy {
        DefaultRecurringJourneyDetector()
    }

    /** One geocoder cache for Home search, Review place names and mission labels. */
    val placeNameResolver: PlaceNameResolver by lazy {
        PlaceNameResolver(applicationContext)
    }

    /** Latest device position seen by the map; biases place searches outside Home. */
    val lastKnownLocation = MutableStateFlow<GeoPoint?>(null)

    /** Recalculates mission CO₂ and EcoPoints when its start or destination is edited. */
    val missionRouteEstimator: MissionRouteEstimator by lazy {
        MissionRouteEstimator(
            externalDataRepository = externalDataRepository,
            locate = placeNameResolver::locate,
            carbonCalculator = carbonCalculator,
            ecoPointsCalculator = ecoPointsCalculator,
            searchCenter = { lastKnownLocation.value ?: MELBOURNE_CBD },
        )
    }

    fun transportEvidenceProvider(recording: RecordingResult): TransportEvidenceProvider =
        DefaultTransportEvidenceProvider(recording, transitousApi)

    /**
     * Shared HTTP client. Jianing builds per-API Retrofit instances (weather/route/public
     * transport/AI) on top of this, so every network client shares one connection pool and
     * one logging policy instead of each owner configuring OkHttp separately.
     */
    val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor(
                HttpLoggingInterceptor().apply {
                    redactQueryParams(
                        "latitude",
                        "longitude",
                        "fromPlace",
                        "toPlace",
                    )
                    level = if (BuildConfig.DEBUG) {
                        HttpLoggingInterceptor.Level.BASIC
                    } else {
                        HttpLoggingInterceptor.Level.NONE
                    }
                },
            )
            .build()
    }

    private val openMeteoApi: OpenMeteoApi by lazy {
        OpenMeteoClient.create(okHttpClient)
    }

    private val weatherDataSource: OpenMeteoWeatherDataSource by lazy {
        OpenMeteoWeatherDataSource(openMeteoApi)
    }

    private val weatherCache: WeatherCache by lazy {
        DataStoreWeatherCache(
            dataStore = applicationContext.weatherDataStore,
        )
    }

    private val routeCache: RouteCache by lazy {
        DataStoreRouteCache(
            dataStore = applicationContext.routeDataStore,
        )
    }

    private val publicTransportCache: PublicTransportCache by lazy {
        DataStorePublicTransportCache(
            dataStore = applicationContext.publicTransportDataStore,
        )
    }

    private val transitousApi by lazy {
        TransitousClient.create(
            okHttpClient = okHttpClient,
        )
    }

    private val publicTransportDataSource by lazy {
        TransitousPublicTransportDataSource(
            transitousApi = transitousApi,
        )
    }

    private val routeProxyApi by lazy {
        RouteProxyClient.create(
            okHttpClient = okHttpClient,
            baseUrl = BuildConfig.ROUTE_PROXY_BASE_URL,
        )
    }

    private val routeDataSource by lazy {
        RouteProxyDataSource(
            routeProxyApi = routeProxyApi,
            accessTokenProvider = RouteAccessTokenProvider {
                authRepository.getIdToken()
            },
        )
    }

    val externalDataRepository: ExternalDataRepository by lazy {
        DefaultExternalDataRepository(
            weatherDataSource = weatherDataSource,
            weatherCache = weatherCache,
            routeDataSource = routeDataSource,
            publicTransportDataSource = publicTransportDataSource,
            routeCache = routeCache,
            publicTransportCache = publicTransportCache,
        )
    }

    /** App-lifetime scope for fire-and-forget data work that must outlive a screen. */
    val applicationScope: CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val firebaseEmulatorHost: String? =
        BuildConfig.FIREBASE_EMULATOR_HOST.takeIf { it.isNotBlank() }

    private val firebaseAuth: FirebaseAuth by lazy {
        FirebaseAuth.getInstance().apply {
            firebaseEmulatorHost?.let { useEmulator(it, 9099) }
        }
    }

    /**
     * Offline persistence is the Android default; it is set explicitly because tracking,
     * review and history rely on reading pending writes from the local cache.
     */
    private val firestore: FirebaseFirestore by lazy {
        FirebaseFirestore.getInstance().apply {
            firebaseEmulatorHost?.let { useEmulator(it, 8080) }
            firestoreSettings = FirebaseFirestoreSettings.Builder()
                .setLocalCacheSettings(PersistentCacheSettings.newBuilder().build())
                .build()
        }
    }

    private val cloudFunctions: CloudFunctionsClient by lazy {
        FirebaseCallableClient(
            FirebaseFunctions.getInstance().apply {
                firebaseEmulatorHost?.let { useEmulator(it, 5001) }
            },
        )
    }

    val authRepository: AuthRepository by lazy {
        FirebaseAuthRepository(
            firebaseAuth = firebaseAuth,
        )
    }

    val journeyRepository: JourneyRepository by lazy {
        FirestoreJourneyRepository(
            authRepository = authRepository,
            firestore = firestore,
        )
    }

    val profileRepository: ProfileRepository by lazy {
        FirestoreProfileRepository(
            authRepository = authRepository,
            firestore = firestore,
        )
    }

    val ecoPointsRepository: EcoPointsRepository by lazy {
        FirestoreEcoPointsRepository(
            authRepository = authRepository,
            firestore = firestore,
        )
    }

    val friendsRepository: FriendsRepository by lazy {
        FirestoreFriendsRepository(
            authRepository = authRepository,
            firestore = firestore,
            functions = cloudFunctions,
        )
    }

    val leaderboardRepository: LeaderboardRepository by lazy {
        FirestoreLeaderboardRepository(
            authRepository = authRepository,
            firestore = firestore,
            friendsRepository = friendsRepository,
        )
    }

    val rewardsRepository: RewardsRepository by lazy {
        FirestoreRewardsRepository(
            authRepository = authRepository,
            firestore = firestore,
            functions = cloudFunctions,
        )
    }

    val missionRepository: MissionRepository by lazy {
        FirestoreMissionRepository(
            authRepository = authRepository,
            firestore = firestore,
        )
    }

    val missionResultRepository: MissionResultRepository by lazy {
        FirestoreMissionResultRepository(
            authRepository = authRepository,
            firestore = firestore,
        )
    }

    /** Shared by Home, Missions, Tracking (active mission) and Review (completion). */
    val missionStore: RepositoryMissionStore by lazy {
        RepositoryMissionStore(
            missionRepository = missionRepository,
            missionResultRepository = missionResultRepository,
            scope = applicationScope,
            seedMissions = {
                // Sample missions help manual testing; release builds start empty until
                // the mission generator suggests real ones.
                if (BuildConfig.DEBUG) {
                    MockMissionScreenDataSource().toSeedMissions(ZoneId.systemDefault().id)
                } else {
                    emptyList()
                }
            },
        )
    }

    /** Mission generator and weekly coach backed by the team's AI worker. */
    val aiModule: AiModule by lazy {
        AiModule(okHttpClient)
    }

    val missionGenerationCoordinator: MissionGenerationCoordinator by lazy {
        MissionGenerationCoordinator(
            authRepository = authRepository,
            profileRepository = profileRepository,
            journeyRepository = journeyRepository,
            missionRepository = missionRepository,
            externalDataRepository = externalDataRepository,
            recurringJourneyDetector = recurringJourneyDetector,
            carbonCalculator = carbonCalculator,
            ecoPointsCalculator = ecoPointsCalculator,
            missionGenerator = aiModule.missionGenerator,
            placeName = placeNameResolver::resolve,
            log = { message -> Log.i(TAG, message) },
        )
    }

    /** Same start path and preconditions as the Start button. */
    val recordingStarter: RecordingStarter by lazy {
        RecordingStarter(applicationContext, journeyTracker, authRepository)
    }

    val autoJourneyDetection: AutoJourneyDetectionCoordinator by lazy {
        AutoJourneyDetectionCoordinator(
            activityUpdates = PlayServicesActivityUpdates(applicationContext),
            isRecording = { journeyTracker.state.value.isRecording },
            startRecording = recordingStarter::start,
            log = { message -> Log.i(TAG, message) },
        )
    }

    private val missionReminderSync: MissionReminderSync by lazy {
        MissionReminderSync(AndroidMissionReminderScheduler(applicationContext))
    }

    val userPreferenceCoordinator: UserPreferenceCoordinator by lazy {
        UserPreferenceCoordinator(
            authRepository = authRepository,
            profileRepository = profileRepository,
            missionRepository = missionRepository,
            reminderSync = missionReminderSync,
            setAutomaticDetection = autoJourneyDetection::setEnabled,
            scope = applicationScope,
        )
    }

    fun rewardsDataSource(): RewardsDataSource =
        RepositoryRewardsDataSource(
            rewardsRepository = rewardsRepository,
            ecoPointsRepository = ecoPointsRepository,
        )

    fun weeklyInsightDataSource(): WeeklyInsightDataSource =
        RepositoryWeeklyInsightDataSource(missionResultRepository)

    fun profileDataSource(): ProfileDataSource =
        RepositoryProfileDataSource(
            authRepository = authRepository,
            profileRepository = profileRepository,
            ecoPointsRepository = ecoPointsRepository,
            friendsRepository = friendsRepository,
            leaderboardRepository = leaderboardRepository,
            journeyRepository = journeyRepository,
        )

    /**
     * Runs after every successful sign-in or sign-up (and on launch while signed in):
     * idempotently creates the user's profile document.
     */
    fun onSignedIn() {
        userPreferenceCoordinator.onSignedIn()
        applicationScope.launch {
            try {
                profileRepository.ensureProfile(
                    defaultDisplayName =
                        authRepository.currentUserEmail?.substringBefore('@').orEmpty(),
                )
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                // Usually offline; the next sign-in or launch retries. No user data is logged.
                Log.w(TAG, "Profile bootstrap deferred: ${exception.javaClass.simpleName}")
            }
        }
    }

    /** Call before signing out: removes reminders and detection for the leaving user. */
    fun onSigningOut() {
        userPreferenceCoordinator.onSignedOut()
    }

    private companion object {
        const val TAG = "AppContainer"

        /** Search area used before the device has reported a position. */
        val MELBOURNE_CBD = GeoPoint(latitude = -37.8136, longitude = 144.9631)
    }
}

class ViewModelFactory<T : ViewModel>(
    private val creator: () -> T,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <VM : ViewModel> create(
        modelClass: Class<VM>,
    ): VM = creator() as VM
}
