package com.bigpapituna.sharpears.engine.brief

/**
 * The child's card (docs/PARENT-BRIEF-PLAN.md): what the pup says at the end of a session, shown as a card and spoken
 * through the app's voice. The writer made the words ([KidCard]); this keeps them fit to show and to say: the same rules as
 * every kid line (no dashes or symbol glyphs, short, sentence case, no scolding words), and one rule for when a card is due.
 */
object KidCardWords {
    /** A card shows at most this many lines, then the "next time" line. */
    const val MAX_LINES = 3
    /** The pup's bubble holds about four lines on a small phone; KidCopyTest allows 110 characters. */
    const val MAX_LINE = 110

    private val DASHES = Regex("\\s*[\u2014\u2013]\\s*|\\s+-\\s+")
    /** Anything that is not a letter, digit, space or sentence punctuation (a hyphen inside a word stays: "ti-ti"). */
    private val SYMBOLS = Regex("[^\\p{L}\\p{N}\\s.,!?'’:;()\"-]")

    /** One line cleaned for showing and speaking: dashes become commas, symbols go, "&" is "and", spaces are single, it ends like a sentence. */
    fun clean(line: String): String {
        var s = line.replace("&", " and ").replace(DASHES, ", ").replace(SYMBOLS, " ").replace(Regex("\\s+"), " ").trim()
        s = s.replace(Regex("\\s+([.,!?;:])"), "$1").replace(Regex("^[,;:.\\s]+"), "").replace(Regex(",\\s*([.!?])"), "$1")
        if (s.isEmpty()) return ""
        s = s.replaceFirstChar { it.uppercase() }
        return if (s.last() in ".!?") s else "$s."
    }

    /** The lines the card shows: cleaned, none empty, at most [MAX_LINES], each cut at a sentence end when it is too long. */
    fun lines(card: KidCard): List<String> =
        card.lines.map(::clean).filter { it.isNotEmpty() }.map(::fit).take(MAX_LINES)

    /** The "next time let's try" line, cleaned; null when the card has none. */
    fun nextTry(card: KidCard): String? = card.nextTry?.let(::clean)?.takeIf { it.isNotEmpty() }?.let(::fit)

    /** What the voice says: the lines and the next try, one after another. */
    fun spoken(card: KidCard): String = (lines(card) + listOfNotNull(nextTry(card))).joinToString(" ")

    /** Cuts a line that is over [MAX_LINE] at the last sentence end that fits, else at the last word with "." so it still reads. */
    private fun fit(line: String): String {
        if (line.length <= MAX_LINE) return line
        val head = line.take(MAX_LINE)
        val end = head.indexOfLast { it in ".!?" }
        if (end > 20) return head.take(end + 1)
        val space = head.lastIndexOf(' ')
        return head.take(if (space > 20) space else MAX_LINE).trimEnd(',', ';', ':', ' ') + "."
    }

    /** The button that opens the parent's note. */
    const val SHOW_MUM = "Show a grown-up"

    /**
     * A card is due when the session had real playing in it ([practisedSeconds] at least [MIN_SECONDS]) and none was shown for
     * this session yet ([shownForSession] is the session's start, remembered by the screen). Never on a quiet session: no
     * card of nothing, and no nagging.
     */
    fun due(practisedSeconds: Int, sessionStart: Long?, shownForSession: Long?): Boolean =
        practisedSeconds >= MIN_SECONDS && sessionStart != null && sessionStart != shownForSession

    const val MIN_SECONDS = 120
}
