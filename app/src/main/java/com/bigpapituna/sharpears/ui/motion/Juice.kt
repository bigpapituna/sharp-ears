package com.bigpapituna.sharpears.ui.motion

import android.os.SystemClock
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import com.bigpapituna.sharpears.data.WorldStore
import com.bigpapituna.sharpears.engine.world.Bounce
import com.bigpapituna.sharpears.ui.world.WorldSounds
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.channels.BufferOverflow

/** A note the player struck: every pup on screen hops with it (higher notes higher, louder a little more). */
data class PupBounce(val midi: Int, val riseDb: Double, val atMillis: Long)

/** How much a pup hops with the notes: not at all, a little (ordinary screens) or a lot (the Jam). */
enum class BounceSize { NONE, SMALL, BIG }

/**
 * Juice on every note, for every profile (KIDS-PLAN 1b): one call per detected note makes every pup on screen hop
 * to its pitch ([bounces]), ticks softly ([WorldSounds.tick]), drops a crumb in the treat jar ([world]) and counts as
 * input for the pup's idle clock. The key's flash is HitLights' (hit, wrong, or [com.bigpapituna.sharpears.ui.HitLights.touch]).
 *
 * PlayerScreen (integrator): in keyStruck, `Juice.onStrike(midi, riseDb, sound = soundAllowed())`; riseDb is the
 * mic strike's (MicEvent.Strike.riseDb), [DEFAULT_RISE] for touch and MIDI (or velocity mapped with [riseOf]).
 */
object Juice {
    /** A touch or MIDI note has no rise; this is a medium strike. */
    const val DEFAULT_RISE = 14.0

    private val flow = MutableSharedFlow<PupBounce>(extraBufferCapacity = 32, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val bounces: SharedFlow<PupBounce> = flow

    /** The open profile's world (MainActivity sets it in openApp): notes go into its treat jar. */
    @Volatile var world: WorldStore? = null

    /**
     * A note was played. [sound]: whether the tick may sound now (false while the mic listens without headphones, the
     * same rule as the sampler's soundAllowed(), so the tick never feeds the matcher).
     */
    fun onStrike(midi: Int, riseDb: Double = DEFAULT_RISE, sound: Boolean = true) {
        flow.tryEmit(PupBounce(midi, riseDb, SystemClock.uptimeMillis()))
        PupInput.poke()
        world?.addNotes(1)
        if (sound) WorldSounds.tick(0.35f + 0.65f * Bounce.loudness(riseDb))
    }

    /**
     * The mic heard something that was no key it could name (2.1.1, "I heard you"): every pup on screen hops a little,
     * so nothing a child does is silent on screen. No tick, no crumb: it was not a note.
     */
    fun onHeard() {
        flow.tryEmit(PupBounce(HEARD_MIDI, HEARD_RISE, SystemClock.uptimeMillis()))
        PupInput.poke()
    }
    /** A small hop: middle C at a soft rise. */
    private const val HEARD_MIDI = 60
    private const val HEARD_RISE = 4.0

    /** A MIDI velocity (1..127) as a rise in dB, for the bounce and the tick. */
    fun riseOf(velocity: Int): Double = 6.0 + 24.0 * (velocity.coerceIn(1, 127) / 127.0)
}

/**
 * The last time anyone touched the screen or played: the pups' idle clock. [Modifier.wakesPup] at the root of the app
 * feeds it with every touch (without taking any), [Juice.onStrike] with every note.
 */
object PupInput {
    val last = mutableLongStateOf(SystemClock.uptimeMillis())

    /** Someone did something now. At most one update per [GRAIN_MS] (a drag sends many events; the clock needs one). */
    fun poke() {
        val now = SystemClock.uptimeMillis()
        if (now - last.longValue >= GRAIN_MS) last.longValue = now
    }

    const val GRAIN_MS = 200L
}

/** Every touch inside counts as input for the pups (they wake up, the idle clock restarts). Consumes nothing. */
fun Modifier.wakesPup(): Modifier = this.then(Modifier.pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) { awaitPointerEvent(PointerEventPass.Initial); PupInput.poke() }
    }
})
