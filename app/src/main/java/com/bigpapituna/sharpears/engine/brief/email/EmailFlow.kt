package com.bigpapituna.sharpears.engine.brief.email

import com.bigpapituna.sharpears.engine.brief.WeeklyPlan
import com.bigpapituna.sharpears.engine.brief.WeeklySlot
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/**
 * What Settings > Briefs and email shows and says (ui/brief/email/BriefsAndEmail.kt draws it): which step the family's
 * address is on, when "Send a new code" opens again, each child's Daily and Weekly lines, the last-sent lines and every
 * refusal in kind words. Pure, so the words follow the app's rules (sentence case, no dashes, nothing scolding) and are tested.
 */
object EmailFlow {
    /** Where the family's address ([FamilyEmail.addressPart]) is in its journey. */
    enum class Stage {
        /** No address yet: the field and "Send code". */
        NO_ADDRESS,
        /** An address, a code, and the first email has not gone yet (no key, a metered network, offline): it is waiting. */
        FIRST_WAITING,
        /** The first email went; the code is waiting to be typed. */
        CODE,
        /** The code is gone (three wrong tries, or 24 hours): ask for a new one. */
        CODE_GONE,
        /** Confirmed: each child's switches, the preview, the stop. */
        VERIFIED,
    }

    fun stage(p: EmailPrefs): Stage = when {
        p.address == null -> Stage.NO_ADDRESS
        p.verified -> Stage.VERIFIED
        p.code == null -> Stage.CODE_GONE
        !p.firstSent -> Stage.FIRST_WAITING
        else -> Stage.CODE
    }

    /** "Send a new code" opens this long after the last email went (the caps allow two a day, but a code every minute helps no one). */
    const val RESEND_AFTER_MS = 10 * 60_000L

    /** Milliseconds until "Send a new code" opens again; 0 when it is open now. */
    fun resendIn(p: EmailPrefs, now: Long): Long {
        val last = p.sends.maxOrNull() ?: return 0
        return (last + RESEND_AFTER_MS - now).coerceIn(0, RESEND_AFTER_MS)
    }

    /** "in 7 minutes", "in a minute": the wait to show beside a button that is not open yet. */
    fun waitWords(ms: Long): String {
        val minutes = ((ms + 59_999) / 60_000).toInt().coerceAtLeast(1)
        return if (minutes == 1) "in a minute" else "in " + EmailWords.count(minutes, "minute")
    }

    /** What typing the six digits said: the three tries, in kind words. */
    fun codeWords(result: EmailVerification.Verified, triesLeft: Int): String = when (result) {
        EmailVerification.Verified.OK -> "That's the one. Each child's emails can start now."
        EmailVerification.Verified.WRONG -> if (triesLeft == 1) "Not that one. One try left." else "Not that one. $triesLeft tries left."
        EmailVerification.Verified.LOCKED -> "That was the third try. Ask for a new code and we will send one."
        EmailVerification.Verified.EXPIRED -> "That code has run out of time. Ask for a new one."
        EmailVerification.Verified.NO_CODE -> "There is no code waiting. Ask for a new one."
        EmailVerification.Verified.ALREADY -> "This address is already confirmed."
    }

    /** The address field's problem, or null when [raw] is fine. */
    fun addressProblem(raw: String): String? = when {
        raw.isBlank() -> null
        EmailVerification.tidy(raw) == null -> "That does not look like an email address."
        else -> null
    }

    /** A refusal in words a parent can read: why nothing was sent, and what happens next. */
    fun refusalWords(why: EmailSender.Refusal): String = when (why) {
        EmailSender.Refusal.NO_KEY -> "Email is not set up on this build."
        EmailSender.Refusal.METERED -> "Waiting for Wi-Fi. Emails go when the phone is on Wi-Fi, or on mobile data if you allow it below."
        EmailSender.Refusal.CAPPED -> "That's enough for today. The next email can go tomorrow."
        EmailSender.Refusal.NOT_WRITTEN -> "I could not write the brief just now. It will try again in a few minutes."
        EmailSender.Refusal.NO_ADDRESS -> "Add an email address first."
        EmailSender.Refusal.NO_CODE -> "Ask for a new code first."
        EmailSender.Refusal.UNVERIFIED -> "Confirm the address with the code first."
        EmailSender.Refusal.OFF -> "These emails are switched off. Turn on Daily or Weekly to start them."
    }

