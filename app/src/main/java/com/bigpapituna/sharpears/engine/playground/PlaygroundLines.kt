package com.bigpapituna.sharpears.engine.playground

/**
 * The words the Playground's stages show over the games (ui/playground: HighLow, KeyHunt, ClapStage, SoftLoudStage,
 * ConcertStage), listed for the voice catalog so the pup can say them in the recorded voice at the kids' speed. Keep
 * this list in step with those files when a call-out changes (the catalog test checks they are speakable).
 */
object PlaygroundLines {
    val CALL_OUTS: List<String> = listOf(
        // The pup flying (lesson 1).
        "Up I go!", "Landed!", "Higher!", "Lower!",
        // The key hunts (lessons 2 and 3).
        "You found it!", "That's it!", "Yes!", "Look for two black keys.", "Look for three black keys.",
        "Find a dog house!", "Find a fence!", "Find a C!", "You found them all!",
        // The drum (lesson 7).
        "Get ready to clap!", "On the boom!", "Clap, clap!", "What a drummer you are!", "I heard you! Now right on the boom.",
        // The nap (lesson 11).
        "Goodnight. That was lovely.", "First, play middle C your usual way.", "Oops, you woke me! Softer, please.",
        "Soft, so I keep sleeping.", "Now loud. Wake me up!",
        // The concert (lesson 12).
        "Which song tonight?", "Bravo!",
        // 2.1.1: the C hunt and the nap's nudges, spoken now between rounds.
        "C is just left of the dog house.", "A little louder!",
        // Every round shows itself first, then listens; three tries and it goes by (RoundCoach).
        LET_ME_SHOW, YOUR_TURN, AGAIN, LET_GO, WHITE_KEY, CLAP_AGAIN, I_HEARD_YOU,
    ) + PageLines.ALL

    /** The first round's demo begins. */
    const val LET_ME_SHOW = "Let me show you."
    /** The demo is over: the kid plays. */
    const val YOUR_TURN = "Your turn!"
    /** A quiet stretch: the round is shown again. */
    const val AGAIN = "Here it is again."
    /** Three tries: the round goes by with one star. */
    const val LET_GO = "Good try, let's keep going."
    /** A black-key hunt heard a white key; the pup points at the group, which glows. */
    const val WHITE_KEY = "That one's white. Try the black ones here."
    /** The drum heard no claps: it plays again by itself. */
    const val CLAP_AGAIN = "Let's clap again!"
    /** Shown (not spoken) when a sound has no key: the pup's ear twitches. */
    const val I_HEARD_YOU = "I heard you!"
}

/**
 * 2.2: what the pond's and the concert hall's stages say (KeyHunt's letters, the copy games, the chord, the soft and
 * loud tune, the set list, the concerts). Part of [PlaygroundLines.CALL_OUTS], so the voice records them at kid speed.
 */
object PageLines {
    /** The letter hunt's call for each letter: readers hear the letter, pre-readers the colour and the animal. */
    fun find(letter: Char, reader: Boolean): String = if (reader) "Find ${if (letter in "EF") "an" else "a"} $letter!"
        else ANIMALS.getValue(letter).let { (colour, animal) -> "Find ${if (colour.first() in "aeiou") "an" else "a"} $colour $animal key!" }

    /** Where a letter lives, said after a key that was not it. */
    fun where(letter: Char, reader: Boolean): String {
        val who = if (reader) "$letter" else "The ${ANIMALS.getValue(letter).second} key"
        return when (letter) {
            'C' -> "$who is just left of the dog house."
            'D' -> "$who sits inside the dog house."
            'E' -> "$who is just right of the dog house."
            'F' -> "$who is just left of the fence."
            else -> "$who sits inside the fence, next to ${if (reader) "F" else "the frog"}."
        }
    }

    /** The letters the hunts ask for, with their colour and animal (engine/skin NoteColours). */
    val ANIMALS = mapOf('C' to ("red" to "cat"), 'D' to ("orange" to "dog"), 'E' to ("yellow" to "elephant"), 'F' to ("green" to "frog"),
        'G' to ("teal" to "goat"))

    // The copy games.
    const val NEXT_NOTE = "Which note comes next?"
    val POND_COPIED = listOf("Ribbit! You played it back!", "The frogs love it!", "Just like the frogs!")
    val HALL_COPIED = listOf("Just like me!", "You copied it!", "Bravo!")
    const val ALL_COPIED = "You copied them all!"

    // The chord.
    val CHORD_FOUND = listOf("Ahh, what a comfy chord!", "Three at once!", "What a chord!")
    const val ALL_THREE = "All three together!"
    const val CHORD_KEYS = "C, E and G, all together."
    const val FIRST_CHORD = "You played your first chord!"

    // The soft and loud tune: the round's ask, then what the pup heard.
    const val TUNE_USUAL = "First, play it your usual way."
    const val TUNE_SOFT = "Now softly, so I keep sleeping."
    const val TUNE_LOUD = "Now loudly. Wake me up!"
    const val TUNE_SOFT_AGAIN = "Softly again, for my nap."
    const val HEARD_USUAL = "Thank you. Now I know your usual way."
    const val HEARD_SOFT = "So soft. I stayed asleep."
    const val HEARD_IN_BETWEEN = "In between. I only stirred."
    const val HEARD_LOUD_IN_SOFT = "That woke me up! Softer next time."
    const val HEARD_LOUD = "Boom! You woke me up!"
    const val HEARD_NOT_LOUD = "A little louder next time!"

    // The set list.
    const val PICK_THREE = "Pick three songs for your recital!"
    val PICKS_LEFT = listOf("That's three!", "One more!", "Two more!")
    const val NEXT_SONG = "Next song!"
    const val WARMED_UP = "All warmed up!"

    // The concerts.
    const val POND_WHICH = "Which song for the frogs?"
    const val RECITAL_BOW = "Take a bow!"

    val ALL: List<String> = buildList {
        for (letter in "CDEFG") for (reader in listOf(true, false)) { add(find(letter, reader)); add(where(letter, reader)) }
        add(NEXT_NOTE); addAll(POND_COPIED); addAll(HALL_COPIED); add(ALL_COPIED)
        addAll(CHORD_FOUND); add(ALL_THREE); add(CHORD_KEYS); add(FIRST_CHORD)
        addAll(listOf(TUNE_USUAL, TUNE_SOFT, TUNE_LOUD, TUNE_SOFT_AGAIN, HEARD_USUAL, HEARD_SOFT, HEARD_IN_BETWEEN, HEARD_LOUD_IN_SOFT, HEARD_LOUD, HEARD_NOT_LOUD))
        add(PICK_THREE); addAll(PICKS_LEFT); add(NEXT_SONG); add(WARMED_UP)
        add(POND_WHICH); add(RECITAL_BOW)
    }.distinct()
}
