package com.bigpapituna.sharpears.engine.brief

import com.bigpapituna.sharpears.engine.family.ProfileKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** A fake [Effects]: every call is written to [calls], every undoable one gives an Undo that writes "undo ...". */
private class FakeEffects : Effects {
    val calls = ArrayList<String>()
    var boom: Exception? = null
    private fun act(s: String): Undo { boom?.let { throw it }; calls += s; return Undo { calls += "undo $s" } }
    override fun setNext(lessonId: String?, untilMs: Long) = act("setNext $lessonId until $untilMs")
    override fun replay(lessonId: String) { boom?.let { throw it }; calls += "replay $lessonId" }
    override fun practiseTogether(lessonId: String?, songId: String?) { boom?.let { throw it }; calls += "together $lessonId $songId" }
    override fun freshStart(pageId: String) = act("fresh $pageId")
    override fun focus(pageId: String) = act("focus $pageId")
    override fun unlockNext(pageId: String) = act("unlock $pageId")
    override fun changeSetting(setting: SettingId, value: String) = act("setting ${setting.wire} $value")
    override fun assignSong(songId: String, note: String, afterSongId: String?) = act("assign $songId '$note' after $afterSongId")
}

class BriefActionsTest {
    private val fx = FakeEffects()
    private val recorded = ArrayList<Pair<String, Map<String, String>>>()
    private val actions = BriefActions(fx, IdTitles, "Mia", { id, p -> recorded += id to p }, clock = { 1_000L })
    private fun cand(a: ActionId, vararg p: Pair<String, String>) = Candidate(RecommendationKind.UNSTICK, a, mapOf(*p))
    private fun done(o: Outcome) = (o as Outcome.Done).performed

    @Test fun setNextPinsTheLessonForADay() {
        val p = done(actions.perform(cand(ActionId.SET_NEXT, "lesson" to "pond-5")))
        assertEquals(listOf("setNext pond-5 until ${1_000L + BriefActions.DAY_MS}"), fx.calls)
        assertTrue(p.reversible)
        assertTrue("not recorded until committed", recorded.isEmpty())
        p.commit()
        assertEquals(listOf("SET_NEXT" to mapOf("lesson" to "pond-5")), recorded)
    }

    @Test fun setNextWithNoLessonMeansThePathsNext() {
        done(actions.perform(cand(ActionId.SET_NEXT)))
        assertEquals("setNext null until ${1_000L + BriefActions.DAY_MS}", fx.calls.single())
    }

    @Test fun replayAndPractiseTogetherOpenThingsAndAreRecordedAtOnce() {
        val r = done(actions.perform(cand(ActionId.REPLAY_FOR_STARS, "lesson" to "pond-4")))
        assertFalse(r.reversible); assertTrue(r.committed)
        val t = done(actions.perform(cand(ActionId.PRACTISE_TOGETHER, "song" to "twinkle")))
        assertFalse(t.reversible)
        assertEquals(listOf("replay pond-4", "together null twinkle"), fx.calls)
        assertEquals(listOf("REPLAY_FOR_STARS", "PRACTISE_TOGETHER"), recorded.map { it.first })
        assertFalse("nothing to undo", r.undo())
    }

    @Test fun freshStartPaceAndUnlockGoThroughWithTheirPage() {
        done(actions.perform(cand(ActionId.FRESH_START, "page" to "pond")))
        done(actions.perform(cand(ActionId.PACE_FOCUS, "page" to "pond")))
        done(actions.perform(cand(ActionId.PACE_UNLOCK, "page" to "garden")))
        assertEquals(listOf("fresh pond", "focus pond", "unlock garden"), fx.calls)
    }

    @Test fun everySettingGoesThroughWithItsValue() {
        for (s in SettingId.entries) done(actions.perform(cand(ActionId.SETTING, "setting" to s.wire, "value" to "true")))
        assertEquals(SettingId.entries.map { "setting ${it.wire} true" }, fx.calls)
    }

    @Test fun teacherModeAssignsTheChosenSongWithTheNote() {
        val p = done(actions.perform(cand(ActionId.TEACHER_ASSIGN, "after" to "twinkle"), ActionInput("ode-to-joy", "  Slowly, hands apart. ")))
        assertEquals("assign ode-to-joy 'Slowly, hands apart.' after twinkle", fx.calls.single())
        p.commit()
        assertEquals(mapOf("after" to "twinkle", "song" to "ode-to-joy", "note" to "Slowly, hands apart."), recorded.single().second)
    }

    @Test fun aLongNoteIsCutAndTeacherModeNeedsASong() {
        done(actions.perform(cand(ActionId.TEACHER_ASSIGN), ActionInput("a", "x".repeat(500))))
        assertEquals(BriefActions.MAX_NOTE, fx.calls.single().substringAfter("'").substringBefore("'").length)
        assertTrue(actions.perform(cand(ActionId.TEACHER_ASSIGN)) is Outcome.Failed)
    }

