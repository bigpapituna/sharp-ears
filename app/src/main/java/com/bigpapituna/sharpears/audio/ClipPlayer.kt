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
    // 2.18.1: the piano's usage (Stage.kt), so the voice gets the same stereo speakers and processing as the notes; as
    // ACCESSIBILITY it went out the primary path in mono and sounded like the earpiece on Roy's Fold (dumpsys, 2026-10-28)
    private val attrs = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()

    /** A clip is loading or playing. */
    val busy: Boolean get() = mp != null

    /** Plays [asset] (anything playing stops first): [started] with its length once it sounds, [ended] once when it is over. */
    fun play(asset: String, started: (lengthMs: Long?) -> Unit, ended: (ok: Boolean) -> Unit) {
        stop()
        val m = MediaPlayer()
        mp = m
        this.ended = ended
        val asked = android.os.SystemClock.uptimeMillis()
        try {
            app.assets.openFd(asset).use { m.setDataSource(it.fileDescriptor, it.startOffset, it.length) }
            m.setAudioAttributes(attrs)
            m.setOnPreparedListener {
                if (mp !== m) return@setOnPreparedListener
                m.start()
                val length = m.duration.toLong().takeIf { it > 0 }
                started(length)
                // 2.19.0: the prepare time (QA B-07) and the end below, for the QA harness and the timing logs.
                Log.d(TAG, "clip: $asset started, prepared in ${android.os.SystemClock.uptimeMillis() - asked} ms, $length ms long")
                // Should its end never be reported, the clip still ends.
                main.postDelayed({ if (mp === m) finish(m, true) }, (length ?: LOST_MS) + 2_000)
            }
            m.setOnCompletionListener { Log.d(TAG, "clip: $asset end"); finish(m, true) }
            m.setOnErrorListener { _, what, extra -> Log.d(TAG, "clip: $asset failed ($what, $extra)"); finish(m, false); true }
            m.prepareAsync()
        } catch (e: Exception) {
            Log.w(TAG, "clip: $asset did not play", e)
            report?.let { com.bigpapituna.sharpears.analytics.Errors.report(it, e) }
            finish(m, false)
        }
    }

    /**
     * 2.19.0 (QA B-07): the first clip of a process took up to 1 s to prepare (the decoder and the media server's first track),
     * so the app's first line started late. This prepares [asset] once, silently, and lets it go: the next real clip is warm.
     */
    fun warmUp(asset: String) {
        val m = MediaPlayer()
        runCatching {
            app.assets.openFd(asset).use { m.setDataSource(it.fileDescriptor, it.startOffset, it.length) }
            m.setAudioAttributes(attrs)
            m.setOnPreparedListener { it.release() }
            m.setOnErrorListener { p, _, _ -> p.release(); true }
            m.prepareAsync()
        }.onFailure { m.release() }
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
