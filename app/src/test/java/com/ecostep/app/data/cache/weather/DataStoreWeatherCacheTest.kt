package com.ecostep.app.data.cache.weather

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.ecostep.app.data.model.GeoPoint
import com.ecostep.app.data.model.WeatherData
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

class DataStoreWeatherCacheTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val melbourneLocation = GeoPoint(
        latitude = -37.8136,
        longitude = 144.9631,
    )

    private val cachedWeather = WeatherCacheEntry(
        weatherData = WeatherData(
            temperatureCelsius = 17.4,
            conditions = "Mainly clear",
        ),
        fetchedAtMillis = 1_789_970_000_000L,
    )

    @Test
    fun `saved weather entry can be read from DataStore`() = runTest {
        val dataStore = createDataStore(backgroundScope)
        val cache = DataStoreWeatherCache(dataStore)

        cache.save(
            location = melbourneLocation,
            entry = cachedWeather,
        )

        assertEquals(
            cachedWeather,
            cache.get(melbourneLocation),
        )
    }

    @Test
    fun `nearby locations with the same rounded key share cached weather`() = runTest {
        val dataStore = createDataStore(backgroundScope)
        val cache = DataStoreWeatherCache(dataStore)

        cache.save(
            location = melbourneLocation,
            entry = cachedWeather,
        )

        val nearbyLocation = GeoPoint(
            latitude = -37.8144,
            longitude = 144.9644,
        )

        assertEquals(
            cachedWeather,
            cache.get(nearbyLocation),
        )
    }

    @Test
    fun `removed weather entry is no longer returned`() = runTest {
        val dataStore = createDataStore(backgroundScope)
        val cache = DataStoreWeatherCache(dataStore)

        cache.save(
            location = melbourneLocation,
            entry = cachedWeather,
        )
        cache.remove(melbourneLocation)

        assertNull(cache.get(melbourneLocation))
    }

    @Test
    fun `corrupted JSON entry is removed and treated as a cache miss`() = runTest {
        val dataStore = createDataStore(backgroundScope)
        val cache = DataStoreWeatherCache(dataStore)
        val preferenceKey = stringPreferencesKey(
            WeatherLocationKey.from(melbourneLocation).value,
        )

        dataStore.edit { preferences ->
            preferences[preferenceKey] = "{not-valid-json"
        }

        assertNull(cache.get(melbourneLocation))
        assertNull(dataStore.data.first()[preferenceKey])
    }

    private fun createDataStore(
        scope: CoroutineScope,
    ): DataStore<Preferences> {
        val file = File(
            temporaryFolder.root,
            "weather-${UUID.randomUUID()}.preferences_pb",
        )

        return PreferenceDataStoreFactory.create(
            scope = scope,
            produceFile = { file },
        )
    }
}