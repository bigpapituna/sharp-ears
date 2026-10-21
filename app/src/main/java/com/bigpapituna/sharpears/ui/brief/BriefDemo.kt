package com.bigpapituna.sharpears.ui.brief

import android.content.Intent
import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.bigpapituna.sharpears.TAG
import com.bigpapituna.sharpears.engine.brief.Brief
import com.bigpapituna.sharpears.engine.brief.BriefEvent
import com.bigpapituna.sharpears.engine.brief.RecommendationKind
import com.bigpapituna.sharpears.engine.brief.ActionId
import com.bigpapituna.sharpears.engine.brief.FlagKind
import com.bigpapituna.sharpears.engine.brief.BriefSamples
import com.bigpapituna.sharpears.engine.brief.Effects
import com.bigpapituna.sharpears.engine.brief.IdTitles
import com.bigpapituna.sharpears.engine.brief.KidCard
import com.bigpapituna.sharpears.engine.brief.Period
import com.bigpapituna.sharpears.engine.brief.SettingId
import com.bigpapituna.sharpears.engine.brief.SongChoice
import com.bigpapituna.sharpears.engine.brief.Undo
import com.bigpapituna.sharpears.engine.brief.WeeklySlot
import com.bigpapituna.sharpears.engine.family.Profile
import com.bigpapituna.sharpears.ui.SharpEarsTheme
import com.bigpapituna.sharpears.ui.family.LocalParentGate
import com.bigpapituna.sharpears.ui.family.ParentGateController
import com.bigpapituna.sharpears.ui.paper
import java.time.ZoneId

/*
 * The debug route `--es screen brief` (debug builds only, MainActivity opens it like the other demos): the parent brief with
 * the stand-in writer over made-up findings (engine/brief/BriefSamples.kt), so every state can be seen without a real child.
 *
 *   --es view parent|myweek|kidcard|mum|weekly|newkid   (default parent)
 *   --es period session|day|week                        the tab (default week)
 *   --es child mia|theo|noor                            the avatar tab (default mia)
 *   --es theme light|dark                               (default the system's)
 *   --es writer ai|phone                                the note on the brief (default ai)
 *   --ez full true                                      the full report open
 *   --ei card 0|1|2                                     open that recommendation's action sheet
 *   --ez export true                                    open the Send sheet
 *   --es ask "<question>"                               ask it at once (a typed question)
 *   --ez gate true                                      start with the parent gate shut (default open)
 *   --ez empty true                                     a child with nothing to read yet
 */

