package com.bigpapituna.sharpears.engine.brief

import com.bigpapituna.sharpears.engine.family.ProfileKind

/*
 * The seven actions behind a recommendation's button (docs/PARENT-BRIEF-PLAN.md, CONTRACT.md "The action menu"), Android-free.
 *
 * [BriefActions.perform] runs one through an injected [Effects] (the integrator wires it to KidStore, CoursePath, Profiles,
 * BedtimeStore, the Duet picker and the song shelf; ui/brief/DemoEffects.kt is a harmless in-memory one), and returns a
 * [Performed]: "Done", with [Performed.undo] when the action can be taken back. An action is recorded for the next brief
 * (Briefs.action, through [BriefActions.record]) once its undo window has passed ([Performed.commit]), so an undone action
 * never shows up as something a grown-up did. Actions that cannot be taken back (opening a lesson, starting a duet) are
 * recorded at once.
 */

/** Takes an action back. Called at most once. */
fun interface Undo { fun undo() }

/** Thrown by an [Effects] method the app has not connected yet: the card says so instead of pretending. */
class NotConnected(what: String) : UnsupportedOperationException(what)

/** A song a grown-up may put on the shelf. */
data class SongChoice(val id: String, val title: String)

/**
 * What an action does on this phone, for one child (the integrator builds one per profile). Methods that can be taken back
 * return an [Undo] that restores what was there before; the ones that only open something return Unit. A method may throw:
 * [BriefActions] turns it into [Outcome.Failed] and records nothing.
 */
interface Effects {
    /** Pins [lessonId] as the next stone until [untilMs] (epoch ms), or the path's next lesson when null. Cleared once it is played. */
    fun setNext(lessonId: String?, untilMs: Long): Undo?
    /** Opens [lessonId] again for more stars (navigation: no undo). */
    fun replay(lessonId: String)
    /** Starts a Duet with the grown-up on the left hand; [lessonId] or [songId] is what to play, none lets the grown-up pick (navigation). */
    fun practiseTogether(lessonId: String?, songId: String?)
    /** Resets the stars of a Playground page's lessons so it can be played fresh; the undo puts them back. */
    fun freshStart(pageId: String): Undo?
    /** Focus mode: pages after [pageId] are hidden for now. */
    fun focus(pageId: String): Undo?
    /** Opens the page after [pageId] early. */
    fun unlockNext(pageId: String): Undo?
    /** One tap setting; [value] is what the contract says (a boolean as "true", micEasier the new offset in dB). */
    fun changeSetting(setting: SettingId, value: String): Undo?
    /** Puts [songId] on the child's shelf with the grown-up's [note] (never sent anywhere); [afterSongId] is the song they mastered. */
    fun assignSong(songId: String, note: String, afterSongId: String?): Undo?
    /** Songs worth offering for Teacher mode, the one mastered ([afterSongId]) left out. */
    fun songChoices(afterSongId: String?): List<SongChoice> = emptyList()
}

/** What the grown-up added in the sheet: Teacher mode's song and note. */
data class ActionInput(val song: String? = null, val note: String = "")

/** The result of [BriefActions.perform]. */
sealed interface Outcome {
    data class Done(val performed: Performed) : Outcome
    data class Failed(val reason: String) : Outcome
}

/**
 * An action that has been done. [undo] takes it back (true when it did) before [commit] writes it to the brief's log;
 * [commit] records it once, and does nothing after an undo. [reversible] is false for actions that only open something.
 */
class Performed internal constructor(
    val action: ActionId,
    val params: Map<String, String>,
    private val undoWith: Undo?,
    private val record: (String, Map<String, String>) -> Unit,
) {
    private var state = 0       // 0 open, 1 undone, 2 committed
    val reversible: Boolean get() = undoWith != null
    val undone: Boolean get() = state == 1
    val committed: Boolean get() = state == 2

    fun undo(): Boolean {
        if (state != 0 || undoWith == null) return false
        state = 1
        runCatching { undoWith?.undo() }
        return true
    }

    fun commit() {
        if (state != 0) return
        state = 2
        record(action.name, params)
    }
}

