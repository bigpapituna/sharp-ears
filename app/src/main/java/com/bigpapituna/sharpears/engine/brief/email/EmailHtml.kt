package com.bigpapituna.sharpears.engine.brief.email

import kotlin.math.roundToInt

/*
 * Design D as an email (docs/email-design-D.py is the mockup): a postcard with the stamp, the chips and the paw strip; "In
 * one minute" with the pup's line to say at home; "What I'd do next" with a why under each and a button to the app; five
 * tap-to-open sections; the gold star; the footer. Android-free and tested.
 *
 * Email-safe the old way: a table for every layout, every style inline (mail apps strip or ignore <style>; the one block
 * kept only draws the <details> toggles and the dark mode), a 600 px column, Nunito first in a web-safe stack, nothing that
 * needs script. Where a client ignores something it degrades to a plain page: Gmail shows every <details> open, Outlook
 * drops the rounded corners, a client that blocks pictures shows the alt text.
 *
 * Every string from the model is escaped. The only links are https (the fonts, hosted pictures), the app's own
 * `sharpears://brief/<profile>/<period>` and one `mailto:` to stop the emails.
 *
 * The first email is not a brief (2.10.1, security review finding 6): [code] draws the code alone, on the same page.
 */

/** What the renderer needs beyond the email: where the pictures come from and where a reply to stop the emails goes. */
data class EmailHtmlOptions(
    val images: EmailImages = EmailImages.NONE,
    /** The sender's address (BuildConfig.EMAIL_FROM): the footer's "stop" link mails it. Blank: no such line. */
    val stopAddress: String = "",
)

object EmailHtml {
    /** The renderer the sender asks for: this HTML and [PlainTextEmail]'s text (with the stop line when there is an address). */
    fun renderer(options: EmailHtmlOptions = EmailHtmlOptions()) = EmailRenderer { e -> render(e, options) to text(e, options) }

    /** The text part: [PlainTextEmail.text] and a line to stop the emails. */
    fun text(e: EmailBrief, options: EmailHtmlOptions = EmailHtmlOptions()): String =
        PlainTextEmail.text(e) + (stopLink(e, options.stopAddress)?.let { "\nTo stop these emails, write to: $it\n" } ?: "")

    /** The app's door for the buttons: sharpears://brief/<profile id>/<day or week>. */
    fun deepLink(e: EmailBrief): String =
        "sharpears://brief" + (if (e.profileId.isBlank()) "" else "/" + percent(e.profileId) + "/" + e.period.name.lowercase())

    /** mailto:<address>?subject=Stop the emails for <name>, or null with no address. */
    fun stopLink(e: EmailBrief, address: String): String? =
        address.trim().takeIf { it.isNotEmpty() && '@' in it }?.let { "mailto:" + percent(it, keep = "@") + "?subject=" + percent("Stop the emails for ${e.name}", keep = "") }

    fun render(e: EmailBrief, options: EmailHtmlOptions = EmailHtmlOptions()): String = Page(e, options).html()

    /**
     * The first email ([CodeEmail]): its HTML and its text part. One card on the page: the pup's line, the code in large spaced
     * digits, the line about the first brief. No picture, no link, nothing about the child.
     */
    fun code(e: CodeEmail): Pair<String, String> {
        val out = StringBuilder(4_000)
        out.head(e.subject)
        out.append("<body class=\"bg-page\" style=\"margin:0;padding:0;background:$PAGE;color:$INK;font-family:$BODY_FONT;-webkit-text-size-adjust:100%\">")
        out.preheader(e.preheader)
        out.append("<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" class=\"bg-page\" style=\"background:$PAGE\"><tr>")
        out.append("<td align=\"center\" style=\"padding:16px\">")
        out.append("<table role=\"presentation\" width=\"600\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" style=\"width:100%;max-width:600px\"><tr><td>")
        out.append("<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" class=\"bg-card edge\" ")
        out.append("style=\"background:$CARD;border-radius:22px;border-bottom:5px solid $LINE\"><tr><td style=\"padding:20px\">")
        out.append("<p class=\"ink\" style=\"margin:0 0 8px;font-family:$HEAD_FONT;font-size:20px;line-height:1.35;font-weight:900;color:$INK\">").append(esc(CodeEmail.GREETING)).append("</p>")
        out.append("<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" style=\"margin:14px 0 10px\"><tr>")
        out.append("<td align=\"center\" class=\"bg-panel\" style=\"background:$WHITE;border:2px dashed $ACCENT;border-radius:16px;padding:12px 8px\">")
        out.append("<div class=\"dim\" style=\"font-size:12px;letter-spacing:1px;text-transform:uppercase;color:$DIM\">").append(esc(CodeEmail.CODE_LABEL)).append("</div>")
        out.append("<div class=\"accent\" style=\"font-family:$HEAD_FONT;font-weight:900;font-size:42px;line-height:1.2;letter-spacing:10px;color:$ACCENT\">")
        out.append(esc(e.code)).append("</div></td></tr></table>")
        out.append("<p class=\"ink\" style=\"margin:8px 0 0;font-size:15px;line-height:1.5;color:$INK\">").append(esc(CodeEmail.CONFIRM)).append("</p>")
        out.append("</td></tr></table>")
        out.append("</td></tr></table></td></tr></table></body></html>")
        return out.toString() to PlainTextEmail.text(e)
    }