/** What the demo's action cards do: remember it in memory and log it. Nothing on the phone changes. */
class DemoEffects(private val log: (String) -> Unit = { Log.d(TAG, "brief demo: $it") }) : Effects {
    private fun done(what: String): Undo { log(what); return Undo { log("undo $what") } }
    override fun setNext(lessonId: String?, untilMs: Long) = done("set next ${lessonId ?: "(the path's)"}")
    override fun replay(lessonId: String) { log("replay $lessonId") }
    override fun practiseTogether(lessonId: String?, songId: String?) { log("practise together ${lessonId ?: songId}") }
    override fun freshStart(pageId: String) = done("fresh start $pageId")
    override fun focus(pageId: String) = done("focus $pageId")
    override fun unlockNext(pageId: String) = done("unlock after $pageId")
    override fun changeSetting(setting: SettingId, value: String) = done("setting ${setting.wire}=$value")
    override fun assignSong(songId: String, note: String, afterSongId: String?) = done("assign $songId note='$note'")
    override fun songChoices(afterSongId: String?) =
        listOf(SongChoice("ode-to-joy", "Ode to Joy"), SongChoice("hot-cross-buns", "Hot Cross Buns"), SongChoice("mary-had-a-little-lamb", "Mary had a little lamb"),
            SongChoice("jingle-bells", "Jingle Bells")).filter { it.id != afterSongId }
}

private val KIDS = listOf(
    Profile.kid("Mia", 3, reader = false).copy(id = "demo-mia"),
    Profile.kid("Theo", 1, reader = true).copy(id = "demo-theo"),
    Profile.kid("Noor", 5, reader = true).copy(id = "demo-noor"),
)

/** The demo's environment: made-up findings, the stand-in writer, in-memory settings. */
fun demoEnv(by: Brief.Writer = Brief.Writer.AI, empty: Boolean = false, zone: ZoneId = ZoneId.systemDefault()): BriefEnv {
    val slots = HashMap<String, WeeklySlot>()
    val writer = FakeWriter(DemoTitles, by)
    return BriefEnv(
        titles = DemoTitles,
        serviceFor = { BriefService(chain = { writer }) },
        loadFindings = { p, period, now ->
            if (empty) BriefSamples.empty(period, now, zone, p.kind)
            else if (p.isKid) BriefSamples.kid(period, now, zone, reader = p.reader) else BriefSamples.adult(period, now, zone)
        },
        effectsFor = { DemoEffects() },
        record = { p, id, params -> Log.d(TAG, "brief demo: recorded ${p.id} $id $params") },
        weekly = object : WeeklyAccess {
            override fun get(profile: Profile) = slots[profile.id] ?: WeeklySlot.DEFAULT.copy(on = profile.isKid)
            override fun set(profile: Profile, slot: WeeklySlot) { slots[profile.id] = slot; Log.d(TAG, "brief demo: weekly ${profile.id} ${slot.encode()}") }
        },
        zone = zone,
    )
}

/** Real titles for the ids the samples use (so the screens read like the app), without needing a context. */
private object DemoTitles : com.bigpapituna.sharpears.engine.brief.BriefTitles {
    private val lessons by lazy { com.bigpapituna.sharpears.engine.COURSE.flatMap { it.lessons }.associate { it.id to it.title } }
    private val pages by lazy { com.bigpapituna.sharpears.engine.playground.PLAYGROUND_PAGES.associate { it.id to it.title } }
    private val songs = mapOf("twinkle" to "Twinkle, Twinkle, Little Star", "hot-cross-buns" to "Hot Cross Buns", "ode-to-joy" to "Ode to Joy")
    override fun lesson(id: String) = lessons[id] ?: IdTitles.lesson(id)
    override fun song(id: String) = songs[id] ?: IdTitles.song(id)
    override fun page(id: String) = pages[id] ?: IdTitles.page(id)
}

/** The route's screen. [onExit] closes the activity. */
@Composable
fun BriefDemo(intent: Intent, onExit: () -> Unit) {
    val dark = when (intent.getStringExtra("theme")) { "dark" -> true; "light" -> false; else -> androidx.compose.foundation.isSystemInDarkTheme() }
    val by = if (intent.getStringExtra("writer") == "phone") Brief.Writer.TEMPLATE else Brief.Writer.AI
    val env = remember { demoEnv(by, empty = intent.getBooleanExtra("empty", false)) }
    val gate = remember { ParentGateController(null).also { if (!intent.getBooleanExtra("gate", false)) it.passed() } }
    val period = when (intent.getStringExtra("period")) { "session" -> Period.SESSION; "day" -> Period.DAY; else -> Period.WEEK }
    val child = KIDS.firstOrNull { it.name.equals(intent.getStringExtra("child"), ignoreCase = true) } ?: KIDS.first()
    val me = Profile.adult("Roy", 0).copy(id = "demo-roy")
    SharpEarsTheme(dark) {
        CompositionLocalProvider(LocalParentGate provides gate) {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                when (intent.getStringExtra("view")) {
                    "myweek" -> ParentBriefScreen(env, listOf(me), onBack = onExit, initialPeriod = period, showChildren = false, title = "My week",
                        expanded = intent.getBooleanExtra("full", false), openCard = intent.getIntExtra("card", -1).takeIf { it >= 0 })
                    "kidcard" -> KidCardDemo(env, child, by, onExit)
                    // The app's own environment over this phone's real profiles and event logs (the stand-in writer still writes).
                    "live" -> {
                        val ctx = androidx.compose.ui.platform.LocalContext.current
                        val liveEnv = rememberBriefEnv()
                        val kids = remember { com.bigpapituna.sharpears.data.Profiles(ctx).all.filter { it.isKid } }
                        ParentBriefScreen(liveEnv, kids, onBack = onExit, initialPeriod = period)
                    }
                    // The weekly notification for a real profile (--es who p1), through the real findings: posts it now if the week had playing in it.
                    "notify" -> NotifyDemo(intent.getStringExtra("who").orEmpty(), onExit)
                    "mum" -> ShowMumDialog(env, child, onClose = onExit, startPassed = !intent.getBooleanExtra("gate", false))
                    "weekly" -> Column(Modifier.fillMaxSize().paper()) {
                        WeeklyTimeStep(child.name, onChosen = { Log.d(TAG, "brief demo: weekly chosen ${it.encode()}"); onExit() })
                    }
                    else -> ParentBriefScreen(env, KIDS, onBack = onExit, initialChild = child.id, initialPeriod = period,
                        expanded = intent.getBooleanExtra("full", false), openCard = intent.getIntExtra("card", -1).takeIf { it >= 0 },
                        openExport = intent.getBooleanExtra("export", false), openAsk = intent.getStringExtra("ask").orEmpty().replace('_', ' '),
                        openWeekly = intent.getBooleanExtra("weeklySheet", false))
                }
            }
        }
    }
}

/** The kid's card on a plain page, with "Show Mum" opening the note. */
@Composable
private fun KidCardDemo(env: BriefEnv, child: Profile, by: Brief.Writer, onExit: () -> Unit) {
    var mum by remember { mutableStateOf(false) }
    val card = remember {
        val f = BriefSamples.kid(Period.SESSION, System.currentTimeMillis())
        FakeWriter(DemoTitles, by).kidCard(f, child.name)
    }
    Box(Modifier.fillMaxSize().paper().padding(16.dp), contentAlignment = Alignment.Center) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            KidBriefCard(card, say = { Log.d(TAG, "voice (demo): $it") }, onShowMum = { mum = true })
        }
    }
    if (mum) ShowMumDialog(env, child, onClose = { mum = false })
}

