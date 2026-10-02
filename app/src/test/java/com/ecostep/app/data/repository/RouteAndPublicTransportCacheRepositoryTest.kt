package com.ecostep.app.data.repository

import com.ecostep.app.data.cache.publictransport.PublicTransportCache
import com.ecostep.app.data.cache.publictransport.PublicTransportCacheEntry
import com.ecostep.app.data.cache.publictransport.PublicTransportCachePolicy
import com.ecostep.app.data.cache.route.RouteCache
import com.ecostep.app.data.cache.route.RouteCacheEntry
import com.ecostep.app.data.cache.route.RouteCachePolicy
import com.ecostep.app.data.cache.weather.WeatherCache
import com.ecostep.app.data.cache.weather.WeatherCacheEntry
import com.ecostep.app.data.model.GeoPoint
import com.ecostep.app.data.model.PublicTransportInfo
import com.ecostep.app.data.model.RouteInfo
import com.ecostep.app.data.model.TransportMode
import com.ecostep.app.network.publictransport.TransitousApi
import com.ecostep.app.network.publictransport.TransitousItinerary
import com.ecostep.app.network.publictransport.TransitousLeg
import com.ecostep.app.network.publictransport.TransitousPublicTransportDataSource
import com.ecostep.app.network.publictransport.TransitousResponse
import com.ecostep.app.network.route.OpenRouteServiceResponse
import com.ecostep.app.network.route.OpenRouteServiceRoute
import com.ecostep.app.network.route.OpenRouteServiceRouteSummary
import com.ecostep.app.network.route.RouteAccessTokenProvider
import com.ecostep.app.network.route.RouteProxyApi
import com.ecostep.app.network.route.RouteProxyDataSource
import com.ecostep.app.network.route.RouteProxyRequest
import com.ecostep.app.network.weather.OpenMeteoApi
import com.ecostep.app.network.weather.OpenMeteoCurrentWeather
import com.ecostep.app.network.weather.OpenMeteoResponse
import com.ecostep.app.network.weather.OpenMeteoWeatherDataSource
import java.io.IOException
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class RouteAndPublicTransportCacheRepositoryTest {
    private val start = GeoPoint(-37.81364, 144.96314)
    private val end = GeoPoint(-37.79634, 144.96144)
    private val now = 1_800_000_000_000L

    @Test
    fun `fresh route cache avoids network`() = runTest {
        val cachedRoutes = sampleRoutes()
        val cache = FakeRouteCache(
            entry = RouteCacheEntry(
                routes = cachedRoutes,
                fetchedAtMillis = now - 60_000L,
            ),
        )
        val routeApi = FakeRouteProxyApi()
        val repository = repository(routeApi = routeApi, routeCache = cache)

        assertEquals(cachedRoutes, repository.getRouteOptions(start, end))
        assertEquals(0, routeApi.requestCount)
    }

    @Test
    fun `route network response is saved`() = runTest {
        val cache = FakeRouteCache()
        val routeApi = FakeRouteProxyApi()
        val repository = repository(routeApi = routeApi, routeCache = cache)

        val routes = repository.getRouteOptions(start, end)

        assertEquals(3, routes.size)
        assertEquals(3, routeApi.requestCount)
        assertEquals(routes, requireNotNull(cache.entry).routes)
        assertEquals(now, requireNotNull(cache.entry).fetchedAtMillis)
        assertEquals(GeoPoint(-37.8136, 144.9631), cache.savedStart)
        assertEquals(GeoPoint(-37.7963, 144.9614), cache.savedEnd)
    }

    @Test
    fun `route network failure returns offline cache`() = runTest {
        val cachedRoutes = sampleRoutes()
        val failure = IOException("Route network unavailable")
        val cache = FakeRouteCache(
            entry = RouteCacheEntry(
                routes = cachedRoutes,
                fetchedAtMillis = now -
                    RouteCachePolicy.DEFAULT_FRESH_MAX_AGE_MILLIS - 1L,
            ),
        )
        val repository = repository(
            routeApi = FakeRouteProxyApi(failure),
            routeCache = cache,
        )

        assertEquals(cachedRoutes, repository.getRouteOptions(start, end))
        assertNull(cache.removedStart)
    }

    @Test
    fun `expired route cache is removed and network error is exposed`() = runTest {
        val failure = IOException("Route network unavailable")
        val cache = FakeRouteCache(
            entry = RouteCacheEntry(
                routes = sampleRoutes(),
                fetchedAtMillis = now -
                    RouteCachePolicy.DEFAULT_OFFLINE_MAX_AGE_MILLIS - 1L,
            ),
        )
        val repository = repository(
            routeApi = FakeRouteProxyApi(failure),
            routeCache = cache,
        )

        val actual = captureExternalDataException {
            repository.getRouteOptions(start, end)
        }

        assertEquals(ExternalDataException.Reason.NETWORK, actual.reason)
        assertSame(failure, actual.cause)
        assertEquals(GeoPoint(-37.8136, 144.9631), cache.removedStart)
    }

    @Test
    fun `route authentication failure is not hidden by cache`() = runTest {
        val cache = FakeRouteCache(
            entry = RouteCacheEntry(
                routes = sampleRoutes(),
                fetchedAtMillis = now -
                    RouteCachePolicy.DEFAULT_FRESH_MAX_AGE_MILLIS - 1L,
            ),
        )
        val repository = repository(
            routeApi = FakeRouteProxyApi(),
            routeCache = cache,
            routeToken = null,
        )

        val actual = captureExternalDataException {
            repository.getRouteOptions(start, end)
        }

        assertEquals(ExternalDataException.Reason.SERVICE, actual.reason)
    }

    @Test
    fun `route cache read failure does not block network`() = runTest {
        val routeApi = FakeRouteProxyApi()
        val repository = repository(
            routeApi = routeApi,
            routeCache = FakeRouteCache(
                readFailure = IOException("Cache unavailable"),
            ),
        )

        assertEquals(3, repository.getRouteOptions(start, end).size)
        assertEquals(3, routeApi.requestCount)
    }

    @Test
    fun `fresh public transport cache avoids network and removes departed options`() = runTest {
        val cache = FakePublicTransportCache(
            entry = PublicTransportCacheEntry(
                options = listOf(
                    publicTransportOption(now - 1L, "Departed"),
                    publicTransportOption(now + 60_000L, "Tram 19"),
                ),
                fetchedAtMillis = now - 30_000L,
            ),
        )
        val transitousApi = FakeTransitousApi()
        val repository = repository(
            transitousApi = transitousApi,
            publicTransportCache = cache,
        )

        val options = repository.getPublicTransportOptions(start, end)

        assertEquals(listOf("Tram 19"), options.map { it.line })
        assertEquals(0, transitousApi.requestCount)
    }

    @Test
    fun `public transport network response is saved`() = runTest {
        val cache = FakePublicTransportCache()
        val transitousApi = FakeTransitousApi(
            response = transitousResponse(now + 60_000L),
        )
        val repository = repository(
            transitousApi = transitousApi,
            publicTransportCache = cache,
        )

        val options = repository.getPublicTransportOptions(start, end)

        assertEquals(1, options.size)
        assertEquals(1, transitousApi.requestCount)
        assertEquals(options, requireNotNull(cache.entry).options)
        assertEquals(now, requireNotNull(cache.entry).fetchedAtMillis)
    }

    @Test
    fun `public transport network failure returns only future fallback options`() = runTest {
        val failure = IOException("Transitous unavailable")
        val cache = FakePublicTransportCache(
            entry = PublicTransportCacheEntry(
                options = listOf(
                    publicTransportOption(now - 1L, "Departed"),
                    publicTransportOption(now + 60_000L, "Train Upfield"),
                ),
                fetchedAtMillis = now -
                    PublicTransportCachePolicy.DEFAULT_FRESH_MAX_AGE_MILLIS - 1L,
            ),
        )
        val repository = repository(
            transitousApi = FakeTransitousApi(failure = failure),
            publicTransportCache = cache,
        )

        val options = repository.getPublicTransportOptions(start, end)

        assertEquals(listOf("Train Upfield"), options.map { it.line })
    }

    @Test
    fun `public transport network failure returns legitimately empty offline cache`() = runTest {
        val failure = IOException("Transitous unavailable")
        val cache = FakePublicTransportCache(
            entry = PublicTransportCacheEntry(
                options = emptyList(),
                fetchedAtMillis = now -
                        PublicTransportCachePolicy.DEFAULT_FRESH_MAX_AGE_MILLIS - 1L,
            ),
        )
        val transitousApi = FakeTransitousApi(failure = failure)
        val repository = repository(
            transitousApi = transitousApi,
            publicTransportCache = cache,
        )

        val options = repository.getPublicTransportOptions(start, end)

        assertEquals(emptyList<PublicTransportInfo>(), options)
        assertEquals(1, transitousApi.requestCount)
        assertNull(cache.removedStart)
    }
    @Test
    fun `public transport failure with only departed cache exposes error`() = runTest {
        val failure = IOException("Transitous unavailable")
        val cache = FakePublicTransportCache(
            entry = PublicTransportCacheEntry(
                options = listOf(
                    publicTransportOption(now - 1L, "Departed"),
                ),
                fetchedAtMillis = now -
                    PublicTransportCachePolicy.DEFAULT_FRESH_MAX_AGE_MILLIS - 1L,
            ),
        )
        val repository = repository(
            transitousApi = FakeTransitousApi(failure = failure),
            publicTransportCache = cache,
        )

        val actual = captureExternalDataException {
            repository.getPublicTransportOptions(start, end)
        }

        assertEquals(ExternalDataException.Reason.NETWORK, actual.reason)
        assertSame(failure, actual.cause)
        assertEquals(GeoPoint(-37.8136, 144.9631), cache.removedStart)
    }

    @Test
    fun `public transport cache write failure keeps successful network result`() = runTest {
        val transitousApi = FakeTransitousApi(
            response = transitousResponse(now + 60_000L),
        )
        val repository = repository(
            transitousApi = transitousApi,
            publicTransportCache = FakePublicTransportCache(
                writeFailure = IOException("Cache write failed"),
            ),
        )

        assertEquals(1, repository.getPublicTransportOptions(start, end).size)
    }

    private fun repository(
        routeApi: RouteProxyApi = FakeRouteProxyApi(),
        routeCache: RouteCache? = null,
        routeToken: String? = "token",
        transitousApi: TransitousApi = FakeTransitousApi(),
        publicTransportCache: PublicTransportCache? = null,
    ): DefaultExternalDataRepository {
        return DefaultExternalDataRepository(
            weatherDataSource = OpenMeteoWeatherDataSource(FakeWeatherApi()),
            weatherCache = NoOpWeatherCache(),
            routeDataSource = RouteProxyDataSource(
                routeProxyApi = routeApi,
                accessTokenProvider = RouteAccessTokenProvider { routeToken },
            ),
            publicTransportDataSource = TransitousPublicTransportDataSource(
                transitousApi = transitousApi,
            ),
            routeCache = routeCache,
            publicTransportCache = publicTransportCache,
            currentTimeMillis = { now },
        )
    }

    private fun sampleRoutes(): List<RouteInfo> = listOf(
        RouteInfo(TransportMode.WALKING, 2_110.0, 1_519L),
        RouteInfo(TransportMode.CYCLING, 2_563.0, 633L),
        RouteInfo(TransportMode.CAR, 2_891.0, 434L),
    )

    private fun publicTransportOption(
        departureTimeMillis: Long,
        line: String,
    ) = PublicTransportInfo(
        line = line,
        departureTimeMillis = departureTimeMillis,
        estimatedDurationSeconds = 1_200L,
    )

    private fun transitousResponse(departureTimeMillis: Long) = TransitousResponse(
        itineraries = listOf(
            TransitousItinerary(
                duration = 1_200L,
                startTime = Instant.ofEpochMilli(departureTimeMillis).toString(),
                endTime = Instant.ofEpochMilli(departureTimeMillis + 1_200_000L).toString(),
                legs = listOf(
                    TransitousLeg(
                        mode = "TRAM",
                        startTime = Instant.ofEpochMilli(departureTimeMillis).toString(),
                        endTime = Instant.ofEpochMilli(departureTimeMillis + 1_200_000L).toString(),
                        routeShortName = "19",
                    ),
                ),
            ),
        ),
    )

    private suspend fun captureExternalDataException(
        block: suspend () -> Unit,
    ): ExternalDataException {
        try {
            block()
        } catch (error: ExternalDataException) {
            return error
        }
        throw AssertionError("Expected ExternalDataException")
    }

    private class FakeRouteCache(
        var entry: RouteCacheEntry? = null,
        private val readFailure: IOException? = null,
    ) : RouteCache {
        var savedStart: GeoPoint? = null
        var savedEnd: GeoPoint? = null
        var removedStart: GeoPoint? = null

        override suspend fun get(start: GeoPoint, end: GeoPoint): RouteCacheEntry? {
            readFailure?.let { throw it }
            return entry
        }

        override suspend fun save(
            start: GeoPoint,
            end: GeoPoint,
            entry: RouteCacheEntry,
        ) {
            savedStart = start
            savedEnd = end
            this.entry = entry
        }

        override suspend fun remove(start: GeoPoint, end: GeoPoint) {
            removedStart = start
            entry = null
        }
    }

    private class FakePublicTransportCache(
        var entry: PublicTransportCacheEntry? = null,
        private val writeFailure: IOException? = null,
    ) : PublicTransportCache {
        var removedStart: GeoPoint? = null

        override suspend fun get(
            start: GeoPoint,
            end: GeoPoint,
        ): PublicTransportCacheEntry? = entry

        override suspend fun save(
            start: GeoPoint,
            end: GeoPoint,
            entry: PublicTransportCacheEntry,
        ) {
            writeFailure?.let { throw it }
            this.entry = entry
        }

        override suspend fun remove(start: GeoPoint, end: GeoPoint) {
            removedStart = start
            entry = null
        }
    }

    private class FakeRouteProxyApi(
        private val failure: Exception? = null,
    ) : RouteProxyApi {
        var requestCount = 0

        override suspend fun getRoute(
            authorization: String,
            request: RouteProxyRequest,
        ): OpenRouteServiceResponse {
            requestCount += 1
            failure?.let { throw it }
            return OpenRouteServiceResponse(
                routes = listOf(
                    OpenRouteServiceRoute(
                        summary = OpenRouteServiceRouteSummary(
                            distance = 2_000.0 + requestCount,
                            duration = 600.0 + requestCount,
                        ),
                    ),
                ),
            )
        }
    }

    private class FakeTransitousApi(
        private val response: TransitousResponse = TransitousResponse(),
        private val failure: Exception? = null,
    ) : TransitousApi {
        var requestCount = 0

        override suspend fun planJourney(
            fromPlace: String,
            toPlace: String,
            arriveBy: Boolean,
            maxTransfers: Int,
            detailedLegs: Boolean,
            detailedTransfers: Boolean,
            userAgent: String,
        ): TransitousResponse {
            requestCount += 1
            failure?.let { throw it }
            return response
        }
    }

    private class FakeWeatherApi : OpenMeteoApi {
        override suspend fun getCurrentWeather(
            latitude: Double,
            longitude: Double,
            current: String,
            temperatureUnit: String,
        ): OpenMeteoResponse {
            return OpenMeteoResponse(
                current = OpenMeteoCurrentWeather(18.0, 1),
            )
        }
    }

    private class NoOpWeatherCache : WeatherCache {
        override suspend fun get(location: GeoPoint): WeatherCacheEntry? = null
        override suspend fun save(location: GeoPoint, entry: WeatherCacheEntry) = Unit
        override suspend fun remove(location: GeoPoint) = Unit
    }
}
