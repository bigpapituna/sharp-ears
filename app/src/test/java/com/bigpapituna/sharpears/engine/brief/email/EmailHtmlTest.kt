package com.bigpapituna.sharpears.engine.brief.email

import com.bigpapituna.sharpears.engine.brief.BriefTitles
import com.bigpapituna.sharpears.engine.brief.KeySlips
import com.bigpapituna.sharpears.engine.brief.NOW
import com.bigpapituna.sharpears.engine.brief.Period
import com.bigpapituna.sharpears.engine.brief.SlipPair
import com.bigpapituna.sharpears.engine.brief.ZONE
import com.bigpapituna.sharpears.engine.brief.templates.TemplateGen
import com.bigpapituna.sharpears.engine.brief.templates.TemplateWriter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File
import java.util.Base64
import java.util.Random

/**
 * Design D as an email: the snapshots of a day, a week and the first email (src/test/resources/email/), every part of the
 * model on the page, escaping, size, the rules every email keeps (no em or en dash), and that the only links are https, the
 * app's own and one mailto.
 */
class EmailHtmlTest {
    private val hosted = EmailImages.hosted("https://pictures.example.org/email")
    private val options = EmailHtmlOptions(images = hosted, stopAddress = "pup@example.org")
    private val assets = File("src/main/assets/email")
    private val inline = EmailImages.inline { name -> File(assets, name).takeIf { it.exists() }?.readBytes() }
    private val dir = File("src/test/resources/email")
    private val out = File("build/email-snapshots")

    private fun render(e: EmailBrief, o: EmailHtmlOptions = options) = EmailHtml.render(e, o)
    private fun esc(s: String) = EmailHtml.esc(s)

    /** What the page must show of the model: not the other subject lines, not a range that is the stamp again, not a details heading with no sections. */
    private fun shown(e: EmailBrief) = e.texts().filter {
        (it !in e.subjects || it == e.subject) && !(it == e.postcard.range && it.equals(e.postcard.stamp, ignoreCase = true)) &&
            (e.details.sections.isNotEmpty() || (it != e.details.title && it != e.details.intro))
    }

    private val samples: Map<String, EmailBrief> get() = linkedMapOf(
        "day" to EmailFixtures.day(), "week" to EmailFixtures.week(), "first" to EmailFixtures.first(), "first-never-played" to EmailFixtures.firstNeverPlayed(),
    )

    // ---- snapshots ----

    /**
     * Pins the HTML of each sample word for word, so a change to the design is deliberate. When this fails after an edit you
     * meant: read the new files in build/email-snapshots/ (open them in a browser), copy them over src/test/resources/email/.
     */
    @Test fun everySampleMatchesItsSnapshot() {
        val changed = ArrayList<String>()
        for ((name, e) in samples) {
            val file = File(dir, "email-$name.html")
            val actual = render(e).replace("\r\n", "\n")
            val pinned = if (file.exists()) file.readText(Charsets.UTF_8).replace("\r\n", "\n") else null
            if (pinned == actual) continue
            out.mkdirs()
            File(out, file.name).writeText(actual, Charsets.UTF_8)
            val a = actual.lines(); val p = pinned?.lines().orEmpty()
            val i = (0 until maxOf(a.size, p.size)).firstOrNull { a.getOrNull(it) != p.getOrNull(it) } ?: 0
            changed += if (pinned == null) "${file.name}: no snapshot yet" else "${file.name}: line ${i + 1} differs"
        }
        if (changed.isNotEmpty()) fail("The email HTML changed. If that is deliberate, copy build/email-snapshots/* over src/test/resources/email/.\n" + changed.joinToString("\n"))
    }

    /** What Roy opens in a browser: app/build/email-samples/designed/email-<name>.html, with the pictures inlined as the phone will send them. */
    @Test fun writesTheDesignedSamples() {
        val dest = File("build/email-samples/designed").apply { mkdirs() }
        for ((name, e) in samples) {
            File(dest, "email-$name.html").writeText(render(e, EmailHtmlOptions(inline, "pup@example.org")), Charsets.UTF_8)
            File(dest, "email-$name.txt").writeText(EmailHtml.text(e, options), Charsets.UTF_8)
        }
        assertTrue(File(dest, "email-week.html").length() > 10_000)
    }

    // ---- every part of the model is on the page ----

