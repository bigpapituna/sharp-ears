package com.bigpapituna.sharpears.engine

import com.bigpapituna.sharpears.engine.tester.TesterRules

/*
 * 2.15 mic trust (docs/MIROFISH-REPORT-2026-10-26.md: 25 of 40 simulated people raised microphone privacy before install).
 * The microphone card and the privacy page's first three sections, written from what this build and this phone actually
 * do ([MicFacts]), by pure functions: a build that keeps recordings can never be told "none of it is kept", and a build
 * without the tester dashboard never hears of Help Roy test. MicTrustTest walks every combination.
 */

/** What this build and this phone do with sound and data, read once by the screens (ui/MicCard.kt micFacts). */
data class MicFacts(
    /** A test build that can keep practice recordings (the debug build: MicInput.canRecord). */
    val canKeepRecordings: Boolean = false,
    /** It keeps them now: Keep the last four recordings is on (MicInput.keepRecordings). Never true without [canKeepRecordings]. */
    val keepsRecordings: Boolean = false,
    /** Send them for review is set up and on (Reporter.enabled): stuck-moment clips go to a private GitHub repository on Wi-Fi. */
    val sendsReviewClips: Boolean = false,
    /** Help Roy test can send screen recordings: the build carries the tester dashboard's key (Tester.hasKey). */
    val testerDashboard: Boolean = false,
    /** Email Roy a daily diary is built in (Tester.diaryBuilt). */
    val testerDiary: Boolean = false,
    /** The parent brief can be written by an AI (BriefAi.hasKey). */
    val aiBrief: Boolean = false,
    /** The brief can go by email (BriefEmail.hasKey). */
    val emailBrief: Boolean = false,
) {
    /** What the facts say about the sound: kept on the phone, or worked out and gone. */
    val keeps: Boolean get() = canKeepRecordings && keepsRecordings
}

/** One short section: a heading and its plain words. */
data class TrustSection(val heading: String, val text: String)

/** The microphone card's words (ui/MicCard.kt draws them). */
data class MicCardCopy(val title: String, val lead: String, val sections: List<TrustSection>, val allow: String, val notNow: String, val close: String) {
    /** Everything the card says, for tests and TalkBack. */
    val all: String get() = (listOf(title, lead) + sections.flatMap { listOf(it.heading, it.text) }).joinToString(" ")
}

object MicTrust {
    const val TITLE = "How the microphone works"
    const val ALLOW = "Allow the microphone"
    const val NOT_NOW = "Not now"
    const val CLOSE = "Close"

    /** The test-build exception, exactly as 2.15 asks; the card and the privacy page say it only where recordings are kept. */
    const val KEEP_LINE = "This test build keeps the last four practice recordings on this phone to tune the listening. Turn this off in Settings, Sound."

    /** The privacy page's three headings, in order. */
    const val LISTENS = "What the app listens to"
    const val STAYS = "What stays on this phone"
    const val LEAVES = "What leaves this phone"

    /** Where the mic listens, said the same way on the card and the page. */
    private const val ONLY_WHILE = "It listens only while a lesson, a song, a game, the tuner or Microphone setup is open with the mic on, " +
        "never in the background."

    /**
     * The card shown before the microphone is first asked for, and from Microphone setup and the privacy page. [child]: the
     * kid whose games will listen (the card is then read by a grown-up, behind the gate).
     */
    fun card(f: MicFacts, child: String? = null): MicCardCopy {
        val lead = if (child != null) "$child's games can listen to the piano through this phone's microphone. Here is what that means."
            else "Sharp Ears can listen to your piano through this phone's microphone. Here is what that means."
        val sections = listOf(
            TrustSection("What it listens for", "The notes you play, so it can check them and move on when they are right. $ONLY_WHILE"),
            TrustSection("What happens to the sound", soundWords(f)),
            TrustSection("Kids' profiles", "Kids' profiles use the same rule. Nothing the microphone hears is saved under a child's name."),
            TrustSection("Playing without the mic", "Tap the keys on the screen, or plug in a MIDI keyboard. You can turn the mic on later with the mic button."),
        )
        return MicCardCopy(TITLE, lead, sections, ALLOW, NOT_NOW, CLOSE)
    }

