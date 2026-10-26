package com.bigpapituna.sharpears.engine.progress

import com.bigpapituna.sharpears.engine.family.Level

/**
 * The level and XP a placement starts someone at (2.14). Sam said "a lot" and You still called him "Level 1, Newcomer, 0 XP"
 * after a week. The answer seeds a floor: XP is raised to the start of the level below (never lowered, never through the
 * level-up screen or the toasts; ProgressStore.seedFromPlacement writes it quietly, as catchUp does). Grown-ups and big kids
 * see levels; a little kid never does, so nothing is seeded for them.
 */
object PlacementLevel {
    /** The level each answer starts at: Newcomer, Note Spotter, Melody Maker, Steady Hands. */
    fun levelFor(level: Level): Int = when (level) {
        Level.NEVER -> 1
        Level.A_LITTLE -> 3
        Level.SOME -> 6
        Level.A_LOT -> 10
    }

    /** The XP that level begins at ([Progression.xpToReach]): 0, 283, 1118, 2700. */
    fun xpFor(level: Level): Int = Progression.xpToReach(levelFor(level))

    /** XP after seeding: the floor for [level], or [xp] when it is already past it. */
    fun seeded(xp: Int, level: Level): Int = maxOf(xp, xpFor(level))
}