/** What the confirmation sheet and the card say about one candidate. */
data class ActionView(
    val action: ActionId,
    /** The ChunkyButton on the recommendation card. */
    val button: String,
    /** The sheet's heading, a question. */
    val title: String,
    val body: String,
    /** The sheet's yes button (the first one, for two confirmations). */
    val confirm: String,
    /** 2 for a fresh start (the second sheet is [secondTitle], [secondBody], [secondConfirm]); 1 otherwise. */
    val confirmations: Int = 1,
    val secondTitle: String = "",
    val secondBody: String = "",
    val secondConfirm: String = "",
    /** What the card says once it is done ("Done" is the pill; this is the line beside it). */
    val doneLine: String,
    /** Teacher mode: the sheet asks for a song and a note. */
    val needsSong: Boolean = false,
    /** Whether the action can be taken back with Undo. */
    val reversible: Boolean,
)

/** Who has a title: lessons, songs and Playground pages, filled in on the phone from the ids (never from a model). */
interface BriefTitles {
    fun lesson(id: String): String
    fun song(id: String): String
    fun page(id: String): String
}

/** Titles made from the ids, for tests and previews: "pond-5" becomes "Pond 5". */
object IdTitles : BriefTitles {
    private fun pretty(id: String) = id.split('-', '_', '.').filter { it.isNotEmpty() }.joinToString(" ").replaceFirstChar { it.uppercase() }
    override fun lesson(id: String) = pretty(id)
    override fun song(id: String) = pretty(id)
    override fun page(id: String) = pretty(id)
}

/** Who may take which action. */
object ActionRules {
    /** Actions that only make sense for a child (CONTRACT.md: grown-ups never get these). */
    private val KID_ONLY = setOf(ActionId.FRESH_START, ActionId.PACE_FOCUS, ActionId.PACE_UNLOCK, ActionId.TEACHER_ASSIGN)
    private val KID_SETTINGS = setOf(SettingId.READER.wire, SettingId.BEDTIME.wire)

    /** True when [c]'s button is shown on a [kind]'s brief: "My week" for grown-ups has no kid-only actions. */
    fun allowed(kind: ProfileKind, c: Candidate): Boolean {
        if (kind == ProfileKind.KID) return true
        if (c.action in KID_ONLY) return false
        if (c.action == ActionId.SETTING && c.params["setting"] in KID_SETTINGS) return false
        return true
    }

    /** The candidates a screen may draw buttons for. */
    fun usable(kind: ProfileKind, candidates: List<Candidate>): List<Candidate> = candidates.filter { allowed(kind, it) }
}

/**
 * Performs the actions. [name] is the person's first name (inserted into the sheets on the phone), [titles] names lessons
 * and pages, [record] writes one action to the brief's log for that person (Briefs.logFor(childId, ParentAction)): not
 * Briefs.action, which logs for whoever is playing, and the grown-up's phone may be open as someone else.
 */