    /** What happens to the sound on this build, in a few sentences. */
    fun soundWords(f: MicFacts): String = buildList {
        when {
            f.keeps -> { add("It works out the notes on this phone as you play."); add(KEEP_LINE) }
            f.canKeepRecordings -> add("It works out the notes on this phone as you play. Keeping recordings is off on this test build, " +
                "so the sound is gone straight away.")
            else -> add("It works out the notes on this phone as you play, and the sound is gone straight away. None of it is kept.")
        }
        if (f.sendsReviewClips) add("Send them for review is on, so on Wi-Fi short clips of stuck moments go to the private GitHub repository set up on this phone.")
        else if (f.keeps) add("They never leave the phone unless you share them.")
        else add("None of it is sent anywhere.")
        if (f.testerDashboard) add("While Help Roy test is on, this test build sends Roy ${TesterRules.SENDS}, with the words hidden. Never any sound.")
    }.joinToString(" ")

    /**
     * The privacy page's first three sections ([LISTENS], [STAYS], [LEAVES]), each short enough for one screen on the Fold's
     * cover display. What leaves the phone lists only what this build has.
     */
    fun privacy(f: MicFacts): List<TrustSection> = listOf(
        TrustSection(LISTENS, listOf(
            "The microphone hears your piano so the app can check the notes you play.",
            "It listens only while a lesson, a song, a game, the tuner or Microphone setup is open with the mic on and the app is on screen. " +
                "Android 12 and newer show a green microphone dot while it listens.",
            soundWords(f.copy(testerDashboard = false)),
            "Kids' profiles use the same rule. Without the mic, play on the screen's keys or a MIDI keyboard.",
        ).joinToString(" ")),
        TrustSection(STAYS, listOf(
            "Everything else stays in the app's own storage on this phone: the names of the people who play, and each one's progress, " +
                "stars, minutes, stickers, settings and parent brief" + (if (f.keeps) ", and this test build's recordings." else "."),
            // A test build's recordings can hold voices in the room (never a name): then no "no voice".
            "A child's profile holds a first name, an age group and their playing. No birthday, no photo" + (if (f.keeps) "." else ", no voice."),
            "The parent gate guards a child's brief and settings.",
            "Deleting a person in Settings, Family removes their progress and their brief. Uninstalling the app removes everything.",
            "If the phone's backup to your Google account is on, it can carry progress to a new phone, never recordings.",
        ).joinToString(" ")),
        TrustSection(LEAVES, leaves(f)),
    )

    /** Each thing that can leave the phone on this build, one sentence each; YouTube always. */
    fun leaving(f: MicFacts): List<String> = listOfNotNull(
        "The parent brief's findings, with no names and no sound, go to an AI service that writes the brief.".takeIf { f.aiBrief },
        "The brief by email, once a grown-up sets it up, goes through an email service to the address they give.".takeIf { f.emailBrief },
        "Help Roy test, while it is on, sends Roy ${TesterRules.SENDS}, with the words on screen hidden. Never any sound.".takeIf { f.testerDashboard },
        "Email Roy a daily diary, while it is on, sends Roy a plain list of screens and tries, with no names.".takeIf { f.testerDiary },
        ("Send them for review, if you switch it on in Microphone setup, sends stuck-moment clips and reports to a private GitHub " +
            "repository.").takeIf { f.canKeepRecordings },
    )

    private fun leaves(f: MicFacts): String {
        val items = leaving(f)
        val youtube = "Tutorial videos play in YouTube's own player, which connects to Google."
        return if (items.isEmpty()) "Nothing about you or your playing leaves this phone. $youtube"
        else (listOf(if (items.size == 1) "Only one thing, on this build:" else "Only these, on this build:") + items + youtube).joinToString(" ")
    }
}
