package com.ecostep.app.data.repository

import com.ecostep.app.data.cache.weather.WeatherCache
import com.ecostep.app.data.cache.weather.WeatherCacheEntry
import com.ecostep.app.data.cache.weather.WeatherCachePolicy
import com.ecostep.app.data.cache.weather.WeatherLocationKey
import com.ecostep.app.data.model.GeoPoint
import com.ecostep.app.data.model.PublicTransportInfo
import com.ecostep.app.data.model.RouteInfo
import com.ecostep.app.data.model.WeatherData
import com.ecostep.app.network.weather.OpenMeteoWeatherDataSource
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException
import retrofit2.HttpException

internal class DefaultExternalDataRepository(
    private val weatherDataSource: OpenMeteoWeatherDataSource,
    private val weatherCache: WeatherCache,
    private val weatherCachePolicy: WeatherCachePolicy = WeatherCachePolicy(),
    private val currentTimeMillis: () -> Long = System::currentTimeMillis,
) : ExternalDataRepository {

    override suspend fun getWeather(location: GeoPoint): WeatherData {
        val roundedLocation = WeatherLocationKey
            .from(location)
            .roundedLocation

        val cachedEntry = readCacheBestEffort(roundedLocation)
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
                removeCacheBestEffort(roundedLocation)
            }

            throw error.toExternalDataException()
        }

        saveCacheBestEffort(
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
        throw UnsupportedOperationException(
            "Route API is not configured yet",
        )
    }

    override suspend fun getPublicTransportOptions(
        start: GeoPoint,
        end: GeoPoint,
    ): List<PublicTransportInfo> {
        throw UnsupportedOperationException(
            "Public transport API is not configured yet",
        )
    }

    private suspend fun readCacheBestEffort(
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

    private suspend fun saveCacheBestEffort(
        location: GeoPoint,
        entry: WeatherCacheEntry,
    ) {
        try {
            weatherCache.save(
                location = location,
                entry = entry,
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: IOException) {
            // A successful network response remains usable if cache writing fails.
        }
    }

    private suspend fun removeCacheBestEffort(
        location: GeoPoint,
    ) {
        try {
            weatherCache.remove(location)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: IOException) {
            // An expired cache entry must not hide the original network failure.
        }
    }

    private fun Exception.toExternalDataException(): ExternalDataException {
        return when (this) {
            is ExternalDataException -> this

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
