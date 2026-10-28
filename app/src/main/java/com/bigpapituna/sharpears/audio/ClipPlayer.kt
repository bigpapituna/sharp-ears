package com.bigpapituna.sharpears.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.bigpapituna.sharpears.TAG

/**
 * 2.18: one recorded clip at a time from the APK's assets (MediaPlayer), on the main thread: the voice's recorded and narrated
 * lines (audio/Voice.kt) and the coach's lines (src/afterdark CoachHost) share it, so both end their lines the same way.
 * [play]'s ended comes exactly once: finished (true), failed or stopped (false); a clip whose end is never reported ends just
 * after its length. [report]: the analytics error name for a clip that fails (null: none).
 */
class ClipPlayer(context: Context, private val report: String? = null) {
    private val app = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private var mp: MediaPlayer? = null
    private var ended: ((Boolean) -> Unit)? = null
    private val attrs = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()

    /** A clip is loading or playing. */
    val busy: Boolean get() = mp != null

    /** Plays [asset] (anything playing stops first): [started] with its length once it sounds, [ended] once when it is over. */
    fun play(asset: String, started: (lengthMs: Long?) -> Unit, ended: (ok: Boolean) -> Unit) {
        stop()
        val m = MediaPlayer()
        mp = m
        this.ended = ended
        try {
            app.assets.openFd(asset).use { m.setDataSource(it.fileDescriptor, it.startOffset, it.length) }
            m.setAudioAttributes(attrs)
            m.setOnPreparedListener {
                if (mp !== m) return@setOnPreparedListener
                m.start()
                val length = m.duration.toLong().takeIf { it > 0 }
                started(length)
                // Should its end never be reported, the clip still ends.
                main.postDelayed({ if (mp === m) finish(m, true) }, (length ?: LOST_MS) + 2_000)
            }
            m.setOnCompletionListener { finish(m, true) }
            m.setOnErrorListener { _, what, extra -> Log.d(TAG, "clip: $asset failed ($what, $extra)"); finish(m, false); true }
            m.prepareAsync()
        } catch (e: Exception) {
            Log.w(TAG, "clip: $asset did not play", e)
            report?.let { com.bigpapituna.sharpears.analytics.Errors.report(it, e) }
            finish(m, false)
        }
    }

    /** Cuts off the clip playing (its ended comes now, false). */
    fun stop() { mp?.let { finish(it, false) } }

    private fun finish(m: MediaPlayer, ok: Boolean) {
        if (mp !== m) { runCatching { m.release() }; return }
        mp = null
        val e = ended
        ended = null
        runCatching { m.stop() }
        runCatching { m.release() }
        e?.invoke(ok)
    }

    private companion object {
        /** A clip whose length MediaPlayer could not tell: the longest any line runs, and then some. */
        const val LOST_MS = 60_000L
    }
}
