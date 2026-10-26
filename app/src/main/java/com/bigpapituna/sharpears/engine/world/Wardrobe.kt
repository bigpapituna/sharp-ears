package com.bigpapituna.sharpears.engine.world

import java.time.LocalDate

/*
 * The wardrobe (2.5): things to dress the pup in, earned by practising. Pure; data/WorldStore.kt keeps the state and
 * ui/world/Outfit.kt draws every item over the sitting base pose.
 *
 * How an item is earned (the practice ledger, [Ledger]): everything a kid does at the piano turns into sparkles.
 *
 * | What                                          | Sparkles                         | Where it comes from                        |
 * |-----------------------------------------------|----------------------------------|--------------------------------------------|
 * | a minute practised                            | 1 ([Sparkles.PER_MINUTE])        | ProgressStore.practiceLog() (seconds a day) |
 * | a day practised (a minute or more)            | 5 ([Sparkles.PER_DAY])           | the same log                               |
 * | a lesson's first three stars                  | 4 ([Sparkles.PER_THREE_STARS])   | KidStore.allStars() (best stars a lesson)  |
 * | a song played to its end (five a day count)   | 2 ([Sparkles.PER_SONG])          | WorldStore.songComplete                    |
 * | a page's finale                               | 20 ([Sparkles.PER_FINALE])       | KidStore.finales() and the page just ended |
 *
 * A typical kid (five ten-minute sessions a week, two lessons and a song a session, half of them three stars) makes
 * about 21 sparkles a session. Common items come in order along the practice track ([PRACTICE_AT], the sparkles
 * each needs in all): the first after a session, then a gap that grows from about half a session to about five
 * sessions, so items two to four land inside the first two weeks and later ones every 40 to 60 minutes of practice,
 * slowing a little after the first month. Never more than one practice item a day (a long Saturday gives one; the
 * next comes on the next day the kid plays). Rare items come on milestones (each page's finale, seven days in a row,
 * ten lessons with three stars, ...), shiny ones on big milestones (three pages, the big kids' road, thirty days).
 * Costumes come as a set (the suit and its hat in one go). Nothing is ever bought, and nothing is ever lost: the
 * ledger only grows, and an item once open stays open (a reset, a fresh start, an import that lowers a count).
 * WardrobeTest simulates the typical kid and prints the timeline.
 */

/** Where on the pup a thing goes; one thing per slot, so four can be worn together. */
enum class WearSlot(val label: String) {
    HEAD("Head"), FACE("Face"), BODY("Body"), BACK("Feet and back");
}

/** How an item is earned: along the practice track, or by a milestone. */
sealed interface Earn {
    /** The [n]th item of the practice track (1-based), at [Wardrobe.PRACTICE_AT] sparkles. */
    data class Practice(val n: Int) : Earn
    /** A page's finale ([pageId], engine/playground/Pages.kt). */
    data class Finale(val pageId: String, val title: String) : Earn
    /** [days] days in a row with practice. */
    data class Streak(val days: Int) : Earn
    /** [lessons] lessons with three stars. */
    data class ThreeStars(val lessons: Int) : Earn
    /** [songs] songs played to their end. */
    data class Songs(val songs: Int) : Earn
    /** [days] days practised in all. */
    data class Days(val days: Int) : Earn
    /** [pages] page finales in all. */
    data class Pages(val pages: Int) : Earn
    /** Every Playground page walked: the big kids' road opens. */
    data object Road : Earn
}

/**
 * One thing to wear: its [id] (saved), what it is ([label]), the name a kid loves ([nickname]), its [slot], its [rarity]
 * (the tile's look: common plain, rare blue, shiny with a sparkle), how it is earned ([earn]; a costume's pieces share
 * one) and what the pup says when it first goes on ([line]). The first six are the 2.1 wardrobe, still the first six
 * practice items.
 */
