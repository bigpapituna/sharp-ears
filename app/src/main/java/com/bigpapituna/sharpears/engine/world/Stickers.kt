package com.bigpapituna.sharpears.engine.world

/**
 * The places of the pup's world, in the order the Playground walks them (2.2: one per page, the garden, the pond and the
 * concert hall; a lesson's place is its page's, engine/kid/KidPages.dropPlace; 2.3: the workshop, the treehouse, the
 * night sky and the fair). Each has ten stickers and its own page in the album.
 */
enum class Place(val id: String, val title: String, /** "in the pond": the end of "7 of 10 in the pond". */ val inThe: String) {
    GARDEN("garden", "Garden", "in the garden"),
    POND("pond", "Pond", "in the pond"),
    CONCERT("concert", "Concert hall", "in the concert hall"),
    WORKSHOP("workshop", "Workshop", "in the workshop"),
    TREEHOUSE("treehouse", "Treehouse", "in the treehouse"),
    NIGHT_SKY("nightsky", "Night sky", "in the night sky"),
    FAIR("fair", "Fair", "at the fair");

    val stickers: List<Sticker> get() = Stickers.of(this)

    companion object {
        fun byId(id: String?): Place? = entries.firstOrNull { it.id == id }
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
data class Sticker(val place: Place, val n: Int, override val name: String, val rarity: Rarity, val animal: Boolean = false) : Decal {
    val id: String get() = "${place.id}-$n"
    override val key: String get() = id
    override val resName: String get() = "sticker_${place.id}_$n"
}

/**
 * The 70 stickers: per place six common, three rare and one shiny. 2.3's places have creatures too (the squirrel, the
 * owls, the sleeping cat), but they are not [Sticker.animal]: the concert hall's audience is the garden's and the pond's
 * animals, nine seats drawn in its scene.
 */
object Stickers {
    private fun s(p: Place, n: Int, name: String, r: Rarity = Rarity.COMMON, animal: Boolean = false) = Sticker(p, n, name, r, animal)
    private val G = Place.GARDEN
    private val P = Place.POND
    private val C = Place.CONCERT
    private val W = Place.WORKSHOP
    private val T = Place.TREEHOUSE
    private val N = Place.NIGHT_SKY
    private val F = Place.FAIR

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
        s(W, 1, "Hammer"),
        s(W, 2, "Saw"),
        s(W, 3, "Tuning fork", Rarity.SHINY),
        s(W, 4, "Glue pot"),
        s(W, 5, "Ruler"),
        s(W, 6, "Piano key", Rarity.RARE),
        s(W, 7, "Screwdriver"),
        s(W, 8, "Paintbrush"),
        s(W, 9, "Toolbox", Rarity.RARE),
        s(W, 10, "Work lamp", Rarity.RARE),
        s(T, 1, "Acorn"),
        s(T, 2, "Squirrel", Rarity.RARE),
        s(T, 3, "Owl", Rarity.RARE),
        s(T, 4, "Rope swing"),
        s(T, 5, "Leaf"),
        s(T, 6, "Bird's nest", Rarity.RARE),
        s(T, 7, "Ladder"),
        s(T, 8, "Lantern"),
        s(T, 9, "Pinecone"),
        s(T, 10, "Kite", Rarity.SHINY),
        s(N, 1, "Moon"),
        s(N, 2, "Comet", Rarity.RARE),
        s(N, 3, "Night owl"),
        s(N, 4, "Firefly"),
        s(N, 5, "Telescope", Rarity.RARE),
        s(N, 6, "Planet", Rarity.RARE),
        s(N, 7, "Shooting star", Rarity.SHINY),
        s(N, 8, "Sleepy cloud"),
        s(N, 9, "Paper lantern"),
        s(N, 10, "Sleeping cat"),
        s(F, 1, "Balloon"),
        s(F, 2, "Fair ticket"),
        s(F, 3, "Popcorn"),
        s(F, 4, "Carousel horse", Rarity.RARE),
        s(F, 5, "Candy floss"),
        s(F, 6, "Ferris wheel", Rarity.SHINY),
        s(F, 7, "Prize ribbon"),
        s(F, 8, "Teddy bear", Rarity.RARE),
        s(F, 9, "Juggling balls"),
        s(F, 10, "Rosette", Rarity.RARE),
    )

    private val byId = ALL.associateBy { it.id }

    fun byId(id: String?): Sticker? = id?.let(byId::get)
    fun of(place: Place): List<Sticker> = ALL.filter { it.place == place }
    fun of(place: Place, rarity: Rarity): List<Sticker> = ALL.filter { it.place == place && it.rarity == rarity }
}
