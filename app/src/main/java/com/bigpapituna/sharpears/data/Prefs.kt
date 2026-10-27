package com.bigpapituna.sharpears.data

import android.content.Context
import android.util.Log
import androidx.compose.runtime.mutableIntStateOf
import com.bigpapituna.sharpears.TAG

/** What the library shows per song. [bestAccuracy] is a Perform-mode score, 0..100, or -1 before the first run. */
data class SongProgress(val lastPositionMs: Long = 0, val completed: Boolean = false, val bestAccuracy: Int = -1,
                        /** The worst bar of the last Wait or Perform run, or null when it was clean. */
                        val troubleBar: Int? = null)

enum class KeyLabels { OFF, C_ONLY, ALL }

/**
 * Progress and settings in one SharedPreferences file (PLAN 5.9). Keys are per song id; no migrations in v1.
 * [version] bumps on every write so screens that read progress recompose. One file per profile since 2.1
 * ("progress" for the owner, "progress.<id>" for everyone else, see [ProgressFiles.fileFor]).
 */
class Prefs(context: Context, val profileId: String) {
    private val p = context.getSharedPreferences(ProgressFiles.fileFor("progress", profileId), Context.MODE_PRIVATE)
    val version = mutableIntStateOf(0)

    fun progress(id: String) = SongProgress(p.getLong("pos:$id", 0), p.getBoolean("done:$id", false), p.getInt("best:$id", -1),
        troubleBars(id).firstOrNull())

    /** The hardest bars of the song's last Wait or Perform run, worst first ("trouble:<id>" = "12,13,27"). */
    fun troubleBars(id: String): List<Int> = com.bigpapituna.sharpears.engine.TroubleSpots.decode(p.getString("trouble:$id", null))
    fun saveTroubleBars(id: String, spots: List<com.bigpapituna.sharpears.engine.TroubleSpot>) =
        edit { if (spots.isEmpty()) remove("trouble:$id") else putString("trouble:$id", com.bigpapituna.sharpears.engine.TroubleSpots.encode(spots)) }

    fun savePosition(id: String, ms: Long) = edit { putLong("pos:$id", ms) }
    fun markCompleted(id: String) = edit { putBoolean("done:$id", true); putLong("pos:$id", 0) }
    /** Returns true when [accuracy] beats the saved best. */
    fun saveScore(id: String, accuracy: Int): Boolean {
        if (accuracy <= progress(id).bestAccuracy) return false
        edit { putInt("best:$id", accuracy) }
        return true
    }

    var keyLabels: KeyLabels
        get() = KeyLabels.entries.getOrElse(p.getInt("keyLabels", KeyLabels.C_ONLY.ordinal)) { KeyLabels.C_ONLY }
        set(v) = edit { putInt("keyLabels", v.ordinal) }
    /** Key labels were ever set (a new kid profile starts with every white key labelled, until someone chooses). */
    val keyLabelsChosen: Boolean get() = p.contains("keyLabels")
    /** App sound for touch and listen mode. The mic still mutes it unless headphones are on. */
    var sound: Boolean
        get() = p.getBoolean("sound", true)
        set(v) = edit { putBoolean("sound", v) }
    /** The mic was on the last time a song was played; songs open with it on again. */
    var micDefault: Boolean
        get() = p.getBoolean("micDefault", false)
        set(v) = edit { putBoolean("micDefault", v) }
    /** The mic was ever switched on or off here (the onboarding, the player's mic button); a kid starts with it on until then. */
    val micDefaultChosen: Boolean get() = p.contains("micDefault")

    /** Kokoro (recorded and on-phone) instead of the phone's own text-to-speech voices. */
    var naturalVoice: Boolean
        get() = p.getBoolean("naturalVoice", true)
        set(v) = edit { putBoolean("naturalVoice", v) }

    /** The chosen text-to-speech voice (engine id), or null for the best available. */
    var voiceName: String?
        get() = p.getString("voiceName", null)
        set(v) = edit { putString("voiceName", v) }

    /** Spoken guidance: lesson steps and feedback read aloud. */
    var voice: Boolean
        get() = p.getBoolean("voice", true)
        set(v) = edit { putBoolean("voice", v) }