enum class WearItem(val id: String, val label: String, val nickname: String, val slot: WearSlot, val rarity: Rarity, val earn: Earn, val line: String) {
    // The 2.1 six, the practice track's first six.
    BOW_TIE("bowtie", "Bow tie", "Dapper Pup", WearSlot.BODY, Rarity.COMMON, Earn.Practice(1), "Very smart."),
    SUNGLASSES("sunglasses", "Round sunglasses", "Too Cool Pup", WearSlot.FACE, Rarity.COMMON, Earn.Practice(2), "Too cool."),
    PARTY_HAT("partyhat", "Party hat", "Party Pup", WearSlot.HEAD, Rarity.COMMON, Earn.Practice(3), "Party time!"),
    SCARF("scarf", "Rainbow scarf", "Cosy Rainbow", WearSlot.BODY, Rarity.COMMON, Earn.Practice(4), "So cosy."),
    HEADPHONES("headphones", "Headphones", "DJ Pup", WearSlot.HEAD, Rarity.COMMON, Earn.Practice(5), "I can hear the music."),
    TOP_HAT("tophat", "Top hat", "Fancy Pup", WearSlot.HEAD, Rarity.COMMON, Earn.Practice(6), "Ready for the concert hall."),

    // The practice track goes on.
    BOOTS("boots", "Rain boots", "Puddle Jumper", WearSlot.BACK, Rarity.COMMON, Earn.Practice(7), "Splish, splash!"),
    BEANIE("beanie", "Bobble beanie", "Snow Day Pup", WearSlot.HEAD, Rarity.COMMON, Earn.Practice(8), "Warm ears, happy pup."),
    FRECKLES("freckles", "Freckles", "Freckle Face", WearSlot.FACE, Rarity.COMMON, Earn.Practice(9), "Look at my freckles!"),
    BAND_PINK("band-pink", "Pink headband", "Pink Power", WearSlot.HEAD, Rarity.COMMON, Earn.Practice(10), "Pink is my power colour."),
    HOODIE("hoodie", "Hoodie", "Snuggle Pup", WearSlot.BODY, Rarity.COMMON, Earn.Practice(11), "Snuggly!"),
    STAR_GLASSES("starglasses", "Star glasses", "Star Eyes", WearSlot.FACE, Rarity.COMMON, Earn.Practice(12), "I see stars!"),
    BACKPACK("backpack", "Backpack", "Adventure Pup", WearSlot.BACK, Rarity.COMMON, Earn.Practice(13), "Off on an adventure!"),
    BAND_BLUE("band-blue", "Blue headband", "Blue Sky", WearSlot.HEAD, Rarity.COMMON, Earn.Practice(14), "Blue like the sky."),
    CHEF_HAT("chefhat", "Chef's hat", "Chef Pupcake", WearSlot.HEAD, Rarity.COMMON, Earn.Practice(15), "Something smells yummy!"),
    RAINCOAT("raincoat", "Raincoat", "Splashy Pup", WearSlot.BODY, Rarity.COMMON, Earn.Practice(16), "Bring on the rain!"),
    SKATES("skates", "Roller skates", "Zoom Zoom", WearSlot.BACK, Rarity.COMMON, Earn.Practice(17), "Wheee, look how fast!"),
    BEE("bee", "Bee costume", "Buzzy Bee", WearSlot.BODY, Rarity.COMMON, Earn.Practice(18), "Buzz, buzz!"),
    ANTENNAE("antennae", "Bee antennae", "Buzzy Bee", WearSlot.HEAD, Rarity.COMMON, Earn.Practice(18), "Boing, boing!"),
    BAND_GREEN("band-green", "Green headband", "Leafy Green", WearSlot.HEAD, Rarity.COMMON, Earn.Practice(19), "Green like the garden."),
    TUTU("tutu", "Tutu", "Twirly Pup", WearSlot.BODY, Rarity.COMMON, Earn.Practice(20), "Twirl, twirl!"),
    SLIPPERS("slippers", "Ballet slippers", "Twirly Pup", WearSlot.BACK, Rarity.COMMON, Earn.Practice(20), "Up on my tiptoes."),
    HEART_GLASSES("heartglasses", "Heart glasses", "Lovely Eyes", WearSlot.FACE, Rarity.COMMON, Earn.Practice(21), "I love music!"),
    COWBOY_HAT("cowboyhat", "Cowboy hat", "Rodeo Pup", WearSlot.HEAD, Rarity.COMMON, Earn.Practice(22), "Yee haw!"),
    BANDANA("bandana", "Bandana", "Rodeo Pup", WearSlot.BODY, Rarity.COMMON, Earn.Practice(22), "Ready to ride!"),
    BAND_YELLOW("band-yellow", "Yellow headband", "Sunny Side", WearSlot.HEAD, Rarity.COMMON, Earn.Practice(23), "Sunny and bright."),
    MONOCLE("monocle", "Monocle", "Professor Pup", WearSlot.FACE, Rarity.COMMON, Earn.Practice(24), "Most interesting."),
    SNOWMAN("snowman", "Snowman suit", "Frosty Pup", WearSlot.BODY, Rarity.COMMON, Earn.Practice(25), "Brrr, I'm a snowman!"),
    CARROT_NOSE("carrotnose", "Carrot nose", "Frosty Pup", WearSlot.FACE, Rarity.COMMON, Earn.Practice(25), "A carrot for a nose!"),
    BAND_PURPLE("band-purple", "Purple headband", "Purple Magic", WearSlot.HEAD, Rarity.COMMON, Earn.Practice(26), "Purple is magic."),
    PUMPKIN("pumpkin", "Pumpkin suit", "Pumpkin Pup", WearSlot.BODY, Rarity.COMMON, Earn.Practice(27), "I'm a little pumpkin!"),
    DINO("dino", "Dinosaur onesie", "Dino Pup", WearSlot.BODY, Rarity.COMMON, Earn.Practice(28), "Rawr! I'm a dinosaur."),
    DINO_CAP("dinocap", "Dinosaur hood", "Dino Pup", WearSlot.HEAD, Rarity.COMMON, Earn.Practice(28), "Spiky!"),
    CAPE_BLUE("cape-blue", "Blue cape", "Midnight Cape", WearSlot.BODY, Rarity.COMMON, Earn.Practice(29), "Swoosh!"),
    WHISKERS("whiskers", "Whiskers", "Kitty Pup", WearSlot.FACE, Rarity.COMMON, Earn.Practice(30), "Meow? I mean woof!"),

