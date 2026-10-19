package com.bigpapituna.sharpears.engine.playground

import com.bigpapituna.sharpears.engine.Exercise
import com.bigpapituna.sharpears.engine.Fingering
import com.bigpapituna.sharpears.engine.Note
import com.bigpapituna.sharpears.engine.Song
import com.bigpapituna.sharpears.engine.toSong

/**
 * 2.3 part B: what the treehouse's and the night sky's stages say (ui/playground CopyStage, TapStage, BeatStarsStage,
 * HigherLowerStage, LoudQuietStage, TempoStage, SkyChordStage, StarJamStage, the concerts), listed with the two pages'
 * steps and scenes for the voice catalog ([fixed], registered in SpokenCatalog.kidLines: recorded at the kids' speed).
 * Never a number written as a digit, never a claim about which finger was used: the app hears keys, not fingers.
 */
object TreeSkyLines {
    private val NUMBER = listOf("zero", "one", "two", "three", "four", "five")

    /** A finger as a word: "three". */
    fun word(finger: Int): String = NUMBER[finger.coerceIn(1, 5)]

    /** What the pup says as his hand plays a phrase: its fingers in order ("One, two, three!"). */
    fun fingerCall(fingers: List<Int>): String = fingers.joinToString(", ") { word(it) }.replaceFirstChar { it.uppercase() } + "!"

    // ---- Copy the pup ------------------------------------------------------------------------------------------------
    val TREE_COPIED = listOf("You played it back!", "Just like me!", "Up the tree we go!")
    val SKY_COPIED = listOf("The stars sang it back!", "You copied the stars!", "Twinkle, just like me!")
    const val BLACK_KEY = "You found the black key!"
    const val NO_PEEKING = "Now just listen. No peeking!"

    // ---- The tap games ------------------------------------------------------------------------------------------------
    /** What the pup asks for each kind of tap game. */
    fun ask(kind: TapKind): String = when (kind) {
        TapKind.UP_DOWN -> "Up or down?"
        TapKind.SAME_DIFFERENT -> "Same or different?"
        TapKind.LOUDER -> "Which one was louder?"
        TapKind.FAST_SLOW -> "Fast or slow?"
        TapKind.LONG_SHORT -> "Long or short?"
        TapKind.HAPPY_SAD -> "Happy or sad?"
        TapKind.FINGER -> "Which finger was it?"
    }

    /** The answer, said by the pup when he shows it (his example, a round let go). */
    fun answer(choice: TapChoice): String = when (choice) {
        TapChoice.UP -> "It went up!"
        TapChoice.DOWN -> "It went down!"
        TapChoice.SAME -> "They were the same!"
        TapChoice.DIFFERENT -> "They were different!"
        TapChoice.FIRST -> "The first one was louder!"
        TapChoice.SECOND -> "The second one was louder!"
        TapChoice.FAST -> "It was fast, like a rabbit!"
        TapChoice.SLOW -> "It was slow, like a turtle!"
        TapChoice.LONG -> "It was a long one!"
        TapChoice.SHORT -> "It was a short one!"
        TapChoice.HAPPY -> "It sounds happy, like the sun!"
        TapChoice.SAD -> "It sounds sad, like the rain!"
        TapChoice.F1 -> "Finger one, the thumb!"
        TapChoice.F5 -> "Finger five, the little finger!"
        else -> "Finger ${word(choice.finger!!)}!"
    }

    /** The answer when the child tapped it. */
    fun yes(choice: TapChoice): String = "Yes! " + answer(choice)

    /** The word under each button (readers see it; the button's name for TalkBack). */
    fun label(choice: TapChoice): String = when (choice) {
        TapChoice.UP -> "Up"; TapChoice.DOWN -> "Down"; TapChoice.SAME -> "Same"; TapChoice.DIFFERENT -> "Different"
        TapChoice.FIRST -> "First"; TapChoice.SECOND -> "Second"; TapChoice.FAST -> "Fast"; TapChoice.SLOW -> "Slow"
        TapChoice.LONG -> "Long"; TapChoice.SHORT -> "Short"; TapChoice.HAPPY -> "Happy"; TapChoice.SAD -> "Sad"
        else -> word(choice.finger!!).replaceFirstChar { it.uppercase() }
    }

    const val LISTEN_AGAIN = "Let's listen again."
    const val WATCH_AGAIN = "Watch my hand again."
    /** Now the key: with the finger the hand used. */
    fun playWith(finger: Int): String = "Now you play it, with finger ${word(finger)}!"
    val PLAYED = listOf("That's the key!", "You played it!", "Yes, that one!")
    const val LOOK_WHERE = "Look where my hand pressed."

    // ---- A key on every beat ------------------------------------------------------------------------------------------
    const val BEAT_ALL = "You lit up the whole sky!"
    const val BEAT_SOME = "Twinkle! You found the beat."
    const val BEAT_AGAIN = "Let's play it again!"
    const val ON_THE_BEAT = "On the beat!"
    const val BEAT_NEAR = "I heard you! Now right on the beat."
    const val PLAY_C = "Play middle C, on the beat."

    // ---- Higher or lower ----------------------------------------------------------------------------------------------
    fun want(way: Way): String = if (way == Way.HIGHER) "Play a higher one!" else "Play a lower one!"
    fun went(way: Way): String = if (way == Way.HIGHER) "Up you go!" else "Down you go!"
    fun same(way: Way): String = if (way == Way.HIGHER) "That's my note! Go higher." else "That's my note! Go lower."
    fun otherWay(way: Way): String = if (way == Way.HIGHER) "That one went down. Try higher!" else "That one went up. Try lower!"
    const val UP_AND_DOWN = "You can go up and down!"

