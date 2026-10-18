package com.bigpapituna.sharpears.engine.play

import com.bigpapituna.sharpears.engine.ear.MelodyWalk
import java.util.Random

/** Who plays Pup says: a profile's id and the name the pup calls them by. */
data class PupSaysPlayer(val id: String, val name: String)

/**
 * One player's result: the longest pattern they copied ([best], 0 when they never got the first one). The World
 * team's crown of the day goes to [PupSaysGame.crown] over the day's results (PlayStore keeps each profile's best
 * of the day).
 */
data class PupSaysResult(val profileId: String, val best: Int)

/**
 * Pup says: Simon on the piano. The pup plays a pattern, the player copies it, and the next time it is one note
 * longer; a wrong note is a miss, three misses and that player is done, and the longest pattern they copied is
 * their best. One, two or three players share one phone and one piano and take turns.
 *
 * Everyone hears the same song: the pattern is the start of one melody (drawn with melody memory's walk, so it moves
 * the way melody memory does), [START] notes long at first, each player at their own length. Kids (any kid among the
 * players) get C D E F G from middle C; grown-ups the C major octave from middle C.
 *
 * Turns, done here and not in the screen: a turn is HANDOFF (two or more players: the pup says whose turn it is and
 * waits for "ready"), DEMO (the pup plays; the mic is off), LISTEN (the player copies), then RESULT. A note that
 * arrives outside LISTEN is ignored, and the answer carries the pup's line about whose turn it is, so a sibling
 * hitting keys early scores nothing. After a miss the turn passes on (with one player, the pup plays it again).
 */
class PupSaysGame(players: List<PupSaysPlayer>, kid: Boolean, seed: Long = System.nanoTime()) {
    init {
        require(players.size in 1..MAX_PLAYERS) { "Pup says takes one to three players" }
        require(players.map { it.id }.toSet().size == players.size) { "a player can only sit once" }
    }

    enum class Phase { HANDOFF, DEMO, LISTEN, RESULT, OVER }

    /** A player's place at the piano: how long their pattern is now, their best, their misses. */
    class Seat internal constructor(val player: PupSaysPlayer) {
        var length = START; internal set
        var best = 0; internal set
        var misses = 0; internal set
        val out get() = misses >= MISSES
        val triesLeft get() = (MISSES - misses).coerceAtLeast(0)
    }

    sealed interface Answer {
        /** Not this player's moment (or the game is over): nothing counts. [line] is what the pup says, if anything. */
        data class Ignored(val line: String?) : Answer
        /** Right note; [copied] notes of the pattern so far. */
        data class Found(val copied: Int) : Answer
        /** The whole pattern, right: [length] notes copied ([best] when that beats their best so far). */
        data class Passed(val length: Int, val best: Boolean, val line: String) : Answer
        /** A wrong note: a miss. [out] when it was their last try. */
        data class Missed(val misses: Int, val out: Boolean, val line: String) : Answer
    }

    val seats: List<Seat> = players.map { Seat(it) }
    val multi get() = seats.size > 1
    val pool: List<Int> = if (kid) KID_POOL else ADULT_POOL
    private val walk = MelodyWalk(pool, MelodyWalk.stepsFor(if (kid) 0 else 1), Random(seed), start = 0)
    private val song = ArrayList<Int>()

    var phase = if (multi) Phase.HANDOFF else Phase.DEMO; private set
    var turn = 0; private set
    /** Notes of the pattern copied in this try. */
    var copied = 0; private set
    val current: Seat get() = seats[turn]
    val over get() = phase == Phase.OVER

    /** The first [length] notes of the song everyone copies. */
    fun melody(length: Int): List<Int> {
        while (song.size < length) song += walk.next()
        return song.take(length)
    }

    /** The pattern the current player copies. */
    val pattern: List<Int> get() = melody(current.length)

    /** "Mia, your turn." The hand-off line, also said to anyone who plays while it is not their moment. */
    fun whoseTurnLine(): String = if (!multi) "Your turn." else "${current.player.name}, your turn."