    // Rare: a page's finale, and the milestones on the way.
    FLOWER_CROWN("flowercrown", "Flower crown", "Flower Fairy", WearSlot.HEAD, Rarity.RARE, Earn.Finale("garden", "the garden"), "I smell like flowers."),
    MERMAID("mermaid", "Mermaid tail", "Merpup", WearSlot.BODY, Rarity.RARE, Earn.Finale("pond", "the pond"), "Splash! I can swim with the fish."),
    BALLERINA("ballerina", "Ballerina dress", "Prima Pupperina", WearSlot.BODY, Rarity.RARE, Earn.Finale("hall", "the concert hall"), "Dancing on the big stage!"),
    GUITAR("guitar", "Tiny guitar", "Rock Star Pup", WearSlot.BACK, Rarity.RARE, Earn.Finale("workshop", "the workshop"), "Let's rock!"),
    WINGS("wings", "Fairy wings", "Fairy Pup", WearSlot.BACK, Rarity.RARE, Earn.Finale("treehouse", "the treehouse"), "Flutter, flutter!"),
    ASTRONAUT("astronaut", "Astronaut suit", "Space Pup", WearSlot.BODY, Rarity.RARE, Earn.Finale("nightsky", "the night sky"), "To the moon!"),
    SPACE_HELMET("spacehelmet", "Space helmet", "Space Pup", WearSlot.HEAD, Rarity.RARE, Earn.Finale("nightsky", "the night sky"), "Ground control to pup!"),
    ROCKET_PACK("rocketpack", "Rocket pack", "Space Pup", WearSlot.BACK, Rarity.RARE, Earn.Finale("nightsky", "the night sky"), "Blast off!"),
    LION("lion", "Lion mane", "Brave Lion", WearSlot.BODY, Rarity.RARE, Earn.Finale("fair", "the fair"), "Roar! I'm a big brave lion."),
    HERO_SUIT("herosuit", "Superhero suit", "Super Pup", WearSlot.BODY, Rarity.RARE, Earn.Streak(7), "Super Pup to the rescue!"),
    HERO_MASK("heromask", "Superhero mask", "Super Pup", WearSlot.FACE, Rarity.RARE, Earn.Streak(7), "Nobody knows it's me."),
    CAPE_RED("cape-red", "Red cape", "Super Cape", WearSlot.BODY, Rarity.RARE, Earn.Streak(14), "Up, up and away!"),
    KNIGHT("knight", "Knight armour", "Sir Pup", WearSlot.BODY, Rarity.RARE, Earn.ThreeStars(10), "Sir Pup, at your service."),
    KNIGHT_HELMET("knighthelmet", "Knight's helmet", "Sir Pup", WearSlot.HEAD, Rarity.RARE, Earn.ThreeStars(10), "Clank, clank!"),
    PRINCESS("princess", "Princess dress", "Princess Pup", WearSlot.BODY, Rarity.RARE, Earn.ThreeStars(25), "A dress for a royal ball."),
    PIRATE_HAT("piratehat", "Pirate hat", "Captain Pup", WearSlot.HEAD, Rarity.RARE, Earn.Songs(20), "Ahoy, me hearties!"),
    EYE_PATCH("eyepatch", "Eye patch", "Captain Pup", WearSlot.FACE, Rarity.RARE, Earn.Songs(20), "Arr!"),
    DOCTOR("doctor", "Doctor's coat", "Doctor Pup", WearSlot.BODY, Rarity.RARE, Earn.Days(40), "Say ahh!"),
    HEAD_MIRROR("headmirror", "Doctor's mirror", "Doctor Pup", WearSlot.HEAD, Rarity.RARE, Earn.Days(40), "Let me have a look."),
    FIREFIGHTER("firefighter", "Firefighter coat", "Fire Chief Pup", WearSlot.BODY, Rarity.RARE, Earn.Songs(60), "Nee naw, here I come!"),
    FIRE_HELMET("firehelmet", "Firefighter helmet", "Fire Chief Pup", WearSlot.HEAD, Rarity.RARE, Earn.Songs(60), "Safety first!"),

