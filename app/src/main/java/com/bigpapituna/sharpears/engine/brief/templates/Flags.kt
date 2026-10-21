package com.bigpapituna.sharpears.engine.brief.templates

import com.bigpapituna.sharpears.engine.brief.Flag
import com.bigpapituna.sharpears.engine.brief.FlagKind
import java.util.Locale

/*
 * The flag lines. Each flag kind has an observation (at least three phrasings, some that need no title) and a "why this
 * is normal" sentence in the writer's own words (at least three). For the good-news flags (ready for letters, the road,
 * cruising, a mastered song) the second sentence says what it means instead.
 */
internal object FlagBank {
    private val obs = HashMap<FlagKind, List<L>>()
    private val why = HashMap<FlagKind, List<L>>()

    fun observations(k: FlagKind): List<L> = obs.getValue(k)
    fun reasons(k: FlagKind): List<L> = why.getValue(k)

    fun sizes(): Map<String, Int> = obs.entries.associate { "flag ${it.key}/observation" to it.value.size } +
        why.entries.associate { "flag ${it.key}/why normal" to it.value.size }

    /** The flags that are good news, not something hard. */
    val GOOD = setOf(FlagKind.READY_FOR_LETTERS, FlagKind.READY_FOR_ROAD, FlagKind.CRUISING, FlagKind.MASTERED_SONG)

    private fun o(k: FlagKind, vararg ls: L) { obs[k] = ls.toList() }
    private fun w(k: FlagKind, vararg ls: L) { why[k] = ls.toList() }