    @Test fun everyPartOfTheModelAppears() {
        for ((name, e) in samples) {
            val html = render(e)
            for (t in shown(e)) assertTrue("$name: \"$t\" is not on the page", html.contains(esc(t)))
            // The order of design D: welcome, postcard, one minute, next, details, gold star, footer.
            val order = listOfNotNull(e.welcome?.title, e.postcard.stamp, e.postcard.headline, e.oneMinute.title, e.next?.title, e.details.title.takeIf { e.details.sections.isNotEmpty() },
                e.signOff.title, e.footer.lines.first()).map(::esc)
            var at = -1
            for (s in order) { val i = html.indexOf(s, at + 1); assertTrue("$name: \"$s\" after position $at", i > at); at = i }
            assertTrue(name, html.contains("<title>" + esc(e.subject) + "</title>"))
            assertTrue("the preheader is hidden", html.contains("display:none") && html.contains(esc(e.preheader)))
        }
    }

    @Test fun theWeeksFiveSectionsAreDetailsAndOnlyTheFirstIsOpen() {
        val e = EmailFixtures.week()
        val html = render(e)
        assertEquals(5, Regex("<details\\b").findAll(html).count())
        assertEquals(5, Regex("</details>").findAll(html).count())
        assertEquals(1, Regex("<details open\\b").findAll(html).count())
        for (s in e.details.sections) assertTrue(s.title, html.contains("<summary") && html.contains(esc(s.title) + "</summary>"))
        assertTrue("says Gmail shows them open", html.contains("Gmail among them, show every section open"))
        assertTrue(html.indexOf("<details open") < html.indexOf(esc(e.details.sections[0].title)))
    }

    @Test fun theBlocksAreDrawnTheEmailWay() {
        val html = render(EmailFixtures.week())
        // The lessons table: a tinted row for the hard one, three stars a row.
        assertTrue(html.contains("background:#FFF3D6"))
        assertTrue(html.contains("&#9733;&#9733;&#9733;"))
        // The slip heatmap: 13 keys C to C (eight white, five black), the count under each key that slipped.
        val keys = Regex("<td valign=\"top\" align=\"center\" width=\"[0-9.]+%\"").findAll(html).count()
        assertEquals(13, keys)
        assertTrue("D's five slips are in the strongest yellow", html.contains("background:#F5C842"))
        assertTrue("G's far misses are orange", html.contains("#FFB574") || html.contains("#F08A5D"))
        // The four-week chart: proportional heights, the tallest 60 px, the 19 minutes about 24.
        assertTrue(html.contains("height:60px;background:#E8B23A"))
        assertTrue(Regex("height:2[0-9]px;background:#F3D98F").containsMatchIn(html))
        // The mix as one bar, and the stat tiles.
        val mix = EmailFixtures.week().details.sections.flatMap { it.blocks }.filterIsInstance<Block.Meter>().single().parts
        assertTrue(html.contains("aria-label=\"" + mix.joinToString(", ") { "${it.percent}% ${it.label}" } + "\""))
        assertTrue("black keys say sharp or flat as the key is named", html.contains("E&#9837;<") && html.contains("F&#9839;<"))
        assertTrue(html.contains("near misses") && html.contains("slips per 100 notes"))
        // The paw strip: seven cells, five gold.
        assertEquals(7, Regex("width=\"14%\"").findAll(html).count())
        assertEquals(5, Regex("background:#E8B23A;border:2px solid").findAll(html).count())
        assertTrue(html.contains("aria-label=\"Played on "))
    }

    @Test fun theFirstEmailShowsTheCodeInLargeDigitsAboveThePostcard() {
        val e = EmailFixtures.first()
        val html = render(e)
        val code = html.indexOf(">482913</div>")
        assertTrue(code > 0)
        assertTrue(html.substring(0, code).contains("font-size:42px"))
        assertTrue(code < html.indexOf(esc(e.postcard.headline)))
        for (c in e.welcome!!.howOften.choices) assertTrue(c.label, html.contains(esc(c.text)))
        assertFalse("only the first email welcomes", render(EmailFixtures.day()).contains("Your code"))
        // A first email for a child who never played still has the welcome, the postcard and the footer.
        val quiet = render(EmailFixtures.firstNeverPlayed())
        assertTrue(quiet.contains(">482913</div>") && quiet.contains("</html>"))
    }

