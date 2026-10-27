package com.bigpapituna.sharpears.engine.tester

import com.bigpapituna.sharpears.engine.lessons.CheerFrequency

/*
 * 2.15, the feature flags Roy can run experiments with in PostHog (docs/POSTHOG-SETUP.md, "Experiments"). The one place a
 * flag's value is read and checked: analytics/AppFlags.kt asks PostHog for the raw value (only while the dashboard records)
 * and hands it here; anything missing (offline, Help Roy test off, a keyless build, a flag Roy has not made) or of the wrong
 * shape gives the safe local default, which is how the app behaves without PostHog. Tested on the JVM (FlagsTest).
 */
object Flags {
    /**
     * Whether the one-time "Prefer a plainer look?" card is offered (the plain-mode branch's PlainLook offer). A boolean flag.
     * Default true: the offer shows as that branch designed it; Roy's experiment can hold it back for a group.
     */
    const val PLAIN_LOOK_OFFER = "plain_look_offer"
    const val PLAIN_LOOK_OFFER_DEFAULT = true

    /**
     * Encouragement's starting point for a profile that never chose one (Settings > Playing > Encouragement): a multivariate
     * flag whose variant keys are the setting's own words, "often", "less" or "off". Default "often" (Roy, 2026-10-25).
     */
    const val CHEER_FREQUENCY_DEFAULT = "cheer_frequency_default"
    val CHEER_FREQUENCY_FALLBACK: CheerFrequency = CheerFrequency.DEFAULT

    val ALL = listOf(PLAIN_LOOK_OFFER, CHEER_FREQUENCY_DEFAULT)

    /** [raw] (PostHog's value: true or false, or a variant's key) as the offer's yes or no. */
    fun plainLookOffer(raw: Any?): Boolean = when (raw) {
        is Boolean -> raw
        is String -> when (raw.trim().lowercase()) { "true", "on", "yes", "offer", "test" -> true; "false", "off", "no", "control" -> false; else -> PLAIN_LOOK_OFFER_DEFAULT }
        else -> PLAIN_LOOK_OFFER_DEFAULT
    }

    /** [raw] (a variant's key) as an Encouragement setting; anything else is the default, Often. */
    fun cheerFrequencyDefault(raw: Any?): CheerFrequency =
        (raw as? String)?.trim()?.lowercase()?.let { s -> CheerFrequency.entries.firstOrNull { it.wire == s } } ?: CHEER_FREQUENCY_FALLBACK
}
