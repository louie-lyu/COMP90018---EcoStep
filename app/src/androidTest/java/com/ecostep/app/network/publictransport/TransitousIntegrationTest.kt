package com.ecostep.app.network.publictransport

import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.ecostep.app.core.di.AppContainer
import com.ecostep.app.data.model.GeoPoint
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@LargeTest
class TransitousIntegrationTest {

    @Test
    fun liveTransitousRequestReturnsMelbourneJourneyOptions() {
        runBlocking {
            val context =
                ApplicationProvider.getApplicationContext<
                        android.content.Context
                        >()

            val repository =
                AppContainer(context)
                    .externalDataRepository

            val options =
                repository.getPublicTransportOptions(
                    start = GeoPoint(
                        latitude = -37.8183,
                        longitude = 144.9671,
                    ),
                    end = GeoPoint(
                        latitude = -37.7697,
                        longitude = 144.9611,
                    ),
                )

            assertTrue(
                "Expected at least one public transport option",
                options.isNotEmpty(),
            )

            assertTrue(
                "Every option should have a non-blank line",
                options.all {
                    it.line.isNotBlank()
                },
            )

            assertTrue(
                "Every option should have a departure time",
                options.all {
                    it.departureTimeMillis > 0L
                },
            )

            assertTrue(
                "Every option should have a positive duration",
                options.all {
                    it.estimatedDurationSeconds > 0L
                },
            )

            Log.i(
                "TransitousIntegration",
                "Received ${options.size} public transport options: " +
                        options.joinToString { option ->
                            "${option.line}: " +
                                    "${option.estimatedDurationSeconds}s"
                        },
            )
        }
    }
}
