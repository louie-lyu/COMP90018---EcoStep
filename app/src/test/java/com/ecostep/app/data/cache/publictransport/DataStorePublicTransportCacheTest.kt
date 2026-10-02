package com.ecostep.app.data.cache.publictransport

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.ecostep.app.data.model.GeoPoint
import com.ecostep.app.data.model.PublicTransportInfo
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

class DataStorePublicTransportCacheTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val start = GeoPoint(-37.8183, 144.9671)
    private val end = GeoPoint(-37.7697, 144.9611)
    private val entry = PublicTransportCacheEntry(
        options = listOf(
            PublicTransportInfo("Tram 19", 1_800_000_600_000L, 1_200L),
        ),
        fetchedAtMillis = 1_800_000_000_000L,
    )

    @Test
    fun `saved public transport entry can be read`() = runTest {
        val cache = DataStorePublicTransportCache(createDataStore(backgroundScope))
        cache.save(start, end, entry)
        assertEquals(entry, cache.get(start, end))
    }

    @Test
    fun `reverse journey does not reuse forward cache`() = runTest {
        val cache = DataStorePublicTransportCache(createDataStore(backgroundScope))
        cache.save(start, end, entry)
        assertNull(cache.get(end, start))
    }

    @Test
    fun `removed entry is no longer returned`() = runTest {
        val cache = DataStorePublicTransportCache(createDataStore(backgroundScope))
        cache.save(start, end, entry)
        cache.remove(start, end)
        assertNull(cache.get(start, end))
    }

    @Test
    fun `corrupted entry is removed`() = runTest {
        val dataStore = createDataStore(backgroundScope)
        val cache = DataStorePublicTransportCache(dataStore)
        val preferenceKey = stringPreferencesKey(
            PublicTransportCacheKey.from(start, end).value,
        )
        dataStore.edit { it[preferenceKey] = "{broken-json" }

        assertNull(cache.get(start, end))
        assertNull(dataStore.data.first()[preferenceKey])
    }

    private fun createDataStore(scope: CoroutineScope): DataStore<Preferences> {
        val file = File(
            temporaryFolder.root,
            "public-transport-${UUID.randomUUID()}.preferences_pb",
        )
        return PreferenceDataStoreFactory.create(
            scope = scope,
            produceFile = { file },
        )
    }
}
