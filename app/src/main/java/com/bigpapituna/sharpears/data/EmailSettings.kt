package com.bigpapituna.sharpears.data

import android.content.Context
import android.util.Log
import com.bigpapituna.sharpears.TAG
import com.bigpapituna.sharpears.engine.brief.ai.BudgetStore
import com.bigpapituna.sharpears.engine.brief.email.EmailFrequency
import com.bigpapituna.sharpears.engine.brief.email.EmailKind
import com.bigpapituna.sharpears.engine.brief.email.EmailPrefs
import com.bigpapituna.sharpears.engine.brief.email.EmailVerification
import com.bigpapituna.sharpears.engine.brief.email.FamilyEmail
import com.bigpapituna.sharpears.engine.family.Profile
import java.time.LocalDate

/**
 * One child's email brief settings (engine/brief/email/FamilyEmail.kt). Since 2.8 the address is the family's, the parent's,
 * entered and confirmed once ([FamilyAddress], [family]); a child keeps only their Daily and Weekly switches, the last emails
 * sent, the held reason and the sends for the caps. [load] joins the two into the [EmailPrefs] the schedule and the sender
 * read, as before. The weekly day and time are the weekly notification's ([BriefStore.weekly]), per child.
 *
 * Kept in one phone-wide preference file, [FILE] ("email.xml"): one key per child (the profile id) and the family's
 * ([FAMILY]), so Android's backup can leave it out by its exact name like the AI cache (res/xml/backup_rules.xml,
 * data_extraction_rules.xml): the address never goes to Google's cloud backup or a phone transfer. It is in
 * ProgressFiles.NAMES and ProfileFiles.SHARED, so the grown-up's own progress export carries it (a restored phone keeps
 * sending) and an import puts it back; deleting a profile removes its key ([forget], from Profiles.delete). The address
 * never goes to a log: [EmailPrefs.toString] hides it.
 */
class EmailSettings(private val store: BudgetStore, val profileId: String) {
    /** The family's address, in the same file. */
    val family = FamilyAddress(store)

    /** This child's own part: the switches, the last sent, held, the sends (no address). */
    fun own(): EmailPrefs = FamilyEmail.ownPart(EmailPrefs.decode(store.get(profileId)))

    /** This child's settings with the family's address. [first]: this child's email is the one with the code ([FamilyEmail.lead]). */
    fun load(first: Boolean = false): EmailPrefs = FamilyEmail.join(family.load(), own(), first)

    /** Saves this child's own part of [prefs]. The address in it is the family's: it is changed through [family] only. */
    fun save(prefs: EmailPrefs) = store.put(profileId, FamilyEmail.ownPart(prefs).encode())

    /** Changes and saves this child's part in one go; returns the new settings. */
    fun update(change: (EmailPrefs) -> EmailPrefs): EmailPrefs = synchronized(LOCK) { change(load()).also(::save) }

    /** The Daily switch on the child's row. */
    fun setDaily(on: Boolean): EmailPrefs = update { it.choose(EmailFrequency.of(on, it.frequency.weekly)) }

    /** The Weekly switch on the child's row. */
    fun setWeekly(on: Boolean): EmailPrefs = update { it.choose(EmailFrequency.of(it.frequency.daily, on)) }

    fun choose(frequency: EmailFrequency): EmailPrefs = update { it.choose(frequency) }

    /**
     * An email about this child went out ([EmailSender] records it through this): the child's day or week and the send for
     * the caps; the first email also marks the family's first email gone. Returns the settings as saved ([first] as in [load]).
     */
    fun sent(kind: EmailKind, day: LocalDate, at: Long, first: Boolean = false): EmailPrefs = synchronized(LOCK) {
        if (kind == EmailKind.FIRST) family.firstWent(at)
        save(load(first).sent(kind, day, at))
        load(first)
    }

    /** Everything kept for this child gone (the profile was deleted). The family's address stays. */
    fun forget() = store.put(profileId, null)