    /** 2.13, Settings > Playing > Encouragement: how often the pup pops up mid-lesson with a few kind words (Often). */
    var cheerFrequency: com.bigpapituna.sharpears.engine.lessons.CheerFrequency
        // 2.15: never chosen, the dashboard's flag cheer_frequency_default may set where it starts (Often without it).
        get() = p.getString("cheer", null)?.let(com.bigpapituna.sharpears.engine.lessons.CheerFrequency::parse) ?: com.bigpapituna.sharpears.analytics.AppFlags.cheerFrequencyDefault
        set(v) = edit { putString("cheer", v.wire) }
    /** The pup says the pops aloud too: null until chosen, which is on for a kid and off for a grown-up (CheerRules.aloud). */
    var cheerAloud: Boolean?
        get() = if (p.contains("cheerAloud")) p.getBoolean("cheerAloud", false) else null
        set(v) = edit { if (v == null) remove("cheerAloud") else putBoolean("cheerAloud", v) }

    /**
     * 2.15, Settings > Playing > Plain look (engine/family/PlainLook.kt): the switch as saved. Off by default; a little kid's
     * profile ignores it (PlainLook.on asks the audience), and nothing else stored changes when it flips.
     */
    var plainLook: Boolean
        get() = p.getBoolean("plainLook", false)
        set(v) = edit { putBoolean("plainLook", v) }
    /** Spoken guidance while the Plain look is on: null until set there (off, PlainLook.voiceOn); [voice] is kept for later. */
    var plainVoice: Boolean?
        get() = if (p.contains("plainVoice")) p.getBoolean("plainVoice", false) else null
        set(v) = edit { if (v == null) remove("plainVoice") else putBoolean("plainVoice", v) }
    /** The step pop's chime in the Plain look ("Step sound", off by default). */
    var plainStepSound: Boolean
        get() = p.getBoolean("plainStepSound", false)
        set(v) = edit { putBoolean("plainStepSound", v) }
    /** The one-time "Prefer a plainer look?" card was shown (or answered) on this profile. */
    var plainOffered: Boolean
        get() = p.getBoolean("plainOffered", false)
        set(v) = edit { putBoolean("plainOffered", v) }

    /** Note names inside the falling notes. */
    var noteNames: Boolean
        get() = p.getBoolean("noteNames", true)
        set(v) = edit { putBoolean("noteNames", v) }
    /** Suggested finger numbers on the falling notes and the waiting key. */
    var fingerNumbers: Boolean
        get() = p.getBoolean("fingers", true)
        set(v) = edit { putBoolean("fingers", v) }

    /** Transpose per song, in semitones (-6..6). */
    fun transpose(id: String) = p.getInt("transpose:$id", 0)
    fun saveTranspose(id: String, semitones: Int) = edit { if (semitones == 0) remove("transpose:$id") else putInt("transpose:$id", semitones) }

    /** The daily practice reminder: on or off, and its time in minutes after midnight (default 18:30). */
    var reminderOn: Boolean
        get() = p.getBoolean("reminderOn", false)
        set(v) = edit { putBoolean("reminderOn", v) }
    var reminderMinutes: Int
        get() = p.getInt("reminderMinutes", 18 * 60 + 30)
        set(v) = edit { putInt("reminderMinutes", v) }

    /** Lines across the falling notes at each beat. Off by default; rhythm lessons show them anyway. */
    var beatLines: Boolean
        get() = p.getBoolean("beatLines", false)
        set(v) = edit { putBoolean("beatLines", v) }

    /** Hit-line light for right and wrong notes; off, right notes flash green on the keys instead (the red border is gone). */
    var feedbackLight: Boolean
        get() = p.getBoolean("feedbackLight", true)
        set(v) = edit { putBoolean("feedbackLight", v) }

    /** The ghost hand over the player's keys, showing which finger plays next. */
    var ghostHand: Boolean
        get() = p.getBoolean("ghostHand", true)
        set(v) = edit { putBoolean("ghostHand", v) }

    /** A light buzz on right and wrong notes played on the touch keys (never for the mic or MIDI). */
    var haptics: Boolean
        get() = p.getBoolean("haptics", true)
        set(v) = edit { putBoolean("haptics", v) }