    @Test fun everyButtonIsAStyledLinkToTheChildsBrief() {
        val week = EmailFixtures.week()
        val html = render(week)
        val buttons = week.next!!.items.count { it.button != null }
        assertTrue(buttons >= 2)
        val links = Regex("<a href=\"(sharpears://[^\"]+)\"").findAll(html).map { it.groupValues[1] }.toList()
        assertEquals(buttons, links.size)
        assertTrue(links.all { it == "sharpears://brief/p1/week" })
        assertEquals(buttons, Regex(">Do this in the app</a>").findAll(html).count())
        for (i in week.next!!.items.filter { it.where != null }) assertTrue(html.contains(esc("If the button does not open the app, open it yourself and go to: " + i.where)))
        assertTrue("a day's buttons open the day", render(EmailFixtures.day()).contains("sharpears://brief/p1/day"))
        assertEquals("sharpears://brief/p%201%2F%C3%A9/week", EmailHtml.deepLink(EmailFixtures.email(EmailFixtures.weekFindings(), profileId = "p 1/é")))
        assertEquals("sharpears://brief", EmailHtml.deepLink(EmailFixtures.email(EmailFixtures.weekFindings(), profileId = "")))
        // Something to keep doing has nothing to tap.
        val keep = week.next!!.items.filter { it.button == null }
        keep.forEach { assertFalse(it.text, it.where != null) }
    }

    @Test fun theFooterEndsWithAPlainMailtoToStopTheEmails() {
        val e = EmailFixtures.week()
        val html = render(e)
        val href = "mailto:pup@example.org?subject=Stop%20the%20emails%20for%20Mia"
        assertEquals(href, EmailHtml.stopLink(e, "pup@example.org"))
        assertTrue(html.contains("href=\"$href\""))
        assertTrue(html.indexOf(href) > html.indexOf(esc(e.footer.lines.first())))
        assertTrue(EmailHtml.text(e, options).trimEnd().endsWith("write to: $href"))
        // No sender address, no line.
        val bare = render(e, EmailHtmlOptions(hosted, ""))
        assertFalse(bare.contains("mailto:"))
        assertFalse(EmailHtml.text(e, EmailHtmlOptions(hosted, "")).contains("mailto:"))
        assertEquals("mailto:a%2Bb@example.org?subject=Stop%20the%20emails%20for%20Zo%C3%AB%20%26%20Co", EmailHtml.stopLink(EmailFixtures.email(EmailFixtures.dayFindings(), name = "Zoë & Co"), "a+b@example.org"))
    }

    @Test fun theTextPartIsPlainTextEmailsWords() {
        val e = EmailFixtures.week()
        val (html, text) = EmailHtml.renderer(options).render(e)
        assertTrue(html.startsWith("<!doctype html>"))
        assertTrue(text.startsWith(PlainTextEmail.text(e)))
        assertFalse(text.contains("<"))
    }

    // ---- escaping ----

    @Test fun everyStringIsEscaped() {
        val evil = "Mia <script>alert(1)</script> & \"Co\" 'x'"
        val base = EmailFixtures.week()
        val e = base.copy(
            name = evil, subject = evil, preheader = evil, profileId = "<b>\"p\"",
            postcard = base.postcard.copy(headline = evil, stamp = evil, range = evil, chips = listOf(evil), pawCaption = evil, avatarColour = evil),
            oneMinute = base.oneMinute.copy(parts = listOf(Part(evil, evil)), sayThis = SayThis(evil, evil, PupPose.STICKER)),
            next = NextSteps(evil, evil, listOf(NextItem(1, evil, evil, null, evil, evil, evil))),
            details = base.details.copy(title = evil, intro = evil, sections = listOf(DetailSection(SectionId.LESSONS, evil, true, listOf(
                Block.Text(evil, evil), Block.Rows(listOf(Row(evil, evil, evil, 2, true))), Block.Tiles(listOf(Tile(evil, evil))),
                Block.Bars(listOf(Bar(evil, 3, 1f)), evil, evil), Block.Meter(listOf(MeterPart(evil, 100))),
                Block.Keys(listOf(KeyCell(60, evil, false, 1, 0, 1)), evil),
            )))),
            signOff = SignOff(evil, evil, evil, PupPose.CROWN), footer = Footer(listOf(evil)),
        )
        val html = render(e, EmailHtmlOptions(EmailImages.hosted("https://x.example.org"), "a@example.org"))
        assertFalse("raw script", html.contains("<script"))
        assertFalse("raw name", html.contains("Mia <script>"))
        assertFalse("an attribute broken open by a quote", Regex("alt=\"[^\"]*\"[^>]*<").containsMatchIn(html))
        assertTrue(html.contains("Mia &lt;script&gt;alert(1)&lt;/script&gt; &amp; &quot;Co&quot; &#39;x&#39;"))
        assertFalse(html.contains("<b>\"p\""))
        assertTrue(html.contains("sharpears://brief/%3Cb%3E%22p%22/week"))
        // The welcome too.
        val w = EmailFixtures.first().let { f -> f.copy(welcome = f.welcome!!.copy(title = evil, code = "<1>")) }
        val wh = render(w)
        assertFalse(wh.contains("<script")); assertTrue(wh.contains("&lt;1&gt;"))
        assertEquals("&amp;&lt;&gt;&quot;&#39;", esc("&<>\"'"))
    }

