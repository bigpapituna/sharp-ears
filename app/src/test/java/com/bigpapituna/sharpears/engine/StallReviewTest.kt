package com.bigpapituna.sharpears.engine

import org.junit.Test
import java.io.File

/**
 * Review of stuck moments saved on the phone (scripts\pull-stalls.ps1 copies them to src/test/resources/stalls).
 * Each clip is replayed through the matcher the way wait mode drove it, and for every expected note the report
 * says whether it was heard and, if not, why the matcher turned it down (its reasons, most common first, and the
 * strongest candidates). Writes build/stall-review.txt. Report only: it never fails.
 */
class StallReviewTest {
    private val byName = (0..127).associateBy { noteName(it) }

    @Test fun reviewStuckMoments() {
        val wavs = File("src/test/resources/stalls").listFiles { f -> f.extension == "wav" }.orEmpty().sortedBy { it.name }
        if (wavs.isEmpty()) { println("no stuck moments; pull them with scripts\\pull-stalls.ps1"); return }
        val out = StringBuilder()
        for (wav in wavs) {
            val info = File(wav.path.removeSuffix(".wav") + ".txt").takeIf { it.exists() }?.readText().orEmpty()
            fun field(k: String) = Regex("(?m)(?:^|\\s)$k=(\\S+)").find(info)?.groupValues?.get(1)
            // Answer-key clips name the notes the keyboard says the mic got wrong ("missed C6", "false E4") instead.
            val expected = (field("expected")?.split(",")?.mapNotNull(byName::get)
                ?: Regex("(?m)^\\s+\\S+\\s+(?:missed|false) (\\S+)$").findAll(info).mapNotNull { byName[it.groupValues[1]] }.toList()).toSet()
            val group = field("group")?.split(",")?.mapNotNull(byName::get)?.toSet()?.ifEmpty { null } ?: expected
            out.append("=== ${wav.nameWithoutExtension}\n").append(info.lines().take(4).joinToString("\n") { "  $it" }).append('\n')
            if (expected.isEmpty()) { out.append("  (no expected notes in the report)\n"); continue }

            val audio = Wav.read(wav)
            val m = NoteMatcher(audio.sampleRate)
            m.group = group
            val heard = LinkedHashMap<Int, Double>()
            val rejects = HashMap<Int, MutableList<Pair<Double, String>>>()   // per note: (rise dB, reason)
            m.trace = { line ->
                Regex("reject (\\S+) at \\d+ ms: rise (-?[\\d.]+) dB, (.*)").find(line)?.destructured?.let { (n, rise, why) ->
                    byName[n]?.let { rejects.getOrPut(it) { mutableListOf() } += rise.toDouble() to why }
                }
            }
            var i = 0
            while (i < audio.samples.size) {
                m.expected = expected - heard.keys
                val n = minOf(480, audio.samples.size - i)
                for (e in m.process(audio.samples.copyOfRange(i, i + n))) if (e is MicEvent.Strike && e.midi !in heard) heard[e.midi] = e.sample / audio.sampleRate.toDouble()
                i += n
            }
            for (note in expected.sorted()) {
                val at = heard[note]
                if (at != null) { out.append("  ${noteName(note)}: heard at %.2f s in the replay\n".format(at)); continue }
                val rs = rejects[note].orEmpty()
                out.append("  ${noteName(note)}: NOT heard. ${rs.size} candidates turned down\n")
                rs.groupingBy { it.second.substringBefore(" (").substringBefore(" |") }.eachCount().entries.sortedByDescending { it.value }.take(5)
                    .forEach { (why, n) -> out.append("    $n x $why\n") }
                rs.sortedByDescending { it.first }.take(3).forEach { (rise, why) -> out.append("    strongest: rise %.1f dB, %s\n".format(rise, why.take(160))) }
            }
        }
        File("build/stall-review.txt").writeText(out.toString())
        print(out)
    }
}
