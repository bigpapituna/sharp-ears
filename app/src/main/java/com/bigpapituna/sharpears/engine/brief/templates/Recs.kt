package com.bigpapituna.sharpears.engine.brief.templates

import com.bigpapituna.sharpears.engine.brief.ActionId
import com.bigpapituna.sharpears.engine.brief.ActionTaken
import com.bigpapituna.sharpears.engine.brief.Candidate
import com.bigpapituna.sharpears.engine.brief.RecommendationKind
import com.bigpapituna.sharpears.engine.brief.SettingId

/*
 * The recommendation lines: one bank per action (and per reason or setting where the sentence differs), each with at
 * least two phrasings, parameters filled with titles and settings. Never longer than 160 characters; when a long title
 * would push a line over, a shorter phrasing or the plain fallback is used.
 */
internal object RecBank {
    const val MAX = 160
    private val m = HashMap<String, List<L>>()
    private fun r(key: String, vararg ls: L) { m[key] = ls.toList() }
    fun lines(key: String): List<L> = m.getValue(key)
    fun sizes(): Map<String, Int> = m.entries.associate { "recommendation ${it.key}" to it.value.size }

    /** Short enough for any title, one per action. */
    val FALLBACK = mapOf(
        ActionId.SET_NEXT to l("Set a lesson as the next stone for a day.", "Set a lesson as your next stone for a day."),
        ActionId.REPLAY_FOR_STARS to l("Replay a lesson for more stars: a second go is usually quicker."),
        ActionId.PRACTISE_TOGETHER to l("Play a duet together, with you on the left hand.", "Play a duet with a second player on the left hand."),
        ActionId.FRESH_START to l("Restart a page for a fresh start. Two taps confirm it."),
        ActionId.PACE_FOCUS to l("Use focus mode to keep attention on one page for now."),
        ActionId.PACE_UNLOCK to l("Open the next page early: the last lessons were easy."),
        ActionId.SETTING to l("Change one setting to suit how the piano is going."),
        ActionId.TEACHER_ASSIGN to l("Pick a new song for the shelf and leave a note."),
    )

