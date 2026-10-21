package com.bigpapituna.sharpears.engine.brief.ai

import java.security.MessageDigest
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/** Where [Budget] keeps its counts and cached replies: a small string store (SharedPreferences on the phone). */
interface BudgetStore {
    fun get(key: String): String?
    /** Stores [value] under [key]; null removes the key. */
    fun put(key: String, value: String?)
}

/** A [BudgetStore] in memory, for tests (and a phone that has no place to keep one). */
class MemoryBudgetStore : BudgetStore {
    private val map = HashMap<String, String>()
    @Synchronized override fun get(key: String): String? = map[key]
    @Synchronized override fun put(key: String, value: String?) { if (value == null) map.remove(key) else map[key] = value }
    @Synchronized fun keys(): Set<String> = map.keys.toSet()
}

/**
 * The AI writer's quota and cache, per profile (Android-free; the integrator persists it through [store]).
 *
 * - **Quota:** at most [callsPerWeek] calls per profile in a week (Monday to Sunday in [zone]). [take] reserves one
 *   before a call; [giveBack] returns it when nothing was billed (a 4xx, a 429, a 5xx, offline, no key). Over quota the
 *   writer returns null and the caller falls back to the templates.
 * - **Cache:** one reply per profile and slot (SESSION, DAY, WEEK, KID, ANSWER), kept with the hash of everything that
 *   was sent ([hash]: the prompt version, the model, the prompts and the findings without "now"). Re-opening the same
 *   brief reads it back without a call; new findings replace it. Refresh skips the read and counts as a call.
 *
 * Keys: `ai.calls.<profileId>` holds "<monday>:<count>"; `ai.cache.<profileId>.<slot>` holds "<hash>\n<writtenAt>\n<reply>".
 * The reply is the model's text with {name} in it (never the name); the writer checks it again when it is read back.
 */
class Budget(private val store: BudgetStore, val callsPerWeek: Int = DEFAULT_CALLS_PER_WEEK, private val zone: ZoneId = ZoneId.systemDefault()) {
    /** A reply written at [at] (epoch ms). */
    data class Cached(val at: Long, val text: String)

    /** Reserves one call for [profileId] this week; false (and nothing reserved) when the week's calls are used up. */
    @Synchronized fun take(profileId: String, now: Long): Boolean {
        val used = used(profileId, now)
        if (used >= callsPerWeek) return false
        store.put(callsKey(profileId), "${week(now)}:${used + 1}")
        return true
    }

    /** Returns a call reserved with [take] that cost nothing. */
    @Synchronized fun giveBack(profileId: String, now: Long) {
        val used = used(profileId, now)
        if (used > 0) store.put(callsKey(profileId), "${week(now)}:${used - 1}")
    }

    /** Calls [profileId] has made this week. */
    @Synchronized fun used(profileId: String, now: Long): Int {
        val v = store.get(callsKey(profileId)) ?: return 0
        val (w, n) = v.split(':', limit = 2).let { it.getOrNull(0) to it.getOrNull(1)?.toIntOrNull() }
        return if (w == week(now) && n != null) n.coerceAtLeast(0) else 0
    }

    fun left(profileId: String, now: Long): Int = (callsPerWeek - used(profileId, now)).coerceAtLeast(0)

    /** The reply kept for [profileId]'s [slot] when it was written from the same [hash], or null. */
    @Synchronized fun cached(profileId: String, slot: String, hash: String): Cached? {
        val v = store.get(cacheKey(profileId, slot)) ?: return null
        val parts = v.split('\n', limit = 3)
        if (parts.size < 3 || parts[0] != hash) return null
        return Cached(parts[1].toLongOrNull() ?: return null, parts[2])
    }

    @Synchronized fun keep(profileId: String, slot: String, hash: String, at: Long, text: String) =
        store.put(cacheKey(profileId, slot), "$hash\n$at\n$text")

    /** Drops [profileId]'s cached reply for [slot] (nothing else changes). */
    @Synchronized fun forget(profileId: String, slot: String) = store.put(cacheKey(profileId, slot), null)

    /** Drops everything kept for [profileId]: call it when the profile is deleted. */
    @Synchronized fun forgetProfile(profileId: String) {
        store.put(callsKey(profileId), null)
        SLOTS.forEach { store.put(cacheKey(profileId, it), null) }
    }

    /** The Monday that starts [now]'s week, as an ISO date. */
    private fun week(now: Long): String =
        Instant.ofEpochMilli(now).atZone(zone).toLocalDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).toString()

    companion object {
        const val DEFAULT_CALLS_PER_WEEK = 40
        /** The cache's slots: the three briefs, the pup's card and the last answer. */
        val SLOTS = listOf("SESSION", "DAY", "WEEK", "KID", "ANSWER")

        fun callsKey(profileId: String) = "ai.calls.$profileId"
        fun cacheKey(profileId: String, slot: String) = "ai.cache.$profileId.$slot"

        /** SHA-256 of [parts], hex. */
        fun hash(vararg parts: String): String {
            val md = MessageDigest.getInstance("SHA-256")
            parts.forEach { md.update(it.toByteArray(Charsets.UTF_8)); md.update(0) }
            return md.digest().joinToString("") { b -> ((b.toInt() and 0xff) + 0x100).toString(16).substring(1) }
        }
    }
}
