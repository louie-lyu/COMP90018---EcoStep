package com.ecostep.app.data.cache.publictransport

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

internal class DataStorePublicTransportCache(
    private val dataStore: DataStore<Preferences>,
    private val json: Json = Json {
        ignoreUnknownKeys = true
    },
) : PublicTransportCache {
    override suspend fun get(
        start: GeoPoint,
        end: GeoPoint,
    ): PublicTransportCacheEntry? {
        val preferenceKey = preferenceKey(start, end)
        val encoded = dataStore.data.first()[preferenceKey] ?: return null

        return try {
            json.decodeFromString<PublicTransportCacheEntry>(encoded)
        } catch (_: SerializationException) {
            dataStore.edit { preferences ->
                preferences.remove(preferenceKey)
            }
            null
        }
    }

    override suspend fun save(
        start: GeoPoint,
        end: GeoPoint,
        entry: PublicTransportCacheEntry,
    ) {
        val preferenceKey = preferenceKey(start, end)
        val encoded = json.encodeToString(entry)
        dataStore.edit { preferences ->
            preferences[preferenceKey] = encoded
        }
    }

    override suspend fun remove(
        start: GeoPoint,
        end: GeoPoint,
    ) {
        val preferenceKey = preferenceKey(start, end)
        dataStore.edit { preferences ->
            preferences.remove(preferenceKey)
        }
    }

    private fun preferenceKey(
        start: GeoPoint,
        end: GeoPoint,
    ): Preferences.Key<String> {
        return stringPreferencesKey(
            PublicTransportCacheKey.from(start, end).value,
        )
    }
}
