package com.ecostep.app.network.publictransport

import com.ecostep.app.data.model.GeoPoint
import com.ecostep.app.data.model.PublicTransportInfo

/**
 * Calls Transitous and converts its response into EcoStep's shared model.
 */
internal class TransitousPublicTransportDataSource(
    private val transitousApi: TransitousApi,
) {

    suspend fun getPublicTransportOptions(
        start: GeoPoint,
        end: GeoPoint,
    ): List<PublicTransportInfo> {
        val response = transitousApi.planJourney(
            fromPlace = start.toTransitousPlace(),
            toPlace = end.toTransitousPlace(),
        )

        return response.toPublicTransportInfoList()
    }

    private fun GeoPoint.toTransitousPlace(): String {
        return "$latitude,$longitude"
    }
}
