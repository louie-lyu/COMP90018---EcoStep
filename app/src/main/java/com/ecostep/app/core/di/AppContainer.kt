package com.ecostep.app.core.di

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.ecostep.app.BuildConfig
import com.ecostep.app.algorithm.TransportEvidenceProvider
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

    val authRepository: AuthRepository by lazy {
        FirebaseAuthRepository(
            firebaseAuth = FirebaseAuth.getInstance(),
        )
    }

    val journeyRepository: JourneyRepository by lazy {
        FirestoreJourneyRepository(
            authRepository = authRepository,
            firestore = FirebaseFirestore.getInstance(),
        )
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
