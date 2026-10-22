package com.bigpapituna.sharpears.engine.brief.ai

import com.bigpapituna.sharpears.engine.COURSE
import com.bigpapituna.sharpears.engine.brief.Findings
import com.bigpapituna.sharpears.engine.playground.PLAYGROUND_PAGES
import com.bigpapituna.sharpears.engine.playground.pageOf

/**
 * The titles the AI writer looks up on the phone for the ids in the findings. Titles are safe to send (they are the
 * app's own words); names are not, so a title that holds a person's name is scrubbed first ([TitleSet]).
 */
interface Titles {
    fun lesson(id: String): String?
    fun song(id: String): String?
    fun page(id: String): String?
    /** Every lesson, song and page title the app knows: a reply naming one the findings do not is caught by these. */
    fun all(): Collection<String>

    companion object {
        /**
         * Lessons from COURSE and pages from PLAYGROUND_PAGES (both engine); songs from [songs], id to title, which the
         * integrator reads from SongRepository.all() (bundled and imported). Read once per call of [all] or [song].
         */
        fun course(songs: () -> Map<String, String> = { emptyMap() }): Titles = CourseTitles(songs)
    }
}

private class CourseTitles(private val songs: () -> Map<String, String>) : Titles {
    private val lessons by lazy {
        COURSE.flatMap { it.lessons }.associate { it.id to it.title } + (com.bigpapituna.sharpears.engine.family.Placement.CHECK_ID to com.bigpapituna.sharpears.engine.family.Placement.CHECK_TITLE)
    }
    private val pages by lazy { PLAYGROUND_PAGES.associate { it.id to it.title } }
    override fun lesson(id: String) = lessons[id]
    /** A library song, or a pop song's run ("pop-<id>", songs by chords: the content's title). */
    override fun song(id: String) = songs()[id] ?: com.bigpapituna.sharpears.engine.chords.PopPlay.titleOfRun(id)
    override fun page(id: String) = pages[id]
    override fun all(): Collection<String> = lessons.values + pages.values + songs().values
}

/** One line of the titles list the model is given: "lesson pond-3: Copy the frogs". */
data class TitleLine(val kind: String, val id: String, val title: String) {
    override fun toString() = "$kind $id: $title"
}

/**
 * The titles for one findings, as the model sees them, and the checks a reply's prose goes through.
 *
 * - [lines]: a title for every lesson, song and page the findings mention (pages also for the findings' lessons), in the
 *   findings' order. A title holding [name] has it replaced by {name} (the app puts it back); a title holding anyone
 *   else's name from [otherNames] is left out, so the model cannot name that song or lesson at all.
 * - [allowed]: those titles. [forbidden]: every other title the app knows, scrubbed the same way, for the reply check.
 * - Each line carries the id the model sees: the [aliases] stand-in for an id that holds a name ([IdAliases]).
 */
class TitleSet(findings: Findings, titles: Titles, name: String, otherNames: Collection<String>, aliases: Map<String, String> = emptyMap()) {
    val lines: List<TitleLine>
    val allowed: Set<String>
    val forbidden: Set<String>

    init {
        val others = otherNames.filter { !it.trim().equals(name.trim(), ignoreCase = true) }
        fun scrub(t: String): String? {
            if (others.any { n -> Names.contains(t, n) }) return null
            return Names.replace(t, name, Prompts.NAME)
        }
        val out = LinkedHashMap<String, TitleLine>()
        fun add(kind: String, id: String, title: String?) {
            val t = title?.trim()?.takeIf { it.isNotEmpty() }?.let(::scrub) ?: return
            out.putIfAbsent("$kind:$id", TitleLine(kind, aliases[id] ?: id, t))
        }
        val lessonIds = findings.lessonIds()
        lessonIds.forEach { add("lesson", it, titles.lesson(it)) }
        findings.songIds().forEach { add("song", it, titles.song(it)) }
        val pageIds = LinkedHashSet<String>()
        findings.flags.forEach { f -> f.page?.let(pageIds::add) }
        findings.praise.forEach { p -> p.page?.let(pageIds::add) }
        findings.candidates.forEach { c -> c.params["page"]?.let(pageIds::add) }
        findings.actions.forEach { a -> a.params["page"]?.let(pageIds::add) }
        lessonIds.forEach { id -> pageOf(id)?.let { pageIds.add(it.id) } }
        pageIds.forEach { add("page", it, titles.page(it)) }
        lines = out.values.toList()
        allowed = lines.map { it.title }.toSet()
        forbidden = titles.all().mapNotNull { t -> t.trim().takeIf { it.isNotEmpty() }?.let { Names.replace(it, name, Prompts.NAME) } }
            .filter { it !in allowed }.toSet()
    }

