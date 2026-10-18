package com.bigpapituna.sharpears.engine.play

/**
 * The Jam: no goal, no score. Every note the mic hears does something, and this decides what: its colour bursts
 * from the key, the pup bounces higher the higher the note, three or more notes within [CHORD_MS] (a chord) throw
 * confetti, and a quick run of [RUN_NOTES] or more rising notes paints a rainbow. The screen (ui/play/JamScreen.kt)
 * draws the effects; this class only turns strikes into them, so it is tested on the JVM.
 *
 * [low]..[high] is the keyboard on screen and the range the mic listens to, plus every C from C2 to C6 (see
 * [micNotes]; the quest's "Find all the Cs" can be done here). Notes outside it still burst, clamped for the bounce.
 */
class Jam(val low: Int = LOW, val high: Int = HIGH) {
    init { require(low < high) { "the jam needs a range" } }

    sealed interface Effect {
        /** The key's colour bursts out of it. */
        data class Burst(val midi: Int, val argb: Int) : Effect
        /** The pup jumps; [height] 0..1 of his highest jump, scaled to the pitch. */
        data class Bounce(val height: Float) : Effect
        /** Three or more notes together: confetti. */
        data class Chord(val notes: List<Int>) : Effect
        /** A quick rising run: a rainbow arc over the keys, from the run's lowest note to its highest. */
        data class Rainbow(val notes: List<Int>) : Effect
    }

    private var clusterStart = Long.MIN_VALUE / 2
    private val cluster = LinkedHashSet<Int>()
    private var clusterThrown = false

    private val run = ArrayList<Int>()
    private var lastAt = Long.MIN_VALUE / 2
    private var runPainted = false

    private val recent = ArrayDeque<Pair<Int, Long>>()

    /**
     * True when a mic strike of [midi] at [atMs] is most likely the echo of a higher note struck just before: that
     * note's fundamental sits on one of [midi]'s upper partials (the 3rd, 5th or 6th), which the matcher, listening to
     * each key on its own, can take for [midi] itself (measured: C6 also "played" F4). The screen drops these before
     * [strike]; touch and MIDI never ask. Octaves are left alone, they are often played on purpose.
     */
    fun micEcho(midi: Int, atMs: Long): Boolean =
        recent.any { (y, t) -> atMs - t in 0..ECHO_MS && (y - midi) in ECHO_INTERVALS }

    /** Notes heard so far, for the log. */
    var notes = 0; private set

    /** A note was struck at [atMs] (any clock, as long as it only goes forward). Returns what to show, in order. */
    fun strike(midi: Int, atMs: Long): List<Effect> {
        notes++
        val out = ArrayList<Effect>(4)
        out += Effect.Burst(midi, NoteColours.argb(midi))
        out += Effect.Bounce(bounceHeight(midi))

        // A chord: notes whose strikes all fall within CHORD_MS of the first one. Confetti once per chord.
        if (atMs - clusterStart > CHORD_MS) { clusterStart = atMs; cluster.clear(); clusterThrown = false }
        cluster += midi
        if (!clusterThrown && cluster.size >= CHORD_NOTES) { clusterThrown = true; out += Effect.Chord(cluster.sorted()) }

        // A run: each note higher than the one before and close behind it. Notes struck together (a chord) are one
        // step, standing for their highest note. The rainbow paints once per run, when it reaches RUN_NOTES.
        val gap = atMs - lastAt
        when {
            run.isNotEmpty() && gap < TOGETHER_MS -> run[run.lastIndex] = maxOf(run.last(), midi)
            run.isNotEmpty() && gap <= RUN_GAP_MS && midi > run.last() -> run += midi
            else -> { run.clear(); run += midi; runPainted = false }
        }
        lastAt = atMs
        recent.addLast(midi to atMs)
        while (recent.size > 8) recent.removeFirst()
        if (!runPainted && run.size >= RUN_NOTES) { runPainted = true; out += Effect.Rainbow(run.toList()) }
        return out
    }

    /** How high the pup jumps for [midi]: a third of his best for the lowest note, all of it for the highest. */
    fun bounceHeight(midi: Int): Float = (MIN_BOUNCE + (1 - MIN_BOUNCE) * (midi.coerceIn(low, high) - low).toFloat() / (high - low))

    /**
     * What the mic listens for: every key of the range and the Cs around it. The matcher checks every expected key on
     * every hop, so the range is what the phone can afford: 16 keys measured about 10% of a desktop core on the JVM
     * (C3 to C6, 37 keys, about 34%).
     */
    // ponytail: one octave plus the Cs; a wider Jam needs a cheaper free-play detector (onset, then one pitch search)
    fun micNotes(): Set<Int> = (low..high).toSet() + EXTRA_CS

    /**
     * The matcher's groups: each key on its own. In a group the keys' shared overtones are set aside, which in free
     * play turns octaves and fifths into false notes (measured: an F alone also "played" C4 and C5); alone, single
     * notes come out exact, and the [ChordEar] finds the rest of a chord.
     */
    fun micGroups(): List<Set<Int>> = micNotes().sorted().map { setOf(it) }

    companion object {
        /** Middle C to the C above: big keys, where a child sits. */
        const val LOW = 60
        const val HIGH = 72
        /** The Cs the mic also hears, so all of C2 to C6 count. */
        val EXTRA_CS = setOf(36, 48, 84)
        const val CHORD_MS = 150L
        const val CHORD_NOTES = 3
        const val RUN_NOTES = 5
        /** The longest gap between two notes of a run that still counts as quick. */
        const val RUN_GAP_MS = 350L
        /** Closer than this is struck together. */
        const val TOGETHER_MS = 40L
        const val MIN_BOUNCE = 0.3f
        /** How long after a note its echoes are dropped. */
        const val ECHO_MS = 300L
        /** A twelfth, two octaves and a third, two octaves and a fifth: partials 3, 5 and 6. */
        val ECHO_INTERVALS = setOf(19, 28, 31)
    }
}
