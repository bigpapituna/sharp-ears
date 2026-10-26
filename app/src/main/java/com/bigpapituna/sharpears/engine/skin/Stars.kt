package com.bigpapituna.sharpears.engine.skin

import com.bigpapituna.sharpears.engine.Recap
import kotlin.math.abs

/**
 * One to three stars on every recap (docs/KIDS-PLAN.md section 3: "heard it, heard it in time, heard it twice"),
 * shown to everyone; grown-ups also see the accuracy number, kids never see a number.
 *
 * A recap holds one run, so "twice" cannot be counted from it; the third star is earned by playing it cleanly
 * instead. Derived from the recap's accuracy and timing fields (decided 2026-10-18):
 * - Perform (the notes keep moving): ★ for reaching the end (you played it); ★★ at [IN_TIME_FROM] percent or more of
 *   the notes in time, the same line as RunWords.CELEBRATE_FROM; ★★★ at [ALL_FROM] percent or more with the
 *   average timing within [TIGHT_MS] ms (RunWords' "a hair early/late" band or better).
 * - Wait (the notes wait, no timing): ★ for reaching the end; ★★ with at most [FEW_WRONG] wrong keys; ★★★ with none.
 * Never zero: a recap only appears once a run reached its end, and getting there earns the first star.
 *
 * 2.14, the third star in one rule ([three]): a clean run (no wrong key in Wait, every note in time in Perform) or
 * [CLEAN_FROM] percent or more in Perform, with nothing let go (no note the app played after the third try, no round the
 * pup let go); 90 percent with the timing within [TIGHT_MS] still counts. Sam played Twinkle at 98 percent with his timing
 * 126 ms ahead and got two stars: he gets three now.
 *
 * 2.14 merge, two rules, each in its place (never or-ed): a lesson step's stars are [three] and [perform] (said on the
 * lesson's finish as [LESSON_RULE]); a song's Perform run is engine/PerformScore.kt songStars through [of] (said on the
 * recap as SongStars.RULE).
 */
object Stars {
    const val MAX = 3
    const val IN_TIME_FROM = 60
    const val ALL_FROM = 90
    /** 2.14: from here a Perform run earns the third star whatever its timing. */
    const val CLEAN_FROM = 98
    const val TIGHT_MS = 80
    const val FEW_WRONG = 3

    /**
     * The 3-star rule, the one place it is decided: [recap] a Perform run's (null: the notes waited), [wrongKeys] a wait
     * run's, [letGos] notes or rounds let go after their third try (any of them keeps it at two at most).
     */
    fun three(recap: Recap?, wrongKeys: Int, letGos: Int = 0): Boolean = letGos <= 0 && if (recap != null)
        recap.accuracy >= CLEAN_FROM || (recap.accuracy >= ALL_FROM && abs(recap.biasMs) < TIGHT_MS)
        else wrongKeys <= 0

    fun perform(r: Recap, letGos: Int = 0): Int = when {
        three(r, 0, letGos) -> 3
        r.accuracy >= IN_TIME_FROM -> 2
        else -> 1
    }

    fun waitRun(wrongKeys: Int, letGos: Int = 0): Int = when {
        three(null, wrongKeys, letGos) -> 3
        wrongKeys <= FEW_WRONG -> 2
        else -> 1
    }

    /**
     * Stars for a song run's recap: a Perform [recap] by the song rule (2.14 depth, engine/PerformScore.kt songStars: 98
     * percent or more played and nine notes in ten in time for three), else a wait run with [wrongKeys]; [letGos] as in
     * [three] (2.14 levels-ages: anything let go keeps it at two at most). A lesson step keeps [perform] and [three]: the two
     * rules are scoped, never or-ed (2.14 merge).
     */
    fun of(recap: Recap?, wrongKeys: Int, letGos: Int = 0): Int =
        recap?.let { com.bigpapituna.sharpears.engine.songStars(it).let { s -> if (letGos > 0) minOf(s, 2) else s } } ?: waitRun(wrongKeys, letGos)

    /**
     * 2.14 merge: a lesson's three-star rule ([three], a step at a time) as its finish says it, in the song recap's frame
     * (engine/PerformScore.kt SongStars.RULE: "3 stars: 98% or more, and 9 of 10 notes in time."). Grown-ups and big kids only:
     * a little kid's finish has no numbers. "80 ms" is held together (a no-break space), so a narrow line never strands "ms".
     */
    val LESSON_RULE = "3 stars: steps played clean or at 98% or more (90% with the timing within 80" + Typography.nbsp + "ms), and nothing let go."

    /** The lesson against the rule, as the recap's "This run:" line: "This lesson: 1 of 3 steps at 3 stars." */
    fun lessonLine(steps: List<Int>): String =
        "This lesson: ${steps.count { it >= MAX }} of ${steps.size} ${if (steps.size == 1) "step" else "steps"} at 3 stars."

    /** The lines under a lesson finish's stars: the rule, then this lesson. */
    fun lessonLines(steps: List<Int>): List<String> = listOf(LESSON_RULE, lessonLine(steps))

    /** What TalkBack says for the row. */
    fun spoken(stars: Int): String = when (stars.coerceIn(1, MAX)) {
        1 -> "One star of three"
        2 -> "Two stars of three"
        else -> "Three stars of three"
    }
}
