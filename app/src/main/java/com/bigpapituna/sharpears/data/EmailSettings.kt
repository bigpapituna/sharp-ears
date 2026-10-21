package com.bigpapituna.sharpears.data

import android.content.Context
import com.bigpapituna.sharpears.engine.brief.ai.BudgetStore
import com.bigpapituna.sharpears.engine.brief.email.EmailFrequency
import com.bigpapituna.sharpears.engine.brief.email.EmailKind
import com.bigpapituna.sharpears.engine.brief.email.EmailPrefs
import com.bigpapituna.sharpears.engine.brief.email.EmailVerification
import java.time.LocalDate

/**
 * One child's email brief settings (engine/brief/email/EmailVerification.kt, [EmailPrefs]): the grown-up's address and
 * whether it is verified, the pending code and its expiry, the frequency, the last daily and weekly sent, the sends for the
 * caps. Per CHILD, not per grown-up: each child's brief can go to a different address. The weekly day and time are the
 * weekly notification's ([BriefStore.weekly]), shared.
 *
 * Kept in one phone-wide preference file, [FILE] ("email.xml"), one key per child (the profile id), so Android's backup can
 * leave it out by its exact name like the AI cache (res/xml/backup_rules.xml, data_extraction_rules.xml): the address
 * never goes to Google's cloud backup or a phone transfer. It is in ProgressFiles.NAMES and ProfileFiles.SHARED, so the
 * grown-up's own progress export carries it (a restored phone keeps sending) and an import puts it back; deleting a profile
 * removes its key ([forget], from Profiles.delete). The address never goes to a log: [EmailPrefs.toString] hides it.
 */
class EmailSettings(private val store: BudgetStore, val profileId: String) {
    fun load(): EmailPrefs = EmailPrefs.decode(store.get(profileId))

    fun save(prefs: EmailPrefs) = store.put(profileId, prefs.encode())

    /** Changes and saves in one go; returns the new settings. */
    fun update(change: (EmailPrefs) -> EmailPrefs): EmailPrefs = synchronized(LOCK) { change(load()).also(::save) }

    /** The grown-up entered [address]: a fresh code, the first email pending. */
    fun start(address: String, verification: EmailVerification = EmailVerification()): EmailVerification.Started = synchronized(LOCK) {
        val (next, result) = verification.start(load(), address)
        if (result == EmailVerification.Started.SENDING) save(next)
        result
    }

    /** The grown-up typed [code] in the app. */
    fun verify(code: String, verification: EmailVerification = EmailVerification()): EmailVerification.Verified = synchronized(LOCK) {
        val (next, result) = verification.verify(load(), code)
        save(next)
        result
    }

    fun choose(frequency: EmailFrequency): EmailPrefs = update { it.choose(frequency) }

    /** An email went out ([EmailSender] records it through this). */
    fun sent(kind: EmailKind, day: LocalDate, at: Long): EmailPrefs = update { it.sent(kind, day, at) }

    /** The grown-up stopped the emails: the address and its code gone (the sends stay for the caps). */
    fun stop(): EmailPrefs = update { it.cleared() }

    /** Everything kept for this child gone (the profile was deleted). */
    fun forget() = store.put(profileId, null)

    companion object {
        /** The file's name: in ProgressFiles.NAMES and ProfileFiles.SHARED, left out of Android's backup. */
        const val FILE = "email"
        private val LOCK = Any()

        fun of(context: Context, profileId: String): EmailSettings = EmailSettings(
            PrefsBudgetStore(context.applicationContext.getSharedPreferences(ProgressFiles.fileFor(FILE, profileId), Context.MODE_PRIVATE)), profileId)

        /** [profileId] was deleted: their address and settings go with them (Profiles.delete). */
        fun forget(context: Context, profileId: String) = of(context, profileId).forget()
    }
}
