package com.bigpapituna.sharpears.engine.playground

/**
 * The words the Playground's stages show over the games (ui/playground: HighLow, KeyHunt, ClapStage, SoftLoudStage,
 * ConcertStage), listed for the voice catalog so the pup can say them in the recorded voice at the kids' speed. Keep
 * this list in step with those files when a call-out changes (the catalog test checks they are speakable).
 */
object PlaygroundLines {
    val CALL_OUTS: List<String> = listOf(
        // The pup flying (lesson 1).
        "Up I go!", "Landed!", "Higher!", "Lower!",
        // The key hunts (lessons 2 and 3).
        "You found it!", "That's it!", "Yes!", "Look for two black keys.", "Look for three black keys.",
        "Find a dog house!", "Find a fence!", "Find a C!", "You found them all!",
        // The drum (lesson 7).
        "Get ready to clap!", "On the boom!", "Clap, clap!", "What a drummer you are!", "I heard you! Now right on the boom.",
        // The nap (lesson 11).
        "Goodnight. That was lovely.", "First, play middle C your usual way.", "Oops, you woke me! Softer, please.",
        "Soft, so I keep sleeping.", "Now loud. Wake me up!",
        // The concert (lesson 12).
        "Which song tonight?", "Bravo!",
        // 2.1.1: the C hunt and the nap's nudges, spoken now between rounds.
        "C is just left of the dog house.", "A little louder!",
        // Every round shows itself first, then listens; three tries and it goes by (RoundCoach).
        LET_ME_SHOW, YOUR_TURN, AGAIN, LET_GO, WHITE_KEY, CLAP_AGAIN, I_HEARD_YOU,
    )

    /** The first round's demo begins. */
    const val LET_ME_SHOW = "Let me show you."
    /** The demo is over: the kid plays. */
    const val YOUR_TURN = "Your turn!"
    /** A quiet stretch: the round is shown again. */
    const val AGAIN = "Here it is again."
    /** Three tries: the round goes by with one star. */
    const val LET_GO = "Good try, let's keep going."
    /** A black-key hunt heard a white key; the pup points at the group, which glows. */
    const val WHITE_KEY = "That one's white. Try the black ones here."
    /** The drum heard no claps: it plays again by itself. */
    const val CLAP_AGAIN = "Let's clap again!"
    /** Shown (not spoken) when a sound has no key: the pup's ear twitches. */
    const val I_HEARD_YOU = "I heard you!"
}
