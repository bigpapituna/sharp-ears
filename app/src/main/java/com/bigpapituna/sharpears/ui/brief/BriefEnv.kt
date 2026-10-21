package com.bigpapituna.sharpears.ui.brief

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.bigpapituna.sharpears.data.BriefAi
import com.bigpapituna.sharpears.data.BriefLoader
import com.bigpapituna.sharpears.data.BriefStore
import com.bigpapituna.sharpears.data.Briefs
import com.bigpapituna.sharpears.data.SongRepository
import com.bigpapituna.sharpears.engine.COURSE
import com.bigpapituna.sharpears.engine.brief.BriefEvent
import com.bigpapituna.sharpears.engine.brief.BriefTitles
import com.bigpapituna.sharpears.engine.brief.BriefWriter
import com.bigpapituna.sharpears.engine.brief.Brief
import com.bigpapituna.sharpears.engine.brief.Effects
import com.bigpapituna.sharpears.engine.brief.Findings
import com.bigpapituna.sharpears.engine.brief.KidCard
import com.bigpapituna.sharpears.engine.brief.Period
import com.bigpapituna.sharpears.engine.brief.WeeklySlot
import com.bigpapituna.sharpears.engine.brief.ai.WriterChain
import com.bigpapituna.sharpears.engine.family.Profile
import com.bigpapituna.sharpears.engine.playground.PLAYGROUND_PAGES
import com.bigpapituna.sharpears.reminder.BriefReminder
import com.bigpapituna.sharpears.ui.family.LocalProfiles
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.time.ZoneId

/**
 * How long the screens wait for the writers before the phone's own words are used instead. The AI writer keeps its own
 * limits (engine/brief/ai/AiLimits: three seconds for a session note and the child's card, twelve for a day and an answer,
 * twelve and one retry for a week) and falls to the templates inside the chain; these are the outer guard, a little longer,
 * for a writer that does not stop on time (docs/PARENT-BRIEF-PLAN.md: a child is waiting for the card).
 */
data class BriefLimits(val sessionMs: Long = 3_500, val longMs: Long = 26_000, val kidCardMs: Long = 3_500, val answerMs: Long = 13_000) {
    fun forPeriod(p: Period) = if (p == Period.SESSION) sessionMs else longMs
}

/**
 * One person's writers behind the screens. [chain] is the WriterChain (the AI writer first, the template writer last; a
 * stand-in in the demo), [phone] the template writer alone ("written on the phone"), [aiOn] whether the AI may write now
 * (a key in this build and the parent corner's switch on; read at each call, so the switch takes effect at once). With
 * the AI off, every call goes straight to [phone]. The writers block (a network call, maybe), so each runs on the IO
 * dispatcher and is interrupted at its limit; past it, or when it gives nothing, [phone] answers. Both are made on first
 * use, off the main thread.
 */
class BriefService(
    chain: () -> BriefWriter,
    phone: (() -> BriefWriter)? = null,
    private val limits: BriefLimits = BriefLimits(),
    private val aiOn: () -> Boolean = { false },
) {
    private val chainWriter: BriefWriter by lazy(chain)
    private val phoneWriter: BriefWriter? by lazy { phone?.invoke() }
    private val hasPhone = phone != null

    /** The writer asked first: the chain while the AI may write (or when there is no phone writer, as in the demo), else the phone's. */
    private fun first(): BriefWriter = if (aiOn() || !hasPhone) chainWriter else phoneWriter ?: chainWriter

    private suspend fun <T : Any> run(limit: Long, call: (BriefWriter) -> T?): T? = withContext(Dispatchers.IO) {
        val w = first()
        val a = withTimeoutOrNull(limit) { runInterruptible { runCatching { call(w) }.getOrNull() } }
        a ?: phoneWriter?.takeIf { it !== w }?.let { s -> runCatching { call(s) }.getOrNull() }
    }

    /**
     * True when the chain may write something better than the phone's words for [f]: the AI is on, there is a phone writer to
     * show first, and something happened. The day and the week then show the phone's brief at once and swap in the AI's.
     */
    fun aiMayWrite(f: Findings): Boolean = hasPhone && aiOn() && !f.isEmpty

    suspend fun write(f: Findings, name: String): Brief? = run(limits.forPeriod(f.period)) { it.write(f, name) }

    /** The phone's words at once (the template writer takes microseconds); null without a phone writer. */
    suspend fun phone(f: Findings, name: String): Brief? = withContext(Dispatchers.Default) { phoneWriter?.let { runCatching { it.write(f, name) }.getOrNull() } }

    /** Refresh: the AI writes again past its cache (the call counts toward the week's quota); the phone's words when it cannot. */
    suspend fun refresh(f: Findings, name: String): Brief? =
        run(limits.forPeriod(f.period)) { w -> if (w is WriterChain) w.refresh(f, name) else w.write(f, name) }

    suspend fun kidCard(f: Findings, name: String): KidCard? = run(limits.kidCardMs) { it.kidCard(f, name) }
    suspend fun answer(f: Findings, name: String, question: String): String? = run(limits.answerMs) { it.answer(f, name, question) }
}

