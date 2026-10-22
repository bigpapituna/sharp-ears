package com.bigpapituna.sharpears

import android.content.pm.ApplicationInfo
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.safeDrawingPadding
import com.bigpapituna.sharpears.engine.COURSE
import com.bigpapituna.sharpears.engine.CourseLesson
import com.bigpapituna.sharpears.ui.LearnScreen
import com.bigpapituna.sharpears.ui.LessonDeps
import com.bigpapituna.sharpears.ui.LessonScreen
import com.bigpapituna.sharpears.engine.CoursePath
import com.bigpapituna.sharpears.engine.play.QuestEvent
import com.bigpapituna.sharpears.engine.world.Reward
import com.bigpapituna.sharpears.data.PlayStore
import com.bigpapituna.sharpears.data.WorldStore
import com.bigpapituna.sharpears.data.KidStore
import com.bigpapituna.sharpears.data.Briefs
import com.bigpapituna.sharpears.data.TimedOnScreen
import com.bigpapituna.sharpears.engine.brief.BriefEvent
import com.bigpapituna.sharpears.engine.brief.ToyKind
import com.bigpapituna.sharpears.engine.kid.Finale
import com.bigpapituna.sharpears.engine.kid.KidMap
import com.bigpapituna.sharpears.engine.kid.KidPages
import com.bigpapituna.sharpears.engine.playground.PLAYGROUND_PAGES
import com.bigpapituna.sharpears.ui.kid.StickerPage
import com.bigpapituna.sharpears.ui.world.LocalWorld
import com.bigpapituna.sharpears.ui.world.LocalPupOutfit
import com.bigpapituna.sharpears.ui.world.WorldTab
import com.bigpapituna.sharpears.ui.world.WorldSounds
import com.bigpapituna.sharpears.ui.motion.Juice
import com.bigpapituna.sharpears.ui.motion.wakesPup
import com.bigpapituna.sharpears.ui.play.Toy
import com.bigpapituna.sharpears.ui.play.frameTap
import com.bigpapituna.sharpears.ui.play.opens
import com.bigpapituna.sharpears.ui.HomeShell
import com.bigpapituna.sharpears.ui.LicencesScreen
import com.bigpapituna.sharpears.ui.PrivacyScreen
import com.bigpapituna.sharpears.ui.HomeTab
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import com.bigpapituna.sharpears.audio.MicInput
import com.bigpapituna.sharpears.audio.MidiDevices
import com.bigpapituna.sharpears.audio.Sampler
import com.bigpapituna.sharpears.audio.Voice
import com.bigpapituna.sharpears.data.ImportException
import com.bigpapituna.sharpears.data.LessonStore
import com.bigpapituna.sharpears.data.Prefs
import com.bigpapituna.sharpears.data.SongMeta
import com.bigpapituna.sharpears.data.SongRepository
import com.bigpapituna.sharpears.engine.Hand
import com.bigpapituna.sharpears.engine.MIC_CHECK_ID
import com.bigpapituna.sharpears.engine.Mode
import com.bigpapituna.sharpears.engine.PROGRESSIONS
import com.bigpapituna.sharpears.engine.Practice
import com.bigpapituna.sharpears.engine.Song
import com.bigpapituna.sharpears.engine.micCheckExercise
import com.bigpapituna.sharpears.engine.progressionSong
import com.bigpapituna.sharpears.engine.chords.PopFind
import com.bigpapituna.sharpears.engine.songPack
import com.bigpapituna.sharpears.engine.toSong
import com.bigpapituna.sharpears.engine.ChordTrack
import com.bigpapituna.sharpears.engine.LessonItem
import com.bigpapituna.sharpears.engine.SessionPart
import com.bigpapituna.sharpears.engine.SessionPlan
import com.bigpapituna.sharpears.engine.SessionSong
import com.bigpapituna.sharpears.engine.planSession
import com.bigpapituna.sharpears.engine.sessionSummary
import com.bigpapituna.sharpears.ui.DailyChords
import com.bigpapituna.sharpears.ui.Interstitial
import com.bigpapituna.sharpears.engine.barMs
import com.bigpapituna.sharpears.engine.labelBarsAligned
import com.bigpapituna.sharpears.ui.GameStart
import com.bigpapituna.sharpears.engine.GameKind
import com.bigpapituna.sharpears.ui.LibraryActions
import com.bigpapituna.sharpears.ui.LibraryEntry
import com.bigpapituna.sharpears.ui.LibraryScreen
import com.bigpapituna.sharpears.ui.GoalState
import com.bigpapituna.sharpears.engine.progress.DailyGoal
import com.bigpapituna.sharpears.ui.MicSetupScreen
import com.bigpapituna.sharpears.ui.PlayerScreen
import com.bigpapituna.sharpears.ui.PlayerStart
import com.bigpapituna.sharpears.ui.SettingsScreen
import com.bigpapituna.sharpears.ui.SharpEarsTheme
import com.bigpapituna.sharpears.ui.paper
import androidx.compose.animation.AnimatedContent
import com.bigpapituna.sharpears.data.PracticeStore
import com.bigpapituna.sharpears.data.EarStore
import com.bigpapituna.sharpears.data.TheoryStore
import com.bigpapituna.sharpears.data.ProgressStore
import com.bigpapituna.sharpears.engine.ear.EarKind
import com.bigpapituna.sharpears.ui.ear.EarStart
import com.bigpapituna.sharpears.ui.ear.EarTrainingScreen
import com.bigpapituna.sharpears.ui.motion.screenTransitions
import com.bigpapituna.sharpears.ui.onboarding.OnboardingScreen
import com.bigpapituna.sharpears.ui.practice.PracticeScreen
import com.bigpapituna.sharpears.ui.practice.PracticeTab
import com.bigpapituna.sharpears.ui.profile.ProfileScreen
import com.bigpapituna.sharpears.ui.reducedMotion
import com.bigpapituna.sharpears.engine.ear.EarEvent
import com.bigpapituna.sharpears.engine.progress.ProgressEvent
import com.bigpapituna.sharpears.ui.practice.PracticeKind
import com.bigpapituna.sharpears.ui.profile.LocalProgress
import com.bigpapituna.sharpears.ui.profile.ProgressOverlays
import com.bigpapituna.sharpears.data.Profiles
import com.bigpapituna.sharpears.engine.family.Profile
import com.bigpapituna.sharpears.ui.family.FamilySettings
import com.bigpapituna.sharpears.ui.family.FamilyWall
import com.bigpapituna.sharpears.ui.family.LocalParentGate
import com.bigpapituna.sharpears.ui.family.LocalProfile
import com.bigpapituna.sharpears.ui.family.LocalProfiles
import com.bigpapituna.sharpears.ui.family.ParentGateController
import com.bigpapituna.sharpears.ui.family.ParentGateHost
import com.bigpapituna.sharpears.ui.family.ProfilePicker
import com.bigpapituna.sharpears.ui.family.familyWallEntries
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

const val TAG = "SharpEars"
/** The instance state's key for the open screen (see routeOf). */
private const val ROUTE = "screen"
/** The instance state's flag for "Who's playing today?" still waiting for an answer. */
private const val PICKER = "picker"
/** The saved instance state's key for the weekly brief the notification opened, while it is still open. */
private const val BRIEF = "brief"

/**
 * Chord games, chord practice and the ear games listen for right notes only: the matcher skips naming wrong keys while
 * one is on screen (cheaper, and nothing there paints them). Not stored; the old setting comes back on leaving.
 */
@androidx.compose.runtime.Composable
private fun QuietWrongKeys(mic: MicInput) = androidx.compose.runtime.DisposableEffect(mic) {
    val before = mic.matcher.config.nameWrongKeys
    mic.matcher.config = mic.matcher.config.copy(nameWrongKeys = false)
    onDispose { mic.matcher.config = mic.matcher.config.copy(nameWrongKeys = before) }
}

private sealed interface Screen {
    /** The tabs (a kid's map for a kid). [practiceTab] only matters for Practice. */
    data class Home(val tab: HomeTab = HomeTab.TODAY, val practiceTab: PracticeTab = PracticeTab.SCALES,
                    /** A kid's home (2.2) after a page's finale: the map opens at that page's end and rolls on to the next stone. */
                    val kidFrom: String? = null,
                    /** The Songs tab's section (2.7): the library, or songs by chords. */
                    val songs: com.bigpapituna.sharpears.ui.SongsSection = com.bigpapituna.sharpears.ui.SongsSection.LIBRARY,
                    /** Songs by chords: the style chip and sort it opens on (popular-song chord mode). */
                    val pop: com.bigpapituna.sharpears.ui.chords.PopStart = com.bigpapituna.sharpears.ui.chords.PopStart()) : Screen
    /** [meta] is null for generated songs (mic check, progressions). [back] is where Back goes. [band] is a pop song's drums and bass. */
    data class Player(val song: Song, val meta: SongMeta?, val start: PlayerStart, val startMs: Double, val back: Screen,
                      val band: com.bigpapituna.sharpears.engine.chords.BandBed? = null,
                      /** A pop song's chord names for the lane's Chords pill (library songs get theirs from the lesson store). */
                      val chords: com.bigpapituna.sharpears.engine.ChordTrack? = null) : Screen
    /**
     * A pop song's page (popular-song chord mode, ui/chords): its chords by section, key and tempo steppers, the pattern and
     * the band. [shift] is semitones from the song's own key, [pattern] and [bpm] what the learner chose (null: the song's own);
     * they come back from the player. [hear] and [kid] are debug: start the band as it opens, show the kid page.
     */
    data class PopSong(val songId: String, val back: Screen, val shift: Int = 0, val pattern: com.bigpapituna.sharpears.engine.chords.PopSong.Pattern? = null,
                       val bpm: Int? = null, val hear: Boolean = false, val kid: Boolean? = null, val pre: Boolean = false) : Screen
    /**
     * Learn chords (2.7, the top of Learn): the daily chord review, the dictionary as a sheet, free chord practice and the
     * packs ([start] opens the sheet or the practice). [inject] (debug) feeds the mic.
     */
    data class LearnChords(val back: Screen, val start: com.bigpapituna.sharpears.ui.LearnChordsStart = com.bigpapituna.sharpears.ui.LearnChordsStart.REVIEW,
                           val inject: String? = null) : Screen
    /** Games (2.7, the top of Learn): the chord games. [start] (debug) opens a game at a level, with an injection. */
    data class ChordGames(val back: Screen, val start: GameStart = GameStart()) : Screen
    /** Progressions (2.7, the top of Learn): hear the classics, practise one in the player. */
    data class Progressions(val back: Screen) : Screen
    /** A course lesson at step [step]. [inject] (debug) feeds the mic for that first step. */
    data class Lesson(val lesson: CourseLesson, val back: Screen, val step: Int = 0, val inject: String? = null, val midiScript: String? = null, val scene: Int = 0) : Screen
    data class MicSetup(val back: Screen) : Screen
    /** The sight-reading drill. [inject] (debug) feeds the mic. */
    data class SightRead(val back: Screen, val level: Int = 1, val inject: String? = null) : Screen
    /** "Practice today": part [index] of [plan]; [between] shows the one-line interstitial before it. */
    data class Daily(val plan: SessionPlan, val index: Int, val between: Boolean, val startedAt: Long, val done: List<SessionPart>) : Screen
    data class Privacy(val back: Screen) : Screen
    data class Licences(val back: Screen) : Screen
    /** Settings, pushed from the gear on You (it was a tab before 2.0). */
    data class Settings(val back: Screen) : Screen
    /** The ear training hub (Learn), or straight into a game or the workout with [start]. */
    data class EarTraining(val back: Screen, val start: EarStart? = null) : Screen
    /** First run: four questions, then the suggested lesson. */
    data object Onboarding : Screen
    /** "Let's hear you play" (2.6): the short listen after the piano level was said ([said]); [startTask] and [inject] are debug. */
    data class Placement(val said: com.bigpapituna.sharpears.engine.family.Level, val startTask: Int = 0, val inject: String? = null) : Screen
    data class Theory(val back: Screen) : Screen
    /** The pup's world (2.1): the places, the jar and egg, the album, dress-up and the records, from the You tab's card. */
    data class World(val back: Screen, val tab: WorldTab = WorldTab.WORLD) : Screen
    /** The play corner (2.1): the Jam, Pup says or Clap along. [hint] is the quest's line for the Jam ("find all the Cs"). */
    data class Play(val toy: Toy, val back: Screen, val hint: String? = null) : Screen
    /** A kid's songs (2.2, the kid home's Songs button): only the songs played by colour, big tiles. */
    data class KidSongs(val back: Screen) : Screen
    /** A kid's songs by chords (2.6, the "Songs by chords" tile on the kid's songs): the Kids style's chord songs, big tiles. */
    data class KidChords(val back: Screen) : Screen
    /** A kid's stickers (2.2, My stickers): the album, dress-up, records and paws as pages. */
    data class KidStickers(val back: Screen, val page: StickerPage = StickerPage.ALBUM) : Screen
    /** A Playground page's finale (2.2): fireworks, the album page, the certificate, then on along the path. */
    data class Finale(val pageId: String) : Screen
}

/** Practise together with nothing chosen (the parent brief, 2.4): the next song [whoId] picks opens as a duet with [partnerId]. */
private data class DuetNext(val whoId: String, val partnerId: String?)

/** The play corner's toy as the brief's log names it. */
private fun toyKind(t: Toy) = when (t) { Toy.JAM -> ToyKind.JAM; Toy.PUP_SAYS -> ToyKind.PUP_SAYS; Toy.CLAP -> ToyKind.CLAP_ALONG }

/** Order of screens for the transitions: deeper goes forward, shallower goes back, the player gets the curtain. */
private fun depthOf(s: Screen) = when (s) {
    is Screen.Home -> 0
    is Screen.Settings, is Screen.EarTraining, is Screen.Theory, Screen.Onboarding, is Screen.Placement, is Screen.World, is Screen.KidSongs, is Screen.KidChords, is Screen.KidStickers, is Screen.PopSong,
    is Screen.LearnChords, is Screen.ChordGames, is Screen.Progressions -> 1
    is Screen.Player, is Screen.Lesson, is Screen.SightRead, is Screen.Daily, is Screen.Privacy, is Screen.Licences, is Screen.MicSetup, is Screen.Play,
    is Screen.Finale -> 2
}

/**
 * The screen as one line for the saved instance state, the screens it goes back to first ("home:LEARN/lesson:2:find-c"),
 * so a recreate (a process the system stopped in the background, Don't keep activities, a dark mode or font size change)
 * reopens it, a lesson at its step, instead of home. Practice today and generated songs (the mic check, progressions,
 * practice patterns) save as where they go back to. Read by [MainActivity.screenAt].
 */
private fun routeOf(s: Screen): String = when (s) {
    is Screen.Home -> "home:${s.tab.name}" + if (s.songs == com.bigpapituna.sharpears.ui.SongsSection.BY_CHORDS) ":chords" else ""
    is Screen.Player -> if (s.meta == null) routeOf(s.back) else "${routeOf(s.back)}/song:${s.meta.id}"
    is Screen.Lesson -> "${routeOf(s.back)}/lesson:${s.step}:${s.lesson.id}"
    is Screen.MicSetup -> "${routeOf(s.back)}/micsetup"
    is Screen.SightRead -> "${routeOf(s.back)}/sight:${s.level}"
    is Screen.Daily -> "home:TODAY"
    is Screen.Privacy -> "${routeOf(s.back)}/privacy"
    is Screen.Licences -> "${routeOf(s.back)}/licences"
    is Screen.Settings -> "${routeOf(s.back)}/settings"
    is Screen.EarTraining -> "${routeOf(s.back)}/ear"
    Screen.Onboarding -> "onboarding"
    is Screen.Placement -> "home:TODAY"
    is Screen.Theory -> "${routeOf(s.back)}/theory"
    is Screen.World -> "${routeOf(s.back)}/world:${s.tab.name}"
    is Screen.Play -> "${routeOf(s.back)}/play:${s.toy.name}"
    is Screen.KidSongs -> "${routeOf(s.back)}/kidsongs"
    is Screen.KidChords -> "${routeOf(s.back)}/kidchords"
    is Screen.PopSong -> "${routeOf(s.back)}/popsong:${s.songId}:${s.shift}:${s.pattern?.name ?: "-"}:${s.bpm ?: 0}"
    is Screen.KidStickers -> "${routeOf(s.back)}/kidstickers:${s.page.name}"
    is Screen.Finale -> "finale:${s.pageId}"
    is Screen.LearnChords -> "${routeOf(s.back)}/learnchords"
    is Screen.ChordGames -> "${routeOf(s.back)}/chordgames"
    is Screen.Progressions -> "${routeOf(s.back)}/progressions"
}

