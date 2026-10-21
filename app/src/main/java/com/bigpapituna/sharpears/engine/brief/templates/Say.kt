package com.bigpapituna.sharpears.engine.brief.templates

import com.bigpapituna.sharpears.engine.brief.Findings
import com.bigpapituna.sharpears.engine.brief.Period
import com.bigpapituna.sharpears.engine.family.ProfileKind

/*
 * The template writer's toolbox: one [Ctx] per brief (the findings, the name, the title lookup, the seed), the line
 * type of the bank, the token filler, and the seeded pick that keeps a week reading the same on Refresh while the next
 * week reads differently.
 */

internal const val DAY_MS = 86_400_000L
internal const val WEEK_MS = 7 * DAY_MS

/**
 * One phrasing in the bank: [kid] is the wording for a grown-up reading about a child (no pronouns, the child's name only
 * where the line says {name}), [adult] the wording for a grown-up reading about themselves ("you"). Most lines are
 * subject-less and read the same either way.
 */
internal class L(val kid: String, val adult: String = kid)

internal fun l(kid: String, adult: String = kid) = L(kid, adult)

/** FNV-1a over the parts: stable on every JVM and across app versions, unlike a hash code of a data class. */
internal fun stableHash(vararg parts: Any?): Int {
    var h = -0x7ee3623b   // 0x811c9dc5
    for (c in parts.joinToString("|")) { h = (h xor c.code) * 16777619 }
    return h
}

private val TOKEN = Regex("[{](\\w+)[}]")
private val WHITESPACE = Regex("\\s+")
private val TITLE_TOKENS = Regex("[{](title|titles|song|songs|first|page)[}]", RegexOption.IGNORE_CASE)

/**
 * Fills {token}s from [vars]; a {Token} with a capital is the same value with its first letter capitalised. Null when a
 * token has no value (the line does not apply to these findings). Values are inserted as they are (never re-scanned).
 */
internal fun fill(template: String, vars: Map<String, String?>, capital: Boolean = true): String? {
    var missing = false
    val out = TOKEN.replace(template) { m ->
        val key = m.groupValues[1]
        val lower = key.replaceFirstChar { it.lowercaseChar() }
        val v = vars[lower]
        if (v == null) { missing = true; "" } else if (key != lower) v.replaceFirstChar { it.uppercaseChar() } else v
    }
    return if (missing) null else if (capital) tidy(out) else out.replace(WHITESPACE, " ").trim()
}

/** Single spaces, a capital to start, nothing else. */
internal fun tidy(s: String): String = s.replace(WHITESPACE, " ").trim().replaceFirstChar { it.uppercaseChar() }

private val WORDS = listOf("zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten")

/** Counts up to ten as words, the rest as digits. */
internal fun words(n: Long): String = if (n in 0..10) WORDS[n.toInt()] else n.toString()
internal fun words(n: Int): String = words(n.toLong())

/** "one day", "five days", "12 minutes". */
internal fun count(n: Long, one: String, many: String = one + "s"): String = words(n) + " " + if (n == 1L) one else many

/** "once", "twice", "three times", "12 times". */
internal fun timesWord(n: Long): String = when (n) { 1L -> "once"; 2L -> "twice"; else -> words(n) + " times" }

internal fun percent(n: Long): String = "$n percent"

/** "A", "A and B", "A, B and C". */
internal fun joinList(items: List<String>): String = when (items.size) {
    0 -> ""
    1 -> items[0]
    else -> items.dropLast(1).joinToString(", ") + " and " + items.last()
}

/** The bits of context every line may use. */
internal class Ctx(val f: Findings, name: String, private val titleOf: (String) -> String, val seed: Long) {
    val kid = f.profileKind == ProfileKind.KID
    val period = f.period

    /** The first name as given, tidied; blank for no name. */
    val name: String = name.trim().replace(WHITESPACE, " ").take(24)

    /** What week it is, so the same week reads the same on Refresh and the next one reads differently. */
    val epoch: Long = Math.floorDiv(f.range.toMs, WEEK_MS)

    val span = when (period) { Period.SESSION -> "this session"; Period.DAY -> "today"; Period.WEEK -> "this week" }
    val spanNoun = when (period) { Period.SESSION -> "session"; Period.DAY -> "day"; Period.WEEK -> "week" }
    val earlier = when (period) { Period.SESSION -> "earlier sessions"; Period.DAY -> "recent days"; Period.WEEK -> "recent weeks" }

    /** A title with no dashes and no braces, or "" when the lookup has none. */
    fun title(id: String): String {
        val raw = runCatching { titleOf(id) }.getOrDefault("")
        return raw.replace(Char(0x2014), '-').replace(Char(0x2013), '-').replace("{", "").replace("}", "").replace(WHITESPACE, " ").trim()
    }

    /** The title in typographic double quotes for the parent's text, or null with no title known. */
    fun quoted(id: String): String? = title(id).takeIf { it.isNotEmpty() }?.let { Char(0x201C) + it + Char(0x201D) }

    /** Up to [max] quoted titles joined, with "and two more" when there are more; null when none has a title. */
    fun titles(ids: List<String>, max: Int = 3): String? {
        val named = ids.distinct().mapNotNull(::quoted)
        if (named.isEmpty()) return null
        val shown = named.take(max)
        val more = named.size - shown.size
        return joinList(shown + if (more > 0) listOf(words(more.toLong()) + " more") else emptyList())
    }

    /** A page's title with the article in lower case: "the pond". */
    fun pageLower(id: String): String? = title(id).takeIf { it.isNotEmpty() }?.let { if (it.startsWith("The ")) "the " + it.drop(4) else it }

    /** The seeded hash base for one slot of this brief; the week is added on top (see [phrase]). */
    private fun base(slot: String): Int = stableHash(slot, period.name, seed)

    /** Picks index [i] of [n] for [slot]: a fixed point for the week, a step on for each new week, never random. */
    fun index(slot: String, n: Int, extra: Int = 0): Int = Math.floorMod(base(slot).toLong() + epoch + extra, n.toLong()).toInt()

    /** The usable phrasings of [bank] for these [vars] (the lines whose tokens all have values), in bank order. */
    fun usable(bank: List<L>, vars: Map<String, String?>, capital: Boolean = true): List<String> =
        bank.mapNotNull { fill(if (kid) it.kid else it.adult, vars, capital) }.distinct()

    /**
     * One phrasing from [bank] chosen for [slot] (and [extra], to vary a second use of one kind); null when none applies.
     * A phrasing that names the title is preferred over one that does not, when two or more do.
     */
    fun phrase(slot: String, bank: List<L>, vars: Map<String, String?>, extra: Int = 0, capital: Boolean = true): String? {
        val pairs = bank.mapNotNull { line ->
            val source = if (kid) line.kid else line.adult
            fill(source, vars, capital)?.let { source to it }
        }
        val titled = pairs.filter { TITLE_TOKENS.containsMatchIn(it.first) }
        val u = (if (titled.size >= 2) titled else pairs).map { it.second }.distinct()
        return if (u.isEmpty()) null else u[index(slot, u.size, extra)]
    }

    /** The tokens every line knows. */
    fun common(): Map<String, String?> = mapOf(
        "span" to span, "spanNoun" to spanNoun, "earlier" to earlier,
        "name" to name.takeIf { kid && it.isNotEmpty() },
    )
}
