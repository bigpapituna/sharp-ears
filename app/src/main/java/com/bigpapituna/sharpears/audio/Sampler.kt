package com.bigpapituna.sharpears.audio

import android.content.Context

/**
 * The piano's notes (assets/samples/p<midi>.ogg, VSCO 2 CE upright piano, CC0). 2.18: a handle on the Stage (audio/Stage.kt),
 * which owns the one piano pool, the duck under a coach line and the speaker-and-mic rule; [onReady] runs once the samples
 * are loaded.
 */
class Sampler(context: Context, onReady: () -> Unit = {}) {
    init { Stage.init(context); Stage.whenPianoReady(onReady) }

    fun noteOn(midi: Int, velocity: Int) = Stage.noteOn(midi, velocity)

    /** A sounding note turned down to [share] of the volume it was struck at (the step pop's chime fades before its noteOff). */
    fun noteVolume(midi: Int, share: Float) = Stage.noteVolume(midi, share)

    fun noteOff(midi: Int) = Stage.noteOff(midi)

    /** The activity's end: the Stage lets go of its pools (the next activity loads them again). */
    fun release() = Stage.release()
}
