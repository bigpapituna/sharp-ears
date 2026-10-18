package com.bigpapituna.sharpears.engine.world

/** Where on the pup a thing goes; one thing per place. */
enum class WearSlot { HEAD, EYES, NECK }

/**
 * Things to dress the pup in, drawn over his sitting base pose. Each unlocks at a number of different stickers
 * collected ([unlockAt]); one per [slot] at a time.
 */
enum class WearItem(val id: String, val label: String, val slot: WearSlot, val unlockAt: Int) {
    BOW_TIE("bowtie", "Bow tie", WearSlot.NECK, 1),
    SUNGLASSES("sunglasses", "Sunglasses", WearSlot.EYES, 3),
    PARTY_HAT("partyhat", "Party hat", WearSlot.HEAD, 6),
    SCARF("scarf", "Scarf", WearSlot.NECK, 10),
    HEADPHONES("headphones", "Headphones", WearSlot.HEAD, 15),
    TOP_HAT("tophat", "Top hat", WearSlot.HEAD, 22);

    companion object {
        fun byId(id: String?): WearItem? = entries.firstOrNull { it.id == id }
    }
}

/** What the pup wears now: at most one item per slot. */
data class Outfit(val items: Set<WearItem> = emptySet()) {
    /** Puts [item] on (replacing whatever was in its slot), or takes it off when it is already worn. */
    fun toggle(item: WearItem): Outfit =
        if (item in items) Outfit(items - item) else Outfit(items.filter { it.slot != item.slot }.toSet() + item)

    fun inSlot(slot: WearSlot): WearItem? = items.firstOrNull { it.slot == slot }
    val isEmpty: Boolean get() = items.isEmpty()

    companion object { val NONE = Outfit() }
}

object Dress {
    /** Items unlocked with [unique] different stickers. */
    fun unlocked(unique: Int): List<WearItem> = WearItem.entries.filter { unique >= it.unlockAt }

    /** Items a step from [before] to [after] different stickers unlocks. */
    fun newlyUnlocked(before: Int, after: Int): List<WearItem> = WearItem.entries.filter { it.unlockAt in (before + 1)..after }

    /** The next item still locked and how many more stickers it needs, or null when all are open. */
    fun next(unique: Int): Pair<WearItem, Int>? = WearItem.entries.firstOrNull { unique < it.unlockAt }?.let { it to it.unlockAt - unique }

    /** [outfit] without anything not unlocked at [unique] (an import or a reset can lower the count). */
    fun allowed(outfit: Outfit, unique: Int): Outfit = Outfit(outfit.items.filter { unique >= it.unlockAt }.toSet())
}
