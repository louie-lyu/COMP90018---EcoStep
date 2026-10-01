package com.ecostep.app.network.route

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import com.ecostep.app.core.di.AppContainer
import com.ecostep.app.data.model.GeoPoint
import com.ecostep.app.data.model.TransportMode
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@LargeTest
class RouteProxyIntegrationTest {

    @Test
    fun authenticatedProxyReturnsMelbourneRouteOptions() = runBlocking<Unit> {
        val context = InstrumentationRegistry
            .getInstrumentation()
            .targetContext

        val appContainer = AppContainer(context)

        assertNotNull(
            "Sign in to the EcoStep app before running this test.",
            appContainer.authRepository.currentUserId,
        )

        val routes = appContainer.externalDataRepository.getRouteOptions(
            start = GeoPoint(
                latitude = -37.8136,
                longitude = 144.9631,
            ),
            end = GeoPoint(
                latitude = -37.7963,
                longitude = 144.9614,
            ),
        )

        assertEquals(
            "The proxy should return walking, cycling and car routes.",
            3,
            routes.size,
        )

        assertEquals(
            setOf(
                TransportMode.WALKING,
                TransportMode.CYCLING,
                TransportMode.CAR,
            ),
            routes.map { it.mode }.toSet(),
        )

        assertTrue(
            "Every route should have a positive distance.",
            routes.all { it.distanceMeters > 0.0 },
        )

        assertTrue(
            "Every route should have a positive duration.",
            routes.all { it.durationSeconds > 0L },
        )

        Log.i(
            "RouteProxyIntegration",
            "Received ${routes.size} route options: " +
                    routes.joinToString { route ->
                        "${route.mode}: " +
                                "${route.distanceMeters.toLong()}m, " +
                                "${route.durationSeconds}s"
                    }
        )
    }
}
