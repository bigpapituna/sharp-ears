package com.bigpapituna.sharpears.engine.brief

/**
 * 2.14, the top of the brief (Priya's P8 and P10): one plain line on what the person can now do, and one on practice
 * against screen time. "Can play Hot Cross Buns and find C on the keyboard." "14 min playing of 22 min on screen."
 *
 * The skills are what a lesson finished with two stars or more shows, said no bigger than that: a Playground lesson's
 * phrase from [SKILLS] (a song lesson plays the start the kid lesson has, never the whole song unless it does), a song
 * played to its end with the hands its best run used, and any other lesson by its title. Nothing here claims what a finish
 * cannot show (no "without looking", no "by heart").
 */
object CanDoWords {
    /** The longest line; past it the last skill is left out. */
    const val MAX_CHARS = 140

    /** What a Playground lesson finished well shows, after "Can": the base form of the verb, no full stop. */
    val SKILLS: Map<String, String> = mapOf(
        // The garden.
        "playground-1" to "tell high keys from low ones",
        "playground-2" to "find the black keys in twos and threes",
        "playground-3" to "find C on the keyboard",
        "playground-4" to "walk up and down three white keys",
        "playground-5" to "play the start of Hot Cross Buns",
        "playground-6" to "copy a short tune by ear",
        "playground-7" to "play C on a steady beat",
        "playground-8" to "play the start of Mary Had a Little Lamb",
        "playground-9" to "play a low C with the left hand",
        "playground-10" to "play five keys up to G, one finger each",
        "playground-11" to "play soft and loud on purpose",
        "playground-12" to "play a song with the band",
        // The pond.
        "pond-1" to "play the start of Twinkle, Twinkle, Little Star",
        "pond-2" to "play the start of Row, Row, Row Your Boat",
        "pond-3" to "copy four notes by ear",
        "pond-4" to "clap along with a drum, slow and quick",
        "pond-5" to "clap and play walking and running notes",
        "pond-6" to "play the start of Old MacDonald",
        "pond-7" to "walk three keys up and back with the left hand",
        "pond-8" to "hold a low C in the left hand while the right hand walks",
        "pond-9" to "play the C chord, three keys at once",
        "pond-10" to "play the start of London Bridge",
        "pond-11" to "play Twinkle, Twinkle with the band",
        "pond-12" to "play a song for an audience",
        // The concert hall.
        "hall-1" to "name the keys C to G",
        "hall-2" to "play the start of Ode to Joy by its letters",
        "hall-3" to "play the start of Lightly Row",
        "hall-4" to "play the start of Yankee Doodle",
        "hall-5" to "walk both hands up to G and back",
        "hall-6" to "play a tune softly, then loudly",
        "hall-7" to "keep time with the band",
        "hall-8" to "hold a long note and keep a rest quiet",
        "hall-9" to "copy five notes by ear",
        "hall-10" to "play a tune over a low C and G",
        "hall-11" to "warm up on three songs",
        "hall-12" to "play a recital of three songs",
        // The workshop.
        "workshop-1" to "sit at the piano like a pianist",
        "workshop-2" to "keep a round hand",
        "workshop-3" to "name the fingers by their numbers",
        "workshop-4" to "find the right hand's home keys, C to G",
        "workshop-5" to "find the left hand's home keys",
        "workshop-6" to "share middle C with both thumbs",
        "workshop-7" to "keep the wrist level",
        "workshop-8" to "use the arm for loud and soft notes",
        "workshop-9" to "play short notes and long ones",
        "workshop-10" to "hear what the pedal does",
        "workshop-11" to "answer the left hand with the right",
        "workshop-12" to "play Mary Had a Little Lamb with the book fingers",
        // The treehouse.
        "treehouse-1" to "walk three fingers up and back",
        "treehouse-2" to "walk three fingers of the left hand",
        "treehouse-3" to "tuck the thumb under to climb to G",
        "treehouse-4" to "play a little tune with fingers four and five",
        "treehouse-5" to "skip keys with fingers one, three and five",
        "treehouse-6" to "play five fingers from G",
        "treehouse-7" to "play five fingers from F, with a black key",
        "treehouse-8" to "play the same tune in either hand",
        "treehouse-9" to "copy notes by their finger numbers",
        "treehouse-10" to "copy a tune with the left hand",
        "treehouse-11" to "tell which finger pressed a key",
        "treehouse-12" to "play Hot Cross Buns with both hands",
        // The night sky.
        "night-1" to "play on a steady beat, slow and quick",
        "night-2" to "clap walking, running and long notes",
        "night-3" to "keep a rest quiet",
        "night-4" to "play right on the click",
        "night-5" to "hear whether a tune goes up or down",
        "night-6" to "hear whether two tunes are the same",
        "night-7" to "hear and play loud and quiet",
        "night-8" to "hear and play fast and slow",
        "night-9" to "hear long notes from short ones",
        "night-10" to "hear a happy chord from a sad one",
        "night-11" to "make up a tune on the black keys",
        "night-12" to "play Twinkle, Twinkle with the band",
        // The fair.
        "fair-1" to "find a key by its colour or letter",
        "fair-2" to "play up to G and back smoothly",
        "fair-3" to "play a big, heavy note",
        "fair-4" to "play the start of a song three times",
    )

    /** One skill after "Can", or null for an id with no title. */
    fun phrase(c: CanDo, titles: BriefTitles): String? {
        c.lessonId?.let { id -> return SKILLS[id] ?: "play the lesson “${titles.lesson(id)}”" }
        val song = c.songId?.let(titles::song) ?: return null
        return when (c.hands) {
            Hands.RIGHT -> "play $song with the right hand"
            Hands.LEFT -> "play $song with the left hand"
            Hands.BOTH -> "play $song with both hands"
            null -> "play $song"
        }
    }

    /**
     * "Can play Hot Cross Buns, find C on the keyboard and tell high keys from low ones." Null with nothing to say. A skill
     * that would take the line past [MAX_CHARS] is left out (the latest come first, so the newest always stays).
     */
    fun line(canDo: List<CanDo>, titles: BriefTitles): String? {
        val phrases = canDo.mapNotNull { phrase(it, titles) }.distinct()
        var n = phrases.size
        while (n > 0) {
            val l = compose(phrases.take(n))
            if (l.length <= MAX_CHARS || n == 1) return l
            n--
        }
        return null
    }

    private fun compose(p: List<String>): String = "Can " + when (p.size) {
        1 -> p[0]
        else -> p.dropLast(1).joinToString(", ") + " and " + p.last()
    } + "."

    /** "14 min playing of 22 min on screen." Null when the range had no time on screen at all. */
    fun minutesLine(practiceMinutes: Int, screenMinutes: Int): String? =
        if (screenMinutes <= 0 && practiceMinutes <= 0) null
        else "$practiceMinutes min playing of ${maxOf(screenMinutes, practiceMinutes)} min on screen."
}
