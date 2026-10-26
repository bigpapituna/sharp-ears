package com.bigpapituna.sharpears.engine.skin

import com.bigpapituna.sharpears.engine.RunWords

/**
 * 2.14, a kid's daily time (Priya's P6: "a lesson limit, not a screen-time limit"): a grown-up picks a cap per child in the
 * grown-ups' corner, behind the gate. It counts time on screen as that child today, from the one minutes ledger
 * (engine/brief/MinutesLedger.kt: Songs, the Jam, the games and the map all count, not only practice). When it is reached,
 * a lesson ends kindly at its next step, a song at the end of its run, and the pup says [line]; the goodbye then offers only
 * the goodbye, and lessons, Songs, the Jam and the games say "Tomorrow" until the next day ([KidDoors]). Pure rules.
 */
object TimeCap {
    /** The choices, in minutes; 0 is off (the default). */
    val CHOICES = listOf(0, 10, 15, 20, 30, 45)

    /** A song or lesson step that runs on past the cap ends at the latest this long after it (a step a kid is stuck on). */
    const val GRACE_MS = 2 * 60_000L

    /** "Off", "10 min". */
    fun label(capMin: Int): String = if (capMin <= 0) "Off" else "$capMin min"

    /** A stored cap read back: one of [CHOICES], anything else is off. */
    fun of(capMin: Int): Int = capMin.takeIf { it in CHOICES } ?: 0

    /** Today's time on screen ([screenMs]) reached the cap. Off never does. */
    fun reached(capMin: Int, screenMs: Long): Boolean = capMin > 0 && screenMs >= capMin * 60_000L

    /** Whole minutes left today (counted up, so "1 min" while any time is left), or null with the cap off. */
    fun minutesLeft(capMin: Int, screenMs: Long): Int? =
        if (capMin <= 0) null else (((capMin * 60_000L - screenMs).coerceAtLeast(0) + 59_999) / 60_000).toInt()

    /**
     * End what is running now? At a step's or a run's end ([boundary]) once the cap is reached, or after [GRACE_MS] past
     * the moment it was reached ([reachedForMs]) when no boundary came.
     */
    fun endNow(boundary: Boolean, reachedForMs: Long): Boolean = boundary || reachedForMs >= GRACE_MS

    /** What the pup says, with the kid's [name] when there is one: "That's all for today, Mia. See you tomorrow!" */
    fun line(name: String?): String = RunWords.named("That's all for today{name}. See you tomorrow!", name)

    /** The grown-ups' line for the setting. */
    const val ABOUT = "Time on screen each day. When it runs out, the pup says goodnight and everything waits for tomorrow."

    /** The grown-ups' line once today's time is up. */
    const val DONE_TODAY = "Today's time is up."
}

/** Where a kid can go from the map (2.14): each asks [KidDoors.open]. */
enum class KidDoor {
    /** A stone, the comeback card's lesson, the next lesson after a finish. */
    LESSON,
    /** A song from Songs, the grown-up's shelf or a pop song, and the Songs screen itself. */
    SONGS,
    /** The Play sheet's toys: the Jam, Pup says, Clap along. */
    TOYS,
    /** The arcade's games. */
    GAMES,
    /** My stickers: the album, dress-up, records (looking, not playing; never closed). */
    STICKERS,
}

/**
 * A kid's day as the doors see it: today's time [capReached]; [lessonsAsleep], lessons asleep after the goal with "After the
 * goal: wind down" on (Bedtime.lessonOpen false); [songsAsleep], Songs and the Jam asleep too (Bedtime.songsOpen false).
 */
data class KidDay(val capReached: Boolean = false, val lessonsAsleep: Boolean = false, val songsAsleep: Boolean = false) {
    /** Everything but the stickers waits for tomorrow: the goodbye offers only the goodbye. */
    val allAsleep: Boolean get() = capReached || songsAsleep
}

object KidDoors {
    /** May a kid go through [door] today? */
    fun open(door: KidDoor, day: KidDay): Boolean = when (door) {
        KidDoor.STICKERS -> true
        KidDoor.LESSON -> !day.capReached && !day.lessonsAsleep
        KidDoor.SONGS, KidDoor.TOYS, KidDoor.GAMES -> !day.allAsleep
    }

    /** What a closed tile says. */
    const val TOMORROW = "Tomorrow"
}
