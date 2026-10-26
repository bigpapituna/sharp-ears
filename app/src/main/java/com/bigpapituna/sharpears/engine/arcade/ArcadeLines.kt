package com.bigpapituna.sharpears.engine.arcade

/*
 * What the arcade says and shows. Kid copy rules (KidCopyTest, ArcadeRulesTest): sentences, no numbers, no "wrong", no
 * dashes or symbols. Keys are never named aloud during play (a line said over the mic makes it deaf for its length): a
 * pre-reader sees each key's colour and animal, a reader its letter. Spoken lines are said at gaps (a line done, the
 * round's start and end); the rest are shown in the pup's bubble.
 */
object ArcadeLines {
    const val HUB = "Pick a game!"
    const val LOCKED = "Keep playing, and this one opens!"
    const val NEW_GAME = "A new game is ready!"
    /** 2.14: the games not open yet are one wrapped tile with this name (shown, and the words TalkBack reads). */
    const val MORE_SOON = "More games soon!"
    const val MORE_SOON_LABEL = "More games soon. Keep playing and the next one opens."

    /** Said as a round starts (over the demo's lead). */
    fun intro(game: ArcadeGame, reader: Boolean, level: Int = 0): String = when (game) {
        ArcadeGame.BALLOONS -> if (reader) "Pop the balloons! Play the key with the same letter." else "Pop the balloons! Play the key with the same colour."
        ArcadeGame.GARDEN -> "Walk with me, and the flowers grow!"
        ArcadeGame.FEED -> if (reader) "I'm hungry! Play each treat's letter before it flies by." else "I'm hungry! Play each treat's colour before it flies by."
        ArcadeGame.TOWER -> "Let's build a tower up to the clouds! Play each block's key."
        ArcadeGame.MOLES -> "Boop the moles! Play the key each mole peeks out of."
        ArcadeGame.FROGS -> if (level >= 2) "Two frogs! The low frog is your left hand, the high frog your right." else "Help the frog hop across! Play each lily pad's key."
        ArcadeGame.FIREWORKS -> "Let's light up the sky with a song! Listen, then play."
        ArcadeGame.PAINTER -> "Let's paint a picture with a song! Listen, then play."
        ArcadeGame.BRIDGE -> "Build a rainbow bridge! Your left hand, then your right."
    }

    /** The song games name their song first. */
    fun song(song: ArcadeSong) = "Our song is ${song.title}!"

    /** Said when a line ends (a gap follows, the mic can be deaf for it). */
    fun lineDone(game: ArcadeGame, n: Int): String? = when (game) {
        ArcadeGame.GARDEN -> GARDEN_DONE[n % GARDEN_DONE.size]
        ArcadeGame.FROGS -> FROGS_DONE[n % FROGS_DONE.size]
        ArcadeGame.BRIDGE -> BRIDGE_DONE[n % BRIDGE_DONE.size]
        ArcadeGame.FIREWORKS -> FIREWORKS_DONE[n % FIREWORKS_DONE.size]
        else -> null
    }

    val GARDEN_DONE = listOf("A flower!", "Look, it opened!", "So pretty!")
    val FROGS_DONE = listOf("Hop, hop, across!", "Ribbit!", "Safe on the lily pads!")
    val BRIDGE_DONE = listOf("The bridge is ready!", "Over the rainbow!")
    val FIREWORKS_DONE = listOf("Boom!", "Look at the sky!", "Sparkly!")

    /** Fireworks: the bites are done, the phrases come; then the whole song. */
    const val JOINED = "Now let's join them up!"
    const val WHOLE_SONG = "Now the whole song, all by yourself!"
    /** Song painter: the phrases again from memory. */
    const val BY_YOURSELF = "Now paint it by yourself!"
    const val ENCORE = "Once more, the encore!"
    const val FINISH_REST = "Let me finish the song for you!"

    /** Shown in the bubble while playing (never said: the mic keeps listening). */
    const val CLOUDS = "You reached the clouds!"
    const val HIGHER = "Higher, up to the moon!"
    val STREAK = listOf("So quick!", "Keep it up!", "Wow, wow, wow!")
    const val HELP = "Look, it's lit up!"
    const val SLIP = "Oops! Try another key."
    const val WATCH = "Watch and listen."
    const val GO = "Go!"

    /** The round's end, by stars. */
    fun finish(stars: Int): String = when {
        stars >= 3 -> "Every star is yours!"
        stars == 2 -> "Great playing! Shall we go again?"
        else -> "Good playing! Let's play again!"
    }
    /**
     * What each star on the finish means (2.14, Mila: "1 of 3 stars and no sign of what a star needs"): a few words under each
     * star, no numbers, the same rule as [ArcadeStars] (a tower counts blocks to the clouds, a song game the song, the rest the
     * share of notes played). [star] is 1..3.
     */
    fun starMeaning(game: ArcadeGame, star: Int): String = when {
        game == ArcadeGame.TOWER -> listOf("You started!", "Halfway up!", "The clouds!")
        game.skill == Skill.SONGS -> listOf("You played!", "Most of the song!", "The whole song!")
        else -> listOf("You played!", "Most of them!", "Nearly all!")
    }[star.coerceIn(1, 3) - 1]

    const val STICKER = "A new sticker for your album!"
    const val PAINTED = "This picture is yours!"
    const val HARDER = "Next time, something new!"

    /** Every line the arcade says aloud, for the voice catalog (recorded at kid speed). */
    fun spoken(): List<String> = buildList {
        add(HUB); add(LOCKED); add(NEW_GAME)
        for (g in ArcadeGame.entries) for (reader in listOf(true, false)) for (level in 0..g.maxLevel) add(intro(g, reader, level))
        ArcadeSongs.ALL.forEach { add(song(it)) }
        addAll(GARDEN_DONE); addAll(FROGS_DONE); addAll(BRIDGE_DONE); addAll(FIREWORKS_DONE)
        add(JOINED); add(WHOLE_SONG); add(ENCORE); add(BY_YOURSELF); add(FINISH_REST)
        for (s in 1..3) add(finish(s))
        add(STICKER); add(PAINTED); add(HARDER)
    }.distinct()

    /** Every line the arcade shows or says (the copy test reads these). */
    fun all(): List<String> = (spoken() + STREAK + listOf(HELP, SLIP, WATCH, GO, CLOUDS, HIGHER, MORE_SOON) + ArcadeGame.entries.map { it.title } +
        ArcadeGame.entries.flatMap { g -> (1..3).map { starMeaning(g, it) } } +
        ArcadeGame.entries.map(ArcadeQuest::title)).distinct()
}
