package com.ecostep.app.data.cache.weather

import android.content.Context
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStore

/**
 * Single application-wide Preferences DataStore used by the weather cache.
 */
internal val Context.weatherDataStore by preferencesDataStore(
    name = "weather_cache",
    corruptionHandler = ReplaceFileCorruptionHandler {
        emptyPreferences()
    },
)