    /** A failure to send ([EmailSender.Outcome.Failed]) in words; the transport already scrubbed what it says. */
    fun failureWords(kind: EmailSendException.Kind): String = when (kind) {
        EmailSendException.Kind.NO_KEY -> refusalWords(EmailSender.Refusal.NO_KEY)
        EmailSendException.Kind.NOT_ALLOWED -> refusalWords(EmailSender.Refusal.METERED)
        EmailSendException.Kind.NETWORK -> "I could not reach the email service. Check the connection; it will try again soon."
        EmailSendException.Kind.TIMEOUT -> "The email service took too long. It may still arrive; check your inbox before trying again."
        EmailSendException.Kind.RATE_LIMITED -> "The email service is busy today. It will try again later."
        EmailSendException.Kind.SERVER -> "The email service had a hiccup. It will try again soon."
        EmailSendException.Kind.CLIENT, EmailSendException.Kind.CONFIG -> "The email service did not accept it. Emails are not set up correctly on this build."
    }

    /** The words for whatever the job or a button did. */
    fun outcomeWords(o: EmailSender.Outcome): String = when (o) {
        is EmailSender.Outcome.Sent -> "Sent. Check your inbox."
        is EmailSender.Outcome.Refused -> refusalWords(o.why)
        is EmailSender.Outcome.Failed -> failureWords(o.kind)
    }

    // ---- a child's row: two switches ----

    /** Under the Daily switch: "Each day Mia plays, 8:30 pm at the latest." */
    fun dailyLine(name: String): String = "Each day $name plays, ${WeeklyPlan.timeLabel(20 * 60 + 30)} at the latest."

    /** Under the Weekly switch: the slot, which a tap changes ("Sunday at 6 pm. Tap to change."). */
    fun weeklyLine(weekly: WeeklySlot): String = "${WeeklyPlan.label(weekly)}. Tap to change."

    /** The line over the children while the address is not confirmed yet. */
    const val ROWS_WAIT = "These start once your address is confirmed."

    // ---- the last-sent lines ----

    /** "Daily: sent Tuesday 8:31 pm", or "Daily: not sent yet". Null when this frequency never sends that kind and none went. */
    fun lastLine(kind: EmailKind, p: EmailPrefs, zone: ZoneId): String? {
        val (label, at, wanted) = when (kind) {
            EmailKind.DAILY -> Triple("Daily", p.lastDailyAt, p.frequency.daily)
            EmailKind.WEEKLY -> Triple("Weekly", p.lastWeeklyAt, p.frequency.weekly)
            EmailKind.FIRST -> return null
        }
        if (at <= 0) return if (wanted && p.verified) "$label: not sent yet" else null
        val t = Instant.ofEpochMilli(at).atZone(zone)
        val day = t.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.ENGLISH)
        val time = t.format(DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)).lowercase(Locale.ROOT)
        return "$label: sent $day $time"
    }

    /** The lines to show under the frequency, daily first. */
    fun lastLines(p: EmailPrefs, zone: ZoneId): List<String> = listOfNotNull(lastLine(EmailKind.DAILY, p, zone), lastLine(EmailKind.WEEKLY, p, zone))

    /** The held reason as the screen's note, or null. */
    fun heldWords(p: EmailPrefs): String? = p.held?.let { h -> EmailSender.Refusal.entries.firstOrNull { it.name == h } }?.let(::refusalWords)

    /** The test email counts against the day's two: how it is described beside its button. */
    const val TEST_NOTE = "Sends today's brief to this address now. It counts as one of the two emails a day."
}
