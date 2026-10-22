package com.bigpapituna.sharpears.engine.world

/**
 * Something a kid can stick on the map (2.5): a level sticker earned on a stone, or a bonus sticker from the drops
 * and the egg. [key] is what placements and the tray save; [resName] its picture in res/drawable-nodpi.
 */
sealed interface Decal {
    val key: String
    val name: String
    val resName: String

    companion object {
        /** The decal saved as [key], or null for a key from a newer version. */
        fun byKey(key: String?): Decal? = when {
            key == null -> null
            key.startsWith(LevelSticker.PREFIX) -> LevelStickers.byLesson(key.removePrefix(LevelSticker.PREFIX))
            else -> Stickers.byId(key)
        }
    }
}

/**
 * A stone's own sticker (2.5, Roy: "stickers that they earn as they complete the trail"): one per Playground lesson and
 * per Basics lesson on the big kids' road, themed to that lesson, earned the first time its stone is finished (any
 * stars), shown as a silhouette on the stone until then. [page] is its Playground page id ("garden" ... "fair") or
 * "road" for Basics; [line] is what the pup says when it is earned. Pictures: sticker_lv_<lesson id>.webp from
 * scripts/gen_stickers.py --levels, prompts in art/GEMINI-BRIEF.md section C2.
 */
data class LevelSticker(val lessonId: String, val page: String, override val name: String, val line: String) : Decal {
    override val key: String get() = PREFIX + lessonId
    override val resName: String get() = "sticker_lv_" + lessonId.replace('-', '_')

    companion object { const val PREFIX = "lv:" }
}

/** The level stickers, page by page in stone order (the album shows them so). */
object LevelStickers {
    /** The page id Basics' stickers are filed under (the big kids' road). */
    const val ROAD = "road"
    /** 2.7: the arcade's games, a sticker each on its first three-star round (engine/arcade); filed as "arcade-<game id>". */
    const val ARCADE = "arcade"

    /** The arcade sticker's id for a game (engine/arcade ArcadeGame.id). */
    fun arcade(gameId: String) = "arcade-$gameId"

    private fun page(id: String, vararg s: Triple<String, String, String>) = s.map { (lesson, name, line) -> LevelSticker(lesson, id, name, line) }
    private fun t(lesson: String, name: String, line: String) = Triple(lesson, name, line)

