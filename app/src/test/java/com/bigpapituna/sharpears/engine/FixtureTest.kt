package com.bigpapituna.sharpears.engine

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Replays real recordings (src/test/resources/fixtures/<name>.wav + <name>.notes with MIDI truth) through the
 * matcher the way wait mode drives it: the expected set is always the next group of truth notes.
 * Pass criteria from the plan: at least 90% of notes heard, at most one false trigger per 30 s.
 * With no fixtures yet, it reports that and passes.
 */
class FixtureTest {
    private val dir = File("src/test/resources/fixtures")

    data class Result(val name: String, val notes: Int, val heard: Int, val early: Int, val falseWrongs: Int,
                      val seconds: Double, val medianLatencyMs: Int?)

    fun evaluate(wav: File, cfg: MatcherConfig = MatcherConfig()): Result {
        val audio = Wav.read(wav)
        require(audio.sampleRate == 48_000) { "${wav.name}: expected 48 kHz" }
        val fs = audio.sampleRate
        val truth = File(wav.path.removeSuffix(".wav") + ".notes").readLines()
            .filter { it.isNotBlank() && !it.startsWith("#") }
            .map { it.split(",").map(String::trim) }
            .filter { it[2].toInt() > 0 && it[0].toLong() >= 0 }
            .map { it[0].toLong() to it[1].toInt() }
            .sortedBy { it.first }
        // Truth notes starting within 60 ms of each other form one group (a chord).
        val groups = mutableListOf<MutableList<Pair<Long, Int>>>()
        for (t in truth) if (groups.isEmpty() || t.first - groups.last().first().first > fs * 60 / 1000) groups += mutableListOf(t) else groups.last() += t

        val m = NoteMatcher(fs, cfg)
        var gi = 0
        val pending = groups.firstOrNull()?.map { it.second }?.toMutableSet() ?: mutableSetOf()
        var heard = 0; var early = 0; var wrongs = 0
        val latencies = mutableListOf<Double>()
        val silence = groups.isEmpty()
        var i = 0
        while (i < audio.samples.size) {
            val nowSample = i.toLong()
            // A player would give up on a group 500 ms after it sounded.
            while (gi < groups.size && nowSample > groups[gi].first().first + fs / 2) {
                gi++; pending.clear(); if (gi < groups.size) pending += groups[gi].map { it.second }
            }
            m.expected = if (silence) setOf(48, 60, 64, 67, 72) else pending.toSet()
            m.group = if (gi < groups.size) groups[gi].map { it.second }.toSet() else emptySet()
            val n = minOf(480, audio.samples.size - i)
            for (e in m.process(audio.samples.copyOfRange(i, i + n))) {
                when (e) {
                    is MicEvent.Strike -> {
                        val onset = groups.getOrNull(gi)?.firstOrNull { it.second == e.midi }?.first
                        if (silence || onset == null || e.sample < onset - fs * 30 / 1000) { early++; continue }
                        if (pending.remove(e.midi)) { heard++; latencies += (e.sample - onset) * 1000.0 / fs }
                        if (pending.isEmpty() && gi < groups.size) { gi++; if (gi < groups.size) pending += groups[gi].map { it.second } }
                    }
                    // A wrong-note flash during the recording is false: the player played the truth notes.
                    is MicEvent.Wrong -> wrongs++
                }
            }
            i += n
        }
        return Result(wav.nameWithoutExtension, truth.size, heard, early, wrongs, audio.samples.size / fs.toDouble(),
            latencies.sorted().getOrNull(latencies.size / 2)?.toInt())
    }

    /** The harness itself: a synthetic fixture (C major scale up, one wrong-free pass) must score fully. */
    @Test fun harnessScoresASyntheticFixture() {
        val tmp = File("build/tmp/fixture-selftest").apply { mkdirs() }
        val scale = listOf(60, 62, 64, 65, 67, 69, 71, 72)
        val notes = scale.mapIndexed { i, m -> SynthNote(m, 500 + i * 700.0, 600.0) }
        Wav.write(File(tmp, "scale.wav"), PianoSynth.render(notes, 6500.0, noiseDb = -60.0), 48_000)
        fun truth(shiftMs: Double) = notes.joinToString("") { "${((it.startMs + shiftMs) * 48).toLong()},${it.midi},80\n" }
        File(tmp, "scale.notes").writeText("# synthetic\n" + truth(0.0))
        val r = evaluate(File(tmp, "scale.wav"))
        println("self-test: $r")
        assertTrue("harness heard ${r.heard}/${r.notes}", r.heard == 8 && r.notes == 8 && r.early == 0 && r.falseWrongs == 0)
        // A shifted truth file (notes claimed 400 ms later than played) must show up as early strikes, not hits.
        File(tmp, "scale.notes").writeText(truth(400.0))
        val shifted = evaluate(File(tmp, "scale.wav"))
        assertTrue("shifted truth should look wrong: $shifted", shifted.heard < 8)
    }

    @Test fun recordedFixturesMeetThePlan() {
        val wavs = dir.listFiles { f -> f.extension == "wav" && File(f.path.removeSuffix(".wav") + ".notes").exists() }.orEmpty().sortedBy { it.name }
        if (wavs.isEmpty()) { println("no fixtures in $dir yet; record them on the phone and run scripts\\pull-fixtures.ps1"); return }
        val failures = mutableListOf<String>()
        for (w in wavs) {
            val r = evaluate(w)
            val falsePer30s = (r.early + r.falseWrongs) / (r.seconds / 30)
            val rate = if (r.notes == 0) 1.0 else r.heard.toDouble() / r.notes
            println("%-24s %3d/%3d heard (%3.0f%%), %d early, %d false wrong, %.1f s, median latency %s ms"
                .format(r.name, r.heard, r.notes, rate * 100, r.early, r.falseWrongs, r.seconds, r.medianLatencyMs))
            if (rate < 0.9 || falsePer30s > 1.0) failures += r.name
        }
        assertTrue("fixtures below the plan's bar (90% heard, <= 1 false trigger per 30 s): $failures", failures.isEmpty())
    }
}
