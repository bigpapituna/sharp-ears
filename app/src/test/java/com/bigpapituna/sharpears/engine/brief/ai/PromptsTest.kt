package com.bigpapituna.sharpears.engine.brief.ai

import com.bigpapituna.sharpears.engine.COURSE
import com.bigpapituna.sharpears.engine.MiniJson
import com.bigpapituna.sharpears.engine.brief.FlagKind
import com.bigpapituna.sharpears.engine.family.ProfileKind
import com.bigpapituna.sharpears.engine.playground.PLAYGROUND_PAGES
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The guardrails are in the prompts, word for word (docs/PARENT-BRIEF-PLAN.md), every flag carries its "why it is
 * normal" from CONTRACT.md, the request carries the redacted findings only, and the worked examples obey the very
 * checks a real reply goes through.
 */
class PromptsTest {
    private val kidBrief = Prompts.brief(ProfileKind.KID)
    private val adultBrief = Prompts.brief(ProfileKind.ADULT)
    private val card = Prompts.kidCard()
    private val kidAnswer = Prompts.answer(ProfileKind.KID)
    private val adultAnswer = Prompts.answer(ProfileKind.ADULT)
    private val all = listOf(kidBrief, adultBrief, card, kidAnswer, adultAnswer)

    /** The library's song titles, from the bundled index. */
    private val songs: Map<String, String> = ((MiniJson.read(File("src/main/assets/songs/index.json").readText()) as Map<*, *>)["songs"] as List<*>)
        .map { it as Map<*, *> }.associate { it["id"] as String to it["title"] as String }
    private val catalog = Titles.course { songs }

    private fun assertHas(prompt: String, vararg sentences: String) =
        sentences.forEach { assertTrue("missing: $it", prompt.contains(it)) }

    @Test fun theBriefCarriesEveryGuardrail() {
        val shared = arrayOf(
            "Use only the findings as facts; never add a number, lesson, song, page, event or reason that is not in them.",
            "Give no advice outside the app: no teachers, books, videos, other apps, equipment, furniture, health or sleep advice.",
            "Whenever something was hard, say why it is normal, using the reason given for its flag below, and give a next step.",
            "Choose recommendations only from the numbered candidates, by their index, and never invent an action.",
            "Numbers belong in the facts, never in the headline.",
            "Be warm, kind, optimistic and factual, like a good coach",
            "Write in sentence case, in plain everyday words.",
            "Never use an em dash or an en dash; use a comma, a full stop or the word \"to\" instead.",
            "Name a lesson, song or page only by its exact title from the titles list, and never name one that is not listed.",
            "No emoji, no markdown, no bullet characters and no quotation marks around titles.",
            "headline: one sentence of at most 110 characters that gives the whole brief in three seconds, with no numbers.",
            "facts: two to four short sentences on what happened, the most important first. The numbers go here.",
            "notes: at most three. Each says where it went well, or pairs something hard with why it is normal and a next step.",
            "recommendations: two or three, fewer only when fewer candidates are given, each at most 160 characters.",
            "sections: only for a day or a week, two to four short paragraphs",
            "Reply with one JSON object and nothing else",
        )
        assertHas(kidBrief, *shared)
        assertHas(adultBrief, *shared)
        assertHas(kidBrief,
            "Compare the child only with their own earlier sessions, days or weeks, as the trends give them.",
            "Never mention or compare with other children or other learners, and never say what is usual for an age.",
            "Never write a name. Write {name} where the child's name belongs, naturally and not in every sentence; the app fills it in.",
            "never \"he\" or \"she\"",
            "praise: one specific sentence about something to praise at home.",
        )
        assertHas(adultBrief,
            "Compare the learner only with their own earlier sessions, days or weeks, as the trends give them.",
            "Never mention or compare with other learners, and never say what is usual for an age.",
            "Never write a name. Write to the learner as \"you\".",
            "Write to them as \"you\", a little drier and shorter than a parent's brief.",
            "Grown-ups have no pup card, no reader mode and no bedtime; never mention them.",
        )
        // The grown-up's prompt never offers what grown-ups never get.
        for (kidOnly in listOf("SETTING reader", "bedtime has", "FRESH_START", "PACE_FOCUS", "PACE_UNLOCK", "TEACHER_ASSIGN",
                "READY_FOR_LETTERS", "READY_FOR_ROAD", "PAGE_STRUGGLE", "LONG_DAYS", "{name}")) {
            assertFalse("adult prompt mentions $kidOnly", adultBrief.contains(kidOnly) || adultAnswer.contains(kidOnly))
        }
    }

