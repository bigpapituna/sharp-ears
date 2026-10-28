package com.bigpapituna.sharpears.afterdark

import com.bigpapituna.sharpears.extras.Extras
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 2.16, the coach voice wired in (read from the sources, as the other wiring tests do): every place in the app that hands a
 * moment to the door asks the profile's own kid flag first; the door asks the wall (CoachGate) at every moment and the setting
 * shows for a grown-up only, behind the parent gate, with the one-time notice; nothing of it reaches the brief or the tester's
 * events but the one boolean; the What's new line is the extra's own.
 */
class CoachWiringTest {
    private val main = File("src/main/java/com/bigpapituna/sharpears")
    private val here = File("src/afterdark/java/com/bigpapituna/sharpears")
    private fun src(path: String) = File(main, path).readText()
    private fun mine(path: String) = File(here, path).readText()

    @Test fun everyCallerAsksTheProfileFirst() {
        val cheer = src("ui/pup/CheerPop.kt")
        assertTrue("val extra = if (profileKid) null else com.bigpapituna.sharpears.extras.Extras.line(com.bigpapituna.sharpears.extras.Moment.of(c.trigger), c.count)" in cheer)
        assertTrue("val extra = if (profileKid) null else com.bigpapituna.sharpears.extras.Extras.line(com.bigpapituna.sharpears.extras.Moment.STEP_PASSED" in cheer)
        assertTrue("if (!profileKid) com.bigpapituna.sharpears.extras.Extras.say(com.bigpapituna.sharpears.extras.Moment.MISTAKE)" in cheer)
        assertTrue("if (!profileKid) com.bigpapituna.sharpears.extras.Extras.say(com.bigpapituna.sharpears.extras.Moment.HARD)" in cheer)
        // profileKid is the profile's isKid (a big kid too), never the skin.
        assertTrue("profileKid = anyKid" in src("ui/LessonScreen.kt") && "val anyKid = who.isKid" in src("ui/LessonScreen.kt"))
        assertTrue("profileKid = com.bigpapituna.sharpears.ui.family.LocalProfile.current.isKid" in src("ui/PlayerScreen.kt"))
        val lesson = src("ui/LessonScreen.kt")
        assertTrue("!anyKid && !greeted" in lesson)
        assertTrue("if (!anyKid) com.bigpapituna.sharpears.extras.Extras.say(com.bigpapituna.sharpears.extras.Moment.finish(" in lesson)
        assertTrue("if (!anyKid) com.bigpapituna.sharpears.extras.Extras.say(if (fails >= 2)" in lesson)
        assertTrue("if (!who.isKid && stars >= 2) LaunchedEffect(e) { com.bigpapituna.sharpears.extras.Extras.say(" in src("ui/RunRecap.kt"))
        val activity = src("MainActivity.kt")
        assertTrue("if (!current.isKid) com.bigpapituna.sharpears.extras.Extras.say(com.bigpapituna.sharpears.extras.Moment.COMEBACK)" in activity)
        assertTrue("if (!profile.isKid) com.bigpapituna.sharpears.extras.Extras.say(" in activity)
        // Every say and line in the app sits behind one of those checks.
        val calls = main.walkTopDown().filter { it.extension == "kt" }.sumOf { f ->
            Regex("""Extras\.(say|line)\(""").findAll(f.readText()).count() }
        assertEquals(10, calls)
    }

    @Test fun theDoorAsksTheWallAtEveryMoment() {
        val host = mine("afterdark/CoachHost.kt")
        val flat = host.replace(Regex("""\s+"""), " ")
        assertTrue("fun line(m: Moment, count: Int?, afterMs: Long): ExtraLine? { if (!on() || busy) return null" in flat)
        assertTrue("fun say(m: Moment) { if (!on() || busy) return" in flat)
        assertTrue("CoachGate.on(p, v, PlainLook.cheerFrequency(plain, pr.cheerFrequency), plain)" in host)
        assertTrue("private fun voice(): String? = CoachGate.voiceOf(prefs?.extraString(CoachGate.KEY), voices().map { it.id })" in host)
        // The profile is read live (MainActivity hands { profiles.current }), so a kind changed in Family holds at once.
        assertTrue("com.bigpapituna.sharpears.extras.Extras.open(this, { profiles.current }, prefs, mic, voice)" in src("MainActivity.kt"))
    }

