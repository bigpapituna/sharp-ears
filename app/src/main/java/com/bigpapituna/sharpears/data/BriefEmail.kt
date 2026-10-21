package com.bigpapituna.sharpears.data

import android.content.Context
import android.util.Log
import androidx.core.content.edit
import com.bigpapituna.sharpears.BuildConfig
import com.bigpapituna.sharpears.TAG
import com.bigpapituna.sharpears.engine.COURSE
import com.bigpapituna.sharpears.engine.CoursePath
import com.bigpapituna.sharpears.engine.brief.Brief
import com.bigpapituna.sharpears.engine.brief.ExportDoc
import com.bigpapituna.sharpears.engine.brief.Findings
import com.bigpapituna.sharpears.engine.brief.WeeklySlot
import com.bigpapituna.sharpears.engine.brief.email.BrevoTransport
import com.bigpapituna.sharpears.engine.brief.email.EmailBrief
import com.bigpapituna.sharpears.engine.brief.email.EmailBriefs
import com.bigpapituna.sharpears.engine.brief.email.EmailExtras
import com.bigpapituna.sharpears.engine.brief.email.EmailFrequency
import com.bigpapituna.sharpears.engine.brief.email.EmailHtml
import com.bigpapituna.sharpears.engine.brief.email.EmailHtmlOptions
import com.bigpapituna.sharpears.engine.brief.email.EmailImages
import com.bigpapituna.sharpears.engine.brief.email.EmailSender
import com.bigpapituna.sharpears.engine.brief.email.PathProgress
import com.bigpapituna.sharpears.engine.family.Profile
import com.bigpapituna.sharpears.engine.kid.KidMap
import com.bigpapituna.sharpears.ui.brief.AppTitles
import com.bigpapituna.sharpears.ui.brief.BriefExport
import com.bigpapituna.sharpears.ui.skin.BedtimeStore
import java.io.File
import java.time.ZoneId

/**
 * The parent brief by email on this phone (engine/brief/email/INTEGRATION.md), wired like [BriefAi]: the Brevo transport
 * from BuildConfig, the designed HTML renderer, the full report's PDF as the attachment, and the rules for when it may go.
 *
 * The key: `BuildConfig.EMAIL_KEY` (with `EMAIL_FROM`, the verified sender), from secrets.properties at build time, empty on
 * any build without it. Only this object reads it: the transport's keyProvider at call time, and [hasKey]. It is never
 * logged; the lines (tag SharpEars, "email: ...") carry ids, kinds and sizes, never the key, the address, a name or a body.
 *
 * A send is allowed on Wi-Fi or another unmetered network, or on mobile data when the grown-up switched [mobileData] on (its
 * own switch on the email settings screen, off by default).
 */
object BriefEmail {
    /** The phone-wide switch (email-options.xml): "Send emails on mobile data". */
    const val OPTIONS_FILE = "email-options"
    private const val MOBILE = "mobileData"

    /** This build can send email: a key and a verified sender address. */
    val hasKey: Boolean get() = BuildConfig.EMAIL_KEY.isNotBlank() && BuildConfig.EMAIL_FROM.isNotBlank()

    private fun options(context: Context) = context.applicationContext.getSharedPreferences(OPTIONS_FILE, Context.MODE_PRIVATE)

    /** "Send emails on mobile data": off until a grown-up allows it. */
    fun mobileData(context: Context): Boolean = options(context).getBoolean(MOBILE, false)
    fun setMobileData(context: Context, on: Boolean) { options(context).edit { putBoolean(MOBILE, on) }; Log.d(TAG, "email: mobile data ${if (on) "allowed" else "not allowed"}") }

    /** True when an email may leave now: an unmetered network, or the grown-up allowed mobile data. */
    fun allowed(context: Context): Boolean = Network.unmetered(context.applicationContext) || mobileData(context)

    private fun log(line: String) = Log.d(TAG, line)

    // ---- the pictures ----

    private fun asset(app: Context, name: String): ByteArray? = runCatching { app.assets.open("email/$name").use { it.readBytes() } }.getOrNull()

    /** The pictures the email carries: hosted at EMAIL_IMAGE_BASE when the build has one (https), else inside the HTML. */
    fun images(context: Context): EmailImages {
        val app = context.applicationContext
        val base = BuildConfig.EMAIL_IMAGE_BASE.trim()
        return if (base.startsWith("https://")) EmailImages.hosted(base) else inlineImages(app)
    }

    /** The host the pictures are fetched from when the email is opened (the privacy page names it), or null when they are inside it. */
    val pictureHost: String? get() = EmailImages.hostOf(BuildConfig.EMAIL_IMAGE_BASE)

