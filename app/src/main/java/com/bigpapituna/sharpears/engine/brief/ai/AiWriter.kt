package com.bigpapituna.sharpears.engine.brief.ai

import com.bigpapituna.sharpears.engine.brief.Brief
import com.bigpapituna.sharpears.engine.brief.BriefWriter
import com.bigpapituna.sharpears.engine.brief.Findings
import com.bigpapituna.sharpears.engine.brief.KidCard
import com.bigpapituna.sharpears.engine.brief.Period
import com.bigpapituna.sharpears.engine.brief.TimeRange
import com.bigpapituna.sharpears.engine.family.ProfileKind
import java.util.concurrent.Callable
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * How long each kind of call may take and how long its reply may be (docs/PARENT-BRIEF-PLAN.md: the session note and
 * the child's card three seconds, then templates; the day and the week longer). A weekly brief may try once more after
 * a server error or a timeout, so it can take up to twice [weekMs].
 */
data class AiLimits(
    val sessionMs: Long = 3_000,
    val kidCardMs: Long = 3_000,
    val dayMs: Long = 12_000,
    val weekMs: Long = 12_000,
    val answerMs: Long = 12_000,
    val sessionTokens: Int = 700,
    val dayTokens: Int = 1_500,
    val weekTokens: Int = 1_500,
    val kidCardTokens: Int = 250,
    val answerTokens: Int = 400,
) {
    fun briefMs(p: Period) = when (p) { Period.SESSION -> sessionMs; Period.DAY -> dayMs; Period.WEEK -> weekMs }
    fun briefTokens(p: Period) = when (p) { Period.SESSION -> sessionTokens; Period.DAY -> dayTokens; Period.WEEK -> weekTokens }
}

/** A writer that can write again past its cache: the brief's Refresh. */
interface Refreshable {
    fun refresh(findings: Findings, name: String): Brief?
}

/**
 * The AI writer (docs/PARENT-BRIEF-PLAN.md, writer 1): the findings go to a model through [transport] and come back in
 * the fixed structure, checked on the way ([AiReplies]). Null whenever it cannot write (offline, metered, no key, slow,
 * over quota, a malformed or rule-breaking reply), and the caller falls back to the templates ([WriterChain]).
 *
 * What is sent: [Prompts] (the rules, the glossary, the worked examples), [Findings.redacted] encoded (ids, numbers and
 * enums only), and the titles of the lessons, songs and pages it mentions, looked up on the phone through [titles]. Never
 * the person's name: the model writes {name} and the app fills it in; a title or question holding the name has it
 * replaced by {name} first, a title holding anyone else's name in [otherNames] (the roster) is not sent at all, and an
 * id that holds a roster name (an imported song's id is made from its file name) goes as a stand-in ([IdAliases]).
 *
 * [budget] keeps [profileId]'s weekly calls and the cache; [clock] stamps writtenAt and picks the budget's week. Every
 * call waits on its own thread for at most the limit (twice for a weekly brief that may retry), so a slow network can
 * never hold the caller longer than [limits] says. Call it off the main thread.
 *
 * [log] gets one line per decision (cache, quota, failure kind, what the checks dropped); never the name, a key, a prompt
 * or a reply.
 */
class AiWriter(
    private val transport: AiTransport,
    private val model: String,
    private val clock: () -> Long,
    private val limits: AiLimits,
    private val budget: Budget,
    private val profileId: String,
    private val titles: Titles = Titles.course(),
    private val otherNames: () -> Collection<String> = { emptyList() },
    private val log: (String) -> Unit = {},
) : BriefWriter, Refreshable {

    override fun write(findings: Findings, name: String): Brief? = brief(findings, name, refresh = false)

    /** Writes the brief again whatever the cache holds; the call counts against the week's quota. */
    override fun refresh(findings: Findings, name: String): Brief? = brief(findings, name, refresh = true)

    private fun brief(findings: Findings, name: String, refresh: Boolean): Brief? {
        val slot = findings.period.name
        if (findings.isEmpty) { log("ai: $slot not asked: nothing happened in it"); return null }
        val (sent, t) = prepare(findings, name)
        val system = Prompts.brief(findings.profileKind)
        return ask(slot, refresh, system, Prompts.briefUser(sent, t.lines), Prompts.briefUser(steady(sent), t.lines),
            json = true, timeoutMs = limits.briefMs(findings.period), maxTokens = limits.briefTokens(findings.period),
            temperature = 0.4, retryOnce = findings.period == Period.WEEK) { text, at -> AiReplies(findings, t, name, at).brief(text) }
    }

    /** The pup's card, for kids only (a grown-up gets null without a call; the template writer decides what they see). */
    override fun kidCard(findings: Findings, name: String): KidCard? {
        if (findings.profileKind != ProfileKind.KID) { log("ai: KID not asked: not a kid"); return null }
        if (findings.isEmpty) { log("ai: KID not asked: nothing happened"); return null }
        val (sent, t) = prepare(findings, name)
        return ask("KID", false, Prompts.kidCard(), Prompts.kidCardUser(sent, t.lines), Prompts.kidCardUser(steady(sent), t.lines),
            json = true, timeoutMs = limits.kidCardMs, maxTokens = limits.kidCardTokens, temperature = 0.7, retryOnce = false,
        ) { text, at -> AiReplies(findings, t, name, at).kidCard(text) }
    }

    /**
     * A grown-up's question answered from the same findings, in plain text. The question is cut to
     * [Prompts.QUESTION_MAX] characters, the person's name in it becomes {name} and anyone else's on the roster
     * "someone else", before it is sent.
     */
    override fun answer(findings: Findings, name: String, question: String): String? {
        val q = question.trim().take(Prompts.QUESTION_MAX)
        if (q.isEmpty()) return null
        val (sent, t) = prepare(findings, name)
        val asked = others(name).fold(Names.replace(q, name, Prompts.NAME)) { s, n -> Names.replace(s, n, "someone else") }
        return ask("ANSWER", false, Prompts.answer(findings.profileKind), Prompts.answerUser(sent, t.lines, asked),
            Prompts.answerUser(steady(sent), t.lines, asked), json = false, timeoutMs = limits.answerMs,
            maxTokens = limits.answerTokens, temperature = 0.3, retryOnce = false) { text, at -> AiReplies(findings, t, name, at).answer(text) }
    }

    // ---- one call -------------------------------------------------------------------------------------------------

    /**
     * The cache, then the quota, then the call, then the checks. [stableUser] is the user message without "now" (the
     * range's end moves with the clock for a day or a week), so the cache key only changes when the findings do.
     */
    private fun <T> ask(
        slot: String, refresh: Boolean, system: String, user: String, stableUser: String,
        json: Boolean, timeoutMs: Long, maxTokens: Int, temperature: Double, retryOnce: Boolean,
        check: (String, Long) -> Checked<T>,
    ): T? {
        val key = Budget.hash(Prompts.VERSION.toString(), model, slot, system, stableUser)
        if (!refresh) budget.cached(profileId, slot, key)?.let { c ->
            check(c.text, c.at).value?.let { log("ai: $slot from the cache"); return it }
        }
        val now = clock()
        if (!budget.take(profileId, now)) { log("ai: $slot not asked: this week's ${budget.callsPerWeek} calls are used"); return null }
        val text = call(AiRequest(model, system, user, json, maxTokens, temperature, timeoutMs, retryOnce), now) ?: return null
        val checked = check(text, now)
        if (checked.dropped.isNotEmpty()) log("ai: $slot dropped ${checked.dropped.size}: ${checked.dropped.joinToString("; ")}")
        val value = checked.value ?: run { log("ai: $slot reply not usable"); return null }
        budget.keep(profileId, slot, key, now, text)
        return value
    }

    /** The transport's reply within the limit, or null; a call that cost nothing goes back to the budget. */
    private fun call(request: AiRequest, now: Long): String? {
        val deadline = request.timeoutMs * (if (request.retryOnce) 2 else 1)
        val future = EXECUTOR.submit(Callable { transport.complete(request) })
        return try {
            future.get(deadline, TimeUnit.MILLISECONDS)
        } catch (e: TimeoutException) {
            future.cancel(true)
            log("ai: no reply within $deadline ms")      // counted: the provider may answer, and bill, after we stopped waiting
            null
        } catch (e: ExecutionException) {
            when (val c = e.cause) {
                is AiCallException -> { if (!c.kind.billed) budget.giveBack(profileId, now); log("ai: ${c.kind}: ${c.message}") }
                // Not our transport's error: its message is not known to be clean, so only its type is logged.
                else -> { budget.giveBack(profileId, now); log("ai: the transport failed (${c?.javaClass?.simpleName})") }
            }
            null
        } catch (e: InterruptedException) {
            future.cancel(true)
            Thread.currentThread().interrupt()
            null
        }
    }

    /**
     * What is sent for [findings]: the findings with any id that holds a roster name replaced by a stand-in
     * ([IdAliases]), and their titles under the same ids. The checks on the way back use the original findings.
     */
    private fun prepare(findings: Findings, name: String): Pair<Findings, TitleSet> {
        val others = others(name)
        val aliases = IdAliases.of(findings, listOf(name) + others)
        return IdAliases.apply(findings, aliases) to TitleSet(findings, titles, name, others, aliases)
    }

    private fun others(name: String): Collection<String> =
        runCatching { otherNames() }.getOrDefault(emptyList()).filter { it.isNotBlank() && !it.trim().equals(name.trim(), ignoreCase = true) }

    /** The findings without "now": a day's or a week's range ends at the moment it was computed. */
    private fun steady(f: Findings) = f.copy(range = TimeRange(f.range.fromMs, 0))

    private companion object {
        /** Calls wait here, so the caller's limit holds even when a read hangs. Daemon threads: they never keep the app up. */
        val EXECUTOR = Executors.newCachedThreadPool { r -> Thread(r, "ai-brief").apply { isDaemon = true } }
    }
}
