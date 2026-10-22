package com.bigpapituna.sharpears.ui.chords

import android.content.Context
import com.bigpapituna.sharpears.audio.BandPlayer
import com.bigpapituna.sharpears.audio.Sampler
import com.bigpapituna.sharpears.engine.chords.BandBed

/**
 * The pop band riding the player's transport: each frame the player says how far the playhead moved ([pass]) and the cues
 * in between sound. Kept out of the player's own body (a very large composable, see PlayerVars) so the player adds one
 * call, not a player's worth of locals.
 */
class BandRider(context: Context, sampler: Sampler, private val bed: BandBed) {
    private val player = BandPlayer(context, sampler)

    fun pass(fromMs: Double, toMs: Double) = player.play(bed.cuesIn(fromMs, toMs))

    fun stop() = player.stop()

    fun release() = player.release()
}
