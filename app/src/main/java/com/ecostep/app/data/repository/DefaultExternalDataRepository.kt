package com.ecostep.app.data.repository

import com.ecostep.app.data.cache.publictransport.PublicTransportCache
import com.ecostep.app.data.cache.publictransport.PublicTransportCacheEntry
import com.ecostep.app.data.cache.publictransport.PublicTransportCacheKey
import com.ecostep.app.data.cache.publictransport.PublicTransportCachePolicy
import com.ecostep.app.data.cache.route.RouteCache
import com.ecostep.app.data.cache.route.RouteCacheEntry
import com.ecostep.app.data.cache.route.RouteCacheKey
import com.ecostep.app.data.cache.route.RouteCachePolicy
import com.ecostep.app.data.cache.weather.WeatherCache
import com.ecostep.app.data.cache.weather.WeatherCacheEntry
import com.ecostep.app.data.cache.weather.WeatherCachePolicy
import com.ecostep.app.data.cache.weather.WeatherLocationKey
import com.ecostep.app.data.model.GeoPoint
import com.ecostep.app.data.model.PublicTransportInfo
import com.ecostep.app.data.model.RouteInfo
import com.ecostep.app.data.model.WeatherData
import com.ecostep.app.network.publictransport.TransitousPublicTransportDataSource
import com.ecostep.app.network.route.OpenRouteServiceProfile
import com.ecostep.app.network.route.RouteProxyAuthenticationException
import com.ecostep.app.network.route.RouteProxyDataSource
import com.ecostep.app.network.route.RouteProxyServiceException
import com.ecostep.app.network.weather.OpenMeteoWeatherDataSource
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException
import retrofit2.HttpException

