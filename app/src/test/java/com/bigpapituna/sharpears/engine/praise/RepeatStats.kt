package com.bigpapituna.sharpears.engine.praise

import java.io.File

/**
 * Measures how a praise source repeats itself (the QA praise pass): feed it the ids in the order they were heard; it reports
 * how many picks repeated the one just before, the smallest gap between two of the same line, and the mean gap as a share
 * of the pool's size (1.0 or more is what a perfect shuffle bag gives).
 */
class RepeatStats(val name: String, val pool: Int) {
    private val last = HashMap<String, Int>()
    private var prev: String? = null
    var picks = 0; private set
    var immediate = 0; private set
    var minGap = Int.MAX_VALUE; private set
    private var gapSum = 0L
    private var gaps = 0
    private val seen = HashSet<String>()

    fun add(id: String) {
        last[id]?.let { at -> val g = picks - at; if (g < minGap) minGap = g; gapSum += g; gaps++ }
        if (id == prev) immediate++
        last[id] = picks; prev = id; picks++; seen += id
    }

    val distinct get() = seen.size
    val meanGapPerPool get() = if (gaps == 0 || pool == 0) 0.0 else gapSum.toDouble() / gaps / pool

    fun row() = "%-34s pool=%2d picks=%3d distinct=%2d immediate=%3d minGap=%s meanGap/pool=%.2f".format(
        name, pool, picks, distinct, immediate, if (minGap == Int.MAX_VALUE) "-" else minGap.toString(), meanGapPerPool)

    companion object {
        /** Appends [lines] under a heading to build/praise-sim-<tag>.txt (the report the QA pass reads). */
        fun write(tag: String, heading: String, lines: List<String>) {
            val f = File("build/praise-sim-$tag.txt"); f.parentFile.mkdirs()
            f.appendText("== $heading\n" + lines.joinToString("\n") + "\n")
        }
    }
}

/** A store that lives in a map: the "app restart" in the simulations is a new [PraiseBags] bound to the same one. */
class MapStore : PraiseStore {
    val map = HashMap<String, String>()
    override fun read(key: String) = map[key]
    override fun write(key: String, value: String) { map[key] = value }
}
