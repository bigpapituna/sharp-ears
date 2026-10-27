package com.bigpapituna.sharpears.ui

import com.bigpapituna.sharpears.engine.nav.TodayCard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 2.15, the Plain look, read from the sources (composables) as CheerPopWiringTest does: ONE switch (ui/Plain.kt LocalPlain),
 * provided once from PlainLook.on (never a little kid's), and every pup, cheer pop, step pop, streak and reward entry point
 * reading it itself, so none of the call sites can miss it. Settings in every layout, the offer, the voice and the logs.
 */
class PlainWiringTest {
    private val root = File("src/main/java/com/bigpapituna/sharpears")
    private fun src(path: String) = File(root, path).readText().replace("\r\n", "\n")

    /** The body of the top-level function declared by [signature] in [path]: up to the next top-level declaration. */
    private fun body(path: String, signature: String): String {
        val s = src(path)
        val at = s.indexOf(signature)
        assertTrue("$path declares $signature", at >= 0)
        val rest = s.substring(at + signature.length)
        val end = Regex("""\n(@Composable|@OptIn|fun |internal fun |private fun |class |private class |internal class |object |val |private val |/\*\*)""")
            .find(rest)?.range?.first ?: rest.length
        return rest.substring(0, end)
    }

    /** Every composable in ui/ named for the pup. */
    private fun found0(): Set<String> {
        val decl = Regex("""fun ((?:[A-Z]\w*)?Pup\w*)\(""")
        return root.resolve("ui").walkTopDown().filter { it.extension == "kt" }.flatMap { f ->
            val text = f.readText()
            decl.findAll(text).map { it.groupValues[1] }.filter { n -> n.first().isUpperCase() && "@Composable" in text }
        }.toSet()
    }

    private fun readsPlain(text: String) = "LocalPlain.current" in text || "plainLook()" in text

    /** Every entry point that draws the pup, a cheer, a step pop, a streak or a reward, and the file it lives in. */
    private val entryPoints = listOf(
        // The pup, everywhere he is drawn (the eighty-odd call sites go through these).
        "ui/pup/Pup.kt" to "fun Pup(",
        "ui/Pup.kt" to "fun PupSays(",
        "ui/Pup.kt" to "fun SpeechBubble(",
        "ui/pup/PupReplay.kt" to "fun ReplayPup(",
        "ui/pup/PupClip.kt" to "fun PupClip(",
        "ui/pup/PupClip.kt" to "fun PupBow(",
        "ui/playground/PlaygroundKit.kt" to "internal fun StagePup(",
        // Cheers and the step pop.
        "ui/pup/CheerPop.kt" to "fun CheerPop(",
        "ui/pup/CheerPop.kt" to "fun StepPopLine(",
        "ui/pup/CheerPop.kt" to "fun rememberCheerPops(",
        "ui/StepPop.kt" to "fun StepPop(",
        "ui/StepPop.kt" to "fun rememberStepPops(",
        "ui/Pup.kt" to "fun Confetti(",
        // Streaks and days played.
        "ui/TodayScreen.kt" to "fun TodayScreen(",
        "ui/profile/ProfileScreen.kt" to "fun ProfileScreen(",
        "ui/profile/ProfileScreen.kt" to "private fun TodayPanel(",
        "ui/profile/ProfileScreen.kt" to "private fun WeekDots(",
        "ui/skin/Goodbye.kt" to "fun PawWeekRow(",
        "ui/skin/Goodbye.kt" to "fun Goodbye(",
        "ui/brief/ReportParts.kt" to "fun WeekStrip(",
        "ui/brief/BriefPage.kt" to "fun BriefPage(",
        "ui/family/FamilyTodayCard.kt" to "private fun ChildRow(",
        "ui/Retention.kt" to "fun WelcomeBackCard(",
        // Rewards and finishes.
        "ui/Celebrate.kt" to "fun Celebration(",
        "ui/Celebrate.kt" to "fun CheerBar(",
        "ui/Celebrate.kt" to "internal fun CalmRewards(",
        "ui/RunRecap.kt" to "fun RunRecap(",
        "ui/world/RewardViews.kt" to "fun RewardsChip(",
        "ui/world/RewardViews.kt" to "fun RewardsSheet(",
        "ui/world/RewardViews.kt" to "fun KidRewardStage(",
        "ui/world/DropReveal.kt" to "fun DropReveal(",
        "ui/Retention.kt" to "fun TomorrowSlot(",
        "ui/Retention.kt" to "fun WrappedGift(",
        "ui/profile/ProfileScreen.kt" to "fun AchievementBadge(",
        "ui/family/ParentGate.kt" to "internal fun HoldThePup(",
        "ui/brief/KidBriefCard.kt" to "fun KidCardStepHost(",
        "ui/Backdrop.kt" to "fun Modifier.paper(",
        "ui/family/Avatar.kt" to "fun PupAvatar(",
        "ui/family/FamilyWall.kt" to "private fun Member(",
        "ui/family/FamilyWall.kt" to "fun Paw(",
    )

    @Test fun everyEntryPointReadsThePlainLocal() {
        for ((path, sig) in entryPoints) assertTrue("$path $sig reads LocalPlain", readsPlain(body(path, sig)))
    }

    /** Any composable named for the pup in ui/ is an entry point above, or draws through one (or is a stated exception). */
    @Test fun noPupComposableEscapesTheSwitch() {
        val listed = entryPoints.map { it.second.substringAfter("fun ").substringBefore("(") }.toSet()
        // Through Pup(...) or PupClip: they draw nothing of their own; PupAvatarArt only through PupAvatar. The debug screens
        // are debug routes.
        // The Pup says toy's screens and previews draw him through Pup, SpeechBubble and the avatars.
        val through = setOf("ListeningPup", "PupLine", "PupDebug", "PupDemo", "SleepyPup") + found0().filter { it.startsWith("PupSays") && it != "PupSays" }
        val exempt = setOf("PupAvatarArt")
        assertEquals(1, Regex("""PupAvatarArt\(""").findAll(root.resolve("ui").walkTopDown().filter { it.extension == "kt" }
            .joinToString("\n") { it.readText() }.replace("fun PupAvatarArt(", "")).count())
        val found = found0()
        for (n in found) assertTrue("$n is an entry point, draws through one, or is a stated exception", n in listed || n in through || n in exempt)
        // Those that draw through one really do.
        assertTrue("Pup(pose, size = size)" in src("ui/ear/EarCommon.kt").substringAfter("internal fun ListeningPup("))
        assertTrue("Pup(pose, size = size, onTap = {})" in src("ui/practice/PracticeKit.kt").substringAfter("internal fun PupLine("))
        val sleepy = src("ui/skin/Goodbye.kt")
        assertTrue("if (plain) com.bigpapituna.sharpears.ui.PlainMark(" in sleepy && "else SleepyPup(pupSize" in sleepy)
    }

    @Test fun oneSwitchProvidedFromTheRuleNeverALittleKids() {
        val main = src("MainActivity.kt")
        assertTrue("PlainMode(com.bigpapituna.sharpears.engine.family.PlainLook.on(current.audience, prefs.plainLook), prefs.plainStepSound)" in main)
        assertEquals(1, Regex("""LocalPlain provides """).findAll(main).count())
        assertTrue("com.bigpapituna.sharpears.ui.LocalPlain provides plainMode" in main)
        // The voice asks the same rule; nobody reads the raw switch to decide what shows.
        assertTrue("voice.plain = { com.bigpapituna.sharpears.engine.family.PlainLook.on(profiles.current.audience, prefs.plainLook) }" in main)
        for (f in root.resolve("ui").walkTopDown().filter { it.extension == "kt" }) {
            val t = f.readText()
            if ("prefs.plainLook" in t) assertTrue("${f.name} reads the raw switch only to show or set it", f.name in setOf("SettingsScreen.kt", "Plain.kt"))
        }
        val plain = src("ui/Plain.kt")
        assertTrue("val LocalPlain = compositionLocalOf { PlainMode() }" in plain)
    }

    @Test fun cheersStepPopAndVoiceFollowTheRule() {
        val cheer = src("ui/pup/CheerPop.kt")
        assertTrue("PlainLook.cheerFrequency(plain(), prefs.cheerFrequency)" in cheer)
        val step = src("ui/StepPop.kt")
        assertTrue("PlainLook.stepChime(plainNow.value.on, sound(), plainNow.value.stepSound)" in step)
        assertTrue("if (plain) { drawCheckDisc(box.center, 1f, StepPopMotion.fade(ms), 1f); return@Canvas }" in step)
        val voice = src("audio/Voice.kt")
        assertTrue("PlainLook.voiceOn(plain(), prefs.voice, prefs.plainVoice)" in voice)
        assertTrue("PlainLook.calm(text, spoken = true)" in voice)
        // Lessons, the player and songs keep their own calls (the entry points decide).
        assertTrue("rememberStepPops(deps.sampler, deps.mic, sound = { deps.prefs.sound }" in src("ui/LessonScreen.kt"))
    }

    @Test fun settingsInEveryLayoutForGrownUpsAndBigKidsAndEncouragementSaysSo() {
        val settings = src("ui/SettingsScreen.kt")
        assertTrue("if (com.bigpapituna.sharpears.engine.family.PlainLook.offered(me.audience) && shows(Simple.SettingsPart.PLAIN_LOOK))" in settings)
        assertTrue("Toggle(com.bigpapituna.sharpears.engine.family.PlainLook.TITLE, com.bigpapituna.sharpears.engine.family.PlainLook.LINE," in settings)
        assertTrue("com.bigpapituna.sharpears.data.Briefs.setting(\"plainLook\", on)" in settings)
        assertTrue("PlainLook.STEP_SOUND" in settings && "Briefs.setting(\"plainStepSound\", it)" in settings)
        assertTrue("if (plainOn) Text(com.bigpapituna.sharpears.engine.family.PlainLook.ENCOURAGEMENT_OFF" in settings)
        assertTrue("if (plainOn) prefs.plainVoice = it else prefs.voice = it" in settings)
        // Under Playing, before the keys.
        assertTrue(settings.indexOf("Section(\"Playing\")") < settings.indexOf("PlainLook.TITLE") && settings.indexOf("PlainLook.TITLE") < settings.indexOf("Sub(\"Keys\")"))
        val simple = src("engine/nav/Simple.kt")
        assertTrue("SettingsPart.PLAIN_LOOK" in simple.substringAfter("val SETTINGS_ESSENTIALS").substringBefore("\n\n"))
        // The brief says it in words.
        assertTrue("\"plainLook\" -> toggle(\"The Plain look\")" in src("engine/brief/templates/Sections.kt"))
        // The own week (a grown-up's) is written without the streak's praise; a kid's brief is as it was.
        assertTrue("if (!p.isKid && com.bigpapituna.sharpears.engine.family.PlainLook.on(p.audience, com.bigpapituna.sharpears.data.Prefs(app, p.id).plainLook))" in src("ui/brief/BriefEnv.kt"))
    }

    @Test fun theOfferOnTodayAndALessonsFinishOncePerProfile() {
        val plain = src("ui/Plain.kt")
        assertTrue("PlainLook.offerDue(audience, prefs.plainLook, prefs.plainOffered, moment" in plain)
        assertTrue("prefs.plainOffered = true" in plain && "com.bigpapituna.sharpears.data.Briefs.setting(\"plainLook\", true)" in plain)
        // 2.15 merge: the dashboard's flag is asked in the decision, before the card is marked offered.
        assertTrue("flag = { com.bigpapituna.sharpears.analytics.AppFlags.plainLookOffer }" in plain)
        assertTrue(plain.indexOf("AppFlags.plainLookOffer") < plain.indexOf("prefs.plainOffered = true"))
        assertTrue("PlainLook.Moment.LESSON_FINISHED" in src("ui/LessonScreen.kt"))
        val main = src("MainActivity.kt")
        assertTrue("PlainLook.Moment.APP_OPENED" in main && "daysPlayed = { progress.practiceLog().count { it.value > 0 } }" in main)
        // Right under the card, where it is seen before it counts as offered.
        val today = src("ui/TodayScreen.kt")
        assertTrue(today.indexOf("TodayCardView(shown, goal, a, quest") < today.indexOf("plainOffer?.invoke()") &&
            today.indexOf("plainOffer?.invoke()") < today.indexOf("family?.invoke()"))
    }

    /**
     * 2.15 merge with mic-trust: the could-not-hear hint's words and buttons never hang on the Plain look (only its pup, a
     * little kid's, through Pup itself), and the microphone card and the privacy page never read it: Plain hides none of them.
     */
    @Test fun theMicHintCardAndPrivacyPageStayInThePlainLook() {
        val hint = src("ui/hints/NotHeardHint.kt")
        assertFalse(readsPlain(hint))
        assertTrue("the pup only for a little kid", "if (kid) Pup(Pose.LISTENING" in hint)
        assertTrue("Text(MicHearing.TITLE" in hint && "Text(cause.line" in hint && "Text(MicHearing.TOUCH" in hint)
        assertTrue("the player's kid is the little kids' skin", "val kid = profile.kidSkin" in src("ui/PlayerScreen.kt"))
        assertTrue("NotHeardHint(cause, kid," in src("ui/PlayerScreen.kt"))
        assertFalse(readsPlain(src("ui/MicCard.kt")))
        assertFalse(readsPlain(body("ui/AboutScreens.kt", "fun PrivacyScreen(")))
    }

    @Test fun theQuietOnesInThePlainLook() {
        val main = src("MainActivity.kt")
        // Level-ups and achievement banners stay unseen, as for a little kid; XP and level on You.
        assertTrue("val quiet = current.kidSkin || plainMode.on" in main)
        assertTrue("if (!isStage(screen) && !quiet)" in main)
        val you = src("ui/profile/ProfileScreen.kt")
        assertTrue("if (!kid && !plain) world?.let" in you)
        assertTrue("Text(\"Level \$level\"" in you)
        // The comeback: its plain words, no streak line.
        val retention = src("ui/Retention.kt")
        assertTrue("PlainLook.comebackLine(item)" in retention && "streak.takeIf { !plain }" in retention)
        assertTrue("streak = streak, item = title," in main)
    }

    /** Today's greeting in the Plain look: plain words, no coach introducing himself, no streak, no rest day. */
    @Test fun todaysGreetingIsPlainText() {
        val plan = TodayCard.State.PLAN
        val atRisk = GoalState("3 of 10 min today", met = false, streak = 5, atRisk = true)
        val rest = GoalState("0 of 10 min today", met = false, streak = 4, atRisk = true, rest = 1, restYesterday = true)
        val running = GoalState("12 of 10 min today", met = false, streak = 6, atRisk = false)
        // As before with the switch off.
        assertEquals("Your 5 day streak is waiting. 3 of 10 min today.", greeting(plan, atRisk, fresh = false, hour = 20))
        assertEquals("Yesterday was a rest day. Ready when you are.", greeting(plan, rest, fresh = false, hour = 20))
        // In the Plain look: the time of day, never the streak or the rest day.
        for (g in listOf(atRisk, rest, running))
            assertEquals("Good evening, Dana. A short session before the day ends?", greeting(plan, g, fresh = false, hour = 20, name = "Dana", plain = true))
        assertEquals("Hi, Dana. Let's start with an easy song.", greeting(plan, null, fresh = true, name = "Dana", plain = true))
        assertEquals("Hi there. There's a challenge at your level in today's plan.", greeting(plan, null, fresh = true, challenge = true, plain = true))
        assertEquals("That's today's goal. Rest those hands.", greeting(plan, running.copy(met = true), fresh = false, plain = true))
        val today = src("ui/TodayScreen.kt")
        assertTrue("challenge = challenge, plain = plain)" in today && "val quest = quest.takeIf { !plain }" in today)
    }
}