    // Shiny: the big milestones.
    HALO("halo", "Halo", "Little Angel", WearSlot.HEAD, Rarity.SHINY, Earn.Pages(3), "A golden halo, just for me."),
    CROWN("crown", "Gold crown", "King of the Road", WearSlot.HEAD, Rarity.SHINY, Earn.Road, "Make way for the king of the road!"),
    WIZARD_HAT("wizardhat", "Wizard hat", "Star Wizard", WearSlot.HEAD, Rarity.SHINY, Earn.Days(30), "Abracadabra, music magic!"),
    RAINBOW_DRESS("rainbowdress", "Rainbow dress", "Rainbow Star", WearSlot.BODY, Rarity.SHINY, Earn.ThreeStars(50), "Every colour at once!");

    companion object {
        fun byId(id: String?): WearItem? = entries.firstOrNull { it.id == id }
    }
}

/** What the pup wears now: at most one item per slot. */
data class Outfit(val items: Set<WearItem> = emptySet()) {
    /** Puts [item] on (replacing whatever was in its slot), or takes it off when it is already worn. */
    fun toggle(item: WearItem): Outfit = if (item in items) Outfit(items - item) else with(item)

    /** [item] on, replacing whatever was in its slot. */
    fun with(item: WearItem): Outfit = Outfit(items.filter { it.slot != item.slot }.toSet() + item)

