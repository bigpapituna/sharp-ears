package com.bigpapituna.sharpears.ui.pup

import android.os.SystemClock
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import com.bigpapituna.sharpears.TAG
import com.bigpapituna.sharpears.engine.pup.PupMood
import com.bigpapituna.sharpears.engine.pup.PupMotion
import com.bigpapituna.sharpears.engine.world.PupBrain
import com.bigpapituna.sharpears.engine.world.PupHold
import com.bigpapituna.sharpears.engine.world.PupLook
import com.bigpapituna.sharpears.engine.world.PupReaction
import com.bigpapituna.sharpears.ui.motion.PupInput
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest

/** A beat asked for: which one, a count so the same beat twice in a row plays twice, and when (System.nanoTime ms). */
internal data class BeatCue(val mood: PupMood, val seq: Int, val atMs: Long)

/**
 * A clip asked for (PupClips): its name, how many times through ([hold]: once, staying on its last frame), the beat played
 * where there is no clip and the mood held after it ([then]), and a count.
 */
internal data class ClipCue(val name: String, val loops: Int, val fallback: PupMood, val seq: Int, val hold: Boolean = false,
                            val then: PupMood? = null)

/**
 * One pup on screen, as a screen drives him (2.10). Make one with [rememberPupController] and pass it to [Pup]:
 *
 * - [play] a beat now: [PupMood.HAPPY] (three stars, a sticker, a surprise), [PupMood.TILT] (a wrong key), [PupMood.WAG]
 *   (a right key). A beat that comes while a bigger one plays waits its turn out (a wag never cuts a jump short).
 * - [mood] holds a mood over the screen's ([PupMood.CHEER] at [bpm], [PupMood.POINT] toward [pointAt], [PupMood.LISTEN]
 *   leaning toward [lean], [PupMood.SLEEP]); null gives the screen's own back.
 * - The living pup's brain (engine/world/PupBrain.kt): idle acts after ten seconds without input (sneeze, tail chase,
 *   butterfly, scratch), asleep after a minute, awake on the next touch or note; [react] for a moment shown with its pose
 *   (a rare drop, the treat jar), which plays its beat too.
 */
@Stable
class PupController internal constructor(seed: Long, idle: Boolean, private val name: String) {
    internal val brain = PupBrain(seed, SystemClock.uptimeMillis(), idle)
    internal val changed = mutableIntStateOf(0)
    private val lookState = mutableStateOf<PupLook>(PupLook.Base)
    internal var cue by mutableStateOf<BeatCue?>(null)
        private set
    /** The last cue a [Pup] took (one pup plays each cue once, and a pup coming on screen does not replay an old one). */
    internal var consumed = 0
    internal var clipCue by mutableStateOf<ClipCue?>(null)
        private set
    internal var clipsTaken = 0
    /** Clips to decode before they are asked for ([prepare]), so they start at once. */
    internal val ahead = mutableStateListOf<String>()

    /** What his brain shows now (the clock loop in [rememberPupController] keeps it current). */
    val look: PupLook get() = lookState.value

    private val moodState = mutableStateOf<PupMood?>(null)

