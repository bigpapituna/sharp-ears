package com.bigpapituna.sharpears.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.util.Log
import com.bigpapituna.sharpears.TAG
import com.bigpapituna.sharpears.engine.Wav
import com.bigpapituna.sharpears.engine.chords.BandCue
import com.bigpapituna.sharpears.engine.chords.BandSounds
import com.bigpapituna.sharpears.engine.chords.PopPlay
import java.io.File

/**
 * Plays a pop song's [com.bigpapituna.sharpears.engine.chords.BandBed]: the drums through a small SoundPool (three
 * sounds made once into the cache, like the metronome's clicks), the bass through the piano [sampler] an octave under the
 * left hand. [play] takes the cues the playhead has just passed; [stop] lets go of the bass.
 */
class BandPlayer(context: Context, private val sampler: Sampler) {
    private val pool = SoundPool.Builder().setMaxStreams(6).setAudioAttributes(
        AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
    ).build()
    private val sounds: Map<PopPlay.Kit, Int>
    private val held = HashSet<Int>()

    init {
        sounds = PopPlay.Kit.entries.associateWith { kit ->
            val s = BandSounds.samples(kit)
            val file = File(context.cacheDir, "band-${kit.name.lowercase()}-${Integer.toHexString(s.contentHashCode())}.wav")
                .also { if (it.length() != 44L + 2L * s.size) Wav.write(it, s, BandSounds.RATE) }
            pool.load(file.path, 1)
        }
    }

    fun play(cues: List<BandCue>, bass: Boolean = true) {
        for (c in cues) when (c) {
            is BandCue.Drum -> {
                val v = 0.25f + 0.75f * c.velocity / 127f
                pool.play(sounds.getValue(c.kit), v, v, 1, 0, 1f)
            }
            is BandCue.BassOn -> if (bass) { held += c.midi; sampler.noteOn(c.midi, c.velocity) }
            is BandCue.BassOff -> if (held.remove(c.midi)) sampler.noteOff(c.midi)
        }
    }

    fun stop() {
        held.forEach(sampler::noteOff)
        held.clear()
        Log.d(TAG, "band: stopped")
    }

    fun release() { stop(); pool.release() }
}
