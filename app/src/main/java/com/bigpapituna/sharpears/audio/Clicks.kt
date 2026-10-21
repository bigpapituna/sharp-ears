package com.bigpapituna.sharpears.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.util.Log
import com.bigpapituna.sharpears.TAG
import com.bigpapituna.sharpears.engine.Wav
import com.bigpapituna.sharpears.engine.clickSamples
import java.io.File

/** The metronome's two sounds (accent and beat), synthesized once into the cache and played through SoundPool. */
class Clicks(context: Context) {
    private val pool = SoundPool.Builder().setMaxStreams(2).setAudioAttributes(
        AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
    ).build()
    private val accent: Int
    private val beat: Int

    init {
        // Written once (the name carries the sound's hash): every lesson and song makes a Clicks, and each used to write
        // both files again on the main thread.
        fun file(name: String, accent: Boolean) = clickSamples(accent).let { s ->
            File(context.cacheDir, "$name-${Integer.toHexString(s.contentHashCode())}.wav").also { if (it.length() != 44L + 2L * s.size) Wav.write(it, s, 48_000) }   // mono PCM16: whole or rewritten
        }
        accent = pool.load(file("click-accent", true).path, 1)
        beat = pool.load(file("click-beat", false).path, 1)
    }

    fun play(isAccent: Boolean) {
        pool.play(if (isAccent) accent else beat, 1f, 1f, 2, 0, 1f)
        Log.d(TAG, "click ${if (isAccent) "accent" else "beat"}")
    }

    fun release() = pool.release()
}