    /** A held mood over the screen's; null: the screen's. A beat set here plays once (as [play]) and holds nothing. */
    var mood: PupMood?
        get() = moodState.value
        set(v) { if (v != null && v.beat) play(v) else if (v != moodState.value) { moodState.value = v; Log.d(TAG, "pup $name: holds ${v ?: "the screen's"}") } }

    /** The beat for [PupMood.CHEER] (folded into a hop a beat by PupMotion.cheerBpm). */
    var bpm by mutableIntStateOf(PupMotion.CHEER_BPM)

    /** When a beat fell (SystemClock.uptimeMillis), so the cheer lands on the music's beats; 0: his own clock. */
    var beatAt by mutableLongStateOf(0L)

    /** [PupMood.POINT]: where to slide, -1 (as far left as he goes) .. 1 (as far right). */
    var pointAt by mutableFloatStateOf(0f)

    /** [PupMood.LISTEN]: which way the microphone is, -1 left, 1 right. */
    var lean by mutableFloatStateOf(1f)

    /** Debug (PupDebug): a blink or a flick now, without waiting for the next one. */
    internal var blinks by mutableIntStateOf(0)
    internal var flicks by mutableIntStateOf(0)

    /** A beat now. A held mood passed here is held instead. */
    fun play(beat: PupMood) {
        if (!beat.beat) { mood = beat; return }
        cue = BeatCue(beat, (cue?.seq ?: 0) + 1, System.nanoTime() / 1_000_000)
        Log.d(TAG, "pup $name: $beat")
    }

    /**
     * A clip in his place ([PupClips]: jump, dance, wave, sleep, celebrate), [loops] times through, then back to the rig;
     * [hold]: once, then he stays on its last frame. Played by a [Pup] with `clips` on; where he has them off, the app has not
     * got the clip, animations are off or he is dressed up, he plays [fallback] instead and then holds [then].
     */
    fun playClip(name: String, loops: Int = 1, fallback: PupMood = PupMood.HAPPY, hold: Boolean = false, then: PupMood? = null) {
        clipCue = ClipCue(name, loops.coerceAtLeast(1), fallback, (clipCue?.seq ?: 0) + 1, hold, then)
        Log.d(TAG, "pup ${this.name}: clip $name x$loops" + if (hold) ", held" else "")
    }

    /** Decodes [name] ahead (a [Pup] with `clips` on), so a [playClip] of it starts at once. */
    fun prepare(name: String) { if (name !in ahead) ahead += name }

    /** A moment: shows the reaction's pose for its time (and plays its beat), then back. Wakes him if he slept. */
    fun react(r: PupReaction) {
        brain.react(r, SystemClock.uptimeMillis())
        refresh()
        Log.d(TAG, "pup $name: react $r")
        r.beat?.let { play(it) }
    }

    /** Input happened (a touch, a note): wakes him and restarts the idle clock. */
    fun poke(at: Long = SystemClock.uptimeMillis()) {
        if (at <= brain.lastInput) return
        brain.input(at)
        refresh()
    }

    internal fun refresh() {
        val now = brain.look(SystemClock.uptimeMillis())
        if (now != lookState.value) {
            Log.d(TAG, "pup $name: " + when (now) {
                is PupLook.Idle -> "idle ${now.act.name.lowercase()}"
                PupLook.Asleep -> "asleep"
                PupLook.Base -> "awake"
                is PupLook.Reacting -> "reacting ${now.reaction.name.lowercase()}"
                is PupLook.Held -> "holding ${now.hold.name.lowercase()}"
            })
            lookState.value = now
        }
        changed.intValue++
    }

    /** The pose to show over the screen's [base]: a reaction's, a held state's, asleep, an idle act's, or the base. */
    fun poseFor(base: Pose): Pose = when (val l = look) {
        is PupLook.Reacting -> l.reaction.pose
        is PupLook.Held -> l.hold.pose ?: base
        PupLook.Asleep -> Pose.SLEEPING
        is PupLook.Idle -> l.act.pose
        PupLook.Base -> base
    }

    /** Held moods keep him busy: no idle acts and no nap while he cheers, points, listens or sleeps on purpose. */
    internal fun holdFor(m: PupMood) {
        val h = if (m == PupMood.IDLE || m.beat) PupHold.NONE else PupHold.BUSY
        if (h != brain.hold) { brain.hold = h; refresh() }
    }
}

private var pupCount = 0

/**
 * A [PupController] for this screen. [idle] false: no idle acts and no sleep (the dress-up screen, where the base pose must
 * stay to show the outfit). Touches anywhere under [com.bigpapituna.sharpears.ui.motion.wakesPup] and every
 * [com.bigpapituna.sharpears.ui.motion.Juice.onStrike] count as input.
 */
@Composable
fun rememberPupController(idle: Boolean = true): PupController {
    val c = remember(idle) { PupController(java.util.Random().nextLong(), idle, "#${++pupCount}") }
    LaunchedEffect(c) { snapshotFlow { PupInput.last.longValue }.collect { c.poke(it) } }
    // Sleeps until the next moment the look can change by itself; any input or reaction restarts the wait.
    LaunchedEffect(c) {
        snapshotFlow { c.changed.intValue }.collectLatest {
            while (true) {
                val now = SystemClock.uptimeMillis()
                val next = c.brain.nextChange(now) ?: break
                delay((next - now).coerceAtLeast(1L))
                c.refresh()
            }
        }
    }
    return c
}
