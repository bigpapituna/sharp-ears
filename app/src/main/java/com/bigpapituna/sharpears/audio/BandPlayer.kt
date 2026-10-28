package com.bigpapituna.sharpears.audio

import android.content.Context
import android.util.Log
import com.bigpapituna.sharpears.TAG
import com.bigpapituna.sharpears.engine.chords.BandCue

/**
 * Plays a pop song's [com.bigpapituna.sharpears.engine.chords.BandBed]: the drums through the Stage's bank (2.18: its kit made
 * once, audio/Stage.kt), the bass through the piano [sampler] an octave under the left hand. [play] takes the cues the playhead
 * has just passed; [stop] lets go of the bass.
 */
class BandPlayer(context: Context, private val sampler: Sampler) {
    private val held = HashSet<Int>()

    init { Stage.init(context) }

    fun play(cues: List<BandCue>, bass: Boolean = true) {
        if (cues.isNotEmpty()) Log.d(TAG, "band: " + cues.joinToString(" ") { c ->
            when (c) { is BandCue.Drum -> c.kit.name.lowercase(); is BandCue.BassOn -> "bass${c.midi}"; is BandCue.BassOff -> "off${c.midi}" } })
        for (c in cues) when (c) {
            is BandCue.Drum -> Stage.band(c.kit, 0.25f + 0.75f * c.velocity / 127f)
            is BandCue.BassOn -> if (bass) { held += c.midi; sampler.noteOn(c.midi, c.velocity) }
            is BandCue.BassOff -> if (held.remove(c.midi)) sampler.noteOff(c.midi)
        }
    }

    fun stop() {
        held.forEach(sampler::noteOff)
        held.clear()
        Log.d(TAG, "band: stopped")
    }

    fun release() = stop()
}