    init {
        r("SET_NEXT/lesson",
            l("Make {title} tomorrow's first stone: a fresh day often does what a fifth try will not.", "Make {title} your first lesson tomorrow: a fresh day often does what a fifth try will not."),
            l("Set {title} as the next stone for a day, so tomorrow opens with it.", "Set {title} as your next stone for a day, so tomorrow opens with it."),
            l("Pin {title} as the first stone next time and let it start fresh.", "Pin {title} as your first stone next time and let it start fresh."),
        )
        r("SET_NEXT/MOVE_ON",
            l("Set the path's next lesson as the next stone: a gentle nudge on from the favourites.", "Set the path's next lesson as your next stone: a gentle nudge on from the favourites."),
            l("Pin the next new lesson as the first stone for a day, with the favourites as a reward after.", "Pin the next new lesson as your first stone for a day, with the favourites as a reward after."),
        )
        r("SET_NEXT/NEXT_STEP",
            l("Set the road's first lesson as the next stone, to cross over from the playground."),
            l("Pin the first lesson of the big kids' road as the next stone."),
        )
        r("SET_NEXT/ROUTINE",
            l("Pin a lesson as the first stone for a day, with the toys as the treat afterwards.", "Pin a lesson as your first stone for a day, with the toys as the treat afterwards."),
            l("Set the path's next lesson as the first stone, so the toys come after it.", "Set the path's next lesson as your first stone, so the toys come after it."),
        )
        r("REPLAY_FOR_STARS",
            l("Replay {title} for more stars: a second go is usually quicker.", "Replay {title} for more stars: a second go is usually quicker."),
            l("Open {title} again to win the stars back. Second runs tend to go faster."),
            l("Give {title} another go for stars. The first run did the hard part."),
        )
        r("PRACTISE_TOGETHER/lesson",
            l("Sit in on {title}: a duet with you on the left hand gives the tricky spot a helper.", "Bring in a second player for {title}: a duet with someone on the left hand."),
            l("Play {title} together as a duet, with you on the left hand.", "Play {title} as a duet with someone on the left hand."),
        )
        r("PRACTISE_TOGETHER/song",
            l("Play {song} as a duet, with you on the left hand.", "Play {song} as a duet with someone on the left hand."),
            l("Pick {song} for a duet: the left hand is yours and the right hand is left to the player.", "Pick {song} for a duet: a second player takes the left hand."),
        )
        r("PRACTISE_TOGETHER/none",
            l("Sit down for one duet together this week: you pick the piece and take the left hand.", "Play one duet with a second player this week: they pick the piece and take the left hand."),
            l("A short duet together, you on the left hand, is an easy way to restart the routine.", "A short duet with a second player on the left hand is an easy way to restart the routine."),
        )
        r("FRESH_START",
            l("Restart {page} from its first lesson. It asks for two confirmations so nothing happens by accident."),
            l("Give {page} a fresh start: a clean run through often goes smoother. Two taps confirm it."),
        )
        r("PACE_FOCUS",
            l("Turn on focus mode for {page}: later pages stay hidden for now, so attention stays in one place."),
            l("Keep attention on {page} for a while with focus mode, which hides the pages after it."),
        )
        r("PACE_UNLOCK",
            l("Open the page after {page} early: the recent lessons were easy enough for a bigger step."),
            l("Unlock the next page ahead of schedule: {page} has been going smoothly."),
        )
        r("SETTING/reader",
            l("Switch to reader mode: letters replace the animals on the keys."),
            l("Turn on reader mode so the keys show letters instead of animals."),
        )
        r("SETTING/bedtime",
            l("Turn on bedtime: a soft goodnight once the daily goal is done."),
            l("Switch on the goodnight after the daily goal, so long days end on a calm note."),
        )
        r("SETTING/colourNotes",
            l("Turn on colour notes so the keys are easier to find.", "Turn on colour notes so the keys are easier to find."),
            l("Add colour to the notes: finding the right key gets quicker with it."),
        )
        r("SETTING/micEasier",
            l("Make the microphone a little easier to trigger, so softer playing registers."),
            l("Nudge the microphone up a step, to {db} dB, so lighter touches register."),
        )
        r("SETTING/quietRoom",
            l("Switch off the red key flashes on this phone: in a noisy room they confuse more than they help."),
            l("Stop painting slips on the keys for now. A noisy room makes the flashes unreliable."),
        )
        r("SETTING/off",
            l("Turn {setting} back off if it is not helping."),
            l("Switch {setting} off again: it can come back any time."),
        )
        r("TEACHER_ASSIGN",
            l("Pick a new song for the shelf and leave a note: {song} is solid and ready to be followed."),
            l("Add a new song to the shelf, with a note. {Song} has been played at three stars lately."),
            l("Time for fresh music: choose the next song for the shelf, now that {song} is done."),
        )
        r("TEACHER_ASSIGN/plain",
            l("Choose a new song for the shelf and add a note: the last one has settled."),
            l("Time for fresh music: pick the next song for the shelf."),
        )
    }
}

private fun settingName(id: String?) = when (SettingId.of(id)) {
    SettingId.READER -> "reader mode"
    SettingId.BEDTIME -> "bedtime"
    SettingId.COLOUR_NOTES -> "colour notes"
    SettingId.MIC_EASIER -> "the easier microphone"
    SettingId.QUIET_ROOM -> "quiet room mode"
    null -> null
}

private fun db(value: String?): String? = value?.toDoubleOrNull()?.let { d ->
    if (d == Math.rint(d)) d.toLong().toString() else String.format(java.util.Locale.ROOT, "%.1f", d)
}