    fun inSlot(slot: WearSlot): WearItem? = items.firstOrNull { it.slot == slot }
    val isEmpty: Boolean get() = items.isEmpty()

    companion object { val NONE = Outfit() }
}

/**
 * The practice ledger: what a kid has done at the piano, only ever growing. [credited] is how many seconds of each
 * recent day are already counted (so syncing again from the same log adds nothing, and a day pruned from the log
 * keeps what it gave; days at or before [floor] were dropped from it and never count again); [days] the days with a minute or more; [streak] and [best] days in a row; [threeStars] the
 * lessons that ever had three stars; [finales] the pages walked; [songs] songs played to their end, [songSparkles]
 * the songs that counted toward sparkles (five a day).
 */
data class Ledger(
    val seconds: Long = 0,
    val credited: Map<LocalDate, Int> = emptyMap(),
    val days: Int = 0,
    val lastDay: LocalDate? = null,
    val streak: Int = 0,
    val best: Int = 0,
    val threeStars: Set<String> = emptySet(),
    val finales: Set<String> = emptySet(),
    val songs: Int = 0,
    val songSparkles: Int = 0,
    val songDay: LocalDate? = null,
    val songsThatDay: Int = 0,
    /** Days on or before this were credited and then dropped from [credited]: never counted again. */
    val floor: LocalDate? = null,
    /** 2.9: sparkles given as they are (Tomorrow's surprise, 25 at a time). */
    val bonus: Int = 0,
) {
    val minutes: Long get() = seconds / 60
    val sparkles: Int get() = Sparkles.of(this)

    /**
     * The ledger after the stores were read: [practice] seconds per day (ProgressStore.practiceLog), [threeStarLessons]
     * (KidStore.allStars at three) and [walked] pages (finales played, or a page whose last lesson was just finished).
     * Idempotent: the same log twice counts once; a day only adds what it grew by since the last sync.
     */
    fun sync(practice: Map<LocalDate, Int>, threeStarLessons: Set<String>, walked: Set<String>): Ledger {
        var l = this
        for ((day, secs) in practice.entries.sortedBy { it.key }) {
            if (l.floor != null && !day.isAfter(l.floor)) continue
            val before = l.credited[day] ?: 0
            if (secs <= before) continue
            val counts = before < Sparkles.DAY_SECONDS && secs >= Sparkles.DAY_SECONDS
            val newer = l.lastDay == null || day.isAfter(l.lastDay)
            val streak = when {
                !counts -> l.streak
                !newer -> l.streak                                   // an older day turning up late: counted, the run unchanged
                l.lastDay == day.minusDays(1) -> l.streak + 1
                else -> 1
            }
            l = l.copy(
                seconds = l.seconds + (secs - before),
                credited = l.credited + (day to secs),
                days = l.days + if (counts) 1 else 0,
                lastDay = if (counts && newer) day else l.lastDay,
                streak = streak,
                best = maxOf(l.best, streak),
            )
        }
        if (l.credited.size > KEEP) {
            val dropped = l.credited.keys.sortedDescending().drop(KEEP)
            l = l.copy(credited = l.credited - dropped.toSet(), floor = maxOf(l.floor ?: dropped.first(), dropped.first()))
        }
        return l.withSets(threeStarLessons, walked)
    }

    private fun withSets(stars: Set<String>, walked: Set<String>) = copy(threeStars = threeStars + stars, finales = finales + walked)

    /** A song played to its end on [day]: counted, and toward sparkles while the day has had fewer than [Sparkles.SONGS_A_DAY]. */
    fun song(day: LocalDate): Ledger {
        val today = if (songDay == day) songsThatDay else 0
        val counts = today < Sparkles.SONGS_A_DAY
        return copy(songs = songs + 1, songSparkles = songSparkles + if (counts) 1 else 0, songDay = day, songsThatDay = today + 1)
    }

    /** The days in a row up to [today]: the run so far when its last day is today or yesterday, else none. */
    fun streakOn(today: LocalDate): Int = if (lastDay != null && !lastDay.isBefore(today.minusDays(1))) streak else 0

    companion object {
        /**
         * Days of [credited] kept, the newest: more than the practice log keeps (ProgressStore 400 days counted, Prefs 60
         * dated). Older ones go below [floor] and are never counted again.
         */
        const val KEEP = 450
    }
}