    /** The pictures inside the HTML (the in-app preview always uses them: it loads nothing from the network). */
    fun inlineImages(context: Context): EmailImages { val app = context.applicationContext; return EmailImages.inline { asset(app, it) } }

    /** What the renderer is given: the pictures, and the sender address the footer's "stop" link writes to. */
    fun htmlOptions(context: Context, images: EmailImages = images(context)) = EmailHtmlOptions(images, BuildConfig.EMAIL_FROM)

    // ---- who the email is about ----

    /** [EmailSender.Child] for [profile]: titles as the brief's, the lesson summaries, the path and bedtime from the child's own stores. */
    fun child(context: Context, profile: Profile, weekly: WeeklySlot): EmailSender.Child {
        val app = context.applicationContext
        return EmailSender.Child(profile.id, profile.name, profile.avatar, AppTitles(app), weekly, extras(app, profile), summary())
    }

    private fun summary(): (String) -> String? {
        val by by lazy { COURSE.flatMap { it.lessons }.associate { it.id to it.summary } }
        return { id -> by[id] }
    }

    /** Where the child is on the path (the page, its stones done, the page after) and whether bedtime is on. */
    fun extras(context: Context, profile: Profile): EmailExtras {
        val app = context.applicationContext
        val bedtime = runCatching { BedtimeStore(app, profile.id).bedtime }.getOrNull()
        val path = if (!profile.isKid) null else runCatching {
            val prefs = Prefs(app, profile.id)
            val kid = KidStore(app, profile.id)
            val model = KidMap.build(CoursePath(profile.firstUnitId, bigKid = profile.reader), prefs::lessonDone, kid::stars, kid.finales(), pace = kid.pace())
            val page = model.pages.getOrNull(model.nextPage) ?: return@runCatching null
            PathProgress(page.page.id, page.stones.count { it.state == com.bigpapituna.sharpears.engine.kid.StoneState.DONE }, page.stones.size,
                model.pages.getOrNull(model.nextPage + 1)?.page?.id)
        }.getOrNull()
        return EmailExtras(path = path, bedtimeOn = bedtime)
    }

    // ---- the sender ----

    /**
     * The sender for [profile]'s emails: Brevo from BuildConfig (the key read at call time), the designed renderer, the full
     * report as the PDF. Cheap to make: make one per send. Call `send` off the main thread.
     */
    fun sender(context: Context, profile: Profile, zone: ZoneId = ZoneId.systemDefault()): EmailSender {
        val app = context.applicationContext
        val allowed = { allowed(app) }
        val renderer = EmailHtml.renderer(htmlOptions(app))
        return EmailSender(
            transport = BrevoTransport(
                BuildConfig.EMAIL_BASE_URL, keyProvider = { BuildConfig.EMAIL_KEY }, senderName = BuildConfig.EMAIL_FROM_NAME,
                senderEmail = BuildConfig.EMAIL_FROM, allowed = allowed, log = ::log,
            ),
            hasKey = { hasKey }, networkAllowed = allowed,
            pdf = { findings, brief -> pdf(app, profile, findings, brief, zone) },
            render = renderer, zone = zone, log = ::log,
        )
    }

    /** The full report as a PDF, drawn by the same code as Send; the temporary file is gone before this returns. Null if it cannot be made. */
    private fun pdf(app: Context, profile: Profile, findings: Findings, brief: Brief, zone: ZoneId): ByteArray? {
        val blocks = ExportDoc.of(brief, findings, profile.name, AppTitles(app), full = true, now = System.currentTimeMillis(), zone = zone)
        val file = File.createTempFile("brief-", ".pdf", app.cacheDir)
        return try { BriefExport(app).pdf(blocks, file); file.readBytes() } finally { file.delete() }
    }

    // ---- the preview ----

    /**
     * The email as the parent would get it now, for the in-app preview: the child's [findings] and [brief], as a day's or a
     * week's, the weekly slot and frequency as chosen. Nothing is sent.
     */
    fun preview(context: Context, profile: Profile, findings: Findings, brief: Brief, weekly: WeeklySlot, frequency: EmailFrequency, zone: ZoneId = ZoneId.systemDefault()): EmailBrief {
        val app = context.applicationContext
        val child = child(app, profile, weekly)
        return EmailBriefs.build(findings, brief, profile.name, profile.avatar, EmailBriefs.weekNumber(findings.range.toMs, zone), frequency, first = false,
            titles = child.titles, weekly = weekly, extras = child.extras, summary = child.summary, zone = zone, attached = true, profileId = profile.id)
    }

    /** The preview's HTML: pictures inside it, nothing loaded from the network. */
    fun previewHtml(context: Context, email: EmailBrief, dark: Boolean): String =
        EmailHtml.render(email, htmlOptions(context, inlineImages(context))).let { if (dark) it else EmailHtml.withoutDarkMode(it) }
}
