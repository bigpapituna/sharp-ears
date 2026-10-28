package com.bigpapituna.sharpears.engine.voice

import com.bigpapituna.sharpears.engine.SpokenCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 2.18: the speaking policy through its interface, with fake speakers and a fake world (no Android). */
class VoicePolicyTest {
    private var now = 0L

    private inner class FakeSpeaker(val name: String) : Speaker {
        val said = ArrayList<Utterance>()
        var ends: ((Boolean) -> Unit)? = null
        var starts: ((Long?) -> Unit)? = null
        var stops = 0
        override fun say(u: Utterance, started: (Long?) -> Unit, ended: (Boolean) -> Unit) { said += u; ends = ended; starts = started; order += "$name:${u.text}" }
        override fun stop() { stops++; ends?.invoke(false); ends = null }
        fun finish() { val e = ends; ends = null; e?.invoke(true) }
    }

    private val order = ArrayList<String>()
    private val system = FakeSpeaker("system")
    private val clip = FakeSpeaker("clip")
    private val model = FakeSpeaker("model")

    private inner class World : SpeakingPolicy.World {
        override var enabled = true
        override var plain = false
        override var natural = true
        override var kid = false
        override var speed = 1f
        override var listening = false
        override var headphones = false
        override var systemReady = true
        var warmed = 0
        override fun warmSystem() { warmed++ }
        val narrated = HashMap<String, String>()
        val recorded = HashSet<String>()
        override fun narration(key: String) = narrated[key]
        override fun recording(key: String) = if (key in recorded) "voice/$key.ogg" else null
        override var modelReady = false
        val deaf = ArrayList<String>()
        override fun lineStarts(owner: Any, lengthMs: Long?) { deaf += "start ${(owner as Utterance).text}" }
        override fun lineLength(owner: Any, lengthMs: Long) { deaf += "length ${(owner as Utterance).text} $lengthMs" }
        override fun lineEnds(owner: Any, tailMs: Long) { deaf += "end ${(owner as Utterance).text} $tailMs" }
        var speakingNow = false
        override fun changed(speaking: Boolean) { speakingNow = speaking }
    }

    private val world = World()
    private val policy = SpeakingPolicy(world, { s -> when (s) { Source.SYSTEM -> system; Source.MODEL -> model; else -> clip } }, { now })

    private fun record(vararg lines: String) = lines.forEach { world.recorded += SpokenCatalog.key(it) }

    @Test fun linesQueueOneAtATimeInOrderWhicheverSpeakerSaysThem() {
        record("Clean.")
        policy.speak("Clean.")
        policy.speak("Something nobody recorded.")
        assertEquals(listOf("clip:Clean."), order)                    // the phone's voice waits its turn (before 2.18 they overlapped)
        assertTrue(policy.speaking && world.speakingNow)
        clip.finish()
        assertEquals(listOf("clip:Clean.", "system:Something nobody recorded."), order)
        system.finish()
        assertFalse(policy.speaking); assertFalse(world.speakingNow)
    }

    @Test fun anInterruptCutsOffWhatIsSaidAndWhatWaits() {
        policy.speak("One."); policy.speak("Two.")
        policy.speak("Three.", interrupt = true)
        assertEquals(1, system.stops)
        assertEquals(listOf("system:One.", "system:Three."), order)
        system.finish(); assertFalse(policy.speaking)
    }

    @Test fun whileTheMicListensOnTheSpeakerLinesWaitAndComeWhenItStops() {
        world.listening = true
        policy.speak("Play it again.")
        policy.speak("F sharp.", deferrable = false)                  // only good now: dropped
        assertTrue(order.isEmpty()); assertEquals(1, policy.held)
        assertFalse(policy.speaking)
        world.listening = false
        policy.micStopped()
        assertEquals(listOf("system:Play it again."), order)
        // Held lines go stale after 30 s.
        world.listening = true
        policy.speak("Old line."); now += 31_000
        world.listening = false; system.finish(); policy.micStopped()
        assertEquals(1, order.size)
        // Headphones: nothing comes out next to the mic, so lines are said at once.
        world.listening = true; world.headphones = true
        policy.speak("With headphones.")
        assertEquals("system:With headphones.", order.last())
    }