    // ---- size ----

    @Test fun sizes() {
        for ((name, e) in samples) {
            val small = render(e)
            assertTrue("$name hosted: ${small.length}", small.length < 60_000)
            val big = render(e, EmailHtmlOptions(inline, "pup@example.org"))
            assertTrue("$name inline: ${big.length}", big.length < 400 * 1024)
            // Three pictures at most (the pup, the one saying the line, the crown), about 20 KB each.
            assertTrue("$name inline: ${big.length}", big.length - small.length < 90_000)
        }
        // The largest email there can be, whichever avatar: the first email of a full week with a picture in every place.
        for (avatar in 0..7) {
            val e = EmailFixtures.email(EmailFixtures.weekFindings(), first = true, avatar = avatar)
            val html = render(e, EmailHtmlOptions(inline, "pup@example.org"))
            assertTrue("avatar $avatar: ${html.length}", html.length < 400 * 1024)
            assertEquals(3, Regex("<img ").findAll(html).count())
        }
    }

    @Test fun picturesComeFromTheImagesGiven() {
        val e = EmailFixtures.week()
        assertFalse(render(e, EmailHtmlOptions(EmailImages.NONE)).contains("<img"))
        val h = render(e)
        assertEquals(3, Regex("<img ").findAll(h).count())
        assertTrue(h.contains("src=\"https://pictures.example.org/email/pup-dancing-1.png\""))
        assertTrue(h.contains("src=\"https://pictures.example.org/email/pup-sticker.png\""))
        assertTrue(h.contains("src=\"https://pictures.example.org/email/pup-crown.png\""))
        assertTrue("alt text names the child's pup", h.contains("alt=\"The blue pup dancing\""))
        assertTrue(Regex("alt=\"The pup holding a gold star\"").containsMatchIn(h) && h.contains("alt=\"The pup wearing a gold crown\""))
        val i = render(e, EmailHtmlOptions(inline, ""))
        val data = Regex("src=\"data:image/png;base64,([A-Za-z0-9+/=]+)\"").findAll(i).map { it.groupValues[1] }.toList()
        assertEquals(3, data.size)
        for (d in data) assertTrue("a PNG", Base64.getDecoder().decode(d).copyOfRange(1, 4).toString(Charsets.US_ASCII) == "PNG")
        // A thinking pup for a quiet day, in the child's colour.
        val quiet = EmailFixtures.email(com.bigpapituna.sharpears.engine.brief.BriefSamples.empty(Period.DAY, NOW, ZONE), avatar = 6)
        assertTrue(render(quiet).contains("pup-thinking-6.png"))
        assertEquals(27, EmailImages.allFiles().size)
        for (f in EmailImages.allFiles()) assertTrue("assets/email/$f", File(assets, f).length() in 1_000..40_000)
        assertEquals("pup-clapping-7.png", EmailImages.fileName(PupPose.CLAPPING, 99))
        assertEquals("pup-flying.png", EmailImages.fileName(PupPose.FLYING, 3))
        try { EmailImages.hosted("http://x.example.org"); fail() } catch (_: IllegalArgumentException) {}
    }

    // ---- the rules ----

