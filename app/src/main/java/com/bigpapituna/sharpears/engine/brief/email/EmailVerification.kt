package com.bigpapituna.sharpears.engine.brief.email

import com.bigpapituna.sharpears.engine.MiniJson
import com.bigpapituna.sharpears.engine.brief.WeeklySlot
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.LocalDate

/**
 * One child's email brief settings (Roy, 2026-10-21): per child, not per grown-up. data/EmailSettings keeps them on the
 * phone (never in Android's own backup); the weekly day and time are the weekly notification's (data/BriefStore.weekly),
 * shared, so they are not here.
 *
 * - [address]: the grown-up's address. It goes to Brevo as the recipient and nowhere else: never into a log, a crash
 *   report, an AI prompt or [toString].
 * - [verified]: the code the first email carried was typed in the app ([EmailVerification.verify]).
 * - [code], [codeExpiresAt], [wrongTries]: the pending 6-digit code, good for 24 hours and three wrong tries.
 * - [firstSent]: the first email (with [code]) went out.
 * - [frequency]: what the grown-up chose after the first email ([chosen]); WEEKLY until they choose, so a verified
 *   address gets the weekly brief rather than silence (the settings screen asks right after the code).
 * - [lastDaily], [lastWeekly]: the last day a daily email covered, the date of the last weekly slot sent.
 * - [sends]: when emails went out over the last two weeks, for the caps (EmailCaps).
 */
data class EmailPrefs(
    val address: String? = null,
    val verified: Boolean = false,
    val code: String? = null,
    val codeExpiresAt: Long = 0,
    val wrongTries: Int = 0,
    val firstSent: Boolean = false,
    val frequency: EmailFrequency = EmailFrequency.WEEKLY,
    val chosen: Boolean = false,
    val lastDaily: LocalDate? = null,
    val lastWeekly: LocalDate? = null,
    val sends: List<Long> = emptyList(),
) {
    /** An address waits for its code to be typed. */
    val pending: Boolean get() = address != null && !verified && code != null

    /** What the schedule needs ([weekly]: BriefStore's slot). The first email is pending until it has gone. */
    fun plan(weekly: WeeklySlot): EmailPlan = EmailPlan(
        frequency = if (verified) frequency else EmailFrequency.OFF, weekly = weekly, verified = verified && address != null,
        firstPending = pending && !firstSent, lastDaily = lastDaily, lastWeekly = lastWeekly, sends = sends,
    )

    /** After an email went out at [at]: the first marked sent, the day or the week recorded, the send counted. */
    fun sent(kind: EmailKind, day: LocalDate, at: Long): EmailPrefs = copy(
        firstSent = firstSent || kind == EmailKind.FIRST,
        lastDaily = if (kind == EmailKind.DAILY) maxOf(day, lastDaily ?: day) else lastDaily,
        lastWeekly = if (kind == EmailKind.WEEKLY) maxOf(day, lastWeekly ?: day) else lastWeekly,
        sends = (sends + at).filter { it > at - KEEP_SENDS_MS }.sorted(),
    )

    /** The grown-up chose how often. */
    fun choose(f: EmailFrequency): EmailPrefs = copy(frequency = f, chosen = true)

    /** The address and everything about it gone (the grown-up stopped the emails, or the profile was deleted). */
    fun cleared(): EmailPrefs = EmailPrefs(sends = sends)

    /** Never the address or the code. */
    override fun toString(): String = "EmailPrefs(address=${if (address == null) "none" else "set"}, verified=$verified, " +
        "code=${if (code == null) "none" else "pending"}, wrongTries=$wrongTries, firstSent=$firstSent, frequency=$frequency, chosen=$chosen, " +
        "lastDaily=$lastDaily, lastWeekly=$lastWeekly, sends=${sends.size})"

    /** One JSON object (MiniJson), what data/EmailSettings stores; [decode] reads it back. */
    fun encode(): String = MiniJson.write(linkedMapOf(
        "address" to address, "verified" to verified, "code" to code, "expires" to codeExpiresAt, "wrong" to wrongTries,
        "firstSent" to firstSent, "frequency" to frequency.name, "chosen" to chosen,
        "lastDaily" to lastDaily?.toString(), "lastWeekly" to lastWeekly?.toString(), "sends" to sends,
    ))

    companion object {
        /** Sends are kept two weeks: the caps look at a day and a week. */
        const val KEEP_SENDS_MS = 14L * 24 * 60 * 60 * 1000

        /** What [encode] wrote; defaults for anything missing or unreadable. */
        fun decode(text: String?): EmailPrefs {
            val m = text?.let { runCatching { MiniJson.read(it) as? Map<*, *> }.getOrNull() } ?: return EmailPrefs()
            fun date(k: String) = (m[k] as? String)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            return EmailPrefs(
                address = (m["address"] as? String)?.takeIf { it.isNotBlank() }, verified = m["verified"] == true,
                code = m["code"] as? String, codeExpiresAt = (m["expires"] as? Number)?.toLong() ?: 0, wrongTries = (m["wrong"] as? Number)?.toInt() ?: 0,
                firstSent = m["firstSent"] == true,
                frequency = EmailFrequency.entries.firstOrNull { it.name == m["frequency"] } ?: EmailFrequency.WEEKLY, chosen = m["chosen"] == true,
                lastDaily = date("lastDaily"), lastWeekly = date("lastWeekly"),
                sends = (m["sends"] as? List<*>).orEmpty().mapNotNull { (it as? Number)?.toLong() },
            )
        }
    }
}