/** Sparkles from the ledger, the rates in the table at the top of this file. */
object Sparkles {
    const val PER_MINUTE = 1
    const val PER_DAY = 5
    const val PER_THREE_STARS = 4
    const val PER_SONG = 2
    const val SONGS_A_DAY = 5
    const val PER_FINALE = 20
    /** A day counts once it has this much practice. */
    const val DAY_SECONDS = 60

    fun of(l: Ledger): Int = (l.minutes * PER_MINUTE).toInt() + l.days * PER_DAY + l.threeStars.size * PER_THREE_STARS +
        l.songSparkles * PER_SONG + l.finales.size * PER_FINALE + l.bonus
}

/** How close the next practice item is: a share 0..1 and words with no number. */
data class NextOutfit(val items: List<WearItem>, val share: Float) {
    /** "Almost there!" near the end, "On the way!" in the middle, "Just started!" at the start. */
    val words: String get() = when {
        share >= 0.75f -> "Almost there!"
        share >= 0.35f -> "On the way!"
        else -> "Just started!"
    }
}

/** The wardrobe's rules: what the ledger has earned, what is new, how far the next item is. */
object Wardrobe {
    /**
     * Sparkles each practice item needs in all (index n-1 for [Earn.Practice] n). Gaps: 12 for the first (the first
     * session), 30, 45 and 60 for the next three (inside two weeks), then 90, 100, 110 and 120 each after that (about
     * 40, 50, then 55 minutes of a typical kid's practice).
     */
    val PRACTICE_AT: List<Int> = run {
        val gaps = listOf(12, 30, 45, 60, 90, 100, 110) + List(40) { 120 }
        gaps.runningReduce { a, b -> a + b }
    }

    /** Items unlocked together, in the order a kid meets them: the practice track, then milestones. */
    val UNITS: List<List<WearItem>> = WearItem.entries.groupBy { it.earn }.values.toList()

    fun practiceCount(): Int = WearItem.entries.mapNotNull { (it.earn as? Earn.Practice)?.n }.max()

    /** True when [ledger] has reached milestone [e] (practice items are counted in [due]). */
    fun reached(e: Earn, l: Ledger): Boolean = when (e) {
        is Earn.Practice -> l.sparkles >= PRACTICE_AT[e.n - 1]
        is Earn.Finale -> e.pageId in l.finales
        is Earn.Streak -> l.best >= e.days
        is Earn.ThreeStars -> l.threeStars.size >= e.lessons
        is Earn.Songs -> l.songs >= e.songs
        is Earn.Days -> l.days >= e.days
        is Earn.Pages -> l.finales.size >= e.pages
        Earn.Road -> l.finales.containsAll(ROAD_PAGES)
    }

    /** The Playground pages whose finales open the big kids' road (engine/playground/Pages.kt, in order). */
    val ROAD_PAGES = setOf("garden", "pond", "hall", "workshop", "treehouse", "nightsky", "fair")