    /**
     * Learn course: lessons finished (exercise played through, or marked done) or skipped by placement (2.6). The path
     * (next lesson, the map's walk) reads this; anything that credits the learner (stars, XP, stickers, finales, units
     * complete) reads [lessonPlayed] instead.
     */
    fun lessonDone(id: String) = p.getBoolean("lesson:$id", false)
    /** Finished for real: done and not a placement skip. */
    fun lessonPlayed(id: String) = lessonDone(id) && !p.getBoolean("skip:$id", false)
    /** Marked "done, skipped" by placement and not played since: not attempted, no stars. */
    fun lessonSkipped(id: String) = lessonDone(id) && p.getBoolean("skip:$id", false)
    /** Every lesson placement skipped and nobody has played since. */
    fun skippedLessons(): Set<String> = p.all.filter { (k, v) -> k.startsWith("skip:") && v == true }.keys.map { it.removePrefix("skip:") }
        .filter { p.getBoolean("lesson:$it", false) }.toSet()
    /** A real finish: it clears a placement skip on the lesson (it has been played now). */
    fun markLessonDone(id: String) = edit { putBoolean("lesson:$id", true); remove("skip:$id") }
    /** The lesson as never finished: the kid home's debug seed (2.2) and the parent brief's fresh start (2.4). */
    fun forgetLessonDone(id: String) = edit { remove("lesson:$id"); remove("skip:$id") }

    /**
     * Applies a placement plan (2.6, engine/family/Placement.kt): its lessons that are neither played nor skipped yet are
     * marked "done, skipped", and lessons skipped before that the plan no longer skips are opened again. Played lessons are
     * never touched. Returns the engine's [com.bigpapituna.sharpears.engine.family.Placement.Reconcile].
     */
    fun applyPlacement(plan: com.bigpapituna.sharpears.engine.family.PlacementPlan): com.bigpapituna.sharpears.engine.family.Placement.Reconcile {
        val r = com.bigpapituna.sharpears.engine.family.Placement.reconcile(plan, ::lessonDone, skippedLessons())
        if (r.changes) {
            edit {
                r.skip.forEach { putBoolean("lesson:$it", true); putBoolean("skip:$it", true) }
                r.reopen.forEach { remove("lesson:$it"); remove("skip:$it") }
            }
            Log.d(TAG, "placement: ${plan.level} for ${plan.kind}: ${r.skip.size} lessons skipped, ${r.reopen.size} opened again")
        }
        return r
    }

    /** A parent asked for "Let's hear you play" for a new kid (2.6): it opens on their first start, once. */
    var placementCheckPending: Boolean
        get() = p.getBoolean("placementCheck", false)
        set(v) = edit { if (v) putBoolean("placementCheck", true) else remove("placementCheck") }

    /** Chord games: best score (out of 10) per game and level, or -1 before the first full round. */
    fun gameBest(game: String, level: Int) = p.getInt("game:$game:$level", -1)
    /** Returns true when [score] beats the saved best. */
    fun saveGameBest(game: String, level: Int, score: Int): Boolean {
        if (score <= gameBest(game, level)) return false
        edit { putInt("game:$game:$level", score) }
        return true
    }

    /** The song opened most recently; the library offers to continue it. */
    var lastSongId: String?
        get() = p.getString("lastSong", null)
        set(v) = edit { putString("lastSong", v) }

    /**
     * What was played last, song or lesson (2.7, Today's Continue tile): "song:<id>", "pop:<id>" (a song by chords) or
     * "lesson:<id>" (engine/nav/Today.kt, ContinueWith). Null before 2.7: the tile then reads [lastSongId].
     */
    var lastPlayed: String?
        get() = p.getString("lastPlayed", null)
        set(v) = edit { if (v == null) remove("lastPlayed") else putString("lastPlayed", v) }

    /**
     * The layout this person chose (2.8, engine/nav/Layout.kt and Simple.kt), or null before they chose: a grown-up is asked at
     * launch, and until then their piano level decides (Simple for Never and A little). 2.7's Simple switch ("simpleMode") is
     * a choice already: on reads as Simple, off as Full (2.7's Advanced was the whole app); the first new choice replaces it.
     * A layout only filters what is shown; nothing else is kept apart.
     */
    var layout: com.bigpapituna.sharpears.engine.nav.Layout?
        get() = com.bigpapituna.sharpears.engine.nav.Layout.parse(p.getString("layout", null))
            ?: if (p.contains("simpleMode")) com.bigpapituna.sharpears.engine.nav.Layout.fromSimpleMode(p.getBoolean("simpleMode", false)) else null
        set(v) = edit { remove("simpleMode"); if (v == null) remove("layout") else putString("layout", v.wire) }