    @Test fun aLineOverTheMicKeepsItDeafExactlyForItsLengthAndNeverHoldsIt() {
        record("Good try, let's keep going.")
        world.listening = true
        policy.speakOverMic("Good try, let's keep going.")
        assertEquals(listOf("start Good try, let's keep going."), world.deaf)
        assertTrue(policy.speaking); assertFalse("a line over the mic never holds it", policy.holdsMic)
        clip.starts!!.invoke(2_400)
        assertEquals("length Good try, let's keep going. 2400", world.deaf.last())
        clip.finish()
        assertEquals("end Good try, let's keep going. ${SpeakingPolicy.VOICE_TAIL_MS}", world.deaf.last())
        // A clip that errors ends its window the same way; stop() ends it at once, no tail.
        policy.speakOverMic("Good try, let's keep going.")
        clip.ends!!.invoke(false)
        assertEquals("end Good try, let's keep going. ${SpeakingPolicy.VOICE_TAIL_MS}", world.deaf.last())
        policy.speakOverMic("Good try, let's keep going.")
        policy.stop()
        assertEquals("end Good try, let's keep going. 0", world.deaf.last())
        assertEquals(3, world.deaf.count { it.startsWith("start") })
        assertEquals(3, world.deaf.count { it.startsWith("end") })
        assertFalse(policy.speaking)
    }

    @Test fun aLineInThePhonesVoiceOverTheMicMustStartNowOrNever() {
        world.listening = true; world.systemReady = false
        policy.speakOverMic("Nobody recorded this.")
        assertTrue(order.isEmpty()); assertEquals(1, world.warmed)
        world.listening = false
        policy.speakOverMic("Nobody recorded this.")                 // mic off: an ordinary line, which waits for the engine
        assertEquals(listOf("system:Nobody recorded this."), order)
    }

    @Test fun theRecordingChoiceNarrationRecordingModelPhone() {
        val line = "Lesson complete."
        val key = SpokenCatalog.key(line)
        assertEquals(Source.SYSTEM to null, policy.choose(line))
        world.modelReady = true
        assertEquals(Source.MODEL, policy.choose(line).first)
        record(line)
        assertEquals(Source.RECORDING to "voice/$key.ogg", policy.choose(line))
        world.narrated[key] = "coach/male_us/course/${key}_w.ogg"
        assertEquals(Source.NARRATION to "coach/male_us/course/${key}_w.ogg", policy.choose(line))
        // The Natural voice off: the phone's voice, narration included (2.16.2).
        world.natural = false
        assertEquals(Source.SYSTEM to null, policy.choose(line))
    }

    @Test fun aKidNeverHearsTheCoachsNarrationWhateverTheDoorSays() {
        val line = "Lesson complete."
        val key = SpokenCatalog.key(line)
        record(line)
        world.narrated[key] = "coach/male_us/course/$key.ogg"
        world.kid = true
        assertEquals(Source.RECORDING, policy.choose(line).first)
        policy.speak(line)
        assertEquals("voice/$key.ogg", clip.said.single().asset)
        assertEquals(RecordingChoice.pick(natural = true, kid = true, narration = "x", recording = null, model = false), Source.SYSTEM to null)
    }

    @Test fun thePupOnlyRuleAndGuidanceOff() {
        world.listening = true
        assertFalse("no recording: shown, not said", policy.sayInPupVoice("Mia, play the red key."))
        assertTrue(order.isEmpty())
        world.modelReady = true
        assertTrue(policy.sayInPupVoice("Mia, play the red key."))
        assertEquals("model:Mia, play the red key.", order.single())
        assertEquals(1f, model.said.single().speed)
        model.finish()
        world.enabled = false
        policy.speak("Anything."); policy.speakOverMic("Anything.")
        assertEquals(1, order.size)
    }