/** Where the weekly day and time are kept and changed (BriefStore, plus the alarm). */
interface WeeklyAccess {
    fun get(profile: Profile): WeeklySlot
    fun set(profile: Profile, slot: WeeklySlot)
}

/**
 * Everything the brief's screens need from the app, in one place so the live app and the debug route differ only here.
 * [loadFindings] is blocking (it reads files), [record] writes a grown-up's action to that person's log, [effectsFor] is
 * what the action cards do on this phone.
 */
class BriefEnv(
    val titles: BriefTitles,
    /** One person's writers (the AI writer's quota and cache, and the templates' wording, are each person's). */
    val serviceFor: (Profile) -> BriefService,
    val loadFindings: (Profile, Period, Long) -> Findings,
    val effectsFor: (Profile) -> Effects,
    val record: (Profile, String, Map<String, String>) -> Unit,
    val weekly: WeeklyAccess,
    val now: () -> Long = System::currentTimeMillis,
    val zone: ZoneId = ZoneId.systemDefault(),
) {
    companion object {
        /**
         * The app's own: findings from the event logs, the writers from [BriefAi] (the AI writer over the templates, or the
         * templates alone when AI briefs are off or this build has no key), the action cards through [AppEffects].
         */
        fun live(context: Context, profiles: com.bigpapituna.sharpears.data.Profiles?, nav: BriefNav? = null): BriefEnv {
            val app = context.applicationContext
            val titles = AppTitles(app)
            return BriefEnv(
                titles = titles,
                serviceFor = { p -> liveService(app, p) },
                loadFindings = { p, period, now -> BriefLoader.findings(app, p, period, now) },
                effectsFor = { p -> AppEffects(app, profiles, p, nav) },
                record = { p, id, params -> Briefs.logFor(p.id) { BriefEvent.ParentAction(it, id, params) } },
                weekly = object : WeeklyAccess {
                    override fun get(profile: Profile) = BriefStore(app, profile.id).weeklyOrDefault(profile.isKid)
                    override fun set(profile: Profile, slot: WeeklySlot) { BriefStore(app, profile.id).weekly = slot; BriefReminder.schedule(app, profile) }
                },
            )
        }
    }
}

/** [profile]'s writers on this phone: the chain and the templates from [BriefAi], the AI only while it is on. */
fun liveService(context: Context, profile: Profile): BriefService {
    val app = context.applicationContext
    val writers by lazy { BriefAi.writers(app, profile) }
    return BriefService(chain = { writers.chain }, phone = { writers.phone }, aiOn = { BriefAi.on(app) })
}

/** The app's [BriefEnv], made once per composition. */
@Composable
fun rememberBriefEnv(): BriefEnv {
    val context = LocalContext.current
    val profiles = LocalProfiles.current
    val nav = LocalBriefNav.current
    return remember(context, profiles, nav) { BriefEnv.live(context, profiles, nav) }
}

/** Lesson titles from the course, song titles from the library, page titles from the Playground: all on the phone. */
class AppTitles(context: Context) : BriefTitles {
    private val lessons by lazy { COURSE.flatMap { it.lessons }.associate { it.id to it.title } }
    private val songs by lazy { runCatching { SongRepository(context).all().associate { it.id to it.title } }.getOrDefault(emptyMap()) }
    private val pages by lazy { PLAYGROUND_PAGES.associate { it.id to it.title } }
    private fun fallback(id: String) = id.split('-', '_').joinToString(" ").replaceFirstChar { it.uppercase() }
    override fun lesson(id: String) = lessons[id] ?: fallback(id)
    override fun song(id: String) = songs[id] ?: fallback(id)
    override fun page(id: String) = pages[id] ?: fallback(id)
}
