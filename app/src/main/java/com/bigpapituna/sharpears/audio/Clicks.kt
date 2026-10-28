package com.bigpapituna.sharpears.audio

import android.content.Context
import android.util.Log
import com.bigpapituna.sharpears.TAG

/** The metronome's two sounds (accent and beat). 2.18: a handle on the Stage's bank (synthesized once, audio/Stage.kt). */
class Clicks(context: Context) {
    init { Stage.init(context) }

    fun play(isAccent: Boolean) {
        Stage.click(isAccent)
        Log.d(TAG, "click ${if (isAccent) "accent" else "beat"}")
    }

    /** Nothing to free: the Stage keeps its bank. */
    fun release() {}
}