class BriefActions(
    private val effects: Effects,
    private val titles: BriefTitles,
    private val name: String,
    private val record: (String, Map<String, String>) -> Unit,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    /** The card and sheet words for [c]. */
    fun view(c: Candidate): ActionView = ActionCopy.of(c, titles, name)

    /** Songs for Teacher mode's picker. */
    fun songChoices(c: Candidate): List<SongChoice> = runCatching { effects.songChoices(c.params["after"]) }.getOrDefault(emptyList())

    /** Does what [c] says (after the sheet's confirmations). Nothing is recorded until [Performed.commit], or at once if it cannot be undone. */
    fun perform(c: Candidate, input: ActionInput = ActionInput()): Outcome {
        val p = c.params
        fun need(key: String): String? = p[key]?.takeIf { it.isNotBlank() }
        return try {
            val undo: Undo? = when (c.action) {
                ActionId.SET_NEXT -> effects.setNext(need("lesson"), clock() + DAY_MS)
                ActionId.REPLAY_FOR_STARS -> { effects.replay(need("lesson") ?: return Outcome.Failed("There is no lesson to open.")); null }
                ActionId.PRACTISE_TOGETHER -> { effects.practiseTogether(need("lesson"), need("song")); null }
                ActionId.FRESH_START -> effects.freshStart(need("page") ?: return Outcome.Failed("There is no page to start again."))
                ActionId.PACE_FOCUS -> effects.focus(need("page") ?: return Outcome.Failed("There is no page to focus on."))
                ActionId.PACE_UNLOCK -> effects.unlockNext(need("page") ?: return Outcome.Failed("There is no page to open after."))
                ActionId.SETTING -> {
                    val setting = SettingId.of(p["setting"]) ?: return Outcome.Failed("That setting is not one this app changes.")
                    effects.changeSetting(setting, p["value"].orEmpty())
                }
                ActionId.TEACHER_ASSIGN -> {
                    val song = input.song?.takeIf { it.isNotBlank() } ?: return Outcome.Failed("Pick a song first.")
                    effects.assignSong(song, input.note.trim().take(MAX_NOTE), need("after"))
                }
            }
            val params = recorded(c, input)
            val performed = Performed(c.action, params, undo, record)
            if (undo == null) performed.commit()
            Outcome.Done(performed)
        } catch (e: NotConnected) {
            Outcome.Failed("This one is not connected in this version yet. Nothing was changed.")
        } catch (e: Exception) {
            Outcome.Failed("That did not work just now. Nothing was changed.")
        }
    }

    /** What the log keeps for the action: the candidate's params, Teacher mode's song and note. */
    private fun recorded(c: Candidate, input: ActionInput): Map<String, String> =
        if (c.action != ActionId.TEACHER_ASSIGN) c.params
        else c.params + listOfNotNull(input.song?.let { "song" to it }, input.note.trim().takeIf { it.isNotEmpty() }?.let { "note" to it.take(MAX_NOTE) })

    companion object {
        const val DAY_MS = 24L * 60 * 60 * 1000
        /** A Teacher mode note is kept this short. */
        const val MAX_NOTE = 140
        /** How long Undo stays on a card before the action is recorded (also when its screen closes). */
        const val UNDO_WINDOW_MS = 60_000L
    }
}

/** The words of every action's card and sheets: warm, short, sentence case, no dashes. */
object ActionCopy {
    fun of(c: Candidate, t: BriefTitles, name: String): ActionView {
        val p = c.params
        val lesson = p["lesson"]?.let(t::lesson)
        val song = p["song"]?.let(t::song)
        val page = p["page"]?.let(t::page)
        return when (c.action) {
            ActionId.SET_NEXT -> ActionView(c.action,
                button = "Set as next",
                title = if (lesson != null) "Make $lesson the next stone?" else "Put the next lesson first?",
                body = (if (lesson != null) "$name will find $lesson waiting as the next stone for a day." else "$name will find the next lesson waiting first for a day.") +
                    " Everything else stays where it is, and you can undo it.",
                confirm = "Yes, set it",
                doneLine = if (lesson != null) "$lesson is $name's next stone for a day." else "The next lesson is first for a day.",
                reversible = true)
            ActionId.REPLAY_FOR_STARS -> ActionView(c.action,
                button = "Replay for stars",
                title = "Play ${lesson ?: "it"} again?",
                body = "It opens now, so $name can go for more stars. Earlier stars are kept, so nothing is lost.",
                confirm = "Open it",
                doneLine = "Opening ${lesson ?: "the lesson"}.",
                reversible = false)
            ActionId.PRACTISE_TOGETHER -> {
                val what = lesson ?: song
                ActionView(c.action,
                    button = "Practise together",
                    title = "Sit down with $name?",
                    body = (if (what != null) "A duet on $what: you take the left hand and $name the right." else "A duet: you take the left hand and $name the right. You pick what to play once it opens.") +
                        " Both of you get the stars.",
                    confirm = "Start together",
                    doneLine = "Starting a duet.",
                    reversible = false)
            }
            ActionId.FRESH_START -> ActionView(c.action,
                button = "Fresh start",
                title = "Start ${page ?: "this page"} again?",
                body = "The stars $name won on ${page ?: "this page"} go back to none, so its lessons can be played fresh. Stickers and finales stay.",
                confirm = "Continue",
                confirmations = 2,
                secondTitle = "Are you sure?",
                secondBody = "${page ?: "This page"} starts again for $name. You can undo it from the card for a little while after.",
                secondConfirm = "Yes, start again",
                doneLine = "${page ?: "The page"} starts again for $name.",
                reversible = true)
            ActionId.PACE_FOCUS -> ActionView(c.action,
                button = "Focus on this page",
                title = "Focus on ${page ?: "this page"}?",
                body = "Later pages are hidden for now, so $name can stay with this one. You can open them again whenever you like.",
                confirm = "Focus here",
                doneLine = "Later pages are hidden for now.",
                reversible = true)
            ActionId.PACE_UNLOCK -> ActionView(c.action,
                button = "Open the next page",
                title = if (page != null) "Open the page after $page?" else "Open the next page?",
                body = "$name can start the next page early. The path carries on in order from there.",
                confirm = "Open it",
                doneLine = "The next page is open.",
                reversible = true)
            ActionId.SETTING -> setting(c, name)
            ActionId.TEACHER_ASSIGN -> ActionView(c.action,
                button = "Add a song to the shelf",
                title = "What should $name play next?",
                body = (p["after"]?.let { "${t.song(it)} is going really well. " } ?: "") + "Pick a song for $name's shelf. A note from you goes with it and stays on this phone.",
                confirm = "Add it",
                doneLine = "Added to $name's shelf.",
                needsSong = true,
                reversible = true)
        }
    }

