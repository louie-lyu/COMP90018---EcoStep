import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import kotlin.math.ceil

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.google.services)
    // AGP 9+ has built-in Kotlin support; org.jetbrains.kotlin.android is no longer used.
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

val runBenchmarks = providers.gradleProperty("ecostep.benchmark").orNull == "true"
val deviceEvaluation = providers.gradleProperty("ecostep.evaluation").orNull
val benchmarkClasses = listOf("EcoPoints", "AiMission", "WeeklyCoach", "MissionTrigger", "LocalFlow", "RecurringJourney")
    .joinToString(",") { "com.ecostep.app.evaluation.${it}Benchmark" }
val deviceEvaluationClasses = mapOf(
    "weather-latency" to "WeatherLatencyDeviceTest", "weather-cache" to "WeatherCacheDeviceTest",
    "weather-recovery" to "WeatherRecoveryDeviceTest", "ai-proxy" to "AiProxyDeviceTest",
    "transport" to "ShlDeviceEvaluationTest",
    "ui-responsiveness" to "UiResponsivenessEvaluationTest",
).mapValues { (_, name) -> "com.ecostep.app.evaluation.$name" }

// Reuse the original evaluation inputs in local JVM tests.
val prepareEvaluationCases = tasks.register<Sync>("prepareEvaluationCases") {
    from("src/androidTest/java") { include("**/*EvaluationCases.kt") }
    into(layout.buildDirectory.dir("generated/evaluationCases"))
}

val prepareEvaluationChecks = tasks.register<Sync>("prepareEvaluationChecks") {
    from("src/test/java") {
        include("com/ecostep/app/evaluation/EvaluationChecks.kt",
            "com/ecostep/app/evaluation/AiMissionChecks.kt",
            "com/ecostep/app/evaluation/LocalFlowHarness.kt",
            "com/ecostep/app/evaluation/AiProxyChecks.kt")
    }
    into(layout.buildDirectory.dir("generated/evaluationChecks"))
}

