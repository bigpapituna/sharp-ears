package com.bigpapituna.sharpears.ui

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** 2.19.0: the QA fix round's wiring (docs/QA-2026-11-02-courses.md), where the fix is a call in a screen or a host. */
class QaFixWiringTest {
    private fun src(path: String) = File("src/main/java/com/bigpapituna/sharpears", path).readText().replace("\r\n", "\n")

    @Test fun theQaFixesStayWired() {
        val voice = src("audio/Voice.kt")
        // B-03: the phone's engine starts a moment after launch when the model will not say the run-time lines.
        assertTrue("if (enabled && !(prefs.naturalVoice && model.installed)) main.postDelayed(warm, WARM_DELAY_MS)" in voice)
        assertTrue("main.removeCallbacks(warm)" in voice)
        // B-07: one silent prepare warms the clip decoder.
        assertTrue("clips.warmUp(\"voice/\$name\")" in voice)
        // B-02: a recording missing from the listing is checked on disk.
        assertTrue("private val recorded = AssetIndex(" in voice)
        // B-09: a pass with misses is titled "Passed".
        assertTrue("else if (!kid && !report.clean) \"Passed\" else com.bigpapituna.sharpears.engine.praise.Praise.title()" in src("ui/LessonScreen.kt"))
        // B-10: the pretend keyboard reaches sight-reading levels 1 and 2.
        assertTrue("inject = inject,\n                                    midiScript = midiScript)" in src("ui/SightReadScreen.kt"))
    }
}
