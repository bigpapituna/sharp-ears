package com.bigpapituna.sharpears.engine.world

import com.bigpapituna.sharpears.engine.progress.LevelUp
import com.bigpapituna.sharpears.engine.progress.Progression

/**
 * What a finish screen reveals, as a queue (2.14, persona walks: a grown-up's lesson ended on a sticker, a rare sticker, a
 * wearable, an egg and a level-up card in thirty seconds, "a kids' game"). Pure, so RewardQueueTest keeps the order and the
 * words; ui/world/RewardsSheet.kt shows the queue.
 *
 * - A grown-up's finish says ONE reward line ([line]) and a small "See rewards (3)" chip ([chip]) that opens ONE sheet with the
 *   queue, a reward at a time, never stacked. Reaching the next lesson never waits on it.
 * - A kid's finish keeps the reveals (they love them), one at a time in this same order, with the big button always in view
 *   (ui/world/KidRewardStage.kt).
 */
sealed interface RewardItem {
    /** A sticker for the album: the lesson's or song's, a double, a rare or shiny one, or the one a mystery egg hatched. */
    data class Sticker(val drop: Drop) : RewardItem
    /** The stone's own sticker for the trail ([placedAt]: where the app stuck it for them on their first lesson, null: in the tray). */
    data class Trail(val sticker: LevelSticker, val placedAt: SceneSlot? = null) : RewardItem
    /** Something new to wear (an outfit's pieces together). */
    data class Wear(val items: List<WearItem>, val starter: Boolean = false) : RewardItem
    data object EggLaid : RewardItem
    data class House(val stage: Int) : RewardItem
    data class Record(val up: RecordUp) : RewardItem
    /** A new level (XP): the last reveal of a grown-up's finish. */
    data class Level(val up: LevelUp) : RewardItem
}

object RewardQueue {
    /**
     * The queue for [reward], in the order people meet them (as 2.5.0 had it): the stone's own sticker, the stickers
     * (the lesson's, then the egg's), something to wear, a new egg, the dog house, a record, and last a new level ([levelUp]).
     */
    fun of(reward: Reward?, levelUp: LevelUp? = null, starter: Boolean = false, placedAt: SceneSlot? = null): List<RewardItem> = buildList {
        if (reward != null) {
            reward.level?.let { add(RewardItem.Trail(it, placedAt)) }
            reward.drops.forEach { add(RewardItem.Sticker(it)) }
            if (reward.unlocked.isNotEmpty()) add(RewardItem.Wear(reward.unlocked, starter))
            if (reward.eggLaid) add(RewardItem.EggLaid)
            if (reward.houseUp) add(RewardItem.House(reward.houseAfter))
            reward.record?.let { add(RewardItem.Record(it)) }
        }
        levelUp?.let { add(RewardItem.Level(it)) }
    }

    /** The chip on a grown-up's finish: "See rewards (3)", null with nothing to see. */
    fun chip(items: List<RewardItem>): String? = if (items.isEmpty()) null else "See rewards (${items.size})"

    /** The sheet's title for the [i]th (0-based) of [n]: "Rewards, 2 of 3". */
    fun heading(i: Int, n: Int): String = if (n <= 1) "Reward" else "Rewards, ${i + 1} of $n"

    /** The sheet's button: the next reward, or Done on the last. */
    fun button(i: Int, n: Int): String = if (i < n - 1) "Next reward" else "Done"

    /**
     * The one reward line on a grown-up's finish: the best thing they got, in a few words, or the XP when there is nothing to
     * collect ("+40 XP, level 3 in 120."), or null when there is neither. [xpGained] is what this finish added; [xpTotal] the
     * total after it.
     */
    fun line(items: List<RewardItem>, xpGained: Int?, xpTotal: Int?): String? {
        val rare = items.filterIsInstance<RewardItem.Sticker>().any { it.drop.rarity.special }
        return when {
            rare -> "A rare sticker for your trail."
            items.any { it is RewardItem.Sticker || it is RewardItem.Trail } -> "A sticker for your trail."
            items.any { it is RewardItem.Wear } -> "Something new for the pup to wear."
            items.any { it is RewardItem.EggLaid } -> "A mystery egg for your trail."
            else -> xpLine(xpGained, xpTotal)
        }
    }

    /** "+40 XP, level 3 in 120." (the next level and the XP it still needs); null without a gain. */
    fun xpLine(gained: Int?, total: Int?): String? {
        if (gained == null || gained <= 0 || total == null) return null
        val level = Progression.levelFor(total)
        return if (level >= Progression.MAX_LEVEL) "+$gained XP, the top level." else "+$gained XP, level ${level + 1} in ${Progression.xpToNext(total)}."
    }
}

/**
 * A kid's first lesson (2.14, Mila: "nothing to dress the pup with on day 1"): the finish gives the first wearable, a free
 * starter piece ([STARTER], the bandana: no practice needed), and sticks the first sticker on the trail for them, in
 * [slot], so a first session ends with something they own and a pup who looks different. World.lessonComplete does it when
 * the world has never finished a lesson and the caller says it is a kid's.
 */
object Starter {
    /** The free wearable of a first lesson. It also belongs to the practice track (Rodeo Pup); the track skips what is open. */
    val ITEM: WearItem = WearItem.BANDANA

    /** The first sticker's place on the map: the first free slot of the garden scene that sits in the sky or on the lawn, never the trail. */
    fun slot(place: Place?): SceneSlot = Decor.of(place ?: Place.GARDEN).first()

    const val PLACED_LINE = "The pup stuck it on the map for you!"
    const val WEARING_LINE = "He is wearing it now!"
}