    /** The 2.7 tour (engine/nav/Welcome.kt) was walked or skipped by this profile: it never shows again. */
    var tourDone: Boolean
        get() = p.getBoolean("tourDone", false)
        set(v) = edit { putBoolean("tourDone", v) }

    /** The versionName whose What's new this profile saw (or skipped as a newcomer, or as a kid): it shows once per version. */
    var whatsNewSeen: String?
        get() = p.getString("whatsNewSeen", null)
        set(v) = edit { if (v == null) remove("whatsNewSeen") else putString("whatsNewSeen", v) }

    /**
     * 2.15: the one-time tips this profile has been shown or has no need for (engine/nav/Tips.kt), ids joined by commas. Written
     * without bumping [version] (a tip is not a change any screen shows) and kept through Reset progress, like the tour.
     */
    val tipsSeen: Set<String> get() = com.bigpapituna.sharpears.engine.nav.Tips.parse(p.getString("tipsSeen", null))

    /** [tip] is on a screen now: never again for this profile, and no other tip this session. */
    fun tipShown(tip: com.bigpapituna.sharpears.engine.nav.Tips.Tip) {
        tipUsed(tip)
        com.bigpapituna.sharpears.engine.nav.TipSession.shown = true
    }

    /** The feature [tip] points at was used: its tip is not needed. */
    fun tipUsed(tip: com.bigpapituna.sharpears.engine.nav.Tips.Tip) {
        if (tip.id in tipsSeen) return
        p.edit().putString("tipsSeen", com.bigpapituna.sharpears.engine.nav.Tips.encode(tipsSeen + tip.id)).apply()
    }

    /** Whether this profile has played any sight-reading level (the old pieces, 1 and 2, or a staff drill, 3 to 8). */
    fun sightTried(): Boolean = (1..2).any { sightBest(it) != null } || (3..8).any { sightDrillBest(it) != null }

    /** Debug (--ez tips true): the tips and the recap count start again. */
    fun resetTips() { p.edit().remove("tipsSeen").remove("hardRecaps").apply() }

    /** Song recaps with hard bars so far (the loop tip waits for the third); counted by the player's finish, grown-ups only. */
    var hardRecaps: Int
        get() = p.getInt("hardRecaps", 0)
        set(v) { p.edit().putInt("hardRecaps", v).apply() }

    /** Today's "Set up the microphone" banner was put away by hand (2.7); it also goes for good once the mic is allowed. */
    var micBannerClosed: Boolean
        get() = p.getBoolean("micBannerClosed", false)
        set(v) = edit { putBoolean("micBannerClosed", v) }

    /**
     * 2.14: the lesson left part way and its step ("key-g:2", engine/Comeback.kt encodeLeft), kept as the steps go by and
     * cleared when the lesson is finished: the comeback opens there. Written without bumping [version] (a step is not a change
     * any screen shows).
     */
    var openStep: String?
        get() = p.getString("openStep", null)
        set(v) { p.edit().apply { if (v == null) remove("openStep") else putString("openStep", v) }.apply() }

    /** 2.14: "I know these chords" was answered or put away ("Not now"): Today stops offering it. */
    var knownChordsAsked: Boolean
        get() = p.getBoolean("knownChordsAsked", false)
        set(v) = edit { putBoolean("knownChordsAsked", v) }

    /** Tutorial alignment: video second at which the song's first note sounds, per song and video. */
    fun anchor(songId: String, videoId: String): Float? = p.getFloat("anchor:$songId:$videoId", -1f).takeIf { it >= 0 }
    fun saveAnchor(songId: String, videoId: String, sec: Float) = edit { putFloat("anchor:$songId:$videoId", sec) }
    /** Second anchor: video second at which the song's last note sounds. */
    fun lastAnchor(songId: String, videoId: String): Float? = p.getFloat("anchor2:$songId:$videoId", -1f).takeIf { it >= 0 }
    fun saveLastAnchor(songId: String, videoId: String, sec: Float) = edit { putFloat("anchor2:$songId:$videoId", sec) }

