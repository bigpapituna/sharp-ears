package com.bigpapituna.sharpears.engine.playground

import com.bigpapituna.sharpears.engine.COURSE
import com.bigpapituna.sharpears.engine.theory.ArticulationVisual
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Audit 2026-10-20: the night sky's "Long or short" picture was labelled "Legato, smooth" and "Staccato, short". */
class ArticulationLabelsTest {
    @Test fun aKidsTouchPictureSaysLongAndShortNotLegatoAndStaccato() {
        val lessons = COURSE.flatMap { it.lessons }.filter { isPlaygroundLesson(it.id) }
        var seen = 0
        for (l in lessons) for (s in l.steps) for (sc in s.scenes) {
            val v = sc.visual as? ArticulationVisual ?: continue
            seen++
            for (k in v.touches.indices) assertTrue("${l.id}: \"${v.label(k)}\"", !Regex("legato|staccato", RegexOption.IGNORE_CASE).containsMatchIn(v.label(k)))
        }
        assertTrue("the Playground has touch pictures", seen >= 3)
        assertEquals(listOf("Long", "Short"), (lessons.first { it.id == "night-9" }.steps.first().scenes[1].visual as ArticulationVisual).labels)
    }
}
