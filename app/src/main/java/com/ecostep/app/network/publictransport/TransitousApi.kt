package com.ecostep.app.network.publictransport

import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Query

/**
 * Retrofit contract for the Transitous MOTIS journey-planning API.
 */
interface TransitousApi {

    @GET("api/v6/plan")
    suspend fun planJourney(
        @Query("fromPlace")
        fromPlace: String,

        @Query("toPlace")
        toPlace: String,

        @Query("time")
        time: String? = null,

        @Query("arriveBy")
        arriveBy: Boolean = false,

        @Query("maxTransfers")
        maxTransfers: Int = 2,

        @Query("detailedLegs")
        detailedLegs: Boolean = false,

        @Query("detailedTransfers")
        detailedTransfers: Boolean = false,

        @Header("User-Agent")
        userAgent: String = USER_AGENT,
    ): TransitousResponse

    companion object {
        const val BASE_URL =
            "https://api.transitous.org/"

        const val USER_AGENT =
            "EcoStep/0.1 " +
                    "(COMP90018 student prototype; " +
                    "https://github.com/louie-lyu/COMP90018---EcoStep)"
    }
}