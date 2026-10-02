package com.ecostep.app.data.cache.publictransport

import android.content.Context
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStore

internal val Context.publicTransportDataStore by preferencesDataStore(
    name = "public_transport_cache",
    corruptionHandler = ReplaceFileCorruptionHandler {
        emptyPreferences()
    },
)
