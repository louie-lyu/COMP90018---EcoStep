package com.ecostep.app.data.model

import kotlinx.serialization.Serializable

@Serializable
enum class TransportMode {
    WALKING,
    CYCLING,
    PUBLIC_TRANSPORT,
    CAR,
    UNKNOWN,
}
