package com.bigpapituna.sharpears.ui.world

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.bigpapituna.sharpears.R
import com.bigpapituna.sharpears.TAG
import com.bigpapituna.sharpears.engine.world.Rarity

/**
 * The world's sounds (res/raw, from scripts/gen_jingles.py) through one SoundPool for the whole app: the tick on
 * every note, a jingle per rarity, a chime per star, the pup munching, the egg cracking, a pop. Loaded once, on
 * first use; nothing plays while [on] is false (the integrator follows the app's Sound setting), and callers that
 * run with the microphone listening pass their own gate (see Juice.onStrike).
 */
object WorldSounds {
    /** The app's sound switch (Prefs.sound); off: silence. */
    @Volatile var on: Boolean = true

    private var pool: SoundPool? = null
    private val ids = HashMap<Int, Int>()

    /** Loads the sounds (once; later calls do nothing). */
    fun init(context: Context) {
        if (pool != null) return
        val p = SoundPool.Builder().setMaxStreams(6).setAudioAttributes(
            AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
        ).build()
        val app = context.applicationContext
        for (res in listOf(R.raw.tick, R.raw.jingle_common, R.raw.jingle_rare, R.raw.jingle_shiny, R.raw.chime_star, R.raw.munch, R.raw.egg_crack, R.raw.pop)) {
            ids[res] = p.load(app, res, 1)
        }
        pool = p
    }

    private fun play(res: Int, volume: Float = 1f, rate: Float = 1f, what: String) {
        if (!on) return
        val p = pool ?: return
        p.play(ids[res] ?: return, volume, volume, 1, 0, rate.coerceIn(0.5f, 2f))
        if (res != R.raw.tick) Log.d(TAG, "world sound: $what")
    }

    /** The soft tick of a note; [volume] 0..1 (louder strikes tick a little louder). */
    fun tick(volume: Float = 0.5f) = play(R.raw.tick, volume.coerceIn(0.1f, 1f) * 0.6f, what = "tick")

    /** The jingle for a sticker of [rarity]. */
    fun jingle(rarity: Rarity) = when (rarity) {
        Rarity.COMMON -> play(R.raw.jingle_common, 0.8f, what = "jingle common")
        Rarity.RARE -> play(R.raw.jingle_rare, 0.9f, what = "jingle rare")
        Rarity.SHINY -> play(R.raw.jingle_shiny, 1f, what = "jingle shiny")
    }

    /** The chime for star [index] (0, 1, 2 rising: G, A, B). */
    fun star(index: Int) = play(R.raw.chime_star, 0.75f, rate = STAR_RATES[index.coerceIn(0, STAR_RATES.size - 1)], what = "star ${index + 1}")

    fun munch() = play(R.raw.munch, 0.9f, what = "munch")
    fun crack() = play(R.raw.egg_crack, 0.9f, what = "egg crack")
    fun pop(rate: Float = 1f) = play(R.raw.pop, 0.7f, rate, what = "pop")

    /** Whole steps up from G5: each star a little brighter than the one before. */
    private val STAR_RATES = floatArrayOf(1f, 1.1225f, 1.2599f)
}

/** [WorldSounds], loaded for this screen's context. */
@Composable
fun rememberWorldSounds(): WorldSounds {
    val context = LocalContext.current
    return remember { WorldSounds.also { it.init(context) } }
}
