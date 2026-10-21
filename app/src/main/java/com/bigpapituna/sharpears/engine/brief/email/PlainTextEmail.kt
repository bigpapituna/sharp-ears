package com.bigpapituna.sharpears.engine.brief.email

/**
 * Turns an [EmailBrief] into the parts an email carries: [EmailRenderer] is what the sender asks for (the HTML and the plain
 * text). Step 2 writes the designed HTML renderer; [PlainTextEmail] writes the text part every email carries, and a plain
 * HTML page from the same words when no designed renderer is given ([PlainTextEmail.renderer]).
 */
fun interface EmailRenderer {
    /** The email's HTML body and its plain-text part. */
    fun render(email: EmailBrief): Pair<String, String>
}

object PlainTextEmail {
    /** The fallback renderer: a plain HTML page and the text part, both from [text]. */
    val renderer = EmailRenderer { e -> html(e) to text(e) }

    /**
     * The text part: every section of design D in order, paragraphs one per line with a blank line between, the details
     * all open (a text reader has no taps), the suggestions numbered with why and where in the app. No markup.
     */
    fun text(e: EmailBrief): String = lines(e).joinToString("\n").replace(Regex("\n{3,}"), "\n\n").trim() + "\n"

    /** A plain HTML body with the same words: headings, paragraphs and lists, every value escaped. */
    fun html(e: EmailBrief): String = buildString {
        append("<!doctype html><html><head><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">")
        append("<title>").append(esc(e.subject)).append("</title></head>")
        append("<body style=\"font-family:Arial,sans-serif;max-width:600px;margin:0 auto;padding:16px;color:#2B2117;background:#FFF8EC\">")
        append("<div style=\"display:none\">").append(esc(e.preheader)).append("</div>")
        e.welcome?.let { w ->
            h(w.title); w.paragraphs.forEach { p(it) }
            append("<p style=\"font-size:28px;letter-spacing:4px\"><b>").append(esc(w.code)).append("</b></p>"); p(w.codeLine)
            h(w.howOften.title, 3); p(w.howOften.where)
            append("<ul>"); w.howOften.choices.forEach { append("<li><b>").append(esc(it.label)).append(":</b> ").append(esc(it.text)).append("</li>") }; append("</ul>")
            p(w.howOften.note)
        }
        with(e.postcard) { p(if (stamp.equals(range, ignoreCase = true)) range else "$stamp, $range"); h(headline); if (chips.isNotEmpty()) p(chips.joinToString(", ")); pawLine(this)?.let { p(it) } }
        h(e.oneMinute.title)
        e.oneMinute.parts.forEach { append("<p><b>").append(esc(it.label)).append("</b> ").append(esc(it.text)).append("</p>") }
        e.oneMinute.sayThis?.let { append("<p><b>").append(esc(it.label)).append("</b> \"").append(esc(it.quote)).append("\"</p>") }
        e.next?.let { n ->
            h(n.title); p(n.intro)
            append("<ol>")
            n.items.forEach { i ->
                append("<li><p><b>").append(esc(i.text)).append("</b> Why: ").append(esc(i.why)).append("</p>")
                i.where?.let { append("<p>").append(esc(i.button ?: EmailWords.DO_IN_APP)).append(": ").append(esc(it)).append(".</p>") }
                append("</li>")
            }
            append("</ol>")
        }
        h(e.details.title)
        e.details.sections.forEach { s -> h(s.title, 3); s.blocks.forEach { b -> blockLines(b).forEach { p(it) } } }
        h(e.signOff.title); p(e.signOff.text); p(e.signOff.signature)
        append("<hr>")
        e.footer.lines.forEach { append("<p style=\"font-size:13px;color:#7A6A52\">").append(esc(it)).append("</p>") }
        append("</body></html>")
    }

    private fun StringBuilder.h(text: String, level: Int = 2) { append("<h$level>").append(esc(text)).append("</h$level>") }
    private fun StringBuilder.p(text: String) { append("<p>").append(esc(text)).append("</p>") }

    private fun lines(e: EmailBrief): List<String> = buildList {
        e.welcome?.let { w ->
            add(w.title); add("")
            w.paragraphs.forEach { add(it); add("") }
            add("Your code: ${w.code}"); add(w.codeLine); add("")
            add(w.howOften.title); add(w.howOften.where)
            w.howOften.choices.forEach { add("${it.label}: ${it.text}") }
            add(w.howOften.note); add(""); add("")
        }
        with(e.postcard) {
            add(if (stamp.equals(range, ignoreCase = true)) range else "$stamp, $range"); add("")
            add(headline)
            if (chips.isNotEmpty()) add(chips.joinToString(", ") + ".")
            pawLine(this)?.let(::add)
            add(""); add("")
        }
        add(e.oneMinute.title.uppercase()); add("")
        e.oneMinute.parts.forEach { add("${it.label} ${it.text}"); add("") }
        e.oneMinute.sayThis?.let { add("${it.label} \"${it.quote}\""); add("") }
        add("")
        e.next?.let { n ->
            add(n.title.uppercase()); add(n.intro); add("")
            n.items.forEach { i ->
                add("${i.number}. ${i.text}")
                add("   Why: ${i.why}")
                i.where?.let { add("   ${i.button ?: EmailWords.DO_IN_APP}: $it.") }
                add("")
            }
            add("")
        }
        if (e.details.sections.isNotEmpty()) {
            add(e.details.title.uppercase()); add("")
            e.details.sections.forEach { s -> add(s.title); s.blocks.forEach { b -> addAll(blockLines(b)) }; add("") }
            add("")
        }
        add(e.signOff.title.uppercase()); add(e.signOff.text); add(e.signOff.signature); add(""); add("")
        e.footer.lines.forEach(::add)
    }

    /** The paw strip as words: "Played on Thu, Sat, Sun, Mon, Tue and Wed (6 of 7 days)." */
    private fun pawLine(c: Postcard): String? {
        if (c.paws.isEmpty()) return null
        val on = c.paws.filter { it.practised }.map { it.day }
        if (on.isEmpty()) return "No practice on any of the last " + EmailWords.count(c.paws.size, "day") + "."
        return "Played on ${EmailSections.joinAnd(on)} (${on.size} of " + EmailWords.count(c.paws.size, "day") + ")." + (c.pawCaption?.let { " $it" } ?: "")
    }

    private fun blockLines(b: Block): List<String> = when (b) {
        is Block.Text -> listOf(listOfNotNull(b.label, b.text).joinToString(" "))
        is Block.Rows -> b.rows.map { r -> "  ${r.title}" + (r.subtitle?.let { " ($it)" } ?: "") + ": ${r.value}." }
        is Block.Keys -> listOf("${b.caption} " + b.keys.filter { it.near + it.far > 0 }.joinToString(", ") { k ->
            "${k.name} ${k.near + k.far}" + when { k.far == 0 -> " near"; k.near == 0 -> " far"; else -> " (${k.near} near, ${k.far} far)" }
        } + ".")
        is Block.Tiles -> listOf(b.tiles.joinToString(", ") { "${it.value} ${it.label}" } + ".")
        is Block.Bars -> listOf("${b.caption}: ${b.values}")
        is Block.Meter -> emptyList()      // its line follows it
    }

    private fun esc(s: String): String = buildString(s.length) {
        for (c in s) when (c) {
            '&' -> append("&amp;"); '<' -> append("&lt;"); '>' -> append("&gt;"); '"' -> append("&quot;"); '\'' -> append("&#39;")
            else -> append(c)
        }
    }
}
