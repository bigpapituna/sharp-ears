package com.bigpapituna.sharpears.data

import android.content.Context
import android.util.Log
import androidx.compose.runtime.mutableIntStateOf
import androidx.core.content.edit
import com.bigpapituna.sharpears.TAG
import com.bigpapituna.sharpears.engine.arcade.ArcadeBook
import com.bigpapituna.sharpears.engine.arcade.ArcadeGame
import com.bigpapituna.sharpears.engine.arcade.ArcadeState
import com.bigpapituna.sharpears.engine.arcade.ArcadeUnlocks
import com.bigpapituna.sharpears.engine.arcade.RoundEnd
import com.bigpapituna.sharpears.engine.arcade.RoundResult

/**
 * A kid's arcade (engine/arcade ArcadeBook): one JSON string in the profile's "play" file (PlayStore.FILE, already in
 * ProgressFiles.NAMES, so backup, import and deleting a profile cover it), next to the quest and Pup says.
 */
class ArcadeStore(context: Context, val profileId: String) {
    private val prefs = context.getSharedPreferences(ProgressFiles.fileFor(PlayStore.FILE, profileId), Context.MODE_PRIVATE)

    /** Bumps on every change, so the hub recomposes. */
    val version = mutableIntStateOf(0)

    var state: ArcadeState = ArcadeState.decode(prefs.getString(KEY, null))
        private set

    private fun save(s: ArcadeState) {
        state = s
        prefs.edit { putString(KEY, s.encode()) }
        version.intValue++
    }

    /** The hub opened with the ledger at [sparkles]: the unlock count starts from the first open. Returns a game that just opened, once. */
    fun opened(sparkles: Int): ArcadeGame? {
        var s = ArcadeUnlocks.opened(state, sparkles)
        val open = ArcadeUnlocks.open(s, sparkles)
        val fresh = if (open > s.seen) ArcadeGame.entries.getOrNull(open - 1) else null
        if (open > s.seen) s = s.copy(seen = open)
        if (s != state) save(s)
        if (fresh != null) Log.d(TAG, "arcade: $profileId opened ${fresh.id} ($open open)")
        return fresh
    }

    /** The games open with the ledger at [sparkles]. */
    fun open(sparkles: Int): List<ArcadeGame> = ArcadeGame.entries.take(ArcadeUnlocks.open(state, sparkles))

    /** A round ended: kept, and what it did. */
    fun record(result: RoundResult): RoundEnd {
        val (s, end) = ArcadeBook.record(state, result)
        save(s)
        Log.d(TAG, "arcade: $profileId ${result.game.id} round ${s.of(result.game).rounds}: ${end.stars} stars, best ${s.of(result.game).best}, " +
            "level ${end.levelBefore} to ${end.levelAfter}" + (if (end.firstThree) ", sticker" else "") + (if (end.paintedNew) ", painted" else ""))
        return end
    }

    private companion object { const val KEY = "arcade" }
}
