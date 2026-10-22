package com.bigpapituna.sharpears.analytics

import android.content.Context
import androidx.compose.ui.Modifier
import com.posthog.PostHog
import com.posthog.PersonProfiles
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
 */
internal object TesterSdk {
    const val present = true
    @Volatile private var started = false

    /** Sets PostHog up (once per process). Returns true when it is running. */
    fun start(context: Context, key: String, host: String, deviceId: String): Boolean {
        if (started) return true
        val id = runCatching { UUID.fromString(deviceId) }.getOrElse { return false }
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
            sendFeatureFlagEvent = false
            surveys = false
            personProfiles = PersonProfiles.IDENTIFIED_ONLY
            reuseAnonymousId = true
            getAnonymousId = { id }
            propertiesSanitizer = object : PostHogPropertiesSanitizer {
                override fun sanitize(properties: MutableMap<String, Any>): MutableMap<String, Any> {
                    properties.entries.removeAll { (_, v) -> v is String && ADDRESS.containsMatchIn(v) }
                    return properties
                }
            }
        }
        PostHogAndroid.setup(context.applicationContext, config)
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

    private val ADDRESS = Regex("[A-Za-z0-9._%+'-]+@[A-Za-z0-9-]+[.][A-Za-z0-9.-]+")
}

/** Hides this part of the screen in the tester replays (PostHog's mask, its whole subtree), on top of the masked text. */
fun Modifier.testerMask(): Modifier = this.postHogMask()
