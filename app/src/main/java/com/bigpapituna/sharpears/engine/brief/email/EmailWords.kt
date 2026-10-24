package com.bigpapituna.sharpears.engine.brief.email

import com.bigpapituna.sharpears.engine.brief.ActionId
import com.bigpapituna.sharpears.engine.brief.Brief
import com.bigpapituna.sharpears.engine.brief.BriefTitles
import com.bigpapituna.sharpears.engine.brief.Candidate
import com.bigpapituna.sharpears.engine.brief.DayPart
import com.bigpapituna.sharpears.engine.brief.FactKind
import com.bigpapituna.sharpears.engine.brief.Findings
import com.bigpapituna.sharpears.engine.brief.FlagKind
import com.bigpapituna.sharpears.engine.brief.Praise
import com.bigpapituna.sharpears.engine.brief.PraiseKind
import com.bigpapituna.sharpears.engine.brief.SettingId
import com.bigpapituna.sharpears.engine.brief.Trend
import com.bigpapituna.sharpears.engine.brief.TrendKind
import com.bigpapituna.sharpears.engine.brief.WeeklyPlan
import com.bigpapituna.sharpears.engine.brief.WeeklySlot
import com.bigpapituna.sharpears.engine.family.Profile
import com.bigpapituna.sharpears.engine.spokenPitch
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The email's own words (the brief's words come from the writer chain): subjects, the preheader, why each suggestion
 * helps, the line to say at home, the welcome, the footer. One per email ([name] the child's first name, [period]).
 * Warm and plain, numbers present, the name instead of a pronoun, no em or en dash, no comparison with anyone, nothing
 * outside the app; EmailBriefTest checks every line of every email against those rules.
 */
class EmailWords(val name: String, val period: EmailPeriod, private val titles: BriefTitles) {
    private val week = period == EmailPeriod.WEEK
    private val span = if (week) "this week" else "today"
    private val spanNoun = if (week) "week" else "day"
    /** Where the trends' "before" came from. */
    private val earlier = if (week) "in earlier weeks" else "on recent days"

    // ---- the postcard ----

    fun chips(f: Findings): List<String> {
        val t = f.stats.totals
        return listOfNotNull(
            t.practiceMinutes.takeIf { it > 0 }?.let { count(it, "minute") },
            if (week) t.daysPractised.takeIf { it > 0 }?.let { "$it of 7 days" } else t.sessions.takeIf { it > 1 }?.let { count(it, "session") },
            t.newLessons.takeIf { it > 0 }?.let { count(it, "new lesson") },
            t.songRuns.takeIf { it > 0 }?.let { count(it, "song played", "songs played") },
            if (!week && t.goalDays > 0) "goal reached" else null,
            t.stickers.takeIf { it > 0 }?.let { count(it, "sticker") },
        ).take(4)
    }

    fun pawCaption(f: Findings): String? {
        if (f.stats.days.isEmpty()) return null
        val goal = f.goalMinutes?.takeIf { it > 0 }?.let { "; the daily goal is " + countWords(it, "minute") }.orEmpty()
        return "A paw is a day with practice$goal."
    }

    fun quiet(): String = "A quiet $spanNoun: nothing was played."

    // ---- in one minute ----

    /** One line on what got better, from the best good trend (skills first) or a good-news flag; null with neither. */
    fun better(f: Findings): String? {
        val t = BETTER_ORDER.firstNotNullOfOrNull { k -> f.trends.firstOrNull { it.kind == k && it.good == true } }
        if (t != null) return better(t)
        val g = f.flags.firstOrNull { it.kind in GOOD_FLAGS } ?: return null
        return when (g.kind) {
            FlagKind.CRUISING -> "New lessons are coming easily: nearly three stars each, with no hard moments."
            FlagKind.MASTERED_SONG -> (g.songs.firstOrNull()?.let(titles::song)?.takeIf { it.isNotBlank() } ?: "A song") + " came out at three stars again and again."
            FlagKind.READY_FOR_LETTERS -> "The letter lessons have gone well for two weeks."
            FlagKind.READY_FOR_ROAD -> "The whole playground is done."
            else -> null
        }
    }

    private fun better(t: Trend): String {
        fun n(x: Double) = x.roundToInt().toString()
        fun one(x: Double) = String.format(Locale.ROOT, "%.1f", x)
        val from = "from about"
        return when (t.kind) {
            TrendKind.WRONG_PER_RUN -> "Slips per run fell to ${one(t.now)}, $from ${one(t.before)} $earlier."
            TrendKind.NEAR_SHARE -> "More slips land on the key next door: ${n(t.now)}% of them, $from ${n(t.before)}% $earlier."
            TrendKind.ACCURACY -> "Perform accuracy rose to ${n(t.now)}%, $from ${n(t.before)}% $earlier."
            TrendKind.STARS -> "Lessons are finishing with more stars: ${one(t.now)} on average, $from ${one(t.before)} $earlier."
            TrendKind.TIMING_MS -> "Notes land closer to the beat: ${n(t.now)} ms off, $from ${n(t.before)} ms $earlier."
            TrendKind.TEMPO_SPREAD -> "The beat is steadier: about ${n(t.now)} ms of wobble, $from ${n(t.before)} ms $earlier."
            TrendKind.HESITATION_MS -> "Keys come quicker: ${seconds(t.now)} to find each, $from ${seconds(t.before)} $earlier."
            TrendKind.LEFT_HAND -> "The left hand lands ${n(t.now)}% of its notes, $from ${n(t.before)}% $earlier."
            TrendKind.RIGHT_HAND -> "The right hand lands ${n(t.now)}% of its notes, $from ${n(t.before)}% $earlier."
            TrendKind.EAR -> "Ear games: ${n(t.now)}% right, $from ${n(t.before)}% $earlier."
            TrendKind.CLAP -> "Claps on the beat: ${n(t.now)}% of the drum's beats, $from ${n(t.before)}% $earlier."
            TrendKind.PLAY_HINTS -> "The app had to play the key less often: ${n(t.now)}% of stuck moments, $from ${n(t.before)}% $earlier."
            TrendKind.DAYS -> count(t.now.roundToInt(), "day") + " at the piano, $from ${n(t.before)} $earlier."
            TrendKind.MINUTES -> count(t.now.roundToInt(), "minute") + " of practice, $from ${n(t.before)} $earlier."
            TrendKind.SESSIONS -> count(t.now.roundToInt(), "session") + ", $from ${n(t.before)} $earlier."
        }
    }

    /** The line for a grown-up to say at home, for the top praise (or a warm general one). */
    fun sayThis(p: Praise?): String {
        val lesson = p?.lessons?.firstOrNull()?.let(titles::lesson)?.takeIf { it.isNotBlank() }
        val song = p?.songs?.firstOrNull()?.let(titles::song)?.takeIf { it.isNotBlank() }
        val page = p?.page?.let(titles::page)?.takeIf { it.isNotBlank() }?.let(com.bigpapituna.sharpears.engine.kid.KidPages::inSentence)
        val general = "I love hearing you play. What was your favourite bit $span?"
        return when (p?.kind) {
            null -> general
            PraiseKind.FINALE -> if (page != null) "You played the whole finale of $page! Will you play it for me?" else "You played a whole finale! Will you play it for me?"
            PraiseKind.FIRST_CHORD -> "Your first chord! Can you show me how you play the keys together?"
            PraiseKind.STUCK_WITH_IT -> if (lesson != null) "I noticed you kept going on $lesson. That's sticking power." else "I noticed you kept going when it was tricky. That's sticking power."
            PraiseKind.SHINY_STICKER -> "A shiny sticker! Will you show me your album?"
            PraiseKind.RARE_STICKER -> "A rare sticker! Will you show me your album?"
            PraiseKind.GOAL_STREAK -> "${words(p.value.toInt()).replaceFirstChar { it.uppercase() }} days in a row! You keep coming back to the piano."
            PraiseKind.THREE_STARS -> if (lesson != null) "Three stars on $lesson! Will you play it for me?" else "Three stars! Will you play it for me?"
            PraiseKind.TIMING_IMPROVED -> "Your playing is right on the beat now. I can hear it."
            PraiseKind.STEADIER_TEMPO -> "Your beat is so steady now. I could march to it."
            PraiseKind.ACCURACY_IMPROVED -> "So many notes just right! Your practice is working."
            PraiseKind.MISSES_CLOSER -> "Your fingers are finding the right keys. You're really learning the piano."
            PraiseKind.FINDS_KEYS_FASTER -> "You find the keys so quickly now!"
            PraiseKind.EAR_IMPROVED -> "Your ears are getting so sharp!"
            PraiseKind.LEFT_HAND_IMPROVED -> "Your left hand is getting strong!"
            PraiseKind.CONSISTENT -> "You played on so many days. I love that you keep coming back."
            PraiseKind.CLEAN_SONG -> if (song != null) "$song sounded lovely. Will you play it for me again?" else "That song sounded lovely. Will you play it for me again?"
            PraiseKind.NEW_LESSONS -> if (lesson != null) "You learned $lesson! Can you show me what it is?" else "You learned something new! Can you show me?"
            PraiseKind.QUEST_DAY -> "You got all of the pup's quest stars!"
            PraiseKind.CAME_BACK -> "I'm so glad you came back to the piano. What shall we play?"
        }
    }

    // ---- what I'd do next ----

    fun nextIntro(): String = "Nothing in this email can change the app: each one is done in the app, in the grown-ups corner."

    /** Where an action is in the app: "Grown-ups corner > Family > Mia, then Set as next". */
    fun where(action: String): String = "Grown-ups corner > Family > $name, then $action"

    /** [whyFor] for one of the brief's recommendations. */
    fun whyFor(recommendation: Brief.Recommendation, f: Findings): String = whyFor(recommendation.candidate, f)

    /**
     * Why a suggestion helps, in one sentence: the reason of the flag it answers (CONTRACT.md's "why it is normal" turned
     * toward the action), or, for one that answers no flag, the action's own reason.
     */
    fun whyFor(c: Candidate, f: Findings): String {
        val p = c.params
        val lesson = p["lesson"]?.let(titles::lesson)?.takeIf { it.isNotBlank() }
        val song = (p["song"] ?: p["after"])?.let(titles::song)?.takeIf { it.isNotBlank() }
        val what = lesson ?: song
        val setting = SettingId.of(p["setting"])
        return when (c.because) {
            FlagKind.STUCK_LESSON -> if (c.action == ActionId.SET_NEXT) "${what ?: "That lesson"} has not settled yet, and a fresh day often does what another try today will not."
                else "A second pair of hands keeps the beat while $name finds the keys on the hard step."
            FlagKind.STUCK_SONG -> "Songs come together in pieces, and playing ${what ?: "it"} together lets $name hear how the pieces fit."
            FlagKind.NOISY_ROOM -> if (setting == SettingId.MIC_EASIER) "Soft playing can slip under the microphone, and a little easier lets the quiet notes count."
                else "The microphone hears the whole room, so the keys stop lighting up for sounds that were not the piano."
            FlagKind.HINT_DEPENDENCE -> "The app had to play the key several times; sitting beside $name, you can point the way before it has to."
            FlagKind.PAGE_STRUGGLE -> "Several lessons on this page were hard, and starting it fresh turns them into new wins."
            FlagKind.READY_FOR_LETTERS -> "$name has played the letter lessons well for two weeks, so letters on the keys are the next step."
            FlagKind.READY_FOR_ROAD -> "The playground is finished, and the big kids' road starts where it ends."
            FlagKind.CRUISING -> "New lessons came in at nearly three stars with no hard moments, so the next page is a fair challenge."
            FlagKind.REPLAYING_NOT_MOVING_ON -> "Favourites build confidence, and one new lesson first keeps the path moving with the favourites as the treat."
            FlagKind.LOW_STARS -> "A first run is often rough, and a second go usually wins the stars back quickly."
            FlagKind.LEFT_HAND_BEHIND -> "The left hand is nearly always the second hand, and with you playing its part $name hears how it should go."
            FlagKind.LEFT_HAND_LAGS -> "The left hand finds its keys more slowly, and hearing you play its part shows $name where they are."
            FlagKind.FAR_MISSES -> "Most slips landed far from the key wanted, and colours make the right key easier to find."
            FlagKind.MASTERED_SONG -> "${what ?: "A song"} has come out at three stars again and again, so a new one keeps the challenge fresh."
            FlagKind.SCATTERED -> "Lessons came from many places with few stars, and staying on one page lets the stars build."
            FlagKind.TOY_HEAVY -> "Play is music too, and a lesson first with the toys after keeps both."
            FlagKind.LONG_DAYS -> "Long days are enthusiasm, and a goodnight after the daily goal ends the day on a high."
            FlagKind.PRACTICE_DIPPED -> "A lighter $spanNoun is normal, and sitting down together is the easiest way back into the routine."
            FlagKind.HESITATES_ON -> {
                val key = f.flags.firstOrNull { it.kind == FlagKind.HESITATES_ON }?.let { keyName(it.value.toInt()) } ?: "One key"
                if (c.action == ActionId.SETTING) "$key still takes a moment to find, and colours make it easier to spot."
                else "$key keeps a pause in front of it, and sitting beside $name you can point to it until the hand knows the way."
            }
            null -> when (c.action) {
                ActionId.SET_NEXT -> "Starting the day with ${what ?: "the next lesson"} gives it fresh hands and full attention."
                ActionId.REPLAY_FOR_STARS -> "A second go usually wins stars back quickly."
                ActionId.PRACTISE_TOGETHER -> "Playing together is the quickest way to hear how a piece should go."
                ActionId.FRESH_START -> "A fresh start turns lessons that were hard into new wins."
                ActionId.PACE_FOCUS -> "Staying on one page for now lets the stars build."
                ActionId.PACE_UNLOCK -> "The last lessons came easily, so the next page is ready."
                ActionId.TEACHER_ASSIGN -> "A new song on the shelf keeps the music fresh."
                ActionId.SETTING -> when (setting) {
                    SettingId.READER -> "Letters on the keys match the letters $name has been reading."
                    SettingId.BEDTIME -> "A goodnight after the daily goal ends the day on a high."
                    SettingId.COLOUR_NOTES -> "Colours make the right key easier to find."
                    SettingId.MIC_EASIER -> "A little easier lets soft playing count."
                    SettingId.QUIET_ROOM -> "The keys stop lighting up for sounds that were not the piano."
                    null -> "It suits how the piano is going."
                }
            }
        }
    }

    /** At most one thing to keep doing (nothing to tap), for when there are fewer than three suggestions. */
    fun keepDoing(f: Findings, nothingElse: Boolean): Pair<String, String>? {
        val tail = " Nothing to tap, just keep doing it."
        f.facts.firstOrNull { it.kind.name.startsWith("MOSTLY_") }?.let { fact ->
            val part = PART_WORDS.getValue(DayPart.valueOf(fact.kind.name.removePrefix("MOSTLY_")))
            val share = fact.of?.takeIf { it > 0 }?.let { (fact.value * 100 / it).toInt() }
            return "Keep the $part slot." to ((if (share != null) "$share% of the $spanNoun's minutes were in the $part, and it is working." else "Most of the playing was in the $part, and it is working.") + tail)
        }
        f.praise.firstOrNull { it.kind == PraiseKind.GOAL_STREAK }?.let {
            return "Keep the streak going." to "The daily goal was reached ${words(it.value.toInt())} days in a row.$tail"
        }
        f.praise.firstOrNull { it.kind == PraiseKind.CONSISTENT }?.let {
            return "Keep the routine going." to "$name played on ${words(it.value.toInt())} of the last seven days.$tail"
        }
        return if (nothingElse) "Keep going as you are." to "Nothing stood out to change $span.$tail" else null
    }

    // ---- subject, preheader ----

    /** The subject variants that fit, warmest first: the brief's headline, then lines with the name. Never empty. */
    fun subjects(headline: String, first: Boolean, code: String?): List<String> {
        val h = headline.trim().trimEnd('.', '!', ' ')
        val named = h.contains(name, ignoreCase = true)
        val opener = if (h.startsWith(name)) null else h.replaceFirstChar { it.lowercase() }
        val all = when {
            first -> listOf("$name's piano brief: your code is $code", "Your code for $name's piano brief is $code", "Your code is $code")
            week -> listOfNotNull(h.takeIf { named }, opener?.let { "$name's week: $it" }, "$name's week at the piano, from the pup",
                "A postcard from the pup: $name's week")
            else -> listOfNotNull(h.takeIf { named }, opener?.let { "$name today: $it" }, "$name's day at the piano, from the pup",
                "Today at the piano with $name")
        }
        return all.filter { it.isNotBlank() && it.length <= SUBJECT_MAX && clean(it) }.distinct()
    }

    fun preheader(f: Findings, suggestions: Int, first: Boolean): String {
        if (first) return "Type the code in the app within 24 hours, then choose how often these come."
        if (f.isEmpty) return if (week) "A quiet week at the piano. The brief picks up when the music does." else "A quiet day at the piano."
        val t = f.stats.totals
        val things = when (suggestions) { 0 -> "a gold star"; 1 -> "one thing to try and a gold star"; else -> "${words(suggestions)} things to try and a gold star" }
        val line = if (week) "In one minute: ${count(t.practiceMinutes, "minute")} on ${count(t.daysPractised, "day")}, $things."
            else "Today in one minute: ${count(t.practiceMinutes, "minute")}, $things."
        return line.take(PREHEADER_MAX)
    }

    // ---- the first email ----

    fun welcome(code: String, weekly: WeeklySlot, empty: Boolean): Welcome {
        val place = SETTINGS_PLACE
        return Welcome(
            title = "Welcome to $name's piano brief",
            paragraphs = listOfNotNull(
                "From now on the pup can send you a postcard about $name's piano: what happened, what to try next, and one thing to praise at home.",
                "It is written from the numbers the app keeps on $name's phone. No recordings or sounds ever leave it.",
                "Nothing in these emails can change the app. Each suggestion says where to find it in the app, in the grown-ups corner.",
                if (empty) "$name has not played yet, so this first one is short. The next ones bring the brief." else null,
            ),
            code = code,
            codeLine = "To confirm this is your address, type the code in the app: $place. It works for 24 hours; three wrong tries and the app asks for a new one.",
            howOften = HowOften(
                title = "Choose how often",
                where = "In the same place, once the code is in, each child has a Daily and a Weekly switch:",
                choices = listOf(
                    FrequencyChoice(EmailFrequency.DAILY, "Daily", "each day $name plays, once the playing is done (${WeeklyPlan.timeLabel(CUTOFF_MIN)} at the latest)."),
                    FrequencyChoice(EmailFrequency.WEEKLY, "Weekly", "once a week, on ${WeeklyPlan.label(weekly)}."),
                    FrequencyChoice(EmailFrequency.BOTH, "Both", "both switches on, for the daily ones and the weekly one."),
                    FrequencyChoice(EmailFrequency.OFF, "Off", "both switches off, so no emails; the brief stays in the app."),
                ),
                note = "Nothing is sent between ${WeeklyPlan.timeLabel(QUIET_FROM_MIN)} and ${WeeklyPlan.timeLabel(QUIET_TO_MIN)}, and never more than two a day.",
            ),
        )
    }

    // ---- the footer ----

    fun footer(by: Brief.Writer, frequency: EmailFrequency, weekly: WeeklySlot, first: Boolean, phoneName: String?, attached: Boolean = true): List<String> = listOfNotNull(
        "Sent by Sharp Ears for $name" + (phoneName?.trim()?.takeIf { it.isNotEmpty() }?.let { ", from $it" } ?: "") + ".",
        (if (by == Brief.Writer.AI) "Written by AI" else "Written on the phone") + " from the $spanNoun's numbers. No audio or recordings leave the phone.",
        if (first) null else when (frequency) {
            EmailFrequency.DAILY -> "This comes each day $name plays."
            EmailFrequency.WEEKLY -> "This comes once a week, on ${WeeklyPlan.label(weekly)}."
            EmailFrequency.BOTH -> "A day's email comes each day $name plays, and the week's on ${WeeklyPlan.label(weekly)}."
            EmailFrequency.OFF -> "These emails are off."
        },
        "To change how often these come, or stop them: $SETTINGS_PLACE, in the app.",
        if (attached) "The full report is attached as a PDF, for mail apps that cannot open the sections." else null,
    )

    companion object {
        /** A subject longer than this is cut off by most phones' mail apps. */
        const val SUBJECT_MAX = 78
        const val PREHEADER_MAX = 110
        /** Where the brief by email is set (2.8: one place for the family's address and each child's switches). */
        const val SETTINGS_PLACE = "Settings > Briefs and email"
        /** What a suggestion's button says: nothing in the email can act. */
        const val DO_IN_APP = "Do this in the app"
        /** The flags that are good news. */
        val GOOD_FLAGS = setOf(FlagKind.READY_FOR_LETTERS, FlagKind.READY_FOR_ROAD, FlagKind.CRUISING, FlagKind.MASTERED_SONG)
        /** Skills first: "getting better" is about playing before it is about time spent. */
        private val BETTER_ORDER = listOf(
            TrendKind.WRONG_PER_RUN, TrendKind.NEAR_SHARE, TrendKind.ACCURACY, TrendKind.STARS, TrendKind.TIMING_MS, TrendKind.TEMPO_SPREAD,
            TrendKind.HESITATION_MS, TrendKind.LEFT_HAND, TrendKind.RIGHT_HAND, TrendKind.EAR, TrendKind.CLAP, TrendKind.PLAY_HINTS,
            TrendKind.DAYS, TrendKind.MINUTES, TrendKind.SESSIONS,
        )
        private val PART_WORDS = mapOf(DayPart.MORNING to "morning", DayPart.AFTERNOON to "afternoon", DayPart.EVENING to "evening", DayPart.NIGHT to "late evening")
        private const val CUTOFF_MIN = 20 * 60 + 30
        private const val QUIET_FROM_MIN = 21 * 60 + 30
        private const val QUIET_TO_MIN = 7 * 60
        private val WORDS = listOf("zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten")
        private val DASHES = Regex("[" + Char(0x2014) + Char(0x2013) + "]| - ")

        /** The first name as the words use it (trimmed, single spaces, at most 24 characters); "your child" with none. */
        fun name(raw: String): String = raw.trim().replace(Regex("\\s+"), " ").take(24).trim().ifEmpty { "your child" }

        fun avatarColour(avatar: Int): String = Profile.AVATAR_NAMES.getOrElse(avatar) { "" }

        /** "WED 21 OCT". */
        fun dayStamp(d: LocalDate): String =
            "${d.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)} ${d.dayOfMonth} ${d.month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)}".uppercase(Locale.ROOT)

        /** True when [s] has no em or en dash, no spaced hyphen and no braces (a placeholder left unfilled). */
        fun clean(s: String): Boolean = !DASHES.containsMatchIn(s) && '{' !in s && '}' !in s

        /** A key by its letter as the voice says it: "G", "F sharp", "B flat". */
        fun keyName(midi: Int): String = spokenPitch(Math.floorMod(midi, 12))

        /** Ten and under as words, the rest as digits. */
        fun words(n: Int): String = if (n in 0..10) WORDS[n] else n.toString()

        /** "one minute", "five minutes", "12 minutes": [words] and the noun, singular for one. */
        fun countWords(n: Int, one: String, many: String = one + "s"): String = words(n) + " " + if (abs(n) == 1) one else many

        /** "1 minute", "78 minutes" (digits: chips and lines with numbers). */
        fun count(n: Int, one: String, many: String = one + "s"): String = "$n " + if (abs(n) == 1) one else many

        /** A wait in seconds with one decimal: "1.1 seconds", "0.8 seconds". */
        fun seconds(ms: Double): String = String.format(Locale.ROOT, "%.1f seconds", ms / 1000)
    }
}