/** Posts the weekly notification for [profileId] (a real profile on this phone) and says what happened. */
@Composable
private fun NotifyDemo(profileId: String, onExit: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var line by remember { mutableStateOf("Working") }
    androidx.compose.runtime.LaunchedEffect(profileId) {
        line = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val p = com.bigpapituna.sharpears.data.Profiles(context).all.firstOrNull { it.id == profileId }
            if (p == null) "No profile $profileId"
            else {
                com.bigpapituna.sharpears.data.BriefStore(context, p.id).lastNotice = 0L
                val sent = com.bigpapituna.sharpears.reminder.BriefReminder.post(context, p)
                if (sent) "Posted for ${p.name}" else "Stayed quiet for ${p.name}"
            }
        }
        Log.d(TAG, "brief demo: notify $line")
    }
    Box(Modifier.fillMaxSize().paper(), contentAlignment = Alignment.Center) { androidx.compose.material3.Text(line, style = MaterialTheme.typography.titleLarge) }
}

/**
 * Debug (2.4 integration walk): the app's own brief env, its real action cards and stores, over made-up findings that
 * offer every action of the menu across the three tabs, for the profile the brief opens on. Opened with the weekly
 * notification's extras plus `--ez brief_samples true` (debug builds only):
 * `adb shell am start -n com.bigpapituna.sharpears/.MainActivity --es brief_profile p1 --es brief_period DAY --ez brief_samples true`.
 * Session: Replay for stars (playground-2), Practise together on Twinkle (a duet), Open the next page (after the garden).
 * Today: Teacher mode (after Twinkle), Focus on the garden, Fresh start on the garden. Week: Set as next (playground-8),
 * Practise together with nothing chosen (the song pick), Bedtime on.
 */
fun sampleActionsEnv(live: BriefEnv): BriefEnv = BriefEnv(live.titles, live.serviceFor,
    loadFindings = { p, period, now -> actionSamples(p, period, now, live.zone) },
    effectsFor = live.effectsFor, record = live.record, weekly = live.weekly, now = live.now, zone = live.zone)

internal fun actionSamples(p: Profile, period: Period, now: Long, zone: ZoneId): com.bigpapituna.sharpears.engine.brief.Findings {
    fun c(kind: com.bigpapituna.sharpears.engine.brief.RecommendationKind, action: com.bigpapituna.sharpears.engine.brief.ActionId,
          params: Map<String, String>, because: com.bigpapituna.sharpears.engine.brief.FlagKind) =
        com.bigpapituna.sharpears.engine.brief.Candidate(kind, action, params, because)
    val candidates = when (period) {
        Period.SESSION -> listOf(c(RecommendationKind.POLISH, ActionId.REPLAY_FOR_STARS, mapOf("lesson" to "playground-2"), FlagKind.LOW_STARS),
            c(RecommendationKind.TOGETHER, ActionId.PRACTISE_TOGETHER, mapOf("song" to "twinkle"), FlagKind.LEFT_HAND_BEHIND),
            c(RecommendationKind.NEXT_STEP, ActionId.PACE_UNLOCK, mapOf("page" to "garden"), FlagKind.CRUISING))
        Period.DAY -> listOf(c(RecommendationKind.NEW_MUSIC, ActionId.TEACHER_ASSIGN, mapOf("after" to "twinkle"), FlagKind.MASTERED_SONG),
            c(RecommendationKind.FOCUS, ActionId.PACE_FOCUS, mapOf("page" to "garden"), FlagKind.SCATTERED),
            c(RecommendationKind.FOCUS, ActionId.FRESH_START, mapOf("page" to "garden"), FlagKind.PAGE_STRUGGLE))
        Period.WEEK -> listOf(c(RecommendationKind.UNSTICK, ActionId.SET_NEXT, mapOf("lesson" to "playground-8"), FlagKind.STUCK_LESSON),
            c(RecommendationKind.ROUTINE, ActionId.PRACTISE_TOGETHER, emptyMap(), FlagKind.PRACTICE_DIPPED),
            c(RecommendationKind.SETTINGS, ActionId.SETTING, mapOf("setting" to "bedtime", "value" to "true"), FlagKind.LONG_DAYS))
    }
    return BriefSamples.kid(period, now, zone, reader = p.reader).copy(candidates = candidates)
}