    @Test fun everyFlagIsPairedWithWhyItIsNormal() {
        val why = mapOf(
            FlagKind.STUCK_LESSON to "new skills take a few days to settle, and the app lets a step go on purpose so nobody stays stuck",
            FlagKind.STUCK_SONG to "songs come together in pieces",
            FlagKind.NOISY_ROOM to "the microphone hears the whole room",
            FlagKind.HINT_DEPENDENCE to "hints are how the app teaches",
            FlagKind.PAGE_STRUGGLE to "a page can be a big step",
            FlagKind.READY_FOR_LETTERS to "reading the letters already",
            FlagKind.REPLAYING_NOT_MOVING_ON to "replaying favourites is how children build confidence",
            FlagKind.LOW_STARS to "first runs are often rough",
            FlagKind.LEFT_HAND_BEHIND to "the left hand is nearly always the second hand",
            FlagKind.FAR_MISSES to "finding the keys comes before playing them",
            FlagKind.TOY_HEAVY to "play is music too",
            FlagKind.LONG_DAYS to "a soft stop helps some families",
            FlagKind.PRACTICE_DIPPED to "weeks vary",
            FlagKind.SCATTERED to "trying lots of things is curiosity",
        )
        for (p in listOf(kidBrief, kidAnswer)) {
            FlagKind.entries.forEach { assertTrue("${it.name} is not explained", p.contains(it.name)) }
            why.values.forEach { assertTrue("missing why: $it", p.contains(it)) }
        }
        val adultFlags = FlagKind.entries - setOf(FlagKind.READY_FOR_LETTERS, FlagKind.READY_FOR_ROAD, FlagKind.PAGE_STRUGGLE, FlagKind.LONG_DAYS)
        for (p in listOf(adultBrief, adultAnswer)) adultFlags.forEach { assertTrue("${it.name} is not explained", p.contains(it.name)) }
        assertHas(adultBrief, "replaying favourites builds confidence", "staying with one level lets it settle")
    }

    @Test fun thePupsCardAndTheAnswerCarryTheirRules() {
        assertHas(card,
            "Speak to the child in the first person, as the pup: warm, happy and simple, like a good friend.",
            "Use only the findings as facts; never invent a lesson, song, sticker or event.",
            "Only positives: what went well, what they tried, what they stuck with. Never anything hard, wrong, missed or stuck.",
            "Never compare the child with anyone else.",
            "Write two or three short lines, each one sentence of at most twelve words.",
            "Then one next time line that starts with \"Next time, let's try\"",
            "The card is read aloud: write numbers as words, never a number over ten, and no symbols, emoji or abbreviations.",
            "When \"reader\" is false the child does not read yet: never mention letters or note names.",
            "Write {name} where the child's name belongs, at most once; never write a name.",
            "Never use an em dash or an en dash.",
            "Flags are for the grown-ups: never tell the child about them.",
        )
        for (a in listOf(kidAnswer, adultAnswer)) assertHas(a,
            "Answer in two to four short sentences, from the findings only.",
            "If the findings cannot answer the question, say so kindly in one sentence and add what the findings do show.",
            "If the question asks for a comparison with anyone else, or for advice outside the app, kindly say the app only looks at",
            "Mention an action only when it is one of the candidates, in plain words.",
            "Use only the findings as facts; never add a number, lesson, song, page, event or reason that is not in them.",
            "Give no advice outside the app",
            "Whenever something was hard, say why it is normal",
            "Reply with plain text only: no JSON, no lists, no markdown and no emoji.",
        )
        assertFalse("an answer has no headline or candidates by index", kidAnswer.contains("Choose recommendations only"))
    }