/** The player is always dark, like a video; everything else follows the system. */
private fun playerShown(s: Screen) = s is Screen.Player ||
    (s is Screen.Daily && !s.between && s.plan.parts[s.index].let { it is SessionPart.Loop || it is SessionPart.Song })

/** The stage: the player, or a Practice today part that runs one. */
private fun isStage(s: Screen) = s is Screen.Player || (s is Screen.Daily && !s.between)

/** Screens played at the piano, hands off the phone for minutes: the screen stays on (see [KeepScreenOnWhile]). */
private fun keepsScreenOn(s: Screen) = s is Screen.Player || s is Screen.Placement || s is Screen.Lesson || s is Screen.Daily || s is Screen.SightRead ||
    s is Screen.Play || s is Screen.EarTraining || s is Screen.Theory || s is Screen.Finale

/**
 * The screen stays on while [stage] or while the mic listens (chord games, the tuner): a phone's 30 s timeout used to
 * switch the screen off under a child playing a lesson, which stopped the mic and the lesson with it.
 */
@androidx.compose.runtime.Composable
private fun KeepScreenOnWhile(mic: MicInput, stage: Boolean) {
    val on = stage || mic.source.value != null
    val view = androidx.compose.ui.platform.LocalView.current
    androidx.compose.runtime.DisposableEffect(view, on) { view.keepScreenOn = on; onDispose { view.keepScreenOn = false } }
}

class MainActivity : ComponentActivity() {
    private lateinit var sampler: Sampler
    private lateinit var midi: MidiDevices
    private lateinit var mic: MicInput
    private var openVoice: Voice? = null
    private val samplerReady = mutableStateOf(false)
    private lateinit var repo: SongRepository
    private lateinit var profiles: Profiles
    private lateinit var gate: ParentGateController
    /** The profile the app is open as (null while the picker shows). */
    private var openId: String? = null
    /** Kid or grown-up, as the app was opened (a switch of kind reopens it). */
    private var openKind: com.bigpapituna.sharpears.engine.family.ProfileKind? = null
    /** A kid reads or not, as the app was opened (a big kid's course path differs, so a change reopens it too). */
    private var openReader: Boolean? = null
    /** The piano level the open profile was placed with: a change re-plans the marks in place, with no reopen (see the profile flow). */
    private var openLevel: com.bigpapituna.sharpears.engine.family.Level? = null
    /** Launches of this activity's content ([openApp] with fromLaunch), so a new one forgets the last one's screen. */
    private var launches = 0
    /** The open screen as [routeOf] writes it, kept in the instance state; [restoreRoute] is the one a recreate reopens. */
    private var shownRoute: String? = null
    private var restoreRoute: String? = null
    /** The open profile's preferences and kid memory, so a parent brief action's change shows at once ([briefNav] changed). */
    private var openPrefs: Prefs? = null
    private var openKid: KidStore? = null
    /** The open profile's world, so a session's end brings its practice ledger up to date ([Briefs.onSessionEnd]). */
    private var openWorld: WorldStore? = null
    /** A screen a parent brief action opens ([briefNav]), taken by the open composition. */
    private val navRequest = mutableStateOf<Screen?>(null)
    /** Counts those, so a lesson an action opens again starts fresh (its LessonScreen is keyed by it). */
    private val navCount = mutableIntStateOf(0)
    /** A screen an action asked for someone who is not open: the app opens as them on it ([openApp]). */
    private var pendingOpen: Pair<String, Screen>? = null
    /** Practise together with nothing chosen: the next song picked opens as a duet. */
    private val duetNext = mutableStateOf<DuetNext?>(null)
    /** The weekly brief's notification asked for this person's brief and period (all builds; closed by Back). */
    private val briefRequest = mutableStateOf<Pair<String, com.bigpapituna.sharpears.engine.brief.Period>?>(null)
    /** Debug: that brief over sample findings offering every action (ui/brief/BriefDemo.kt, sampleActionsEnv). */
    private var briefSamples = false

    /**
     * The parent brief's doors into the app (ui/brief/AppEffects.kt, step 3 of the brief): Replay, Practise together and the
     * duet's song pick open screens for the child the brief is about, switching to them first when someone else is open,
     * and a change to someone's path (Set as next, Fresh start, Focus, Open the next page, Teacher mode) is read again by
     * the open map. Called from the action's worker thread: everything here runs on the main thread.
     */
    private val briefNav = object : com.bigpapituna.sharpears.ui.brief.BriefNav {
        override fun openLesson(who: Profile, lessonId: String) = runOnUiThread {
            val lesson = COURSE.flatMap { it.lessons }.firstOrNull { it.id == lessonId } ?: return@runOnUiThread
            Log.d(TAG, "brief: open lesson $lessonId for ${who.id}")
            // A grown-up chose it behind the gate: after today's goodnight it is their "one more lesson", as on the asleep screen.
            if (who.isKid) com.bigpapituna.sharpears.ui.skin.BedtimeStore(this@MainActivity, who.id).oneMoreOn = java.time.LocalDate.now()
            go(who, Screen.Lesson(lesson, if (who.isKid) Screen.Home() else Screen.Home(HomeTab.LEARN)))
        }

        override fun openSong(who: Profile, songId: String, duet: Boolean) = runOnUiThread {
            // A pop song's run ("pop-<id>", songs by chords): its page, where it is played along (no duet there).
            com.bigpapituna.sharpears.engine.chords.PopPlay.ofRun(songId, com.bigpapituna.sharpears.ui.chords.PopCatalogue.songs)?.let { pop ->
                Log.d(TAG, "brief: open pop song ${pop.id} for ${who.id}")
                val home = if (who.isKid) Screen.KidSongs(Screen.Home()) else Screen.Home(HomeTab.SONGS, songs = com.bigpapituna.sharpears.ui.SongsSection.BY_CHORDS)
                go(who, Screen.PopSong(pop.id, if (who.isKid) Screen.KidChords(home) else home))
                return@runOnUiThread
            }
            val m = repo.all().firstOrNull { it.id == songId && repo.available(it) } ?: return@runOnUiThread
            val song = runCatching { repo.load(m) }.getOrNull() ?: return@runOnUiThread
            val partner = if (duet) com.bigpapituna.sharpears.engine.play.Duet.grownUpFor(who, openId, profiles.all) else null
            Log.d(TAG, "brief: open song $songId for ${who.id}" + if (duet) " as a duet with ${partner ?: "a partner they pick"}" else "")
            go(who, Screen.Player(song, m, briefStart(who, Prefs(this@MainActivity, who.id), duet, partner), 0.0,
                if (who.isKid) Screen.KidSongs(Screen.Home()) else Screen.Home(HomeTab.SONGS)))
        }

        override fun pickSong(who: Profile, duet: Boolean) = runOnUiThread {
            duetNext.value = if (duet) DuetNext(who.id, com.bigpapituna.sharpears.engine.play.Duet.grownUpFor(who, openId, profiles.all)) else null
            Log.d(TAG, "brief: ${who.id} picks a song" + if (duet) " for a duet" else "")
            go(who, if (who.isKid) Screen.KidSongs(Screen.Home()) else Screen.Home(HomeTab.SONGS))
        }

        override fun changed(who: Profile) = runOnUiThread {
            if (who.id == openId) { openPrefs?.let { it.version.intValue++ }; openKid?.let { it.version.intValue++ } }
        }
    }

    /** How a song a brief action opens starts: a kid's duet with the grown-up on the left hand, a grown-up's with the picker open. */
    private fun briefStart(who: Profile, prefs: Prefs, duet: Boolean, partnerId: String?): PlayerStart {
        val micOn = prefs.micDefault && mic.hasPermission()
        return when {
            !duet -> PlayerStart(Mode.WAIT, mic = micOn)
            who.isKid && partnerId != null -> PlayerStart(Mode.WAIT, Practice.BOTH, mic = micOn, duet = com.bigpapituna.sharpears.engine.play.Duet(partnerId, who.id))
            else -> PlayerStart(Mode.WAIT, Practice.BOTH, mic = micOn, pickDuet = true)
        }
    }