    private fun setting(c: Candidate, name: String): ActionView {
        val on = c.params["value"] != "false"
        return when (SettingId.of(c.params["setting"])) {
            SettingId.READER -> ActionView(c.action,
                button = if (on) "Show letters" else "Back to animals",
                title = if (on) "Show letters on the keys?" else "Go back to animals on the keys?",
                body = if (on) "$name has been reading the letters, so the keys can name notes with letters instead of animals." else "The keys name notes with animals again.",
                confirm = "Yes, change it", doneLine = if (on) "The keys show letters." else "The keys show animals.", reversible = true)
            SettingId.BEDTIME -> ActionView(c.action,
                button = if (on) "Turn on bedtime" else "Turn off bedtime",
                title = if (on) "Say goodnight after the goal?" else "Stop saying goodnight?",
                body = if (on) "When $name reaches the daily goal the pup says goodnight, and lessons rest until tomorrow. Songs and play stay open." else "Lessons stay open after the daily goal.",
                confirm = "Yes, change it", doneLine = if (on) "Bedtime is on after the goal." else "Bedtime is off.", reversible = true)
            SettingId.COLOUR_NOTES -> ActionView(c.action,
                button = if (on) "Colour the notes" else "Plain notes",
                title = if (on) "Colour the notes?" else "Switch the note colours off?",
                body = if (on) "Colours on the keys and the falling notes make the right key easier to find." else "The notes go back to plain.",
                confirm = "Yes, change it", doneLine = if (on) "The notes are coloured." else "The notes are plain.", reversible = true)
            SettingId.MIC_EASIER -> ActionView(c.action,
                button = "Make the mic easier",
                title = "Make the microphone a little easier?",
                body = "The piano needs to be a little softer to be heard, about 3 dB. If the room is noisy, undo it or use the quiet room setting instead.",
                confirm = "Yes, make it easier", doneLine = "The microphone is a little easier.", reversible = true)
            SettingId.QUIET_ROOM -> ActionView(c.action,
                button = "Quiet the wrong key lights",
                title = "Stop painting wrong keys here?",
                body = "On this phone the keys stop lighting up when the microphone hears something else, which helps in a noisy room. You can turn it back on in Microphone setup.",
                confirm = "Yes, quiet them", doneLine = "Wrong key lights are off on this phone.", reversible = true)
            null -> ActionView(c.action, "Change a setting", "Change this setting?", "$name's settings change right away.", "Yes, change it", doneLine = "Changed.", reversible = true)
        }
    }
}