android {
    namespace = "com.ecostep.app"
    // Current AndroidX/library releases require compileSdk 36-37; 37 (Android 17) is the
    // platform actually installed locally, matching build-tools 36.0.0 also present.
    compileSdk = 37

    defaultConfig {
        applicationId = "com.ecostep.app"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"
        buildConfigField(
            "String",
            "ROUTE_PROXY_BASE_URL",
            "\"https://ecostep-route-proxy.jm674420449.workers.dev/\"",
        )
        // Local Firebase Emulator Suite host for manual testing: -Pecostep.firebaseEmulatorHost=
        // 127.0.0.1 together with `adb reverse tcp:9099 tcp:9099` (and 8080, 5001). Empty, the
        // default, uses the real project from google-services.json.
        val firebaseEmulatorHost =
            providers.gradleProperty("ecostep.firebaseEmulatorHost").getOrElse("")
        buildConfigField("String", "FIREBASE_EMULATOR_HOST", "\"$firebaseEmulatorHost\"")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        if (runBenchmarks) {
            testInstrumentationRunner = "androidx.benchmark.junit4.AndroidBenchmarkRunner"
            testInstrumentationRunnerArguments["class"] = benchmarkClasses
            testInstrumentationRunnerArguments["androidx.benchmark.suppressErrors"] = "EMULATOR"
            testInstrumentationRunnerArguments["androidx.benchmark.output.enable"] = "true"
        } else if (deviceEvaluation != null) {
            testInstrumentationRunnerArguments["class"] = deviceEvaluationClasses.getValue(deviceEvaluation)
            testInstrumentationRunnerArguments["live"] = "true"
            if (deviceEvaluation in setOf("transport", "ui-responsiveness")) {
                // Scope Git's ownership exception to this invocation.
                fun gitOutput(vararg arguments: String): String? = runCatching {
                    val result = providers.exec {
                        workingDir(rootDir)
                        commandLine(listOf("git", "-c", "safe.directory=${rootDir.invariantSeparatorsPath}") + arguments)
                        isIgnoreExitValue = true
                    }
                    if (result.result.get().exitValue == 0) result.standardOutput.asText.get().trim() else null
                }.getOrNull()
                testInstrumentationRunnerArguments["gitCommit"] = gitOutput("rev-parse", "HEAD") ?: "unknown"
                gitOutput("status", "--porcelain")?.let {
                    testInstrumentationRunnerArguments["workingTreeDirty"] = it.isNotEmpty().toString()
                }
            }
        } else {
            // General instrumentation still runs offline regression tests in evaluation.
            // Measurements require the presets, which configure their variant, inputs and export.
            testInstrumentationRunnerArguments["notClass"] =
                (listOf(benchmarkClasses) + deviceEvaluationClasses.values).joinToString(",")
        }
    }

    testBuildType = if (runBenchmarks) "benchmark" else "debug"
    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        create("benchmark") {
            initWith(getByName("release"))
            signingConfig = signingConfigs.getByName("debug")
            applicationIdSuffix = ".benchmark"
            isDebuggable = false
            matchingFallbacks += "release"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        // No local JDK 17 toolchain is registered and toolchain auto-download isn't configured;
        // JDK 21 is installed and fine to compile Java-17-target bytecode with (compileOptions
        // above still governs the actual bytecode target).
        jvmToolchain(21)
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
}

androidComponents {
    onVariants(selector().withBuildType("benchmark")) {
        // Same ahead-of-time compilation setting used by the Benchmark Gradle plugin.
        it.experimentalProperties.put("android.experimental.force-aot-compilation", true)
    }
}

extensions.configure<com.android.build.api.dsl.ApplicationExtension> {
    sourceSets.getByName("test").kotlin.directories.add("build/generated/evaluationCases")
    sourceSets.getByName("androidTest").kotlin.directories.add("build/generated/evaluationChecks")
    if (deviceEvaluation == "transport") {
        sourceSets.getByName("androidTest").assets.directories.add("build/generated/shlAssets")
    }
}
val prepareShlAssets = tasks.register<Sync>("prepareShlAssets") {
    from(providers.gradleProperty("ecostep.shlDataset").getOrElse(
        "src/androidTest/java/com/ecostep/app/evaluation/data/SHL-preview-evaluation.zip"))
    rename { "shl-preview.zip" }
    into(layout.buildDirectory.dir("generated/shlAssets"))
}
if (deviceEvaluation == "transport") {
    tasks.matching { it.name == "mergeDebugAndroidTestAssets" }.configureEach { dependsOn(prepareShlAssets) }
}
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    if (name.endsWith("UnitTestKotlin")) dependsOn(prepareEvaluationCases)
    if (name.endsWith("AndroidTestKotlin")) dependsOn(prepareEvaluationChecks)
}
// The benchmark application has no Firebase initialization or account state.
tasks.matching { it.name == "processBenchmarkGoogleServices" }.configureEach { enabled = false }

dependencies {
    implementation(libs.core.ktx)
    implementation(libs.lifecycle.runtime.ktx)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.activity.compose)
    implementation(libs.navigation.compose)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.datastore.preferences)

    // Foundational HTTP client for Jianing's weather/route/public-transport/AI clients.
    // Which external services to call is her decision — this just wires up the client.
    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization.converter)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging.interceptor)

    // Foundational GPS client for Zongcheng's sensor/journey-tracking module.
    implementation(libs.play.services.location)

    // OpenStreetMap renderer for the Home map.
    implementation(libs.osmdroid.android)

    // Firebase (Auth + Firestore, per docs/WORK_PLAN.md). Plugin left un-applied above
    // until Zongcheng adds the real google-services.json.
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    // Callable Cloud Functions for trusted operations (reward redemption, friend requests).
    implementation(libs.firebase.functions)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)

    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
    androidTestImplementation("androidx.benchmark:benchmark-junit4:1.5.0")
    // Benchmark's activity must also be present in the target application's manifest.
    add("benchmarkImplementation", "androidx.benchmark:benchmark-junit4:1.5.0")
}

