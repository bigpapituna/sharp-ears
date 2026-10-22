import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// The AI brief's key and model (docs/PARENT-BRIEF-PLAN.md, "Option B"): read at build time from secrets.properties
// next to this project, which is gitignored and never printed. Without the file the app has no key and the brief is
// written by the built-in templates. Rotate the key at OpenRouter when the private beta ends.
val secrets = Properties().apply {
    val f = rootProject.file("secrets.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
fun secret(name: String, default: String = "") = (secrets.getProperty(name) ?: default).replace("\\", "\\\\").replace("\"", "\\\"")

// Tester analytics (CLAUDE.md "Tester analytics"): the PostHog SDK and the code that starts it are compiled in only when
// secrets.properties has a POSTHOG_KEY. Without one the build takes src/noposthog (a stub that does nothing) and has no
// PostHog class at all. `-Psharpears.posthog=true` compiles the SDK in without a key, to check that code builds; it still
// never starts without a key (analytics/Tester.kt).
val withPostHog = secret("POSTHOG_KEY").isNotBlank() || providers.gradleProperty("sharpears.posthog").orNull == "true"

android {
    namespace = "com.bigpapituna.sharpears"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.bigpapituna.sharpears"
        minSdk = 28   // Android 9: microphone addresses (bottom/back) need API 28
        targetSdk = 37
        versionCode = 20
        versionName = "2.8.1"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // The Fold's ABI only: the x86_64 emulators run arm64 apps through their ARM translation (the speech library loads
        // only with the Kokoro model installed, which the emulators never have). x86_64 cost 26 MB of every install.
        ndk { abiFilters += listOf("arm64-v8a") }
        buildConfigField("String", "AI_KEY", "\"${secret("AI_KEY")}\"")
        buildConfigField("String", "AI_MODEL", "\"${secret("AI_MODEL", "anthropic/claude-haiku-5.5")}\"")
        buildConfigField("String", "AI_BASE_URL", "\"${secret("AI_BASE_URL", "https://openrouter.ai/api/v1")}\"")
        // The parent brief by email (Brevo): the key and the verified sender, from the same secrets.properties.
        buildConfigField("String", "EMAIL_KEY", "\"${secret("EMAIL_KEY")}\"")
        buildConfigField("String", "EMAIL_FROM", "\"${secret("EMAIL_FROM")}\"")
        buildConfigField("String", "EMAIL_FROM_NAME", "\"${secret("EMAIL_FROM_NAME", "The pup at Sharp Ears")}\"")
        buildConfigField("String", "EMAIL_BASE_URL", "\"${secret("EMAIL_BASE_URL", "https://api.brevo.com/v3")}\"")
        // Where the email's pup pictures are hosted (https, the files of app/src/main/assets/email). Blank: they go inside the email.
        buildConfigField("String", "EMAIL_IMAGE_BASE", "\"${secret("EMAIL_IMAGE_BASE")}\"")
        // Tester analytics: PostHog's project API key and host, and the address the tester diary goes to (Roy's own).
        // Empty by default: no SDK, no dashboard, no diary switch.
        buildConfigField("String", "POSTHOG_KEY", "\"${secret("POSTHOG_KEY")}\"")
        buildConfigField("String", "POSTHOG_HOST", "\"${secret("POSTHOG_HOST", "https://us.i.posthog.com")}\"")
        buildConfigField("String", "DIARY_TO", "\"${secret("DIARY_TO")}\"")
        buildConfigField("boolean", "POSTHOG_SDK", "$withPostHog")
        // Songs marked privateOnly (engine/chords/PopSongsTemp.kt) show only when secrets.properties says PRIVATE_BUILD=true (scripts/set-private-build.ps1); public by default.
        buildConfigField("boolean", "PRIVATE_BUILD", "${secret("PRIVATE_BUILD", "false").trim().equals("true", ignoreCase = true)}")
    }

    buildTypes {
        // Release-optimized but debug-signed, for honest CPU and frame measurements on the phone.
        create("profile") {
            initWith(getByName("release"))
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += "release"
        }
    }

    buildFeatures { compose = true; buildConfig = true }

    // The tester SDK's door (analytics/TesterSdk.kt): the real one with the SDK, the stub without it.
    sourceSets.getByName("main").kotlin.srcDir(if (withPostHog) "src/posthog/java" else "src/noposthog/java")

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
    if (withPostHog) implementation(libs.posthog.android)   // tester analytics, only on a build with POSTHOG_KEY (above)
    implementation(files("libs/sherpa-onnx-1.13.8.aar"))   // on-device Kokoro speech (Apache 2.0), github.com/k2-fsa/sherpa-onnx
    debugImplementation(libs.compose.ui.tooling)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
    androidTestImplementation(libs.test.runner)
    androidTestImplementation(libs.espresso.core)   // Compose test pulls 3.5.0, which breaks on API 37
    debugImplementation(libs.compose.ui.test.manifest)
}