    companion object {
        /** The file's name: in ProgressFiles.NAMES and ProfileFiles.SHARED, left out of Android's backup. */
        const val FILE = "email"
        /** The family's record, beside the children's (profile ids never hold a dot). */
        const val FAMILY = "email.family"
        internal val LOCK = Any()

        private fun store(context: Context): BudgetStore =
            PrefsBudgetStore(context.applicationContext.getSharedPreferences(ProgressFiles.fileFor(FILE, Profile.OWNER_ID), Context.MODE_PRIVATE))

        fun of(context: Context, profileId: String): EmailSettings = EmailSettings(store(context), profileId)

        /** The family's one address (Settings > Briefs and email). */
        fun family(context: Context): FamilyAddress = FamilyAddress(store(context))

        /** [profileId] was deleted: their settings go with them (Profiles.delete). */
        fun forget(context: Context, profileId: String) = of(context, profileId).forget()

        /**
         * The first run of 2.8 moves 2.7's per-child addresses to the family's ([FamilyEmail.migrate]), once: nothing is
         * confirmed again. [children]: every profile id, children first, in the family's order. True when it ran now.
         */
        fun migrate(store: BudgetStore, children: List<String>): Boolean = synchronized(LOCK) {
            if (store.get(FAMILY) != null) return false
            val moved = FamilyEmail.migrate(children.map { id -> id to store.get(id)?.let(EmailPrefs::decode) })
            moved.children.forEach { (id, p) -> store.put(id, p.encode()) }
            store.put(FAMILY, moved.family.encode())
            true
        }

        /** [migrate] on this phone, for [profiles] (the roster; kids first). Called by the email job and the settings before they read. */
        fun migrate(context: Context, profiles: List<Profile>) {
            val order = profiles.filter { it.isKid } + profiles.filter { !it.isKid }
            if (migrate(store(context), order.map { it.id }))
                Log.d(TAG, "email: one address for the family now (" + (if (family(context).load().address != null) "moved from a child" else "none yet") + ")")
        }

        /** [migrate] for everyone on the phone (the settings screen, which shows only the kids). */
        fun migrate(context: Context) = migrate(context, Profiles(context).all)
    }
}

/**
 * The family's one address, the parent's (Roy, 2026-10-22), in email.xml under [EmailSettings.FAMILY]: entered, confirmed
 * with the first email's code and stopped here, once for every child, in Settings > Briefs and email. It holds the address,
 * whether it is confirmed, the code and its tries, whether the first email went and when the first emails went (for "Send a
 * new code" waiting ten minutes); the children's switches and the caps are theirs ([EmailSettings]).
 */
class FamilyAddress(private val store: BudgetStore) {
    fun load(): EmailPrefs = FamilyEmail.addressPart(EmailPrefs.decode(store.get(EmailSettings.FAMILY)))

    private fun save(p: EmailPrefs) = store.put(EmailSettings.FAMILY, FamilyEmail.addressPart(p).encode())

    /** The grown-up entered [address]: a fresh code, the first email pending. */
    fun start(address: String, verification: EmailVerification = EmailVerification()): EmailVerification.Started = synchronized(EmailSettings.LOCK) {
        val (next, result) = verification.start(load(), address)
        if (result == EmailVerification.Started.SENDING) save(next)
        result
    }

    /** The grown-up typed [code] in the app. */
    fun verify(code: String, verification: EmailVerification = EmailVerification()): EmailVerification.Verified = synchronized(EmailSettings.LOCK) {
        val (next, result) = verification.verify(load(), code)
        save(next)
        result
    }

    /** The grown-up stopped the emails (or is changing the address): the address and its code gone. The children's switches stay. */
    fun stop(): EmailPrefs = synchronized(EmailSettings.LOCK) { load().cleared().also(::save) }

    /** The first email (with the code) went out at [at]. */
    internal fun firstWent(at: Long) = synchronized(EmailSettings.LOCK) { save(load().copy(firstSent = true).counted(at)) }

    /** Debug seeding only (ui/brief/email/EmailDemo.kt): the family's record as given. */
    internal fun seed(p: EmailPrefs) = synchronized(EmailSettings.LOCK) { save(p) }
}
