plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.bigpapituna.sharpears"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.bigpapituna.sharpears"
        minSdk = 28   // Android 9: microphone addresses (bottom/back) need API 28
        targetSdk = 37
        versionCode = 12
        versionName = "2.3.1"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // The Fold's ABI only: the x86_64 emulators run arm64 apps through their ARM translation (the speech library loads
        // only with the Kokoro model installed, which the emulators never have). x86_64 cost 26 MB of every install.
        ndk { abiFilters += listOf("arm64-v8a") }
    }

    buildTypes {
        // Release-optimized but debug-signed, for honest CPU and frame measurements on the phone.
        create("profile") {
            initWith(getByName("release"))
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += "release"
        }
    }

    buildFeatures { compose = true }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.activity.compose)
    implementation(libs.window)           // Fold tabletop posture (DESIGN 2.2)
    implementation(libs.youtube.player)   // official IFrame embed for tutorials (PLAN 6.3)
    implementation(files("libs/sherpa-onnx-1.13.8.aar"))   // on-device Kokoro speech (Apache 2.0), github.com/k2-fsa/sherpa-onnx
    debugImplementation(libs.compose.ui.tooling)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
    androidTestImplementation(libs.test.runner)
    androidTestImplementation(libs.espresso.core)   // Compose test pulls 3.5.0, which breaks on API 37
    debugImplementation(libs.compose.ui.test.manifest)
}