    /** The allowed titles, longest first, case-insensitive: blanked from prose before the other checks. */
    internal val allowedPatterns: List<Regex> by lazy {
        allowed.sortedByDescending { it.length }.map { Regex(Regex.escape(it), RegexOption.IGNORE_CASE) }
    }

    /**
     * The other titles as whole phrases, case-sensitive as the app writes them, two words or more: many titles are
     * everyday words ("Skips", "Rests") or phrases ("hands together", "soft and loud") a sentence may use in passing.
     */
    internal val forbiddenPatterns: List<Regex> by lazy {
        forbidden.filter { it.split(Regex("\\s+")).size >= 2 }
            .map { Regex("(?<![\\p{L}\\p{N}])" + Regex.escape(it) + "(?![\\p{L}\\p{N}])") }
    }

    /** The title of [id] of [kind] ("lesson", "song", "page") as the model sees it, or null. */
    fun of(kind: String, id: String): String? = lines.firstOrNull { it.kind == kind && it.id == id }?.title
}

/**
 * Ids that would carry a name to the model. An imported song's id is made from its file name
 * (SongRepository.userSongId: "Mia's lullaby" is user-mia-s-lullaby), and [Findings.redacted] keeps it because it is
 * id-shaped. Every lesson, song or page id holding one of the roster's names, spelled the way ids are made, is replaced
 * by a stand-in ("hidden-1") in what is sent; its title goes under the stand-in, scrubbed like any other.
 */
internal object IdAliases {
    /** Original id to stand-in, for the ids in [f] that hold one of [names]. Empty when none do (the usual case). */
    fun of(f: Findings, names: Collection<String>): Map<String, String> {
        val forms = names.flatMap { n -> listOf(n) + n.split(Regex("\\s+")) }.map(::idForm).filter { it.length >= 2 }.distinct()
        if (forms.isEmpty()) return emptyMap()
        val ids = LinkedHashSet<String>()
        ids += f.lessonIds(); ids += f.songIds()
        f.flags.forEach { it.page?.let(ids::add) }; f.praise.forEach { it.page?.let(ids::add) }
        (f.candidates.map { it.params } + f.actions.map { it.params }).forEach { p -> REFS.forEach { k -> p[k]?.let(ids::add) } }
        return ids.filter { id -> forms.any { "-${idForm(id)}-".contains("-$it-") } }
            .withIndex().associate { (i, id) -> id to "hidden-${i + 1}" }
    }

    /** [f] with every id in [aliases] replaced, wherever an id can be. */
    fun apply(f: Findings, aliases: Map<String, String>): Findings {
        if (aliases.isEmpty()) return f
        fun a(id: String) = aliases[id] ?: id
        fun ids(l: List<String>) = l.map(::a)
        fun params(p: Map<String, String>) = p.mapValues { (k, v) -> if (k in REFS) a(v) else v }
        return f.copy(
            facts = f.facts.map { it.copy(lessons = ids(it.lessons), songs = ids(it.songs)) },
            flags = f.flags.map { it.copy(lessons = ids(it.lessons), songs = ids(it.songs), page = it.page?.let(::a)) },
            praise = f.praise.map { it.copy(lessons = ids(it.lessons), songs = ids(it.songs), page = it.page?.let(::a)) },
            candidates = f.candidates.map { it.copy(params = params(it.params)) },
            actions = f.actions.map { it.copy(params = params(it.params)) },
            stats = f.stats.copy(lessons = f.stats.lessons.map { it.copy(lessonId = a(it.lessonId)) }, songs = f.stats.songs.map { it.copy(songId = a(it.songId)) }),
        )
    }

    /** Params that name a lesson, a song or a page. */
    private val REFS = setOf("lesson", "song", "page", "after")

    /** The way SongRepository.userSongId spells a name in an id: lower case, runs of anything else one dash. */
    private fun idForm(s: String) = s.lowercase(java.util.Locale.ROOT).replace(Regex("[^a-z0-9]+"), "-").trim('-')
}

/** Whole-word, case-insensitive name matching (the way StallWatch.scrubNames finds names in logs). */
internal object Names {
    private fun pattern(name: String) = Regex("(?<![\\p{L}\\p{N}])" + Regex.escape(name.trim()) + "(?![\\p{L}\\p{N}])", RegexOption.IGNORE_CASE)

    fun contains(text: String, name: String): Boolean = name.trim().length >= 2 && pattern(name).containsMatchIn(text)

    fun replace(text: String, name: String, with: String): String =
        if (name.trim().length < 2) text else pattern(name).replace(text, Regex.escapeReplacement(with))
}
