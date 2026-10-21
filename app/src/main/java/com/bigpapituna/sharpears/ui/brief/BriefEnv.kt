package com.bigpapituna.sharpears.ui.brief

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
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
 * The writers the screens use. THE ONE LINE THE INTEGRATOR CHANGES: [chain] builds the writer from the titles; today it is
 * the stand-in [FakeWriter]. Replace it with the real WriterChain (the AI writer first, the template writer behind it), and
 * keep [fallback] as the template writer so a slow or failed AI call still gives a brief within [BriefLimits].
 */
object BriefWriters {
    /** The first writer (the AI one once it exists). */
    var chain: (BriefTitles) -> BriefWriter = { FakeWriter(it) }
    /** The one behind it when [chain] returns null or runs past its limit (the template writer; null: none). */
    var fallback: ((BriefTitles) -> BriefWriter?) = { null }
}

/**
 * How long the screens wait for the first writer before they use the next: a session note and the child's card three
 * seconds (a child is waiting), a daily or weekly brief and an answer longer (docs/PARENT-BRIEF-PLAN.md).
 */
data class BriefLimits(val sessionMs: Long = 3_000, val longMs: Long = 20_000, val kidCardMs: Long = 3_000, val answerMs: Long = 20_000) {
    fun forPeriod(p: Period) = if (p == Period.SESSION) sessionMs else longMs
}

/**
 * The writers behind the screens: the first, then the fallback when it gives nothing or is too slow. The writers are blocking
 * (a network call, maybe), so each runs on the IO dispatcher and is interrupted at its limit.
 */
class BriefService(private val first: BriefWriter, private val second: BriefWriter? = null, private val limits: BriefLimits = BriefLimits()) {
    private suspend fun <T : Any> run(limit: Long, call: (BriefWriter) -> T?): T? = withContext(Dispatchers.IO) {
        val a = withTimeoutOrNull(limit) { runInterruptible { runCatching { call(first) }.getOrNull() } }
        a ?: second?.let { s -> runCatching { call(s) }.getOrNull() }
    }

    suspend fun write(f: Findings, name: String): Brief? = run(limits.forPeriod(f.period)) { it.write(f, name) }
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
    val service: BriefService,
    val loadFindings: (Profile, Period, Long) -> Findings,
    val effectsFor: (Profile) -> Effects,
    val record: (Profile, String, Map<String, String>) -> Unit,
    val weekly: WeeklyAccess,
    val now: () -> Long = System::currentTimeMillis,
    val zone: ZoneId = ZoneId.systemDefault(),
) {
    companion object {
        /** The app's own: findings from the event logs, the writers of [BriefWriters], settings through [AppEffects]. */
        fun live(context: Context, profiles: com.bigpapituna.sharpears.data.Profiles?): BriefEnv {
            val app = context.applicationContext
            val titles = AppTitles(app)
            return BriefEnv(
                titles = titles,
                service = BriefService(BriefWriters.chain(titles), BriefWriters.fallback(titles)),
                loadFindings = { p, period, now -> BriefLoader.findings(app, p, period, now) },
                effectsFor = { p -> AppEffects(app, profiles, p) },
                record = { p, id, params -> Briefs.logFor(p.id) { BriefEvent.ParentAction(it, id, params) } },
                weekly = object : WeeklyAccess {
                    override fun get(profile: Profile) = BriefStore(app, profile.id).weeklyOrDefault(profile.isKid)
                    override fun set(profile: Profile, slot: WeeklySlot) { BriefStore(app, profile.id).weekly = slot; BriefReminder.schedule(app, profile) }
                },
            )
        }
    }
}

/** The app's [BriefEnv], made once per composition. */
@Composable
fun rememberBriefEnv(): BriefEnv {
    val context = LocalContext.current
    val profiles = LocalProfiles.current
    return remember(context, profiles) { BriefEnv.live(context, profiles) }
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