    init {
        o(FlagKind.STUCK_LESSON,
            l("{Title} is still tricky: one step took {tries}."),
            l("{Title} took {tries} on its hardest step, and the app let {steps} go."),
            l("A step in {title} needed {tries} and is not settled yet."),
            l("{Title} has been slow to settle, with {tries} on the hardest step."),
            l("The app moved past {steps} in {title} after a few tries."),
            l("A lesson {span} is still tricky: its hardest step took {tries}."),
            l("One step took {tries} {span} and is not settled yet."),
        )
        w(FlagKind.STUCK_LESSON,
            l("New skills take a few days to settle, and the app lets a step go on purpose so nobody stays stuck."),
            l("A step that will not click today often does tomorrow. The app moves on after a few tries and the lesson stays open."),
            l("Skills often settle overnight. Letting a step go after a few tries keeps a hard spot from blocking the path."),
            l("Letting a step go is part of the design: the lesson is still there to come back to, and a fresh day does a lot."),
        )
        o(FlagKind.STUCK_SONG,
            l("{Song} has had {runs} without reaching two stars."),
            l("{Runs} at {song} so far, all under two stars."),
            l("Three or more tries at {song} are still short of two stars."),
            l("One song has had {runs} without reaching two stars."),
            l("A song {span} has had {runs} and is still under two stars."),
        )
        w(FlagKind.STUCK_SONG,
            l("Songs come in pieces: the opening bars settle first and the rest follows."),
            l("Most songs click one phrase at a time, and a few runs give the ear a map of the whole piece."),
            l("A full run is a big ask. Songs usually come together in short pieces first."),
            l("Under two stars after a few runs is an ordinary place to be with a new song."),
        )
        o(FlagKind.NOISY_ROOM,
            l("The microphone heard the room {times} {span}."),
            l("Background noise got in the way {times} {span}."),
            l("The room got loud enough to confuse the microphone {times} {span}."),
            l("Room noise interrupted {times} {span}, across {sessions}."),
        )
        w(FlagKind.NOISY_ROOM,
            l("The microphone hears the whole room, so a TV, a tap or a sibling can look like a note.",
                "The microphone hears the whole room, so a TV, a tap or a conversation can look like a note."),
            l("The microphone listens to everything in the room, not only the piano."),
            l("Phones cannot tell a piano from a kitchen: the microphone takes in whatever is in the room."),
            l("A noisy room is the microphone's hardest case, and most homes have a few."),
        )
        o(FlagKind.HINT_DEPENDENCE,
            l("The app played the key itself {times} {span}, most often in {where}."),
            l("Hints went as far as the app playing the key {times} {span}."),
            l("Of {stuck} moments with a hint, {play} ended with the app playing the key."),
            l("The third hint, the app playing the key, came up {times} {span}."),
        )
        w(FlagKind.HINT_DEPENDENCE,
            l("Hints are the app's way of teaching: first a glow, then a pointing hand, then the sound of the key. Needing the last one means the ear is still learning that spot."),
            l("The last hint plays the key so the ear hears what the hand is looking for. It is a teaching step, not a verdict."),
            l("Hints exist for exactly these spots, and hearing the key is how an ear learns where a hand should go."),
        )
        o(FlagKind.PAGE_STRUGGLE,
            l("{Page} is a big step: {n} of its lessons are stuck or at one star."),
            l("{N} lessons in {page} are stuck or at one star: {titles}."),
            l("A cluster of tricky lessons sits on {page}: {titles}."),
            l("One page of the playground has {n} lessons stuck or at one star."),
            l("Several lessons on one page are still stuck or at one star."),
        )
        w(FlagKind.PAGE_STRUGGLE,
            l("A page of the playground packs in several new ideas at once."),
            l("Each page asks for something new, and the middle lessons are where it stacks up."),
            l("Pages get bigger as they go, and a slow patch on one page is common."),
        )
        o(FlagKind.READY_FOR_LETTERS,
            l("The letter lessons are going well: {titles} averaged {stars} stars."),
            l("{Samples} letter-lesson plays averaged {stars} stars over the last two weeks."),
            l("{Titles} suggest the letters are sticking."),
            l("Letter lessons have gone well for two weeks, at {stars} stars on average."),
        )
        w(FlagKind.READY_FOR_LETTERS,
            l("Reading letters on the keys is the next step after the animals, and these results say it has already started."),
            l("Results like these mean the letters are doing their job, so the animals can step back."),
            l("Reader mode swaps the animals for letters. It makes sense once the letters are familiar, and they are."),
        )
        o(FlagKind.READY_FOR_ROAD,
            l("The playground is finished: the fair is done."),
            l("The last page of the playground, the fair, is complete."),
            l("The fair is finished, which is the end of the playground."),
        )
        w(FlagKind.READY_FOR_ROAD,
            l("The big kids' road is the path after the playground, with a steady step up."),
            l("From here the lessons move to the big kids' road."),
            l("The road follows the playground and builds on everything it taught."),
        )
        o(FlagKind.CRUISING,
            l("{Titles} all came easily, with close to three stars and no steps let go."),
            l("A smooth stretch: {n} new lessons at close to three stars, with no hints needed."),
            l("{N} new lessons at nearly three stars, none needing the app to play the key."),
            l("Lessons {span} came easily: {n} new ones at close to three stars."),
        )
        w(FlagKind.CRUISING,
            l("Smooth runs like these mean the current pace has room to grow."),
            l("When lessons come this easily, a bigger step is usually welcome."),
            l("Easy wins are a sign the ground underneath is firm."),
        )
        o(FlagKind.REPLAYING_NOT_MOVING_ON,
            l("{Replays} of lessons already finished, with nothing new started: {titles}."),
            l("{Titles} got replayed {times} {span}, and no new lesson began."),
            l("The same favourites again: {titles}."),
            l("{Replays} {span}, and no new lesson started."),
            l("Lessons already finished came out again {times} {span}, with nothing new begun."),
        )
        w(FlagKind.REPLAYING_NOT_MOVING_ON,
            l("Replaying favourites builds confidence, and it often means the music feels good."),
            l("Going back to a lesson already finished is a normal way to get comfortable before a step up."),
            l("People replay what feels good, and the repeats build the base the next lesson stands on."),
        )
        o(FlagKind.LOW_STARS,
            l("{Titles} finished at one star on the first go."),
            l("First finishes at one star: {titles}."),
            l("{Lessons} finished at one star on the first go."),
            l("Some first finishes {span} came in at one star."),
        )
        w(FlagKind.LOW_STARS,
            l("First runs are often rough, and the stars are quick to win back on a second go."),
            l("One star on a first finish is a starting point, and the second run is usually much smoother."),
            l("A lesson finished is a lesson learned well enough to move on. Stars come with repeats."),
        )
        o(FlagKind.LEFT_HAND_BEHIND,
            l("The left hand hit {left} of its notes against {right} for the right."),
            l("Left hand {left}, right hand {right}: a gap of {gap}."),
            l("The hands are uneven {span}: left {left}, right {right}."),
        )
        w(FlagKind.LEFT_HAND_BEHIND,
            l("The left hand is nearly always the second hand to catch up, and it improves quickly with use."),
            l("Left hands start with less practice and less feel, so a gap is normal and it closes with playing."),
            l("Most players favour one hand early on, and the other needs time to match."),
        )
        o(FlagKind.FAR_MISSES,
            l("{Far} of {slips} were far misses, keys well away from the one wanted."),
            l("Most slips, {far} of {total}, landed far from the target key."),
            l("Far misses made up {far} of {slips} {span}."),
        )
        w(FlagKind.FAR_MISSES,
            l("Finding keys comes before playing them. A far miss means the hand is still learning where the keys are."),
            l("A slip far from the target is a search, not a clumsy finger, and colour notes make the search faster."),
            l("Keys look alike at first, and finding them by sight takes time."),
        )
        o(FlagKind.LONG_DAYS,
            l("{Days} went past {line} at the piano {span}."),
            l("{Days} ran long, over {line} on screen."),
            l("Long days at the piano: {days} over {line}."),
        )
        w(FlagKind.LONG_DAYS,
            l("Long stretches are enthusiasm, and a gentle goodnight after the daily goal helps some families end on a high."),
            l("A long day at the piano usually means the music is fun, and a soft stop can keep it that way."),
            l("Enthusiasm like this is welcome, and a soft goodnight keeps it comfortable for some families."),
        )
        o(FlagKind.MASTERED_SONG,
            l("{Songs} came out at three stars three times or more."),
            l("{Song} is solid: three stars, again and again."),
            l("A song {span} reached three stars three times or more."),
            l("One song came out clean, at three stars, again and again {span}."),
        )
        w(FlagKind.MASTERED_SONG,
            l("A song that comes out clean again and again has settled, and fresh music keeps the interest going."),
            l("Three clean runs mean the piece has settled and new music is ready to start."),
            l("Clean repeats are the sign a piece has taught what it had to."),
        )
        o(FlagKind.SCATTERED,
            l("Lessons from {levels} different levels or pages {span}, with few stars so far."),
            l("{Levels} different parts of the course got a visit {span}, and the stars are low."),
            l("The lessons jumped around: {levels} places {span}, mostly under two stars."),
        )
        w(FlagKind.SCATTERED,
            l("Sampling is natural: people try a few places before one feels like home."),
            l("Jumping around is what curiosity looks like, and stars tend to come once one path holds the attention."),
            l("Early on, trying lots of things helps people find what they like."),
        )
        o(FlagKind.TOY_HEAVY,
            l("Most of the time went to the toys {where}."),
            l("The toys took over {where}: Jam, Pup says and Clap along."),
            l("Free play in the toys filled most of the time {where}."),
            l("More toy time than lesson time {where}."),
        )
        w(FlagKind.TOY_HEAVY,
            l("Play is music too: the toys train ear, rhythm and nerve."),
            l("Free play builds the same things lessons do: ear, rhythm and the nerve to try."),
            l("Jamming and clapping is real musical practice, and it keeps the piano fun."),
        )
        o(FlagKind.PRACTICE_DIPPED,
            l("Fewer days than usual: {days} against about {usualn}."),
            l("A lighter week: {days} at the piano, where the usual is about {usualn}."),
            l("The piano came out on {days} this week, below the usual {usualn}."),
            l("Practice dipped to {days}, from a usual {usualn}."),
        )
        w(FlagKind.PRACTICE_DIPPED,
            l("Weeks vary. School, travel and tired evenings all show up in the piano's numbers.",
                "Weeks vary. Work, travel and tired evenings all show up in the piano's numbers."),
            l("A lighter week is common, and a routine usually comes back once the schedule settles."),
            l("Practice rises and falls with the calendar, and one quiet week says little about the next."),
        )
        o(FlagKind.HESITATES_ON,
            l("{Key} is the key that takes longest to find, about {wait} each time."),
            l("One key keeps a pause in front of it: {key}, at about {wait}."),
            l("{Key} still takes a moment to find in {where}, about {wait}."),
            l("A pause comes before {key} most times it is due, about {wait}."),
        )
        w(FlagKind.HESITATES_ON,
            l("A key the hand still looks for is a key it is learning, and the pause shrinks a little with every find."),
            l("Finding a key comes before playing it in time, and the search gets shorter with use."),
            l("Most players have a key or two that hide at first, and they stop hiding after a few days of finding them."),
        )
        o(FlagKind.LEFT_HAND_LAGS,
            l("The left hand takes longer to find its keys: about {left} against {right} for the right."),
            l("Left-hand notes wait a little longer for their keys, {left} against {right}."),
            l("The right hand finds its keys in {right}, the left in {left}."),
        )
        w(FlagKind.LEFT_HAND_LAGS,
            l("The left hand is nearly always the second hand, and it learns where the keys are a little later."),
            l("Most players find keys faster with one hand at first, and the other catches up with use."),
            l("A slower left hand is the usual order of things: it gets less practice until both hands play together."),
        )
    }
}

