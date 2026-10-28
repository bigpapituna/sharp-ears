package com.bigpapituna.sharpears.audio

import android.util.Log
import com.bigpapituna.sharpears.TAG
import java.util.concurrent.CopyOnWriteArraySet

/**
 * 2.17: the app's notes ducked while a spoken line of the build's extra plays (engine/Ducking.kt: about 4 dB down). The extra
 * turns it on as its line starts and off as it ends; every [Sampler] follows at once, notes already sounding included.
 */
object Duck {
    @Volatile var on: Boolean = false
        private set
    private val followers = CopyOnWriteArraySet<() -> Unit>()

    fun set(ducked: Boolean) {
        if (on == ducked) return
        on = ducked
        Log.d(TAG, "duck: notes ${if (ducked) "down ${com.bigpapituna.sharpears.engine.Ducking.DB.toInt()} dB" else "back to full"}")
        followers.forEach { it() }
    }

    internal fun follow(f: () -> Unit) { followers += f }
    internal fun unfollow(f: () -> Unit) { followers -= f }
}