    @Test fun linesHeldForTheMicCountAsAboutToTalk() {
        // A lesson's finish: "Clean." and "Lesson complete." are held for the moment the mic takes to stop; the coach's stars
        // line must wait for them, not slip into the gap and play over them.
        world.listening = true
        policy.speak("Clean."); policy.speak("Lesson complete.")
        assertFalse(policy.speaking)
        assertTrue(policy.talkingOrAboutTo)
        world.listening = false; policy.micStopped()
        assertTrue(policy.talkingOrAboutTo)
        system.finish(); system.finish()
        assertFalse(policy.talkingOrAboutTo)
    }

    @Test fun headphonesOrAQuietMicNeverHoldTheVoiceWhenItIsQuiet() {
        policy.speak("One.")
        assertTrue(policy.holdsMic)
        world.headphones = true
        assertFalse(policy.holdsMic)
        system.finish()
        assertNull(system.ends)
    }

    /**
     * Bug 1 (2.18): the coach's 3-star or 2-star line at a lesson's finish waited at most 8 s for the voice, and Liam's narrated
     * "Clean." and "Lesson complete... good girl" fill more than that, so it was dropped. Now a line with no bubble of its own
     * waits the voice out (bounded by ExtraTurn.MAX_MS), then plays.
     */
    @Test fun theStarsLineWaitsOutALongNarrationThenPlays() {
        val turn = ExtraTurn(startAt = 0, waitMs = 8_000, outlastsTheVoice = true)
        var t = 0L
        while (t < 12_000) { assertEquals(ExtraTurn.Next.WAIT, turn.next(t, voiceTalking = true, blocked = false)); t += 50 }
        assertEquals(ExtraTurn.Next.GO, turn.next(t, voiceTalking = false, blocked = false))
        // The 2.17 rule (the deadline counted the narration): dropped.
        val old = ExtraTurn(startAt = 0, waitMs = 8_000, outlastsTheVoice = false)
        t = 0L
        var verdict = ExtraTurn.Next.WAIT
        while (t <= 12_000 && verdict == ExtraTurn.Next.WAIT) { verdict = old.next(t, voiceTalking = t < 12_000, blocked = false); t += 50 }
        assertEquals(ExtraTurn.Next.DROP, verdict)
    }

    @Test fun anExtraLineStillDropsWhenSomethingElseHoldsItOrItGrowsStale() {
        // The chime or a line of its own: those still count toward its wait.
        val blocked = ExtraTurn(startAt = 0, waitMs = 8_000, outlastsTheVoice = true)
        assertEquals(ExtraTurn.Next.WAIT, blocked.next(4_000, voiceTalking = false, blocked = true))
        assertEquals(ExtraTurn.Next.DROP, blocked.next(8_100, voiceTalking = false, blocked = true))
        // However long the voice talks, never more than MAX_MS after it was asked for.
        val stale = ExtraTurn(startAt = 0, waitMs = 8_000, outlastsTheVoice = true)
        assertEquals(ExtraTurn.Next.DROP, stale.next(ExtraTurn.MAX_MS + 1, voiceTalking = true, blocked = false))
        // A line whose words a bubble shows keeps its short wait, voice or not (its words would be gone).
        val shown = ExtraTurn(startAt = 500, waitMs = 1_200, outlastsTheVoice = false)
        assertEquals(ExtraTurn.Next.WAIT, shown.next(100, voiceTalking = false, blocked = false))
        assertEquals(ExtraTurn.Next.WAIT, shown.next(1_000, voiceTalking = true, blocked = false))
        assertEquals(ExtraTurn.Next.DROP, shown.next(1_800, voiceTalking = true, blocked = false))
    }
}
