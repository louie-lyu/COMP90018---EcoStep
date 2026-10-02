package com.ecostep.app.data.cache.route

import android.content.Context
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStore

internal val Context.routeDataStore by preferencesDataStore(
    name = "route_cache",
    corruptionHandler = ReplaceFileCorruptionHandler {
        emptyPreferences()
    },
)