    /**
     * What opens now: every milestone reached, and the next practice item in order when its sparkles are in, unless a
     * practice item already opened today ([practiceDay], the day of the last one). Already [open] items are left out.
     */
    fun due(l: Ledger, open: Set<WearItem>, today: LocalDate, practiceDay: LocalDate?): List<WearItem> {
        val milestones = WearItem.entries.filter { it !in open && it.earn !is Earn.Practice && reached(it.earn, l) }
        val next = nextPractice(open)
        val practice = if (next == null || practiceDay == today || !reached(next.first().earn, l)) emptyList() else next
        return practice + milestones
    }

    /** The next practice set still closed, in track order, or null when the track is done. */
    fun nextPractice(open: Set<WearItem>): List<WearItem>? =
        (1..practiceCount()).asSequence().map { n -> WearItem.entries.filter { (it.earn as? Earn.Practice)?.n == n } }
            .firstOrNull { set -> set.any { it !in open } }

    /** How far the next practice item is (0..1 of the gap from the last one), or null when the track is done. */
    fun next(l: Ledger, open: Set<WearItem>): NextOutfit? {
        val set = nextPractice(open) ?: return null
        val n = (set.first().earn as Earn.Practice).n
        val to = PRACTICE_AT[n - 1]
        val from = if (n == 1) 0 else PRACTICE_AT[n - 2]
        return NextOutfit(set, ((l.sparkles - from).toFloat() / (to - from)).coerceIn(0f, 1f))
    }

    /** The 2.1 rule (1, 3, 6, 10, 15 and 22 different stickers), kept for old saves: what they had open stays open. */
    fun legacy(unique: Int): Set<WearItem> =
        listOf(1 to WearItem.BOW_TIE, 3 to WearItem.SUNGLASSES, 6 to WearItem.PARTY_HAT, 10 to WearItem.SCARF, 15 to WearItem.HEADPHONES, 22 to WearItem.TOP_HAT)
            .filter { unique >= it.first }.map { it.second }.toSet()

    /** How a locked item is earned, in a kid's words (no numbers). */
    fun how(item: WearItem): String = when (val e = item.earn) {
        is Earn.Practice -> "Keep playing to earn it."
        is Earn.Finale -> "Finish ${e.title} to earn it."
        is Earn.Streak -> "Play ${words(e.days)} days in a row to earn it."
        is Earn.ThreeStars -> "Get three stars on ${words(e.lessons)} games to earn it."
        is Earn.Songs -> "Play ${words(e.songs)} songs to the end to earn it."
        is Earn.Days -> "Play on ${words(e.days)} different days to earn it."
        is Earn.Pages -> "Finish ${words(e.pages)} pages of the map to earn it."
        Earn.Road -> "Walk all the way to the big kids' road to earn it."
    }

    private fun words(n: Int): String = when (n) {
        3 -> "three"; 7 -> "seven"; 10 -> "ten"; 14 -> "fourteen"; 15 -> "fifteen"; 20 -> "twenty"; 25 -> "twenty five"; 30 -> "thirty"
        40 -> "forty"; 50 -> "fifty"; 60 -> "sixty"
        else -> error("no words for $n")
    }

    /** 2.14: the tile that stands for every locked item in a slot, and what the pup says when it is tapped (kid words, no numbers). */
    const val MORE_SOON = "More soon!"
    const val MORE_SOON_LINE = "Keep playing, and more things to wear come!"

    /** What the pup says when an item opens at a finish screen. */
    fun revealLine(items: List<WearItem>, ask: Boolean = true): String =
        (if (items.size > 1) "A whole new outfit!" else "Something new to wear!") + if (ask) " Keep it on?" else ""
}

/**
 * What the ledger reads from the other stores at a sync: [practice] seconds a day (ProgressStore.practiceLog), the
 * lessons with three stars (KidStore.allStars at three) and the pages [walked] (KidStore.finales, plus a page whose last
 * lesson is done: its finale is about to play).
 */
data class LedgerInput(val practice: Map<LocalDate, Int>, val threeStars: Set<String>, val walked: Set<String>)