    /** The page's head: the title, the fonts, and the one style block (the toggles of the <details> and the dark mode). */
    private fun StringBuilder.head(title: String) {
        append("<!doctype html><html lang=\"en\"><head><meta charset=\"utf-8\">")
        append("<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">")
        append("<meta name=\"color-scheme\" content=\"light dark\"><meta name=\"supported-color-schemes\" content=\"light dark\">")
        append("<title>").append(esc(title)).append("</title>")
        append("<link href=\"$FONTS\" rel=\"stylesheet\">")
        // The only <style>: the toggles of the <details> and the dark mode. Everything else is inline.
        append("<style>")
        append("details>summary{list-style:none}details>summary::-webkit-details-marker{display:none}")
        append("details>summary:after{content:\"+\";float:right;font-size:22px;line-height:1;color:$ACCENT}details[open]>summary:after{content:\"\\2212\"}")
        append("$DARK_QUERY{")
        append(".bg-page{background:#1E1913!important}.bg-card{background:#2B241B!important}.bg-gold{background:#3B2F14!important}")
        append(".bg-panel{background:#352C21!important}.bg-bubble{background:#4A3A1F!important}.bg-pale{background:#4A3E2F!important}")
        append(".bg-hilite{background:#43361F!important}.ink,.ink *{color:#F3E9D8!important}.dim{color:#C4B397!important}")
        append(".line-d{border-color:#4A3E2F!important}.line-b{border-bottom-color:#4A3E2F!important}.edge{border-bottom-color:#4A3E2F!important}")
        append(".accent{color:#F2A36B!important}details>summary:after{color:#F2A36B}}")
        append("</style></head>")
    }

    /** The line a mail app shows after the subject; padded so no body text follows it in the preview. */
    private fun StringBuilder.preheader(text: String) {
        append("<div style=\"display:none;max-height:0;overflow:hidden;mso-hide:all;font-size:1px;line-height:1px;color:$PAGE\">")
        append(esc(text))
        append("&zwnj;&nbsp;".repeat(60)).append("</div>")
    }

    /** [html] that never goes dark: for a preview that must show the light design whatever the viewer's theme says. */
    fun withoutDarkMode(html: String): String = html.replace(DARK_QUERY, "@media not all")

    private const val DARK_QUERY = "@media (prefers-color-scheme:dark)"

    // ---- palette (the mockup's, with the button and stamp text a shade deeper for contrast on white) ----
    private const val PAGE = "#F3E9D8"
    private const val CARD = "#FFF8EC"
    private const val INK = "#2B2117"
    private const val DIM = "#7A6A52"
    private const val LINE = "#E6D3B3"
    private const val ACCENT = "#B4531F"
    private const val ACCENT_SHADOW = "#8F421B"
    private const val BLUE = "#3F6FB8"
    private const val BLUE_SHADOW = "#2F5690"
    private const val GOLD = "#E8B23A"
    private const val GOLD_SOFT = "#F3D98F"
    private const val PALE = "#EFE2C8"
    private const val BUBBLE = "#FFE7B3"
    private const val HILITE = "#FFF3D6"
    private const val WHITE = "#FFFFFF"
    private const val RULE = "#F0E4CC"
    private const val STAR_OFF = "#D9C8A6"
    private const val CHART_PX = 60

    private const val HEAD_FONT = "Nunito,'Trebuchet MS',Arial,Helvetica,sans-serif"
    private const val BODY_FONT = "'Nunito Sans',Nunito,Arial,Helvetica,sans-serif"
    private const val FONTS = "https://fonts.googleapis.com/css2?family=Nunito:wght@700;900&family=Nunito+Sans:wght@400;600&display=swap"

    /** The slip map: yellow for the key next door, orange for further away, darker for more (heat 1 to 3). */
    private val NEAR_HEAT = listOf(WHITE, "#FFF0BF", "#FFE08A", "#F5C842")
    private val FAR_HEAT = listOf(WHITE, "#FFD9B3", "#FFB574", "#F08A5D")
    private val MIX = listOf("#3F6FB8", GOLD, "#C8622E", "#5E9E6A")

    private val POSE_WORDS = mapOf(
        PupPose.DANCING to "dancing", PupPose.CLAPPING to "clapping", PupPose.THINKING to "thinking",
        PupPose.STICKER to "holding a gold star", PupPose.CROWN to "wearing a gold crown", PupPose.FLYING to "flying",
    )

    /** Escapes text for HTML text and attribute values. */
    fun esc(s: String): String = buildString(s.length + 8) {
        for (c in s) when (c) {
            '&' -> append("&amp;"); '<' -> append("&lt;"); '>' -> append("&gt;"); '"' -> append("&quot;"); '\'' -> append("&#39;")
            else -> append(c)
        }
    }

    /** Percent-encodes UTF-8 for a URL, keeping letters, digits, "-._~" and the characters in [keep]. */
    private fun percent(s: String, keep: String = ""): String = buildString {
        for (b in s.toByteArray(Charsets.UTF_8)) {
            val c = (b.toInt() and 0xFF).toChar()
            if (c in 'a'..'z' || c in 'A'..'Z' || c in '0'..'9' || c in "-._~" || c in keep) append(c) else append('%').append("%02X".format(b.toInt() and 0xFF))
        }
    }

    /** One rendering of one email. */
    private class Page(private val e: EmailBrief, private val o: EmailHtmlOptions) {
        private val out = StringBuilder(32_000)
        private val link = esc(deepLink(e))

        fun html(): String {
            out.head(e.subject)
            out.append("<body class=\"bg-page\" style=\"margin:0;padding:0;background:$PAGE;color:$INK;font-family:$BODY_FONT;-webkit-text-size-adjust:100%\">")
            out.preheader(e.preheader)
            out.append("<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" class=\"bg-page\" style=\"background:$PAGE\"><tr>")
            out.append("<td align=\"center\" style=\"padding:16px\">")
            out.append("<table role=\"presentation\" width=\"600\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" style=\"width:100%;max-width:600px\">")
            card(style = "border:3px dashed $LINE;", classes = "bg-card line-d") { postcard() }
            card { oneMinute() }
            e.next?.let { n -> card { next(n) } }
            if (e.details.sections.isNotEmpty()) card(pad = "14px 20px") { details() }
            card(bg = "#FFF1C9", classes = "bg-gold", edge = GOLD) { signOff() }
            out.append("<tr><td style=\"padding:6px 8px 20px\">"); footer(); out.append("</td></tr>")
            out.append("</table></td></tr></table></body></html>")
            return out.toString()
        }

        // ---- the page ----

        /** One card of the column: a row with the card's rounded table, then a row of space. */
        private fun card(
            bg: String = CARD, edge: String = LINE, pad: String = "20px", style: String = "border-bottom:5px solid $edge;",
            classes: String = "bg-card edge", content: () -> Unit,
        ) {
            out.append("<tr><td><table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" class=\"$classes\" ")
            out.append("style=\"background:$bg;border-radius:22px;$style\"><tr><td style=\"padding:$pad\">")
            content()
            out.append("</td></tr></table></td></tr><tr><td height=\"14\" style=\"height:14px;font-size:0;line-height:0\">&nbsp;</td></tr>")
        }

        private fun h2(text: String, margin: String = "0 0 8px") {
            out.append("<h2 class=\"ink\" style=\"margin:$margin;font-family:$HEAD_FONT;font-size:20px;line-height:1.25;color:$INK\">").append(esc(text)).append("</h2>")
        }

        private fun p(text: String, style: String = "", small: Boolean = false, bold: String? = null) {
            out.append("<p class=\"").append(if (small) "dim" else "ink").append("\" style=\"")
            if ("margin" !in style) out.append("margin:").append(if (small) "4px 0" else "8px 0").append(';')
            out.append("font-size:").append(if (small) "13px" else "15px").append(";line-height:1.5;color:").append(if (small) DIM else INK)
            if (style.isNotEmpty()) out.append(';').append(style)
            out.append("\">")
            if (bold != null) out.append("<b>").append(esc(bold)).append("</b> ")
            out.append(esc(text)).append("</p>")
        }

        // ---- the postcard ----

        private fun pup(pose: PupPose, width: Int, margin: String = "0"): String? {
            val src = o.images.src(pose, e.postcard.avatar) ?: return null
            val colour = if (pose == e.postcard.pose && pose in setOf(PupPose.DANCING, PupPose.CLAPPING, PupPose.THINKING)) "${e.postcard.avatarColour} " else ""
            val alt = "The ${colour}pup ${POSE_WORDS.getValue(pose)}"
            return "<img src=\"${esc(src)}\" width=\"$width\" alt=\"${esc(alt)}\" style=\"display:block;width:${width}px;max-width:${width}px;height:auto;border:0;margin:$margin\">"
        }

        private fun postcard() {
            val c = e.postcard
            out.append("<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\"><tr>")
            pup(c.pose, 140, "-6px 0 0 -8px")?.let { out.append("<td valign=\"top\" width=\"140\">").append(it).append("</td>") }
            out.append("<td valign=\"top\" align=\"right\">")
            out.append("<span class=\"accent\" style=\"display:inline-block;background:$WHITE;border:2px solid $ACCENT;border-radius:8px;padding:5px 9px;margin:6px 4px 4px 0;")
            out.append("font-family:$HEAD_FONT;font-weight:900;font-size:15px;letter-spacing:1px;color:$ACCENT;transform:rotate(6deg)\">").append(esc(c.stamp)).append("</span>")
            if (!c.range.equals(c.stamp, ignoreCase = true)) out.append("<div class=\"dim\" style=\"font-size:13px;color:$DIM;margin-top:6px\">").append(esc(c.range)).append("</div>")
            out.append("</td></tr></table>")
            out.append("<h1 class=\"ink\" style=\"margin:6px 0 10px;font-family:$HEAD_FONT;font-size:26px;line-height:1.2;color:$INK\">").append(esc(c.headline)).append("</h1>")
            if (c.chips.isNotEmpty()) {
                out.append("<div style=\"margin:0 0 6px\">")
                c.chips.forEach {
                    out.append("<span class=\"bg-panel ink line-d\" style=\"display:inline-block;background:$WHITE;border:2px solid $LINE;border-radius:999px;padding:5px 12px;margin:4px 6px 0 0;")
                    out.append("font-size:14px;font-weight:600;color:$INK\">").append(esc(it)).append("</span>")
                }
                out.append("</div>")
            }
            paws(c)
        }

        /** Seven round cells, oldest first: gold with a paw for a day with practice, a ring for the goal, the last day in bold. */
        private fun paws(c: Postcard) {
            if (c.paws.isEmpty()) return
            val on = c.paws.filter { it.practised }.map { it.day }
            val said = if (on.isEmpty()) "No practice on any of the last " + EmailWords.count(c.paws.size, "day") else "Played on " + EmailSections.joinAnd(on)
            out.append("<table role=\"img\" aria-label=\"").append(esc(said)).append("\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" style=\"margin:12px 0 2px\"><tr>")
            for (d in c.paws) {
                out.append("<td align=\"center\" valign=\"top\" width=\"14%\"><table role=\"presentation\" align=\"center\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\"><tr>")
                out.append("<td align=\"center\" valign=\"middle\" width=\"34\" height=\"34\" class=\"${if (d.practised) "paw" else "bg-pale"}\" ")
                out.append("style=\"width:34px;height:34px;border-radius:17px;box-sizing:border-box;background:${if (d.practised) GOLD else PALE};")
                out.append("border:2px solid ${if (d.goal) ACCENT else if (d.practised) GOLD else PALE};font-size:16px;line-height:30px\">")
                out.append(if (d.practised) "&#x1F43E;" else "&nbsp;").append("</td></tr></table>")
                out.append("<div class=\"${if (d.today) "accent" else "dim"}\" style=\"font-size:11px;margin-top:3px;color:${if (d.today) ACCENT else DIM};")
                out.append("font-weight:${if (d.today) 900 else 400}\">").append(esc(d.day)).append("</div></td>")
            }
            out.append("</tr></table>")
            val caption = listOfNotNull(c.pawCaption, "A ring is a day the goal was reached.".takeIf { c.paws.any { it.goal } }).joinToString(" ")
            if (caption.isNotEmpty()) p(caption, "margin:2px 0 0", small = true)
        }

        // ---- in one minute ----

        private fun oneMinute() {
            val m = e.oneMinute
            h2(m.title)
            m.parts.forEach { p(it.text, bold = it.label) }
            m.sayThis?.let { s ->
                out.append("<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" style=\"margin-top:12px\"><tr>")
                pup(s.pose, 64)?.let { out.append("<td valign=\"middle\" width=\"64\">").append(it).append("</td><td width=\"10\" style=\"width:10px\">&nbsp;</td>") }
                out.append("<td valign=\"middle\" class=\"bg-bubble ink\" style=\"background:$BUBBLE;border-radius:18px;padding:13px 16px;font-size:15px;line-height:1.45;color:$INK\">")
                out.append("<b>").append(esc(s.label)).append("</b> &ldquo;").append(esc(s.quote)).append("&rdquo;</td></tr></table>")
            }
        }

        // ---- what I'd do next ----

        private fun next(n: NextSteps) {
            h2(n.title, "0 0 4px")
            p(n.intro, small = true)
            p("Each button opens the app on ${e.name}'s brief; the app asks a grown-up question first.", small = true)
            n.items.forEachIndexed { index, i ->
                out.append("<p class=\"ink\" style=\"margin:16px 0 2px;font-size:15px;line-height:1.45;color:$INK\"><b>").append(i.number).append(". ").append(esc(i.text)).append("</b></p>")
                out.append("<p class=\"dim\" style=\"margin:0 0 4px;font-size:13px;line-height:1.5;color:$DIM\"><b>Why:</b> ").append(esc(i.why)).append("</p>")
                if (i.button != null) {
                    val (fill, shadow) = if (index == 0) ACCENT to ACCENT_SHADOW else BLUE to BLUE_SHADOW
                    out.append("<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" style=\"margin:8px 0 4px\"><tr>")
                    out.append("<td align=\"center\" bgcolor=\"$fill\" style=\"background:$fill;border-radius:999px;border-bottom:4px solid $shadow\">")
                    out.append("<a href=\"$link\" style=\"display:block;padding:13px 18px;font-family:$HEAD_FONT;font-weight:900;font-size:16px;line-height:1.2;color:$WHITE;text-decoration:none;text-align:center\">")
                    out.append(esc(i.button)).append("</a></td></tr></table>")
                    if (i.where != null) p("If the button does not open the app, open it yourself and go to: ${i.where}", "text-align:center", small = true)
                }
            }
        }

        // ---- the details ----

        private fun details() {
            val d = e.details
            h2(d.title, "6px 0 2px")
            p(d.intro, "margin-bottom:0", small = true)
            p("Some mail apps, Gmail among them, show every section open already. That is fine.", "margin-top:0", small = true)
            for (s in d.sections) {
                out.append("<details").append(if (s.open) " open" else "").append(" class=\"bg-panel line-d\" style=\"background:$WHITE;border:2px solid $LINE;border-radius:16px;padding:0 14px;margin:10px 0\">")
                out.append("<summary class=\"ink\" style=\"cursor:pointer;padding:13px 0;font-family:$HEAD_FONT;font-weight:900;font-size:16px;color:$INK\">").append(esc(s.title)).append("</summary>")
                out.append("<div style=\"padding:0 0 14px;font-size:15px;line-height:1.5\">")
                s.blocks.forEach(::block)
                out.append("</div></details>")
            }
        }

        private fun block(b: Block) = when (b) {
            is Block.Text -> p(b.text, "margin:6px 0", small = b.small, bold = b.label)
            is Block.Rows -> rows(b)
            is Block.Keys -> keys(b)
            is Block.Tiles -> tiles(b)
            is Block.Bars -> bars(b)
            is Block.Meter -> meter(b)
        }

        private fun rows(b: Block.Rows) {
            out.append("<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" style=\"border-collapse:collapse;font-size:14px;margin:4px 0\">")
            for (r in b.rows) {
                val tint = if (r.highlight) " class=\"bg-hilite\" style=\"background:$HILITE\"" else ""
                out.append("<tr$tint><td valign=\"top\" class=\"line-b\" style=\"padding:8px 6px 8px 4px;border-bottom:1px solid $RULE\"><span class=\"ink\" style=\"font-weight:600;color:$INK\">")
                out.append(esc(r.title)).append("</span>")
                if (r.subtitle != null) out.append("<br><span class=\"dim\" style=\"font-size:13px;color:$DIM\">").append(esc(r.subtitle)).append("</span>")
                out.append("</td><td valign=\"top\" align=\"right\" width=\"46%\" class=\"line-b\" style=\"padding:8px 4px 8px 6px;border-bottom:1px solid $RULE\">")
                out.append("<span aria-hidden=\"true\" style=\"font-size:16px;letter-spacing:1px;color:$GOLD\">")
                val n = (r.stars ?: 0).coerceIn(0, 3)
                out.append("&#9733;".repeat(n)).append("</span><span aria-hidden=\"true\" style=\"font-size:16px;letter-spacing:1px;color:$STAR_OFF\">").append("&#9733;".repeat(3 - n)).append("</span>")
                out.append("<br><span class=\"dim\" style=\"font-size:13px;color:$DIM\">").append(esc(r.value)).append("</span></td></tr>")
            }
            out.append("</table>")
        }

        /** The slip map: a cell per key, C to C, black keys narrower and shorter; shaded by count, the count under it. */
        private fun keys(b: Block.Keys) {
            if (b.keys.isEmpty()) return
            val white = b.keys.count { !it.black }
            val blacks = b.keys.size - white
            val wWhite = 100.0 / (white + blacks * 0.7)
            val wBlack = wWhite * 0.7
            out.append("<table role=\"img\" aria-label=\"").append(esc(b.caption)).append("\" width=\"100%\" cellpadding=\"0\" cellspacing=\"1\" border=\"0\" style=\"table-layout:fixed;margin:8px 0 2px\"><tr>")
            for (k in b.keys) {
                val w = "%.1f".format(java.util.Locale.ROOT, if (k.black) wBlack else wWhite)
                val fill = when {
                    k.heat <= 0 -> if (k.black) "#4A3F33" else WHITE
                    k.far > k.near -> FAR_HEAT[k.heat.coerceIn(1, 3)]
                    else -> NEAR_HEAT[k.heat.coerceIn(1, 3)]
                }
                val text = if (k.heat <= 0 && k.black) WHITE else INK
                out.append("<td valign=\"top\" align=\"center\" width=\"$w%\" style=\"width:$w%\"><div title=\"").append(esc(k.name)).append("\" style=\"height:${if (k.black) 30 else 46}px;background:$fill;")
                out.append("border:1px solid ${if (k.black) "#2B2117" else "#D9C8A6"};border-radius:0 0 6px 6px;font-size:11px;line-height:${if (k.black) 44 else 70}px;color:$text;overflow:hidden\">")
                out.append(esc(k.name.take(1))).append(if (!k.black) "" else if (k.name.endsWith("flat")) "&#9837;" else "&#9839;").append("</div></td>")
            }
            out.append("</tr><tr>")
            for (k in b.keys) {
                val n = k.near + k.far
                out.append("<td align=\"center\" class=\"ink\" style=\"font-size:11px;font-weight:900;color:$INK;padding-top:2px\">").append(if (n > 0) "$n" else "&nbsp;").append("</td>")
            }
            out.append("</tr></table>")
            p(b.caption + " Yellow is the key next door, orange is further away.", "margin:2px 0 8px", small = true)
        }

        private fun tiles(b: Block.Tiles) {
            for (chunk in b.tiles.chunked(3)) {
                out.append("<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" style=\"margin:6px 0\"><tr>")
                chunk.forEachIndexed { i, t ->
                    if (i > 0) out.append("<td width=\"8\" style=\"width:8px\">&nbsp;</td>")
                    out.append("<td align=\"center\" valign=\"top\" width=\"${100 / 3 - 2}%\" class=\"bg-pale\" style=\"background:$CARD;border:1px solid $LINE;border-radius:16px;padding:10px 4px\">")
                    out.append("<div class=\"accent\" style=\"font-family:$HEAD_FONT;font-weight:900;font-size:22px;line-height:1.2;color:$ACCENT\">").append(esc(t.value)).append("</div>")
                    out.append("<div class=\"dim\" style=\"font-size:12px;line-height:1.3;color:$DIM\">").append(esc(t.label)).append("</div></td>")
                }
                out.append("</tr></table>")
            }
        }

        /** Minutes per week as bars of proportional height, the latest in full gold, the value above and the week's name below. */
        private fun bars(b: Block.Bars) {
            if (b.bars.isEmpty()) return
            p(b.caption, "margin:0 0 4px", small = true)
            val w = 100 / b.bars.size
            out.append("<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" style=\"table-layout:fixed\"><tr>")
            b.bars.forEachIndexed { i, bar ->
                val h = if (bar.value <= 0) 2 else (bar.share.coerceIn(0f, 1f) * CHART_PX).roundToInt().coerceAtLeast(4)
                val fill = if (i == b.bars.lastIndex) GOLD else GOLD_SOFT
                out.append("<td valign=\"bottom\" align=\"center\" width=\"$w%\" style=\"padding:0 8px\">")
                out.append("<div class=\"ink\" style=\"font-size:12px;font-weight:900;color:$INK;padding-bottom:2px\">").append(bar.value).append("</div>")
                out.append("<div style=\"height:${h}px;background:${if (bar.value <= 0) PALE else fill};border-radius:4px 4px 0 0;font-size:0;line-height:0\">&nbsp;</div></td>")
            }
            out.append("</tr><tr>")
            for (bar in b.bars) out.append("<td align=\"center\" class=\"dim\" style=\"font-size:11px;color:$DIM;padding-top:3px\">").append(esc(bar.label)).append("</td>")
            out.append("</tr></table>")
            p(b.values, "margin:6px 0 10px", small = true)
        }

        /** One bar split in parts by percent. */
        private fun meter(b: Block.Meter) {
            val parts = b.parts.filter { it.percent > 0 }
            if (parts.isEmpty()) return
            out.append("<table role=\"img\" aria-label=\"").append(esc(parts.joinToString(", ") { "${it.percent}% ${it.label}" })).append("\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" style=\"margin:6px 0\"><tr>")
            parts.forEachIndexed { i, part ->
                out.append("<td width=\"${part.percent}%\" height=\"10\" title=\"").append(esc(part.label)).append("\" style=\"width:${part.percent}%;height:10px;background:${MIX[i % MIX.size]};font-size:0;line-height:0")
                if (i == 0) out.append(";border-radius:5px 0 0 5px")
                if (i == parts.lastIndex) out.append(";border-radius:0 5px 5px 0")
                if (parts.size == 1) out.append(";border-radius:5px")
                out.append("\">&nbsp;</td>")
            }
            out.append("</tr></table>")
        }

        // ---- sign-off and footer ----

        private fun signOff() {
            val s = e.signOff
            out.append("<div align=\"center\" style=\"text-align:center\">")
            pup(s.pose, 110, "0 auto")?.let { out.append(it) }
            out.append("<h3 class=\"ink\" style=\"margin:8px 0 4px;font-family:$HEAD_FONT;font-size:18px;color:$INK\">").append(esc(s.title)).append("</h3>")
            p(s.text, "margin:0")
            p(s.signature, "margin-top:12px", small = true)
            out.append("</div>")
        }

        private fun footer() {
            e.footer.lines.forEach { p(it, "text-align:center;margin:6px 0", small = true) }
            stopLink(e, o.stopAddress)?.let { href ->
                out.append("<p class=\"dim\" style=\"margin:6px 0;font-size:13px;line-height:1.5;text-align:center;color:$DIM\">Would you rather not get these? ")
                out.append("<a href=\"").append(esc(href)).append("\" style=\"color:$ACCENT;text-decoration:underline\" class=\"accent\">Send a note to stop the emails for ")
                out.append(esc(e.name)).append("</a>.</p>")
            }
        }
    }
}
