package com.ecostep.app.data.cache.weather

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.ecostep.app.data.model.GeoPoint
import kotlinx.coroutines.flow.first
import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Persistent [WeatherCache] backed by Preferences DataStore.
 *
 * Each rounded weather location is stored under its own preference key, while
 * the cache entry is serialised as JSON.
 */
internal class DataStoreWeatherCache(
    private val dataStore: DataStore<Preferences>,
    private val json: Json = Json {
        ignoreUnknownKeys = true
    },
) : WeatherCache {

    override suspend fun get(location: GeoPoint): WeatherCacheEntry? {
        val preferenceKey = location.toPreferenceKey()
        val encodedEntry = dataStore.data.first()[preferenceKey] ?: return null

        return try {
            json.decodeFromString<WeatherCacheEntry>(encodedEntry)
        } catch (_: SerializationException) {
            dataStore.edit { preferences ->
                preferences.remove(preferenceKey)
            }
            null
        }
    }

    override suspend fun save(
        location: GeoPoint,
        entry: WeatherCacheEntry,
    ) {
        val preferenceKey = location.toPreferenceKey()
        val encodedEntry = json.encodeToString(entry)

        dataStore.edit { preferences ->
            preferences[preferenceKey] = encodedEntry
        }
    }

    override suspend fun remove(location: GeoPoint) {
        val preferenceKey = location.toPreferenceKey()

        dataStore.edit { preferences ->
            preferences.remove(preferenceKey)
        }
    }

    private fun GeoPoint.toPreferenceKey(): Preferences.Key<String> {
        val locationKey = WeatherLocationKey.from(this)
        return stringPreferencesKey(locationKey.value)
    }
}