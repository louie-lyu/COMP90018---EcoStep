package com.ecostep.app.data.cache.route

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.ecostep.app.data.model.GeoPoint
import com.ecostep.app.data.model.RouteInfo
import com.ecostep.app.data.model.TransportMode
import java.io.File
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DataStoreRouteCacheTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val start = GeoPoint(-37.8136, 144.9631)
    private val end = GeoPoint(-37.7963, 144.9614)
    private val entry = RouteCacheEntry(
        routes = listOf(
            RouteInfo(TransportMode.WALKING, 2_110.0, 1_519L),
            RouteInfo(TransportMode.CYCLING, 2_563.0, 633L),
            RouteInfo(TransportMode.CAR, 2_891.0, 434L),
        ),
        fetchedAtMillis = 1_800_000_000_000L,
    )

    @Test
    fun `saved route entry can be read`() = runTest {
        val cache = DataStoreRouteCache(createDataStore(backgroundScope))
        cache.save(start, end, entry)
        assertEquals(entry, cache.get(start, end))
    }

    @Test
    fun `reverse route does not reuse forward cache`() = runTest {
        val cache = DataStoreRouteCache(createDataStore(backgroundScope))
        cache.save(start, end, entry)
        assertNull(cache.get(end, start))
    }

    @Test
    fun `removed route entry is no longer returned`() = runTest {
        val cache = DataStoreRouteCache(createDataStore(backgroundScope))
        cache.save(start, end, entry)
        cache.remove(start, end)
        assertNull(cache.get(start, end))
    }

    @Test
    fun `corrupted route entry is removed`() = runTest {
        val dataStore = createDataStore(backgroundScope)
        val cache = DataStoreRouteCache(dataStore)
        val preferenceKey = stringPreferencesKey(
            RouteCacheKey.from(start, end).value,
        )
        dataStore.edit { it[preferenceKey] = "{broken-json" }

        assertNull(cache.get(start, end))
        assertNull(dataStore.data.first()[preferenceKey])
    }

    private fun createDataStore(scope: CoroutineScope): DataStore<Preferences> {
        val file = File(
            temporaryFolder.root,
            "route-${UUID.randomUUID()}.preferences_pb",
        )
        return PreferenceDataStoreFactory.create(
            scope = scope,
            produceFile = { file },
        )
    }
}
