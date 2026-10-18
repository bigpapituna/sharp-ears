package com.bigpapituna.sharpears.engine.world

import java.time.LocalDate

/**
 * A finished Pup says game (play team): who played and the longest pattern they copied. The play team reports one
 * per game; the world keeps each profile's best of the day, and the crown of the day goes to the best among profiles.
 */
data class PupSaysResult(val profileId: String, val best: Int, val day: LocalDate = LocalDate.now(), val atMillis: Long = System.currentTimeMillis())

/**
 * The crown of the day: the profile with the longest Pup says pattern today. A tie goes to whoever got there first,
 * so nobody loses a crown by someone matching it. Nobody wears it for a best of zero.
 */
object Crown {
    fun of(results: List<PupSaysResult>, day: LocalDate): String? =
        results.filter { it.day == day && it.best > 0 }
            .sortedWith(compareByDescending<PupSaysResult> { it.best }.thenBy { it.atMillis })
            .firstOrNull()?.profileId
}