/** The tokens a flag's lines may use. */
internal fun flagVars(c: Ctx, f: Flag): Map<String, String?> {
    val v = f.value
    val of = f.of
    val lessonTitles = c.titles(f.lessons)
    val songTitles = c.titles(f.songs)
    val where = c.titles(f.lessons + f.songs)
    val sessionsWhere = if ((of ?: 0) > 1) "in ${words(v)} of ${words(of ?: 0)} sessions" else "in this session"
    val common = c.common() + mapOf(
        "title" to f.lessons.firstOrNull()?.let(c::quoted), "titles" to lessonTitles,
        "song" to f.songs.firstOrNull()?.let(c::quoted), "songs" to songTitles,
        "where" to where, "page" to f.page?.let(c::pageLower),
        "times" to timesWord(v),
    )
    val kind: Map<String, String?> = when (f.kind) {
        FlagKind.STUCK_LESSON -> mapOf("tries" to count(v, "try", "tries"), "steps" to of?.takeIf { it > 0 }?.let { count(it, "step") })
        FlagKind.STUCK_SONG -> mapOf("runs" to count(v, "run"))
        FlagKind.NOISY_ROOM -> mapOf("sessions" to of?.takeIf { it > 0 }?.let { count(it, "session") })
        FlagKind.HINT_DEPENDENCE -> mapOf("stuck" to of?.let(::words), "play" to words(v), "where" to where)
        FlagKind.PAGE_STRUGGLE -> mapOf("n" to words(v))
        FlagKind.READY_FOR_LETTERS -> mapOf("stars" to String.format(Locale.ROOT, "%.1f", v / 10.0), "samples" to of?.let(::words))
        FlagKind.READY_FOR_ROAD -> emptyMap()
        FlagKind.CRUISING -> mapOf("n" to words(v))
        FlagKind.REPLAYING_NOT_MOVING_ON -> mapOf("replays" to count(v, "replay"))
        FlagKind.LOW_STARS -> mapOf("lessons" to count(v, "lesson"))
        FlagKind.LEFT_HAND_BEHIND -> mapOf("left" to percent(v), "right" to of?.let(::percent),
            "gap" to of?.let { (it - v).takeIf { g -> g > 0 }?.let { g -> count(g, "point") } })
        FlagKind.FAR_MISSES -> mapOf("far" to words(v), "slips" to of?.let { count(it, "slip") }, "total" to of?.let(::words))
        FlagKind.LONG_DAYS -> mapOf("days" to count(v, "day"), "line" to of?.let { count(it, "minute") })
        FlagKind.MASTERED_SONG -> emptyMap()
        FlagKind.SCATTERED -> mapOf("levels" to words(v))
        FlagKind.TOY_HEAVY -> mapOf("where" to sessionsWhere)
        FlagKind.PRACTICE_DIPPED -> mapOf("days" to count(v, "day"), "usualn" to of?.let { count(it, "day") })
        FlagKind.HESITATES_ON -> mapOf("key" to keyName(v), "wait" to of?.let(::seconds))
        FlagKind.LEFT_HAND_LAGS -> mapOf("left" to seconds(v), "right" to of?.let(::seconds))
    }
    return common + kind
}

