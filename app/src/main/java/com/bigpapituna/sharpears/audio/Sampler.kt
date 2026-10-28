package com.bigpapituna.sharpears.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.util.Log
import com.bigpapituna.sharpears.TAG
import com.bigpapituna.sharpears.engine.sampleFor

/** Plays piano notes from assets/samples/p<midi>.ogg (VSCO 2 CE upright piano, CC0). */
class Sampler(context: Context, onReady: () -> Unit = {}) {
    private val pool = SoundPool.Builder()
        .setMaxStreams(16)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)          // low-latency path
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()
        ).build()

    private val samples: Map<Int, Int>                         // midi -> SoundPool sound id
    private val streams = HashMap<Int, Int>()                  // midi -> playing stream id
    private val volumes = HashMap<Int, Float>()                // midi -> the volume it was struck at
    private var loaded = 0
    /** 2.17: notes sounding follow the duck (audio/Duck.kt) as a line of the build's extra starts and ends. */
    private val reduck: () -> Unit = {
        val g = com.bigpapituna.sharpears.engine.Ducking.gain(Duck.on)
        streams.toMap().forEach { (midi, id) -> val v = ((volumes[midi] ?: 1f) * g).coerceIn(0f, 1f); pool.setVolume(id, v, v) }
    }

    init {
        val files = context.assets.list("samples").orEmpty()
        pool.setOnLoadCompleteListener { _, _, status ->
            if (status == 0 && ++loaded == files.size) { Log.d(TAG, "sampler: ${files.size} samples loaded"); onReady() }
        }
        samples = files.associate { name ->
            name.removePrefix("p").removeSuffix(".ogg").toInt() to context.assets.openFd("samples/$name").use { pool.load(it, 1) }
        }
        Duck.follow(reduck)
    }

    fun noteOn(midi: Int, velocity: Int) {
        if (samples.isEmpty()) return
        val (sample, rate) = sampleFor(midi, samples.keys)
        val volume = 0.3f + 0.7f * velocity / 127f
        streams.remove(midi)?.let(pool::stop)
        val played = com.bigpapituna.sharpears.engine.Ducking.volume(volume, Duck.on)
        streams[midi] = pool.play(samples.getValue(sample), played, played, 1, 0, rate)
        volumes[midi] = volume
    }

    /** A sounding note turned down to [share] of the volume it was struck at (the step pop's chime fades before its noteOff). */
    fun noteVolume(midi: Int, share: Float) {
        val id = streams[midi] ?: return
        val v = com.bigpapituna.sharpears.engine.Ducking.volume((volumes[midi] ?: 1f) * share.coerceIn(0f, 1f), Duck.on)
        pool.setVolume(id, v, v)
    }

    // ponytail: hard stop on release (no damper fade); add a short volume ramp if the cut-off clicks
    fun noteOff(midi: Int) { streams.remove(midi)?.let(pool::stop) }

    fun release() { Duck.unfollow(reduck); pool.release() }
}