    @Test fun thePromptsFollowTheirOwnCopyRules() {
        for (p in all) {
            assertFalse("an em or en dash in a prompt", p.contains(EM) || p.contains(EN))
            assertFalse("a name in a prompt", Regex("(?i)(?<![a-z])(mia|roy|leo)(?![a-z])").containsMatchIn(p))
            assertTrue("two worked examples", p.contains("Example 1\n") && p.contains("Example 2\n") && !p.contains("Example 3"))
        }
    }

    @Test fun theRequestCarriesTheRedactedFindingsOnly() {
        val f = AiFixtures.week()
        val lines = TitleSet(f, AiFixtures.TITLES, AiFixtures.NAME, AiFixtures.ROSTER).lines
        for (user in listOf(Prompts.briefUser(f, lines), Prompts.kidCardUser(f, lines), Prompts.answerUser(f, lines, "How is it going?"))) {
            assertTrue(user.contains("Findings:\n" + f.redacted().encode()))
            assertFalse(user.contains(f.encode()))
            assertFalse("a teacher's note never goes", user.contains("slowly") || user.contains("a note with spaces"))
            assertTrue(user.contains("0: UNSTICK, SET_NEXT, lesson pond-5 (Ta and ti-ti), because STUCK_LESSON"))
            assertTrue(user.contains("lesson pond-3: Copy the frogs") && user.contains("page pond: The pond") && user.contains("song twinkle: Twinkle, Twinkle, Little Star"))
            assertTrue(user.contains("The child does not read yet."))
        }
    }

    @Test fun theExampleTitlesAreTheAppsOwn() {
        val lessons = COURSE.flatMap { it.lessons }.associate { it.id to it.title }
        val pages = PLAYGROUND_PAGES.associate { it.id to it.title }
        for ((k, title) in Prompts.EXAMPLE_TITLES) {
            val (kind, id) = k.split(':')
            val real = when (kind) { "lesson" -> lessons[id]; "song" -> songs[id]; else -> pages[id] }
            assertEquals("the example's $k", real, title)
        }
    }

    @Test fun theWorkedExamplesPassTheChecksARealReplyGoesThrough() {
        fun check(f: com.bigpapituna.sharpears.engine.brief.Findings) = AiReplies(f, TitleSet(f, catalog, "", emptyList()), "Mia", 0)
        val briefs = listOf(Prompts.EX_KID_WEEK to Prompts.EX_KID_WEEK_BRIEF, Prompts.EX_KID_SESSION to Prompts.EX_KID_SESSION_BRIEF,
            Prompts.EX_ADULT_WEEK to Prompts.EX_ADULT_WEEK_BRIEF, Prompts.EX_ADULT_SESSION to Prompts.EX_ADULT_SESSION_BRIEF)
        for ((f, reply) in briefs) {
            val c = check(f).brief(reply)
            assertNotNull(c.dropped.toString(), c.value)
            assertEquals(emptyList<String>(), c.dropped)
            val raw = AiReplies.json(reply)!!
            assertEquals("every recommendation kept", (raw["recommendations"] as List<*>).size, c.value!!.recommendations.size)
            assertTrue("two or three recommendations when there are", c.value!!.recommendations.size >= minOf(2, f.candidates.size))
            assertTrue((raw["facts"] as List<*>).size in 2..4)
        }
        for ((f, reply) in listOf(Prompts.EX_KID_SESSION to Prompts.EX_KID_SESSION_CARD, Prompts.EX_KID_WEEK to Prompts.EX_KID_WEEK_CARD)) {
            val c = check(f).kidCard(reply)
            assertEquals(emptyList<String>(), c.dropped)
            assertNotNull(c.value!!.nextTry)
        }
        for ((f, qa) in Prompts.EX_KID_ANSWERS.map { Prompts.EX_KID_WEEK to it } + Prompts.EX_ADULT_ANSWERS.map { Prompts.EX_ADULT_WEEK to it }) {
            assertNotNull(qa.second, check(f).answer(qa.second).value)
        }
    }
}
