package com.ecostep.app.core.di

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.ecostep.app.BuildConfig
import com.ecostep.app.data.repository.DefaultExternalDataRepository
import com.ecostep.app.data.repository.ExternalDataRepository
import com.ecostep.app.network.weather.OpenMeteoApi
import com.ecostep.app.network.weather.OpenMeteoClient
import com.ecostep.app.network.weather.OpenMeteoWeatherDataSource
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor

/**
 * Manual dependency provisioning (no DI framework) — see docs/ARCHITECTURE.md for why.
 * One shared instance, held by [com.ecostep.app.EcoStepApp].
 */
class AppContainer {

    /**
     * Shared HTTP client. Jianing builds per-API Retrofit instances (weather/route/public
     * transport/AI) on top of this, so every network client shares one connection pool and
     * one logging policy instead of each owner configuring OkHttp separately.
     */
    val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor(
                HttpLoggingInterceptor().apply {
                    // Never log request/response details in release builds.
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

    val externalDataRepository: ExternalDataRepository by lazy {
        DefaultExternalDataRepository(
            weatherDataSource = weatherDataSource,
        )
    }

    // TODO(Zongcheng): add `val journeyRepository: JourneyRepository` here once implemented,
    // backed by Firebase Auth + Firestore (usable once app/google-services.json is in place).
}

/**
 * Generic ViewModel factory so every screen constructs its ViewModel the same documented way:
 *
 * ```
 * val viewModel: HomeViewModel = viewModel(
 *     factory = ViewModelFactory { HomeViewModel(appContainer.journeyRepository) },
 * )
 * ```
 */
class ViewModelFactory<T : ViewModel>(private val creator: () -> T) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <VM : ViewModel> create(modelClass: Class<VM>): VM = creator() as VM
}