    /** Opens [to] as [who]: at once when they are open, else after the app opens as them (the brief's dialog closes either way). */
    private fun go(who: Profile, to: Screen) {
        briefRequest.value = null
        navCount.intValue++
        if (who.id == openId) {
            if (who.isKid) gate.close()          // the phone goes back to the child: a grown-up's open gate does not stay open for them
            navRequest.value = to
        } else {
            pendingOpen = who.id to to
            val wasOpen = openId != null
            profiles.switchTo(who.id)            // the profile flow reopens the app as them, on [to]
            if (!wasOpen) openApp(fromLaunch = false)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        restoreRoute = savedInstanceState?.getString(ROUTE)
        // The weekly brief's notification (all builds): that person's brief opens over whatever shows first. After a recreate,
        // only what was still open then (the launch intent keeps its extras, and the brief may have been closed since).
        briefRequest.value = if (savedInstanceState != null) savedInstanceState.getString(BRIEF)?.let(::briefOf) else briefFrom(intent)
        briefSamples = applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0 && intent.getBooleanExtra("brief_samples", false)
        sampler = Sampler(this) { samplerReady.value = true }
        midi = MidiDevices(this).also { it.start() }
        mic = MicInput(this)
        repo = SongRepository(this)
        profiles = Profiles(this)
        gate = ParentGateController(profiles)
        Briefs.init(this)                                  // the parent brief's event logs, files/events/<profile>.log
        // Tester analytics (analytics/Tester.kt): PostHog starts only on a build with a key and "Help Roy test" on.
        com.bigpapituna.sharpears.analytics.Tester.init(this)
        Briefs.tap = com.bigpapituna.sharpears.analytics.Tester::briefEvent
        val debuggable = applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
        // --es profile <id> (debug): open as that profile, no picker.
        val debugProfile = intent.getStringExtra("profile")?.takeIf { debuggable }?.also(profiles::switchTo)
        // Review pipeline (private build): crashes are kept, a weekly summary is written, and whatever waits goes out.
        if (mic.keepRecordings) {
            val app = applicationContext      // read at crash time, never this activity (the handler outlives it)
            mic.reporter.catchCrashes(packageManager.getPackageInfo(packageName, 0).versionName ?: "?") { Profiles(app).all.map { it.name } }
            mic.reporter.weeklySummary(Prefs(this, Profile.OWNER_ID).practice())
            mic.reporter.flush()
        }
        com.bigpapituna.sharpears.reminder.Reminder.schedule(this)        // alarms do not survive an update or restart
        com.bigpapituna.sharpears.reminder.BriefReminder.scheduleAll(this) // the weekly brief's alarms, one per person, likewise
        // A session's end (the app leaves the screen, or someone else takes over): the wardrobe's ledger takes in the minutes
        // and days just played (whatever was opened waits for the next finish screen), and the brief by email
        // (reminder/EmailJob.kt) plans its next look. The app's start sends what is due and sets the alarm.
        Briefs.onSessionEnd = { id ->
            openWorld?.takeIf { it.profileId == id }?.let { w ->
                runCatching { w.sync() }.onFailure { Log.d(TAG, "wardrobe: ledger sync at the session's end failed: ${it.javaClass.simpleName}") }
            }
            com.bigpapituna.sharpears.reminder.EmailJob.afterSession(applicationContext)
            // The tester diary keeps the session's refusal and error lines before logcat rolls them away.
            if (com.bigpapituna.sharpears.analytics.Tester.switchOn.value || com.bigpapituna.sharpears.analytics.Tester.diaryOn.value) {
                val app = applicationContext
                Thread { com.bigpapituna.sharpears.analytics.Tester.collectLog(app) }.start()
            }
        }
        com.bigpapituna.sharpears.reminder.EmailJob.runAsync(this, "app start")
        // Someone else picked (the family wall, Settings > Family, the player deleted): the app opens again as them. So it
        // does when the player turns from kid to grown-up or back: the course path, the skin and the mic offset change.
        lifecycleScope.launch {
            profiles.currentFlow.map { listOf(it.id, it.kind, it.reader, it.level) }.distinctUntilChanged().collect {
                val now = profiles.current
                if (openId != null && (openId != now.id || openKind != now.kind || openReader != now.reader)) openApp(fromLaunch = false)
                // A new piano level (the onboarding, Family > Piano level) re-plans the open profile's marks where it stands: the
                // path and the map read the prefs, so nothing reopens and no screen is lost.
                else if (openId != null && openLevel != now.level) {
                    openLevel = now.level
                    openPrefs?.let { p -> p.applyPlacement(com.bigpapituna.sharpears.engine.family.Placement.planOf(now)); p.version.intValue++ }
                    openKid?.let { it.version.intValue++ }
                }
            }
        }
        // Two or more people: "Who's playing today?" first, unless a debug route or a recreate (rotation, import) opened us;
        // a recreate while it still asked (the process stopped in the background) asks again rather than open as the last player.
        val debugRoute = debuggable && (listOf("screen", "song", "lesson").any { intent.getStringExtra(it) != null } || intent.getBooleanExtra("daily", false))
        // The world's jingles and the note tick (one SoundPool for the app; Settings > Sound switches them with the rest).
        WorldSounds.init(this)
        if (profiles.roster.needsPicker && (savedInstanceState == null || savedInstanceState.getBoolean(PICKER)) && !debugRoute && debugProfile == null) showPicker()
        else openApp(fromLaunch = true)
    }

    /** The launch picker; picking (or adding someone) opens the app as them. */
    private fun showPicker() {
        Briefs.sessionEnd(); Briefs.currentId = null        // no one is playing yet: nothing is logged
        setContent {
            SharpEarsTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    val roster by profiles.rosterFlow.collectAsState()
                    var weekly by remember { mutableStateOf<com.bigpapituna.sharpears.engine.brief.WeeklySlot?>(null) }
                    var wantsCheck by remember { mutableStateOf(false) }
                    androidx.compose.runtime.CompositionLocalProvider(LocalProfiles provides profiles, LocalParentGate provides gate,
                        com.bigpapituna.sharpears.ui.brief.LocalBriefNav provides briefNav) {
                        ProfilePicker(roster,
                            onPick = { id -> profiles.switchTo(id); openApp(fromLaunch = true) },
                            onAdd = { p ->
                                val made = profiles.create(p)
                                // A kid's weekly brief: the day and time chosen at the end of the flow (Sunday at 6 pm unless changed),
                                // as Settings > Family does.
                                if (made != null && made.isKid) {
                                    com.bigpapituna.sharpears.data.BriefStore(this@MainActivity, made.id).weekly = weekly ?: com.bigpapituna.sharpears.engine.brief.WeeklySlot.DEFAULT
                                    com.bigpapituna.sharpears.reminder.BriefReminder.schedule(this@MainActivity, made)
                                }
                                if (made != null && wantsCheck) Prefs(this@MainActivity, made.id).placementCheckPending = true
                                wantsCheck = false
                                if (made != null) openApp(fromLaunch = true)
                            },
                            onWeekly = { weekly = it }, onCheck = { wantsCheck = it })
                        BriefFromNotification(roster, openKid = false)
                        ParentGateHost(gate)
                    }
                }
            }
        }
    }

    /**
     * Builds the current profile's stores and shows the app as them. [fromLaunch] honours the launch intent's debug
     * extras; a later switch opens at home. Everything per profile lives here, so a switch starts it fresh.
     */
    private fun openApp(fromLaunch: Boolean) {
        val profile = profiles.current
        val newPlayer = openId != profile.id
        // The phone handed to a kid: a gate a grown-up passed a minute ago does not stay open for them.
        if (profile.isKid && newPlayer) gate.close()
        openId = profile.id
        openKind = profile.kind
        openReader = profile.reader
        Log.d(TAG, "profiles: open as ${profile.id} (${profile.kind}, reader=${profile.reader})")
        com.bigpapituna.sharpears.analytics.Tester.profile(profile.isKid, profile.id)     // "kid" or "adult" only, never the name
        val intent = if (fromLaunch) intent else android.content.Intent()      // a switch carries no debug extras
        val prefs = Prefs(this, profile.id).also { it.logRestored() }
        // Placement (2.6): the lessons this person's level skips are marked "done, skipped" (idempotent; a played lesson is never touched).
        prefs.applyPlacement(com.bigpapituna.sharpears.engine.family.Placement.planOf(profile))
        openLevel = profile.level
        // The mic follows the player: a kid's offset and report tag for every mic screen (chords, ear games, tuner,
        // playground), and a new player starts with wrong keys painted again (the last one's room may have been noisier).
        mic.forProfile(profile.isKid, profile.micOffsetDb, newSession = newPlayer)
        openVoice?.release()
        val voice = Voice(this, prefs, mic).also { openVoice = it }   // the speech engine itself starts on the first line
        voice.speed = if (profile.isKid) 0.85f else 1f              // big and slow for kids (KIDS-PLAN 3.8)
        val lessons = LessonStore(this, profile.id)
        val progress = ProgressStore(this, prefs, songIds = { repo.all().map { it.id } })
        progress.catchUp()                             // once ever: XP for practice, lessons and songs from before 2.0, quietly
        // The parent brief's log follows the player: a switch ends the last one's session (with their practice) and, on
        // screen, starts this one's; events from here on go to this profile's log.
        if (Briefs.currentId != profile.id) Briefs.sessionEnd()
        Briefs.practiceSeconds = { progress.practiceSecondsTotal }
        Briefs.currentId = profile.id
        if (lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)) Briefs.sessionStart(profile.id)
        // Someone who already played (XP from catchUp or since) skips the first-run questions.
        if (!progress.onboardingDone && progress.xp > 0) progress.onboardingDone = true
        // A kid goes straight in: no questions, the goal the profile was made with, and the first game opens at once.
        val newKid = profile.isKid && !progress.onboardingDone
        if (newKid) { progress.goalMinutes = profile.dailyGoalMin; progress.onboardingDone = true }
        // Kids read the keys by their pads: every white key labelled (a letter, or an animal before reading), until a grown-up picks.
        if (profile.isKid && !prefs.keyLabelsChosen) prefs.keyLabels = com.bigpapituna.sharpears.data.KeyLabels.ALL
        // Kids skip the onboarding that asks about the mic, so their lessons' player steps, concerts and songs opened
        // with it off and heard nothing a kid played (the stages listen anyway). On until someone switches it off.
        if (profile.isKid && !prefs.micDefaultChosen) prefs.micDefault = true
        // The pup's world for this profile: stickers, the jar every note feeds, the outfit; the shelf starts with old bests.
        val world = WorldStore(this, profile.id)
        world.mergeRecords(repo.all().associate { it.id to prefs.progress(it.id).bestAccuracy }.filterValues { it >= 0 })
        Juice.world = world
        WorldSounds.on = prefs.sound
        val playStore = PlayStore(this, profile.id)
        // The kid home's memory (2.2): each lesson's best stars, the finales played (kept for grown-ups too, never shown).
        val kidStore = KidStore(this, profile.id)
        openPrefs = prefs; openKid = kidStore; openWorld = world
        // 2.5, the wardrobe's practice ledger: minutes and days from the practice log, three-star lessons and pages walked
        // from the kid store (a page whose last lesson is done counts at once: its rare item shows on that lesson's finish).
        world.ledgerSource = {
            com.bigpapituna.sharpears.engine.world.LedgerInput(progress.practiceLog(), kidStore.allStars().filterValues { it >= 3 }.keys,
                kidStore.finales() + PLAYGROUND_PAGES.filter { it.lessonIds.isNotEmpty() && prefs.lessonPlayed(it.lastLessonId) }.map { it.id })
        }
        fun quest(e: QuestEvent) = playStore.recordQuest(e, profile.isKid).forEach { Log.d(TAG, "quest: ${it.kind} done ($e)") }
        // The course in this person's order: a kid's Playground first, a grown-up's Basics (the Playground an extra).
        val path = CoursePath(profile.firstUnitId, bigKid = profile.isKid && profile.reader,
            startAt = { com.bigpapituna.sharpears.engine.family.Placement.startLessonId(profiles.current) ?: progress.experience?.startLessonId })
        val bedtime = com.bigpapituna.sharpears.ui.skin.BedtimeStore(this, profile.id)
        val practiceStore = PracticeStore(this, profile.id)
        val earStore = EarStore(this, profile.id)      // read for the Learn row and reset from Settings; the hub keeps its own
        val theory = TheoryStore(this, profile.id)     // one store for the Learn card and the academy, so the card updates
        val micCheck = micCheckExercise()
        if (fromLaunch && applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) kidDebugSeed(intent, prefs, kidStore, progress, bedtime)
        // Stones finished before 2.5 earn their own stickers into the tray; the ledger catches up with what was played.
        world.earnLevels(prefs::lessonPlayed)
        world.sync()
        if (fromLaunch && applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) worldDebugSeed(intent, world)

        // Debug builds accept extras to open a screen directly:
        //   --es song <id>|mic-check [--el seek <ms>] [--ez dump true] [--ez tabletop true]
        //   [--es mode wait|perform] [--es hands left|right] [--ez mic true] [--es inject <wav path>|synth:76,75,60+64+67@600]
        //   --es song prog:<index>:<key 0-11>   (a chord progression, e.g. prog:0:7 = I-V-vi-IV in G)
        //   --es screen chords|settings|learn|profile [--es tab play|daily|dictionary|practice|progressions] [--es inject ...]
        //   --es screen practice [--es tab scales|arpeggios|warmups|metronome|tuner]
        //   --es screen ear [--es game rhythm|melody|pitch|degree|prog --ei level 0-2 | --ez workout true]
        //   --es screen onboarding   (the first-run flow, whatever was answered before)
        //   --es screen placement [--es said A_LITTLE|SOME|A_LOT] [--ei task n] [--es inject ...]   (2.6, "Let's hear you play"; the level is not changed unless it is played through)
        //   --es screen chords --es game build|hear|mm [--ei level 0-2] [--es inject ...]   (a chord game, seeded)
        //   --es lesson <course lesson id> [--ei step <0-based step>] [--es inject ...]
        //   --es say "<text>"   (speaks the line 1.5 s after launch, through the mic rule)
        //   --ef lightSlow <n>   (stretches the hit-line lights n times, to catch them in a screenshot)
        //   --ef fxSlow <n>      (2.1.1: the same for the Playground stages' rings and call-outs and the Jam's captions)
        //   --es screen jam --es midi "3000:60/90,3010:64/90"   (2.1.1: a pretend MIDI keyboard into the Jam: ms, key/velocity)
        //   --ez daily true   (starts "Practice today" straight away)
        //   --es screen sight [--ei level 1|2] [--es inject ...]   (sight-reading; seeded when injecting)
        //   --es lesson <id> --es midi "800:p1 4790:p0 1000:60/40"   (a pretend MIDI keyboard: pedal and key/velocity at ms after Play)
        //   --es profile <id>   (open as that profile, skipping the picker; combine with any route above)
        //   --es screen world [--es tab world|album|dress|records]   (this profile's world)
        //   --es screen jam|pupsays|clap [--es hint "<line>"]   (the play corner)
        //   --es screen worlddemo --es part album|world|map|jar|egg|dress|reveal|shelf|idle|onemore ...   (a demo world, ui/world/WorldDemo.kt)
        //   --es screen skin --es view lane|wrong|recap|goodbye|asleep|paws|bite ...   (the kid skin's demo, ui/skin/SkinDemo.kt)
        //   --es screen motion [--es demo celebrate|cheer|pup|keys]   (the motion demo over home)
        //   --es lesson playground-<n> [--ei step n] [--es inject synth:...|claps:400,1150|dyn:60/-20,60/-8@1600~1500]
        //   2.2, the kid home (open as a kid with --es profile <id>):
        //   --es screen kidsongs | kidstickers [--es page album|dress|records|paws] | finale [--es page garden]
        //   --es kidseed <page>:<n>|road|reset   (marks the page's first n lessons done with stars; road: every page walked and
        //       its finale played; reset: the Playground's lessons, stars and finales forgotten) [--ez sleepy true] (today's goal
        //       reached and said goodnight to, so the map shows the pup asleep) [--ei practice <minutes>] (practice added today)
        val debuggable = applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
        val openSong = intent.getStringExtra("song")?.takeIf { debuggable }
        val debugStart = PlayerStart(
            mode = when (intent.getStringExtra("mode")) { "wait" -> Mode.WAIT; "perform" -> Mode.PERFORM; else -> Mode.LISTEN },
            practice = when (intent.getStringExtra("hands")) { "left" -> Practice.LEFT; "right" -> Practice.RIGHT; else -> Practice.BOTH },
            mic = intent.getBooleanExtra("mic", false),
            inject = intent.getStringExtra("inject")?.takeIf { debuggable },
            tabletop = debuggable && intent.getBooleanExtra("tabletop", false),
            lightSlow = if (debuggable) intent.getFloatExtra("lightSlow", 1f) else 1f,
        )
        com.bigpapituna.sharpears.ui.playground.FxSlow.factor = if (debuggable) intent.getFloatExtra("fxSlow", 1f).coerceIn(1f, 20f) else 1f
        val seekMs = intent.getLongExtra("seek", 0L).toDouble()
        val dump = debuggable && intent.getBooleanExtra("dump", false)
        val route = intent.getStringExtra("screen")?.takeIf { debuggable }
        // A debug route with a bad value (an unknown lesson or song, "prog:x") opens home instead of crashing the launch.
        val first: Screen = runCatching { when {
            // 2.7: the Chords tab's places now sit at the top of Learn; the old tab names still open them.
            route == "chords" || route == "learnchords" -> {
                // --es pack <id>: start a chord session on that pack, locked or not (to see its items), in Learn chords.
                val pack = intent.getStringExtra("pack")?.let { id -> lessons.curriculum().packs.firstOrNull { p -> p.id == id } }
                pack?.let { lessons.startSession(it.items) }
                val learn = Screen.Home(HomeTab.LEARN)
                val tab = intent.getStringExtra("tab") ?: if (route == "learnchords" || pack != null) "daily" else "play"
                when (tab) {
                    "dictionary" -> Screen.LearnChords(learn, com.bigpapituna.sharpears.ui.LearnChordsStart.DICTIONARY)
                    "practice" -> Screen.LearnChords(learn, com.bigpapituna.sharpears.ui.LearnChordsStart.PRACTICE, debugStart.inject)
                    "daily", "lessons" -> Screen.LearnChords(learn, inject = debugStart.inject)
                    "progressions" -> Screen.Progressions(learn)
                    "songs" -> Screen.Home(HomeTab.SONGS, songs = com.bigpapituna.sharpears.ui.SongsSection.BY_CHORDS)
                    else -> Screen.ChordGames(learn, GameStart(GameKind.entries.firstOrNull { it.id == intent.getStringExtra("game") }, intent.getIntExtra("level", 0), debugStart.inject))
                }
            }
            route == "games" -> Screen.ChordGames(Screen.Home(HomeTab.LEARN),
                GameStart(GameKind.entries.firstOrNull { it.id == intent.getStringExtra("game") }, intent.getIntExtra("level", 0), debugStart.inject))
            route == "progressions" -> Screen.Progressions(Screen.Home(HomeTab.LEARN))
            route == "popsongs" -> {
                // --es screen popsongs [--es chip all|hits|yacht|80s|pop|rock|country|soul|kids|holiday] [--es sort easiest|newest|can|one]
                //   [--es song <id> [--ei shift n] [--es pattern WHOLE|HALVES|...] [--ei bpm n] [--ez hear true] [--ez kid true]]
                //   [--ez kid true]   (no song: a kid's chord songs, from the kid's Songs)
                //   [--es view cards]   ("Songs that use these chords", over a plain page)
                //   [--es known C,F,G,Am]   (those chords as known, Leitner box 3, before it opens)
                intent.getStringExtra("known")?.let { k -> lessons.debugKnow(k.split(',').map { it.trim() }.filter { it.isNotEmpty() }, PopFind.KNOWN_BOX) }
                val chipArg = intent.getStringExtra("chip") ?: "all"
                val chip = PopFind.Chip.entries.firstOrNull { c -> c.name.startsWith(chipArg.uppercase()) || c.label.startsWith(chipArg, ignoreCase = true) } ?: PopFind.Chip.ALL
                val sort = when (intent.getStringExtra("sort")) { "newest" -> PopFind.Sort.NEWEST; "can" -> PopFind.Sort.CAN_PLAY; "one" -> PopFind.Sort.ONE_AWAY; else -> PopFind.Sort.EASIEST }
                val home = Screen.Home(HomeTab.SONGS, songs = com.bigpapituna.sharpears.ui.SongsSection.BY_CHORDS, pop = com.bigpapituna.sharpears.ui.chords.PopStart(chip, sort))
                val id = intent.getStringExtra("song")
                if (intent.getStringExtra("view") == "cards") return setContent {
                    SharpEarsTheme(dark = isSystemInDarkTheme()) { com.bigpapituna.sharpears.ui.chords.PopCardsDemo(lessons) } }
                if (id == null) { if (intent.getBooleanExtra("kid", false)) Screen.KidChords(Screen.KidSongs(Screen.Home())) else home }
                else Screen.PopSong(id, home, intent.getIntExtra("shift", 0),
                    com.bigpapituna.sharpears.engine.chords.PopSong.Pattern.entries.firstOrNull { it.name.equals(intent.getStringExtra("pattern"), ignoreCase = true) },
                    intent.getIntExtra("bpm", 0).takeIf { it > 0 }, intent.getBooleanExtra("hear", false),
                    if (intent.hasExtra("kid")) intent.getBooleanExtra("kid", false) else null, intent.getBooleanExtra("pre", false))
            }
            route == "settings" -> Screen.Settings(Screen.Home(HomeTab.YOU))
            route == "learn" -> Screen.Home(HomeTab.LEARN)
            route == "profile" || route == "you" -> Screen.Home(HomeTab.YOU)
            route == "onboarding" -> Screen.Onboarding
            // --es screen placement [--es said A_LITTLE|SOME|A_LOT] [--ei task n] [--es inject synth:...] (2.6): "Let's hear you play"
            route == "placement" -> Screen.Placement(com.bigpapituna.sharpears.engine.family.Level.parse(intent.getStringExtra("said") ?: "SOME"), intent.getIntExtra("task", 0), debugStart.inject)
            route == "practice" -> Screen.Home(HomeTab.PRACTICE,
                practiceTab = PracticeTab.entries.firstOrNull { it.name.equals(intent.getStringExtra("tab"), ignoreCase = true) } ?: PracticeTab.SCALES)
            route == "ear" -> Screen.EarTraining(Screen.Home(HomeTab.LEARN), when {
                intent.getBooleanExtra("workout", false) -> EarStart(workout = true)
                else -> intent.getStringExtra("game")?.let(EarKind::byId)?.let { EarStart(it, intent.getIntExtra("level", 0)) }
            })
            route == "theory" -> Screen.Theory(Screen.Home(HomeTab.LEARN))
            route == "sight" -> Screen.SightRead(Screen.Home(HomeTab.LEARN), intent.getIntExtra("level", 1), debugStart.inject)
            route == "world" -> Screen.World(Screen.Home(HomeTab.YOU),
                WorldTab.entries.firstOrNull { it.name.equals(intent.getStringExtra("tab"), ignoreCase = true) } ?: WorldTab.WORLD)
            route == "kidsongs" -> Screen.KidSongs(Screen.Home())
            route == "kidstickers" -> Screen.KidStickers(Screen.Home(),
                StickerPage.entries.firstOrNull { it.name.equals(intent.getStringExtra("page"), ignoreCase = true) } ?: StickerPage.ALBUM)
            route == "finale" -> Screen.Finale(intent.getStringExtra("page") ?: PLAYGROUND_PAGES.first().id)
            route == "jam" || route == "pupsays" || route == "clap" ->
                Screen.Play(when (route) { "jam" -> Toy.JAM; "pupsays" -> Toy.PUP_SAYS; else -> Toy.CLAP }, Screen.Home(), intent.getStringExtra("hint"))
            // The teams' effect harnesses, each in its own demo state (never a real profile's world or progress).
            route == "worlddemo" -> return setContent { com.bigpapituna.sharpears.ui.world.WorldDemo(intent) { finish() } }
            route == "brief" -> return setContent { com.bigpapituna.sharpears.ui.brief.BriefDemo(intent) { finish() } }
            route == "email" -> return setContent { com.bigpapituna.sharpears.ui.brief.email.EmailDemo(intent, profile) { finish() } }
            route == "skin" -> return setContent { com.bigpapituna.sharpears.ui.skin.SkinDemo(intent, sampler, midi, mic, voice, prefs) { samplerReady.value } }
            debuggable && intent.getStringExtra("lesson") != null ->
                COURSE.flatMap { it.lessons }.first { it.id == intent.getStringExtra("lesson") }.let {
                    Screen.Lesson(it, Screen.Home(HomeTab.LEARN), intent.getIntExtra("step", 0), debugStart.inject, intent.getStringExtra("midi"), intent.getIntExtra("scene", 0))
                }
            openSong == null -> Screen.Home()
            openSong == MIC_CHECK_ID -> Screen.Player(micCheck, null, debugStart, seekMs, Screen.Home())
            openSong.startsWith("prog:") -> openSong.split(":").let { (_, i, k) ->
                Screen.Player(progressionSong(PROGRESSIONS[i.toInt()], k.toInt()), null, debugStart, seekMs, Screen.Progressions(Screen.Home(HomeTab.LEARN)))
            }
            else -> repo.all().first { it.id == openSong }.let { m -> Screen.Player(repo.load(m), m, debugStart, seekMs, Screen.Home()) }
        } }.getOrElse { Log.d(TAG, "debug route failed, opening home: $it"); Screen.Home() }

        val startDaily = debuggable && intent.getBooleanExtra("daily", false)
        // First run: the onboarding questions, unless a debug route asked for a screen. A new kid skips them and lands in
        // the first Playground game (Back from it goes to the Learn path).
        val opening = when {
            // 2.6: a parent asked for "Let's hear you play" when adding this kid: it comes before the first stone, once.
            newKid && first == Screen.Home() && !startDaily && prefs.placementCheckPending && com.bigpapituna.sharpears.engine.family.PlacementCheck.offered(profile.level.forKid) -> {
                prefs.placementCheckPending = false
                Screen.Placement(profile.level.forKid)
            }
            newKid && first == Screen.Home() && !startDaily ->
                KidMap.next(path, prefs::lessonDone, kidStore.finales(), pace = kidStore.pace(), stars = kidStore::stars)?.let { Screen.Lesson(it, Screen.Home(HomeTab.LEARN)) } ?: Screen.Home()
            !progress.onboardingDone && first == Screen.Home() && !startDaily -> Screen.Onboarding
            else -> first
        }
        if (newKid) Log.d(TAG, "profiles: new kid ${profile.id} opens on ${(opening as? Screen.Lesson)?.lesson?.id}")
        // A recreate reopens the screen that was open (see routeOf); a route that no longer resolves opens as a launch would.
        val reopened = restoreRoute?.takeIf { fromLaunch }?.let { r ->
            runCatching { screenAt(r, prefs, profile.isKid) }.onSuccess { Log.d(TAG, "screen restored: $r") }
                .onFailure { Log.d(TAG, "screen not restored ($r): $it") }.getOrNull()
        }
        restoreRoute = null
        // A parent brief action for this person, asked while someone else was open ([briefNav]): the app opens on its screen.
        val requested = pendingOpen?.takeIf { it.first == profile.id }?.second
        pendingOpen = null
        // --es midi "<script>": a pretend keyboard plays into the first lesson step (see MidiDevices.debugPlay).
        if (debuggable && intent.getStringExtra("midi") != null) midi.debugConnect()
        intent.getStringExtra("say")?.takeIf { debuggable }?.let { line ->
            android.os.Handler(mainLooper).postDelayed({ voice.speak(line) }, 1500)
        }

        // A launch (onCreate, the picker, a debug route through onNewIntent) starts a fresh composition; a profile's kind
        // changing keeps the screen it is on.
        val opened = if (fromLaunch) ++launches else launches
        setContent { androidx.compose.runtime.key(profile.id, opened) {
            var screen by remember { mutableStateOf(requested ?: reopened ?: opening) }
            androidx.compose.runtime.SideEffect { shownRoute = routeOf(screen); com.bigpapituna.sharpears.analytics.Tester.screen(shownRoute!!) }
            // A parent brief action's screen for the person open now ([briefNav]: Replay, Practise together).
            val asked = navRequest.value
            LaunchedEffect(asked) { if (asked != null) { voice.stop(); screen = asked; navRequest.value = null } }
            // Practise together with nothing chosen waits on the songs; leaving them without picking one drops it.
            LaunchedEffect(screen) {
                val sc = screen
                val onSongs = sc is Screen.KidSongs || sc is Screen.Player || (sc is Screen.Home && sc.tab == HomeTab.SONGS && !profile.isKid)
                if (!onSongs && duetNext.value != null) { duetNext.value = null; Log.d(TAG, "brief: the duet's song pick was left") }
            }
            KeepScreenOnWhile(mic, stage = keepsScreenOn(screen))
            val current by profiles.currentFlow.collectAsState()
            val roster by profiles.rosterFlow.collectAsState()
            // Every screen can report XP events through LocalProgress, and read who is playing through LocalProfile; the
            // pup's world (and the outfit every base-pose pup wears) through LocalWorld.
            androidx.compose.runtime.CompositionLocalProvider(LocalProgress provides progress, LocalProfile provides current,
                LocalProfiles provides profiles, LocalParentGate provides gate, LocalWorld provides world, LocalPupOutfit provides world.state.outfit,
                com.bigpapituna.sharpears.ui.brief.LocalBriefNav provides briefNav) {
            SharpEarsTheme(dark = playerShown(screen) || isSystemInDarkTheme()) {
                // Every touch anywhere wakes the pups (their idle clock restarts); it takes nothing from the screens.
                Surface(Modifier.fillMaxSize().paper().wakesPup(), color = androidx.compose.ui.graphics.Color.Transparent,
                    contentColor = MaterialTheme.colorScheme.onBackground) {   // textured page (ui/Backdrop.kt)
                    var libraryVersion by remember { mutableIntStateOf(0) }       // bumps after an import
                    var importError by remember { mutableStateOf<String?>(null) }
                    var importInto by remember { mutableStateOf<SongMeta?>(null) }
                    // "Practice today" just finished: parts done, minutes, days in a row, for the celebration.
                    var dailyDone by remember { mutableStateOf<Triple<Int, Long, Int>?>(null) }
                    val scope = rememberCoroutineScope()
                    val songs = remember(libraryVersion) { repo.all() }

                    // Each library song yields a "Chords of <song>" lesson pack (DESIGN 4.3), labeled off the main thread.
                    // The same bar labels drive the chord names on the falling notes (ChordTrack per song id).
                    LaunchedEffect(libraryVersion) {
                        val labeled = withContext(Dispatchers.Default) {
                            songs.filter(repo::available).mapNotNull { m ->
                                runCatching {
                                    val song = repo.load(m)
                                    val bars = labelBarsAligned(song)
                                    Triple(m.id, songPack(song, bars = bars), ChordTrack(bars, barMs(song)))
                                }.onFailure { Log.d(TAG, "song pack ${m.id}: $it") }.getOrNull()
                            }
                        }
                        lessons.songPacks.value = labeled.map { it.second }.filter { it.items.isNotEmpty() }
                        lessons.songChords.value = labeled.filter { !it.third.isEmpty }.associate { it.first to it.third }
                        Log.d(TAG, "lessons: ${lessons.songPacks.value.size} song packs " + lessons.songPacks.value.joinToString { "${it.id}=${it.items.size}" })
                    }

                    fun openSong(m: SongMeta, back: Screen = Screen.Home()) {
                        val song = try { repo.load(m) } catch (e: Exception) { importError = "Could not open ${m.title}: ${e.message}"; return }
                        val p = prefs.progress(m.id)
                        prefs.lastSongId = m.id; prefs.lastPlayed = com.bigpapituna.sharpears.engine.nav.ContinueWith.encode(com.bigpapituna.sharpears.engine.nav.ContinueWith.Song(m.id))
                        Log.d(TAG, "song ${song.id}: ${song.notes.size} notes, ${song.groups.size} groups, ${song.durationMs} ms, " +
                            "right=${song.notes.count { it.hand == Hand.RIGHT }} left=${song.notes.count { it.hand == Hand.LEFT }} resume=${p.lastPositionMs}")
                        // Practise together (the parent brief) with nothing chosen: the song picked now is the duet.
                        duetNext.value?.takeIf { it.whoId == profile.id }?.let { d ->
                            duetNext.value = null
                            screen = Screen.Player(song, m, briefStart(profile, prefs, duet = true, partnerId = d.partnerId), 0.0, back)
                            return
                        }
                        // A kid starts a two-hand song on the tune (the right hand), as the grown-ups' hands tip advises.
                        val hands = if (profile.isKid && song.notes.any { it.hand == Hand.RIGHT }) Practice.RIGHT else Practice.BOTH
                        screen = Screen.Player(song, m, PlayerStart(Mode.WAIT, hands, mic = prefs.micDefault && mic.hasPermission()),
                            if (p.completed) 0.0 else p.lastPositionMs.toDouble(), back)
                    }
                    // "Practice today": due chords, the next lesson, then the Continue song's worst bar (or the song).
                    fun continueMeta() = songs.firstOrNull { it.id == prefs.lastSongId && repo.available(it) }
                        ?: songs.firstOrNull { it.difficulty == "easy" && repo.available(it) }
                    // A kid who reached today's goal hears goodnight once a run or a part is over (never mid-run; lessons
                    // say it themselves): the pup yawns, today's paw lands, songs and the Jam stay open.
                    var goodbye by remember { mutableStateOf(false) }
                    // A kid who finished Beginner is offered grown-up mode once, on their Learn path.
                    var grownUpOffered by remember { mutableStateOf(bedtime.grownUpOffered) }
                    fun maybeGoodnight(after: String): Boolean {
                        val today = java.time.LocalDate.now()
                        val sec = progress.secondsToday(today)
                        // The goal itself only earns a cheer; bedtime is a parental control (off by default).
                        if (!bedtime.bedtime && com.bigpapituna.sharpears.engine.skin.Bedtime.sayWellDone(profile.isKid, sec, progress.goalMinutes, bedtime.wellDoneOn, today)) {
                            bedtime.wellDoneOn = today
                            voice.speak(com.bigpapituna.sharpears.engine.SessionWords.wellDone(profile.name))
                            Log.d(TAG, "bedtime: goal cheered after $after")
                        }
                        if (!com.bigpapituna.sharpears.engine.skin.Bedtime.sayGoodnight(profile.isKid, sec, progress.goalMinutes, bedtime.goodnightOn, today, bedtime.bedtime)) return false
                        bedtime.goodnightOn = today
                        goodbye = true
                        Log.d(TAG, "bedtime: goodnight after $after")
                        return true
                    }
                    // A kid finished a Playground page's last lesson (2.2): its finale plays when they leave the lesson.
                    var pendingFinale by remember { mutableStateOf<String?>(null) }
                    // A sticker tapped in My stickers' tray (2.5): the map opens in decorate mode holding it.
                    var decorateWith by remember { mutableStateOf<String?>(null) }
                    // A course lesson finished: progress, today's quest, its stars on the stone, and the pup's world (a sticker from
                    // the lesson's page; the egg laid or hatched).
                    fun lessonDone(l: CourseLesson, stars: Int): Reward {
                        prefs.markLessonDone(l.id); progress.record(ProgressEvent.LessonComplete(l.id))
                        kidStore.lessonPlayed(l.id)          // a lesson pinned as the next stone (the parent brief) has been played
                        quest(QuestEvent.LessonDone(l.id))
                        kidStore.recordStars(l.id, stars)
                        if (profile.isKid) Finale.trigger(l.id, kidStore.finales())?.let { page ->
                            pendingFinale = page.id
                            Log.d(TAG, "kid: ${l.id} ends ${page.id}, its finale is next")
                        }
                        return world.lessonComplete(l.id, KidPages.dropPlace(l.id))
                    }
                    // The next lesson to suggest: a kid's next stone on the map, a grown-up's Learn path; a lesson a grown-up pinned
                    // from the parent brief comes first while its day lasts (engine/kid/Pace.kt).
                    fun nextLesson(): CourseLesson? = if (profile.isKid) KidMap.next(path, prefs::lessonDone, kidStore.finales(), pace = kidStore.pace(), stars = kidStore::stars)
                        else kidStore.pace().pin?.let(path::lesson) ?: path.next { prefs.lessonDone(it) }
                    // Where Songs is: the kid's own list for a kid, the Songs tab for a grown-up.
                    fun songsScreen(back: Screen = Screen.Home()): Screen = if (profile.isKid) Screen.KidSongs(back) else Screen.Home(HomeTab.SONGS)
                    fun todayPlan(): SessionPlan {
                        val queue = lessons.curriculum().queue(lessons.mastery(), System.currentTimeMillis())
                        // After a kid's goodnight the lessons sleep until tomorrow: no lesson part (songs stay open).
                        val asleep = profile.isKid && bedtime.goodnightOn == java.time.LocalDate.now()
                        val next = if (asleep) null else nextLesson()
                        // A kid's session is short (KIDS-PLAN 3.9): the next game or lesson and a whole song, no chord
                        // review and no bar on a loop.
                        val song = continueMeta()?.let { m ->
                            val minutes = runCatching { repo.load(m).durationMs / 60_000.0 }.getOrDefault(3.0)
                            SessionSong(m.id, m.title, minutes, if (profile.isKid) null else prefs.troubleBars(m.id).firstOrNull())
                        }
                        val chords = if (profile.isKid) emptyList() else queue.filterIsInstance<LessonItem.ChordItem>().map { it.id }
                        return planSession(chords, next?.let { it.id to it.title }, song)
                    }
                    fun startDaily() {
                        val plan = todayPlan()
                        Log.d(TAG, "daily: plan ${plan.aboutLabel}: " + plan.parts.joinToString { it.done })
                        if (!plan.isEmpty) screen = Screen.Daily(plan, 0, between = true, startedAt = System.currentTimeMillis(), done = emptyList())
                    }
                    LaunchedEffect(Unit) { if (startDaily) startDaily() }
                    fun finishDaily(s: Screen.Daily, done: List<SessionPart>) {
                        val secs = (System.currentTimeMillis() - s.startedAt) / 1000
                        // Nothing done (every part skipped or left at once): no summary, the panel stays as it was.
                        if (done.isEmpty()) { Log.d(TAG, "daily: ended with nothing done"); screen = Screen.Home(); return }
                        val summary = sessionSummary(done, secs)
                        prefs.saveDailySummary(java.time.LocalDate.now(), summary)
                        progress.record(ProgressEvent.DailySession(done.size))
                        Log.d(TAG, "daily: $summary")
                        voice.speak(if (profile.isKid) com.bigpapituna.sharpears.engine.SessionWords.kidSessionDone(profile.name)
                            else "Done for today: " + summary.removePrefix("Done today: ").replace(" min.", " minutes."))
                        dailyDone = Triple(done.size, maxOf(1L, (secs + 30) / 60), progress.streak().streak)
                        screen = Screen.Home()
                    }
                    fun advanceDaily(s: Screen.Daily, completed: Boolean, as_: SessionPart? = null) {
                        val part = as_ ?: s.plan.parts[s.index]
                        val done = if (completed) s.done + part else s.done
                        Log.d(TAG, "daily: part ${s.index + 1} ${if (completed) "done" else "left"}: ${part.done}")
                        // A kid's goal reached with this part: the session ends on goodnight (a lesson says it itself).
                        if (completed && part !is SessionPart.Lesson && maybeGoodnight("a Practice today part")) finishDaily(s, done)
                        else if (s.index + 1 >= s.plan.parts.size) finishDaily(s, done)
                        else screen = s.copy(index = s.index + 1, between = true, done = done)
                    }

                    // Ear training: a tick per round, a game's credit at its end, the workout like a game plus its practice time.
                    fun earEvent(e: EarEvent) {
                        fun practised(secs: Int) {
                            if (secs <= 0) return
                            prefs.addPractice(java.time.LocalDate.now(), secs)
                            progress.record(ProgressEvent.Practice(java.time.LocalDateTime.now(), secs))
                        }
                        when (e) {
                            is EarEvent.Round -> {
                                progress.record(ProgressEvent.EarRound(e.clean))
                                Briefs.log { BriefEvent.EarRound(it, e.kind.id, e.clean, e.level) }
                                if (e.kind == EarKind.MELODY && e.clean) quest(QuestEvent.PupCopied(0))     // copying the pup, by ear
                            }
                            is EarEvent.GameComplete -> { practised(e.seconds); progress.record(ProgressEvent.GameComplete("ear:" + e.kind.id, e.level, e.score, e.rounds)) }
                            is EarEvent.Workout -> { practised(e.seconds); progress.record(ProgressEvent.GameComplete("ear:workout", 0, e.score, e.rounds)) }
                        }
                        Log.d(TAG, "ear: $e -> xp ${progress.xp}")
                    }

                    val pickMidi = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                        val into = importInto
                        if (uri == null) return@rememberLauncherForActivityResult
                        scope.launch {
                            try {
                                val m = withContext(Dispatchers.IO) { repo.import(uri, into) }
                                Log.d(TAG, "import ok ${m.id} -> ${m.file}")
                                progress.record(ProgressEvent.FeatureUsed("import"))
                                libraryVersion++
                            } catch (e: ImportException) {
                                Log.d(TAG, "import failed: ${e.message}"); importError = e.message
                            } catch (e: Exception) {
                                Log.d(TAG, "import failed: $e"); importError = "Import failed: ${e.message}"
                            }
                        }
                    }

                    // One transition for the whole stack (ui/motion/Transitions.kt). The key is the screen's class only, so
                    // changes inside a screen (a tab, Daily's index, a lesson step) do not count as a new screen; HomeShell
                    // cross-fades its own tabs. Inside, always use `s`, never the outer `screen`.
                    val still = reducedMotion()
                    val systemDark = isSystemInDarkTheme()
                    AnimatedContent(screen, Modifier.fillMaxSize(), label = "screen", contentKey = { it::class },
                        transitionSpec = screenTransitions(still, isStage = ::isStage, depth = ::depthOf)) { s ->
                    // Each screen keeps its own theme while it animates out (the home does not turn dark under the curtain).
                    // A screen on its way out is off screen for onScreen(): its mic stops before the next screen's starts.
                    androidx.compose.runtime.CompositionLocalProvider(com.bigpapituna.sharpears.ui.LocalLeaving provides (s::class != screen::class)) {
                    SharpEarsTheme(dark = playerShown(s) || systemDark) {
                    when (s) {
                        is Screen.Player -> {
                            // A run that reached its end (a song, a progression) may be where a kid's goal was reached.
                            var ranToEnd by remember { mutableStateOf(false) }
                            // A kid leaving a library song (or a pop song, 2.6) played to its end: the pup's card first (the parent brief, 2.4).
                            val cardStep = com.bigpapituna.sharpears.ui.brief.rememberKidCardStep(current, ready = profile.isKid && ranToEnd && (s.meta != null || s.band != null))
                            Box(Modifier.fillMaxSize()) {
                                PlayerScreen(s.song, s.meta, sampler, midi, mic, voice, prefs, samplerReady.value,
                                    s.startMs, dump, s.start, onBack = cardStep.then("All done!") { screen = s.back; if (ranToEnd) maybeGoodnight("a song") },
                                    onFinished = { ranToEnd = true },
                                    chordTrack = s.meta?.let { lessons.songChords.value[it.id] } ?: s.chords,
                                    band = s.band)
                                if (profile.isKid) SharpEarsTheme(dark = isSystemInDarkTheme()) {
                                    com.bigpapituna.sharpears.ui.brief.KidCardStepHost(cardStep, current, say = { voice.speak(it, interrupt = true) })
                                }
                            }
                        }
                        is Screen.Lesson -> Box(Modifier.fillMaxSize().safeDrawingPadding()) {
                            val l = s.lesson
                            // A grown-up's Today offers it again as Continue (2.7).
                            LaunchedEffect(l.id) { if (!profile.isKid) prefs.lastPlayed = com.bigpapituna.sharpears.engine.nav.ContinueWith.encode(com.bigpapituna.sharpears.engine.nav.ContinueWith.Lesson(l.id)) }
                            // A kid (2.2): "one more" is the next stone on the map; a page's last lesson, finished the first time,
                            // leads to the page's finale instead (and today's goodbye waits until after it).
                            val finaleAhead = profile.isKid && remember(l.id) { Finale.trigger(l.id, kidStore.finales()) != null }
                            val next = if (finaleAhead) null else if (profile.isKid) KidMap.next(path, { it == l.id || prefs.lessonDone(it) }, kidStore.finales(),
                                pace = kidStore.pace().let { if (it.pin == l.id) it.copy(pin = null) else it }, stars = kidStore::stars)
                                else path.after(l) { prefs.lessonDone(it) }
                            val leave = {
                                val f = pendingFinale
                                if (f != null) { pendingFinale = null; voice.stop(); screen = Screen.Finale(f) } else screen = s.back
                            }
                            BackHandler { voice.stop(); leave() }
                            androidx.compose.runtime.key(l.id, navCount.intValue) { LessonScreen(l, s.step, LessonDeps(sampler, midi, mic, voice, prefs, samplerReady.value), s.inject,
                                midiScript = s.midiScript, startScene = s.scene,
                                onStep = { if (it != s.step) screen = s.copy(step = it, inject = null, midiScript = null, scene = 0) },
                                onSong = l.songId?.let { id -> { songs.firstOrNull { it.id == id && repo.available(it) }?.let { openSong(it, back = s) } } },
                                onDone = { stars -> lessonDone(l, stars) },
                                onNext = if (finaleAhead) leave else next?.let { n -> { screen = Screen.Lesson(n, s.back) } },
                                onBack = leave,
                                onSongs = { voice.stop(); screen = songsScreen() },
                                onJam = { voice.stop(); screen = Screen.Play(Toy.JAM, Screen.Home()) },
                                next = next,
                                finishLabel = if (finaleAhead) "Hooray!" else null,
                                holdGoodnight = finaleAhead) }
                        }
                        is Screen.Daily -> {
                            val part = s.plan.parts[s.index]
                            if (s.between) Box(Modifier.fillMaxSize().safeDrawingPadding()) {
                                BackHandler { finishDaily(s, s.done) }
                                Interstitial(part.next, voice, onGo = { screen = s.copy(between = false) },
                                    onSkip = { voice.stop(); advanceDaily(s, completed = false) },
                                    onEnd = { voice.stop(); finishDaily(s, s.done) },
                                    parts = s.plan.parts, index = s.index, done = s.done, kid = profile.isKid)
                            } else when (part) {
                                is SessionPart.Chords -> Box(Modifier.fillMaxSize().safeDrawingPadding()) {
                                    // Back is "Skip the rest": the chords answered count, and the session ends here (left open,
                                    // it took over Chords > Daily afterwards).
                                    fun endChords(n: Int) {
                                        part.itemIds.take(n).forEach { quest(QuestEvent.ChordReviewed(it)) }
                                        advanceDaily(s, completed = n > 0, part.copy(itemIds = part.itemIds.take(n)))
                                    }
                                    BackHandler { val n = lessons.sessionIndex; lessons.endSession(); endChords(n) }
                                    // Chord practice does not run the player, so its time is counted here.
                                    androidx.compose.runtime.DisposableEffect(s.index) {
                                        val t0 = com.bigpapituna.sharpears.engine.ForegroundClock.app.nowMs()
                                        onDispose {
                                            val secs = ((com.bigpapituna.sharpears.engine.ForegroundClock.app.nowMs() - t0) / 1000).toInt()
                                            prefs.addPractice(java.time.LocalDate.now(), secs)
                                            if (secs > 0) progress.record(ProgressEvent.Practice(java.time.LocalDateTime.now(), secs))
                                        }
                                    }
                                    QuietWrongKeys(mic)
                                    androidx.compose.foundation.layout.Column {
                                        Text("Practice today: chords", style = MaterialTheme.typography.titleLarge,
                                            modifier = Modifier.padding(start = 20.dp, top = 20.dp))
                                        DailyChords(lessons, sampler, midi, mic, prefs.micDefault, part.itemIds, inject = debugStart.inject.takeIf { startDaily }, sound = { prefs.sound },
                                            onEnd = ::endChords)
                                    }
                                }
                                is SessionPart.Lesson -> Box(Modifier.fillMaxSize().safeDrawingPadding()) {
                                    val l = COURSE.flatMap { it.lessons }.first { it.id == part.lessonId }
                                    LaunchedEffect(l.id) { if (!profile.isKid) prefs.lastPlayed = com.bigpapituna.sharpears.engine.nav.ContinueWith.encode(com.bigpapituna.sharpears.engine.nav.ContinueWith.Lesson(l.id)) }
                                    BackHandler { voice.stop(); advanceDaily(s, prefs.lessonDone(l.id)) }
                                    androidx.compose.runtime.key(l.id) { LessonScreen(l, 0, LessonDeps(sampler, midi, mic, voice, prefs, samplerReady.value), null,
                                        onStep = {}, onSong = null,
                                        onDone = { stars -> lessonDone(l, stars) },
                                        onNext = null,
                                        onBack = { voice.stop(); advanceDaily(s, prefs.lessonDone(l.id)) },
                                        onSongs = { voice.stop(); finishDaily(s, s.done + part); screen = songsScreen() },
                                        onJam = { voice.stop(); finishDaily(s, s.done + part); screen = Screen.Play(Toy.JAM, Screen.Home()) }) }
                                }
                                is SessionPart.Loop, is SessionPart.Song -> {
                                    val id = if (part is SessionPart.Loop) part.songId else (part as SessionPart.Song).songId
                                    val m = songs.first { it.id == id }
                                    val song = remember(id) { repo.load(m) }
                                    // Played here, it is the Continue song (Songs said "Start here" after a whole session of it).
                                    LaunchedEffect(id) { prefs.lastSongId = id; prefs.lastPlayed = com.bigpapituna.sharpears.engine.nav.ContinueWith.encode(com.bigpapituna.sharpears.engine.nav.ContinueWith.Song(id)) }
                                    val partStart = remember(s.index) { System.currentTimeMillis() }
                                    // A song part played to its end shows its recap first (stars, XP, the sticker it dropped);
                                    // closing the recap, or Back, goes on. It went on at once before, so none of it was seen.
                                    var ranToEnd by remember(s.index) { mutableStateOf(false) }
                                    androidx.compose.runtime.key(s.index) {
                                        PlayerScreen(song, m, sampler, midi, mic, voice, prefs, samplerReady.value,
                                            if (part is SessionPart.Song) prefs.progress(id).let { if (it.completed) 0.0 else it.lastPositionMs.toDouble() } else 0.0,
                                            dump, PlayerStart(Mode.WAIT, mic = prefs.micDefault && mic.hasPermission(),
                                                loopBar = (part as? SessionPart.Loop)?.bar, timeLimitMs = if (part is SessionPart.Loop) 120_000L else null),
                                            // Leaving the song after a minute or more of it counts; leaving at once does not.
                                            onBack = { advanceDaily(s, completed = ranToEnd || System.currentTimeMillis() - partStart >= 60_000) },
                                            onFinished = { if (part is SessionPart.Song) ranToEnd = true },
                                            onRecapClosed = { if (ranToEnd) advanceDaily(s, completed = true) },
                                            chordTrack = lessons.songChords.value[id],
                                            onTimeUp = {
                                                if (part is SessionPart.Loop) quest(QuestEvent.LoopDone(part.songId, part.bar))
                                                advanceDaily(s, completed = true)
                                            })
                                    }
                                }
                            }
                        }
                        is Screen.SightRead -> Box(Modifier.fillMaxSize().safeDrawingPadding()) {
                            BackHandler { voice.stop(); screen = s.back }
                            com.bigpapituna.sharpears.ui.SightReadScreen(LessonDeps(sampler, midi, mic, voice, prefs, samplerReady.value), s.level, s.inject,
                                onBack = { screen = s.back })
                        }
                        is Screen.EarTraining -> Box(Modifier.fillMaxSize().safeDrawingPadding()) {
                            QuietWrongKeys(mic)
                            // The hub handles Back itself: a game goes back to the hub, the hub to Learn.
                            EarTrainingScreen(sampler, midi, mic, voice, prefs.micDefault, onBack = { screen = s.back },
                                onReport = ::earEvent, sound = { prefs.sound }, start = s.start)
                        }
                        Screen.Onboarding -> Box(Modifier.fillMaxSize().safeDrawingPadding()) {
                            OnboardingScreen(progress, prefs, onReminderChanged = { com.bigpapituna.sharpears.reminder.Reminder.schedule(this@MainActivity) },
                                askReminder = profile.isOwner,
                                // How much they have played (2.6): the profile keeps it and the flow below re-plans the marks in place.
                                onLevel = { l -> profiles.update(profile.id) { it.copy(level = l) } },
                                onCheck = { l -> screen = Screen.Placement(l) }) { lessonId ->
                                // The suggested first lesson opens straight away; Back from it lands on Learn. Someone here for
                                // songs ("a lot") lands on the library, the rest on the course.
                                val songsFirst = com.bigpapituna.sharpears.engine.family.Placement.planOf(profiles.current).homeFirst == com.bigpapituna.sharpears.engine.family.HomeFirst.SONGS
                                screen = lessonId?.let { id -> COURSE.flatMap { it.lessons }.firstOrNull { it.id == id } }
                                    ?.let { Screen.Lesson(it, Screen.Home(HomeTab.LEARN)) } ?: Screen.Home(if (songsFirst) HomeTab.SONGS else HomeTab.LEARN)
                            }
                        }
                        // "Let's hear you play" (2.6): two or three tiny things, then where they begin; it counts as a first lesson.
                        is Screen.Placement -> Box(Modifier.fillMaxSize().safeDrawingPadding()) {
                            val plan = com.bigpapituna.sharpears.engine.family.Placement.planFor(s.said, profile.kind, profile.reader)
                            val songsFirst = plan.homeFirst == com.bigpapituna.sharpears.engine.family.HomeFirst.SONGS
                            com.bigpapituna.sharpears.ui.family.PlacementCheckScreen(sampler, midi, mic, voice, samplerReady.value, current, s.said,
                                award = {
                                    // The first lesson's rewards, once: the XP event, three stars on its stone, the world's sticker and sparkles.
                                    if (kidStore.stars(com.bigpapituna.sharpears.engine.family.Placement.CHECK_ID) > 0) null else {
                                        progress.record(ProgressEvent.LessonComplete(com.bigpapituna.sharpears.engine.family.Placement.CHECK_ID))
                                        kidStore.recordStars(com.bigpapituna.sharpears.engine.family.Placement.CHECK_ID, 3)
                                        world.lessonComplete(com.bigpapituna.sharpears.engine.family.Placement.CHECK_ID, null)
                                    }
                                },
                                finishLabel = if (profile.isKid) "To the map" else if (songsFirst) "Show me the songs" else "Start learning",
                                inject = s.inject, startTask = s.startTask,
                                onDone = { begin, played ->
                                    Log.d(TAG, "placement: ${profile.id} begins at $begin (check ${if (played) "played" else "skipped"})")
                                    // Only a check played through moves the level: skipped or left, the level said stays (it is already
                                    // stored by the onboarding or the new-kid flow; the debug route's said level is never stored).
                                    if (played && begin != profiles.current.level) profiles.update(profile.id) { it.copy(level = begin) }
                                    val after = com.bigpapituna.sharpears.engine.family.Placement.planFor(if (played) begin else profiles.current.level, profile.kind, profile.reader)
                                    screen = if (profile.isKid) Screen.Home()
                                    else if (after.homeFirst == com.bigpapituna.sharpears.engine.family.HomeFirst.SONGS) Screen.Home(HomeTab.SONGS)
                                    else COURSE.flatMap { it.lessons }.firstOrNull { it.id == after.startLessonId }?.let { Screen.Lesson(it, Screen.Home(HomeTab.LEARN)) } ?: Screen.Home(HomeTab.LEARN)
                                })
                        }
                        is Screen.Settings -> Box(Modifier.fillMaxSize().safeDrawingPadding()) {
                            BackHandler { screen = s.back }
                            SettingsScreen(prefs, songs, packageManager.getPackageInfo(packageName, 0).versionName ?: "", voice = voice,
                                onReminder = { com.bigpapituna.sharpears.reminder.Reminder.schedule(this@MainActivity) },
                                onMicSetup = { gate.guardForKids { screen = Screen.MicSetup(s) } },
                                onPrivacy = { screen = Screen.Privacy(s) }, onLicences = { screen = Screen.Licences(s) },
                                onBack = { screen = s.back },
                                onResetProgress = { earStore.reset(); practiceStore.clear(); theory.reset(); progress.clearPracticeLog(); Log.d(TAG, "progress: reset (prefs, ear, practice, theory, practice log; XP kept)") },
                                family = { FamilySettings(profiles) }, showReminder = current.isOwner)
                        }
                        is Screen.Privacy -> Box(Modifier.fillMaxSize().safeDrawingPadding()) {
                            BackHandler { screen = s.back }
                            PrivacyScreen(mic.canRecord, onMicSetup = { gate.guardForKids { screen = Screen.MicSetup(s) } }, onBack = { screen = s.back })
                        }
                        is Screen.Theory -> Box(Modifier.fillMaxSize().safeDrawingPadding()) {
                            com.bigpapituna.sharpears.ui.theory.TheoryScreen(sampler, voice, onBack = { voice.stop(); screen = s.back },
                                store = theory, soundOn = prefs.sound,
                                // Time in a lesson is practice; recorded first, so the lesson's XP lands after the minutes.
                                onPractice = { secs ->
                                    prefs.addPractice(java.time.LocalDate.now(), secs)
                                    if (secs > 0) progress.record(ProgressEvent.Practice(java.time.LocalDateTime.now(), secs))
                                },
                                onLessonDone = { id, _ -> progress.record(ProgressEvent.TheoryLesson(id)) },
                                onUnitComplete = { unit, _, _ -> progress.record(ProgressEvent.TheoryUnit(unit)) })
                        }
                        is Screen.Licences -> Box(Modifier.fillMaxSize().safeDrawingPadding()) {
                            BackHandler { screen = s.back }
                            LicencesScreen(songs, onBack = { screen = s.back })
                        }
                        is Screen.World -> Box(Modifier.fillMaxSize().safeDrawingPadding()) {
                            com.bigpapituna.sharpears.ui.world.WorldScreen(world, songs, onBack = { screen = s.back }, start = s.tab,
                                onSong = { openSong(it, back = s) })
                        }
                        is Screen.Play -> Box(Modifier.fillMaxSize()) {
                            BackHandler { voice.stop(); screen = s.back }
                            TimedOnScreen(s.toy) { ms -> Briefs.log { BriefEvent.ToyOpened(it, toyKind(s.toy), ms) } }
                            val frames = remember { mic.frameTap() }
                            when (s.toy) {
                                Toy.JAM -> {
                                    com.bigpapituna.sharpears.ui.play.JamScreen(mic, midi, frames, onExit = { screen = s.back }, hint = s.hint, voice = voice)
                                    // Debug: --es screen jam --es midi "3000:60/90,3010:64/90,..." plays a pretend keyboard into the Jam.
                                    val script = remember { intent.getStringExtra("midi")?.takeIf { applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0 } }
                                    if (script != null) LaunchedEffect(script) { midi.debugPlay(script) }
                                }
                                Toy.PUP_SAYS -> com.bigpapituna.sharpears.ui.play.PupSaysScreen(mic, midi, sampler, voice, sound = { prefs.sound },
                                    onExit = { voice.stop(); screen = s.back },
                                    // Each player's best of the day goes into their own world (the crown of the day is read from them).
                                    onFinished = { results -> results.forEach { r ->
                                        val result = com.bigpapituna.sharpears.engine.world.PupSaysResult(r.profileId, r.best)
                                        if (r.profileId == world.profileId) world.pupSays(result) else WorldStore(this@MainActivity, r.profileId).pupSays(result)
                                    } })
                                Toy.CLAP -> {
                                    val clicks = remember { runCatching { com.bigpapituna.sharpears.audio.Clicks(this@MainActivity) }.getOrNull() }
                                    androidx.compose.runtime.DisposableEffect(Unit) { onDispose { clicks?.release() } }
                                    com.bigpapituna.sharpears.ui.play.ClapAlongScreen(mic, frames, clicks, voice, sound = { prefs.sound }, onExit = { voice.stop(); screen = s.back })
                                }
                            }
                        }
                        is Screen.MicSetup -> Box(Modifier.fillMaxSize().safeDrawingPadding()) {
                            MicSetupScreen(mic, midi, debuggable,
                                onMicCheck = { screen = Screen.Player(micCheck, null, PlayerStart(Mode.WAIT, Practice.BOTH, mic = true), 0.0, s) },
                                onBack = { screen = s.back }, guardToken = gate::guard)
                        }
                        is Screen.Home -> if (current.isKid) {
                            // A kid's home (2.2) is the pup's world: one screen, no tabs (ui/kid/KidHome.kt).
                            val home = s.copy(kidFrom = null)
                            val pv = prefs.version.intValue
                            val kv = kidStore.version.intValue
                            val model = remember(pv, kv) { KidMap.build(path, prefs::lessonDone, kidStore::stars, kidStore.finales(), pace = kidStore.pace(), skipped = prefs::lessonSkipped) }
                            val today = remember { java.time.LocalDate.now() }
                            val asleep = !com.bigpapituna.sharpears.engine.skin.Bedtime.lessonOpen(true, progress.secondsToday(today), progress.goalMinutes,
                                bedtime.oneMoreOn, today, bedtime.bedtime)
                            val quest = remember(pv, kv) { playStore.quest(kid = true, plan = null) }
                            var corner by remember { mutableStateOf(false) }
                            // The parent brief from the corner (2.4): every kid on the phone, this one's tab first.
                            var briefOpen by remember { mutableStateOf(false) }
                            // Family (2.4.1): grown-ups corner > Family > Mia > Email.
                            var familyOpen by remember { mutableStateOf(false) }
                            // Back on the kid's map, a grown-up is done (Settings, a share, one more lesson): the gate shuts, so
                            // the whistle cannot hand the kid Settings or another player for the rest of its ten minutes.
                            LaunchedEffect(Unit) { if (gate.isOpen) { gate.close(); Log.d(TAG, "gate: closed, back on the kid's map") } }
                            // After the last page's finale the pup says it once: the big kids' road is open.
                            val roadLine = remember(kv) { model.roadOpen && !kidStore.roadSaid && kidStore.finales().containsAll(PLAYGROUND_PAGES.map { it.id }) }
                            // A finale left waiting (its lesson was left another way, or the app was closed before): it plays now.
                            LaunchedEffect(pendingFinale) {
                                (pendingFinale ?: Finale.owed(prefs::lessonPlayed, kidStore.finales())?.id)?.let { pendingFinale = null; screen = Screen.Finale(it) }
                            }
                            com.bigpapituna.sharpears.ui.kid.KidHome(model, world.state, current.name, current.avatar, asleep, quest,
                                onStone = { st -> path.lesson(st.lessonId)?.let { voice.stop(); screen = Screen.Lesson(it, home) } },
                                onToy = { t -> screen = Screen.Play(t, home) },
                                onSongs = { screen = Screen.KidSongs(home) },
                                onStickers = { screen = Screen.KidStickers(home) },
                                onQuestTask = { t ->
                                    Log.d(TAG, "quest: open ${t.opens} for ${t.kind} (kid home)")
                                    screen = when (t.opens) {
                                        "jam" -> Screen.Play(Toy.JAM, home, hint = "Can you find all the Cs?")
                                        "pupsays" -> Screen.Play(Toy.PUP_SAYS, home)
                                        "songs" -> Screen.KidSongs(home)
                                        else -> home
                                    }
                                },
                                onGrownUps = { gate.guard { corner = true } },
                                say = { voice.speak(it, interrupt = true) },
                                from = s.kidFrom, roadLine = roadLine, onRoadSaid = { kidStore.roadSaid = true },
                                // The garden's jar and egg, as on the world screen: a full jar is tipped for the pup; the pup says
                                // what the egg and an unfilled jar are about.
                                onJar = {
                                    if (world.tip()) { WorldSounds.munch(); voice.speak(com.bigpapituna.sharpears.engine.kid.KidLines.YUM, interrupt = true) }
                                    else voice.speak(com.bigpapituna.sharpears.ui.world.jarLine(world.state.jar, kid = true, world.state.treats), interrupt = true)
                                },
                                onEgg = { voice.speak(com.bigpapituna.sharpears.engine.kid.KidLines.EGG, interrupt = true) },
                                grownUps = if (!corner) null else com.bigpapituna.sharpears.ui.kid.GrownUpCorner(roster.profiles, current.id,
                                    onSettings = { corner = false; screen = Screen.Settings(home) },
                                    onMicSetup = { corner = false; screen = Screen.MicSetup(home) },
                                    onSwitch = { id -> corner = false; profiles.switchTo(id) },
                                    onBrief = { corner = false; briefOpen = true; Log.d(TAG, "kid home: grown-ups opened the parent brief") },
                                    onFamily = { corner = false; familyOpen = true; Log.d(TAG, "kid home: grown-ups opened Family") }),
                                onGrownUpsClosed = { corner = false; gate.close(); Log.d(TAG, "gate: closed with the grown-ups' corner") },
                                decor = com.bigpapituna.sharpears.ui.kid.DecorActions(world::place, world::unplace, say = {}),
                                decorateWith = decorateWith, onDecorateStarted = { decorateWith = null },
                                homesLine = world.state.homesLine, onHomesSaid = world::homesSaid,
                                // Grown-up mode, offered once after Beginner (it was on the kid's Learn path before 2.2).
                                grownUpOffer = if (!CoursePath.offerGrownUp(true, grownUpOffered, { pv >= 0 && prefs.lessonDone(it) })) null else { {
                                    fun answered() { bedtime.grownUpOffered = true; grownUpOffered = true }
                                    com.bigpapituna.sharpears.ui.family.GrownUpOffer(current.name,
                                        onSwitch = { gate.guard {
                                            answered()
                                            Log.d(TAG, "profiles: ${current.id} switched to grown-up mode after Beginner")
                                            profiles.setKind(current.id, com.bigpapituna.sharpears.engine.family.ProfileKind.ADULT)
                                        } },
                                        // A grown-up's answer too: a kid's tap on "Not yet" put the one-time offer away for good.
                                        onLater = { gate.guard { answered(); Log.d(TAG, "profiles: grown-up mode offered, not yet") } })
                                } })
                            if (familyOpen) com.bigpapituna.sharpears.ui.family.FamilyDialog(profiles,
                                onClose = { familyOpen = false; gate.close(); Log.d(TAG, "gate: closed with Family") })
                            if (briefOpen) com.bigpapituna.sharpears.ui.brief.ParentBriefDialog(com.bigpapituna.sharpears.ui.brief.rememberBriefEnv(),
                                roster.profiles.filter { it.isKid }, initial = current.id,
                                // Closed, the phone is the kid's again: the gate shuts as it does with the corner.
                                onClose = { briefOpen = false; gate.close(); Log.d(TAG, "gate: closed with the parent brief") })
                        } else {
                            BackHandler(enabled = s.tab != HomeTab.TODAY) { screen = Screen.Home() }
                            val lv = lessons.version.intValue
                            val due = remember(lv, lessons.songPacks.value) {
                                lessons.curriculum().queue(lessons.mastery(), System.currentTimeMillis()).size
                            }
                            HomeShell(s.tab, { screen = s.copy(tab = it) }, badge = mapOf(HomeTab.LEARN to due)) { tab ->
                                when (tab) {
                                    // 2.7: Today, the grown-ups' home. One main action: the plan and its Start.
                                    HomeTab.TODAY -> {
                                        val pv = prefs.version.intValue
                                        val today = java.time.LocalDate.now()
                                        val plan = remember(pv, lv, lessons.songPacks.value) { todayPlan() }
                                        val card = remember(plan, pv) { com.bigpapituna.sharpears.engine.nav.TodayCard.of(plan, prefs.dailySummary(today)) }
                                        val goal = remember(pv, progress.version.intValue) {
                                            val st = progress.streak()
                                            GoalState(progress.goalLine(), DailyGoal.met(progress.secondsToday(), progress.goalMinutes), st.streak, st.atRisk)
                                        }
                                        // Continue: the song, song by chords or lesson played last; on a fresh install an easy first song.
                                        val cont = remember(songs, pv) {
                                            val with = com.bigpapituna.sharpears.engine.nav.ContinueWith.of(prefs.lastPlayed, prefs.lastSongId) { c ->
                                                when (c) {
                                                    is com.bigpapituna.sharpears.engine.nav.ContinueWith.Song -> songs.any { it.id == c.id && repo.available(it) }
                                                    is com.bigpapituna.sharpears.engine.nav.ContinueWith.PopSong -> com.bigpapituna.sharpears.ui.chords.PopCatalogue.songs.any { it.id == c.id }
                                                    is com.bigpapituna.sharpears.engine.nav.ContinueWith.Lesson -> path.lesson(c.id) != null
                                                }
                                            }
                                            fun entry(m: SongMeta) = LibraryEntry(m, repo.available(m), prefs.progress(m.id), null)
                                            when (with) {
                                                is com.bigpapituna.sharpears.engine.nav.ContinueWith.Song -> songs.first { it.id == with.id }.let { com.bigpapituna.sharpears.ui.ContinueTile.Song(entry(it), fresh = false) }
                                                is com.bigpapituna.sharpears.engine.nav.ContinueWith.PopSong ->
                                                    com.bigpapituna.sharpears.ui.ContinueTile.PopSong(com.bigpapituna.sharpears.ui.chords.PopCatalogue.songs.first { it.id == with.id })
                                                is com.bigpapituna.sharpears.engine.nav.ContinueWith.Lesson -> path.lesson(with.id)!!.let { l ->
                                                    com.bigpapituna.sharpears.ui.ContinueTile.Lesson(l, path.levelOf(l)?.title, prefs.lessonPlayed(l.id))
                                                }
                                                null -> songs.firstOrNull { it.difficulty == "easy" && repo.available(it) }?.let { com.bigpapituna.sharpears.ui.ContinueTile.Song(entry(it), fresh = true) }
                                            }
                                        }
                                        // The microphone banner, until the mic is allowed (then never again) or put away by hand.
                                        val micAllowed = mic.hasPermission()
                                        LaunchedEffect(micAllowed) { if (micAllowed && !prefs.micBannerClosed) prefs.micBannerClosed = true }
                                        com.bigpapituna.sharpears.ui.TodayScreen(card, goal, cont, micBanner = !micAllowed && !prefs.micBannerClosed,
                                            com.bigpapituna.sharpears.ui.TodayActions(
                                                onStart = ::startDaily,
                                                onContinue = {
                                                    when (cont) {
                                                        is com.bigpapituna.sharpears.ui.ContinueTile.Song -> openSong(cont.entry.meta, back = s)
                                                        is com.bigpapituna.sharpears.ui.ContinueTile.PopSong -> screen = Screen.PopSong(cont.song.id, s)
                                                        is com.bigpapituna.sharpears.ui.ContinueTile.Lesson -> screen = Screen.Lesson(cont.lesson, s)
                                                        null -> {}
                                                    }
                                                },
                                                onMic = { screen = Screen.MicSetup(s) },
                                                onMicClose = { prefs.micBannerClosed = true; Log.d(TAG, "today: mic banner put away") },
                                                onSongs = { screen = Screen.Home(HomeTab.SONGS) },
                                                onChords = { screen = Screen.Home(HomeTab.LEARN) },
                                                onToy = { t -> screen = Screen.Play(t, s) },
                                            ),
                                            // Pup's quest as a row of stars in the card: a task's tap goes where it is done.
                                            quest = {
                                                com.bigpapituna.sharpears.ui.play.QuestStars(plan, onTask = { t ->
                                                    Log.d(TAG, "quest: open ${t.opens} for ${t.kind}")
                                                    val song = t.ref?.let { id -> songs.firstOrNull { it.id == id && repo.available(it) } }
                                                    if (t.opens == "songs" && song != null) openSong(song, back = s)     // "Play Minuet in G": straight in
                                                    else screen = when (t.opens) {
                                                        "jam" -> Screen.Play(Toy.JAM, s, hint = "Can you find all the Cs?")
                                                        "pupsays" -> Screen.Play(Toy.PUP_SAYS, s)
                                                        "chords" -> Screen.LearnChords(s)
                                                        "learn" -> Screen.Home(HomeTab.LEARN)
                                                        "songs" -> Screen.Home(HomeTab.SONGS)
                                                        else -> s
                                                    }
                                                })
                                            },
                                            emptyPlan = debuggable && intent.getBooleanExtra("emptyToday", false))
                                    }
                                    // 2.7: everything playable. The Play corner, then the library or songs by chords.
                                    HomeTab.SONGS -> {
                                        val pv = prefs.version.intValue
                                        val entries = remember(songs, pv) {
                                            songs.map { m -> LibraryEntry(m, repo.available(m), prefs.progress(m.id), m.pairWith?.let { p -> songs.firstOrNull { it.id == p } }) }
                                        }
                                        LibraryScreen(entries, played = prefs.lastSongId != null,
                                            LibraryActions(
                                                onOpen = { openSong(it, back = s) },
                                                onImport = { into -> importInto = into; pickMidi.launch(arrayOf("audio/midi", "audio/x-midi", "audio/mid", "application/x-midi", "application/octet-stream", "*/*")) },
                                                onRename = { m, title -> repo.rename(m, title); Log.d(TAG, "song ${m.id} renamed"); libraryVersion++ },
                                                onDelete = { m ->
                                                    repo.delete(m)
                                                    if (prefs.lastSongId == m.id) prefs.lastSongId = null
                                                    Log.d(TAG, "song ${m.id} deleted"); libraryVersion++
                                                },
                                            ),
                                            // The Play corner at the top (it was on the home).
                                            play = { com.bigpapituna.sharpears.ui.play.PlayRow(onToy = { t -> screen = Screen.Play(t, s) }) },
                                            // Songs by chords, the tab's second section (2.7; the Chords tab's Songs before).
                                            pop = if (com.bigpapituna.sharpears.ui.chords.PopCatalogue.songs.isEmpty()) null else {
                                                val known = remember(lv) { PopFind.known(lessons.mastery()) }
                                                com.bigpapituna.sharpears.ui.PopSection(com.bigpapituna.sharpears.ui.chords.PopCatalogue.songs, known, s.pop) { song, at ->
                                                    screen = Screen.PopSong(song.id, s.copy(songs = com.bigpapituna.sharpears.ui.SongsSection.BY_CHORDS, pop = at))
                                                }
                                            },
                                            section = s.songs, onSection = { screen = s.copy(songs = it) })
                                    }
                                    HomeTab.LEARN -> {
                                        val pv = prefs.version.intValue
                                        val kv = kidStore.version.intValue
                                        LearnScreen(done = { pv >= 0 && prefs.lessonDone(it) }, skipped = { pv >= 0 && prefs.lessonSkipped(it) },
                                            onOpen = { screen = Screen.Lesson(it, s) },
                                            pinned = remember(pv, kv) { kidStore.pace().pin?.let(path::lesson) },
                                            onSightRead = { screen = Screen.SightRead(s) },
                                            sightBest = remember(pv) { (prefs.sightBest(1) ?: prefs.sightBest(2))?.label },
                                            onEar = { screen = Screen.EarTraining(s) },
                                            theory = { m -> com.bigpapituna.sharpears.ui.theory.TheoryCard(theory, onOpen = { screen = Screen.Theory(s) }, modifier = m) },
                                            earStreak = remember { earStore.workoutStreak() },
                                            path = path,
                                            // 2.7: the Chords tab's places, at the top of Learn (a kid's chords are on their own Songs).
                                            chords = if (current.isKid) null else ({
                                                com.bigpapituna.sharpears.ui.ChordsSection(due, onLearnChords = { screen = Screen.LearnChords(s) },
                                                    onGames = { screen = Screen.ChordGames(s) }, onProgressions = { screen = Screen.Progressions(s) })
                                            }),
                                            grownUp = if (!CoursePath.offerGrownUp(current.isKid, grownUpOffered, { pv >= 0 && prefs.lessonDone(it) })) null else { {
                                                fun answered() { bedtime.grownUpOffered = true; grownUpOffered = true }
                                                com.bigpapituna.sharpears.ui.family.GrownUpOffer(current.name,
                                                    onSwitch = { gate.guard {
                                                        answered()
                                                        Log.d(TAG, "profiles: ${current.id} switched to grown-up mode after Beginner")
                                                        profiles.setKind(current.id, com.bigpapituna.sharpears.engine.family.ProfileKind.ADULT)
                                                    } },
                                                    onLater = { answered(); Log.d(TAG, "profiles: grown-up mode offered, not yet") })
                                            } })
                                    }
                                    HomeTab.PRACTICE -> PracticeScreen(sampler, mic, practiceStore, sound = { prefs.sound },
                                        // A scale or warm-up plays like a lesson step: the full player in wait mode at the exercise's
                                        // tempo; Back returns to this tab (the tab picked is kept in the Home screen).
                                        onOpenExercise = { ex, title ->
                                            screen = Screen.Player(ex.toSong("practice-" + title.hashCode(), title), null,
                                                PlayerStart(Mode.WAIT, ex.practice, mic = prefs.micDefault && mic.hasPermission(), beatLines = ex.beatLines), 0.0, s)
                                        },
                                        startTab = s.practiceTab,
                                        onEvent = { e -> progress.record(ProgressEvent.FeatureUsed(if (e.kind == PracticeKind.WARMUP) "warmups" else "scales")) },
                                        onTab = { t ->
                                            screen = s.copy(practiceTab = t)
                                            // The metronome and tuner have no "start" of their own: opening them is using them.
                                            when (t) { PracticeTab.METRONOME -> "metronome"; PracticeTab.TUNER -> "tuner"; else -> null }
                                                ?.let { progress.record(ProgressEvent.FeatureUsed(it)) }
                                        })
                                    HomeTab.YOU -> ProfileScreen(progress, prefs, songs, onSettings = { gate.guardForKids { screen = Screen.Settings(s) } },
                                        kid = current.isKid,
                                        // The pup's world in one card: the garden, and a way into the album, dress-up and the records.
                                        world = { com.bigpapituna.sharpears.ui.world.WorldCard(world, onOpen = { tab -> screen = Screen.World(s, tab) }) },
                                        paws = { com.bigpapituna.sharpears.ui.skin.PawCalendarCard(kid = current.isKid) },
                                        // The parent brief about themselves (2.4): a card that opens their week, no gate.
                                        brief = if (current.isKid) null else { { com.bigpapituna.sharpears.ui.brief.MyWeekCard(com.bigpapituna.sharpears.ui.brief.rememberBriefEnv(), current) } },
                                        // Everyone's week at the top (2.7; it was on the home), when there is more than one of us: tapping
                                        // someone opens the app as them. Each shows their latest sticker; whoever copied the pup furthest
                                        // today wears the crown.
                                        family = if (current.isKid || !roster.needsPicker) null else { {
                                            val pv = prefs.version.intValue
                                            val wall = remember(roster, pv, progress.version.intValue) {
                                                familyWallEntries(this@MainActivity, roster, latestSticker = { id -> WorldStore.latestSticker(this@MainActivity, id) })
                                            }
                                            val crowned = remember(roster, pv) { WorldStore.crownOfTheDay(this@MainActivity, roster.profiles.map { it.id }) }
                                            FamilyWall(wall, onSwitch = profiles::switchTo, crowned = crowned)
                                        } })
                                }
                            }
                        }
                        // 2.7, the top of Learn: Learn chords, Games and Progressions (the Chords tab's places before).
                        is Screen.LearnChords -> Box(Modifier.fillMaxSize().safeDrawingPadding()) {
                            BackHandler { screen = s.back }
                            QuietWrongKeys(mic)
                            val here = s.copy(start = com.bigpapituna.sharpears.ui.LearnChordsStart.REVIEW, inject = null)
                            com.bigpapituna.sharpears.ui.LearnChordsScreen(sampler, midi, mic, prefs, lessons, prefs.micDefault, s.start, s.inject,
                                // A session's progression opens in the player; Back comes here, to the session.
                                onPlayProgression = { p, key ->
                                    screen = Screen.Player(progressionSong(p, key), null, PlayerStart(Mode.WAIT, mic = prefs.micDefault && mic.hasPermission()), 0.0, here)
                                },
                                popSongs = com.bigpapituna.sharpears.ui.chords.PopCatalogue.songs,
                                onOpenPopSong = { screen = Screen.PopSong(it.id, here) },
                                onShowPlayable = {
                                    screen = Screen.Home(HomeTab.SONGS, songs = com.bigpapituna.sharpears.ui.SongsSection.BY_CHORDS,
                                        pop = com.bigpapituna.sharpears.ui.chords.PopStart(sort = PopFind.Sort.CAN_PLAY))
                                },
                                onBack = { screen = s.back })
                        }
                        is Screen.ChordGames -> Box(Modifier.fillMaxSize().safeDrawingPadding()) {
                            BackHandler { voice.stop(); screen = s.back }
                            QuietWrongKeys(mic)
                            com.bigpapituna.sharpears.ui.ChordGamesPage(sampler, midi, mic, voice, prefs, prefs.micDefault, s.start, onBack = { voice.stop(); screen = s.back })
                        }
                        is Screen.Progressions -> Box(Modifier.fillMaxSize().safeDrawingPadding()) {
                            BackHandler { screen = s.back }
                            com.bigpapituna.sharpears.ui.ProgressionsPage(sampler, prefs, onPlay = { p, key ->
                                screen = Screen.Player(progressionSong(p, key), null, PlayerStart(Mode.WAIT, mic = prefs.micDefault && mic.hasPermission()), 0.0, s)
                            }, onBack = { screen = s.back })
                        }
                        is Screen.PopSong -> Box(Modifier.fillMaxSize().safeDrawingPadding()) {
                            BackHandler { screen = s.back }
                            val pop = com.bigpapituna.sharpears.ui.chords.PopCatalogue.songs.firstOrNull { it.id == s.songId }
                            if (pop == null) com.bigpapituna.sharpears.ui.chords.PopSongMissing { screen = s.back }
                            else {
                                LaunchedEffect(pop.id) {
                                    progress.record(ProgressEvent.FeatureUsed("popsongs"))
                                    if (!profile.isKid) prefs.lastPlayed = com.bigpapituna.sharpears.engine.nav.ContinueWith.encode(com.bigpapituna.sharpears.engine.nav.ContinueWith.PopSong(pop.id))      // Today's Continue (2.7)
                                }
                                val known = remember(lessons.version.intValue) { com.bigpapituna.sharpears.engine.chords.PopFind.known(lessons.mastery()) }
                                com.bigpapituna.sharpears.ui.chords.PopSongPage(pop, known, sampler, sound = { prefs.sound },
                                    shift = s.shift, pattern = s.pattern ?: pop.pattern, bpm = s.bpm ?: pop.bpm,
                                    onState = { sh, pa, bp -> screen = s.copy(shift = sh, pattern = pa, bpm = bp, hear = false) },
                                    onPlayAlong = { run ->
                                        Log.d(TAG, "pop: play along ${pop.id} ${run.song.id}")
                                        // The right hand plays the chords; the app plays the left hand (the band toggle adds drums and bass).
                                        screen = Screen.Player(run.song, null, PlayerStart(Mode.WAIT, Practice.RIGHT, mic = prefs.micDefault && mic.hasPermission()),
                                            0.0, s.copy(hear = false), run.band, run.chords)
                                    },
                                    onBack = { screen = s.back }, hearAtOpen = s.hear, kidOverride = s.kid, preReader = s.pre)
                            }
                        }
                        is Screen.KidSongs -> Box(Modifier.fillMaxSize().safeDrawingPadding()) {
                            BackHandler { screen = s.back }
                            val kv = kidStore.version.intValue
                            com.bigpapituna.sharpears.ui.kid.KidSongsScreen(songs, repo, onSong = { openSong(it, back = s) }, onBack = { screen = s.back },
                                shelf = remember(kv) { kidStore.shelf },
                                duetWith = duetNext.value?.takeIf { it.whoId == profile.id }?.partnerId?.let { id -> roster.profiles.firstOrNull { it.id == id }?.name },
                                say = { voice.speak(it, interrupt = true) },
                                onChordSongs = if (com.bigpapituna.sharpears.ui.chords.PopCatalogue.songs.none { com.bigpapituna.sharpears.engine.chords.PopSong.Style.KIDS in it.styles }) null
                                    else ({ screen = Screen.KidChords(s) }),
                                reader = current.reader)
                        }
                        // A kid's chord songs (2.6): the kid list; a song opens the kid page (colour blocks; animals for a pre-reader).
                        is Screen.KidChords -> Box(Modifier.fillMaxSize().safeDrawingPadding()) {
                            BackHandler { screen = s.back }
                            com.bigpapituna.sharpears.ui.chords.KidChordSongs(com.bigpapituna.sharpears.ui.chords.PopCatalogue.songs,
                                onOpen = { screen = Screen.PopSong(it.id, s) }, onBack = { screen = s.back })
                        }
                        is Screen.KidStickers -> Box(Modifier.fillMaxSize().safeDrawingPadding()) {
                            BackHandler { screen = s.back }
                            com.bigpapituna.sharpears.ui.kid.KidStickersScreen(world, songs, onBack = { screen = s.back }, start = s.page,
                                onSong = { openSong(it, back = s) }, reader = current.reader,
                                onDecorate = { key -> decorateWith = key; screen = if (s.back is Screen.Home) s.back else Screen.Home() })
                        }
                        is Screen.Finale -> {
                            val page = PLAYGROUND_PAGES.firstOrNull { it.id == s.pageId }
                            if (page == null) LaunchedEffect(Unit) { screen = Screen.Home() }
                            else {
                                // Played once: the path walks on past the page from now on (engine/kid/KidMap.next).
                                LaunchedEffect(page.id) { kidStore.finalePlayed(page.id) }
                                com.bigpapituna.sharpears.ui.kid.FinaleScreen(page, current.name, world.state.album.page(KidPages.worldPlace(page)), world.state,
                                    com.bigpapituna.sharpears.engine.kid.LessonStars.ofPage(page.lessonIds, kidStore::stars), Finale.after(page),
                                    say = { voice.speak(it) }, sound = prefs.sound,
                                    onDone = { voice.stop(); screen = Screen.Home(kidFrom = page.id); maybeGoodnight("the ${page.id} finale") })
                            }
                        }
                    }
                    }
                    }
                    }
                    if (goodbye) SharpEarsTheme(dark = isSystemInDarkTheme()) {
                        com.bigpapituna.sharpears.ui.skin.Goodbye(current.name,
                            remember { com.bigpapituna.sharpears.engine.family.PawWeek.of(progress.practiceLog(), java.time.LocalDate.now()) },
                            onSongs = { voice.stop(); goodbye = false; dailyDone = null; screen = songsScreen() },
                            onClose = { voice.stop(); goodbye = false; dailyDone = null },
                            onJam = { voice.stop(); goodbye = false; dailyDone = null; screen = Screen.Play(Toy.JAM, Screen.Home()) },
                            say = { voice.speak(it, interrupt = true) })
                    }
                    if (!goodbye) dailyDone?.let { (parts, min, streak) ->
                        // A kid's session ends on the pup, not on numbers.
                        if (current.isKid) com.bigpapituna.sharpears.ui.Celebration("Done for today!",
                            com.bigpapituna.sharpears.engine.SessionWords.kidSessionDone(current.name), emptyList(),
                            primary = "Continue", onPrimary = { dailyDone = null },
                            pose = com.bigpapituna.sharpears.ui.Pose.LEVEL_UP, sampler = sampler.takeIf { prefs.sound })
                        else com.bigpapituna.sharpears.ui.Celebration("Done for today!",
                            if (streak >= 2) "$streak days in a row. Keep it going." else "Same time tomorrow?",
                            listOf(com.bigpapituna.sharpears.ui.Stat("$parts", if (parts == 1) "Part" else "Parts"),
                                com.bigpapituna.sharpears.ui.Stat("$min min", "Practice"),
                                com.bigpapituna.sharpears.ui.Stat("$streak", if (streak == 1) "Day" else "Days in a row")),
                            primary = "Continue", onPrimary = { dailyDone = null },
                            pose = if (streak >= 2) com.bigpapituna.sharpears.ui.Pose.STREAK else com.bigpapituna.sharpears.ui.Pose.LEVEL_UP,
                            sampler = sampler.takeIf { prefs.sound })
                    }
                    // Debug: --es screen motion [--es demo celebrate|cheer|pup|keys] (Team E's effects, ui/motion/MotionDemo.kt).
                    var motionDemo by remember { mutableStateOf(debuggable && intent.getStringExtra("screen") == "motion") }
                    if (motionDemo) com.bigpapituna.sharpears.ui.motion.MotionDemo(intent.getStringExtra("demo"), sampler) { motionDemo = false }
                    importError?.let { msg ->
                        LaunchedEffect(msg) { com.bigpapituna.sharpears.analytics.Tester.error("song import") }
                        AlertDialog(onDismissRequest = { importError = null }, title = { Text("Could not import") }, text = { Text(msg) },
                            confirmButton = { TextButton(onClick = { importError = null }) { Text("OK") } })
                    }
                    // Level-ups and achievement toasts, last so they sit on top. Not on the stage: a level-up waits until
                    // the song is over (the store keeps it pending), so it never interrupts playing.
                    // A Celebration on screen holds the level-up back until it closes: celebration first, then the level.
                    // A kid's session ends on the sticker, not on XP (KIDS-PLAN 3.4): XP still adds up underneath for grown-up
                    // mode later, but no level-up screen full of numbers.
                    if (current.isKid) LaunchedEffect(progress.levelUp.value) { if (progress.levelUp.value != null) progress.consumeLevelUp() }
                    // Nor achievement banners ("One week streak", "100 minutes"): they unlock underneath, unseen by a kid.
                    if (current.isKid) LaunchedEffect(progress.toasts.size) { progress.toasts.clear() }
                    if (!isStage(screen) && !current.isKid) SharpEarsTheme(dark = isSystemInDarkTheme()) {
                        ProgressOverlays(progress, holdLevelUp = current.isKid || com.bigpapituna.sharpears.ui.celebrationsShowing.intValue > 0)
                    }
                    // The weekly brief's notification asked for someone's brief (2.4).
                    BriefFromNotification(roster, openKid = current.isKid)
                    // The parent gate, over everything while an action waits for a grown-up.
                    SharpEarsTheme(dark = isSystemInDarkTheme()) { ParentGateHost(gate) }
                }
            }
            }
        } }
    }

    /**
     * The weekly brief's notification asked for someone's brief ([briefRequest]): a kid's opens the parent brief on their tab
     * and that period, behind the parent gate; a grown-up's opens their own week. Closing it on a kid's screen shuts the gate.
     */
    @androidx.compose.runtime.Composable
    private fun BriefFromNotification(roster: com.bigpapituna.sharpears.engine.family.Roster, openKid: Boolean) {
        val (id, period) = briefRequest.value ?: return
        val who = roster.profiles.firstOrNull { it.id == id }
        if (who == null) { LaunchedEffect(id) { briefRequest.value = null; Log.d(TAG, "brief: notification for $id, who is gone") }; return }
        SharpEarsTheme(dark = isSystemInDarkTheme()) {
            val env = com.bigpapituna.sharpears.ui.brief.rememberBriefEnv().let { live ->
                if (briefSamples) remember(live) { com.bigpapituna.sharpears.ui.brief.sampleActionsEnv(live) } else live
            }
            val close = { briefRequest.value = null; if (openKid) gate.close() }
            if (who.isKid) com.bigpapituna.sharpears.ui.brief.ParentBriefDialog(env, roster.profiles.filter { it.isKid }, onClose = close, initial = id, period = period)
            else com.bigpapituna.sharpears.ui.brief.MyWeekDialog(env, who, onClose = close, period = period)
        }
    }

    /** The weekly notification's extras ([BriefReminder.EXTRA_PROFILE], [BriefReminder.EXTRA_PERIOD]), or null. */
    private fun briefFrom(intent: android.content.Intent?): Pair<String, com.bigpapituna.sharpears.engine.brief.Period>? {
        // The email brief's buttons: sharpears://brief/<profile id>/<day or week> (any build; the brief asks the parent gate itself).
        if (intent?.data?.scheme == "sharpears") return com.bigpapituna.sharpears.engine.brief.email.EmailLinks.parse(intent.dataString)?.let { it.profileId to it.period }
        val id = intent?.getStringExtra(com.bigpapituna.sharpears.reminder.BriefReminder.EXTRA_PROFILE) ?: return null
        val period = intent.getStringExtra(com.bigpapituna.sharpears.reminder.BriefReminder.EXTRA_PERIOD)
        return briefOf("$id|${period ?: "WEEK"}")
    }

    /** "<profile id>|<PERIOD>" back to the request (a bad period is the week). */
    private fun briefOf(s: String): Pair<String, com.bigpapituna.sharpears.engine.brief.Period>? {
        val id = s.substringBefore('|').takeIf { it.isNotBlank() } ?: return null
        val period = com.bigpapituna.sharpears.engine.brief.Period.entries.firstOrNull { it.name == s.substringAfter('|', "") }
            ?: com.bigpapituna.sharpears.engine.brief.Period.WEEK
        return id to period
    }

    /**
     * Debug builds (2.2): --es kidseed <page>:<n> marks a page's first n lessons done with stars (3, 2, 3, 1, ...) and
     * forgets its finale; road marks every page walked and its finale played; reset forgets the Playground's lessons,
     * stars and finales. --ez sleepy true reaches today's goal and says goodnight (the map shows the pup asleep);
     * --ez awake true lets a grown-up's one more lesson through today (the map awake whatever was played).
     */
    /**
     * Debug builds (2.5, the wardrobe and decorate mode): `--es wardrobe all|<ids>` opens items, `--es wear <ids>` dresses
     * the pup, `--es fresh <ids>` queues items for the next finish screen's reveal, `--es decor <key>@<slot>+...` sticks
     * stickers (`--es decor clear` empties every spot), `--ez homes true` brings the "new homes" line back. Ids by commas.
     */
    private fun worldDebugSeed(intent: android.content.Intent, world: WorldStore) {
        fun items(extra: String) = intent.getStringExtra(extra)?.let { v ->
            if (v == "all") com.bigpapituna.sharpears.engine.world.WearItem.entries.toList()
            else v.split(",").mapNotNull { com.bigpapituna.sharpears.engine.world.WearItem.byId(it.trim()) }
        }
        var s = world.state
        items("wardrobe")?.let { s = s.copy(wardrobe = s.wardrobe + it) }
        items("wear")?.let { w -> s = s.copy(wardrobe = s.wardrobe + w, outfit = w.fold(com.bigpapituna.sharpears.engine.world.Outfit.NONE) { o, i -> o.with(i) }) }
        items("fresh")?.let { f -> s = s.copy(wardrobe = s.wardrobe + f, fresh = f) }
        intent.getStringExtra("decor")?.let { spec ->
            s = if (spec == "clear") s.copy(placements = com.bigpapituna.sharpears.engine.world.Placements())
            else spec.split("+").fold(s) { st, p -> com.bigpapituna.sharpears.engine.world.World.place(st, p.substringBefore("@"), p.substringAfter("@")) }
        }
        if (intent.getBooleanExtra("homes", false)) s = s.copy(homesLine = true)
        if (s != world.state) world.debugSet(s)
    }

    private fun kidDebugSeed(intent: android.content.Intent, prefs: Prefs, kid: KidStore, progress: ProgressStore,
                             bedtime: com.bigpapituna.sharpears.ui.skin.BedtimeStore) {
        val stars = intArrayOf(3, 2, 3, 1, 3, 2, 2, 3, 1, 3, 2, 3)
        intent.getStringExtra("kidseed")?.let { spec ->
            val all = PLAYGROUND_PAGES.flatMap { it.lessonIds }
            when (spec) {
                "reset" -> { all.forEach(prefs::forgetLessonDone); kid.debugForget() }
                "road" -> PLAYGROUND_PAGES.forEach { p ->
                    p.lessonIds.forEachIndexed { i, id -> prefs.markLessonDone(id); kid.recordStars(id, stars[i % stars.size]) }
                    kid.finalePlayed(p.id)
                }
                else -> {
                    val page = PLAYGROUND_PAGES.firstOrNull { it.id == spec.substringBefore(":") } ?: PLAYGROUND_PAGES.first()
                    val n = spec.substringAfter(":", "0").toIntOrNull() ?: 0
                    page.lessonIds.forEach(prefs::forgetLessonDone)
                    kid.debugForget(lessonIds = page.lessonIds, pageIds = listOf(page.id))
                    page.lessonIds.take(n).forEachIndexed { i, id -> prefs.markLessonDone(id); kid.recordStars(id, stars[i % stars.size]) }
                }
            }
            Log.d(TAG, "kid debug: seeded $spec")
        }
        val today = java.time.LocalDate.now()
        if (intent.getBooleanExtra("sleepy", false)) {
            val need = progress.goalMinutes * 60 - progress.secondsToday(today)
            if (need > 0) prefs.addPractice(today, need)
            bedtime.goodnightOn = today; bedtime.oneMoreOn = null
            Log.d(TAG, "kid debug: today's goal reached, goodnight said")
        }
        if (intent.getBooleanExtra("awake", false)) { bedtime.oneMoreOn = today; Log.d(TAG, "kid debug: lessons open today") }
        // 2.5.0: --ei practice <minutes> adds practice to today (the wardrobe's ledger takes it in at the next sync).
        intent.getIntExtra("practice", 0).takeIf { it > 0 }?.let { m -> prefs.addPractice(today, m * 60); Log.d(TAG, "kid debug: $m minutes of practice added today") }
    }

    /**
     * The activity is singleTop (manifest): the launcher icon, the reminder's notification and `adb shell am start` bring
     * this one back instead of stacking a second MainActivity on it (each with its own mic, voice, MIDI and sampler).
     * Debug builds: a route in the new intent (--es screen, song, lesson, profile, --ez daily) opens as a fresh launch would.
     */
    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        // The weekly brief's notification on an app already running: its brief opens over the screen that is up.
        briefFrom(intent)?.let { req ->
            Log.d(TAG, "brief: notification opens ${req.first} ${req.second}")
            briefSamples = applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0 && intent.getBooleanExtra("brief_samples", false)
            briefRequest.value = req
            return
        }
        val debuggable = applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
        if (!debuggable || (listOf("screen", "song", "lesson", "profile").none { intent.getStringExtra(it) != null } && !intent.getBooleanExtra("daily", false))) return
        setIntent(intent)
        intent.getStringExtra("profile")?.let(profiles::switchTo)
        openApp(fromLaunch = true)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        shownRoute?.let { outState.putString(ROUTE, it) }
        briefRequest.value?.let { (id, period) -> outState.putString(BRIEF, "$id|${period.name}") }
        if (openId == null) outState.putBoolean(PICKER, true)
    }

    /**
     * The screen [route] names (see [routeOf]), each part going back to the one before it. A song reopens in wait mode at
     * its saved position, as from the library. A kid's Settings, Mic setup, Privacy and Licences sit behind the parent
     * gate, which a recreate closes: they reopen as the screen they go back to.
     */
    private fun screenAt(route: String, prefs: Prefs, kid: Boolean): Screen = route.split("/").fold<String, Screen>(Screen.Home()) { back, part ->
        val arg = part.substringAfter(":", "")
        when (part.substringBefore(":")) {
            "home" -> Screen.Home(HomeTab.valueOf(arg.substringBefore(":")),
                songs = if (arg.substringAfter(":", "") == "chords") com.bigpapituna.sharpears.ui.SongsSection.BY_CHORDS else com.bigpapituna.sharpears.ui.SongsSection.LIBRARY)
            "song" -> repo.all().first { it.id == arg }.let { m ->
                val p = prefs.progress(m.id)
                Screen.Player(repo.load(m), m, PlayerStart(Mode.WAIT, mic = prefs.micDefault && mic.hasPermission()),
                    if (p.completed) 0.0 else p.lastPositionMs.toDouble(), back)
            }
            "lesson" -> arg.substringAfter(":").let { id -> COURSE.flatMap { it.lessons }.first { it.id == id } }
                .let { Screen.Lesson(it, back, arg.substringBefore(":").toInt().coerceIn(0, it.steps.lastIndex)) }
            "micsetup" -> if (kid) back else Screen.MicSetup(back)
            "sight" -> Screen.SightRead(back, arg.toInt())
            "privacy" -> if (kid) back else Screen.Privacy(back)
            "licences" -> if (kid) back else Screen.Licences(back)
            "settings" -> if (kid) back else Screen.Settings(back)
            "ear" -> Screen.EarTraining(back)
            "onboarding" -> Screen.Onboarding
            "theory" -> Screen.Theory(back)
            "world" -> Screen.World(back, WorldTab.valueOf(arg))
            "play" -> Screen.Play(Toy.valueOf(arg), back)
            "kidsongs" -> Screen.KidSongs(back)
            "kidchords" -> Screen.KidChords(back)
            "popsong" -> arg.split(":").let { a ->
                Screen.PopSong(a[0], back, a.getOrNull(1)?.toIntOrNull() ?: 0, com.bigpapituna.sharpears.engine.chords.PopSong.Pattern.entries.firstOrNull { it.name == a.getOrNull(2) },
                    a.getOrNull(3)?.toIntOrNull()?.takeIf { it > 0 })
            }
            "kidstickers" -> Screen.KidStickers(back, StickerPage.valueOf(arg))
            "finale" -> Screen.Finale(arg)
            "learnchords" -> if (kid) back else Screen.LearnChords(back)
            "chordgames" -> if (kid) back else Screen.ChordGames(back)
            "progressions" -> if (kid) back else Screen.Progressions(back)
            else -> back
        }
    }

    // A session of the parent brief's log runs while the app is on screen as someone (the picker is no one).
    override fun onStart() {
        super.onStart(); com.bigpapituna.sharpears.engine.ForegroundClock.app.start(); openId?.let(Briefs::sessionStart)
        com.bigpapituna.sharpears.analytics.Tester.back()
    }
    override fun onStop() {
        Briefs.sessionEnd(); com.bigpapituna.sharpears.engine.ForegroundClock.app.stop(); com.bigpapituna.sharpears.analytics.Tester.away()
        super.onStop()
    }

    override fun onPause() {
        Juice.world?.flush()            // the treat jar saves every few notes; the rest now
        super.onPause()
    }

    override fun onDestroy() {
        openVoice?.release()
        mic.stop()
        midi.stop()
        sampler.release()
        super.onDestroy()
    }
}
