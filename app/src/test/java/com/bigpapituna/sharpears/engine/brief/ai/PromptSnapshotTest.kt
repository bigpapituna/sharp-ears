package com.bigpapituna.sharpears.engine.brief.ai

import com.bigpapituna.sharpears.engine.family.ProfileKind
import org.junit.Assert.fail
import org.junit.Test
import java.io.File

/**
 * Pins every prompt the model sees, word for word, so a change to what the AI is told is always deliberate. The
 * snapshots are text files in src/test/resources/brief-ai/. When this fails after an edit you meant: read the new text
 * written to build/prompt-snapshots/, copy it over the snapshot, and bump Prompts.VERSION in the same commit (it
 * retires the briefs cached under the old prompt).
 */
class PromptSnapshotTest {
    private val dir = File("src/test/resources/brief-ai")
    private val out = File("build/prompt-snapshots")

    private val prompts: Map<String, () -> String> = linkedMapOf(
        "brief-kid.txt" to { Prompts.brief(ProfileKind.KID) },
        "brief-adult.txt" to { Prompts.brief(ProfileKind.ADULT) },
        "kid-card.txt" to { Prompts.kidCard() },
        "answer-kid.txt" to { Prompts.answer(ProfileKind.KID) },
        "answer-adult.txt" to { Prompts.answer(ProfileKind.ADULT) },
        "user-week-kid.txt" to {
            val f = AiFixtures.week()
            Prompts.briefUser(f, TitleSet(f, AiFixtures.TITLES, AiFixtures.NAME, AiFixtures.ROSTER).lines)
        },
        "version.txt" to { "Prompts.VERSION = ${Prompts.VERSION}\n" },
    )

    @Test fun everyPromptMatchesItsSnapshot() {
        val changed = ArrayList<String>()
        for ((name, make) in prompts) {
            val actual = make().replace("\r\n", "\n")
            val file = File(dir, name)
            val pinned = if (file.exists()) file.readText(Charsets.UTF_8).replace("\r\n", "\n") else null
            if (pinned == actual) continue
            out.mkdirs()
            File(out, name).writeText(actual, Charsets.UTF_8)
            val a = actual.lines(); val p = pinned?.lines().orEmpty()
            val i = (0 until maxOf(a.size, p.size)).firstOrNull { a.getOrNull(it) != p.getOrNull(it) } ?: 0
            changed += if (pinned == null) "$name: no snapshot yet" else
                "$name: line ${i + 1} differs\n  pinned: ${p.getOrNull(i)}\n  now:    ${a.getOrNull(i)}"
        }
        if (changed.isNotEmpty()) fail("The prompts changed. If that is deliberate, copy build/prompt-snapshots/* over " +
            "src/test/resources/brief-ai/ and bump Prompts.VERSION.\n" + changed.joinToString("\n"))
    }
}
