package com.bigpapituna.sharpears.analytics

import android.content.Context
import androidx.compose.ui.Modifier

/**
 * The tester SDK's door on a build without POSTHOG_KEY: the PostHog SDK is not in the APK at all (app/build.gradle.kts) and
 * this stub does nothing. src/posthog has the real one with the same shape.
 */
internal object TesterSdk {
    const val present = false
    @Suppress("UNUSED_PARAMETER") fun start(context: Context, key: String, host: String, deviceId: String,
                                            beforeSend: (event: String, props: MutableMap<String, Any>) -> Boolean = { _, _ -> true }): Boolean = false
    val running: Boolean get() = false
    fun resume() {}
    fun pause() {}
    @Suppress("UNUSED_PARAMETER") fun capture(event: String, props: Map<String, Any>) {}
    @Suppress("UNUSED_PARAMETER") fun screen(name: String, props: Map<String, Any>) {}
    @Suppress("UNUSED_PARAMETER") fun register(key: String, value: Any) {}
    @Suppress("UNUSED_PARAMETER") fun captureException(throwable: Throwable, props: Map<String, Any>) {}
    @Suppress("UNUSED_PARAMETER") fun flag(key: String): Any? = null
}

/** Nothing to hide without the SDK. */
@Suppress("NOTHING_TO_INLINE") inline fun Modifier.testerMask(): Modifier = this