val reportGroup = deviceEvaluation ?: "algorithms"
var runStartedAt = Long.MIN_VALUE
val exportEvaluationReports = tasks.register("exportEvaluationReports") {
    group = "verification"
    description = "Export device reports and readable Microbenchmark statistics."
    doLast {
        val variant = if (runBenchmarks) "benchmarkAndroidTest" else "debugAndroidTest"
        val source = layout.buildDirectory.dir("outputs/connected_android_test_additional_output/$variant").get().asFile
        val destination = rootProject.layout.projectDirectory.dir("evaluation_results/$reportGroup/${System.currentTimeMillis()}").asFile
        destination.mkdirs()
        val reports = source.walkTopDown().filter {
            it.isFile && it.extension in listOf("json", "csv") && it.lastModified() >= runStartedAt
        }.toList()
        reports.forEach { file ->
            val target = destination.resolve(file.relativeTo(source))
            target.parentFile.mkdirs()
            file.copyTo(target, overwrite = true)
        }
        if (runBenchmarks) {
            val rows = reports.filter { it.name.endsWith("benchmarkData.json") }.flatMap { file ->
                val data = JsonSlurper().parse(file) as Map<*, *>
                (data["benchmarks"] as List<*>).map { value ->
                    val benchmark = value as Map<*, *>
                    val metrics = benchmark["metrics"] as Map<*, *>
                    val timing = metrics["timeNs"] as Map<*, *>
                    val runs = (timing["runs"] as List<*>).map { (it as Number).toDouble() }.sorted()
                    val calls = if (benchmark["className"].toString().endsWith("EcoPointsBenchmark")) 1000 else 1
                    val median = (timing["median"] as Number).toDouble()
                    linkedMapOf<String, Any?>(
                        "device" to file.parentFile.name, "class" to benchmark["className"],
                        "scenario" to benchmark["name"], "callsPerMeasurement" to calls,
                        "measurementSamples" to runs.size, "medianNs" to median,
                        "p95Ns" to runs[ceil(runs.size * .95).toInt() - 1],
                        "minNs" to timing["minimum"], "maxNs" to timing["maximum"],
                        "meanNsPerCall" to runs.average() / calls, "medianNsPerCall" to median / calls,
                        "allocationCountMedian" to (metrics["allocationCount"] as? Map<*, *>)?.get("median"),
                        "warmupIterations" to benchmark["warmupIterations"],
                        "repeatIterations" to benchmark["repeatIterations"])
                }
            }
            val summary = mapOf("schemaVersion" to 1, "measuredGroups" to rows.size,
                "scope" to "AndroidX warmed measurements. EcoPoints is a 1000-call batch; other groups are one operation. p95 is nearest rank over measurement averages, not individual-call tail latency. Dry runs produce no timing rows. Emulator results describe this host/device only.",
                "benchmarks" to rows)
            destination.resolve("summary.json").writeText(JsonOutput.prettyPrint(JsonOutput.toJson(summary)))
            fun csv(value: Any?) = "\"${value?.toString()?.replace("\"", "\"\"").orEmpty()}\""
            val columns = rows.firstOrNull()?.keys.orEmpty()
            destination.resolve("summary.csv").writeText((listOf(columns.joinToString(",", transform = ::csv)) +
                rows.map { row -> columns.joinToString(",") { csv(row[it]) } }).joinToString("\n"))
            logger.lifecycle("Measured benchmark groups: ${rows.size}")
        }
        logger.lifecycle("Evaluation reports: $destination")
    }
}
if (runBenchmarks || deviceEvaluation != null) {
    tasks.matching { it.name == if (runBenchmarks) "connectedBenchmarkAndroidTest" else "connectedDebugAndroidTest" }
        .configureEach {
            doFirst { runStartedAt = System.currentTimeMillis() }
            finalizedBy(exportEvaluationReports)
        }
}
