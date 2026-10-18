package com.bigpapituna.sharpears.engine.world

/**
 * The three places of the pup's world, in the order the Playground walks them: the garden (lessons 1 to 4), the
 * pond (5 to 8) and the concert hall (9 to 12). Each has ten stickers and its own page in the album.
 */
enum class Place(val id: String, val title: String, /** "in the pond": the end of "7 of 10 in the pond". */ val inThe: String) {
    GARDEN("garden", "Garden", "in the garden"),
    POND("pond", "Pond", "in the pond"),
    CONCERT("concert", "Concert hall", "in the concert hall");

    val stickers: List<Sticker> get() = Stickers.of(this)

    companion object {
        fun byId(id: String?): Place? = entries.firstOrNull { it.id == id }

        /** The place a Playground lesson belongs to by its number (1..12), or null outside the Playground. */
        fun ofPlaygroundLesson(n: Int): Place? = when (n) { in 1..4 -> GARDEN; in 5..8 -> POND; in 9..12 -> CONCERT; else -> null }
    }
}

/** How hard a sticker is to find: common (70% of rolls), rare (25%) or shiny (5%, drawn with a sparkle). */
enum class Rarity(val label: String) {
    COMMON("Common"), RARE("Rare"), SHINY("Shiny");

    /** One rarity up (what a mystery egg hatches into); shiny stays shiny. */
    fun up(): Rarity = if (this == SHINY) SHINY else entries[ordinal + 1]

    /** Rare or shiny: what the floor guarantees. */
    val special: Boolean get() = this != COMMON
}

/**
 * One sticker: its [place], its number there (1..10, as in art/GEMINI-BRIEF.md section C), what it shows and how rare
 * it is. Its picture is `res/drawable-nodpi/sticker_<place>_<n>.webp` ([resName]); placeholder shapes from
 * scripts/gen_stickers.py until the painted ones replace them under the same names. [animal]s also take a seat in
 * the concert audience once collected.
 */
data class Sticker(val place: Place, val n: Int, val name: String, val rarity: Rarity, val animal: Boolean = false) {
    val id: String get() = "${place.id}-$n"
    val resName: String get() = "sticker_${place.id}_$n"
}

/** The 30 stickers: per place six common, three rare and one shiny. */
object Stickers {
    private fun s(p: Place, n: Int, name: String, r: Rarity = Rarity.COMMON, animal: Boolean = false) = Sticker(p, n, name, r, animal)
    private val G = Place.GARDEN
    private val P = Place.POND
    private val C = Place.CONCERT

    val ALL: List<Sticker> = listOf(
        s(G, 1, "Red tulip"),
        s(G, 2, "Smiling sun"),
        s(G, 3, "Blue bird", Rarity.RARE, animal = true),
        s(G, 4, "Ladybird", animal = true),
        s(G, 5, "Watering can"),
        s(G, 6, "Snail", animal = true),
        s(G, 7, "Dog house", Rarity.RARE),
        s(G, 8, "Wooden fence"),
        s(G, 9, "Butterfly", Rarity.RARE, animal = true),
        s(G, 10, "Rainbow", Rarity.SHINY),
        s(P, 1, "Green frog", animal = true),
        s(P, 2, "Duckling", animal = true),
        s(P, 3, "Goldfish", Rarity.SHINY, animal = true),
        s(P, 4, "Dragonfly", Rarity.RARE, animal = true),
        s(P, 5, "Paper boat"),
        s(P, 6, "Turtle", Rarity.RARE, animal = true),
        s(P, 7, "Water lily"),
        s(P, 8, "Fishing rod"),
        s(P, 9, "Little bridge", Rarity.RARE),
        s(P, 10, "Rain cloud"),
        s(C, 1, "Gold star"),
        s(C, 2, "Trophy", Rarity.RARE),
        s(C, 3, "Red curtain"),
        s(C, 4, "Microphone"),
        s(C, 5, "Drum"),
        s(C, 6, "Grand piano", Rarity.RARE),
        s(C, 7, "Spotlight", Rarity.RARE),
        s(C, 8, "Flowers"),
        s(C, 9, "Ticket"),
        s(C, 10, "Fireworks", Rarity.SHINY),
    )

    private val byId = ALL.associateBy { it.id }

    fun byId(id: String?): Sticker? = id?.let(byId::get)
    fun of(place: Place): List<Sticker> = ALL.filter { it.place == place }
    fun of(place: Place, rarity: Rarity): List<Sticker> = ALL.filter { it.place == place && it.rarity == rarity }
}