internal class DefaultExternalDataRepository(
    private val weatherDataSource: OpenMeteoWeatherDataSource,
    private val weatherCache: WeatherCache,
    private val routeDataSource: RouteProxyDataSource? = null,
    private val publicTransportDataSource: TransitousPublicTransportDataSource? = null,
    private val routeCache: RouteCache? = null,
    private val publicTransportCache: PublicTransportCache? = null,
    private val weatherCachePolicy: WeatherCachePolicy = WeatherCachePolicy(),
    private val routeCachePolicy: RouteCachePolicy = RouteCachePolicy(),
    private val publicTransportCachePolicy: PublicTransportCachePolicy =
        PublicTransportCachePolicy(),
    private val currentTimeMillis: () -> Long = System::currentTimeMillis,
) : ExternalDataRepository {

    override suspend fun getWeather(location: GeoPoint): WeatherData {
        val roundedLocation = WeatherLocationKey
            .from(location)
            .roundedLocation

        val cachedEntry = readWeatherCacheBestEffort(roundedLocation)
        val requestStartedAtMillis = currentTimeMillis()

        if (
            cachedEntry != null &&
            weatherCachePolicy.isFresh(
                entry = cachedEntry,
                currentTimeMillis = requestStartedAtMillis,
            )
        ) {
            return cachedEntry.weatherData
        }

        val networkWeather = try {
            weatherDataSource.getWeather(roundedLocation)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            val failureTimeMillis = currentTimeMillis()

            if (
                cachedEntry != null &&
                weatherCachePolicy.canUseAsOfflineFallback(
                    entry = cachedEntry,
                    currentTimeMillis = failureTimeMillis,
                )
            ) {
                return cachedEntry.weatherData
            }

            if (cachedEntry != null) {
                removeWeatherCacheBestEffort(roundedLocation)
            }

            throw error.toExternalDataException()
        }

        saveWeatherCacheBestEffort(
            location = roundedLocation,
            entry = WeatherCacheEntry(
                weatherData = networkWeather,
                fetchedAtMillis = currentTimeMillis(),
            ),
        )

        return networkWeather
    }

    override suspend fun getRouteOptions(
        start: GeoPoint,
        end: GeoPoint,
    ): List<RouteInfo> {
        val dataSource = routeDataSource
            ?: throw ExternalDataException(
                reason = ExternalDataException.Reason.SERVICE,
                message = "Route service is not configured.",
            )

        val cacheKey = RouteCacheKey.from(start, end)
        val cachedEntry = readRouteCacheBestEffort(
            start = cacheKey.roundedStart,
            end = cacheKey.roundedEnd,
        )
        val requestStartedAtMillis = currentTimeMillis()

        if (
            cachedEntry != null &&
            cachedEntry.routes.isNotEmpty() &&
            routeCachePolicy.isFresh(
                entry = cachedEntry,
                currentTimeMillis = requestStartedAtMillis,
            )
        ) {
            return cachedEntry.routes
        }

        val networkRoutes = try {
            OpenRouteServiceProfile.entries.map { profile ->
                dataSource.getRoute(
                    start = cacheKey.roundedStart,
                    end = cacheKey.roundedEnd,
                    profile = profile,
                )
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (authentication: RouteProxyAuthenticationException) {
            throw authentication.toExternalDataException()
        } catch (error: Exception) {
            val failureTimeMillis = currentTimeMillis()

            if (
                cachedEntry != null &&
                cachedEntry.routes.isNotEmpty() &&
                routeCachePolicy.canUseAsOfflineFallback(
                    entry = cachedEntry,
                    currentTimeMillis = failureTimeMillis,
                )
            ) {
                return cachedEntry.routes
            }

            if (cachedEntry != null) {
                removeRouteCacheBestEffort(
                    start = cacheKey.roundedStart,
                    end = cacheKey.roundedEnd,
                )
            }

            throw error.toExternalDataException()
        }

        saveRouteCacheBestEffort(
            start = cacheKey.roundedStart,
            end = cacheKey.roundedEnd,
            entry = RouteCacheEntry(
                routes = networkRoutes,
                fetchedAtMillis = currentTimeMillis(),
            ),
        )

        return networkRoutes
    }

    override suspend fun getPublicTransportOptions(
        start: GeoPoint,
        end: GeoPoint,
    ): List<PublicTransportInfo> {
        val dataSource = publicTransportDataSource
            ?: throw ExternalDataException(
                reason = ExternalDataException.Reason.SERVICE,
                message = "Public transport service is not configured.",
            )

        val cacheKey = PublicTransportCacheKey.from(start, end)
        val cachedEntry = readPublicTransportCacheBestEffort(
            start = cacheKey.roundedStart,
            end = cacheKey.roundedEnd,
        )
        val requestStartedAtMillis = currentTimeMillis()
        val futureCachedOptions = cachedEntry?.let { entry ->
            publicTransportCachePolicy.futureOptions(
                entry = entry,
                currentTimeMillis = requestStartedAtMillis,
            )
        }.orEmpty()

        if (
            cachedEntry != null &&
            publicTransportCachePolicy.isFresh(
                entry = cachedEntry,
                currentTimeMillis = requestStartedAtMillis,
            ) &&
            (cachedEntry.options.isEmpty() || futureCachedOptions.isNotEmpty())
        ) {
            return futureCachedOptions
        }

        val networkOptions = try {
            dataSource.getPublicTransportOptions(
                start = cacheKey.roundedStart,
                end = cacheKey.roundedEnd,
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            val failureTimeMillis = currentTimeMillis()
            val fallbackOptions = cachedEntry?.let { entry ->
                publicTransportCachePolicy.futureOptions(
                    entry = entry,
                    currentTimeMillis = failureTimeMillis,
                )
            }.orEmpty()

            if (
                cachedEntry != null &&
                fallbackOptions.isNotEmpty() &&
                publicTransportCachePolicy.canUseAsOfflineFallback(
                    entry = cachedEntry,
                    currentTimeMillis = failureTimeMillis,
                )
            ) {
                return fallbackOptions
            }

            if (cachedEntry != null) {
                removePublicTransportCacheBestEffort(
                    start = cacheKey.roundedStart,
                    end = cacheKey.roundedEnd,
                )
            }

            throw error.toExternalDataException()
        }

        savePublicTransportCacheBestEffort(
            start = cacheKey.roundedStart,
            end = cacheKey.roundedEnd,
            entry = PublicTransportCacheEntry(
                options = networkOptions,
                fetchedAtMillis = currentTimeMillis(),
            ),
        )

        return networkOptions
    }

    private suspend fun readWeatherCacheBestEffort(
        location: GeoPoint,
    ): WeatherCacheEntry? {
        return try {
            weatherCache.get(location)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: IOException) {
            null
        }
    }

    private suspend fun saveWeatherCacheBestEffort(
        location: GeoPoint,
        entry: WeatherCacheEntry,
    ) {
        try {
            weatherCache.save(location, entry)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: IOException) {
            // A successful network response remains usable if cache writing fails.
        }
    }

    private suspend fun removeWeatherCacheBestEffort(location: GeoPoint) {
        try {
            weatherCache.remove(location)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: IOException) {
            // An expired entry must not hide the original network failure.
        }
    }

    private suspend fun readRouteCacheBestEffort(
        start: GeoPoint,
        end: GeoPoint,
    ): RouteCacheEntry? {
        val cache = routeCache ?: return null
        return try {
            cache.get(start, end)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: IOException) {
            null
        }
    }

    private suspend fun saveRouteCacheBestEffort(
        start: GeoPoint,
        end: GeoPoint,
        entry: RouteCacheEntry,
    ) {
        val cache = routeCache ?: return
        try {
            cache.save(start, end, entry)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: IOException) {
            // A successful network response remains usable if cache writing fails.
        }
    }

    private suspend fun removeRouteCacheBestEffort(
        start: GeoPoint,
        end: GeoPoint,
    ) {
        val cache = routeCache ?: return
        try {
            cache.remove(start, end)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: IOException) {
            // An expired entry must not hide the original network failure.
        }
    }

    private suspend fun readPublicTransportCacheBestEffort(
        start: GeoPoint,
        end: GeoPoint,
    ): PublicTransportCacheEntry? {
        val cache = publicTransportCache ?: return null
        return try {
            cache.get(start, end)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: IOException) {
            null
        }
    }

    private suspend fun savePublicTransportCacheBestEffort(
        start: GeoPoint,
        end: GeoPoint,
        entry: PublicTransportCacheEntry,
    ) {
        val cache = publicTransportCache ?: return
        try {
            cache.save(start, end, entry)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: IOException) {
            // A successful network response remains usable if cache writing fails.
        }
    }

    private suspend fun removePublicTransportCacheBestEffort(
        start: GeoPoint,
        end: GeoPoint,
    ) {
        val cache = publicTransportCache ?: return
        try {
            cache.remove(start, end)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: IOException) {
            // An expired entry must not hide the original network failure.
        }
    }

    private fun Exception.toExternalDataException(): ExternalDataException {
        return when (this) {
            is ExternalDataException -> this

            is RouteProxyAuthenticationException,
            is RouteProxyServiceException -> ExternalDataException(
                reason = ExternalDataException.Reason.SERVICE,
                cause = this,
            )

            is IOException -> ExternalDataException(
                reason = ExternalDataException.Reason.NETWORK,
                cause = this,
            )

            is HttpException -> ExternalDataException(
                reason = ExternalDataException.Reason.SERVICE,
                cause = this,
            )

            is SerializationException -> ExternalDataException(
                reason = ExternalDataException.Reason.INVALID_RESPONSE,
                cause = this,
            )

            else -> ExternalDataException(
                reason = ExternalDataException.Reason.UNKNOWN,
                cause = this,
            )
        }
    }
}