/** What the flag says, as one sentence ([extra] varies a second use). */
internal fun flagObservation(c: Ctx, f: Flag, extra: Int = 0): String? =
    c.phrase("flag ${f.kind} obs", FlagBank.observations(f.kind), flagVars(c, f), extra)

/** Why it is normal (or what it means, for the good-news flags). */
internal fun flagWhy(c: Ctx, f: Flag, extra: Int = 0): String? =
    c.phrase("flag ${f.kind} why", FlagBank.reasons(f.kind), flagVars(c, f), extra)

/** The flag as a note: the observation, then why. Falls back to a plain statement when no phrasing applies. */
internal fun flagNote(c: Ctx, f: Flag, extra: Int = 0): String {
    val first = flagObservation(c, f, extra) ?: genericObservation(f)
    val second = flagWhy(c, f, extra)
    return if (second == null) first else "$first $second"
}

private fun genericObservation(f: Flag): String = when (f.kind) {
    FlagKind.STUCK_LESSON -> "One lesson is still tricky."
    FlagKind.STUCK_SONG -> "One song is still under two stars."
    FlagKind.NOISY_ROOM -> "The room got in the way of the microphone."
    FlagKind.HINT_DEPENDENCE -> "The app played the key itself a few times."
    FlagKind.PAGE_STRUGGLE -> "One page has several tricky lessons."
    FlagKind.READY_FOR_LETTERS -> "The letter lessons are going well."
    FlagKind.READY_FOR_ROAD -> "The playground is finished."
    FlagKind.CRUISING -> "Lessons are coming easily."
    FlagKind.REPLAYING_NOT_MOVING_ON -> "Lessons already finished got replayed, and nothing new started."
    FlagKind.LOW_STARS -> "Some first finishes came in at one star."
    FlagKind.LEFT_HAND_BEHIND -> "The left hand is landing fewer notes than the right."
    FlagKind.FAR_MISSES -> "Most slips landed far from the target key."
    FlagKind.LONG_DAYS -> "Some days ran long."
    FlagKind.MASTERED_SONG -> "A song came out at three stars again and again."
    FlagKind.SCATTERED -> "The lessons jumped around."
    FlagKind.TOY_HEAVY -> "Most of the time went to the toys."
    FlagKind.PRACTICE_DIPPED -> "Fewer days than usual."
    FlagKind.HESITATES_ON -> "One key still takes a moment to find."
    FlagKind.LEFT_HAND_LAGS -> "The left hand takes longer to find its keys than the right."
}
