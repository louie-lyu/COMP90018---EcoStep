package com.ecostep.app.evaluation

import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.platform.app.InstrumentationRegistry
import com.ecostep.app.BuildConfig
import com.ecostep.app.data.cache.weather.DataStoreWeatherCache
import com.ecostep.app.data.repository.ExternalDataException
import com.ecostep.app.network.weather.OpenMeteoApi
import com.ecostep.app.network.weather.OpenMeteoResponse
import java.io.File
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** A real disk cache owned only by this evaluation, never the application's weather cache. */
internal class EvaluationWeatherCache {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val file = File(context.cacheDir, "weather-evaluation-${UUID.randomUUID()}.preferences_pb")
    private var job = SupervisorJob()
    var cache = createCache()
        private set

    private fun createCache() = DataStoreWeatherCache(
        PreferenceDataStoreFactory.create(
            scope = CoroutineScope(Dispatchers.IO + job),
            produceFile = { file },
        ),
    )

    suspend fun reopen() {
        // Wait for the old store to close before opening the same file again.
        job.cancelAndJoin()
        job = SupervisorJob()
        cache = createCache()
    }

    suspend fun close() = withContext(NonCancellable) {
        job.cancelAndJoin()
        check(!file.exists() || file.delete()) { "Cannot remove evaluation cache" }
    }
}

/** Counts provider calls, including failures, so a cache hit is verified rather than assumed. */
internal class CountingWeatherApi(private val delegate: OpenMeteoApi) : OpenMeteoApi {
    var calls = 0
        private set

    override suspend fun getCurrentWeather(
        latitude: Double,
        longitude: Double,
        current: String,
        temperatureUnit: String,
    ): OpenMeteoResponse {
        calls++
        return delegate.getCurrentWeather(latitude, longitude, current, temperatureUnit)
    }
}

internal fun weatherEvaluationReport(startedAt: Long, scope: String): JSONObject {
    val arguments = InstrumentationRegistry.getArguments()
    return JSONObject().apply {
        put("schemaVersion", 2)
        put("startedAtMillis", startedAt)
        put("gitCommit", arguments.getString("gitCommit") ?: "unknown")
        put("workingTreeDirty", arguments.getString("workingTreeDirty")?.toBooleanStrictOrNull() ?: JSONObject.NULL)
        put("device", "${Build.MANUFACTURER} ${Build.MODEL}")
        put("androidVersion", Build.VERSION.RELEASE)
        put("apiLevel", Build.VERSION.SDK_INT)
        put("buildType", BuildConfig.BUILD_TYPE)
        put("networkConditions", arguments.getString("networkConditions") ?: "unspecified")
        put("networkAtStart", evaluationNetworkState())
        put("scope", scope)
    }
}

internal fun evaluationNetworkState(): JSONObject {
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    val manager = context.getSystemService(ConnectivityManager::class.java)
    val capabilities = manager.getNetworkCapabilities(manager.activeNetwork)
    return JSONObject().apply {
        put("wifi", capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true)
        put("cellular", capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true)
        put("ethernet", capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true)
        put("vpn", capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true)
        put("validated", capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true)
        put("metered", manager.isActiveNetworkMetered)
    }
}

internal fun evaluationError(error: Throwable): JSONObject = JSONObject().apply {
    // Types and typed reasons avoid copying URLs, locations or credentials to reports.
    put("type", error.javaClass.simpleName)
    put("causeType", error.cause?.javaClass?.simpleName ?: JSONObject.NULL)
    put("reason", (error as? ExternalDataException)?.reason?.name ?: JSONObject.NULL)
    if (error is AssertionError || error is IllegalStateException) {
        put("check", error.message ?: JSONObject.NULL)
    }
}

internal fun saveWeatherEvaluationReport(prefix: String, startedAt: Long, report: JSONObject) {
    report.put("finishedAtMillis", System.currentTimeMillis())
    report.put("networkAtEnd", evaluationNetworkState())
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    val directory = File(context.filesDir, "evaluation")
    check(directory.isDirectory || directory.mkdirs()) { "Cannot create evaluation directory" }
    File(directory, "$prefix-$startedAt.json").writeText(report.toString(2), Charsets.UTF_8)
}