    @Test fun undoTakesTheActionBackAndItIsNeverRecorded() {
        val p = done(actions.perform(cand(ActionId.PACE_FOCUS, "page" to "pond")))
        assertTrue(p.undo()); assertTrue(p.undone)
        assertEquals(listOf("focus pond", "undo focus pond"), fx.calls)
        p.commit()
        assertTrue("an undone action is not a thing the grown-up did", recorded.isEmpty())
        assertFalse("only once", p.undo())
    }

    @Test fun commitRecordsOnceAndEndsTheUndo() {
        val p = done(actions.perform(cand(ActionId.FRESH_START, "page" to "pond")))
        p.commit(); p.commit()
        assertEquals(1, recorded.size)
        assertFalse(p.undo())
        assertEquals(listOf("fresh pond"), fx.calls)
    }

    @Test fun aFailureChangesNothingAndRecordsNothing() {
        fx.boom = IllegalStateException("disk full")
        val o = actions.perform(cand(ActionId.SET_NEXT, "lesson" to "pond-5"))
        assertTrue(o is Outcome.Failed)
        assertTrue(recorded.isEmpty())
        fx.boom = NotConnected("x")
        assertTrue((actions.perform(cand(ActionId.FRESH_START, "page" to "pond")) as Outcome.Failed).reason.contains("not connected"))
    }

    @Test fun missingParamsFailInsteadOfGuessing() {
        for (c in listOf(cand(ActionId.REPLAY_FOR_STARS), cand(ActionId.FRESH_START), cand(ActionId.PACE_FOCUS), cand(ActionId.PACE_UNLOCK),
            cand(ActionId.SETTING, "setting" to "nonsense"), cand(ActionId.SETTING)))
            assertTrue("$c", actions.perform(c) is Outcome.Failed)
        assertTrue(fx.calls.isEmpty())
    }

    @Test fun grownUpsGetNoKidOnlyActions() {
        val kidOnly = listOf(cand(ActionId.FRESH_START, "page" to "pond"), cand(ActionId.PACE_FOCUS, "page" to "pond"), cand(ActionId.PACE_UNLOCK, "page" to "pond"),
            cand(ActionId.TEACHER_ASSIGN), cand(ActionId.SETTING, "setting" to "reader", "value" to "true"), cand(ActionId.SETTING, "setting" to "bedtime", "value" to "true"))
        val shared = listOf(cand(ActionId.SET_NEXT), cand(ActionId.REPLAY_FOR_STARS, "lesson" to "a"), cand(ActionId.PRACTISE_TOGETHER),
            cand(ActionId.SETTING, "setting" to "quietRoom", "value" to "true"), cand(ActionId.SETTING, "setting" to "micEasier", "value" to "3"))
        assertTrue(ActionRules.usable(ProfileKind.ADULT, kidOnly).isEmpty())
        assertEquals(shared, ActionRules.usable(ProfileKind.ADULT, shared))
        assertEquals(kidOnly + shared, ActionRules.usable(ProfileKind.KID, kidOnly + shared))
    }

    @Test fun onlyAFreshStartAsksTwice() {
        for (a in ActionId.entries) {
            val v = ActionCopy.of(cand(a, "lesson" to "pond-5", "page" to "pond", "setting" to "reader", "value" to "true"), IdTitles, "Mia")
            assertEquals(a.name, if (a == ActionId.FRESH_START) 2 else 1, v.confirmations)
            if (a == ActionId.FRESH_START) assertTrue(v.secondTitle.isNotEmpty() && v.secondConfirm.isNotEmpty())
        }
    }

    @Test fun theWordsFollowTheCopyRules() {
        val all = ArrayList<ActionView>()
        for (a in ActionId.entries) for (value in listOf("true", "false")) {
            all += ActionCopy.of(cand(a, "lesson" to "pond-5", "page" to "pond", "song" to "twinkle", "after" to "twinkle", "setting" to "reader", "value" to value), IdTitles, "Mia")
            all += ActionCopy.of(cand(a), IdTitles, "Mia")
        }
        for (s in SettingId.entries) for (value in listOf("true", "false", "3.0")) all += ActionCopy.of(cand(ActionId.SETTING, "setting" to s.wire, "value" to value), IdTitles, "Mia")
        val banned = Regex("[—–·#&]| - ")
        for (v in all) for (t in listOf(v.button, v.title, v.body, v.confirm, v.doneLine, v.secondTitle, v.secondBody, v.secondConfirm).filter { it.isNotEmpty() }) {
            assertFalse("no dashes or symbols: $t", banned.containsMatchIn(t))
            assertTrue("sentence case: $t", t.first().isUpperCase())
            assertFalse("no double spaces: $t", "  " in t)
        }
        for (v in all) { assertTrue(v.title, v.title.endsWith("?")); assertTrue(v.button, v.button.length <= 30) }
        assertTrue(all.any { "Mia" in it.body })
    }
}
