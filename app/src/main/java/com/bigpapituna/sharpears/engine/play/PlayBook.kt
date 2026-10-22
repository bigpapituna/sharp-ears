package com.bigpapituna.sharpears.engine.play

import com.bigpapituna.sharpears.engine.SessionPlan
import java.time.LocalDate

/** Where the Play corner keeps its few values: one string per key. data/PlayStore.kt backs it with SharedPreferences. */
interface PlayStorage {
    fun get(key: String): String?
    fun put(key: String, value: String)
}

/**
 * One profile's Play corner memory: today's quest and the day's best in Pup says. Android-free so the rules are
 * tested on the JVM; data/PlayStore.kt opens it on the profile's "play" file.
 */
class PlayBook(private val storage: PlayStorage) {
    /** Today's quest, made (and saved) when there is none for [day] yet; a kid's may ask for [game] (2.7, the arcade's pick). */
    fun quest(day: LocalDate, kid: Boolean, plan: SessionPlan?, game: Pair<String, String>? = null): Quest {
        val stored = Quest.decode(storage.get(KEY_QUEST))
        val q = Quest.today(stored, day, kid, plan, game)
        if (q !== stored) storage.put(KEY_QUEST, q.encode())
        return q
    }

    /**
     * Counts [event] toward [day]'s quest and returns the tasks it completed (a star each). A kid's quest is made on
     * the spot when the day has none yet (the kid tasks need no plan); a grown-up's comes from the day's plan, so
     * until the home card has made it, events wait for nothing and are dropped.
     */
    fun recordQuest(event: QuestEvent, day: LocalDate, kid: Boolean): List<QuestTask> {
        val stored = Quest.decode(storage.get(KEY_QUEST))?.takeIf { it.day == day && it.kid == kid }
        val q = stored ?: if (kid) Quest.forDay(day, true, null) else return emptyList()
        val done = q.record(event)
        storage.put(KEY_QUEST, q.encode())
        return done
    }

    /** The best Pup says pattern of [day] (0 when none). */
    fun pupSaysBest(day: LocalDate): Int {
        val v = storage.get(KEY_PUP_SAYS) ?: return 0
        val (d, best) = v.split(':').let { (it.getOrNull(0) ?: "") to (it.getOrNull(1)?.toIntOrNull() ?: 0) }
        return if (d == day.toString()) best else 0
    }

    /** Keeps [best] when it beats the day's best so far; returns this profile's result for the crown of the day. */
    fun savePupSays(profileId: String, best: Int, day: LocalDate): PupSaysResult {
        val top = maxOf(pupSaysBest(day), best.coerceAtLeast(0))
        storage.put(KEY_PUP_SAYS, "$day:$top")
        return PupSaysResult(profileId, top)
    }

    companion object {
        const val KEY_QUEST = "quest"
        const val KEY_PUP_SAYS = "pupSaysDay"
    }
}
