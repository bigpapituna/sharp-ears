package com.bigpapituna.sharpears.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.core.content.edit
import com.bigpapituna.sharpears.BuildConfig
import com.bigpapituna.sharpears.TAG
import com.bigpapituna.sharpears.engine.brief.ai.AiLimits
import com.bigpapituna.sharpears.engine.brief.ai.AiWriter
import com.bigpapituna.sharpears.engine.brief.ai.Budget
import com.bigpapituna.sharpears.engine.brief.ai.BudgetStore
import com.bigpapituna.sharpears.engine.brief.ai.OpenRouterTransport
import com.bigpapituna.sharpears.engine.brief.ai.Titles
import com.bigpapituna.sharpears.engine.brief.ai.WriterChain
import com.bigpapituna.sharpears.engine.brief.templates.CourseTitles
import com.bigpapituna.sharpears.engine.brief.templates.TemplateWriter
import com.bigpapituna.sharpears.engine.family.Profile

/** The AI brief's weekly quota and reply cache (engine/brief/ai/Budget.kt) over a preference file. */
class PrefsBudgetStore(private val p: SharedPreferences) : BudgetStore {
    override fun get(key: String): String? = p.getString(key, null)
    override fun put(key: String, value: String?) { p.edit { if (value == null) remove(key) else putString(key, value) } }
}

/** One person's writers: [chain] is the AI writer over the templates (engine/brief/ai/WriterChain), [phone] the templates alone. */
class BriefWriters(val chain: WriterChain, val phone: TemplateWriter)

/**
 * The parent brief's writers on this phone (engine/brief/ai/INTEGRATION.md, engine/brief/templates/INTEGRATION.md): the AI
 * writer first when this build has a key and a grown-up has not switched AI briefs off, the template writer behind it (it
 * never fails, so a brief is always written). The transport and the quota are made once per process; the writers per
 * person (the quota, the cache and the templates' wording are each person's).
 *
 * The key: `BuildConfig.AI_KEY`, from secrets.properties at build time (empty on any build without it). Only this object
 * reads it: the transport at call time, and [hasKey]. It is never logged; the AI's log lines (tag SharpEars, "ai: ...")
 * carry only times, sizes, kinds and decisions.
 *
 * Settings (file [SETTINGS_FILE], backed up like any setting): [on] (the parent corner's "AI briefs", on by default when
 * there is a key) and [mobileData] ("Write briefs on mobile data", off by default: the AI then writes only on Wi-Fi or
 * another unmetered network, and the templates on mobile data). The cache and quota live in [CACHE_FILE], left out of
 * Android's backup (res/xml/backup_rules.xml, data_extraction_rules.xml) and cleared for a deleted profile.
 */
object BriefAi {
    /** The quota and cache file (ai-brief.xml); not backed up. */
    const val CACHE_FILE = "ai-brief"
    /** The two switches (ai-settings.xml). */
    const val SETTINGS_FILE = "ai-settings"
    private const val ON = "on"
    private const val MOBILE = "mobileData"

    /** This build carries an AI key. */
    val hasKey: Boolean get() = BuildConfig.AI_KEY.isNotBlank()

    @Volatile private var budget: Budget? = null
    @Volatile private var transport: OpenRouterTransport? = null

    private fun log(line: String) = Log.d(TAG, line)

    private fun settings(context: Context) = context.applicationContext.getSharedPreferences(SETTINGS_FILE, Context.MODE_PRIVATE)

    /** AI briefs are written by the model on this phone: a key, and the parent corner's switch on (its default). */
    fun on(context: Context): Boolean = hasKey && settings(context).getBoolean(ON, true)

    /** The parent corner's "AI briefs" switch as saved (on until a grown-up turns it off). */
    fun switchOn(context: Context): Boolean = settings(context).getBoolean(ON, true)
    fun setOn(context: Context, on: Boolean) { settings(context).edit { putBoolean(ON, on) }; log("ai: briefs ${if (on) "on" else "off"} (parent corner)") }

    /** "Write briefs on mobile data": off until a grown-up allows it. */
    fun mobileData(context: Context): Boolean = settings(context).getBoolean(MOBILE, false)
    fun setMobileData(context: Context, on: Boolean) { settings(context).edit { putBoolean(MOBILE, on) }; log("ai: mobile data ${if (on) "allowed" else "not allowed"}") }

    /** 40 calls a person a week and the last reply per slot. */
    fun budget(context: Context): Budget = budget ?: synchronized(this) {
        budget ?: Budget(PrefsBudgetStore(context.applicationContext.getSharedPreferences(CACHE_FILE, Context.MODE_PRIVATE))).also { budget = it }
    }

    private fun transport(context: Context): OpenRouterTransport = transport ?: synchronized(this) {
        transport ?: run {
            val app = context.applicationContext
            OpenRouterTransport(
                baseUrl = BuildConfig.AI_BASE_URL,
                keyProvider = { BuildConfig.AI_KEY },          // read at call time, kept in no field
                model = BuildConfig.AI_MODEL,
                allowed = { Network.unmetered(app) || mobileData(app) },
                log = ::log,
            )
        }.also { transport = it }
    }

    /** A deleted profile's quota and cached replies go with it (Profiles.delete). */
    fun forgetProfile(context: Context, profileId: String) = budget(context).forgetProfile(profileId)

    /**
     * [profile]'s writers. Song titles are read once here (bundled and imported songs); the roster's names are read when the
     * AI writer asks (a title or an id holding someone else's name is never sent). Cheap: make one per screen or job.
     */
    fun writers(context: Context, profile: Profile): BriefWriters {
        val app = context.applicationContext
        val songs = runCatching { SongRepository(app).all().associate { it.id to it.title } }.getOrDefault(emptyMap())
        val phone = TemplateWriter(CourseTitles.lookup { songs[it] }, seed = profile.id.hashCode().toLong())
        val ai = AiWriter(transport(app), BuildConfig.AI_MODEL, System::currentTimeMillis, AiLimits(), budget(app), profile.id,
            titles = Titles.course { songs },
            otherNames = { runCatching { Profiles(app).all.map { it.name } }.getOrDefault(emptyList()) },
            log = ::log)
        return BriefWriters(WriterChain(listOf(ai, phone), log = ::log), phone)
    }
}
