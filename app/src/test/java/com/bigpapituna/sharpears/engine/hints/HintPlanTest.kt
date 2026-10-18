package com.bigpapituna.sharpears.engine.hints

import com.bigpapituna.sharpears.engine.family.Profile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HintPlanTest {
    /** Every step reached while a wait runs from [from] to [to] ms, sampled every [frameMs] like the frame loop. */
    private fun steps(plan: HintPlan, from: Long, to: Long, frameMs: Long = 16): List<Pair<Long, HintStep>> {
        val out = mutableListOf<Pair<Long, HintStep>>()
        plan.begin(from)
        var t = from
        while (t <= to) { plan.advance(t)?.let { out += (t - from) to it }; t += frameMs }
        return out
    }

    @Test fun grownUpsGlowAtSixPointAtTwelvePlayAtEighteen() {
        val got = steps(HintPlan(Profile.ADULT_HINTS), 1_000, 30_000)
        assertEquals(listOf(HintStep.GLOW, HintStep.POINT, HintStep.PLAY), got.map { it.second })
        got.forEachIndexed { i, (at, _) -> assert(at in (6_000L * (i + 1))..(6_000L * (i + 1) + 16)) { "step $i at $at" } }
    }

    @Test fun kidsGetEachStepSooner() {
        val got = steps(HintPlan(Profile.KID_HINTS), 0, 20_000)
        assertEquals(listOf(HintStep.GLOW, HintStep.POINT, HintStep.PLAY), got.map { it.second })
        got.forEachIndexed { i, (at, _) -> assert(at in (4_000L * (i + 1))..(4_000L * (i + 1) + 16)) { "step $i at $at" } }
    }

    @Test fun eachStepOnceAndNothingAfterTheWaitEnds() {
        val plan = HintPlan(listOf(6, 12, 18))
        plan.begin(0)
        assertNull(plan.advance(5_999))
        assertEquals(HintStep.GLOW, plan.advance(6_000))
        assertNull("reported once", plan.advance(7_000))
        assertEquals(HintStep.GLOW, plan.current())
        plan.end()
        assertNull(plan.advance(13_000))
        assertEquals(HintStep.NONE, plan.current())
        // A new wait starts over.
        plan.begin(20_000)
        assertNull(plan.advance(25_000))
        assertEquals(HintStep.GLOW, plan.advance(26_000))
    }

    @Test fun aLongFrameJumpsToTheLaterStep() {
        val plan = HintPlan(listOf(4, 8, 12))
        plan.begin(0)
        assertEquals(HintStep.POINT, plan.advance(9_000))
        assertEquals(HintStep.PLAY, plan.advance(12_000))
    }

    @Test fun malformedTimingsFallBackToTheGrownUpOnes() {
        assertEquals(listOf(6_000L, 12_000L, 18_000L), HintPlan(listOf(8, 4, 12)).stepsMs)
        assertEquals(listOf(6_000L, 12_000L, 18_000L), HintPlan(listOf(3)).stepsMs)
        assertEquals(listOf(6_000L, 12_000L, 18_000L), HintPlan(listOf(0, 4, 8)).stepsMs)
        assertEquals(listOf(3_000L, 5_000L, 9_000L), HintPlan(listOf(3, 5, 9)).stepsMs)
    }

    @Test fun stepAtMatchesTheThresholds() {
        val plan = HintPlan(Profile.KID_HINTS)
        assertEquals(HintStep.NONE, plan.stepAt(3_999))
        assertEquals(HintStep.GLOW, plan.stepAt(4_000))
        assertEquals(HintStep.POINT, plan.stepAt(8_000))
        assertEquals(HintStep.PLAY, plan.stepAt(60_000))
    }
}