    private fun rules(where: String, html: String, text: String) {
        assertFalse("$where: an em or en dash", html.any { it == '—' || it == '–' } || text.any { it == '—' || it == '–' })
        assertFalse("$where: script", html.contains("<script", ignoreCase = true) || html.contains("javascript:", ignoreCase = true))
        // Links: the fonts and hosted pictures over https, the app, one mailto; a picture may also be inline.
        for (m in Regex("(href|src)=\"([^\"]*)\"").findAll(html)) {
            val (attr, url) = m.groupValues[1] to m.groupValues[2]
            val ok = url.startsWith("https://") || url.startsWith("sharpears://") || url.startsWith("mailto:") || (attr == "src" && url.startsWith("data:image/png;base64,"))
            assertTrue("$where: $attr=\"$url\"", ok)
            if (attr == "href") assertFalse("$where: a plain http link", url.startsWith("http://"))
        }
        assertFalse(where, html.contains("http://"))
        // Table layout and inline styles: nothing a mail app drops.
        assertFalse("$where: flex", html.contains("display:flex") || html.contains("position:absolute") || html.contains("box-shadow"))
        assertTrue("$where: one 600 px column", html.contains("max-width:600px"))
        assertEquals("$where: one style block", 1, Regex("<style>").findAll(html).count())
        val style = html.substringAfter("<style>").substringBefore("</style>")
        assertTrue("$where: the style block only toggles details and darkens", style.length < 1_800 && !style.contains("font-family"))
        // Tags close.
        for (tag in listOf("table", "tr", "td", "div", "details", "summary", "span", "p", "h1", "h2", "h3", "a")) {
            val open = Regex("<$tag[ >]").findAll(html).count()
            val close = Regex("</$tag>").findAll(html).count()
            assertEquals("$where: <$tag> opened $open closed $close", open, close)
        }
    }

    @Test fun everySampleKeepsTheRules() {
        for ((name, e) in samples) {
            rules(name, render(e), EmailHtml.text(e, options))
            rules("$name inline", render(e, EmailHtmlOptions(inline, "pup@example.org")), EmailHtml.text(e, options))
            rules("$name plain", render(e, EmailHtmlOptions()), EmailHtml.text(e, EmailHtmlOptions()))
        }
    }

    @Test fun darkModeAndTheFontsAreThere() {
        val html = render(EmailFixtures.week())
        assertTrue(html.contains("<meta name=\"color-scheme\" content=\"light dark\">"))
        assertTrue(html.contains("@media (prefers-color-scheme:dark)"))
        assertTrue(html.contains("<link href=\"https://fonts.googleapis.com/css2?family=Nunito"))
        assertTrue("Nunito first, then web-safe", html.contains("font-family:Nunito,'Trebuchet MS',Arial,Helvetica,sans-serif"))
        assertTrue(html.contains("<html lang=\"en\">"))
        val light = EmailHtml.withoutDarkMode(html)
        assertFalse(light.contains("prefers-color-scheme:dark")); assertTrue(light.contains("@media not all{"))
    }

    @Test fun overRandomFindingsTheEmailKeepsTheRules() {
        val r = Random(2027)
        val gen = TemplateWriter(TemplateGen.titles, seed = 9, clock = { NOW })
        val titles = object : BriefTitles {
            override fun lesson(id: String) = TemplateGen.titles(id)
            override fun song(id: String) = TemplateGen.titles(id)
            override fun page(id: String) = TemplateGen.titles(id)
        }
        repeat(150) { i ->
            val period = if (r.nextBoolean()) Period.DAY else Period.WEEK
            val base = TemplateGen.findings(r, period = period)
            val keys = List(r.nextInt(5)) { KeySlips(40 + r.nextInt(50), r.nextInt(6), r.nextInt(4)) }.distinctBy { it.midi }
            val f = base.copy(stats = base.stats.copy(keySlips = keys, topSlip = keys.firstOrNull()?.let { SlipPair(it.midi, it.midi + r.nextInt(25) - 12, 1 + r.nextInt(9)) },
                notesDue = r.nextInt(500), weeks = if (r.nextBoolean()) List(4) { r.nextInt(120) } else emptyList()))
            val first = r.nextInt(5) == 0
            val e = EmailBriefs.build(f, gen.write(f, "Noor"), "Noor", r.nextInt(8), 1 + r.nextInt(52), EmailFrequency.entries[r.nextInt(4)], first, titles,
                code = if (first) "123456" else null, zone = ZONE, profileId = "kid-$i")
            val html = render(e)
            rules("email $i ($period)", html, EmailHtml.text(e, options))
            assertNotNull(html)
            for (t in shown(e)) assertTrue("email $i: \"$t\"", html.contains(esc(t)))
            assertTrue("email $i: ${html.length}", html.length < 60_000)
        }
    }
}