    /** The player at the keys said ready: the pup plays. */
    fun ready() { if (phase == Phase.HANDOFF) phase = Phase.DEMO }

    /** The pup finished playing the pattern: listen now. */
    fun demoDone() { if (phase == Phase.DEMO) { phase = Phase.LISTEN; copied = 0 } }

    /** What the mic should listen for: the keys of the pool while someone copies, nothing otherwise. */
    fun micNotes(): Set<Int> = if (phase == Phase.LISTEN) pool.toSet() else emptySet()

    /** The matcher's groups: each key on its own (see Jam.micGroups). */
    fun micGroups(): List<Set<Int>> = pool.map { setOf(it) }

    /** A note played (touch, MIDI or mic). Only the current player's LISTEN counts. */
    fun input(midi: Int): Answer = when (phase) {
        Phase.OVER, Phase.RESULT -> Answer.Ignored(null)
        Phase.HANDOFF -> Answer.Ignored("Wait for it. ${whoseTurnLine()} Tap ready at the keys.")
        Phase.DEMO -> Answer.Ignored(if (multi) "Listen first, ${current.player.name}." else "Listen first, then copy me.")
        Phase.LISTEN -> judge(midi)
    }

    private fun judge(midi: Int): Answer {
        val seat = current
        val p = pattern
        if (midi != p[copied]) {
            seat.misses++
            copied = 0
            phase = Phase.RESULT
            return Answer.Missed(seat.misses, seat.out, missLine(seat))
        }
        copied++
        if (copied < p.size) return Answer.Found(copied)
        val best = p.size > seat.best
        seat.best = maxOf(seat.best, p.size)
        seat.length++
        phase = Phase.RESULT
        return Answer.Passed(p.size, best, passLine(p.size))
    }

    private fun passLine(n: Int): String {
        val yes = when (n % 3) { 0 -> "Yes! $n notes."; 1 -> "You copied all $n."; else -> "That's it, $n notes!" }
        return if (multi) yes else "$yes Now one more."
    }

    private fun missLine(seat: Seat): String {
        val name = if (multi) seat.player.name else null
        return when (seat.triesLeft) {
            0 -> (name?.let { "$it, that" } ?: "That") + " was your last try. " +
                if (seat.best > 0) "Your best: ${seat.best} notes!" else "Good playing!"
            1 -> "Oops! " + (name?.let { "$it, one" } ?: "One") + " more try."
            else -> "Oops! " + (name?.let { "$it, two" } ?: "Two") + " more tries."
        }
    }

    /**
     * After RESULT: the next player who is still in. With one player (or one left) it is the same one again, straight
     * to the pup playing; with a new player, the hand-off first. OVER once everyone is out.
     */
    fun next() {
        if (phase != Phase.RESULT) return
        if (seats.all { it.out }) { phase = Phase.OVER; return }
        val from = turn
        do { turn = (turn + 1) % seats.size } while (seats[turn].out)
        copied = 0
        phase = if (turn == from) Phase.DEMO else Phase.HANDOFF
    }

    /** Ends the game now (the exit pill): everyone keeps the best they had. */
    fun stop() { phase = Phase.OVER }

    /** Everyone's best, in seat order. Everyone who played gets a result, whatever their best. */
    fun results(): List<PupSaysResult> = seats.map { PupSaysResult(it.player.id, it.best) }

    companion object {
        const val START = 2
        const val MISSES = 3
        const val MAX_PLAYERS = 3
        /** C D E F G from middle C. */
        val KID_POOL = listOf(60, 62, 64, 65, 67)
        /** The C major octave from middle C. */
        val ADULT_POOL = listOf(60, 62, 64, 65, 67, 69, 71, 72)

        /** Who wears the crown: everyone with the best result (ties share it), nobody when no one copied anything. */
        fun crown(results: List<PupSaysResult>): Set<String> {
            val top = results.maxOfOrNull { it.best } ?: return emptySet()
            return if (top <= 0) emptySet() else results.filter { it.best == top }.mapTo(LinkedHashSet()) { it.profileId }
        }
    }
}
