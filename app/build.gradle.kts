import java.security.MessageDigest
import java.util.Properties
import java.util.zip.ZipFile

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
// 2.15: `-Psharpears.posthog=false` leaves it out of a keyed build, to check that the keyless door (src/noposthog) still
// compiles and its tests pass on a PC that has the key.
val withPostHog = secret("POSTHOG_KEY").isNotBlank() && providers.gradleProperty("sharpears.posthog").orNull != "false" ||
    providers.gradleProperty("sharpears.posthog").orNull == "true"

// 2.16, the coach voice (CLAUDE.md "2.16 coach voice"): an adult-only extra whose code, captions and audio live only in
// src/afterdark (src/afterdark/java, src/afterdark/assets/coach, src/afterdark/test). It is compiled in only when
// secrets.properties has AFTERDARK=true; otherwise the build takes src/noafterdark (a door that does nothing) and the APK holds
// no coach class, caption or sound. `-Psharpears.afterdark=false` forces it off on a PC that has the key (and
// `-Psharpears.afterdark=true` forces it on, for a worktree without secrets.properties). `checkNoAfterdark<Type>` runs after
// every APK packaged without it and fails if any of it got in.
val afterdark = when (providers.gradleProperty("sharpears.afterdark").orNull) {
    "false" -> false
    "true" -> true
    else -> secret("AFTERDARK").trim().equals("true", ignoreCase = true)
}

// The debug routes' key (security review 2026-10-22, finding 1; engine/DebugRoutes.kt). With DEBUG_ROUTE_KEY in
// secrets.properties (scripts/set-debug-key.ps1) the debug build acts on `--es screen ...` and the other launch extras only
// with `--es dk <key>` (scripts/adb-route.ps1); without it (keyless worktree builds) they work as before. BuildConfig keeps
// the key's SHA-256, never the key. `-PdebugRouteKey=<key>` is read before secrets.properties, to test a keyed build
// without touching that file.
val debugRouteKey = (providers.gradleProperty("debugRouteKey").orNull ?: secrets.getProperty("DEBUG_ROUTE_KEY") ?: "").trim()
val debugRouteKeySha256 = if (debugRouteKey.isEmpty()) "" else MessageDigest.getInstance("SHA-256").digest(debugRouteKey.toByteArray(Charsets.UTF_8))
    .joinToString("") { Integer.toHexString((it.toInt() and 0xFF) or 0x100).substring(1) }

android {
    namespace = "com.bigpapituna.sharpears"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.bigpapituna.sharpears"
        minSdk = 28   // Android 9: microphone addresses (bottom/back) need API 28
        targetSdk = 37
        versionCode = 33
        versionName = "2.16.1"
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
        // The debug routes' key, as its SHA-256 (above); empty on a keyless build.
        buildConfigField("String", "DEBUG_ROUTE_KEY_SHA256", "\"$debugRouteKeySha256\"")
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
    // The coach voice's door (extras/Extras.kt): the real one with its code, captions and sounds, or the empty one; each side's
    // JUnit tests come with it.
    sourceSets.getByName("main").kotlin.srcDir(if (afterdark) "src/afterdark/java" else "src/noafterdark/java")
    if (afterdark) sourceSets.getByName("main").assets.srcDir("src/afterdark/assets")
    sourceSets.getByName("test").kotlin.srcDir(if (afterdark) "src/afterdark/test" else "src/noafterdark/test")

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

// 2.16, the wall around the coach voice: an APK packaged without it (no AFTERDARK=true, or -Psharpears.afterdark=false) must
// hold none of it. Each check opens that build type's APK and fails on an asset under coach/ or named afterdark, on any class
// of the afterdark package, or on any caption from src/afterdark/assets/coach/<voice>/lines.json or the super property's name found in
// its dex, resources or manifest. It runs by itself after every such package task, and on its own as
// `.\gradlew.bat :app:checkNoAfterdarkDebug "-Psharpears.afterdark=false"`.
val coachBanks = file("src/afterdark/assets/coach")
for (type in listOf("debug", "profile", "release")) {
    val cap = type.replaceFirstChar { it.uppercase() }
    val apkDir = layout.buildDirectory.dir("outputs/apk/$type")
    val built = afterdark
    val banks = coachBanks          // a local: the task must not hold the script (configuration cache)
    val check = tasks.register("checkNoAfterdark$cap") {
        group = "verification"
        description = "Fails if the coach voice (src/afterdark) got into the $type APK built without it."
        doLast {
            if (built) throw GradleException("checkNoAfterdark$cap checks a build without the coach voice: run it with -Psharpears.afterdark=false")
            val apks = apkDir.get().asFile.listFiles { f -> f.name.endsWith(".apk") }.orEmpty()
            if (apks.isEmpty()) throw GradleException("checkNoAfterdark$cap: no APK in ${apkDir.get().asFile}")
            // The needles: every caption (from the bank's own captions file), the property's name and the package's path, as
            // UTF-8 bytes read one to one (ISO 8859-1), so a plain indexOf finds them in dex strings (MUTF-8) and resources.
            @Suppress("UNCHECKED_CAST")
            val files = banks.listFiles().orEmpty().map { it.resolve("lines.json") }.filter { it.isFile }
            val captions = files.flatMap { f -> (groovy.json.JsonSlurper().parse(f) as List<Map<String, Any?>>).map { it["caption"].toString() } }.distinct()
            if (banks.exists() && captions.size < 100) throw GradleException("checkNoAfterdark$cap: read only ${captions.size} captions from $banks")
            val needles = (captions + listOf("coach_voice", "coach_for", "sharpears/afterdark")).map { String(it.toByteArray(Charsets.UTF_8), Charsets.ISO_8859_1) }
            val found = mutableListOf<String>()
            for (apk in apks) ZipFile(apk).use { zip ->
                for (e in zip.entries()) {
                    val name = e.name
                    if (name.startsWith("assets/coach/") || name.contains("afterdark", ignoreCase = true)) { found += "${apk.name}: $name"; continue }
                    if (!(name.endsWith(".dex") || name.endsWith(".arsc") || name.endsWith(".xml") || name.endsWith(".json") || name.endsWith(".txt") || name.endsWith(".properties"))) continue
                    val text = String(zip.getInputStream(e).use { it.readBytes() }, Charsets.ISO_8859_1)
                    for (n in needles) if (text.indexOf(n) >= 0) found += "${apk.name}: $name holds \"${String(n.toByteArray(Charsets.ISO_8859_1), Charsets.UTF_8)}\""
                }
            }
            if (found.isNotEmpty()) throw GradleException("The coach voice got into a build without it: " + found.joinToString("; "))
            println("checkNoAfterdark$cap: ${apks.joinToString { it.name }} holds no coach asset, class or caption (${captions.size} captions checked)")
        }
    }
    if (!afterdark) tasks.matching { it.name == "package$cap" }.configureEach { finalizedBy(check) }
}