    val ALL: List<LevelSticker> = page("garden",
        t("playground-1", "See-saw", "Up and down we go!"),
        t("playground-2", "Black keys", "Two black, three black!"),
        t("playground-3", "Red cat", "C is for cat!"),
        t("playground-4", "Paw steps", "Step, step, step!"),
        t("playground-5", "Hot cross bun", "Yum, a hot cross bun!"),
        t("playground-6", "Echo parrot", "Copy me, copy me!"),
        t("playground-7", "Drumsticks", "Boom, boom, boom!"),
        t("playground-8", "Little lamb", "Baa! A little lamb."),
        t("playground-9", "Left mitten", "A cosy mitten for my left paw."),
        t("playground-10", "Goat", "G is for goat!"),
        t("playground-11", "Feather", "Soft as a feather."),
        t("playground-12", "Garden medal", "You played a concert!"),
    ) + page("pond",
        t("pond-1", "Twinkle star", "Twinkle, twinkle!"),
        t("pond-2", "Rowing boat", "Row, row, row!"),
        t("pond-3", "Singing frog", "Ribbit, la la la!"),
        t("pond-4", "Lily pad drum", "Pitter patter!"),
        t("pond-5", "Music notes", "Ta, ti ti!"),
        t("pond-6", "Farm cow", "Moo! Old MacDonald had a cow."),
        t("pond-7", "Stepping stones", "Hop, hop, hop!"),
        t("pond-8", "High five", "Both paws together!"),
        t("pond-9", "Triple scoop", "Three at once, yum!"),
        t("pond-10", "London Bridge", "Over the bridge we go!"),
        t("pond-11", "Wishing wand", "Make a wish!"),
        t("pond-12", "Pond medal", "The frogs are clapping!"),
    ) + page("hall",
        t("hall-1", "Letter blocks", "C, D, E, F, G!"),
        t("hall-2", "Happy note", "A happy tune!"),
        t("hall-3", "Little sailboat", "Sail away!"),
        t("hall-4", "Feather cap", "A feather in my cap!"),
        t("hall-5", "Hand print", "Five fingers, two hands!"),
        t("hall-6", "Little mouse", "Squeak, quiet as a mouse."),
        t("hall-7", "Trumpet", "Toot, toot!"),
        t("hall-8", "Pillow", "A rest is a little nap."),
        t("hall-9", "Xylophone", "Ding, dong, ding!"),
        t("hall-10", "Two hearts", "Hands together, hearts together."),
        t("hall-11", "Song book", "Pick your songs!"),
        t("hall-12", "Recital rose", "Take a bow!"),
    ) + page("workshop",
        t("workshop-1", "Piano bench", "Sit up tall!"),
        t("workshop-2", "Bubbles", "Pop! A bubble hand."),
        t("workshop-3", "Finger puppet", "Wiggle your fingers!"),
        t("workshop-4", "Little house", "Home sweet home."),
        t("workshop-5", "Mailbox", "A letter for the left hand!"),
        t("workshop-6", "Thumbs up", "Thumbs up!"),
        t("workshop-7", "Toy car", "Vroom, over the bridge!"),
        t("workshop-8", "Elephant", "Heavy like an elephant."),
        t("workshop-9", "Bouncy ball", "Boing!"),
        t("workshop-10", "Sneaker", "Press the pedal with your foot."),
        t("workshop-11", "Tin can phone", "Hello? Your turn!"),
        t("workshop-12", "Workshop medal", "Built with your own hands!"),
    ) + page("treehouse",
        t("treehouse-1", "Caterpillar", "Inch, inch, inch!"),
        t("treehouse-2", "Marching ant", "Left, right, left!"),
        t("treehouse-3", "Hedgehog", "Tuck and roll!"),
        t("treehouse-4", "Little weights", "Strong little fingers!"),
        t("treehouse-5", "Hopscotch", "Skip, hop, skip!"),
        t("treehouse-6", "Grapes", "G for grapes!"),
        t("treehouse-7", "Fox", "F for fox!"),
        t("treehouse-8", "Mirror", "Same tune, other hand!"),
        t("treehouse-9", "Monkey", "Monkey see, monkey do!"),
        t("treehouse-10", "Echo bat", "Echo, echo!"),
        t("treehouse-11", "Magnifying glass", "Which finger? Let's look!"),
        t("treehouse-12", "Treehouse medal", "What a concert in the trees!"),
    ) + page("nightsky",
        t("night-1", "Ticking clock", "Tick, tock, a steady beat."),
        t("night-2", "Maracas", "Shake, shake!"),
        t("night-3", "Sleepy bear", "Shh, a quiet beat."),
        t("night-4", "Metronome", "Tick, tick, tick!"),
        t("night-5", "Rocket", "Up, up and away!"),
        t("night-6", "Odd socks", "Same or different?"),
        t("night-7", "Thunder cloud", "Boom, like thunder!"),
        t("night-8", "Racing rabbit", "Zoom like a rabbit!"),
        t("night-9", "Long snake", "Sss, a long note."),
        t("night-10", "Umbrella", "Sunny or rainy?"),
        t("night-11", "Black cat", "Black keys all the way!"),
        t("night-12", "Star medal", "The stars are cheering!"),
    ) + page("fair",
        t("fair-1", "Balloon bunch", "Pop, pop, pop!"),
        t("fair-2", "Pinwheel", "Round and round!"),
        t("fair-3", "Strongman bell", "Ding! Ring the bell!"),
        t("fair-4", "Camera", "Say cheese!"),
    ) + page(ROAD,
        t("sit", "Cushion", "Sit like a big kid."),
        t("find-c", "Treasure map", "Find every C!"),
        t("fingers-right", "Glove", "Every finger has a number."),
        t("fingers-left", "Mittens", "Left hand, right hand."),
        t("beat", "Baton", "Keep the beat!"),
        t("white-keys", "Alphabet train", "A, B, C, D, E, F, G!"),
    ) + page(ARCADE,
        t("arcade-balloons", "Balloon pop", "Pop, bang, pop!"),
        t("arcade-garden", "Watering can", "Grow, flowers, grow!"),
        t("arcade-feed", "Doggy bowl", "Yum, yum, yum!"),
        t("arcade-tower", "Toy blocks", "Up to the clouds!"),
        t("arcade-moles", "Little mole", "Boop!"),
        t("arcade-frogs", "Leaping frog", "Hop, hop, hop!"),
        t("arcade-fireworks", "Sky rocket", "Whoosh, bang!"),
        t("arcade-painter", "Paint palette", "A picture with a song!"),
        t("arcade-bridge", "Rainbow", "Over the rainbow bridge!"),
    )

    private val byLesson = ALL.associateBy { it.lessonId }

    fun byLesson(lessonId: String?): LevelSticker? = lessonId?.let(byLesson::get)
    fun of(page: String): List<LevelSticker> = ALL.filter { it.page == page }

    /** The page ids in album order: the Playground's pages, then the road. */
    val PAGES: List<String> = ALL.map { it.page }.distinct()
}
