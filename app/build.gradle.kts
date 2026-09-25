plugins {
    alias(libs.plugins.android.application)
    // AGP 9+ has built-in Kotlin support; org.jetbrains.kotlin.android is no longer used.
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.google.services)
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

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
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

    // Firebase (Auth + Firestore, per docs/WORK_PLAN.md). Plugin left un-applied above
    // until Zongcheng adds the real google-services.json.
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)

    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
}