    // ---- Loud and quiet ---------------------------------------------------------------------------------------------
    const val USUAL = "First, play it your usual way."
    const val NOW_LOUD = "Now loud, like thunder!"
    const val NOW_QUIET = "Now quiet, like a mouse."
    const val HEARD_USUAL = "Thank you. Now I know your usual way."
    const val LOUD_YES = "Boom! A big loud star!"
    const val LOUD_NOT = "A little louder next time!"
    const val QUIET_YES = "So quiet. A tiny twinkle."
    const val QUIET_IN_BETWEEN = "In between. Even softer next time."
    const val QUIET_LOUD = "That was loud! Softer next time."
    const val LOUD_AND_QUIET = "You can play loud and quiet!"

    // ---- Fast and slow ------------------------------------------------------------------------------------------------
    const val NOW_FAST = "Now fast, like a rabbit!"
    const val NOW_SLOW = "Now slow, like a turtle."
    const val FAST_YES = "Zoom! What a fast rabbit!"
    const val FAST_NOT = "A little faster next time!"
    const val SLOW_YES = "Nice and slow, like a turtle."
    const val SLOW_NOT = "A little slower next time!"
    const val FAST_AND_SLOW = "Fast and slow, you can do both!"

    // ---- The happy chord ----------------------------------------------------------------------------------------------
    val SUN_OUT = listOf("Here comes the sun!", "What a happy chord!", "Sunshine!")
    const val SUN_ALL = "You made the sun come out!"

    // ---- The jam ------------------------------------------------------------------------------------------------------
    const val JAM_GO = "The band is ready. Play any black keys!"
    const val JAM_BLACK = "The black keys make the stars."
    const val JAM_NUDGE = "Here are some black keys to start."
    const val JAM_END = "Look, you drew your tune in the stars!"

    // ---- The concerts -------------------------------------------------------------------------------------------------
    const val TREE_BRAVO = "Bravo! The whole tree is cheering!"
    const val SKY_BRAVO = "Bravo! The stars are dancing!"

    /** The phrases of a copy game, as the stage plays them: notes in time order, a rest (a gap after a note) ends one. */
    fun phrases(song: Song): List<List<Note>> {
        val out = ArrayList<MutableList<Note>>()
        var end = Long.MIN_VALUE
        for (n in song.notes.sortedWith(compareBy({ it.startMs }, { it.midi }))) {
            if (out.isEmpty() || n.startMs > end + 50) out += mutableListOf<Note>()
            out.last() += n
            end = maxOf(end, n.startMs + (n.durationMs / 0.92).toLong())
        }
        return out
    }

    /** Each phrase's finger call for a copy game's [ex]. */
    fun calls(ex: Exercise): List<String> {
        val song = ex.toSong("calls", "calls")
        val f = Fingering.of(song)
        return phrases(song).map { p -> fingerCall(p.map { f.getValue(it) }) }
    }

    /** Every fixed line the two pages say: their steps and scenes (as LessonScreen speaks them) and the stages' lines. */
    fun fixed(): List<String> = buildList {
        for (lesson in (TREEHOUSE.lessons + NIGHT_SKY.lessons)) lesson.steps.forEach { step ->
            add(step.text.replace("\n\n", " "))
            step.scenes.forEach { add(com.bigpapituna.sharpears.engine.theory.spokenTheory(it.say)) }
        }
        for ((text, game) in StageGames.marked) {
            val step = (TREEHOUSE.lessons + NIGHT_SKY.lessons).flatMap { it.steps }.first { it.text == text }
            if (game is StageGame.Copy) addAll(calls(step.exercise!!))
            if (game is StageGame.Tap) {
                add(ask(game.kind))
                game.kind.choices.forEach { add(answer(it)); add(yes(it)) }
                game.rounds.mapNotNull { r -> r.play?.let { r.parts.flatten().firstNotNullOfOrNull { s -> s.finger } } }.forEach { add(playWith(it)) }
            }
            if (game is StageGame.HigherLower) game.rounds.map { it.way }.distinct().forEach { add(want(it)); add(went(it)); add(same(it)); add(otherWay(it)) }
        }
        addAll(TREE_COPIED); addAll(SKY_COPIED); add(BLACK_KEY); add(NO_PEEKING)
        add(LISTEN_AGAIN); add(WATCH_AGAIN); addAll(PLAYED); add(LOOK_WHERE)
        add(BEAT_ALL); add(BEAT_SOME); add(BEAT_AGAIN); add(ON_THE_BEAT); add(BEAT_NEAR); add(PLAY_C)
        add(UP_AND_DOWN)
        addAll(listOf(USUAL, NOW_LOUD, NOW_QUIET, HEARD_USUAL, LOUD_YES, LOUD_NOT, QUIET_YES, QUIET_IN_BETWEEN, QUIET_LOUD, LOUD_AND_QUIET))
        addAll(listOf(NOW_FAST, NOW_SLOW, FAST_YES, FAST_NOT, SLOW_YES, SLOW_NOT, FAST_AND_SLOW))
        addAll(SUN_OUT); add(SUN_ALL)
        addAll(listOf(JAM_GO, JAM_BLACK, JAM_NUDGE, JAM_END, TREE_BRAVO, SKY_BRAVO))
    }.distinct()
}
