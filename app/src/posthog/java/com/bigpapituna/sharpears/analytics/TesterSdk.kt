package com.bigpapituna.sharpears.analytics

import android.content.Context
import androidx.compose.ui.Modifier
import com.posthog.PostHog
import com.posthog.PersonProfiles
import com.posthog.PostHogBeforeSend
import com.posthog.PostHogEvent
import com.posthog.PostHogPropertiesSanitizer
import com.posthog.android.PostHogAndroid
import com.posthog.android.PostHogAndroidConfig
import com.posthog.android.replay.PostHogMaskModifier.postHogMask
import java.util.UUID

/**
 * The PostHog SDK's door, on a build with POSTHOG_KEY (app/build.gradle.kts compiles this file and the SDK in only then;
 * src/noposthog has the stub with the same shape). Only [Tester] calls it, and only once [Tester] has decided the SDK may
 * start (engine/tester/TesterRules.sdkMayStart): nothing here runs on its own.
 *
 * Config (posthog.com/docs/libraries/android and /docs/session-replay/installation/android, SDK 3.72.5, read 2026-10-21):
 * session replay on in screenshot mode (Compose is recorded only in screenshot mode), every text and text input masked
 * (`maskAllTextInputs`), every image masked (`maskAllImages`: a certificate or a shared card carries a child's name in a
 * picture), no logcat in the replay (`captureLogcat` off), no element texts (`captureElementInteractions` off), Activity
 * screen views off (the app sends its own per screen), deep links off, no feature-flag events, the distinct id the phone's
 * own random device id ([start]'s deviceId, through `getAnonymousId`). The SDK records no sound: it has no audio capture.
 * A last guard drops any property value shaped like an email address (`propertiesSanitizer`).
 *
 * 2.15, error tracking (CLAUDE.md "2.15 posthog"; the SDK's error tracking, read 2026-10-27 from the 3.72.5 / core 6.47.2
 * classes): uncaught exceptions are captured by the SDK (`errorTrackingConfig.autoCapture`; PostHog also needs the project's
 * "Exception autocapture" on, or its remote config turns this off), native crashes of the speech library from Android's
 * tombstones at the next start (`captureNativeCrashes`), handled ones through [captureException] (analytics/Errors.kt, rate
 * limited and scrubbed). No exception steps (breadcrumbs). Every event passes [start]'s `beforeSend` last (Errors.beforeSend):
 * it scrubs the exception messages the SDK collected itself, stamps the test marks and can drop an event. Feature flags
 * ([flag]) send `$feature_flag_called` so Roy's experiments see who was shown what (it was off before 2.15, with no flags).
 */
internal object TesterSdk {
    const val present = true
    @Volatile private var started = false

    /** Sets PostHog up (once per process). Returns true when it is running. */
    fun start(context: Context, key: String, host: String, deviceId: String,
              beforeSend: (event: String, props: MutableMap<String, Any>) -> Boolean = { _, _ -> true }): Boolean {
        if (started) return true
        val id = runCatching { UUID.fromString(deviceId) }.getOrElse { return false }
        val app = context.applicationContext
        val config = PostHogAndroidConfig(apiKey = key, host = host).apply {
            sessionReplay = true
            sessionReplayConfig.screenshot = true
            sessionReplayConfig.maskAllTextInputs = true
            sessionReplayConfig.maskAllImages = true
            sessionReplayConfig.captureLogcat = false
            captureScreenViews = false
            captureDeepLinks = false
            captureElementInteractions = false
            captureApplicationLifecycleEvents = true
            sendFeatureFlagEvent = true
            surveys = false
            errorTrackingConfig.autoCapture = true
            errorTrackingConfig.captureNativeCrashes = true
            errorTrackingConfig.inAppIncludes.add(app.packageName)
            errorTrackingConfig.exceptionSteps.enabled = false
            personProfiles = PersonProfiles.IDENTIFIED_ONLY
            reuseAnonymousId = true
            getAnonymousId = { id }
            propertiesSanitizer = object : PostHogPropertiesSanitizer {
                override fun sanitize(properties: MutableMap<String, Any>): MutableMap<String, Any> {
                    properties.entries.removeAll { (_, v) -> v is String && ADDRESS.containsMatchIn(v) }
                    return properties
                }
            }
            addBeforeSend(object : PostHogBeforeSend {
                override fun run(event: PostHogEvent): PostHogEvent? {
                    val props = event.properties?.toMutableMap() ?: mutableMapOf()
                    return if (beforeSend(event.event, props)) event.copy(properties = props) else null
                }
            })
        }
        PostHogAndroid.setup(app, config)
        // An opt-out from an earlier run (the switch turned off) is kept by the SDK: the switch is on now.
        if (PostHog.isOptOut()) { PostHog.optIn(); PostHog.startSessionReplay(resumeCurrent = false) }
        started = true
        return true
    }

    val running: Boolean get() = started

    /** On again after [pause]: capture, and a new replay. */
    fun resume() { if (!started) return; PostHog.optIn(); PostHog.startSessionReplay(resumeCurrent = false) }

    /** Off: the replay stops and nothing more is captured (opt-out). Nothing is flushed. */
    fun pause() { if (!started) return; PostHog.stopSessionReplay(); PostHog.optOut() }

    fun capture(event: String, props: Map<String, Any>) { if (started) PostHog.capture(event = event, properties = props) }

    fun screen(name: String, props: Map<String, Any>) { if (started) PostHog.screen(screenTitle = name, properties = props) }

    /** A property sent with every later event (the profile's kind). */
    fun register(key: String, value: Any) { if (started) PostHog.register(key, value) }

    /** 2.15: a handled exception (analytics/Errors.report, which scrubbed and limited it): PostHog's `$exception`. */
    fun captureException(throwable: Throwable, props: Map<String, Any>) { if (started) PostHog.captureException(throwable, props) }

    /** 2.15: a feature flag's value as PostHog has it for this phone (true or false, a variant's key), or null. */
    fun flag(key: String): Any? = if (started) PostHog.getFeatureFlag(key) else null

    private val ADDRESS = Regex("[A-Za-z0-9._%+'-]+@[A-Za-z0-9-]+[.][A-Za-z0-9.-]+")
}

/** Hides this part of the screen in the tester replays (PostHog's mask, its whole subtree), on top of the masked text. */
fun Modifier.testerMask(): Modifier = this.postHogMask()
