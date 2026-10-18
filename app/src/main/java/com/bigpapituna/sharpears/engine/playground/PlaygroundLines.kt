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
    )
}