/**
 * The address check (Roy: the grown-up enters the address in the app and confirms it with a code the first email
 * carries). Pure: [clock] and [digits] (six random digits) are injected, so it is tested on the JVM.
 *
 * [start] keeps a tidy [address], makes a fresh code good for [CODE_TTL_MS] and marks the first email pending (the
 * schedule sends it at once). [verify] with the right code within the time sets the address verified; each wrong code
 * counts, and the [MAX_TRIES]th wrong one throws the code away (the grown-up starts again, and a new first email goes).
 */
class EmailVerification(
    private val clock: () -> Long = System::currentTimeMillis,
    private val digits: () -> String = { String.format(java.util.Locale.ROOT, "%06d", RANDOM.nextInt(1_000_000)) },
) {
    enum class Started { SENDING, BAD_ADDRESS }
    enum class Verified { OK, WRONG, LOCKED, EXPIRED, NO_CODE, ALREADY }

    /** [prefs] with [address] waiting for its code, or unchanged with BAD_ADDRESS. The day, week and sends are kept. */
    fun start(prefs: EmailPrefs, address: String): Pair<EmailPrefs, Started> {
        val tidy = tidy(address) ?: return prefs to Started.BAD_ADDRESS
        return prefs.copy(address = tidy, verified = false, code = digits(), codeExpiresAt = clock() + CODE_TTL_MS, wrongTries = 0,
            firstSent = false) to Started.SENDING
    }

    /** Checks a typed [code] (spaces ignored). */
    fun verify(prefs: EmailPrefs, code: String): Pair<EmailPrefs, Verified> {
        if (prefs.verified) return prefs to Verified.ALREADY
        val want = prefs.code ?: return prefs to Verified.NO_CODE
        if (clock() > prefs.codeExpiresAt) return prefs.copy(code = null, wrongTries = 0) to Verified.EXPIRED
        val typed = code.filter { !it.isWhitespace() }
        if (MessageDigest.isEqual(typed.toByteArray(), want.toByteArray()))
            return prefs.copy(verified = true, code = null, wrongTries = 0) to Verified.OK
        val tries = prefs.wrongTries + 1
        return if (tries >= MAX_TRIES) prefs.copy(code = null, wrongTries = 0) to Verified.LOCKED
        else prefs.copy(wrongTries = tries) to Verified.WRONG
    }

    /** Wrong tries left on the pending code. */
    fun triesLeft(prefs: EmailPrefs): Int = if (prefs.code == null) 0 else MAX_TRIES - prefs.wrongTries

    companion object {
        const val CODE_TTL_MS = 24L * 60 * 60 * 1000
        const val MAX_TRIES = 3
        private val RANDOM = SecureRandom()
        private val SHAPE = Regex("[A-Za-z0-9.!#\$%&'*+/=?^_`{|}~-]+@[A-Za-z0-9](?:[A-Za-z0-9-]*[A-Za-z0-9])?(?:\\.[A-Za-z0-9](?:[A-Za-z0-9-]*[A-Za-z0-9])?)+")

        /** [raw] trimmed with its domain in lower case, or null when it is not shaped like an address (254 characters at most). */
        fun tidy(raw: String): String? {
            val a = raw.trim()
            if (a.length > 254 || !SHAPE.matches(a)) return null
            val at = a.lastIndexOf('@')
            if (!a.substring(at + 1).contains('.') || a.substring(at + 1).split('.').last().length < 2) return null
            return a.substring(0, at) + "@" + a.substring(at + 1).lowercase(java.util.Locale.ROOT)
        }
    }
}