    @Test fun theVoiceAsksTheDoorForANarrationFirst() {
        val natural = src("audio/NaturalVoice.kt")
        assertTrue("com.bigpapituna.sharpears.extras.Extras.narration(key) ?: \"voice/\$key.ogg\".takeIf { \"\$key.ogg\" in recorded }" in natural)
        val host = mine("afterdark/CoachHost.kt")
        assertTrue("if (!CoachGate.narrates(p, v, PlainLook.on(p.audience, pr.plainLook))) return null" in host)
    }

    @Test fun theSettingIsAGrownUpsBehindTheGateWithTheNotice() {
        val ui = mine("afterdark/CoachUi.kt")
        assertTrue("if (!CoachGate.offered(me)) return" in ui)
        // From Off to a voice: the gate, then the notice once, then the question once.
        assertTrue("else -> gate.guard { proceed(voice.id) }" in ui)
        assertTrue("prefs.extraFlag(CoachGate.NOTICE_KEY) != true -> { turningOn = voice; notice = true" in ui)
        assertTrue("CoachGate.forOf(prefs.extraString(CoachGate.FOR_KEY)) == null -> { turningOn = voice; notice = false" in ui)
        assertTrue("prefs.setExtraFlag(CoachGate.NOTICE_KEY, true); proceed(waiting)" in ui)
        assertEquals("How should your coach talk to you?", CoachCopy.ASK)
        assertEquals(listOf("As a woman", "As a man"), listOf(CoachCopy.AS_WOMAN, CoachCopy.AS_MAN))
        assertTrue(CoachCopy.NOTICE.contains("flirty") && CoachCopy.NOTICE.contains("grown-ups") && CoachCopy.NOTICE_TITLE == "For adults only")
        assertTrue("if (!CoachGate.offered(LocalProfile.current) || LocalPlain.current.on) return" in ui)
        // Under Encouragement, in every layout that shows it.
        val settings = src("ui/SettingsScreen.kt")
        // 2.16.1: in the Playing tab, right under Encouragement, before Wrong keys and the reminder (SettingsTabsTest).
        val playing = settings.indexOf("SettingsTabs.Tab.PLAYING -> {")
        val enc = settings.indexOf("if (on(SettingsTabs.Item.ENCOURAGEMENT)) {")
        val part = settings.indexOf("if (on(SettingsTabs.Item.COACH_VOICE)) com.bigpapituna.sharpears.extras.Extras.SettingsPart(prefs) { Sub(it) }")
        val reminder = settings.indexOf("Sub(\"Reminder\")")
        assertTrue(playing in 0 until enc && enc < part && part < reminder)
    }

    @Test fun nothingButOneBooleanLeavesThePhone() {
        val all = here.walkTopDown().filter { it.extension == "kt" }.joinToString("\n") { it.readText() }
        assertFalse("never the brief's settings log (it reaches the AI brief and the email)", "Briefs." in all)
        assertEquals(listOf("Tester.extra(PROPERTY, on)", "Tester.extra(VOICE_PROPERTY, if (on && v != null) v else CoachGate.OFF)",
            "Tester.extra(FOR_PROPERTY, forWho() ?: \"unset\")"), Regex("""Tester\.\w+\(.*\)$""", RegexOption.MULTILINE).findAll(all).map { it.value }.toList())
        assertEquals(listOf("coach_voice", "coach_voice_id", "coach_for"), listOf(CoachHost.PROPERTY, CoachHost.VOICE_PROPERTY, CoachHost.FOR_PROPERTY))
        val events = File(main, "analytics/EVENTS.md").readText()
        for (k in listOf("coach_voice", "coach_voice_id", "coach_for")) assertTrue(k, "`$k`" in events)
    }

    @Test fun whatsNewIsTheExtrasOwn() {
        assertTrue(Extras.BUILT)
        val lines = Extras.WHATS_NEW["2.16.0"]!!
        assertEquals(1, lines.size)
        assertTrue(lines[0].contains("Adults only"))
        assertFalse(lines[0].contains("!") || lines[0].contains("—"))
        val merged = com.bigpapituna.sharpears.engine.nav.Welcome.merged(Extras.WHATS_NEW)
        assertEquals(lines, com.bigpapituna.sharpears.engine.nav.Welcome.whatsNew("2.16.0", merged))
        // 2.16.1: the app's tabs line, then the coach's, naming the tab and the four voices.
        val now = com.bigpapituna.sharpears.engine.nav.Welcome.whatsNew("2.16.1", merged)
        assertEquals(2, now.size)
        assertTrue(now[0].startsWith("Settings are now grouped into tabs"))
        assertTrue(now[1].contains("Settings, Playing, under Encouragement") && now[1].contains("Sienna") && now[1].contains("Adults only"))
        assertFalse(now[1].contains("!") || now[1].contains("—"))
    }
}
