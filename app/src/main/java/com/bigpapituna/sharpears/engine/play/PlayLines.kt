package com.bigpapituna.sharpears.engine.play

/**
 * The play corner's fixed spoken lines (ui/play: Pup says, Clap along, the Jam), for the voice catalog. Lines with a
 * player's name in them are made on the phone; their nameless versions are here. Keep in step with those screens and
 * [PupSaysGame]'s words.
 */
object PlayLines {
    fun fixed(): List<String> = buildList {
        // Pup says, one player.
        add("Your turn!"); add("Your turn."); add("Listen first, then copy me."); add("Wait for it. Your turn. Tap ready at the keys.")
        add("Your turn. Tap ready at the keys.")
        // A pattern starts at PupSaysGame.START notes, so there is never a "1 notes" to record.
        for (n in PupSaysGame.START..20) {
            val yes = when (n % 3) { 0 -> "Yes! $n notes."; 1 -> "You copied all $n."; else -> "That's it, $n notes!" }
            add(yes); add("$yes Now one more.")
            add("That was your last try. Your best: $n notes!")
        }
        add("That was your last try. Good playing!"); add("Oops! One more try."); add("Oops! Two more tries.")
        // Clap along.
        listOf("You're right with me!", "Eight in a row. What a drummer!", "Sixteen! We're a band.",
            "Tap the drum with me!", "Tap the drum on my beat.", "Clap with my drum!", "Clap with me! I'll drum quietly so I can hear you.").forEach(::add)
    }
}