    /** Practice time: seconds the transport played, per day ("practice:yyyy-MM-dd"); days past 60 are dropped. */
    fun addPractice(day: java.time.LocalDate, seconds: Int) {
        if (seconds <= 0) return
        val old = com.bigpapituna.sharpears.engine.PracticeStats.stale(practice().keys, day)
        edit {
            putInt("practice:$day", p.getInt("practice:$day", 0) + seconds)
            old.forEach { remove("practice:$it") }
        }
    }
    fun practice(): Map<java.time.LocalDate, Int> = p.all.mapNotNull { (k, v) ->
        if (!k.startsWith("practice:") || v !is Int) null
        else runCatching { java.time.LocalDate.parse(k.removePrefix("practice:")) to v }.getOrNull()
    }.toMap()

    /** Which hands a song has been played through with (wait or Perform runs that reached the end). */
    fun handsDone(id: String): Set<com.bigpapituna.sharpears.engine.Practice> =
        p.getString("hands:$id", "").orEmpty().split(',').mapNotNull { n -> com.bigpapituna.sharpears.engine.Practice.entries.firstOrNull { it.name == n } }.toSet()
    fun addHandDone(id: String, hand: com.bigpapituna.sharpears.engine.Practice) {
        val now = handsDone(id) + hand
        edit { putString("hands:$id", now.joinToString(",") { it.name }) }
    }
    /** The hands-apart tip that was dismissed for a song ("right", "left" or "together"), so it stays away. */
    fun handsTipDismissed(id: String) = p.getString("handsTip:$id", null)
    fun dismissHandsTip(id: String, stage: String) = edit { putString("handsTip:$id", stage) }

    /** The Auto speed of a song ("auto:<id>" = "72,2026-10-07": percent and the day of the last nudge). */
    fun autoSpeed(id: String) = com.bigpapituna.sharpears.engine.AutoSpeed.decode(p.getString("auto:$id", null))
    fun saveAutoSpeed(id: String, a: com.bigpapituna.sharpears.engine.AutoSpeed) = edit { putString("auto:$id", a.encode()) }

    /** Sight-reading: the best run per level (fewest points: seconds plus two per wrong note). */
    fun sightBest(level: Int) = com.bigpapituna.sharpears.engine.SightScore.decode(p.getString("sight:$level", null))
    /** Returns true when [s] beats the saved best. */
    fun saveSightBest(level: Int, s: com.bigpapituna.sharpears.engine.SightScore): Boolean {
        val old = sightBest(level)
        if (old != null && old.points <= s.points) return false
        edit { putString("sight:$level", s.encode()) }
        return true
    }

    /** 2.14: the best two-minute staff drill at [level] (3 to 8): most right, then fewest wrong ("sightDrill:<level>"). */
    fun sightDrillBest(level: Int) = com.bigpapituna.sharpears.engine.DrillScore.decode(p.getString("sightDrill:$level", null))
    /** Returns true when [s] beats the saved best. */
    fun saveSightDrillBest(level: Int, s: com.bigpapituna.sharpears.engine.DrillScore): Boolean {
        if (!s.beats(sightDrillBest(level))) return false
        edit { putString("sightDrill:$level", s.encode()) }
        return true
    }

    /** "Practice today" finished on [day]: the summary row, or null. */
    fun dailySummary(day: java.time.LocalDate): String? = p.getString("daily:$day", null)
    fun saveDailySummary(day: java.time.LocalDate, summary: String) = edit {
        p.all.keys.filter { it.startsWith("daily:") }.forEach(::remove)
        putString("daily:$day", summary)
    }

    fun resetProgress() {
        val keys = p.all.keys.filter { it.startsWith("pos:") || it.startsWith("done:") || it.startsWith("best:") || it.startsWith("trouble:") || it.startsWith("auto:") || it.startsWith("hands:") || it.startsWith("handsTip:") || it == "lastSong" || it == "lastPlayed" || it.startsWith("practice:") || it.startsWith("sight:") || it.startsWith("sightDrill:") ||
            it.startsWith("daily:") }      // today's "Done for today: ... 11 min." went on showing the minutes just cleared
        edit { keys.forEach(::remove) }
    }

    fun logRestored() = Log.d(TAG, "prefs restored: " + p.all.entries.sortedBy { it.key }.joinToString { "${it.key}=${it.value}" })

    private inline fun edit(block: android.content.SharedPreferences.Editor.() -> Unit) {
        p.edit().apply(block).apply()
        version.intValue++
    }
}
