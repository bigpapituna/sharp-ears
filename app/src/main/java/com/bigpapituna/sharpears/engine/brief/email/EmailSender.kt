package com.bigpapituna.sharpears.engine.brief.email

import com.bigpapituna.sharpears.engine.brief.Brief
import com.bigpapituna.sharpears.engine.brief.BriefTitles
import com.bigpapituna.sharpears.engine.brief.BriefWriter
import com.bigpapituna.sharpears.engine.brief.Findings
import com.bigpapituna.sharpears.engine.brief.WeeklySlot
import java.time.ZoneId

/**
 * Sends one email the schedule says is due (Android-free; the integrator calls it off the main thread, in the job that
 * asked EmailSchedule.decide). In order: refuse when it must not go ([refusal]: no address, unverified except the first
 * email, the frequency, the caps, no key, a metered network the grown-up did not allow), so nothing is written for an email
 * that cannot leave; write the brief with the writer chain ([BriefWriter], the AI first when it is on, the templates
 * behind it); get the full report's PDF bytes ([pdf], from ui/brief/BriefExport on the phone; null sends without);
 * build the [EmailBrief]; render it ([render]: step 2's HTML renderer, [PlainTextEmail] as the fallback and for the text
 * part); send it through [transport]; and return the settings with the send recorded, for the caller to save.
 *
 * [log] gets one line per decision with the profile id, the kind and sizes: never the address, the name, the subject, the
 * body or the key.
 */
class EmailSender(
    private val transport: EmailTransport,
    private val hasKey: () -> Boolean,
    private val networkAllowed: () -> Boolean,
    private val pdf: (Findings, Brief) -> ByteArray? = { _, _ -> null },
    private val render: EmailRenderer = PlainTextEmail.renderer,
    private val clock: () -> Long = System::currentTimeMillis,
    private val caps: EmailCaps = EmailCaps(),
    private val zone: ZoneId = ZoneId.systemDefault(),
    private val log: (String) -> Unit = {},
) {
    /** Who the email is about: the profile, the first name, the avatar, the titles, the weekly slot (BriefStore's), the extras. */
    data class Child(
        val profileId: String, val name: String, val avatar: Int, val titles: BriefTitles,
        val weekly: WeeklySlot = WeeklySlot.DEFAULT, val extras: EmailExtras = EmailExtras(),
        val summary: (String) -> String? = { null },
    )

    sealed interface Outcome {
        /** It went: save [prefs] (the send recorded). [email] is what was sent, for a "sent" line in the app. */
        data class Sent(val prefs: EmailPrefs, val email: EmailBrief) : Outcome
        /** It must not go (nothing was written or sent). */
        data class Refused(val why: Refusal) : Outcome
        /** The transport could not send it; [message] is scrubbed (safe to log and show). The schedule will offer it again. */
        data class Failed(val kind: EmailSendException.Kind, val message: String) : Outcome
    }

    enum class Refusal { NO_ADDRESS, NO_CODE, UNVERIFIED, OFF, CAPPED, NO_KEY, METERED, NOT_WRITTEN }

    /** Why [due] must not go for [prefs] now, or null when it may. */
    fun refusal(due: EmailDue.Send, prefs: EmailPrefs): Refusal? = when {
        prefs.address == null -> Refusal.NO_ADDRESS
        due.kind == EmailKind.FIRST && prefs.code == null -> Refusal.NO_CODE
        due.kind != EmailKind.FIRST && !prefs.verified -> Refusal.UNVERIFIED
        due.kind == EmailKind.DAILY && !prefs.frequency.daily -> Refusal.OFF
        due.kind == EmailKind.WEEKLY && !prefs.frequency.weekly -> Refusal.OFF
        !caps.allows(prefs.sends, clock(), zone) -> Refusal.CAPPED
        !hasKey() -> Refusal.NO_KEY
        !networkAllowed() -> Refusal.METERED
        else -> null
    }

    /** Writes, builds, renders and sends [due] for [child] from [findings] (computed at due.findingsAt). */
    fun send(due: EmailDue.Send, prefs: EmailPrefs, findings: Findings, writer: BriefWriter, child: Child): Outcome {
        val what = "${due.kind} ${due.day} for ${child.profileId}"
        refusal(due, prefs)?.let { log("email: $what not sent: $it"); return Outcome.Refused(it) }
        val brief = try { writer.write(findings, child.name) } catch (e: Exception) { null }
            ?: run { log("email: $what not sent: no writer wrote it"); return Outcome.Refused(Refusal.NOT_WRITTEN) }
        val bytes = try { pdf(findings, brief) } catch (e: Exception) { log("email: $what has no PDF (${e.javaClass.simpleName})"); null }
        val email = EmailBriefs.build(findings, brief, child.name, child.avatar, EmailBriefs.weekNumber(findings.range.toMs, zone),
            prefs.frequency, first = due.kind == EmailKind.FIRST, titles = child.titles, code = prefs.code, weekly = child.weekly,
            extras = child.extras, summary = child.summary, zone = zone, attached = bytes != null, profileId = child.profileId)
        val (html, text) = try { render.render(email) } catch (e: Exception) {
            log("email: $what drawn plain (the renderer failed: ${e.javaClass.simpleName})")
            PlainTextEmail.renderer.render(email)
        }
        val message = EmailMessage(prefs.address!!, email.subject, html.ifBlank { PlainTextEmail.html(email) }, text.ifBlank { PlainTextEmail.text(email) },
            bytes, email.attachmentName.takeIf { bytes != null })
        return try {
            transport.send(message)
            log("email: $what sent (${brief.writtenBy}, html ${html.length / 1024} KB, pdf ${(bytes?.size ?: 0) / 1024} KB)")
            Outcome.Sent(prefs.sent(due.kind, due.day, clock()), email)
        } catch (e: EmailSendException) {
            log("email: $what failed: ${e.kind}: ${e.message}")
            Outcome.Failed(e.kind, e.message.orEmpty())
        } catch (e: Exception) {
            // Not our transport's error: its message is not known to be clean, so only its type is kept.
            log("email: $what failed (${e.javaClass.simpleName})")
            Outcome.Failed(EmailSendException.Kind.NETWORK, e.javaClass.simpleName)
        }
    }
}
