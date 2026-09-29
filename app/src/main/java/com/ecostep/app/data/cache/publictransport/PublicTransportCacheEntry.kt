package com.ecostep.app.data.cache.publictransport

import com.ecostep.app.data.model.PublicTransportInfo
import kotlinx.serialization.Serializable

@Serializable
internal data class PublicTransportCacheEntry(
    val options: List<PublicTransportInfo>,
    val fetchedAtMillis: Long,
)