internal fun recKey(cand: Candidate): String {
    val p = cand.params
    return when (cand.action) {
        ActionId.SET_NEXT -> when {
            p["lesson"] != null -> "SET_NEXT/lesson"
            cand.recommendation == RecommendationKind.NEXT_STEP -> "SET_NEXT/NEXT_STEP"
            cand.recommendation == RecommendationKind.ROUTINE -> "SET_NEXT/ROUTINE"
            else -> "SET_NEXT/MOVE_ON"
        }
        ActionId.REPLAY_FOR_STARS -> "REPLAY_FOR_STARS"
        ActionId.PRACTISE_TOGETHER -> when { p["lesson"] != null -> "PRACTISE_TOGETHER/lesson"; p["song"] != null -> "PRACTISE_TOGETHER/song"; else -> "PRACTISE_TOGETHER/none" }
        ActionId.FRESH_START -> "FRESH_START"
        ActionId.PACE_FOCUS -> "PACE_FOCUS"
        ActionId.PACE_UNLOCK -> "PACE_UNLOCK"
        ActionId.SETTING -> {
            val s = SettingId.of(p["setting"])
            if (s == null) "SETTING/off"
            else if (p["value"] == "false" && s != SettingId.MIC_EASIER) "SETTING/off" else "SETTING/${s.wire}"
        }
        ActionId.TEACHER_ASSIGN -> "TEACHER_ASSIGN"
    }
}

/** The tokens a recommendation may use. */
internal fun recVars(c: Ctx, cand: Candidate): Map<String, String?> {
    val p = cand.params
    return c.common() + mapOf(
        "title" to p["lesson"]?.let(c::quoted),
        "song" to (p["song"] ?: p["after"])?.let(c::quoted),
        "page" to p["page"]?.let(c::pageLower),
        "db" to db(p["value"]),
        "setting" to settingName(p["setting"]),
    )
}

/** The recommendation as one line of 160 characters or fewer. */
internal fun recText(c: Ctx, cand: Candidate, extra: Int = 0): String {
    val vars = recVars(c, cand)
    var key = recKey(cand)
    if (key == "TEACHER_ASSIGN" && vars["song"] == null) key = "TEACHER_ASSIGN/plain"
    val fits = RecBank.lines(key).filter { line ->
        val text = fill(if (c.kid) line.kid else line.adult, vars)
        text != null && text.length <= RecBank.MAX
    }
    val chosen = if (fits.isEmpty()) null else c.phrase("rec $key", fits, vars, extra)
    if (chosen != null) return chosen
    val f = RecBank.FALLBACK.getValue(cand.action)
    return tidy(if (c.kid) f.kid else f.adult)
}

/** A line saying what an action was, for "What you did". */
internal fun actionDone(c: Ctx, a: ActionTaken): String {
    val p = a.params
    val lesson = p["lesson"]?.let(c::quoted)
    val song = (p["song"] ?: p["after"])?.let(c::quoted)
    val page = p["page"]?.let(c::pageLower)
    return when (a.action) {
        ActionId.SET_NEXT -> if (lesson != null) "You set $lesson as the next stone." else "You set the next stone."
        ActionId.REPLAY_FOR_STARS -> if (lesson != null) "You reopened $lesson to win more stars." else "You reopened a lesson to win more stars."
        ActionId.PRACTISE_TOGETHER -> when {
            song != null -> "You played $song as a duet."
            lesson != null -> "You sat in on $lesson as a duet."
            else -> "You played a duet."
        }
        ActionId.FRESH_START -> if (page != null) "You restarted $page." else "You restarted a page."
        ActionId.PACE_FOCUS -> if (page != null) "You turned on focus mode for $page." else "You turned on focus mode."
        ActionId.PACE_UNLOCK -> if (page != null) "You opened the page after $page early." else "You opened the next page early."
        ActionId.SETTING -> settingName(p["setting"])?.let { n ->
            if (p["value"] == "false") "You turned $n off." else "You turned $n on."
        } ?: "You changed a setting."
        ActionId.TEACHER_ASSIGN -> if (song != null) "You put a new song on the shelf after $song." else "You put a new song on the shelf."
    }
}
